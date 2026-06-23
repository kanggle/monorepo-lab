import type { NextConfig } from 'next';

const objectStorageHostname = process.env.NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME;

const nextConfig: NextConfig = {
  output: 'standalone',
  transpilePackages: ['@repo/ui', '@repo/types', '@repo/api-client', '@repo/utils'],
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
