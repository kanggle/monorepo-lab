import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { NotificationPreferences } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/my/notifications/settings',
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

vi.mock('@/features/notification/api/notification-api');

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
}));

import { getMyPreferences, updateMyPreferences } from '@/features/notification/api/notification-api';
import { NotificationSettings } from '@/features/notification/ui/NotificationSettings';

const mockGetMyPreferences = vi.mocked(getMyPreferences);
const mockUpdateMyPreferences = vi.mocked(updateMyPreferences);

const MOCK_PREFERENCES: NotificationPreferences = {
  userId: 'user-1',
  emailEnabled: true,
  smsEnabled: false,
  pushEnabled: true,
};

describe('NotificationSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 토글이 표시되지 않는다', () => {
    mockGetMyPreferences.mockReturnValue(new Promise(() => {}));

    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    expect(screen.queryByRole('switch', { name: '이메일' })).not.toBeInTheDocument();
  });

  it('알림 설정을 렌더링한다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);

    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: '이메일' })).toBeInTheDocument();
    });
    expect(screen.getByRole('switch', { name: 'SMS' })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: '푸시' })).toBeInTheDocument();
  });

  it('설정 값에 따라 토글 상태를 표시한다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);

    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: '이메일' })).toBeChecked();
    });
    expect(screen.getByRole('switch', { name: 'SMS' })).not.toBeChecked();
    expect(screen.getByRole('switch', { name: '푸시' })).toBeChecked();
  });

  it('토글 클릭 시 설정을 변경하고 저장한다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);
    mockUpdateMyPreferences.mockResolvedValueOnce({ ...MOCK_PREFERENCES, smsEnabled: true });

    const user = userEvent.setup();
    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: 'SMS' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('switch', { name: 'SMS' }));

    await waitFor(() => {
      expect(mockUpdateMyPreferences).toHaveBeenCalledWith({
        emailEnabled: true,
        smsEnabled: true,
        pushEnabled: true,
      });
    });
  });

  it('저장 성공 시 피드백 메시지를 표시한다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);
    mockUpdateMyPreferences.mockResolvedValueOnce({ ...MOCK_PREFERENCES, smsEnabled: true });

    const user = userEvent.setup();
    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: 'SMS' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('switch', { name: 'SMS' }));

    await waitFor(() => {
      expect(screen.getByTestId('save-success')).toBeInTheDocument();
    });
    expect(screen.getByText('설정이 저장되었습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetMyPreferences.mockRejectedValueOnce(new Error('fail'));

    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('알림 설정을 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('설정 변경 실패 시 토글이 이전 상태로 롤백된다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);
    mockUpdateMyPreferences.mockRejectedValueOnce(new Error('Network error'));

    const user = userEvent.setup();
    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: 'SMS' })).toBeInTheDocument();
    });

    // SMS is initially off (false)
    expect(screen.getByRole('switch', { name: 'SMS' })).not.toBeChecked();

    await user.click(screen.getByRole('switch', { name: 'SMS' }));

    // After failure, toggle should revert to unchecked
    await waitFor(() => {
      expect(screen.getByRole('switch', { name: 'SMS' })).not.toBeChecked();
    });
  });

  it('설정 변경 실패 시 인라인 에러 메시지를 표시한다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);
    mockUpdateMyPreferences.mockRejectedValueOnce(new Error('Network error'));

    const user = userEvent.setup();
    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: 'SMS' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('switch', { name: 'SMS' }));

    await waitFor(() => {
      expect(screen.getByTestId('save-error')).toBeInTheDocument();
    });
  });

  it('알림 목록으로 돌아가는 링크를 표시한다', async () => {
    mockGetMyPreferences.mockResolvedValueOnce(MOCK_PREFERENCES);

    render(<TestQueryProvider><NotificationSettings /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('switch', { name: '이메일' })).toBeInTheDocument();
    });

    const backLink = screen.getByText(/알림 목록/);
    expect(backLink.closest('a')).toHaveAttribute('href', '/my/notifications');
  });
});
