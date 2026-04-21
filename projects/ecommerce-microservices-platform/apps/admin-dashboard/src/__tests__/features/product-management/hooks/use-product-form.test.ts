import { renderHook, act } from '@testing-library/react';
import { useProductForm } from '@/features/product-management/hooks/use-product-form';
import type { ProductDetail } from '@repo/types';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockCreateMutateAsync = vi.fn().mockResolvedValue({ id: 'new-1' });
const mockUpdateMutateAsync = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/product-management/hooks/use-create-product', () => ({
  useCreateProduct: () => ({ mutateAsync: mockCreateMutateAsync }),
}));

vi.mock('@/features/product-management/hooks/use-update-product', () => ({
  useUpdateProduct: () => ({ mutateAsync: mockUpdateMutateAsync }),
}));

describe('useProductForm', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockCreateMutateAsync.mockClear().mockResolvedValue({ id: 'new-1' });
    mockUpdateMutateAsync.mockClear().mockResolvedValue(undefined);
  });

  describe('생성 모드', () => {
    it('초기 상태에서 isEdit=false이다', () => {
      const { result } = renderHook(() => useProductForm());
      expect(result.current.isEdit).toBe(false);
    });

    it('이름, 가격, 카테고리가 모두 유효하면 isValid=true이다', () => {
      const { result } = renderHook(() => useProductForm());

      act(() => {
        result.current.setName('테스트 상품');
        result.current.setPrice(10000);
        result.current.setCategoryId('cat-1');
      });

      expect(result.current.isValid).toBe(true);
    });

    it('이름이 비어있으면 isValid=false이다', () => {
      const { result } = renderHook(() => useProductForm());

      act(() => {
        result.current.setPrice(10000);
        result.current.setCategoryId('cat-1');
      });

      expect(result.current.isValid).toBe(false);
    });

    it('가격이 0이면 isValid=false이다', () => {
      const { result } = renderHook(() => useProductForm());

      act(() => {
        result.current.setName('상품');
        result.current.setCategoryId('cat-1');
      });

      expect(result.current.isValid).toBe(false);
    });

    it('생성 submit 시 createProduct를 호출하고 상세 페이지로 이동한다', async () => {
      const { result } = renderHook(() => useProductForm());

      act(() => {
        result.current.setName('새 상품');
        result.current.setDescription('설명');
        result.current.setPrice(5000);
        result.current.setCategoryId('cat-1');
      });

      await act(async () => {
        await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
      });

      expect(mockCreateMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          name: '새 상품',
          description: '설명',
          price: 5000,
          categoryId: 'cat-1',
        }),
      );
      expect(mockPush).toHaveBeenCalledWith('/products/new-1');
    });

    it('isValid이 false이면 submit이 실행되지 않는다', async () => {
      const { result } = renderHook(() => useProductForm());

      await act(async () => {
        await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
      });

      expect(mockCreateMutateAsync).not.toHaveBeenCalled();
    });
  });

  describe('수정 모드', () => {
    const existingProduct: ProductDetail = {
      id: 'prod-1',
      name: '기존 상품',
      description: '기존 설명',
      price: 20000,
      categoryId: 'cat-2',
      status: 'ON_SALE',
      variants: [
        { id: 'v-1', optionName: '기본', stock: 5, additionalPrice: 0 },
      ],
    };

    it('기존 상품 데이터로 초기화된다', () => {
      const { result } = renderHook(() => useProductForm(existingProduct));

      expect(result.current.isEdit).toBe(true);
      expect(result.current.name).toBe('기존 상품');
      expect(result.current.description).toBe('기존 설명');
      expect(result.current.price).toBe(20000);
    });

    it('수정 submit 시 updateProduct를 호출하고 상세 페이지로 이동한다', async () => {
      const { result } = renderHook(() => useProductForm(existingProduct));

      act(() => {
        result.current.setName('수정된 상품');
      });

      await act(async () => {
        await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
      });

      expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
        productId: 'prod-1',
        data: expect.objectContaining({ name: '수정된 상품' }),
      });
      expect(mockPush).toHaveBeenCalledWith('/products/prod-1');
    });
  });

  it('submit 실패 시 에러 메시지를 설정한다', async () => {
    mockCreateMutateAsync.mockRejectedValueOnce(new Error('서버 오류'));

    const { result } = renderHook(() => useProductForm());

    act(() => {
      result.current.setName('상품');
      result.current.setPrice(1000);
      result.current.setCategoryId('cat-1');
    });

    await act(async () => {
      await result.current.handleSubmit({ preventDefault: vi.fn() } as unknown as React.FormEvent);
    });

    expect(result.current.error).toBeTruthy();
    expect(result.current.isSubmitting).toBe(false);
  });
});
