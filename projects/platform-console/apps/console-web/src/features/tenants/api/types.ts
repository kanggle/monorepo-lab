import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Feature-local types for the IAM tenant-management surface (TASK-PC-FE-226).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md`
 *   § "Tenant Lifecycle (TASK-BE-256)" —
 *     `POST /api/admin/tenants`, `GET /api/admin/tenants`,
 *     `GET /api/admin/tenants/{tenantId}`, `PATCH /api/admin/tenants/{tenantId}`.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. Feature-local (not cross-feature) per architecture.md
 * § Allowed Dependencies.
 *
 * NOTE on wire shape: the tenant list envelope uses `items` (NOT `content` —
 * distinct from the accounts/operators page envelopes, which do use
 * `content`). Verified against the contract's documented example response —
 * do not "fix" this to match the sibling IAM surfaces; it is a genuine
 * producer difference.
 *
 * This screen is a management CRUD surface for the isolation-boundary
 * resource itself (SUPER_ADMIN only) — functionally UNRELATED to
 * `features/tenant` (`TenantSwitcher`, the operator's own active-tenant
 * session selector). Do not conflate the two.
 *
 * TASK-MONO-773 S4: a create carries the first admin (`firstAdmin`, required);
 * a SUPER_ADMIN-created tenant is born `PENDING` («관리자 대기») until that
 * first admin accepts; every tenant response carries a `firstAdmin` summary.
 */

// --- tenant type / status ---------------------------------------------------

export const TENANT_TYPES = ['B2C_CONSUMER', 'B2B_ENTERPRISE'] as const;
export type TenantType = (typeof TENANT_TYPES)[number];

/**
 * Every status a tenant can be READ in (list filter · badges). TASK-MONO-773 S4
 * adds `PENDING` («관리자 대기»).
 */
export const TENANT_STATUSES = ['ACTIVE', 'SUSPENDED', 'PENDING'] as const;
export type TenantStatus = (typeof TENANT_STATUSES)[number];

/**
 * The statuses a PATCH may REQUEST — `PENDING` is never a request value (IAM
 * answers `400`; only the create makes it).
 */
export const TENANT_PATCH_STATUSES = ['ACTIVE', 'SUSPENDED'] as const;
export type TenantPatchStatus = (typeof TENANT_PATCH_STATUSES)[number];

export function tenantStatusTone(status: string): StatusTone {
  if (status === 'ACTIVE') return 'success';
  if (status === 'SUSPENDED') return 'warning';
  if (status === 'PENDING') return 'progress';
  return 'neutral';
}

/** `firstAdmin.state` suffix of a `PENDING` row (contract § 2.6.4 `/tenants`). */
const FIRST_ADMIN_STATE_SUFFIX: Record<string, string> = {
  EXPIRED: '초대 만료',
  NONE: '초대 없음',
  ACCEPTED: '활성화 재시도',
};

/** The filter-option label of a status (`PENDING` → «관리자 대기»; others verbatim). */
export function tenantStatusOptionLabel(status: string): string {
  return status === 'PENDING' ? '관리자 대기' : status;
}

// --- tenant_id validation (contract § "Tenant ID 규칙") ---------------------

/** `^[a-z][a-z0-9-]{1,31}$` — lowercase start, lowercase/digit/hyphen body,
 *  2-32 chars total (multi-tenancy.md#tenantid). */
export const TENANT_ID_REGEX = /^[a-z][a-z0-9-]{1,31}$/;

/** Reserved words the producer rejects at create time (`400
 *  TENANT_ID_RESERVED`) — mirrored here ONLY as a UX fail-fast pre-check;
 *  the producer remains the final authority. */
export const RESERVED_TENANT_IDS = [
  'admin',
  'internal',
  'system',
  'null',
  'default',
  'public',
  'gap',
  'iam',
  'auth',
  'oauth',
  'me',
] as const;

/** `null` when valid; otherwise a stable reason key for inline UX copy. */
export function tenantIdValidationError(tenantId: string): string | null {
  if (!TENANT_ID_REGEX.test(tenantId)) return 'TENANT_ID_FORMAT_INVALID';
  if ((RESERVED_TENANT_IDS as readonly string[]).includes(tenantId)) {
    return 'TENANT_ID_RESERVED';
  }
  return null;
}

// --- first admin (TASK-MONO-773 S4 — admin-api.md «firstAdmin 요약») -------

/** Loose address check (the same shape IAM uses) — IAM is the authority. */
export const FIRST_ADMIN_EMAIL_REGEX = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
export const FIRST_ADMIN_EMAIL_MAX = 255;
export const FIRST_ADMIN_DISPLAY_NAME_MAX = 64;

/** `null` when the (trimmed) first-admin draft is valid; otherwise a reason key. */
export function firstAdminValidationError(email: string, displayName: string): string | null {
  const e = email.trim();
  const n = displayName.trim();
  if (e.length === 0 || e.length > FIRST_ADMIN_EMAIL_MAX || !FIRST_ADMIN_EMAIL_REGEX.test(e)) {
    return 'FIRST_ADMIN_EMAIL_INVALID';
  }
  if (n.length === 0 || n.length > FIRST_ADMIN_DISPLAY_NAME_MAX) {
    return 'FIRST_ADMIN_DISPLAY_NAME_INVALID';
  }
  return null;
}

export const FirstAdminSummarySchema = z.object({
  // z.string(), not an enum — a future state must not crash the render.
  state: z.string(),
  email: z.string().nullable(),
  invitationId: z.string().nullable(),
  expiresAt: z.string().nullable(),
});
export type FirstAdminSummary = z.infer<typeof FirstAdminSummarySchema>;

// --- tenant resource (shared shape across create/get/patch responses) ------

export const TenantSchema = z.object({
  tenantId: z.string(),
  displayName: z.string(),
  // Kept as z.string() (not the TenantType enum) so a future producer value
  // never crashes the render — a generic label is safer than a parse throw
  // (mirrors the operators/accounts "tolerate an unknown status" posture).
  tenantType: z.string(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  /**
   * TASK-MONO-773 S4 — `null` for a tenant without a first-admin record;
   * 🔵 ABSENT on a pre-S4 backend's response (deploy-order window: Vercel ships
   * before the AMI), so it is optional AND nullable.
   */
  firstAdmin: FirstAdminSummarySchema.nullable().optional(),
  /** TASK-MONO-773 S4 — the POST response's invitation item (never a token). */
  firstAdminInvitation: z.record(z.string(), z.unknown()).nullable().optional(),
});
export type Tenant = z.infer<typeof TenantSchema>;

/**
 * The status shown for one tenant: `PENDING` reads «관리자 대기» plus the
 * `firstAdmin.state` suffix («· 초대 만료» · «· 초대 없음» · «· 활성화
 * 재시도»; `INVITED`/absent → none). Every other status stays verbatim — an
 * old (pre-S4) response renders exactly as before.
 */
export function tenantStatusLabel(tenant: Pick<Tenant, 'status' | 'firstAdmin'>): string {
  if (tenant.status !== 'PENDING') return tenant.status;
  const suffix = tenant.firstAdmin ? FIRST_ADMIN_STATE_SUFFIX[tenant.firstAdmin.state] : undefined;
  return suffix ? `관리자 대기 · ${suffix}` : '관리자 대기';
}

export const TenantPageSchema = z.object({
  items: z.array(TenantSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});
export type TenantPage = z.infer<typeof TenantPageSchema>;

export interface TenantListParams {
  /** `ACTIVE` | `SUSPENDED` | `PENDING` (TASK-MONO-773) filter; absent ⇒ all. */
  status?: TenantStatus;
  /** `B2C_CONSUMER` | `B2B_ENTERPRISE` filter; absent ⇒ all. */
  tenantType?: TenantType;
  page?: number;
  size?: number;
}

// --- create (POST /api/admin/tenants) --------------------------------------

export interface CreateTenantInput {
  tenantId: string;
  displayName: string;
  tenantType: TenantType;
  /**
   * Optional org-node to place the new tenant under (TASK-PC-FE-312 /
   * TASK-BE-625 — admin-api.md § POST /api/admin/tenants `orgNodeId`).
   * 🔴 ABSENT (not `undefined`-valued, not `null`, not `''`) when the operator
   *    leaves «소속 노드» empty — the request body must stay byte-identical to
   *    the pre-312 create (regression, task AC-4).
   */
  orgNodeId?: string;
  /**
   * TASK-MONO-773 S4 — REQUIRED: who receives the first-admin invitation (the
   * tenant stays «관리자 대기» until it is accepted).
   */
  firstAdmin: { email: string; displayName: string };
}

/**
 * One choice of the create form's «소속 노드 (선택)» select. The tenants
 * feature's OWN view-model (architecture.md § Forbidden Dependencies — it does
 * not import `features/org-hierarchy`); `app/(console)/tenants/page.tsx`
 * composes it from the org-node list server-side.
 */
export interface TenantOrgNodeOption {
  orgNodeId: string;
  name: string;
}

// --- update (PATCH /api/admin/tenants/{tenantId}) --------------------------

/** Both fields optional; the producer requires at least one to be present. */
export interface UpdateTenantInput {
  displayName?: string;
  /** Never `PENDING` (TASK-MONO-773 — not a request value). */
  status?: TenantPatchStatus;
}

/**
 * The audit reason an operator must enter before a tenant mutation fires
 * (→ `X-Operator-Reason` header, percent-encoded per TASK-MONO-176).
 * Required, non-empty (mirrors `OperatorMutationReason` /
 * subscriptions' inline reason parameter).
 */
export interface TenantMutationReason {
  reason: string;
}
