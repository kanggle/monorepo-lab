import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useCatalog } from '@/features/catalog';
import type { CatalogState } from '@/shared/api/registry-types';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('useCatalog', () => {
  it('serves the SSR-provided initial catalog immediately', () => {
    const initial: CatalogState = {
      products: [
        {
          productKey: 'iam',
          displayName: 'IAM',
          available: true,
          tenants: ['wms'],
          baseRoute: '/iam',
        },
      ],
      degraded: false,
    };
    const { result } = renderHook(() => useCatalog(initial), {
      wrapper: wrapper(),
    });
    expect(result.current.data).toEqual(initial);
  });

  it('re-queries /api/registry and parses the envelope', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          products: [
            {
              productKey: 'wms',
              displayName: 'WMS',
              available: true,
              tenants: ['wms'],
              baseRoute: '/wms',
            },
          ],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const degradedInitial: CatalogState = { products: [], degraded: true };
    const { result } = renderHook(() => useCatalog(degradedInitial), {
      wrapper: wrapper(),
    });

    await waitFor(() =>
      expect(result.current.data?.products[0]?.productKey).toBe('wms'),
    );
    expect(result.current.data?.degraded).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith('/api/registry', expect.anything());
  });
});
