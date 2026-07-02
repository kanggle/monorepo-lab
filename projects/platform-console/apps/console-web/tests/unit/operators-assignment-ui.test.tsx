import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
 *   - the create/edit-roles selectors offer TENANT_ADMIN (delegation
 *     appointment) — no one-click; still confirm-gated.
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

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('selectable roles include the tenant-scoped delegation roles', () => {
  it('KNOWN_OPERATOR_ROLES offers TENANT_ADMIN + TENANT_BILLING_ADMIN', () => {
    expect(KNOWN_OPERATOR_ROLES).toContain('TENANT_ADMIN');
    expect(KNOWN_OPERATOR_ROLES).toContain('TENANT_BILLING_ADMIN');
  });

  it('renders a TENANT_ADMIN checkbox in the create form', () => {
    render(
      <OperatorsScreen initial={PAGE} activeTenant="acme-corp" />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByTestId('create-operator-role-TENANT_ADMIN'),
    ).toBeInTheDocument();
  });
});

describe('assign form → confirm → POST', () => {
  it('POSTs the operatorId to the active-tenant assignment path with the reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
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

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('/api/operators/op-9/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('POST');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      reason: 'onboard partner',
    });
  });

  it('does NOT fire the assign without a reason (confirm gate)', async () => {
    const fetchMock = vi.fn();
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
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('per-row unassign → confirm → DELETE', () => {
  it('DELETEs the row operator from the active tenant with the reason', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
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

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('/api/operators/op-1/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('DELETE');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      reason: 'left the team',
    });
  });
});

describe('assignment surface hidden without an active tenant', () => {
  it('no assign form / no unassign action when activeTenant is null', () => {
    render(<OperatorsScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('assign-operator-form')).toBeNull();
    expect(screen.queryByTestId('action-unassign-op-1')).toBeNull();
  });
});
