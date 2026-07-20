import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  ProductListSchema,
  type ProductList,
  ProductDetailSchema,
  type ProductDetail,
  VariantSchema,
  type Variant,
  RegisterProductResponseSchema,
  type RegisterProductResponse,
  AdjustStockResponseSchema,
  type AdjustStockResponse,
  ProductAreaSummarySchema,
  type ProductAreaSummary,
  type ProductListParams,
  type RegisterProductBody,
  type UpdateProductBody,
  type AddVariantBody,
  type UpdateVariantBody,
  type AdjustStockBody,
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side ecommerce `product-service` operations client (TASK-PC-FE-081 —
 * the FIRST ecommerce **write** surface, ADR-MONO-031 Phase 1b). Drives the
 * in-console product catalog operator surface: list / detail / register /
 * update / delete + variant inline CRUD + stock adjust.
 *
 * Server-only by construction (same posture as `outbound-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/ecommerce/products/**` proxy routes, which attach the HttpOnly
 * credential here server-side.
 *
 * ── THE AUTH MODEL (console-integration-contract § 2.4.10, inheriting the
 *    non-IAM § 2.4.5 per-domain rules) ──────────────────────────────────────
 *
 * Per ADR-MONO-017 D2.A this surface is console-web → ecommerce gateway
 * DIRECT (no console-bff write leg). The ecommerce gateway requires
 * `account_type=OPERATOR` on the IAM OIDC token (BE-366 removed the producer
 * `X-User-Role` gate). Therefore this client uses `getDomainFacingToken()`
 * (the assumed tenant-scoped IAM OIDC token when the operator switched to a
 * customer, else the base access token — net-zero; ADR-MONO-020 D4) and NEVER
 * `getOperatorToken()` (that is the IAM `/api/admin/**` exchanged credential —
 * wrong issuer/type here; the #569 invariant is GAP-domain-scoped). A test
 * pins that `getOperatorToken` is never called.
 *
 * Tenant invariant (§ 2.4.10): ecommerce resolves the tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim (gateway `TenantClaimValidator` injects
 * the trusted `X-Tenant-Id`; the repository `WHERE tenant_id` chokepoint
 * isolates) — the console therefore does NOT send `X-Tenant-Id`.
 *
 * Mutation discipline (§ 2.4.10, updated by TASK-BE-536): the ecommerce product
 * admin API defines no `version` / ETag anywhere, and no `Idempotency-Key` on
 * MOST mutations — those still get none from the console (carrying a key the
 * producer ignores is a defect); confirm-gate (UI) + producer state guards
 * (the `409 CONFLICT` optimistic-lock + the `422 VALIDATION_ERROR` family) are
 * the double-submit / conflict defence for those. `registerProduct` and
 * `adjustStock` are the exception: the producer now REQUIRES
 * `Idempotency-Key` on those two (a duplicate write moves a real balance with
 * no other guard), so this client mints one per call — see their doc comments.
 * Mutations are reason-free (the surface defines no `X-Operator-Reason`); the
 * one exception is the stock-adjust producer body, which carries a `reason`
 * field IN THE BODY (AdjustStockRequest), not a header.
 *
 * Error envelope (§ 2.4.10 / § 2.5): ecommerce uses the FLAT shape
 * `{ code, message, timestamp }` (the shared `ErrorResponse.of` — DISTINCT
 * from wms's nested `{ error: { code } }`). `parseEcommerceError()` reads the
 * flat shape and tolerates an absent / non-JSON body without crashing.
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (whole-session re-login); `403` → `ApiError` (inline
 * "not available to your role"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable — the `409 CONFLICT` path drives a refetch + retry-prompt, never
 * a silent auto-retry); `503`/timeout/network → `EcommerceUnavailableError`
 * (ONLY this section degrades).
 *
 * Logging: structured, server-side only; the IAM access token and any product
 * data are NEVER logged (redacted).
 */

/**
 * Per-slice observability + message label for the product surface. The products
 * slice uses the EMPTY log-event infix — the emitted events are the bare
 * `ecommerce_ok` / `ecommerce_unauthorized` / … (no `_product_` infix),
 * preserved verbatim from the original inline call site.
 */
const PRODUCT_LABEL: EcommerceCallLabel = {
  event: '',
  errorNoun: 'product',
  unavailableLabel: 'product-service',
  timedOutLabel: 'product-service',
  failedLabel: 'product-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, PRODUCT_DEFAULT_PAGE_SIZE, PRODUCT_MAX_PAGE_SIZE);

// ===========================================================================
// READS (no mutation artifacts)
// ===========================================================================

/** 1 — GET /admin/products (paginated summaries). */
export function listProducts(
  params: ProductListParams = {},
): Promise<ProductList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.categoryId) qs.set('categoryId', params.categoryId);
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products?${qs.toString()}`,
    },
    (j) => ProductListSchema.parse(j),
    PRODUCT_LABEL,
  );
}

/** GET /admin/products/summary — period-based counts (TASK-PC-FE-164).
 *  Returns { today, week, month, total } for the tenant. */
export function getProductsSummary(): Promise<ProductAreaSummary> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: '/products/summary',
    },
    (j) => ProductAreaSummarySchema.parse(j),
    PRODUCT_LABEL,
  );
}

/** 2 — GET /products/{id} (public detail read path — admin controller has no
 *  GET /{id}; contract row #2). Carries variants[] + images[]. */
export function getProduct(id: string): Promise<ProductDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/products/${encodeURIComponent(id)}`,
    },
    (j) => ProductDetailSchema.parse(j),
    PRODUCT_LABEL,
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key — producer defines
// none; state-guard-dependent — 409/422 surfaced inline)
// ===========================================================================

/**
 * 3 — POST /admin/products (register). The producer now REQUIRES
 * `Idempotency-Key` (TASK-BE-536 — a replayed registration would otherwise
 * create a second product with a second stock ledger).
 *
 * <p>Key-generation-location note: this route currently has no client-side
 * confirm/retry dialog state (unlike e.g. wms-outbound's cancel dialog, which
 * mints its key once per confirmed user action and reuses it across a retry —
 * see `use-outbound-cancel-dialog.ts`). Absent that machinery, the key is
 * minted here, once per invocation of this function — i.e. once per BFF proxy
 * request, which today is 1:1 with a user's confirm click. A future
 * network-level auto-retry of the SAME submit (without a fresh user click)
 * would need a client-held key to replay correctly; that is a frontend
 * follow-up, not attempted here (backend-scoped task).
 */
export function registerProduct(
  body: RegisterProductBody,
): Promise<RegisterProductResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: '/products',
      body,
      idempotencyKey: crypto.randomUUID(),
    },
    (j) => RegisterProductResponseSchema.parse(j),
    PRODUCT_LABEL,
  );
}

/** 4 — PATCH /admin/products/{id} (update; partial). */
export function updateProduct(
  id: string,
  body: UpdateProductBody,
): Promise<RegisterProductResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PATCH',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(id)}`,
      body,
    },
    (j) => RegisterProductResponseSchema.parse(j),
    PRODUCT_LABEL,
  );
}

/** 5 — DELETE /admin/products/{id} (204 No Content). */
export function deleteProduct(id: string): Promise<void> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'DELETE',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(id)}`,
    },
    undefined,
    PRODUCT_LABEL,
  );
}

/** 6 — POST /admin/products/{id}/variants (add variant → VariantDetail). */
export function addVariant(
  productId: string,
  body: AddVariantBody,
): Promise<Variant> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/variants`,
      body,
    },
    (j) => VariantSchema.parse(j),
    PRODUCT_LABEL,
  );
}

/** 7 — PATCH /admin/products/{id}/variants/{variantId} (update variant). */
export function updateVariant(
  productId: string,
  variantId: string,
  body: UpdateVariantBody,
): Promise<Variant> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PATCH',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
      body,
    },
    (j) => VariantSchema.parse(j),
    PRODUCT_LABEL,
  );
}

/** 8 — DELETE /admin/products/{id}/variants/{variantId} (204 No Content). */
export function deleteVariant(
  productId: string,
  variantId: string,
): Promise<void> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'DELETE',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/variants/${encodeURIComponent(variantId)}`,
    },
    undefined,
    PRODUCT_LABEL,
  );
}

/**
 * 9 — PATCH /admin/products/{id}/stock (adjust stock; body carries the signed
 * `quantity` delta + `reason`). Confirm-gated. The producer now REQUIRES
 * `Idempotency-Key` (TASK-BE-536 — two identical deltas can both be genuine, so
 * only a client key can tell a retry apart from a real second adjustment). See
 * {@link registerProduct} for the key-generation-location rationale — same
 * reasoning applies here (minted once per BFF proxy invocation).
 */
export function adjustStock(
  productId: string,
  body: AdjustStockBody,
): Promise<AdjustStockResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PATCH',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/products/${encodeURIComponent(productId)}/stock`,
      body,
      idempotencyKey: crypto.randomUUID(),
    },
    (j) => AdjustStockResponseSchema.parse(j),
    PRODUCT_LABEL,
  );
}
