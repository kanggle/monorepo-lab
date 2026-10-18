/**
 * TASK-PC-FE-347 — page wiring of the ERP write-button gate (contract § 2.4.3.4).
 *
 * Failure Scenario 3 of the ticket: «선언만 하고 배선을 빠뜨린 화면». The screens' `canWrite` is
 * pinned in `erp-write-buttons.test.tsx`; THIS file pins that the three ERP routes compute it from
 * the real chain — `resolveErpCanWrite` → `getDomainBusinessRoles` (row 12 ∩ the registry's erp
 * tenants) → `canWriteOn(href, roles)` — and hand it to the screen. Only the data sources (session,
 * row 12, registry eligibility) and the screen bodies are stubbed.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactElement } from 'react';

const session = vi.hoisted(() => ({ sample: false, tenant: 'demo-corp' as string | null }));
const row12 = vi.hoisted(() => ({
  value: [] as { domainKey: string; role: string }[] | null,
}));
const eligibility = vi.hoisted(() => ({
  value: {
    eligible: true,
    registryDegraded: false,
    tenants: ['demo-corp'] as string[] | null,
  },
}));

vi.mock('@/shared/lib/session', () => ({
  isSampleVisitor: async () => session.sample,
  getActiveTenant: async () => session.tenant,
}));
vi.mock('@/shared/api/iam-operators-read', () => ({
  getMyDomainRolesOrNull: async () => row12.value,
}));
vi.mock('@/app/(console)/erp/_eligibility', () => ({
  resolveErpEligibility: async () => eligibility.value,
}));
vi.mock('next/navigation', () => ({
  notFound: () => {
    throw new Error('NOT_FOUND');
  },
  redirect: () => {
    throw new Error('REDIRECT');
  },
}));

vi.mock('@/features/erp-ops', () => {
  const ok = async () => ({
    notEligible: false,
    forbidden: false,
    degraded: false,
    departments: null,
    employees: null,
    jobGrades: null,
    costCenters: null,
    businessPartners: null,
    approvalRequests: null,
    approvalInbox: null,
    delegationFacts: null,
  });
  const screen_ =
    () =>
    ({ canWrite, mastersWritable }: { canWrite?: boolean; mastersWritable?: boolean }) => (
      <div
        data-testid="screen"
        data-can-write={String(canWrite)}
        data-masters-writable={String(mastersWritable)}
      />
    );
  return {
    ErpSectionNotice: () => <div data-testid="notice" />,
    getErpMastersState: ok,
    getErpApprovalState: ok,
    getErpDelegationState: ok,
    ErpMastersScreen: screen_(),
    ErpApprovalScreen: screen_(),
    ErpDelegationScreen: screen_(),
  };
});

import MastersPage from '@/app/(console)/erp/masters/page';
import ApprovalPage from '@/app/(console)/erp/approval/page';
import DelegationPage from '@/app/(console)/erp/delegation/page';

type PageFn = (props: { searchParams?: Promise<Record<string, string>> }) => Promise<ReactElement>;
async function renderPage(page: unknown) {
  render(await (page as PageFn)({ searchParams: Promise.resolve({}) }));
}

const R = (...roles: string[]) => roles.map((role) => ({ domainKey: 'erp', role }));
const CATALOG = ['ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE', 'ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE'];
/** `erp-read` system preset. */
const READ_SET = ['ERP_MASTERDATA_READ', 'ERP_APPROVAL_READ'];
const MASTERDATA_SET = ['ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE'];
const APPROVAL_SET = ['ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE'];

const PAGES: { name: string; page: unknown; write: string; other: string }[] = [
  { name: '/erp/masters', page: MastersPage, write: 'ERP_MASTERDATA_WRITE', other: 'ERP_APPROVAL_WRITE' },
  { name: '/erp/approval', page: ApprovalPage, write: 'ERP_APPROVAL_WRITE', other: 'ERP_MASTERDATA_WRITE' },
  { name: '/erp/delegation', page: DelegationPage, write: 'ERP_APPROVAL_WRITE', other: 'ERP_MASTERDATA_WRITE' },
];

const canWriteOfScreen = () => screen.getByTestId('screen').getAttribute('data-can-write');

beforeEach(() => {
  cleanup();
  session.sample = false;
  session.tenant = 'demo-corp';
  row12.value = R(...CATALOG);
  eligibility.value = { eligible: true, registryDegraded: false, tenants: ['demo-corp'] };
});

describe('TASK-PC-FE-347 — ERP screens receive canWrite from the operator\'s area roles', () => {
  describe.each(PAGES)('$name', ({ page, write, other }) => {
    it('`erp-read` set → canWrite=false', async () => {
      row12.value = R(...READ_SET);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('false');
    });

    it('holds the area `*_WRITE` role → canWrite=true', async () => {
      row12.value = R(write);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('demo operator (legacy-compat: all 4 roles) → canWrite=true', async () => {
      row12.value = R(...CATALOG);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('the OTHER ERP area\'s write role only → canWrite=false', async () => {
      row12.value = R(other);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('false');
    });

    it('🔴 the derived entry role `ERP_OPERATOR` alone opens nothing', async () => {
      row12.value = R('ERP_OPERATOR');
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('false');
    });

    it('a role of ANOTHER domain does not count (row 12 ∩ erp subscription)', async () => {
      row12.value = [{ domainKey: 'finance', role: write }];
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('false');
    });

    it('fail-open (AC-3): row 12 failed → canWrite=true', async () => {
      row12.value = null;
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('fail-open (AC-3): no active tenant → canWrite=true', async () => {
      session.tenant = null;
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('fail-open (AC-3): sample visitor → canWrite=true', async () => {
      session.sample = true;
      row12.value = R(...READ_SET);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });

    it('fail-open (AC-3): registry tenants unknown → canWrite=true', async () => {
      eligibility.value = { eligible: true, registryDegraded: false, tenants: null };
      row12.value = R(...READ_SET);
      await renderPage(page);
      expect(canWriteOfScreen()).toBe('true');
    });
  });
});

describe('TASK-PC-FE-347 AC-2 — control groups across the 3 screens', () => {
  async function matrix(roles: string[]) {
    const out: Record<string, string | null> = {};
    for (const { name, page } of PAGES) {
      cleanup();
      row12.value = R(...roles);
      await renderPage(page);
      out[name] = canWriteOfScreen();
    }
    return out;
  }

  it('erp-read → no ERP write button on any of the three', async () => {
    expect(await matrix(READ_SET)).toEqual({
      '/erp/masters': 'false', '/erp/approval': 'false', '/erp/delegation': 'false',
    });
  });

  it('approval set only → approval AND delegation write, masters does not', async () => {
    expect(await matrix(APPROVAL_SET)).toEqual({
      '/erp/masters': 'false', '/erp/approval': 'true', '/erp/delegation': 'true',
    });
  });

  it('masterdata set only → masters writes, approval and delegation do not', async () => {
    expect(await matrix(MASTERDATA_SET)).toEqual({
      '/erp/masters': 'true', '/erp/approval': 'false', '/erp/delegation': 'false',
    });
  });

  it('the masters page no longer hard-codes `mastersWritable` (it passes the computed `canWrite`)', async () => {
    row12.value = R(...READ_SET);
    await renderPage(MastersPage);
    expect(screen.getByTestId('screen').getAttribute('data-masters-writable')).not.toBe('true');
  });
});
