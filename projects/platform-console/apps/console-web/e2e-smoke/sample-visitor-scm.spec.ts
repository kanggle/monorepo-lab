import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 SCM 조달 화면을 샘플 데이터로 본다 — 백엔드 하나도 안 뜬 채로
 * (`ADR-MONO-074` — `TASK-PC-FE-288` AC-7, `ADR-MONO-074` 실행 시리즈의
 * 마지막 도메인 티켓).
 *
 * `playwright.smoke.config.ts` 는 OIDC · registry · token-exchange · console-bff
 * 를 전부 도달 불가 loopback(127.0.0.1:1)으로 고정한다. 그 상태에서 `/scm/procurement`
 * 이 배너 + 발주 목록 화면으로 서면 「실제 화면이 네트워크 없이 선다」가 프로덕션
 * 빌드로 증명된다. 🔴 행 수는 단언하지 않는다(작업지시서 AC-7) — 렌더 1칸만 잰다.
 */
test.describe('샘플 방문자 — SCM 조달 화면 (backend 미기동 · 미인증)', () => {
  test('GET /scm/procurement → 배너 + 발주 목록 화면', async ({ page }) => {
    await page.goto('/scm/procurement');
    await expect(page.getByTestId('sample-visitor-banner')).toContainText(
      '샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터',
    );
    await expect(page.getByRole('heading', { name: 'SCM 조달' })).toBeVisible();
    await expect(page.getByTestId('scm-po-filter-status')).toBeVisible();
    // ready 화면에는 «준비 중» 이 없다.
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });
});
