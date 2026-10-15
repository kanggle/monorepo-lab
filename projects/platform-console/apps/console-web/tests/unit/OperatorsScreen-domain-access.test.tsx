import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorsScreen } from '@/features/operators';
import type { OperatorPage } from '@/features/operators';

/**
 * TASK-PC-FE-338 slice b — per-operator ADR-MONO-083 「업무 권한」 dialog
 * (contract § 2.4.3.4 screen 2 / rows 6-8), mounted through `OperatorsScreen`
 * (the `OperatorDomainAccessDialog` has no isolated test file — same
 * convention as the pre-existing `OrgScopeDialog`, exercised only through
 * its screen host).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const PAGE: OperatorPage = {
  content: [
    {
      operatorId: 'op-1',
      email: 'alice@x.com',
      displayName: 'Alice',
      status: 'ACTIVE',
      roles: ['SUPPORT_LOCK'],
      createdAt: '2026-01-01',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};

const EMPTY_INVITATIONS = {
  content: [],
  totalElements: 0,
  page: 0,
  size: 20,
  totalPages: 0,
};

const DOMAIN_ACCESS = {
  operatorId: 'op-1',
  tenantId: 'acme-corp',
  roles: [
    { domainKey: 'wms', role: 'INVENTORY_READ' },
    { domainKey: 'scm', role: 'SCM_OPERATOR' },
  ],
  sets: [
    {
      setId: 's1',
      name: '재고팀(직접)',
      systemKey: null,
      sources: [
        {
          source: 'DIRECT',
          tenantId: 'acme-corp',
          grantedAt: '2026-10-01T00:00:00Z',
          grantedBy: 'op-9',
        },
      ],
    },
    {
      setId: 's2',
      name: '그룹전용',
      systemKey: null,
      sources: [
        {
          source: 'GROUP',
          groupId: 'g1',
          groupName: '재고그룹',
          tenantId: 'acme-corp',
          grantedAt: '2026-10-01T00:00:00Z',
          grantedBy: 'op-9',
        },
      ],
    },
    {
      setId: 's3',
      name: '복합(직접+그룹)',
      systemKey: null,
      sources: [
        {
          source: 'DIRECT',
          tenantId: 'acme-corp',
          grantedAt: '2026-10-01T00:00:00Z',
          grantedBy: 'op-9',
        },
        {
          source: 'GROUP',
          groupId: 'g2',
          groupName: '혼합그룹',
          tenantId: 'acme-corp',
          grantedAt: '2026-10-01T00:00:00Z',
          grantedBy: 'op-9',
        },
      ],
    },
    {
      setId: 's4',
      name: '플랫폼 전체',
      systemKey: null,
      sources: [
        {
          source: 'DIRECT',
          tenantId: '*',
          grantedAt: '2026-10-01T00:00:00Z',
          grantedBy: 'op-9',
        },
      ],
    },
  ],
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

/** fetch router: invitations / domain-access / grantable-sets each answer
 *  their own fixture; a mutation (POST/DELETE) goes to `other`. */
function routedFetch(other?: (url: string, init?: RequestInit) => Response) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    const u = String(url);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (u.startsWith('/api/operator-invitations')) {
      return new Response(JSON.stringify(EMPTY_INVITATIONS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    if (method === 'GET' && u === '/api/operators/op-1/domain-access') {
      return new Response(JSON.stringify(DOMAIN_ACCESS), {
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
  vi.stubGlobal('fetch', routedFetch());
});

async function openDialog(user: ReturnType<typeof userEvent.setup>) {
  render(
    <OperatorsScreen initial={PAGE} activeTenant="acme-corp" subscribedDomains={['wms']} />,
    { wrapper: wrapper() },
  );
  await user.click(screen.getByTestId('action-domain-access-op-1'));
  await screen.findByTestId('operator-domain-access-dialog');
}

describe('OperatorDomainAccessDialog — sources + revoke visibility', () => {
  it('DIRECT-only set shows the revoke button', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    expect(
      screen.getByTestId('operator-domain-access-set-s1-revoke'),
    ).toBeInTheDocument();
  });

  it('GROUP-only set has NO revoke button, only a link to the group', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    expect(
      screen.queryByTestId('operator-domain-access-set-s2-revoke'),
    ).toBeNull();
    const link = screen.getByTestId('operator-domain-access-set-s2-group-link-0');
    expect(link).toHaveAttribute('href', '/operator-groups');
    expect(link).toHaveTextContent('재고그룹');
  });

  it('DIRECT+GROUP set shows the revoke button, and its confirm says the group copy stays', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    await user.click(screen.getByTestId('operator-domain-access-set-s3-revoke'));
    const dialog = await screen.findByTestId('operator-domain-access-reason-dialog');
    expect(dialog).toHaveTextContent('혼합그룹');
    expect(dialog).toHaveTextContent('그룹에서 빼야 사라집니다');
  });

  it('a `*` tenant row is marked «모든 테넌트»', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    expect(
      screen.getByTestId('operator-domain-access-set-s4-source-0-all-tenants'),
    ).toHaveTextContent('모든 테넌트');
  });

  // TASK-PC-FE-338 c-2 PR 1 — post-P3 latency (TTL 300 s + 60 s resource-server skew, BE-632 AC-6).
  it('latency copy says «최대 약 6분» (not 30분), never «즉시»', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    await user.click(screen.getByTestId('operator-domain-access-set-s1-revoke'));
    const dialog = await screen.findByTestId('operator-domain-access-reason-dialog');
    expect(dialog).toHaveTextContent('최대 약 6분');
    expect(dialog).not.toHaveTextContent('30분');
    expect(dialog).not.toHaveTextContent('즉시');
  });

  it('carries the post-P3 effect note, no «적용 예정»', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    const note = screen.getByTestId('operator-domain-access-effect-note');
    expect(note).toHaveTextContent('구독한 도메인');
    expect(screen.queryByTestId('operator-domain-access-applies-later-note')).toBeNull();
    expect(screen.getByTestId('operator-domain-access-dialog')).not.toHaveTextContent('적용 예정');
  });
});

describe('OperatorDomainAccessDialog — effective roles grouped by domain', () => {
  it('marks a role whose domain the active tenant has not subscribed to', async () => {
    const user = userEvent.setup();
    await openDialog(user);
    expect(
      screen.getByTestId('operator-domain-access-role-domain-scm-unsubscribed'),
    ).toHaveTextContent('구독 없음');
    expect(
      screen.queryByTestId('operator-domain-access-role-domain-wms-unsubscribed'),
    ).toBeNull();
  });
});

describe('OperatorDomainAccessDialog — revoke fires DELETE with reason only', () => {
  it('DELETEs with the reason in the body and no idempotencyKey', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openDialog(user);

    await user.click(screen.getByTestId('operator-domain-access-set-s1-revoke'));
    const dialog = await screen.findByTestId('operator-domain-access-reason-dialog');
    await user.type(
      within(dialog).getByTestId('operator-domain-access-reason-input'),
      'no longer needed',
    );
    await user.click(within(dialog).getByTestId('operator-domain-access-reason-submit'));

    await waitFor(() =>
      expect(
        callsTo(fetchMock, '/api/operators/op-1/domain-permission-sets/s1'),
      ).toHaveLength(1),
    );
    const [, init] = callsTo(
      fetchMock,
      '/api/operators/op-1/domain-permission-sets/s1',
    )[0];
    expect((init as RequestInit).method).toBe('DELETE');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ reason: 'no longer needed' });
    expect(body.idempotencyKey).toBeUndefined();
  });
});

describe('OperatorDomainAccessDialog — grant', () => {
  it('sends setId + reason, and NO Idempotency-Key, on grant', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await openDialog(user);

    await user.click(screen.getByTestId('operator-domain-access-add'));
    await screen.findByTestId('operator-domain-access-grant-select');
    await user.selectOptions(
      screen.getByTestId('operator-domain-access-grant-select'),
      'preset-1',
    );
    await user.click(screen.getByTestId('operator-domain-access-grant-next'));

    const dialog = await screen.findByTestId('operator-domain-access-reason-dialog');
    await user.type(
      within(dialog).getByTestId('operator-domain-access-reason-input'),
      'onboarding',
    );
    await user.click(within(dialog).getByTestId('operator-domain-access-reason-submit'));

    await waitFor(() =>
      expect(
        callsTo(fetchMock, '/api/operators/op-1/domain-permission-sets').filter(
          ([, init]) => (init as RequestInit | undefined)?.method === 'POST',
        ),
      ).toHaveLength(1),
    );
    const postCalls = callsTo(
      fetchMock,
      '/api/operators/op-1/domain-permission-sets',
    ).filter(([, init]) => (init as RequestInit | undefined)?.method === 'POST');
    const [, init] = postCalls[0];
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ setId: 'preset-1', reason: 'onboarding' });
    expect((init as RequestInit).headers as Headers | undefined).toBeTruthy();
  });

  it('self-grant (target = caller) shows the self-assignment confirm copy', async () => {
    const user = userEvent.setup();
    render(
      <OperatorsScreen
        initial={PAGE}
        activeTenant="acme-corp"
        selfOperatorId="op-1"
        subscribedDomains={['wms']}
      />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('action-domain-access-op-1'));
    await screen.findByTestId('operator-domain-access-dialog');

    await user.click(screen.getByTestId('operator-domain-access-add'));
    await screen.findByTestId('operator-domain-access-grant-select');
    await user.selectOptions(
      screen.getByTestId('operator-domain-access-grant-select'),
      'preset-1',
    );
    await user.click(screen.getByTestId('operator-domain-access-grant-next'));

    const dialog = await screen.findByTestId('operator-domain-access-reason-dialog');
    expect(dialog).toHaveTextContent('자기 자신에게 업무 권한을 줍니다');
  });
});
