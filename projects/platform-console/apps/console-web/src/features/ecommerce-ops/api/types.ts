import { z } from 'zod';

/**
 * Feature-local types for the ecommerce `product-service` operator surface —
 * the FIRST ecommerce **write** binding federated by the console
 * (TASK-PC-FE-081, ADR-MONO-031 Phase 1b). Drives the in-console equivalent
 * of the standalone `admin-dashboard` product screens: list / detail /
 * register / update / delete + variant inline CRUD + stock adjust.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `product-service`
 *   `AdminProductController` (`/api/admin/products/**`, BE-366 operator-plane)
 *   + the public `ProductController` detail read (`GET /api/products/{id}`).
 * Consumer obligation: `console-integration-contract.md` § 2.4.10
 * (#1–9 product endpoints; inherits the non-IAM domain credential/tenant/
 * envelope/resilience rules).
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies. Producer DTO field names + types
 * are matched verbatim (RegisterProductRequest / UpdateProductRequest /
 * AddVariantRequest / UpdateVariantRequest / AdjustStockRequest + the
 * response DTOs ProductListResponse / ProductDetailResponse /
 * RegisterProductResponse / AdjustStockResponse / VariantDetail).
 *
 * TOLERANCE invariant (task Edge Case "producer DTO 불일치" — defensive):
 * read shapes are permissive (`.passthrough()`); only the fields the UI
 * strictly needs are required, everything else passes through. An unknown /
 * future `status` enum parses to a generic string and NEVER throws.
 */

// ===========================================================================
// READ shapes
// ===========================================================================

/** ProductStatus producer enum (ON_SALE | SOLD_OUT | HIDDEN). Kept tolerant
 *  as a plain string so a future status renders rather than throwing. */
export const PRODUCT_STATUS_VALUES = ['ON_SALE', 'SOLD_OUT', 'HIDDEN'] as const;
export type ProductStatus = (typeof PRODUCT_STATUS_VALUES)[number];

/** 1. list — ProductListResponse.ProductSummaryItem row. */
export const ProductSummarySchema = z
  .object({
    id: z.string(),
    name: z.string(),
    status: z.string(),
    price: z.number(),
    thumbnailUrl: z.string().nullable().optional(),
    categoryId: z.string().nullable().optional(),
    sellerId: z.string().nullable().optional(),
  })
  .passthrough();
export type ProductSummary = z.infer<typeof ProductSummarySchema>;

/** 1. list — ProductListResponse envelope ({content, page, size, totalElements}). */
export const ProductListSchema = z
  .object({
    content: z.array(ProductSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
  })
  .passthrough();
export type ProductList = z.infer<typeof ProductListSchema>;

/** 2. detail — ProductDetailResponse.VariantDetailItem. */
export const VariantSchema = z
  .object({
    id: z.string(),
    optionName: z.string(),
    stock: z.number().int(),
    additionalPrice: z.number().int(),
  })
  .passthrough();
export type Variant = z.infer<typeof VariantSchema>;

/** 2. detail — ProductDetailResponse.ImageItem (read-only here; image CRUD is
 *  the out-of-scope PC-FE-082 facet). Tolerated so detail parses cleanly. */
export const ProductImageSchema = z
  .object({
    imageId: z.string(),
    url: z.string(),
    sortOrder: z.number().int(),
    isPrimary: z.boolean(),
  })
  .passthrough();
export type ProductImage = z.infer<typeof ProductImageSchema>;

/** 2. detail — ProductDetailResponse. */
export const ProductDetailSchema = z
  .object({
    id: z.string(),
    name: z.string(),
    description: z.string().nullable().optional(),
    status: z.string(),
    price: z.number(),
    categoryId: z.string().nullable().optional(),
    thumbnailUrl: z.string().nullable().optional(),
    sellerId: z.string().nullable().optional(),
    images: z.array(ProductImageSchema).default([]),
    variants: z.array(VariantSchema).default([]),
  })
  .passthrough();
export type ProductDetail = z.infer<typeof ProductDetailSchema>;

/** 3. register — RegisterProductResponse ({id}). */
export const RegisterProductResponseSchema = z
  .object({ id: z.string() })
  .passthrough();
export type RegisterProductResponse = z.infer<
  typeof RegisterProductResponseSchema
>;

/** 9. adjust stock — AdjustStockResponse ({variantId, currentStock}). */
export const AdjustStockResponseSchema = z
  .object({
    variantId: z.string(),
    currentStock: z.number().int(),
  })
  .passthrough();
export type AdjustStockResponse = z.infer<typeof AdjustStockResponseSchema>;

// ===========================================================================
// WRITE request bodies — matched to the producer request DTOs verbatim
// ===========================================================================

/** RegisterVariantRequest (optionName / stock / additionalPrice). */
export const RegisterVariantBodySchema = z.object({
  optionName: z.string().min(1),
  stock: z.number().int().nonnegative(),
  additionalPrice: z.number().int().nonnegative(),
});
export type RegisterVariantBody = z.infer<typeof RegisterVariantBodySchema>;

/** 3. RegisterProductRequest. `name` + `price` (positive) + ≥1 variant are
 *  required producer-side; description/categoryId/thumbnailUrl/sellerId
 *  optional. */
export const RegisterProductBodySchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  price: z.number().int().positive(),
  categoryId: z.string().optional(),
  thumbnailUrl: z.string().optional(),
  sellerId: z.string().optional(),
  variants: z.array(RegisterVariantBodySchema).min(1),
});
export type RegisterProductBody = z.infer<typeof RegisterProductBodySchema>;

/** 4. UpdateProductRequest — ALL fields optional (PATCH partial update);
 *  price `@Min(0)` producer-side. */
export const UpdateProductBodySchema = z.object({
  name: z.string().optional(),
  description: z.string().optional(),
  price: z.number().int().nonnegative().optional(),
  status: z.enum(PRODUCT_STATUS_VALUES).optional(),
  thumbnailUrl: z.string().optional(),
});
export type UpdateProductBody = z.infer<typeof UpdateProductBodySchema>;

/** 6. AddVariantRequest (optionName / stock>=0 / additionalPrice>=0). */
export const AddVariantBodySchema = z.object({
  optionName: z.string().min(1),
  stock: z.number().int().nonnegative(),
  additionalPrice: z.number().int().nonnegative(),
});
export type AddVariantBody = z.infer<typeof AddVariantBodySchema>;

/** 7. UpdateVariantRequest (optionName / additionalPrice>=0 — NO stock; stock
 *  is adjusted via #9). */
export const UpdateVariantBodySchema = z.object({
  optionName: z.string().min(1),
  additionalPrice: z.number().int().nonnegative(),
});
export type UpdateVariantBody = z.infer<typeof UpdateVariantBodySchema>;

/** 9. AdjustStockRequest (variantId / quantity (signed delta) / reason). */
export const AdjustStockBodySchema = z.object({
  variantId: z.string().min(1),
  quantity: z.number().int(),
  reason: z.string().min(1),
});
export type AdjustStockBody = z.infer<typeof AdjustStockBodySchema>;

// ===========================================================================
// list query params + pagination defaults
// ===========================================================================

export const PRODUCT_DEFAULT_PAGE_SIZE = 20;
export const PRODUCT_MAX_PAGE_SIZE = 100;

export interface ProductListParams {
  categoryId?: string;
  status?: string;
  page?: number;
  size?: number;
}
