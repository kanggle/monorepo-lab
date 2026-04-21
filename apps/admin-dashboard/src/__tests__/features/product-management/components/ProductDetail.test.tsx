import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProductDetail } from '@/features/product-management/components/ProductDetail';

const mockProduct = {
  id: 'p1',
  name: '테스트 상품',
  description: '설명입니다',
  status: 'ON_SALE' as const,
  price: 15000,
  categoryId: 'cat1',
  variants: [
    { id: 'v1', optionName: '기본', stock: 10, additionalPrice: 0 },
    { id: 'v2', optionName: '대용량', stock: 5, additionalPrice: 3000 },
  ],
};

const mockGetProduct = vi.fn().mockResolvedValue(mockProduct);

vi.mock('@/features/product-management/api/product-api', () => ({
  getProduct: (...args: unknown[]) => mockGetProduct(...args),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/features/product-management/hooks/use-adjust-stock', () => ({
  useAdjustStock: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
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

describe('ProductDetail', () => {
  beforeEach(() => {
    mockGetProduct.mockClear();
  });

  it('상품 기본 정보를 표시한다', async () => {
    render(<ProductDetail productId="p1" />, { wrapper: createWrapper() });

    expect(await screen.findByText('테스트 상품')).toBeInTheDocument();
    expect(screen.getByText('15,000원')).toBeInTheDocument();
    expect(screen.getByText('cat1')).toBeInTheDocument();
    expect(screen.getByText('설명입니다')).toBeInTheDocument();
  });

  it('옵션 목록을 표시한다', async () => {
    render(<ProductDetail productId="p1" />, { wrapper: createWrapper() });

    expect(await screen.findByText('기본')).toBeInTheDocument();
    expect(screen.getByText('대용량')).toBeInTheDocument();
    expect(screen.getByText('+3,000원')).toBeInTheDocument();
  });

  it('재고 조정 버튼 클릭 시 폼을 표시한다', async () => {
    const user = userEvent.setup();
    render(<ProductDetail productId="p1" />, { wrapper: createWrapper() });

    await screen.findByText('기본');
    const buttons = screen.getAllByText('재고 조정');
    await user.click(buttons[0]);

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText(/재고 조정 — 기본/)).toBeInTheDocument();
  });

  it('옵션이 없으면 안내 메시지를 표시한다', async () => {
    mockGetProduct.mockResolvedValueOnce({ ...mockProduct, variants: [] });
    render(<ProductDetail productId="p1" />, { wrapper: createWrapper() });

    expect(await screen.findByText('등록된 옵션이 없습니다.')).toBeInTheDocument();
  });
});
