# Task ID

TASK-MONO-129

# Title

Portfolio-wide `@MockBean` → `@MockitoBean` migration — Spring Boot 3.4 표준 deprecation path 적용. backend `-Xlint:all` audit (post TASK-BE-310) 잔여 `[removal]` warning category 중 가장 큰 mechanical sweep (37 test files / 122 annotation occurrences / 6 projects). API surface byte-identical, behavior change 0, monorepo-level chore.

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: 없음. Spring Boot 3.4 이미 portfolio 전체에 적용 (parent `build.gradle` 또는 Spring Dependency Management plugin), `@MockitoBean` 클래스 (`org.springframework.test.context.bean.override.mockito.MockitoBean`) 사용 가능. 기존 fan-platform + ecommerce/payment-service 가 이미 `@MockitoBean` 사용 중 — 동일 API 가용성 portfolio 증명.
- **origin**: ① backend `-Xlint:all` audit (post-TASK-BE-310 + post-PR #747 `HttpMessageNotReadableException` 단일-line cleanup) 잔여 `[removal]` warning 의 가장 큰 mechanical sweep category. ② `@MockBean in org.springframework.boot.test.mock.mockito has been deprecated and marked for removal` — Spring Boot 의 표준 migration path 가 `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`). ③ Spring Boot 4.0 시점에 `@MockBean` 가 hard-removed 되면 build failure 가 발생할 silent backlog 의 사전 해소.
- **prerequisite for**: 없음 (cleanup-only). KafkaContainer migration / LoginController sunset 은 별 dependency 없는 독립 backlog.

---

# Goal

Spring Boot 3.4 가 `@MockBean` (`org.springframework.boot.test.mock.mockito.MockBean`) 을 `@Deprecated(forRemoval=true)` 마킹하고 `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`) 을 standard replacement 로 지정. 본 task 는 portfolio-wide single sweep 으로 37 test files (122 annotation occurrences across 6 projects: GAP admin/auth/community, erp masterdata, finance account, platform-console BFF, scm procurement/inventory-visibility, wms inbound/outbound/inventory) 의 import + annotation rename.

**Mechanical**:

1. 각 test file 의 `import org.springframework.boot.test.mock.mockito.MockBean;` → `import org.springframework.test.context.bean.override.mockito.MockitoBean;`.
2. 모든 `@MockBean` annotation → `@MockitoBean`.

**Behavior change 0**:

- `@MockBean` 과 `@MockitoBean` 는 Spring Boot 3.4 docs § "Mocking and Spying Beans" 의 standard migration — Spring Framework 6.2 의 `BeanOverride` mechanism 위에서 reset semantics + answer semantics + name binding 모두 동일.
- 본 task 의 grep 검증: 122 `@MockBean` 모두 plain annotation form (no `name=` / `reset=` / `answer=` customization parameter). 가장 단순한 form 만 사용 — drop-in replace 안전.
- `@SpyBean` 사용처 0 (이미 `@MockitoSpyBean` 1 곳만 존재; scope-out).

**기대 효과**:

- Backend `-Xlint:all` `[removal]` warning 122건 (37 files × annotation 회수) 해소 (사실상 file-당 1건 또는 import-당 1건으로 압축되지만 누적 회수가 122).
- Spring Boot 4.0 시점 의 hard build failure 사전 회피.
- Portfolio 가 modern test API 로 일관화 (fan-platform + ecommerce 이미 modern, 본 task 가 remaining 6 projects 일관화).

---

# Decision authority

- **Why monorepo-level (`tasks/`) and NOT project-internal**: libs/ 영향은 없지만 6 projects 동시 touch (cross-project mechanical sweep) — CLAUDE.md § "Cross-Project Changes" 의 "atomic cross-project commits" 원칙 적용. 분리하면 staggered PR 중 일부만 modern API, 일부는 deprecated API 가 됨 — drift 위험. 1 atomic PR 가 더 깨끗.
- **Why single impl PR (NOT per-project split)**: mechanical migration pattern 가 100% 동일 — file-당 import 1줄 + annotation rename. per-project split 시 6 PRs / 6 close chore = 12 PR cycle overhead. 단일 atomic PR 가 review burden 낮음 (37 file diff, all single-pattern; CI 가 모든 project test 실행).
- **Why no spec/contract change**: test infrastructure only. production behavior / API contract 영향 0.
- **Why no ADR**: HARDSTOP-09 not triggered. Spring Boot 표준 migration path = `shared-library-policy.md` 의 "framework upgrade migration" 정상 lifecycle. 새 architectural decision 아님.
- **Why no platform/architecture.md touch**: test framework 의 internal API rename — service architecture 와 무관.
- **Why no `@SpyBean` parallel migration**: 이미 codebase 에 0 사용 (audit verified). scope-out.
- **Why edge case parameters absent**: grep `@MockBean\(` (with parens) returned **0 matches** — all 122 usages are bare `@MockBean` annotation. drop-in replace 안전.
- **Why include all 6 projects in single sweep**: portfolio drift prevention. atomic 가 monorepo 의 primary advantage.

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- 본 task file.
- `tasks/INDEX.md` — root INDEX ready entry.

**Code (impl PR — out of scope here, dispatch shape)**:

37 test files across 6 projects (모두 동일 mechanical pattern):

- **GAP admin-service** (16 files): `OperatorAdminControllerTest`, `TotpRecoveryCodeRegenerateControllerTest`, `AdminRefreshControllerTest`, `AdminLogoutControllerTest`, `AdminLoginControllerTest`, `AdminAuthControllerTest`, `RequiresPermissionAspectTest`, `TenantAdminControllerTest`, `SessionAdminControllerTest`, `BulkLockControllerTest`, `AuditDeniedCrossPermissionTest`, `AuditControllerTest`, `AdminGdprControllerTest`, `AccountAdminControllerTest`.
- **GAP auth-service** (1 file): `OAuth2AuthorizationServerSliceTest`.
- **GAP community-service** (5 files): `ReactionControllerTest`, `FeedSubscriptionControllerTest`, `PostControllerTest`, `CommentControllerTest`, `FeedControllerTest`.
- **erp-platform** (1 file): `DepartmentControllerSliceTest`.
- **finance-platform** (1 file): `AccountControllerSliceTest`.
- **platform-console BFF** (2 files): `OperatorOverviewSliceTest`, `DomainHealthSliceTest`.
- **scm-platform** (4 files): `SupplierAckWebhookControllerSliceTest`, `PurchaseOrderControllerSliceTest`, `AsnWebhookControllerSliceTest`, `InventoryVisibilityControllerTest`.
- **wms-platform** (7 files): `ErpAsnWebhookControllerTest`, `ErpOrderWebhookControllerTest`, `PutawayControllerTest`, `AsnControllerTest`, `AdjustmentControllerTest`, `TransferControllerTest`, `ReservationControllerTest`, `MovementQueryControllerTest`, `InventoryQueryControllerTest`.

(상세 list 는 `git grep "import org.springframework.boot.test.mock.mockito.MockBean" --name-only` 가 권위)

## Out of Scope

- KafkaContainer migration (별 task — Confluent image `.waitingFor()` semantics 변경 가능, CI iteration 필요).
- `LoginController` `[removal]` warnings (별 task — production endpoint sunset 2026-08-01, spec/contract 분석 필요).
- `[serial]` warnings (exception class `serialVersionUID` 부재) — 별 audit 분류.
- `@SpyBean` migration — 사용처 0.
- 동작 변경 / test invariant strengthen — pure rename only.

---

# Acceptance Criteria

**AC-1** — 37 test files 모두 `import org.springframework.boot.test.mock.mockito.MockBean;` → `import org.springframework.test.context.bean.override.mockito.MockitoBean;` 교체 완료.

**AC-2** — 37 test files 의 모든 `@MockBean` annotation → `@MockitoBean` (122 occurrence 전수 교체).

**AC-3** — Repo-wide grep `org\.springframework\.boot\.test\.mock\.mockito\.MockBean` 가 production code + test code 에 **0 match** (test fixture, application code, 다른 *.java 모두 포함). Historical task md 또는 reference 에서의 mention 은 허용 (이미 grep 결과: `tasks/done/TASK-BE-241-fix-TASK-BE-237.md`, `TASK-BE-076-mysql-container-context-lifecycle.md` 등).

**AC-4** — Repo-wide grep `@MockBean\b` 가 production code + test code 에 **0 match**.

**AC-5** — `import org.springframework.test.context.bean.override.mockito.MockitoBean;` 가 37 file 전체에 추가 (각 1줄).

**AC-6** — LOCAL `./gradlew :libs:java-test-support:compileTestJava` + 영향 받는 6 projects 의 `compileTestJava` 모두 BUILD SUCCESSFUL.

**AC-7** — CI Linux 의 모든 `Integration (X, Testcontainers)` job 가 GREEN (gap / scm / wms master+notification / erp / finance / platform-console console-bff). Slice/unit test 가 동일 invariant 검증 — test result PASS rate 변경 0.

**AC-8** — Production code byte-unchanged: `git diff --stat origin/main -- 'projects/*/src/main/'` + `git diff --stat origin/main -- 'libs/*/src/main/'` 둘 다 empty.

**AC-9** — Zero ADR drift: `git diff --stat origin/main -- 'docs/adr/'` empty.

**AC-10** — Zero spec/contract drift: `git diff --stat origin/main -- 'projects/*/specs/' 'platform/' 'rules/'` empty.

---

# Related Specs

- `platform/testing-strategy.md` — slice test pattern 의 mock injection 메커니즘 (Spring Boot 표준 `@MockBean` → `@MockitoBean` migration 은 mechanism 의 표면 명칭 변경, strategy unchanged).
- 본 task 가 spec 변경 없이 implementation-only.

---

# Related Contracts

- 없음. Test infrastructure cleanup, API/event contract 영향 없음.

---

# Edge Cases

- **`@MockBean(name = ...)` / `@MockBean(reset = ...)` parameterized usages**: grep `@MockBean\([^)]+\)` portfolio-wide = **0 matches** (audit verified). 가장 단순한 form 만 사용 — drop-in replace 안전.
- **`@SpyBean` usages**: portfolio-wide = **0 matches**. `@MockitoSpyBean` 1 곳만 존재 (scm-platform `StateMachineAtomicityIntegrationTest`). scope-out.
- **String-form annotation reference (e.g., `"MockBean"` as text)**: grep 가 import path full-qualified 로 확인 — text reference 는 영향 없음.
- **Multi-line `@MockBean` annotation forms**: 없음 (모두 single-line annotation, multi-line forms 가 검색되면 grep 패턴 보정).
- **Reflection-based access to MockBean class**: 없음 (생성자 호출 / `Class.forName("MockBean")` etc. 미사용 — grep 검증 가능, but unlikely).

---

# Failure Scenarios

- **F1 — Spring Boot version < 3.4 으로 `@MockitoBean` 미가용**: 검증 — fan-platform + ecommerce/payment-service 이미 `@MockitoBean` 사용 중 (audit verified). Spring Boot 3.4 portfolio 전체 적용 확인 (root build.gradle 또는 BOM). 만약 일부 module 이 < 3.4 라면 해당 module 만 hold + 별 task.
- **F2 — `@MockitoBean` 의 reset/answer 기본값이 `@MockBean` 과 다를 경우**: Spring Boot 3.4 docs `@MockitoBean` § Default behavior — `reset = MockReset.AFTER` (default), `answer = Answers.RETURNS_DEFAULTS` (default). `@MockBean` 의 동일 default. drop-in 안전. (만약 test 가 reset 행동에 의존했다면 명시 parameter 필요했을 것이지만, 모든 122 usages 가 bare annotation = default 의존 — `@MockitoBean` default 와 호환.)
- **F3 — Test compilation failure**: AC-6 LOCAL compile 가 catch. 발생 시 STOP + 해당 file rollback.
- **F4 — Test runtime failure (CI Linux)**: AC-7 CI 가 catch. 발생 시 STOP + 해당 file 검토.
- **F5 — `import` 충돌 (예: 같은 file 에 `@MockitoBean` 이미 import 되어 있는 경우 — 부분 마이그레이션 후 잔재)**: grep verified — `@MockitoBean` 가 already imported 한 37 file 중 0 (cross-contamination 없음, audit verified).

---

# Implementation hints (dispatch agent)

1. `git checkout -b task/mono-129-impl-mockbean-mockitobean-migration`.
2. 37 file 의 import 교체 + annotation rename (sed 또는 IDE refactor 또는 직접 Edit). 추천: 각 file 마다 `Edit(file, "import org.springframework.boot.test.mock.mockito.MockBean;", "import org.springframework.test.context.bean.override.mockito.MockitoBean;")` + `Edit(file, "@MockBean", "@MockitoBean", replace_all=true)`.
3. LOCAL verify:
   ```
   git grep "org\.springframework\.boot\.test\.mock\.mockito\.MockBean" -- '*.java'  # expect 0
   git grep "@MockBean\b" -- '*.java'  # expect 0
   ./gradlew compileTestJava --no-daemon -q  # all subprojects, expect BUILD SUCCESSFUL
   ```
4. (선택) LOCAL Docker-free unit subset:
   ```
   ./gradlew :projects:global-account-platform:apps:admin-service:test --tests "*ControllerTest" -q
   ```
5. Push branch + open PR; CI Linux 가 권위 (Testcontainers integration tests + slice tests).
6. BE-303 3-dim 객관 머지 검증 후 close chore.

---

# 분석 / 구현 / 리뷰 모델 권장

- **분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — mechanical sweep, 37 file × 동일 pattern, behavior change 0**.
