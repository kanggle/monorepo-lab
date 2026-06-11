import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationBell } from '@/features/notification/ui/NotificationBell';
import type { Notification } from '@/entities/notification';

const { markNotificationRead, markAllNotificationsRead } = vi.hoisted(() => ({
  markNotificationRead: vi.fn(async () => {}),
  markAllNotificationsRead: vi.fn(async () => {}),
}));
vi.mock('@/features/notification/api/actions', () => ({
  markNotificationRead,
  markAllNotificationsRead,
}));

function note(over: Partial<Notification> = {}): Notification {
  return {
    id: 'n-1',
    type: 'WELCOME',
    title: '멤버십이 시작되었어요',
    body: '프리미엄 멤버십이 활성화되었습니다.',
    status: 'UNREAD',
    read: false,
    createdAt: '2026-06-11T00:00:00Z',
    ...over,
  };
}

describe('NotificationBell', () => {
  beforeEach(() => {
    markNotificationRead.mockClear();
    markAllNotificationsRead.mockClear();
  });

  it('shows the unread count badge', () => {
    render(<NotificationBell initialItems={[note()]} initialUnread={3} />);
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('3');
  });

  it('caps the badge at 9+', () => {
    render(<NotificationBell initialItems={[note()]} initialUnread={12} />);
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('9+');
  });

  it('hides the badge when there are no unread notifications', () => {
    render(<NotificationBell initialItems={[]} initialUnread={0} />);
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });

  it('renders the empty state in the dropdown', async () => {
    const user = userEvent.setup();
    render(<NotificationBell initialItems={[]} initialUnread={0} />);
    await user.click(screen.getByRole('button', { name: '알림' }));
    expect(screen.getByText('새 알림이 없습니다')).toBeInTheDocument();
  });

  it('marks an unread item read and decrements the badge optimistically', async () => {
    const user = userEvent.setup();
    render(
      <NotificationBell
        initialItems={[note({ id: 'n-7', title: '환영합니다' })]}
        initialUnread={1}
      />,
    );
    await user.click(screen.getByRole('button', { name: '알림' }));
    await user.click(screen.getByTestId('notification-item'));

    expect(markNotificationRead).toHaveBeenCalledWith('n-7');
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });

  it('marks all visible unread items read', async () => {
    const user = userEvent.setup();
    render(
      <NotificationBell
        initialItems={[
          note({ id: 'a', read: false, status: 'UNREAD' }),
          note({ id: 'b', read: false, status: 'UNREAD' }),
        ]}
        initialUnread={2}
      />,
    );
    await user.click(screen.getByRole('button', { name: '알림' }));
    await user.click(screen.getByRole('button', { name: '모두 읽음' }));

    expect(markAllNotificationsRead).toHaveBeenCalledWith(['a', 'b']);
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument();
  });
});
