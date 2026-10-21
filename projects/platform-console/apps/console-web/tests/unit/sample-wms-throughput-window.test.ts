import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { ThroughputSchema, RefPageSchema } from '@/features/wms-ops/api/types';
import {
  activeWarehouseOptions,
  utcThroughputWindow,
  zeroFillThroughput,
} from '@/features/wms-ops/lib/throughput';

/**
 * TASK-PC-FE-358 — the anonymous sample `/wms` 개요 asks for the 14 UTC days
 * ending today; the fixture must answer THAT window (a fixed September answer
 * would draw an empty chart forever), deterministically, with some days
 * ABSENT so the console's zero-fill is exercised, not hidden. Goes through
 * `sampleResponse` (never `fixtures/wms.ts` directly — importing that module
 * first trips the dashboards.ts init order, see its header).
 */

const ADMIN = '/api/v1/admin';
const admin = (path: string) =>
  sampleResponse({ core: 'wms', surface: 'wms', method: 'GET', path });

async function throughputAt(from: string, to: string, warehouseId = 'wh-sample-0001') {
  const res = admin(
    `${ADMIN}/dashboard/throughput?warehouseId=${warehouseId}&from=${from}&to=${to}`,
  );
  expect(res.status).toBe(200);
  return ThroughputSchema.parse(await res.json());
}

describe('sample throughput fixture honours from/to (TASK-PC-FE-358)', () => {
  const { from, to } = utcThroughputWindow(new Date('2026-10-22T12:00:00Z'));

  it('answers the requested window, only dates inside it, some days absent', async () => {
    const t = await throughputAt(from, to);
    expect(t.from).toBe(from);
    expect(t.to).toBe(to);
    expect(t.days.length).toBeGreaterThan(0);
    expect(t.days.length).toBeLessThan(14);
    expect(t.days.every((d) => d.date >= from && d.date <= to)).toBe(true);
    const filled = zeroFillThroughput(t, from, to);
    expect(filled).toHaveLength(14);
    expect(filled.some((d) => d.putawayCount === 0 && d.shipmentCount === 0)).toBe(true);
  });

  it('is deterministic and totals equal the sum of days', async () => {
    const a = await throughputAt(from, to);
    expect(await throughputAt(from, to)).toEqual(a);
    const sum = a.days.reduce((s, d) => s + (d.inbound?.putawayCount ?? 0), 0);
    expect(a.totals?.inbound?.putawayCount).toBe(sum);
  });

  it('an unknown warehouse → no rows', async () => {
    expect((await throughputAt(from, to, 'nope')).days).toEqual([]);
  });

  it('no window → the original fixed answer (kept for the label-guard document)', async () => {
    const res = admin(`${ADMIN}/dashboard/throughput`);
    const t = ThroughputSchema.parse(await res.json());
    expect(t.from).toBe('2026-09-01');
  });

  it('the sample world has exactly one ACTIVE warehouse → the card shows no selector', async () => {
    const res = admin(`${ADMIN}/dashboard/refs/warehouses?status=ACTIVE&page=0&size=100`);
    const page = RefPageSchema.parse(await res.json());
    const opts = activeWarehouseOptions(page.content);
    expect(opts).toHaveLength(1);
    expect(opts[0].code).toBe('WH01');
  });
});
