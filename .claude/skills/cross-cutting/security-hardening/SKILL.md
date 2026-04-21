---
name: security-hardening
description: OWASP Top 10 hardening checklist
category: cross-cutting
---

# Skill: Security Hardening

Cross-cutting checklist for hardening any service against common attack classes.

Prerequisite: read `specs/platform/security-rules.md` before using this skill. Auth-specific patterns live in `backend/jwt-auth/SKILL.md`, `backend/oauth-provider/SKILL.md`, `backend/gateway-security/SKILL.md`.

---

## OWASP Top 10 Mapping

| OWASP | Service Responsibility | Skill / Spec |
|---|---|---|
| A01 Broken Access Control | Enforce RBAC at controller + domain layer | `backend/gateway-security/SKILL.md` |
| A02 Cryptographic Failures | TLS in transit, AES-256 at rest, no plain secrets | this skill |
| A03 Injection | Parameterized queries, input sanitization | `backend/validation/SKILL.md` |
| A04 Insecure Design | Threat model per feature, deny-by-default | `architect` agent |
| A05 Security Misconfiguration | Hardened defaults, no debug in prod | this skill |
| A06 Vulnerable Components | Dependency scanning in CI | `infra/ci-cd/SKILL.md` |
| A07 Auth Failures | Lockout, MFA, secure session store | `backend/jwt-auth/SKILL.md`, `backend/redis-session/SKILL.md` |
| A08 Software/Data Integrity | Signed artifacts, SBOM | `infra/ci-cd/SKILL.md` |
| A09 Logging Failures | Structured logs, no PII leak | `cross-cutting/observability-setup/SKILL.md` |
| A10 SSRF | Egress allowlist, URL validation on user input | this skill |

---

## Secrets Management

- **Never** commit secrets, keys, tokens to git — pre-commit hook scans for high-entropy strings
- Production secrets live in **Vault / SOPS / Kubernetes Secret with KMS encryption**, never in `application.yml`
- Local dev uses `.env.local` (gitignored) or `@Profile("standalone")` placeholder values
- See `infra/secrets-management/SKILL.md` for storage and rotation patterns

```yaml
# Bad
spring.datasource.password: hunter2

# Good
spring.datasource.password: ${DB_PASSWORD}    # injected from K8s secret
```

---

## Transport Security

| Layer | Requirement |
|---|---|
| External traffic | TLS 1.3, HSTS enabled, HTTP redirects to HTTPS |
| Internal service-to-service | mTLS via service mesh (`infra/service-mesh/SKILL.md`) |
| Database | TLS required, certificate verification on |
| Kafka | SASL_SSL with SCRAM-SHA-512 |

---

## CORS

Default deny. Allow only what is needed.

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(List.of("https://app.example.com"));   // explicit, no wildcard with credentials
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
}
```

`*` is forbidden in production with `allowCredentials=true`.

---

## CSRF

- Stateless JWT APIs: CSRF protection disabled but **only if** all state-changing endpoints require `Authorization: Bearer` header
- Cookie-based sessions: CSRF token required (Spring Security default)
- Forms in admin dashboard: SameSite=Lax cookies + CSRF token

---

## Input Sanitization

- Validate at controller layer with `@Valid` (see `backend/validation/SKILL.md`)
- Reject inputs that fail format/length/range
- Sanitize free-text fields rendered in HTML (DOMPurify on FE, OWASP Java HTML Sanitizer on BE if BE renders HTML)
- File uploads: whitelist MIME types + virus scan + quarantine bucket

---

## Egress Controls (SSRF Defense)

When the service makes outbound HTTP based on user-controlled URLs:

- Validate scheme (http/https only)
- Resolve DNS server-side and reject private IP ranges (RFC1918, 169.254.0.0/16, ::1, fc00::/7)
- Use a proxy with allowlist for known external domains

---

## Dependency Scanning

CI must run:
- `trivy fs` or `grype` for OS/library CVEs
- `gradle dependencyCheck` (OWASP) for Java
- `npm audit --audit-level=high` for Node
- Build fails on Critical/High findings without documented exception

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `permitAll()` on actuator endpoints | Restrict to internal network or auth required |
| H2 console in prod | Forbidden — use real DB |
| Stack traces in error responses | Mask via `error-handling.md` |
| Wildcard CORS with credentials | Explicit origin list |
| Logging access tokens | Mask in logger configuration |
| Default admin password | Forced rotation on first login |

---

## Verification Checklist

- [ ] No secrets in git history (`gitleaks` clean)
- [ ] TLS enforced on all external endpoints
- [ ] CORS origins explicit, no wildcards with credentials
- [ ] Input validation at controller layer
- [ ] Dependency scan passes in CI
- [ ] Error responses do not leak stack traces or internal paths
- [ ] Actuator endpoints restricted
