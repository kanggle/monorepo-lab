import Link from 'next/link';
import { isAuthenticated } from '@/shared/lib/session';
import { sanitizeReturnPath } from '@/shared/lib/return-path';
import { redirect } from 'next/navigation';

export const dynamic = 'force-dynamic';

const ERROR_MESSAGES: Record<string, string> = {
  provider_error: 'IAM 로그인 중 오류가 발생했습니다. 다시 시도해주세요.',
  invalid_state: '로그인 세션이 만료되었습니다. 다시 로그인해주세요.',
  state_mismatch: '보안 검증에 실패했습니다. 다시 로그인해주세요.',
  token_exchange_failed:
    '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.',
  // Gap C (F5): operator-provisioning and transient server-side error codes
  // emitted by callback/route.ts — previously unmapped → silent failure.
  // NOTE (TASK-PC-FE-182 / ADR-MONO-044): the callback no longer emits
  // `not_provisioned` — a logged-in non-operator is routed to `/onboarding`
  // (self-service org creation) instead of here. This entry survives only as
  // a defensive fallback for a hand-crafted URL; the copy points at the new
  // self-service path rather than the old "ask an admin".
  not_provisioned:
    '아직 소속된 조직이 없습니다. 다시 로그인하면 조직 만들기로 안내됩니다.',
  operator_exchange_unavailable:
    '인증 서버 일시 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
};

/** Generic fallback message shown for any unrecognised error code. */
const GENERIC_ERROR =
  '로그인 중 오류가 발생했습니다. 다시 시도해주세요.';

/**
 * Login entry (IAM OIDC Auth Code + PKCE). Server component — no client JS,
 * minimal first-load (perf budget: /login 180 KB). The actual PKCE generation
 * + redirect happens in the `/api/auth/login` route handler; this page only
 * renders the "Sign in with GAP" link and any returned error.
 *
 * If already authenticated, skip straight to the console.
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; redirect?: string }>;
}) {
  if (await isAuthenticated()) redirect('/console');

  const sp = await searchParams;
  // Gap C (F5): unknown codes must never render silent (null → visible fallback).
  const error = sp.error
    ? (ERROR_MESSAGES[sp.error] ?? GENERIC_ERROR)
    : null;
  // Same-site sanitise via the shared predicate the login route also uses —
  // page and route must never diverge on "is this redirect safe?" (PC-FE-253).
  const next = sanitizeReturnPath(sp.redirect);
  const loginHref = `/api/auth/login?redirect=${encodeURIComponent(next)}`;

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted px-4">
      <div className="w-full max-w-sm rounded-lg border border-border bg-background p-8">
        <h1 className="text-xl font-semibold text-foreground">
          Platform Console
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          엔터프라이즈 스위트 통합 운영 콘솔
        </p>

        {error && (
          <div
            role="alert"
            className="mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {error}
          </div>
        )}

        <Link
          href={loginHref}
          prefetch={false}
          data-testid="iam-login"
          className="mt-6 inline-flex w-full items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        >
          IAM 계정으로 로그인
        </Link>

        <p className="mt-4 text-center text-xs text-muted-foreground">
          IAM OIDC (Authorization Code + PKCE) 단일 로그인
        </p>
      </div>
    </main>
  );
}
