import { readStoreSnapshot } from '@/shared/public-data/store-snapshot';
import { toProductDetail, type StoreProductDetail } from './snapshot-mappers';

export type { StoreProductDetail };

/**
 * 공개 상품 상세. **출처는 공개 저장본이다** — 근거와 «TASK-FE-061 과 무엇이 다른가» 는
 * `get-products.ts` 의 머리 주석에 한 번만 적었다(같은 사실을 두 곳에 적으면 한쪽만 고쳐진다).
 *
 * 🔴 목록과 상세가 **같은 봉투 하나**에서 나온다. 판독자가 인자를 안 받는 것이 그 성질을
 *    만든다(read.ts § 목록과 상세가 섞이지 않는다) — "목록은 저장본, 상세는 백엔드" 같은
 *    반쪽 구현을 표현할 방법이 없다.
 *
 * 🔵 반환 `null` 의 뜻이 바뀌었다: 예전엔 "백엔드가 404" 였고 지금은 **"이 저장본에 없다"**
 *    이다. 화면(`not-found.tsx`)이 둘을 같게 그리는 것은 방문자 입장에서 같은 사실이기
 *    때문이고, 「저장본에 없음」을 「세상에 없음」으로 읽히게 하는 자리는 목록 쪽의
 *    `corpusSize` 가 맡는다.
 */
export async function getProduct(id: string): Promise<StoreProductDetail | null> {
  const { data } = await readStoreSnapshot();
  const product = data.products.find((p) => p.id === id);
  if (!product) return null;
  return toProductDetail(product, data.categories);
}
