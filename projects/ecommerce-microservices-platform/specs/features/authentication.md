# Feature: Authentication & Authorization

## Purpose

Provides user identity management and access control for the platform. Handles account registration, login/logout, JWT-based session management, and admin-only authorization enforcement via the gateway.

## Related Services

| Service | Role |
|---|---|
| auth-service | Primary owner — credential storage, token issuance/rotation, session management, audit logging |
| gateway-service | JWT validation, user identity injection (X-User-Id, X-User-Email, X-User-Role headers), rate limiting |
| user-service | Consumes UserSignedUp event to create initial user profile |

## User Flows

### Signup

1. Client sends POST /api/auth/signup with email, password, name
2. auth-service validates input, creates credentials, returns account info (userId, email, name, createdAt)
3. auth-service publishes UserSignedUp event
4. user-service consumes UserSignedUp and creates initial profile record

### Login

1. Client sends POST /api/auth/login with email, password
2. auth-service validates credentials
3. On success: issues access token (JWT, 1h TTL) and refresh token (opaque UUID, 30d TTL in Redis)
4. auth-service publishes UserLoggedIn event
5. If concurrent session limit exceeded, oldest session is evicted (SessionLimitExceeded event)

### Token Refresh

1. Client sends POST /api/auth/refresh with refreshToken
2. auth-service validates token in Redis
3. Old token is atomically revoked, new token pair issued (token rotation)
4. auth-service publishes TokenRefreshed event

### Logout

1. Client sends POST /api/auth/logout with refreshToken (requires Bearer JWT)
2. auth-service removes refresh token from Redis
3. auth-service publishes UserLoggedOut event

### Request Authorization (Gateway)

1. Client sends request with Bearer JWT to any API endpoint
2. gateway-service validates JWT signature and expiration
3. On success: injects X-User-Id, X-User-Email, and X-User-Role headers, forwards to downstream service
4. On failure: returns 401 Unauthorized

## Business Rules

- Access token: JWT signed with HS256, TTL 1 hour
- Refresh token: opaque UUID stored in Redis, TTL 30 days
- Token rotation: on every refresh, old token is revoked and new token issued atomically (Redis Lua script or transaction)
- Reuse detection: previously consumed refresh tokens are rejected with REFRESH_TOKEN_REVOKED
- Concurrent session limit: per-user maximum active sessions with oldest session eviction
- Inactivity timeout: sessions past inactivity threshold are expired
- Login rate limiting: failed login attempts are tracked and rate-limited
- Audit logging: all login attempts, token refreshes, logouts, and session changes are logged

## Related Contracts

- HTTP: `specs/contracts/http/auth-api.md`
- Events: `specs/contracts/events/auth-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| UserSignedUp | auth-service | user-service, notification-service (future) |
| UserLoggedIn | auth-service | audit/analytics |
| UserLoggedOut | auth-service | audit/analytics |
| TokenRefreshed | auth-service | audit |
| LoginFailed | auth-service | audit/security-monitoring |
| SessionLimitExceeded | auth-service | audit |
