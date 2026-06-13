/**
 * `features/scm-config` public API (Layered-by-Feature — app/ imports only this
 * barrel, never feature internals; architecture.md § Allowed Dependencies). scm
 * replenishment **seed/config** operator screen, TASK-PC-FE-080 — the operator
 * config arm of the ADR-MONO-027 wms→scm replenishment loop (the third SCM
 * drill-in tab 설정), the operational fix-path for FE-077's
 * `SKU_SUPPLIER_UNMAPPED` gap. Shares the FE-077 `features/scm-replenishment` /
 * FE-008 `features/scm-ops` credential + flat-envelope + SCM_GATEWAY_BASE_URL +
 * 429-backoff primitives (reuse, do not fork).
 *
 * Auth (console-integration-contract § 2.4.6.2 — REUSE of the § 2.4.6 /
 * § 2.4.6.1 per-domain credential rule, NOT re-derived): this feature's server
 * client uses the **domain-facing IAM OIDC access token** (`getDomainFacingToken()`),
 * NEVER the IAM exchanged operator token (`getOperatorToken()`) — the #569
 * invariant is GAP-domain-scoped; scm has no token-exchange. Same credential for
 * the GET inspect AND the PUT upsert.
 *
 * Mutation discipline (§ 2.4.6.2): PUT is an idempotent upsert — the body IS the
 * FULL row. NO `Idempotency-Key`, NO `X-Operator-Reason` (the producer defines
 * neither). 404 on GET = "not configured yet → create" (a typed not-found, NOT
 * an error toast). Edits affect FUTURE evaluation only — the screen issues NO
 * suggestion / PO / dispatch call.
 */
export { SeedConfigScreen } from './components/SeedConfigScreen';
export { PolicyForm } from './components/PolicyForm';
export { SupplierMapForm } from './components/SupplierMapForm';
export type {
  ReorderPolicy,
  ReorderPolicyInput,
  SupplierMap,
  SupplierMapInput,
  SeedLookup,
} from './api/types';
