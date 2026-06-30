/**
 * Server-side scm gateway operations client — public barrel.
 *
 * TASK-PC-FE-145 split the former single ~413-line module into cohesive
 * units while preserving this module path as the stable public surface
 * (every `@/features/scm-ops/api/scm-api` import — the `/api/scm/**` route
 * handlers + `scm-state.ts` + `scm-api.test.ts` — keeps working unchanged):
 *
 *   - `scm-client.ts`                  — the hardened `callScm` HTTP core +
 *                                        the scm FLAT error-envelope parser +
 *                                        the 429 `Retry-After` / `X-Cache`
 *                                        header readers + the page-param
 *                                        helper + the per-domain-credential /
 *                                        resilience / read-only narrative
 *                                        (barrel-internal — NOT re-exported,
 *                                        so the `scm-api.test.ts`
 *                                        `Object.keys` guard sees exactly the
 *                                        6 endpoint exports).
 *   - `scm-procurement-api.ts`         — the procurement PO reads
 *                                        (`listPurchaseOrders` / `getPurchaseOrder`).
 *   - `scm-inventory-visibility-api.ts`— the inventory-visibility reads
 *                                        (`getSnapshot` / `getSkuBreakdown` /
 *                                        `getStaleness` / `getNodes`), each
 *                                        surfacing the REQUIRED S5 meta.warning.
 *
 * STRICTLY READ-ONLY. Pure structural split — 0 behavior / contract /
 * log-event change. The public export set is EXACTLY the 6 endpoint
 * functions (pinned by `scm-api.test.ts`).
 */

export * from './scm-procurement-api';
export * from './scm-inventory-visibility-api';
