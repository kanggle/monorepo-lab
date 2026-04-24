import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UsersPage from '@/app/(admin)/users/page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/user-management/api/user-api', () => ({
  getUsers: vi.fn().mockResolvedValue({
    content: [
      { userId: 'u1', email: 'user1@example.com', name: '홍길동', role: 'USER', createdAt: '2026-03-20T10:00:00Z' },
    ],
    totalElements: 1,
    page: 0,
    size: 20,
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

describe('UsersPage', () => {
  it('Suspense fallback으로 로딩 스피너를 표시한다', () => {
    render(<UsersPage />, { wrapper: createWrapper() });

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('로딩 중...')).toBeInTheDocument();
  });

  it('데이터 로드 후 사용자 목록을 표시한다', async () => {
    render(<UsersPage />, { wrapper: createWrapper() });

    expect(await screen.findByText('홍길동')).toBeInTheDocument();
  });
});
