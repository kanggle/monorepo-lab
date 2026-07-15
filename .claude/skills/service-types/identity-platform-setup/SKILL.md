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
5. **Token issuance** — `POST /v1/oauth/token` (Authorization Code + PKCE); build JWT with all mandatory claims (`sub`, `aud`, `roles`, `email`, `iss`, `iat`, `exp`, `jti`, `kid`) per `jwt-standard-claims.md`; each token is `aud`-scoped to one platform and carries only that platform's roles
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

- [ ] The identity holds ≥ 1 role on the requested `aud` platform (otherwise no token for that platform)
- [ ] The issued token is `aud`-scoped to one platform and carries only that platform's roles (per-token least privilege)
- [ ] Consumer-facing tokens (consumer roles, e.g. `CUSTOMER`/`FAN`): social login allowed, long-lived refresh token (up to 30 days, sliding)
- [ ] Operator-facing tokens (operator roles, e.g. `WMS_OPERATOR`/`ADMIN`): local credentials or OIDC federation only (no social login), MFA for sensitive scopes, short refresh token (8h), elevated audit retention
- [ ] A single identity holding both capabilities is **one account** — it may hold a consumer-facing and an operator-facing token concurrently; there is no cross-type rejection

---

## JWT Payload Construction Template

```java
Jwts.builder()
    .subject(account.getId().toString())          // sub
    .audience().add(aud).and()                    // aud (one platform per token)
    .claim("roles", resolveRoles(account, aud))   // roles[] — only this aud's roles
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

Custom **role-based** authorization (and any ABAC data-scope) is added as a reactive `GlobalFilter` (see the `backend/gateway-security` skill for the edge-gateway filter pattern). Authorize by **role presence** for the requested surface — relying parties check `roles`.

---

## Out of Scope

- User profile management (avatar, address, preferences) — belongs in the consuming platform's user-service
- Fine-grained resource authorization — belongs in domain services
- API key management for machine-to-machine — separate service type
- Notification delivery (welcome email, password reset) — use the platform's notification-service
