import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MembershipHistoryList } from '@/features/membership/ui/MembershipHistoryList';
import type { MembershipListItem } from '@/entities/membership';

function item(over: Partial<MembershipListItem>): MembershipListItem {
  return {
    membershipId: 'm',
    tier: 'PREMIUM',
    status: 'ACTIVE',
    validFrom: '2026-06-01T00:00:00Z',
    validTo: '2026-08-01T00:00:00Z',
    planMonths: 1,
    active: false,
    createdAt: '2026-06-01T00:00:00Z',
    canceledAt: null,
    ...over,
  };
}

describe('MembershipHistoryList', () => {
  it('renders a row per membership', () => {
    render(
      <MembershipHistoryList
        memberships={[
          item({ membershipId: 'a', active: true }),
          item({ membershipId: 'b', status: 'CANCELED' }),
        ]}
      />,
    );
    expect(screen.getAllByTestId('history-row')).toHaveLength(2);
  });

  it('labels an active membership 이용 중 and a canceled one 해지됨', () => {
    render(
      <MembershipHistoryList
        memberships={[
          item({ membershipId: 'a', tier: 'PREMIUM', active: true }),
          item({ membershipId: 'b', tier: 'MEMBERS_ONLY', status: 'CANCELED' }),
        ]}
      />,
    );
    expect(screen.getByText('이용 중')).toBeInTheDocument();
    expect(screen.getByText('해지됨')).toBeInTheDocument();
    expect(screen.getByText('프리미엄')).toBeInTheDocument();
    expect(screen.getByText('멤버스 전용')).toBeInTheDocument();
  });
});
