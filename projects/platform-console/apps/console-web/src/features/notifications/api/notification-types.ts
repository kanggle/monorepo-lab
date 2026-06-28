import { z } from 'zod';

/**
 * Feature-local types for the console notification bell (TASK-PC-FE-052;
 * ADR-MONO-016 § D3 first increment). Since ADR-MONO-043 P3b (TASK-PC-FE-137)
 * the bell consumes the **console-bff notification aggregator**
 * (`/api/console/notifications/**`) — one cross-domain merged feed — rather than
 * the retired erp-direct path. The erp-direct server client + `/api/erp/notifications`
 * proxy routes were removed in TASK-PC-FE-138.
 *
 * Authoritative contracts (do NOT redefine — consume):
 *   `platform/contracts/notification-inbox-contract.md` (§1 envelope, §4 aggregator)
 *   `erp-platform/specs/contracts/http/notification-api.md` (erp producer — the
 *     `sourceType`/`sourceId` extension + APPROVAL deep-link vocabulary).
 *
 * FIRST INCREMENT — erp source type APPROVAL drives the deep-link fallback;
 * the bell handles unknown source types / domains with a generic label and no
 * deep-link (forward-tolerant).
 *
 * NON_NULL absent-field convention (producer § `@JsonInclude(NON_NULL)`):
 * `readAt` is ABSENT from the JSON while `read == false`, never serialized
 * as `null`. Parses to `undefined` here (zod `.optional()`); the UI
 * distinguishes unread via `read === false`, never by a null `readAt` check.
 *
 * TOLERANCE: `type` / `sourceType` are parsed as free strings (an unknown /
 * future enum value renders with a generic label and NEVER throws — the
 * producer is the authority for the enum vocabulary; forward compat).
 */

// ---------------------------------------------------------------------------
// envelope meta — flat erp shape (same wire as masterdata / approval surface).
// ---------------------------------------------------------------------------

export const NotificationMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type NotificationMeta = z.infer<typeof NotificationMetaSchema>;

// ---------------------------------------------------------------------------
// Notification — single item; readAt ABSENT until read (NON_NULL).
// type + sourceType parsed as free strings (tolerant enum).
// ---------------------------------------------------------------------------

export const NotificationSchema = z
  .object({
    id: z.string(),
    // ADR-MONO-043 §1 attribution — the owning domain ("erp" | "fan" | …).
    // OPTIONAL on the base shape (the mark-read detail response isn't consumed
    // for attribution, so it need not carry it); the aggregator feed re-asserts
    // it as REQUIRED in `AggregatedNotificationSchema` below (P3a injects/
    // preserves it on every merged item so the bell can label + route per
    // domain and mark-read can address the owner).
    sourceDomain: z.string().optional(),
    // Free-string tolerance: unknown future type renders generically, no throw.
    type: z.string(),
    title: z.string(),
    body: z.string(),
    // §1 optional in-app link (NON_NULL — absent when the domain supplies none).
    deepLink: z.string().optional(),
    // erp non-normative extensions (contract §1.2) — OPTIONAL now that the feed
    // is cross-domain (non-erp items omit them). Used only for the erp deep-link
    // fallback when `deepLink` is absent.
    sourceType: z.string().optional(),
    sourceId: z.string().optional(),
    read: z.boolean(),
    createdAt: z.string(),
    // ABSENT while read == false (NON_NULL convention — never null in the wire).
    readAt: z.string().optional(),
  })
  .passthrough();
export type Notification = z.infer<typeof NotificationSchema>;

/**
 * Aggregator feed item — `sourceDomain` is GUARANTEED present (the console-bff
 * aggregator injects/preserves it on every merged item, contract §4) so the
 * bell can key/label/route per domain and mark-read can address the owner. The
 * base `NotificationSchema` keeps it optional (the mark-read detail response
 * doesn't require attribution).
 */
export const AggregatedNotificationSchema = NotificationSchema.extend({
  sourceDomain: z.string(),
});
export type AggregatedNotification = z.infer<
  typeof AggregatedNotificationSchema
>;

// ---------------------------------------------------------------------------
// envelope response schemas.
// ---------------------------------------------------------------------------

/**
 * The console-bff notification **aggregator** response (ADR-MONO-043 P3a /
 * §4): one merged feed across the operator-facing domains, with per-domain
 * `degradedDomains` attribution. Always HTTP 200 (D5 failure isolation — a
 * downed domain appears in `degradedDomains`, never collapses the bell).
 */
export const NotificationInboxResponseSchema = z.object({
  asOf: z.string().optional(),
  items: z.array(AggregatedNotificationSchema),
  meta: NotificationMetaSchema,
  degradedDomains: z.array(z.string()).default([]),
});
export type NotificationInboxResponse = z.infer<
  typeof NotificationInboxResponseSchema
>;

export const NotificationDetailResponseSchema = z.object({
  data: NotificationSchema,
  meta: NotificationMetaSchema,
});
export type NotificationDetailResponse = z.infer<
  typeof NotificationDetailResponseSchema
>;

// ---------------------------------------------------------------------------
// query params.
// ---------------------------------------------------------------------------

export const NOTIFICATION_DEFAULT_PAGE_SIZE = 20;
export const NOTIFICATION_MAX_PAGE_SIZE = 100;

/** Inbox query — optional unread filter + pagination. */
export interface NotificationInboxQueryParams {
  /** `true` → only unread; `false` → only read; omitted → all. Only set in
   *  the upstream query string when explicitly provided (omitting is the
   *  producer default "all"). */
  unread?: boolean;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// helpers.
// ---------------------------------------------------------------------------

/** True when `n.sourceType === 'APPROVAL'` — enables the deep-link
 *  `/erp?approval=<sourceId>` in the bell dropdown. Unknown / future source
 *  types render a generic label only (no deep-link). */
export function isApprovalSource(n: Notification): boolean {
  return n.sourceType === 'APPROVAL';
}
