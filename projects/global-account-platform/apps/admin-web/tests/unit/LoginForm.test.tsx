import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { runAxe } from '../a11y/axe-helper';

const pushMock = vi.fn();
const refreshMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, refresh: refreshMock }),
  useSearchParams: () => new URLSearchParams(''),
}));

import { LoginForm } from '@/features/auth/components/LoginForm';

describe('LoginForm', () => {
  beforeEach(() => {
    pushMock.mockReset();
    refreshMock.mockReset();
  });

  it('shows validation errors for invalid inputs', async () => {
    const user = userEvent.setup();
    render(<LoginForm />);
    await user.click(screen.getByRole('button', { name: /로그인/ }));
    const alerts = await screen.findAllByRole('alert');
    expect(alerts.length).toBeGreaterThan(0);
  });

  it('redirects on successful login', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<LoginForm />);
    await user.type(screen.getByLabelText('운영자 ID'), 'admin@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: /로그인/ }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith('/accounts'));
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' }));
  });

  it('has no axe violations on initial render', async () => {
    const { container } = render(<LoginForm />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });

  it('shows a friendly error on 401', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'CREDENTIALS_INVALID', message: 'nope' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<LoginForm />);
    await user.type(screen.getByLabelText('운영자 ID'), 'admin@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: /로그인/ }));

    expect(await screen.findByText(/이메일 또는 비밀번호가 올바르지 않습니다/)).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
