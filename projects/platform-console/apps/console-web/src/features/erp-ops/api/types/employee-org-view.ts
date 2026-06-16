import { z } from 'zod';
import { EffectivePeriodSchema, ReadModelMetaSchema } from './common';

/**
 * erp read-model — EmployeeOrgView types (TASK-PC-FE-049; TASK-PC-FE-109
 * split). The denormalised employee org-view projection + its department /
 * cost-center / job-grade refs. A `null` ref means the projected master event
 * has not yet been consumed (eventually-consistent, E5) — NEVER fabricate a
 * non-null value for an unresolved reference. Re-exported via the `types`
 * barrel. 0 behavior change.
 *
 *   GET /api/erp/read-model/employees (?page=&size=&asOf=&departmentId=&status=)
 *   GET /api/erp/read-model/employees/{id} (?asOf=)
 */

export const DepartmentPathNodeSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
  })
  .passthrough();
export type DepartmentPathNode = z.infer<typeof DepartmentPathNodeSchema>;

export const DepartmentRefSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    path: z.array(DepartmentPathNodeSchema),
  })
  .passthrough();
export type DepartmentRef = z.infer<typeof DepartmentRefSchema>;

export const CostCenterRefSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
  })
  .passthrough();
export type CostCenterRef = z.infer<typeof CostCenterRefSchema>;

export const JobGradeRefSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    displayOrder: z.number().int().optional(),
  })
  .passthrough();
export type JobGradeRef = z.infer<typeof JobGradeRefSchema>;

export const EmployeeOrgViewSchema = z
  .object({
    id: z.string(),
    employeeNumber: z.string(),
    name: z.string(),
    // free-string for tolerance (ACTIVE / RETIRED / future)
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    department: DepartmentRefSchema.nullable(),
    costCenter: CostCenterRefSchema.nullable(),
    jobGrade: JobGradeRefSchema.nullable(),
  })
  .passthrough();
export type EmployeeOrgView = z.infer<typeof EmployeeOrgViewSchema>;

export const EmployeeOrgViewListResponseSchema = z.object({
  data: z.array(EmployeeOrgViewSchema),
  meta: ReadModelMetaSchema,
});
export type EmployeeOrgViewListResponse = z.infer<
  typeof EmployeeOrgViewListResponseSchema
>;

export const EmployeeOrgViewDetailResponseSchema = z.object({
  data: EmployeeOrgViewSchema,
  meta: ReadModelMetaSchema,
});
export type EmployeeOrgViewDetailResponse = z.infer<
  typeof EmployeeOrgViewDetailResponseSchema
>;

/** Query params for the read-model employee org-view list.
 *  Extends `ErpListQueryParams` with `departmentId` (subtree filter)
 *  and `status` (`ACTIVE | RETIRED`; default `ACTIVE`). `asOf` (E3)
 *  threads through verbatim. */
export interface OrgViewListQueryParams {
  asOf?: string;
  page?: number;
  size?: number;
  departmentId?: string;
  status?: string;
}
