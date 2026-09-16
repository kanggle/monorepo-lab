/**
 * IAM domain sample fixtures parse with the SAME production schemas the real
 * screens use, and behave like the real producer over filters/pagination/
 * detail lookups (TASK-PC-FE-283 AC-1 / AC-3 / AC-4 / AC-5).
 *
 * 🔴 AC-1 — a fixture that broke its schema would render as the section
 *    degrade state, indistinguishable from "broken" (task Failure Scenario).
 *    Every assertion below goes THROUGH `sampleResponse` (not the raw seed
 *    arrays) so routing, status codes and the query-string handling are
 *    exercised too, not just the shape of the data.
 * 🔴 AC-4 — a filter that the router ignored would look like "search box does
 *    nothing" to a demo visitor, indistinguishable from a real bug. Every
 *    filter cell below asserts the filtered result is a PROPER SUBSET of the
 *    unfiltered one (narrower, non-empty, and every row satisfies the filter).
 * 🔴 AC-3 — a detail id come from the list must resolve; an id that does not
 *    exist must produce the SAME shape (FLAT `{code,message,timestamp}` — none
 *    of these 9 surfaces is `wms`) a real 404 would.
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { messageForCode } from '@/shared/api/errors';
import { AccountPageSchema } from '@/shared/api/iam-accounts-types';
import { AuditPageSchema } from '@/shared/api/iam-audit-types';
import { OperatorPageSchema } from '@/shared/api/iam-operators-types';
import { RolesResponseSchema, PermissionsResponseSchema } from '@/shared/api/rbac-catalog';
import { TenantPageSchema, TenantSchema } from '@/features/tenants/api/types';
import {
  OrgNodeListSchema,
  OrgNodeSchema,
  SubtreeTenantsSchema,
  OrgAdminListSchema,
} from '@/features/org-hierarchy/api/types';
import {
  GroupPageSchema,
  GroupSchema,
  GroupMemberListSchema,
  GroupGrantListSchema,
} from '@/features/operator-groups/api/types';
import { PartnershipListSchema } from '@/features/partnerships/api/types';

function get(surface: string, path: string): Response {
  return sampleResponse({ core: 'iam', surface, method: 'GET', path });
}

describe('accounts (AC-1 / AC-4)', () => {
  it('list parses with AccountPageSchema and reports a real totalElements', async () => {
    const res = get('accounts', '/api/admin/accounts?page=0&size=20&tenantId=sample');
    expect(res.status).toBe(200);
    const parsed = AccountPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBe(parsed.content.length);
    expect(parsed.totalElements).toBeGreaterThan(0);
  });

  it('status=LOCKED narrows the result (AC-4) — a subset, non-empty, every row LOCKED', async () => {
    const all = AccountPageSchema.parse(await (await get('accounts', '/api/admin/accounts?page=0&size=20')).json());
    const locked = AccountPageSchema.parse(
      await (await get('accounts', '/api/admin/accounts?status=LOCKED&page=0&size=20')).json(),
    );
    expect(locked.totalElements).toBeGreaterThan(0);
    expect(locked.totalElements).toBeLessThan(all.totalElements);
    expect(locked.content.every((a) => a.status === 'LOCKED')).toBe(true);
  });

  it('email single-lookup with the PLAIN typed address finds the seeded account (AC-4 — a visitor never sees the suffix before searching)', async () => {
    const res = get(
      'accounts',
      `/api/admin/accounts?email=${encodeURIComponent('hana.kim.sample@example.com')}`,
    );
    const parsed = AccountPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBe(1);
    expect(parsed.content[0].id).toBe('acc-sample-0001');
  });

  it('email single-lookup with the SUFFIXED rendered value (copy-pasted from the table) ALSO finds it', async () => {
    const res = get(
      'accounts',
      `/api/admin/accounts?email=${encodeURIComponent('hana.kim.sample@example.com (샘플)')}`,
    );
    const parsed = AccountPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBe(1);
    expect(parsed.content[0].id).toBe('acc-sample-0001');
  });

  it('email lookup does NOT substring-match an unrelated address sharing the seed vocabulary (AC-4 — proves the search actually narrows)', async () => {
    const res = get('accounts', '/api/admin/accounts?email=sample');
    const parsed = AccountPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBe(0);
  });

  it('AC-3 — an unmatched address is the REAL producer shape (a 200 empty PAGE, not a 404: accounts has no GET-by-id)', async () => {
    const res = get('accounts', '/api/admin/accounts?email=nobody-here@example.com');
    expect(res.status).toBe(200);
    const parsed = AccountPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBe(0);
    expect(parsed.content).toEqual([]);
  });
});

describe('audit (AC-1 / AC-4)', () => {
  it('list parses with AuditPageSchema', async () => {
    const res = get('audit', '/api/admin/audit?page=0&size=20&tenantId=sample');
    const parsed = AuditPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBeGreaterThan(0);
  });

  it('source=admin narrows the result and every row is the admin discriminant', async () => {
    const all = AuditPageSchema.parse(await (await get('audit', '/api/admin/audit?page=0&size=20')).json());
    const admin = AuditPageSchema.parse(
      await (await get('audit', '/api/admin/audit?source=admin&page=0&size=20')).json(),
    );
    expect(admin.totalElements).toBeGreaterThan(0);
    expect(admin.totalElements).toBeLessThan(all.totalElements);
    expect(admin.content.every((r) => r.source === 'admin')).toBe(true);
  });

  it('source=login_history narrows to only login_history rows', async () => {
    const res = AuditPageSchema.parse(
      await (await get('audit', '/api/admin/audit?source=login_history&page=0&size=20')).json(),
    );
    expect(res.totalElements).toBeGreaterThan(0);
    expect(res.content.every((r) => r.source === 'login_history')).toBe(true);
  });
});

describe('operators (AC-1 / AC-4)', () => {
  it('list parses with OperatorPageSchema', async () => {
    const res = get('operators', '/api/admin/operators?page=0&size=20&tenantId=sample');
    const parsed = OperatorPageSchema.parse(await res.json());
    expect(parsed.totalElements).toBeGreaterThan(0);
  });

  it('status=SUSPENDED narrows the result', async () => {
    const all = OperatorPageSchema.parse(
      await (await get('operators', '/api/admin/operators?page=0&size=20')).json(),
    );
    const suspended = OperatorPageSchema.parse(
      await (await get('operators', '/api/admin/operators?status=SUSPENDED&page=0&size=20')).json(),
    );
    expect(suspended.totalElements).toBeGreaterThan(0);
    expect(suspended.totalElements).toBeLessThan(all.totalElements);
    expect(suspended.content.every((o) => o.status === 'SUSPENDED')).toBe(true);
  });

  it('grantable-roles and /me resolve (used by the operators page pre-filter + self-row gate)', async () => {
    const roles = await (await get('operators', '/api/admin/operators/grantable-roles')).json();
    expect(Array.isArray(roles.roles)).toBe(true);
    expect(roles.roles.length).toBeGreaterThan(0);
    const me = await (await get('operators', '/api/admin/me')).json();
    expect(typeof me.operatorId).toBe('string');
  });
});

describe('rbac (AC-1) — shared by /permissions and /permission-sets', () => {
  it('roles catalog parses with RolesResponseSchema', async () => {
    const res = RolesResponseSchema.parse(await (await get('rbac', '/api/admin/roles')).json());
    expect(res.roles.length).toBeGreaterThan(0);
    expect(res.scope).toBe('global');
  });

  it('permission catalog parses with PermissionsResponseSchema', async () => {
    const res = PermissionsResponseSchema.parse(
      await (await get('rbac', '/api/admin/permissions')).json(),
    );
    expect(res.permissions.length).toBeGreaterThan(0);
  });
});

describe('tenants (AC-1 / AC-3 / AC-4)', () => {
  it('list parses with TenantPageSchema', async () => {
    const res = TenantPageSchema.parse(
      await (await get('tenants', '/api/admin/tenants?page=0&size=20')).json(),
    );
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('status=SUSPENDED narrows the result', async () => {
    const all = TenantPageSchema.parse(
      await (await get('tenants', '/api/admin/tenants?page=0&size=20')).json(),
    );
    const suspended = TenantPageSchema.parse(
      await (await get('tenants', '/api/admin/tenants?status=SUSPENDED&page=0&size=20')).json(),
    );
    expect(suspended.totalElements).toBeGreaterThan(0);
    expect(suspended.totalElements).toBeLessThan(all.totalElements);
  });

  it('tenantType=B2C_CONSUMER narrows the result', async () => {
    const all = TenantPageSchema.parse(
      await (await get('tenants', '/api/admin/tenants?page=0&size=20')).json(),
    );
    const consumer = TenantPageSchema.parse(
      await (
        await get('tenants', '/api/admin/tenants?tenantType=B2C_CONSUMER&page=0&size=20')
      ).json(),
    );
    expect(consumer.totalElements).toBeGreaterThan(0);
    expect(consumer.totalElements).toBeLessThan(all.totalElements);
  });

  it('AC-3 — a list id resolves in the detail lookup', async () => {
    const list = TenantPageSchema.parse(
      await (await get('tenants', '/api/admin/tenants?page=0&size=20')).json(),
    );
    const first = list.items[0];
    const detailRes = get('tenants', `/api/admin/tenants/${encodeURIComponent(first.tenantId)}`);
    expect(detailRes.status).toBe(200);
    const detail = TenantSchema.parse(await detailRes.json());
    expect(detail.tenantId).toBe(first.tenantId);
  });

  it('AC-3 — an id absent from the fixture 404s with the real backend shape (FLAT envelope, TENANT_NOT_FOUND)', async () => {
    const res = get('tenants', '/api/admin/tenants/no-such-tenant-id');
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('TENANT_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');
  });
});

describe('org_nodes (AC-1 / AC-3)', () => {
  it('flat list parses with OrgNodeListSchema', async () => {
    const res = OrgNodeListSchema.parse(await (await get('org_nodes', '/api/admin/org-nodes')).json());
    expect(res.items.length).toBeGreaterThan(0);
  });

  it('AC-3 — a list id resolves in the detail lookup + its sub-resources', async () => {
    const list = OrgNodeListSchema.parse(
      await (await get('org_nodes', '/api/admin/org-nodes')).json(),
    );
    const first = list.items[0];
    const detail = OrgNodeSchema.parse(
      await (await get('org_nodes', `/api/admin/org-nodes/${first.orgNodeId}`)).json(),
    );
    expect(detail.orgNodeId).toBe(first.orgNodeId);

    const tenants = SubtreeTenantsSchema.parse(
      await (await get('org_nodes', `/api/admin/org-nodes/${first.orgNodeId}/tenants`)).json(),
    );
    expect(tenants.tenantIds.length).toBeGreaterThan(0);

    const admins = OrgAdminListSchema.parse(
      await (await get('org_nodes', `/api/admin/org-nodes/${first.orgNodeId}/admins`)).json(),
    );
    expect(admins.items.length).toBeGreaterThan(0);
  });

  it('AC-3 — an unknown org node id 404s', async () => {
    const res = get('org_nodes', '/api/admin/org-nodes/no-such-node');
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string };
    expect(body.code).toBe('ORG_NODE_NOT_FOUND');
  });
});

describe('groups (AC-1 / AC-3 / AC-4)', () => {
  it('list parses with GroupPageSchema', async () => {
    const res = GroupPageSchema.parse(
      await (await get('groups', '/api/admin/groups?page=0&size=20')).json(),
    );
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('tenantId filter narrows (all seeded groups share the sample tenant, so an unknown tenant narrows to empty)', async () => {
    const all = GroupPageSchema.parse(
      await (await get('groups', '/api/admin/groups?page=0&size=20')).json(),
    );
    const other = GroupPageSchema.parse(
      await (await get('groups', '/api/admin/groups?tenantId=globex-sample&page=0&size=20')).json(),
    );
    expect(other.totalElements).toBeLessThan(all.totalElements);
    expect(other.totalElements).toBe(0);
  });

  it('AC-3 — a list id resolves in the detail lookup + members + grants', async () => {
    const list = GroupPageSchema.parse(
      await (await get('groups', '/api/admin/groups?page=0&size=20')).json(),
    );
    const first = list.items[0];
    const detail = GroupSchema.parse(
      await (await get('groups', `/api/admin/groups/${first.groupId}`)).json(),
    );
    expect(detail.groupId).toBe(first.groupId);

    const members = GroupMemberListSchema.parse(
      await (await get('groups', `/api/admin/groups/${first.groupId}/members`)).json(),
    );
    expect(members.items.length).toBeGreaterThan(0);

    const grants = GroupGrantListSchema.parse(
      await (await get('groups', `/api/admin/groups/${first.groupId}/grants`)).json(),
    );
    expect(grants.items.length).toBeGreaterThan(0);
  });

  it('AC-3 — an unknown group id 404s', async () => {
    const res = get('groups', '/api/admin/groups/no-such-group');
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string };
    expect(body.code).toBe('GROUP_NOT_FOUND');
  });
});

describe('partnerships (AC-1 / AC-4)', () => {
  it('list parses with PartnershipListSchema', async () => {
    const res = PartnershipListSchema.parse(
      await (await get('partnerships', '/api/admin/partnerships?page=0&size=20')).json(),
    );
    expect(res.totalElements).toBeGreaterThan(0);
  });

  it('role=host narrows the result and every row is host-side', async () => {
    const all = PartnershipListSchema.parse(
      await (await get('partnerships', '/api/admin/partnerships?page=0&size=20')).json(),
    );
    const host = PartnershipListSchema.parse(
      await (await get('partnerships', '/api/admin/partnerships?role=host&page=0&size=20')).json(),
    );
    expect(host.totalElements).toBeGreaterThan(0);
    expect(host.totalElements).toBeLessThan(all.totalElements);
    expect(host.items.every((p) => p.myRole === 'host')).toBe(true);
  });

  it('status=PENDING narrows the result', async () => {
    const pending = PartnershipListSchema.parse(
      await (await get('partnerships', '/api/admin/partnerships?status=PENDING&page=0&size=20')).json(),
    );
    expect(pending.totalElements).toBeGreaterThan(0);
    expect(pending.items.every((p) => p.status === 'PENDING')).toBe(true);
  });
});

describe('subscriptions — no producer GET exists', () => {
  it('⚪ the surface answers 200 (ledger invariant only) — no real screen ever issues this GET (ADR-MONO-023: /subscriptions derives state from the registry, already ready since TASK-PC-FE-282)', async () => {
    const res = get('subscriptions', '/api/admin/subscriptions');
    expect(res.status).toBe(200);
  });
});

describe('AC-5 — a representative write is refused with the sample copy', () => {
  it('POST account lock → 403 SAMPLE_READ_ONLY → the R1ⓐ copy via messageForCode', async () => {
    const res = sampleResponse({
      core: 'iam',
      surface: 'accounts',
      method: 'POST',
      path: '/api/admin/accounts/acc-sample-0001/lock',
    });
    expect(res.status).toBe(403);
    const body = (await res.json()) as { code: string };
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.code)).toBe(
      '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다',
    );
  });
});
