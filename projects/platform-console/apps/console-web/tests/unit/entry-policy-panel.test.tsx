import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-MONO-771 S5 — the «운영자 진입 2단계 인증» control
 * (console-integration-contract § 2.4.3.3, OD-1 · OD-4). The same-origin
 * `apiClient` is mocked; what the control SENDS and SAYS is asserted.
 */

const apiGet = vi.fn();
const apiPut = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => apiGet(...a),
    put: (...a: unknown[]) => apiPut(...a),
  },
}));

import {
  EntryPolicyPanel,
  ENTRY_POLICY_ON_COPY,
} from '@/features/tenant-entry-policy/components/EntryPolicyPanel';
import { ApiError } from '@/shared/api/errors';
import type { EntryPolicy } from '@/features/tenant-entry-policy';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const OFF: EntryPolicy = { tenantId: 'acme', requireMfa: false, updatedAt: null, updatedBy: null };
const ON: EntryPolicy = {
  tenantId: 'acme',
  requireMfa: true,
  updatedAt: '2026-10-09T01:00:00Z',
  updatedBy: 'op-uuid',
};
const SUMMARY = { tenantId: 'acme', operators: 7, enrolled: 3, notEnrolled: 3, unlinked: 1 };

function renderPanel(initial = OFF, selfHasSecondFactor: boolean | null = true) {
  return render(
    <EntryPolicyPanel tenantId="acme" initial={initial} selfHasSecondFactor={selfHasSecondFactor} />,
    { wrapper: wrapper() },
  );
}

beforeEach(() => {
  apiGet.mockReset();
  apiPut.mockReset();
});

describe('EntryPolicyPanel — turning ON (OD-4 transition copy)', () => {
  it('🔴 the confirm states the transition rule and never says «locked out»', async () => {
    apiGet.mockResolvedValue(SUMMARY);
    renderPanel();
    expect(screen.getByTestId('entry-policy-status')).toHaveAttribute('data-require-mfa', 'false');

    fireEvent.click(screen.getByTestId('entry-policy-toggle'));
    const dialog = screen.getByTestId('entry-policy-dialog');
    expect(dialog).toHaveTextContent(ENTRY_POLICY_ON_COPY);
    expect(dialog).toHaveTextContent('등록 화면으로 안내');
    expect(dialog.textContent ?? '').not.toMatch(/잠기|잠깁|잠금/);
  });

  it('🔴 shows the pre-check count from enrolment-summary («미등록 N명»)', async () => {
    apiGet.mockResolvedValue(SUMMARY);
    renderPanel();
    fireEvent.click(screen.getByTestId('entry-policy-toggle'));

    await waitFor(() =>
      expect(screen.getByTestId('entry-policy-precheck')).toHaveTextContent(
        '이 테넌트 운영자 7명 중 2단계 미등록 3명은 다음 진입 때 등록 화면으로 안내됩니다.',
      ),
    );
    expect(apiGet).toHaveBeenCalledWith('/api/tenants/acme/entry-policy/enrolment-summary');
  });

  it('🔴 pre-check failure (503) → copy WITHOUT a number — never a made-up 0, toggle still allowed', async () => {
    apiGet.mockRejectedValue(new ApiError(503, 'DOWNSTREAM_ERROR', 'x'));
    renderPanel();
    fireEvent.click(screen.getByTestId('entry-policy-toggle'));

    await waitFor(() =>
      expect(screen.getByTestId('entry-policy-precheck')).toHaveTextContent('지금 확인할 수 없습니다'),
    );
    expect(screen.getByTestId('entry-policy-precheck').textContent ?? '').not.toMatch(/\d+명/);
    fireEvent.change(screen.getByTestId('entry-policy-reason'), { target: { value: '정책' } });
    expect(screen.getByTestId('entry-policy-submit')).not.toBeDisabled();
  });

  it('self-effect: a session without mfa is warned; one with mfa is not', async () => {
    apiGet.mockResolvedValue(SUMMARY);
    const { unmount } = renderPanel(OFF, false);
    fireEvent.click(screen.getByTestId('entry-policy-toggle'));
    expect(screen.getByTestId('entry-policy-self-effect')).toHaveTextContent('본인도');
    unmount();

    renderPanel(OFF, true);
    fireEvent.click(screen.getByTestId('entry-policy-toggle'));
    expect(screen.queryByTestId('entry-policy-self-effect')).toBeNull();
  });

  it('reason-gated: confirm stays disabled until a reason is typed; PUT carries {requireMfa:true, reason}', async () => {
    apiGet.mockResolvedValue(SUMMARY);
    apiPut.mockResolvedValue(ON);
    renderPanel();
    fireEvent.click(screen.getByTestId('entry-policy-toggle'));
    expect(screen.getByTestId('entry-policy-submit')).toBeDisabled();

    fireEvent.change(screen.getByTestId('entry-policy-reason'), { target: { value: '  회사 보안 정책  ' } });
    fireEvent.click(screen.getByTestId('entry-policy-submit'));

    await waitFor(() =>
      expect(apiPut).toHaveBeenCalledWith('/api/tenants/acme/entry-policy', {
        requireMfa: true,
        reason: '회사 보안 정책',
      }),
    );
    await waitFor(() =>
      expect(screen.getByTestId('entry-policy-status')).toHaveAttribute('data-require-mfa', 'true'),
    );
    expect(screen.queryByTestId('entry-policy-dialog')).toBeNull();
    expect(screen.getByTestId('entry-policy-updated-by')).toHaveTextContent('op-uuid');
  });

  it('a producer 403 stays inline in the dialog (no crash, state unchanged)', async () => {
    apiGet.mockResolvedValue(SUMMARY);
    apiPut.mockRejectedValue(new ApiError(403, 'TENANT_SCOPE_DENIED', 'no'));
    renderPanel();
    fireEvent.click(screen.getByTestId('entry-policy-toggle'));
    fireEvent.change(screen.getByTestId('entry-policy-reason'), { target: { value: 'r' } });
    fireEvent.click(screen.getByTestId('entry-policy-submit'));

    await waitFor(() => expect(screen.getByTestId('entry-policy-error')).toBeInTheDocument());
    expect(screen.getByTestId('entry-policy-status')).toHaveAttribute('data-require-mfa', 'false');
  });
});

describe('EntryPolicyPanel — turning OFF', () => {
  it('no pre-check read, OFF copy, PUT {requireMfa:false}', async () => {
    apiPut.mockResolvedValue({ ...ON, requireMfa: false });
    renderPanel(ON, false);
    expect(screen.getByTestId('entry-policy-toggle')).toHaveTextContent('끄기');

    fireEvent.click(screen.getByTestId('entry-policy-toggle'));
    expect(screen.queryByTestId('entry-policy-precheck')).toBeNull();
    expect(screen.queryByTestId('entry-policy-self-effect')).toBeNull();
    expect(apiGet).not.toHaveBeenCalled();

    fireEvent.change(screen.getByTestId('entry-policy-reason'), { target: { value: 'rollback' } });
    fireEvent.click(screen.getByTestId('entry-policy-submit'));
    await waitFor(() =>
      expect(apiPut).toHaveBeenCalledWith('/api/tenants/acme/entry-policy', {
        requireMfa: false,
        reason: 'rollback',
      }),
    );
  });
});
