/** @type {import('next').NextConfig} */
const isDev = process.env.NODE_ENV !== 'production';
// Enable standalone output only inside Docker (avoids Windows symlink EPERM on pnpm).
// Set NEXT_STANDALONE=1 in the Dockerfile builder stage.
const isStandalone = process.env.NEXT_STANDALONE === '1';

const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ''}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  // TODO(TASK-PC-FE-001): add connect-src for GAP OIDC issuer and domain gateway hosts
].join('; ');

const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  ...(isStandalone ? { output: 'standalone' } : {}),
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'Content-Security-Policy', value: csp },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
        ],
      },
    ];
  },
};

export default nextConfig;
