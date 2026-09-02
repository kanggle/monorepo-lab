import type { NextConfig } from 'next';

const objectStorageHostname = process.env.NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME;

const nextConfig: NextConfig = {
  output: 'standalone',
  transpilePackages: [
    // 🔴 TS 소스를 그대로 내보내는 패키지는 전부 여기 있어야 한다
    //    (`main: ./src/index.ts`). `@demo/backend-resolver` 는 워크스페이스 멤버가
    //    아니라 `file:` 의존이지만 같은 이유로 필요하다 — TASK-MONO-614.
    '@demo/backend-resolver',
    '@repo/ui',
    '@repo/types',
    '@repo/api-client',
    '@repo/utils',
  ],
  experimental: {
    // Tree-shake barrel imports so a single named import doesn't pull the whole
    // package into the client bundle. Targets the workspace UI barrel and
    // react-query (largest client dep).
    optimizePackageImports: ['@repo/ui', '@repo/utils', '@tanstack/react-query'],
  },
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: 'images.unsplash.com' },
      { protocol: 'https', hostname: 'placehold.co' },
      { protocol: 'http', hostname: 'localhost' },
      { protocol: 'http', hostname: '127.0.0.1' },
      ...(objectStorageHostname
        ? [{ protocol: 'https' as const, hostname: objectStorageHostname }]
        : []),
    ],
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
