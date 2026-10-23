# Task ID

TASK-MONO-805

# Title

도메인 개요에 **실적 지표** — 한 응답 모양 · 한국 시간 기간 · 백엔드 집계: 조사 · `ADR-MONO-084` 발행 · ACCEPT 뒤 단계 티켓 기안

# Status

review

# Owner

monorepo

# Task Tags

- adr
- platform-console
- metrics

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (집계 계약 · 매출 인식 기준 · WMS 읽기 모델 다시 쌓기), 도메인별 콘솔 화면은 Sonnet
>
> 🔴 이 티켓은 **결정 티켓**이다. 코드는 바꾸지 않는다. 구현은 `ADR-MONO-084` ACCEPT 뒤 그 PR 안에서 기안하는 단계 티켓(P1~P8)이 한다. 이 티켓을 닫았다고 «구현 완료» 로 보고하지 않는다.

---

# Dependency Markers

- **선행**: 없음.
- **후속**: ADR-084 P1~P8 단계 티켓(ACCEPT PR 에서 기안). Fan 은 `ADR-MONO-079` D4-A 재결정 뒤(ADR-084 D6).

# Goal

콘솔의 도메인 «개요» 는 지금 거의 전부 현재 상태만 보여 준다. 운영자가 기간의 실적(이번 주 · 이번 달 매출 · 수량 · 건수, 직전 대비, 일별 추이)을 보게 하는 공통 규칙을 정한다: 자리 · 응답 모양 · 기간 경계 · 계산 위치 · 실적으로 세는 기준 · 범위.

# Scope

## In Scope

- 현재 구조 실측(AC-0) — 도메인 개요 6곳 · 전체 홈의 블록과 출처 · 기존 기간 집계 API · 시간대 처리 · 돈을 세는 기준 · Fan 운영자 접근. 결과 = `ADR-MONO-084` § Context ①~⑤.
- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` PROPOSED 발행 · `docs/adr/INDEX.md` 행.
- 소유자 ACCEPT 게이트(정확형 `ADR-MONO-084 ACCEPTED`) · OD-1~OD-4 답 기록.
- ACCEPT PR 안에서 단계 티켓 P1~P8 기안(루트 · 각 프로젝트 `tasks/ready/`).

## Out of Scope

- 코드 · 계약 · 마이그레이션 변경 — 전부 단계 티켓.
- Fan 실적 지표(ADR-084 D6).
- 테넌트별 시간대 필드(ADR-084 D3).
- 별도 분석 화면 · 내보내기 · 미리 집계한 테이블.

# Acceptance Criteria

- [x] **AC-0** — 실측: ① 도메인 개요 6곳 · 전체 홈이 무엇을 보여 주고 어디서 받나 ② 기간 집계가 있는 곳과 그 기간 경계(KST/UTC) ③ 응답 모양 ④ 매출 계산 기준 ⑤ 시간대 설정의 위치 ⑥ Fan 운영자 접근. — ✅ 2026-10-23 UTC: `ADR-MONO-084` § Context ①~⑤ (file:line 근거).
- [x] **AC-1** — `ADR-MONO-084` PROPOSED 를 `main` 에 머지(번호 확정). — ✅ PR #4489 (머지 대기). 🔴 **절차 차이**: PROPOSED 커밋의 머지가 자동 모드 분류기에 막힌 사이 ACCEPT 가 도착해, PROPOSED · ACCEPTED · 단계 티켓이 **같은 PR** 에 실렸다. 번호 `084` 는 열린 PR 이 쥐고 있고 충돌하는 브랜치 · PR 은 없었다(2026-10-23 UTC 확인).
- [x] **AC-2** — 소유자 정확형 `ADR-MONO-084 ACCEPTED` 수신 → Status 전환 · History · OD 답(또는 기본값) 대조. 결정 본문 바이트 불변. — ✅ 2026-10-23 UTC 소유자 정확형 *«ADR-MONO-084 ACCEPTED»* · OD 답 미공급 → OD-1~OD-4 기본값(ADR § OD 대조). D1~D7 본문 무변경.
- [x] **AC-3** — ACCEPT PR 안에서 단계 티켓 P1~P8 기안. — ✅ P1 `TASK-MONO-806` · P2~P7 `TASK-MONO-807`~`812`(루트 — 도메인 + 콘솔 원자 PR) · P8 `TASK-PC-FE-368`(콘솔). OD 기본값을 각 티켓 AC 로 옮겼다(OD-1 · OD-2 → 807, OD-3 → 812, OD-4 → 806).

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` (이 티켓의 산출물)
- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` D2 · `ADR-MONO-017` D3.B · D5 · `ADR-MONO-081`
- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D4-A · `ADR-MONO-059`
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` § `GET /api/admin/orders/summary` · `/insights`
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § `GET /api/v1/admin/dashboard/throughput`
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (전체 홈 — P8)
- 각 도메인의 새 `…/metrics` 계약 — 단계 티켓

# Edge Cases

- 00:00–09:00 KST 에 UTC 날짜와 KST 날짜가 다르다 — WMS 추세가 지금 이 차이를 갖고 있다(ADR-084 Context ①).
- 짧은 달의 `previous` 구간(3월 30일 ↔ 2월) — 그 달 말일에서 멈춘다(D2).
- `BACKORDERED` 가 나중에 `CONFIRMED` 가 되면 지난 날의 매출이 바뀐다(OD-1).

# Failure Scenarios

- 소유자의 «추천대로 진행» 을 ACCEPT 로 읽고 단계 티켓을 착수한다 → 게이트 우회. 이 티켓은 AC-1 에서 멈춘다.
- WMS 를 화면만 KST 로 바꾼다 → 읽기 모델은 UTC 날짜라서 숫자가 틀린 날에 붙는다(ADR-084 D3).

# 영향 받는 경로

- 이 티켓: `docs/adr/ADR-MONO-084-…md` · `docs/adr/INDEX.md` · `tasks/INDEX.md` · 이 파일.
- `projects/<name>/` 영향: 없음(결정만). 단계 티켓이 ecommerce · wms · scm · erp · iam · finance · platform-console 을 건드린다.
