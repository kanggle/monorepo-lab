import { test, expect } from '@playwright/test';

/**
 * 익명 방문자가 운영자 «도메인 접근» 대화상자와 그룹 상세 «세트» 칸을 샘플 데이터로 본다 —
 * 백엔드 하나도 안 뜬 채로 (`ADR-MONO-074` — `TASK-PC-FE-383`).
 *
 * 두 GET(`/operators/{id}/domain-access` · `/groups/{id}/domain-permission-sets`)은 이미 `ready` 인
 * `operators` · `groups` 표면 안의 하위 경로라 원장이 빈칸을 못 봤다 — 픽스처가 없으면 503 →
 * 대화상자 · 칸이 «불러오지 못했습니다» 로 degrade 한다.
 */
test.describe('샘플 방문자 — 운영자 도메인 접근 · 그룹 세트 (backend 미기동 · 미인증)', () => {
  test('/operators → 행의 «도메인 접근» 대화상자에 배정 행 + 출처 표기, «준비 중» 없음', async ({ page }) => {
    await page.goto('/operators');
    await expect(page.getByTestId('sample-visitor-banner')).toBeVisible();
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);

    await page.getByTestId('action-domain-access-op-sample-0002').click();
    await expect(page.getByTestId('operator-domain-access-dialog')).toBeVisible();
    // 직접 + 그룹 두 출처로 같은 세트(재고팀) — 화면이 둘을 따로 그린다.
    const set = page.getByTestId('operator-domain-access-set-0192f3a0-0000-7000-8000-00000000d001');
    await expect(set).toBeVisible();
    await expect(page.getByTestId('operator-domain-access-sets-list')).toBeVisible();
    await expect(page.getByTestId('operator-domain-access-sets-empty')).toHaveCount(0);
    await expect(page.getByTestId('operator-domain-access-error')).toHaveCount(0);
    await expect(set).toContainText('직접');
    await expect(set).toContainText('그룹: 영업1팀');
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
    await expect(page.getByText('준비 중')).toHaveCount(0);
  });

  test('/operator-groups → 그룹 상세 «세트» 칸에 배정 행, «준비 중» 없음', async ({ page }) => {
    await page.goto('/operator-groups');
    await page.getByTestId('groups-row-select-group-sample-0001').click();
    await expect(page.getByTestId('group-detail')).toBeVisible();
    await expect(page.getByTestId('group-domain-sets-list')).toBeVisible();
    await expect(
      page.getByTestId('group-domain-set-row-0192f3a0-0000-7000-8000-00000000d001'),
    ).toBeVisible();
    await expect(page.getByTestId('group-domain-sets-error')).toHaveCount(0);
    await expect(page.getByTestId('group-domain-sets-empty')).toHaveCount(0);
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(0);
    await expect(page.getByText('준비 중')).toHaveCount(0);
  });

  test('대조군 — 아직 pending 인 /fan 은 같은 술어가 «준비 중» 을 잡는다', async ({ page }) => {
    await page.goto('/fan');
    await expect(page.getByTestId('sample-screen-not-ready')).toHaveCount(1);
  });
});
