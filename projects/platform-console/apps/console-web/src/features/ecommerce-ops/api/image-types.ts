import { z } from 'zod';

/**
 * Feature-local types for the ecommerce `product-service` **image** operator
 * surface — the Phase 1b CLOSING facet (TASK-PC-FE-082, ADR-MONO-031). Drives
 * the in-console equivalent of the standalone `admin-dashboard` product-image
 * screens: list / presigned upload / register / update (sortOrder·isPrimary) /
 * delete, embedded inside the product detail (`ProductDetail` → `ImageManager`).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `product-service` `AdminProductImageController`
 *   (`/api/admin/products/{id}/images/**`, BE-366 operator-plane).
 * Consumer obligation: `console-integration-contract.md` § 2.4.10
 * (#10 list / #11 presigned upload url / #12 register / #13 update /
 * #14 delete; inherits the non-IAM § 2.4.5 credential/tenant/envelope/
 * resilience rules — flat `{ code, message, timestamp }` envelope).
 *
 * Producer DTO field names + types matched verbatim (`ImageResponse` /
 * `ImageListResponse` / `PresignedUrlRequest` / `PresignedUrlResponse` /
 * `RegisterImageRequest` / `RegisterImageResponse` / `UpdateImageRequest`).
 *
 * TOLERANCE invariant (task Edge Case "producer DTO 불일치" — defensive):
 * read shapes are permissive (`.passthrough()`); only the fields the UI
 * strictly needs are required, everything else passes through. `uploadedAt`
 * is consumed as a plain string (both `ImageResponse.uploadedAt` (already a
 * string) and `RegisterImageResponse.uploadedAt` (an `Instant` serialised to
 * an ISO string) parse cleanly).
 */

// ===========================================================================
// Upload constraints — MIRROR the producer `ProductImageService` limits so the
// client can reject obviously-bad files BEFORE the presigned round-trip. These
// are a UX aid, NOT a trust boundary (the producer re-validates: a
// `MediaValidationException` → 400 `MEDIA_VALIDATION_FAILED`).
// ===========================================================================

/** Producer `ALLOWED_CONTENT_TYPES` (ProductImageService). */
export const IMAGE_ALLOWED_CONTENT_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
] as const;
export type ImageContentType = (typeof IMAGE_ALLOWED_CONTENT_TYPES)[number];

/** Producer `MAX_CONTENT_LENGTH` (5 MiB). */
export const IMAGE_MAX_BYTES = 5 * 1024 * 1024;

/** Producer `MAX_IMAGES_PER_PRODUCT` — gates the "add" affordance client-side
 *  (the producer enforces it too → 422 `IMAGE_LIMIT_EXCEEDED`). */
export const IMAGE_MAX_PER_PRODUCT = 10;

export function isAllowedImageContentType(
  type: string,
): type is ImageContentType {
  return (IMAGE_ALLOWED_CONTENT_TYPES as readonly string[]).includes(type);
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** #10/#12/#13 — a single image row. `ImageResponse` / `RegisterImageResponse`
 *  share these fields (register adds nothing the UI needs beyond them). */
export const ImageItemSchema = z
  .object({
    imageId: z.string(),
    objectKey: z.string(),
    sortOrder: z.number().int(),
    isPrimary: z.boolean(),
    url: z.string(),
    uploadedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type ImageItem = z.infer<typeof ImageItemSchema>;

/** #10 — `ImageListResponse` envelope (`{ images: [...] }`). */
export const ImageListSchema = z
  .object({
    images: z.array(ImageItemSchema).default([]),
  })
  .passthrough();
export type ImageList = z.infer<typeof ImageListSchema>;

/** #11 — `PresignedUrlResponse` ({ uploadUrl, objectKey, expiresAt }). The
 *  `uploadUrl` is the S3 presigned PUT target the BROWSER uploads to directly;
 *  `objectKey` is echoed back into the #12 register call. */
export const PresignedUrlResponseSchema = z
  .object({
    uploadUrl: z.string(),
    objectKey: z.string(),
    expiresAt: z.string().nullable().optional(),
  })
  .passthrough();
export type PresignedUrlResponse = z.infer<typeof PresignedUrlResponseSchema>;

/** #12 — `RegisterImageResponse` (same row shape as `ImageItem`). */
export const RegisterImageResponseSchema = ImageItemSchema;
export type RegisterImageResponse = ImageItem;

// ===========================================================================
// WRITE request bodies — matched to the producer request DTOs verbatim
// ===========================================================================

/** #11 — `PresignedUrlRequest` ({ contentType @NotBlank, contentLength
 *  @Positive long }). */
export const PresignedUrlBodySchema = z.object({
  contentType: z.string().min(1),
  contentLength: z.number().int().positive(),
});
export type PresignedUrlBody = z.infer<typeof PresignedUrlBodySchema>;

/** #12 — `RegisterImageRequest` ({ objectKey @NotBlank, sortOrder @Min 0 int,
 *  isPrimary bool }). */
export const RegisterImageBodySchema = z.object({
  objectKey: z.string().min(1),
  sortOrder: z.number().int().nonnegative(),
  isPrimary: z.boolean(),
});
export type RegisterImageBody = z.infer<typeof RegisterImageBodySchema>;

/** #13 — `UpdateImageRequest` ({ sortOrder Integer?, isPrimary Boolean? }) —
 *  a PATCH; both fields nullable producer-side, but the console requires AT
 *  LEAST ONE present (an empty PATCH is a client defect → never forwarded). */
export const UpdateImageBodySchema = z
  .object({
    sortOrder: z.number().int().nonnegative().optional(),
    isPrimary: z.boolean().optional(),
  })
  .refine(
    (b) => b.sortOrder !== undefined || b.isPrimary !== undefined,
    { message: 'at least one of sortOrder / isPrimary is required' },
  );
export type UpdateImageBody = z.infer<typeof UpdateImageBodySchema>;
