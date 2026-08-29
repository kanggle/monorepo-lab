import { test, expect } from '@playwright/test';

/**
 * Auth guard — 보호 경로 (/artists, /posts/:id) 에 비인증 상태로 접근하면
 * middleware 가 /login 으로 redirect 하고 `from` 쿼리에 원래 URL 을 보존한다.
 */
test.describe('auth guard', () => {
  test('/artists 비인증 접근 → /login?from=/artists', async ({ page }) => {
    await page.goto('/artists');
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 10_000 });
    expect(new URL(page.url()).searchParams.get('from')).toBe('/artists');
  });

  test('/posts/:id 비인증 접근 → /login redirect', async ({ page }) => {
    await page.goto('/posts/abc-123');
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 10_000 });
    expect(new URL(page.url()).searchParams.get('from')).toContain('/posts/abc-123');
  });

  /**
   * TASK-MONO-600 — 🔴 **가드가 자기 감시자를 막으면 안 된다.**
   *
   * `/build-info.json` 은 `scripts/write-build-info.mjs` 가 «지금 서빙 중인 판이 어느
   * 커밋인가» 를 적어 두는 곳이고, 배포 밖에서 도는 감시자
   * (`check-fan-fresh.sh`)가 읽을 수 있는 **유일한 기계 판독 값**이다.
   *
   * matcher 에 남아 있던 동안 미인증 요청은 307 로 `/login` 에 꺾였고, 그래서 그
   * 판정자는 가드가 살아난 2026-08-27 부터 **줄곧 «판정 불가»** 였다. 부르는 잡이
   * 없어서 아무도 몰랐다. 다시 막히면 감시자는 **조용히** 눈이 머는데, 그 침묵은
   * 리다이렉트가 아니라 「측정 못 함」의 얼굴로 오므로 이 칸이 그것을 대신 말한다.
   *
   * 🔵 공개 저장소의 `{ commit, ref, builtAt }` 이라 robots.txt·sitemap.xml 과 같은
   * 등급이다 — 비밀이 아니다.
   */
  test('/build-info.json 은 가드 밖이다 — 감시자가 읽을 수 있어야 한다', async ({
    request,
  }) => {
    const res = await request.get('/build-info.json', { maxRedirects: 0 });
    expect(res.status(), '307 이면 matcher 가 다시 막은 것이다').toBe(200);
    // 🔴 200 이 곧 JSON 은 아니다 — 로그인 HTML 도 200 으로 온다(그 함정이 이 칸의 출처).
    const body = await res.json();
    expect(body).toHaveProperty('commit');
  });
});
