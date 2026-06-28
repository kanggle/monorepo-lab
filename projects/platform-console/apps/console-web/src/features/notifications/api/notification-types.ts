import { z } from 'zod';

/**
 * Feature-local types for the erp `notification-service` in-app inbox surface
 * (TASK-PC-FE-052 — ADR-MONO-016 § D3 notification first increment;
 * console bell + proxy consuming TASK-ERP-BE-011).
 *
 * Authoritative producer contract (do NOT redefine — consume):
 *   `erp-platform/specs/contracts/http/notification-api.md`
 *     base path `/api/erp/notifications`
 *     GET  /notifications           (inbox; optional unread/page/size filter)
 *     GET  /notifications/{id}      (single notification)
 *     POST /notifications/{id}/read (idempotent mark-read; no body, no Idempotency-Key)
 *
 * FIRST INCREMENT ONLY — source type APPROVAL only; v2 adds MASTERDATA /
 * PERMISSION additively. The bell UI handles unknown source types with a
 * generic label and no deep-link (forward-tolerant).
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
    // ADR-MONO-043 §1 attribution — the owning domain ("erp" | "fan" | …). The
    // console-bff aggregator (P3a) injects/preserves it on every merged item so
    // the bell can label + route per domain and mark-read can address the owner.
    sourceDomain: z.string(),
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
  items: z.array(NotificationSchema),
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

/**
 * Legacy erp-direct inbox envelope (`{ data, meta }`). Retained for the
 * erp-direct server client `notification-api.ts` (no longer consumed by the
 * bell after the P3b aggregator rewire — slated for removal in a follow-up
 * close-chore once the erp-direct proxy routes are dropped).
 */
export const NotificationListResponseSchema = z.object({
  data: z.array(NotificationSchema),
  meta: NotificationMetaSchema,
});
export type NotificationListResponse = z.infer<
  typeof NotificationListResponseSchema
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
