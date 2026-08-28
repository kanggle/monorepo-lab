import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config — smoke E2E (CI 전용, 백엔드 미기동).
 *
 * fan-platform-web 는 인증을 GAP IdP 에 위임하므로 smoke 단계에서는 OIDC 발신
 * 호스트를 도달 불가능한 closed loopback (127.0.0.1:1) 으로 강제하고,
 * 게이트웨이 베이스 URL 도 동일하게 강제한다. 페이지의 fallback 경로
 * (`.catch(() => null)`, middleware 의 `/login` redirect) 가 결정론적으로
 * 활성화되어 백엔드 / GAP 없이도 페이지 렌더 / 라우트 가드 / 로그인 진입
 * 흐름을 검증할 수 있다.
 *
 * 로컬: pnpm --filter fan-platform-web e2e:smoke
 * CI : `frontend-e2e-smoke` job 에서 동일 스크립트 호출.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * TASK-FAN-FE-019 — 여기에는 축이 **둘** 있고, 둘 다 남아 있어야 한다.
 *
 *   :3002 «인증 설정 있음»  — 기존 축. `auth-guard.spec.ts` 등이 여기서 돈다.
 *   :3003 «인증 설정 없음»  — 신규 축. `auth-config-absent.spec.ts` 만 여기서
 *                            돈다.
 *
 * 왜 축이 둘인가: TASK-FAN-FE-018 이 프로덕션에서 잰 결함은 *"인증 설정이
 * 없을 때 가드가 통과시킨다"* 였는데, 이 config 의 `webServer.env` 가 바로 그
 * 결핍 변수들을 **채워 넣고 있었다.** 그래서 `auth-guard.spec.ts` 가 단언하던
 * 명제는 *"설정이 있을 때 리다이렉트한다"* 였고, 프로덕션이 실패한 지점과
 * 겹치지 않는다. 가드는 초록이고 프로덕션은 조용한 404 였다.
 *
 * 🔴 그래서 **기존 env 를 지워서 결핍 축을 만들면 안 된다** — 지금 초록인
 * 칸들이 같이 죽고, 결핍 축은 되돌리는 순간 사라진다. 두 서버를 나란히 띄우고
 * 프로젝트별 `baseURL` 로 가른다.
 *
 * 🔵 새 config 파일을 만들지 않은 것도 의도다. `frontend-e2e-smoke` 잡은
 * `pnpm e2e:smoke` **하나만** 부르고, `summarise-test-results` 는 이 config 가
 * 쓰는 junit 파일 **하나만** 읽는다. 별도 config 로 갈랐다면 새 칸은 CI 에서
 * 안 돌고 카운트에도 안 잡혀 로컬 전용으로 썩었을 것이다.
 * ─────────────────────────────────────────────────────────────────────────
 */

/** 백엔드·GAP 미기동을 결정론적으로 만드는 축 — 두 서버 공통. */
const UNREACHABLE_BACKEND_ENV = {
  // SSR fetch / auth.js OIDC discovery 가 즉시 ECONNREFUSED 로 실패하도록
  // 강제. 미들웨어와 페이지의 fallback 경로 (`/login` redirect 등) 가
  // 활성화되어 백엔드·GAP 미기동에서도 결정론적으로 검증 가능.
  NEXT_PUBLIC_GATEWAY_URL: 'http://127.0.0.1:1',
  GATEWAY_URL_INTERNAL: 'http://127.0.0.1:1',
} as const;

/**
 * 인증 설정 축. 프로덕션(Vercel)에서 빠져 있던 것이 정확히 이 묶음이고,
 * 그래서 이 묶음이 결핍 대조군의 모집단이다.
 */
const AUTH_CONFIG_ENV = {
  OIDC_ISSUER_URL: 'http://127.0.0.1:1',
  OIDC_CLIENT_ID: 'fan-platform-user-flow-client',
  OIDC_CLIENT_SECRET: 'smoke-test-placeholder',
  NEXTAUTH_URL: 'http://localhost:3002',
  NEXTAUTH_SECRET: 'smoke-test-secret-32-bytes-min-OK',
  AUTH_TRUST_HOST: 'true',
} as const;

/**
 * 같은 키들을 **빈 문자열로** 덮는다. 단순히 «안 넣는다» 로는 부족하다:
 * Playwright 의 `webServer.env` 는 `process.env` 위에 병합되고, `next start`
 * 는 그 위에 `.env.local` 을 얹는다. 이 저장소의 `web/fan-platform-web/.env.local`
 * (untracked, LOCAL DEMO ONLY) 에는 `NEXTAUTH_SECRET` 이 들어 있으므로, 키를
 * 비워 두면 로컬에서 결핍 서버가 **설정을 물려받아** 「설정 있음」을 다시 재게
 * 된다 — CI 는 초록, 로컬도 초록, 잡는 결함은 0.
 *
 * 빈 문자열이 먹는 근거는 `@next/env` 의 `processEnv()` 다: `.env*` 의 키는
 * `typeof initialEnv[key] === 'undefined'` 일 때만 적용된다. `''` 는 정의된
 * 값이므로 `.env.local` 이 덮어쓰지 못하고, `secret: process.env.NEXTAUTH_SECRET`
 * 은 falsy 를 받아 auth.js 가 결핍 상태로 뜬다.
 *
 * 🔴 그래도 이 추론을 믿고 끝내지 않는다 — `auth-config-absent.spec.ts` 의 첫
 * 칸이 `/api/auth/providers` 로 **주입 자체를 단언**한다. 결핍 서버가 설정을
 * 물려받으면 그 칸이 먼저 빨개져서 «안 물었다» 와 «시험한 적이 없다» 가
 * 구별된다.
 */
const AUTH_CONFIG_ABSENT_ENV = Object.fromEntries(
  Object.keys(AUTH_CONFIG_ENV).map((key) => [key, '']),
) as Record<keyof typeof AUTH_CONFIG_ENV, string>;

const CONFIGURED_PORT = 3002;
/** 결핍 서버는 다른 포트로 띄운다 — 두 `next start` 가 포트를 다투지 않게. */
const AUTH_CONFIG_ABSENT_PORT = 3003;

const ABSENT_SPEC = /auth-config-absent\.spec\.ts/;

export default defineConfig({
  testDir: './e2e-smoke',
  timeout: 30_000,
  expect: { timeout: 5_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  // TASK-MONO-545 — `junit` is the only reporter here that writes a count a machine can
  // read: `list` goes to stdout, `html` embeds a zipped blob. CI's summarise step reads
  // this file, so a green run records how many specs actually ran instead of leaving
  // "3 passed" and "0 discovered" indistinguishable from outside the job.
  // `test-results-smoke/` is already gitignored and already inside the upload path.
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report-smoke', open: 'never' }],
    ['junit', { outputFile: 'test-results-smoke/junit.xml' }],
  ],
  outputDir: 'test-results-smoke',
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'ko-KR',
  },
  webServer: [
    {
      // 축 1 — 인증 설정 **있음**. 기존 동작 그대로.
      command: 'pnpm start',
      port: CONFIGURED_PORT,
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
      env: { ...UNREACHABLE_BACKEND_ENV, ...AUTH_CONFIG_ENV },
    },
    {
      // 축 2 — 인증 설정 **없음**. 프로덕션이 실패한 그 조건.
      //
      // 🔴 `reuseExistingServer` 는 CI 밖에서도 `false` 다. 이 저장소에는
      // 러너가 «남의 세션이 띄워 둔 서버»에 붙어 엉뚱한 트리를 재는 함정이
      // 이미 있다. 3003 이 이미 물려 있으면 붙지 말고 시끄럽게 실패해야
      // 한다 — 조용히 남의 서버를 재는 것보다 낫다.
      command: `pnpm exec next start --port ${AUTH_CONFIG_ABSENT_PORT}`,
      port: AUTH_CONFIG_ABSENT_PORT,
      timeout: 60_000,
      reuseExistingServer: false,
      env: { ...UNREACHABLE_BACKEND_ENV, ...AUTH_CONFIG_ABSENT_ENV },
    },
  ],
  projects: [
    {
      name: 'chromium',
      testIgnore: ABSENT_SPEC,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.PLAYWRIGHT_BASE_URL ?? `http://localhost:${CONFIGURED_PORT}`,
      },
    },
    {
      // 🔵 `PLAYWRIGHT_BASE_URL` 을 여기서는 읽지 않는다. 그 변수는 «배포된
      // 표면을 대신 찔러라» 는 뜻인데, 결핍 축은 서버의 **기동 설정**이
      // 조건이라 외부 URL 로 대체할 수 없다. 대체를 허용하면 결핍이 아닌
      // 서버를 결핍이라 부르며 재게 된다.
      name: 'chromium-auth-config-absent',
      testMatch: ABSENT_SPEC,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: `http://localhost:${AUTH_CONFIG_ABSENT_PORT}`,
      },
    },
  ],
});
