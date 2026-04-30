import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ToastProvider } from '@/shared/ui/toast';
import { CreateOperatorDialog } from '@/features/operators/components/CreateOperatorDialog';

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

describe('CreateOperatorDialog', () => {
  it('blocks submission when password fails the policy', async () => {
    const user = userEvent.setup();
    render(wrap(<CreateOperatorDialog open onOpenChange={() => {}} />));

    await user.type(screen.getByLabelText('이메일'), 'new@example.com');
    await user.type(screen.getByLabelText('표시 이름'), 'New Operator');
    // Password that is only letters — violates the policy (no digit, no special char, <10).
    await user.type(screen.getByLabelText('초기 비밀번호'), 'abc');
    await user.click(screen.getByRole('button', { name: '생성' }));

    const alerts = await screen.findAllByRole('alert');
    expect(alerts.some((el) => el.textContent?.includes('영문·숫자·특수문자'))).toBe(true);
  });

  it('submits with X-Operator-Reason + Idempotency-Key on valid input', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          operatorId: 'op-new',
          email: 'new@example.com',
          displayName: 'New Operator',
          status: 'ACTIVE',
          roles: ['SUPPORT_LOCK'],
          totpEnrolled: false,
          createdAt: '2026-04-24T10:00:00Z',
          auditId: 'aud-1',
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const onOpenChange = vi.fn();
    const user = userEvent.setup();
    render(wrap(<CreateOperatorDialog open onOpenChange={onOpenChange} />));

    // Populate controlled inputs via fireEvent.change to avoid user-event's
    // keyboard-shortcut semantics for special chars in the password.
    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'new@example.com' },
    });
    fireEvent.change(screen.getByLabelText('표시 이름'), {
      target: { value: 'New Operator' },
    });
    // Password satisfies policy: ≥10 chars, 영문 + 숫자 + 특수문자.
    fireEvent.change(screen.getByLabelText('초기 비밀번호'), {
      target: { value: 'Abcdefg1!@' },
    });
    await user.click(screen.getByRole('checkbox', { name: 'SUPPORT_LOCK' }));
    await user.click(screen.getByRole('button', { name: '생성' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const call = fetchMock.mock.calls[0];
    const init = call[1] as RequestInit;
    const headers = new Headers(init.headers);
    // Component uses an ASCII-safe constant because HTTP headers must be
    // ByteString-safe (ASCII/Latin-1) under strict undici validation.
    expect(headers.get('X-Operator-Reason')).toBe('operator.create');
    expect(headers.get('Idempotency-Key')).toMatch(/[0-9a-f-]{36}/i);
    expect(String(call[0])).toContain('/api/admin/operators');
    expect(init.method).toBe('POST');

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});
