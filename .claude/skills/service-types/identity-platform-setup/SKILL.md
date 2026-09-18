---
name: identity-platform-setup
description: Set up an `identity-platform` service end-to-end
category: service-types
---

# Skill: Identity Platform Service Setup

Implementation orchestration for an `identity-platform` service. Composes existing skills into a setup workflow.

Prerequisite: read `platform/service-types/identity-platform.md` and `platform/contracts/jwt-standard-claims.md` before using this skill. This skill is the orchestration layer; concrete patterns live in the referenced skills.

---

## Orchestration Order

1. **Contract** — establish `platform/contracts/jwt-standard-claims.md` as the authoritative token contract (already exists for this monorepo); note the `aud` values and the role capabilities (consumer-facing / operator-facing) your instance must support
2. **Architecture style** — Hexagonal (ports & adapters) is mandatory; declare in `specs/services/<service>/architecture.md`
3. **Key Management bootstrap** — generate RSA key pair; configure JWKS endpoint (`GET /.well-known/jwks.json`); see `backend/jwt-auth/SKILL.md` for RS256 library setup
4. **Account domain** — model `Account` aggregate with `email`, `status`, and a **roles** collection (roles are the sole identity axis — ADR-MONO-032); a single account MAY hold both consumer-facing roles (e.g. `CUSTOMER`, `FAN`) and operator-facing roles (e.g. `WMS_OPERATOR`, `ADMIN`). Persistence via JPA
5. **Token issuance** — `POST /v1/oauth/token` (Authorization Code + PKCE); build JWT with all mandatory claims (`sub`, `aud`, `roles`, `email`, `iss`, `iat`, `exp`, `jti`, `kid`) per `jwt-standard-claims.md`; `aud` is the requesting **client id** (not a platform name) and the token carries only that client's platform's roles
6. **Refresh token** — opaque token stored server-side (DB or Redis); rotation policy. **There is no separate refresh path**: the *same* `POST /v1/oauth/token` endpoint serves **both** grant types (`authorization_code` and `refresh_token`), selected by the `grant_type` parameter — per `platform/service-types/identity-platform.md` § Endpoints. Rate-limit it on `acct:<sub>` (authenticated), not on IP
7. **Token revocation** — `POST /v1/oauth/revoke` (**not** `/oauth/token/revoke`); revokes the refresh token and its family; short-lived access tokens expire naturally. Session teardown is `POST /v1/oauth/logout`; stateful introspection is `POST /v1/oauth/introspect` (internal callers only)
8. **JWKS endpoint** — `GET /.well-known/jwks.json`; serve current + grace-period keys; cache-control headers aligned to spec (1h max-age)
9. **Social login adapters** — for the **consumer-facing capability** only (identities authenticating for consumer roles); OAuth2 callback handlers (Google, Naver, Kakao, etc.); use `backend/gateway-security/SKILL.md` for callback verification. Operator-facing surfaces use local credentials / enterprise OIDC federation + MFA, never consumer-grade social login
10. **SSO scope enforcement** — scoped by **role possession on the target platform**: an identity MAY receive a token for any platform on which it holds ≥ 1 role (subject to consent + `aud` scoping). There is **no cross-type prohibition** (removed by ADR-MONO-032) — an identity holding both consumer-facing and operator-facing roles may obtain a consumer-facing token (`aud` in `{ecommerce, fan}`) and an operator-facing token (`aud` in `{wms, erp, mes, scm, ecommerce-admin}`) in the same session, each carrying only that platform's roles
11. **Key rotation** — `kid` versioning; 24h grace period; scheduled rotation job
12. **Audit logging** — outbox-based audit events for every login attempt, token issuance, token revocation, account change; see `backend/observability-metrics/SKILL.md`
13. **Rate limiting + brute force defense** — `POST /v1/oauth/token` must enforce account lockout. Key **pre-auth** attempts (login / signup) on client **IP**, and the **`refresh_token` grant** on **`acct:<sub>`** (it carries a principal) — see `backend/rate-limiting/SKILL.md` and `platform/api-gateway-policy.md` § Rate Limiting; also `cross-cutting/observability-setup/SKILL.md`
14. **Error handling** — `backend/exception-handling/SKILL.md`; map OAuth2 error codes (`invalid_grant`, `invalid_client`, `unauthorized_client`, etc.)
15. **Tests** — `backend/testing-backend/SKILL.md`; unit tests for token construction, key rotation, SSO scope enforcement; slice tests for token endpoint; integration tests for full PKCE flow

---

## Token-Issuance Capability Checklist

Before issuing a token, verify (capability is derived from **roles** — ADR-MONO-032):

- [ ] The identity holds ≥ 1 role on the platform of the **requesting client** (otherwise no token for that platform)
- [ ] The issued token carries only that platform's roles (per-token least privilege)
- [ ] `aud` is the **client id** of the registered client the token was issued to — **not** a platform name. The two are easy to conflate because one client belongs to one platform, so "scope the roles by platform" and "write the platform into `aud`" *sound* like the same instruction. They are not, and a token minted the second way is rejected by every edge that applies rule 5 (see [`jwt-standard-claims.md`](../../../../platform/contracts/jwt-standard-claims.md) § Standard Claims `aud`).
- [ ] Consumer-facing tokens (consumer roles, e.g. `CUSTOMER`/`FAN`): social login allowed, long-lived refresh token (up to 30 days, sliding)
- [ ] Operator-facing tokens (operator roles, e.g. `WMS_OPERATOR`/`ADMIN`): local credentials or OIDC federation only (no social login), MFA for sensitive scopes, short refresh token (8h), elevated audit retention
- [ ] A single identity holding both capabilities is **one account** — it may hold a consumer-facing and an operator-facing token concurrently; there is no cross-type rejection

---

## JWT Payload Construction Template

```java
Jwts.builder()
    .subject(account.getId().toString())          // sub
    .audience().add(clientId).and()               // aud — the requesting client's id, never a platform name
    .claim("roles", resolveRoles(account, platformOf(clientId)))  // roles[] — only that platform's roles
    .claim("tenant_id", tenantId)                 // REQUIRED on every grant — edge-enforced (TenantClaimValidator)
    .claim("tenant_type", tenantType)             // REQUIRED, always minted alongside tenant_id
    .claim("email", account.getEmail())
    .issuer(issuerUri)                            // iss
    .issuedAt(now)
    .expiration(now + accessTokenTtl)
    .claim("jti", UUID.randomUUID().toString())
    .header().keyId(currentKeyId).and()           // kid
    .signWith(privateKey, Jwts.SIG.RS256)
    .compact();
```

---

## Gateway Integration Pattern

Consuming gateways validate tokens as Spring Security OAuth2 Resource Server:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${IDENTITY_JWKS_URI}
```

🔴 **This block deliberately configures no audience property.** An earlier version of this skill
put a `spring…jwt.audiences` line here with the example value `wms`, and both halves of it were
wrong:

🔵 (The removed line is described rather than quoted on purpose: a census that greps this tree for
the dead property must not match the very paragraph that documents its removal — the discriminator
would trip over its own explanation.)

- The **property was dead**. These gateways build their own decoder bean, and
  `spring.security.oauth2.resourceserver.jwt.audiences` configures the auto-configured one they
  never use — so the value was read by nobody while looking, in review and in audit, exactly like a
  configured audience check (`TASK-MONO-696`).
- The **example value was a platform name**. `aud` is a client id (above).

A new gateway declares its audience allowlist where it assembles its decoder, per
[`jwt-standard-claims.md`](../../../../platform/contracts/jwt-standard-claims.md) § JWT Validation
rule 5 — read the rule there rather than a copy here, because a copy is what goes stale when the
rule is next revised.

Custom **role-based** authorization (and any ABAC data-scope) is added as a reactive `GlobalFilter` (see the `backend/gateway-security` skill for the edge-gateway filter pattern). Authorize by **role presence** for the requested surface — relying parties check `roles`.

---

## Out of Scope

- User profile management (avatar, address, preferences) — belongs in the consuming platform's user-service
- Fine-grained resource authorization — belongs in domain services
- API key management for machine-to-machine — separate service type
- Notification delivery (welcome email, password reset) — use the platform's notification-service
