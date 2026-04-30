import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { SocialLoginButtons } from '@/features/auth/components/SocialLoginButtons';

describe('SocialLoginButtons', () => {
  const originalLocation = window.location;
  let assignMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    assignMock = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        ...originalLocation,
        assign: assignMock,
        href: 'http://localhost:3000/login',
        origin: 'http://localhost:3000',
        pathname: '/login',
      },
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
  });

  it('renders all three provider buttons with separator', () => {
    render(<SocialLoginButtons />);
    expect(screen.getByRole('button', { name: /Google로 계속하기/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Kakao로 계속하기/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Microsoft로 계속하기/ })).toBeInTheDocument();
    expect(screen.getByText('또는')).toBeInTheDocument();
  });

  it('calls /api/auth/oauth/authorize and redirects to authorizationUrl on click', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth?state=abc',
          state: 'abc',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SocialLoginButtons />);
    await user.click(screen.getByRole('button', { name: /Google로 계속하기/ }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const calledUrl = fetchMock.mock.calls[0][0] as string;
    expect(calledUrl).toContain('/api/auth/oauth/authorize');
    expect(calledUrl).toContain('provider=google');
    expect(calledUrl).toContain('redirectUri=');
    expect(decodeURIComponent(calledUrl)).toContain('/oauth/callback');

    await waitFor(() =>
      expect(assignMock).toHaveBeenCalledWith('https://accounts.google.com/o/oauth2/v2/auth?state=abc'),
    );
  });

  it('disables all buttons while a request is in flight', async () => {
    const user = userEvent.setup();
    let resolveFetch: ((value: Response) => void) | undefined;
    const fetchMock = vi.fn().mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          resolveFetch = resolve;
        }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SocialLoginButtons />);
    const googleBtn = screen.getByRole('button', { name: /Google로 계속하기/ });
    const kakaoBtn = screen.getByRole('button', { name: /Kakao로 계속하기/ });

    await user.click(googleBtn);
    await waitFor(() => expect(googleBtn).toBeDisabled());
    expect(kakaoBtn).toBeDisabled();
    expect(googleBtn).toHaveAttribute('aria-busy', 'true');

    // Cleanup so the in-flight promise does not leak between tests.
    resolveFetch?.(
      new Response(
        JSON.stringify({ authorizationUrl: 'https://example.test', state: 'x' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
  });

  it('shows an error message when authorize fails (PROVIDER_ERROR)', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'PROVIDER_ERROR', message: 'upstream' }), {
        status: 502,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SocialLoginButtons />);
    await user.click(screen.getByRole('button', { name: /Google로 계속하기/ }));

    expect(
      await screen.findByText(/소셜 로그인 서비스에 일시적인 문제가 발생했습니다/),
    ).toBeInTheDocument();
    expect(assignMock).not.toHaveBeenCalled();
  });
});
