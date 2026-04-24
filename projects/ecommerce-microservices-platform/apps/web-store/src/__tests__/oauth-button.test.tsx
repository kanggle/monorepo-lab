import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OAuthButton } from '@/features/auth/ui/OAuthButton';

describe('OAuthButton', () => {
  const originalLocation = window.location;

  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { ...originalLocation, origin: 'http://localhost:3000', href: '' },
    });
  });

  it('라벨을 표시한다', () => {
    render(<OAuthButton provider="google" label="Google로 로그인" icon={<span>G</span>} />);

    expect(screen.getByText('Google로 로그인')).toBeInTheDocument();
  });

  it('아이콘을 표시한다', () => {
    render(<OAuthButton provider="google" label="Google로 로그인" icon={<span data-testid="icon">G</span>} />);

    expect(screen.getByTestId('icon')).toBeInTheDocument();
  });

  it('클릭 시 OAuth URL로 리다이렉트한다', async () => {
    const user = userEvent.setup();
    render(<OAuthButton provider="google" label="Google로 로그인" icon={<span>G</span>} />);

    await user.click(screen.getByRole('button'));

    const expectedCallback = encodeURIComponent('http://localhost:3000/oauth/callback');
    expect(window.location.href).toBe(
      `http://localhost:8080/api/auth/oauth/google?callbackUrl=${expectedCallback}`,
    );
  });

  it('다른 provider로 올바른 URL을 구성한다', async () => {
    const user = userEvent.setup();
    render(<OAuthButton provider="kakao" label="카카오 로그인" icon={<span>K</span>} />);

    await user.click(screen.getByRole('button'));

    expect(window.location.href).toContain('/oauth/kakao?');
  });

  it('button type이 button이다', () => {
    render(<OAuthButton provider="google" label="Google" icon={<span>G</span>} />);

    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });
});
