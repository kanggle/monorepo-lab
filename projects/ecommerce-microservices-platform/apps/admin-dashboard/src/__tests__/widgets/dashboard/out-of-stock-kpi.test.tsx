import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { OutOfStockKpi } from '@/widgets/dashboard/OutOfStockKpi';
import { getProducts } from '@/features/product-management/api/product-api';

vi.mock('@/features/product-management/api/product-api', () => ({
  getProducts: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const mockedGetProducts = vi.mocked(getProducts);

describe('OutOfStockKpi', () => {
  beforeEach(() => {
    mockedGetProducts.mockReset();
  });

  it('품절 상품 수를 표시한다', async () => {
    mockedGetProducts.mockResolvedValue({
      content: [],
      page: 0,
      size: 1,
      totalElements: 12,
    });
    render(<OutOfStockKpi />, { wrapper: createWrapper() });
    expect(await screen.findByText('12개')).toBeInTheDocument();
    expect(screen.getByText('SOLD_OUT 상태')).toBeInTheDocument();
  });

  it('로딩 중 스켈레톤을 표시한다', () => {
    mockedGetProducts.mockImplementation(() => new Promise(() => {}));
    render(<OutOfStockKpi />, { wrapper: createWrapper() });
    expect(screen.getByLabelText('로딩 중')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockedGetProducts.mockRejectedValue(new Error('fail'));
    render(<OutOfStockKpi />, { wrapper: createWrapper() });
    expect(await screen.findByText('상품 데이터를 불러오지 못했습니다.')).toBeInTheDocument();
  });
});
