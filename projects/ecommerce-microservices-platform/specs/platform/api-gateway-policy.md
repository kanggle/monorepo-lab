# API Gateway Policy

Defines the responsibilities and rules of the `gateway-service`.

---

# Role

`gateway-service` is the single entry point for all external client requests.

All requests from external clients (web, mobile) must pass through the gateway.
Services must not be directly exposed to external traffic.

---

# Responsibilities

- **Routing**: Forward requests to the appropriate downstream service.
- **Authentication**: Validate JWT access tokens on all non-public routes.
- **Rate Limiting**: Apply per-client and per-route rate limits.
- **Request Logging**: Log all inbound requests (method, path, status, latency) without sensitive data.
- **CORS**: Manage allowed origins centrally at the gateway level.

---

# Authentication at Gateway

- The gateway verifies the `Authorization: Bearer <token>` header on protected routes.
- On valid JWT: forward the request with the verified `X-User-Id`, `X-User-Email`, and `X-User-Role` headers.
- On invalid or missing JWT: return `401 UNAUTHORIZED` immediately without forwarding.
- Public routes (no JWT required) must be explicitly listed in the gateway route config.

## Public Routes (no auth required)

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/products/**`
- `GET /api/search/**`
- `GET /api/reviews/products/**`
- `GET /actuator/health`

---

# Service Trust Model

- Services behind the gateway may trust the `X-User-Id` header forwarded by the gateway.
- Services must not accept `X-User-Id` from external clients directly.
- Services must still enforce their own authorization logic beyond identity.

---

# Rate Limiting

- Default: 100 requests per minute per IP.
- Auth endpoints: 10 requests per minute per IP (stricter to prevent brute force).
- Configurable per route in gateway configuration.

---

# Error Responses

Gateway-level errors (before reaching a service) must follow the platform error response format:

```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

---

# Change Rule

Any change to public route lists or gateway behavior must be documented here before deployment.
