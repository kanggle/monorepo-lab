import { describe, expect, it } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_METRICS_WINDOWS } from '@/shared/sample/fixtures/metrics-windows';
import { METRICS_PERIODS } from '@/shared/lib/kst-period';
import { PerformanceMetricsSchema } from '@/shared/api/performance-metrics';
import { PoPageSchema, type PurchaseOrder } from '@/features/scm-ops/api/types';
import { scmPerformanceCardHref } from '@/features/scm-ops/components/ScmOverview';

/**
 * TASK-MONO-810 — the anonymous visitor's scm «실적» strip (ADR-MONO-074 sample
 * mode) and the PO list's submit-window filter. The strip is derived from the
 * SAME seed POs the 조달 list shows, so the expectations below are re-derived
 * THROUGH THE ROUTER from the list read (`status`, `submittedAt`,
 * `totalAmount`, `currency`), never from the fixture's internals.
 */

function scm(path: string): Promise<Response> {
  return Promise.resolve(
    sampleResponse({ core: 'flat', surface: 'scm', method: 'GET', path }),
  );
}

const inside = (iso: string, w: { from: string; to: string }) => {
  const t = Date.parse(iso);
  return t >= Date.parse(w.from) && t < Date.parse(w.to);
};

async function metrics(period: string) {
  return PerformanceMetricsSchema.parse(
    await (await scm(`/api/v1/procurement/po/metrics?period=${period}`)).json(),
  );
}

async function list(qs: string): Promise<{ rows: PurchaseOrder[]; total: number }> {
  const env = (await (await scm(`/api/v1/procurement/po?${qs}`)).json()) as { data: unknown };
  const page = PoPageSchema.parse(env.data);
  return { rows: page.content, total: page.totalElements };
}

/** KRW minor units = whole won; the seed amounts are `NNN.00`, so exact. */
function wonOf(rows: readonly PurchaseOrder[]): string {
  return rows
    .reduce((acc, p) => acc + BigInt((p.totalAmount ?? '0').split('.')[0]), BigInt(0))
    .toString();
}

describe('sample scm /api/v1/procurement/po/metrics (TASK-MONO-810)', () => {
  it('answers the shared contract shape for both periods — poCount, poAmount:KRW, receivedCount', async () => {
    for (const p of METRICS_PERIODS) {
      const res = await scm(`/api/v1/procurement/po/metrics?period=${p}`);
      expect(res.status, p).toBe(200);
      const parsed = PerformanceMetricsSchema.safeParse(await res.json());
      expect(parsed.success, p).toBe(true);
      expect(parsed.data?.period).toBe(p);
      expect(parsed.data?.timezone).toBe('Asia/Seoul');
      expect(parsed.data?.current).toEqual(SAMPLE_METRICS_WINDOWS[p].current);
      expect(parsed.data?.metrics.map((m) => m.key)).toEqual([
        'poCount',
        'poAmount:KRW',
        'receivedCount',
      ]);
      const amount = parsed.data?.metrics.find((m) => m.key === 'poAmount:KRW');
      expect(amount?.unit).toBe('money');
      expect(amount?.currency).toBe('KRW');
    }
  });

  it('발주 건수 / 금액 = POs submitted in the window that are not CANCELED (re-derived from the list)', async () => {
    const { rows } = await list('page=0&size=100');
    expect(rows.length).toBe(4); // non-vacuous: DRAFT · SUBMITTED · CONFIRMED · CANCELED
    // The control group really exists: a DRAFT (no submittedAt) and a CANCELED one.
    expect(rows.some((r) => r.status === 'DRAFT' && !r.submittedAt)).toBe(true);
    expect(rows.some((r) => r.status === 'CANCELED' && r.submittedAt)).toBe(true);

    let sawNonZero = false;
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      const w = SAMPLE_METRICS_WINDOWS[p];
      for (const [side, win] of [['current', w.current], ['previous', w.previous]] as const) {
        const counted = rows.filter(
          (r) => r.status !== 'CANCELED' && r.submittedAt && inside(r.submittedAt, win),
        );
        if (counted.length > 0) sawNonZero = true;
        expect(m.metrics.find((x) => x.key === 'poCount')?.[side], `${p} ${side}`).toBe(
          String(counted.length),
        );
        expect(m.metrics.find((x) => x.key === 'poAmount:KRW')?.[side], `${p} ${side}`).toBe(
          wonOf(counted),
        );
      }
    }
    expect(sawNonZero).toBe(true);
    // The month: SUBMITTED 100,000.00 + CONFIRMED 62,500.00 (the DRAFT and the
    // August CANCELED one do not count).
    const month = await metrics('month');
    expect(month.metrics.find((x) => x.key === 'poCount')?.current).toBe('2');
    expect(month.metrics.find((x) => x.key === 'poAmount:KRW')?.current).toBe('162500');
  });

  it('입고 완료 is 0 — the sample has no status history and no RECEIVED PO', async () => {
    const { rows } = await list('status=RECEIVED&page=0&size=100');
    expect(rows).toHaveLength(0);
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      const r = m.metrics.find((x) => x.key === 'receivedCount');
      expect(r?.current).toBe('0');
      expect(r?.previous).toBe('0');
    }
  });

  it('the series is every date of the current window, zero-filled, every key, summing to the totals', async () => {
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      expect(m.series.map((s) => s.date), p).toEqual([...SAMPLE_METRICS_WINDOWS[p].dates]);
      for (const key of ['poCount', 'poAmount:KRW', 'receivedCount']) {
        expect(m.series.every((s) => typeof s.values[key] === 'string'), `${p} ${key}`).toBe(true);
        const sum = m.series.reduce((a, s) => a + BigInt(s.values[key]), BigInt(0));
        expect(String(sum), `${p} ${key}`).toBe(m.metrics.find((x) => x.key === key)?.current);
      }
    }
    // 2026-09-11 (KST) carries the SUBMITTED PO; quiet days are "0", not dropped.
    const month = await metrics('month');
    expect(month.series.find((s) => s.date === '2026-09-11')?.values.poCount).toBe('1');
    expect(month.series.find((s) => s.date === '2026-09-11')?.values['poAmount:KRW']).toBe('100000');
    expect(month.series.some((s) => s.values.poCount === '0')).toBe(true);
  });

  it('/po/metrics is not swallowed by the /po/{poId} detail match (no PO_NOT_FOUND)', async () => {
    const res = await scm('/api/v1/procurement/po/metrics?period=month');
    expect(res.status).toBe(200);
    // A real unknown id still 404s through the detail branch.
    expect((await scm('/api/v1/procurement/po/po-nope')).status).toBe(404);
  });

  it('no / unknown period -> not answered (the console always sends one)', async () => {
    expect((await scm('/api/v1/procurement/po/metrics')).status).toBe(503);
    expect((await scm('/api/v1/procurement/po/metrics?period=year')).status).toBe(503);
  });
});

describe('sample scm GET /po — submittedFrom / submittedTo (TASK-MONO-810)', () => {
  const enc = (v: string) => encodeURIComponent(v);

  /** The list the REAL card link opens (`scmPerformanceCardHref`), read from
   *  the sample router: href → its query string → `/api/v1/procurement/po`. */
  async function cardList(data: Parameters<typeof scmPerformanceCardHref>[1], noExclude = false) {
    const card = data.metrics.find((x) => x.key === 'poCount')!;
    const href = scmPerformanceCardHref(card, data)!;
    const qs = new URL(href, 'http://console.local').searchParams;
    expect(qs.get('excludeStatus')).toBe('CANCELED');
    if (noExclude) qs.delete('excludeStatus');
    qs.set('page', '0');
    qs.set('size', '100');
    return list(qs.toString());
  }

  it('🔴 the 발주 건수 card link opens a list whose totalElements = the card’s poCount — both windows; without excludeStatus it differs', async () => {
    const month = await metrics('month');
    const poCount = month.metrics.find((x) => x.key === 'poCount')!;

    // Current window (the card as shown): 2 = 2.
    const cur = await cardList(month);
    expect(cur.rows.map((r) => r.id).sort()).toEqual(['po-sample-0002', 'po-sample-0003']);
    expect(String(cur.total)).toBe(poCount.current);

    // Previous window — the one holding the CANCELED sample (po-sample-0004).
    // Same card-link builder over the previous bounds: 0 = 0 with the
    // exclusion, 1 ≠ 0 without it (so the exclusion is what makes them agree).
    const prevView = { ...month, current: month.previous };
    const prev = await cardList(prevView);
    expect(String(prev.total)).toBe(poCount.previous);
    expect(prev.total).toBe(0);
    const prevAll = await cardList(prevView, true);
    expect(prevAll.total).toBe(1);
    expect(String(prevAll.total)).not.toBe(poCount.previous);
    expect(prevAll.rows.map((r) => r.status)).toEqual(['CANCELED']);
  });

  it('excludeStatus leaves exactly that status out and combines with status', async () => {
    const all = await list('page=0&size=100');
    const noCanceled = await list('excludeStatus=CANCELED&page=0&size=100');
    expect(noCanceled.total).toBe(all.total - 1);
    expect(noCanceled.rows.map((r) => r.status)).not.toContain('CANCELED');
    expect((await list('status=CANCELED&excludeStatus=CANCELED&page=0&size=100')).total).toBe(0);
  });

  it('half-open: a PO submitted exactly at `to` is out, exactly at `from` is in', async () => {
    const at = '2026-09-11T02:00:00Z'; // po-sample-0002's submittedAt
    expect((await list(`submittedTo=${enc(at)}&page=0&size=100`)).rows.map((r) => r.id)).not.toContain(
      'po-sample-0002',
    );
    expect(
      (await list(`submittedFrom=${enc(at)}&page=0&size=100`)).rows.map((r) => r.id),
    ).toContain('po-sample-0002');
  });

  it('never-submitted (DRAFT) POs are outside any window; CANCELED ones inside a window are listed', async () => {
    const { rows } = await list(
      `submittedFrom=${enc('2026-08-01T00:00:00+09:00')}&page=0&size=100`,
    );
    expect(rows.map((r) => r.status)).not.toContain('DRAFT');
    expect(rows.map((r) => r.id)).toContain('po-sample-0004'); // CANCELED, submitted 08-05
  });

  it('🔴 control group: the CANCELED PO sits in the previous-month window, is listed there, and is NOT counted', async () => {
    const w = SAMPLE_METRICS_WINDOWS.month.previous;
    const { rows } = await list(
      `submittedFrom=${enc(w.from)}&submittedTo=${enc(w.to)}&page=0&size=100`,
    );
    // Non-vacuous: the window really holds a submitted-then-canceled PO.
    expect(rows.map((r) => [r.id, r.status])).toEqual([['po-sample-0004', 'CANCELED']]);
    const month = await metrics('month');
    expect(month.metrics.find((x) => x.key === 'poCount')?.previous).toBe('0');
    expect(month.metrics.find((x) => x.key === 'poAmount:KRW')?.previous).toBe('0');
  });

  it('combines with status', async () => {
    const w = SAMPLE_METRICS_WINDOWS.month.current;
    const { rows } = await list(
      `status=CONFIRMED&submittedFrom=${enc(w.from)}&submittedTo=${enc(w.to)}&page=0&size=100`,
    );
    expect(rows.map((r) => r.id)).toEqual(['po-sample-0003']);
  });

  it('an unparseable bound is not answered (the producer would 400; the console never sends one)', async () => {
    expect((await scm('/api/v1/procurement/po?submittedFrom=yesterday')).status).toBe(503);
  });
});
