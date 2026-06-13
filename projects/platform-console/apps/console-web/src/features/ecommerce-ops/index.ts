/**
 * `features/ecommerce-ops` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). ecommerce product operations section, TASK-PC-FE-081 — the
 * FIRST ecommerce **write** surface (ADR-MONO-031 Phase 1b; § 2.4.9.1/§ 2.4.9.2
 * bind ecommerce only as console-bff READ legs). The console equivalent of the
 * standalone `admin-dashboard` product screens.
 *
 * Auth (console-integration-contract § 2.4.10, inheriting the non-IAM § 2.4.5
 * rules): per ADR-MONO-017 D2.A this surface is console-web → ecommerce
 * gateway DIRECT (no console-bff write leg). The server client uses the
 * **domain-facing IAM OIDC token** (`getDomainFacingToken()`), NEVER the IAM
 * exchanged operator token (`getOperatorToken()`) — the #569 invariant is
 * GAP-domain-scoped. Tenant rides in the JWT `tenant_id` claim (NO
 * `X-Tenant-Id` header). NO `Idempotency-Key` (producer defines none) —
 * confirm-gate + producer state guards.
 *
 * v1 scope = list / detail / register / update / delete + variant inline CRUD
 * + stock adjust. Image (presigned) + orders are out of scope (PC-FE-082/083).
 */
export { ProductsScreen } from './components/ProductsScreen';
export { ProductDetail } from './components/ProductDetail';
export { ProductForm } from './components/ProductForm';
export { VariantEditor } from './components/VariantEditor';
export { StockAdjustDialog } from './components/StockAdjustDialog';
export { ConfirmDialog } from './components/ConfirmDialog';

export {
  getProductsSectionState,
  getProductDetailSectionState,
} from './api/products-state';
export type {
  ProductsSectionState,
  ProductDetailSectionState,
} from './api/products-state';

export type {
  ProductSummary,
  ProductList,
  ProductDetail as ProductDetailData,
  Variant,
  ProductImage,
  ProductListParams,
  RegisterProductBody,
  UpdateProductBody,
  AddVariantBody,
  UpdateVariantBody,
  AdjustStockBody,
  ProductStatus,
} from './api/types';
