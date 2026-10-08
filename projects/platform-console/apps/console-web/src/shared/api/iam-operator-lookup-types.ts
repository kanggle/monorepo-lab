import { z } from 'zod';

/**
 * Client-safe wire shape of the IAM operator e-mail lookup —
 * `GET /api/admin/operators/lookup` (iam `admin-api.md` § same name,
 * TASK-MONO-777). zod only: no `next/headers`, no cookies, no fetch — client
 * hooks parse the same-origin proxy answer with it.
 *
 * `accountId` is the operator's `oidc_subject` = the `sub` that person carries
 * into the console — the value an erp employee ↔ account link needs.
 * `tenantId` is the operator's HOME tenant (`*` for a platform operator).
 */
export const OperatorLookupItemSchema = z.object({
  accountId: z.string().min(1),
  displayName: z.string().nullable().optional(),
  tenantId: z.string(),
});
export type OperatorLookupItem = z.infer<typeof OperatorLookupItemSchema>;

export const OperatorLookupResponseSchema = z.object({
  content: z.array(OperatorLookupItemSchema),
});
export type OperatorLookupResponse = z.infer<typeof OperatorLookupResponseSchema>;
