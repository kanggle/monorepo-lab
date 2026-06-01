# TASK-INT-023: web-store real-GAP e2e — RP-initiated logout AC-1 automation

> **Status: DONE (2026-06-01)** — impl PR #1004 (squash `73b7b01c`). Lean real-GAP OIDC stack for the web-store logout AC-1, closing the TASK-FE-070 live-verification gap. **핵심 발견**: web-store 로그인은 `auth_db.credentials`만 필요(SAS form-login, account-service·account_type 클레임 불필요) → 4컨테이너 lean 스택 + 시드 1행. `docker-compose.gap-e2e.yml` + consumer 시드 + `loginAsSeededConsumer`(실 Spring `#username`/`#password` 폼) + `rp-initiated-logout.spec.ts`(`shouldSkipGap()` 게이트 → 기존 CI 무회귀) + playwright NEXTAUTH_URL + CI 핸드오프 문서(`.github` classifier 차단 → 사용자 적용). **라이브 실증**: 실행 중 federation-e2e GAP(V0012 web-store client + BE-328 매퍼) 대상 `1 passed (51.0s)` — 로그인→로그아웃→재로그인 GAP `#username` 폼 재노출. compose config + tsc clean. 3차원 ✓. 공유 federated-logout 패턴(fan/admin/console) transitively 검증. **메타: NextAuth v5 federated logout = `getToken`(@auth/core) server-only id_token; Next `output:standalone` 빌드는 Windows symlink 권한 필요(dev 우회); GAP `/login`=Spring 기본 폼(signup-or-login 아님).** 분석=Opus 4.8 / 구현=Opus.

## Goal

Stand up a LEAN real-GAP OIDC stack for the web-store Playwright suite so the RP-initiated logout (`end_session`) AC-1 — login → logout → re-login re-presents the GAP credential form (no silent re-auth) — can be verified end-to-end against a real IdP, closing the live-verification gap left open by TASK-FE-070 (the web-store logout fix shipped under decision (A): merged + CI-green but not browser-verified).

This revives the piece TASK-MONO-014 explicitly deferred ("Adding a GAP container to the web-store e2e docker-compose"), but lean: the existing `SKIP_GAP_E2E=1` CRUD specs are NOT un-skipped here — only the new logout AC-1 spec runs GAP-backed.

## Background / why lean

- web-store login uses GAP's SAS form-login (`CredentialAuthenticationProvider` reads `auth_db.credentials` only — no account-service call). The issued OIDC token carries `tenant_id` but NO `account_type` claim (the GAP pipeline does not emit it), and web-store's NextAuth `signIn` callback ACCEPTS an absent `account_type`. So a CONSUMER can log in from a single seeded `auth_db.credentials` row — **no account-service / admin-service / producers / full ecommerce backend needed**.
- GAP's `/login` is the Spring Security DEFAULT credential form (`#username` + `#password` + hidden `_csrf` + submit) — NOT the richer "signup-or-login" page the legacy `auth.ts` docstring assumed.

## Scope

- `docker-compose.gap-e2e.yml` (NEW, ecommerce root): lean GAP stack — `mysql` (auth_db, GAP `init.sh`) + `redis` + DNS-only `kafka` placeholder + `auth-service` (reuses the federation-e2e service blocks; needs `:apps:auth-service:bootJar` prebuilt; publishes 8081; test RSA keys mounted).
- `apps/web-store/e2e/fixtures/gap-consumer-seed.sql` (NEW): one idempotent `auth_db.credentials` row (`tenant_id='ecommerce'`, `e2e-consumer@example.com`, password `devpassword123!` via the shared Argon2id hash). Applied after auth-service Flyway, before the Playwright run.
- `apps/web-store/e2e/helpers/auth.ts`: add `SEEDED_CONSUMER` + `loginAsSeededConsumer` + `fillGapCredentialForm` (drive GAP's real Spring-default `#username`/`#password` form). Legacy `completeGapSignIn` kept for the still-skipped CRUD specs; its docstring corrected.
- `apps/web-store/e2e/rp-initiated-logout.spec.ts` (NEW): the AC-1 spec, gated by `shouldSkipGap()` (so the current `SKIP_GAP_E2E=1` CI runs skip it — no breakage).
- `apps/web-store/playwright.config.ts`: pass `NEXTAUTH_URL` through the webServer env (the GAP-backed run overrides `OIDC_ISSUER_URL=http://auth-service:8081` + `SKIP_GAP_E2E=0`).
- **CI wiring** (a new GAP-backed nightly job: build GAP bootJar → `docker compose -f docker-compose.gap-e2e.yml up` → apply seed → `/etc/hosts 127.0.0.1 auth-service` → run the logout spec with `SKIP_GAP_E2E=0`): authored as a **handed-over patch** for the maintainer to apply — `.github/workflows/` edits are classifier-blocked for the agent.

## Acceptance Criteria

- **AC-1** The logout spec passes against a real GAP: login as the seeded consumer → logout → re-login re-presents the GAP `#username` credential form (IdP session terminated, no silent re-auth). **VERIFIED locally** against the running federation-hardening-e2e GAP (V0012 web-store client + BE-328 mapper): `1 passed (51.0s)`.
- **AC-2** The new spec is gated by `SKIP_GAP_E2E` so existing CI (default `=1`) skips it — no regression to the nightly frontend-e2e job.
- **AC-3** `docker compose -f docker-compose.gap-e2e.yml config` validates; web-store `tsc --noEmit` stays clean.
- **AC-4** No account-service / full ecommerce backend required (lean stack proven sufficient).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/gap-integration.md`
- `projects/global-account-platform/specs/contracts/auth-api.md` (§ OIDC `end_session` / `/connect/logout`)

## Related Contracts

- GAP V0012 `ecommerce-web-store-client` (redirect_uris, post-logout-redirect-uris — made effective by TASK-BE-328).

## Edge Cases

- GAP `/login` is the Spring-default form (`#username`/`#password`), not the assumed signup-or-login page — the new helper targets the real fields.
- `auth_db.credentials` composite UK is `(tenant_id, email)`; seed is `INSERT IGNORE` (re-runnable).
- Issuer host `auth-service` must resolve on the host for BOTH the browser redirect and the Next.js server-side discovery/token calls → `/etc/hosts 127.0.0.1 auth-service` (PC-FE-028 constraint).
- `next build` (Next `output: 'standalone'`) fails on Windows without symlink permission → local verification used `next dev`; CI (Linux) uses the standard build/start.

## Failure Scenarios

- auth-service bootJar missing → Dockerfile build fails fast with the documented `./gradlew :apps:auth-service:bootJar` hint.
- Seed not applied before the run → login fails (no credential) → AC-1 fails loudly.

## Dependency Markers

- **closes the AC-1 follow-up** for: TASK-FE-070 (web-store), and transitively validates the shared `federated-logout` pattern used by TASK-FAN-FE-002 (fan) + TASK-FE-071 (admin-dashboard) + TASK-PC-FE-033 (console).
- **depends on**: TASK-BE-328 (post-logout-redirect-uris effective on the SAS RegisteredClient). On `origin/main`.
