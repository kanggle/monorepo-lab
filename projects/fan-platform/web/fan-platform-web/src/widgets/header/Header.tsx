import Link from 'next/link';
import { signOut } from '@/shared/auth/auth';
import { buildGapEndSessionUrl } from '@/shared/auth/federated-logout';
import { getFanSession, isAuthenticated } from '@/shared/auth/session';
import { NotificationBell, getRecentNotifications, getUnreadCount } from '@/features/notification';

/** Top navigation. Server Component — reads session via the server boundary. */
export async function Header() {
  const authed = await isAuthenticated();
  const session = authed ? await getFanSession() : null;
  // Notification bell data — fetched server-side (token never leaves the server),
  // in parallel. Both degrade to empty/0 on a notification-service outage so the
  // header never breaks an authed page.
  const [recent, unread] = session
    ? await Promise.all([
        getRecentNotifications(session.accessToken),
        getUnreadCount(session.accessToken),
      ])
    : [[], 0];

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
              <NotificationBell initialItems={recent} initialUnread={unread} />
              <Link
                href="/me"
                className="text-sm text-ink-700 hover:text-brand-600 dark:text-ink-200"
              >
                {session?.tenantId === 'fan-platform' ? '내 정보' : 'Account'}
              </Link>
              <form
                action={async () => {
                  'use server';
                  // RP-initiated logout: clear the local NextAuth session AND
                  // redirect to GAP end_session so the IdP terminates its own
                  // session (no silent re-auth on next login). Falls back to a
                  // local-only logout when there is no id_token_hint.
                  const endSession = await buildGapEndSessionUrl();
                  await signOut({ redirectTo: endSession ?? '/login' });
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
