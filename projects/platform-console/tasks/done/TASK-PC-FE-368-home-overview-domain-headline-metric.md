# Task ID

TASK-PC-FE-368

# Title

`ADR-MONO-084` P8 — 전체 홈(`/dashboards/overview`) 도메인 카드에 대표 실적 지표 하나(이번 주 값 · 직전 대비 증감)

# Status

done

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

- [x] **AC-0** — 재측정: 합성의 현재 fan-out 수 · 지연 예산 · 카드별 실패 처리 · 샘플 모드(ADR-MONO-074)가 합성 경로를 어떻게 먹이나 · 대표 지표 기본안의 소유자 확인 · 일부 도메인만 섰을 때의 순서.
  - → **닫힘 2026-10-24 UTC** (origin/main `4763a2ac2`).
  - **합성 구조:** 서버(`composeOperatorOverview()`)에서 카드 레그 6개와 지표 레그 6개를 `Promise.all` 로 병렬 실행한다. 요청당 바깥 호출은 12건이고, 지연은 둘 중 느린 쪽이다.
  - **지표를 별도 패스로 둔 이유:** 계약에 이미 «카드당 호출 1건» 소유자 결정이 있다. 같은 레그에 넣으면 시한 4초를 공유해 카드의 유일한 숫자를 잃을 수 있다. 그래서 지표는 자기 시한을 따로 갖는다.
  - ⚪ **지연 예산의 수치:** 재지 않았다. 로컬에 백엔드가 없다.
  - **샘플 모드:** 기존 `sampleGate` 경로를 탄다(바깥 호출 0). 대표 지표 값은 도메인 샘플 픽스처의 `…/metrics?period=week` 응답에서 가져온다.
  - **일부 도메인만 선 경우:** 해당 없다. P2~P7 여섯이 모두 `main` 에 있다(807~812).
  - 🔵 **소유자 확인 (2026-10-24 UTC, 선택창):** «기본안 + finance=이체 금액».
    - ecommerce `revenue` · wms `qtyShipped` · scm `poAmount:<CUR>` · erp `approved` · iam `loginFailure` · finance `transferAmount:<CUR>`.
    - 통화가 여럿이면 한 줄씩 보이고 합산하지 않는다.
  - 🔵 **선행 조건:** 지표 레그 때문에 데모 주소 조회가 요청당 6회에서 12회로 는다. 동시 조회를 하나로 묶는 `TASK-MONO-814` 가 이미 `main`(`45fa46428`)에 있고 배포 기록도 있다(`8e6b9ac0c`).
- [x] **AC-1** — 지표 칸 실패 · 카드 현재 숫자 정상인 경우와 그 반대를 각각 시험한다.
  - → `operator-overview-headline-composition.test.ts`: 도메인별 지표 실패 7종 · 시한 초과 · 전체 지표 실패에서 카드 숫자가 유지된다. 반대 방향(카드 degraded · 지표 정상)도 시험한다.
  - 어떤 실패도 0 으로 직렬화되거나 그려지지 않는다.
- [x] **AC-2** — 응답 모양 대조군: 기존 필드가 바이트 그대로이고 새 칸만 늘었다(`specs/contracts/fixtures/operator-overview-leg-bodies.json` 갱신 포함).
  - → 세 카드 스키마에 optional `headline` 만 더했다.
  - `operator-overview-leg-bodies.json` 에는 최상위 `headlineMetrics` 키만 추가했다. `legs` 와 `variants` 는 바이트 그대로다.
  - `console-integration-contract.md` § 2.4.9.1 «Card headline metric» 에서 기존 줄 삭제는 0 이다.
  - 🔴 **알려진 위험 (기록):** `shared/` 가 `features/` 를 import 하는 것은 계층 가드가 금지한다. 그래서 합성 쪽(`shared/composition/operator-overview-headline.ts`)에서 도메인 지표 경로를 다시 적었다. 그 문자열이 각 도메인 fetcher(`features/*/api/*metrics-api.ts`)와 같다는 것을 대조하는 시험은 없다 — 도메인 경로가 바뀌면 홈 칸만 조용히 `degraded` 가 된다(0 은 아니다). 샘플 픽스처의 키 선택은 `headline-metric.test.tsx` 가 프로덕션 선택과 대조한다.
- [x] **AC-3** — 401 → 전체 재로그인(지금과 같다) · 403 칸 → 그 칸만 «권한 없음».
  - → 지표 401 은 카드 레그 401 과 같이 전체 재로그인이다. 지표 403 은 그 칸만 `forbidden`(«권한 없음»)이다.
- [ ] **AC-4** — 🔴 nightly 전용 e2e 스펙 디렉터리를 grep 한다(이 화면의 testid · 제목을 보는 스펙) · 머지 뒤 첫 `nightly-e2e.yml` 결과를 확인한다.
  - 부분(머지 전): nightly 스펙 grep 을 마쳤다. 카드를 보는 `overview-consolidation.spec.ts:137-142` 는 `data-status` · 드릴다운 testid 만 본다. 대표 지표 칸은 새 testid(`…-headline-*`)이고 카드 상태와 독립이며, `getByText` 단언은 없다 → 충돌 없음.
  - 남은 것: 머지 뒤 첫 `nightly-e2e.yml` 결과 확인.
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

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-4 — 닫힘.** 34차 창(2026-10-24 UTC): 머지(`540dccd88`, #4524) 뒤 첫 `nightly-e2e.yml` run `40087927639` 가 success 다.
- **AC-5 — 닫힘.** 34차 창(2026-10-24 UTC): `main` = #4524 `540dccd88` · Vercel = 콘솔은 머지 때 배포된다 · 라이브 = 소유자 홈 개요에서 여섯 도메인 모두 대표 지표가 보인다 — IAM 0 · WMS 이번 주 출고 수량 10 · SCM ₩200,000 · Finance 이번 주 이체 금액 ₩100,000 · ERP 내 결재 대기 2 · ecommerce ₩0.
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 그 AC 들의 닫힘 기록이다.)
