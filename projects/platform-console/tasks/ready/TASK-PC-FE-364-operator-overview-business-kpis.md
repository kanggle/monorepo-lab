# Task ID

TASK-PC-FE-364

# Title

운영자 통합 개요(`/dashboards/overview`)의 카드 여섯을 «행 개수» 에서 **업무 지표**로 — 잠긴 계정 · 미출고 주문 · 제출 대기 발주 · 미해소 대사 차이 · 내 결재 대기 · 출고 대기 주문 (카드당 호출 1개 그대로, 1:1 교체)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계약 § 2.4.9.1 표 · 계약 시험 고정값 · 카드 스키마 · 샘플 · 권한별 저하가 함께 바뀜)
> 기안 2026-10-22 UTC — 소유자 질문 «전체개요 · 도메인 개요가 대시보드 느낌인가» 의 2단계. 후보 측정(읽기 전용 에이전트, 이 세션) 뒤 **소유자 결정 2026-10-22 KST**(선택창 — 넷은 추천 그대로, IAM · WMS 는 추천을 알리고 진행).

# Dependency Markers

- 선행 없음. 같은 날 1단계 = `TASK-PC-FE-363`(도메인 개요 모양 맞추기 — 다른 파일). 둘 다 `tasks/INDEX.md` 를 고치므로 **직렬 머지**.
- 🔴 «카드당 호출 1개» 는 기존 소유자 결정이다(계약 § 2.4.9.1 의 두 번째 호출 기각 기록 — `console-integration-contract.md` 3020-3044 근처). 이 티켓은 **교체만** 하고 호출을 늘리지 않는다.

# Goal

운영 익명 샘플 캡처(2026-10-22 UTC)에서 통합 개요의 카드 여섯은 «회원 계정 · 재고 행 · 스냅샷 행 · 잔액 있음 · 활성 부서 · 상품 수» — 운영자가 처음 볼 «할 일 · 쌓인 것 · 문제» 가 아니다. 소유자 결정:

| 도메인 | 지금 | 바꿀 지표 | 출처(기존 목록 API · `size=1` 의 `totalElements`) | 측정 메모(읽기 전용 에이전트 · 메인 세션 확인 포함) |
|---|---|---|---|---|
| IAM | 회원 계정 수 | **잠긴 계정 수** | `GET /api/admin/accounts?status=LOCKED&tenantId=…` (`searchAccounts`) | 같은 API · 같은 권한(`account.read`). 계정 수준 잠금만 센다(사이트 소속 잠금은 제외 — `AccountJpaRepository.java:119`) ⇒ 라벨에 «계정» |
| WMS | 재고 행 수 | **미출고 주문 수** | `GET /dashboard/orders?status=RECEIVED` (`listOrders`) | 읽기 모델이 상태를 셋으로 접지만 «받았고 아직 출하 · 취소 안 됨» 의 뜻으로는 참(`OrderSummaryEntity.java:123-135`) · 테넌트 격리 · 같은 권한 |
| SCM | 재고 스냅샷 행 수 | **제출 대기 발주(초안)** | `GET /api/v1/procurement/po?status=DRAFT` (`listPurchaseOrders`) | 쓰기 모델 상태 기계 · 🔴 테넌트가 호출자 기준인지 **추론** — AC-0 이 확인 · 권한 `SCM_PROCUREMENT_READ`(지금 카드와 다름). 보충 제안은 테넌트가 `"scm"` 고정(`SuggestionController.java:39`)이라 탈락 |
| Finance | 기본계좌 잔액 있음/없음 | **미해소 대사 차이** | `GET /api/finance/ledger/reconciliation/discrepancies?status=OPEN` (`listDiscrepancies`) | 테넌트 범위 · 진짜 `totalElements` · **기본계좌 선행 조건이 사라진다**(지금 카드는 기본계좌 없는 운영자에게 «권한 없음») · 권한 `FINANCE_LEDGER_READ`(다름) |
| ERP | 활성 부서 수 | **내 결재 대기** | `GET /api/erp/approval/inbox` (`listApprovalInbox`) | 호출 1개로 `SUBMITTED` + `IN_REVIEW` · 🔴 운영자 계정이 직원과 연결 안 돼 있으면 빈 결과에 `meta.actorEmployeeId` 없음(`ApprovalInboxController.java:45`) ⇒ **0 이 아니라 «직원 연결 없음»** 을 그린다 · 권한 `ERP_APPROVAL_READ`(다름) |
| E-commerce | 상품 수 | **출고 대기 주문**(결제 완료 · 배송 전 = `CONFIRMED`) | `GET /api/admin/orders?status=CONFIRMED` (`listOrders`) | 쓰기 모델 상태 · 테넌트 격리 · 권한 `ECOMMERCE_ORDER_READ`(다름). 🔴 `STUCK_RECOVERY_FAILED` 는 기록되는 곳이 없어 늘 0(`Order.java:341` 호출자 0) — 쓰지 않는다 |

# Scope

## In Scope

- **AC-0 재확인** (file:line — 측정 보고를 그대로 믿지 않는다): ① 표의 여섯 출처가 지금도 위와 같은가 · SCM 발주 목록의 테넌트 범위(호출자 기준인가 고정인가 — 고정이면 STOP 하고 소유자에게) ② 각 카드의 지금 자격 증명(IAM = 운영자 토큰 · 나머지 = 도메인용 IAM OIDC 토큰)으로 새 엔드포인트가 통과하는가 · 업무 권한 세트(ADR-MONO-083)별로 «권한 없음» 이 새로 생기는 운영자가 누구인가(표로) ③ 계약 § 2.4.9.1 의 생산자 표 · ok 데이터 모양 표 · `specs/contracts/fixtures/operator-overview-leg-bodies.json` · `leg-body-contract.test.tsx` · `console-composition.test.ts` · `shared/sample/fixtures/dashboards.ts` · 카드 스키마(`features/operator-overview`) 의 바꿀 자리 전수 ④ 샘플 고정값이 각 필터를 지키는가(측정: IAM · WMS · SCM · Finance · ERP 는 지킴, E-commerce 는 상태 필터는 지키나 행 구성 미확인).
- **계약 먼저**(별도 커밋): § 2.4.9.1 생산자 표 · 데이터 모양 표 · 고정값 JSON.
- 구현: 카드 여섯의 읽기 · 스키마 · 라벨 · 링크(카드를 누르면 그 할 일 화면 — 예 잠긴 계정 → `/accounts?status=LOCKED`, 미출고 → `/wms/outbound` 등, 있으면) · ERP «직원 연결 없음» 상태 · 샘플. 카드마다 독립 저하 규칙 그대로(403 → 권한 없음 · 그 밖 → 점검 필요 · 401 → 전체 재로그인).
- 시험: 카드마다 ok · 0 · 403 · 저하 · (ERP) 연결 없음 · 계약 시험 고정값 · 호출 수(카드당 1, 총합 불변) · 샘플.

## Out of Scope

- 카드에 두 번째 숫자 · 호출 추가(기존 결정 — 다시 열려면 소유자 결정).
- 서버 변경 · 새 생산자 엔드포인트(ADR-MONO-017).
- 도메인 개요들(1단계 `TASK-PC-FE-363`).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④. ① SCM 테넌트가 고정이면 STOP.
- [ ] **AC-1** — 계약 먼저 → 구현 + **사용자에게 보이는 새 문구(카드 제목 · 설명 · «직원 연결 없음» · 링크 글자) 목록과 «권한 없음» 이 새로 생기는 운영자 표를 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`, 마지막 전체 실행은 최종 커밋에서) · `tsc --noEmit` · `next lint` · Client graph 가드 둘 · 큐 가드 셋(스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인 · 로그인 운영자 라이브(권한별 «권한 없음» 표가 맞는가) = 다음 데모 창.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` (생산자 `/summary` 기각 — 이 티켓은 해당 없음)
- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` (업무 권한 세트 — AC-0 ②)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (갱신 대상)
- `projects/platform-console/specs/contracts/fixtures/operator-overview-leg-bodies.json` (갱신 대상)
- 각 도메인 계약(읽기만): iam admin-api · wms admin-service-api § 1.3 · scm procurement-api · finance ledger-api · erp approval-api · ecommerce order-api

# Edge Cases

- 업무 권한 세트가 좁은 운영자: 지금 보이던 카드가 «권한 없음» 으로 바뀔 수 있다 — AC-1 의 표로 소유자가 본다.
- ERP 직원 연결 없음 vs 결재 0 건 — 다른 상태로 그린다.
- WMS 테넌트 축 없는 운영자(네이티브 `wms` 평면) — 미출고 주문이 비어 보일 수 있음(계약 § 1.0) — 빈 값이 오류처럼 보이지 않게.

# Failure Scenarios

1. 측정 보고를 그대로 믿고 SCM 테넌트 고정을 놓친다 → AC-0 ① STOP.
2. ERP 연결 없음을 0 으로 그려 «할 일 없음» 으로 읽힌다 → 별도 상태 + 시험.
3. 계약 시험 고정값을 안 바꿔 CI 가 빨개지거나, 고정값만 바꾸고 계약 표를 안 바꿔 계약이 거짓이 된다 → AC-0 ③ 전수.
