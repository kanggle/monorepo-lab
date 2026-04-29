# JWT Standard Claims Contract

**Status:** Established  
**Audience:** All platform projects (ecommerce, wms, fan-platform, scm, erp, mes)  
**Authority:** Global Account Platform  
**Effective Date:** 2026-04-30

---

## Purpose

This contract defines the standard JWT structure, claims, and validation rules that all platform gateways and services must follow when processing tokens issued by the Global Account Platform. It enables:

- Unified authentication across multiple platforms and account types
- Consistent role-based authorization within each platform
- Single Sign-On (SSO) scoping by account type
- Cryptographic verification via asymmetric signing (RSA)

---

## Account Types

Two distinct account types exist within the same identity service:

| Type | Purpose | Examples | Social Login | Session | Target Platforms |
|---|---|---|---|---|---|
| `CONSUMER` | Self-registered B2C users | Ecommerce shoppers, fan-site members | Allowed (Google, Naver, etc.) | Long-lived refresh tokens | ecommerce, fan-platform |
| `OPERATOR` | Company-provisioned B2B users | Warehouse staff, ERP clerks, MES operators | Not allowed | Short sessions | wms, erp, mes, scm |

**Constraint:** A single account cannot hold both account types. One person requiring both roles must provision separate accounts.

---

## JWT Signing Strategy

- **Algorithm:** RSA asymmetric (public/private key pair)
- **Key Management:**
  - Global Account Platform holds the private key (signing)
  - All platform gateways fetch the public key via JWKS endpoint (verification)
  - Keys may be rotated; gateways must implement key versioning via `kid` (key ID)
- **Token Lifetime:**
  - Access tokens: short-lived (5–15 minutes typical)
  - Refresh tokens: longer-lived, stored server-side, not validated as JWTs
- **Audience Scoping:** Each access token carries a platform-specific `aud` claim; gateways reject tokens with mismatched `aud`

---

## Standard Claims

All access tokens issued by the Global Account Platform MUST include the following claims:

| Claim | Type | Required | Description | Example |
|---|---|---|---|---|
| `sub` | UUID string | Yes | Account ID (globally unique, immutable across all platforms) | `550e8400-e29b-41d4-a716-446655440000` |
| `account_type` | `CONSUMER` \| `OPERATOR` | Yes | Account classification — determines platform eligibility | `CONSUMER` |
| `aud` | string | Yes | Target platform audience — must match gateway's own platform | `ecommerce`, `fan`, `wms`, `erp`, `mes`, `scm` |
| `roles` | string[] | Yes | Platform-scoped roles for the `aud` platform (may be empty, minimum `[]`) | `["CUSTOMER"]` or `["WMS_OPERATOR", "OUTBOUND_MANAGER"]` |
| `email` | string | Yes | Account email address | `user@example.com` |
| `iss` | string | Yes | Issuer URI of the Global Account Platform | `https://account.example.com` |
| `iat` | number | Yes | Issued at (Unix epoch seconds) | `1746000000` |
| `exp` | number | Yes | Expiry time (Unix epoch seconds) — gateways must reject expired tokens | `1746003600` |
| `jti` | string | Recommended | JWT ID (unique token identifier for revocation and audit) | UUID |
| `kid` | string | Recommended | Key ID (version identifier for key rotation) | `key-v1` |

Additional custom claims MAY be added by the identity service but MUST NOT conflict with standard OIDC claims. Gateways that do not recognize a claim MUST ignore it.

---

## Role Strategy

Roles are platform-scoped and define authorization within the target `aud` platform.

### CONSUMER Roles

- **ecommerce:** `CUSTOMER` (single, immutable)
- **fan-platform:** `FAN` (base role); may also include `PREMIUM_MEMBER` when an active membership subscription exists
- **Other CONSUMER platforms:** Domain-specific role (e.g., `SUBSCRIBER`)

CONSUMER accounts have only one active role per platform. Multi-role support is not required for CONSUMER platforms.

### OPERATOR Roles

- Operators may hold multiple roles on a single platform
- A single operator account may have roles on multiple platforms (e.g., `wms` + `scm`)
- Platform administrators define and assign roles; the identity service does not prescribe a role catalog per platform
- Examples:
  - WMS: `["WMS_OPERATOR", "OUTBOUND_MANAGER"]`
  - SCM: `["SCM_OPERATOR", "BUYER"]`
  - An operator with both: `aud: wms` token has `roles: ["WMS_OPERATOR"]`; same account with `aud: scm` has `roles: ["SCM_OPERATOR"]`

---

## Single Sign-On (SSO) Scope

SSO is scoped by account type, not globally:

- **CONSUMER SSO:** ecommerce and fan-platform share the same account. When a user logs in once, they can request tokens for either platform without re-entering credentials.
- **OPERATOR SSO:** WMS, ERP, MES, and SCM share the same account. A provisioned operator can request tokens for any assigned platform.
- **No Cross-Type SSO:** A CONSUMER account and an OPERATOR account never share credentials or tokens, even if held by the same person.

---

## Gateway Enforcement Rules

Every platform gateway MUST implement the following validation and injection logic:

### Pre-Validation Cleanup

1. **Strip all identity-related headers** from the incoming HTTP request before JWT validation:
   - `Authorization`, `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Account-Type`
   - Any custom headers the identity service may inject
   - This prevents client-side spoofing

### JWT Validation

2. **Fetch and verify the signature** using the public key from the identity service's JWKS endpoint:
   - Construct the JWKS URL from the `iss` claim: `${iss}/.well-known/jwks.json`
   - Match the `kid` in the JWT header to a key in the JWKS response
   - Verify the signature; reject on failure

3. **Validate expiry:** Reject if current time > `exp`

4. **Validate issuer:** Reject if `iss` does not match the expected identity service URI (e.g., `https://account.example.com`)

5. **Validate audience:** Reject if `aud` does not match the gateway's own platform identifier

6. **Validate account type:**
   - ecommerce and fan gateways: reject if `account_type != CONSUMER`
   - Wms, erp, mes, scm gateways: reject if `account_type != OPERATOR`

### Post-Validation Injection

7. **Inject standard headers** into the request context for downstream services:
   - `X-User-Id` ← `sub`
   - `X-User-Role` ← comma-separated `roles` array (e.g., `WMS_OPERATOR,OUTBOUND_MANAGER`)
   - `X-User-Email` ← `email`
   - `X-Account-Type` ← `account_type`

### Error Handling

- Invalid or missing JWT: respond with HTTP 401 Unauthorized
- Expired token: respond with HTTP 401 Unauthorized
- Signature mismatch: respond with HTTP 401 Unauthorized
- Wrong `aud` or `account_type`: respond with HTTP 403 Forbidden (authenticated but not authorized for this platform)
- JWKS endpoint unreachable: log error and respond with HTTP 503 Service Unavailable (do not fall back to cached keys older than 1 hour)

---

## JWKS Endpoint Convention

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

## Token Examples

### Example 1: ecommerce CONSUMER

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "account_type": "CONSUMER",
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

**Gateway Behavior (ecommerce):**
- Validate signature, expiry, issuer, `aud = "ecommerce"`, `account_type = CONSUMER` ✓
- Inject: `X-User-Id: 550e8400-e29b-41d4-a716-446655440000`, `X-User-Role: CUSTOMER`, `X-User-Email: shopper@example.com`, `X-Account-Type: CONSUMER`

### Example 2: fan-platform CONSUMER with Premium Membership

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "account_type": "CONSUMER",
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
- Validate signature, expiry, issuer, `aud = "fan"`, `account_type = CONSUMER` ✓
- Inject: `X-User-Id: 550e8400-e29b-41d4-a716-446655440000`, `X-User-Role: FAN,PREMIUM_MEMBER`, `X-User-Email: shopper@example.com`, `X-Account-Type: CONSUMER`
- Authorization: services may check `X-User-Role` for `PREMIUM_MEMBER` to enable premium features

### Example 3: WMS OPERATOR with Multiple Roles

```json
{
  "sub": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "account_type": "OPERATOR",
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
- Validate signature, expiry, issuer, `aud = "wms"`, `account_type = OPERATOR` ✓
- Inject: `X-User-Id: 6ba7b810-9dad-11d1-80b4-00c04fd430c8`, `X-User-Role: WMS_OPERATOR,OUTBOUND_MANAGER`, `X-User-Email: warehouse-lead@company.com`, `X-Account-Type: OPERATOR`
- Authorization: services may check `X-User-Role` for `OUTBOUND_MANAGER` to enable outbound-specific operations

### Example 4: Invalid Token (Wrong Audience)

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "account_type": "CONSUMER",
  "aud": "wms",  // mismatch for ecommerce gateway
  "roles": ["CUSTOMER"],
  "email": "shopper@example.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746003600
}
```

**Gateway Behavior (ecommerce):**
- Validate signature, expiry, issuer ✓
- Validate `aud = "wms"` against expected `"ecommerce"` ✗
- Respond: HTTP 403 Forbidden — token is for a different platform

### Example 5: Invalid Token (Wrong Account Type)

```json
{
  "sub": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "account_type": "OPERATOR",  // mismatch for ecommerce (CONSUMER only)
  "aud": "ecommerce",
  "roles": ["WMS_OPERATOR"],
  "email": "operator@company.com",
  "iss": "https://account.example.com",
  "iat": 1746000000,
  "exp": 1746003600
}
```

**Gateway Behavior (ecommerce):**
- Validate signature, expiry, issuer, `aud = "ecommerce"` ✓
- Validate `account_type = CONSUMER` (got `OPERATOR`) ✗
- Respond: HTTP 403 Forbidden — operator accounts cannot access consumer platforms

---

## Implementation Notes

- **Graceful Key Rotation:** When a new key version is deployed, the old key remains valid for 24 hours to allow tokens signed with the old key to complete in-flight requests.
- **Clock Skew:** Gateways SHOULD allow up to 60 seconds of clock skew when validating `iat` and `exp` to tolerate minor time synchronization issues.
- **Audit Logging:** Log all validation failures (signature, expiry, account type, audience) with the `jti` claim for audit trails.
- **Token Refresh:** Access tokens are not refreshed in-place; clients must use the refresh token endpoint to obtain a new access token.

---

## References

- IETF RFC 7519: JSON Web Token (JWT)
- IETF RFC 7517: JSON Web Key (JWK)
- IETF RFC 8414: OAuth 2.0 Authorization Server Metadata
- OpenID Connect Discovery 1.0: `.well-known/openid-configuration`
