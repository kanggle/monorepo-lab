import { getServerEnv } from '@/shared/config/env';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  NotificationTemplateListSchema,
  type NotificationTemplateList,
  NotificationTemplateDetailSchema,
  type NotificationTemplateDetail,
  NotificationMutationResponseSchema,
  type NotificationMutationResponse,
  NotificationAreaSummarySchema,
  type NotificationAreaSummary,
  type NotificationTemplateListParams,
  type CreateTemplateBody,
  type UpdateTemplateBody,
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  NOTIFICATION_MAX_PAGE_SIZE,
} from './notification-types';

/**
 * Server-side ecommerce `notification-service` operations client (TASK-PC-FE-089 —
 * ADR-MONO-031 Phase 5b). Drives the in-console notification template operator
 * surface: list / detail / create / update (no delete).
 *
 * ── BASE URL RESOLUTION (notification-service path) ─────────────────────────
 *
 * notification-service exposes endpoints at `/api/notifications/templates` —
 * the **non-admin** path (same model as promotions/shippings, NOT the
 * `/api/admin/**` subtree). Therefore this client uses `ECOMMERCE_PUBLIC_BASE_URL`
 * (defaults to `http://ecommerce.local/api`) with path `/notifications/templates`,
 * yielding: `http://ecommerce.local/api/notifications/templates`
 *
 * ── AUTH MODEL (identical to promotions-api / shippings-api — § 2.4.10) ─────
 *
 * Uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC token —
 * net-zero; ADR-MONO-020 D4). NEVER `getOperatorToken()` (that is the
 * IAM-domain credential — wrong issuer/type for ecommerce). Tenant rides in
 * the JWT `tenant_id` claim — the console sends NO `X-Tenant-Id`.
 * NO `Idempotency-Key` (producer defines none — § 2.4.10).
 *
 * ── ERROR ENVELOPE (flat { code, message, timestamp } — same as promotions) ──
 *
 * Producer codes: 400 VALIDATION_ERROR, 403 ACCESS_DENIED, 404 TEMPLATE_NOT_FOUND,
 * 409 TEMPLATE_ALREADY_EXISTS (duplicate type+channel within tenant).
 *
 * ── TYPE/CHANNEL IMMUTABILITY ────────────────────────────────────────────────
 * After creation, `type` and `channel` are immutable. The update body accepts
 * ONLY `{ subject, body }` — never send type/channel on update.
 */

/** Per-slice observability + message label for the notification surface. */
const NOTIFICATION_LABEL: EcommerceCallLabel = {
  event: 'notification',
  errorNoun: 'notification',
  unavailableLabel: 'notification-service',
  timedOutLabel: 'notification-service',
  failedLabel: 'notification-service',
};

const clampSize = (size?: number): number =>
  clampPageSize(size, NOTIFICATION_DEFAULT_PAGE_SIZE, NOTIFICATION_MAX_PAGE_SIZE);

// ===========================================================================
// READS
// ===========================================================================

/** GET /api/notifications/templates/summary — period-based counts (TASK-PC-FE-160).
 *  Returns { today, week, month, total } for the tenant. */
export function getTemplatesSummary(): Promise<NotificationAreaSummary> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: '/notifications/templates/summary',
    },
    (j) => NotificationAreaSummarySchema.parse(j),
    NOTIFICATION_LABEL,
  );
}

/** GET /api/notifications/templates?page=&size= (paginated summary list). */
export function listTemplates(
  params: NotificationTemplateListParams = {},
): Promise<NotificationTemplateList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/notifications/templates?${qs.toString()}`,
    },
    (j) => NotificationTemplateListSchema.parse(j),
    NOTIFICATION_LABEL,
  );
}

/** GET /api/notifications/templates/{templateId} (full detail incl. body). */
export function getTemplate(
  id: string,
): Promise<NotificationTemplateDetail> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/notifications/templates/${encodeURIComponent(id)}`,
    },
    (j) => NotificationTemplateDetailSchema.parse(j),
    NOTIFICATION_LABEL,
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key)
// ===========================================================================

/** POST /api/notifications/templates (create). Returns { templateId }. */
export function createTemplate(
  body: CreateTemplateBody,
): Promise<NotificationMutationResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: '/notifications/templates',
      body,
    },
    (j) => NotificationMutationResponseSchema.parse(j),
    NOTIFICATION_LABEL,
  );
}

/** PUT /api/notifications/templates/{templateId} (update subject+body only).
 *  NOTE: type/channel are immutable — NEVER send them in the update body. */
export function updateTemplate(
  id: string,
  body: UpdateTemplateBody,
): Promise<NotificationMutationResponse> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: 'PUT',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/notifications/templates/${encodeURIComponent(id)}`,
      body,
    },
    (j) => NotificationMutationResponseSchema.parse(j),
    NOTIFICATION_LABEL,
  );
}
