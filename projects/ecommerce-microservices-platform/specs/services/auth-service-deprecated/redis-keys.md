# auth-service Redis Key Patterns

Defines Redis key naming patterns specific to auth-service.

General Redis key rules are defined in `platform/naming-conventions.md`.

---

# Key Patterns

All keys are prefixed with a configurable namespace (default: `auth`).

| Purpose | Pattern | Example |
|---|---|---|
| Refresh token storage | `{namespace}:refresh:{tokenHash}` | `auth:refresh:sha256hex...` |
| Revoked token marker | `{namespace}:revoked:{tokenHash}` | `auth:revoked:sha256hex...` |
| Access token blocklist | `{namespace}:blocked-at:{tokenHash}` | `auth:blocked-at:sha256hex...` |
| User session set | `{namespace}:sessions:{userId}` | `auth:sessions:550e8400-...` |
| User refresh token index | `{namespace}:user-tokens:{userId}` | `auth:user-tokens:550e8400-...` |
| Blocked user marker | `{namespace}:blocked-user:{userId}` | `auth:blocked-user:550e8400-...` |
| Rate limiter counter | `{namespace}:ratelimit:{clientKey}` | `auth:ratelimit:1.2.3.4:/api/auth/login` |
| OAuth state | `{namespace}:oauth:state:{state}` | `auth:oauth:state:a1b2c3d4-...` |

> Note: `tokenHash` is the SHA-256 hex digest of the raw token value. Raw tokens are never stored as Redis keys.

---

# Rules

- All keys must have a TTL. Do not create keys without expiration.
- Refresh token TTL: 30 days.
- Revoked token marker TTL: must match the original refresh token's remaining TTL.
- Access token blocklist TTL: must match the original access token's remaining TTL.
- Rate limiter counter TTL: aligned with the rate limit window (e.g., 60s for login, 3600s for signup).
- Session set TTL: aligned with refresh token TTL.
- User refresh token index TTL: aligned with refresh token TTL (save 시 갱신).
- Blocked user marker TTL: aligned with access token TTL.
- OAuth state TTL: 10 minutes.

---

# Change Rule

New key patterns for auth-service must be documented here before implementation.
