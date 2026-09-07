export { ProductCard } from './ui/ProductCard';
export { ProductImage } from './ui/ProductImage';
export { getProducts } from './api/get-products';
export type { StoreListParams, StoreProductListResult } from './api/get-products';
export { getProduct } from './api/get-product';
export type { StoreProductDetail } from './api/snapshot-mappers';
// 🔴 저장본(위)과 라이브(아래)는 **다른 출처**다. 배럴에서 이름으로 갈리게 둔다 —
//    호출부가 `getProduct` 와 `getLiveProduct` 중 무엇을 부르는지가 곧 «무엇을 주장하는가» 다.
export { getLiveProduct } from './api/get-live-product';
export { MAX_ORDER_QUANTITY } from './model/types';
export type { ProductDetailView, ProductVariantView } from './model/types';
