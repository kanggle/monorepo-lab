import { z } from 'zod';

/**
 * Shared leaf primitives for the finance `ledger-service` wire types
 * (TASK-PC-FE-233 split · TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * These were the leaf module of `features/ledger-ops/api/types/`, imported by
 * every per-concept ledger type module. Four of those concept modules
 * (`trial-balance` · `period` · `reconciliation` · `fx`) back reads that
 * `features/finance-overview` also consumes for the `/finance` landing
 * snapshot, so they were promoted to `shared/api/ledger-types/` per
 * `architecture.md` § Forbidden Dependencies ("공유 가치는 `shared/` 로 승격")
 * — and a `shared/` module may not import a feature, so their leaf moved with
 * them. `features/ledger-ops/api/types/money.ts` re-exports everything here,
 * so every ledger-ops consumer's `../api/types` import is unchanged.
 *
 * The **F5 `Money` primitive itself** is one level further out, in
 * `shared/lib/money.ts` — it is pure value + renderer (no producer knowledge)
 * and is shared with `features/finance-ops` too.
 *
 * F5 MONEY INVARIANT — see `features/ledger-ops/api/types/index.ts` for the
 * full contract narrative (§ 2.4.7.1). `Money` is
 * `{ amount: "<string-integer-minor-units>", currency }` — `amount` is a
 * **string-encoded integer in minor units**, NEVER a JS `Number` / float.
 * `formatMoney(...)` is the only sanctioned way to render a Money value.
 */

/**
 * The `exchangeRate` on a journal line is an exact-decimal provenance
 * factor in minor units (e.g. `"13.5"`, `"1"`) — a **string**, NEVER a
 * float (F5). It is surfaced verbatim (no arithmetic). The schema is a
 * permissive decimal-string regex; a stray non-decimal value is tolerated
 * as a free string (rendered as-is, never throws — defensive tolerant
 * parser). There is intentionally NO numeric coercion helper for it.
 */
export const ExchangeRateSchema = z
  .string()
  .regex(/^-?\d+(\.\d+)?$/, 'exchangeRate must be a decimal string (F5)')
  .or(z.string()); // tolerant fallback — render verbatim, never throw

// ---------------------------------------------------------------------------
// finance success envelope meta (timestamp + optional pagination).
// ---------------------------------------------------------------------------

export const LedgerMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
    totalPages: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type LedgerMeta = z.infer<typeof LedgerMetaSchema>;

// ---------------------------------------------------------------------------
// shared pagination constants — consumed by the per-concept
// `*QueryParams` interfaces (period.ts / reconciliation.ts / account.ts)
// and by `ledger-client.ts`'s page-size clamping. Kept here (the leaf
// module) rather than in any single concept module to avoid a
// concept-to-concept import.
// ---------------------------------------------------------------------------

export const LEDGER_DEFAULT_PAGE_SIZE = 20;
export const LEDGER_MAX_PAGE_SIZE = 100;
