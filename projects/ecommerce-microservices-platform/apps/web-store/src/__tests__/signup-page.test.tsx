import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSession, signIn } from 'next-auth/react';
import SignupPage from '@/app/(auth)/signup/page';
import { AuthProvider } from '@/features/auth/model/auth-context';

const mockUseSession = vi.mocked(useSession);
const mockSignIn = vi.mocked(signIn);

function renderSignupPage() {
  return render(
    <AuthProvider>
      <SignupPage />
    </AuthProvider>,
  );
}

/**
 * TASK-FE-097. `/signup` used to `redirect('/api/auth/signin/iam')` — a next-auth
 * v4 action this app's v5 runtime does not serve, so every click landed on
 * `/login?error=Configuration`.
 *
 * These assert the *behaviour* rather than the string, which is what makes them
 * bite on both wrong answers:
 *
 *  - reverting to a `/api/auth/signin/...` URL means `signIn` is never called;
 *  - linking straight at IAM's `/signup` (the other tempting fix) also skips
 *    `signIn`, and would additionally bypass `/oauth2/authorize` — which is how
 *    `SavedRequestTenantResolver` learns the tenant, so accounts created that way
 *    are born in `fan-platform` and the ecommerce edge rejects them.
 *
 * Either regression turns these red without anyone having to remember the URL.
 */
describe('SignupPage (TASK-FE-097)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSession.mockReturnValue({
      data: null,
      status: 'unauthenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);
  });

  it('OIDC 플로우를 signIn 프로바이더 호출로 시작한다 (v4 URL 로 이동하지 않는다)', async () => {
    renderSignupPage();

    await waitFor(() => expect(mockSignIn).toHaveBeenCalledTimes(1));
    expect(mockSignIn).toHaveBeenCalledWith('iam', { callbackUrl: '/' });
  });

  it('마운트 시 한 번만 시작한다 (리렌더가 로그인을 재개시하지 않는다)', async () => {
    const { rerender } = renderSignupPage();
    await waitFor(() => expect(mockSignIn).toHaveBeenCalledTimes(1));

    rerender(
      <AuthProvider>
        <SignupPage />
      </AuthProvider>,
    );

    await new Promise((r) => setTimeout(r, 10));
    expect(mockSignIn).toHaveBeenCalledTimes(1);
  });

  it('JS 가 늦거나 막혀도 누를 수 있는 버튼이 남는다', async () => {
    renderSignupPage();
    const button = await screen.findByRole('button', { name: 'Global Account 로 이동' });

    await userEvent.click(button);

    expect(mockSignIn).toHaveBeenCalledWith('iam', { callbackUrl: '/' });
  });

  it('무엇을 하는 화면인지 알려 준다 (빈 화면으로 리다이렉트를 기다리지 않는다)', async () => {
    renderSignupPage();
    expect(await screen.findByText(/Global Account 에서 진행합니다/)).toBeInTheDocument();
  });
});
