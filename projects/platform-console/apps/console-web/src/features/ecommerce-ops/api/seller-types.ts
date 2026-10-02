import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Feature-local types for the ecommerce `product-service` seller operator
 * surface — the sellers facet of the ecommerce console absorption
 * (TASK-PC-FE-090, ADR-MONO-031 § 2.4.10 7th area / ADR-MONO-030 Step 4 facet f).
 * Drives the in-console seller list, detail, and register screens.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `product-service` `AdminSellerController`
 *   `GET /api/admin/sellers` (list), `GET /api/admin/sellers/{sellerId}` (detail),
 *   `POST /api/admin/sellers` (register).
 *   Precondition: TASK-BE-375.
 * Consumer obligation: `console-integration-contract.md` § 2.4.10.5
 *
 * TOLERANCE invariant: read shapes are permissive (`.passthrough()`); only the
 * fields the UI strictly needs are required, everything else passes through.
 * An unknown / future field (or status) never throws.
 *
 * Lifecycle actions (provision/suspend/close) added by TASK-PC-FE-154 (ADR-MONO-042).
 * The seller domain has NO update/delete (CRUD); mutation = state transitions.
 *
 * Base URL: ECOMMERCE_ADMIN_BASE_URL + /sellers (the ADMIN subtree `/api/admin/sellers`
 * — unlike promotions/notifications/shippings which use ECOMMERCE_PUBLIC_BASE_URL).
 */

// ===========================================================================
// SELLER STATUS
// ===========================================================================

/**
 * Full seller lifecycle (ADR-MONO-042): a seller is born `PENDING_PROVISIONING`,
 * becomes `ACTIVE` on provision, and can be `SUSPENDED` (reversible lock) or
 * `CLOSED` (terminal). The producer may emit a future value — read shapes keep
 * `status: z.string()` so anything unknown passes through and renders neutral.
 */
export const SELLER_STATUS_VALUES = [
  'PENDING_PROVISIONING',
  'ACTIVE',
  'SUSPENDED',
  'CLOSED',
] as const;
export type SellerStatus = (typeof SELLER_STATUS_VALUES)[number];

const SELLER_STATUS_TONE: Record<SellerStatus, StatusTone> = {
  ACTIVE: 'success',
  PENDING_PROVISIONING: 'warning',
  SUSPENDED: 'neutral',
  CLOSED: 'danger',
};

/**
 * Maps a (possibly unknown) seller status to a shared semantic
 * {@link StatusTone} (rendered via the shared `<StatusBadge>` — TASK-PC-FE-158).
 * A value outside the known lifecycle → `neutral`, so the console never crashes
 * on a future producer status (TOLERANCE invariant). The raw status string
 * stays the badge label at the call site.
 */
export function sellerStatusTone(status: string): StatusTone {
  return SELLER_STATUS_TONE[status as SellerStatus] ?? 'neutral';
}

/** The lifecycle actions valid from a given status (drives the detail UI). */
export type SellerLifecycleAction = 'provision' | 'suspend' | 'close';

export function sellerActionsFor(status: string): SellerLifecycleAction[] {
  switch (status) {
    case 'PENDING_PROVISIONING':
      return ['provision'];
    case 'ACTIVE':
      return ['suspend', 'close'];
    case 'SUSPENDED':
      return ['close'];
    default:
      // CLOSED (terminal) or any unknown status → no actions.
      return [];
  }
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** List — seller summary row. */
export const SellerSummarySchema = z
  .object({
    sellerId: z.string(),
    displayName: z.string(),
    status: z.string(),
    createdAt: z.string(),
  })
  .passthrough();
export type SellerSummary = z.infer<typeof SellerSummarySchema>;

/** List envelope. */
export const SellerListSchema = z
  .object({
    content: z.array(SellerSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type SellerList = z.infer<typeof SellerListSchema>;

/** Detail — full seller detail (adds updatedAt). */
export const SellerDetailSchema = z
  .object({
    sellerId: z.string(),
    displayName: z.string(),
    status: z.string(),
    createdAt: z.string(),
    updatedAt: z.string().optional().nullable(),
  })
  .passthrough();
export type SellerDetail = z.infer<typeof SellerDetailSchema>;

/** Register response — { sellerId }. */
export const RegisterSellerResponseSchema = z
  .object({ sellerId: z.string() })
  .passthrough();
export type RegisterSellerResponse = z.infer<typeof RegisterSellerResponseSchema>;

// ===========================================================================
// WRITE request bodies
// ===========================================================================

/**
 * RegisterSellerRequest body: `sellerId` (≤64 chars, non-blank) + `displayName` (non-blank).
 * Used by both the route handler (Zod validation) and the client form.
 */
export const RegisterSellerBodySchema = z.object({
  sellerId: z
    .string()
    .min(1, '셀러 ID를 입력해 주세요.')
    .max(64, '셀러 ID는 64자 이하여야 합니다.'),
  displayName: z.string().min(1, '셀러 이름을 입력해 주세요.'),
});
export type RegisterSellerBody = z.infer<typeof RegisterSellerBodySchema>;

// ===========================================================================
// SUMMARY (TASK-PC-FE-164 — period-based counts)
// ===========================================================================

/** GET /admin/sellers/summary — period-based count.
 *  Response: { today, week, month, total } all non-negative integers. */
export const SellerAreaSummarySchema = z
  .object({
    today: z.number().int().nonnegative(),
    week: z.number().int().nonnegative(),
    month: z.number().int().nonnegative(),
    total: z.number().int().nonnegative(),
  })
  .passthrough();
export type SellerAreaSummary = z.infer<typeof SellerAreaSummarySchema>;

// ===========================================================================
// List query params + pagination defaults
// ===========================================================================

export const SELLER_DEFAULT_PAGE_SIZE = 20;
export const SELLER_MAX_PAGE_SIZE = 100;

export interface SellerListParams {
  page?: number;
  size?: number;
}

// ===========================================================================
// MEMBERS (TASK-MONO-752 — ADR-MONO-079 D5: people linked to a seller)
//   GET  /api/admin/sellers/{id}/members      → { members, invitations }
//   POST /api/admin/sellers/{id}/invitations  → 201 { invitationId, email, expiresAt, token }
// The token is returned once, at invite — the console shows it to the operator
// to hand over (there is no mail path). The list never carries it.
// ===========================================================================

export const SellerMemberSchema = z
  .object({
    accountId: z.string(),
    role: z.string(),
    status: z.string(),
    joinedAt: z.string(),
  })
  .passthrough();
export type SellerMember = z.infer<typeof SellerMemberSchema>;

export const SellerInvitationSchema = z
  .object({
    invitationId: z.string(),
    email: z.string(),
    status: z.string(),
    expired: z.boolean().optional(),
    expiresAt: z.string(),
    createdAt: z.string(),
    acceptedAt: z.string().optional().nullable(),
  })
  .passthrough();
export type SellerInvitation = z.infer<typeof SellerInvitationSchema>;

export const SellerMembersSchema = z
  .object({
    members: z.array(SellerMemberSchema),
    invitations: z.array(SellerInvitationSchema),
  })
  .passthrough();
export type SellerMembers = z.infer<typeof SellerMembersSchema>;

export const InviteSellerMemberBodySchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, '이메일을 입력해 주세요.')
    .max(320, '이메일은 320자 이하여야 합니다.')
    .email('이메일 형식이 올바르지 않습니다.'),
});
export type InviteSellerMemberBody = z.infer<typeof InviteSellerMemberBodySchema>;

export const InviteSellerMemberResponseSchema = z
  .object({
    invitationId: z.string(),
    email: z.string(),
    expiresAt: z.string(),
    token: z.string(),
  })
  .passthrough();
export type InviteSellerMemberResponse = z.infer<
  typeof InviteSellerMemberResponseSchema
>;

/** Display label of an invitation row: 수락됨 / 만료 / 대기. */
export function invitationStateLabel(inv: SellerInvitation): string {
  if (inv.status === 'ACCEPTED') return '수락됨';
  if (inv.expired) return '만료';
  return '대기';
}
