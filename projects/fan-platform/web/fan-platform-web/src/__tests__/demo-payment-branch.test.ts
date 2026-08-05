import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * TASK-FAN-FE-015 — the demo payment branch.
 *
 * Both directions are asserted. A test that only proves the demo path works cannot tell
 * "the branch opened" from "the real path is gone", and the thing being branched around is
 * the payment window.
 */

const { requestPayment, requestIssueBillingKey } = vi.hoisted(() => ({
  requestPayment: vi.fn(),
  requestIssueBillingKey: vi.fn(),
}));
vi.mock('@portone/browser-sdk/v2', () => ({ requestPayment, requestIssueBillingKey }));

function mockConfig(demoPayment: unknown, ok = true) {
  const fetchMock = vi.fn(async () => ({
    ok,
    json: async () => ({ demoPayment }),
  })) as unknown as typeof fetch;
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal('crypto', { randomUUID: () => '11111111-2222-3333-4444-555555555555' });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe('requestPortOnePayment — demo branch', () => {
  it('데모이면 결제창을 열지 않고 성공한다 (백엔드 목 게이트웨이가 승인한다)', async () => {
    mockConfig(true);
    const { requestPortOnePayment } = await import('@/features/membership/lib/portone-checkout');

    const result = await requestPortOnePayment('프리미엄 1개월', 17900);

    expect(result.ok).toBe(true);
    // The whole defect: the SDK must not be reached, because reaching it is what produced
    // "결제 모듈이 설정되지 않았습니다" and stopped the request before the backend saw it.
    expect(requestPayment).not.toHaveBeenCalled();
  });

  it('🔴 음성 대조 — 데모가 아니면 실제 PortOne 결제창을 연다', async () => {
    mockConfig(false);
    vi.stubEnv('NEXT_PUBLIC_PORTONE_STORE_ID', 'store-real');
    vi.stubEnv('NEXT_PUBLIC_PORTONE_CHANNEL_KEY', 'channel-real');
    vi.resetModules();
    requestPayment.mockResolvedValue({ code: undefined });
    const { requestPortOnePayment } = await import('@/features/membership/lib/portone-checkout');

    await requestPortOnePayment('프리미엄 1개월', 17900);

    // If this ever stops being called, the demo branch has become the default and a real
    // deployment silently reports every checkout as paid without charging anyone.
    expect(requestPayment).toHaveBeenCalledTimes(1);
  });

  it('설정 조회가 실패하면 데모가 아닌 쪽으로 폴백한다', async () => {
    mockConfig(true, /* ok */ false);
    vi.resetModules();
    const { requestPortOnePayment } = await import('@/features/membership/lib/portone-checkout');

    const result = await requestPortOnePayment('프리미엄 1개월', 17900);

    // Keys are unset in this test, so the pre-existing guard is what answers — which is the
    // point: an unreachable config behaves exactly as it did before this change. Failing the
    // other way would let one bad response turn a real storefront into "everything is paid".
    expect(result.ok).toBe(false);
    expect(requestPayment).not.toHaveBeenCalled();
  });

  it('설정 응답이 boolean true 가 아니면 데모가 아니다', async () => {
    mockConfig('1'); // a string, not the boolean the route emits
    vi.resetModules();
    const { requestPortOnePayment } = await import('@/features/membership/lib/portone-checkout');

    const result = await requestPortOnePayment('프리미엄 1개월', 17900);

    expect(result.ok).toBe(false);
    expect(requestPayment).not.toHaveBeenCalled();
  });
});

describe('requestIssueBillingKey — demo branch', () => {
  it('데모이면 발급창 없이 빌링키를 반환한다 (자동갱신도 같은 사전 가드에 막혀 있었다)', async () => {
    mockConfig(true);
    vi.resetModules();
    const { requestIssueBillingKey: issue } = await import(
      '@/features/membership/lib/portone-billing-key'
    );

    const result = await issue();

    expect(result.ok).toBe(true);
    expect(requestIssueBillingKey).not.toHaveBeenCalled();
  });

  it('🔴 음성 대조 — 데모가 아니면 실제 발급창을 연다', async () => {
    mockConfig(false);
    vi.stubEnv('NEXT_PUBLIC_PORTONE_STORE_ID', 'store-real');
    vi.stubEnv('NEXT_PUBLIC_PORTONE_CHANNEL_KEY', 'channel-real');
    vi.resetModules();
    requestIssueBillingKey.mockResolvedValue({ code: undefined, billingKey: 'bkey-real' });
    const { requestIssueBillingKey: issue } = await import(
      '@/features/membership/lib/portone-billing-key'
    );

    await issue();

    expect(requestIssueBillingKey).toHaveBeenCalledTimes(1);
  });
});

describe('AC-4 — the flag is read at runtime, not inlined at build time', () => {
  it('플래그는 `/api/payment-config` 조회로 온다 — NEXT_PUBLIC_* 이 아니다', async () => {
    const fetchMock = mockConfig(true);
    vi.resetModules();
    const { requestPortOnePayment } = await import('@/features/membership/lib/portone-checkout');

    await requestPortOnePayment('프리미엄 1개월', 17900);

    // Next inlines NEXT_PUBLIC_* at build time, so a flag shaped that way would freeze the
    // answer into the image and compose env could never change it — the exact trap this
    // ticket's AC-4 names. Pinning the fetch is what keeps a later "simplification" to
    // process.env.NEXT_PUBLIC_DEMO_PAYMENT from passing silently.
    expect(fetchMock).toHaveBeenCalledWith('/api/payment-config', { cache: 'no-store' });
  });

  it('소스에 NEXT_PUBLIC_ 데모 플래그가 존재하지 않는다', async () => {
    const { readFileSync } = await import('node:fs');
    const { join } = await import('node:path');
    // vitest's root is the package dir, so paths resolve the same in CI and locally.
    for (const rel of [
      'src/features/membership/lib/demo-payment.ts',
      'src/features/membership/lib/portone-checkout.ts',
      'src/features/membership/lib/portone-billing-key.ts',
      'src/app/api/payment-config/route.ts',
    ]) {
      const src = readFileSync(join(process.cwd(), rel), 'utf8');
      // Non-vacuity: the file must actually have been read.
      expect(src.length).toBeGreaterThan(0);
      expect(src).not.toMatch(/NEXT_PUBLIC_[A-Z_]*DEMO/);
    }
  });
});

describe('randomUuid — 안전 컨텍스트가 아닌 곳(HTTP)에서도 id 를 만든다', () => {
  it('crypto.randomUUID 가 없으면 getRandomValues 로 v4 를 만든다', async () => {
    // 평문 HTTP 로 서빙되는 데모의 실제 조건. `randomUUID` 는 secure context 전용이라
    // 그 브라우저에는 아예 존재하지 않는다 — 라이브에서 이 정확한 TypeError 를 받았다.
    vi.stubGlobal('crypto', {
      getRandomValues: (a: Uint8Array) => {
        a.fill(0xab);
        return a;
      },
    });
    vi.resetModules();
    const { randomUuid } = await import('@/shared/lib/random-id');

    const id = randomUuid();

    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it('randomUUID 가 있으면 그것을 그대로 쓴다', async () => {
    const randomUUID = vi.fn(() => 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee');
    vi.stubGlobal('crypto', { randomUUID, getRandomValues: () => new Uint8Array(16) });
    vi.resetModules();
    const { randomUuid } = await import('@/shared/lib/random-id');

    expect(randomUuid()).toBe('aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee');
    expect(randomUUID).toHaveBeenCalled();
  });
});
