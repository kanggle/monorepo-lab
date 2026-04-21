import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UserDetail } from '@/features/user-management/components/UserDetail';

const mockUser = {
  userId: 'u1',
  email: 'user1@example.com',
  name: '홍길동',
  nickname: '길동',
  phone: '010-1234-5678',
  profileImageUrl: null,
  status: 'ACTIVE' as const,
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-21T10:00:00Z',
};

const mockGetUser = vi.fn().mockResolvedValue(mockUser);

vi.mock('@/features/user-management/api/user-api', () => ({
  getUser: (...args: unknown[]) => mockGetUser(...args),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('UserDetail', () => {
  beforeEach(() => {
    mockGetUser.mockClear();
    mockGetUser.mockResolvedValue(mockUser);
  });

  it('사용자 기본 정보를 표시한다', async () => {
    render(<UserDetail userId="u1" />, { wrapper: createWrapper() });

    expect(await screen.findByText('user1@example.com')).toBeInTheDocument();
    const names = screen.getAllByText('홍길동');
    expect(names.length).toBeGreaterThanOrEqual(2); // title + detail
    expect(screen.getByText('길동')).toBeInTheDocument();
    expect(screen.getByText('010-1234-5678')).toBeInTheDocument();
  });

  it('사용자 상태 뱃지를 표시한다', async () => {
    render(<UserDetail userId="u1" />, { wrapper: createWrapper() });

    await screen.findByText('user1@example.com');
    expect(screen.getByText('활성')).toBeInTheDocument();
  });

  it('닉네임이 null이면 -를 표시한다', async () => {
    mockGetUser.mockResolvedValueOnce({ ...mockUser, nickname: null });
    render(<UserDetail userId="u1" />, { wrapper: createWrapper() });

    await screen.findByText('user1@example.com');
    const dashes = screen.getAllByText('-');
    expect(dashes.length).toBeGreaterThanOrEqual(1);
  });

  it('프로필 이미지가 있으면 이미지를 표시한다', async () => {
    mockGetUser.mockResolvedValueOnce({
      ...mockUser,
      profileImageUrl: 'https://example.com/photo.jpg',
    });
    render(<UserDetail userId="u1" />, { wrapper: createWrapper() });

    await screen.findByText('user1@example.com');
    const img = screen.getByRole('img');
    expect(img).toHaveAttribute('src', 'https://example.com/photo.jpg');
  });

  it('존재하지 않는 사용자 조회 시 404 메시지를 표시한다', async () => {
    mockGetUser.mockRejectedValueOnce({
      code: 'USER_PROFILE_NOT_FOUND',
      message: 'User not found',
      timestamp: '2026-03-20T10:00:00Z',
    });

    render(<UserDetail userId="not-exist" />, { wrapper: createWrapper() });

    expect(await screen.findByText('사용자를 찾을 수 없습니다.')).toBeInTheDocument();
  });

  it('네트워크 에러 시 재시도 버튼을 표시한다', async () => {
    mockGetUser.mockRejectedValueOnce({
      code: 'NETWORK_ERROR',
      message: 'Network error',
      timestamp: '2026-03-20T10:00:00Z',
    });

    render(<UserDetail userId="u1" />, { wrapper: createWrapper() });

    expect(await screen.findByText('사용자 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
    expect(screen.getByText('다시 시도')).toBeInTheDocument();
  });

  it('로딩 중일 때 스켈레톤을 표시한다', () => {
    render(<UserDetail userId="u1" />, { wrapper: createWrapper() });

    expect(screen.queryByText('user1@example.com')).not.toBeInTheDocument();
  });
});
