import { renderHook, act } from '@testing-library/react';
import { useVariantManagement } from '@/features/product-management/hooks/use-variant-management';

const mockAddMutateAsync = vi.fn().mockResolvedValue(undefined);
const mockUpdateMutateAsync = vi.fn().mockResolvedValue(undefined);
const mockDeleteMutateAsync = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/product-management/hooks/use-variant-mutations', () => ({
  useAddVariant: () => ({ mutateAsync: mockAddMutateAsync, isPending: false }),
  useUpdateVariant: () => ({ mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useDeleteVariant: () => ({ mutateAsync: mockDeleteMutateAsync, isPending: false }),
}));

describe('useVariantManagement', () => {
  const onChanged = vi.fn();

  beforeEach(() => {
    onChanged.mockClear();
    mockAddMutateAsync.mockClear().mockResolvedValue(undefined);
    mockUpdateMutateAsync.mockClear().mockResolvedValue(undefined);
    mockDeleteMutateAsync.mockClear().mockResolvedValue(undefined);
  });

  it('초기 상태에서 editing과 adding은 null이다', () => {
    const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

    expect(result.current.editing).toBeNull();
    expect(result.current.adding).toBeNull();
    expect(result.current.error).toBe('');
  });

  describe('handleUpdate', () => {
    it('옵션 수정 시 mutation을 호출하고 onChanged를 실행한다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      act(() => {
        result.current.setEditing({
          variantId: 'v-1',
          optionName: '파랑',
          additionalPrice: 500,
        });
      });

      await act(async () => {
        await result.current.handleUpdate();
      });

      expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
        variantId: 'v-1',
        data: { optionName: '파랑', additionalPrice: 500 },
      });
      expect(result.current.editing).toBeNull();
      expect(onChanged).toHaveBeenCalled();
    });

    it('editing이 null이면 아무 동작도 하지 않는다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      await act(async () => {
        await result.current.handleUpdate();
      });

      expect(mockUpdateMutateAsync).not.toHaveBeenCalled();
    });

    it('optionName이 빈 문자열이면 아무 동작도 하지 않는다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      act(() => {
        result.current.setEditing({
          variantId: 'v-1',
          optionName: '  ',
          additionalPrice: 0,
        });
      });

      await act(async () => {
        await result.current.handleUpdate();
      });

      expect(mockUpdateMutateAsync).not.toHaveBeenCalled();
    });
  });

  describe('handleDelete', () => {
    it('옵션 삭제 시 mutation을 호출하고 onChanged를 실행한다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      await act(async () => {
        await result.current.handleDelete('v-1');
      });

      expect(mockDeleteMutateAsync).toHaveBeenCalledWith('v-1');
      expect(onChanged).toHaveBeenCalled();
    });
  });

  describe('handleAdd', () => {
    it('옵션 추가 시 mutation을 호출하고 onChanged를 실행한다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      act(() => {
        result.current.setAdding({
          optionName: '초록',
          stock: 20,
          additionalPrice: 300,
        });
      });

      await act(async () => {
        await result.current.handleAdd();
      });

      expect(mockAddMutateAsync).toHaveBeenCalledWith({
        optionName: '초록',
        stock: 20,
        additionalPrice: 300,
      });
      expect(result.current.adding).toBeNull();
      expect(onChanged).toHaveBeenCalled();
    });

    it('adding이 null이면 아무 동작도 하지 않는다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      await act(async () => {
        await result.current.handleAdd();
      });

      expect(mockAddMutateAsync).not.toHaveBeenCalled();
    });

    it('optionName이 빈 문자열이면 아무 동작도 하지 않는다', async () => {
      const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

      act(() => {
        result.current.setAdding({ optionName: '', stock: 0, additionalPrice: 0 });
      });

      await act(async () => {
        await result.current.handleAdd();
      });

      expect(mockAddMutateAsync).not.toHaveBeenCalled();
    });
  });

  it('mutation 실패 시 에러 메시지를 설정한다', async () => {
    mockDeleteMutateAsync.mockRejectedValueOnce(new Error('삭제 실패'));

    const { result } = renderHook(() => useVariantManagement('prod-1', onChanged));

    await act(async () => {
      await result.current.handleDelete('v-1');
    });

    expect(result.current.error).toBeTruthy();
  });
});
