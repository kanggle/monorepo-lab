import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 IAM 화면을 샘플 데이터로 본다 — 백엔드 하나도 안 뜬 채로
 * (`ADR-MONO-074` — `TASK-PC-FE-283` AC-6).
 *
 * `playwright.smoke.config.ts` 는 OIDC · registry · token-exchange · console-bff
 * 를 전부 도달 불가 loopback(127.0.0.1:1)으로 고정한다. 그 상태에서 `/accounts`
 * 가 배너 + 표 + «(샘플)» 문자열로 서면 「실제 화면이 네트워크 없이 선다」가
 * 프로덕션 빌드로 증명된다.
 */
test.describe('샘플 방문자 — IAM 계정 운영 화면 (backend 미기동 · 미인증)', () => {
  test('GET /accounts → 배너 + 표 + «(샘플)» 문자열', async ({ page }) => {
    await page.goto('/accounts');
    await expect(page.getByTestId('sample-visitor-banner')).toContainText(
      '샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터',
    );
    await expect(page.getByTestId('accounts-table')).toBeVisible();
    // AC-7 — every seeded account is `*.example`, unmistakably synthetic.
    await expect(page.getByTestId('accounts-table')).toContainText('@example.com');
    // AC-6's literal requirement: the «(샘플)» string on the SCREEN's own data
    // (not just the shell banner) — `email` is the accounts table's only
    // human-readable field (TASK-PC-FE-283 label-rule.ts classification).
    await expect(page.getByTestId('accounts-table')).toContainText('(샘플)');
    // ready 화면에는 «준비 중» 이 없다.
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });
});
