import { z } from 'zod';
import { EffectivePeriodSchema, AuditSchema, ErpMetaSchema } from './common';

/**
 * erp masters — CostCenter types (TASK-PC-FE-109 split; references department).
 * Read schemas + responses + create/update inputs / body schemas
 * (TASK-PC-FE-048). Re-exported via the `types` barrel. 0 behavior change.
 *
 *   GET /api/erp/masterdata/cost-centers (?asOf=&active=&departmentId=&page=&size=)
 *   GET /api/erp/masterdata/cost-centers/{id} (?asOf=)
 */

export const CostCenterSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    departmentId: z.string().nullable().optional(),
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type CostCenter = z.infer<typeof CostCenterSchema>;

export const CostCenterListResponseSchema = z.object({
  data: z.array(CostCenterSchema),
  meta: ErpMetaSchema,
});
export type CostCenterListResponse = z.infer<typeof CostCenterListResponseSchema>;

export const CostCenterDetailResponseSchema = z.object({
  data: CostCenterSchema,
  meta: ErpMetaSchema,
});
export type CostCenterDetailResponse = z.infer<typeof CostCenterDetailResponseSchema>;

// ---------------------------------------------------------------------------
// CostCenter WRITE inputs (TASK-PC-FE-048). retire is the shared `{ reason }`
// (ErpRetireBodySchema in ./common).
// ---------------------------------------------------------------------------

export interface CreateCostCenterInput {
  code: string;
  name: string;
  departmentId?: string | null;
  effectiveFrom?: string;
}
export interface UpdateCostCenterInput {
  name?: string;
  departmentId?: string | null;
  effectiveFrom?: string;
}
export const CreateCostCenterBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  departmentId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateCostCenterBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  departmentId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
