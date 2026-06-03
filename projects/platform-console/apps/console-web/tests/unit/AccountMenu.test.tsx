import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AccountMenu } from '@/shared/ui/AccountMenu';

// TASK-PC-FE-041: the kebab account menu replaces the bare logout button. The
// 로그아웃 item runs the existing RP-initiated OIDC logout (performLogout);
// mock it so the test asserts the call without navigating the jsdom window.
const { logoutMock } = vi.hoisted(() => ({ logoutMock: vi.fn() }));
vi.mock('@/features/auth', () => ({ performLogout: logoutMock }));

beforeEach(() => {
  logoutMock.mockClear();
});

describe('AccountMenu (TASK-PC-FE-041)', () => {
  it('renders only the kebab trigger initially (menu closed)', () => {
    render(<AccountMenu accountLabel="ops@example.com" />);
    expect(screen.getByTestId('account-menu-trigger')).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(screen.queryByTestId('account-menu')).not.toBeInTheDocument();
  });

  it('opens the menu on trigger click: 아이디 / 계정 설정 / 로그아웃', async () => {
    render(<AccountMenu accountLabel="ops@example.com" />);
    await userEvent.click(screen.getByTestId('account-menu-trigger'));

    expect(screen.getByTestId('account-menu')).toBeInTheDocument();
    expect(screen.getByTestId('account-menu-trigger')).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    expect(screen.getByTestId('account-menu-id')).toHaveTextContent(
      'ops@example.com',
    );
    expect(screen.getByTestId('account-menu-settings')).toHaveAttribute(
      'href',
      '/account',
    );
    expect(screen.getByTestId('logout-button')).toBeInTheDocument();
  });

  it('runs performLogout when the 로그아웃 item is activated', async () => {
    render(<AccountMenu accountLabel="ops@example.com" />);
    await userEvent.click(screen.getByTestId('account-menu-trigger'));
    await userEvent.click(screen.getByTestId('logout-button'));
    expect(logoutMock).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape', async () => {
    render(<AccountMenu accountLabel="ops@example.com" />);
    await userEvent.click(screen.getByTestId('account-menu-trigger'));
    expect(screen.getByTestId('account-menu')).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByTestId('account-menu')).not.toBeInTheDocument();
  });

  it('closes on an outside click', async () => {
    render(<AccountMenu accountLabel="ops@example.com" />);
    await userEvent.click(screen.getByTestId('account-menu-trigger'));
    expect(screen.getByTestId('account-menu')).toBeInTheDocument();

    fireEvent.mouseDown(document.body);
    expect(screen.queryByTestId('account-menu')).not.toBeInTheDocument();
  });
});
