import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const pushMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => new URLSearchParams(),
}));

import { DashboardTabs } from '@/app/(console)/dashboards/_components/DashboardTabs';

describe('DashboardTabs', () => {
  beforeEach(() => pushMock.mockReset());

  it('renders all three tab buttons', () => {
    render(<DashboardTabs activeTab="accounts" />);
    expect(screen.getByRole('tab', { name: '계정 현황' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '보안 이벤트' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '시스템 상태' })).toBeInTheDocument();
  });

  it('marks the active tab with aria-selected=true', () => {
    render(<DashboardTabs activeTab="security" />);
    expect(screen.getByRole('tab', { name: '보안 이벤트' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: '계정 현황' })).toHaveAttribute('aria-selected', 'false');
    expect(screen.getByRole('tab', { name: '시스템 상태' })).toHaveAttribute('aria-selected', 'false');
  });

  it('applies active style class to active tab', () => {
    render(<DashboardTabs activeTab="system" />);
    const activeTab = screen.getByRole('tab', { name: '시스템 상태' });
    expect(activeTab.className).toContain('border-b-2');
    expect(activeTab.className).toContain('border-primary');
    expect(activeTab.className).toContain('font-medium');
  });

  it('applies muted style to inactive tabs', () => {
    render(<DashboardTabs activeTab="accounts" />);
    const inactiveTab = screen.getByRole('tab', { name: '보안 이벤트' });
    expect(inactiveTab.className).toContain('text-muted-foreground');
  });

  it('calls router.push with correct URL on tab click', async () => {
    const user = userEvent.setup();
    render(<DashboardTabs activeTab="accounts" />);
    await user.click(screen.getByRole('tab', { name: '보안 이벤트' }));
    expect(pushMock).toHaveBeenCalledWith('/dashboards?tab=security');
  });

  it('calls router.push with system tab URL when 시스템 상태 is clicked', async () => {
    const user = userEvent.setup();
    render(<DashboardTabs activeTab="accounts" />);
    await user.click(screen.getByRole('tab', { name: '시스템 상태' }));
    expect(pushMock).toHaveBeenCalledWith('/dashboards?tab=system');
  });
});
