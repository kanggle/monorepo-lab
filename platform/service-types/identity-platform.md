# Service Type: Identity Platform

Normative requirements for any service whose `Service Type` is `identity-platform`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

An `identity-platform` service issues and manages JWT tokens, exposes a JWKS endpoint, and is the authoritative source of authentication for one or more downstream platforms (gateways, REST APIs, frontends).

A single `identity-platform` deployment may serve multiple platforms (e.g., an e-commerce platform and a WMS platform) by scoping tokens via the `aud` claim. Each downstream gateway delegates token validation to the public keys exposed at the JWKS endpoint.

This service type is reserved for the central identity / OIDC / IAM service. There MUST be at most one `identity-platform` per project. General-purpose REST services that merely accept bearer tokens remain `rest-api`.

---

# When to Use

Use `identity-platform` for a service whose primary responsibility is **all of**:

- Issuing access tokens and refresh tokens signed with an asymmetric key pair (RSA or EdDSA)
- Publishing the corresponding public keys at a JWKS endpoint for relying parties
- Owning the lifecycle of user / operator accounts that authenticate against the platform
- Providing OIDC-style flows (Authorization Code + PKCE, refresh, revoke, introspect)

Do **not** use `identity-platform` for:

- A REST service that consumes tokens but does not issue them (`rest-api`)
- A user-profile or preferences service that holds business data about an already-authenticated user (`rest-api`)
- A session/audit projection that materializes login events into a read model (`event-consumer`)

---

# Core Responsibilities

The service is the single authoritative origin for the following concerns. No other service may take ownership of them:

1. **Token issuance** — minting access and refresh tokens, including signing, claim assembly, and `aud` scoping.
2. **Token validation primitives** — exposing the JWKS public keys and the optional introspection endpoint. Stateless validation by relying parties uses JWKS; stateful validation (revocation check) uses introspection.
3. **Account lifecycle** — registration, identity-proofing hooks, password / credential storage, account deactivation, and account-type assignment (CONSUMER vs OPERATOR).
4. **Session and refresh-token lifecycle** — refresh token rotation, family invalidation on suspected reuse, explicit logout / revocation.
5. **Key lifecycle** — generating, publishing, rotating, and retiring signing keys with grace periods.
6. **Audit of authentication events** — every login attempt, token issuance, token revocation, key rotation, and account change.

---

# Mandatory Endpoints

All endpoints are versioned (`/v1/...`) except the OIDC-discovery and JWKS endpoints, which follow well-known conventions.

| Method | Path | Purpose |
|---|---|---|
| GET | `/.well-known/jwks.json` | Public keys for stateless token validation by relying parties |
| GET | `/.well-known/openid-configuration` | OIDC discovery document referencing the endpoints below |
| GET | `/v1/oauth/authorize` | Authorization Code Flow entry point (PKCE required) |
| POST | `/v1/oauth/token` | Token issuance (authorization_code, refresh_token grant types) |
| POST | `/v1/oauth/revoke` | Revoke a refresh token (and the associated refresh-token family) |
| POST | `/v1/oauth/introspect` | Stateful introspection (returns `active`, claims, expiry) — internal callers only |
| POST | `/v1/oauth/logout` | End the current session and revoke its refresh-token family |
| GET | `/v1/accounts/me` | Authenticated account profile |
| POST | `/v1/accounts` | Create a CONSUMER account (OPERATOR creation is admin-only and lives elsewhere) |

The `password` grant type is forbidden. The `client_credentials` grant type is allowed only for internal service-to-service callers and MUST be scoped to a non-user `aud`.

---

# Token Lifecycle Rules

## Access Token

- Format: signed JWT (RS256 or stronger; HS256 forbidden for cross-service use)
- Lifetime: **5 to 15 minutes** depending on the requested surface's capability
  - operator-facing platforms (wms, erp, mes, scm, ecommerce admin surface): 5 minutes (short-lived; high-privilege)
  - consumer-facing surfaces (ecommerce customer, fan-platform): 15 minutes
- Required claims: `iss`, `sub`, `aud`, `iat`, `exp`, `kid` (in JOSE header), `jti`, `roles`, `scope`. (`account_type` is **deprecated** — migration-only per ADR-MONO-032; see the JWT Standard Claims contract § Migration Compatibility. `roles` is the authorization axis.)
- The `aud` claim MUST identify the target platform (e.g., `wms`, `ecommerce`). A token issued for one platform is invalid for another.
- Access tokens are NOT stored server-side. Validation is stateless via JWKS.

## Refresh Token

- Format: opaque, server-side stored (NOT a self-contained JWT). The platform MUST be able to revoke a single refresh token without rotating signing keys.
- Lifetime:
  - operator-facing surfaces: 8 hours maximum (short session)
  - consumer-facing surfaces: 30 days, sliding via rotation
- Refresh tokens MUST rotate on every use. The previous token in the rotation chain MUST be invalidated atomically with the issuance of the new one.
- **Reuse detection**: if a refresh token is presented after it has already been rotated, the entire refresh-token family MUST be revoked and the user re-prompted to authenticate. This event MUST be audited and alerted.

## Logout / Revocation

- Logout MUST revoke the refresh-token family. The access token will expire naturally within its short window; relying parties MAY additionally call `/introspect` for high-privilege endpoints.
- A revoked refresh token MUST NOT be re-issuable; reuse is treated as theft (see above).

---

# Key Management Rules

## Algorithm

- RSA 2048-bit minimum, or EdDSA (Ed25519). RSA 4096 recommended for OPERATOR-aud signing keys.
- The same key MAY be used to sign for multiple `aud` values, but operators MAY isolate keys per platform if blast-radius reduction is desired.

## Key Identifier (`kid`)

- Every signing key has a stable `kid`. The `kid` is included in the JWT JOSE header AND in the JWKS entry.
- Relying parties MUST select the verification key by `kid`, not by position in the JWKS array.

## Rotation

- Signing keys rotate on a scheduled cadence (recommended: every 90 days) and on-demand for incident response.
- Rotation procedure:
  1. Generate the new key. Add it to JWKS as a verification-only key. Do NOT sign with it yet.
  2. Wait until the JWKS cache TTL has elapsed across all relying parties (default: 1 hour).
  3. Promote the new key to active signing key. The previous key remains in JWKS as verification-only.
  4. **Grace period: 24 hours** during which both old and new keys appear in JWKS. This MUST exceed the maximum access-token lifetime.
  5. Remove the previous key from JWKS.

- Emergency rotation (suspected key compromise) MUST shorten the grace period to the maximum issued access-token lifetime and MUST revoke all refresh-token families.

## JWKS Cache

- Relying parties MUST cache the JWKS response. Recommended TTL: 1 hour.
- Relying parties MUST refresh JWKS on `kid`-not-found (with cache-bust + rate limit) before failing token validation.
- JWKS responses MUST be served with appropriate `Cache-Control` headers and MUST be reachable without authentication.

---

# Identity & Role Capability Rules

> **Unified identity model (ADR-MONO-032, ACCEPTED 2026-06-14).** The platform has a **single kind of account** — an identity (`sub`) authorized by a **set of roles**. There is no immutable account-type partition. The former CONSUMER/OPERATOR account types are now **role capabilities** an identity may hold, and one identity may hold **both**. The `account_type` claim is deprecated (migration-only); `roles` is the authorization axis. See the JWT Standard Claims contract § Identity Model.

## Consumer-facing capability

- Roles such as ecommerce `CUSTOMER`, fan-platform `FAN`/`PREMIUM_MEMBER`. Represent end users of the downstream platforms.
- MAY authenticate via social-login providers (Google, Apple, Kakao, etc.) in addition to local credentials.
- Long-lived sessions (refresh token up to 30 days, sliding) for consumer-facing surfaces.

## Operator-facing capability

- Roles such as `WMS_OPERATOR`, ecommerce `ADMIN`, `TENANT_ADMIN`, finance/support roles. Represent internal staff.
- For operator-facing surfaces: MUST authenticate with local credentials (or enterprise SSO via OIDC federation, NOT consumer-grade social login); MUST require multi-factor authentication for sensitive scopes; short-lived sessions (refresh 8h; access 5min); all authentication and high-privilege actions MUST be audited with elevated retention.

## One identity, both capabilities

- A single identity MAY hold both consumer-facing and operator-facing roles (e.g., a staff member who also shops on the e-commerce platform). This is **one account**, not two — the former "must provision separate accounts" rule is removed.
- Per-token least privilege is preserved by `aud`-scoping: each token is for one platform and carries only that platform's roles. Relying parties authorize by **role presence** for the requested surface (they check `roles`, not `account_type`).
- Operator-facing surfaces remain protected by their role requirement + MFA + RBAC/ABAC defense-in-depth; a consumer-facing token (different `aud`, consumer roles) cannot reach them.

---

# SSO Scope Rules

- SSO is scoped by **role possession on the target platform**. An identity MAY receive a token for any platform on which it holds ≥ 1 role, in the same browser session, subject to consent and `aud` scoping.
- There is **no cross-type SSO prohibition** (removed by ADR-MONO-032). The unified identity holds whatever capabilities its roles grant, and each `aud` token is independently `aud`-scoped to that platform's roles.
- A staff member who also shops uses **one account** and MAY hold both an operator-facing token (for their work platform) and a consumer-facing token (for the storefront) in one session — each token carries only its own platform's roles.

---

# Security Rules

## Authorization Code Flow

- PKCE (S256) is **required** for all `authorization_code` flows, including confidential clients.
- The `state` parameter is **required** and MUST be validated on callback to prevent CSRF.
- The `redirect_uri` MUST exactly match a pre-registered URI for the client; partial / wildcard matches are forbidden.
- Authorization codes are single-use, expire within 60 seconds, and are bound to the issuing client and `redirect_uri`.

## Credential Storage

- Passwords MUST be stored using a memory-hard KDF (Argon2id preferred; bcrypt cost ≥ 12 acceptable).
- Plaintext passwords MUST never be logged, never appear in audit events, and never be sent in error messages.

## Brute-Force and Enumeration Defenses

- Login endpoints MUST apply rate limiting per (account, IP) and per (IP) at the edge.
- After N consecutive failed attempts (default: 5 within 15 minutes), the account MUST be temporarily locked or required to solve a challenge (CAPTCHA / step-up MFA).
- Login responses MUST NOT distinguish "unknown user" from "wrong password" in the user-visible message.

## Transport

- All endpoints MUST be served over TLS. Plaintext is forbidden in any environment that holds real credentials.
- Refresh tokens issued to browsers MUST be set as `HttpOnly; Secure; SameSite=Strict` cookies, or held only by server-side BFFs.

## Token Theft Posture

- Refresh-token reuse triggers family revocation (see Token Lifecycle Rules).
- Suspicious-activity signals (new device, geo-velocity, high-risk IP) MAY downgrade session lifetime or force re-authentication. Document the policy in the service spec.

---

# Audit Requirements

Every event below MUST produce an immutable audit record. Audit records MUST be written through the outbox pattern so that they survive a transactional rollback boundary.

| Event | Required Fields |
|---|---|
| Login attempt (success and failure) | `accountId` (if known), `roles`, `clientId`, `ip`, `userAgent`, `outcome`, `failureReason` |
| Token issuance | `accountId`, `aud`, `scope`, `tokenType`, `kid`, `jti`, `expiresAt` |
| Token refresh | `accountId`, `previousJti`, `newJti`, `aud` |
| Refresh-token reuse detection | `accountId`, `familyId`, all `jti`s revoked, originating `ip`/`userAgent` |
| Token revocation | `accountId`, `jti`, `reason` |
| Account creation / modification / deactivation | `accountId`, `actorId`, `roles`, changed fields |
| Key rotation | `previousKid`, `newKid`, `rotationReason`, `actorId` |
| Admin action against an account (lock, unlock, force-logout) | `accountId`, `actorId`, `action`, `justification` |

Retention: CONSUMER audit ≥ 1 year; OPERATOR audit ≥ 3 years (or longer per applicable regulation).

Audit events MAY be projected by an `event-consumer` service for query / reporting; the projection MUST NOT be the source of truth.

---

# Integration Rules

## How Other Services Consume Tokens

Relying parties (typically `rest-api` services and the gateway) integrate with `identity-platform` via JWKS. They MUST NOT call internal endpoints to validate tokens on the hot path.

A relying party MUST:

1. Resolve the JWKS URL from the OIDC discovery document at startup.
2. Cache the JWKS response (recommended TTL: 1 hour) and refresh on `kid`-not-found.
3. Validate every incoming bearer token: signature (via `kid`), `iss`, `aud` (matches its own platform), `exp`, `nbf` (if present).
4. Authorize by **role presence** for the requested surface: admit iff `roles` contains a role valid for the route, otherwise reject (403). (During the ADR-MONO-032 migration window a relying party MAY accept legacy `account_type`-based OR role-based admission — see the JWT Standard Claims contract § Migration Compatibility. `account_type` is deprecated and MUST NOT be the sole gate once role-based admission is deployed.)
5. For high-privilege endpoints, OPTIONALLY call `/v1/oauth/introspect` to confirm the refresh-token family has not been revoked. Document this on a per-endpoint basis in the relying party's service spec.

## How the Gateway Integrates

- The `gateway-service` of each downstream platform performs JWT validation at the edge using JWKS.
- The gateway propagates the validated `accountId`, `roles`, and `scope` as trusted internal headers (e.g., `X-User-Id`, `X-User-Role`) to downstream services. Downstream services trust these headers ONLY when received via the gateway, never when received directly from the public internet. (`X-Account-Type` is no longer injected — the claim is deprecated per ADR-MONO-032; downstream services derive any consumer-vs-operator distinction from `X-User-Role`.)
- The gateway MUST NOT mint tokens. It is purely a relying party.

## Federated / External Identity Providers

- Social-login providers (Google, Apple, Kakao) federate **into** the `identity-platform`. The platform exchanges the provider's identity assertion for a platform-issued token. Provider-issued tokens MUST NOT leak past the federation boundary.
- Enterprise SSO (for OPERATOR) is handled via OIDC federation; the `identity-platform` is the relying party of the upstream IdP and the issuer of the platform's own tokens.

---

# Allowed Patterns

- Multiple `aud` values served by a single deployment, scoped by client registration
- Federation with external OIDC / social-login providers
- A small admin REST surface for account management (lives on the same service)
- Publishing audit events to Kafka via outbox for downstream projection

---

# Forbidden Patterns

- Issuing tokens whose `aud` is unbounded ("any platform") — every token MUST be scoped
- Self-contained refresh tokens (refresh tokens MUST be opaque and server-revocable)
- Symmetric signing (HS256) for tokens consumed by other services
- Embedding business-domain logic (orders, inventory, profile preferences) in this service
- Allowing CONSUMER tokens to satisfy OPERATOR-only routes by virtue of equal `sub`
- Returning user-enumeration signals from authentication endpoints
- Storing passwords with fast / non-memory-hard hashes (SHA-x, MD5, bcrypt cost < 12)
- Bypassing JWKS by hard-coding signing keys in relying parties
- Skipping PKCE for "trusted" first-party clients
- Logging tokens, authorization codes, refresh tokens, or password material

---

# Out of Scope

The following responsibilities do **not** belong on an `identity-platform` service. They belong to other services in the relevant downstream platform:

- Business-domain user data beyond authentication-relevant attributes (preferences, addresses, marketing consent → profile service)
- Authorization policy decisions tied to business resources (e.g., "can this operator approve this purchase order") — the `identity-platform` provides the identity and coarse role; fine-grained authorization lives in the resource-owning service or in a dedicated policy service
- Notification delivery (email verification, password-reset email) — the platform emits an event; a notification `event-consumer` delivers it
- Long-running risk / fraud analysis — the platform emits authentication events; a separate analytics service consumes them

---

# Testing Requirements

- Unit tests for token issuance (claims, lifetime, `aud`, `kid`), refresh rotation, refresh-reuse detection, and revocation.
- Contract tests for every endpoint in `specs/contracts/http/<service>-api.md` and the OIDC discovery document.
- Integration tests with Testcontainers covering: full Authorization Code + PKCE flow, refresh rotation, refresh-reuse triggers family revocation, JWKS reachable without auth, key rotation surfaces both `kid`s during grace.
- Cross-service contract test: a sample relying party fetches JWKS and validates a freshly issued token end-to-end.
- Negative tests: HS256 token rejected, mismatched `aud` rejected, expired token rejected, revoked refresh token rejected, social-login token NOT accepted as platform token.
- Audit assertions: every test that triggers an audited event verifies the audit record was written.

---

# Default Skill Set

When implementing or extending an `identity-platform` service:

`backend/springboot-api`, matched architecture skill, `backend/jwt-auth`, `backend/exception-handling`, `backend/validation`, `backend/dto-mapping`, `backend/transaction-handling`, `messaging/outbox-pattern`, `cross-cutting/api-versioning`, `cross-cutting/observability-setup`, `cross-cutting/security-hardening`, `backend/testing-backend`, `service-types/identity-platform-setup`

---

# Acceptance for a New Identity Platform Service

- [ ] `specs/contracts/http/<service>-api.md` covers all mandatory endpoints (token, refresh, revoke, introspect, JWKS, OIDC discovery, account)
- [ ] `specs/services/<service>/architecture.md` declares `Service Type: identity-platform`
- [ ] Asymmetric signing key pair generated; `kid` strategy documented
- [ ] JWKS endpoint reachable without auth, served with cache headers
- [ ] OIDC discovery document published and referenced by relying parties
- [ ] Access-token lifetimes match account-type policy (OPERATOR ≤ 5 min, CONSUMER ≤ 15 min)
- [ ] Refresh-token rotation + reuse-detection wired and tested
- [ ] Key rotation procedure documented with 24h grace period
- [ ] PKCE enforced; `state` validated; `redirect_uri` exact-match enforced
- [ ] Account-type isolation enforced (no cross-type SSO)
- [ ] Audit events emitted via outbox for every required event
- [ ] Brute-force / enumeration defenses in place and tested
- [ ] At least one relying party (gateway) validates tokens against JWKS end-to-end

---

# Change Rule

Changes to the mandatory requirements, allowed/forbidden patterns, or testing expectations for `identity-platform` services must be documented in this file before applying to existing services. New constraints affecting deployed services require an ADR (`docs/adr/` for monorepo-wide impact or `projects/<project>/docs/adr/` for project-scoped impact) per [`architecture-decision-rule.md`](../architecture-decision-rule.md).
