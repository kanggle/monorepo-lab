import { z } from 'zod';

/**
 * Feature-local view-model for the IAM **composed operator overview**
 * (TASK-PC-FE-005 — ADR-MONO-013 Phase 2 slice 4 / ADR-MONO-015 D1-B).
 *
 * This is a CONSUMER-SIDE composition view-model — there is NO new GAP
 * producer endpoint. The overview is a bounded fan-out over the EXISTING
 * reads already integrated by FE-002/003/004 (`searchAccounts` /
 * `queryAudit` / `listOperators`); the producer contracts stay
 * authoritative + unchanged in `admin-api.md`. Consumer obligation:
 * `console-integration-contract.md` § 2.4.4 (ADR-MONO-015-refined: a
 * composed overview, NOT a Grafana embed).
 *
 * Per-source isolation is the key design point (§ 2.4.4 / ADR-015 D3): a
 * degraded card NEVER throws — it carries its own status so one source
 * being down (403/503/timeout) degrades only that card. A `401` on ANY
 * leg is NOT modelled here as a card status — it is a whole-overview
 * forced re-login (no partial authed state); the api layer raises it as
 * an `ApiError(401)` so the page/proxy redirects.
 */

// --- per-card status ------------------------------------------------------

/**
 * The outcome of a single fan-out leg:
 *   - `ok`        — the leg returned data; the card renders it.
 *   - `degraded`  — `503`/`CIRCUIT_OPEN`/timeout/network: the card shows a
 *                   degraded placeholder + retry; the overview/shell stay
 *                   intact (never blank).
 *   - `forbidden` — `403 PERMISSION_DENIED`/`TENANT_SCOPE_DENIED`: the card
 *                   shows a "not available to your role" placeholder
 *                   (operators: non-`operator.manage`/non-SUPER_ADMIN;
 *                   audit: the § 2.4.2 intersection-permission for the
 *                   security subset). Not a crash, not a re-login.
 *
 * `401` is deliberately ABSENT — it is never a per-card status (§ 2.4.4).
 */
export const CARD_STATUSES = ['ok', 'degraded', 'forbidden'] as const;
export type CardStatus = (typeof CARD_STATUSES)[number];

const CardStatusSchema = z.enum(CARD_STATUSES);

// --- card 1: accounts summary --------------------------------------------

/** Snapshot derived from `GET /api/admin/accounts` (page total + a small
 *  recent slice). `account.read` absent ⇒ producer returns an empty list
 *  (not 403) ⇒ this renders as a zero/empty state, not `forbidden`. */
export const AccountsSummarySchema = z.object({
  status: CardStatusSchema,
  /** Total accounts (producer `totalElements`); null when not `ok`. */
  totalElements: z.number().int().nonnegative().nullable(),
  /** A tiny recent-snapshot count actually fetched (the page size). */
  sampleCount: z.number().int().nonnegative().nullable(),
});
export type AccountsSummary = z.infer<typeof AccountsSummarySchema>;

// --- card 2: audit + security activity -----------------------------------

/** Snapshot derived from `GET /api/admin/audit` (recent rows count +
 *  the most-recent occurredAt). PII-free by construction — only counts +
 *  a timestamp are surfaced (the producer already masks; the overview
 *  never buffers audit-row PII — § 2.4.2 / § 2.4.4). */
export const AuditActivitySummarySchema = z.object({
  status: CardStatusSchema,
  /** Total recent audit rows the producer reports; null when not `ok`. */
  totalElements: z.number().int().nonnegative().nullable(),
  /** Rows actually returned in the bounded recent slice. */
  recentCount: z.number().int().nonnegative().nullable(),
  /** The most-recent row's `occurredAt` (UTC ISO string), if any. */
  latestOccurredAt: z.string().nullable(),
});
export type AuditActivitySummary = z.infer<typeof AuditActivitySummarySchema>;

// --- card 3: operators summary -------------------------------------------

/** Snapshot derived from `GET /api/admin/operators` (count + status mix).
 *  Non-`operator.manage`/non-SUPER_ADMIN ⇒ producer `403` ⇒ this card is
 *  `forbidden` ("not available to your role"); the overview is NOT
 *  SUPER_ADMIN-only — the other cards still render. */
export const OperatorsSummarySchema = z.object({
  status: CardStatusSchema,
  /** Total operators (producer `totalElements`); null when not `ok`. */
  totalElements: z.number().int().nonnegative().nullable(),
  /** Status mix over the bounded slice (ACTIVE / SUSPENDED / other). */
  activeCount: z.number().int().nonnegative().nullable(),
  suspendedCount: z.number().int().nonnegative().nullable(),
});
export type OperatorsSummary = z.infer<typeof OperatorsSummarySchema>;

// --- composed overview view-model ----------------------------------------

/**
 * The whole composed overview. Every card is always present (a degraded /
 * forbidden card is a status, never a missing key, never a throw) so the
 * UI renders the full shell even when sources are down — per-source
 * isolation (§ 2.4.4 / ADR-015 D3).
 */
export const IamComposedOverviewSchema = z.object({
  accounts: AccountsSummarySchema,
  audit: AuditActivitySummarySchema,
  operators: OperatorsSummarySchema,
});
export type IamComposedOverview = z.infer<typeof IamComposedOverviewSchema>;

/** Quick-link targets — the overview links into the EXISTING in-console
 *  routes (FE-002/003/004). `iam.baseRoute` (`/accounts`) is unchanged;
 *  these are nav destinations, not catalog routes. */
export const OVERVIEW_QUICK_LINKS = {
  accounts: '/accounts',
  audit: '/audit',
  operators: '/operators',
} as const;
