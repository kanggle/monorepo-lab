# Task ID

TASK-PC-FE-366

# Title

콘솔 주문 상태 변경에서 «확정» 을 뺀다 — 운영자가 할 수 있는 변경은 «취소» 하나 (서버 `TASK-BE-648` 의 짝)

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (전이표 · 대화상자 · 가이드 문구)
> 기안 2026-10-22 UTC — 소유자 결정 2026-10-22 KST(«운영자 확정 없애기 · 티켓»). 서버 짝 = `TASK-BE-648`.

# Dependency Markers

- 짝: `projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-648-*.md`. 🔵 이 콘솔 변경은 **서버보다 먼저 머지돼도 안전**하다(선택지를 줄일 뿐 — 서버는 굽기 전까지 확정을 받아도 콘솔이 안 낸다). 반대 순서(서버 먼저)는 운영자에게 400 이 보이므로 피한다.

# Goal

- 콘솔 전이표 `features/ecommerce-ops/api/order-types.ts:79` — `PENDING: ['CONFIRMED', 'CANCELLED']` · 머리말 `:23-25`.
- «확정» 을 빼서 대기 주문에서도 «취소» 만 보인다. 확정은 결제 · 재고 예약이 끝나면 자동으로 된다(가이드 문구와 맞춘다).

# Scope

## In Scope

- **AC-0 측정**: ① `allowedTransitions` · 상태 변경 대화상자 · 가이드(`features/ecommerce-guide/data.ts` 의 «운영자는 확정하거나 취소할 수 있습니다» 류) · 시험 · e2e 의 «확정» 전수 ② 대기 주문에서 «취소» 만 남았을 때 대화상자 모양(선택지 하나 — 버튼으로 바꿀지 그대로 둘지).
- 구현: 전이표에서 `PENDING → CONFIRMED` 제거 · 가이드 문구 사실대로 · 시험.

## Out of Scope

- 서버(`TASK-BE-648`).

# Acceptance Criteria

- [x] **AC-0** — 위 ①②. (아래 «AC-0 측정»)
- [x] **AC-1** — 구현 + **사용자에게 보이는 바뀐 문구(가이드 · 대화상자) 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [x] **AC-2** — 시험 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · Client graph 가드 둘 · 큐 가드 셋 rc=0. 대화상자가 클라이언트 전용이면 nightly 는 판단(SSR 변화 없음을 기록).
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플에서 대기 주문 상세의 상태 변경 선택지 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` (운영자 상태 변경 — 읽기)

# Edge Cases

- 대기 주문에 선택지가 «취소» 하나 — 대화상자가 빈 선택 상태를 만들지 않게.

# Failure Scenarios

1. 가이드가 여전히 «운영자는 확정할 수 있다» 고 말한다 → AC-0 ① 전수.

## AC-0 측정 (2026-10-22 UTC)

경로 기준 `apps/console-web/`. ① «확정» 을 운영자 동작으로 내거나 그렇게 설명하는 곳 전수:

| 위치 | 내용 | 조치 |
|---|---|---|
| `src/features/ecommerce-ops/api/order-types.ts:23` · `:79` | 머리말 `PENDING → {CONFIRMED, CANCELLED}` · 전이표 `PENDING: ['CONFIRMED','CANCELLED']` | 둘 다 `CANCELLED` 만 |
| `src/features/ecommerce-ops/components/OrderStatusDialog.tsx:20` | 머리말 주석의 전이 나열 | 주석 갱신 |
| `…/OrderStatusDialog.tsx:46` | `STATUS_LABELS.CONFIRMED = '확인'` (버튼 라벨 — 전이표에서 빠지면 죽은 항목) | 삭제 |
| `src/features/ecommerce-guide/data.ts:156` | 주석 «운영자 가능 전이는 `대기→{확정,취소}`…» | 주석 갱신 |
| `…/ecommerce-guide/data.ts:164` | PENDING 설명 «운영자는 확정하거나 취소할 수 있습니다.» (**사용자에게 보이는 문구**) | 문구 변경 |
| `tests/unit/ecommerce-orders-state.test.ts:73-74` | `PENDING → [CONFIRMED, CANCELLED]` 단언 | `['CANCELLED']` + 전 상태 CONFIRMED 미제공 단언 |
| `tests/unit/ecommerce-write-buttons.test.tsx:232` | 대기 주문 상세의 `writeTestIds` 에 `order-status-btn-confirmed` | 제거 + 대기 주문 취소만 단언 신설 |
| `tests/unit/EcommerceGuideScreen.test.tsx:104-108` | 운영자 가능 상태 목록(PENDING · BACKORDERED · CONFIRMED — 셋 다 취소 가능이라 **불변**) | 주석 + PENDING 문구 단언 추가 |

변경 없음으로 확인한 곳 (CONFIRMED 가 «주문의 상태 값» 이지 운영자 동작이 아님):

- `order-types.ts:41,61,86` — 상태 열거 · 톤 · `CONFIRMED: ['CANCELLED']`(확정 주문의 취소).
- `ecommerce-guide/data.ts:86,148,174-178,215,241,286,583,636`, `EcommerceGuideScreen.tsx:401,469` — 자동 확정 · 확정 주문 설명(이미 «결제가 끝나면 자동으로 확정» 이라고 말함).
- `shared/sample/fixtures/ecommerce.ts:374,454` — 주문 행 데이터(표시용). 샘플 모드는 쓰기를 403 으로 거부(`SAMPLE_READ_ONLY`)하므로 확정 동작 없음.
- `tests/unit/ecommerce-orders-api.test.ts:182-234` · `ecommerce-orders-proxy.test.ts:198-321` — `changeOrderStatus` 클라이언트 · BFF 프록시의 **본문 통과** 시험(요청 본문 `{status}` 를 그대로 전달하는지). UI 가 안 내도 전송 계층은 값을 가리지 않는다 → 유지(서버가 최종 권위, BE-648 이 400).
- `src/shared/guide/domain-features.ts:359` «주문 확인과 상태 변경» — «확인» 은 조회. `permission-map.ts:1103` — «상태 변경» 일반 서술.
- `tests/e2e/*.spec.ts` (5개) · e2e-smoke — 주문 상태 변경 · `order-status-btn-*` 참조 **0건**(Grep). `ecommerce-orders-backordered.test.tsx` · `ecommerce-orders-proxy.test.ts:101,111,127` — 목록 필터 `status=CONFIRMED` (조회).
- 클라이언트 요청 타입(`orders-api.ts`)은 `OrderStatus` 전체를 받음 → 좁히지 않음(톨러런스 · 서버 권위).

② 대화상자 모양: `OrderStatusDialog` 는 이미 **`<select>` 가 아니라 전이 대상마다 버튼**(`order-status-btn-<target>`) → 확인 `ConfirmDialog`(`ecommerce-confirm-*`) 구조다. 전이표에서 «확정» 이 빠지면 대기 주문도 BACKORDERED · CONFIRMED 와 똑같이 **«취소» 버튼 하나**가 되고, 이 모양은 FE-365 시험(`BACKORDERED` 취소만)이 이미 보증한다. «빈 선택 상태» 가 생길 자리가 없다(선택 상태 없음 · `pendingTarget=null`). **결정: 구조 변경 없음** — 버튼 하나 그대로, testid(`order-status-actions` · `order-status-btn-cancelled` · `ecommerce-confirm-*`) 전부 유지.

## 새 문구 (소유자 승인 대기)

| 위치 | 이전 → 이후 | 언제 보이나 |
|---|---|---|
| `src/features/ecommerce-guide/data.ts:164` (PENDING `desc`) | «주문이 접수되어 결제를 기다리는 상태입니다. 결제가 끝나면 자동으로 확정됩니다. 운영자는 확정하거나 취소할 수 있습니다.» → «주문이 접수되어 결제를 기다리는 상태입니다. 결제가 끝나고 재고가 확보되면 자동으로 확정되며, 운영자가 직접 확정할 수는 없습니다. 운영자는 취소만 할 수 있습니다.» | 이커머스 가이드(`/ecommerce/guide`) 주문 상태 흐름의 «대기» 행 |
| 주문 상세 상태 전이 영역 (`OrderStatusDialog`, 문구 변경 없음 · **버튼이 사라짐**) | 대기 주문: 「확인」 · 「취소」 두 버튼 → 「취소」 하나 | 대기(PENDING) 주문 상세, 쓰기 권한 있는 운영자 |

(나머지 주석 · 테스트 수정은 화면에 보이지 않는다. 확인 대화상자 제목 «주문 상태를 변경할까요?» 와 «취소» 흐름은 그대로.)

## 구현 (2026-10-22 UTC · Sonnet)

- `order-types.ts`: `TRANSITIONS.PENDING = ['CANCELLED']` + 머리말 · 사유 주석.
- `OrderStatusDialog.tsx`: 죽은 `STATUS_LABELS.CONFIRMED` 제거 · 머리말 주석.
- `ecommerce-guide/data.ts`: PENDING 설명 · 상태 목록 주석(`대기→{취소}`, `재고부족 이월→{취소}`, `확정→{취소}`).
- 시험: `ecommerce-orders-state`(PENDING=['CANCELLED'] · 어떤 상태도 CONFIRMED 를 제안하지 않음) · `ecommerce-write-buttons`(쓰기 testid 목록에서 confirmed 제거 + «대기 주문은 취소만» 신설) · `EcommerceGuideScreen`(PENDING 설명이 «확정하거나» 를 말하지 않음).
- 검증: vitest 426 files / 5229 tests pass · `tsc --noEmit` rc=0 · `next lint` rc=0 (기존 경고만) · 가드는 커밋 메시지/보고에 기록.
- **SSR 영향**: 대화상자(`OrderStatusDialog`, `'use client'`)는 주문 상세 클라이언트 트리 안이라 SSR HTML 은 `allowedTransitions` 결과를 반영하는 버튼 목록만 다르다(대기 주문의 «확인» 버튼 1개 제거). 가이드 페이지(`ecommerce-guide`)는 정적 서버 렌더라 PENDING 설명 한 문장이 SSR HTML 에서 바뀐다. 새 fetch · 라우트 · 서버 전용 모듈 경계 변화 없음 → nightly 는 가이드 문구/대기 주문 버튼 외 영향 없음으로 판단.

## 소유자 승인 · 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- 가이드 «대기» 설명 변경 · 대기 주문의 «확인» 버튼 제거 = **소유자 승인**(선택창, 2026-10-22 KST, 머지 전) → AC-1 닫힘.
- 브랜치 nightly run `39850739891`(head `96efdb1d2`) **success** · PR #4484 같은 head 체크 pending 0 · fail 0 → AC-2 닫힘.
- 서버 짝 `TASK-BE-648`(#4485)보다 **먼저** 머지한다 — 서버만 나가면 콘솔 «확정» 이 400 을 받는다.
- 열림: **AC-3**(Vercel Production 기록 · 익명 샘플 — `/ecommerce/guide` «대기» 행 문장).

## CORRECTION (2026-10-22 UTC · close chore · 4차원 확인)

- **AC-3 닫힘** — 위 AC 목록의 AC-3 칸은 review 시점 기록이라 빈 채로 둔다. Vercel Production `7268124145` success. 익명 샘플 화면(`https://console.hubwang.com/ecommerce/guide`, 서버 렌더 HTML, 2026-10-22 UTC): «대기» 행에 «운영자가 직접 확정할 수는 없습니다» 가 있고, 옛 문장 «운영자는 확정하거나 취소할 수 있습니다» 는 0건. 대기 주문 상세의 버튼 감소(클라이언트 대화상자)는 단위 시험(`ecommerce-write-buttons.test.tsx`)으로 고정.
- 4차원: (a) #4484 `state=MERGED` · (b) 스쿼시 `c8b7262d8` 가 `origin/main` 조상 · (c) 머지 시점 `statusCheckRollup` 실패 0 · 대기 0 · (d) AC 원문의 남은 칸 = AC-3 하나 — 위 기록으로 «배포 · 확인» 대로 닫힘.
