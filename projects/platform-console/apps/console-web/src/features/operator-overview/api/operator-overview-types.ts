import { z } from 'zod';

/**
 * `features/operator-overview` — wire types for the BFF-routed cross-domain
 * dashboard envelope (TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7 MVP /
 * `console-integration-contract.md` § 2.4.9.1).
 *
 * The wire shape is byte-verbatim from the BE `OperatorOverviewResponse`
 * Java record landed by the BE half of this same task. This is the FIRST
 * concrete `§ 2.4.9.X` composition route — generalises the GAP-only
 * `features/dashboards` (ADR-MONO-015 D1-B) across 5 backend domains
 * (gap + wms + scm + finance + erp) via the new `console-bff`.
 *
 * Hard invariants (mirrored from § 2.4.9.1 + the BE Javadoc):
 *  - `cards[]` is ALWAYS exactly 5 entries in fixed order
 *    `[gap, wms, scm, finance, erp]`, regardless of which legs
 *    succeeded. The screen re-sorts/maps defensively (the BE
 *    invariant is enforced; the FE re-sort is belt-and-braces).
 *  - `asOf` is the server-side composition request timestamp (NOT
 *    per-leg response).
 *  - `data` is present only on `ok` cards; `reason` only on
 *    `degraded` / `forbidden` cards (Jackson `NON_NULL` elides the
 *    unused field server-side; the schema models the discriminated
 *    union accordingly).
 *
 * READ-ONLY — there is NO mutation surface modelled here (no body
 * schemas; no Idempotency-Key / X-Operator-Reason; no POST/PUT/PATCH/
 * DELETE on this route or any future `§ 2.4.9.X`). The same hard
 * invariant the BE asserts.
 */

// --- domain enum ----------------------------------------------------------

/** Fixed render order — § 2.4.9.1 envelope schema invariant. */
export const CARD_ORDER = ['gap', 'wms', 'scm', 'finance', 'erp'] as const;
export type DomainKey = (typeof CARD_ORDER)[number];

const DomainKeySchema = z.enum(CARD_ORDER);

// --- per-card status ------------------------------------------------------

export const CARD_STATUSES = ['ok', 'degraded', 'forbidden'] as const;
export type CardStatus = (typeof CARD_STATUSES)[number];

/** Degraded reasons — narrow string union per § 2.4.9.1 response schema. */
export const DEGRADED_REASONS = [
  'DOWNSTREAM_ERROR',
  'TIMEOUT',
  'CIRCUIT_OPEN',
] as const;
export type DegradedReason = (typeof DEGRADED_REASONS)[number];

/** Forbidden reasons — narrow string union per § 2.4.9.1 response schema. */
export const FORBIDDEN_REASONS = [
  'PERMISSION_DENIED',
  'TENANT_FORBIDDEN',
  'MISSING_PREREQUISITE',
] as const;
export type ForbiddenReason = (typeof FORBIDDEN_REASONS)[number];

// --- per-card discriminated union ----------------------------------------

/**
 * `ok` card — `data` is a domain-shaped opaque payload. The BE `data` field
 * is the producer's raw response body (e.g. `Map<String,Object>` in Java);
 * the FE keeps it as `unknown` and narrows defensively inside each card
 * renderer (each domain's UI extracts only what it surfaces).
 *
 * NOTE: the BE `data` slot can carry an arbitrary producer-shaped object;
 * the schema only asserts presence + record-ness. The card renderer is
 * the boundary that narrows further.
 */
const OkCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('ok'),
  data: z.unknown(),
});

const DegradedCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('degraded'),
  reason: z.enum(DEGRADED_REASONS),
});

const ForbiddenCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('forbidden'),
  reason: z.enum(FORBIDDEN_REASONS),
});

/** Discriminated union over `status`. */
export const CardSchema = z.discriminatedUnion('status', [
  OkCardSchema,
  DegradedCardSchema,
  ForbiddenCardSchema,
]);
export type Card = z.infer<typeof CardSchema>;
export type OkCard = z.infer<typeof OkCardSchema>;
export type DegradedCard = z.infer<typeof DegradedCardSchema>;
export type ForbiddenCard = z.infer<typeof ForbiddenCardSchema>;

// --- envelope ------------------------------------------------------------

/**
 * The composition envelope — fixed 5-card array + `asOf` request timestamp.
 *
 * The BE invariant is "5 cards in fixed `[gap, wms, scm, finance, erp]`
 * order"; the schema only asserts "exactly 5 cards" + per-card shape +
 * "each declared domain appears exactly once". The screen does the
 * re-sort/map defensively (it indexes by `domain` rather than positional
 * order), so a future re-ordering bug in the BE would not crash the UI.
 */
export const OperatorOverviewSchema = z
  .object({
    asOf: z.string().min(1),
    cards: z.array(CardSchema).length(5),
  })
  .refine(
    (env) => {
      // Each of the 5 declared domains appears exactly once (set equality).
      const seen = new Set(env.cards.map((c) => c.domain));
      if (seen.size !== CARD_ORDER.length) return false;
      for (const d of CARD_ORDER) if (!seen.has(d)) return false;
      return true;
    },
    {
      message:
        'cards[] MUST contain each of [gap,wms,scm,finance,erp] exactly once',
      path: ['cards'],
    },
  );
export type OperatorOverview = z.infer<typeof OperatorOverviewSchema>;

// --- per-domain data shapes (narrow, defensive — surfaced read-only) -----

/**
 * Each `ok` card's `data` shape is the producer's verbatim body. The FE
 * narrows defensively at the renderer boundary — the schemas below model
 * ONLY the few fields each card surfaces in the MVP UI; unknown extra
 * fields are ignored (`.passthrough()` is the runtime default for
 * `z.object`, but the narrower shape pins what the UI reads).
 *
 * Honest constraint: the BE adapters return whatever the producer returns
 * (`Map<String, Object>`); the producer specs are authoritative for the
 * full shape. The narrowers below extract the minimum the MVP UI needs;
 * a `safeParse(...).success === false` falls through to a "data summary
 * unavailable" branch (the card stays `ok` but renders a defensive
 * placeholder — never a UI crash on a producer schema drift).
 */

// GAP accounts summary — `GET /api/admin/accounts?page=0&size=1`
// (page total snapshot — `totalElements` is the count surfaced).
export const GapDataSchema = z
  .object({
    totalElements: z.number().int().nonnegative().nullable().optional(),
  })
  .passthrough();
export type GapData = z.infer<typeof GapDataSchema>;

// wms inventory snapshot — `GET /api/v1/admin/dashboard/inventory`.
// The producer surfaces stock total + alert count; the snapshot fields
// vary by producer schema version — only the optional summary numbers are
// read defensively.
export const WmsDataSchema = z
  .object({
    inventorySnapshot: z
      .object({
        totalStockUnits: z.number().nonnegative().nullable().optional(),
        alertCount: z.number().int().nonnegative().nullable().optional(),
      })
      .passthrough()
      .optional(),
  })
  .passthrough();
export type WmsData = z.infer<typeof WmsDataSchema>;

// scm inventory visibility — producer `GET /api/inventory-visibility/snapshot`
// (the BFF leg path; FE consumes the BFF, not the producer — TASK-MONO-162).
// Surfaces optional `meta.warning` (S5 "Not for procurement decisions"
// non-blocking hint per § 2.4.6 invariant).
export const ScmDataSchema = z
  .object({
    meta: z
      .object({
        warning: z.string().optional(),
      })
      .passthrough()
      .optional(),
    nodes: z.array(z.unknown()).optional(),
    skus: z.array(z.unknown()).optional(),
  })
  .passthrough();
export type ScmData = z.infer<typeof ScmDataSchema>;

// finance balance health — `GET /api/finance/accounts/{id}/balances`.
// **F5 money discipline**: `amount` is a STRING (minor units); never
// `Number(...)` / `parseFloat(...)` / `parseInt(...)`. The MVP card
// surfaces only "balance available" framing — no numeric coercion.
// `currency` is the ISO 4217 code (3-letter); both fields are optional
// for the MVP (the card stays "ok" even if the producer's shape varies).
export const FinanceDataSchema = z
  .object({
    balance: z
      .object({
        amount: z.string().optional(),
        currency: z.string().optional(),
      })
      .passthrough()
      .optional(),
    accountId: z.string().optional(),
  })
  .passthrough();
export type FinanceData = z.infer<typeof FinanceDataSchema>;

// erp departments snapshot — `GET /api/erp/masterdata/departments?active=true&page=0&size=1`.
// `meta.totalElements` is the active department count; surfaced verbatim.
export const ErpDataSchema = z
  .object({
    meta: z
      .object({
        totalElements: z.number().int().nonnegative().nullable().optional(),
      })
      .passthrough()
      .optional(),
    data: z.array(z.unknown()).optional(),
  })
  .passthrough();
export type ErpData = z.infer<typeof ErpDataSchema>;
