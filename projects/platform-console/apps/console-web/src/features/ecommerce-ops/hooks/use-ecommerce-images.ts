'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  ImageListSchema,
  type ImageList,
  type PresignedUrlResponse,
  type RegisterImageResponse,
  type RegisterImageBody,
  type UpdateImageBody,
  type ImageItem,
} from '../api/image-types';

/**
 * Client-side ecommerce-ops **image** hooks (TASK-PC-FE-082 — the Phase 1b
 * CLOSING facet). Every server call goes to the same-origin
 * `/api/ecommerce/products/{id}/images/**` proxy (the typed api-client's single
 * backend entry point); the proxy attaches the HttpOnly **domain-facing IAM
 * OIDC token** server-side — the browser never reads a token or calls the
 * ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * EXCEPTION — the presigned byte PUT (`uploadToPresignedUrl`) is a DIRECT
 * browser→S3 cross-origin upload to the `uploadUrl` minted by #11. It does NOT
 * go through the api-client / proxy and carries NO credential (the presign IS
 * the authorization). It uses `XMLHttpRequest` for upload-progress events
 * (`fetch` has no upload-progress API).
 *
 * Mutation discipline (§ 2.4.10): NO `Idempotency-Key` (producer defines none).
 * Every mutation invalidates the image list AND the product detail query (the
 * producer recomputes the product `thumbnailUrl` from the primary image, so
 * the detail must refetch).
 */

const IMAGES_KEY = 'ecommerce-images';
// Mirror of use-ecommerce-products' ECOMMERCE_KEY/detail key — invalidated so
// the product detail (thumbnailUrl) refetches after an image mutation. Kept as
// a literal (not imported) to avoid a hook↔hook import.
const PRODUCTS_DETAIL_KEY = 'ecommerce-products';

export function imagesKey(productId: string) {
  return [IMAGES_KEY, 'list', productId] as const;
}

async function fetchImages(productId: string): Promise<ImageList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/products/${encodeURIComponent(productId)}/images`,
  );
  return ImageListSchema.parse(raw);
}

export function useImages(productId: string | null, initial?: ImageList) {
  return useQuery({
    queryKey: imagesKey(productId ?? ''),
    queryFn: () => fetchImages(productId as string),
    enabled: productId !== null,
    initialData: initial,
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
}

// --- mutations ------------------------------------------------------------

function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  productId: string,
) {
  qc.invalidateQueries({ queryKey: imagesKey(productId) });
  qc.invalidateQueries({
    queryKey: [PRODUCTS_DETAIL_KEY, 'detail', productId],
  });
  // The primary-image thumbnail also appears in the list summaries.
  qc.invalidateQueries({ queryKey: [PRODUCTS_DETAIL_KEY, 'list'] });
}

/** #11 — mint a presigned upload URL (server proxy). */
export function useCreateUploadUrl() {
  return useMutation({
    mutationFn: ({
      productId,
      contentType,
      contentLength,
    }: {
      productId: string;
      contentType: string;
      contentLength: number;
    }) =>
      apiClient.post<PresignedUrlResponse>(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/images/upload-url`,
        { contentType, contentLength },
      ),
  });
}

/** #12 — register an uploaded objectKey (server proxy). */
export function useRegisterImage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      productId,
      body,
    }: {
      productId: string;
      body: RegisterImageBody;
    }) =>
      apiClient.post<RegisterImageResponse>(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/images`,
        body,
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}

/** #13 — update sortOrder / isPrimary (server proxy). */
export function useUpdateImage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      productId,
      imageId,
      body,
    }: {
      productId: string;
      imageId: string;
      body: UpdateImageBody;
    }) =>
      apiClient.patch<ImageItem>(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`,
        body,
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}

/** #14 — delete an image (server proxy; 204). */
export function useDeleteImage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      productId,
      imageId,
    }: {
      productId: string;
      imageId: string;
    }) =>
      apiClient.delete<void>(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`,
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}

// ---------------------------------------------------------------------------
// Presigned byte upload — DIRECT browser → S3 (no credential; the presign is
// the authorization). XHR for upload-progress. NOT routed through the proxy.
// ---------------------------------------------------------------------------

export class PresignedUploadError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'PresignedUploadError';
    this.status = status;
  }
}

/**
 * PUT `file` bytes to the presigned `uploadUrl`. Resolves on a 2xx S3 response;
 * rejects with {@link PresignedUploadError} on a non-2xx / network / abort.
 * `onProgress` receives a 0–1 fraction. NO `Authorization` header / cookie is
 * attached (the presign carries the auth; adding a bearer would break the S3
 * signature). The S3 response body is XML/empty — only the status is judged.
 */
export function uploadToPresignedUrl(
  uploadUrl: string,
  file: File,
  onProgress?: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl, true);
    // The content type MUST match what was sent to #11 (it is part of the
    // presigned signature). withCredentials stays false — no cookie to S3.
    xhr.setRequestHeader('Content-Type', file.type);
    xhr.withCredentials = false;

    if (xhr.upload && onProgress) {
      xhr.upload.onprogress = (e: ProgressEvent) => {
        if (e.lengthComputable && e.total > 0) {
          onProgress(Math.min(1, e.loaded / e.total));
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress?.(1);
        resolve();
      } else {
        reject(
          new PresignedUploadError(
            xhr.status,
            `presigned upload failed (${xhr.status})`,
          ),
        );
      }
    };
    xhr.onerror = () =>
      reject(new PresignedUploadError(0, 'presigned upload network error'));
    xhr.onabort = () =>
      reject(new PresignedUploadError(0, 'presigned upload aborted'));

    if (signal) {
      if (signal.aborted) {
        xhr.abort();
        return;
      }
      signal.addEventListener('abort', () => xhr.abort(), { once: true });
    }

    xhr.send(file);
  });
}
