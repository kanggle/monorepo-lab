import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { LockDialog } from '@/features/accounts/components/LockDialog';
import { runAxe } from '../a11y/axe-helper';

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <ToastProvider>{ui}</ToastProvider>
    </QueryClientProvider>
  );
}

describe('LockDialog', () => {
  it('has no axe violations when open', async () => {
    const { container } = render(wrap(<LockDialog open onOpenChange={() => {}} accountId="acc-1" />));
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });

  it('requires a reason before submitting', async () => {
    const user = userEvent.setup();
    render(wrap(<LockDialog open onOpenChange={() => {}} accountId="acc-1" />));
    await user.click(screen.getByRole('button', { name: '잠금' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('sends X-Operator-Reason and Idempotency-Key on submit', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          accountId: 'acc-1',
          previousStatus: 'ACTIVE',
          currentStatus: 'LOCKED',
          operatorId: 'op-1',
          lockedAt: '2026-04-12T10:00:00Z',
          auditId: 'a-9',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(wrap(<LockDialog open onOpenChange={onOpenChange} accountId="acc-1" />));

    await user.type(screen.getByLabelText('사유 (필수)'), 'abuse report');
    await user.click(screen.getByRole('button', { name: '잠금' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const call = fetchMock.mock.calls[0];
    const init = call[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get('X-Operator-Reason')).toBe('abuse report');
    expect(headers.get('Idempotency-Key')).toMatch(/[0-9a-f-]{36}/i);
    expect(init.credentials).toBe('include');
    expect(String(call[0])).toContain('/api/admin/accounts/acc-1/lock');

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});
