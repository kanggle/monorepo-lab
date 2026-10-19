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
    // TASK-PC-FE-318 — the IAM account linked to this employee
    // (`masterdata-api.md` § Employee 응답의 `accountId`). ABSENT = not
    // linked (`@JsonInclude(NON_NULL)`). Rendered as «연결됨» / «연결된 계정
    // 없음», never printed raw as a reference label (TASK-PC-FE-309).
    accountId: z.string().optional(),
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
// Employee «선택용» picker list (TASK-ERP-BE-045 — `masterdata-api.md` §
// `GET /api/erp/masterdata/employees/picker`, v1.2 additive). The four employee
// pickers (결재 대상 · 결재선 · 대결자 · 위임 사실 필터) read THIS, not the master list:
// it opens to `ERP_MASTERDATA_READ ∨ _WRITE ∨ ERP_APPROVAL_WRITE`, carries only the
// fields a picker label needs, and is narrowed to the operator's department subtree.
//   GET /api/erp/masterdata/employees/picker (?active=&page=&size=)
// ---------------------------------------------------------------------------

export const EmployeePickerEntrySchema = z
  .object({
    id: z.string(),
    employeeNumber: z.string(),
    // confidential / E7 — surfaced to the operator UI, never logged.
    name: z.string(),
    departmentId: z.string().nullable().optional(),
    // the department's name valid today (UTC) — ABSENT when it does not resolve.
    departmentName: z.string().optional(),
    // master `status` (`ACTIVE`/`RETIRED`) — honest tolerant.
    status: z.string(),
    // whether an IAM account is linked — the producer never sends the account id
    // here. Optional for tolerance; absent reads as «not linked» (the label then
    // warns, which is the safe side).
    accountLinked: z.boolean().optional(),
  })
  .passthrough();
export type EmployeePickerEntry = z.infer<typeof EmployeePickerEntrySchema>;

/** `meta.scope` values the producer documents. Parsed as a free string (tolerance
 *  invariant) — consumers compare against these literals. */
export const EMPLOYEE_PICKER_SCOPES = ['TENANT', 'SUBTREE'] as const;
export type EmployeePickerScope = (typeof EMPLOYEE_PICKER_SCOPES)[number];

export const EmployeePickerMetaSchema = ErpMetaSchema.extend({
  // `TENANT` = the whole tenant was listed · `SUBTREE` = narrowed to the
  // operator's department subtree (the «목록에 없는 직원» hint's basis).
  scope: z.string().optional(),
}).passthrough();

export const EmployeePickerListResponseSchema = z.object({
  data: z.array(EmployeePickerEntrySchema),
  meta: EmployeePickerMetaSchema,
});
export type EmployeePickerListResponse = z.infer<typeof EmployeePickerListResponseSchema>;

/** Query the picker accepts — no `asOf`, no per-master filters. */
export interface EmployeePickerQueryParams {
  active?: boolean;
  page?: number;
  size?: number;
}

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
