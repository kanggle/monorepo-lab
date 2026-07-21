import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  ProductsScreen,
  ProductDetail,
} from '@/features/ecommerce-ops';
import type {
  ProductList,
  ProductDetailData,
} from '@/features/ecommerce-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/ecommerce-ops` component behaviour (TASK-PC-FE-081 — list + detail
 * + variant inline CRUD + stock adjust):
 *   - product table (status filter + pagination, re-query via proxy)
 *   - delete is CONFIRM-GATED (no one-click delete)
 *   - variant add/update/delete + stock adjust are confirm/inline-gated
 *   - 503 degrade / 409 inline; WCAG AA axe-clean + Escape cancels dialog
 *
 * Client calls the same-origin `/api/ecommerce/products/**` proxy via `fetch`
 * (mocked, routed by URL).
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
}));

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const LIST: ProductList = {
  content: [
    { id: 'p-1', name: 'Tee', status: 'ON_SALE', price: 12000, sellerId: 's-1' },
  ],
  page: 0,
  size: 20,
  totalElements: 40,
};

const DETAIL: ProductDetailData = {
  id: 'p-1',
  name: 'Tee',
  description: 'a tee',
  status: 'ON_SALE',
  price: 12000,
  sellerId: 's-1',
  images: [],
  variants: [{ id: 'v-1', optionName: 'M', stock: 10, additionalPrice: 0 }],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function noContent() {
  return new Response(null, { status: 204 });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ProductsScreen — table + filter + delete confirm-gate', () => {
  it('renders the seeded products + register link', () => {
    render(<ProductsScreen products={LIST} />, { wrapper: wrapper() });
    expect(
      screen.getByRole('heading', { name: 'E-Commerce 상품' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('product-table')).toBeInTheDocument();
    expect(screen.getByTestId('product-row-status-0')).toHaveTextContent(
      'ON_SALE',
    );
    expect(screen.getByTestId('product-new-link')).toHaveAttribute(
      'href',
      '/ecommerce/products/new',
    );
    expect(screen.getByTestId('product-pageinfo')).toHaveTextContent('1 / 2 페이지');
  });

  it('delete is confirm-gated — the dialog appears and only confirm fires the proxy DELETE', async () => {
    // DELETE → 204; a subsequent invalidation re-query of the list → valid page.
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      Promise.resolve(init?.method === 'DELETE' ? noContent() : jsonResponse(LIST)),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductsScreen products={LIST} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('product-delete-0'));
    // Dialog open, no upstream call yet (confirm-gate).
    expect(screen.getByTestId('ecommerce-confirm-dialog')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const deleteCall = fetchMock.mock.calls.find(
      (c) => (c[1] as RequestInit)?.method === 'DELETE',
    )!;
    expect(deleteCall).toBeDefined();
    expect(String(deleteCall[0])).toBe('/api/ecommerce/products/p-1');
  });

  it('Escape cancels the delete dialog (no proxy call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductsScreen products={LIST} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('product-delete-0'));
    expect(screen.getByTestId('ecommerce-confirm-dialog')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    await waitFor(() => {
      expect(screen.queryByTestId('ecommerce-confirm-dialog')).not.toBeInTheDocument();
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a 503 on a re-query degrades the section only', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'X' }, 503)));
    const user = userEvent.setup();
    render(<ProductsScreen products={LIST} />, { wrapper: wrapper() });
    // Filter submit → a non-seeded re-query that 503s.
    await user.selectOptions(screen.getByTestId('product-status-filter'), 'HIDDEN');
    await user.click(screen.getByTestId('product-filter-submit'));
    await waitFor(() => {
      expect(screen.getByTestId('product-degraded')).toBeInTheDocument();
    });
  });

  it('is axe-clean (WCAG AA)', async () => {
    const { container } = render(<ProductsScreen products={LIST} />, {
      wrapper: wrapper(),
    });
    const violations = await runAxe(container);
    expect(violations).toHaveLength(0);
  });
});

describe('ProductDetail — variant inline CRUD + stock adjust', () => {
  it('renders variants + opens the add row + stock-adjust buttons', () => {
    render(<ProductDetail product={DETAIL} />, { wrapper: wrapper() });
    expect(screen.getByTestId('variant-table')).toBeInTheDocument();
    expect(screen.getByTestId('variant-stock-0')).toHaveTextContent('10');
    expect(screen.getByTestId('variant-add-row')).toBeInTheDocument();
    expect(screen.getByTestId('stock-adjust-open-v-1')).toBeInTheDocument();
  });

  it('add variant posts AddVariantRequest to the proxy', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 'v-2', optionName: 'L', stock: 3, additionalPrice: 0 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductDetail product={DETAIL} />, { wrapper: wrapper() });

    await user.type(screen.getByTestId('variant-add-name'), 'L');
    await user.clear(screen.getByTestId('variant-add-stock'));
    await user.type(screen.getByTestId('variant-add-stock'), '3');
    await user.click(screen.getByTestId('variant-add-submit'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    // The success invalidation refetches the detail (a GET) — find the POST
    // mutation call specifically.
    const postCall = fetchMock.mock.calls.find(
      (c) => (c[1] as RequestInit)?.method === 'POST',
    )!;
    expect(postCall).toBeDefined();
    expect(String(postCall[0])).toBe('/api/ecommerce/products/p-1/variants');
    const body = JSON.parse((postCall[1] as RequestInit).body as string);
    expect(body.optionName).toBe('L');
    expect(body.stock).toBe(3);
  });

  it('stock adjust is confirm-gated with a required reason + signed quantity', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ variantId: 'v-1', currentStock: 7 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductDetail product={DETAIL} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('stock-adjust-open-v-1'));
    const dialog = screen.getByTestId('ecommerce-confirm-dialog');
    // Confirm disabled until quantity + reason valid.
    expect(within(dialog).getByTestId('ecommerce-confirm-confirm')).toBeDisabled();

    await user.type(screen.getByTestId('stock-adjust-quantity'), '-3');
    await user.type(screen.getByTestId('stock-adjust-reason'), 'damage');
    expect(within(dialog).getByTestId('ecommerce-confirm-confirm')).not.toBeDisabled();

    await user.click(within(dialog).getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    // The success invalidation refetches the detail (a GET) — find the PATCH
    // stock mutation call specifically.
    const patchCall = fetchMock.mock.calls.find(
      (c) => (c[1] as RequestInit)?.method === 'PATCH',
    )!;
    expect(patchCall).toBeDefined();
    expect(String(patchCall[0])).toBe('/api/ecommerce/products/p-1/stock');
    const body = JSON.parse((patchCall[1] as RequestInit).body as string);
    // The client body carries the intent's Idempotency-Key alongside the producer
    // fields (the proxy strips it into the header — TASK-PC-FE-252).
    expect(body).toMatchObject({ variantId: 'v-1', quantity: -3, reason: 'damage' });
    expect(body.idempotencyKey).toEqual(expect.any(String));
  });
});
