import { getLiveProduct } from '@/entities/product';
import type { ProductDetail } from '@repo/types';
import type { CheckoutCartItem } from './types';

/**
 * 주문이 만들어지기 **직전**에 라이브 백엔드로 각 줄을 다시 확인한다.
 *
 * =============================================================================
 * 왜 필요한가 — 공개 카탈로그가 저장본을 읽게 된 순간 생긴 요구
 * =============================================================================
 * 공개 화면의 가격은 **표시 가격**이다(발행 시점의 값). 장바구니는 그 값을 그대로 담고,
 * `CheckoutForm` 은 그것을 `unitPrice` 로 order-service 에 보낸다. 그리고 order-service 의
 * `OrderPlacementService.placeOrder` 는 **클라이언트가 보낸 `unitPrice` 를 그대로 받는다** —
 * 상품 서비스에 가격을 되묻지 않는다(재고는 `OrderPlaced` 이후 saga 가 **비동기로** 잡는다).
 * ⇒ 동기 재검증이 백엔드에 **없다.** 그래서 여기 있다.
 *
 * 🔴🔴 그리고 이 함수의 더 중요한 일은 **백엔드가 죽었을 때 주문을 만들지 않는 것**이다.
 *    판정 불가(`backend_unreachable`)를 «괜찮다» 로 번역하면, 저장본만 보고 도는 화면이
 *    백엔드가 꺼진 채로 «주문 완료» 를 그린다. 성공을 흉내내지 않는다 — 거절한다.
 *
 * 🔵 여기서 읽는 재고는 **라이브 재고**다(저장본이 아니라). 저장본의 재고를 이 판정에 쓰는
 *    것은 정의상 불가능하다 — 저장본에는 재고 필드가 없다(ADR-MONO-070 § 재고).
 */
export type OrderLineVerdict =
  | { ok: true }
  | { ok: false; reason: 'backend_unreachable' }
  | { ok: false; reason: 'unavailable'; productName: string }
  | { ok: false; reason: 'option_gone'; productName: string; optionName: string }
  | { ok: false; reason: 'out_of_stock'; productName: string; optionName: string }
  | { ok: false; reason: 'price_changed'; productName: string; currentPrice: number };

/** 화면 문구. 🔴 «실패» 를 «주문 실패» 로 뭉뚱그리지 않는다 — 사용자가 할 일이 다르다. */
export function orderLineVerdictMessage(verdict: OrderLineVerdict): string {
  if (verdict.ok) return '';
  switch (verdict.reason) {
    case 'backend_unreachable':
      return '주문 서버에 연결할 수 없어 주문을 진행할 수 없습니다. 잠시 후 다시 시도해 주세요.';
    case 'unavailable':
      return `"${verdict.productName}" 은(는) 현재 판매 중이 아닙니다. 장바구니에서 제외한 뒤 다시 시도해 주세요.`;
    case 'option_gone':
      return `"${verdict.productName}" 의 옵션 "${verdict.optionName}" 이(가) 더 이상 없습니다.`;
    case 'out_of_stock':
      return `"${verdict.productName}" 의 옵션 "${verdict.optionName}" 재고가 부족합니다.`;
    case 'price_changed':
      return `"${verdict.productName}" 의 가격이 ${verdict.currentPrice.toLocaleString()}원으로 변경되었습니다. 장바구니를 새로고침한 뒤 다시 시도해 주세요.`;
  }
}

/** 테스트가 라이브 호출을 갈아 끼우는 자리. 기본값이 곧 «진짜 백엔드로 간다» 는 선언이다. */
export interface VerifyOrderLinesDeps {
  fetchProduct?: (productId: string) => Promise<ProductDetail | null>;
}

export async function verifyOrderLines(
  items: CheckoutCartItem[],
  deps: VerifyOrderLinesDeps = {},
): Promise<OrderLineVerdict> {
  const fetchProduct = deps.fetchProduct ?? getLiveProduct;

  // 🔵 같은 상품이 여러 옵션으로 담겨 있을 수 있다 — 상품당 한 번만 묻는다.
  const productIds = [...new Set(items.map((i) => i.productId))];
  const live = new Map<string, ProductDetail | null>();

  for (const id of productIds) {
    try {
      live.set(id, await fetchProduct(id));
    } catch {
      // 🔴 여기서 «없는 상품» 으로 번역하지 마라. 네트워크 실패와 404 는 다른 사실이고,
      //    전자는 «모른다» 다. 모를 때 통과시키는 것이 이 함수가 막으려는 실패다.
      return { ok: false, reason: 'backend_unreachable' };
    }
  }

  for (const item of items) {
    const product = live.get(item.productId) ?? null;
    if (!product || product.status !== 'ON_SALE') {
      return { ok: false, reason: 'unavailable', productName: item.productName };
    }

    const variant = product.variants.find((v) => v.id === item.variantId);
    if (!variant) {
      return {
        ok: false,
        reason: 'option_gone',
        productName: item.productName,
        optionName: item.optionName,
      };
    }

    if (variant.stock < item.quantity) {
      return {
        ok: false,
        reason: 'out_of_stock',
        productName: item.productName,
        optionName: item.optionName,
      };
    }

    const currentPrice = product.price + variant.additionalPrice;
    if (currentPrice !== item.price) {
      return { ok: false, reason: 'price_changed', productName: item.productName, currentPrice };
    }
  }

  return { ok: true };
}
