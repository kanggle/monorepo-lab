import { expect, type Page } from '@playwright/test';

export interface TestUser {
  name: string;
  email: string;
  password: string;
}

export function uniqueUser(prefix = 'e2e'): TestUser {
  const ts = Date.now();
  const rand = Math.floor(Math.random() * 10_000).toString().padStart(4, '0');
  return {
    name: 'E2E 테스터',
    email: `${prefix}-${ts}-${rand}@example.com`,
    password: 'Passw0rd!E2e',
  };
}

export async function signup(page: Page, user: TestUser): Promise<void> {
  await page.goto('/signup');
  await page.getByLabel('이름').fill(user.name);
  await page.getByLabel('이메일').fill(user.email);
  await page.getByLabel('비밀번호').fill(user.password);

  const submit = page.getByRole('button', { name: '회원가입' });
  await expect(submit).toBeEnabled();
  await submit.click();

  await page.waitForURL('**/login', { timeout: 15_000 });
}

export async function login(page: Page, user: Pick<TestUser, 'email' | 'password'>): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(user.email);
  await page.getByLabel('비밀번호').fill(user.password);

  const submit = page.getByRole('button', { name: '로그인', exact: true });
  await expect(submit).toBeEnabled();
  await submit.click();

  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15_000 });
}

/**
 * 회원가입 + 로그인을 하나의 호출로 처리. 대부분의 E2E 시나리오에 필요한 선행 조건을 단축한다.
 *
 * signup → login 사이 300ms pacing을 둔다. Gateway의 auth route는 brute-force 방어용
 * rate limit을 적용하는데, 9개 테스트가 연속 실행되며 signup/login이 burst window에
 * 몰리면 429가 간헐적으로 발생한다. 300ms × 9 테스트 = 2.7초로 총 런타임에 영향은
 * 최소화하면서 rate limit replenish 시간을 확보한다.
 */
export async function signupAndLogin(page: Page, user: TestUser = uniqueUser()): Promise<TestUser> {
  await signup(page, user);
  await page.waitForTimeout(300);
  await login(page, user);
  return user;
}
