import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TemplateList } from '@/features/notification-management/components/TemplateList';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/notification-management/api/notification-api', () => ({
  getTemplates: vi.fn().mockResolvedValue({
    content: [
      {
        templateId: 't1',
        type: 'ORDER_PLACED',
        channel: 'EMAIL',
        subject: '주문이 접수되었습니다',
        createdAt: '2024-01-01T00:00:00Z',
      },
      {
        templateId: 't2',
        type: 'WELCOME',
        channel: 'SMS',
        subject: '환영합니다',
        createdAt: '2024-01-02T00:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
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

describe('TemplateList', () => {
  it('알림 템플릿 목록을 테이블에 표시한다', async () => {
    render(<TemplateList />, { wrapper: createWrapper() });

    expect(await screen.findByText('주문이 접수되었습니다')).toBeInTheDocument();
    expect(screen.getByText('환영합니다')).toBeInTheDocument();
    expect(screen.getByText('주문 완료')).toBeInTheDocument();
    expect(screen.getByText('회원 가입')).toBeInTheDocument();
    expect(screen.getByText('이메일')).toBeInTheDocument();
    expect(screen.getByText('SMS')).toBeInTheDocument();
  });

  it('로딩 중일 때 스피너를 표시한다', () => {
    render(<TemplateList />, { wrapper: createWrapper() });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('빈 상태일 때 안내 메시지를 표시한다', async () => {
    const { getTemplates } = await import(
      '@/features/notification-management/api/notification-api'
    );
    vi.mocked(getTemplates).mockResolvedValueOnce({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
    });

    render(<TemplateList />, { wrapper: createWrapper() });

    expect(
      await screen.findByText('등록된 알림 템플릿이 없습니다.'),
    ).toBeInTheDocument();
  });
});
