/**
 * 옵션 항목을 «선택 가능한 옵션» 으로 지목하는 유일한 술어. (TASK-MONO-655 원인 ②)
 *
 * ## 왜 `data-testid` 인가 — 텍스트로 고르는 것이 **설계상** 깨졌기 때문이다
 *
 * 헬퍼는 원래 `/재고\s+\d+/` 로 옵션을 골랐다. 그 술어는 `ADR-MONO-070` 이후 **정의상**
 * 아무것도 못 맞춘다: 공개 상세는 저장본에서 오고(`get-product.ts`), 저장본에는 재고가
 * 없어서 매퍼가 `stock: null` 을 넣고(`snapshot-mappers.ts`), `VariantSelector` 는
 * `null` 을 **아무것도 안 그리는 것**으로 번역한다. 🔴 즉 낡은 것은 ADR 이 아니라 술어다.
 *
 * 그러면 남는 선택지는 셋이고, 앞의 둘은 틀렸다:
 *
 * 1. 🔴 **테스트를 위해 텍스트를 되살린다** — "재고 있음" 같은 배지를 다시 그리는 것.
 *    그 화면이 **모르는 사실을 주장하게** 만드는 것이고, ADR-MONO-070 이 금지한 바로 그것이다.
 * 2. 🔴 **술어를 `button:not([disabled])` 로 넓히기만 한다** — 그러면 페이지 전체의 첫
 *    활성 버튼(헤더 내비·드롭다운 트리거·찜)이 잡힌다. 실패가 아니라 **엉뚱한 클릭**으로
 *    바뀌므로 더 나쁘다.
 * 3. 🟢 **원소의 «역할» 에 이름을 붙인다** — 이것이 testid 다. testid 는 재고에 대해
 *    아무것도 주장하지 않는다. 「이것은 옵션 항목이다」만 말한다.
 *
 * ## 품절은 무엇이 거른나 — `:not([disabled])` 하나뿐이다
 *
 * `VariantSelector` 는 `disabled={isSoldOut || isSelected}` 로 **이미** 선택 불가를 DOM 에
 * 표현한다. 같은 사실을 `data-sold-out` 같은 두 번째 속성으로 또 적으면 한쪽만 고쳐지는
 * 자리가 생기므로, 술어의 근거는 **`disabled` 한 곳**으로 둔다.
 * 🔴 그 대가로 「`disabled` 가 품절을 계속 추적하는가」가 이 술어의 전제가 된다 —
 * `product-detail-with-cart.test.tsx` 의 대조군이 그 전제를 문다(품절 항목은 testid 를
 * **가진 채** 이 술어에 **안 잡혀야** 한다).
 *
 * 🔴 이 파일에는 React·CSS 를 들이지 마라. Playwright 의 node 프로세스가 그대로 import 한다.
 */
export const VARIANT_OPTION_TESTID = 'variant-option';

/** 선택 가능한 옵션 항목. e2e 헬퍼와 단위 대조군이 **같은 문자열**을 쓴다. */
export const SELECTABLE_VARIANT_OPTION = `[data-testid="${VARIANT_OPTION_TESTID}"]:not([disabled])`;
