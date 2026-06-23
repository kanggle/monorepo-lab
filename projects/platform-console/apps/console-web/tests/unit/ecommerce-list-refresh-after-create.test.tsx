import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  useNotificationTemplates,
  useCreateTemplate,
  notificationsKey,
} from '@/features/ecommerce-ops/hooks/use-ecommerce-notifications';
import type { NotificationTemplateList } from '@/features/ecommerce-ops/api/notification-types';

/**
 * TASK-PC-FE-126 — the ecommerce-ops list does not refresh after a cross-page
 * create.
 *
 * The seeded list query uses `staleTime: 30s` + `refetchOnMount: false` (the SSR
 * seed is authoritative on the first visit). Create/update, however, happen on a
 * SEPARATE `/new` (or `[id]/edit`) page, so when the mutation's `invalidate`
 * fires the list page is UNMOUNTED and its query is INACTIVE. An inactive query
 * populated only from the SSR seed (it never actually fetched) is NOT refetched
 * by `invalidateQueries`/`refetchQueries` — so the operator returns to a stale
 * list missing the just-created row (until staleTime elapses / a hard reload).
 *
 * The fix drops the INACTIVE list cache on mutation success
 * (`removeQueries({ type: 'inactive' })`), so the next mount re-seeds from the
 * fresh SSR render (the forms call `router.refresh()` on success). A mounted
 * list is still refreshed in the background by the retained `invalidateQueries`
 * (no loading flash). This test reproduces the exact sequence.
 */

const SEED_ONE_ROW: NotificationTemplateList = {
  content: [
    {
      templateId: 't-1',
      type: 'ORDER_PLACED',
      channel: 'EMAIL',
      subject: '기존 템플릿',
      createdAt: '2026-06-23T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

// What a fresh SSR render returns AFTER the create (router.refresh()).
const SSR_TWO_ROWS: NotificationTemplateList = {
  content: [
    SEED_ONE_ROW.content[0],
    {
      templateId: 't-2',
      type: 'PAYMENT_COMPLETED',
      channel: 'PUSH',
      subject: '새로 등록한 템플릿',
      createdAt: '2026-06-23T01:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ecommerce-ops list refresh after a cross-page create (TASK-PC-FE-126)', () => {
  it('drops the inactive list cache so a remount shows the new row from the fresh SSR seed', async () => {
    // POST (create) → { templateId }. No GET is expected — the fix relies on the
    // fresh SSR seed at remount, not a client refetch of the inactive query.
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      Promise.resolve(
        init?.method === 'POST'
          ? jsonResponse({ templateId: 't-2' }, 201)
          : jsonResponse(SSR_TWO_ROWS),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    // A SINGLE QueryClient shared across the hooks (one browser session).
    const qc = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    );
    const params = { page: 0, size: 20 };
    const key = notificationsKey(params);

    // 1) List page mounts with the SSR seed (1 row). seeded → initialData,
    //    staleTime 30s, refetchOnMount false ⇒ NO fetch fired.
    const list = renderHook(
      () => useNotificationTemplates(params, SEED_ONE_ROW),
      { wrapper },
    );
    expect(list.result.current.data?.content).toHaveLength(1);
    expect(fetchMock).not.toHaveBeenCalled();

    // 2) Operator navigates to /new → the list page unmounts (query inactive).
    list.unmount();
    expect(qc.getQueryData<NotificationTemplateList>(key)?.content).toHaveLength(1);

    // 3) Create on the /new page.
    const create = renderHook(() => useCreateTemplate(), { wrapper });
    create.result.current.mutate({
      type: 'PAYMENT_COMPLETED',
      channel: 'PUSH',
      subject: '새로 등록한 템플릿',
      body: '본문',
    });
    await waitFor(() => expect(create.result.current.isSuccess).toBe(true));

    // The stale INACTIVE cache must have been dropped (the fix). Pre-fix this
    // query persists with the stale 1-row payload.
    await waitFor(() => {
      expect(qc.getQueryData<NotificationTemplateList>(key)).toBeUndefined();
    });

    // 4) Operator returns to the list → it remounts. router.refresh() produced a
    //    fresh SSR seed (2 rows); with the stale cache gone, the seed is used and
    //    the new row shows. Pre-fix, the stale 1-row cache would shadow the seed.
    const back = renderHook(
      () => useNotificationTemplates(params, SSR_TWO_ROWS),
      { wrapper },
    );
    expect(back.result.current.data?.content).toHaveLength(2);
    expect(back.result.current.data?.content[1].subject).toBe('새로 등록한 템플릿');
  });
});
