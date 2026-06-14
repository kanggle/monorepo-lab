import { z } from 'zod';

/**
 * Feature-local types for the finance `ledger-service`'s read-only
 * double-entry general-ledger surface (TASK-PC-FE-072 — § 2.4.7.1; the
 * SECOND finance-product service bound by the console alongside the
 * § 2.4.7 `account-service`, exactly as § 2.4.5.1 binds a second wms
 * service alongside § 2.4.5). STRICTLY READ-ONLY.
 *
 * Authoritative producer contracts (do NOT redefine — consume read-only):
 *   `finance-platform/specs/contracts/http/ledger-api.md`
 *     § 1 `GET /api/finance/ledger/entries/{entryId}` (entry by id)
 *     § 4 `GET /api/finance/ledger/trial-balance`
 *     § 7 `GET /api/finance/ledger/periods` (paginated list)
 *     § 8 `GET /api/finance/ledger/periods/{periodId}` (detail + snapshot)
 *   `finance-platform/specs/contracts/http/reconciliation-api.md`
 *     § 4 `GET /api/finance/ledger/reconciliation/discrepancies` (queue)
 *     § 5 `GET /api/finance/ledger/reconciliation/discrepancies/{id}`
 * Consumer obligation: `console-integration-contract.md` § 2.4.7.1 (reuses
 * the § 2.4.5 per-domain credential rule VIA the § 2.4.7 finance binding —
 * NOT re-derived). finance-side spec-first basis:
 * `finance-platform/specs/integration/iam-integration.md` § *platform-
 * console Operator Read Consumer* (TASK-FIN-BE-005 — the same finance
 * tenant gate the ledger shares with the account-service).
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per architecture.md
 * § Allowed Dependencies.
 *
 * F5 MONEY INVARIANT — MULTI-CURRENCY LEDGER FORM (CONTRACT obligation,
 * NOT a UX nicety — § 2.4.7.1): every money is
 * `{ amount: "<string-integer-minor-units>", currency }` — `amount` is a
 * **string-encoded integer in minor units** (KRW scale 0, USD scale 2). A
 * journal line carries THREE money/rate fields — the transaction `money`,
 * the `exchangeRate` (an exact-decimal **string** factor in minor units,
 * never a float — e.g. `"13.5"`), and the `baseAmount` (the line's value in
 * the fixed base currency **KRW**, which is balance-authoritative). The
 * console MUST render all of them faithfully from the **string** and MUST
 * NOT coerce any `amount` or `exchangeRate` to a JS `Number` / float
 * anywhere (parse / store / arithmetic / display) — the precision-
 * preservation contract. The `Money` schema therefore enforces
 * `amount: z.string().regex(/^-?\d+$/)`, NEVER `z.number()`; `exchangeRate`
 * is a free decimal **string** (`/^-?\d+(\.\d+)?$/`), NEVER a number.
 * `formatMoney(...)` is the only sanctioned way to render a Money value; it
 * uses string manipulation (no float math, no `Number(...)`). A test
 * grep-asserts that `Number()` / `parseFloat()` / `parseInt()` never appear
 * on a line that references `amount` or `exchangeRate` anywhere under
 * `features/ledger-ops/`.
 *
 * TOLERANCE invariant (§ 2.4.7.1 / task Edge Case "Unknown/future enum"):
 * every read shape is permissive — unknown / future `source.sourceType`,
 * period `status`, discrepancy `type`/`status` values parse to a generic
 * string value and NEVER throw. Only the fields the UI strictly needs are
 * required; everything else is passthrough.
 */

// ---------------------------------------------------------------------------
// F5 money — string-encoded integer minor units + ISO-4217 currency.
// ---------------------------------------------------------------------------

/**
 * Money — F5 contract shape: `{ amount, currency }` where `amount` is a
 * precision-exact **string** of integer minor units (e.g. KRW
 * `"1234567890123"`), NEVER a `number`. `currency` is ISO-4217 (3 chars).
 * Producer-side scale: KRW=0 (no decimals), USD=2 (cents).
 *
 * Why a string regex (and NEVER `z.number()`): a JS `Number` is an IEEE
 * 754 float — precision loss on large minor-units values (e.g. KRW
 * `2^54+1`). The regex is the parser-level guarantee that we never
 * accidentally hand the UI a Number-shaped amount.
 */
export const MoneySchema = z.object({
  amount: z.string().regex(/^-?\d+$/, 'amount must be an integer string (F5)'),
  currency: z.string().min(3).max(3),
});
export type Money = z.infer<typeof MoneySchema>;

/** Per-currency minor-unit scale (digits after the decimal point in the
 *  presentation form). Producer source = `ledger-api.md` § Common shapes. */
export const DEFAULT_CURRENCY_SCALES: Readonly<Record<string, number>> = {
  KRW: 0,
  USD: 2,
  EUR: 2,
  JPY: 0,
  GBP: 2,
};

/**
 * Renders a Money value scale-correct, **from the string minor-units**
 * — no float / `Number(...)` / `parseFloat(...)` / `parseInt(...)` is
 * applied to `amount` (F5 invariant; a test grep-asserts this).
 *
 * String manipulation only:
 *   - locate the sign (if any) and operate on the digit body;
 *   - left-pad to >= scale+1 digits;
 *   - splice in a decimal point (scale > 0) or use the digits as-is
 *     (scale = 0);
 *   - re-attach the sign + the currency.
 *
 * An unknown currency falls back to a sensible default scale (0,
 * tolerant-parser discipline) — no throw.
 */
export function formatMoney(
  money: Money,
  scales: Readonly<Record<string, number>> = DEFAULT_CURRENCY_SCALES,
): string {
  const scale = scales[money.currency] ?? 0;
  const isNegative = money.amount.startsWith('-');
  const digits = isNegative ? money.amount.slice(1) : money.amount;
  // We deliberately work with the string; integer length comparisons are
  // string-length, not numeric — no Number coercion of `amount`.
  let body: string;
  if (scale <= 0) {
    body = digits;
  } else {
    // Left-pad so we have at least `scale + 1` digits, then splice the
    // decimal in.
    const padded =
      digits.length > scale ? digits : '0'.repeat(scale - digits.length + 1) + digits;
    const intPart = padded.slice(0, padded.length - scale);
    const fracPart = padded.slice(padded.length - scale);
    body = `${intPart}.${fracPart}`;
  }
  return `${isNegative ? '-' : ''}${body} ${money.currency}`;
}

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
// Trial balance — GET /api/finance/ledger/trial-balance (ledger-api.md § 4)
//   { data: { accounts: [...], grand*Total, inBalance }, meta }
// ---------------------------------------------------------------------------

/** One per-account row of the trial balance. Each carries its original
 *  transaction-currency totals AND its base-currency (KRW) totals
 *  (8th increment multi-currency consolidation). All four are F5 Money. */
export const TrialBalanceAccountSchema = z
  .object({
    ledgerAccountCode: z.string(),
    debitTotal: MoneySchema,
    creditTotal: MoneySchema,
    baseDebitTotal: MoneySchema,
    baseCreditTotal: MoneySchema,
  })
  .passthrough();
export type TrialBalanceAccount = z.infer<typeof TrialBalanceAccountSchema>;

export const TrialBalanceSchema = z
  .object({
    accounts: z.array(TrialBalanceAccountSchema),
    grandDebitTotal: MoneySchema,
    grandCreditTotal: MoneySchema,
    grandBaseDebitTotal: MoneySchema,
    grandBaseCreditTotal: MoneySchema,
    // The live double-entry invariant — surfaced honestly.
    inBalance: z.boolean(),
  })
  .passthrough();
export type TrialBalance = z.infer<typeof TrialBalanceSchema>;

// ---------------------------------------------------------------------------
// Journal entry — GET /api/finance/ledger/entries/{entryId} (ledger-api.md § 1)
//   lines carry money + exchangeRate + baseAmount; source.sourceType; balanced
// ---------------------------------------------------------------------------

/** Producer journal-entry source types surfaced HONESTLY (TRANSACTION /
 *  MANUAL / REVALUATION / SETTLEMENT shown as-is — § 2.4.7.1). Stored as a
 *  free string so unknown / future values render generically (no parser
 *  throw, tolerant-parser discipline). */
export const KNOWN_SOURCE_TYPES = [
  'TRANSACTION',
  'MANUAL',
  'REVALUATION',
  'SETTLEMENT',
] as const;
export type KnownSourceType = (typeof KNOWN_SOURCE_TYPES)[number];

export const KNOWN_DIRECTIONS = ['DEBIT', 'CREDIT'] as const;
export type KnownDirection = (typeof KNOWN_DIRECTIONS)[number];

export const JournalSourceSchema = z
  .object({
    // tolerated as free string (unknown → generic label).
    sourceType: z.string(),
    sourceTransactionId: z.string().nullable().optional(),
    sourceEventId: z.string().nullable().optional(),
  })
  .passthrough();
export type JournalSource = z.infer<typeof JournalSourceSchema>;

/** A journal line — the F5 triple: `money` (transaction currency) +
 *  `exchangeRate` (decimal string, minor-to-minor) + `baseAmount` (the
 *  balance-authoritative KRW value). A base-currency KRW line has
 *  `exchangeRate "1"` and `baseAmount == money`; a 9th-increment
 *  revaluation line has `money.amount "0"` (foreign) with a non-zero KRW
 *  `baseAmount`. `direction` is tolerated as a free string. */
export const JournalLineSchema = z
  .object({
    ledgerAccountCode: z.string(),
    // tolerated as free string (unknown → generic label).
    direction: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
    // F5 — REQUIRED decimal string, surfaced verbatim (never floated).
    exchangeRate: z.string(),
    baseAmount: MoneySchema, // F5 — REQUIRED, balance-authoritative KRW
  })
  .passthrough();
export type JournalLine = z.infer<typeof JournalLineSchema>;

export const JournalEntrySchema = z
  .object({
    entryId: z.string(),
    postedAt: z.string().optional(),
    source: JournalSourceSchema,
    reversalOfEntryId: z.string().nullable().optional(),
    lines: z.array(JournalLineSchema),
    balanced: z.boolean(),
  })
  .passthrough();
export type JournalEntry = z.infer<typeof JournalEntrySchema>;

// ---------------------------------------------------------------------------
// Accounting periods — GET /api/finance/ledger/periods[/{periodId}]
//   ledger-api.md § 7 (list), § 8 (detail + snapshot when CLOSED)
// ---------------------------------------------------------------------------

/** Producer period statuses surfaced HONESTLY (OPEN / CLOSED shown as-is
 *  — § 2.4.7.1). Free string for tolerance. */
export const KNOWN_PERIOD_STATUSES = ['OPEN', 'CLOSED'] as const;
export type KnownPeriodStatus = (typeof KNOWN_PERIOD_STATUSES)[number];

/** One close-snapshot account row (per-account debit/credit, no base
 *  totals — the close snapshot is the simpler shape per ledger-api.md
 *  § 6/§ 8). F5 Money. */
export const PeriodSnapshotAccountSchema = z
  .object({
    ledgerAccountCode: z.string(),
    debitTotal: MoneySchema,
    creditTotal: MoneySchema,
  })
  .passthrough();
export type PeriodSnapshotAccount = z.infer<
  typeof PeriodSnapshotAccountSchema
>;

/** The immutable close snapshot — present only for a CLOSED period
 *  (`null` while OPEN; `snapshot: null` is NOT an error — § 2.4.7.1). */
export const PeriodSnapshotSchema = z
  .object({
    accounts: z.array(PeriodSnapshotAccountSchema),
    grandDebitTotal: MoneySchema,
    grandCreditTotal: MoneySchema,
    inBalance: z.boolean(),
  })
  .passthrough();
export type PeriodSnapshot = z.infer<typeof PeriodSnapshotSchema>;

export const PeriodSchema = z
  .object({
    periodId: z.string(),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    from: z.string().optional(),
    to: z.string().optional(),
    closedAt: z.string().nullable().optional(),
    closedBy: z.string().nullable().optional(),
    entryCount: z.number().int().nullable().optional(),
    // present (+ non-null) only on the detail read of a CLOSED period.
    snapshot: PeriodSnapshotSchema.nullable().optional(),
  })
  .passthrough();
export type Period = z.infer<typeof PeriodSchema>;

export const PeriodsResponseSchema = z.object({
  data: z.array(PeriodSchema),
  meta: LedgerMetaSchema,
});
export type PeriodsResponse = z.infer<typeof PeriodsResponseSchema>;

// ---------------------------------------------------------------------------
// Reconciliation discrepancies — reconciliation-api.md § 4 (queue), § 5 (detail)
//   GET /api/finance/ledger/reconciliation/discrepancies[/{id}]
// ---------------------------------------------------------------------------

/** Producer discrepancy types surfaced HONESTLY (UNMATCHED_EXTERNAL /
 *  UNMATCHED_INTERNAL / AMOUNT_MISMATCH shown as-is — § 2.4.7.1; the
 *  11th-increment FX-difference `AMOUNT_MISMATCH` carries both
 *  `externalRef` and `journalEntryId`). Free string for tolerance —
 *  unknown / future values render with a generic label. */
export const KNOWN_DISCREPANCY_TYPES = [
  'UNMATCHED_EXTERNAL',
  'UNMATCHED_INTERNAL',
  'AMOUNT_MISMATCH',
] as const;
export type KnownDiscrepancyType = (typeof KNOWN_DISCREPANCY_TYPES)[number];

export const KNOWN_DISCREPANCY_STATUSES = ['OPEN', 'RESOLVED'] as const;
export type KnownDiscrepancyStatus =
  (typeof KNOWN_DISCREPANCY_STATUSES)[number];

/** The resolution sub-object — present only when `status === 'RESOLVED'`
 *  (reconciliation-api.md § 5). The `resolutionType` is surfaced honestly
 *  (free string for tolerance). */
export const DiscrepancyResolutionSchema = z
  .object({
    resolutionType: z.string(),
    note: z.string().nullable().optional(),
    resolvedBy: z.string().nullable().optional(),
    resolvedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type DiscrepancyResolution = z.infer<
  typeof DiscrepancyResolutionSchema
>;

export const DiscrepancySchema = z
  .object({
    discrepancyId: z.string(),
    // tolerated as free string (unknown → generic label).
    type: z.string(),
    // present on BOTH for the 11th-increment FX-difference AMOUNT_MISMATCH
    // (the matched pair); nullable for the unmatched types.
    externalRef: z.string().nullable().optional(),
    journalEntryId: z.string().nullable().optional(),
    // F5 — the expected (internal carrying base) and actual (bank base)
    // amounts are minor-units STRINGS, never floats. They are flat string
    // fields (NOT wrapped Money) per reconciliation-api.md § 1/§ 4.
    expectedMinor: z
      .string()
      .regex(/^-?\d+$/, 'expectedMinor must be an integer string (F5)'),
    actualMinor: z
      .string()
      .regex(/^-?\d+$/, 'actualMinor must be an integer string (F5)'),
    currency: z.string().min(3).max(3),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    resolution: DiscrepancyResolutionSchema.nullable().optional(),
  })
  .passthrough();
export type Discrepancy = z.infer<typeof DiscrepancySchema>;

export const DiscrepanciesResponseSchema = z.object({
  data: z.array(DiscrepancySchema),
  meta: LedgerMetaSchema,
});
export type DiscrepanciesResponse = z.infer<
  typeof DiscrepanciesResponseSchema
>;

// ---------------------------------------------------------------------------
// Reconciliation discrepancy RESOLVE mutation — reconciliation-api.md § 2
//   POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve
//   (TASK-PC-FE-073 — the ledger surface's FIRST and ONLY operator mutation)
// ---------------------------------------------------------------------------

/**
 * The three producer-sanctioned resolution types (`reconciliation-api.md`
 * § 2 — `resolutionType ∈ { MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`).
 * Unlike the READ enum vocabularies (which are tolerant free strings so an
 * unknown/future producer value never throws), the RESOLVE request is a
 * console-ORIGINATED value: the operator picks exactly one of these three —
 * a closed `z.enum` is correct here (we must never send a value the producer
 * does not define).
 */
export const RESOLUTION_TYPES = [
  'MATCHED_MANUALLY',
  'WRITTEN_OFF',
  'ACCEPTED',
] as const;
export type ResolutionType = (typeof RESOLUTION_TYPES)[number];

/**
 * The resolve request body — `{ resolutionType, note }`.
 *
 * `note` is a **required**, non-empty operator narrative (the audit record);
 * the producer's `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the
 * double-submit defence, so there is deliberately **NO `idempotencyKey`
 * field** — `reconciliation-api.md` § 2 defines none for resolve (unlike the
 * ledger `POST /entries`, which requires one). Fabricating a header the
 * producer ignores is forbidden (the same honesty discipline as the no-429
 * rule).
 */
export const ResolveDiscrepancyBodySchema = z.object({
  resolutionType: z.enum(RESOLUTION_TYPES),
  // A non-empty operator narrative — the audit record. A whitespace-only
  // value is rejected (the reason is required); the proxy maps a parse
  // failure to `400 VALIDATION_ERROR` with NO upstream call.
  note: z
    .string()
    .min(1)
    .max(512)
    .refine((s) => s.trim() !== '', { message: 'note must not be blank' }),
});
export type ResolveDiscrepancyBody = z.infer<
  typeof ResolveDiscrepancyBodySchema
>;

/**
 * Materialises a discrepancy's `expectedMinor` / `actualMinor` strings as
 * Money objects for the same `currency` (the reconciliation amounts are
 * KRW base values). Pure string transformation (no `Number(...)`).
 */
export function discrepancyMoney(d: Discrepancy): {
  expected: Money;
  actual: Money;
} {
  return {
    expected: { amount: d.expectedMinor, currency: d.currency },
    actual: { amount: d.actualMinor, currency: d.currency },
  };
}

// ---------------------------------------------------------------------------
// Account-level drill reads — TASK-PC-FE-074
//   § 2 GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries
//   § 3 GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance
//   STRICTLY READ-ONLY — these two reads add NO mutation. The account code
//   in the colon form (e.g. `CUSTOMER_WALLET:acc-1`) MUST be
//   `encodeURIComponent`-encoded on the producer path.
//
// F5 MONEY: all four money fields (`debitTotal`, `creditTotal`, `balance`,
// `money` in entries) are the SAME F5 Money shape — string minor-units,
// NEVER a number. `formatMoney(...)` is the only sanctioned render path.
//
// TOLERANCE: `type` / `normalSide` / `balanceSide` / `direction` are
// surfaced as free strings (tolerant-parser discipline — unknown / future
// values render with a generic label, NEVER throw).
// ---------------------------------------------------------------------------

/**
 * AccountBalance — § 3 `GET /api/finance/ledger/accounts/{code}/balance`.
 *
 * `balance = |debitTotal − creditTotal|` (the net, computed by the producer).
 * `type` / `normalSide` / `balanceSide` are free strings (LIABILITY / ASSET /
 * …, DEBIT / CREDIT / NONE — tolerant, new values render generically, no
 * throw). All four money fields are F5 — string minor-units.
 */
export const AccountBalanceSchema = z
  .object({
    ledgerAccountCode: z.string(),
    // tolerant free strings — unknown / future account type / side values
    // parse without throwing (tolerant-parser discipline).
    type: z.string(),
    normalSide: z.string(),
    debitTotal: MoneySchema, // F5 — REQUIRED, precision-preserving
    creditTotal: MoneySchema, // F5 — REQUIRED, precision-preserving
    balance: MoneySchema, // F5 — REQUIRED, |debitTotal − creditTotal|
    balanceSide: z.string(),
  })
  .passthrough();
export type AccountBalance = z.infer<typeof AccountBalanceSchema>;

/**
 * AccountEntryLine — one journal line posted to an account (§ 2 entries
 * array element). `entryId` is the journal-entry id for the drill-back into
 * the Journal Entry tab. `direction` is a free string (DEBIT / CREDIT —
 * tolerant). `money` is F5. `counterpartyLines` is an optional convenience
 * field (the other legs of the journal entry the account is part of) — kept
 * permissive (`z.any()`) because the producer treats it as an optional
 * advisory field and its shape is not the primary contract surface here.
 */
export const AccountEntryLineSchema = z
  .object({
    entryId: z.string(),
    postedAt: z.string().optional(),
    // tolerant free string — unknown direction renders generically.
    direction: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
    counterpartyLines: z.array(z.any()).nullable().optional(),
  })
  .passthrough();
export type AccountEntryLine = z.infer<typeof AccountEntryLineSchema>;

export const AccountEntriesResponseSchema = z.object({
  data: z.array(AccountEntryLineSchema),
  meta: LedgerMetaSchema,
});
export type AccountEntriesResponse = z.infer<
  typeof AccountEntriesResponseSchema
>;

// ---------------------------------------------------------------------------
// query params
// ---------------------------------------------------------------------------

export const LEDGER_DEFAULT_PAGE_SIZE = 20;
export const LEDGER_MAX_PAGE_SIZE = 100;

export interface PeriodsQueryParams {
  page?: number;
  size?: number;
}

export interface DiscrepanciesQueryParams {
  /** Producer discrepancy status filter (`OPEN` | `RESOLVED`; absent = all). */
  status?: string;
  page?: number;
  size?: number;
}

/** AccountEntriesQueryParams — pagination for the account entries read
 *  (§ 2 GET `.../accounts/{code}/entries?page=&size=`). */
export interface AccountEntriesQueryParams {
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Reconciliation statement-detail — TASK-PC-FE-075
//   § 3 GET /api/finance/ledger/reconciliation/statements/{id}
//   (reconciliation-api.md § 1 + § 3). STRICTLY READ-ONLY.
//   No list/search GET over statements (id-driven — the honest ledger
//   constraint, same as entries + accounts; statement ids come from the
//   ingest the operator's integration ran — ingest is out of console scope).
//
// F5 MONEY: the `money` field on each `StatementMatch` is the SAME F5 Money
// shape — string minor-units, NEVER a number. `formatMoney(...)` is the
// only sanctioned render path.
//
// TOLERANCE: `source` is a free string (no closed enum in the producer
// contract — new sources render generically, never throw).
//
// The `discrepancies` array reuses the EXISTING `DiscrepancySchema`
// verbatim (the statement's discrepancies are the same shape as the § 4/§ 5
// discrepancy reads — do NOT redefine).
// ---------------------------------------------------------------------------

/**
 * StatementMatch — one matched line in a reconciliation statement
 * (`reconciliation-api.md` § 1 `matches` array element).
 *
 * `statementLineExternalRef` — the external ref the source system supplied
 * for this line (links back to the bank/external statement).
 * `journalEntryId` — the ledger journal entry matched to this line; used by
 * the console to drill into the Journal Entry tab.
 * `money` — the matched amount (F5 — string minor-units, NEVER a float).
 */
export const StatementMatchSchema = z
  .object({
    statementLineExternalRef: z.string(),
    journalEntryId: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
  })
  .passthrough();
export type StatementMatch = z.infer<typeof StatementMatchSchema>;

/**
 * Statement — `reconciliation-api.md` § 3 `GET .../statements/{id}` 200
 * body `data` sub-object.
 *
 * `source` is a free string (tolerant — no closed enum; new values render
 * generically, no throw). `statementDate` is optional (nullable from the
 * producer). `matchedCount` + `discrepancyCount` are non-negative integers
 * (the summary; the actual rows are in `matches` / `discrepancies`).
 * `discrepancies` reuses the existing `DiscrepancySchema` (the statement's
 * discrepancies are the SAME shape as the § 4/§ 5 discrepancy reads).
 */
export const StatementSchema = z
  .object({
    statementId: z.string(),
    ledgerAccountCode: z.string(),
    // tolerant free string — no closed enum in the producer contract.
    source: z.string(),
    statementDate: z.string().optional(),
    matchedCount: z.number().int().nonnegative(),
    discrepancyCount: z.number().int().nonnegative(),
    matches: z.array(StatementMatchSchema),
    discrepancies: z.array(DiscrepancySchema), // reuse — do NOT redefine
  })
  .passthrough();
export type Statement = z.infer<typeof StatementSchema>;

// ---------------------------------------------------------------------------
// FX position open-lots drill — TASK-PC-FE-091
//   § 12 GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots
//   (ledger-api.md § 12 — the 20th increment, FIN-BE-028). STRICTLY READ-ONLY.
//   id-driven by (ledgerAccountCode, currency) — the colon-form account code
//   is `encodeURIComponent`-encoded on the producer path; `currency` is a
//   3-letter ISO-4217 code. An empty position → 200 with `lots: []`, all
//   totals `"0"`, `lotCount: 0` (NOT a 404 — an empty-state, never an error).
//
// F5 MONEY (CONTRACT obligation — § 2.4.7.1): EVERY `*Minor` field
// (`originalForeignMinor`, `remainingForeignMinor`, `originalBaseMinor`,
// `carryingBaseMinor`, `totalRemainingForeignMinor`, `totalCarryingBaseMinor`)
// is a precision-exact **string** of integer minor units — NEVER a number.
// The console MUST render these from the string ONLY (no float / `Number(...)`
// / `parseFloat(...)` / `parseInt(...)` — a test grep-asserts this); `seq` and
// `lotCount` ARE numbers (lot index / count, not money — the F5 invariant is
// amount-only). `acquiredAt` is an ISO-8601 string.
//
// TOLERANCE: only the fields the UI strictly needs are required; everything
// else is passthrough (forward-compatible with later producer fields).
// ---------------------------------------------------------------------------

/**
 * One open FX acquisition lot (`ledger-api.md` § 12 `lots` array element).
 *
 * `originalForeignMinor` + `originalBaseMinor` are the acquisition-time
 * values (never change); `remainingForeignMinor` + `carryingBaseMinor`
 * reflect FIFO consumption (17th incr) + revaluation mark-to-spot (18th
 * incr). `sourceJournalEntryId` is the journal entry that created the lot
 * (acquisition provenance — drill key into the entry view). Every `*Minor`
 * field is an F5 minor-units **string** (NEVER a number); `seq` is the
 * lot's per-`acquiredAt` ordering index (a number, NOT money).
 */
export const PositionLotSchema = z
  .object({
    lotId: z.string(),
    currency: z.string().min(3).max(3),
    acquiredAt: z.string(),
    // ordering index within an `acquiredAt` — a number, NOT money (F5 is
    // amount-only). Tolerant: a non-integer/absent value degrades to 0.
    seq: z.number().int().nonnegative().optional().default(0),
    // F5 — minor-units STRINGS, never coerced to a number.
    originalForeignMinor: z
      .string()
      .regex(/^-?\d+$/, 'originalForeignMinor must be an integer string (F5)'),
    remainingForeignMinor: z
      .string()
      .regex(/^-?\d+$/, 'remainingForeignMinor must be an integer string (F5)'),
    originalBaseMinor: z
      .string()
      .regex(/^-?\d+$/, 'originalBaseMinor must be an integer string (F5)'),
    carryingBaseMinor: z
      .string()
      .regex(/^-?\d+$/, 'carryingBaseMinor must be an integer string (F5)'),
    sourceJournalEntryId: z.string(),
  })
  .passthrough();
export type PositionLot = z.infer<typeof PositionLotSchema>;

/**
 * PositionLotsResponse — § 12 `GET .../settlements/{code}/{currency}/lots`
 * 200 body `data` sub-object: the open lots + the position summary
 * (Σremaining foreign, Σcarrying base, lot count). An empty position →
 * `lots: []`, totals `"0"`, `lotCount: 0` (an empty-state — never a 404 /
 * error). The two `total*Minor` fields are F5 minor-units **strings**;
 * `lotCount` is a number.
 */
export const PositionLotsResponseSchema = z
  .object({
    lots: z.array(PositionLotSchema),
    totalRemainingForeignMinor: z
      .string()
      .regex(/^-?\d+$/, 'totalRemainingForeignMinor must be an integer string (F5)'),
    totalCarryingBaseMinor: z
      .string()
      .regex(/^-?\d+$/, 'totalCarryingBaseMinor must be an integer string (F5)'),
    lotCount: z.number().int().nonnegative(),
  })
  .passthrough();
export type PositionLotsResponse = z.infer<typeof PositionLotsResponseSchema>;

/**
 * Materialises a lot's `remainingForeignMinor` (foreign currency) +
 * `carryingBaseMinor` (KRW base) strings as Money objects for rendering via
 * `formatMoney(...)`. Pure string transformation — NO `Number(...)`. The
 * base leg is always KRW (the fixed base currency, v1).
 */
export function positionLotMoney(lot: PositionLot): {
  originalForeign: Money;
  remainingForeign: Money;
  originalBase: Money;
  carryingBase: Money;
} {
  return {
    originalForeign: { amount: lot.originalForeignMinor, currency: lot.currency },
    remainingForeign: { amount: lot.remainingForeignMinor, currency: lot.currency },
    originalBase: { amount: lot.originalBaseMinor, currency: 'KRW' },
    carryingBase: { amount: lot.carryingBaseMinor, currency: 'KRW' },
  };
}
