import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileLoader } from '@/features/user/ui/ProfileLoader';

const mockRefetch = vi.fn();
const mockSetImageUrl = vi.fn();

const mockUseProfile = vi.fn();

vi.mock('../features/user/model/use-profile', () => ({
  useProfile: () => mockUseProfile(),
}));

vi.mock('@/shared/context/ProfileImageContext', () => ({
  useProfileImage: () => ({ imageUrl: '', setImageUrl: mockSetImageUrl }),
  ProfileImageProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div role="alert">
      {message}
      <button onClick={onRetry}>다시 시도</button>
    </div>
  ),
}));

vi.mock('@/shared/ui/Skeleton', () => ({
  Skeleton: () => <div data-testid="skeleton" />,
}));

vi.mock('@/features/user/ui/ProfileForm', () => ({
  ProfileForm: ({ profile }: { profile: { name: string } }) => (
    <div data-testid="profile-form">{profile.name}</div>
  ),
}));

describe('ProfileLoader', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 스켈레톤을 표시한다', () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
      refetch: mockRefetch,
    });

    render(<ProfileLoader />);

    expect(screen.getByText('내 프로필')).toBeInTheDocument();
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('에러 시 에러 메시지를 표시한다', () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { code: 'UNKNOWN' },
      refetch: mockRefetch,
    });

    render(<ProfileLoader />);

    expect(screen.getByRole('alert')).toHaveTextContent('프로필을 불러오는데 실패했습니다.');
  });

  it('USER_PROFILE_NOT_FOUND 에러 시 해당 메시지를 표시한다', () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { code: 'USER_PROFILE_NOT_FOUND' },
      refetch: mockRefetch,
    });

    render(<ProfileLoader />);

    expect(screen.getByRole('alert')).toHaveTextContent('프로필을 찾을 수 없습니다.');
  });

  it('다시 시도 버튼 클릭 시 refetch를 호출한다', async () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { code: 'UNKNOWN' },
      refetch: mockRefetch,
    });

    const user = userEvent.setup();
    render(<ProfileLoader />);

    await user.click(screen.getByText('다시 시도'));
    expect(mockRefetch).toHaveBeenCalled();
  });

  it('프로필 데이터가 있으면 ProfileForm을 표시한다', () => {
    const profile = {
      userId: 'u1',
      email: 'test@test.com',
      name: '홍길동',
      nickname: null,
      phone: null,
      profileImageUrl: '/img.jpg',
      status: 'ACTIVE' as const,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-01',
    };

    mockUseProfile.mockReturnValue({
      data: profile,
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockRefetch,
    });

    render(<ProfileLoader />);

    expect(screen.getByTestId('profile-form')).toHaveTextContent('홍길동');
  });

  it('프로필 로드 시 전역 프로필 이미지를 설정한다', () => {
    const profile = {
      userId: 'u1',
      email: 'test@test.com',
      name: '홍길동',
      nickname: null,
      phone: null,
      profileImageUrl: '/img.jpg',
      status: 'ACTIVE' as const,
      createdAt: '2024-01-01',
      updatedAt: '2024-01-01',
    };

    mockUseProfile.mockReturnValue({
      data: profile,
      isLoading: false,
      isError: false,
      error: null,
      refetch: mockRefetch,
    });

    render(<ProfileLoader />);

    expect(mockSetImageUrl).toHaveBeenCalledWith('/img.jpg');
  });
});
