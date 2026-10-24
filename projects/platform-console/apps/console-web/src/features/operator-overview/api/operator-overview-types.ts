import { z } from 'zod';
import { HeadlineSchema } from '@/shared/lib/operator-overview-headline';

/**
 * `features/operator-overview` — wire types for the composed cross-domain
 * dashboard envelope (TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7 MVP /
 * `console-integration-contract.md` § 2.4.9.1).
 *
 * The wire shape is byte-verbatim from the BE `OperatorOverviewResponse`
 * Java record landed by the BE half of this same task. This is the FIRST
 * concrete `§ 2.4.9.X` composition route — generalises the GAP-only
 * `features/dashboards` (ADR-MONO-015 D1-B) across 6 backend domains
 * (gap + wms + scm + finance + erp + ecommerce). Originally produced by a
 * separate BFF; since TASK-PC-FE-302 the console-web server composes it
 * (ADR-MONO-081) with the same wire shape.
 *
 * Hard invariants (mirrored from § 2.4.9.1 + the BE Javadoc):
 *  - `cards[]` is ALWAYS exactly 6 entries in fixed order
 *    `[gap, wms, scm, finance, erp, ecommerce]`, regardless of which legs
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
export const CARD_ORDER = [
  'iam',
  'wms',
  'scm',
  'finance',
  'erp',
  'ecommerce',
] as const;
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
// TASK-PC-FE-368 — `headline` (ADR-MONO-084 D7) is OPTIONAL on every status: a card
// from before the field, or a sample/test envelope without it, still parses. It is the
// card's headline performance metric and its own outcome — never the card's status.
const OkCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('ok'),
  data: z.unknown(),
  headline: HeadlineSchema.optional(),
});

const DegradedCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('degraded'),
  reason: z.enum(DEGRADED_REASONS),
  headline: HeadlineSchema.optional(),
});

const ForbiddenCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('forbidden'),
  reason: z.enum(FORBIDDEN_REASONS),
  headline: HeadlineSchema.optional(),
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
 * The composition envelope — fixed 6-card array + `asOf` request timestamp.
 *
 * The BE invariant is "6 cards in fixed `[gap, wms, scm, finance, erp,
 * ecommerce]` order"; the schema only asserts "exactly 6 cards" + per-card
 * shape + "each declared domain appears exactly once". The screen does the
 * re-sort/map defensively (it indexes by `domain` rather than positional
 * order), so a future re-ordering bug in the BE would not crash the UI.
 */
export const OperatorOverviewSchema = z
  .object({
    asOf: z.string().min(1),
    cards: z.array(CardSchema).length(CARD_ORDER.length),
  })
  .refine(
    (env) => {
      // Each of the 6 declared domains appears exactly once (set equality).
      const seen = new Set(env.cards.map((c) => c.domain));
      if (seen.size !== CARD_ORDER.length) return false;
      for (const d of CARD_ORDER) if (!seen.has(d)) return false;
      return true;
    },
    {
      message:
        'cards[] MUST contain each of [gap,wms,scm,finance,erp,ecommerce] exactly once',
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

// 🔵 TASK-PC-FE-364 — the six legs now ask for business figures (one call per
// card, swapped one-for-one): locked accounts · unshipped orders · draft POs ·
// open reconciliation discrepancies · my approval inbox · orders to ship. Every
// leg is a filtered list read with `size=1`, so each card surfaces exactly ONE
// page total from the producer's own body (contract § 2.4.9.1 «ok card data
// shapes»; the shared fixture `specs/contracts/fixtures/operator-overview-leg-bodies.json`).
//
// 🔴🔴 The TASK-PC-FE-295 lesson still binds every schema below: these were once
// written against the contract's illustrative example instead of the producers
// (`inventorySnapshot.totalStockUnits`, `balance.amount` — keys no producer ever
// emitted), and because every field is optional + passthrough the parse
// SUCCEEDED and the cards silently rendered «—». Shapes here are transcribed
// from the producers' controllers/DTOs; the leg-body census test feeds the real
// bodies through the renderers.

// IAM locked accounts — `GET /api/admin/accounts?status=LOCKED&page=0&size=1`
// (`AccountSearchResponse` — `totalElements` is the account-level LOCKED count).
export const GapDataSchema = z
  .object({
    totalElements: z.number().int().nonnegative().nullable().optional(),
  })
  .passthrough();
export type GapData = z.infer<typeof GapDataSchema>;

// wms unshipped orders — `GET /api/v1/admin/dashboard/orders?status=RECEIVED&page=0&size=1`.
// The producer body is the read-model page: `{ content[], page: { number, size,
// totalElements, totalPages }, sort }` (`OrderDashboardController#list` ->
// `PageResponse<OrderSummaryResponse>`). The card surfaces `page.totalElements`.
// 🔴 Never a sum over `content[]` — one page is not the whole set (295 AC-6).
export const WmsDataSchema = z
  .object({
    page: z
      .object({
        totalElements: z.number().int().nonnegative().nullable().optional(),
      })
      .passthrough()
      .optional(),
  })
  .passthrough();
export type WmsData = z.infer<typeof WmsDataSchema>;

// scm draft purchase orders — `GET /api/v1/procurement/po?status=DRAFT&page=0&size=1`
// through the scm gateway. The producer body is `{ data: { content[], page,
// size, totalElements, totalPages }, meta: { timestamp } }`
// (`PurchaseOrderController#search` -> `ApiEnvelope<PageResponse<PurchaseOrderResponse>>`).
// The card surfaces `data.totalElements`. (The procurement read carries no S5
// `meta.warning` — that hint belonged to the inventory-visibility leg this
// replaced.)
export const ScmDataSchema = z
  .object({
    data: z
      .object({
        totalElements: z.number().int().nonnegative().nullable().optional(),
      })
      .passthrough()
      .optional(),
  })
  .passthrough();
export type ScmData = z.infer<typeof ScmDataSchema>;

// finance open reconciliation discrepancies —
// `GET /api/finance/ledger/reconciliation/discrepancies?status=OPEN&page=0&size=1`
// (ledger-service). The producer body is `{ data: [ DiscrepancyResponse ],
// meta: { page, size, totalElements, totalPages, timestamp } }`
// (`ReconciliationController#listDiscrepancies`). The card surfaces ONLY
// `meta.totalElements`.
//
// **F5 money discipline**: the rows carry `expectedMinor` / `actualMinor` as
// minor-units STRINGS. The card reads no row at all, so no money field is
// modelled here — nothing to coerce, by construction.
export const FinanceDataSchema = z
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
export type FinanceData = z.infer<typeof FinanceDataSchema>;

// erp my approval inbox — `GET /api/erp/approval/inbox?page=0&size=1`.
// The producer body is `{ data: [ ApprovalSummaryView ], meta: { page, size,
// totalElements, totalPages, timestamp, actorEmployeeId? } }`
// (`ApprovalInboxController#inbox` -> `ApiEnvelope.ofList`).
//
// 🔴 `meta.actorEmployeeId` PRESENT = the employee the caller acts as, and
// `meta.totalElements` is that employee's pending queue. ABSENT = the caller's
// account is linked to no employee (approval-api.md § v2.4) — the page is
// empty, but that means «not linked», NOT «nothing waiting». The card renders a
// distinct state for it and never a 0. The id itself is never rendered.
export const ErpDataSchema = z
  .object({
    meta: z
      .object({
        totalElements: z.number().int().nonnegative().nullable().optional(),
        // nullable: the producer never sends null (it omits the key), but a null
        // must read as «not linked» rather than fail the whole parse into «—».
        actorEmployeeId: z.string().nullable().optional(),
      })
      .passthrough()
      .optional(),
    data: z.array(z.unknown()).optional(),
  })
  .passthrough();
export type ErpData = z.infer<typeof ErpDataSchema>;

// ecommerce orders to ship —
// `GET http://ecommerce.local/api/admin/orders?status=CONFIRMED&page=0&size=1`.
// The producer body is `{ content[], page, size, totalElements }`
// (`AdminOrderListResponse` — no `totalPages`); the card surfaces ONLY
// `totalElements` = CONFIRMED (paid, shipping not started) orders.
// `totalElements: 0` is a real answer — surfaced as "0", NOT hidden (the
// renderer uses an explicit null/undefined check, not truthiness).
export const EcommerceDataSchema = z
  .object({
    totalElements: z.number().int().nonnegative().nullable().optional(),
  })
  .passthrough();
export type EcommerceData = z.infer<typeof EcommerceDataSchema>;
