import { test, expect } from '@playwright/test';
import { loginAsOperatorAndExpectMismatch, uniqueUser } from './helpers/auth';

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
 * ⚠️ 보류 중 — TASK-MONO-378. 다른 스펙들처럼 `SKIP_GAP_E2E` 로 게이트하지 않는 이유:
 * TASK-MONO-373 이 GAP 컨테이너를 full-stack 레인에 배선해서 형제 3개(golden-flow /
 * cart-management / wishlist)는 이제 실제로 돈다. **이 스펙만 여전히 못 돈다 — 헬퍼가
 * 아니라 스택 때문이다.**
 *
 *   1. e2e IAM 스택엔 실제 account-service 가 없다(nginx `account-mock`). roles 조회는 404.
 *   2. roles 404 → fail-soft → `RoleSeedPolicy`.
 *   3. `RoleSeedPolicy.seed()` 는 사용자가 아니라 **등록된 클라이언트의 platform** 으로
 *      키잉된다 — `case "ecommerce" -> List.of("CUSTOMER")` (BE-369, ADR-MONO-033 S3).
 *
 * ⇒ web-store 클라이언트로 로그인하는 **모든** 자격증명이 CUSTOMER 를 받는다. 이 스펙이
 * 필요로 하는 "CUSTOMER 없는 operator" 토큰은 **이 스택에서 발급 자체가 불가능**하다.
 * 무엇을 어떻게 시드해도 안 된다. 실제 account-service 가 있어야 한다 → MONO-378.
 *
 * (부수적으로 `loginAsOperatorAndExpectMismatch` → `completeGapSignIn` 도 깨져 있다:
 * GAP 이 렌더하지 않는 "가입-또는-로그인" 페이지를 가정하고, GAP 엔 inline signup 이
 * 없는데 `uniqueUser` 로 존재하지 않는 이메일을 넣는다. MONO-378 이 같이 고친다.)
 *
 * `skip` 이 아니라 `fixme` 인 이유: skip 은 "이 환경에선 해당 없음" 이지만 이건
 * "고쳐야 하는데 아직 못 고쳤다" 이고, 그 차이가 리포트에 남아야 한다. 티켓 없는 유예는
 * 썩는다 — 이 스펙이 두 task(MONO-014 → MONO-318)를 건너 아무도 모르게 안 돌던 이유가
 * 정확히 그것이다(MONO-373).
 */
test.describe('consumer-role 교차 앱 차단 (web-store)', () => {
  test.fixme(true, 'TASK-MONO-378 — lean IAM 스택은 CUSTOMER 없는 토큰을 발급할 수 없다 (RoleSeedPolicy 가 클라이언트 platform 으로 키잉됨)');

  test('OPERATOR 가 web-store 에 로그인 시도하면 거부된다', async ({ page }) => {
    const operator = { ...uniqueUser('operator'), accountType: 'OPERATOR' as const };
    await loginAsOperatorAndExpectMismatch(page, operator);
    await expect(page.getByRole('alert')).toContainText('operator');
  });
});
