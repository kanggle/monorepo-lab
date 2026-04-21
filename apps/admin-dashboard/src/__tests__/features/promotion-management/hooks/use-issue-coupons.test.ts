import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useIssueCoupons } from '@/features/promotion-management/hooks/use-issue-coupons';

const mockIssueCoupons = vi.fn().mockResolvedValue({ issuedCount: 3 });

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  issueCoupons: (...args: unknown[]) => mockIssueCoupons(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useIssueCoupons', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockIssueCoupons.mockClear();
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('쿠폰 발급 mutation을 promotionId와 함께 호출한다', async () => {
    const { result } = renderHook(() => useIssueCoupons('p1'), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync({
      userIds: ['u1', 'u2', 'u3'],
    });

    expect(mockIssueCoupons).toHaveBeenCalledWith('p1', {
      userIds: ['u1', 'u2', 'u3'],
    });
  });

  it('실패 시 기본 메시지로 alert를 표시한다', async () => {
    mockIssueCoupons.mockRejectedValueOnce(null);

    const { result } = renderHook(() => useIssueCoupons('p1'), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ userIds: ['u1'] });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('쿠폰 발급에 실패했습니다.');
  });
});
