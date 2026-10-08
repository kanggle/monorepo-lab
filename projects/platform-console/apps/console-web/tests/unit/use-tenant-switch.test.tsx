import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-MONO-780 — a tenant switch must re-scope the wms sections' CLIENT
 * caches, not only the server render.
 *
 * The server half was already right: the wms gateway core sends
 * `getDomainFacingToken()` (the assumed token of the active tenant — pinned in
 * `wms-active-tenant-token.test.ts`). What kept the previous tenant's rows on
 * screen is the client half: the wms lists are seeded from the server render
 * (initialData + staleTime 30 s + `refetchOnMount: false`) under a key with NO
 * tenant slot, and React Query ignores a refreshed initialData for a key it
 * already holds — the PC-FE-044 stale-list shape, measured here for wms.
 *
 * Bite: delete the `WMS_TENANT_SCOPED_QUERY_ROOTS` loop from `useTenantSwitch`
 * and the «mounted» and «visited before the switch» cells go red (they keep
 * demo-corp's `SO-DEMO-0001`).
 */

const { postMock, getMock, refreshMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
  refreshMock: vi.fn(),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: refreshMock }),
}));
vi.mock('@/shared/api/client', () => ({
  apiClient: { post: postMock, get: getMock },
}));

import {
  useTenantSwitch,
  WMS_TENANT_SCOPED_QUERY_ROOTS,
} from '@/shared/api/use-tenant-switch';
import {
  ordersKey,
  useOutboundOrders,
} from '@/features/wms-outbound-ops/hooks/use-outbound-ops';
import {
  alertsKey,
  asnsKey,
  inventoryKey,
  shipmentsKey,
} from '@/features/wms-ops/hooks/use-wms-ops';
import type { OutboundOrderPage } from '@/features/wms-outbound-ops/api/types';

function page(orderNo: string, tenant: string): OutboundOrderPage {
  return {
    content: [{ orderId: `${tenant}-1`, orderNo, status: 'PICKING' }],
    page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
  } as OutboundOrderPage;
}

/** demo-corp's seeded manual order vs ecommerce's fulfillment order (24th window). */
const DEMO_CORP = page('SO-DEMO-0001', 'demo-corp');
const ECOMMERCE = page('FUL-ECOM-0001', 'ecommerce');

function harness() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return { qc, wrapper };
}

const orderNos = (p: OutboundOrderPage | undefined) =>
  (p?.content ?? []).map((r) => r.orderNo);

beforeEach(() => {
  postMock.mockReset();
  getMock.mockReset();
  refreshMock.mockReset();
});

describe('useTenantSwitch — wms client caches follow the active tenant (TASK-MONO-780)', () => {
  it('names the real wms query roots (a rename in features/ turns this red)', () => {
    const roots: readonly string[] = WMS_TENANT_SCOPED_QUERY_ROOTS;
    expect(roots).toContain(ordersKey({})[0]);
    for (const key of [
      inventoryKey({}),
      alertsKey({}),
      shipmentsKey({}),
      asnsKey({}),
    ]) {
      expect(roots).toContain(key[0]);
    }
  });

  it('a MOUNTED outbound list re-fetches after the switch → ecommerce rows, not SO-DEMO-0001', async () => {
    const { wrapper } = harness();
    // The server rendered demo-corp's page; the proxy now answers with the
    // new tenant's token (the cookie changed with the switch).
    getMock.mockResolvedValue(ECOMMERCE);
    postMock.mockResolvedValue({ ok: true, activeTenant: 'ecommerce' });

    const { result } = renderHook(
      () => ({ list: useOutboundOrders({}, DEMO_CORP), sw: useTenantSwitch() }),
      { wrapper },
    );
    expect(orderNos(result.current.list.data)).toEqual(['SO-DEMO-0001']);
    expect(getMock).not.toHaveBeenCalled(); // seeded — no client fetch yet

    await act(async () => {
      await result.current.sw.mutateAsync('ecommerce');
    });

    await waitFor(() =>
      expect(orderNos(result.current.list.data)).toEqual(['FUL-ECOM-0001']),
    );
    expect(getMock).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/wms\/outbound\?/),
    );
    expect(refreshMock).toHaveBeenCalled();
  });

  it('a list visited BEFORE the switch (now unmounted) is not served from cache afterwards', async () => {
    const { wrapper, qc } = harness();
    postMock.mockResolvedValue({ ok: true, activeTenant: 'ecommerce' });

    // 1. Operator opens /wms/outbound under demo-corp, then navigates away.
    const first = renderHook(() => useOutboundOrders({}, DEMO_CORP), { wrapper });
    expect(orderNos(first.result.current.data)).toEqual(['SO-DEMO-0001']);
    first.unmount();

    // 2. Switches tenant from another page.
    const sw = renderHook(() => useTenantSwitch(), { wrapper });
    await act(async () => {
      await sw.result.current.mutateAsync('ecommerce');
    });

    // 3. Comes back: the server render now seeds ecommerce's page.
    const back = renderHook(() => useOutboundOrders({}, ECOMMERCE), { wrapper });
    expect(orderNos(back.result.current.data)).toEqual(['FUL-ECOM-0001']);
    expect(qc.getQueryData(ordersKey({}))).toEqual(ECOMMERCE);
  });

  it('switching BACK to demo-corp shows demo-corp again (follows the selection, not «ecommerce fixed»)', async () => {
    const { wrapper } = harness();
    postMock.mockResolvedValue({ ok: true });
    getMock.mockResolvedValueOnce(ECOMMERCE).mockResolvedValueOnce(DEMO_CORP);

    const { result } = renderHook(
      () => ({ list: useOutboundOrders({}, DEMO_CORP), sw: useTenantSwitch() }),
      { wrapper },
    );

    await act(async () => {
      await result.current.sw.mutateAsync('ecommerce');
    });
    await waitFor(() =>
      expect(orderNos(result.current.list.data)).toEqual(['FUL-ECOM-0001']),
    );

    await act(async () => {
      await result.current.sw.mutateAsync('demo-corp');
    });
    await waitFor(() =>
      expect(orderNos(result.current.list.data)).toEqual(['SO-DEMO-0001']),
    );
  });

  it('a REFUSED switch (403 TENANT_FORBIDDEN) leaves the cached list untouched', async () => {
    const { wrapper, qc } = harness();
    postMock.mockRejectedValue(Object.assign(new Error('forbidden'), { status: 403 }));

    const { result } = renderHook(
      () => ({ list: useOutboundOrders({}, DEMO_CORP), sw: useTenantSwitch() }),
      { wrapper },
    );
    await act(async () => {
      await result.current.sw.mutateAsync('ecommerce').catch(() => undefined);
    });

    expect(getMock).not.toHaveBeenCalled();
    expect(qc.getQueryData(ordersKey({}))).toEqual(DEMO_CORP);
    expect(refreshMock).not.toHaveBeenCalled();
  });
});
