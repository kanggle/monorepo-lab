import Link from 'next/link';
import { isAuthenticated } from '@/shared/lib/session';
import { SESSION_EXPIRED } from '@/shared/lib/re-login';
import { sanitizeReturnPath } from '@/shared/lib/return-path';
import { redirect } from 'next/navigation';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';
import { DemoLoginCredentials } from '@/widgets/demo-credentials/DemoLoginCredentials';

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
  // TASK-PC-FE-278 — 백엔드가 `401` 을 내서 **강제 재로그인**으로 꺾인 경우.
  // 🔴 이 코드는 `(console)` 아래 53개 지점이 붙이는 마커다({@link SESSION_EXPIRED}).
  [SESSION_EXPIRED]:
    '세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요.',
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
 * If already authenticated, skip straight to the console — **단, 강제 재로그인으로
 * 꺾여 온 경우는 예외다** (아래).
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 `TASK-PC-FE-278` — 이 페이지의 첫 줄이 재로그인을 삼키고 있었다
 * -----------------------------------------------------------------------------
 * `if (await isAuthenticated()) redirect('/console')` 는 **쿠키만** 보고 판정한다
 * ({@link isAuthenticated} 는 백엔드에 묻지 않는다). 백엔드가 `401` 을 내도 쿠키는
 * 멀쩡하므로, 401 지점이 보낸 재로그인 요청이 **여기서 되튕겨 카탈로그로 돌아갔다**:
 *
 * ```
 *   /ecommerce ──401──▶ /login ──쿠키 있음──▶ /console      (반짝임 + 무설명)
 * ```
 *
 * 계약(`console-integration-contract` § 2.4.6/§ 2.4.7)이 **"never a re-login loop"**
 * 라고 금지한 그 루프이고, 같은 문서가 스무 곳에서 요구한 *"forced whole-session
 * re-login"* 은 **한 번도 일어나지 않았다.**
 *
 * 🔵 고침은 술어 교체가 아니라 **정보 채널**이다 — 401 지점이
 * {@link SESSION_EXPIRED} 마커를 붙이고, 여기서는 그 마커가 있으면 단락 회로를 타지
 * 않는다. 마커가 없는 방문(운영자가 직접 `/login` 을 친 경우)은 **예전 그대로**
 * `/console` 로 보낸다 — 편의를 뺏지 않았다는 것이 이 수리의 대조군이다.
 *
 * 🔴 죽은 쿠키를 **지우는** 것은 여기가 아니라 `/api/auth/login` 이다(서버 컴포넌트는
 * 쿠키를 못 바꾼다 — 애초에 이 결함이 생긴 구조적 이유다). 그 라우트가 PKCE 를 걸기
 * 전에 {@link clearFullSession} 을 부른다.
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string; redirect?: string }>;
}) {
  const sp = await searchParams;

  // 🔴 순서가 바뀌었다 — `searchParams` 를 **먼저** 읽어야 마커를 볼 수 있다.
  //    마커가 붙어 있으면 쿠키가 남아 있어도 로그인 화면을 보여준다. 그 쿠키는
  //    백엔드가 이미 거절한 것이므로 "인증됨" 의 증거가 아니다.
  const forcedReLogin = sp.error === SESSION_EXPIRED;
  if (!forcedReLogin && (await isAuthenticated())) redirect('/console');

  // Gap C (F5): unknown codes must never render silent (null → visible fallback).
  const error = sp.error
    ? (ERROR_MESSAGES[sp.error] ?? GENERIC_ERROR)
    : null;
  // Same-site sanitise via the shared predicate the login route also uses —
  // page and route must never diverge on "is this redirect safe?" (PC-FE-253).
  const next = sanitizeReturnPath(sp.redirect);
  const loginHref = `/api/auth/login?redirect=${encodeURIComponent(next)}`;

  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-muted px-4">
      {/* TASK-MONO-585 AC-3 — 콘솔의 **유일한 익명 표면**이다(page.tsx 67개 중 1개).
          데모가 꺼져 있으면 IdP 도 함께 꺼져 로그인이 실패하는데, 이 자리에 아무 말도
          없으면 방문자는 그 실패를 앱의 고장으로 읽는다. 데모가 아닌 배포에서는
          아무것도 렌더하지 않는다(위젯의 `not-demo` 분기). */}
      <DemoBackendNotice />
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

        {/* TASK-PC-FE-275 — 이 화면이 계정을 말하지 않으면 방문자는 «회원가입» 을 눌러
            빈 조직(`/onboarding`)에 도착한다. `TASK-MONO-561` 이 런처에서 고친 결함의
            낙오한 형제였다. 데모가 아닌 배포에서는 아무것도 렌더하지 않는다(위젯의
            `not-demo` 분기). */}
        <DemoLoginCredentials />
      </div>
    </main>
  );
}
