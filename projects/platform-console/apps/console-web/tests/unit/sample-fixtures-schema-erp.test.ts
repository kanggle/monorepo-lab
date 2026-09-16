/**
 * erp domain sample fixtures parse with the SAME production schemas the real
 * screens use, and behave like the real producer over filters/pagination/
 * detail lookups (TASK-PC-FE-285 AC-1 / AC-3 / AC-4 / AC-5).
 *
 * 🔴 AC-1 — a fixture that broke its schema would render as the section
 *    degrade state, indistinguishable from "broken" (task Failure Scenario).
 *    Every assertion below goes THROUGH `sampleResponse` (not the raw seed
 *    arrays) so routing, status codes and the query-string handling are
 *    exercised too, not just the shape of the data.
 * 🔴 AC-3 — the `/erp` overview's counts (5 masters · 결재 대기 · 활성 위임)
 *    MUST equal the count the corresponding LIST screen would show. Every
 *    assertion below calls `sampleResponse` for BOTH the overview's own
 *    query shape (`page=0&size=1`) and the list screen's shape
 *    (`page=0&size=20`) and compares `meta.totalElements` — never the
 *    internal seed arrays directly (that is how TASK-PC-FE-284's
 *    overview-vs-list money mismatch was caught, per this ticket's brief).
 * 🔴 AC-4 — every org-chart department reference and every approval-line
 *    approver must resolve INSIDE the master fixtures via `masterRefLabel`
 *    (never fall back to the raw id). Every reference below is walked and
 *    resolved through a REAL detail lookup via `sampleResponse` (not a
 *    static array find) — the same "through the router" discipline as AC-3.
 * 🔴 Edge case (task body) — erp has NO 404-as-empty special case (unlike
 *    IAM accounts): an unknown id is always a plain 404, for every surface.
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { messageForCode } from '@/shared/api/errors';
import { masterRefLabel, MASTER_REF_UNRESOLVED } from '@/shared/lib/master-ref-label';
import {
  DepartmentListResponseSchema,
  DepartmentDetailResponseSchema,
  EmployeeListResponseSchema,
  EmployeeDetailResponseSchema,
  JobGradeListResponseSchema,
  JobGradeDetailResponseSchema,
  CostCenterListResponseSchema,
  CostCenterDetailResponseSchema,
  BusinessPartnerListResponseSchema,
  BusinessPartnerDetailResponseSchema,
  EmployeeOrgViewListResponseSchema,
  EmployeeOrgViewDetailResponseSchema,
  DelegationFactListResponseSchema,
  DelegationFactDetailResponseSchema,
} from '@/features/erp-ops/api/types';
import {
  ApprovalListResponseSchema,
  ApprovalDetailResponseSchema,
} from '@/features/erp-ops/api/approval-types';
import { DelegationListResponseSchema } from '@/features/erp-ops/api/delegation-types';

function get(surface: string, path: string): Response {
  return sampleResponse({ core: 'flat', surface, method: 'GET', path });
}
const erp = (path: string) => get('erp', path);
const approval = (path: string) => get('erp_approval', path);
const delegation = (path: string) => get('erp_delegation', path);

describe('departments (AC-1 / AC-3)', () => {
  it('list parses with DepartmentListResponseSchema and totalElements matches content length', async () => {
    const res = erp('/api/erp/masterdata/departments?page=0&size=20');
    expect(res.status).toBe(200);
    const parsed = DepartmentListResponseSchema.parse(await res.json());
    expect(parsed.meta.totalElements).toBe(parsed.data.length);
    expect(parsed.meta.totalElements).toBeGreaterThan(0);
  });

  it('AC-3 — page=0&size=1 (the overview shape) reports the SAME totalElements as page=0&size=20 (the list-screen shape)', async () => {
    const overviewShape = DepartmentListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/departments?page=0&size=1')).json(),
    );
    const listShape = DepartmentListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/departments?page=0&size=20')).json(),
    );
    expect(overviewShape.meta.totalElements).toBe(listShape.meta.totalElements);
    expect(overviewShape.data.length).toBe(1);
    expect(listShape.data.length).toBe(listShape.meta.totalElements);
  });

  it('active=false narrows to the RETIRED row only (E2 honesty — retired rows exist, are not hidden by default)', async () => {
    const all = DepartmentListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/departments?page=0&size=20')).json(),
    );
    const retired = DepartmentListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/departments?active=false&page=0&size=20')).json(),
    );
    expect(retired.meta.totalElements).toBeGreaterThan(0);
    expect(retired.meta.totalElements).toBeLessThan(all.meta.totalElements ?? 0);
    expect(retired.data.every((d) => d.status === 'RETIRED')).toBe(true);
  });

  it('AC-3 — a list id resolves in the detail lookup; the root department has no parent', async () => {
    const list = DepartmentListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/departments?page=0&size=20')).json(),
    );
    const root = list.data.find((d) => d.parentId == null)!;
    expect(root).toBeTruthy();
    const detailRes = erp(`/api/erp/masterdata/departments/${root.id}`);
    expect(detailRes.status).toBe(200);
    const detail = DepartmentDetailResponseSchema.parse(await detailRes.json());
    expect(detail.data.id).toBe(root.id);
  });

  it('edge case — an id absent from the fixture is a PLAIN 404 (erp has no 404-as-empty)', async () => {
    const res = erp('/api/erp/masterdata/departments/no-such-department');
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('MASTERDATA_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');
  });
});

describe('employees (AC-1 / AC-3)', () => {
  it('list parses with EmployeeListResponseSchema', async () => {
    const res = EmployeeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/employees?page=0&size=20')).json(),
    );
    expect(res.meta.totalElements).toBe(res.data.length);
    expect(res.meta.totalElements).toBeGreaterThan(0);
  });

  it('departmentId filter narrows the result', async () => {
    const all = EmployeeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/employees?page=0&size=20')).json(),
    );
    const narrowed = EmployeeListResponseSchema.parse(
      await (
        await erp('/api/erp/masterdata/employees?departmentId=dept-sample-0002&page=0&size=20')
      ).json(),
    );
    expect(narrowed.meta.totalElements).toBeGreaterThan(0);
    expect(narrowed.meta.totalElements).toBeLessThan(all.meta.totalElements ?? 0);
    expect(narrowed.data.every((e) => e.departmentId === 'dept-sample-0002')).toBe(true);
  });

  it('AC-3 — a list id resolves in the detail lookup', async () => {
    const list = EmployeeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/employees?page=0&size=20')).json(),
    );
    const first = list.data[0];
    const detail = EmployeeDetailResponseSchema.parse(
      await (await erp(`/api/erp/masterdata/employees/${first.id}`)).json(),
    );
    expect(detail.data.id).toBe(first.id);
  });

  it('edge case — an unknown employee id is a plain 404', async () => {
    const res = erp('/api/erp/masterdata/employees/no-such-employee');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('MASTERDATA_NOT_FOUND');
  });

  it('an unknown / future employmentStatus would parse tolerantly (schema is a free string)', async () => {
    const list = EmployeeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/employees?page=0&size=20')).json(),
    );
    expect(list.data.some((e) => e.employmentStatus === 'SEPARATED')).toBe(true);
    expect(list.data.some((e) => e.departmentId == null)).toBe(true);
  });
});

describe('job-grades (AC-1)', () => {
  it('list parses, ordered by displayOrder asc', async () => {
    const res = JobGradeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/job-grades?page=0&size=20')).json(),
    );
    expect(res.meta.totalElements).toBeGreaterThan(0);
    const orders = res.data.map((g) => g.displayOrder ?? 0);
    expect(orders).toEqual([...orders].sort((a, b) => a - b));
  });

  it('AC-3 — a list id resolves in the detail lookup; unknown id 404s', async () => {
    const list = JobGradeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/job-grades?page=0&size=20')).json(),
    );
    const first = list.data[0];
    const detail = JobGradeDetailResponseSchema.parse(
      await (await erp(`/api/erp/masterdata/job-grades/${first.id}`)).json(),
    );
    expect(detail.data.id).toBe(first.id);
    const res = erp('/api/erp/masterdata/job-grades/no-such-grade');
    expect(res.status).toBe(404);
  });
});

describe('cost-centers (AC-1 / AC-4)', () => {
  it('list parses; departmentId filter narrows', async () => {
    const all = CostCenterListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/cost-centers?page=0&size=20')).json(),
    );
    const narrowed = CostCenterListResponseSchema.parse(
      await (
        await erp('/api/erp/masterdata/cost-centers?departmentId=dept-sample-0002&page=0&size=20')
      ).json(),
    );
    expect(narrowed.meta.totalElements).toBeGreaterThan(0);
    expect(narrowed.meta.totalElements).toBeLessThan(all.meta.totalElements ?? 0);
  });

  it('AC-3 — a list id resolves in the detail lookup; unknown id 404s', async () => {
    const list = CostCenterListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/cost-centers?page=0&size=20')).json(),
    );
    const first = list.data[0];
    const detail = CostCenterDetailResponseSchema.parse(
      await (await erp(`/api/erp/masterdata/cost-centers/${first.id}`)).json(),
    );
    expect(detail.data.id).toBe(first.id);
    const res = erp('/api/erp/masterdata/cost-centers/no-such-cc');
    expect(res.status).toBe(404);
  });
});

describe('business-partners (AC-1)', () => {
  it('list parses; partnerType filter narrows; detail carries paymentTerms', async () => {
    const all = BusinessPartnerListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/business-partners?page=0&size=20')).json(),
    );
    expect(all.meta.totalElements).toBeGreaterThan(0);
    const suppliers = BusinessPartnerListResponseSchema.parse(
      await (
        await erp('/api/erp/masterdata/business-partners?partnerType=SUPPLIER&page=0&size=20')
      ).json(),
    );
    expect(suppliers.meta.totalElements).toBeGreaterThan(0);
    expect(suppliers.meta.totalElements).toBeLessThan(all.meta.totalElements ?? 0);
    expect(suppliers.data.every((p) => p.partnerType === 'SUPPLIER')).toBe(true);

    const first = all.data.find((p) => p.status === 'ACTIVE')!;
    const detail = BusinessPartnerDetailResponseSchema.parse(
      await (await erp(`/api/erp/masterdata/business-partners/${first.id}`)).json(),
    );
    expect(detail.data.paymentTerms).toBeTruthy();
  });

  it('unknown business-partner id is a plain 404', async () => {
    const res = erp('/api/erp/masterdata/business-partners/no-such-bp');
    expect(res.status).toBe(404);
  });
});

describe('read-model — employee org-view (AC-1)', () => {
  it('list parses with EmployeeOrgViewListResponseSchema and carries the E5 warning', async () => {
    const res = await erp('/api/erp/read-model/employees?page=0&size=20');
    const parsed = EmployeeOrgViewListResponseSchema.parse(await res.json());
    expect(parsed.meta.totalElements).toBeGreaterThan(0);
    expect(parsed.meta.warning).toContain('Eventually-consistent');
  });

  it('an unresolved (null) department renders as null, never fabricated', async () => {
    const parsed = EmployeeOrgViewListResponseSchema.parse(
      await (await erp('/api/erp/read-model/employees?page=0&size=20')).json(),
    );
    const noRef = parsed.data.find((e) => e.department === null);
    expect(noRef).toBeTruthy();
    expect(noRef!.costCenter).toBeNull();
    expect(noRef!.jobGrade).toBeNull();
  });

  it('a resolved department path is root→leaf and its nodes resolve in the masterdata detail lookup', async () => {
    const parsed = EmployeeOrgViewListResponseSchema.parse(
      await (await erp('/api/erp/read-model/employees?page=0&size=20')).json(),
    );
    const withDept = parsed.data.find((e) => e.department !== null)!;
    const path = withDept.department!.path;
    expect(path.length).toBeGreaterThanOrEqual(1);
    expect(path[path.length - 1].id).toBe(withDept.department!.id);
    for (const node of path) {
      const detailRes = erp(`/api/erp/masterdata/departments/${node.id}`);
      expect(detailRes.status, node.id).toBe(200);
    }
  });

  it('AC-3 — a list id resolves in the detail lookup; unknown id 404s', async () => {
    const list = EmployeeOrgViewListResponseSchema.parse(
      await (await erp('/api/erp/read-model/employees?page=0&size=20')).json(),
    );
    const first = list.data[0];
    const detail = EmployeeOrgViewDetailResponseSchema.parse(
      await (await erp(`/api/erp/read-model/employees/${first.id}`)).json(),
    );
    expect(detail.data.id).toBe(first.id);
    const res = erp('/api/erp/read-model/employees/no-such-orgview');
    expect(res.status).toBe(404);
  });
});

describe('read-model — delegation facts (AC-1 / AC-3)', () => {
  it('list parses with DelegationFactListResponseSchema and carries the E5 warning', async () => {
    const res = await erp('/api/erp/read-model/delegations?page=0&size=20');
    const parsed = DelegationFactListResponseSchema.parse(await res.json());
    expect(parsed.meta.totalElements).toBeGreaterThan(0);
    expect(parsed.meta.warning).toContain('Eventually-consistent');
  });

  it('AC-3 — status=ACTIVE at page=0&size=1 (overview shape) reports the SAME totalElements as page=0&size=20 (list shape)', async () => {
    const overviewShape = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?status=ACTIVE&page=0&size=1')).json(),
    );
    const listShape = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?status=ACTIVE&page=0&size=20')).json(),
    );
    expect(overviewShape.meta.totalElements).toBe(listShape.meta.totalElements);
    expect(overviewShape.meta.totalElements).toBeGreaterThan(0);
    expect(listShape.data.every((f) => f.status === 'ACTIVE')).toBe(true);
  });

  it('status=REVOKED narrows to a disjoint set from status=ACTIVE', async () => {
    const active = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?status=ACTIVE&page=0&size=20')).json(),
    );
    const revoked = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?status=REVOKED&page=0&size=20')).json(),
    );
    expect(revoked.meta.totalElements).toBeGreaterThan(0);
    const activeIds = new Set(active.data.map((f) => f.grantId));
    expect(revoked.data.every((f) => !activeIds.has(f.grantId))).toBe(true);
  });

  it('AC-3 — a list id resolves in the detail lookup; unknown id 404s', async () => {
    const list = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?page=0&size=20')).json(),
    );
    const first = list.data[0];
    const detail = DelegationFactDetailResponseSchema.parse(
      await (await erp(`/api/erp/read-model/delegations/${first.grantId}`)).json(),
    );
    expect(detail.data.grantId).toBe(first.grantId);
    const res = erp('/api/erp/read-model/delegations/no-such-grant');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('DELEGATION_NOT_FOUND');
  });

  it('BE-018 edge case — a revoke-before-grant row (scope + validFrom absent) parses without throwing', async () => {
    const list = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?page=0&size=20')).json(),
    );
    const revokeBeforeGrant = list.data.find((f) => f.scope === undefined);
    expect(revokeBeforeGrant).toBeTruthy();
    expect(revokeBeforeGrant!.validFrom).toBeUndefined();
  });
});

describe('erp_approval (AC-1)', () => {
  it('requests list parses; status filter narrows', async () => {
    const all = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/requests?page=0&size=20')).json(),
    );
    expect(all.meta.totalElements).toBeGreaterThan(0);
    const submitted = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/requests?status=SUBMITTED&page=0&size=20')).json(),
    );
    expect(submitted.meta.totalElements).toBeGreaterThan(0);
    expect(submitted.meta.totalElements).toBeLessThan(all.meta.totalElements ?? 0);
    expect(submitted.data.every((r) => r.status === 'SUBMITTED')).toBe(true);
  });

  it('a list id resolves in the detail lookup (incl. multi-stage history); unknown id 404s', async () => {
    const list = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/requests?page=0&size=20')).json(),
    );
    const multiStage = list.data.find((r) => (r.totalStages ?? 0) > 1)!;
    const detailRes = approval(`/api/erp/approval/requests/${multiStage.id}`);
    expect(detailRes.status).toBe(200);
    const detail = ApprovalDetailResponseSchema.parse(await detailRes.json());
    expect(detail.data.stages?.length).toBe(2);
    expect(detail.data.history.length).toBeGreaterThan(0);

    const res = approval('/api/erp/approval/requests/no-such-request');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('APPROVAL_REQUEST_NOT_FOUND');
  });

  it('AC-3 — the inbox at page=0&size=1 (overview shape) reports the SAME totalElements as page=0&size=20 (screen shape)', async () => {
    const overviewShape = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/inbox?page=0&size=1')).json(),
    );
    const screenShape = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/inbox?page=0&size=20')).json(),
    );
    expect(overviewShape.meta.totalElements).toBe(screenShape.meta.totalElements);
    expect(overviewShape.meta.totalElements).toBeGreaterThan(0);
  });

  it('the inbox contains only non-terminal requests whose CURRENT stage approver is the caller (not stage-0-only)', async () => {
    const inbox = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/inbox?page=0&size=20')).json(),
    );
    expect(inbox.data.every((r) => r.status === 'SUBMITTED' || r.status === 'IN_REVIEW')).toBe(
      true,
    );
    // The multi-stage request (appr-sample-0002) has ME approved at stage 0
    // but the CURRENT stage belongs to someone else — it must NOT be in the
    // inbox (proves the inbox reads the current approver, not stage 0).
    expect(inbox.data.some((r) => (r.totalStages ?? 0) > 1)).toBe(false);
  });
});

describe('erp_delegation (AC-1)', () => {
  it('unfiltered list parses with DelegationListResponseSchema', async () => {
    const res = DelegationListResponseSchema.parse(
      await (await delegation('/api/erp/approval/delegations')).json(),
    );
    expect(res.meta.totalElements).toBeGreaterThan(0);
  });

  it('role=DELEGATOR and role=DELEGATE each narrow to a disjoint, non-empty subset of the unfiltered list', async () => {
    const all = DelegationListResponseSchema.parse(
      await (await delegation('/api/erp/approval/delegations')).json(),
    );
    const asDelegator = DelegationListResponseSchema.parse(
      await (await delegation('/api/erp/approval/delegations?role=DELEGATOR')).json(),
    );
    const asDelegate = DelegationListResponseSchema.parse(
      await (await delegation('/api/erp/approval/delegations?role=DELEGATE')).json(),
    );
    expect(asDelegator.data.length).toBeGreaterThan(0);
    expect(asDelegate.data.length).toBeGreaterThan(0);
    expect(asDelegator.data.length).toBeLessThan(all.data.length);
    const delegatorIds = new Set(asDelegator.data.map((g) => g.id));
    const delegateIds = new Set(asDelegate.data.map((g) => g.id));
    expect([...delegatorIds].some((id) => delegateIds.has(id))).toBe(false);
  });

  it('a REVOKED grant carries revokedAt/revokedBy; an ACTIVE open-ended grant has validTo absent', async () => {
    const all = DelegationListResponseSchema.parse(
      await (await delegation('/api/erp/approval/delegations')).json(),
    );
    const revoked = all.data.find((g) => g.status === 'REVOKED')!;
    expect(revoked.revokedAt).toBeTruthy();
    expect(revoked.revokedBy).toBeTruthy();
    const openEnded = all.data.find((g) => g.status === 'ACTIVE' && g.validTo === undefined);
    expect(openEnded).toBeTruthy();
  });
});

describe('AC-4 — org-chart references and approval-line approvers resolve inside the master fixtures', () => {
  /** Resolves a master's `{code,name}` via a REAL detail lookup through the
   *  router (not the internal seed array) — mirrors what `useDepartment(id)`
   *  etc. do in the real screens. */
  async function resolveDetail(
    listPath: string,
  ): Promise<{ id: string; code: string; name: string } | null> {
    const res = erp(listPath);
    if (res.status !== 200) return null;
    const body = (await res.json()) as { data: { id: string; code: string; name: string } };
    return body.data;
  }

  it('every department.parentId resolves (or is absent for the root)', async () => {
    const list = DepartmentListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/departments?page=0&size=20')).json(),
    );
    let sawResolved = false;
    for (const d of list.data) {
      if (d.parentId == null) continue;
      const resolved = await resolveDetail(`/api/erp/masterdata/departments/${d.parentId}`);
      expect(masterRefLabel(d.parentId, resolved), d.id).not.toBe(MASTER_REF_UNRESOLVED);
      sawResolved = true;
    }
    expect(sawResolved).toBe(true);
  });

  it('every employee.{departmentId,jobGradeId,costCenterId} resolves when present (incl. the RETIRED department case)', async () => {
    const list = EmployeeListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/employees?page=0&size=20')).json(),
    );
    let sawRetiredDeptRef = false;
    for (const e of list.data) {
      if (e.departmentId) {
        const resolved = await resolveDetail(`/api/erp/masterdata/departments/${e.departmentId}`);
        expect(masterRefLabel(e.departmentId, resolved), e.id).not.toBe(MASTER_REF_UNRESOLVED);
        if (resolved) {
          const detailRes = erp(`/api/erp/masterdata/departments/${e.departmentId}`);
          const detail = DepartmentDetailResponseSchema.parse(await detailRes.json());
          if (detail.data.status === 'RETIRED') sawRetiredDeptRef = true;
        }
      }
      if (e.jobGradeId) {
        const resolved = await resolveDetail(`/api/erp/masterdata/job-grades/${e.jobGradeId}`);
        expect(masterRefLabel(e.jobGradeId, resolved), e.id).not.toBe(MASTER_REF_UNRESOLVED);
      }
      if (e.costCenterId) {
        const resolved = await resolveDetail(`/api/erp/masterdata/cost-centers/${e.costCenterId}`);
        expect(masterRefLabel(e.costCenterId, resolved), e.id).not.toBe(MASTER_REF_UNRESOLVED);
      }
    }
    // E1 headline case (task spec: "employee → retired department").
    expect(sawRetiredDeptRef).toBe(true);
  });

  it('every cost-center.departmentId resolves', async () => {
    const list = CostCenterListResponseSchema.parse(
      await (await erp('/api/erp/masterdata/cost-centers?page=0&size=20')).json(),
    );
    for (const c of list.data) {
      if (!c.departmentId) continue;
      const resolved = await resolveDetail(`/api/erp/masterdata/departments/${c.departmentId}`);
      expect(masterRefLabel(c.departmentId, resolved), c.id).not.toBe(MASTER_REF_UNRESOLVED);
    }
  });

  it('every approval request\'s approverId / submitterId, and every stage approverId, resolves against an employee', async () => {
    const list = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/requests?page=0&size=20')).json(),
    );
    let sawStages = false;
    for (const r of list.data) {
      const approverResolved = await resolveDetail(`/api/erp/masterdata/employees/${r.approverId}`);
      expect(masterRefLabel(r.approverId, approverResolved), r.id).not.toBe(MASTER_REF_UNRESOLVED);
      const submitterResolved = await resolveDetail(
        `/api/erp/masterdata/employees/${r.submitterId}`,
      );
      expect(masterRefLabel(r.submitterId, submitterResolved), r.id).not.toBe(
        MASTER_REF_UNRESOLVED,
      );
      for (const stage of r.stages ?? []) {
        sawStages = true;
        const stageResolved = await resolveDetail(
          `/api/erp/masterdata/employees/${stage.approverId}`,
        );
        expect(masterRefLabel(stage.approverId, stageResolved), r.id).not.toBe(
          MASTER_REF_UNRESOLVED,
        );
      }
    }
    expect(sawStages).toBe(true);
  });

  it('every EMPLOYEE-subject approval request\'s subjectId resolves against an employee; every DEPARTMENT-subject resolves against a department', async () => {
    const list = ApprovalListResponseSchema.parse(
      await (await approval('/api/erp/approval/requests?page=0&size=20')).json(),
    );
    let sawEmployeeSubject = false;
    let sawDepartmentSubject = false;
    for (const r of list.data) {
      const path =
        r.subjectType === 'EMPLOYEE'
          ? `/api/erp/masterdata/employees/${r.subjectId}`
          : `/api/erp/masterdata/departments/${r.subjectId}`;
      const resolved = await resolveDetail(path);
      expect(masterRefLabel(r.subjectId, resolved), r.id).not.toBe(MASTER_REF_UNRESOLVED);
      if (r.subjectType === 'EMPLOYEE') sawEmployeeSubject = true;
      else sawDepartmentSubject = true;
    }
    expect(sawEmployeeSubject).toBe(true);
    expect(sawDepartmentSubject).toBe(true);
  });

  it('every delegation grant/fact\'s delegatorId + delegateId resolve against an employee', async () => {
    const grants = DelegationListResponseSchema.parse(
      await (await delegation('/api/erp/approval/delegations')).json(),
    );
    for (const g of grants.data) {
      const delegator = await resolveDetail(`/api/erp/masterdata/employees/${g.delegatorId}`);
      expect(masterRefLabel(g.delegatorId, delegator), g.id).not.toBe(MASTER_REF_UNRESOLVED);
      const delegate = await resolveDetail(`/api/erp/masterdata/employees/${g.delegateId}`);
      expect(masterRefLabel(g.delegateId, delegate), g.id).not.toBe(MASTER_REF_UNRESOLVED);
    }

    const facts = DelegationFactListResponseSchema.parse(
      await (await erp('/api/erp/read-model/delegations?page=0&size=20')).json(),
    );
    for (const f of facts.data) {
      const delegator = await resolveDetail(`/api/erp/masterdata/employees/${f.delegatorId}`);
      expect(masterRefLabel(f.delegatorId, delegator), f.grantId).not.toBe(MASTER_REF_UNRESOLVED);
      const delegate = await resolveDetail(`/api/erp/masterdata/employees/${f.delegateId}`);
      expect(masterRefLabel(f.delegateId, delegate), f.grantId).not.toBe(MASTER_REF_UNRESOLVED);
    }
  });
});

describe('AC-5 — a representative write is refused with the sample copy', () => {
  it('POST department create → 403 SAMPLE_READ_ONLY → the R1ⓐ copy via messageForCode', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'erp',
      method: 'POST',
      path: '/api/erp/masterdata/departments',
    });
    expect(res.status).toBe(403);
    const body = (await res.json()) as { code: string };
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.code)).toBe(
      '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다',
    );
  });

  it('POST approve transition → 403 SAMPLE_READ_ONLY', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'erp_approval',
      method: 'POST',
      path: '/api/erp/approval/requests/appr-sample-0001/approve',
    });
    expect(res.status).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
  });

  it('POST delegation grant create → 403 SAMPLE_READ_ONLY', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'erp_delegation',
      method: 'POST',
      path: '/api/erp/approval/delegations',
    });
    expect(res.status).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
  });
});
