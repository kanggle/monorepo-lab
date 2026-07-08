import { z } from 'zod';

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
 */

// --- tenant type / status ---------------------------------------------------

export const TENANT_TYPES = ['B2C_CONSUMER', 'B2B_ENTERPRISE'] as const;
export type TenantType = (typeof TENANT_TYPES)[number];

export const TENANT_STATUSES = ['ACTIVE', 'SUSPENDED'] as const;
export type TenantStatus = (typeof TENANT_STATUSES)[number];

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
});
export type Tenant = z.infer<typeof TenantSchema>;

export const TenantPageSchema = z.object({
  items: z.array(TenantSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});
export type TenantPage = z.infer<typeof TenantPageSchema>;

export interface TenantListParams {
  /** `ACTIVE` | `SUSPENDED` filter; absent ⇒ all. */
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
}

// --- update (PATCH /api/admin/tenants/{tenantId}) --------------------------

/** Both fields optional; the producer requires at least one to be present. */
export interface UpdateTenantInput {
  displayName?: string;
  status?: TenantStatus;
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
