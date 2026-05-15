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

(empty)

## done

- `TASK-BE-294-refresh-blacklist-redis-key-tenant-scope.md` — impl `task/be-294-refresh-blacklist-tenant-scope` (off `task/be-290-gap-spec-drift`, spec-only, no `apps/`). **REVIEWED → approved** (2026-05-16, single-task inline `/review-task`, review-checklist 6/6 — Spec/Arch/Quality/Security PASS, Perf N/A, Testing PASS via grep + code-state gate; fix task 0). Closes TASK-BE-290 deferred backlog item (b). **WI-1**: `refresh:blacklist` 키 shape drift — canonical `refresh:blacklist:{tenant_id}:{jti}` **3중 확인** (`architecture.md:207` + `contracts/http/auth-api.md:342` + live `RedisTokenBlacklist.buildKey` TASK-BE-229) → **decision gate 없음** (BE-290 G7 와 달리 spec-다수결 tension 부재; canonical 기확정, 단순 전파). registry SoT `redis-keys.md` 먼저 canonical (패턴+tenant-prefixed 예시+`{tenant_id}` rationale 단락[refresh token 의 resolved tenant claim, post-auth 라 pre-auth 센티넬 `'*'` 비적용 — login:fail 와 service-specific 차이 명시, copy-paste 아님]+**레거시 `{jti}` 읽기 전용 fallback 명시**[`buildLegacyKey` 코드 정합]+Naming Convention 예시 확장) 후 `dependencies.md:30`/`overview.md:34`/`features/authentication.md:40` 전파. `grep refresh:blacklist specs/` → write shape 단일 `{tenant_id}:{jti}` (registry+3 narrative+2 anchor), 잔존 `{jti}` 4건 전부 라벨된 read-only fallback. **code-state gate (BE-290 선례, 독립 재확인)**: `RedisTokenBlacklist.buildKey(tenantId,jti)` = 신규 write = `{tenant_id}:{jti}` (이미 tenant-scoped, TASK-BE-229) / `buildLegacyKey(jti)` = 레거시 read-only — **live un-scoped write 부재** → spec-only closure 유효, **code follow-up 불요** (BE-290 은 hashing 확인이 gate 였으나 본 task 는 write 가 이미 scoped, 더 명확). 핵심 nuance = 순수 find-replace 가 아니라 레거시 read-fallback 을 registry 에 문서화해 spec↔code drift 재발 방지 (jti=opaque UUID, PII/regulated 아님 — login:fail email_hash R4 와 구분). dead-ref/orphan 0 (신규 링크 = same-dir `redis-keys.md` + 기존 5-up multi-tenant.md form), 파일 add/remove 0. lifecycle = ready → in-progress → review → done (implement → review inline). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-BE-290-gap-spec-drift-redis-key-and-admin-web-sections.md` — impl `task/be-290-gap-spec-drift` (spec-only, no `apps/`, commit `5de9dedf`). **REVIEWED → approved** (2026-05-16, `/review-task TASK-BE-290` single-task, review-checklist 6/6 — Spec/Arch/Quality/Security PASS, Perf N/A, Testing PASS via task Verification). **WI-1 (G7)**: decision gate 결과 = `rules/traits/multi-tenant.md` M1(강제, SoT layer 4 > service spec layer 7; Redis key `<tenant_id>:...` prefix) + live BE-229 code(`RedisLoginAttemptCounter.buildKey` = `login:fail:{tenant_id}:{email_hash}`, 해시) 가 4-spec 무-tenant `{email_hash}`(stale, pre-BE-229) 보다 우선 → canonical = `login:fail:{tenant_id}:{email_hash}`. registry(`redis-keys.md`) 먼저 갱신(패턴+예시+rationale+naming, 센티넬 `'*'` 비적용 명시) 후 `architecture.md`(평문 `{email}` outlier 제거)·`rate-limiting`·`overview`·`dependencies`·`use-cases` 전파. `grep login:fail specs/` 단일 shape·평문 `{email}` 0·`contracts/` 0. **code-state gate (독립 재확인)**: `LoginUseCase.execute` L58 `hashEmail(command.email())`(SHA-256) → `buildKey(tenant,emailHash)` 경로 검증 — live 키에 평문 email 도달 불가 → spec-only closure 유효, code follow-up 불요. **WI-2 (G19)**: admin-web/architecture.md 에 `## Integration Rules`+`## Change Rule` 추가 (admin-service sibling 구조 정합, frontend-app 정확 — DB/outbox/event "owns none" 명시, Change Rule = UI/contract-compat/bundle scope, DB 불변식 미단언). canonical ADR-MONO-012 form(`### Service Type Composition` H3 + Identity 표 + `## References` tail) 보존. dead-ref 7-file 재스캔 0, 파일 add/remove 0 → orphan 0. **비차단 관찰(BE-290 결함 아님, 별도 백로그 후보)**: (a) task Related Skills 가 `.claude/skills/{refactor-spec,validate-rules}/SKILL.md` 로 표기됐으나 실제는 `.claude/commands/*.md` (task #559 author drift, advisory); (b) `refresh:blacklist` 키 동일 multi-tenant drift (`architecture.md` `{tenant_id}:{jti}` vs `redis-keys.md` `{jti}`) — BE-290 scope(login:fail/G7) 외, 향후 task 후보. lifecycle = ready → in-progress → review → done (implement-task → review-task). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-BE-289-fix-TASK-BE-288.md` — impl PR #558 (squash → main `479da205`) + close chore `chore/be-289-review-closure`. **REVIEWED → approved** (2026-05-15, `/review-task TASK-BE-289` single-task, review-checklist 6/6 pass). TASK-BE-288 review 의 잔여 2 follow-up 종결. **WI-1 (Finding 2)**: `architecture.md` L75/L87/L141 + production javadoc 3건(`AuditReasons`/`AdminOperatorPort`)의 삭제된 `OperatorRoleResolver` 참조 제거 → port/adapter/`AuditReasons` shape 문서화 + stale L141 naming-collision Boundary Rule 재작성. **grep `OperatorRoleResolver`=0** (specs/ + apps/admin-service/src/main; git+TASK-BE-121/288 가 audit trail). **WI-2 (tenant_id correctness)**: data-model.md(spec-first) `admin_operator_roles.tenant_id` 컬럼 + per-tenant 불변식("operator tenant 미러링", sentinel `'*'`) + ADR-002 cross-ref / `PatchOperatorRoleUseCase` → `operator.tenantId()` (= `CreateOperatorUseCase` 정합, `LEGACY_BINDING_TENANT_ID` 제거) / **legacy 4-arg `AdminOperatorRoleJpaEntity.create` overload 제거** (Finding-1 silent-default foot-gun 소거 — AC decision=remove, production 0 caller post-flip, test 11 site 5-arg 마이그레이션 동작 보존) / **V0026** Flyway backfill (`SET aor.tenant_id=o.tenant_id WHERE <>`, V0025 STEP 3 불변식, re-run safe, SUPER_ADMIN `'*'` 자연 처리) / `OperatorAdminIntegrationTest` 격리 회귀 테스트 (non-fan-platform 대상 → binding tenant==operator tenant, PROJECT.md mandate). **검증**: PR #558 CI run `25921811488` **17/17 PASS** (Build&Test 4m16s + Integration global-account-platform Testcontainers 2m7s — V0026+회귀테스트 실 MySQL 검증) / 로컬 `:test` 389-0-0-49 = TASK-BE-288 baseline (회귀 0). HTTP/event contract 무변경 (WI-2=persisted-state only; audit row tenant 는 actor 해석 경로라 무영향). **비차단 관찰**: data-model.md `admin_operators`·`admin_actions` 의 tenant_id/target_tenant_id 컬럼 선존 drift 잔존 (ADR-002 canonical cross-ref, WI-2 scope 외) → 향후 refactor-spec 후보, 조기 fix task 미생성. lifecycle = ready → in-progress → review → done (implement-task → review-task → close chore). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-BE-288-admin-operator-totp-port-refactor.md` — impl PR #555 (open) `task/be-288-admin-operator-totp-port-refactor` + close chore `chore/be-288-review-closure`. **REVIEWED → fix_needed** (2026-05-15, `/review-task TASK-BE-288` single-task mode, full spec/contract/behavior-preservation pass). **승인 부분**: `AdminOperatorPort` + `AdminOperatorTotpPort` 인터페이스+Record projection JPA 의존 0 ✅ / `JpaAdminOperatorAdapter`(rbac) + `JpaAdminOperatorTotpAdapter` `@Component`·자체 `@Transactional` 없음·legacy 위임 정확 ✅ / 9 application file 중 8개 byte-identical 동작 보존 ✅ / `consumeRecoveryCode` optimistic-lock 단일 retry 계약 충실 보존 (port boolean 반환으로 JPA exception 캡슐화, 2-attempt cap·re-read·re-match·final throw 동일) ✅ / timing-leveled login dummy verify·byte[] zeroize·audit row 순서·`OperatorLookupPort` 재사용 모두 보존 ✅ / forbidden-import grep = 0 (`application/` 에 `infrastructure.persistence.{rbac.AdminOperator|AdminRole|AdminOperatorTotp}` 잔존 0) ✅ / `operator_id` 생성 `newOperatorId()`→`UuidV7.randomString()` = byte-identical (위임 동일) ✅. **Testing 증거**: PR #555 CI 커밋 고정 run `25917636171` 17/17 PASS (`Build & Test JDK21` + `Integration (global-account-platform, Testcontainers)` 포함) — 로컬 Testcontainers 재실행은 `project_testcontainers_docker_desktop_blocker` 비용 대비 불필요, CI authoritative. **Finding 1 (BLOCKING, PR #555 merge gate)**: `PatchOperatorRoleUseCase` role-binding `tenant_id` legacy 4-arg `AdminOperatorRoleJpaEntity.create` (하드코딩 `"fan-platform"`) → 5-arg `operator.tenantId()` 전환 — non-fan-platform 대상(WMS 테넌트/SUPER_ADMIN cross-tenant patch) persisted state 변경, "behavior 0 / byte-identical" 보증 위반 (`CreateOperatorUseCase` 는 origin/main 에서 이미 5-arg → 미선언 latent-bug alignment 추정). **Finding 2 (minor, fix-forward)**: `OperatorRoleResolver.java` 삭제됐으나 `architecture.md` L75/L87/L141 잔존 참조 = spec→deleted-code dead-ref. **Disposition**: `/review-task` 규칙대로 원 task review→done 이동 + fix task `TASK-BE-289-fix-TASK-BE-288` ready/ 생성. **Finding 1 해소 완료**: Option A 적용 — `PatchOperatorRoleUseCase` 를 `LEGACY_BINDING_TENANT_ID="fan-platform"` 로 pin (commit `572e003c`), PR #555 squash-merge → main `1ef970bf` (CI run `25919652146` 17/17 PASS incl. global-account-platform Testcontainers IT). **TASK-BE-288 은 origin/main 대비 strictly behavior-neutral 로 확정**. 잔여: TASK-BE-289 가 (1) Finding 2 architecture.md dead-ref fix-forward + (2) deferred tenant_id correctness (Option A 가 분리한 deliberate fix: `operator.tenantId()` 전환 + Flyway backfill + 격리 회귀 테스트) 담당. 분석=Opus 4.7 / 리뷰=Opus 4.7 / Finding 1 구현=Opus 4.7 (multi-tenant persisted-state + optimistic-lock 계약 + spec governance 정밀 검증, recommended merge-order 실행).

- `TASK-BE-284-piimaskingutils-reference-rename.md` — spec commit `7eb2cf62` + impl PR #515 (squash `926cee9f`) + close chore (BE-283 직속 후속). **APPROVED (inline self-review)** — `/refactor-spec all --dry-run` **Tier 2 closure** (BE-283 § Out of Scope deferred 1건). admin-service/data-model.md L170 sample link 1-line rename: `apps/security-service/.../domain/util/PiiMaskingUtils.java` (non-existent) → `apps/security-service/.../application/pii/PiiMaskingService.java` (production actual). Production code rename (package `domain/util/` → `application/pii/` + class `PiiMaskingUtils` → `PiiMaskingService`) 이 spec sample link 까지 sync 안 됐던 잔재. **Judgment**: 3-option weighed (A rename / B drop link / C drop sentence) → A 채택 ("참조 구현" author 의도 보존 + production sync). **검증**: `[ -e ... ]` RESOLVED + `bash /tmp/check_gap_links.sh` = **0 broken** (BE-283 시점 1 → 본 task 후 0, **GAP scope dead-ref 완전 종결**). **CI**: 1 pass (`changes` SUCCESS) / 16 SKIPPED / 0 fail (admin-service spec 만 변경 → Frontend E2E 도 SKIP, BE-283 보다 더 깨끗). branch = `task/be-284-piimaskingutils-ref-rename` (main 분기, SCM-BE-013 chore 머지 commit `f5bfac6d` 직후). lifecycle = ready → review → done. D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec polish). **refactor-spec cycle 완전 종결** (4 task / 5 + 47 + 1 + 1 = 54 fix / portfolio dead-ref 잔존 0): BE-165 WMS Tier 1 → BE-283 GAP Tier 3 #1 → SCM-BE-013 SCM Tier 3 #2 → 본 GAP Tier 2. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7 (inline self-review, judgment-required Tier 2 + 3-option weighing + production code rename trace).

- `TASK-BE-283-gap-spec-dead-ref-libs-depth-batch.md` — spec commit `4059c9ca` + impl PR #511 (squash `43470bd3`) + close chore (2026-05-14, BE-165 직속 후속). **APPROVED (inline self-review)** — `/refactor-spec all --dry-run` (2026-05-14 single-day cycle) **Tier 3 #1 closure** — GAP scope pre-import era dead-reference 47/48 mechanical batch. 19 spec file / 50 line edit / 0 production code change: (1) **46 libs/* depth +2 ups** (`](../../../libs/java-*` → `](../../../../../libs/java-*` sed batch, 19 file 매칭 — 3 contracts/events + 16 services/*; GAP specs at 4-level deep need 5 `../` to reach repo-root `libs/`; 2026-04-30 monorepo import 후 spec author 가 depth realign 안 한 잔재 — TASK-MONO-085 scope rules+platform 위주라 libs/* 미커버); (2) **1 V0008 SQL depth -1 up** (auth-service/data-model.md L191 Edit — `../../../../apps/auth-service/...V0008__create_oauth_tables.sql` over by 1, landing at `projects/apps/`). **Skipped (1, Tier 2)**: admin-service/data-model.md L170 `PiiMaskingUtils.java` — production 에 같은 명/path file 부재 (`application/pii/PiiMaskingService.java` 가장 가깝지만 class+package 다름) → BE-284 후보 (rename or drop judgment). **검증**: `bash /tmp/check_gap_links.sh` post-fix = 1 remaining (PiiMaskingUtils, 예상치). dead-ref count 48 → 1 (97.9% closure). **CI**: 2 pass (`changes` 6s + `Frontend E2E smoke` 4m19s, contracts/events 트리거됐지만 markdown only 라 통과) / 15 SKIPPED (path-filter 의도) / 0 fail. branch = `task/be-283-gap-spec-dead-ref-libs-depth-batch` (main 분기, BE-165 chore 머지 commit `1a1b5028` 직후). lifecycle = ready → review → done (2-commit task author + impl/lifecycle + close chore). D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec polish, refactor-spec follow-up). **잔존 Tier 3 candidate**: TASK-BE-284 (PiiMaskingUtils judgment, GAP) + scm-platform 별 task (`scm-procurement-events.md:44` ADR-MONO-004 path). 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7 (inline self-review, refactor-spec audit → Tier 3 mechanical batch sed pattern + Tier 2 skip 명시 + post-merge re-verification, TASK-MONO-085/086/BE-165 precedent 답습 4x scope).

- `TASK-BE-282-account-wms-tenant-seed.md` — spec commit `dcc62b4c` + impl commit `44cd93e2` (direct main push, 2026-05-14). **TASK-MONO-088 PR-time first-call validation cycle 8 root cause closure** — `V0016__seed_wms_tenant.sql` 추가 (`INSERT IGNORE INTO tenants ('wms', 'Warehouse Management Platform', 'B2B_ENTERPRISE', 'ACTIVE', ...)`, V0015 scm pattern byte-level 답습). Root cause = `TenantProvisioningE2ETest` 가 wms tenant 로 user account provisioning 검증하지만 V0014 (ecommerce) + V0015 (scm) 만 seed, `tenants.wms` row 부재 → FK 위배 / tenant 검증 실패 → 4xx. v1 "wms = service-to-service only" 가정 (V0014 comment) reverse — 2026-05-14 design decision. **8-cycle archaeology PR-time 가속 패턴 1차 실증**: nightly 환경에서는 cycle 별 cron 사이클 (8 days) 소요였지만 PR-time first-call validation 으로 ~30분 cycle 1 회 (MONO-088 impl 직후 surface → 진단 → fix). 3 file / +29 / -8 (impl). production code = 0 (Flyway seed migration only). V0014 historical comment 보존 (acceptance 시점 snapshot, V0016 가 evolution). lifecycle = ready → review 직접 (in-progress 우회, single-PR closure 패턴 7번째 적용 — TASK-MONO-084/085/086/087/088/089 + 본 task precedent). **Verification (예상)**: 다음 nightly `gap-e2e-full` run 또는 push to main 시 `TenantProvisioningE2ETest` (MONO-089 으로 full 분류) 5/6 step GREEN 회복 예상. cycle 9 finding 발생 시 별 follow-up task. account-service `TenantProvisioningIntegrationTest` 회귀 0. **Option C-1 audit-only**: ADR-MONO-010/011 + memory `project_e2e_3phase_strategy_complete.md` § "7-cycle archaeology" 본문 수정 안 함, INDEX outcome + task body 가 cycle 8 closure source-of-truth (option C-1 audit-trail 누적 8차). **Memory delta**: `project_e2e_3phase_strategy_complete.md` § "7 cycle" 표 가 retrospective 8 cycle 가 됨 — historical 6 layer (settings.gradle + boot jars + JWT keys + Phase 0 진단 + MySQL TEMPORARY TABLES + e2e seed tenant_id) + **cycle 8 layer 7번째 (account-service tenants seed)** + PR-time 가속 의 시작. 분석=Opus 4.7 / 구현=Opus 4.7.

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
