import { productReviews, type PublicReview, type PublicReviewSummary } from '@demo/public-data';
import { readStoreSnapshot } from '@/shared/public-data/store-snapshot';

/**
 * 공개 상품 상세의 리뷰 — **출처는 공개 저장본이다** (ADR-MONO-075 D3).
 *
 * 🔴 상품(`entities/product/api/get-product.ts`)과 **같은 봉투 하나**에서 읽는다. 판독자가 인자를 안
 *    받으므로 «상품은 저장본, 리뷰는 백엔드» 같은 반쪽 구현을 표현할 수 없다 — 이 파일이 생기기 전의
 *    화면이 정확히 그 반쪽이었다(데모가 꺼진 동안 리뷰 API 502, 2026-09-15 라이브 실측).
 * 🔴 서버에서만 부른다. `features/review/index.ts` 배럴에 넣지 않는다 — 클라이언트 컴포넌트가 배럴을
 *    임포트하는 순간 판독자와 번들 시드 JSON 이 브라우저 번들로 끌려간다.
 */
export interface SnapshotProductReviews {
  reviews: PublicReview[];
  summary: PublicReviewSummary;
  /**
   * 🔴 「샘플 리뷰」 표시가 걸리는 **판정 값**(D4). 저장본이 백엔드에서 발행된 것이 아니면(번들 시드 ·
   *    authored) 리뷰는 합성이다. 백엔드 발행본으로 바뀌는 날 이 값이 `false` 가 되고 표시도 자동으로
   *    사라진다 — 지워야 할 문구가 남는 실패를 막는다(`fromSnapshot` 과 같은 설계).
   */
  isSample: boolean;
}

export async function getSnapshotProductReviews(productId: string): Promise<SnapshotProductReviews> {
  const { data, envelope } = await readStoreSnapshot();
  const { reviews, summary } = productReviews(data.reviews, productId);
  return { reviews, summary, isSample: envelope.source !== 'backend' };
}
