# JWT Standard Claims Contract

This contract defines the standard JWT structure, claims, and validation rules that all platform gateways and services must follow when processing tokens issued by the identity-platform service. It enables:

- Unified authentication across multiple platforms
- Consistent role-based authorization within each platform
- Single Sign-On (SSO) across every platform an identity is entitled to
- Cryptographic verification via asymmetric signing (RSA)

> **Unified identity model (ADR-MONO-032, ACCEPTED 2026-06-14).** This contract was rewritten to remove the former `account_type` CONSUMER/OPERATOR partition. There is **one account = one identity**, authorized by a **set of roles**. A single identity MAY simultaneously hold consumer-facing roles (`CUSTOMER`, `FAN`) and operator-facing roles (`WMS_OPERATOR`, …) — the same person can be both a customer and an operator without provisioning separate accounts. This supersedes the partition decided by ADR-MONO-021 (now SUPERSEDED). The `roles` set is the **sole authorization axis**.
>
> **`account_type` removed (TASK-MONO-263 / ADR-MONO-035 4b-2b / ADR-032 D5 step 4, 2026-06-14).** The migration is complete: the IdP no longer emits the `account_type` claim, the `auth_db.credentials.account_type` column is dropped, and gateways gate on `roles` only. The `account_type` claim is **no longer part of any token**. The § Migration Compatibility table below is retained as a historical record of the staged rollout.

---

# Identity Model

There is a single kind of account: an **identity** (globally unique `sub`), authorized by a **set of platform-scoped roles**.

- **No account-type partition.** An identity is not classified as "consumer" or "operator" at the account level. Capability is expressed entirely by the roles the identity holds.
- **Roles carry capability.** Some roles are *consumer-facing* (e.g. ecommerce `CUSTOMER`, fan-platform `FAN`/`PREMIUM_MEMBER`); others are *operator-facing* (e.g. `WMS_OPERATOR`, `SCM_OPERATOR`, ecommerce `ADMIN`). "Consumer-facing" vs "operator-facing" describes the *role*, not the *person*.
- **One identity may hold both.** A marketplace MD who also shops, a seller who also buys, a warehouse lead who is also a fan member — each is **one account** holding both consumer-facing and operator-facing roles. (Under the former model these required two separate accounts; that constraint is removed.)
- **Per-token least privilege is preserved.** A token is always for exactly one platform (`aud`) and carries only that platform's roles (§ Role Strategy). The "one person, both capabilities" fact lives in the identity's *role grants*; any single token stays narrowly scoped.

| Capability family | Example roles | Target platforms |
|---|---|---|
| Consumer-facing | `CUSTOMER` (ecommerce), `FAN` / `PREMIUM_MEMBER` (fan-platform) | ecommerce (customer surface), fan-platform |
| Operator-facing | `WMS_OPERATOR`, `OUTBOUND_MANAGER`, `SCM_OPERATOR`, `BUYER`, `ADMIN`, `TENANT_ADMIN`, … | wms, erp, mes, scm, ecommerce (admin surface) |

Self-service signup grants consumer-facing roles; operator/admin provisioning grants operator-facing roles onto the same identity model (no separate account type). Role assignment and delegation follow the RBAC / assignment / ABAC decisions (ADR-MONO-002 lineage, ADR-MONO-020, ADR-MONO-024, ADR-MONO-025, ADR-MONO-026), unchanged.

---

# JWT Signing Strategy

- **Algorithm:** RSA asymmetric (public/private key pair)
- **Key Management:**
  - The identity-platform service holds the private key (signing)
  - All platform gateways fetch the public key via JWKS endpoint (verification)
  - Keys may be rotated; gateways must implement key versioning via `kid` (key ID)
- **Token Lifetime:**
  - Access tokens: short-lived (5–15 minutes typical)
  - Refresh tokens: longer-lived, stored server-side, not validated as JWTs
- **Audience Scoping:** Each access token carries a platform-specific `aud` claim; gateways reject tokens with mismatched `aud`

---

# Standard Claims

All access tokens issued by the identity-platform service MUST include the following claims:

| Claim | Type | Required | Description | Example |
|---|---|---|---|---|
| `sub` | UUID string | Yes | Account ID (globally unique, immutable across all platforms). **Known deviation (ADR-MONO-040):** the live SAS OIDC path currently emits the login **email** here, not the UUID; Phase 2 restores `sub` = account UUID. Until then, consumer-facing services read the account UUID from `account_id` (below). | `550e8400-e29b-41d4-a716-446655440000` |
| `account_id` | UUID string | Conditional | **ADR-MONO-040 Phase 1** transitional claim: the account UUID, emitted additively on SAS access tokens while `sub` carries the email. Consumer-facing services derive `X-User-Id` from this claim (fallback `sub`). Retired in Phase 2 when `sub` itself is the account UUID. | `550e8400-e29b-41d4-a716-446655440000` |
| `aud` | string | Yes | Target platform audience — must match gateway's own platform | `ecommerce`, `fan`, `wms`, `erp`, `mes`, `scm` |
| `roles` | string[] | Yes | Platform-scoped roles for the `aud` platform — **the sole authorization axis** (may span consumer-facing and operator-facing roles; may be empty, minimum `[]`) | `["CUSTOMER"]`, `["CUSTOMER","ADMIN"]`, `["WMS_OPERATOR","OUTBOUND_MANAGER"]` |
| `email` | string | Yes | Account email address | `user@example.com` |
| `iss` | string | Yes | Issuer URI of the identity-platform service | `https://account.example.com` |
| `iat` | number | Yes | Issued at (Unix epoch seconds) | `1746000000` |
| `exp` | number | Yes | Expiry time (Unix epoch seconds) — gateways must reject expired tokens | `1746003600` |
| `jti` | string | Recommended | JWT ID (unique token identifier for revocation and audit) | UUID |
| `kid` | string | Recommended | Key ID (version identifier for key rotation) | `key-v1` |

> **`account_type` — REMOVED (TASK-MONO-263, D5 step 4).** The former `account_type` (`CONSUMER`\|`OPERATOR`) claim is no longer emitted on any token and is no longer part of this contract. It is not in the standard-claims table above. Any token observed carrying it is legacy; gateways MUST ignore it. `roles` is the sole authorization axis.

Additional custom claims MAY be added by the identity service but MUST NOT conflict with standard OIDC claims. Gateways that do not recognize a claim MUST ignore it.

---

# Role Strategy

Roles are platform-scoped and define authorization within the target `aud` platform. **`roles` is the only authorization axis** — there is no account-type gate above it.

- **A token carries only its `aud` platform's roles** (aud-scoped). A `wms` token carries the identity's wms roles; an `ecommerce` token carries its ecommerce roles. Roles are **not** flattened across platforms into a single token — this preserves per-token least privilege.
- **An identity may hold roles on multiple platforms** (e.g. `wms` + `scm`), and may hold **both consumer-facing and operator-facing roles** — on the same platform (ecommerce `["CUSTOMER","ADMIN"]`) or across platforms (ecommerce `CUSTOMER` + wms `WMS_OPERATOR`).
- **Multiple roles per platform are supported for every platform** (the former "CONSUMER single-role" restriction is removed). A consumer surface typically issues one role (`CUSTOMER`) but the model does not forbid more.
- Platform administrators define and assign roles; the identity service does not prescribe a role catalog per platform. Examples:
  - WMS: `["WMS_OPERATOR", "OUTBOUND_MANAGER"]`
  - SCM: `["SCM_OPERATOR", "BUYER"]`
  - ecommerce consumer surface: `["CUSTOMER"]`
  - ecommerce admin surface: `["ADMIN"]`
  - ecommerce, an account that both shops and administers: `["CUSTOMER", "ADMIN"]` (gateway path-routes on role — see § Gateway Enforcement)
  - fan-platform: `["FAN"]`, or `["FAN", "PREMIUM_MEMBER"]` when an active membership subscription exists

---

# Single Sign-On (SSO) Scope

SSO is scoped by **role possession on the target platform**, not by account type:

- An identity may request an access token for **any platform (`aud`) on which it holds ≥ 1 role**, without re-entering credentials.
- A single login therefore spans every entitled platform — consumer-facing and operator-facing alike. The same identity can hold a `CUSTOMER` token for ecommerce and a `WMS_OPERATOR` token for wms in one session.
- **No cross-type restriction.** The former rule that consumer and operator surfaces could never share a session is removed — the unified identity holds both capabilities, and each `aud` token is independently scoped to that platform's roles.

---

# Gateway Enforcement Rules

Every platform gateway MUST implement the following validation and injection logic.

## Pre-Validation Cleanup

1. **Strip all identity-related headers** from the incoming HTTP request before JWT validation:
   - `Authorization`, `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Account-Type`
   - Any custom headers the identity service may inject
   - This prevents client-side spoofing

## JWT Validation

2. **Fetch and verify the signature** using the public key from the identity service's JWKS endpoint:
   - Construct the JWKS URL from the `iss` claim: `${iss}/.well-known/jwks.json`
   - Match the `kid` in the JWT header to a key in the JWKS response
   - Verify the signature; reject on failure

3. **Validate expiry:** Reject if current time > `exp`

4. **Validate issuer:** Reject if `iss` does not match the expected identity service URI (e.g., `https://account.example.com`)

5. **Validate audience:** Reject if `aud` does not match the gateway's own platform identifier

6. **Validate authorization (role-based admission):** Admit iff the token carries ≥ 1 role valid for the requested surface; otherwise respond `403 Forbidden`. Authorization is a positive check against a closed role set.
   - **fan gateway:** require a FAN-family role (e.g. `FAN`)
   - **ecommerce gateway (path-based):**
     - `/api/admin/**` paths: require an admin-family role (e.g. `ADMIN`)
     - All other paths: require a consumer role (e.g. `CUSTOMER`)
   - **wms, erp, mes, scm gateways:** require an operator role for that platform (e.g. `WMS_OPERATOR`)

   This preserves the isolation that matters: a `CUSTOMER`-only token still fails the `/api/admin/**` role check, and a consumer-surface token never carries operator roles for a different `aud` (different token, § Role Strategy). Defense-in-depth (RBAC, ABAC data scope, access conditions) is unchanged and remains the primary gate on sensitive surfaces.

   **Roles-only (end state).** The ADR-MONO-032 D5 migration is complete (TASK-MONO-263 / D5 step 4): issuance is roles-only and the `account_type` claim is dropped. Gateways gate on `roles` only and **ignore `account_type` if seen** on a legacy token. The dual-read window (§ Migration Compatibility) is historical.

## Post-Validation Injection

7. **Inject standard headers** into the request context for downstream services:
   - `X-User-Id` ← `account_id` claim when present, else `sub` (**ADR-MONO-040 Phase 1**: the SAS `sub` is currently the login email, so the account UUID is sourced from `account_id`; Phase 2 makes `sub` the UUID and restores `X-User-Id` ← `sub`)
   - `X-User-Role` ← comma-separated `roles` array (e.g., `WMS_OPERATOR,OUTBOUND_MANAGER`)
   - `X-User-Email` ← `email`
   - `X-Account-Type` is **no longer injected** (the claim is deprecated). Downstream services that need a consumer-vs-operator distinction derive it from `X-User-Role` (the presence of a consumer-facing vs operator-facing role).

## Error Handling

- Invalid or missing JWT: respond with HTTP 401 Unauthorized
- Expired token: respond with HTTP 401 Unauthorized
- Signature mismatch: respond with HTTP 401 Unauthorized
- Wrong `aud`, or no role valid for the requested surface: respond with HTTP 403 Forbidden (authenticated but not authorized for this surface)
- JWKS endpoint unreachable: log error and respond with HTTP 503 Service Unavailable (do not fall back to cached keys older than 1 hour)

---

# Migration Compatibility

The move from the `account_type` partition to roles-only followed the ADR-MONO-032 D5 staged migration. **The migration is complete** (all stages landed, TASK-MONO-263 = D5 step 4). This table is retained as a historical record of the staged rollout:

| Stage | Issuance | Gateways | `account_type` | Status |
|---|---|---|---|---|
| **Dual-read** (D5 step 1) | still emits `account_type` (legacy) | accept **legacy account-type OR role-based** admission (whichever passes) | present (legacy) | done |
| **Roles-only issuance** (D5 step 2) | stops requiring `account_type`; emits roles-only; one identity may obtain a token for any `aud` it holds roles for; consumer capability seeded as `CUSTOMER`/`FAN` roles | role-based admission (dual-read leg still tolerant) | optional/absent | done |
| **Account unify** (D5 step 3) | one account = one credential = role-grant set; existing separate accounts opt-in-linked (not force-merged) | role-based | optional/absent | done |
| **Drop legacy** (D5 step 4, TASK-MONO-263) | `account_type` removed entirely; `auth_db.credentials.account_type` column dropped | role-based only; ignore `account_type` if seen | **removed** | **done** |

The end state is roles-only: `account_type` is not emitted on any token. Gateways gate on `roles` only and ignore `account_type` if seen on a legacy token.

---

# JWKS Endpoint Convention

The identity service MUST expose public keys in OIDC-compliant JWKS format:

**Endpoint:** `GET ${iss}/.well-known/jwks.json`

**Response Format:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "key-v1",
      "n": "...",
      "e": "AQAB",
      "alg": "RS256"
    }
  ]
}
```

Gateways SHOULD cache this endpoint for up to 1 hour and refresh on-demand if a token contains an unknown `kid`.

---

# Token Examples

## Example 1: ecommerce consumer

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "aud": "ecommerce",
  "roles": ["CUSTOMER"],
  "email": "shopper@example.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746003600,
  "jti": "token-uuid-1",
  "kid": "key-v1"
}
```

**Gateway Behavior (ecommerce, `/api/products` path):**
- Validate signature, expiry, issuer, `aud = "ecommerce"` ✓
- Path is not `/api/admin/**` → require a consumer role; `roles` contains `CUSTOMER` ✓
- Inject: `X-User-Id: 550e8400-…`, `X-User-Role: CUSTOMER`, `X-User-Email: shopper@example.com`

## Example 2: fan-platform consumer with Premium Membership

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "aud": "fan",
  "roles": ["FAN", "PREMIUM_MEMBER"],
  "email": "shopper@example.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746003600,
  "jti": "token-uuid-2",
  "kid": "key-v1"
}
```

**Gateway Behavior (fan-platform):**
- Validate signature, expiry, issuer, `aud = "fan"`; require a FAN-family role; `roles` contains `FAN` ✓
- Inject: `X-User-Role: FAN,PREMIUM_MEMBER`. Services may check for `PREMIUM_MEMBER` to enable premium features.

## Example 3: WMS operator with multiple roles

```json
{
  "sub": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "aud": "wms",
  "roles": ["WMS_OPERATOR", "OUTBOUND_MANAGER"],
  "email": "warehouse-lead@company.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746001800,
  "jti": "token-uuid-3",
  "kid": "key-v1"
}
```

**Gateway Behavior (WMS):**
- Validate signature, expiry, issuer, `aud = "wms"`; require an operator role; `roles` contains `WMS_OPERATOR` ✓
- Inject: `X-User-Role: WMS_OPERATOR,OUTBOUND_MANAGER`. Services may check for `OUTBOUND_MANAGER` to enable outbound-specific operations.

## Example 4: dual-capability identity (same person is both a customer and an admin)

The unified model's defining case: **one account** holds both a consumer-facing and an operator-facing role. Each `aud` token is still scoped to that platform's roles.

```json
// token for aud=ecommerce — the account both shops and administers
{
  "sub": "7f3d2c1b-0000-4abc-8def-111122223333",
  "aud": "ecommerce",
  "roles": ["CUSTOMER", "ADMIN"],
  "email": "md-who-also-shops@company.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746001800,
  "jti": "token-uuid-4",
  "kid": "key-v1"
}
```

**Gateway Behavior (ecommerce):**
- `/api/products` (consumer path) → requires a consumer role; `roles` contains `CUSTOMER` ✓ → admitted
- `/api/admin/products` (admin path) → requires an admin-family role; `roles` contains `ADMIN` ✓ → admitted
- The same identity is authorized on both surfaces from one token, yet each path independently checks the role it requires. Under the former model this required two separate accounts.

## Example 5: invalid token (wrong audience)

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "aud": "wms",
  "roles": ["CUSTOMER"],
  "email": "shopper@example.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746003600
}
```

**Gateway Behavior (ecommerce):**
- Validate `aud = "wms"` against expected `"ecommerce"` ✗ → HTTP 403 Forbidden (token is for a different platform)

## Example 6: invalid token (no role for the requested surface)

```json
{
  "sub": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "aud": "ecommerce",
  "roles": ["CUSTOMER"],
  "email": "shopper@example.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746003600,
  "jti": "token-uuid-6",
  "kid": "key-v1"
}
```

**Gateway Behavior (ecommerce — `/api/admin/products` path):**
- Validate signature, expiry, issuer, `aud = "ecommerce"` ✓
- Path `/api/admin/**` → require an admin-family role; `roles` is `["CUSTOMER"]`, no admin role ✗
- Respond: HTTP 403 Forbidden — this token lacks an admin role for the admin surface (the account would need an `ADMIN` grant; see Example 4).

---

# Implementation Notes

- **Graceful Key Rotation:** When a new key version is deployed, the old key remains valid for 24 hours to allow tokens signed with the old key to complete in-flight requests.
- **Clock Skew:** Gateways SHOULD allow up to 60 seconds of clock skew when validating `iat` and `exp` to tolerate minor time synchronization issues.
- **Audit Logging:** Log all validation failures (signature, expiry, authorization, audience) with the `jti` claim for audit trails.
- **Token Refresh:** Access tokens are not refreshed in-place; clients must use the refresh token endpoint to obtain a new access token.

---

# Change Rule

Any change to the JWT structure, standard claims, signing strategy, or gateway enforcement rules defined in this contract — new claim, claim type change, signature algorithm change, validation rule, header injection — must be documented in this file **before** any project's identity-platform service emits the change or any project's gateway enforces it. Breaking changes (claim rename, claim removal, validation tightening) require a coordinated rollout across all consuming projects (each project's gateway + downstream services), and the contract update MUST precede the implementation PR.

**Change log:**

- **2026-06-14 (ADR-MONO-035 4b-2b / ADR-MONO-032 D5 step 4, TASK-MONO-263) — `account_type` removed (breaking, finalizes the migration).** The IdP no longer emits the `account_type` claim on any grant; the `auth_db.credentials.account_type` column is dropped (auth-service V0025); `RoleSeedPolicy` seeds consumer roles by platform only (`ecommerce → CUSTOMER`, `fan-platform → FAN`); the credential-provisioning API (`auth-internal.md`) carries no `accountType`. Operators get domain roles at assume-tenant (OperatorRoleDerivation, BE-376); consumers carry their platform seed role. Gateways gate on `roles` only and ignore `account_type` if seen on a legacy token. Completes the ADR-032 D5 staged migration (§ Migration Compatibility).
- **2026-06-14 (ADR-MONO-032, TASK-MONO-255) — unified identity model (breaking).** Removed the `account_type` CONSUMER/OPERATOR account-level partition; `roles` is now the sole authorization axis (one identity may hold consumer-facing + operator-facing roles). `account_type` demoted from Required to Deprecated (migration-only, removed at completion). Gateway enforcement changed from account-type partition to role-based admission; `X-Account-Type` injection removed. Cross-type SSO lifted (SSO scoped by role-possession). The coordinated rollout is the ADR-032 D5 staged migration (§ Migration Compatibility): dual-read gateways → roles-only issuance → account unify → drop legacy → e2e. Supersedes the `account_type` Required claim of ADR-MONO-021.

---

# References

- IETF RFC 7519: JSON Web Token (JWT)
- IETF RFC 7517: JSON Web Key (JWK)
- IETF RFC 8414: OAuth 2.0 Authorization Server Metadata
- OpenID Connect Discovery 1.0: `.well-known/openid-configuration`
