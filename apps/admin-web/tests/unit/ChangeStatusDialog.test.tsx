import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { ChangeStatusDialog } from '@/features/operators/components/ChangeStatusDialog';
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
  operatorId: 'op-7',
  email: 'ops7@example.com',
  displayName: 'Ops Seven',
  status: 'ACTIVE',
  roles: ['SUPPORT_READONLY'],
  totpEnrolled: false,
  lastLoginAt: null,
  createdAt: '2026-04-01T00:00:00Z',
};

describe('ChangeStatusDialog', () => {
  it('sends ASCII-safe X-Operator-Reason header even when user enters Korean reason', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          operatorId: 'op-7',
          previousStatus: 'ACTIVE',
          currentStatus: 'SUSPENDED',
          auditId: 'a-99',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(
      wrap(
        <ChangeStatusDialog
          open
          onOpenChange={onOpenChange}
          operator={operator}
          nextStatus="SUSPENDED"
        />,
      ),
    );

    // User enters Korean reason — must not leak into X-Operator-Reason header.
    await user.type(screen.getByLabelText('사유 (필수)'), '계정 오남용 신고');
    await user.click(screen.getByRole('button', { name: '정지' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const call = fetchMock.mock.calls[0];
    const init = call[1] as RequestInit;
    const headers = new Headers(init.headers);

    // Header must be the fixed ASCII constant, not the Korean user input.
    expect(headers.get('X-Operator-Reason')).toBe('operator.status.change');
    // Assert ASCII-only (ByteString-safe): every char code <= 0xFF.
    const headerValue = headers.get('X-Operator-Reason') ?? '';
    for (let i = 0; i < headerValue.length; i += 1) {
      expect(headerValue.charCodeAt(i)).toBeLessThanOrEqual(0x7f);
    }
    expect(String(call[0])).toContain('/api/admin/operators/op-7/status');

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});
