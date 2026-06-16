import { z } from 'zod';
import { EffectivePeriodSchema, AuditSchema, ErpMetaSchema } from './common';

/**
 * erp masters — Department types (TASK-PC-FE-109 split). Read schemas +
 * responses + the WRITE PILOT inputs / body schemas (TASK-PC-FE-046).
 * Re-exported verbatim via the `types` barrel. 0 behavior change.
 *
 *   GET /api/erp/masterdata/departments (?asOf=&active=&parentId=&page=&size=)
 *   GET /api/erp/masterdata/departments/{id} (?asOf=)
 */

export const DepartmentSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    parentId: z.string().nullable().optional(),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type Department = z.infer<typeof DepartmentSchema>;

export const DepartmentListResponseSchema = z.object({
  data: z.array(DepartmentSchema),
  meta: ErpMetaSchema,
});
export type DepartmentListResponse = z.infer<typeof DepartmentListResponseSchema>;

export const DepartmentDetailResponseSchema = z.object({
  data: DepartmentSchema,
  meta: ErpMetaSchema,
});
export type DepartmentDetailResponse = z.infer<typeof DepartmentDetailResponseSchema>;

// ---------------------------------------------------------------------------
// Department WRITE inputs (TASK-PC-FE-046 — the department write PILOT;
// console-integration-contract § 2.4.8 *Department write binding (PILOT)*).
// These mirror the UNCHANGED producer `masterdata-api.md` § Department
// mutation request bodies — the console does NOT redefine them, it consumes
// them.
//
// `reason` lives on the body ONLY where the producer has a slot
// (`retire` required, `move-parent` optional). `create`/`update` have
// NO reason slot — the console MUST NOT fabricate `X-Operator-Reason`
// (erp does not read it). Every mutation carries an `Idempotency-Key`
// (set as a header by the api client, supplied console-side per attempt).
// ---------------------------------------------------------------------------

/** `POST /api/erp/masterdata/departments` body — create. `code` +
 *  `name` required; `parentId` optional (root when absent);
 *  `effectiveFrom` optional ISO-8601 DATE (producer defaults today). */
export interface CreateDepartmentInput {
  code: string;
  name: string;
  parentId?: string | null;
  effectiveFrom?: string;
}

/** `PATCH /api/erp/masterdata/departments/{id}` body — update. Partial;
 *  a new revision is created producer-side (NOT an in-place overwrite). */
export interface UpdateDepartmentInput {
  name?: string;
  effectiveFrom?: string;
}

/** `POST /api/erp/masterdata/departments/{id}/retire` body. `reason`
 *  REQUIRED (≤256) — the producer has a slot here (maps to E8 audit). */
export interface RetireDepartmentInput {
  reason: string;
}

/** `POST /api/erp/masterdata/departments/{id}/move-parent` body.
 *  `newParentId` may be `null` (promote to root). `effectiveFrom`
 *  required ISO-8601 DATE. `reason` optional (≤256, producer slot). */
export interface MoveDepartmentParentInput {
  newParentId: string | null;
  effectiveFrom: string;
  reason?: string;
}

/** Zod parsers for the same-origin proxy request bodies (the route
 *  handlers validate the incoming client body before forwarding). */
export const CreateDepartmentBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  parentId: z.string().min(1).nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

export const UpdateDepartmentBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});

export const RetireDepartmentBodySchema = z.object({
  reason: z.string().min(1).max(256),
  idempotencyKey: z.string().min(1),
});

export const MoveDepartmentParentBodySchema = z.object({
  newParentId: z.string().min(1).nullable(),
  effectiveFrom: z.string().min(1),
  reason: z.string().max(256).optional(),
  idempotencyKey: z.string().min(1),
});
