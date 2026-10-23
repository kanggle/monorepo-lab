import { afterEach, describe, expect, it } from 'vitest';
import path from 'node:path';
import { existsSync, readFileSync } from 'node:fs';
import { kstMetricsWindows, METRICS_PERIODS } from '@/shared/lib/kst-period';

/**
 * TASK-MONO-806 AC-1 · AC-2 · AC-5 — `kstMetricsWindows` against the SAME
 * fixture the java `KstPeriodBounds` tests read
 * (`libs/java-common/.../KstPeriodBoundsWindowsFixtureTest`). If the console and
 * the producers disagree on a window or a date, one side goes red.
 */

const FIXTURE = path.resolve(
  __dirname,
  '../../../../../../platform/contracts/fixtures/performance-metrics-windows.json',
);

interface PeriodCase {
  current: { from: string; to: string };
  previous: { from: string; to: string };
  firstDate: string;
  lastDate: string;
  dateCount: number;
}
interface FixtureCase {
  name: string;
  now: string;
  week: PeriodCase;
  month: PeriodCase;
}

function loadCases(): FixtureCase[] {
  expect(existsSync(FIXTURE), `fixture at ${FIXTURE}`).toBe(true);
  return (JSON.parse(readFileSync(FIXTURE, 'utf8')) as { cases: FixtureCase[] }).cases;
}

const ORIGINAL_TZ = process.env.TZ;
afterEach(() => {
  process.env.TZ = ORIGINAL_TZ;
});

describe('kstMetricsWindows (TASK-MONO-806)', () => {
  it('the fixture is not empty (a vacuous fixture would pass everything)', () => {
    expect(loadCases().length).toBeGreaterThanOrEqual(8);
  });

  // AC-2: the host zone must not move any answer — the KST host / UTC CI split.
  for (const tz of ['UTC', 'Asia/Seoul', 'America/Los_Angeles']) {
    it(`matches every fixture case with TZ=${tz}`, () => {
      process.env.TZ = tz;
      let checked = 0;
      for (const c of loadCases()) {
        for (const period of METRICS_PERIODS) {
          const want = c[period];
          const got = kstMetricsWindows(period, new Date(c.now));
          const label = `${c.name} / ${period}`;
          expect(got.current, label).toEqual(want.current);
          expect(got.previous, label).toEqual(want.previous);
          expect(got.dates.length, label).toBe(want.dateCount);
          expect(got.dates[0], label).toBe(want.firstDate);
          expect(got.dates[got.dates.length - 1], label).toBe(want.lastDate);
          checked++;
        }
      }
      expect(checked).toBe(loadCases().length * METRICS_PERIODS.length);
    });
  }

  it('drops sub-second precision like the producers do', () => {
    const w = kstMetricsWindows('week', new Date('2026-07-04T12:00:00.999Z'));
    expect(w.current.to).toBe('2026-07-04T21:00:00+09:00');
  });

  it('dates are consecutive days', () => {
    const { dates } = kstMetricsWindows('month', new Date('2026-03-30T05:00:00Z'));
    for (let i = 1; i < dates.length; i++) {
      const prev = new Date(`${dates[i - 1]}T00:00:00Z`).getTime();
      const cur = new Date(`${dates[i]}T00:00:00Z`).getTime();
      expect(cur - prev).toBe(24 * 60 * 60 * 1000);
    }
  });
});
