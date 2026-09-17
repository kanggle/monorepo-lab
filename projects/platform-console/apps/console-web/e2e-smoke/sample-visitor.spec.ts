import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 **실제 콘솔 화면**을 샘플 데이터로 본다 — 백엔드 하나도 안 뜬 채로
 * (`ADR-MONO-074` R3ⓐ — `TASK-PC-FE-282` AC-10).
 *
 * `playwright.smoke.config.ts` 는 OIDC · registry · token-exchange · console-bff 를 전부
 * 도달 불가 loopback(127.0.0.1:1)으로 고정한다. 그 상태에서 개요 카드에 **샘플 숫자**가
 * 서면 «샘플 방문자의 요청은 네트워크에 닿지 않는다» 가 실제 프로덕션 빌드로 증명된다 —
 * 하나라도 백엔드로 나갔다면 그 카드는 degrade 상태로 떨어졌을 것이다.
 *
 * 🔴 숫자를 단언하는 이유: 배너만 재면 «셸은 섰는데 데이터는 전부 degrade» 도 초록이다
 *    (degrade 카드는 `—` 를 그린다).
 * 🔴 TASK-PC-FE-285 — 이 칸은 원래 `'128'` · `'342'` 를 **문자 그대로** 단언했다. 그 값이 바로
 *    결함이었다: 개요 카드가 목록(계정 5 · 상품 3)과 다른 숫자를 말했고, 이 핀은 그 불일치를
 *    초록으로 얼려 두었다. 이제 카드 값은 도메인 픽스처에서 파생되고, «카드 = 목록» 은
 *    `tests/unit/sample-overview-cards-match-lists.test.ts` 가 router 로 문다. 여기서는
 *    «숫자가 섰다(degrade 아님)» 만 잰다 — 값을 다시 적으면 다음 픽스처 변경에서 또 얼어붙는다.
 */
test.describe('샘플 방문자 — 실제 개요 (backend 미기동 · 미인증)', () => {
  test('GET /dashboards/overview → 배너 + 개요 카드 + 샘플 지표', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('sample-visitor-banner')).toContainText(
      '샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터',
    );
    await expect(page.getByTestId('operator-overview-cards')).toBeVisible();
    await expect(page.getByTestId('operator-overview-card-iam-total')).toHaveText(/^[1-9][\d,]*$/);
    await expect(page.getByTestId('operator-overview-card-ecommerce-products')).toHaveText(/^[1-9][\d,]*$/);
    // ready 화면에는 «준비 중» 이 없다.
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });

  test('테넌트 스위처는 샘플 테넌트 하나(읽기 전용)', async ({ page }) => {
    await page.goto('/dashboards/overview');
    await expect(page.getByTestId('tenant-single')).toContainText('sample');
  });

  test('🔴 pending 화면 → 셸은 서고 «이 화면의 샘플 데이터는 준비 중입니다»', async ({ page }) => {
    // TASK-PC-FE-287 — `/wms` is now `ready` (wms 실행 6/8), so this cell
    // retargets to `/scm` (still `pending` — owned by TASK-PC-FE-288). The
    // cell's SUBJECT (a pending screen's shell + notice) is unchanged; only
    // the concrete route changed, because the ledger did.
    // 🔴 note for the NEXT ticket (TASK-PC-FE-288): once scm's GETs also turn
    // `ready`, NO screen will be `pending` and this cell will have nowhere to
    // point. Do not solve that here — the fix is not "leave one screen
    // artificially pending forever"; it is to re-home this assertion onto
    // `SampleScreenNotice`'s own unit test (a synthetic `pending` ledger row
    // fed directly to the component), not a real e2e route.
    await page.goto('/scm');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveText(
      '이 화면의 샘플 데이터는 준비 중입니다',
    );
    expect(new URL(page.url()).pathname).toBe('/scm');
  });
});
