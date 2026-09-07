import { test, expect } from '@playwright/test';

/**
 * Auth guard — **양방향**으로 잰다.
 *
 * =============================================================================
 * 🔴🔴 이 파일의 두 칸이 뒤집혔다 (TASK-MONO-635 / ADR-MONO-070)
 * =============================================================================
 * 예전에는 `/artists` 와 `/posts/:id` 가 **보호 경로**였고 이 파일이 그 리다이렉트를
 * 단언했다. `ADR-MONO-070` 이 그 둘을 **공개**로 만들었다 — 공개 열람이 이 저장소의
 * 새 요구이고, 그 화면들은 이제 Vercel 저장본만 읽는다(게이트웨이를 안 부른다).
 *
 * 🔴 **단언을 지우지 않고 뒤집었다.** 「공개가 됐으니 이 칸은 이제 의미 없다」로 삭제하면
 *    가드가 그 경로를 다시 막아도 아무도 모른다 — 그리고 그 회귀의 사용자 표면은
 *    «둘러보기가 로그인을 요구한다», 즉 이 ADR 전체가 무효가 된 상태다.
 *
 * 🔴🔴 그리고 **보호 축을 잃지 않았다.** 아래 § 방향 ② 가 «여전히 막히는가» 를 잰다.
 *    공개 경로를 늘리는 변경에서 가장 흔한 사고는 *"열면서 옆칸도 같이 열린 것을 모르는
 *    것"* 이고, 그 사고는 **닫힘을 재는 칸이 없으면 조용하다.**
 *    특히 `/membership`(공개) 과 `/membership/history`(개인 결제 이력)는 **한 접두사를
 *    공유**한다 — 이 파일에서 가장 위험한 쌍이고, 두 칸이 나란히 있는 이유다.
 */

test.describe('방향 ① — 공개 경로는 익명으로 열린다 (ADR-MONO-070)', () => {
  test('/artists 익명 접근 → 리다이렉트 없음', async ({ page }) => {
    const res = await page.goto('/artists');
    expect(new URL(page.url()).pathname, '리다이렉트가 일어났다면 공개가 아니다').toBe('/artists');
    expect(res?.status()).toBeLessThan(400);
  });

  test('/posts/:id 익명 접근 → /login 으로 꺾이지 않는다', async ({ page }) => {
    // 🔵 존재하지 않는 id 를 쓴다 — 재는 것은 «내용» 이 아니라 «가드가 꺾는가» 다.
    //    저장본에 없는 글이면 404/빈 상태가 정상이고, 그것은 로그인 리다이렉트와 **다른 사실**이다.
    await page.goto('/posts/abc-123');
    expect(new URL(page.url()).pathname).not.toBe('/login');
  });

  test('/ 익명 접근 → 공개 피드가 뜬다', async ({ page }) => {
    await page.goto('/');
    expect(new URL(page.url()).pathname).toBe('/');
  });

  test('/membership 익명 접근 → 공개 요금제 소개가 열린다', async ({ page }) => {
    await page.goto('/membership');
    expect(new URL(page.url()).pathname).toBe('/membership');
  });
});

test.describe('방향 ② — 보호 경로는 **여전히** 막힌다', () => {
  // 🔴 이 목록이 비면 위의 «열림» 칸들은 «전부 열렸다» 와 구별되지 않는다.
  for (const path of ['/me', '/compose', '/notifications', '/membership/history']) {
    test(`${path} 비인증 접근 → /login?from=${path}`, async ({ page }) => {
      await page.goto(path);
      await page.waitForURL((url) => url.pathname === '/login', { timeout: 10_000 });
      expect(new URL(page.url()).searchParams.get('from')).toContain(path);
    });
  }

  /**
   * 🔴🔴 **판별자.** 미들웨어가 아예 안 도는 상태와 «공개가 됐다» 를 가른다.
   *
   * 존재하지 않는 경로는 허용 목록에 없으므로 **여전히 `/login` 으로 꺾여야** 한다.
   * 이 칸이 404 가 되면 그것은 "그런 페이지가 없다" 가 아니라 **«미들웨어가 안 돈다»**
   * 는 뜻이고, `TASK-FAN-FE-018` 이 프로덕션에서 3일간 놓친 결함이 정확히 그 모양이었다.
   */
  test('존재하지 않는 경로도 꺾인다 — 미들웨어가 살아 있다는 증거', async ({ page }) => {
    await page.goto('/nonexistent-xyz');
    await page.waitForURL((url) => url.pathname === '/login', { timeout: 10_000 });
  });
});

/**
 * TASK-MONO-600 — 🔴 **가드가 자기 감시자를 막으면 안 된다.**
 *
 * `/build-info.json` 은 «지금 서빙 중인 판이 어느 커밋인가» 를 적어 두는 곳이고, 배포
 * 밖에서 도는 감시자(`check-fan-fresh.sh`)가 읽을 수 있는 **유일한 기계 판독 값**이다.
 * matcher 에 남아 있던 동안 미인증 요청은 307 로 `/login` 에 꺾였고, 그래서 그 판정자는
 * 가드가 살아난 2026-08-27 부터 **줄곧 «판정 불가»** 였다. 부르는 잡이 없어서 아무도
 * 몰랐다. 다시 막히면 감시자는 **조용히** 눈이 머는데, 그 침묵은 리다이렉트가 아니라
 * 「측정 못 함」의 얼굴로 오므로 이 칸이 그것을 대신 말한다.
 */
test('/build-info.json 은 가드 밖이다 — 감시자가 읽을 수 있어야 한다', async ({ request }) => {
  const res = await request.get('/build-info.json', { maxRedirects: 0 });
  expect(res.status(), '307 이면 matcher 가 다시 막은 것이다').toBe(200);
  // 🔴 200 이 곧 JSON 은 아니다 — 로그인 HTML 도 200 으로 온다(그 함정이 이 칸의 출처).
  const body = await res.json();
  expect(body).toHaveProperty('commit');
});
