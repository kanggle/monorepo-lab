import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import {
  useAddVariant,
  useUpdateVariant,
  useDeleteVariant,
} from '@/features/product-management/hooks/use-variant-mutations';

const mockAddVariant = vi.fn().mockResolvedValue({ id: 'v-new' });
const mockUpdateVariant = vi.fn().mockResolvedValue(undefined);
const mockDeleteVariant = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/product-management/api/product-api', () => ({
  addVariant: (...args: unknown[]) => mockAddVariant(...args),
  updateVariant: (...args: unknown[]) => mockUpdateVariant(...args),
  deleteVariant: (...args: unknown[]) => mockDeleteVariant(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useAddVariant', () => {
  beforeEach(() => mockAddVariant.mockClear());

  it('옵션 추가 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useAddVariant('prod-1'), {
      wrapper: createWrapper(),
    });

    const data = { optionName: '빨강', stock: 10, additionalPrice: 1000 };
    await result.current.mutateAsync(data);

    expect(mockAddVariant).toHaveBeenCalledWith('prod-1', data);
  });
});

describe('useUpdateVariant', () => {
  beforeEach(() => mockUpdateVariant.mockClear());

  it('옵션 수정 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useUpdateVariant('prod-1'), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync({
      variantId: 'v-1',
      data: { optionName: '파랑', additionalPrice: 500 },
    });

    expect(mockUpdateVariant).toHaveBeenCalledWith('prod-1', 'v-1', {
      optionName: '파랑',
      additionalPrice: 500,
    });
  });
});

describe('useDeleteVariant', () => {
  beforeEach(() => mockDeleteVariant.mockClear());

  it('옵션 삭제 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useDeleteVariant('prod-1'), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync('v-1');

    expect(mockDeleteVariant).toHaveBeenCalledWith('prod-1', 'v-1');
  });

  it('삭제 실패 시 isError가 true가 된다', async () => {
    mockDeleteVariant.mockRejectedValueOnce(new Error('삭제 실패'));

    const { result } = renderHook(() => useDeleteVariant('prod-1'), {
      wrapper: createWrapper(),
    });

    result.current.mutate('v-1');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
  });
});
