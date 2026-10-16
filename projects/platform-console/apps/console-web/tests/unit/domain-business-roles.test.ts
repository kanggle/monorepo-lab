/**
 * TASK-PC-FE-342 — `getDomainBusinessRoles` (the write-button gate's roles input, contract
 * § 2.4.3.4): row 12 ∩ the active tenant's subscriptions to the domains the SCREEN gates on.
 *
 * 🔴 The case this pins: `/wms/outbound` «발송 재시도» is served by scm logistics-service. With
 * the WMS-only input (`{ wms }`) an `SCM_LOGISTICS_WRITE` holder's role is DROPPED (domainKey
 * scm is not "subscribed" in that input) and the button would hide for exactly the operators the
 * server serves. The page therefore passes `{ wms, scm }`.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

const session = vi.hoisted(() => ({
  sample: false,
  tenant: 'demo-corp' as string | null,
}));
const rows = vi.hoisted(() => ({
  value: [] as { domainKey: string; role: string }[] | null,
}));

vi.mock('@/shared/lib/session', () => ({
  isSampleVisitor: async () => session.sample,
  getActiveTenant: async () => session.tenant,
}));
vi.mock('@/shared/api/iam-operators-read', () => ({
  getMyDomainRolesOrNull: async () => rows.value,
}));

import { getDomainBusinessRoles } from '@/app/(console)/_lib/domain-business-roles';
import { getWmsBusinessRoles } from '@/app/(console)/wms/_lib/wms-business-roles';
import { canWriteOn } from '@/shared/ui/console-nav-exposure';

const OUTBOUND_AND_LOGISTICS = [
  { domainKey: 'wms', role: 'OUTBOUND_READ' },
  { domainKey: 'scm', role: 'SCM_LOGISTICS_WRITE' },
];

beforeEach(() => {
  session.sample = false;
  session.tenant = 'demo-corp';
  rows.value = OUTBOUND_AND_LOGISTICS;
});

describe('getDomainBusinessRoles', () => {
  it('{ wms, scm } both subscribed → keeps the scm role («발송 재시도» shown)', async () => {
    const r = await getDomainBusinessRoles({ wms: ['demo-corp'], scm: ['demo-corp'] });
    expect(r && [...r].sort()).toEqual(['OUTBOUND_READ', 'SCM_LOGISTICS_WRITE']);
    expect(canWriteOn('/wms/outbound', r, 'retry-dispatch')).toBe(true);
  });

  it('control: the WMS-only input drops the scm role (why the outbound page must pass scm)', async () => {
    const r = await getWmsBusinessRoles(['demo-corp']);
    expect(r && [...r]).toEqual(['OUTBOUND_READ']);
  });

  it('scm not subscribed by the active tenant → the scm role is dropped (server token has none)', async () => {
    const r = await getDomainBusinessRoles({ wms: ['demo-corp'], scm: ['other-corp'] });
    expect(r && [...r]).toEqual(['OUTBOUND_READ']);
  });

  it('fail-open: any unknown registry entry (null) → undefined', async () => {
    expect(await getDomainBusinessRoles({ wms: ['demo-corp'], scm: null })).toBeUndefined();
  });

  it('fail-open: row 12 failed → undefined', async () => {
    rows.value = null;
    expect(await getDomainBusinessRoles({ scm: ['demo-corp'] })).toBeUndefined();
  });

  it('fail-open: no active tenant / sample visitor → undefined', async () => {
    session.tenant = null;
    expect(await getDomainBusinessRoles({ scm: ['demo-corp'] })).toBeUndefined();
    session.tenant = 'demo-corp';
    session.sample = true;
    expect(await getDomainBusinessRoles({ scm: ['demo-corp'] })).toBeUndefined();
  });
});
