import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileForm } from '@/features/user/ui/ProfileForm';
import type {
  UserProfile,
  ApiErrorResponse,
  UpdateUserProfileResponse,
} from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('@/features/user/api/user-profile-api', () => ({
  updateMyProfile: vi.fn(),
}));

import { updateMyProfile } from '@/features/user/api/user-profile-api';
const mockUpdateMyProfile = vi.mocked(updateMyProfile);

const MOCK_PROFILE: UserProfile = {
  userId: 'user-1',
  email: 'test@example.com',
  name: '홍길동',
  nickname: '길동이',
  phone: '010-1234-5678',
  profileImageUrl: 'https://example.com/image.jpg',
  status: 'ACTIVE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockOnUpdated = vi.fn();

function renderProfileForm(profile = MOCK_PROFILE) {
  return render(<TestQueryProvider><ProfileForm profile={profile} onUpdated={mockOnUpdated} /></TestQueryProvider>);
}

describe('ProfileForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('기본 정보(이메일, 이름)를 표시한다', () => {
    renderProfileForm();

    expect(screen.getByText('test@example.com')).toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
  });

  it('프로필 필드(닉네임, 전화번호)를 입력 필드로 표시한다', () => {
    renderProfileForm();

    expect(screen.getByLabelText('닉네임')).toHaveValue('길동이');
    expect(screen.getByLabelText('전화번호')).toHaveValue('010-1234-5678');
  });

  it('변경사항이 없으면 수정 버튼이 비활성화된다', () => {
    renderProfileForm();

    const button = screen.getByRole('button', { name: '프로필 수정' });
    expect(button).toBeDisabled();
  });

  it('필드를 변경하면 수정 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');

    const button = screen.getByRole('button', { name: '프로필 수정' });
    expect(button).toBeEnabled();
  });

  it('수정 성공 시 성공 메시지를 표시하고 onUpdated를 호출한다', async () => {
    mockUpdateMyProfile.mockResolvedValueOnce({
      userId: 'user-1',
      email: 'test@example.com',
      name: '홍길동',
      nickname: '새닉네임',
      phone: '010-1234-5678',
      profileImageUrl: 'https://example.com/image.jpg',
      status: 'ACTIVE',
      updatedAt: '2024-01-02T00:00:00Z',
    });

    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    await waitFor(() => {
      expect(screen.getByText('프로필이 수정되었습니다.')).toBeInTheDocument();
    });

    expect(mockOnUpdated).toHaveBeenCalledTimes(1);
  });

  it('변경된 필드만 PATCH 요청에 포함한다', async () => {
    mockUpdateMyProfile.mockResolvedValueOnce({
      userId: 'user-1',
      email: 'test@example.com',
      name: '홍길동',
      nickname: '새닉네임',
      phone: '010-1234-5678',
      profileImageUrl: 'https://example.com/image.jpg',
      status: 'ACTIVE',
      updatedAt: '2024-01-02T00:00:00Z',
    });

    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    await waitFor(() => {
      expect(mockUpdateMyProfile).toHaveBeenCalledWith({ nickname: '새닉네임' });
    });
  });

  it('유효하지 않은 전화번호를 입력하면 에러를 표시한다', async () => {
    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('전화번호'));
    await user.type(screen.getByLabelText('전화번호'), 'abc');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    expect(
      screen.getByText('전화번호 형식이 올바르지 않습니다.'),
    ).toBeInTheDocument();
    expect(mockUpdateMyProfile).not.toHaveBeenCalled();
  });

  it('API 에러 시 에러 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'VALIDATION_ERROR',
      message: 'Invalid field',
      timestamp: new Date().toISOString(),
    };
    mockUpdateMyProfile.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    await waitFor(() => {
      expect(screen.getByText('입력값을 확인해주세요.')).toBeInTheDocument();
    });
  });

  it('알 수 없는 에러 시 기본 메시지를 표시한다', async () => {
    mockUpdateMyProfile.mockRejectedValueOnce(new Error('unknown'));

    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    await waitFor(() => {
      expect(
        screen.getByText('프로필 수정에 실패했습니다.'),
      ).toBeInTheDocument();
    });
  });

  it('수정 처리 중 중복 클릭을 방지한다', async () => {
    let resolveUpdate: (value: UpdateUserProfileResponse) => void;
    mockUpdateMyProfile.mockImplementationOnce(
      () =>
        new Promise<UpdateUserProfileResponse>((resolve) => {
          resolveUpdate = resolve;
        }),
    );

    const user = userEvent.setup();
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');

    const button = screen.getByRole('button', { name: '프로필 수정' });
    await user.click(button);

    expect(screen.getByRole('button', { name: '수정 중...' })).toBeDisabled();
    expect(mockUpdateMyProfile).toHaveBeenCalledTimes(1);

    resolveUpdate!({
      userId: 'user-1',
      email: 'test@example.com',
      name: '홍길동',
      nickname: '새닉네임',
      phone: '010-1234-5678',
      profileImageUrl: 'https://example.com/image.jpg',
      status: 'ACTIVE',
      updatedAt: '2024-01-02T00:00:00Z',
    });

    await waitFor(() => {
      expect(screen.getByText('프로필이 수정되었습니다.')).toBeInTheDocument();
    });
  });

  it('성공 토스트가 일정 시간 후 자동으로 사라진다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockUpdateMyProfile.mockResolvedValueOnce({
      userId: 'user-1',
      email: 'test@example.com',
      name: '홍길동',
      nickname: '새닉네임',
      phone: '010-1234-5678',
      profileImageUrl: 'https://example.com/image.jpg',
      status: 'ACTIVE',
      updatedAt: '2024-01-02T00:00:00Z',
    });

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    await waitFor(() => {
      expect(screen.getByText('프로필이 수정되었습니다.')).toBeInTheDocument();
    });

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.queryByText('프로필이 수정되었습니다.')).not.toBeInTheDocument();

    vi.useRealTimers();
  });

  it('에러 토스트가 일정 시간 후 자동으로 사라진다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockUpdateMyProfile.mockRejectedValueOnce(new Error('unknown'));

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderProfileForm();

    await user.clear(screen.getByLabelText('닉네임'));
    await user.type(screen.getByLabelText('닉네임'), '새닉네임');
    await user.click(screen.getByRole('button', { name: '프로필 수정' }));

    await waitFor(() => {
      expect(screen.getByText('프로필 수정에 실패했습니다.')).toBeInTheDocument();
    });

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.queryByText('프로필 수정에 실패했습니다.')).not.toBeInTheDocument();

    vi.useRealTimers();
  });

  it('nullable 필드가 null인 프로필을 올바르게 렌더링한다', () => {
    const profileWithNulls: UserProfile = {
      ...MOCK_PROFILE,
      nickname: null,
      phone: null,
      profileImageUrl: null,
    };

    renderProfileForm(profileWithNulls);

    expect(screen.getByLabelText('닉네임')).toHaveValue('');
    expect(screen.getByLabelText('전화번호')).toHaveValue('');
  });
});
