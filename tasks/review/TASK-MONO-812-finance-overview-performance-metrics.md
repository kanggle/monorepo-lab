# Task ID

TASK-MONO-812

# Title

`ADR-MONO-084` P7 — Finance 실적 지표: 유형별 거래 건수 · 금액(확정 거래만 · 캡처된 보류만 · 통화마다 따로) · 콘솔 개요 실적 줄

# Status

review

# Owner

monorepo

# Task Tags

- code
- api
- finance
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (돈 · 보류/캡처 의미 · 다통화 · 계정 목록이 의도적으로 없는 도메인)
> 🔴 **cross-project 원자 PR**: finance 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807`~`811`.

# Goal

Finance 개요(`/finance`)는 원장 집계(시산표 · 열린 회계기간 · 대사 차이 · 환율)와 운영자 기본 계정 하나만 보여 준다. 맨 위에 이번 주 · 이번 달 **유형별 거래 건수 · 금액**과 직전 대비 · 일별 추이를 붙인다.

# Scope

## In Scope

- 계약 먼저: 거래가 사는 서비스(account-service 또는 ledger-service — AC-0)의 계약에 `…/metrics?period=week|month` (P1 공용 모양). 지표 = 거래 유형(충전 · 이체 · 캡처 …)별 건수 · 금액. ADR-084 D5: **확정된 돈만** — 보류(hold)는 캡처된 금액만, 해제(release)된 보류는 0. OD-3 기본값: 통화마다 한 줄, 환산 없음.
- 🔴 `account-api.md` 는 `GET /accounts` 를 **의도적으로** 두지 않는다(계정 목록 노출 금지). 집계는 테넌트 전체 합계만 내려주고 계정별 행은 내려주지 않는다.
- 콘솔: `features/finance-overview` 에 P1 «실적 줄» · 통화마다 KPI 를 따로 그린다.

## Out of Scope

- 분개 건수 · 대사 차이 발생/해소 추이 — 계약 변경으로 나중에.
- 환율 환산 합계.

# Acceptance Criteria

- [x] **AC-0** — 재측정: 거래 유형 · 상태 목록 · 확정 시각 필드 · 보류/캡처/해제가 거래 행으로 어떻게 남나(캡처가 별도 거래 행인가) · 원천을 account-service 와 ledger-service 중 어디로 할지(복식부기 원장에서 세면 한 거래가 두 번 잡힌다) · 통화 · 테넌트 범위 · 인가 역할. 결과 → 이 파일 + 계약 문구.
  - → **닫힘 2026-10-24 UTC** (origin/main `067f7aed6`, 경로는 `projects/finance-platform/apps/account-service` 기준).
  - **유형:** `TOPUP · WITHDRAW · TRANSFER · HOLD · CAPTURE · RELEASE · REVERSAL` (`TransactionType.java:7-15`).
  - **상태와 확정 시각:** `REQUESTED → VALIDATED → AUTHORIZED → SETTLED → COMPLETED` · `FAILED` · `REVERSED`. 확정 시각은 `settled_at`(`Transaction.java:86,159-162`)이다. 돈이 움직이는 거래는 한 트랜잭션 안에서 곧바로 COMPLETED 가 된다.
  - 🔴 **HOLD 거래 행은 보류를 거는 순간 COMPLETED 로 저장된다** (`AccountApplicationService.java:191,205`). 상태로 거르면 보류 금액이 실적에 섞인다 → **유형으로 뺀다**.
  - **캡처 · 해제:** CAPTURE 는 별도 행이고 금액은 캡처된 금액이다(부분 캡처 = 캡처된 부분, `:227-228` · `Hold.java:130-150`). RELEASE 는 보류 전액 금액을 가진 COMPLETED 행이다. 만료는 행을 남기지 않는다.
  - **실패:** FAILED 행, `settled_at` 은 null (`ComplianceFailureRecorder.java:43-70`).
  - **이체:** 행 하나다(보내는 쪽 행에 상대 계좌).
  - **WITHDRAW · REVERSAL:** v1 에 쓰기 경로가 없다.
  - **원천:** account-service 로 정했다. ledger 는 복식부기라 행을 더하면 금액이 두 배가 된다.
  - **금액 · 인가:** 통화별 정수 최소 단위로 저장되므로 반올림 단계가 없다. 인가는 기존 계좌 읽기 묶음(`SecurityConfig.java:79`)을 쓴다.
- [x] **AC-1** — 🔴 대조군: 활성 보류 · 해제된 보류 · 실패 거래는 금액을 안 움직인다. 캡처 하나 → 캡처 금액만큼 움직인다. 부분 캡처 → 캡처된 부분만.
  - → `TransactionMetricsServiceTest` 「AC-1 control」: 활성 보류(HOLD COMPLETED) · 해제된 보류(HOLD+RELEASE) · FAILED 는 0, 부분 캡처는 캡처 금액만 움직인다.
  - MySQL 통합 테스트 `TransactionMetricsIntegrationTest` 는 실제 쓰기 경로로 같은 행을 만든다 → CI 판정.
- [x] **AC-2** — 다통화: KRW 거래와 USD 거래가 각자 줄로 나오고 서로 더해지지 않는다.
  - → 키는 `topupAmount:<CUR>` · `transferAmount:<CUR>` · `captureAmount:<CUR>` 이다. KRW 와 USD 가 따로 나오고 서로 더하지 않는다(단위 · 슬라이스 · IT 가 단언).
- [x] **AC-3** — 응답에 계정 id · 보유자 정보가 없다(필드 전수 단언).
  - → `TransactionMetricsControllerSliceTest` 「AC-3 census」: 응답 필드는 공용 모양뿐이다.
  - 조회가 SELECT 하는 열은 다섯 개(`type, status, amountMinor, currency, settledAt`)뿐이다.
- [x] **AC-4** — 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 테넌트 격리 · 인가.
  - → `TransactionMetricsServiceTest` 는 `@ValueSource` 로 JVM 시간대를 바꿔 두 번 돈다. 짧은 달 경계(3/30 → 2월 창) 시험이 있다.
  - IT: 테넌트 격리, ledger 전용 운영자 403, 토큰 없음 401, 잘못된 period 400.
  - 🔵 와일드카드 토큰(`tenant_id=*`)은 모든 값이 `0` 이다. 기존 finance 읽기 API · SCM 형제와 같은 동작이다. 공용 계약의 «0 은 아무 일도 없었다로 읽힌다» 취지와 긴장이 있으므로 계약에 명시했다(시리즈 공통 사안).
- [x] **AC-5** — 콘솔: 실적 줄 · 통화별 KPI · 칸 실패 · 401 · 샘플 모드 응답.
  - → `FinanceOverviewScreen` 맨 위에 실적 줄과 통화별 KPI 가 있다. 칸 실패 · 403(그 줄만 «권한 없음») · 401 · 샘플 모드를 시험한다.
  - 콘솔 검증: `tsc` rc=0 · lint rc=0 · 전체 vitest 444 파일 5,452 통과.
  - 샘플 데이터: 백엔드에 없는 `CREDIT` 유형을 `TOPUP` 으로 고쳤다.
  - 🔴 **발견, 미수정:** 콘솔 거래 목록의 유형 필터가 `CREDIT` / `DEBIT` 을 보내면 백엔드가 422 `AMOUNT_INVALID` 로 답한다. 원장 타일의 `<dl>` 안 `<p>` 는 axe 의 `definition-list` 위반이다.
- [ ] **AC-6** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5 · OD-3
- `projects/finance-platform/specs/services/account-service/architecture.md` · `ledger-service/architecture.md`

# Related Contracts

- `projects/finance-platform/specs/contracts/http/account-api.md` (`GET /accounts` 부재의 이유) · `ledger-api.md` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 이체 = 보내는 쪽 · 받는 쪽 두 행 → 한 번만 센다(AC-0 에서 정의).
- 보류가 지난주, 캡처가 이번 주 → 캡처 시각으로 귀속.

# Failure Scenarios

- 원장(분개)에서 차변 · 대변을 둘 다 센다 → 금액이 두 배.
- 보류 금액을 거래액에 넣는다 → 해제될 돈이 실적으로 보인다.
