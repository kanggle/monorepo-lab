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

1. **Contract** — establish `platform/contracts/jwt-standard-claims.md` as the authoritative token contract (already exists for this monorepo); note the `aud` values and account types your instance must support
2. **Architecture style** — Hexagonal (ports & adapters) is mandatory; declare in `specs/services/<service>/architecture.md`
3. **Key Management bootstrap** — generate RSA key pair; configure JWKS endpoint (`GET /.well-known/jwks.json`); see `backend/jwt-auth/SKILL.md` for RS256 library setup
4. **Account domain** — model `Account` aggregate with `account_type (CONSUMER | OPERATOR)`, `email`, `status`; persistence via JPA
5. **Token issuance** — `POST /oauth/token` (Authorization Code + PKCE); build JWT with all mandatory claims (`sub`, `account_type`, `aud`, `roles`, `email`, `iss`, `iat`, `exp`, `jti`, `kid`)
6. **Refresh token** — opaque token stored server-side (DB or Redis); `POST /oauth/token/refresh`; implement rotation policy
7. **Token revocation** — `POST /oauth/token/revoke`; mark refresh token as revoked; short-lived access tokens expire naturally
8. **JWKS endpoint** — `GET /.well-known/jwks.json`; serve current + grace-period keys; cache-control headers aligned to spec (1h max-age)
9. **Social login adapters** — CONSUMER only; OAuth2 callback handlers (Google, Naver, Kakao, etc.); use `backend/gateway-security/SKILL.md` for callback verification
10. **SSO scope enforcement** — CONSUMER tokens: `aud` in `{ecommerce, fan}`; OPERATOR tokens: `aud` in `{wms, erp, mes, scm, ecommerce (admin)}`; reject cross-type token requests at issuance time
11. **Key rotation** — `kid` versioning; 24h grace period; scheduled rotation job
12. **Audit logging** — outbox-based audit events for every login attempt, token issuance, token revocation, account change; see `backend/observability-metrics/SKILL.md`
13. **Rate limiting + brute force defense** — `POST /oauth/token` and `POST /auth/login` must enforce account lockout; see `cross-cutting/observability-setup/SKILL.md`
14. **Error handling** — `backend/exception-handling/SKILL.md`; map OAuth2 error codes (`invalid_grant`, `invalid_client`, `unauthorized_client`, etc.)
15. **Tests** — `backend/testing-backend/SKILL.md`; unit tests for token construction, key rotation, SSO scope enforcement; slice tests for token endpoint; integration tests for full PKCE flow

---

## Account Type Enforcement Checklist

Before issuing a token, verify:

- [ ] Requested `aud` is in the allowed set for the account's `account_type`
- [ ] CONSUMER accounts: social login allowed, long-lived refresh token (up to 30 days)
- [ ] OPERATOR accounts: social login NOT allowed, short refresh token (8h)
- [ ] Cross-type token request → `unauthorized_client` error (HTTP 400)

---

## JWT Payload Construction Template

```java
Jwts.builder()
    .subject(account.getId().toString())          // sub
    .claim("account_type", account.getAccountType().name())
    .audience().add(aud).and()                    // aud
    .claim("roles", resolveRoles(account, aud))   // roles[]
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
          audiences: ${GATEWAY_AUDIENCE}   # e.g., wms
```

Custom `account_type` enforcement is added as a reactive `GlobalFilter` (see WMS gateway pattern in `projects/wms-platform/apps/gateway-service`).

---

## Out of Scope

- User profile management (avatar, address, preferences) — belongs in the consuming platform's user-service
- Fine-grained resource authorization — belongs in domain services
- API key management for machine-to-machine — separate service type
- Notification delivery (welcome email, password reset) — use the platform's notification-service
