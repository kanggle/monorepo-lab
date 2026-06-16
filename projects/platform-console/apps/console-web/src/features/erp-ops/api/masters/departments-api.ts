import {
  DepartmentListResponseSchema,
  type DepartmentListResponse,
  DepartmentDetailResponseSchema,
  type Department,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateDepartmentInput,
  type UpdateDepartmentInput,
  type RetireDepartmentInput,
  type MoveDepartmentParentInput,
} from '../types';
import { callErp } from '../erp-client';
import { listQs, detailQs } from './masters-qs';

/**
 * erp masters — departments api (TASK-PC-FE-108 split of the former
 * `erp-masters-api.ts` god-file; behavior-preserving, re-exported verbatim
 * through the `erp-masters-api` barrel → the `erp-api` public barrel).
 * List + detail reads + the department WRITE PILOT mutations.
 */

// ---------------------------------------------------------------------------
// departments — list + detail
//   GET /api/erp/masterdata/departments
//   GET /api/erp/masterdata/departments/{id}
// ---------------------------------------------------------------------------

export async function listDepartments(
  params: ErpListQueryParams = {},
): Promise<DepartmentListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments?${listQs(params)}`,
      logPath: '/api/erp/masterdata/departments',
    },
    (json) => DepartmentListResponseSchema.parse(json),
  );
}

export async function getDepartmentById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}${detailQs(params)}`,
      // confidential — the log path carries no record id.
      logPath: '/api/erp/masterdata/departments/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DepartmentDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// departments — WRITE PILOT (TASK-PC-FE-046 / § 2.4.8 *Department
//   write binding (PILOT)*). The FIRST erp console write. Consumes the
//   UNCHANGED producer `masterdata-api.md` § Department mutations:
//     POST  /api/erp/masterdata/departments              (create)
//     PATCH /api/erp/masterdata/departments/{id}          (update)
//     POST  /api/erp/masterdata/departments/{id}/retire   (retire)
//     POST  /api/erp/masterdata/departments/{id}/move-parent (move-parent)
//   Each carries an `Idempotency-Key` (generated console-side per
//   attempt). `reason` rides in the BODY only where the producer has a
//   slot (retire required / move-parent optional) — NEVER an
//   `X-Operator-Reason` header (erp does not read it).
// ---------------------------------------------------------------------------

/** Parses a department mutation response envelope (`{ data, meta }`)
 *  into the `Department` — same tolerant shape as `getDepartmentById`. */
function parseDepartmentData(json: unknown): Department {
  const env = (json ?? {}) as { data?: unknown };
  return DepartmentDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createDepartment(
  input: CreateDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: '/api/erp/masterdata/departments',
      logPath: '/api/erp/masterdata/departments',
      method: 'POST',
      idempotencyKey,
      body: {
        code: input.code,
        name: input.name,
        ...(input.parentId !== undefined ? { parentId: input.parentId } : {}),
        ...(input.effectiveFrom ? { effectiveFrom: input.effectiveFrom } : {}),
      },
    },
    parseDepartmentData,
  );
}

export async function updateDepartment(
  id: string,
  input: UpdateDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/departments/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: {
        ...(input.name !== undefined ? { name: input.name } : {}),
        ...(input.effectiveFrom ? { effectiveFrom: input.effectiveFrom } : {}),
      },
    },
    parseDepartmentData,
  );
}

export async function retireDepartment(
  id: string,
  input: RetireDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/departments/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason: input.reason },
    },
    parseDepartmentData,
  );
}

export async function moveDepartmentParent(
  id: string,
  input: MoveDepartmentParentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}/move-parent`,
      logPath: '/api/erp/masterdata/departments/{id}/move-parent',
      method: 'POST',
      idempotencyKey,
      body: {
        newParentId: input.newParentId,
        effectiveFrom: input.effectiveFrom,
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    parseDepartmentData,
  );
}
