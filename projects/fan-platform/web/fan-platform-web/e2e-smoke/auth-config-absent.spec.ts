import { test, expect } from '@playwright/test';

/**
 * 결핍 대조군 — **인증 설정이 없는** 서버(:3003)에 보호 경로를 찌른다.
 * `playwright.smoke.config.ts` 의 `chromium-auth-config-absent` 프로젝트에서만
 * 돌고, 「설정 있음」 축(:3002, `auth-guard.spec.ts`)은 그대로 남아 있다.
 *
 * 왜 이 파일이 있나 (TASK-FAN-FE-018 → TASK-FAN-FE-019):
 * `auth-guard.spec.ts` 는 *"설정이 **있을 때** 미들웨어가 리다이렉트한다"* 를
 * 단언한다. 프로덕션이 실패한 지점은 *"설정이 **없다**"* 였다. 두 명제는
 * 겹치지 않아서, 가드는 초록이고 프로덕션은 3일간 조용히 뚫려 있었다.
 * 이 파일이 그 겹치지 않던 칸이다.
 *
 * 기대값의 근거는 `src/middleware.ts` 의 AC-1 결정 —
 * **(A) fail-closed, `/login` 리다이렉트**. 「현행 유지」가 아니다: 아래 칸들은
 * 오늘의 동작이 아니라 **정해진 동작**을 단언하며, 수정 전 코드에서는 빨갛다.
 *
 * 🔵 결핍 서버가 **기동 자체에 실패**하면 이 파일의 칸이 아니라 Playwright 의
 * `webServer` 타임아웃이 먼저 터진다("Timed out waiting … from config.webServer").
 * «기동 실패» 와 «가드가 막았다» 는 그렇게 갈라진다.
 */

/** 미인증 요청이 보호 경로에서 받아야 할 것 — 리다이렉트 상태 코드 전체. */
const REDIRECT_STATUSES = [301, 302, 303, 307, 308];

test.describe('auth config absent — 설정이 없어도 가드는 닫힌다', () => {
  /**
   * AC-2 — 🔴 **주입부터 단언한다.**
   *
   * 이 칸이 없으면 «가드가 안 물었다» 와 «애초에 결핍 서버가 아니었다» 가
   * 구별되지 않는다. `webServer.env` 가 빈 문자열로 덮는 데 실패하거나
   * (`.env.local` 이 다시 채우거나, `@next/env` 의 우선순위가 바뀌거나),
   * 누가 결핍 축에 설정을 되돌려 넣으면 **여기가 먼저 빨개진다.**
   *
   * 500 + 이 본문은 auth.js 가 쓸 수 있는 secret 없이 떴을 때의 지문이고,
   * TASK-FAN-FE-018 이 `fan.hubwang.com` 에서 잰 것과 같은 값이다.
   */
  test('주입 확인: /api/auth/* 가 설정 결핍 지문(500)을 낸다', async ({ request }) => {
    const res = await request.get('/api/auth/providers');
    expect(res.status()).toBe(500);
    expect(await res.text()).toContain('problem with the server configuration');
  });

  /**
   * AC-2 — 🔵 **음성 대조군.**
   *
   * 이게 없으면 「전부 막힘」이라는 자명한 오답 — 미들웨어가 공개 경로까지
   * 꺾거나(리다이렉트 루프), AC-1 (B) 처럼 사이트 전체가 5xx 로 죽는 것 —
   * 이 위의 칸들과 함께 통과해 버린다.
   */
  test('음성 대조군: /login 은 결핍 서버에서도 200 이다', async ({ request }) => {
    const res = await request.get('/login', { maxRedirects: 0 });
    expect(res.status()).toBe(200);
  });

  /**
   * AC-3 — 🔴 상태 코드를 **문자 그대로 박지 않는다.**
   *
   * TASK-FAN-FE-018 의 AC 문구는 `302` 라고 적혀 있었지만 실제 코드는 **307**
   * 이다 — `NextResponse.redirect()` 의 기본값(메서드 보존). `302` 를 그대로
   * 단언했으면 **고쳐진 동작에 빨간불**이 켜졌을 것이다.
   *
   * 그래서 이 칸이 재는 명제는 두 개다: ① 리다이렉트인가, ② `Location` 이
   * `/login` 이고 원래 경로를 `from` 에 보존하는가. 오늘 실측값은 **307**
   * 이지만 판정에 쓰지 않는다 — `NextResponse.redirect(url, 302)` 로 바꾸거나
   * Next 가 기본값을 바꾸면 코드는 달라지고, 그 둘 중 어느 것도 이 가드가
   * 지키려는 성질(«미인증은 /login 으로 꺾인다»)을 깨지 않기 때문이다.
   */
  const protectedPaths = [
    { path: '/artists', label: '보호 경로' },
    { path: '/posts/abc-123', label: '보호 경로(동적)' },
    // 🔴 판별자. 미들웨어는 라우팅보다 **먼저** 도므로, 존재하지 않는 경로도
    // 미인증이면 `/login` 으로 꺾여야 한다. 여기서 404 가 나오면 그것은
    // "그런 페이지가 없다"가 아니라 **"가드를 안 거쳤다"** 는 뜻이다 —
    // TASK-FAN-FE-018 이 프로덕션에서 A/B 를 가른 바로 그 칸.
    { path: '/nonexistent-xyz', label: '존재하지 않는 경로(판별자)' },
  ];

  for (const { path, label } of protectedPaths) {
    test(`${label} ${path} — 설정이 없어도 /login 으로 꺾인다`, async ({ request }) => {
      const res = await request.get(path, { maxRedirects: 0 });

      expect(REDIRECT_STATUSES).toContain(res.status());

      const location = res.headers()['location'];
      expect(location, 'Location 헤더가 있어야 한다').toBeTruthy();

      const target = new URL(location, 'http://localhost');
      expect(target.pathname).toBe('/login');
      expect(target.searchParams.get('from')).toBe(path);
    });
  }
});
