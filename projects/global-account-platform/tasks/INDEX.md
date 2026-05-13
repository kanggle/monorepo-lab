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

(empty)

## in-progress

(empty)

Cross-project (root `tasks/done/`): TASK-MONO-019 APPROVED 2026-05-02. TASK-MONO-046-7/7a/8/8a closed 2026-05-08~09. BE-272/273/274 closed 2026-05-09 (PR #292/#294/#296 모두 main 머지 완료). **TASK-MONO-079/080/081/082 + TASK-BE-278/279 closed 2026-05-13 — Phase 3 nightly full e2e 5/5 GREEN 완전 종결** (7 cycle archaeological inspection: settings.gradle + boot jars + JWT keys + Phase 0 진단 + MySQL TEMPORARY TABLES privilege + e2e test seed schema 모두 해소).

## review

- `TASK-BE-282-account-wms-tenant-seed.md` — `V0016__seed_wms_tenant.sql` 추가 (`INSERT IGNORE INTO tenants ('wms', 'Warehouse Management Platform', 'B2B_ENTERPRISE', 'ACTIVE', ...)`, V0015 scm pattern 답습). **TASK-MONO-088 PR-time first-call validation cycle 8 root cause closure** — `TenantProvisioningE2ETest` 의 wms tenant FK 위배 root cause. v1 "wms = service-to-service only" 가정 reverse (2026-05-14 design decision). Cycle 8 가 nightly 8 days 대신 PR-time ~30분 cycle 1 회 surface — 7-cycle archaeology PR-time 가속 패턴 답습. mechanical 1-line single-PR closure. 분석=Opus 4.7 / 구현=Opus 4.7.

## done

- `TASK-BE-281-auth-service-architecture-tenant-info-sync.md` — spec PR #460 (squash `7e2d33ab`) + impl PR #461 (squash `14a557d7`) 머지 (2026-05-14). **`/refactor-spec all --dry-run` (2026-05-13~14) GAP audit critical #4 finding closure** — auth-service/architecture.md 3 영역 (L157 § application + L168 § infrastructure + L174 § Integration Rules) 의 `AccountServiceClient.lookupCredential(email)` + "(갱신 시 확정)" stale 표현이 contract `auth-to-account.md` (TASK-BE-229 `GET /internal/accounts/tenant-info`, array response 0/1/N + presentation LOGIN_TENANT_AMBIGUOUS) 결정 사항과 byte-level align. (a) L157 → 단계 (1) local `CredentialRepository` (TASK-BE-063) + (2) `lookupTenantInfo(email, tenantId?)` (TASK-BE-229) + (3) array length 0=ACCOUNT_NOT_FOUND / 1=정상 / ≥2=presentation LOGIN_TENANT_AMBIGUOUS 변환 명시 / (4) 토큰 발급 / (5) 이벤트 발행. (b) L168 § infrastructure/ "credential lookup 응답" → "tenant-info lookup 응답 (array shape)" + credential local 명시. (c) L174 § Integration Rules "credential lookup" → "tenant-info lookup (array shape, TASK-BE-229)". 3 file / +16 / -16 (architecture.md spec wording 6 line + task spec lifecycle + AC 22 line + gap INDEX 4 line). production code = 0. **Impl PR CI = 15 SKIP + 1 changes PASS** (markdown-only path-filter, TASK-MONO-074 axis A 자연 검증). lifecycle = ready → review 직접 (in-progress 우회, spec-only single-PR closure 패턴 첫 적용). **Audit CRIT-3 (auth-to-account.md L9 deprecated endpoint inline note) = false positive** — inline note 이미 충분, fix 미적용. Sibling 답습 패턴 = TASK-BE-280 (PROJECT.md service_types sync, PR #449) + TASK-MONO-083 (platform jwt-standard-claims, PR #455) + TASK-SCM-BE-011 (SCM envelope align, PR #458). 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-BE-280-auth-service-identity-platform-project-md-frontmatter-sync.md` — impl PR #449 머지 (2026-05-13, squash commit `60462d84`). `/refactor-spec all --dry-run` (2026-05-13) GAP audit Top 3 critical (HARDSTOP-02/10 risk) closure. `PROJECT.md:5` frontmatter `service_types` array 에 `identity-platform` 추가 (`[rest-api, event-consumer, frontend-app]` → `[..., identity-platform]`) + Service Map table line 63 의 auth-service row 갱신 (rest-api → identity-platform, OIDC AS 책임 명시 + ADR-001 ACCEPTED 반영). 2026-05-11 service-type 정정 (architecture.md history line 13: 2026-04-30 rest-api → 2026-05-02 ADR-001 + SAS → 2026-05-09 BE-272/273/274 closure → 2026-05-11 identity-platform) 의 PROJECT.md 동기. 1 file / +2 / -2. production code 0. CI 15 SKIP + 1 changes PASS (markdown-only, TASK-MONO-074 axis A 11번째 자연 검증). HARDSTOP-02/10 hook PASS. admin-service self-IdP service type promote 후보는 follow-up audit task 분리. 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-BE-279-cross-service-bulk-lock-e2e-tenant-id-seed.md` — impl PR #446 머지 (2026-05-13, squash commit `c4872336`). **Full close — Phase 3 5/5 GREEN 완성** 🎉. TASK-BE-278 cycle 7 spawn 의 결정적 root cause = `CrossServiceBulkLockE2ETest.seedAccount` (line 90) 의 direct-SQL INSERT 가 `tenant_id` column 명시 안 함. V0011 의 NOT NULL default 제거 후 MySQL strict mode 에서 `Field 'tenant_id' doesn't have a default value` throw. test code 영역 (production code 무관), 1 file / +10 / -5 (column 추가 + inline comment). **Fix**: INSERT 에 `tenant_id` column + 'fan-platform' value 추가 (V0009 seed + V0010-era historical default). PR-time IT 영향 0 (@Tag("full") nightly-only). **재검증 (push-to-main trigger 일곱 번째 nightly run `25779144374`)**: **gap-e2e-full 3m 53s GREEN 첫 SUCCESS** ✅ — 4 backend full job (**wms 59s / fan 66s / scm 63s / gap 3m 53s**) + frontend-e2e-fullstack 7m 12s + ecommerce-boot-jars-nightly 84s **모두 SUCCESS = Phase 3 5/5 완전 종결**. 7 cycle archaeological inspection 종결: (1) settings.gradle 미등록 + (2) boot jars 부재 + (3) JWT keys 부재 + Phase 0 진단 + Phase 1 검증 + (6) MySQL CREATE TEMPORARY TABLES privilege 부재 + (7) e2e test seed code schema 불일치. ADR-MONO-011 § D5 audit-trail 누적 5차 closure (option C-1, TASK-080/081/082/BE-278 패턴 답습). 분석=Opus 4.7 / 구현=Opus 4.7 (cycle 7 진단 + fix).

- `TASK-BE-278-account-service-v0013-flyway-migration-fail-on-fresh-db.md` — impl PR #444 머지 (2026-05-13, squash commit `6d2bb874`). **Partial close** — cycle 6 (MySQL 8.0 `CREATE TEMPORARY TABLES` privilege 부재) 해소 + cycle 7 (CrossServiceBulkLockE2ETest.seedAccount missing tenant_id, test code 영역) 잔존 발견 → TASK-BE-279 분리. **결정적 root cause** = V0013 step 2 `CREATE TEMPORARY TABLE account_roles_backup AS SELECT FROM account_roles` 가 account_user 권한 부족으로 access denied → Flyway "failed" record → 다음 boot validate fail. PR-time IT (Testcontainers default root-equivalent) 와의 결정적 차이. **Fix**: `docker/mysql/init.sh` 의 `SERVICE_PRIVILEGES` 에 `CREATE TEMPORARY TABLES` 추가 (1 line, 6 service user 모두 적용). 1 file / +11 / -1. production code 0. **CI 15 PASS + 1 SKIP** (Observability path-filter skip 정상). **재검증 (push-to-main trigger 여섯 번째 nightly run `25778172254`)**: gap-e2e-full **3m 8s fail (cycle 7)** — 이전 16-17min 패턴이 사라져 cycle 6 fix 검증 완료, **ComposeFixture + V0013 + 5 service healthy 모두 정상** (auth/account/security/admin/gateway 5/5 healthy, V0013 PASS). 3 e2e test 실행 → 2 PASS (DlqHandling + RefreshReuseDetection) + 1 FAIL (CrossServiceBulkLock, `tenant_id` direct-SQL seed 누락). 다른 backend full job + frontend-e2e-fullstack + ecommerce-boot-jars-nightly 모두 SUCCESS (Phase 3 4/5 유지). cycle 7 fix = TASK-BE-279. **ADR-MONO-011 § D5 audit-trail 누적 4차 closure** (option C-1, TASK-080/081/082 패턴 답습). 분석=Opus 4.7 / 구현=Opus 4.7.

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
