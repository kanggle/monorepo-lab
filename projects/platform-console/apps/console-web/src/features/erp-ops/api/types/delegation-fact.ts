import { z } from 'zod';
import { ReadModelMetaSchema } from './common';

/**
 * erp read-model — DelegationFact types (TASK-PC-FE-055; TASK-PC-FE-109
 * split). Read-model projection of approval-delegation grants. All optional
 * fields are NON_NULL-absent — parse tolerant (`.optional()` NOT
 * `.nullable()`). A `validTo` absent means open-ended / 무기한. Re-exported
 * via the `types` barrel. 0 behavior change.
 *
 *   GET /api/erp/read-model/delegations (?delegatorId=&delegateId=&status=&activeAt=&page=&size=)
 *   GET /api/erp/read-model/delegations/{grantId}
 */

export const DelegationFactSchema = z
  .object({
    grantId: z.string(),
    // free-string for tolerance (ACTIVE | REVOKED | future)
    status: z.string(),
    delegatorId: z.string(),
    delegateId: z.string(),
    // NON_NULL-absent: out-of-order revoke-before-grant → ABSENT.
    validFrom: z.string().optional(),
    // NON_NULL-absent: ABSENT = open-ended (무기한) or revoke-before-grant.
    validTo: z.string().optional(),
    // NON_NULL-absent: ABSENT when no reason was provided.
    reason: z.string().optional(),
    // NON_NULL-absent: ABSENT while ACTIVE.
    revokedAt: z.string().optional(),
    // NON_NULL-absent: scope of the grant — GLOBAL (blanket) | REQUEST (one request);
    // ABSENT when only a revoke was seen out-of-order (scope unknown — BE-018).
    scope: z.string().optional(),
    // NON_NULL-absent: the target approvalRequestId — present only when scope=REQUEST.
    scopeRequestId: z.string().optional(),
  })
  .passthrough();
export type DelegationFact = z.infer<typeof DelegationFactSchema>;

export const DelegationFactListResponseSchema = z.object({
  data: z.array(DelegationFactSchema),
  meta: ReadModelMetaSchema,
});
export type DelegationFactListResponse = z.infer<
  typeof DelegationFactListResponseSchema
>;

export const DelegationFactDetailResponseSchema = z.object({
  data: DelegationFactSchema,
  meta: ReadModelMetaSchema,
});
export type DelegationFactDetailResponse = z.infer<
  typeof DelegationFactDetailResponseSchema
>;

/** Query params for the read-model delegation-fact list.
 *  `delegatorId` / `delegateId` / `status` / `activeAt` are
 *  producer-defined filters — the console passes them verbatim. */
export interface DelegationFactListQueryParams {
  delegatorId?: string;
  delegateId?: string;
  status?: string;
  activeAt?: string;
  page?: number;
  size?: number;
}
