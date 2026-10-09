import { z } from 'zod';

/**
 * Feature-local types for the tenant ENTRY POLICY surface (TASK-MONO-771 S5 —
 * console-integration-contract § 2.4.3.3).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md` § Tenant Entry Policy —
 *     `GET` / `PUT /api/admin/tenants/{tenantId}/entry-policy`,
 *     `GET /api/admin/tenants/{tenantId}/entry-policy/enrolment-summary`.
 *
 * «이 테넌트에 운영자로 들어오려면 2단계 인증 필요» — a flag of the OPERATOR
 * plane, not the tenant's consumer-login rule.
 */

/** GET / PUT response — every key always present (`null` = never set). */
export const EntryPolicySchema = z.object({
  tenantId: z.string(),
  requireMfa: z.boolean(),
  updatedAt: z.string().nullable(),
  updatedBy: z.string().nullable(),
});
export type EntryPolicy = z.infer<typeof EntryPolicySchema>;

/** The «켜기 전 사전 점검» read — `operators = enrolled + notEnrolled + unlinked`. */
export const EnrolmentSummarySchema = z.object({
  tenantId: z.string(),
  operators: z.number().int().nonnegative(),
  enrolled: z.number().int().nonnegative(),
  notEnrolled: z.number().int().nonnegative(),
  unlinked: z.number().int().nonnegative(),
});
export type EnrolmentSummary = z.infer<typeof EnrolmentSummarySchema>;

/** Same-origin BFF PUT body: the decision + the operator-typed reason. */
export const SetEntryPolicyBodySchema = z
  .object({
    requireMfa: z.boolean(),
    reason: z.string(),
  })
  .strict();
export type SetEntryPolicyBody = z.infer<typeof SetEntryPolicyBodySchema>;
