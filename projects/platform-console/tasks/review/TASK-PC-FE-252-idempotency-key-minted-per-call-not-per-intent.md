# TASK-PC-FE-252 — 콘솔이 멱등키를 "호출마다" 만든다 — 더블클릭은 키가 둘이라 가드를 통과한다

**Status:** review

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (프런트 상태 관리 + 테스트. 다만 § Goal 의 *리셋 시점* 판단이 실질이다)

> `TASK-BE-536`(머지됨, PR #2754) 이 남긴 명시적 후속. 백엔드 가드는 랜딩했고 이 티켓은 **그 가드가 실제 위협 경로에 도달하게** 만드는 쪽이다.

---

## Goal

`TASK-BE-536` 이 세 엔드포인트에 `Idempotency-Key` 를 필수화했다:

- `PATCH /api/admin/products/{id}/stock`
- `POST /api/admin/products`
- `POST /api/promotions/{id}/coupons/issue`

콘솔은 키를 보내지만 **호출 지점마다 새로 만든다**:

```
products-api.ts:192    idempotencyKey: crypto.randomUUID(),
products-api.ts:304    idempotencyKey: crypto.randomUUID(),
promotions-api.ts:200  idempotencyKey: crypto.randomUUID(),
```

⇒ **같은 사용자 의도의 두 번째 요청이 다른 키를 들고 가므로 서버가 중복으로 볼 수 없다.** 두 번 클릭하면 재고가 두 번 조정된다.

이건 사소한 마감이 아니다. BE-536 티켓이 위협 모델을 이렇게 못박았다:

> *"these are operator endpoints, so a retry is likelier to come from a **double-clicked UI** or an at-least-once job than from a hostile client. That shapes the mechanism (**a UI-supplied request id is enough**) but does not remove the need."*

**주 위협이 더블클릭인데 현재 배선은 정확히 그 경로에서 물지 않는다.** 가드가 존재하고 테스트도 초록이지만, 결함이 실제로 도착하는 경로를 따라오지 않는다.

### 완화돼 있는 부분 (그래서 P0 는 아니다)

- 다이얼로그가 in-flight 중 비활성화된다(`CouponIssueDialog` 의 `pending={issue.isPending}`) ⇒ **순진한 연타는 이미 막힌다.**
- 백엔드 가드는 **안정 키를 보내는 다른 호출자**(at-least-once 잡, API 클라이언트, 재시도 래퍼)에는 이미 도달한다 ⇒ "물 기회 0" 인 죽은 가드는 아니다.

남는 구멍은 **pending 상태를 우회하는 경로**다: 제출 직후 새로고침 후 재제출, 탭 중복, 느린 응답 중 사용자가 되돌아와 다시 누르는 경우, 그리고 `pending` 배선이 없는 화면.

### 이 티켓의 실질 = 키의 수명을 정하는 것

`crypto.randomUUID()` 를 다른 줄로 옮기는 기계적 작업이 아니다. **키는 "의도" 단위여야 하고, 그 의도가 언제 시작되고 언제 끝나는지를 정해야 한다:**

- 폼/다이얼로그가 **열릴 때** 만들고 성공 시 폐기? → 같은 폼 두 번 제출 = 같은 키(원하는 동작). 그러나 사용자가 값을 고쳐 다시 내면 **다른 요청인데 같은 키**라 409 가 난다.
- **폼 값이 바뀔 때마다** 새로 만든다? → 값-불변 재제출만 dedupe. 더 정확하지만 상태 관리가 는다.
- 값의 **해시**를 키로? → 서버가 "같은 키+다른 페이로드=409" 로 이미 보호하므로 해시는 그 축과 중복이고, 정당한 "같은 값 두 번"(재고 +10 두 번 = BE-536 이 명시적으로 정당하다고 한 케이스)을 **영구히 막아버린다.** ⚠️ 이 선택지는 거의 확실히 틀렸다 — 기록해 두니 재발명하지 마라.

**세 엔드포인트가 같은 답을 가질 필요는 없다.** 재고 조정(+10 두 번이 정당)과 상품 생성(동명이 정당)과 쿠폰 발급(같은 배치 재발급이 정당)은 "같은 의도"의 경계가 서로 다르다.

---

## Scope

**In scope**

1. 세 mutation 각각에 대해 **키의 수명 경계를 정하고 근거를 남긴다**(AC-1).
2. 그에 맞게 키 생성 위치를 옮긴다 — 호출 지점이 아니라 의도 단위.
3. 더블클릭/재제출 시나리오를 **실제로 재현하는 테스트**. "키가 전달된다" 가 아니라 **"같은 의도의 두 번째 제출이 같은 키를 보낸다"** 를 단언해야 한다.
4. `pending` 비활성화가 없는 화면이 있으면 함께 배선(저비용 방어층).

**Out of scope**

- **백엔드 변경 0** — BE-536 이 랜딩한 가드·테이블·계약은 그대로다. 이건 순수 콘솔 티켓이다.
- BE-536 이 손대지 않은 다른 mutation 에 키 확산 0 — 인구조사가 NONE 으로 분류한 것만 대상이었고 나머지는 이미 안전하다.
- `ecommerce-client.ts` / `ecommerce-gateway.ts` 의 opt-in 전달 메커니즘 재설계 0 — 그 배관은 동작한다. 바뀌는 건 **키를 언제 만드느냐** 뿐이다.

---

## Acceptance Criteria

- **AC-0 (gate — 재측정)** — 착수 시 세 곳의 `crypto.randomUUID()` 가 여전히 호출 지점에 있는지 확인한다. 이미 옮겨졌으면 phantom 으로 기록하고 종료.
- **AC-1** — 세 mutation 각각에 대해 채택한 키 수명(언제 만들고 언제 버리는가)과 **그 경계를 그렇게 그은 이유**가 코드 주석 + PR 본문에 있다. 셋이 달라도 된다.
- **AC-2** — **같은 의도의 재제출은 같은 키를 보낸다**를 단언하는 테스트. 두 번 제출을 실제로 시뮬레이션하고 전송된 헤더를 비교한다.
- **AC-3** — **다른 의도는 다른 키를 보낸다** — 값을 고쳐 다시 내면 새 키여야 한다(안 그러면 정당한 조작이 409 로 막힌다). AC-2 의 짝이며, 이게 없으면 AC-2 를 과하게 만족시키는 구현이 통과한다.
- **AC-4** — 재고 +10 을 **의도적으로** 두 번 하는 흐름이 여전히 가능하다. BE-536 이 "둘 다 정당한 창고 이벤트" 라고 명시한 케이스이며, 이 티켓이 깨뜨리기 가장 쉬운 것이다.
- **AC-5** — `pnpm lint` + vitest GREEN. (`tsc`/vitest 가 못 잡는 CI 프런트 RED 가 있으므로 lint 는 생략 불가.)

---

## Related Specs

- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-536-inventory-coupon-path-duplicate-request-guards.md` — 위협 모델 + 백엔드 가드 명세, 이 한계를 명시적으로 남긴 곳
- `projects/ecommerce-microservices-platform/specs/contracts/http/{product-api,promotion-api}.md` — 헤더 계약(변경 대상 아님)
- `platform/error-handling.md` § Product / § Promotion — `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_KEY_CONFLICT`

---

## Related Contracts

- 없음 — 계약 변경 0. 콘솔이 이미 계약대로 헤더를 보내고 있고, 바뀌는 것은 그 값의 **수명**뿐이다.

---

## Edge Cases

1. **🔴 값 수정 후 재제출** — 같은 폼 인스턴스인데 다른 요청이다. 키를 폼-열림에 묶으면 정당한 수정 제출이 **409 로 막힌다**. AC-3 이 이걸 잡는다.
2. **정당한 반복 조작** — 재고 +10 두 번, 같은 배치 재발급. 값-해시 키를 쓰면 **영구히 막힌다.** § Goal 에 적어뒀듯 그 선택지는 거의 확실히 틀렸다.
3. **성공 후 키 재사용** — 성공했는데 키를 안 버리면 다음 제출이 이전 결과로 replay 된다. 리셋 시점을 반드시 정하라.
4. **낙관적 UI / 자동 재시도** — 클라이언트에 자동 재시도가 있다면 그 재시도는 **반드시 같은 키**여야 한다(그게 멱등키의 본래 목적). 있는지 먼저 확인하라.
5. **세 화면의 배선이 다를 수 있다** — 쿠폰만 다이얼로그고 나머지는 인라인일 수 있다. 한 패턴을 세 곳에 강제하지 말고 화면 구조를 먼저 읽어라.

---

## Failure Scenarios

- **F1 — 정당한 조작을 막는다.** 이 티켓의 가장 큰 리스크이고 백엔드 F1 과 같은 성질이다. AC-3/AC-4 가 방어한다.
- **F2 — 키만 옮기고 테스트는 "헤더가 있다" 만 단언한다.** 그러면 지금과 구분이 안 된다 — 현재도 헤더는 있다. **테스트는 두 요청의 키가 같은지를 봐야 한다.**
- **F3 — 성공 후 리셋을 잊는다.** 다음 제출이 조용히 replay 되어 "저장이 안 된다" 로 보고된다.
- **F4 — `pending` 만 추가하고 키는 그대로 둔다.** UI 방어층은 유용하지만 새로고침·다중탭을 못 막는다. 키 수명이 본체다.

---

## Definition of Done

- [x] AC-0 재측정 (세 `randomUUID()` 위치 확인) — 세 곳 여전히 호출지점 채번 확인. **추가 발견**: 형제 features(`tenants`·`operators`·`wms-outbound-ops` 등)는 이미 "확정 액션당 다이얼로그에서 1회 생성·재시도 재사용" 패턴 → **ecommerce-ops 3곳만 straggler**([[project_enforcement_straggler_sibling_parity]]). 판단은 재발명이 아니라 형제 정렬.
- [x] 세 mutation 의 키 수명 결정 + 근거 기록 (§ Implementation Decision) — 단일 규칙: **confirm 시 지연 생성, body 변경/성공/취소 시 폐기, 실패 시 유지**.
- [x] 키 생성을 의도 단위로 이동 — server-side api fn 채번 제거, client(다이얼로그/폼 state)가 채번해 **body 로 전달**, proxy 가 wrapper 스키마로 분리해 header 로(tenants B2 정경). `ecommerce-gateway.ts:195` 배선 재사용.
- [x] AC-2(같은 의도=같은 키) / AC-3(다른 의도=다른 키) 테스트 — 신규 `ecommerce-idempotency-key-lifetime.test.tsx`: 세 다이얼로그 각각 두 번 제출을 실제 구동해 **전송된 키를 비교**(F2 방어 — "헤더 존재"가 아니라 키 동일성).
- [x] AC-4 정당한 반복 조작 가능 확인 — +10 두 번(성공 후 재조정)·같은 배치 재발급이 각각 **다른 키**로 통과함을 단언(값-해시 오답 회피).
- [x] `pnpm lint` + vitest GREEN — `next lint` clean, `tsc --noEmit` clean, 전체 vitest **274 files / 2860 tests pass**(신규 8 포함). 로컬 검증(메인 node_modules junction). CI 가 최종 권위.

---

## Implementation Decision (AC-1)

**키 = "이 확정 시도(intent) 시점의 body". 단일 규칙을 세 mutation 에 적용하되 컴포넌트 구조 차이만 반영:**

- **생성**: confirm 시 지연(lazy) 생성(`idempotencyKey ?? crypto.randomUUID()`).
- **유지**: 실패(409/에러) 시 유지 → **무편집 재시도는 같은 키**(AC-2, 서버 dedup 이 이중제출을 접음).
- **폐기(null)**: (a) body 필드 변경 → 다음 confirm 이 새 키(AC-3) · (b) 성공 → 다음은 새 의도(AC-4) · (c) 취소.
- **값-해시 키 미채택**(§ Goal 경고) — 같은 값 반복(+10 두 번)을 영구 차단하므로 오답. 랜덤 UUID + 위 수명이 정답.

**mutation 별 구조 차이**:
- `registerProduct`(use-product-form): 폼이 confirm 뒤에 잠기므로 편집=취소→재제출이고 `onSubmit` 이 매번 재채번 → 별도 필드 리셋 불필요.
- `adjustStock`(StockAdjustDialog): 필드가 다이얼로그 안에서 편집 가능 → quantity/reason `onChange`·`reset()`(성공+취소)에서 키 null.
- `issueCoupons`(CouponIssueDialog): 성공해도 안 닫힘 → **성공 시에도** 키 null(같은 배치 재발급 정당, AC-4), userIds `onChange` 에서 null.

**배선**: 3개 producer body 스키마는 producer 로 그대로 나가므로 키 직접 추가 불가(`z.object` 가 unknown strip). tenants B2 처럼 **wrapper 스키마**(`XxxRequestSchema = XxxBodySchema.extend({ idempotencyKey })`)로 route 에서 분리 → api fn 별도 인자 → `Idempotency-Key` 헤더. 백엔드 변경 0, 계약 변경 0(콘솔이 이미 계약대로 헤더 전송, 바뀐 건 값의 수명뿐).

---

## Notes

- **분량**: small~medium. 파일 수는 적지만 판단이 실질이다.
- **dependency**: `선행` = `TASK-BE-536`(done, PR #2754). 백엔드가 이미 키를 받고 있으므로 이 티켓은 독립적으로 진행 가능하다.
- **이 task 가 방어하는 실패 모드**: **가드가 존재하고 테스트가 초록이어도, 결함이 실제로 도착하는 경로에 그 가드가 없으면 보호받지 못한다.** BE-536 의 백엔드 테스트는 전부 통과하고 실제로 옳다 — 서버가 같은 키를 두 번 받으면 정확히 한 번만 처리한다. 다만 **아무도 같은 키를 두 번 보내지 않을 뿐이다.**
