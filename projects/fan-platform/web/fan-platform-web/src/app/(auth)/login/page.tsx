// DEMO-RESOLVER-CONSUMER: fan-platform-web   (ADR-MONO-068 § D6 = B2 — 구현은 @demo/backend-resolver 하나뿐이다)
//
// 🔵 이 파일은 해석기를 **소비**할 뿐 자기가 주소를 만들지 않는다. 형제(`DemoBackendNotice`)가
//    적어 둔 이유 그대로 마커를 단다 — 마커를 안 달고 문구를 바꿔 가드의 눈을 피하는 쪽이 나쁘다.
import Link from 'next/link';
import { signIn } from '@/shared/auth/auth';
import { assertOidcIssuerConfigured } from '@/shared/config/env';
import { resolveDemoBackendState } from '@/shared/config/demo-backend';
import { Button } from '@/shared/ui/Button';

/**
 * Auth.js 가 `/login?error=<code>` 로 돌려보내는 코드별 문구 (`TASK-FAN-FE-020` AC-1).
 *
 * 🔴 **이 목록은 «전수» 가 아니다.** Auth.js 판이 올라가면 코드가 늘 수 있고, 그래서
 * 이 맵이 아니라 아래 {@link GENERIC_ERROR} fallback 이 AC-1 의 본체다 — 알 수 없는
 * 코드가 **조용히 사라지는 것**이 이 티켓이 고치는 결함의 절반이다.
 */
const ERROR_MESSAGES: Record<string, string> = {
  // 설정 오류 **또는** OIDC discovery 실패. 후자가 압도적으로 흔한데(데모 IdP 가 꺼져
  // 있으면 여기로 떨어진다) 그 경우는 아래 데모 분기가 먼저 가로챈다 — 이 문구는
  // 데모가 **켜져 있는데도** Configuration 이 난 경우, 즉 진짜 설정 결함용이다.
  Configuration:
    'IAM 인증 서버에 연결할 수 없습니다. 문제가 계속되면 관리자에게 문의해주세요.',
  AccessDenied: '이 계정으로는 로그인할 수 없습니다. 접근 권한을 확인해주세요.',
  Verification: '로그인 링크가 만료되었거나 이미 사용되었습니다. 다시 시도해주세요.',
};

/** 알 수 없는 코드용 fallback — 조용한 실패 금지 (AC-1). */
const GENERIC_ERROR = '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';

/**
 * 데모 백엔드가 꺼져 있을 때의 **로그인 전용** 문구 (AC-2).
 *
 * 🔴 `(main)` 배너를 재사용하지 않는 이유 — 그 문구는 *"지금 보이는 피드와 아티스트는
 * 샘플 데이터입니다"* 로 시작하는데 `/login` 에는 피드가 없다. 형제 위젯
 * (`DemoBackendNotice`) 이 *"로그인 실패는 **다른 증상**이라 다른 처방이 필요하다"* 고
 * 적으며 이 자리를 **알고 비워 뒀고**, 이것이 그 처방이다.
 *
 * 🔴 마지막 문장이 이 티켓의 본체다 — 옛 문구 *"잠시 후 다시 시도해주세요"* 는
 * **재시도가 고친다고 약속**했는데, 데모가 꺼진 동안에는 몇 번을 눌러도 같은 결과다.
 */
const DEMO_OFF_MESSAGE =
  '데모 서버가 꺼져 있어 로그인할 수 없습니다. 데모 시작 페이지에서 서버를 켠 뒤(약 10분) 다시 열어주세요. 서버가 꺼진 동안에는 다시 시도해도 같은 결과입니다.';

/**
 * Public login page. Triggers `signIn('iam', ...)` which redirects to GAP's
 * `/oauth2/authorize` endpoint with PKCE + state. After GAP roundtrip the
 * `[...nextauth]` callback completes the code-exchange and sets the session
 * cookie.
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 실패를 **한 문장으로 뭉개지 않는다** (`TASK-FAN-FE-020`)
 * -----------------------------------------------------------------------------
 * 2026-09-10 UTC 실측 — `fan.hubwang.com/api/auth/{providers,session,csrf}` 는 셋 다
 * **200**(⇒ 시크릿은 들어가 있다. 2026-08-26 의 「`NEXTAUTH_SECRET` 미투입」 뿌리가
 * **아니다** — 그때는 셋 다 500 이었다)인데 `/api/auth/signin/iam` 만 **302 →
 * `?error=Configuration`** 이었다. 원인은 `auth.hubwang.com/.well-known/openid-configuration`
 * 이 **503**(*"데모 백엔드가 지금 꺼져 있습니다"*)을 주는 것 — 즉 깨진 것은 설정이 아니라
 * **discovery 한 다리**이고, `signIn` 이 그 fetch 가 실제로 나가는 자리다(아래 주석).
 *
 * 🔵 그래서 판정 순서가 「코드 → 문구」가 아니라 **「데모 상태 → 코드 → 문구」** 다.
 * 두 문장을 같이 띄우면 화면이 스스로 모순된다(하나는 *"기다리면 된다"*, 다른 하나는
 * *"켜야 한다"*) ⇒ 상호배타로 렌더한다 (AC-3).
 *
 * 🔴 `not-demo`(로컬 개발·CI)에서는 데모 문구를 **내지 않는다** — 거기서 "데모가 꺼졌다"는
 * 거짓이다. 판정을 가르는 것은 백엔드의 상태가 아니라 **컨트롤 플레인이 설정돼 있는가**이고,
 * 그 규칙은 형제 위젯과 동일하다 (AC-4).
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ from?: string; error?: string }>;
}) {
  const params = await searchParams;
  const callbackUrl = params.from ?? '/';

  // 🔵 서버 컴포넌트에서만 판정한다 — `DEMO_API_BASE` 는 비공개 env 이고 그 이름이
  //    클라이언트 번들에 들어가면 안 된다. `/login` 은 이미 서버 컴포넌트라 경계 이동 없음.
  const demoOff = (await resolveDemoBackendState()) === 'unavailable';

  const codeMessage = params.error
    ? (ERROR_MESSAGES[params.error] ?? GENERIC_ERROR)
    : null;

  return (
    <main className="mx-auto flex min-h-[80vh] max-w-md flex-col items-center justify-center gap-6 p-8">
      <header className="text-center">
        <h1 className="bg-gradient-to-r from-brand-600 to-accent-500 bg-clip-text text-3xl font-bold text-transparent">
          fan-platform
        </h1>
        <p className="mt-2 text-sm text-ink-600">
          좋아하는 아티스트의 가장 가까운 곳에서.
        </p>
      </header>

      <section className="w-full rounded-xl border border-ink-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-ink-900">로그인</h2>
        <p className="mt-1 text-sm text-ink-600">
          IAM 으로 안전하게 로그인합니다.
        </p>

        {/* 🔴 상호배타 — 데모가 꺼져 있으면 그 사실만 말한다 (AC-2/AC-3). 색은 형제
            위젯(`DemoBackendNotice`)과 같은 amber 로 맞춘다: 방문자가 두 화면에서
            같은 사실을 같은 색으로 본다. */}
        {demoOff ? (
          <p
            role="status"
            data-testid="login-demo-off"
            className="mt-4 rounded-md border border-[#fcd34d] bg-[#fef3c7] p-3 text-sm text-[#92400e]"
          >
            {DEMO_OFF_MESSAGE}
          </p>
        ) : codeMessage ? (
          <p
            role="alert"
            data-testid="login-error"
            className="mt-4 rounded-md bg-accent-50 p-3 text-sm text-accent-700"
          >
            {codeMessage}
          </p>
        ) : null}

        <form
          className="mt-6"
          action={async () => {
            'use server';
            // 🔴 Request-scoped, on purpose. `signIn` is where OIDC discovery
            // actually goes over the network, so this is the last point before
            // the fetch -- and the first point at which throwing costs nothing
            // but this one sign-in attempt. Asserting at module scope instead
            // breaks `next build` outright (TASK-MONO-611, measured).
            assertOidcIssuerConfigured();
            await signIn('iam', { redirectTo: callbackUrl });
          }}
        >
          <Button type="submit" size="lg" className="w-full" data-testid="oidc-signin">
            GAP 로 로그인
          </Button>
        </form>

        <p className="mt-4 text-xs text-ink-500">
          로그인하면 fan-platform의{' '}
          <Link href="#" className="text-brand-600 hover:underline">
            서비스 약관
          </Link>{' '}
          및{' '}
          <Link href="#" className="text-brand-600 hover:underline">
            개인정보 처리방침
          </Link>
          에 동의하는 것으로 간주됩니다.
        </p>
      </section>
    </main>
  );
}
