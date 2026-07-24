import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RenewPanel } from '@/features/membership/ui/RenewPanel';
import type { MembershipListItem } from '@/entities/membership';

const { renewMembership } = vi.hoisted(() => ({
  renewMembership: vi.fn(async () => ({ ok: true as const, membership: {} })),
}));
vi.mock('@/features/membership/api/actions', () => ({ renewMembership }));

const { requestPortOnePayment } = vi.hoisted(() => ({
  requestPortOnePayment: vi.fn(async () => ({ ok: true as const, paymentId: 'pay-renew' })),
}));
vi.mock('@/features/membership/lib/portone-checkout', () => ({ requestPortOnePayment }));

const expired: MembershipListItem = {
  membershipId: 'm-9',
  tier: 'MEMBERS_ONLY',
  status: 'ACTIVE',
  validFrom: '2026-05-01T00:00:00Z',
  validTo: '2026-06-01T00:00:00Z',
  planMonths: 1,
  active: false,
  createdAt: '2026-05-01T00:00:00Z',
  canceledAt: null,
};

describe('RenewPanel', () => {
  beforeEach(() => {
    renewMembership.mockClear();
    requestPortOnePayment.mockClear();
  });

  it('renders the expired tier and window end', () => {
    render(<RenewPanel membership={expired} />);
    expect(screen.getByText(/멤버스 전용 멤버십이 만료되었습니다/)).toBeInTheDocument();
  });

  it('opens the payment window then renews with the returned paymentId', async () => {
    const user = userEvent.setup();
    render(<RenewPanel membership={expired} />);

    await user.click(screen.getByTestId('renew-panel-button'));

    expect(requestPortOnePayment).toHaveBeenCalled();
    expect(renewMembership).toHaveBeenCalledWith('m-9', 1, 'pay-renew');
  });
});
