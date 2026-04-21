---
name: jwt-auth
description: JWT token generation, refresh token rotation, token store
category: backend
---

# Skill: JWT Authentication

Patterns for JWT token generation, validation, and refresh token management.

Prerequisite: read `specs/platform/security-rules.md` before using this skill.

---

## Token Generator

Domain interface in `domain/service/`, implementation in `infrastructure/security/`.

```java
// domain/service/TokenGenerator.java
public interface TokenGenerator {
    String generateAccessToken(User user);
    long accessTokenTtlSeconds();
}
```

```java
// infrastructure/security/JwtTokenGenerator.java
@Component
public class JwtTokenGenerator implements TokenGenerator {

    private final SecretKey secretKey;
    private final long ttlSeconds;
    private final String issuer;
    private final String audience;

    public JwtTokenGenerator(JwtProperties jwtProperties) {
        this.secretKey = jwtProperties.getSecretKey();
        this.ttlSeconds = jwtProperties.getAccessTokenTtlSeconds();
        this.issuer = jwtProperties.getIssuer();
        this.audience = jwtProperties.getAudience();
    }

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail().value())
            .claim("role", user.getRole().name())
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(secretKey)
            .compact();
    }

    @Override
    public long accessTokenTtlSeconds() {
        return ttlSeconds;
    }
}
```

---

## JWT Claims

| Claim | Value | Purpose |
|---|---|---|
| `sub` | userId (UUID) | User identification |
| `email` | user email | Downstream services use via `X-User-Email` header |
| `role` | user role | Authorization checks |
| `iss` | issuer string | Token origin validation |
| `aud` | audience string | Intended recipient |
| `iat` | issued at | Token freshness |
| `exp` | expiration | Token expiry |

---

## Refresh Token Store (Redis)

Stores refresh tokens as SHA-256 hashes with TTL. Uses Lua scripts for atomic invalidation.

```java
// domain/repository/RefreshTokenStore.java
public interface RefreshTokenStore {
    void save(String token, UUID userId, long ttlSeconds);
    Optional<UUID> findUserIdByToken(String token);
    boolean isRevoked(String token);
    boolean invalidate(String token, long revokedTtlSeconds);
    Set<String> findAllTokenHashesByUserId(UUID userId);
    void invalidateAllByUserId(UUID userId, long revokedTtlSeconds);
}
```

### Redis Key Structure

| Key | Type | Content |
|---|---|---|
| `{ns}:refresh:{sha256}` | String | userId |
| `{ns}:revoked:{sha256}` | String | `"1"` (with TTL) |
| `{ns}:user-tokens:{userId}` | Set | token hashes |

### Atomic Invalidation (Lua)

```lua
local deleted = redis.call('DEL', KEYS[1])    -- delete refresh token
redis.call('SET', KEYS[2], '1', 'EX', ARGV[1]) -- mark as revoked
return deleted
```

---

## Token Rotation Flow

```
1. Client sends refresh token
2. Validate: exists in store? not revoked?
3. Invalidate old token (DEL + mark revoked)
4. Generate new access + refresh token pair
5. Save new refresh token to store
6. Rotate session in session registry
```

---

## Rules

- Never store raw tokens — always SHA-256 hash.
- Revoked tokens tracked with TTL to prevent reuse within window.
- User-tokens index enables bulk invalidation on account deactivation.
- `@Profile("!standalone")` on Redis implementation.
- JWT secret must be at least 32 bytes for HMAC-SHA256.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Storing raw refresh tokens in Redis | Always hash with SHA-256 before storage |
| No revoked token tracking | Mark invalidated tokens as revoked with TTL |
| Missing user-tokens index cleanup | Remove hash from user index on invalidation |
| Short JWT secret | Minimum 32 bytes for HMAC-SHA256 |
| No standalone fallback | Provide in-memory implementation for `@Profile("standalone")` |
