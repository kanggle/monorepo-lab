import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { EditRolesDialog } from '@/features/operators/components/EditRolesDialog';
import type { Operator } from '@/shared/api/admin-api';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
}

const operator: Operator = {
  operatorId: 'op-1',
  email: 'ops@example.com',
  displayName: 'Ops One',
  status: 'ACTIVE',
  roles: ['SUPPORT_READONLY'],
  totpEnrolled: false,
  lastLoginAt: null,
  createdAt: '2026-04-01T00:00:00Z',
};

describe('EditRolesDialog', () => {
  it('sends ASCII-safe X-Operator-Reason header on submit', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          operatorId: 'op-1',
          roles: ['SUPPORT_READONLY', 'SECURITY_ANALYST'],
          auditId: 'a-42',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(wrap(<EditRolesDialog open onOpenChange={onOpenChange} operator={operator} />));

    // Toggle an additional role so submit has a clear user action.
    await user.click(screen.getByLabelText('SECURITY_ANALYST'));
    await user.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const call = fetchMock.mock.calls[0];
    const init = call[1] as RequestInit;
    const headers = new Headers(init.headers);

    // Must be ASCII-safe (ByteString) — no Korean characters.
    expect(headers.get('X-Operator-Reason')).toBe('operator.roles.change');
    expect(init.credentials).toBe('include');
    expect(String(call[0])).toContain('/api/admin/operators/op-1/roles');

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});
