# Task ID

TASK-BE-465

# Title

`SasRefreshTokenAuthenticationProvider` reads resource-owner principal under the wrong attribute key → rotated access token carries `sub=client_id` (refresh drops account identity)

# Status

done

# Owner

backend

# Task Tags

- code
- test
- bug

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix a refresh-grant identity-loss bug in
[`SasRefreshTokenAuthenticationProvider`](../../apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/SasRefreshTokenAuthenticationProvider.java).

On the `refresh_token` grant the provider recovers the original resource-owner
`Authentication` to feed the token-generation context (so
`TenantClaimTokenCustomizer.alignSubToAccountId` can override `sub` = account UUID and
`populateRoles` can read the account's stored roles). It read that attribute under

```
org.springframework.security.core.Authentication.class.getName()
  == "org.springframework.security.core.Authentication"
```

but SAS (and the persisted `oauth2_authorization.attributes` blob) stores the principal
under

```
java.security.Principal.class.getName()  == "java.security.Principal"
```

— the exact key SAS's own `OAuth2AuthorizationCodeAuthenticationProvider` writes and the
built-in refresh provider reads. The lookup therefore **always** returned `null`, so every
refresh fell back to `clientPrincipal`. The rotated access token was minted with
`sub = <client_id>` and `roles = RoleSeedPolicy` default; the account_id / tenant / roles
carried on the stored principal's `details` map were lost.

Downstream, gateways bind `X-User-Id ← sub` as a `UUID` (ADR-MONO-040 Phase 3). A
`client_id` `sub` fails that bind, so **every authenticated call breaks ~5 min after login,
on the first token refresh** — reproduced live as ecommerce web-store
`GET /api/users/me` → user-service `400` ("프로필을 불러오는데 실패했습니다"). The defect
affects **all** OIDC consumers of this SAS (ecommerce, fan, erp, console), not just
ecommerce.

## Root-cause evidence (live fed-e2e stack, 2026-07-02)

- gateway access log: `/api/users/me` `200` while the authorization_code token was fresh,
  flipping to a steady `400` after the access token's TTL (~5 min) → first refresh.
- Decoded tokens for the same session:
  - authorization_code: `sub = 01928c4a-…-c500` (account UUID), `roles = ["ADMIN"]`.
  - refresh_token: `sub = "ecommerce-web-store-client"` (client_id), `roles = ["CUSTOMER"]` (seed).
- user-service probed directly: `X-User-Id` = UUID → 404 (binds), email/client_id → **400**
  (`MethodArgumentTypeMismatchException`), missing → 401.
- DB `oauth2_authorization.attributes` stores the principal under `java.security.Principal`
  as a `UsernamePasswordAuthenticationToken` whose `details` HashMap carries
  `account_id`/`tenant_id`/`tenant_type` — i.e. reading the correct key fully recovers the
  identity.

# Scope (in/out)

**In**

- One-line key correction in `SasRefreshTokenAuthenticationProvider.authenticate()`:
  read the principal under `java.security.Principal.class.getName()`.
- Regression unit test in `SasRefreshTokenAuthenticationProviderTest` asserting the rotated
  access token is generated from the stored resource-owner principal (account_id preserved),
  not the client principal.

**Out**

- No contract/spec change — the fix *restores* the existing
  `jwt-standard-claims.md` (`sub` = account UUID) contract; it does not alter it.
- No change to `TenantClaimTokenCustomizer`, the persistence layer, or the gateway.
- The pre-existing labelling of `authorization.getPrincipalName()` as `accountId` inside
  `persistRotation()` / event publishing (it is actually the login email) is a separate
  cosmetic concern, left untouched.

# Acceptance Criteria

- `SasRefreshTokenAuthenticationProvider` reads the resource-owner principal under
  `java.security.Principal.class.getName()`.
- On a `refresh_token` rotation with a stored resource-owner principal, the token-generation
  `OAuth2TokenContext.getPrincipal()` is that stored principal (carrying `account_id`), not
  the client principal → `sub` = account UUID and `roles` = stored account roles on the
  rotated access token.
- New regression test fails against the old key and passes with the fix.
- `:projects:iam-platform:apps:auth-service:test` green.

# Related Specs

- `projects/iam-platform/apps/auth-service/…` (identity-platform service type)
- ADR-MONO-040 Phase 3 (`X-User-Id ← sub`, `sub` = account UUID)
- `jwt-standard-claims.md` (§ Post-Validation Injection)

# Related Contracts

- No contract change. Restores conformance to `jwt-standard-claims.md`.

# Edge Cases

- Authorization with no stored principal (e.g. a client-only grant): the attribute is
  legitimately absent → the existing `principal != null ? principal : clientPrincipal`
  fallback still applies (unchanged behaviour). `alignSubToAccountId` then no-ops (no
  account_id) — net-zero, same as today for that path.
- Public (PKCE) and confidential refresh both route through this provider → both fixed.

# Failure Scenarios

- If a future SAS upgrade changes the principal attribute key, the regression test (which
  stores under `java.security.Principal`) is the guard; the built-in providers use the same
  key, so drift would surface framework-wide.
