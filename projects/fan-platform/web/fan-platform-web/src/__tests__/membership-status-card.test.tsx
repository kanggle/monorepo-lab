import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MembershipStatusCard } from '@/features/membership/ui/MembershipStatusCard';
import type { MembershipListItem } from '@/entities/membership';

const { cancelMembership } = vi.hoisted(() => ({ cancelMembership: vi.fn(async () => {}) }));
vi.mock('@/features/membership/api/actions', () => ({ cancelMembership }));

const active: MembershipListItem = {
  membershipId: 'm-1',
  tier: 'PREMIUM',
  status: 'ACTIVE',
  validFrom: '2026-06-01T00:00:00Z',
  validTo: '2026-07-01T00:00:00Z',
  planMonths: 1,
  active: true,
  createdAt: '2026-06-01T00:00:00Z',
  canceledAt: null,
};

describe('MembershipStatusCard', () => {
  beforeEach(() => cancelMembership.mockClear());

  it('renders the active tier and window', () => {
    render(<MembershipStatusCard membership={active} />);
    expect(screen.getByText('프리미엄')).toBeInTheDocument();
    expect(screen.getByText('이용 중')).toBeInTheDocument();
    expect(screen.getByText(/1개월/)).toBeInTheDocument();
  });

  it('requires a confirm step before canceling', async () => {
    const user = userEvent.setup();
    render(<MembershipStatusCard membership={active} />);

    // First click reveals the confirm affordance — does NOT cancel yet.
    await user.click(screen.getByRole('button', { name: '멤버십 해지' }));
    expect(cancelMembership).not.toHaveBeenCalled();

    // Confirming calls the server action with the membership id.
    await user.click(screen.getByRole('button', { name: '해지 확정' }));
    expect(cancelMembership).toHaveBeenCalledWith('m-1');
  });
});
