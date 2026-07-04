import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin subscription proxy routes
 * (`app/api/subscriptions/route.ts` POST + `.../[domainKey]/status/route.ts`
 * PATCH). The api layer (createSubscription / changeSubscriptionStatus) is
 * mocked; body/param validation + error mapping are asserted in isolation.
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const createSubscription = vi.fn();
const changeSubscriptionStatus = vi.fn();
vi.mock('@/features/subscriptions/api/subscriptions-api', () => ({
  createSubscription: (...a: unknown[]) => createSubscription(...a),
  changeSubscriptionStatus: (...a: unknown[]) => changeSubscriptionStatus(...a),
}));

import { POST } from '@/app/api/subscriptions/route';
import { PATCH } from '@/app/api/subscriptions/[domainKey]/status/route';
import { ApiError, SubscriptionsUnavailableError } from '@/shared/api/errors';

function post(body: unknown) {
  return new Request('http://console.local/api/subscriptions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
function patch(body: unknown) {
  return new Request('http://console.local/api/subscriptions/wms/status', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
const params = (domainKey: string) => ({ params: Promise.resolve({ domainKey }) });

const RESULT = {
  tenantId: 'acme-corp',
  domainKey: 'wms',
  previousStatus: null,
  currentStatus: 'ACTIVE',
  occurredAt: '2026-07-04T10:00:00Z',
};

beforeEach(() => {
  createSubscription.mockReset();
  changeSubscriptionStatus.mockReset();
});

describe('POST /api/subscriptions (subscribe)', () => {
  it('201 with the producer result on a valid body', async () => {
    createSubscription.mockResolvedValue(RESULT);
    const res = await POST(post({ domainKey: 'wms', reason: '활성화' }));
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual(RESULT);
    expect(createSubscription).toHaveBeenCalledWith('wms', '활성화');
  });

  it('422 on an unknown domain key (iam is not subscribable)', async () => {
    const res = await POST(post({ domainKey: 'iam', reason: 'x' }));
    expect(res.status).toBe(422);
    expect(createSubscription).not.toHaveBeenCalled();
  });

  it('403 PERMISSION_DENIED passes through', async () => {
    createSubscription.mockRejectedValue(
      new ApiError(403, 'PERMISSION_DENIED', 'no'),
    );
    const res = await POST(post({ domainKey: 'wms', reason: 'x' }));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('PERMISSION_DENIED');
  });

  it('409 SUBSCRIPTION_ALREADY_EXISTS passes through (client offers resume)', async () => {
    createSubscription.mockRejectedValue(
      new ApiError(409, 'SUBSCRIPTION_ALREADY_EXISTS', 'exists'),
    );
    const res = await POST(post({ domainKey: 'wms', reason: 'x' }));
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('SUBSCRIPTION_ALREADY_EXISTS');
  });

  it('503 on SubscriptionsUnavailableError', async () => {
    createSubscription.mockRejectedValue(
      new SubscriptionsUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await POST(post({ domainKey: 'wms', reason: 'x' }));
    expect(res.status).toBe(503);
  });
});

describe('PATCH /api/subscriptions/[domainKey]/status', () => {
  it('200 on a valid status transition', async () => {
    changeSubscriptionStatus.mockResolvedValue({
      ...RESULT,
      currentStatus: 'SUSPENDED',
    });
    const res = await PATCH(patch({ status: 'SUSPENDED', reason: '중지' }), params('wms'));
    expect(res.status).toBe(200);
    expect(changeSubscriptionStatus).toHaveBeenCalledWith('wms', 'SUSPENDED', '중지');
  });

  it('422 on an unknown domainKey path param', async () => {
    const res = await PATCH(patch({ status: 'SUSPENDED', reason: 'x' }), params('iam'));
    expect(res.status).toBe(422);
    expect(changeSubscriptionStatus).not.toHaveBeenCalled();
  });

  it('422 on an invalid status', async () => {
    const res = await PATCH(patch({ status: 'BOGUS', reason: 'x' }), params('wms'));
    expect(res.status).toBe(422);
    expect(changeSubscriptionStatus).not.toHaveBeenCalled();
  });

  it('409 SUBSCRIPTION_TRANSITION_INVALID passes through', async () => {
    changeSubscriptionStatus.mockRejectedValue(
      new ApiError(409, 'SUBSCRIPTION_TRANSITION_INVALID', 'bad'),
    );
    const res = await PATCH(patch({ status: 'ACTIVE', reason: 'x' }), params('wms'));
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('SUBSCRIPTION_TRANSITION_INVALID');
  });
});
