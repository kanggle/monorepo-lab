import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PartnershipsScreen, type PartnershipList } from '@/features/partnerships';

/**
 * TASK-PC-FE-249 — render regression guard for the `/partnerships` screen.
 *
 * The feature already had proxy / client / state / nav unit tests, but the
 * screen component itself was never mounted in a test — so a crash-on-mount
 * would have shipped green. This mounts the REAL screen with a representative
 * host+partner fixture and asserts its primary rendered structure (heading +
 * both sections + the seeded rows), plus the empty-state branch.
 *
 * PartnershipsScreen uses `useRouter().refresh()` on mutation (no react-query),
 * so only `next/navigation` needs stubbing.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
}));

const LIST: PartnershipList = {
  items: [
    {
      partnershipId: 'ps-1',
      hostTenantId: 'acme',
      partnerTenantId: 'globex',
      status: 'ACTIVE',
      delegatedScope: { domains: ['wms'], roles: ['WMS_VIEWER'] },
      myRole: 'host',
      invitedAt: '2026-01-01T00:00:00Z',
      acceptedAt: '2026-01-02T00:00:00Z',
      participantCount: 1,
    },
    {
      partnershipId: 'ps-2',
      hostTenantId: 'initech',
      partnerTenantId: 'acme',
      status: 'PENDING',
      delegatedScope: { domains: ['finance'], roles: [] },
      myRole: 'partner',
      invitedAt: '2026-01-03T00:00:00Z',
      acceptedAt: null,
      participantCount: 0,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
};

describe('PartnershipsScreen — mount render', () => {
  it('renders the heading + host/partner sections with the seeded rows', () => {
    render(<PartnershipsScreen initial={LIST} activeTenant="acme" />);

    expect(
      screen.getByRole('heading', { level: 1, name: '파트너십' }),
    ).toBeInTheDocument();
    // Both sections render their subheadings ("(host)" / "(partner)").
    expect(screen.getByRole('heading', { name: /host/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /partner/ })).toBeInTheDocument();
    // The seeded rows mount (one per side).
    expect(screen.getByTestId('partnership-row-ps-1')).toBeInTheDocument();
    expect(screen.getByTestId('partnership-row-ps-2')).toBeInTheDocument();
  });

  it('renders both empty-states when there are no partnerships', () => {
    const empty: PartnershipList = {
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    };
    render(<PartnershipsScreen initial={empty} activeTenant="acme" />);

    expect(
      screen.getByText('우리 조직이 발행한 파트너십이 없습니다.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('우리 조직에 위임된 파트너십이 없습니다.'),
    ).toBeInTheDocument();
  });
});
