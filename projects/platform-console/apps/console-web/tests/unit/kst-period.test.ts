import { describe, it, expect } from 'vitest';
import { kstPeriodBounds } from '@/features/wms-ops/api/kst-period';

/**
 * TASK-PC-FE-174 — `kstPeriodBounds` KST calendar-period-to-date boundaries.
 * The TS analogue of the shared java `KstPeriodBounds`: today = KST day start,
 * week = current ISO week (Monday), month = the 1st, +09:00 fixed (no DST).
 * All returned as UTC instant ISO strings.
 */
describe('kstPeriodBounds (TASK-PC-FE-174)', () => {
  it('mid-day Saturday: KST day/week(ISO Mon)/month starts as UTC instants', () => {
    // 2026-07-04T12:00:00Z = 2026-07-04 21:00 KST (a Saturday).
    const b = kstPeriodBounds(new Date('2026-07-04T12:00:00Z'));
    // KST 2026-07-04 00:00 = 2026-07-03T15:00:00Z.
    expect(b.todayStartInstant).toBe('2026-07-03T15:00:00.000Z');
    // ISO week Monday of that week = 2026-06-29 → KST 00:00 = 2026-06-28T15:00Z.
    expect(b.weekStartInstant).toBe('2026-06-28T15:00:00.000Z');
    // KST 2026-07-01 00:00 = 2026-06-30T15:00:00Z.
    expect(b.monthStartInstant).toBe('2026-06-30T15:00:00.000Z');
    expect(b.nowInstant).toBe('2026-07-04T12:00:00.000Z');
  });

  it('week start rolls back across the month/year boundary (Date.UTC normalization)', () => {
    // 2026-01-01T00:30:00Z = 2026-01-01 09:30 KST (a Thursday).
    const b = kstPeriodBounds(new Date('2026-01-01T00:30:00Z'));
    // KST 2026-01-01 00:00 = 2025-12-31T15:00:00Z (today AND month start).
    expect(b.todayStartInstant).toBe('2025-12-31T15:00:00.000Z');
    expect(b.monthStartInstant).toBe('2025-12-31T15:00:00.000Z');
    // Thursday → ISO week Monday = 2025-12-29 → KST 00:00 = 2025-12-28T15:00Z.
    expect(b.weekStartInstant).toBe('2025-12-28T15:00:00.000Z');
  });

  it('KST day boundary: an instant just before KST midnight belongs to the previous KST day', () => {
    // 2026-07-04T14:59:00Z = 2026-07-04 23:59 KST — still 2026-07-04 in KST.
    const before = kstPeriodBounds(new Date('2026-07-04T14:59:00Z'));
    expect(before.todayStartInstant).toBe('2026-07-03T15:00:00.000Z');
    // 2026-07-04T15:00:00Z = 2026-07-05 00:00 KST — rolls to the next KST day.
    const after = kstPeriodBounds(new Date('2026-07-04T15:00:00Z'));
    expect(after.todayStartInstant).toBe('2026-07-04T15:00:00.000Z');
  });

  it('week/month starts never exceed today, today never exceeds now', () => {
    // Guaranteed orderings only: weekStart ≤ today, monthStart ≤ today,
    // today ≤ now. NOTE month vs week is NOT ordered — at a month boundary the
    // ISO week can begin before the 1st (e.g. 2026-07-04: weekStart 2026-06-29
    // precedes monthStart 2026-07-01), so counts are not strictly today ≤ week
    // ≤ month; the UI renders raw counts and never re-derives this.
    const b = kstPeriodBounds(new Date('2026-07-04T12:00:00Z'));
    expect(b.weekStartInstant <= b.todayStartInstant).toBe(true);
    expect(b.monthStartInstant <= b.todayStartInstant).toBe(true);
    expect(b.todayStartInstant <= b.nowInstant).toBe(true);
    // Confirm the boundary quirk is real (documents the intentional behavior).
    expect(b.weekStartInstant < b.monthStartInstant).toBe(true);
  });
});
