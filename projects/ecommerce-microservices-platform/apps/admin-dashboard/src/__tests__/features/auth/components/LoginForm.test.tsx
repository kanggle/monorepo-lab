import { render, screen } from '@testing-library/react';
import { LoginForm } from '@/features/auth/components/LoginForm';

const mockReplace = vi.fn();
const mockPush = vi.fn();
const mockGet = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush }),
  useSearchParams: () => ({ get: mockGet }),
}));

const mockUseAuth = vi.fn();
vi.mock('@/shared/hooks', () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock('@/features/auth/components/SocialLoginButtons', () => ({
  SocialLoginButtons: () => <div data-testid="social-login-buttons" />,
}));

describe('LoginForm', () => {
  beforeEach(() => {
    mockReplace.mockClear();
    mockPush.mockClear();
    mockGet.mockClear();
    mockUseAuth.mockReturnValue({
      isLoading: false,
      isAuthenticated: false,
      login: vi.fn(),
    });
  });

  it('error=oauth_failed 쿼리 파라미터가 있을 때 Google 로그인 실패 에러 메시지를 표시한다', () => {
    mockGet.mockImplementation((key: string) => (key === 'error' ? 'oauth_failed' : null));

    render(<LoginForm />);

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Google 로그인에 실패했습니다. 다시 시도해 주세요.',
    );
  });

  it('error 쿼리 파라미터가 없을 때 에러 메시지를 표시하지 않는다', () => {
    mockGet.mockReturnValue(null);

    render(<LoginForm />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('error 쿼리 파라미터가 oauth_failed가 아닌 다른 값일 때 에러 메시지를 표시하지 않는다', () => {
    mockGet.mockImplementation((key: string) => (key === 'error' ? 'unknown_error' : null));

    render(<LoginForm />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
