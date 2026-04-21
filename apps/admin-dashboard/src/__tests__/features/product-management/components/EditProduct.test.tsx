import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EditProduct } from '@/features/product-management/components/EditProduct';

const mockRefetch = vi.fn();
let mockUseProduct: { data: unknown; isLoading: boolean; isError: boolean; refetch: () => void };

vi.mock('@/features/product-management/hooks/use-product', () => ({
  useProduct: () => mockUseProduct,
}));

vi.mock('@/shared/ui', () => ({
  PageLayout: Object.assign(
    ({ title, children }: { title: string; children: React.ReactNode }) => (
      <div data-testid="page-layout" data-title={title}>{children}</div>
    ),
    { Skeleton: () => <div data-testid="skeleton" /> },
  ),
}));

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      <span>{message}</span>
      <button onClick={onRetry}>다시 시도</button>
    </div>
  ),
}));

vi.mock('@/features/product-management/components/ProductForm', () => ({
  ProductForm: ({ product }: { product: unknown }) => (
    <div data-testid="product-form" data-product={JSON.stringify(product)} />
  ),
}));

describe('EditProduct', () => {
  beforeEach(() => {
    mockRefetch.mockClear();
  });

  it('로딩 중이면 Skeleton을 표시한다', () => {
    mockUseProduct = { data: undefined, isLoading: true, isError: false, refetch: mockRefetch };

    render(<EditProduct productId="prod-1" />);

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });

  it('data가 없고 isError=true이면 Skeleton을 표시한다 (isLoading || !product 조건 우선)', () => {
    mockUseProduct = { data: undefined, isLoading: false, isError: true, refetch: mockRefetch };

    render(<EditProduct productId="prod-1" />);

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();
  });

  it('데이터 로드 성공 시 PageLayout과 ProductForm을 렌더링한다', () => {
    const product = { id: 'prod-1', name: '테스트 상품' };
    mockUseProduct = { data: product, isLoading: false, isError: false, refetch: mockRefetch };

    render(<EditProduct productId="prod-1" />);

    expect(screen.getByTestId('page-layout')).toHaveAttribute('data-title', '테스트 상품 수정');
    expect(screen.getByTestId('product-form')).toBeInTheDocument();
  });
});
