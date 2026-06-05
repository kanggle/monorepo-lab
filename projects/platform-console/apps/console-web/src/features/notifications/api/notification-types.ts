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
    // Free-string tolerance: unknown future type renders generically, no throw.
    type: z.string(),
    title: z.string(),
    body: z.string(),
    // Free-string tolerance: v2 adds MASTERDATA / PERMISSION additively.
    sourceType: z.string(),
    sourceId: z.string(),
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

export const NotificationListResponseSchema = z.object({
  data: z.array(NotificationSchema),
  meta: NotificationMetaSchema,
});
export type NotificationListResponse = z.infer<
  typeof NotificationListResponseSchema
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
