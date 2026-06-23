import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ProductForm } from '@/features/ecommerce-ops';

/**
 * `ProductForm` register-mode variant fields (TASK-PC-FE-130). The two numeric
 * option inputs (재고 / 추가 가격) must start EMPTY so their placeholder is
 * visible (they used to be pre-filled with '0', hiding the placeholder), and a
 * persistent column header labels the columns. Leaving them empty must still
 * register as 0 — the wire shape is unchanged.
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

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ProductForm — register-mode variant number fields', () => {
  it('starts the stock / additional-price inputs EMPTY so the placeholder shows', () => {
    render(<ProductForm />, { wrapper: wrapper() });

    const stock = screen.getByTestId('product-form-variant-stock-0');
    const addprice = screen.getByTestId('product-form-variant-addprice-0');

    expect(stock).toHaveValue('');
    expect(stock).toHaveAttribute('placeholder', '재고');
    expect(addprice).toHaveValue('');
    expect(addprice).toHaveAttribute('placeholder', '추가 가격');
  });

  it('renders a persistent column header labelling 옵션명 / 재고 / 추가 가격', () => {
    render(<ProductForm />, { wrapper: wrapper() });

    const header = screen.getByTestId('product-form-variant-header');
    expect(header).toHaveTextContent('옵션명');
    expect(header).toHaveTextContent('재고');
    expect(header).toHaveTextContent('추가 가격');
  });

  it('newly added option rows are also empty (placeholder visible)', async () => {
    const user = userEvent.setup();
    render(<ProductForm />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('product-form-variant-add'));

    expect(screen.getByTestId('product-form-variant-stock-1')).toHaveValue('');
    expect(screen.getByTestId('product-form-variant-addprice-1')).toHaveValue('');
  });

  it('registers empty stock / additional-price as 0 (wire shape unchanged)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'p-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ProductForm />, { wrapper: wrapper() });

    await user.type(screen.getByTestId('product-form-name'), 'Tee');
    await user.type(screen.getByTestId('product-form-price'), '12000');
    await user.type(screen.getByTestId('product-form-variant-name-0'), 'M');
    // stock + additional price left empty on purpose.

    await user.click(screen.getByTestId('product-form-submit'));
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    const postCall = fetchMock.mock.calls.find(
      (c) => (c[1] as RequestInit)?.method === 'POST',
    )!;
    expect(postCall).toBeDefined();
    expect(String(postCall[0])).toBe('/api/ecommerce/products');
    const body = JSON.parse((postCall[1] as RequestInit).body as string);
    expect(body.variants).toEqual([
      { optionName: 'M', stock: 0, additionalPrice: 0 },
    ]);
  });
});
