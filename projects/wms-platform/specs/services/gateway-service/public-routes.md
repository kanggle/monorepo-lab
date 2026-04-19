# gateway-service — Public Routes (v1)

Authoritative list of routes that bypass JWT authentication. Every route not
listed here requires a valid bearer token.

Per `platform/api-gateway-policy.md`: *default = no route is public*.

---

## Public Routes

| Path | Method | Purpose |
|---|---|---|
| `/actuator/health` | GET | Liveness/readiness probes |
| `/actuator/info` | GET | Build info |

That's the entire public surface. No login/token endpoints (no auth-service in
v1), no public catalog reads.

---

## Not Public (require JWT)

| Path prefix | Owning service |
|---|---|
| `/api/v1/master/**` | master-service |
| `/actuator/prometheus` | internal scrape only (bind to internal port in prod; fail closed externally) |

All other paths return `404 NOT_FOUND`.

---

## Change Rule

Adding a public route requires:

1. Updating this file first.
2. Adding the corresponding matcher to `SecurityConfig`'s `permitAll()` list.
3. Security review note on why the route is safe without authentication.
