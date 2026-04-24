import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TemplatesPage from '@/app/(admin)/notifications/templates/page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/notification-management/api/notification-api', () => ({
  getTemplates: vi.fn().mockResolvedValue({
    content: [
      {
        templateId: 't1',
        type: 'ORDER_PLACED',
        channel: 'EMAIL',
        subject: '주문 확인',
        createdAt: '2024-01-01T00:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  }),
  getTemplate: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('TemplatesPage', () => {
  it('페이지 제목을 표시한다', () => {
    render(<TemplatesPage />, { wrapper: createWrapper() });
    expect(screen.getByText('알림 템플릿 관리')).toBeInTheDocument();
  });

  it('템플릿 등록 버튼을 표시한다', () => {
    render(<TemplatesPage />, { wrapper: createWrapper() });
    expect(screen.getByText('템플릿 등록')).toBeInTheDocument();
  });

  it('템플릿 목록을 로드한다', async () => {
    render(<TemplatesPage />, { wrapper: createWrapper() });
    expect(await screen.findByText('주문 확인')).toBeInTheDocument();
  });
});
