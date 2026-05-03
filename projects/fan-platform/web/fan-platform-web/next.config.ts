import type { NextConfig } from 'next';

/**
 * v1 frontend is dev-server first (`pnpm fan-platform:web` runs `next dev`).
 * Production-style standalone output is deferred to v2 — see TASK-FAN-FE-001
 * § Out of Scope #10. Dropping `output: 'standalone'` also avoids a Windows
 * symlink permission error when copying traced files inside a worktree
 * (pnpm hoists deps via symlinks; the standalone copier requires elevated
 * symlink privileges on Windows).
 */
const nextConfig: NextConfig = {
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
