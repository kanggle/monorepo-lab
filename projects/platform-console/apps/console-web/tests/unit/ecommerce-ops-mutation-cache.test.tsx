import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import {
  QueryClient,
  QueryClientProvider,
  type QueryKey,
} from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * Regression for TASK-PC-FE-126 — "registered/created entity missing from the
 * list until a hard reload".
 *
 * The ecommerce-ops list mutations (seller register, product/promotion/template
 * create+update+delete) all run on a SEPARATE route (`/new`, `/[id]`) and then
 * redirect to a `force-dynamic` list page. The seeded page-0 list query is
 * `refetchOnMount: false` + `staleTime: 30s` with window-focus/interval refetch
 * OFF (see `query-options.ts`). So if a mutation only `invalidateQueries`d the
 * (inactive) list, React Query would: (a) never refetch it on the way out, and
 * (b) on return, IGNORE the fresh SSR seed (`initialData` is dropped when a
 * cache entry already exists) and keep showing the stale pre-mutation snapshot.
 *
 * The fix: mutations `removeQueries` the LIST key (so the fresh SSR seed
 * re-seeds it), while keeping `invalidateQueries` on the DETAIL key (the detail
 * page IS active during an update → seamless background refetch is correct).
 *
 * These tests pin BOTH halves: list cache is REMOVED, detail cache is preserved
 * (invalidated, not removed).
 */

const post = vi.fn();
const put = vi.fn();
const patch = vi.fn();
const del = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: (...a: unknown[]) => post(...a),
    put: (...a: unknown[]) => put(...a),
    patch: (...a: unknown[]) => patch(...a),
    delete: (...a: unknown[]) => del(...a),
  },
}));

import {
  useRegisterSeller,
} from '@/features/ecommerce-ops/hooks/use-ecommerce-sellers';
import {
  useRegisterProduct,
  useUpdateProduct,
  useDeleteProduct,
} from '@/features/ecommerce-ops/hooks/use-ecommerce-products';
import {
  useCreatePromotion,
} from '@/features/ecommerce-ops/hooks/use-ecommerce-promotions';
import {
  useCreateTemplate,
} from '@/features/ecommerce-ops/hooks/use-ecommerce-notifications';
import { DISCOUNT_TYPE_VALUES } from '@/features/ecommerce-ops/api/types';
import {
  TEMPLATE_TYPE_VALUES,
  NOTIFICATION_CHANNEL_VALUES,
} from '@/features/ecommerce-ops/api/notification-types';

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapperFor(qc: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

/** A stale cached list page the SSR seed must be allowed to replace. */
const STALE_LIST = { content: [], page: 0, size: 20, totalElements: 0 };

beforeEach(() => {
  post.mockReset().mockResolvedValue({ id: 'x' });
  put.mockReset().mockResolvedValue({ id: 'x' });
  patch.mockReset().mockResolvedValue({ id: 'x' });
  del.mockReset().mockResolvedValue(undefined);
});

describe('ecommerce-ops mutations DROP the list cache (not just invalidate)', () => {
  it('seller register → list cache removed (fresh SSR seed wins on return)', async () => {
    const qc = makeClient();
    const listKey: QueryKey = ['ecommerce-sellers', 'list', 0, 20];
    qc.setQueryData(listKey, STALE_LIST);

    const { result } = renderHook(() => useRegisterSeller(), {
      wrapper: wrapperFor(qc),
    });
    await result.current.mutateAsync({ sellerId: 's1', displayName: 'S One' });

    await waitFor(() =>
      expect(qc.getQueryData(listKey)).toBeUndefined(),
    );
  });

  it('product create → list cache removed', async () => {
    const qc = makeClient();
    const listKey: QueryKey = ['ecommerce-products', 'list', 0, 20];
    qc.setQueryData(listKey, STALE_LIST);

    const { result } = renderHook(() => useRegisterProduct(), {
      wrapper: wrapperFor(qc),
    });
    await result.current.mutateAsync({
      name: 'P One',
      price: 1000,
      variants: [],
    });

    await waitFor(() => expect(qc.getQueryData(listKey)).toBeUndefined());
  });

  it('promotion create → list cache removed', async () => {
    const qc = makeClient();
    const listKey: QueryKey = ['ecommerce-promotions', 'list', 0, 20];
    qc.setQueryData(listKey, STALE_LIST);

    const { result } = renderHook(() => useCreatePromotion(), {
      wrapper: wrapperFor(qc),
    });
    await result.current.mutateAsync({
      name: 'Promo',
      discountType: DISCOUNT_TYPE_VALUES[0],
      discountValue: 10,
      maxDiscountAmount: 0,
      maxIssuanceCount: 100,
      startDate: '2026-06-23T00:00:00Z',
      endDate: '2026-06-30T00:00:00Z',
    });

    await waitFor(() => expect(qc.getQueryData(listKey)).toBeUndefined());
  });

  it('notification template create → list cache removed', async () => {
    const qc = makeClient();
    const listKey: QueryKey = ['ecommerce-notifications', 'list', 0, 20];
    qc.setQueryData(listKey, STALE_LIST);

    const { result } = renderHook(() => useCreateTemplate(), {
      wrapper: wrapperFor(qc),
    });
    await result.current.mutateAsync({
      type: TEMPLATE_TYPE_VALUES[0],
      channel: NOTIFICATION_CHANNEL_VALUES[0],
      subject: 'Sub',
      body: 'Body',
    });

    await waitFor(() => expect(qc.getQueryData(listKey)).toBeUndefined());
  });
});

describe('ecommerce-ops update PRESERVES the detail cache (invalidate, not remove)', () => {
  it('product update → list removed, detail kept-but-invalidated', async () => {
    const qc = makeClient();
    const listKey: QueryKey = ['ecommerce-products', 'list', 0, 20];
    const detailKey: QueryKey = ['ecommerce-products', 'detail', 'p1'];
    qc.setQueryData(listKey, STALE_LIST);
    qc.setQueryData(detailKey, { id: 'p1', name: 'old' });

    const { result } = renderHook(() => useUpdateProduct(), {
      wrapper: wrapperFor(qc),
    });
    await result.current.mutateAsync({ id: 'p1', body: { name: 'new' } });

    await waitFor(() => expect(qc.getQueryData(listKey)).toBeUndefined());
    // Detail is NOT removed — its data survives (a detail-page refetch, not a drop).
    expect(qc.getQueryData(detailKey)).toEqual({ id: 'p1', name: 'old' });
    expect(qc.getQueryState(detailKey)?.isInvalidated).toBe(true);
  });

  it('product delete → list removed', async () => {
    const qc = makeClient();
    const listKey: QueryKey = ['ecommerce-products', 'list', 0, 20];
    qc.setQueryData(listKey, STALE_LIST);

    const { result } = renderHook(() => useDeleteProduct(), {
      wrapper: wrapperFor(qc),
    });
    await result.current.mutateAsync('p1');

    await waitFor(() => expect(qc.getQueryData(listKey)).toBeUndefined());
  });
});
