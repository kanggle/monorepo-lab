# Task ID

TASK-MONO-809

# Title

`ADR-MONO-084` P4 — ERP 실적 지표: 결재 상신 · 승인 · 반려 건수(전이 시각 귀속) · 콘솔 개요 실적 줄

# Status

done

# Owner

monorepo

# Task Tags

- code
- api
- erp
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (집계 하나 · 화면 붙이기 — 전이 시각이 저장돼 있다면. AC-0 에서 없다고 나오면 Opus 로 올린다)
> 🔴 **cross-project 원자 PR**: approval-service 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807` · `808` · `810`~`812`.

# Goal

ERP 개요(`/erp`)는 지금 마스터 건수 · 결재 상태 분포 · 내 결재 대기만 보여 준다(전부 현재 상태). 맨 위에 이번 주 · 이번 달 **결재 상신 · 승인 · 반려 건수**와 직전 대비 · 일별 추이를 붙인다.

# Scope

## In Scope

- 계약 먼저: `approval-api.md` 에 `…/metrics?period=week|month` (P1 공용 모양, 경로는 그 서비스 관례) · 지표 `submitted` · `approved` · `rejected` 각각 «어떤 전이 · 어느 시각으로 귀속 · 무엇을 빼나» 한 줄(ADR-084 D5).
- approval-service: 집계 · 인가(기존 조회 API 와 같은 역할 게이트 — ADR-083 P6 의 세분 인가를 그대로 따른다).
- 콘솔: `features/erp-ops` 개요에 P1 «실적 줄». `?asOf=` 가 있을 때 실적 줄의 의미(시점 보기와 기간 실적은 다른 축)를 AC-0 에서 정하고 화면에 적는다.

## Out of Scope

- 결재 처리 시간(상신 → 완결 소요) — 평균 · 분위 정의가 필요하다. 계약 변경으로 나중에.
- 인사 · 마스터 변동 지표.

## AC-0 기록 (2026-10-24 UTC, origin/main `159d40a1a`)

🟢 **세 지표 모두 자기 전이 시각이 저장돼 있다 — `updatedAt` 대체 없음, 마이그레이션 없이도 정확하다.**

| 지표 | 귀속 시각 | 근거 |
|---|---|---|
| `submitted` | `approval_request.submitted_at` | `ApprovalRequest.submit()` 이 한 번만 씀 · 재상신 없음(SUBMIT 은 DRAFT 에서만 — `ApprovalStateMachine.java:50-59`) · 회수가 지우지 않음 |
| `approved` | `finalized_at` + `status = APPROVED` | **최종 단계 승인만** `finalizedAt` 을 씀(`ApprovalRequest.java:190-204`) · 중간 단계 승인은 `IN_REVIEW` + `updated_at` 뿐 |
| `rejected` | `finalized_at` + `status = REJECTED` | 어느 단계 반려든 요청을 끝낸다(`ApprovalRequest.java:221-230`) |

- 🔴 **함정**: 중간 단계 승인도 `approval_action` 에 `transition='APPROVED'` 행을 쓴다(`ApprovalApplicationService.java:211-215`) ⇒ 이력 표로 세면 여러 단계 결재가 **부풀려진다**. 요청 행의 최종 상태로 센다(Edge «중간 단계 승인» = 기본값 «최종 결과만»).
- `finalized_at` 하나를 상태로 가른다 — 끝난 요청은 다시 안 바뀌므로 안전(`isFinalized()` 이후 모든 명령 `APPROVAL_ALREADY_FINALIZED`). 🔴 **회수도 `finalized_at` 을 쓴다**(`ApprovalRequest.withdraw()` — 구현 중 발견, 위 조사에서 빠졌던 사실) ⇒ `status` 필터가 빠지면 회수가 승인 · 반려로 섞인다. 쿼리에 필터가 있고 IT 의 «초안 회수» · «상신 후 회수» 대조군이 그것을 문다(JPQL 이라 단위 시험은 못 문다 — 실 DB 에서만).
- **회수**: DRAFT · SUBMITTED · IN_REVIEW 에서 가능, 종착. 상신 뒤 회수된 요청은 **상신 창에서 `submitted` 1 로 남는다**(상신은 일어났다; 회수는 `submitted_at` 을 안 건드림). 상신 전 회수(DRAFT → WITHDRAWN)는 `submitted_at` NULL ⇒ 0. 회수는 세 지표 어디에도 더하지 **않는다**(AC-1 «회수는 submitted 를 안 움직인다» 의 정의).
- **위임(대결)**: 전이 하나 · 행 하나 · 상태 변화 하나 — 실행자가 대리인이어도 이중 계산 없음(셈 단위는 요청).
- **테넌트**: 모든 표 `tenant_id NOT NULL` · 테넌트는 JWT(`ActorContext.tenantId()`) · `TenantClaimEnforcer` 403.
- **인가**: `authorizeRead`(= `ERP_APPROVAL_READ ∨ ERP_APPROVAL_WRITE ∨ erp.read ∨ erp.write`, ADR-083 P6 배너 `approval-api.md:3`). **범위 = 목록과 같은 Q4 규칙(무변경)**: `isOperator() ∨ isPlatformScope()` → 테넌트 전체, 아니면 **내가 참여한 요청**(상신자 ∨ 결재자, `ApprovalApplicationService.java:284-296`), 직원에 안 묶인 호출자는 0. 🔵 403 이 아니라 범위 축소를 고른 이유: 개요의 결재 상태 블록이 이미 그 규칙으로 그려지고(«운영자는 전체, 그 밖은 내 것»), 같은 화면의 두 결재 숫자가 다른 범위면 비교가 거짓이 된다.
- **`?asOf=`**: 지금 asOf 는 마스터 건수 5 개에만 걸린다(`overview-state.ts:140-141`). 실적 줄은 **기간 축**이라 asOf 와 무관하게 «오늘까지» 를 보이고, asOf 가 있으면 줄 위에 그 사실을 적는다.
- **인덱스**: `submitted_at` · `finalized_at` 을 덮는 인덱스 없음 ⇒ V6 로 `(tenant_id, submitted_at)` · `(tenant_id, status, finalized_at)` 추가.
- **데모 시드**: `seed-erp.sh` §6 = 상신 2 · 초안 1(시드 시각, 승인 · 반려 0) ⇒ 신선 데모는 상신 2 / 승인 0 / 반려 0.
- 곁에서 본 것(범위 밖): `approval-api.md` § Auth(580-605)와 목록 `status` enum 에 `IN_REVIEW` 누락 — P6 배너가 대신하나 본문이 낡았다.
# Acceptance Criteria

- [x] **AC-0** — 재측정: 결재 요청 · 단계의 상태 전이 시각이 저장돼 있는가(상신 · 승인 · 반려 각각) · 회수 · 재상신의 셈법 · 위임 결재의 귀속 · 테넌트 범위 · 조회 인가 역할. 결과 → 이 파일 + 계약 문구.
- [x] **AC-1** — 🔴 대조군: 초안(상신 전) · 회수된 요청은 `submitted` 를 안 움직인다(AC-0 정의대로). 승인 하나 → `approved` 만 움직인다.
- [x] **AC-2** — 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 테넌트 격리 · 인가 없는 토큰 403.
- [x] **AC-3** — 콘솔: 실적 줄 렌더 · 칸 실패 시 현재 상태 블록이 선다 · 401 · 샘플 모드 응답.
- [ ] **AC-4** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5
- `projects/erp-platform/specs/services/approval-service/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 여러 단계 결재: 중간 단계 승인은 `approved` 인가, 최종 승인만인가 → AC-0 에서 정하고 계약에 적는다(기본: 최종 결과만).
- 상신은 지난주, 승인은 이번 주 → 각자 자기 시각으로 귀속.

# Failure Scenarios

- 상태 분포(현재)와 기간 실적을 같은 숫자로 착각하게 그린다 → 카드 제목에 기간을 적는다.
- 전이 시각이 없어 `updatedAt` 으로 대신한다 → 다른 수정에도 날짜가 움직인다. AC-0 에서 없으면 멈추고 보고한다.

## 검증 (2026-10-24 UTC, 로컬 · Windows 호스트 · Docker 없음)

| 무엇 | 결과 |
|---|---|
| approval-service 단위 · 슬라이스 (`:approval-service:test` + `compileTestJava`, 내가 다시 돌림) | rc=0 · **232 / 232** (새 `ApprovalMetricsServiceTest` 8 · `ApprovalMetricsControllerSliceTest` 5) |
| 🔴 bite (에이전트) | 날 자르기를 UTC 로 → KST 버킷 시험 두 시간대 다 빨강 · `approved` 가 REJECTED 목록을 쓰게 → 4 빨강 · 복원 rc=0 |
| 콘솔 `tsc --noEmit` (내가 다시 돌림) | rc=0 |
| 콘솔 erp- · Erp · sample- · f5- · performance- · approval- · status-distribution (내가 다시 돌림) | rc=0 · **44 파일 813** |
| 콘솔 전체 (에이전트) | 5326 / 5327 — 빨강 1 = `ecommerce-idempotency-key-lifetime.test.tsx`(무관 · 단독 8 / 8) |
| `next build` (에이전트) | rc=0 (PageProps 포함) |
| ⚪ `ApprovalMetricsIntegrationTest` (MySQL Testcontainers) | **CI 가 첫 판정** — 대조군 · 반열린 창 · 참여자 범위 · 테넌트 격리 · 400/401/403 |

**AC 판정 메모**

- AC-1 🔴 대조군 — 초안 · 초안 회수 · 상신 후 회수 · 2 단계 중 1 단계 승인(→ `approved` 0) · 최종 승인 · 1 단계 반려. **JPQL 의 상태 필터는 실 DB 에서만 물린다**(위 «회수도 `finalized_at` 을 쓴다») ⇒ CI 판정 전이라 [ ].
- AC-2 — 경계(`Clock` 고정 + JVM 기본 시간대 UTC · Asia/Seoul) · 읽기 권한 없음 403 은 단위 · 슬라이스에서 초록. 테넌트 격리 · `TENANT_FORBIDDEN` 은 IT ⇒ CI 판정 전이라 [ ].
- AC-3 — 실적 줄 맨 위 · 칸 실패 시 상태 블록이 선다 · 401 재로그인 · 403 forbidden · 샘플 모드 응답 · 🔵 `?asOf=` 가 있으면 기간 링크가 asOf 를 유지하고 «실적 줄은 기준일(asOf)과 관계없이 오늘까지의 기간입니다.» 를 띄운다(Failure «현재 분포와 기간 실적을 같은 숫자로 착각» — 줄 제목에 기간, 아래에 범위 안내).
- 구현 메모: 지표는 기존 `ApprovalApplicationService`(생성자를 여러 시험이 위치 인자로 부름)에 붙이지 않고 `ApprovalMetricsService` 로 나눴다 · 읽기 인가 15 줄을 복제했다 — 🔴 한 사실 두 곳: 목록의 인가 · 범위를 바꾸면 이쪽도 바꿔야 한다.
- 참여자 범위의 `approver_id` 는 **현재 단계** 결재자(반정규화) — 다단계에서 1 단계 결재자는 다음 단계로 넘어간 뒤 그 요청의 범위에서 빠진다. 목록과 같은 동작이라 따랐다.

## 배포 상태

- `main` ✅ `c76a60d73`(PR #4510, 머지 직전 실패 0 · 25 pass / 42 skip).
- AMI ⚪ 다음 굽기(approval-service 새 이미지 + V6 인덱스). 굽기 전 데모의 실적 줄은 «불러올 수 없음»(엔드포인트 없음 → degraded), 다른 블록은 그대로.
- 라이브 ⚪ 데모 창: 신선 시드면 이번 달 상신 2 · 승인 0 · 반려 0(`seed-erp.sh` §6).

## CI 판정 (PR #4510 `657293d21`)

- `Integration (erp-platform, Testcontainers)` 초록. `ApprovalMetricsIntegrationTest` 가 **건너뛴 것이 아니라 돌았다** — 이 서비스의 IT 는 `DockerAvailableCondition` 으로 Docker 가 없으면 조용히 빠지므로 초록만으로는 부족하다; job 로그에 그 클래스의 `STANDARD_OUT` 과 Spring 컨텍스트 기동 줄이 있다(27 곳). ⇒ AC-1(대조군: 회수 · 중간 단계 승인이 섞이지 않음) · AC-2(테넌트 격리 · `TENANT_FORBIDDEN` · 반열린 창)가 **실 MySQL 에서 통과** — 닫힘.
- AC-4(굽기 · 데모 창 라이브)가 남아 review 유지.

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-4 — 닫힘.** 34차 창(2026-10-24 UTC): `main` = #4510 `c76a60d73` · AMI = `ami-0d9f8da7d20d7a180`(`d5bb28f14` ⊇ `c76a60d73`) · 라이브 = erp 지표 API 200, 상신 2 · 승인 0 · 반려 0. 소유자 화면 `/erp` 실적 줄이 렌더됨. (증거 o04)
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 그 AC 의 닫힘 기록이다. 위 «review 유지» 문장은 CI 판정 시점의 기록이며 지금은 `done` 이다.)
