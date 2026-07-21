# TASK-BE-552 — gateway-service: public-routes spec advertises removed `/api/auth/**` routes (auth moved to IAM OIDC)

Status: ready

`(분석=Opus 4.8 / 구현 권장=Sonnet — doc-fix + 선택적 dead-code 정리)`

---

## Goal

Close a spec↔code drift found in the 2026-07-21 reconciliation audit and re-measured against `main` (`dd93fc420`). The gateway **public-routes** spec still lists three `/api/auth/**` rows as public routes, but the gateway **removed the auth route** (`TASK-BE-132`, auth-service decommissioned; authentication is now IAM OIDC per `TASK-MONO-027`). The **code is correct** — this is a **doc-only stale reference**; no routing defect exists (an `/api/auth/**` request matches no route → 404, no dangling proxy → no 502).

## Re-measured evidence (line numbers = hypotheses, re-verify)

**Spec (stale):** [`specs/services/gateway-service/public-routes.md`](../../specs/services/gateway-service/public-routes.md):
- ≈ lines 13–15: three `/api/auth/**` rows under **Public Routes (no auth required)** — `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`.
- ≈ line 32: Rate-Limit "Sensitive" tier still names `/api/auth/login`, `/api/auth/signup`, `/api/auth/refresh`.

**Code (already correct — route removed):**
- [`apps/gateway-service/src/main/resources/application.yml`](../../apps/gateway-service/src/main/resources/application.yml) (≈ lines 42–46): explicit removal comment, no `/api/auth/**` route — `# auth-service route removed by TASK-BE-132 — auth-service decommissioned.` / `# Authentication is now handled by GAP OIDC (TASK-MONO-027).` No `- id: auth-service`, no `Path=/api/auth/**` anywhere (same in `application-standalone.yml`).
- [`PROJECT.md`](../../PROJECT.md) (≈ line 53): `~~auth-service~~ … RETIRED (TASK-BE-132) — settings.gradle include 제외, IAM OIDC 로 대체. 소스는 이력 보존 목적으로 apps/auth-service/ 에 잔존`. Gateway does IAM RS256 JWT validation (OAuth2 Resource Server) per ≈ line 40; the gateway is configured against the GAP/IAM OIDC issuer (application.yml ≈ 5–23, 226–239).

**Secondary stale code references (cosmetic — NO route reaches them, not defects):**
- `apps/gateway-service/.../config/SecurityConfig.java` (≈ line 34): `/api/auth/**` still in `PUBLIC_PATHS` (permitAll) — harmless: permits a path with no route → still 404.
- `apps/gateway-service/.../security/RouteService.java` (≈ line 9): `if (path.startsWith("/api/auth")) return "auth-service";` — dead metrics/log-label branch; no live route feeds it.
- `apps/gateway-service/src/test/resources/application-integration-test.yml` (≈ 17–20): `id: auth-service-test`, `Path=/api/auth/**` → dead port `19999`; test scaffolding only.

## Scope

**In (required):** correct `public-routes.md` so it no longer advertises gateway-owned `/api/auth/**` routes; state that authentication moved to IAM OIDC.
**In (optional, only if bundled cleanly):** remove the dead `/api/auth/**` code references (SecurityConfig PUBLIC_PATHS, RouteService branch, integration-test yml route) — no functional change (no route → 404 today).
**Out:** the gateway route config itself (already correct); the retained `apps/auth-service/` source (kept for history per PROJECT.md); IAM OIDC integration.

## Acceptance Criteria

- **AC-0 (re-measure):** confirm on `main` that (a) `public-routes.md` still lists the three `/api/auth/**` public rows + the rate-limit reference, and (b) the gateway route config has **no** `/api/auth/**` route (the removal comment is present), and (c) a request to `/api/auth/login` would 404 (no route). Re-verify the file:line refs — code wins.
- **AC-1 (doc):** remove the three `/api/auth/**` rows (≈ L13–15) and the "Sensitive" tier `/api/auth/*` reference (≈ L32) from `public-routes.md`; add a note that authentication is IAM/GAP OIDC (`/oauth2/authorize`), citing `TASK-BE-132` + `TASK-MONO-027`, mirroring the `application.yml` removal comment. If the OIDC surface (`/oauth/**`, `/oauth2/**`) is public at the gateway (see `SecurityConfig` PUBLIC_PATHS), the spec should describe THAT instead of `/api/auth/**`.
- **AC-2 (optional code cleanup — decide + record):** either (a) drop the dead `/api/auth/**` references in `SecurityConfig.java`, `RouteService.java`, and the integration-test yml (leaving no dangling auth-route artifacts), or (b) explicitly defer them as harmless-and-out-of-scope in this task's close note. Do not leave the decision implicit.
- **AC-3:** if AC-2(a) is taken, `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:check` green (the integration-test yml route removal must not break the gateway IT) — CI Linux authority.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/gateway-service/public-routes.md` (the stale spec).
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` — the IAM OIDC surface that replaced `/api/auth/**`.
- `projects/ecommerce-microservices-platform/PROJECT.md` § auth-service RETIRED.

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/services/gateway-service/public-routes.md`.

## Edge Cases

- The retained `apps/auth-service/` source (excluded from `settings.gradle`) still has tests hitting `/api/auth/*` — those run against the standalone auth-service module, not the gateway; do NOT delete that source (PROJECT.md keeps it for history).
- If any FE or client doc still points users at `/api/auth/login`, note it — but the OIDC migration surface is out of scope here (this task only reconciles the gateway public-routes spec).

## Failure Scenarios

- **Treating this as a code routing defect:** the gateway routing is already correct (route removed) — a "fix" that re-adds or re-points `/api/auth/**` would reintroduce a dangling route. The only real drift is the spec advertising routes the gateway no longer serves.
- **Doc-fix that leaves the dead code implicitly:** the `/api/auth/**` PUBLIC_PATHS permit + RouteService branch + test-yml route keep confusing the next reader; AC-2 forces an explicit remove-or-defer decision.
