import type { NextConfig } from 'next';

/**
 * Standalone output is OPT-IN via `NEXT_OUTPUT_STANDALONE=1` (TASK-FAN-FE-014).
 * The demo container build sets it; a plain local `pnpm build` does not.
 *
 * That split is deliberate and is why this is a flag rather than the bare
 * `output: 'standalone'` the sibling web-store uses. v1 was dev-server first
 * and dropped standalone outright (TASK-FAN-FE-001 § Out of Scope #10) because
 * the file-tracing copier needs elevated symlink privileges on Windows — pnpm
 * hoists deps as symlinks and the copier has to reproduce them — and this repo
 * is developed on Windows. The demo container needs standalone (small image,
 * `node server.js` as the production entrypoint) and builds on Linux, where
 * that constraint does not exist. Enabling it unconditionally would buy a
 * container improvement with a local-build regression on the very hosts that
 * cannot absorb it.
 *
 * `next.config` is evaluated per build, so this is read at build time only —
 * nothing about it reaches the running server.
 */
const nextConfig: NextConfig = {
  // 🔴 `@demo/backend-resolver` 은 TS 소스를 그대로 내보낸다(`main: ./src/index.ts`).
  //    이 목록에 없으면 `next build` 가 node_modules 안 TS 를 만나 죽는다 — TASK-MONO-614.
  transpilePackages: ['@demo/backend-resolver'],
  ...(process.env.NEXT_OUTPUT_STANDALONE === '1'
    ? { output: 'standalone' as const }
    : {}),
  images: {
    unoptimized: true,
  },
  eslint: {
    // CI runs `next lint` separately via the `lint` script; skip during build
    // to keep `next build` deterministic when transient lint warnings appear.
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
