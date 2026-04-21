// Common
export type { ApiErrorResponse, PaginationParams, PaginatedResponse } from './common';

// Guards
export { isApiErrorResponse, isApiError, getErrorMessage, ERROR_MESSAGES } from './guards';

// Auth
export type {
  SignupRequest,
  SignupResponse,
  LoginRequest,
  TokenResponse,
  RefreshRequest,
  LogoutRequest,
} from './auth';

// Product
export type {
  ProductStatus,
  ProductSummary,
  ProductVariant,
  ProductDetail,
  ProductListParams,
  CreateProductRequest,
  UpdateProductRequest,
  StockAdjustmentRequest,
  StockAdjustmentResponse,
  CreateProductResponse,
  UpdateProductResponse,
} from './product';

// Order
export type {
  OrderStatus,
  ShippingAddress,
  OrderItem,
  OrderItemDetail,
  PlaceOrderRequest,
  PlaceOrderResponse,
  OrderListParams,
  OrderSummary,
  OrderDetail,
  CancelOrderResponse,
  AdminOrderSummary,
  AdminOrderDetail,
  AdminOrderStatusChangeRequest,
  AdminOrderStatusChangeResponse,
} from './order';

// Search
export type {
  SearchSortOrder,
  SearchProductItem,
  CategoryFacet,
  PriceRangeFacet,
  SearchFacets,
  SearchRequest,
  SearchResponse,
} from './search';

// Payment
export type {
  PaymentStatus,
  PaymentResponse,
  PaymentConfirmRequest,
  PaymentConfirmResponse,
} from './payment';

// Notification
export type {
  NotificationChannel,
  NotificationStatus,
  NotificationTemplateType,
  NotificationSummary,
  NotificationDetail,
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
  NotificationTemplateSummary,
  NotificationTemplateDetail,
  CreateNotificationTemplateRequest,
  UpdateNotificationTemplateRequest,
  NotificationTemplateResponse,
  NotificationTemplateListParams,
} from './notification';

// Wishlist
export type {
  AddWishlistRequest,
  AddWishlistResponse,
  WishlistItem,
  WishlistCheckResponse,
} from './wishlist';

// Shipping
export type {
  ShippingStatus,
  ShippingStatusHistoryEntry,
  ShippingResponse,
  ShippingSummary,
  ShippingListParams,
  UpdateShippingStatusRequest,
  UpdateShippingStatusResponse,
} from './shipping';

// Promotion
export type {
  PromotionStatus,
  DiscountType,
  PromotionSummary,
  PromotionDetail,
  PromotionListParams,
  CreatePromotionRequest,
  CreatePromotionResponse,
  UpdatePromotionRequest,
  UpdatePromotionResponse,
  IssueCouponsRequest,
  IssueCouponsResponse,
  CouponStatus,
  CouponSummary,
  CouponListParams,
  ApplyCouponRequest,
  ApplyCouponResponse,
} from './promotion';

// Review
export type {
  CreateReviewRequest,
  CreateReviewResponse,
  UpdateReviewRequest,
  UpdateReviewResponse,
  ReviewItem,
  ReviewListResponse,
  ReviewSummary,
  MyReviewItem,
  MyReviewListResponse,
  ReviewListParams,
} from './review';

// User
export type {
  UserProfile,
  UpdateUserProfileRequest,
  UpdateUserProfileResponse,
  Address,
  AddressListResponse,
  CreateAddressRequest,
  CreateAddressResponse,
  UpdateAddressRequest,
  UserStatus,
  AdminUserSummary,
  AdminUserDetail,
  AdminUserListParams,
} from './user';
