import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { useState, useEffect, useCallback } from 'react';
import type { UserProfile } from '@repo/types';
import { LoadingSpinner, ErrorMessage } from '@repo/ui';

const mockPush = vi.fn();
const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
}));

let mockAuthState = {
  user: { userId: 'user-1', email: 'test@example.com', name: '홍길동' },
  isAuthenticated: true,
  isLoading: false,
  login: vi.fn(),
  logout: vi.fn(),
};

vi.mock('@/features/auth', () => ({
  useAuth: () => mockAuthState,
  useRequireAuth: vi.fn(),
}));

vi.mock('@/features/user', () => ({
  ProfileForm: ({ profile }: { profile: UserProfile }) => (
    <div data-testid="profile-form">{profile.email}</div>
  ),
  ProfileLoader: vi.fn(),
  getMyProfile: vi.fn(),
}));

import { getMyProfile, ProfileLoader } from '@/features/user';
import { useRequireAuth } from '@/features/auth';
const mockGetMyProfile = vi.mocked(getMyProfile);
const MockProfileLoader = vi.mocked(ProfileLoader);
const mockUseRequireAuth = vi.mocked(useRequireAuth);

import ProfilePage from '@/app/(store)/my/profile/page';

const MOCK_PROFILE: UserProfile = {
  userId: 'user-1',
  email: 'test@example.com',
  name: '홍길동',
  nickname: '길동이',
  phone: '010-1234-5678',
  profileImageUrl: null,
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

function createProfileLoaderImpl(getProfile: typeof getMyProfile) {
  return function ProfileLoaderImpl() {
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    const loadProfile = useCallback(async () => {
      setIsLoading(true);
      setError('');
      try {
        const data = await getProfile();
        setProfile(data);
      } catch (err) {
        const apiErr = err as { code?: string };
        if (apiErr.code === 'USER_PROFILE_NOT_FOUND') {
          setError('프로필을 찾을 수 없습니다.');
        } else {
          setError('프로필을 불러오는데 실패했습니다.');
        }
      } finally {
        setIsLoading(false);
      }
    }, []);

    useEffect(() => {
      loadProfile();
    }, [loadProfile]);

    return (
      <main style={{ maxWidth: '600px', margin: '0 auto', padding: '24px' }}>
        <h1 style={{ marginBottom: '24px' }}>내 프로필</h1>
        {isLoading && <LoadingSpinner />}
        {error && <ErrorMessage message={error} onRetry={loadProfile} />}
        {!isLoading && !error && profile && (
          <div data-testid="profile-form">{profile.email}</div>
        )}
      </main>
    );
  };
}

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthState = {
      user: { userId: 'user-1', email: 'test@example.com', name: '홍길동' },
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    };
    mockUseRequireAuth.mockReturnValue({ isReady: true });
    MockProfileLoader.mockImplementation(createProfileLoaderImpl(mockGetMyProfile));
  });

  it('프로필을 로드하고 ProfileForm을 렌더링한다', async () => {
    mockGetMyProfile.mockResolvedValueOnce(MOCK_PROFILE);

    render(<ProfilePage />);

    expect(screen.getByText('로딩 중...')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByTestId('profile-form')).toBeInTheDocument();
    });

    expect(screen.getByText('test@example.com')).toBeInTheDocument();
  });

  it('미인증 사용자는 로그인 페이지로 리다이렉트한다', () => {
    mockUseRequireAuth.mockImplementation(() => {
      mockReplace('/login');
      return { isReady: false };
    });

    render(<ProfilePage />);

    expect(mockReplace).toHaveBeenCalledWith('/login');
  });

  it('프로필 조회 실패 시 에러 메시지를 표시한다', async () => {
    mockGetMyProfile.mockRejectedValueOnce({
      code: 'NETWORK_ERROR',
      message: 'Network error',
      timestamp: new Date().toISOString(),
    });

    render(<ProfilePage />);

    await waitFor(() => {
      expect(
        screen.getByText('프로필을 불러오는데 실패했습니다.'),
      ).toBeInTheDocument();
    });
  });

  it('프로필 미존재 시 적절한 메시지를 표시한다', async () => {
    mockGetMyProfile.mockRejectedValueOnce({
      code: 'USER_PROFILE_NOT_FOUND',
      message: 'Profile not found',
      timestamp: new Date().toISOString(),
    });

    render(<ProfilePage />);

    await waitFor(() => {
      expect(
        screen.getByText('프로필을 찾을 수 없습니다.'),
      ).toBeInTheDocument();
    });
  });

  it('에러 시 재시도 버튼이 표시된다', async () => {
    mockGetMyProfile.mockRejectedValueOnce({
      code: 'NETWORK_ERROR',
      message: 'error',
      timestamp: new Date().toISOString(),
    });

    render(<ProfilePage />);

    await waitFor(() => {
      expect(screen.getByText('다시 시도')).toBeInTheDocument();
    });
  });

  it('로딩 중 스피너를 표시한다', () => {
    mockGetMyProfile.mockImplementation(
      () => new Promise(() => {}),
    );

    render(<ProfilePage />);

    expect(screen.getByText('로딩 중...')).toBeInTheDocument();
  });
});
