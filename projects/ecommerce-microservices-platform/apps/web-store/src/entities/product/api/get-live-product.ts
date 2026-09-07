import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';
import type { ProductDetail } from '@repo/types';

const productApi = createProductApi(apiClient);

/**
 * **라이브** 상품 상세 — 게이트웨이의 product-service 를 그대로 부른다.
 *
 * 🔴🔴 이 모듈은 공개 저장본과 **아무 관계가 없다.** 저장본은 표시용이고(`get-product.ts`),
 *    주문 직전의 판정은 저장본이 하면 안 된다: 저장본에는 재고가 없고 가격은 발행 시점의
 *    값이다. 그래서 «주문할 수 있는가» 를 묻는 경로는 여기 하나뿐이고, 여기는 백엔드가
 *    없으면 **던진다** — 조용히 0 이나 빈 값으로 번역하지 않는다. 판정 불가를 «괜찮다» 로
 *    번역하는 순간 백엔드가 죽은 채로 주문이 만들어진다.
 *
 * 🔵 클라이언트에서 부르면 `apiClient` 가 동일 출처 BFF(`/api/bff`)로 가고, 그 라우트가
 *    서버에서 베어러를 붙인다(shared/config/api.ts § F2).
 */
export async function getLiveProduct(id: string): Promise<ProductDetail | null> {
  return productApi.getProduct(id);
}
