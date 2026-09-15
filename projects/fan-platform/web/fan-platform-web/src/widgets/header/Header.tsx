import Link from 'next/link';
import { signOut } from '@/shared/auth/auth';
import { buildGapEndSessionUrl } from '@/shared/auth/federated-logout';
import { getFanSession, isAuthenticated } from '@/shared/auth/session';
import { NotificationBell, getRecentNotifications, getUnreadCount } from '@/features/notification';
import { DemoHeartbeat } from '@/widgets/heartbeat/DemoHeartbeat';

/**
 * Top navigation. Server Component — reads session via the server boundary.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴🔴 익명 방문자에게는 **게이트웨이 호출이 하나도 없다**
 * ─────────────────────────────────────────────────────────────────────────
 * 이 헤더는 공개 페이지를 포함한 `(main)` 셸 **전부**에 붙는다. 그래서 여기서 새는 요청
 * 하나가 «익명 방문은 백엔드를 안 부른다» 를 페이지별 노력과 무관하게 통째로 깬다.
 *
 * 그 성질은 아래 `authed` 삼항 하나에 걸려 있고, 그 값의 신뢰도는 `isAuthenticated()` 의
 * 술어에 걸려 있다. 예전 구현은 `Boolean(await auth())` 였고 auth.js 의 **설정 오류 본문**
 * (`{ message: "There was a problem…" }`)을 true 로 읽었다 — 즉 인증이 깨진 배포에서는
 * 익명 방문자마다 알림 조회 두 건이 게이트웨이로 나갔고, 401 로 조용히 실패해서 아무도
 * 몰랐다. 그 술어는 `shared/auth/session-shape.ts` 로 옮겨 미들웨어와 공유한다.
 *
 * 🔵 인증된 렌더는 하나도 안 바뀐다 — 알림 벨도, 조회 병렬화도, 알림 서비스 장애 시
 *    빈 값으로 degrade 하는 것도 그대로다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 휴대폰 너비 배치 (TASK-FAN-FE-023)
 * ─────────────────────────────────────────────────────────────────────────
 * 예전 `<nav className="flex … gap-6">` 은 400px 에서 항목이 줄어들며 **글자 단위로** 끊겼다
 * (`피`/`드`, `로그`/`인` — 한글은 글자 사이 어디서나 끊긴다). 그래서:
 *   · 모든 링크·버튼에 `whitespace-nowrap` — 항목은 쪼개지지 않는다.
 *   · `flex-wrap` + 메뉴 묶음 `order-last w-full` — 640px 미만에서는 첫 줄 = 로고 + 오른쪽 동작,
 *     둘째 줄 = 피드 · 아티스트 · 멤버십. `sm:` 부터는 예전과 같은 한 줄·같은 간격·같은 순서다.
 *   · 줄바꿈 금지만 넣고 줄 배치를 안 정하면 문서가 **가로로 넘친다** — 둘은 짝이다.
 * 🔴 클래스만 바꿨다. 햄버거 메뉴처럼 클라이언트 상태를 들이면 위 § 의 서버 경계가 흔들린다.
 */
const NAV_LINK = 'whitespace-nowrap text-sm text-ink-700 hover:text-brand-600 dark:text-ink-200';

export async function Header() {
  const authed = await isAuthenticated();
  const session = authed ? await getFanSession() : null;
  // Notification bell data — fetched server-side (token never leaves the server),
  // in parallel. Both degrade to empty/0 on a notification-service outage so the
  // header never breaks an authed page.
  // 🔴 `session` 이 null 인 익명 경로에서는 이 두 호출이 **평가되지 않는다**(§ 위).
  const [recent, unread] = session
    ? await Promise.all([
        getRecentNotifications(session.accessToken),
        getUnreadCount(session.accessToken),
      ])
    : [[], 0];

  return (
    <header className="sticky top-0 z-10 border-b border-ink-200 bg-white/80 backdrop-blur dark:bg-ink-900/80 dark:border-ink-800">
      <nav className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <Link
          href="/"
          className="whitespace-nowrap bg-gradient-to-r from-brand-600 to-accent-500 bg-clip-text text-lg font-bold text-transparent"
        >
          fan-platform
        </Link>
        <div
          data-testid="nav-primary"
          className="order-last flex w-full items-center gap-5 sm:order-none sm:w-auto sm:gap-6"
        >
          <Link href="/" className={NAV_LINK}>
            피드
          </Link>
          <Link href="/artists" className={NAV_LINK}>
            아티스트
          </Link>
          <Link href="/membership" className={NAV_LINK}>
            멤버십
          </Link>
        </div>
        <div className="ml-auto flex flex-wrap items-center justify-end gap-x-3 gap-y-2">
          {authed ? (
            <>
              {/* 🔴 heartbeat 은 **인증된 셸에만** 붙는다 — 공개 브라우징이 데모 EC2 를
                  살려 두면 안 되기 때문이고, 이유 전체는
                  `app/api/demo/heartbeat/route.ts` 헤더에 있다. 이것을 `(main)/layout.tsx`
                  같은 공용 자리로 올리면 익명 방문자도 핑을 보내게 된다. */}
              <DemoHeartbeat />
              {/* TASK-FAN-FE-016: the compose entry point lives inside the `authed`
                  branch, so an anonymous visitor is never offered a form they cannot
                  submit (AC-3). This is the whole of the anonymous-case handling —
                  there is no second, unguarded link elsewhere. */}
              <Link
                href="/compose"
                data-testid="nav-compose"
                className="whitespace-nowrap rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
              >
                글쓰기
              </Link>
              <Link href="/me/posts" data-testid="nav-my-posts" className={NAV_LINK}>
                내 글
              </Link>
              <NotificationBell initialItems={recent} initialUnread={unread} />
              <Link href="/me" className={NAV_LINK}>
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
                  className="whitespace-nowrap rounded-md border border-ink-200 px-3 py-1.5 text-sm text-ink-700 hover:bg-ink-50"
                >
                  로그아웃
                </button>
              </form>
            </>
          ) : (
            <Link
              href="/login"
              className="whitespace-nowrap rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
            >
              로그인
            </Link>
          )}
        </div>
      </nav>
    </header>
  );
}
