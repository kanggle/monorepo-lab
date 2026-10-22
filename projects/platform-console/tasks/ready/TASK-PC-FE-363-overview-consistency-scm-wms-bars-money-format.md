# Task ID

TASK-PC-FE-363

# Title

도메인 개요 모양 맞추기 — SCM 개요 발주 상태 · WMS 개요 알림 상태를 숫자 나열에서 분포 막대로 · SCM · Finance 개요의 금액 서식을 이커머스와 같게

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (360 의 상태 분포 막대와 같은 모양 · 서버 변경 없음)
> 기안 2026-10-22 UTC — 소유자 질문 «전체개요 · 도메인 개요가 대시보드 느낌인가» 에 대한 캡처 판정(이 세션)의 1단계(소유자 «추천대로 진행»). 2단계(전체 개요를 업무 지표로) 는 측정 뒤 별도 티켓.

# Dependency Markers

- 선행 없음. 선례: `TASK-PC-FE-360`(배송 · 결재 상태 분포 막대 — `ShippingStatusBar` · `ApprovalStatusBar`) · `TASK-PC-FE-357` `shared/ui/InlineBar.tsx`.

# Goal

운영 익명 샘플 화면 캡처(2026-10-22 UTC)에서 도메인 개요끼리 모양이 갈렸다:

| # | 화면 | 지금 | 바꿀 것 |
|---|---|---|---|
| 1 | SCM 개요 «발주 상태» | 9종(초안 · 제출 · 접수 · 확정 · 부분입고 · 입고 · 정산 · 마감 · 취소) 숫자만 나열 | 360 과 같은 **분포 막대** + 색 견본(숫자는 그대로) |
| 2 | WMS 개요 «알림 상태» | 미확인 · 확인 숫자 둘 | 같은 막대(미확인 = 경고 톤) |
| 3 | SCM 개요 «최근 발주» 금액 | `125000.00 KRW` | 이커머스 개요와 같은 서식(예 `₩125,000`) — **글자 서식만** |
| 4 | Finance 개요 «기본계좌 스냅샷» 잔액 | `KRW: 80000 KRW (가용)` | 같은 서식 |

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 1 · 2 의 숫자가 **전체 모집단**인가(상태 필터 `size:1` 의 `totalElements` 인가, 첫 페이지에서 센 것인가 — 첫 페이지면 막대를 그리지 않고 이유를 적는다) · 그 값을 만드는 읽기가 상태를 **실제로 저장**하는가(🔴 선례: WMS admin 읽기 모델은 출고 상태를 셋으로 접었다 — `TASK-PC-FE-362`) ② 상태의 순서 · 색을 어디서 가져올지(기존 배지 톤 · 라벨) ③ 3 · 4 의 금액이 어떤 모양으로 오는가(문자열 · 소수 자리 · 통화) 와 콘솔의 기존 서식 함수(`formatMoney` · `minorToWon` 등)가 F5 규칙(문자열 금액을 `Number` 로 바꾸지 않는다 — `console-web/architecture.md` § Integration Rules)을 지키며 쓸 수 있는가 ④ 샘플 고정값.
- 구현: 360 규칙 그대로(칸 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구 · 숫자 글자 · 기존 testid 유지) · 금액은 **기존 서식 함수만** — 새 `Number(` 없음(F5 가드 `tests/unit/f5-chart-geometry-exception.test.ts` 초록 유지).
- 시험: 비율 · 하나 저하 · 전부 0 · 금액 서식(소수 · 큰 수 · 통화 다름 · 빈 값).

## Out of Scope

- 전체 개요(`/dashboards/overview`) — 2단계.
- 새 호출 · 서버 변경.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④. ① 이 «첫 페이지에서 셈» 이거나 상태를 접는다면 그 칸은 막대에서 빼고 이유를 적는다.
- [ ] **AC-1** — 구현 + **사용자에게 보이는 새 문구 목록과 금액 서식 변경 전후를 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · Client graph 가드 둘(스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (F5)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — SCM 개요 · WMS 개요(§ 2.4.5.2) · Finance 개요 절(이름은 AC-0 에서)

# Edge Cases

- 통화가 KRW 가 아닌 금액 → 그 통화의 기호 · 자리수(기존 서식 함수의 규칙).
- 금액 문자열이 파싱 불가 → 원문 그대로(지금처럼).
- 알림 0 건 → 막대 없음 · 빈 문구.

# Failure Scenarios

1. 금액 서식을 위해 `Number(` 를 새로 쓴다 → F5 위반, 가드가 막는다.
2. 상태를 접는 읽기 위에 분포 막대를 그린다 → AC-0 ① 이 막는다.
