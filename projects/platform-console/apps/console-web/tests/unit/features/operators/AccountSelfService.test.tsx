import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AccountSelfService } from '@/features/operators';

/**
 * TASK-PC-FE-045 — 계정 설정 셀프서비스. The self change-password + default
 * finance-account forms moved here from OperatorsScreen (계정 설정 = 내 것,
 * 운영자 관리 = 남 관리). Client island over the same-origin `/api/operators/me/*`
 * proxy (API unchanged); fetch mocked.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('AccountSelfService (계정 설정 셀프서비스)', () => {
  it('renders BOTH self forms (change-password + my-profile)', () => {
    render(<AccountSelfService initialDefaultAccountId={null} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('change-password-form')).toBeInTheDocument();
    expect(screen.getByTestId('my-profile-form')).toBeInTheDocument();
  });

  it('seeds the my-profile input from initialDefaultAccountId', () => {
    render(
      <AccountSelfService initialDefaultAccountId="acct-xyz" />,
      { wrapper: wrapper() },
    );
    expect(
      (screen.getByTestId('my-profile-default-account-id') as HTMLInputElement)
        .value,
    ).toBe('acct-xyz');
  });

  it('change-password blocks on mismatch and POSTs me/password when valid', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountSelfService initialDefaultAccountId={null} />, {
      wrapper: wrapper(),
    });

    await user.type(screen.getByTestId('change-password-current'), 'Old1!pass');
    await user.type(screen.getByTestId('change-password-new'), 'New2@word!');
    await user.type(
      screen.getByTestId('change-password-confirm'),
      'Different1!',
    );
    expect(
      screen.getByTestId('change-password-confirm-error'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('change-password-submit')).toBeDisabled();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.clear(screen.getByTestId('change-password-confirm'));
    await user.type(
      screen.getByTestId('change-password-confirm'),
      'New2@word!',
    );
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    expect(screen.getByTestId('change-password-submit')).toBeEnabled();
    await user.click(screen.getByTestId('change-password-submit'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/operators/me/password',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
  });
});
