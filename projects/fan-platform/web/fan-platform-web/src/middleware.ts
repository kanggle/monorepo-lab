import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth } from '@/shared/auth/auth';
import { isPublicPath } from '@/shared/auth/public-paths';
import { hasAuthenticatedUser } from '@/shared/auth/session-shape';

/**
 * Route guard. Protects every page except the public browsing surface
 * (`shared/auth/public-paths.ts` — the allowlist, and the only place it lives).
 *
 * The `authorized` callback in auth.ts calls **the same `isPublicPath`** for
 * next-auth internal flows; this middleware is the explicit redirect path so
 * unauth'd visits to a protected route land on `/login?from=<original>`.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 공개 브라우징이 생긴 뒤에도 **fail-closed 는 그대로다**
 * ─────────────────────────────────────────────────────────────────────────
 * 아래 AC-1 서술은 *"세션을 판정할 수 없으면 닫는다"* 를 말하고, 그 성질은 이 변경으로
 * 하나도 약해지지 않았다. 바뀐 것은 **무엇을 판정 대상으로 삼는가** 뿐이다:
 *
 *   · 공개 경로  — 애초에 세션을 안 묻는다(익명이 정상 상태다). 판정이 없으므로
 *                  판정 실패도 없다.
 *   · 그 외 전부 — 예전 그대로. `auth()` 가 throw 하든, 오류 본문을 주든, F3 로 강등된
 *                  세션을 주든 **전부 `/login` 으로 꺾인다**.
 *
 * 🔴 그래서 판별자(`/nonexistent-xyz` → `/login`)가 **살아 있어야 한다.** 그 칸이
 *    404 로 바뀌면 그것은 "그런 페이지가 없다" 가 아니라 «미들웨어가 안 돈다» 는 뜻이고,
 *    TASK-FAN-FE-018 이 프로덕션에서 3일간 놓친 결함이 정확히 그 모양이었다.
 *    `e2e-smoke/auth-config-absent.spec.ts` 와 `__tests__/middleware-public-paths.test.ts`
 *    가 둘 다 그 칸을 들고 있다.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * ─────────────────────────────────────────────────────────────────────────
 * TASK-FAN-FE-019 / AC-1 — what this guard does when auth is NOT configured.
 * Chosen: **(A) fail-closed, redirect to `/login`.** The measurement that
 * forced the choice is TASK-FAN-FE-018's verdict; reproduced 2026-08-28 UTC
 * against a local `next start` (prod build) with the auth env absent:
 *
 *   /artists  /me  /posts/:id  → 200  (page rendered, no redirect)
 *   /nonexistent-xyz           → 404  ← the discriminator: the request reached
 *                                       routing, so the guard did not close
 *   /login                     → 200  (public — correct)
 *   /api/auth/providers        → 500  {"message":"There was a problem with
 *                                       the server configuration…"}
 *
 * Mechanism: with no usable secret, auth.js answers its own session request
 * with a 500 **and a JSON body**, and `auth()` returns that body verbatim
 * (`next-auth/lib/index.js` → `getSession(...).then((r) => r.json())` — there
 * is no `response.ok` check). So the previous `if (!session)` was asking "is
 * this truthy?" of `{ message: "There was a problem…" }`, which it is. The
 * guard read a configuration error as "there is a session" and opened.
 *
 * A route guard must never fail in that direction, and the symptom was not an
 * error but *nothing happening*, so nobody saw it for three days.
 *
 * Why (A) and not (B) 5xx: a 5xx takes down the public paths too — `/login`
 * and `/api/auth/*`, i.e. the whole site — and removes the one page that can
 * tell an operator what broke. (A)'s known cost is that a misconfigured
 * deployment can look normal; that is paid off two ways: the misconfiguration
 * keeps an independent loud signal (`/api/auth/*` → 500, asserted in
 * `e2e-smoke/auth-config-absent.spec.ts`), and the branch below logs every
 * time it fires. "Closed" and "misconfigured" stay distinguishable.
 *
 * Why the predicate is the *shape of the returned value* rather than an env
 * check (`process.env.NEXTAUTH_SECRET`): env names are a declaration, and
 * this defect was exactly a declaration that did not match the runtime. What
 * gets trusted is the value `auth()` returns, so that is what gets asked.
 * ─────────────────────────────────────────────────────────────────────────
 */

/**
 * 🔵 `hasAuthenticatedUser` 는 `shared/auth/session-shape.ts` 로 옮겼다 — 표(어떤 반환값이
 * 무엇을 뜻하는가)도 거기 있다. 옮긴 이유는 **소비자가 둘이 됐기 때문**이다:
 * `session.ts` 의 `isAuthenticated()` 가 같은 질문에 `Boolean(session)` 이라는 **다른**
 * 답을 내고 있었고, 그 오답 위에서 `Header` 가 익명 방문자에게 알림 조회를 보냈다.
 */

/**
 * Resolve "is this request authenticated?" so that every failure mode —
 * throw, error payload, degraded session — lands on `false` (closed).
 */
async function isAuthenticated(pathname: string): Promise<boolean> {
  let session: unknown;
  try {
    session = await auth();
  } catch (error) {
    // auth.js can also throw outright (e.g. an unparseable config). Closed.
    console.error(
      `[middleware] auth() threw for ${pathname}; failing closed to /login`,
      error,
    );
    return false;
  }
  if (session == null) return false; // anonymous — the ordinary case, not an error
  if (hasAuthenticatedUser(session)) return true;
  // Non-null but carrying no user: an auth.js error payload, or a session the
  // `session` callback degraded to anonymous. Both are closed, and the first
  // is an outage — say so, because option (A) is otherwise silent.
  console.error(
    `[middleware] auth() returned no user for ${pathname}; failing closed to /login. ` +
      'If this is a configuration error, /api/auth/providers answers 500. Value: ' +
      JSON.stringify(session),
  );
  return false;
}

export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  // 공개 경로 — 세션을 **묻지 않는다**. 목록은 `shared/auth/public-paths.ts` 한 곳뿐이고,
  // `auth.ts` 의 `authorized` 콜백도 같은 함수를 부른다(사본이 없으므로 갈라질 자리가 없다).
  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }
  if (!(await isAuthenticated(pathname))) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = '/login';
    loginUrl.search = `?from=${encodeURIComponent(pathname + search)}`;
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match every path except:
     *  - /api/auth (next-auth handler)
     *  - /_next/static, /_next/image
     *  - /favicon.ico, /robots.txt, /sitemap.xml
     *  - /build-info.json (see below)
     *  - All public asset extensions handled by the negative lookahead
     *
     * TASK-MONO-600 — `build-info.json` joins the public-metadata list, because
     * leaving it out made a verdict impossible rather than merely inconvenient.
     * `scripts/write-build-info.mjs` writes it so `check-fan-fresh.sh` can ask
     * "is the serving build actually main?". While it sat inside the matcher an
     * unauthenticated fetch got 307 → /login and the script read the login HTML,
     * so from 2026-08-27 — the day the guard started working — that freshness
     * verdict was permanently "판정 불가". Nobody saw it because no job called
     * the script. Measured 2026-08-29 UTC against fan.hubwang.com.
     *
     * 🔵 It carries no secret: `{ commit, ref, builtAt }` of a PUBLIC repo — the
     * same class as robots.txt/sitemap.xml. What it does carry is the only
     * machine-readable answer to "which commit is live", which is exactly what a
     * watcher outside the deployment has to be able to read.
     * 🔴 Re-gating it silently blinds that watcher, so `auth-guard.spec.ts`
     * asserts this exclusion — do not drop the cell.
     */
    '/((?!api/auth|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml|build-info.json).*)',
  ],
};
