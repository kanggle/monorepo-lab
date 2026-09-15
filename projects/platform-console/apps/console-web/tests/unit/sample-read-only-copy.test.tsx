/**
 * R1ⓐ refusal copy — ONE code → copy mapping (TASK-PC-FE-282 AC-3 / AC-9).
 *
 * 🔴🔴 Why the copy may not ride in the response message: four gateway cores
 *    overwrite a 403 message (`'not permitted'`) and the route handlers pass that
 *    overwritten message on. Only the CODE survives. So the copy must come from
 *    `messageForCode`, and every renderer must reach it.
 *
 * The write-error renderers in `src/features/**` fall into three kinds
 * (counted in TASK-PC-FE-282 § Implementation notes):
 *
 *   K1  `messageForCode(err.code, …)`  — looks the code up
 *   K2  prints `err.message` (e.g. the `default:` branch of `approvalErrorMessage`)
 *   K3  prints a fixed string and never reads the error
 *
 * K1 gets the copy from the mapping; K2 gets it because the single client entry
 * point (`shared/api/client.ts`) rewrites a sample code's message from the same
 * mapping; K3 cannot — so the shell's `SampleRefusalNotice` says it, on a signal
 * published by that same entry point. Each kind has a cell below.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { apiClient } from '@/shared/api/client';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { SAMPLE_NOT_READY, SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { sampleResponse } from '@/shared/sample/router';
import { subscribeSampleRefusal } from '@/shared/lib/sample-refusal';
import { approvalErrorMessage } from '@/features/erp-ops/components/approval-error';
import { SampleRefusalNotice } from '@/widgets/sample-visitor/SampleRefusalNotice';

const READ_ONLY_COPY = '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다';
const NOT_READY_COPY = '이 화면의 샘플 데이터는 준비 중입니다';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('the mapping', () => {
  it('SAMPLE_READ_ONLY → the refusal copy, verbatim', () => {
    expect(messageForCode(SAMPLE_READ_ONLY)).toBe(READ_ONLY_COPY);
  });

  it('SAMPLE_NOT_READY → the not-ready copy, verbatim', () => {
    expect(messageForCode(SAMPLE_NOT_READY)).toBe(NOT_READY_COPY);
  });

  it('🔴 the copy is NOT in the router response — only the code is', async () => {
    const res = sampleResponse({
      core: 'iam',
      surface: 'accounts',
      method: 'POST',
      path: '/api/admin/accounts/a/lock',
    });
    const body = (await res.json()) as { code: string; message: string };
    expect(res.status).toBe(403);
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    expect(body.message).not.toContain('샘플');
  });
});

describe('the single client entry point (shared/api/client.ts)', () => {
  let published: string[];
  let unsubscribe: () => void;

  beforeEach(() => {
    published = [];
    unsubscribe = subscribeSampleRefusal((code) => published.push(code));
  });
  afterEach(() => unsubscribe());

  it('a refused write (403 SAMPLE_READ_ONLY, message overwritten upstream) → ApiError carrying the copy + one refusal signal', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(403, { code: SAMPLE_READ_ONLY, message: 'not permitted' })),
    );
    const err = await apiClient.post('/api/accounts/a/lock', { reason: 'x' }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe(SAMPLE_READ_ONLY);
    expect((err as ApiError).message).toBe(READ_ONLY_COPY);
    expect(published).toEqual([SAMPLE_READ_ONLY]);
  });

  it('a pending read (503 SAMPLE_NOT_READY) → the not-ready copy, and NO refusal signal', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(503, { code: SAMPLE_NOT_READY, message: 'wms unavailable' })),
    );
    const err = await apiClient.get('/api/wms/alerts').catch((e) => e);
    expect((err as ApiError).message).toBe(NOT_READY_COPY);
    expect(published).toEqual([]);
  });

  it('🔵 control — a real 403 (PERMISSION_DENIED) keeps its message and publishes nothing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(403, { code: 'PERMISSION_DENIED', message: 'not permitted' })),
    );
    const err = await apiClient.post('/api/accounts/a/lock', {}).catch((e) => e);
    expect((err as ApiError).code).toBe('PERMISSION_DENIED');
    expect((err as ApiError).message).toBe('not permitted');
    expect(published).toEqual([]);
  });
});

describe('every renderer kind reaches the copy', () => {
  // What a component actually receives after the client entry point.
  async function refusedError(): Promise<ApiError> {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(403, { code: SAMPLE_READ_ONLY, message: 'not permitted' })),
    );
    return (await apiClient
      .post('/api/erp/approval/requests/r/approve', {})
      .catch((e: unknown) => e)) as ApiError;
  }

  it('K1 — messageForCode(err.code, err.message)', async () => {
    const err = await refusedError();
    expect(messageForCode(err.code, err.message)).toBe(READ_ONLY_COPY);
  });

  it('K2 — a domain mapper whose default branch prints err.message (approvalErrorMessage)', async () => {
    const err = await refusedError();
    expect(approvalErrorMessage(err, 'approve')).toBe(READ_ONLY_COPY);
  });

  it('K3 — a fixed-string renderer: the shell notice says it instead', async () => {
    render(<SampleRefusalNotice />);
    expect(screen.queryByTestId('sample-visitor-refusal')).toBeNull();
    await act(async () => {
      await refusedError();
    });
    expect(screen.getByTestId('sample-visitor-refusal')).toHaveTextContent(READ_ONLY_COPY);
  });
});
