import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorGroupsScreen } from '@/features/operator-groups';
import type { GroupPage } from '@/features/operator-groups/api/types';

/**
 * TASK-PC-FE-338 slice b — group ADR-MONO-083 「업무 권한 세트」 panel
 * (contract § 2.4.3.4 screen 3 / rows 9-11), exercised through
 * `OperatorGroupsScreen` → `GroupDetail` (mirrors the repo's existing
 * convention of testing a detail dialog/panel only through its screen host
 * — there is no isolated `GroupDetail.test.tsx`).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const GROUP = {
  groupId: 'g-1',
  tenantId: 'acme-corp',
  name: '재고팀',
  description: null,
  memberCount: 2,
  grantCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const PAGE: GroupPage = {
  items: [GROUP],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

const GRANTABLE_SETS = {
  items: [
    {
      setId: 'preset-1',
      tenantId: null,
      systemKey: 'wms-inventory-read',
      readOnly: true,
      name: '재고 조회',
      description: null,
      roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
      domains: ['wms'],
      grantCount: 0,
      groupCount: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ],
};

function routedFetch(
  domainSets: unknown[],
  other?: (url: string, init?: RequestInit) => Response,
) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    const u = String(url);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (method === 'GET' && u === '/api/groups/g-1/members') {
      return new Response(JSON.stringify({ items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    if (method === 'GET' && u === '/api/groups/g-1/grants') {
      return new Response(JSON.stringify({ items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    if (method === 'GET' && u === '/api/groups/g-1/domain-permission-sets') {
      return new Response(JSON.stringify({ items: domainSets }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    if (method === 'GET' && u === '/api/domain-permission-sets') {
      return new Response(JSON.stringify(GRANTABLE_SETS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return other ? other(u, init) : new Response(null, { status: 204 });
  });
}

function callsTo(fetchMock: ReturnType<typeof routedFetch>, prefix: string) {
  return fetchMock.mock.calls.filter(([u]) => String(u).startsWith(prefix));
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

async function openDetail(fetchMock: ReturnType<typeof routedFetch>) {
  vi.stubGlobal('fetch', fetchMock);
  const user = userEvent.setup();
  render(<OperatorGroupsScreen initial={PAGE} grantableRoles={null} />, {
    wrapper: wrapper(),
  });
  await user.click(screen.getByTestId('groups-row-select-g-1'));
  await screen.findByTestId('group-detail');
  return user;
}

describe('group 업무 권한 세트 panel — add', () => {
  it('sends setId + reason + Idempotency-Key, and shows the fan-out count on success', async () => {
    const fetchMock = routedFetch(
      [],
      (u) =>
        u === '/api/groups/g-1/domain-permission-sets'
          ? new Response(
              JSON.stringify({
                item: {
                  setId: 'preset-1',
                  name: '재고 조회',
                  systemKey: 'wms-inventory-read',
                  grantedAt: '2026-10-14T00:00:00Z',
                  grantedBy: 'op-9',
                },
                fannedOutRows: 2,
              }),
              { status: 201, headers: { 'Content-Type': 'application/json' } },
            )
          : new Response(null, { status: 204 }),
    );
    const user = await openDetail(fetchMock);

    await user.click(screen.getByTestId('group-domain-set-add'));
    await screen.findByTestId('group-domain-set-select');
    await user.selectOptions(
      screen.getByTestId('group-domain-set-select'),
      'preset-1',
    );
    await user.click(screen.getByTestId('group-domain-set-next'));

    const dialog = await screen.findByTestId('group-reason-dialog');
    await user.type(within(dialog).getByTestId('group-reason-input'), 'team grant');
    await user.click(within(dialog).getByTestId('group-reason-submit'));

    await waitFor(() =>
      expect(
        callsTo(fetchMock, '/api/groups/g-1/domain-permission-sets').filter(
          ([, init]) => (init as RequestInit | undefined)?.method === 'POST',
        ),
      ).toHaveLength(1),
    );
    const postCalls = callsTo(
      fetchMock,
      '/api/groups/g-1/domain-permission-sets',
    ).filter(([, init]) => (init as RequestInit | undefined)?.method === 'POST');
    const [, init] = postCalls[0];
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.setId).toBe('preset-1');
    expect(body.reason).toBe('team grant');
    expect(typeof body.idempotencyKey).toBe('string');
    expect(body.idempotencyKey.length).toBeGreaterThan(0);

    await waitFor(() =>
      expect(
        screen.getByTestId('group-domain-set-fanout-notice'),
      ).toHaveTextContent('멤버 2명에게 배정했습니다'),
    );
  });
});

describe('group 업무 권한 세트 panel — remove', () => {
  it('DELETEs with the reason only, no idempotencyKey', async () => {
    const fetchMock = routedFetch([
      {
        setId: 'set-9',
        name: '배정됨',
        systemKey: null,
        grantedAt: '2026-10-01T00:00:00Z',
        grantedBy: 'op-9',
      },
    ]);
    const user = await openDetail(fetchMock);

    await screen.findByTestId('group-domain-set-row-set-9');
    await user.click(screen.getByTestId('group-domain-set-remove-set-9'));
    const dialog = await screen.findByTestId('group-reason-dialog');
    await user.type(within(dialog).getByTestId('group-reason-input'), 'revoke it');
    await user.click(within(dialog).getByTestId('group-reason-submit'));

    await waitFor(() =>
      expect(
        callsTo(fetchMock, '/api/groups/g-1/domain-permission-sets/set-9'),
      ).toHaveLength(1),
    );
    const [, init] = callsTo(
      fetchMock,
      '/api/groups/g-1/domain-permission-sets/set-9',
    )[0];
    expect((init as RequestInit).method).toBe('DELETE');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ reason: 'revoke it' });
  });
});

describe('group member-add hint when the group carries domain sets', () => {
  it('shows the domain_access.manage hint in both the panel and the add-member dialog', async () => {
    const fetchMock = routedFetch([
      {
        setId: 'set-9',
        name: '배정됨',
        systemKey: null,
        grantedAt: '2026-10-01T00:00:00Z',
        grantedBy: 'op-9',
      },
    ]);
    const user = await openDetail(fetchMock);

    await screen.findByTestId('group-member-domain-sets-hint');
    await user.click(screen.getByTestId('group-member-add'));
    await screen.findByTestId('group-member-domain-sets-dialog-hint');
  });

  it('no hint when the group carries no domain sets', async () => {
    const fetchMock = routedFetch([]);
    await openDetail(fetchMock);
    expect(screen.queryByTestId('group-member-domain-sets-hint')).toBeNull();
  });

  it('carries the «적용 예정» status-honesty note (P3 not deployed)', async () => {
    const fetchMock = routedFetch([]);
    await openDetail(fetchMock);
    expect(screen.getByTestId('group-domain-sets-applies-later-note')).toHaveTextContent('적용 예정');
  });
});
