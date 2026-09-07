import type { ProductSummary, ProductDetail, ProductVariant } from '@repo/types';

// Re-export backend types for use within the app
export type { ProductSummary, ProductDetail, ProductVariant };

export type ProductStatus = 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN';

/**
 * 화면이 그리는 옵션 — 백엔드 `ProductVariant` 를 **재고 축에서만** 넓힌 것.
 *
 * 🔴🔴 `stock: null` 은 "재고 0" 이 아니라 **"이 경로는 재고를 모른다"** 이다. 공개 저장본
 *    (`PublicProductOption`)에는 `stock` 필드가 **없다** — 저장된 수를 실시간 재고처럼
 *    보이게 하지 않기 위해 발행자가 애초에 안 싣는다(ADR-MONO-070 § 재고).
 *    ⇒ 세 값이 세 사실이다: `숫자` = 라이브 백엔드가 말한 수, `0` = 라이브 품절,
 *      `null` = 아무도 안 물어봤다. 화면은 `null` 을 **아무것도 안 그리는 것**으로 번역해야
 *      하고, 절대 숫자나 "재고 있음" 배지로 번역하면 안 된다.
 * 🔵 넓히기만 했으므로 백엔드에서 온 `ProductVariant` 는 그대로 이 자리에 들어간다
 *    (`number` 는 `number | null` 에 대입 가능) — 라이브 경로의 호출부는 손댈 게 없다.
 */
export interface ProductVariantView extends Omit<ProductVariant, 'stock'> {
  stock: number | null;
}

/** 재고를 «모를 수도 있는» 상세. 같은 이유로 `ProductDetail` 은 그대로 대입된다. */
export interface ProductDetailView extends Omit<ProductDetail, 'variants'> {
  variants: ProductVariantView[];
}

/**
 * 수량 입력의 상한 — **재고 주장이 아니다.**
 *
 * 🔴 라이브 재고를 모를 때(`stock === null`) `+` 버튼을 무엇으로 막을지 정하는 UI 상한일
 *    뿐이다. 진짜 상한은 주문 시점에 백엔드가 정하고(`verifyOrderLines` → 주문 saga),
 *    이 수는 화면 어디에도 "재고" 로 표시되지 않는다.
 */
export const MAX_ORDER_QUANTITY = 99;
