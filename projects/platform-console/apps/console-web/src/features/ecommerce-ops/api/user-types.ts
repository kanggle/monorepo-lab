import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Feature-local types for the ecommerce `user-service` operator surface —
 * the users facet of the ecommerce console absorption (TASK-PC-FE-084,
 * ADR-MONO-031 Phase 2b). Drives the in-console user list and detail screens.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `user-service` (tenant-isolated by BE-367 operator-plane)
 *   `GET /admin/users` (list), `GET /admin/users/{userId}` (detail)
 * Consumer obligation: `console-integration-contract.md` § 2.4.10
 *
 * TOLERANCE invariant: read shapes are permissive (`.passthrough()`); only the
 * fields the UI strictly needs are required, everything else passes through.
 * An unknown / future `status` enum parses to a generic string and NEVER throws.
 *
 * `email` / `name` are NULLABLE: user-service made both columns nullable
 * (V5__user_profile_email_name_nullable) and serializes them as JSON `null`
 * for minimally-created profiles (IAM `account.created` before first
 * profile-update) and anonymized/withdrawn accounts (`account.deleted`
 * reaction, which nulls — not tombstones — email/name). These rows are NOT
 * filtered out of the admin list, so a plain `z.string()` throws the moment any
 * such user exists, and that parse failure is mis-surfaced as a fake degrade
 * ("일시적으로 불러올 수 없습니다"). Same bug class as the order-detail
 * null-address2 fix — see order-types.ts.
 *
 * READ-ONLY surface: no mutation schemas, no state machine, no
 * allowedTransitions. Users are display-only status in this console surface.
 */

// ===========================================================================
// USER STATUS
// ===========================================================================

export const USER_STATUS_VALUES = [
  'ACTIVE',
  'SUSPENDED',
  'WITHDRAWN',
] as const;
export type UserStatus = (typeof USER_STATUS_VALUES)[number];

const USER_STATUS_TONE: Record<UserStatus, StatusTone> = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  WITHDRAWN: 'neutral',
};

/**
 * Maps a (possibly unknown) user status to a shared semantic {@link StatusTone}
 * (rendered via the shared `<StatusBadge>` — TASK-PC-FE-158). Unknown/future
 * status → `neutral` (TOLERANCE invariant). The raw status string stays the
 * badge label at the call site.
 */
export function userStatusTone(status: string): StatusTone {
  return USER_STATUS_TONE[status as UserStatus] ?? 'neutral';
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** List — user summary row. */
export const UserSummarySchema = z
  .object({
    userId: z.string(),
    email: z.string().nullable().optional(),
    name: z.string().nullable().optional(),
    nickname: z.string().nullable().optional(),
    status: z.string(),
    createdAt: z.string(),
  })
  .passthrough();
export type UserSummary = z.infer<typeof UserSummarySchema>;

/** List envelope — AdminUserListResponse. */
export const UserListSchema = z
  .object({
    content: z.array(UserSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type UserList = z.infer<typeof UserListSchema>;

/** Detail — AdminUserDetailResponse (summary + extended profile fields). */
export const UserDetailSchema = z
  .object({
    userId: z.string(),
    email: z.string().nullable().optional(),
    name: z.string().nullable().optional(),
    nickname: z.string().nullable().optional(),
    status: z.string(),
    createdAt: z.string(),
    phone: z.string().nullable().optional(),
    profileImageUrl: z.string().nullable().optional(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type UserDetail = z.infer<typeof UserDetailSchema>;

// ===========================================================================
// List query params + pagination defaults
// ===========================================================================

export const USER_DEFAULT_PAGE_SIZE = 20;
export const USER_MAX_PAGE_SIZE = 100;

export interface UserListParams {
  status?: string;
  email?: string;
  page?: number;
  size?: number;
}
