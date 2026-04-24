import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProductList } from '@/features/product-management/components/ProductList';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/product-management/api/product-api', () => ({
  getProducts: vi.fn().mockResolvedValue({
    content: [
      { id: '1', name: '상품 A', price: 10000, status: 'ON_SALE', thumbnailUrl: '', categoryId: 'cat1' },
      { id: '2', name: '상품 B', price: 20000, status: 'SOLD_OUT', thumbnailUrl: '', categoryId: 'cat2' },
    ],
    totalPages: 1,
    totalElements: 2,
    page: 0,
    size: 20,
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('ProductList', () => {
  it('상품 목록을 테이블에 표시한다', async () => {
    render(<ProductList />, { wrapper: createWrapper() });

    expect(await screen.findByText('상품 A')).toBeInTheDocument();
    expect(screen.getByText('10,000원')).toBeInTheDocument();
    expect(screen.getByText('상품 B')).toBeInTheDocument();
    expect(screen.getByText('20,000원')).toBeInTheDocument();
  });

  it('로딩 중일 때 스피너를 표시한다', () => {
    render(<ProductList />, { wrapper: createWrapper() });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('상태 필터를 표시한다', async () => {
    render(<ProductList />, { wrapper: createWrapper() });

    await screen.findByText('상품 A');
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText('전체 상태')).toBeInTheDocument();
  });

  it('상품명 검색 입력을 표시한다', async () => {
    render(<ProductList />, { wrapper: createWrapper() });

    await screen.findByText('상품 A');
    expect(screen.getByPlaceholderText('상품명 검색...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '검색' })).toBeInTheDocument();
  });
});
