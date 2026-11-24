# Runbook — GatewayJwtFailureSpike

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`sum(rate(gateway_jwt_validation_total{result!="valid"}[5m])) > 5` for 5 minutes, severity `warning`,
i.e. more than 5 rejected access tokens per second across all outcomes.

Metric: counter `gateway_jwt_validation_total{result}`, exported by
`gateway-service/.../filter/JwtAuthenticationFilter.java` (`recordJwtValidation`, constant
`JWT_VALIDATION_METRIC_NAME`). Values: `valid`, `expired`, `missing`, `invalid_signature`.

- `expired` — the `exp` claim has passed (the only failure with its own value).
- `missing` — no `Authorization` header (or a blank one).
- `invalid_signature` — **every other failure**: header present but not `Bearer …`, unknown or
  missing `kid`, bad signature, wrong issuer, an unexpected verifier error (`classifyVerificationFailure`).
- Not counted: public routes (`gateway.public-paths` in `application.yml` — signup, refresh,
  password reset, `/oauth2/**`, **the whole `/api/admin/**` subtree**, which admin-service verifies
  itself) and 401s produced after a valid signature (missing `tenant_id`, token invalidated in
  Redis). So admin operator-token problems never move this metric.

## 2. First checks

```bash
# Which outcome dominates
docker exec iam-prometheus wget -qO- --post-data='query=sum by (result) (rate(gateway_jwt_validation_total[5m]))' http://localhost:9090/api/v1/query

# Gateway health (200 even when a route is DOWN: it reports WARNING)
curl -s http://localhost:8080/actuator/health

# Are the signing keys reachable? (the gateway fetches this URL: gateway.jwt.jwks-url)
curl -s -o /dev/null -w "jwks=%{http_code}\n" http://localhost:8081/internal/auth/jwks
```

Gateway logs are the terminal running `:apps:gateway-service:bootRun`. Look for
`Initial JWKS load failed`, `JWKS refresh failed`, `Scheduled JWKS refresh failed`,
`In-memory JWKS grace period expired` (all `JwksCache.java`). The per-request rejection is logged at
DEBUG only (`JWT verification failed`), so it is absent at default levels.

## 3. Likely causes and how to confirm each

| Dominant `result` | Cause | Confirm |
|---|---|---|
| `expired` | Clients keep using an old access token (a client that does not refresh, a clock skew) | The rate is steady and low per client; auth refresh calls succeed. Not an attack by itself |
| `missing` | A client or probe calling protected routes with no header, or a proxy stripping `Authorization` | Gateway access log for the route; reproduce with `curl -i http://localhost:8080/api/accounts/me/sessions` (an authenticated route, `application.yml:37-42`; expect `401` and one `missing` increment) |
| `invalid_signature` | The gateway cannot fetch or does not know the signing key (auth-service down, key rotated) | `jwks=` above is not 200; the `JWKS refresh failed` lines above |
| `invalid_signature` | Issuer mismatch: tokens carry an `iss` outside `gateway.jwt.allowed-issuers` (env `JWT_ALLOWED_ISSUERS`, default = `OIDC_ISSUER_URL`, default `http://localhost:8081`) | Decode a failing token's payload and compare `iss` with `curl -s http://localhost:8081/.well-known/openid-configuration` (`issuer` field, public route) |
| any | Credential stuffing / token forgery from outside | Source IPs in the gateway access log; compare with `gateway-rate-limit-exceeded.md` |

## 4. Recover / mitigate

- Keys unreachable: restore auth-service first (`service-down.md`); the gateway refreshes JWKS on its
  own schedule (`gateway.jwt.jwks-refresh-interval-ms: 600000`, TTL 600 s), so allow up to that
  interval or restart the gateway to force the initial load.
- Issuer mismatch: align `JWT_ALLOWED_ISSUERS` with the issuer auth-service mints, restart the gateway.
- Client-caused (`expired` / `missing`): nothing to fix on the platform; tell the client owner.
- Attack: rate limits already apply per scope (`gateway-rate-limit-exceeded.md`); blocking an address
  at the network edge is outside what this repository configures — **not verified** how it is done
  in the demo environment.
- Confirm: the first query's non-`valid` rate is under 5/s for 5 minutes.

## 5. Escalate

- `invalid_signature` with a healthy JWKS and a matching issuer: possible forged tokens — escalate to
  security; keep the sample token's header and claims (not the raw token) for them.
- Key rotation suspected: hand to the auth-service owner.
