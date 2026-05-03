import Link from 'next/link';
import { signOut } from '@/shared/auth/auth';
import { getFanSession, isAuthenticated } from '@/shared/auth/session';

/** Top navigation. Server Component — reads session via the server boundary. */
export async function Header() {
  const authed = await isAuthenticated();
  const session = authed ? await getFanSession() : null;

  return (
    <header className="sticky top-0 z-10 border-b border-ink-200 bg-white/80 backdrop-blur dark:bg-ink-900/80 dark:border-ink-800">
      <nav className="mx-auto flex max-w-5xl items-center gap-6 px-4 py-3">
        <Link
          href="/"
          className="bg-gradient-to-r from-brand-600 to-accent-500 bg-clip-text text-lg font-bold text-transparent"
        >
          fan-platform
        </Link>
        <Link href="/" className="text-sm text-ink-700 hover:text-brand-600 dark:text-ink-200">
          피드
        </Link>
        <Link
          href="/artists"
          className="text-sm text-ink-700 hover:text-brand-600 dark:text-ink-200"
        >
          아티스트
        </Link>
        <Link
          href="/membership"
          className="text-sm text-ink-700 hover:text-brand-600 dark:text-ink-200"
        >
          멤버십
        </Link>
        <div className="ml-auto flex items-center gap-3">
          {authed ? (
            <>
              <Link
                href="/me"
                className="text-sm text-ink-700 hover:text-brand-600 dark:text-ink-200"
              >
                {session?.tenantId === 'fan-platform' ? '내 정보' : 'Account'}
              </Link>
              <form
                action={async () => {
                  'use server';
                  await signOut({ redirectTo: '/login' });
                }}
              >
                <button
                  type="submit"
                  className="rounded-md border border-ink-200 px-3 py-1.5 text-sm text-ink-700 hover:bg-ink-50"
                >
                  로그아웃
                </button>
              </form>
            </>
          ) : (
            <Link
              href="/login"
              className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
            >
              로그인
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}
