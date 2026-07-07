/**
 * Public surface barrel for the ecommerce-ops API client (TASK-PC-FE-217).
 *
 * Aligns ecommerce-ops with the `<name>-api.ts` api-barrel convention already
 * used by the other federated domains (`scm-ops/api/scm-api.ts`,
 * `wms-ops/api/wms-api.ts`, `wms-outbound-ops/api/outbound-api.ts`): the
 * per-area endpoint slices are gathered here so feature-internal consumers
 * (the `*-state.ts` server-state builders) resolve every endpoint function
 * through ONE stable module path.
 *
 * Each slice owns one ecommerce area's read/write endpoints (all attach the
 * domain-facing IAM OIDC token server-side via `ecommerce-client.ts`):
 *   - `products-api.ts`      — products list/detail/register/update/delete +
 *                              variants + stock (+ `getProductsSummary`)
 *   - `orders-api.ts`        — orders list/detail/status-change (+ summary/insights)
 *   - `users-api.ts`         — users list/detail (READ-ONLY, + summary)
 *   - `promotions-api.ts`    — promotions CRUD + coupon issue (+ summary)
 *   - `shippings-api.ts`     — shippings list/status/tracking (+ summary)
 *   - `notifications-api.ts` — notification templates list/detail/create/update (+ summary)
 *   - `sellers-api.ts`       — sellers list/detail/register/provision/suspend/close (+ summary)
 *   - `images-api.ts`        — product-image list/presign/register/update/delete
 *
 * Pure structural aggregation — 0 behavior / contract / endpoint change. Each
 * slice's own module path stays valid (the `app/api/ecommerce/**` route
 * handlers and the per-slice `*-api.test.ts` suites keep importing their slice
 * directly), so this barrel is additive.
 */
export * from './products-api';
export * from './orders-api';
export * from './users-api';
export * from './promotions-api';
export * from './shippings-api';
export * from './notifications-api';
export * from './sellers-api';
export * from './images-api';
