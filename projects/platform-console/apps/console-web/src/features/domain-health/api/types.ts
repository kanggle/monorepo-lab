import { z } from 'zod';

/**
 * `features/domain-health` — wire types for the BFF-routed Phase 7
 * "Domain Health Overview" composition envelope (TASK-PC-FE-013 —
 * ADR-MONO-017 § 3.3 #4 / `console-integration-contract.md` § 2.4.9.2).
 *
 * The wire shape is byte-verbatim from the BE `DomainHealthResponse`
 * Java record landed by the BE half of this same task. Sibling to
 * `features/operator-overview` (TASK-PC-FE-011 / § 2.4.9.1) — they
 * are independent composition surfaces with their own `route` label
 * for the per-leg metrics (`route="domain-health"` here vs
 * `route="operator-overview"` there).
 *
 * Hard invariants (mirrored from § 2.4.9.2 + the BE Javadoc):
 *  - `cards[]` is ALWAYS exactly 6 entries in fixed order
 *    `[gap, wms, scm, finance, erp, ecommerce]` (ecommerce 6th card added
 *    by TASK-MONO-241). The screen re-sorts/maps defensively by `domain`
 *    rather than positional order.
 *  - `cards[i].status` ∈ `{ ok, degraded }` ONLY — `forbidden` is
 *    NEVER emitted on this route (no permission outcome exists on a
 *    public actuator leg per the § D4 scope clarification: D4 governs
 *    the data API legs only). A defensive schema MUST reject any
 *    `'forbidden'` literal that would indicate a BE drift.
 *  - On `ok` cards `data` carries the producer's Spring Boot health
 *    JSON, typically `{"status": "UP"}`. The `data.status` slot is the
 *    Spring Boot health enum: `UP` / `DOWN` / `OUT_OF_SERVICE` /
 *    `UNKNOWN`. `DOWN` is a successful health document — NOT a
 *    BFF/network failure (the leg reached the producer; the producer
 *    self-reported critical). Distinction from card-level `degraded`
 *    which means the BFF could not reach the producer at all.
 *  - `asOf` is the server-side composition request timestamp.
 *  - On `degraded` cards `reason` is the narrow union
 *    `{ DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN }` (NO permission
 *    classifications on this route — see hard invariant above).
 *
 * READ-ONLY — there is NO mutation surface modelled here (no body
 * schemas; no Idempotency-Key / X-Operator-Reason; no POST/PUT/PATCH/
 * DELETE on this route). The same hard invariant the BE asserts.
 */

// --- domain enum ----------------------------------------------------------

/**
 * Fixed render order — § 2.4.9.2 envelope schema invariant. `ecommerce` is
 * appended LAST (TASK-MONO-241) so the existing 5 keep their positions; the
 * Domain Health surface is 6 cards while the Operator Overview (§ 2.4.9.1)
 * stays 5 (two independent surfaces).
 */
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

/**
 * Card-level status — `ok` or `degraded` ONLY. `forbidden` is NEVER emitted
 * on this route (see invariant above). The schema deliberately omits
 * `'forbidden'` so a BE drift that started emitting it is rejected at the
 * parse boundary.
 */
export const CARD_STATUSES = ['ok', 'degraded'] as const;
export type CardStatus = (typeof CARD_STATUSES)[number];

/** Degraded reasons — narrow string union per § 2.4.9.2 response schema. */
export const DEGRADED_REASONS = [
  'DOWNSTREAM_ERROR',
  'TIMEOUT',
  'CIRCUIT_OPEN',
] as const;
export type DegradedReason = (typeof DEGRADED_REASONS)[number];

// --- Spring Boot health enum --------------------------------------------

/**
 * Spring Boot `/actuator/health` aggregated status enum. The producer
 * emits this verbatim on a successful health document; the FE surfaces
 * one visual per value:
 *
 *   - `UP` → green-checkmark.
 *   - `DOWN` → red-cross (producer self-reported critical — NOT degraded).
 *   - `OUT_OF_SERVICE` → yellow-wrench (maintenance).
 *   - `UNKNOWN` → grey-question.
 */
export const HEALTH_STATUSES = ['UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN'] as const;
export type HealthStatus = (typeof HEALTH_STATUSES)[number];

const HealthStatusSchema = z.enum(HEALTH_STATUSES);

/**
 * `data` shape for `ok` cards — the producer's aggregated Spring Boot
 * health document. Only `status` is read by the UI; any extra producer
 * fields (`components`, `details`) are ignored via passthrough.
 */
export const HealthDataSchema = z
  .object({
    status: HealthStatusSchema,
  })
  .passthrough();
export type HealthData = z.infer<typeof HealthDataSchema>;

// --- per-card discriminated union ----------------------------------------

const OkCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('ok'),
  data: HealthDataSchema,
});

const DegradedCardSchema = z.object({
  domain: DomainKeySchema,
  status: z.literal('degraded'),
  reason: z.enum(DEGRADED_REASONS),
});

/** Discriminated union over `status` — `ok` | `degraded` only. */
export const CardSchema = z.discriminatedUnion('status', [
  OkCardSchema,
  DegradedCardSchema,
]);
export type Card = z.infer<typeof CardSchema>;
export type OkCard = z.infer<typeof OkCardSchema>;
export type DegradedCard = z.infer<typeof DegradedCardSchema>;

// --- envelope ------------------------------------------------------------

/**
 * The composition envelope — fixed 6-card array + `asOf` request timestamp.
 *
 * The BE invariant is "6 cards in fixed `[gap, wms, scm, finance, erp,
 * ecommerce]` order"; the schema asserts "exactly 6 cards" + per-card shape +
 * "each declared domain appears exactly once". The screen does the
 * re-sort/map defensively (it indexes by `domain` rather than positional
 * order), so a future re-ordering bug in the BE would not crash the UI.
 */
export const DomainHealthSchema = z
  .object({
    asOf: z.string().min(1),
    cards: z.array(CardSchema).length(CARD_ORDER.length),
  })
  .refine(
    (env) => {
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
export type DomainHealth = z.infer<typeof DomainHealthSchema>;
