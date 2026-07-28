import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AutoRenewToggle } from '@/features/membership/ui/AutoRenewToggle';

// Server actions mocked — no gateway / session in unit tests.
const { enrollBillingKey, cancelBillingKeyEnrollment } = vi.hoisted(() => ({
  enrollBillingKey: vi.fn(),
  cancelBillingKeyEnrollment: vi.fn(),
}));
vi.mock('@/features/membership/api/actions', () => ({
  enrollBillingKey,
  cancelBillingKeyEnrollment,
}));

// PortOne issuance window mocked — no real SDK / storeId in unit tests. An
// obviously-fake billing key is used so no real-looking secret enters a snapshot.
const { requestIssueBillingKey } = vi.hoisted(() => ({ requestIssueBillingKey: vi.fn() }));
vi.mock('@/features/membership/lib/portone-billing-key', () => ({ requestIssueBillingKey }));

describe('AutoRenewToggle', () => {
  beforeEach(() => {
    enrollBillingKey.mockReset();
    cancelBillingKeyEnrollment.mockReset();
    requestIssueBillingKey.mockReset();
  });

  it('renders the enroll CTA when there is no active enrollment', () => {
    render(<AutoRenewToggle tier="PREMIUM" />);
    expect(screen.getByRole('button', { name: '자동 갱신 등록' })).toBeInTheDocument();
    expect(screen.queryByText('자동 갱신 등록됨')).not.toBeInTheDocument();
  });

  it('enroll flow: issues a billing key then shows the registered state', async () => {
    requestIssueBillingKey.mockResolvedValue({ ok: true, billingKey: 'bk_test_xxx' });
    enrollBillingKey.mockResolvedValue({
      ok: true,
      enrollment: { enrollmentId: 'e-1', tier: 'PREMIUM', active: true, createdAt: 'now' },
    });
    const user = userEvent.setup();
    render(<AutoRenewToggle tier="PREMIUM" />);

    await user.click(screen.getByRole('button', { name: '자동 갱신 등록' }));

    expect(enrollBillingKey).toHaveBeenCalledWith('PREMIUM', 'bk_test_xxx');
    expect(await screen.findByText('자동 갱신 등록됨')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '해지' })).toBeInTheDocument();
  });

  it('forwards the signed-in fan identity to the issuance SDK call', async () => {
    requestIssueBillingKey.mockResolvedValue({ ok: true, billingKey: 'bk_test_xxx' });
    enrollBillingKey.mockResolvedValue({
      ok: true,
      enrollment: { enrollmentId: 'e-1', tier: 'MEMBERS_ONLY', active: true, createdAt: 'now' },
    });
    const user = userEvent.setup();
    render(
      <AutoRenewToggle tier="MEMBERS_ONLY" buyerEmail="fan@example.com" buyerName="테스트 팬" />,
    );

    await user.click(screen.getByRole('button', { name: '자동 갱신 등록' }));

    expect(requestIssueBillingKey).toHaveBeenCalledWith(
      expect.objectContaining({ email: 'fan@example.com', fullName: '테스트 팬' }),
    );
  });

  it('starts in the registered state when an active enrollment is passed', () => {
    render(
      <AutoRenewToggle
        tier="PREMIUM"
        enrollment={{ enrollmentId: 'e-1', tier: 'PREMIUM', active: true, createdAt: 'now' }}
      />,
    );
    expect(screen.getByText('자동 갱신 등록됨')).toBeInTheDocument();
  });

  it('cancel flow: calls the DELETE action and returns to the enroll CTA', async () => {
    cancelBillingKeyEnrollment.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <AutoRenewToggle
        tier="PREMIUM"
        enrollment={{ enrollmentId: 'e-1', tier: 'PREMIUM', active: true, createdAt: 'now' }}
      />,
    );

    await user.click(screen.getByRole('button', { name: '해지' }));

    expect(cancelBillingKeyEnrollment).toHaveBeenCalledWith('PREMIUM');
    expect(await screen.findByRole('button', { name: '자동 갱신 등록' })).toBeInTheDocument();
  });

  it('does not enroll when the issuance window is canceled (inline notice, no throw)', async () => {
    requestIssueBillingKey.mockResolvedValue({ ok: false, message: '자동 갱신 등록이 취소되었습니다.' });
    const user = userEvent.setup();
    render(<AutoRenewToggle tier="PREMIUM" />);

    await user.click(screen.getByRole('button', { name: '자동 갱신 등록' }));

    expect(enrollBillingKey).not.toHaveBeenCalled();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('자동 갱신 등록이 취소되었습니다');
  });

  it('surfaces a backend enroll validation inline (no throw)', async () => {
    requestIssueBillingKey.mockResolvedValue({ ok: true, billingKey: 'bk_test_xxx' });
    enrollBillingKey.mockResolvedValue({
      ok: false,
      code: 'MEMBERSHIP_TIER_INVALID',
      message: '유효하지 않은 등급입니다.',
    });
    const user = userEvent.setup();
    render(<AutoRenewToggle tier="PREMIUM" />);

    await user.click(screen.getByRole('button', { name: '자동 갱신 등록' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('유효하지 않은 등급입니다');
    expect(screen.queryByText('자동 갱신 등록됨')).not.toBeInTheDocument();
  });
});
