import { describe, expect, it } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_METRICS_WINDOWS } from '@/shared/sample/fixtures/metrics-windows';
import { METRICS_PERIODS } from '@/shared/lib/kst-period';
import { PerformanceMetricsSchema } from '@/shared/api/performance-metrics';
import {
  ApprovalListResponseSchema,
  ApprovalDetailResponseSchema,
  type ApprovalRequest,
} from '@/features/erp-ops/api/approval-types';

/**
 * TASK-MONO-809 — the anonymous visitor's erp «실적» strip (ADR-MONO-074 sample
 * mode). The strip is derived from the SAME seed requests the 결재함 shows, so
 * the expectations below are re-derived THROUGH THE ROUTER from the list and
 * the detail reads (status, `submittedAt`, `finalizedAt`), never from the
 * fixture's internals.
 */

function approval(path: string): Promise<Response> {
  return Promise.resolve(
    sampleResponse({ core: 'flat', surface: 'erp_approval', method: 'GET', path }),
  );
}

const inside = (iso: string, w: { from: string; to: string }) => {
  const t = Date.parse(iso);
  return t >= Date.parse(w.from) && t < Date.parse(w.to);
};

async function metrics(period: string) {
  return PerformanceMetricsSchema.parse(
    await (await approval(`/api/erp/approval/metrics?period=${period}`)).json(),
  );
}

async function allRequests(): Promise<ApprovalRequest[]> {
  const list = ApprovalListResponseSchema.parse(
    await (await approval('/api/erp/approval/requests?page=0&size=100')).json(),
  );
  const out: ApprovalRequest[] = [];
  for (const s of list.data) {
    const res = await approval(`/api/erp/approval/requests/${s.id}`);
    out.push(ApprovalDetailResponseSchema.parse(await res.json()).data);
  }
  return out;
}

const KEYS = ['submitted', 'approved', 'rejected'] as const;

describe('sample erp /api/erp/approval/metrics (TASK-MONO-809)', () => {
  it('answers the shared contract shape for both periods — three count metrics', async () => {
    for (const p of METRICS_PERIODS) {
      const res = await approval(`/api/erp/approval/metrics?period=${p}`);
      expect(res.status, p).toBe(200);
      const parsed = PerformanceMetricsSchema.safeParse(await res.json());
      expect(parsed.success, p).toBe(true);
      expect(parsed.data?.period).toBe(p);
      expect(parsed.data?.timezone).toBe('Asia/Seoul');
      expect(parsed.data?.metrics.map((m) => m.key)).toEqual([...KEYS]);
      expect(parsed.data?.metrics.every((m) => m.unit === 'count' && m.currency === null)).toBe(true);
    }
  });

  it('상신 / 승인 / 반려 equal the sample requests (submittedAt · final status at finalizedAt) in each window', async () => {
    const requests = await allRequests();
    expect(requests.length).toBeGreaterThan(0); // non-vacuous
    const events = {
      submitted: requests.filter((r) => r.submittedAt).map((r) => r.submittedAt as string),
      approved: requests
        .filter((r) => r.status === 'APPROVED' && r.finalizedAt)
        .map((r) => r.finalizedAt as string),
      rejected: requests
        .filter((r) => r.status === 'REJECTED' && r.finalizedAt)
        .map((r) => r.finalizedAt as string),
    };
    // The seed really has all three kinds somewhere (otherwise the check is vacuous).
    for (const k of KEYS) expect(events[k].length, k).toBeGreaterThan(0);

    let sawNonZero = false;
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      const w = SAMPLE_METRICS_WINDOWS[p];
      for (const k of KEYS) {
        for (const [side, win] of [['current', w.current], ['previous', w.previous]] as const) {
          const want = events[k].filter((at) => inside(at, win)).length;
          if (want > 0) sawNonZero = true;
          expect(m.metrics.find((x) => x.key === k)?.[side], `${p} ${k} ${side}`).toBe(String(want));
        }
      }
    }
    expect(sawNonZero).toBe(true);
  });

  it('승인 counts a FINAL approval only — the IN_REVIEW request’s stage-0 approval is not one', async () => {
    const requests = await allRequests();
    const inReview = requests.find((r) => r.status === 'IN_REVIEW');
    expect(inReview).toBeDefined();
    const month = await metrics('month');
    const approvedTotal = month.metrics
      .filter((x) => x.key === 'approved')
      .reduce((acc, x) => acc + BigInt(x.current) + BigInt(x.previous), BigInt(0));
    // Only the one APPROVED request (finalized 2026-08-02, the previous month
    // window) contributes — the stage-0 approval of the IN_REVIEW one does not.
    expect(requests.filter((r) => r.status === 'APPROVED')).toHaveLength(1);
    expect(String(approvedTotal)).toBe('1');
  });

  it('the series is every date of the current window, zero-filled, and sums to the current totals', async () => {
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      expect(m.series.map((s) => s.date), p).toEqual([...SAMPLE_METRICS_WINDOWS[p].dates]);
      for (const key of KEYS) {
        const sum = m.series.reduce((a, s) => a + BigInt(s.values[key]), BigInt(0));
        expect(String(sum), `${p} ${key}`).toBe(m.metrics.find((x) => x.key === key)?.current);
      }
    }
    // Quiet days are present as "0", not dropped; busy days carry the count.
    const month = await metrics('month');
    expect(month.series.some((s) => s.values.submitted === '0')).toBe(true);
    expect(month.series.find((s) => s.date === '2026-09-10')?.values.submitted).toBe('1');
  });

  it('/metrics is not swallowed by the /requests/{id} detail match', async () => {
    const res = await approval('/api/erp/approval/requests/metrics?period=month');
    expect(res.status).toBe(404);
  });

  it('no / unknown period -> not answered (the console always sends one)', async () => {
    expect((await approval('/api/erp/approval/metrics')).status).toBe(503);
    expect((await approval('/api/erp/approval/metrics?period=year')).status).toBe(503);
  });
});
