import type { NextConfig } from 'next';

/**
 * `auth.hubwang.com` 포워더 (ADR-MONO-069 C2 / TASK-MONO-610).
 *
 * 🔴 `@demo/backend-resolver` 는 TS 소스를 그대로 내보낸다(`main: ./src/index.ts`).
 *    이 목록에 없으면 `next build` 가 node_modules 안 TS 를 만나 죽는다 — TASK-MONO-614.
 *
 * 🔵 이 앱에는 화면이 없다. 라우트 핸들러 하나뿐이므로 `images` · `output` 설정이 없다.
 *    `output: 'standalone'` 도 쓰지 않는다 — 컨테이너로 굽지 않고 Vercel 에서만 돈다.
 */
const nextConfig: NextConfig = {
  transpilePackages: ['@demo/backend-resolver'],
  eslint: {
    // 이 앱에는 eslint 설정이 없다(화면이 없어 규칙 대부분이 무의미하다).
    // CI 는 `pnpm typecheck` + `pnpm build` 로 잡는다 — README § CI 를 보라.
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
