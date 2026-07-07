import { test, expect } from '@playwright/test';
import { loginAsOperatorAndExpectMismatch, shouldSkipGap, uniqueUser } from './helpers/auth';

/**
 * Cross-app consumer-role guard (roles-based per ADR-MONO-035 4b-1).
 *
 * GAP V0012 시드는 web-store 가 `ecommerce.consumer` scope 만 요청하도록 등록한다.
 * operator 는 web-store 가 아니라 platform-console(hub) 을 사용한다. operator 의
 * web-store 토큰은 `CUSTOMER` 롤을 담지 않으므로(운영자는 assume-tenant 에서 `ECOMMERCE_OPERATOR`
 * 도메인 롤을 파생받는다 — ADR-035 4a) web-store 로 진입하면:
 *   - GAP `/oauth2/authorize` 에서 callback 까지는 성공
 *   - NextAuth `signIn()`/`session()` 콜백이 `roles ∌ CUSTOMER` 인 경우 user 를 anonymous
 *     로 다운그레이드하고 `/login?error=account_type_mismatch` 로 redirect
 *     (에러코드 문자열은 UI 호환을 위해 유지 — 레거시 `account_type` claim 은
 *     ADR-MONO-032 D5 step 4 에서 제거됨)
 *
 * 이 테스트는 GAP 컨테이너 + operator/consumer 시드 모두 필요하므로 (TASK-MONO-014
 * docker-compose 갱신) 미가용 환경에서 자동 skip.
 */
test.describe('consumer-role 교차 앱 차단 (web-store)', () => {
  test.skip(shouldSkipGap, 'SKIP_GAP_E2E=1 — GAP 컨테이너 미가용');

  test('OPERATOR 가 web-store 에 로그인 시도하면 거부된다', async ({ page }) => {
    const operator = { ...uniqueUser('operator'), accountType: 'OPERATOR' as const };
    await loginAsOperatorAndExpectMismatch(page, operator);
    await expect(page.getByRole('alert')).toContainText('operator');
  });
});
