import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockCheckWishlist = vi.fn();

vi.mock('@/features/wishlist/api/wishlist-api', () => ({
  checkWishlist: (...args: unknown[]) => mockCheckWishlist(...args),
}));

import { useWishlistCheck } from '@/features/wishlist/model/use-wishlist-check';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useWishlistCheck', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('enabled=true일 때 위시리스트 포함 여부를 조회한다', async () => {
    mockCheckWishlist.mockResolvedValueOnce({ inWishlist: true });

    const { result } = renderHook(() => useWishlistCheck('prod-1', true), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({ inWishlist: true });
    expect(mockCheckWishlist).toHaveBeenCalledWith('prod-1');
  });

  it('enabled=false일 때 쿼리를 실행하지 않는다', () => {
    renderHook(() => useWishlistCheck('prod-1', false), { wrapper: createWrapper() });

    expect(mockCheckWishlist).not.toHaveBeenCalled();
  });

  it('API 실패 시 에러 상태를 반환한다', async () => {
    mockCheckWishlist.mockRejectedValueOnce(new Error('fail'));

    const { result } = renderHook(() => useWishlistCheck('prod-1', true), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
