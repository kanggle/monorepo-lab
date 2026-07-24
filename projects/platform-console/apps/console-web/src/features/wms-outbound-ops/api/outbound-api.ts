/**
 * Server-side wms `outbound-service` operations client (TASK-PC-FE-057 —
 * the SECOND wms surface, ADR-MONO-022 § D7 operator leg). Drives an
 * ecommerce-originated outbound order PICKING → PICKED → PACKING/PACKED →
 * SHIPPED from inside the console.
 *
 * Server-only by construction (same posture as `wms-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/wms/outbound/**` proxy routes, which attach the HttpOnly credential
 * here server-side.
 *
 * ── THE AUTH-MODEL DIVERGENCE (the crux — console-integration-contract
 *    § 2.4.5.1, inherited verbatim from § 2.4.5) ───────────────────────────
 *
 * wms's `outbound-service-api.md` § Global Conventions requires
 * `Authorization: Bearer <IAM OIDC access token>` DIRECTLY (RS256, ADR-001;
 * the wms gateway + outbound-service validate it against IAM JWKS and enforce
 * `tenant_id=wms` from the JWT claim itself). wms has NO token-exchange.
 *
 * Therefore this client uses `getDomainFacingToken()` (the assumed
 * tenant-scoped IAM OIDC token when the operator switched to a customer, else
 * the base access token — net-zero; ADR-MONO-020 D4) and NEVER
 * `getOperatorToken()`. This is the EXACT INVERSE of the IAM
 * `features/{accounts,audit,operators,dashboards}` clients — the #569
 * trust-boundary invariant is GAP-domain-scoped and does NOT generalise to
 * wms (the wms gateway *requires* the IAM OIDC token). A test pins this (the
 * `getOperatorToken` path MUST be absent).
 *
 * Tenant invariant (§ 2.4.5.1): wms resolves the tenant from the JWT
 * `tenant_id=wms` claim — NOT an `X-Tenant-Id` header. The console therefore
 * does NOT send `X-Tenant-Id`; wms rejects cross-tenant producer-side.
 *
 * Mutation discipline (§ 2.4.5.1): every POST/PATCH (ops 5–8) carries an
 * `Idempotency-Key` (caller-supplied, stable per a confirmed action, fresh
 * per a new attempt) and is reason-free — wms does NOT define
 * `X-Operator-Reason` (carrying IAM's § 2.4.1 reason header over is a
 * header-matrix-drift defect; a test asserts its absence). All reads carry
 * NO mutation artifacts.
 *
 * Error envelope (§ 2.4.5.1 / § 2.5): wms uses the NESTED shape
 * `{ "error": { "code", "message", "timestamp", … } }` — DISTINCT from
 * GAP's flat `{ code, message, timestamp }`. `parseOutboundError()` reads the
 * wms shape (and tolerates an absent/flat body without crashing).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (whole-session re-login); `403` → `ApiError` (inline
 * "not available to your role"); `404`/`400`/`422`/`409` → `ApiError` (inline
 * actionable — the `409 CONFLICT` path drives a refetch + retry-prompt, never
 * a silent auto-retry); `503`/timeout/network → `WmsOutboundUnavailableError`
 * (ONLY this section degrades).
 *
 * Logging: structured, server-side only; the IAM access token and any wms
 * data are NEVER logged (redacted).
 *
 * ── SPLIT (TASK-PC-FE-147) ──────────────────────────────────────────────────
 * Implementation is split into domain sub-modules. This file is now a barrel
 * that re-exports the full public surface — all import sites remain unchanged.
 *
 *   outbound-client.ts          — CallOptions, callOutbound, clampSize (internal)
 *   outbound-order-api.ts       — listOrders, getOrder, getSaga,
 *                                 listPickingRequests, cancelOrder
 *   outbound-fulfillment-api.ts — ConfirmPickLine, confirmPick,
 *                                 PackingUnitLine, createPackingUnit,
 *                                 sealPackingUnit, confirmShipping
 *   outbound-shipment-api.ts    — resolveShipmentIdForOrder (wms admin read-model)
 *   outbound-logistics-api.ts   — resolveDispatchIdForShipment, retryDispatch
 *                                 (logistics-service, scm gateway — TASK-PC-FE-258)
 */

// ORDER domain (reads + cancel)
export {
  listOrders,
  getOrder,
  getSaga,
  listPickingRequests,
  cancelOrder,
} from './outbound-order-api';

// FULFILLMENT domain (picking + packing + shipping)
export type { ConfirmPickLine } from './outbound-fulfillment-api';
export {
  confirmPick,
  createPackingUnit,
  sealPackingUnit,
  confirmShipping,
} from './outbound-fulfillment-api';
export type { PackingUnitLine } from './outbound-fulfillment-api';

// SHIPMENT resolver (wms admin read-model — the wms half of the two-hop resolve)
export { resolveShipmentIdForOrder } from './outbound-shipment-api';

// LOGISTICS dispatch (scm gateway — the carrier-dispatch retry, TASK-PC-FE-258)
export {
  resolveDispatchIdForShipment,
  retryDispatch,
} from './outbound-logistics-api';
