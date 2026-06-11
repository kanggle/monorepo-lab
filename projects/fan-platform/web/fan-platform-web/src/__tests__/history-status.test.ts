import { describe, it, expect } from 'vitest';
import { historyStatus, HISTORY_LABEL } from '@/features/membership/ui/historyStatus';
import type { MembershipListItem } from '@/entities/membership';

const NOW = Date.parse('2026-07-01T00:00:00Z');

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

describe('historyStatus', () => {
  it('canceled regardless of window', () => {
    expect(historyStatus(item({ status: 'CANCELED' }), NOW)).toBe('canceled');
  });

  it('active when read-time active', () => {
    expect(historyStatus(item({ active: true }), NOW)).toBe('active');
  });

  it('scheduled for a future (not-yet-started) window', () => {
    expect(
      historyStatus(item({ active: false, validFrom: '2026-09-01T00:00:00Z' }), NOW),
    ).toBe('scheduled');
  });

  it('expired for a past window', () => {
    expect(
      historyStatus(
        item({ active: false, validFrom: '2026-04-01T00:00:00Z', validTo: '2026-05-01T00:00:00Z' }),
        NOW,
      ),
    ).toBe('expired');
  });

  it('labels cover every state', () => {
    expect(HISTORY_LABEL.active).toBeTruthy();
    expect(HISTORY_LABEL.scheduled).toBeTruthy();
    expect(HISTORY_LABEL.expired).toBeTruthy();
    expect(HISTORY_LABEL.canceled).toBeTruthy();
  });
});
