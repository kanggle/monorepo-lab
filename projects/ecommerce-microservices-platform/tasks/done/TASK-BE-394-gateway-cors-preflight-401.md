# TASK-BE-394 — ecommerce-gateway returns 401 on CORS preflight (`OPTIONS`), blocking all browser-origin authed writes

**Status:** done

**Type:** TASK-BE (project-internal — `projects/ecommerce-microservices-platform/` gateway service)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (Spring Security CORS filter ordering — small config change + a gateway IT; behavior must be verified against a browser preflight)

---

## Goal

Surfaced by **TASK-FE-074's AC-2 run** (web-store consumer e2e against a live GAP stack, PR #1764): the **`ecommerce-gateway` rejects the CORS preflight with `401 Unauthorized`**. A browser issues an `OPTIONS` preflight before any non-simple cross-origin request (e.g. an authed `POST /api/wishlists` carrying `Authorization` + `Content-Type: application/json`); the preflight is unauthenticated *by spec* and must be permitted (return `200`/`204` + the `Access-Control-Allow-*` headers). The gateway's Spring Security filter chain evaluates the `OPTIONS` request **before** CORS handling and 401s it, so the browser reports `TypeError: Failed to fetch` and the real request never goes out.

Evidence (2026-06-16, fed-e2e `ecommerce-gateway`):

```
curl -X OPTIONS http://localhost:8080/api/wishlists \
  -H 'Origin: http://localhost:3000' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization,content-type'
→ HTTP/1.1 401 Unauthorized            # same for Origin http://localhost:3001
```

This blocks **every browser-origin authed write** (wishlist add/remove, profile mutations, any client-side `POST/PUT/DELETE/PATCH` with auth), independent of origin/port. SSR reads and `GET`s work; the hole is specifically the preflight. If the same gateway config ships in a prod-like deployment, the web-store's authed client flows are broken there too — so this is a genuine bug, not merely an e2e-stack quirk.

## Scope

**In scope** — `ecommerce-gateway` (the API gateway service):

1. Ensure CORS is wired so that **preflight `OPTIONS` requests are permitted without authentication** and answered with the proper `Access-Control-Allow-Origin/-Methods/-Headers/-Credentials` headers. In Spring Security terms: register a `CorsConfigurationSource` via `http.cors(...)` (which inserts the `CorsFilter` ahead of authorization) and/or `authorizeExchange/authorizeHttpRequests` permitting `HttpMethod.OPTIONS`. Verify against the WebFlux-vs-servlet stack the gateway actually uses.
2. Confirm `Access-Control-Allow-Methods` includes `POST,PUT,DELETE,PATCH` and `Access-Control-Allow-Headers` includes `authorization,content-type` for the `/api/**` routes.
3. **Folded (from FE-074 AC-2):** the gateway's `CORS_ALLOWED_ORIGINS` was `http://localhost:3000` only; the standalone web-store dev port is `:3001`. Decide whether `:3001` belongs in the configured dev origins (it was added ad-hoc to the fed-e2e compose during the AC-2 run). This is a config/origins question separate from the preflight-permit fix, which is the real blocker.

**Out of scope:** the web-store frontend (FE-074 is done — its login migration is verified; golden-flow + cart-management pass). The IAM/user-domain seeding (auth_db/account_db/user_db rows for a B2C consumer) is a fed-e2e fixture concern, not this gateway fix.

## Acceptance Criteria

- **AC-1** — `OPTIONS /api/wishlists` (and any `/api/**` route) from an allowed origin with `Access-Control-Request-Method: POST` returns **`200`/`204`** (NOT 401) with the matching `Access-Control-Allow-*` headers. Verified by `curl` preflight + by a real browser `fetch` (no `TypeError: Failed to fetch`).
- **AC-2** — an authed browser `POST /api/wishlists` from the web-store succeeds end-to-end; the web-store **wishlist e2e spec passes** under `SKIP_GAP_E2E=0` against a stack with a B2C consumer provisioned (the FE-074 spec is already migrated + ready — this unblocks its 3rd test).
- **AC-3** — a gateway test (IT or slice) locks in "preflight `OPTIONS` is permitted without auth" so the regression can't silently return.
- **AC-4** — no regression to authenticated `GET`/SSR flows or to the existing CI (the gateway's `:test` + the ecommerce CI jobs stay GREEN).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/` gateway / security spec (CORS + auth posture).
- TASK-FE-074 (`tasks/done/`) — the AC-2 run that surfaced this; its PR #1764 comments hold the full diagnosis (preflight-401, `CORS_ALLOWED_ORIGINS` 3000-only).

## Related Contracts

None changed — this restores the intended CORS contract (preflight is unauthenticated); no API request/response shape changes.

## Edge Cases

- **WebFlux vs servlet** — if the gateway is Spring Cloud Gateway (WebFlux), the fix is a `CorsWebFilter`/`globalcors` config, not the servlet `CorsFilter`; apply the stack-correct form.
- **Credentials** — if `Access-Control-Allow-Credentials: true` is needed (cookies), `Allow-Origin` cannot be `*`; keep the explicit allowlist.

## Failure Scenarios

- Permitting `OPTIONS` globally but not returning the `Allow-*` headers → browser still blocks (preflight "succeeds" with 200 but no CORS headers). AC-1 must assert the headers, not just the status.
- Adding `:3001` to origins without fixing the preflight-permit → still 401 (the FE-074 AC-2 finding: origin fix alone is insufficient).
