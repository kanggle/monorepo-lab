import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useTemplate } from '@/features/notification-management/hooks/use-template';

vi.mock('@/features/notification-management/api/notification-api', () => ({
  getTemplate: vi.fn().mockResolvedValue({
    templateId: 't1',
    type: 'ORDER_PLACED',
    channel: 'EMAIL',
    subject: '주문 확인',
    body: '주문이 접수되었습니다.',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useTemplate', () => {
  it('알림 템플릿 상세를 조회한다', async () => {
    const { result } = renderHook(() => useTemplate('t1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.templateId).toBe('t1');
    expect(result.current.data?.subject).toBe('주문 확인');
  });

  it('templateId가 빈 문자열이면 쿼리를 실행하지 않는다', () => {
    const { result } = renderHook(() => useTemplate(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
  });
});
