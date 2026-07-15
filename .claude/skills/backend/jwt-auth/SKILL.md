---
name: jwt-auth
description: JWT issuance (identity-platform only), RS256/JWKS verification, refresh token rotation and store
category: backend
---

# Skill: JWT Authentication

Patterns for JWT issuance, verification, and refresh-token management.

**Source of truth:** `platform/contracts/jwt-standard-claims.md` (canonical claim set, signing
algorithm, gateway enforcement) + `platform/security-rules.md`. **Whether you may issue tokens
at all is decided by the `Service Type` your service declares in
`specs/services/<service>/architecture.md`** — see the scope table.

> ## Scope — who may use this skill (TASK-MONO-410)
>
> **The platform has exactly ONE token issuer: the service whose `Service Type` is
> `identity-platform` (iam).** Everything else does not issue tokens — it *verifies* them.
>
> | Declared `Service Type` | Use |
> |---|---|
> | `identity-platform` | § Token Issuance + § Refresh Token Store below, with `service-types/identity-platform-setup` |
> | gateway (edge `rest-api`) | **`backend/gateway-security`** — and do not hand-roll it: `libs/java-gateway` already ships the filters |
> | any other backend service | you are an OAuth2 **Resource Server**. Verify with `libs/java-security` (`Rs256JwtVerifier`, `JwksProvider`, `AllowedIssuersValidator`). **Do not issue your own tokens.** |
>
> **Never mint a service-local HMAC token.** A service that signs its own JWT with a shared
> secret re-creates the model this platform retired (ecommerce `auth-service` — decommissioned
> by TASK-BE-132 and **excluded from the build**; its source survives only as history). Platform
> tokens are **RSA-signed and verified via JWKS**: a symmetric secret can be verified only by
> its own issuer, which is exactly what a shared identity plane cannot require.

---

## Use the library — do not re-implement signing or verification

`ADR-MONO-049` consolidated 49 hand-written copies of servlet/gateway security into shared
libraries **because the copies drifted and nobody was watching them**. A skill that teaches you
to hand-roll signing is a skill that teaches you to write copy #50.

| Need | Use |
|---|---|
| Sign an access token (IdP only) | `libs/java-security` → `JwtSigner` / `Rs256JwtSigner` |
| Verify a token (any consumer) | `libs/java-security` → `JwtVerifier` / `Rs256JwtVerifier` |
| Serve / fetch public keys | `libs/java-security` → `JwksProvider` |
| Reject unknown issuers | `libs/java-security` → `AllowedIssuersValidator` |
| Gateway header enrichment + identity-header stripping | `libs/java-gateway` (see `backend/gateway-security`) |

The code below is the **shape** those libraries implement — read it to understand the contract,
not to copy it into a new service.

---

## Token Issuance (identity-platform only)

Domain interface in `domain/service/`, implementation in `infrastructure/security/`.

```java
// domain/service/TokenGenerator.java
public interface TokenGenerator {
    String generateAccessToken(Account account, String audience);
    long accessTokenTtlSeconds();
}
```

```java
// infrastructure/security/JwtTokenGenerator.java  — RS256, asymmetric
@Component
public class JwtTokenGenerator implements TokenGenerator {

    private final PrivateKey signingKey;   // RSA private key; public half served via JWKS
    private final String keyId;            // -> `kid` header, so verifiers can select the key
    private final long ttlSeconds;
    private final String issuer;

    @Override
    public String generateAccessToken(Account account, String audience) {
        Instant now = Instant.now();
        // A token is scoped to ONE platform (`aud`) and carries ONLY that platform's roles.
        List<String> roles = account.rolesFor(audience);   // never the account's full role set
        return Jwts.builder()
            .header().keyId(keyId).and()
            .subject(account.getId().toString())
            .claim("email", account.getEmail().value())
            .claim("roles", roles)                          // ARRAY. `roles` is the sole authorization axis.
            .issuer(issuer)
            .audience().add(audience).and()
            .id(UUID.randomUUID().toString())               // `jti` - required for revocation
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(signingKey, Jwts.SIG.RS256)           // asymmetric - NOT a SecretKey
            .compact();
    }
}
```

**`roles` is an array, and it is the only authorization axis** (ADR-MONO-032). One identity may
hold `CUSTOMER` *and* `WMS_OPERATOR` simultaneously — a singular `role` claim cannot express
that, which is why the contract has no such claim. There is no `account_type` claim: it was
removed (TASK-MONO-263) and gateways gate on `roles` only.

---

## JWT Claims

Per `platform/contracts/jwt-standard-claims.md` — this table is a summary, the contract wins.

| Claim | Value | Purpose |
|---|---|---|
| `sub` | account UUID | Identity. Gateways inject it as `X-User-Id` |
| `roles` | **array** of platform-scoped roles | **Sole authorization axis.** Gateways inject it as `X-User-Role` (comma-separated) |
| `email` | account email | Injected as `X-User-Email` |
| `iss` | issuer | Validated against the gateway's allow-list |
| `aud` | **one** platform | A token is for exactly one platform and carries only that platform's roles |
| `iat` / `exp` | timestamps | Freshness / expiry |
| `jti` | token id | Revocation |
| `kid` (header) | signing key id | Lets verifiers pick the right JWKS key across rotation |

---

## Refresh Token Store (Redis)

Refresh tokens are **opaque and server-side** (they are not JWTs). Stored as SHA-256 hashes with
TTL; Lua scripts make invalidation atomic.

```java
// domain/repository/RefreshTokenStore.java
public interface RefreshTokenStore {
    void save(String token, UUID accountId, long ttlSeconds);
    Optional<UUID> findAccountIdByToken(String token);
    boolean isRevoked(String token);
    boolean invalidate(String token, long revokedTtlSeconds);
    Set<String> findAllTokenHashesByAccountId(UUID accountId);
    void invalidateAllByAccountId(UUID accountId, long revokedTtlSeconds);
}
```

### Redis Key Structure

| Key | Type | Content |
|---|---|---|
| `{ns}:refresh:{sha256}` | String | accountId |
| `{ns}:revoked:{sha256}` | String | `"1"` (with TTL) |
| `{ns}:account-tokens:{accountId}` | Set | token hashes |

### Atomic Invalidation (Lua)

```lua
local deleted = redis.call('DEL', KEYS[1])    -- delete refresh token
redis.call('SET', KEYS[2], '1', 'EX', ARGV[1]) -- mark as revoked
return deleted
```

---

## Token Rotation Flow

```
1. Client POSTs the refresh token to /v1/oauth/token (grant_type=refresh_token)
2. Validate: exists in store? not revoked?
3. Invalidate the old token (DEL + mark revoked)
4. Issue a new access token + a new refresh token
5. Save the new refresh token
6. Rotate the session in the session registry
```

Rate-limit this route on the **authenticated principal** (`acct:<sub>`), not the client IP — see
`backend/rate-limiting`.

---

## Rules

- **Sign with RSA (RS256) and publish the public key via JWKS.** Never a shared symmetric secret.
- **`roles` is an array**, and it is the sole authorization axis. No singular `role`. No `account_type`.
- **One token = one `aud`**, carrying only that platform's roles.
- Never store raw refresh tokens — always SHA-256 hash.
- Revoked tokens are tracked with a TTL so a token cannot be replayed inside its window.
- The account-tokens index enables bulk invalidation on account deactivation.
- `@Profile("!standalone")` on the Redis implementation.
- Rotate signing keys with a grace period; serve current + previous key in JWKS so in-flight tokens still verify.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| **Issuing tokens from a non-IdP service** | You are a Resource Server. Verify with `libs/java-security`; the IdP issues |
| **HMAC / `SecretKey` signing** | RS256 + JWKS. A symmetric secret cannot be verified by a gateway that does not hold it |
| **Singular `role` claim** | `roles` array — one identity legitimately holds several roles (ADR-MONO-032) |
| **Putting every role in every token** | Scope to the token's `aud`: only that platform's roles |
| Missing `jti` / `kid` | `jti` makes revocation possible; `kid` makes key rotation possible |
| Storing raw refresh tokens in Redis | Always hash with SHA-256 before storage |
| No revoked-token tracking | Mark invalidated tokens as revoked with a TTL |
| Missing account-tokens index cleanup | Remove the hash from the index on invalidation |
| No standalone fallback | Provide an in-memory implementation for `@Profile("standalone")` |
