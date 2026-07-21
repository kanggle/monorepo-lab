'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  ProductListSchema,
  type ProductList,
  ProductDetailSchema,
  type ProductDetail,
  type ProductListParams,
  type RegisterProductBody,
  type UpdateProductBody,
  type AddVariantBody,
  type UpdateVariantBody,
  type AdjustStockBody,
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side ecommerce-ops product hooks (TASK-PC-FE-081 — architecture.md
 * § Server vs Client Components — React Query is client-only). Every call goes
 * to the same-origin `/api/ecommerce/products/**` proxy (the typed API
 * client's single backend entry point); the proxy attaches the HttpOnly
 * **domain-facing IAM OIDC token** server-side — the browser never reads a
 * token or calls the ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * No tight auto-refetch loop (no `refetchInterval` / `refetchOnWindowFocus`);
 * a re-query is a filter/page change (a new queryKey), an explicit drill, or a
 * mutation-success `invalidateQueries`.
 *
 * Mutation discipline (§ 2.4.10): NO `Idempotency-Key` (the producer defines
 * none) — confirm-gate (in the screen) + producer state guards (409/422)
 * are the double-submit / conflict defence. Mutations are reason-free except
 * stock-adjust, whose `reason` rides in the producer body.
 */

const ECOMMERCE_KEY = 'ecommerce-products';

const clampSize = (size?: number): number =>
  clampPageSize(size, PRODUCT_DEFAULT_PAGE_SIZE, PRODUCT_MAX_PAGE_SIZE);

// --- list -----------------------------------------------------------------

export function productsKey(params: ProductListParams) {
  return [
    ECOMMERCE_KEY,
    'list',
    params.status ?? null,
    params.categoryId ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildProductsQs(params: ProductListParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.categoryId) qs.set('categoryId', params.categoryId);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchProducts(params: ProductListParams): Promise<ProductList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/products?${buildProductsQs(params)}`,
  );
  return ProductListSchema.parse(raw);
}

export function useProducts(params: ProductListParams, initial?: ProductList) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.categoryId;
  return useQuery({
    queryKey: productsKey(params),
    queryFn: () => fetchProducts(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- detail ---------------------------------------------------------------

async function fetchProduct(id: string): Promise<ProductDetail> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/products/${encodeURIComponent(id)}`,
  );
  return ProductDetailSchema.parse(raw);
}

export function useProduct(id: string | null, initial?: ProductDetail) {
  return useQuery({
    queryKey: [ECOMMERCE_KEY, 'detail', id] as const,
    queryFn: () => fetchProduct(id as string),
    enabled: id !== null,
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}

// --- mutations ------------------------------------------------------------

/** Invalidate the list + (optionally) one product's detail after a mutation. */
function invalidate(
  qc: ReturnType<typeof useQueryClient>,
  productId?: string,
) {
  // Refetch a mounted list (no flash) AND drop the inactive seeded cache so a
  // remount after a cross-page register/update re-seeds from the fresh SSR
  // render — an inactive seed-only query is not refetched (TASK-PC-FE-126).
  qc.invalidateQueries({ queryKey: [ECOMMERCE_KEY, 'list'] });
  qc.removeQueries({ queryKey: [ECOMMERCE_KEY, 'list'], type: 'inactive' });
  if (productId) {
    qc.invalidateQueries({ queryKey: [ECOMMERCE_KEY, 'detail', productId] });
  }
}

export function useRegisterProduct() {
  const qc = useQueryClient();
  return useMutation({
    // idempotencyKey is minted per confirmed create in the form hook and sent in
    // the body; the proxy strips it back out (TASK-PC-FE-252).
    mutationFn: ({
      body,
      idempotencyKey,
    }: {
      body: RegisterProductBody;
      idempotencyKey: string;
    }) =>
      apiClient.post<{ id: string }>('/api/ecommerce/products', {
        ...body,
        idempotencyKey,
      }),
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdateProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateProductBody }) =>
      apiClient.patch<{ id: string }>(
        `/api/ecommerce/products/${encodeURIComponent(id)}`,
        body,
      ),
    onSuccess: (_d, { id }) => invalidate(qc, id),
  });
}

export function useDeleteProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.delete<void>(
        `/api/ecommerce/products/${encodeURIComponent(id)}`,
      ),
    onSuccess: (_d, id) => invalidate(qc, id),
  });
}

export function useAddVariant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      productId,
      body,
    }: {
      productId: string;
      body: AddVariantBody;
    }) =>
      apiClient.post(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/variants`,
        body,
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}

export function useUpdateVariant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      body,
    }: {
      productId: string;
      variantId: string;
      body: UpdateVariantBody;
    }) =>
      apiClient.patch(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
        body,
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}

export function useDeleteVariant() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      productId,
      variantId,
    }: {
      productId: string;
      variantId: string;
    }) =>
      apiClient.delete<void>(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}

export function useAdjustStock() {
  const qc = useQueryClient();
  return useMutation({
    // idempotencyKey minted per confirmed adjustment in StockAdjustDialog; sent
    // in the body, stripped by the proxy (TASK-PC-FE-252).
    mutationFn: ({
      productId,
      body,
      idempotencyKey,
    }: {
      productId: string;
      body: AdjustStockBody;
      idempotencyKey: string;
    }) =>
      apiClient.patch<{ variantId: string; currentStock: number }>(
        `/api/ecommerce/products/${encodeURIComponent(productId)}/stock`,
        { ...body, idempotencyKey },
      ),
    onSuccess: (_d, { productId }) => invalidate(qc, productId),
  });
}
