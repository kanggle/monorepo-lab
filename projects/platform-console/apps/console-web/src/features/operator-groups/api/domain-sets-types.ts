import { z } from 'zod';

/**
 * Feature-local types for the GROUP ADR-MONO-083 「업무 권한 세트」 surface
 * (TASK-PC-FE-338 slice b — contract § 2.4.3.4 rows 9-11).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `iam-platform/specs/contracts/http/admin-api.md` § 도메인 업무 권한 세트 —
 *     `GET/POST /api/admin/groups/{groupId}/domain-permission-sets`,
 *     `DELETE …/domain-permission-sets/{setId}`.
 *
 * NO cross-feature import of `features/domain-permission-sets` (architecture.md
 * § Forbidden Dependencies) — see `features/operators/api/domain-access-types.ts`
 * file header for the identical remedy this module applies (own small
 * view-models; the grant-picker option list is a separate trimmed copy of
 * the one `features/operators` already owns — the small duplication is the
 * cost of the architecture's one-way feature isolation, not an oversight).
 */

export const GroupDomainSetSchema = z.object({
  setId: z.string(),
  name: z.string(),
  systemKey: z.string().nullable(),
  grantedAt: z.string(),
  grantedBy: z.string().nullable(),
});
export type GroupDomainSet = z.infer<typeof GroupDomainSetSchema>;

export const GroupDomainSetListSchema = z.object({
  items: z.array(GroupDomainSetSchema),
});

/** `POST …/domain-permission-sets` 201 — `{ item, fannedOutRows }`. */
export const GrantGroupDomainSetResultSchema = z.object({
  item: GroupDomainSetSchema,
  fannedOutRows: z.number().int(),
});
export type GrantGroupDomainSetResult = z.infer<
  typeof GrantGroupDomainSetResultSchema
>;

// --- grant-picker view-model (own copy — see file header) ------------------

export const GrantableSetOptionSchema = z
  .object({
    setId: z.string(),
    name: z.string(),
    systemKey: z.string().nullable(),
    readOnly: z.boolean(),
    domains: z.array(z.string()),
  })
  .passthrough();
export type GrantableSetOption = z.infer<typeof GrantableSetOptionSchema>;

export const GrantableSetOptionListSchema = z.object({
  items: z.array(GrantableSetOptionSchema),
});
