# Task ID

TASK-BE-626

# Status

review

# Title

`GET /api/admin/operators` 항목(과 같은 DTO 를 쓰는 `GET /api/admin/me`)에 운영자의 HOME 테넌트 `homeTenantId` 를 싣는다 — 테넌트 목록이 HOME 과 배정 운영자를 섞어 돌려주는데 소비자가 둘을 가를 방법이 없었다

# Owner

iam-platform

# Task Tags

- admin-service
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 응답 필드 하나 추가(additive). 판정·권한 로직 무변경. (실제 구현: Opus 5.5)
>
> 🔵 **소유자 결정(2026-10-08 UTC):** 대화에서 그룹 멤버 선택기(`TASK-PC-FE-317`)의 빈틈 — «목록은 HOME ∪ 배정인데 그룹은 HOME 만 받아 배정-only 운영자를 고르면 422» — 를 두고 선택지 A(목록에 home 표시) · B′/B″(그룹 멤버 규칙 확장) 중 **«A»**.
> B 계열은 실측 결과 그룹 역할 fan-out 이 멤버의 **HOME 테넌트**에 역할 행을 쓰고(`GroupFanOutService.java:125-134`) 역할 행 PK 가 `(operator, role)` 이라, 규칙만 넓히면 다른 테넌트 관리 권한이 생기는 결함이 드러나 보류됐다(대화 기록).

---

# Dependency Markers

- 선행: 없음.
- 후속(같은 PR): `projects/platform-console` `TASK-PC-FE-319` — 이 필드로 그룹 멤버 선택기에서 배정-only 운영자를 미리 막는다.
- 관련: `TASK-PC-FE-317`(done 대기, 빈틈을 처음 기록한 티켓).

# Background (착수 전 측정, `origin/main` `779195d40`)

| 사실 | 근거 |
|---|---|
| 테넌트 목록 = HOME == X **또는** `operator_tenant_assignment` 에 X | `apps/admin-service/src/main/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorJpaRepository.java:96-112` |
| 그룹 멤버 추가 = HOME == 그룹 테넌트만(아니면 `422 GROUP_MEMBER_TENANT_MISMATCH`) | `application/GroupAdminUseCase.java:171-176` |
| 응답 항목에 테넌트 필드 없음 — `OperatorSummaryResponse` 는 `/me` · 목록이 공유 | `presentation/dto/OperatorSummaryResponse.java` · `OperatorAdminController.java:80-105, 415-426` |
| 서비스 계층 `OperatorView` 는 이미 HOME(`tenantId`)을 들고 있다 — 쿼리 변경 불필요 | `application/OperatorQueryService.java:56-60` |
| 파트너십 참여자(ADR-MONO-045)는 배정 행이 아니라 별도 참여 테이블 → 이 목록에 안 나온다 | `ManagePartnershipParticipantUseCase.java` · 위 쿼리 |
| 소비자 = console-web 하나. zod 스키마는 비-strict(모르는 키 무시) | `projects/platform-console/apps/console-web/src/shared/api/iam-operators-types.ts` |

# Goal

목록·`/me` 응답 항목마다 그 운영자의 HOME 테넌트(`admin_operators.tenant_id`, 플랫폼 운영자는 `*`)를 `homeTenantId` 로 싣는다. 판정·권한·쿼리는 바꾸지 않는다.

# Scope

## In Scope

- 계약 `specs/contracts/http/admin-api.md` § GET /api/admin/me · § GET /api/admin/operators(예시 + 항목 표 `homeTenantId` 행) — **구현보다 먼저**.
- `OperatorQueryService.OperatorSummary` + `OperatorSummaryResponse` + 컨트롤러 매핑.
- 시험: 서비스 단위(항목마다 자기 HOME — 요청 테넌트가 아님) · 컨트롤러 슬라이스(`/me` · 목록 JSON 에 실림).

## Out of Scope

- 그룹 멤버 규칙 변경(B′/B″) — 보류(위 소유자 결정).
- `POST /api/admin/operators` 응답(`CreateOperatorResponse`, 별도 DTO) — 소비 필요 없음.

# Acceptance Criteria

- [x] **AC-1** — 계약 두 절에 `homeTenantId` 가 적혀 있고, 목록 항목 표에 «HOME 과 배정을 가르려면 `tenantId` 와 비교» · 파트너십 참여자 비포함이 적혀 있다.
- [x] **AC-2** — 목록 항목의 `homeTenantId` = 그 운영자 자신의 HOME(배정으로만 속한 운영자는 다른 값) — `OperatorQueryServiceTest.listOperators_carries_each_operators_own_home_tenant`.
- [x] **AC-3** — `/me` · 목록 JSON 에 `homeTenantId` 가 실린다 — `OperatorAdminControllerSliceTest`.
- [ ] **AC-4** — admin-service 대상 시험 통과 + CI 의 iam 레인 초록(아래 기록).
- [x] **AC-5** — 하위 호환: 필드 추가만(기존 키 무변경). 유일한 소비자 console-web 은 비-strict zod + optional 로 받는다.

# Related Specs

- `projects/iam-platform/specs/services/admin-service/architecture.md`
- `docs/adr/ADR-MONO-046-operator-group-model.md` § D3 (그룹 = 자기 테넌트 운영자)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § D1 (HOME ∪ 배정)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/me · § GET /api/admin/operators

# Edge Cases

- 플랫폼 운영자 → `homeTenantId = "*"`.
- `'*'` 교차 테넌트 목록(플랫폼이 `tenantId=*` 로 조회) — 각 항목이 자기 HOME 을 그대로 싣는다.

# Failure Scenarios

- 요청 테넌트를 `homeTenantId` 로 잘못 매핑 → 배정-only 운영자가 HOME 으로 보여 선택기가 막지 못함 → AC-2 시험이 «요청 테넌트가 아니라 자기 HOME» 을 단언한다.

# Implementation Record (2026-10-08 UTC)

- 변경: `admin-api.md` 두 절 · `OperatorQueryService.java`(record + 두 생성 지점) · `OperatorSummaryResponse.java` · `OperatorAdminController.java` · `OperatorQueryServiceTest.java`(+1) · `OperatorAdminControllerSliceTest.java`(+1, 생성 지점 5곳 인자 추가).
- 게이트(워크트리, `./gradlew :projects:iam-platform:apps:admin-service:test --tests OperatorQueryServiceTest --tests OperatorAdminControllerSliceTest`): rc=0 · 결과 XML 실측 `OperatorQueryServiceTest` 9/9 · `OperatorAdminControllerSliceTest` 43/43(새 시험 둘이 결과에 있음 — «0개 실행 rc=0» 아님 확인). 테스트 소스 전체가 컴파일됐으므로 다른 생성 지점 누락 없음. `OperatorAdminIntegrationTest`(Testcontainers)는 CI 에서 — 엄격 JSON 비교 없음(grep 0).
- AC-4 의 «CI iam 레인 초록» 은 PR 체크로 닫는다.
