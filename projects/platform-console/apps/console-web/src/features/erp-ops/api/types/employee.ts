import { z } from 'zod';
import { EffectivePeriodSchema, AuditSchema, ErpMetaSchema } from './common';

/**
 * erp masters — Employee types (TASK-PC-FE-109 split; cross-refs department /
 * jobGrade / costCenter; employment status). Read schemas + responses +
 * create/update inputs / body schemas (TASK-PC-FE-048). `name` is confidential
 * (E7) — surfaced to the operator UI but never logged by the api module.
 * Re-exported verbatim via the `types` barrel. 0 behavior change.
 *
 *   GET /api/erp/masterdata/employees (?asOf=&active=&departmentId=&costCenterId=&page=&size=)
 *   GET /api/erp/masterdata/employees/{id} (?asOf=)
 */

export const EmployeeSchema = z
  .object({
    id: z.string(),
    employeeNumber: z.string(),
    // confidential / E7 — surfaced to the operator UI but never
    // logged by the api module.
    name: z.string(),
    departmentId: z.string().nullable().optional(),
    jobGradeId: z.string().nullable().optional(),
    costCenterId: z.string().nullable().optional(),
    // master `status` (`ACTIVE`/`RETIRED`) — honest tolerant.
    status: z.string(),
    // separate from master status: employment lifecycle
    // (`EMPLOYED`/`ON_LEAVE`/`SEPARATED`/...) — honest tolerant.
    employmentStatus: z.string().optional(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type Employee = z.infer<typeof EmployeeSchema>;

export const EmployeeListResponseSchema = z.object({
  data: z.array(EmployeeSchema),
  meta: ErpMetaSchema,
});
export type EmployeeListResponse = z.infer<typeof EmployeeListResponseSchema>;

export const EmployeeDetailResponseSchema = z.object({
  data: EmployeeSchema,
  meta: ErpMetaSchema,
});
export type EmployeeDetailResponse = z.infer<typeof EmployeeDetailResponseSchema>;

// ---------------------------------------------------------------------------
// Employee WRITE inputs (TASK-PC-FE-048). Consume the UNCHANGED producer
// `masterdata-api.md` § Employee create/update bodies. retire is the shared
// `{ reason }` (ErpRetireBodySchema in ./common).
// ---------------------------------------------------------------------------

export interface CreateEmployeeInput {
  employeeNumber: string;
  name: string;
  departmentId?: string | null;
  costCenterId?: string | null;
  jobGradeId?: string | null;
  effectiveFrom?: string;
}
export interface UpdateEmployeeInput {
  name?: string;
  departmentId?: string | null;
  costCenterId?: string | null;
  jobGradeId?: string | null;
  effectiveFrom?: string;
}
export const CreateEmployeeBodySchema = z.object({
  employeeNumber: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  departmentId: z.string().min(1).nullable().optional(),
  costCenterId: z.string().min(1).nullable().optional(),
  jobGradeId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateEmployeeBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  departmentId: z.string().min(1).nullable().optional(),
  costCenterId: z.string().min(1).nullable().optional(),
  jobGradeId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
