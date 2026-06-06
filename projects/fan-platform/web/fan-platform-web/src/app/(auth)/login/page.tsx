import Link from 'next/link';
import { signIn } from '@/shared/auth/auth';
import { Button } from '@/shared/ui/Button';

/**
 * Public login page. Triggers `signIn('iam', ...)` which redirects to GAP's
 * `/oauth2/authorize` endpoint with PKCE + state. After GAP roundtrip the
 * `[...nextauth]` callback completes the code-exchange and sets the session
 * cookie.
 */
export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ from?: string; error?: string }>;
}) {
  const params = await searchParams;
  const callbackUrl = params.from ?? '/';

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

        {params.error ? (
          <p
            role="alert"
            className="mt-4 rounded-md bg-accent-50 p-3 text-sm text-accent-700"
          >
            로그인에 실패했습니다. 잠시 후 다시 시도해주세요.
          </p>
        ) : null}

        <form
          className="mt-6"
          action={async () => {
            'use server';
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
