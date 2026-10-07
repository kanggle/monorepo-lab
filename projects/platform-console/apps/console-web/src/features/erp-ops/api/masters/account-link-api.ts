import {
  AccountLinkProposalListResponseSchema,
  type AccountLinkProposalListResponse,
  EmployeeAccountLinkProposalSchema,
  type EmployeeAccountLinkProposal,
  EmployeeDetailResponseSchema,
  type Employee,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from '../types';
import { callErp } from '../erp-client';
import { compact } from './masters-qs';

/**
 * erp employee ↔ IAM account link api (TASK-PC-FE-318 — `TASK-MONO-774` S4).
 * Server-only (rides the hardened `callErp` — domain-facing IAM OIDC token,
 * never `getOperatorToken()`, no `X-Tenant-Id`). Every write carries the
 * console-generated `Idempotency-Key` (the producer requires it on all five —
 * `AccountLinkProposalController` / `EmployeeController`); `reason` rides in
 * the BODY, never an `X-Operator-Reason` header.
 *
 * Producer: `masterdata-api.md` § Employee ↔ IAM account link. Log paths are
 * sanitised route shapes (no ids — the account id is a person identifier).
 */

function pageQs(page?: number, size?: number): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(Math.min(ERP_MAX_PAGE_SIZE, Math.max(1, size ?? ERP_DEFAULT_PAGE_SIZE))),
  );
  return qs.toString();
}

function parseProposal(json: unknown): EmployeeAccountLinkProposal {
  const env = (json ?? {}) as { data?: unknown };
  return EmployeeAccountLinkProposalSchema.parse(env.data);
}

function parseEmployee(json: unknown): Employee {
  const env = (json ?? {}) as { data?: unknown };
  return EmployeeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

// ---------------------------------------------------------------------------
// reads
// ---------------------------------------------------------------------------

/** `GET /account-link-proposals/mine` — PENDING proposals addressed to the
 *  caller's own `sub` (no data scope). */
export async function listMyAccountLinkProposals(
  params: { page?: number; size?: number } = {},
): Promise<AccountLinkProposalListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/account-link-proposals/mine?${pageQs(params.page, params.size)}`,
      logPath: '/api/erp/masterdata/account-link-proposals/mine',
    },
    (json) => AccountLinkProposalListResponseSchema.parse(json),
  );
}

/** `GET /employees/{id}/account-link-proposals` — one employee's history
 *  (every status, newest first; department data scope). */
export async function listEmployeeAccountLinkProposals(
  employeeId: string,
  params: { page?: number; size?: number } = {},
): Promise<AccountLinkProposalListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(employeeId)}/account-link-proposals?${pageQs(params.page, params.size)}`,
      logPath: '/api/erp/masterdata/employees/{id}/account-link-proposals',
    },
    (json) => AccountLinkProposalListResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// writes
// ---------------------------------------------------------------------------

export async function proposeAccountLink(
  employeeId: string,
  input: { accountId: string; reason?: string },
  idempotencyKey: string,
): Promise<EmployeeAccountLinkProposal> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(employeeId)}/account-link-proposals`,
      logPath: '/api/erp/masterdata/employees/{id}/account-link-proposals',
      method: 'POST',
      idempotencyKey,
      body: compact({ accountId: input.accountId, reason: input.reason }),
    },
    parseProposal,
  );
}

/** accept — the ONLY path that writes `employees.account_id`; 200 = the
 *  employee detail envelope (with `accountId`). Body is `{}`. */
export async function acceptAccountLink(
  proposalId: string,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/account-link-proposals/${encodeURIComponent(proposalId)}/accept`,
      logPath: '/api/erp/masterdata/account-link-proposals/{proposalId}/accept',
      method: 'POST',
      idempotencyKey,
      body: {},
    },
    parseEmployee,
  );
}

export async function declineAccountLink(
  proposalId: string,
  reason: string | undefined,
  idempotencyKey: string,
): Promise<EmployeeAccountLinkProposal> {
  return callErp(
    {
      path: `/api/erp/masterdata/account-link-proposals/${encodeURIComponent(proposalId)}/decline`,
      logPath: '/api/erp/masterdata/account-link-proposals/{proposalId}/decline',
      method: 'POST',
      idempotencyKey,
      body: compact({ reason }),
    },
    parseProposal,
  );
}

export async function revokeAccountLink(
  proposalId: string,
  reason: string,
  idempotencyKey: string,
): Promise<EmployeeAccountLinkProposal> {
  return callErp(
    {
      path: `/api/erp/masterdata/account-link-proposals/${encodeURIComponent(proposalId)}/revoke`,
      logPath: '/api/erp/masterdata/account-link-proposals/{proposalId}/revoke',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseProposal,
  );
}

/** unlink — 200 = the employee detail envelope with `accountId` ABSENT. */
export async function unlinkEmployeeAccount(
  employeeId: string,
  reason: string,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(employeeId)}/account-link/unlink`,
      logPath: '/api/erp/masterdata/employees/{id}/account-link/unlink',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseEmployee,
  );
}
