import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useTemplates } from '@/features/notification-management/hooks/use-templates';

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => mockSearchParams,
}));

vi.mock('@/features/notification-management/api/notification-api', () => ({
  getTemplates: vi.fn().mockResolvedValue({
    content: [
      {
        templateId: 't1',
        type: 'ORDER_PLACED',
        channel: 'EMAIL',
        subject: '주문 확인',
        updatedAt: '2026-01-02T00:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useTemplates', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockSearchParams = new URLSearchParams();
  });

  it('알림 템플릿 목록을 조회한다', async () => {
    const { result } = renderHook(() => useTemplates(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.content).toHaveLength(1);
    expect(result.current.data?.content[0].subject).toBe('주문 확인');
  });

  it('pagination 정보를 반환한다', async () => {
    const { result } = renderHook(() => useTemplates(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pagination.totalPages).toBe(1);
  });
});
