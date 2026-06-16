import { z } from 'zod';
import { EffectivePeriodSchema, AuditSchema, ErpMetaSchema } from './common';

/**
 * erp masters — JobGrade types (TASK-PC-FE-109 split; list ordered by
 * `displayOrder` asc producer-side). Read schemas + responses + create/update
 * inputs / body schemas (TASK-PC-FE-048). Re-exported via the `types` barrel.
 * 0 behavior change.
 *
 *   GET /api/erp/masterdata/job-grades (?asOf=&active=&page=&size=)
 *   GET /api/erp/masterdata/job-grades/{id} (?asOf=)
 */

export const JobGradeSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    displayOrder: z.number().int().optional(),
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type JobGrade = z.infer<typeof JobGradeSchema>;

export const JobGradeListResponseSchema = z.object({
  data: z.array(JobGradeSchema),
  meta: ErpMetaSchema,
});
export type JobGradeListResponse = z.infer<typeof JobGradeListResponseSchema>;

export const JobGradeDetailResponseSchema = z.object({
  data: JobGradeSchema,
  meta: ErpMetaSchema,
});
export type JobGradeDetailResponse = z.infer<typeof JobGradeDetailResponseSchema>;

// ---------------------------------------------------------------------------
// JobGrade WRITE inputs (TASK-PC-FE-048). retire is the shared `{ reason }`
// (ErpRetireBodySchema in ./common).
// ---------------------------------------------------------------------------

export interface CreateJobGradeInput {
  code: string;
  name: string;
  displayOrder?: number;
  effectiveFrom?: string;
}
export interface UpdateJobGradeInput {
  name?: string;
  displayOrder?: number;
  effectiveFrom?: string;
}
export const CreateJobGradeBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  displayOrder: z.number().int().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateJobGradeBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  displayOrder: z.number().int().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
