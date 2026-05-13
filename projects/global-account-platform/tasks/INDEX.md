# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-INT-XXX`: integration

(`TASK-FE-XXX` is reserved but not used in this backend-only project.)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
- Do not modify a task file after it moves to `review/` or `done/`.

## Move Method (all transitions)
All task file moves between directories must use `git mv`, never copy-then-delete or Write-new-file.
Example: `git mv tasks/review/TASK-BE-001.md tasks/done/TASK-BE-001.md`
Rationale: preserves git history and prevents duplicate files across directories.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty — tasks will be added after TASK-BE-000 cleanup is merged)

## ready

- `TASK-BE-278-account-service-v0013-flyway-migration-fail-on-fresh-db.md` — TASK-MONO-082 (PR #442, cycle 3 auth-service JWT keys fix) 머지 후 다섯 번째 nightly run `25776967635` (push `bdc00b40`, 2026-05-13 03:45 UTC) 의 gap-e2e-full 16m 54s fail 의 cycle 6 root cause. account-service 첫 boot 에서 V0013 ("rebuild account roles composite pk") Flyway migration 가 mid-execution fail → flyway_schema_history 에 failed record → 두 번째 boot 부터는 `FlywayValidateException: Detected failed migration to version 0013` validation fail. ComposeFixture HEALTH_TIMEOUT 5min × 3 class = 16m 54s. cycle 5 docker compose ps: auth/security/admin/gateway `Up (healthy)` (cycle 3 fix 검증 완료) + account `Exited (1)`. 진단 plan: 첫 boot 시점의 actual SQL error 식별 (Phase 0 강화 또는 local 재현 권장) → 가설 B1 (TEMPORARY TABLE implicit-commit) > B2 (composite FK) > B3 (V0012 잔재) > B4 (docker-compose mysql config) 별 minimal fix. V0013 의 TEMPORARY TABLE 패턴은 fresh DB 에서 0 row 보존 무의미 → 단순화 후보. PR-time IT (Testcontainers) 와 동시 호환 필요. ADR-MONO-011 § D5 audit-trail 누적 4차 (option C-1, TASK-080/081/082 패턴 답습). 분석=Opus 4.7 / 구현 권장=Opus 4.7.

Cross-project (root `tasks/done/`): TASK-MONO-019 APPROVED 2026-05-02. TASK-MONO-046-7/7a/8/8a closed 2026-05-08~09. BE-272/273/274 closed 2026-05-09 (PR #292/#294/#296 모두 main 머지 완료). **TASK-MONO-079/080/081/082 closed 2026-05-13** (Phase 3 nightly full e2e 4 cycle 누적, gap-e2e-full cycle 6 잔존이 본 TASK-BE-278 의 scope).

## in-progress

(empty)

## review

(empty — TASK-BE-271 reviewed and moved to done on 2026-05-02)

## done

- `TASK-BE-277-auth-credentials-social-identities-data-model-backfill.md` — PR #342 머지 (2026-05-11). auth-service `credentials` + `social_identities` 두 table 의 data-model.md 백필. 컬럼 / NOT NULL / PK / FK / 인덱스 / 보안 노트 (bcrypt cost, AES-GCM column 명시). spec-only.

- `TASK-BE-276-auth-service-refresh-tokens-tenant-id-doc.md` — PR #335 머지 (2026-05-11). `refresh_tokens.tenant_id` column 명시 + `jti` 길이 widening (255→500) backfill. data-model.md 표 + 인덱스 + multi-tenant Phase 2/3 NOT NULL 보장 명시. /refactor-spec all (PR #326) Finding [GAP 2] closure.

- `TASK-BE-275-admin-web-frontend-app-section-pattern.md` — PR #334 머지 (2026-05-11). admin-web/architecture.md 를 backend 6 service canonical pattern (Service / Service Type / Architecture Style / Internal Structure / Allowed Dependencies / ...) 으로 재정렬. frontend-app service-type canonical pattern 확립 — sibling frontend-app (web-store / admin-dashboard / fan-platform-web) 와 정합. /refactor-spec all Finding [GAP 1] closure.

- `TASK-BE-274-sas-refresh-token-provider-side-fallback.md` — PR #296 머지 (2026-05-09, commit `48c9edc1`). Self-verdict: **APPROVED**. cycle 3/6 사용 (남은 3 미사용). **Cluster A 3/3 완전 회복** (RT 2 + revoke). Phase 1 진단 (provider line 233+237 dual-INSERT 식별) → Phase 2 cycle 1 (`172216b8` skip-path TSM flag, race 해소) → cycle 2 (`a83a4d12` TransactionTemplate publish in-tx, A3 회피) → cycle 3 (`7e7719c9` TenantClaimTokenCustomizer REFRESH_TOKEN 분기 누락 = 기존 결함 unmasked + fix). 회귀 0, AC-01~07 모두 충족. ADR-003 status `ACCEPTED — partial` → `ACCEPTED — 옵션 B closure`. **누적 deferred IT 회복 = 9/8** (8 + token customizer bonus). 4 anti-pattern: A2 architectural 해소, A1/A3/A4 회피. 분석=Opus 4.7 / 구현=Opus 4.7 (Phase 1+2) + Sonnet 4.6 (Phase 2 cycle 3 customizer fix).
- `TASK-BE-273-oauth-callback-ci-linux-503-diagnostic.md` — PR #294 머지 (2026-05-09, commit `e7e9f08f`). Self-verdict: **APPROVED**. 8 file / +307 -14 lines / **OAuthLoginIntegrationTest 5/5 disabled IT method 완전 회복** (Google/Kakao/Microsoft happy + Microsoft preferredUsername fallback + Microsoft existing email auto-link). Phase 1 (`f8742134` log 강화 + WireMock listener) → 원래 run 5/5 PASS / retry 1 5/5 FAIL → flaky 확정 + RC 식별 (`Received RST_STREAM: Stream cancelled` = JDK HttpClient HTTP/2 multiplexing race). Phase 2 옵션 1 (`ab52b8b4` HTTP/1.1 강제, 4 client + libs/java-common ResilienceClientFactory DRY) 구현 후 GAP Integration 5m12s PASS. ADR-004 status `PROPOSED` → `ACCEPTED — 옵션 1` + Phase 1 Findings + Phase 2 Outcome 섹션 추가 (`8b62be41`). 13-cycle 미해결 RC 가 1 cycle 강화 log + 1 cycle simple patch 로 종결 — 메모리 메타학습 (local PASS / CI FAIL split → diagnostic harness 우선) ROI 정점 사례. 분석=Opus 4.7 / 구현=Opus 4.7 (Phase 1 진단) + Sonnet 4.6 (Phase 2 옵션 1 패치).
- `TASK-BE-272-public-client-refresh-token-revoke-converter.md` — PR #292 머지 (2026-05-09, commit `d05bef23`). Self-verdict: **PARTIAL — APPROVED with follow-up**. 5 cycle / **public-client RT/revoke converter 신설 + Cluster A revoke 1/3 회복**, RT 2 method 는 A2 anti-pattern (`idx_rt_jti` dual-INSERT race) 재발로 재@Disabled. ADR-003 status `PROPOSED` → `ACCEPTED — partial (Cluster A 1/3 recovered, RT 2 deferred to TASK-BE-274 옵션 B)`. 회귀 매트릭스 6/8 (RT 2 deferred). 4 anti-pattern 평가: A1/A3/A4 회피, A2 재발 (도메인 영역, scope 외). Cycle 추적: cycle 1 (slot 잘못, CI 401), cycle 2 (slot 이동, CI 400), cycle 3 (NoPkceProvider 추가, revoke 회복), cycle 4 (A1 회피, RT NPE→A2), cycle 5 (RT 2 재disable, CI GREEN). 도메인 follow-up = TASK-BE-274 (옵션 B provider-side fallback). 분석=Opus 4.7 / 구현=Opus 4.7 (backend-engineer agent dispatch).

264 backend tasks + 26 frontend tasks completed. Latest BE batch (2026-05-01..02):
TASK-BE-248 (security tenant events), TASK-BE-249 (admin tenant audit schema),
TASK-BE-250 (admin tenant lifecycle API), TASK-BE-251 (Spring Authorization Server),
TASK-BE-252 (OAuth schema), TASK-BE-256 (tenant onboarding API contract), and
follow-ups 260/261/262/268.
Newly reviewed (2026-05-02): TASK-BE-254 (consumer integration guide,
FIX_NEEDED → 263), TASK-BE-255 (account_roles schema, FIX_NEEDED → 265),
TASK-BE-259 (auth.token.reuse.detected tenant_id, APPROVED), TASK-BE-263
(auth-api.md discovery, APPROVED), TASK-BE-265 (255 review fix, FIX_NEEDED → 267),
TASK-BE-267 (267 review, APPROVED), TASK-BE-253 (community/membership OIDC RS,
FIX_NEEDED → 269), TASK-BE-269 (269 OAuth2 WebClient timeout fix, APPROVED),
TASK-BE-258 (GDPR downstream contract + security ref impl, FIX_NEEDED → 270),
TASK-BE-270 (258 review fix: salt + data-model.md, APPROVED), TASK-BE-257
(bulk provisioning API, FIX_NEEDED → 271), TASK-BE-271 (257 review fix:
readOnly tx + enum + BULK_LIMIT routing, APPROVED).
Numbers TASK-BE-238/239/240/244 were not assigned.
