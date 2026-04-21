import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { NotificationSummary, PaginatedResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/my/notifications',
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

vi.mock('@/features/notification/api/notification-api');

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner">로딩 중...</div>,
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

import { getMyNotifications } from '@/features/notification/api/notification-api';
import { NotificationList } from '@/features/notification/ui/NotificationList';

const mockGetMyNotifications = vi.mocked(getMyNotifications);

const MOCK_NOTIFICATIONS: NotificationSummary[] = [
  {
    notificationId: 'notif-1',
    channel: 'EMAIL',
    subject: '주문이 접수되었습니다',
    status: 'SENT',
    sentAt: '2026-04-01T10:00:00Z',
    createdAt: '2026-04-01T10:00:00Z',
  },
  {
    notificationId: 'notif-2',
    channel: 'SMS',
    subject: '배송이 시작되었습니다',
    status: 'SENT',
    sentAt: '2026-04-02T14:00:00Z',
    createdAt: '2026-04-02T14:00:00Z',
  },
];

function createPaginatedResponse(
  content: NotificationSummary[],
  page = 0,
  size = 20,
  totalElements = content.length,
): PaginatedResponse<NotificationSummary> {
  return { content, page, size, totalElements };
}

describe('NotificationList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 알림 카드가 표시되지 않는다', () => {
    mockGetMyNotifications.mockReturnValue(new Promise(() => {}));

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    expect(screen.queryAllByTestId('notification-card')).toHaveLength(0);
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('알림 목록을 렌더링한다', async () => {
    mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS));

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getAllByTestId('notification-card')).toHaveLength(2);
    });
    expect(screen.getByText('주문이 접수되었습니다')).toBeInTheDocument();
    expect(screen.getByText('배송이 시작되었습니다')).toBeInTheDocument();
  });

  it('알림 채널 라벨을 표시한다', async () => {
    mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS));

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('이메일')).toBeInTheDocument();
    });
    expect(screen.getByText('SMS')).toBeInTheDocument();
  });

  it('SENT 상태가 아닌 알림은 표시하지 않는다', async () => {
    const mixedNotifications: NotificationSummary[] = [
      ...MOCK_NOTIFICATIONS,
      {
        notificationId: 'notif-pending',
        channel: 'EMAIL',
        subject: '대기 중인 알림',
        status: 'PENDING',
        sentAt: '2026-04-03T10:00:00Z',
        createdAt: '2026-04-03T10:00:00Z',
      },
      {
        notificationId: 'notif-failed',
        channel: 'PUSH',
        subject: '실패한 알림',
        status: 'FAILED',
        sentAt: '2026-04-03T11:00:00Z',
        createdAt: '2026-04-03T11:00:00Z',
      },
    ];
    mockGetMyNotifications.mockResolvedValueOnce(
      createPaginatedResponse(mixedNotifications, 0, 20, 4),
    );

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getAllByTestId('notification-card')).toHaveLength(2);
    });
    expect(screen.queryByText('대기 중인 알림')).not.toBeInTheDocument();
    expect(screen.queryByText('실패한 알림')).not.toBeInTheDocument();
  });

  it('알림이 없으면 빈 상태를 표시한다', async () => {
    mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse([]));

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('알림이 없습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetMyNotifications.mockRejectedValueOnce(new Error('fail'));

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('알림 목록을 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('에러 후 재시도 버튼을 클릭하면 다시 로드한다', async () => {
    mockGetMyNotifications.mockRejectedValueOnce(new Error('fail'));

    const user = userEvent.setup();
    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });

    mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS));
    await user.click(screen.getByText('재시도'));

    await waitFor(() => {
      expect(screen.getAllByTestId('notification-card')).toHaveLength(2);
    });
  });

  it('알림 설정 링크를 표시한다', async () => {
    mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS));

    render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

    const settingsLink = screen.getByText('알림 설정');
    expect(settingsLink).toBeInTheDocument();
    expect(settingsLink.closest('a')).toHaveAttribute('href', '/my/notifications/settings');
  });

  describe('페이지네이션', () => {
    it('페이지네이션 컨트롤을 표시한다', async () => {
      mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS, 0, 20, 50));

      render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('1 / 3')).toBeInTheDocument();
      });
      expect(screen.getByText('이전')).toBeInTheDocument();
      expect(screen.getByText('다음')).toBeInTheDocument();
    });

    it('첫 페이지에서 이전 버튼이 비활성화된다', async () => {
      mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS, 0, 20, 50));

      render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('이전')).toBeDisabled();
      });
    });

    it('다음 버튼 클릭 시 다음 페이지를 로드한다', async () => {
      mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS, 0, 20, 50));

      const user = userEvent.setup();
      render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('다음')).toBeEnabled();
      });

      mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS, 1, 20, 50));
      await user.click(screen.getByText('다음'));

      await waitFor(() => {
        expect(screen.getByText('2 / 3')).toBeInTheDocument();
      });
    });

    it('페이지 크기를 변경할 수 있다', async () => {
      mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS, 0, 20, 50));

      const user = userEvent.setup();
      render(<TestQueryProvider><NotificationList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByLabelText('페이지 크기:')).toBeInTheDocument();
      });

      mockGetMyNotifications.mockResolvedValueOnce(createPaginatedResponse(MOCK_NOTIFICATIONS, 0, 10, 50));
      await user.selectOptions(screen.getByLabelText('페이지 크기:'), '10');

      await waitFor(() => {
        expect(mockGetMyNotifications).toHaveBeenCalledWith(0, 10);
      });
    });
  });
});
