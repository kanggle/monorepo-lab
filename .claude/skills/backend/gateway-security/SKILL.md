---
name: gateway-security
description: API gateway JWT verification, role admission, identity header enrichment, rate-limit keying
category: backend
---

# Skill: Gateway Security

Patterns for API-gateway JWT verification, **role-based admission**, and request enrichment
(Spring Cloud Gateway, reactive).

**Source of truth:** `platform/contracts/jwt-standard-claims.md` § *Gateway Enforcement Rules*
(normative — the numbered rules below are its rules) + `platform/api-gateway-policy.md`
(rate-limit keying) + `platform/security-rules.md`. Your gateway's own surface is described in
`specs/services/<service>/architecture.md`.

---

## Use `libs/java-gateway` — do not hand-roll these filters

`ADR-MONO-049` consolidated **49** hand-written copies of gateway/servlet security into shared
libraries, because the copies drifted and nobody was watching them. **Writing a fresh
`JwtAuthenticationFilter` in your gateway is writing copy #50.**

| Need | Class in `libs/java-gateway` |
|---|---|
| Strip client-supplied identity headers (anti-spoofing) | `IdentityHeaderStripFilter` |
| Verify the token and inject `X-User-*` headers | `JwtHeaderEnrichmentFilter` + `JwtHeaderMapping` |
| Read claims for **header enrichment** — `JwtClaims.role(jwt)` returns the `roles` array **comma-joined** (with a legacy singular fallback, and `""` when absent; that precedence *is* the security contract) | `JwtClaims` |
| Read the **array itself** for admission | `jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES)` — there is no `JwtClaims.roles(...)`; do not admit on the joined header string |
| Decoder / issuer allow-list wiring | `GatewayJwtDecoders`, `libs/java-security` → `AllowedIssuersValidator` |
| Baseline security chain | `SecurityConfig` |
| Rate limiting that must not fail closed | `FailOpenRateLimiter` |

The snippets below explain **what those classes guarantee** and **what your gateway must still
add on top** (rule 6 — admission — is per-platform and the library cannot decide it for you).

---

## The six enforcement rules (contract § Gateway Enforcement Rules)

1. **Strip** every client-supplied identity header (`Authorization` is kept; `X-User-Id`,
   `X-User-Role`, `X-User-Email`, `X-Account-Type` are removed) **before** validation → `IdentityHeaderStripFilter`.
2. **Require** a bearer token on non-public routes → `401` if missing.
3. **Verify the signature** against the IdP's **JWKS** (RS256). Never a shared secret.
4. **Validate the issuer** against the allow-list → `AllowedIssuersValidator`.
5. **Validate `aud`** — reject a token minted for a different platform.
6. **Validate authorization (role admission)** — *"Admit iff the token carries ≥ 1 role valid for
   the requested surface; otherwise respond `403 Forbidden`."* **This is a positive check against
   a closed role set.**
7. **Inject** downstream headers: `X-User-Id ← sub`, `X-User-Email ← email`,
   **`X-User-Role ← comma-separated `roles` array`** (e.g. `WMS_OPERATOR,OUTBOUND_MANAGER`).

> **`roles` is an array and it is the sole authorization axis** (ADR-MONO-032). There is **no
> singular `role` claim** and **no `account_type` claim** (removed by TASK-MONO-263). Reading
> `claims.get("role", String.class)` returns `null` on a conforming token — and a `null` role
> that is then never checked is an *authentication-only* gateway: it lets any valid token from
> the platform reach any surface behind it.

---

## Rule 6 — role admission (the step most gateways forget)

Authentication answers *"is this token real?"*. **Admission answers *"is this holder allowed on
this surface?"*** — the library cannot answer it, because the valid role set is per-platform.

```java
// Reactive gateway: admit only holders of a role valid for the requested surface.
// Reference shape — a live example is ecommerce's AccountTypeEnforcementFilter.
@Component
public class RoleAdmissionFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -90;  // after JWT verification, before routing

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (routeService.isPublicRoute(exchange.getRequest().getMethod(), path)) {
            return chain.filter(exchange);
        }
        return ReactiveJwtAccess.currentJwt()                          // libs/java-gateway
            .flatMap(jwt -> {
                // Read the ARRAY. `JwtClaims.role(jwt)` is the *header* value
                // (comma-joined, with the legacy singular fallback) — do not
                // admit on a joined string.
                List<String> roles = jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES);
                if (roles == null || !admits(path, roles)) {
                    return forbidden(exchange, "FORBIDDEN");          // 403 — not 401
                }
                return chain.filter(exchange);
            });
    }

    /** Closed, positive role set — per platform. Examples from the contract: */
    private boolean admits(String path, List<String> roles) {
        // ecommerce (path-based):
        //   /api/admin/**  -> requires an admin-family role (e.g. ECOMMERCE_OPERATOR / ADMIN)
        //   everything else -> requires a consumer role (e.g. CUSTOMER)
        // wms / erp / scm  -> require an operator role for that platform (e.g. WMS_OPERATOR)
        // fan              -> require a FAN-family role
        return roles.stream().anyMatch(requiredFor(path)::contains);
    }
}
```

**401 vs 403 is not cosmetic.** Missing/invalid token → `401` (authenticate). Valid token,
wrong role → **`403`** (authorization failed). Collapsing them hides the difference between
"log in" and "you may not be here".

---

## Rate-limit keying — `acct:<sub>` for authenticated traffic

`platform/api-gateway-policy.md`: **authenticated traffic keys on the authenticated principal
(`sub`), not the client IP.**

```java
@Configuration
public class RateLimiterConfig {

    /** Anonymous / pre-auth routes (login, signup): IP is CORRECT here — there is no principal yet. */
    @Bean
    public KeyResolver anonymousIpKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    /** Authenticated routes: key on the account. */
    @Bean
    @Primary
    public KeyResolver accountKeyResolver() {
        return exchange -> ReactiveJwtAccess.currentJwt()
            .map(jwt -> "acct:" + jwt.getSubject())
            // No principal (public/pre-auth route) → fall back to IP, which is correct there.
            .switchIfEmpty(Mono.defer(() -> anonymousIpKeyResolver().resolve(exchange)));
    }
}
```

- **IP-keying every route is not "simple", it is broken**: everyone behind one NAT shares a
  bucket, while an abuser rotating IPs is never throttled (TASK-MONO-368 / TASK-MONO-370 — this
  is not hypothetical; wms and ecommerce both shipped it).
- **A claim your own config pins to a constant is not a bucket.** ecommerce keyed on `tenant_id`
  — which is a *constant* for every shopper — so every authenticated shopper hashed into one
  bucket wearing the costume of per-tenant isolation. Verify a claim **varies** before keying on
  it (ecommerce now uses `t:<tenant>:acct:<sub>`).
- **Anonymous routes still key on IP** — that is the rule, not an exception to it. Over-correcting
  here means you can no longer throttle a login flood.

---

## Filter Ordering

| Order | Filter | Purpose |
|---|---|---|
| -110 | `IdentityHeaderStripFilter` (lib) | Remove spoofed `X-User-*` before anything reads them |
| -100 | `JwtHeaderEnrichmentFilter` (lib) | Verify (JWKS/RS256, `iss`, `aud`) + inject `X-User-*` |
| **-90** | **`RoleAdmissionFilter` (yours)** | **Rule 6 — `403` unless ≥ 1 valid role for the surface** |
| -50 | `RequestIdFilter` / logging (lib) | Correlation id, latency, error metrics |

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| **Reading `claims.get("role", String.class)`** | The claim is **`roles`, an array**. For admission read `jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES)`; for the `X-User-Role` header use `JwtClaims.role(jwt)` (comma-joined). A singular read returns `null` on a conforming token |
| **Authentication without admission** | A verified token is not an authorized one. Implement rule 6 → `403` |
| **`X-User-Role` set from a single role** | It is the **comma-separated `roles` array** |
| **IP-keyed rate limiting on authenticated routes** | Key on `acct:<sub>` (MONO-368/370) |
| **Keying on a claim your gateway pins to a constant** | That is one global bucket. Verify the claim varies |
| **Hand-rolling the JWT filter** | Use `libs/java-gateway` — 49 copies were deleted for a reason (ADR-MONO-049) |
| Not stripping spoof headers | Strip **before** validation, or a client sets its own identity |
| Public-route check after JWT validation | Check public routes first |
| Blocking calls in a reactive filter | Reactive operators only — no blocking I/O |
| Rate limiter that fails closed | Redis down must not 429 the fleet — `FailOpenRateLimiter` |
