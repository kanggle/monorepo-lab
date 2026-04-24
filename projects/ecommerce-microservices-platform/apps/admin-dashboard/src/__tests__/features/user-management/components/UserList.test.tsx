import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UserList } from '@/features/user-management/components/UserList';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/user-management/api/user-api', () => ({
  getUsers: vi.fn().mockResolvedValue({
    content: [
      { userId: 'u1', email: 'user1@example.com', name: '홍길동', nickname: '길동', status: 'ACTIVE', createdAt: '2026-03-20T10:00:00Z' },
      { userId: 'u2', email: 'user2@example.com', name: '김철수', nickname: null, status: 'SUSPENDED', createdAt: '2026-03-21T10:00:00Z' },
    ],
    totalElements: 2,
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

describe('UserList', () => {
  it('사용자 목록을 테이블에 표시한다', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    expect(await screen.findByText('user1@example.com')).toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
    expect(screen.getByText('user2@example.com')).toBeInTheDocument();
    expect(screen.getByText('김철수')).toBeInTheDocument();
  });

  it('로딩 중일 때 스피너를 표시한다', () => {
    render(<UserList />, { wrapper: createWrapper() });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('상태 필터를 표시한다', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    await screen.findByText('user1@example.com');
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText('전체 상태')).toBeInTheDocument();
  });

  it('사용자 상태 뱃지를 표시한다', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    await screen.findByText('user1@example.com');
    const activeBadges = screen.getAllByText('활성');
    expect(activeBadges.length).toBeGreaterThanOrEqual(2);
    const suspendedBadges = screen.getAllByText('정지');
    expect(suspendedBadges.length).toBeGreaterThanOrEqual(2);
  });

  it('이메일 검색 입력 필드를 표시한다', async () => {
    render(<UserList />, { wrapper: createWrapper() });

    await screen.findByText('user1@example.com');
    expect(screen.getByPlaceholderText('이메일 검색...')).toBeInTheDocument();
  });

  it('빈 목록일 때 안내 메시지를 표시한다', async () => {
    const { getUsers } = await import('@/features/user-management/api/user-api');
    vi.mocked(getUsers).mockResolvedValueOnce({
      content: [],
      totalElements: 0,
      page: 0,
      size: 20,
    });

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <UserList />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('등록된 사용자가 없습니다.')).toBeInTheDocument();
  });
});
