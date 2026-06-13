import { z } from 'zod';

/**
 * Feature-local types for the scm `demand-planning-service` replenishment
 * **operator gate** — TASK-PC-FE-077. The first scm operator-MUTATION surface
 * (approve / dismiss), layered on the FE-008 `features/scm-ops` read
 * foundation (the SECOND non-IAM federated domain).
 *
 * Authoritative producer contract (do NOT redefine — consumed unchanged):
 *   `scm-platform/specs/contracts/http/demand-planning-api.md`
 *     §  GET /api/v1/demand-planning/suggestions          (list)
 *     §  GET /api/v1/demand-planning/suggestions/{id}      (detail)
 *     §  POST .../suggestions/{id}/approve                 (operator action)
 *     §  POST .../suggestions/{id}/dismiss                 (operator action)
 * Consumer obligation: `console-integration-contract.md` § 2.4.6.1 (reuses the
 * § 2.4.5 / § 2.4.6 per-domain credential rule — NOT re-derived).
 *
 * The `policies` / `sku-supplier-map` seed routes are out of scope (admin-seed,
 * not the operator gate) — there is no view-model for them here.
 *
 * TOLERANCE invariant (§ 2.4.6.1 / task Edge Case "Unknown/future status or
 * source enum"): the suggestion `status` and `source` are kept as free strings
 * — an unknown / future enum value parses to a generic value and NEVER throws.
 * Only the fields the UI strictly needs are required; everything else is
 * passthrough.
 */

// --- the suggestion view-model -------------------------------------------
//   demand-planning-api.md § GET /suggestions row shape.

export const SuggestionSchema = z
  .object({
    id: z.string(),
    skuCode: z.string().optional(),
    warehouseId: z.string().nullable().optional(),
    supplierId: z.string().nullable().optional(),
    suggestedQty: z.number().optional(),
    // SUGGESTED | APPROVED | MATERIALIZED | DISMISSED — tolerated as a free
    // string so an unknown/future status renders a generic label, never a
    // parser throw. The action buttons gate on recognised states only.
    status: z.string().optional(),
    // ALERT | … — tolerated as a free string (unknown/future → generic label).
    source: z.string().nullable().optional(),
    // why it was suggested: the available qty that tripped the reorder point.
    triggerAvailableQty: z.number().nullable().optional(),
    // present once approve materialised a DRAFT PO.
    materializedPoId: z.string().nullable().optional(),
    createdAt: z.string().optional(),
  })
  .passthrough();
export type Suggestion = z.infer<typeof SuggestionSchema>;

/** `GET /api/v1/demand-planning/suggestions` →
 *  `{ data: [...], meta: { page, size, totalElements, totalPages } }`. */
export const SuggestionPageMetaSchema = z
  .object({
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
    totalPages: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type SuggestionPageMeta = z.infer<typeof SuggestionPageMetaSchema>;

export const SuggestionPageSchema = z.object({
  content: z.array(SuggestionSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative().optional(),
});
export type SuggestionPage = z.infer<typeof SuggestionPageSchema>;

// --- the approve result --------------------------------------------------
//   demand-planning-api.md § POST .../approve →
//   { data: { id, status: "MATERIALIZED", poId, poStatus: "DRAFT" }, meta }.
//   The DRAFT-PO-only invariant is surfaced from `poStatus` (never SUBMITTED
//   by this screen — the operator dispatches it via Procurement).

export const ApproveResultSchema = z
  .object({
    id: z.string(),
    status: z.string().optional(),
    poId: z.string().nullable().optional(),
    // DRAFT — the materialised PO is DRAFT only (ADR-MONO-027 D5). Tolerated
    // as a free string so an unknown/future PO status renders generically.
    poStatus: z.string().nullable().optional(),
  })
  .passthrough();
export type ApproveResult = z.infer<typeof ApproveResultSchema>;

// --- the dismiss result --------------------------------------------------
//   demand-planning-api.md § POST .../dismiss →
//   { data: { id, status: "DISMISSED" }, meta }.

export const DismissResultSchema = z
  .object({
    id: z.string(),
    status: z.string().optional(),
  })
  .passthrough();
export type DismissResult = z.infer<typeof DismissResultSchema>;

// --- query params + pagination defaults ----------------------------------

export const REPL_DEFAULT_PAGE_SIZE = 20;
export const REPL_MAX_PAGE_SIZE = 100;

/** The closed set of producer suggestion statuses the filter exposes. An
 *  unknown/future status still renders (tolerant) but is not a filter option. */
export const KNOWN_SUGGESTION_STATUSES = [
  'SUGGESTED',
  'APPROVED',
  'MATERIALIZED',
  'DISMISSED',
] as const;

export interface SuggestionQueryParams {
  status?: string;
  skuCode?: string;
  page?: number;
  size?: number;
}

// --- action gating helpers (UI mirror of the producer state machine) -----
// The console mirrors the producer state machine for UX only — the producer
// is the final authority (a server 422 INVALID_SUGGESTION_STATE is still
// handled inline). Tolerant: unknown enums simply gate the action off (the
// action buttons gate conservatively on recognised states only).

/** Approve is reachable only for a `SUGGESTED` (or `APPROVED`) suggestion —
 *  NOT a `DISMISSED` (→ INVALID_SUGGESTION_STATE) nor an already-`MATERIALIZED`
 *  one (idempotent, no re-approve affordance offered). */
export function canApprove(status: string | undefined): boolean {
  return status === 'SUGGESTED' || status === 'APPROVED';
}

/** Dismiss is reachable for a `SUGGESTED` / `APPROVED` suggestion — NOT a
 *  `MATERIALIZED` one (→ INVALID_SUGGESTION_STATE) and not re-offered on an
 *  already-`DISMISSED` one. */
export function canDismiss(status: string | undefined): boolean {
  return status === 'SUGGESTED' || status === 'APPROVED';
}
