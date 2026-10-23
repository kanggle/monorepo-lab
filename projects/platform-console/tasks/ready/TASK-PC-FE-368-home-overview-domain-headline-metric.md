# Task ID

TASK-PC-FE-368

# Title

`ADR-MONO-084` P8 — 전체 홈(`/dashboards/overview`) 도메인 카드에 대표 실적 지표 하나(이번 주 값 · 직전 대비 증감)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기존 합성에 칸 추가 — 모양 · 실패 격리 · 카드 순서 불변)
> 기안 2026-10-23 UTC — `ADR-MONO-084` ACCEPTED, D7.

# Dependency Markers

- **선행**: `TASK-MONO-807`~`812`(P2~P7) 중 **카드에 실을 도메인의 `…/metrics` 가 `main` 에 있어야** 한다. 🔴 ADR-084 D7 은 «다섯 도메인 응답이 다 선 다음» 이다 — 일부만 섰을 때 그 도메인만 먼저 실을지는 AC-0 에서 소유자에게 묻는다.

# Goal

전체 홈의 도메인 카드 6장(`features/operator-overview` — iam · wms · scm · finance · erp · ecommerce)은 각각 현재 숫자 하나만 보여 준다. 카드마다 그 도메인의 **대표 지표 하나**를 이번 주 값 + 직전 대비 증감으로 더한다.

# Scope

## In Scope

- 계약 먼저: `console-integration-contract.md` § 2.4.9.1 에 카드별 대표 지표 칸 · 그 칸의 출처(도메인 `…/metrics?period=week` 의 어느 `key`) · 칸별 실패.
- 콘솔 서버 합성(`shared/composition/operator-overview-composition.ts`, ADR-081)에 도메인 다섯 곳의 `metrics` 호출을 더한다. 응답 모양 · 카드 순서 · 카드 상태(ok/degraded/forbidden)는 바꾸지 않는다 — **칸만 더한다**.
- 대표 지표(기본안, AC-0 에서 소유자 확인): ecommerce = 매출 · wms = 출고 수량 · scm = 발주 금액 · erp = 결재 승인 건수 · iam = 로그인 실패 · finance = 거래 금액(통화마다).
- 지표 칸이 실패해도 카드의 현재 숫자는 선다.

## Out of Scope

- Fan 카드(ADR-084 D6 — Fan 은 카드 자체가 없다).
- 카드 클릭 시 도메인 개요의 기간 선택을 이어받는 일.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 합성의 현재 fan-out 수 · 지연 예산 · 카드별 실패 처리 · 샘플 모드(ADR-MONO-074)가 합성 경로를 어떻게 먹이나 · 대표 지표 기본안의 소유자 확인 · 일부 도메인만 섰을 때의 순서.
- [ ] **AC-1** — 지표 칸 실패 · 카드 현재 숫자 정상인 경우와 그 반대를 각각 시험한다.
- [ ] **AC-2** — 응답 모양 대조군: 기존 필드가 바이트 그대로이고 새 칸만 늘었다(`specs/contracts/fixtures/operator-overview-leg-bodies.json` 갱신 포함).
- [ ] **AC-3** — 401 → 전체 재로그인(지금과 같다) · 403 칸 → 그 칸만 «권한 없음».
- [ ] **AC-4** — 🔴 nightly 전용 e2e 스펙 디렉터리를 grep 한다(이 화면의 testid · 제목을 보는 스펙) · 머지 뒤 첫 `nightly-e2e.yml` 결과를 확인한다.
- [ ] **AC-5** — 배포 상태(`main` · Vercel · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D7
- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1
- 도메인 다섯 곳의 `…/metrics` (P2~P7)

# Edge Cases

- finance 는 통화가 여럿 → 카드에 통화마다 한 줄인가, 대표 통화 하나인가 → AC-0.
- 운영자가 일부 도메인 권한만 가짐 → 그 카드의 지표 칸은 «권한 없음», 카드 자체는 지금 규칙대로.

# Failure Scenarios

- 지표 호출 하나가 느려 홈 전체가 늦어진다 → 칸별 타임아웃 · 실패 격리(ADR-017 D5).
- 기존 필드 이름을 바꾼다 → nightly e2e 가 머지 뒤에 깨진다(AC-4).
