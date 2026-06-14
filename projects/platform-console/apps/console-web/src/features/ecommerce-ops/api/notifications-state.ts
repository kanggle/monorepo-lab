import { redirect } from 'next/navigation';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { listTemplates, getTemplate } from './notifications-api';
import type {
  NotificationTemplateList,
  NotificationTemplateDetail,
  NotificationTemplateListParams,
} from './notification-types';

/**
 * Server-side ecommerce notification template operations section state for the
 * `(console)/ecommerce/notifications/templates` routes (TASK-PC-FE-089 —
 * ADR-031 Phase 5b).
 *
 * Eligibility gate: ecommerce resolves the operator's tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim — the console sends NO `X-Tenant-Id`.
 * The page resolves ecommerce eligibility from the data-driven registry and
 * passes it in here. If not eligible the section blocks with no ecommerce call.
 *
 * Resilience boundary (mirrors promotions-state.ts):
 *   - `401` → `redirect('/login')` (whole-session re-login, NOT a per-section degrade).
 *   - `403` → non-crashing inline "not available to your role" state.
 *   - `503` / timeout / network → DEGRADED (only the ecommerce section degrades).
 *   - any other producer error → degrade rather than crash.
 */
export interface NotificationsSectionState {
  /** Server-seeded page-0 template list (the table's initialData). */
  templates: NotificationTemplateList | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: NotificationsSectionState = {
  templates: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * @param eligible whether the operator is ecommerce-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no ecommerce call).
 * @param params optional list filters (page / size).
 */
export async function getNotificationsSectionState(
  eligible: boolean,
  params: NotificationTemplateListParams = {},
): Promise<NotificationsSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }
  try {
    const templates = await listTemplates({ page: 0, size: 20, ...params });
    return { ...EMPTY, templates };
  } catch (err) {
    return mapSectionError(err);
  }
}

export interface NotificationDetailSectionState {
  detail: NotificationTemplateDetail | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 TEMPLATE_NOT_FOUND — actionable not-found, not a crash. */
  notFound: boolean;
  degraded: boolean;
}

const DETAIL_EMPTY: NotificationDetailSectionState = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/** Detail-page section state (the `[id]/edit` route). */
export async function getNotificationDetailSectionState(
  eligible: boolean,
  id: string,
): Promise<NotificationDetailSectionState> {
  if (!eligible) {
    return { ...DETAIL_EMPTY, notEligible: true };
  }
  try {
    const detail = await getTemplate(id);
    return { ...DETAIL_EMPTY, detail };
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return { ...DETAIL_EMPTY, notFound: true };
    }
    const mapped = mapSectionError(err);
    return {
      ...DETAIL_EMPTY,
      forbidden: mapped.forbidden,
      degraded: mapped.degraded,
    };
  }
}

/** Shared resilience mapping for the list/detail server reads. */
function mapSectionError(err: unknown): NotificationsSectionState {
  if (err instanceof ApiError && err.status === 401) {
    redirect('/login');
  }
  if (err instanceof ApiError && err.status === 403) {
    return { ...EMPTY, forbidden: true };
  }
  if (err instanceof EcommerceUnavailableError) {
    return { ...EMPTY, degraded: true };
  }
  return { ...EMPTY, degraded: true };
}
