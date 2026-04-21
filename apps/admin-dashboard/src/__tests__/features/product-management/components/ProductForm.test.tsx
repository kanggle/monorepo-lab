import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProductForm } from '@/features/product-management/components/ProductForm';

const mockPush = vi.fn();
const mockBack = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, back: mockBack }),
}));

const mockCreateProduct = vi.fn().mockResolvedValue({ id: 'new-1' });
const mockUpdateProduct = vi.fn().mockResolvedValue({ id: '1' });

vi.mock('@/features/product-management/hooks/use-create-product', () => ({
  useCreateProduct: () => ({
    mutateAsync: mockCreateProduct,
    isPending: false,
  }),
}));

vi.mock('@/features/product-management/hooks/use-update-product', () => ({
  useUpdateProduct: () => ({
    mutateAsync: mockUpdateProduct,
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

describe('ProductForm', () => {
  beforeEach(() => {
    mockCreateProduct.mockClear();
    mockUpdateProduct.mockClear();
    mockPush.mockClear();
    mockBack.mockClear();
  });

  describe('등록 모드', () => {
    it('빈 폼을 렌더링한다', () => {
      render(<ProductForm />, { wrapper: createWrapper() });

      expect(screen.getByLabelText('상품명 *')).toHaveValue('');
      expect(screen.getByLabelText('가격 *')).toHaveValue(0);
      expect(screen.getByLabelText('카테고리 ID *')).toHaveValue('');
    });

    it('필수 필드가 비어있으면 등록 버튼이 비활성화된다', () => {
      render(<ProductForm />, { wrapper: createWrapper() });

      const submitButton = screen.getByText('등록');
      expect(submitButton).toBeDisabled();
    });

    it('필수 필드 입력 후 등록 버튼이 활성화된다', async () => {
      render(<ProductForm />, { wrapper: createWrapper() });

      await userEvent.type(screen.getByLabelText('상품명 *'), '테스트 상품');
      await userEvent.clear(screen.getByLabelText('가격 *'));
      await userEvent.type(screen.getByLabelText('가격 *'), '10000');
      await userEvent.type(screen.getByLabelText('카테고리 ID *'), 'cat1');

      expect(screen.getByText('등록')).not.toBeDisabled();
    });

    it('옵션 추가 버튼이 표시된다', () => {
      render(<ProductForm />, { wrapper: createWrapper() });
      expect(screen.getByText('+ 옵션 추가')).toBeInTheDocument();
    });
  });

  describe('수정 모드', () => {
    const product = {
      id: '1',
      name: '기존 상품',
      description: '기존 설명',
      price: 15000,
      status: 'ON_SALE' as const,
      categoryId: 'cat1',
      variants: [
        { id: 'v1', optionName: '기본', stock: 50, additionalPrice: 0 },
      ],
    };

    it('기존 데이터로 폼을 채운다', () => {
      render(<ProductForm product={product} />, { wrapper: createWrapper() });

      expect(screen.getByLabelText('상품명 *')).toHaveValue('기존 상품');
      expect(screen.getByLabelText('가격 *')).toHaveValue(15000);
      expect(screen.getByLabelText('카테고리 ID *')).toBeDisabled();
    });

    it('수정 모드에서는 상태 선택이 표시된다', () => {
      render(<ProductForm product={product} />, { wrapper: createWrapper() });
      expect(screen.getByLabelText('상태')).toBeInTheDocument();
    });

    it('수정 모드에서는 옵션 추가 버튼이 표시되지 않는다', () => {
      render(<ProductForm product={product} />, { wrapper: createWrapper() });
      expect(screen.queryByText('+ 옵션 추가')).not.toBeInTheDocument();
    });

    it('취소 버튼 클릭 시 뒤로 이동한다', async () => {
      render(<ProductForm product={product} />, { wrapper: createWrapper() });

      await userEvent.click(screen.getByText('취소'));
      expect(mockBack).toHaveBeenCalledTimes(1);
    });
  });
});
