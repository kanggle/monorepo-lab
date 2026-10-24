import { describe, it, expect } from 'vitest';
import {
  activeWarehouseOptions,
  barPercent,
  enumerateIsoDates,
  sumThroughput,
  kstThroughputWindow,
  validateThroughputQuery,
  zeroFillThroughput,
} from '@/features/wms-ops/lib/throughput';

/**
 * TASK-PC-FE-358 — pure helpers behind the `/wms` 개요 throughput card
 * (contract § 2.4.5.2 «Daily throughput card»): the KST window, zero-fill,
 * the proxy's pre-upstream validation, warehouse ordering and bar scaling.
 */

const WH = '01910000-0000-7000-8000-000000000001';

describe('kstThroughputWindow — 14 KST days ending at the KST date of now (TASK-MONO-815)', () => {
  it('AC-1: 00:30 KST 10-25 (= 2026-10-24T15:30Z) ends on the KST date 10-25, 14 days', () => {
    const w = kstThroughputWindow(new Date('2026-10-24T15:30:00Z'));
    expect(w).toEqual({ from: '2026-10-12', to: '2026-10-25' });
    expect(enumerateIsoDates(w.from, w.to)).toHaveLength(14);
  });

  it('control: 2026-10-24T05:00Z (14:00 KST) ends on 10-24', () => {
    expect(kstThroughputWindow(new Date('2026-10-24T05:00:00Z'))).toEqual({
      from: '2026-10-11',
      to: '2026-10-24',
    });
  });

  it('late UTC evening (2026-10-22T23:30Z — already 10-23 08:30 KST) ends on 10-23', () => {
    expect(kstThroughputWindow(new Date('2026-10-22T23:30:00Z'))).toEqual({
      from: '2026-10-10',
      to: '2026-10-23',
    });
  });

  it('crosses a month/year boundary (2027-01-05T20:00Z = 01-06 05:00 KST)', () => {
    expect(kstThroughputWindow(new Date('2027-01-05T20:00:00Z'))).toEqual({
      from: '2026-12-24',
      to: '2027-01-06',
    });
  });

  it('is exactly 14 inclusive dates', () => {
    const w = kstThroughputWindow(new Date('2026-10-22T12:00:00Z'));
    expect(enumerateIsoDates(w.from, w.to)).toHaveLength(14);
  });
});

describe('enumerateIsoDates', () => {
  it('inclusive both ends; reversed / invalid → empty', () => {
    expect(enumerateIsoDates('2026-02-27', '2026-03-01')).toEqual([
      '2026-02-27',
      '2026-02-28',
      '2026-03-01',
    ]);
    expect(enumerateIsoDates('2026-03-02', '2026-03-01')).toEqual([]);
    expect(enumerateIsoDates('2026-02-30', '2026-03-01')).toEqual([]);
  });
});

describe('zeroFillThroughput — the producer omits days without a row', () => {
  it('fills every missing date with zeros, keeps present days, ignores days outside the window', () => {
    const days = zeroFillThroughput(
      {
        days: [
          {
            date: '2026-10-20',
            inbound: { putawayCount: 3, qtyReceived: 30 },
            outbound: { shipmentCount: 1, qtyShipped: 5 },
          },
          {
            date: '2026-10-01', // outside — must not widen the axis
            inbound: { putawayCount: 99, qtyReceived: 1 },
            outbound: { shipmentCount: 99, qtyShipped: 1 },
          },
        ],
      },
      '2026-10-19',
      '2026-10-21',
    );
    expect(days.map((d) => d.date)).toEqual(['2026-10-19', '2026-10-20', '2026-10-21']);
    expect(days[0]).toEqual({
      date: '2026-10-19',
      putawayCount: 0,
      shipmentCount: 0,
      qtyReceived: 0,
      qtyShipped: 0,
    });
    expect(days[1].putawayCount).toBe(3);
    expect(days[1].shipmentCount).toBe(1);
    expect(sumThroughput(days)).toEqual({
      putawayCount: 3,
      shipmentCount: 1,
      qtyReceived: 30,
      qtyShipped: 5,
    });
  });

  it('empty days → all zeros for the whole window', () => {
    const days = zeroFillThroughput({ days: [] }, '2026-10-09', '2026-10-22');
    expect(days).toHaveLength(14);
    expect(days.every((d) => d.putawayCount === 0 && d.shipmentCount === 0)).toBe(true);
  });

  it('a day with one side missing reads that side as 0', () => {
    const [d] = zeroFillThroughput(
      { days: [{ date: '2026-10-22', inbound: { putawayCount: 2 } }] },
      '2026-10-22',
      '2026-10-22',
    );
    expect(d).toMatchObject({ putawayCount: 2, shipmentCount: 0, qtyReceived: 0 });
  });
});

describe('validateThroughputQuery — what the proxy rejects before upstream', () => {
  const ok = { warehouseId: WH, from: '2026-10-09', to: '2026-10-22' };

  it('a valid 14-day query passes', () => {
    expect(validateThroughputQuery(ok)).toBeNull();
  });

  it.each([
    ['missing warehouseId', { ...ok, warehouseId: null }],
    ['non-UUID warehouseId', { ...ok, warehouseId: 'wh-sample-0001' }],
    ['missing from', { ...ok, from: null }],
    ['missing to', { ...ok, to: null }],
    ['non-ISO from', { ...ok, from: '2026/10/09' }],
    ['impossible date', { ...ok, to: '2026-02-30' }],
    ['to < from', { ...ok, from: '2026-10-22', to: '2026-10-21' }],
    ['91-day inclusive span', { ...ok, from: '2026-01-01', to: '2026-04-01' }],
  ])('%s → rejected', (_label, q) => {
    expect(validateThroughputQuery(q)).not.toBeNull();
  });

  it('exactly 90 inclusive days passes; same-day passes', () => {
    expect(validateThroughputQuery({ ...ok, from: '2026-01-01', to: '2026-03-31' })).toBeNull();
    expect(validateThroughputQuery({ ...ok, from: '2026-10-22', to: '2026-10-22' })).toBeNull();
  });
});

describe('activeWarehouseOptions — owner decision (a)', () => {
  it('keeps ACTIVE (and status-less) rows, drops others, sorts by code ascending', () => {
    const opts = activeWarehouseOptions([
      { id: 'b', code: 'WH02', name: 'B', status: 'ACTIVE' },
      { id: 'x', code: 'WH00', name: 'X', status: 'INACTIVE' },
      { id: 'a', code: 'WH01', name: 'A', status: 'ACTIVE' },
      { id: 'c', code: 'WH03' },
      { code: 'NO-ID', status: 'ACTIVE' },
    ]);
    expect(opts.map((o) => o.id)).toEqual(['a', 'b', 'c']);
  });

  it('reads the producer `code`, falls back to `warehouseCode` (sample) then id', () => {
    const opts = activeWarehouseOptions([
      { id: 'w1', warehouseCode: 'WH01', name: '강서 (샘플)' },
      { id: 'zz-id-only' },
    ]);
    expect(opts).toEqual([
      { id: 'w1', code: 'WH01', name: '강서 (샘플)' },
      { id: 'zz-id-only', code: 'zz-id-only', name: null },
    ]);
  });

  it('the demo seed pair sorts by code-unit order (WH-MAIN before WH01)', () => {
    const opts = activeWarehouseOptions([
      { id: '1', code: 'WH01', status: 'ACTIVE' },
      { id: '2', code: 'WH-MAIN', status: 'ACTIVE' },
    ]);
    expect(opts.map((o) => o.code)).toEqual(['WH-MAIN', 'WH01']);
  });
});

describe('barPercent', () => {
  it('ratio of max, 2% sliver for a tiny non-zero, 0 for zero or max 0', () => {
    expect(barPercent(10, 10)).toBe(100);
    expect(barPercent(5, 10)).toBe(50);
    expect(barPercent(1, 1000)).toBe(2);
    expect(barPercent(0, 10)).toBe(0);
    expect(barPercent(0, 0)).toBe(0);
    expect(barPercent(3, 0)).toBe(0);
  });
});
