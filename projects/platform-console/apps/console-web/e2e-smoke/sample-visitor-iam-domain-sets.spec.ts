import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 «업무 권한 세트» · «테넌트 진입 정책» 을 샘플 데이터로 본다 — 백엔드
 * 하나도 안 뜬 채로 (`ADR-MONO-074` — `TASK-PC-FE-382`).
 *
 * 이 두 표면은 샘플 원장에서 `pending` 이었다 — 방문자는 «이 화면의 샘플 데이터는
 * 준비 중입니다» 를 봤다. 픽스처가 생긴 뒤에는 시스템 프리셋 · 샘플 테넌트 세트와
 * 진입 정책 패널이 서야 하고 «준비 중» 문구는 없어야 한다.
 */
test.describe('샘플 방문자 — 업무 권한 세트 · 테넌트 진입 정책 (backend 미기동 · 미인증)', () => {
  test('GET /domain-permission-sets → 프리셋 + 샘플 테넌트 세트, «준비 중» 없음', async ({ page }) => {
    await page.goto('/domain-permission-sets');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
    await expect(page.getByTestId('domain-permission-sets-degraded')).toHaveCount(0);
    // 시스템 프리셋(읽기 전용) — 계약의 고정 setId.
    await expect(page.getByTestId('domain-set-presets-list')).toBeVisible();
    await expect(
      page.getByTestId('domain-set-preset-00000083-0000-7000-8000-000000000001'),
    ).toBeVisible();
    // 샘플 테넌트 세트 — «(샘플)» 표기.
    await expect(page.getByTestId('domain-set-tenant-list')).toContainText('(샘플)');
    await expect(page.getByText('이 화면의 샘플 데이터는 준비 중입니다')).toHaveCount(0);
  });

  test('GET /tenants/sample → 진입 정책 패널 (degraded 메모 없음)', async ({ page }) => {
    await page.goto('/tenants/sample');
    await expect(page.getByTestId('entry-policy-panel')).toBeVisible();
    await expect(page.getByTestId('entry-policy-degraded')).toHaveCount(0);
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });

  test('GET /security-settings → 진입 정책 패널, «준비 중» 없음', async ({ page }) => {
    await page.goto('/security-settings');
    await expect(page.getByTestId('entry-policy-panel')).toBeVisible();
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
  });
});
