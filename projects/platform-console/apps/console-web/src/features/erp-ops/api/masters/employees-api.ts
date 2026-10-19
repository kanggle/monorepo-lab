import {
  EmployeeListResponseSchema,
  type EmployeeListResponse,
  EmployeeDetailResponseSchema,
  type Employee,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateEmployeeInput,
  type UpdateEmployeeInput,
  EmployeePickerListResponseSchema,
  type EmployeePickerListResponse,
  type EmployeePickerQueryParams,
} from '../types';
import { callErp } from '../erp-client';
import { listQs, detailQs, compact } from './masters-qs';

/**
 * erp masters — employees api (TASK-PC-FE-108 split). List + detail reads +
 * create/update/retire mutations (TASK-PC-FE-048). Behavior-preserving;
 * re-exported through the `erp-masters-api` barrel.
 */

// ---------------------------------------------------------------------------
// employees — list + detail
//   GET /api/erp/masterdata/employees
//   GET /api/erp/masterdata/employees/{id}
//   GET /api/erp/masterdata/employees/picker   (TASK-ERP-BE-045)
// ---------------------------------------------------------------------------

export async function listEmployees(
  params: ErpListQueryParams = {},
): Promise<EmployeeListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees?${listQs(params)}`,
      logPath: '/api/erp/masterdata/employees',
    },
    (json) => EmployeeListResponseSchema.parse(json),
  );
}

/**
 * TASK-ERP-BE-045 — the employee «선택용» picker list
 * (`GET /api/erp/masterdata/employees/picker`, masterdata-api.md v1.2). Only
 * `active` / `page` / `size` are forwarded — the picker has no `asOf` and no
 * per-master filters. Same hardened `callErp` (domain-facing token, error map).
 */
export async function listEmployeePicker(
  params: EmployeePickerQueryParams = {},
): Promise<EmployeePickerListResponse> {
  const { active, page, size } = params;
  return callErp(
    {
      path: `/api/erp/masterdata/employees/picker?${listQs({ active, page, size })}`,
      logPath: '/api/erp/masterdata/employees/picker',
    },
    (json) => EmployeePickerListResponseSchema.parse(json),
  );
}

export async function getEmployeeById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/employees/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return EmployeeDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// employees — WRITE (TASK-PC-FE-048). Same hardened callErp (method/body/
//   idempotencyKey); `reason` rides in the body on retire only (NEVER an
//   X-Operator-Reason header).
// ---------------------------------------------------------------------------

function parseEmployeeData(json: unknown): Employee {
  const env = (json ?? {}) as { data?: unknown };
  return EmployeeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createEmployee(
  input: CreateEmployeeInput,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: '/api/erp/masterdata/employees',
      logPath: '/api/erp/masterdata/employees',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseEmployeeData,
  );
}
export async function updateEmployee(
  id: string,
  input: UpdateEmployeeInput,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/employees/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseEmployeeData,
  );
}
export async function retireEmployee(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/employees/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseEmployeeData,
  );
}
