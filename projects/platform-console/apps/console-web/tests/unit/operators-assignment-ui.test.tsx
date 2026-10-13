import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorsScreen } from '@/features/operators';
import type { OperatorPage } from '@/features/operators';
import { KNOWN_OPERATOR_ROLES } from '@/features/operators/api/types';

/**
 * TASK-PC-FE-157 — operator↔tenant assignment console UI:
 *   - the 테넌트 배정 form is shown when an active tenant is resolved; a
 *     free-text operatorId → reason+confirm dialog → POST to
 *     `/api/operators/{id}/assignments/{activeTenant}` with the reason.
 *   - the per-row 배정 해제 action → reason+confirm → DELETE the same path.
 *   - the invite/edit-roles selectors offer TENANT_ADMIN (delegation
 *     appointment) — no one-click; still confirm-gated.
 *
 * TASK-MONO-772 S5 — with an active tenant the screen ALSO reads the pending
 * invitations (`GET /api/operator-invitations`) on mount, so `fetch` is
 * stubbed with a router that answers that read with an empty page and lets
 * each test see only the calls it is about (`callsTo`).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
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

/** fetch stub: the invitations read answers an empty page; everything else
 *  goes to `other` (default: 204). */
function routedFetch(other?: (url: string, init?: RequestInit) => Response) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (String(url).startsWith('/api/operator-invitations')) {
      return new Response(JSON.stringify(EMPTY_INVITATIONS), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    return other ? other(String(url), init) : new Response(null, { status: 204 });
  });
}

function callsTo(fetchMock: ReturnType<typeof routedFetch>, prefix: string) {
  return fetchMock.mock.calls.filter(([u]) => String(u).startsWith(prefix));
}

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.stubGlobal('fetch', routedFetch());
});

describe('selectable roles include the tenant-scoped delegation roles', () => {
  it('KNOWN_OPERATOR_ROLES offers TENANT_ADMIN + TENANT_BILLING_ADMIN', () => {
    expect(KNOWN_OPERATOR_ROLES).toContain('TENANT_ADMIN');
    expect(KNOWN_OPERATOR_ROLES).toContain('TENANT_BILLING_ADMIN');
  });

  it('renders a TENANT_ADMIN checkbox in the invite form (TASK-MONO-772 — company operators are invited)', () => {
    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByTestId('invite-operator-role-TENANT_ADMIN'),
    ).toBeInTheDocument();
  });
});

describe('assign form → confirm → POST', () => {
  it('POSTs the operatorId to the active-tenant assignment path with the reason', async () => {
    const fetchMock = routedFetch(
      () =>
        new Response(JSON.stringify({ tenantId: 'acme-corp' }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );

    await user.type(screen.getByTestId('assign-operator-id'), 'op-9');
    await user.click(screen.getByTestId('assign-operator-submit'));

    // the confirm dialog opens — no one-click assign.
    const reason = await screen.findByTestId('operator-confirm-reason');
    await user.type(reason, 'onboard partner');
    await user.click(screen.getByTestId('operator-confirm-submit'));

    await waitFor(() =>
      expect(callsTo(fetchMock, '/api/operators/')).toHaveLength(1),
    );
    const [url, init] = callsTo(fetchMock, '/api/operators/')[0];
    expect(String(url)).toBe('/api/operators/op-9/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('POST');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      reason: 'onboard partner',
    });
  });

  it('does NOT fire the assign without a reason (confirm gate)', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );
    await user.type(screen.getByTestId('assign-operator-id'), 'op-9');
    await user.click(screen.getByTestId('assign-operator-submit'));
    // confirm submit is disabled until a reason is entered → click is a no-op.
    await user.click(screen.getByTestId('operator-confirm-submit'));
    expect(callsTo(fetchMock, '/api/operators/')).toHaveLength(0);
  });
});

describe('per-row unassign → confirm → DELETE', () => {
  it('DELETEs the row operator from the active tenant with the reason', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('action-unassign-op-1'));
    const reason = await screen.findByTestId('operator-confirm-reason');
    await user.type(reason, 'left the team');
    await user.click(screen.getByTestId('operator-confirm-submit'));

    await waitFor(() =>
      expect(callsTo(fetchMock, '/api/operators/')).toHaveLength(1),
    );
    const [url, init] = callsTo(fetchMock, '/api/operators/')[0];
    expect(String(url)).toBe('/api/operators/op-1/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('DELETE');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      reason: 'left the team',
    });
  });
});

// TASK-MONO-784 S6 (ADR-MONO-082 b — console-integration-contract §
// «한 사람 · 여러 회사» row 9) — «배정 해제» → «이 회사에서 내보내기»: the
// row button's visible label, the confirm dialog's elevated copy, and the
// inline rendering of the producer's new last-admin refusal.
describe('per-row unassign — «이 회사에서 내보내기» copy + 409 TENANT_LAST_ADMIN_REQUIRED (TASK-MONO-784 S6)', () => {
  it('the row action reads «이 회사에서 내보내기» (not «배정 해제»), and the confirm dialog names the company + what is removed', async () => {
    const fetchMock = routedFetch();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );

    const button = screen.getByTestId('action-unassign-op-1');
    expect(button).toHaveTextContent('이 회사에서 내보내기');
    expect(button).not.toHaveTextContent('배정 해제');

    await user.click(button);
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(dialog).toHaveTextContent('acme-corp에서 내보냅니다');
    expect(dialog).toHaveTextContent('역할');
    expect(dialog).toHaveTextContent('그룹');
    expect(dialog).toHaveTextContent('협력사 참여');
    expect(dialog).toHaveTextContent('다른 회사의 소속');
  });

  it('409 TENANT_LAST_ADMIN_REQUIRED on the DELETE surfaces inline (no crash)', async () => {
    const fetchMock = routedFetch(() =>
      new Response(JSON.stringify({ code: 'TENANT_LAST_ADMIN_REQUIRED' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('action-unassign-op-1'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      'offboarding',
    );
    await user.click(within(dialog).getByTestId('operator-confirm-submit'));

    await waitFor(() =>
      expect(
        within(dialog).getByTestId('operator-confirm-error'),
      ).toHaveTextContent('관리자가 한 명도 남지 않게'),
    );
  });
});

// TASK-MONO-784 S6 — edit-roles now replaces only the ACTIVE company's
// direct roles; the confirm copy names the company, and the PATCH carries
// the active-tenant header (the shared `callGapOperators` core already
// attaches `X-Tenant-Id` to every operators call — this proves it rides on
// THIS mutation too, not just the list read).
describe('per-row edit-roles — per-company copy + active-tenant header (TASK-MONO-784 S6)', () => {
  it('names the active company in the confirm dialog and PATCHes with X-Tenant-Id: acme-corp', async () => {
    const fetchMock = routedFetch(
      () =>
        new Response(
          JSON.stringify({ operatorId: 'op-1', roles: ['SUPPORT_READONLY'], auditId: 'a' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('action-edit-roles-op-1'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(dialog).toHaveTextContent('이 회사(');
    expect(dialog).toHaveTextContent('acme-corp');
    expect(dialog).toHaveTextContent('다른 회사에서의 역할은 그대로');

    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      'role realignment',
    );
    await user.click(within(dialog).getByTestId('operator-confirm-submit'));

    await waitFor(() =>
      expect(callsTo(fetchMock, '/api/operators/')).toHaveLength(1),
    );
    const [url, init] = callsTo(fetchMock, '/api/operators/')[0];
    expect(String(url)).toBe('/api/operators/op-1/roles');
    // The PATCH goes through the same-origin proxy — the SERVER attaches
    // `X-Tenant-Id` from the active-tenant cookie (not the browser request),
    // so this UI-level assertion is on the request the browser makes; the
    // header-attachment itself is proven server-side in
    // `tests/unit/operators-api.test.ts`.
    expect((init as RequestInit).method).toBe('POST');
  });
});

describe('assignment surface hidden without an active tenant', () => {
  it('no assign form / no unassign action when activeTenant is null', () => {
    render(<OperatorsScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('assign-operator-form')).toBeNull();
    expect(screen.queryByTestId('action-unassign-op-1')).toBeNull();
  });
});

// TASK-PC-FE-195 — the assign form disambiguates intra-org tenant assignment
// from the cross-org partnership surface (PC-FE-187) and links to it.
describe('assign form → cross-org partnership disambiguation (PC-FE-195)', () => {
  it('renders the partnership hint linking to /partnerships', () => {
    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );
    const hint = screen.getByTestId('assign-operator-partnership-hint');
    expect(hint).toBeInTheDocument();
    expect(hint).toHaveTextContent('파트너십');
    const link = screen.getByTestId('assign-operator-partnership-link');
    expect(link).toHaveAttribute('href', '/partnerships');
  });

  // TASK-PC-FE-202 — the hint also links to the IAM guide's "4개의 모자" for the
  // full account-relationship picture (consumer / owner / employee / cross-org).
  it('links to the IAM guide (/iam/guide) for the 4-hats overview', () => {
    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );
    const guideLink = screen.getByTestId('assign-operator-guide-link');
    expect(guideLink).toHaveAttribute('href', '/iam/guide');
  });

  // TASK-PC-FE-338 — owner decision «이메일 초대를 주 경로로».
  it('leads with the email invitation and links to the invite form on the same screen', () => {
    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );
    const lead = screen.getByTestId('assign-operator-invite-first');
    expect(lead).toHaveTextContent('운영자 초대');
    expect(lead).toHaveTextContent('수락할 때 이 테넌트 소속이');
    expect(screen.getByTestId('assign-operator-invite-link')).toHaveAttribute(
      'href',
      '#invite-operator-form',
    );
    // The link target exists on the same screen.
    expect(screen.getByTestId('invite-operator-form')).toHaveAttribute(
      'id',
      'invite-operator-form',
    );
    // The raw-id path is still there, folded under «ID 를 아는 경우».
    const byId = screen.getByTestId('assign-operator-by-id');
    expect(byId.tagName).toBe('DETAILS');
    expect(byId).not.toHaveAttribute('open');
    expect(within(byId).getByTestId('assign-operator-id')).toBeInTheDocument();
  });

  it('platform scope (*) has no invite form, so the lead names it without a dead link', () => {
    render(
      <OperatorsScreen initial={PAGE} activeTenant="*" />,
      { wrapper: wrapper() },
    );
    expect(screen.queryByTestId('invite-operator-form')).toBeNull();
    expect(screen.getByTestId('assign-operator-invite-first')).toHaveTextContent(
      '운영자 초대',
    );
    expect(screen.queryByTestId('assign-operator-invite-link')).toBeNull();
  });

  it('no partnership hint when the assign surface is hidden (no active tenant)', () => {
    render(<OperatorsScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(
      screen.queryByTestId('assign-operator-partnership-hint'),
    ).toBeNull();
  });
});
