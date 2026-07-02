import { z } from 'zod';

/**
 * Feature-local types for the ecommerce `notification-service` operator surface —
 * the notifications-template management slice absorbed into the console
 * (TASK-PC-FE-089, ADR-MONO-031 Phase 5b). Drives the in-console equivalent
 * of the standalone `admin-dashboard` notification-template screens:
 * list / detail / create / update (no delete).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `notification-service` `TemplateController`
 *   (`/api/notifications/templates`, the **non-admin** path — same model
 *   as promotions/shippings). Base URL: `ECOMMERCE_PUBLIC_BASE_URL`.
 * Consumer obligation: `console-integration-contract.md` § 2.4.10.4.
 *
 * ── IMMUTABILITY RULE ────────────────────────────────────────────────────────
 * `type` and `channel` are immutable after creation. The producer PUT body
 * accepts only `{ subject, body }`. The UI keeps them read-only on the edit
 * form and NEVER sends type/channel in the update request body.
 *
 * ── ENUM VALUES ──────────────────────────────────────────────────────────────
 * TemplateType: ORDER_PLACED / PAYMENT_COMPLETED / SHIPPING_STATUS_CHANGED / WELCOME
 * NotificationChannel: EMAIL / SMS / PUSH
 *
 * ── TOLERANCE INVARIANT ──────────────────────────────────────────────────────
 * Read shapes use `.passthrough()` so unknown future fields pass through
 * without throwing. An unknown enum value parses as a plain string.
 */

// ===========================================================================
// ENUM VALUES
// ===========================================================================

export const TEMPLATE_TYPE_VALUES = [
  'ORDER_PLACED',
  'PAYMENT_COMPLETED',
  'SHIPPING_STATUS_CHANGED',
  'WELCOME',
] as const;
export type TemplateType = (typeof TEMPLATE_TYPE_VALUES)[number];

export const TEMPLATE_TYPE_LABELS: Record<TemplateType, string> = {
  ORDER_PLACED: '주문 완료',
  PAYMENT_COMPLETED: '결제 완료',
  SHIPPING_STATUS_CHANGED: '배송 상태 변경',
  WELCOME: '회원 가입',
};

export const NOTIFICATION_CHANNEL_VALUES = ['EMAIL', 'SMS', 'PUSH'] as const;
export type NotificationChannel = (typeof NOTIFICATION_CHANNEL_VALUES)[number];

// ===========================================================================
// READ shapes
// ===========================================================================

/** 1. list — NotificationTemplateSummary row. */
export const NotificationTemplateSummarySchema = z
  .object({
    templateId: z.string(),
    type: z.string(),
    channel: z.string(),
    subject: z.string(),
    createdAt: z.string(),
    updatedAt: z.string().optional().nullable(),
  })
  .passthrough();
export type NotificationTemplateSummary = z.infer<
  typeof NotificationTemplateSummarySchema
>;

/** 1. list — paginated list envelope ({content, page, size, totalElements}). */
export const NotificationTemplateListSchema = z
  .object({
    content: z.array(NotificationTemplateSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
  })
  .passthrough();
export type NotificationTemplateList = z.infer<
  typeof NotificationTemplateListSchema
>;

/** 2. detail — full template including body, createdAt, updatedAt. */
export const NotificationTemplateDetailSchema = z
  .object({
    templateId: z.string(),
    type: z.string(),
    channel: z.string(),
    subject: z.string(),
    body: z.string(),
    createdAt: z.string().optional().nullable(),
    updatedAt: z.string().optional().nullable(),
  })
  .passthrough();
export type NotificationTemplateDetail = z.infer<
  typeof NotificationTemplateDetailSchema
>;

// ===========================================================================
// MUTATION response
// ===========================================================================

/** POST /api/notifications/templates → 201 { templateId }. */
export const NotificationMutationResponseSchema = z
  .object({ templateId: z.string() })
  .passthrough();
export type NotificationMutationResponse = z.infer<
  typeof NotificationMutationResponseSchema
>;

// ===========================================================================
// WRITE request bodies
// ===========================================================================

/** POST /api/notifications/templates (create) body.
 *  `type` + `channel` are required on create; immutable after creation. */
export const CreateTemplateBodySchema = z.object({
  type: z.enum(TEMPLATE_TYPE_VALUES),
  channel: z.enum(NOTIFICATION_CHANNEL_VALUES),
  subject: z.string().min(1),
  body: z.string().min(1),
});
export type CreateTemplateBody = z.infer<typeof CreateTemplateBodySchema>;

/** PUT /api/notifications/templates/{templateId} (update) body.
 *  ONLY subject + body — type/channel are immutable (producer ignores them). */
export const UpdateTemplateBodySchema = z.object({
  subject: z.string().min(1),
  body: z.string().min(1),
});
export type UpdateTemplateBody = z.infer<typeof UpdateTemplateBodySchema>;

// ===========================================================================
// SUMMARY (TASK-PC-FE-164 — period-based counts)
// ===========================================================================

/** GET /api/notifications/templates/summary — period-based count.
 *  Response: { today, week, month, total } all non-negative integers. */
export const NotificationAreaSummarySchema = z
  .object({
    today: z.number().int().nonnegative(),
    week: z.number().int().nonnegative(),
    month: z.number().int().nonnegative(),
    total: z.number().int().nonnegative(),
  })
  .passthrough();
export type NotificationAreaSummary = z.infer<typeof NotificationAreaSummarySchema>;

// ===========================================================================
// list query params + pagination defaults
// ===========================================================================

export const NOTIFICATION_DEFAULT_PAGE_SIZE = 20;
export const NOTIFICATION_MAX_PAGE_SIZE = 100;

export interface NotificationTemplateListParams {
  page?: number;
  size?: number;
}
