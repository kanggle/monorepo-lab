import { z } from 'zod';

/**
 * Self-service tenant-onboarding types (ADR-MONO-044 §3.4 / TASK-PC-FE-182).
 * Consumes `onboarding-api.md` § POST /api/admin/onboarding/organizations
 * (authoritative producer — request/response/error owned by IAM admin-service).
 */

/** New-tenant slug — mirrors the producer `^[a-z][a-z0-9-]{1,31}$` (a fail-fast
 *  pre-check at the boundary; the producer stays the final authority). */
export const TENANT_SLUG_RE = /^[a-z][a-z0-9-]{1,31}$/;

/** The client-supplied onboarding form fields (the caller's IAM OIDC access
 *  token is attached server-side as `subjectToken` — never in this shape). */
export const CreateOrganizationInputSchema = z.object({
  tenantId: z.string().regex(TENANT_SLUG_RE),
  organizationName: z.string().trim().min(1).max(100),
});
export type CreateOrganizationInput = z.infer<
  typeof CreateOrganizationInputSchema
>;

/** Producer 201 response (onboarding-api.md § Response 201). */
export const OnboardResponseSchema = z.object({
  tenantId: z.string().min(1),
  operatorId: z.string().min(1),
  roles: z.array(z.string()),
  status: z.string().min(1),
});
export type OnboardResponse = z.infer<typeof OnboardResponseSchema>;

// --- received invitations (TASK-MONO-773 OD-7 — onboarding-api.md § received-invitations) ----------------

/** One invitation addressed to the caller's verified email (read-only — never a token or a link). */
export const ReceivedInvitationSchema = z.object({
  invitationId: z.string().min(1),
  tenantId: z.string().min(1),
  tenantDisplayName: z.string().nullable(),
  roles: z.array(z.string()),
  firstAdmin: z.boolean(),
  expiresAt: z.string(),
  invitedAt: z.string(),
});
export type ReceivedInvitation = z.infer<typeof ReceivedInvitationSchema>;

/** Producer 200 — `accountState` kept a string so a future value never crashes the screen. */
export const ReceivedInvitationsResponseSchema = z.object({
  accountState: z.string().min(1),
  items: z.array(ReceivedInvitationSchema),
});
export type ReceivedInvitationsResponse = z.infer<typeof ReceivedInvitationsResponseSchema>;

/** What the «테넌트 생성» screen knows about the caller's received invitations. */
export type ReceivedInvitationsState =
  | { kind: 'loaded'; accountState: string; items: ReceivedInvitation[] }
  /** IAM could not judge (503 / timeout / bad shape) — never rendered as «no invitations». */
  | { kind: 'unavailable' };
