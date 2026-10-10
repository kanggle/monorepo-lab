import { z } from 'zod';

/**
 * Operator invitation wire shapes (TASK-MONO-772 S5 — console slice of
 * ADR-MONO-080 D6). Client-safe (pure zod — imported by the client hooks and
 * by the server api module alike).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md` § Operator Invitation
 *   («공통 응답 항목 (invitation item)» — create · list · cancel · resend share it).
 * Consumer obligation: `console-integration-contract.md` § 2.4.3 rows 11–14 +
 * the «등록 → 초대» bullet.
 *
 * 🔴 The producer never returns a token, a token hash or a link (OD-4 — the
 *    invitation travels by mail only). `z.object` STRIPS unknown keys, so even
 *    if a future producer build leaked one, the parsed value the screen renders
 *    could not carry it. There is no field for it here on purpose.
 *
 * Tolerance: `status` / `delivery.status` are parsed as `z.string()` — an
 * unknown future value degrades to a generic label, never a parse failure that
 * would blank the section.
 */

/** `delivery.status` values the producer documents (admin-api.md). */
export const INVITATION_DELIVERY_STATUSES = [
  'SENT',
  'FAILED_TRANSIENT',
  'FAILED_PERMANENT',
] as const;
export type InvitationDeliveryStatus =
  (typeof INVITATION_DELIVERY_STATUSES)[number];

/** Invitation lifecycle (`EXPIRED` is NOT a status — `expired` is a read-time flag). */
export const INVITATION_STATUSES = ['PENDING', 'ACCEPTED', 'CANCELLED'] as const;
export type InvitationStatus = (typeof INVITATION_STATUSES)[number];

export const InvitationDeliverySchema = z.object({
  status: z.string(),
  attemptedAt: z.string().nullable().optional(),
});
export type InvitationDelivery = z.infer<typeof InvitationDeliverySchema>;

export const OperatorInvitationSchema = z.object({
  invitationId: z.string(),
  tenantId: z.string(),
  email: z.string(),
  displayName: z.string(),
  roles: z.array(z.string()),
  status: z.string(),
  expired: z.boolean(),
  expiresAt: z.string(),
  createdAt: z.string(),
  invitedBy: z.string().nullable().optional(),
  /** `null` before the first send attempt (the producer writes it after commit). */
  delivery: InvitationDeliverySchema.nullable().optional(),
  acceptedAt: z.string().nullable().optional(),
  acceptedOperatorId: z.string().nullable().optional(),
  cancelledAt: z.string().nullable().optional(),
  /** Create response only. */
  auditId: z.string().optional(),
});
export type OperatorInvitation = z.infer<typeof OperatorInvitationSchema>;

export const OperatorInvitationPageSchema = z.object({
  content: z.array(OperatorInvitationSchema),
  totalElements: z.number(),
  page: z.number(),
  size: z.number(),
  totalPages: z.number(),
});
export type OperatorInvitationPage = z.infer<typeof OperatorInvitationPageSchema>;

/** Invite form draft (row 11). `tenantId` is the active tenant — never `*`. */
export interface InviteOperatorInput {
  email: string;
  displayName: string;
  roles: string[];
  tenantId: string;
}

export interface InvitationListParams {
  /** Defaults to the active tenant server-side. */
  tenantId?: string;
  status?: InvitationStatus;
  page?: number;
  size?: number;
}

/** True when the last send attempt failed — shown as a WARNING, never as an
 *  error (the invitation exists; OD-4 «발송 실패는 화면에»). */
export function isDeliveryFailed(inv: Pick<OperatorInvitation, 'delivery'>): boolean {
  const s = inv.delivery?.status;
  return s === 'FAILED_TRANSIENT' || s === 'FAILED_PERMANENT';
}
