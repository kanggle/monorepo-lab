/**
 * `features/scm-replenishment` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). scm replenishment-suggestions operator screen, TASK-PC-FE-077
 * — the human operator gate of the ADR-MONO-027 wms→scm replenishment loop and
 * the FIRST scm operator-MUTATION surface, layered on the FE-008
 * `features/scm-ops` read foundation (share, do not fork).
 *
 * Auth (console-integration-contract § 2.4.6.1 — REUSE of the § 2.4.5 / § 2.4.6
 * per-domain credential rule, NOT re-derived): this feature's server client
 * uses the **domain-facing IAM OIDC access token** (`getDomainFacingToken()`),
 * NEVER the IAM exchanged operator token (`getOperatorToken()`) — the #569
 * invariant is GAP-domain-scoped; scm has no token-exchange. Same credential
 * for the reads AND the two operator actions (approve / dismiss).
 *
 * Mutation discipline (§ 2.4.6.1): approve / dismiss carry an OPTIONAL
 * note/reason in the request BODY — NO `Idempotency-Key`, NO
 * `X-Operator-Reason` (demand-planning-api defines neither; idempotency is
 * server-side by suggestion state). approve materialises a DRAFT PO only; this
 * screen NEVER submits a PO.
 */
export { ReplenishmentScreen } from './components/ReplenishmentScreen';
export { ReplenishmentActionDialog } from './components/ReplenishmentActionDialog';
export { getReplenishmentSectionState } from './api/replenishment-state';
export type { ReplenishmentSectionState } from './api/replenishment-state';
export type {
  Suggestion,
  SuggestionPage,
  ApproveResult,
  DismissResult,
  SuggestionQueryParams,
} from './api/types';
