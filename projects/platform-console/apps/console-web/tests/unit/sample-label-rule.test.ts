/**
 * R2ⓐ «(샘플)» labelling rule over every fixture (TASK-PC-FE-282 AC-7).
 *
 * Human-readable strings (names, titles, descriptions, memos) end with
 * « (샘플)»; ids, codes, amounts, dates and status enums never contain it —
 * parsers and `StatusBadge` read those. An unclassified string key is itself a
 * failure (see `shared/sample/label-rule.ts`).
 *
 * The self-check cells pin the predicate in BOTH directions, so the fixture
 * cell cannot go green by the predicate quietly accepting everything.
 */
import { describe, it, expect } from 'vitest';
import { findLabelViolations } from '@/shared/sample/label-rule';
import { SAMPLE_FIXTURE_DOCUMENTS } from '@/shared/sample/fixtures';

describe('predicate self-check (both directions)', () => {
  it('a human-readable string WITHOUT the suffix is a violation', () => {
    expect(findLabelViolations({ name: '평택 물류센터' })).toEqual([
      { path: '$.name', value: '평택 물류센터', problem: 'missing-suffix' },
    ]);
  });

  it('a status enum WITH the suffix is a violation', () => {
    expect(findLabelViolations({ status: 'UP (샘플)' })).toEqual([
      { path: '$.status', value: 'UP (샘플)', problem: 'suffix-on-machine-value' },
    ]);
  });

  it('an id / date / amount with the suffix is a violation', () => {
    const v = findLabelViolations({
      sourceId: 'x (샘플)',
      createdAt: '2026-09-15T00:00:00Z (샘플)',
      amount: '100 (샘플)',
    });
    expect(v.map((x) => x.problem)).toEqual([
      'suffix-on-machine-value',
      'suffix-on-machine-value',
      'suffix-on-machine-value',
    ]);
  });

  it('🔴 an unclassified string key is a violation (never a silent pass)', () => {
    // 🔴 TASK-PC-FE-284 classified `nickname` (ecommerce users) as
    // human-readable, so it is no longer an example of an UNclassified key —
    // this cell's SUBJECT is the predicate's fallback branch, not `nickname`
    // specifically. Swapped to a key no fixture ticket has claimed.
    expect(findLabelViolations({ mysteryField: '하나 (샘플)' })[0].problem).toBe(
      'unclassified-key',
    );
  });

  it('strings inside arrays inherit the array key', () => {
    expect(findLabelViolations({ tenants: ['sample'] })).toEqual([]);
    expect(findLabelViolations({ tenants: ['sample (샘플)'] })[0].problem).toBe(
      'suffix-on-machine-value',
    );
  });

  it('well-formed values pass', () => {
    expect(
      findLabelViolations({
        id: 'n-1',
        title: '결재 요청 (샘플)',
        status: 'UP',
        count: 3,
        read: false,
      }),
    ).toEqual([]);
  });
});

describe('every sample fixture follows the rule', () => {
  it('🔵 non-vacuity — there are fixtures to check', () => {
    expect(Object.keys(SAMPLE_FIXTURE_DOCUMENTS).length).toBeGreaterThanOrEqual(4);
  });

  it.each(Object.entries(SAMPLE_FIXTURE_DOCUMENTS))('%s', (_key, doc) => {
    expect(findLabelViolations(doc)).toEqual([]);
  });

  it('🔵 non-vacuity — the fixtures actually carry suffixed strings', () => {
    const json = JSON.stringify(SAMPLE_FIXTURE_DOCUMENTS);
    expect((json.match(/\(샘플\)/g) ?? []).length).toBeGreaterThanOrEqual(10);
  });
});
