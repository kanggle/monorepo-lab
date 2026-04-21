---
name: redis-session
description: Redis Sorted Set session management, concurrent limits
category: backend
---

# Skill: Redis Session Management

Patterns for user session management using Redis Sorted Sets with concurrent session limits.

Prerequisite: read `specs/platform/security-rules.md` before using this skill.

---

## Session Registry Interface

```java
// domain/repository/UserSessionRegistry.java
public interface UserSessionRegistry {

    record RegistrationResult(String newSessionId, String evictedSessionId) {}

    RegistrationResult registerSession(UUID userId, String refreshToken, long inactivityTimeoutSeconds);
    void rotateSession(UUID userId, String oldRefreshToken, String newRefreshToken, long inactivityTimeoutSeconds);
    void removeSession(UUID userId, String refreshToken);
    void removeAllSessions(UUID userId);
}
```

---

## Redis Data Structure

| Key | Type | Member | Score |
|---|---|---|---|
| `{ns}:sessions:{userId}` | Sorted Set | SHA-256(refreshToken) | last activity epoch millis |

---

## Session Registration (Lua Script)

Atomically: remove inactive sessions → check count → evict oldest if over limit → add new session.

```lua
-- KEYS[1] = sessions:{userId}
-- ARGV[1] = cutoffMillis, ARGV[2] = maxSessions, ARGV[3] = nowMillis
-- ARGV[4] = newSessionHash, ARGV[5] = inactivityTimeout, ARGV[6] = refreshKeyPrefix

redis.call('ZREMRANGEBYSCORE', KEYS[1], '0', ARGV[1])  -- remove inactive
local count = redis.call('ZCARD', KEYS[1])
local evictedHash = false
if count >= tonumber(ARGV[2]) then
    local oldest = redis.call('ZRANGE', KEYS[1], '0', '0')
    if #oldest > 0 then
        evictedHash = oldest[1]
        redis.call('ZREM', KEYS[1], evictedHash)
        redis.call('DEL', ARGV[6] .. evictedHash)  -- delete evicted refresh token
    end
end
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
redis.call('EXPIRE', KEYS[1], ARGV[5])
return evictedHash
```

---

## Session Rotation (Lua Script)

On token refresh: replace old session hash with new, update activity timestamp.

```lua
-- KEYS[1] = sessions:{userId}
-- ARGV[1] = oldHash, ARGV[2] = nowMillis, ARGV[3] = newHash, ARGV[4] = timeout

redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
redis.call('EXPIRE', KEYS[1], ARGV[4])
return 1
```

---

## Flow

```
Login:
  1. registerSession() → evicts oldest if over maxConcurrentSessions
  2. If evictedSessionId returned → record metrics (session eviction)

Token Refresh:
  1. rotateSession() → replaces old hash, updates activity time

Logout:
  1. removeSession() → removes single session

Account Deactivation:
  1. removeAllSessions() → deletes entire sorted set
```

---

## Configuration

```yaml
app:
  session:
    max-concurrent-sessions: 5
    inactivity-timeout-seconds: 604800  # 7 days
```

---

## Rules

- All session hashes are SHA-256 of the refresh token — never store raw tokens.
- Sorted Set score = epoch millis for inactivity-based eviction.
- Lua scripts ensure atomicity — no race conditions between check and evict.
- Single Redis instance only — Cluster requires explicit KEYS declarations.
- `@Profile("!standalone")` on Redis implementation.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Race condition in check-then-evict | Use Lua script for atomic operation |
| Stale sessions not cleaned up | `ZREMRANGEBYSCORE` removes inactive sessions on each registration |
| Evicted session's refresh token still valid | Lua script DELs the refresh token key on eviction |
| Missing TTL on sorted set | Set EXPIRE equal to inactivity timeout |
