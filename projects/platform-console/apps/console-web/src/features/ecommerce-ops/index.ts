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
 * v1 scope = products (list/detail/register/update/delete + variant + stock)
 * + orders (list/detail/status-change, TASK-PC-FE-083) + product images
 * (list/presigned-upload/register/update/delete, TASK-PC-FE-082 — the Phase 1b
 * CLOSING facet, embedded in the product detail).
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

// ---------------------------------------------------------------------------
// Orders facet (TASK-PC-FE-083 — ADR-MONO-031 Phase 1b orders slice)
// ---------------------------------------------------------------------------
export { OrdersScreen } from './components/OrdersScreen';
export { OrderDetail } from './components/OrderDetail';
export { OrderStatusDialog } from './components/OrderStatusDialog';

export {
  getOrdersSectionState,
  getOrderDetailSectionState,
} from './api/orders-state';
export type {
  OrdersSectionState,
  OrderDetailSectionState,
} from './api/orders-state';

export type {
  OrderSummary,
  OrderList,
  OrderDetail as OrderDetailData,
  OrderItem,
  ShippingAddress,
  OrderStatusChangeBody,
  OrderStatusChangeResponse,
  OrderListParams,
  OrderStatus,
} from './api/order-types';
export { allowedTransitions, ORDER_STATUS_VALUES } from './api/order-types';

// ---------------------------------------------------------------------------
// Users facet (TASK-PC-FE-084 — ADR-MONO-031 Phase 2b — READ-ONLY)
// ---------------------------------------------------------------------------
export { UsersScreen } from './components/UsersScreen';
export { UserDetail } from './components/UserDetail';

export {
  getUsersSectionState,
  getUserDetailSectionState,
} from './api/users-state';
export type {
  UsersSectionState,
  UserDetailSectionState,
} from './api/users-state';

export type {
  UserSummary,
  UserList,
  UserDetail as UserDetailData,
  UserListParams,
  UserStatus,
} from './api/user-types';
export { USER_STATUS_VALUES } from './api/user-types';

// ---------------------------------------------------------------------------
// Promotions facet (TASK-PC-FE-086 — ADR-031 Phase 3b — FULL CRUD)
// ---------------------------------------------------------------------------
export { PromotionsScreen } from './components/PromotionsScreen';
export { PromotionDetail } from './components/PromotionDetail';
export { PromotionForm } from './components/PromotionForm';
export { CouponIssueDialog } from './components/CouponIssueDialog';

export {
  getPromotionsSectionState,
  getPromotionDetailSectionState,
} from './api/promotions-state';
export type {
  PromotionsSectionState,
  PromotionDetailSectionState,
} from './api/promotions-state';

export type {
  PromotionSummary,
  PromotionList,
  PromotionDetail as PromotionDetailData,
  PromotionMutationResponse,
  IssueCouponResponse,
  PromotionListParams,
  CreatePromotionBody,
  UpdatePromotionBody,
  IssueCouponBody,
  PromotionStatus,
  DiscountType,
} from './api/types';
export {
  PROMOTION_STATUS_VALUES,
  DISCOUNT_TYPE_VALUES,
} from './api/types';

// ---------------------------------------------------------------------------
// Shippings facet (TASK-PC-FE-088 — ADR-031 Phase 4b — LIST + STATUS CHANGE + REFRESH)
// ---------------------------------------------------------------------------
export { ShippingsScreen } from './components/ShippingsScreen';
export { ShipFormDialog } from './components/ShipFormDialog';

export { getShippingsSectionState } from './api/shippings-state';
export type { ShippingsSectionState } from './api/shippings-state';

export type {
  Shipping,
  ShippingSummary,
  ShippingList,
  ShippingListParams,
  ShippingStatus,
  UpdateShippingStatusBody,
} from './api/shipping-types';
export {
  SHIPPING_STATUS_VALUES,
  allowedNextStatus,
} from './api/shipping-types';

// ---------------------------------------------------------------------------
// Notifications facet (TASK-PC-FE-089 — ADR-031 Phase 5b — list + create + edit)
// ---------------------------------------------------------------------------
export { NotificationsScreen } from './components/NotificationsScreen';
export { TemplateForm } from './components/TemplateForm';

export {
  getNotificationsSectionState,
  getNotificationDetailSectionState,
} from './api/notifications-state';
export type {
  NotificationsSectionState,
  NotificationDetailSectionState,
} from './api/notifications-state';

export type {
  NotificationTemplateSummary,
  NotificationTemplateList,
  NotificationTemplateDetail,
  NotificationMutationResponse,
  NotificationTemplateListParams,
  CreateTemplateBody,
  UpdateTemplateBody,
  TemplateType,
  NotificationChannel,
} from './api/notification-types';
export {
  TEMPLATE_TYPE_VALUES,
  TEMPLATE_TYPE_LABELS,
  NOTIFICATION_CHANNEL_VALUES,
} from './api/notification-types';

// ---------------------------------------------------------------------------
// Image facet (TASK-PC-FE-082 — ADR-MONO-031 Phase 1b CLOSING slice). The
// product-image management surface is embedded in `ProductDetail` (no separate
// route / sidebar leaf); `ImageManager` is exported for completeness/tests.
// ---------------------------------------------------------------------------
export { ImageManager } from './components/ImageManager';
export { ImageUploadField } from './components/ImageUploadField';

export type {
  ImageItem,
  ImageList,
  PresignedUrlResponse,
  RegisterImageResponse,
  PresignedUrlBody,
  RegisterImageBody,
  UpdateImageBody,
  ImageContentType,
} from './api/image-types';
export {
  IMAGE_ALLOWED_CONTENT_TYPES,
  IMAGE_MAX_BYTES,
  IMAGE_MAX_PER_PRODUCT,
  isAllowedImageContentType,
} from './api/image-types';
