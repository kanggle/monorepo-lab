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
  // OIDC redirect + token exchange both go server-side; the browser only ever
  // navigates to GAP /oauth2/authorize (top-level, not connect-src) and calls
  // same-origin Next route handlers. connect-src stays 'self'. form-action
  // allows the GAP authorize redirect target.
  "connect-src 'self'",
  "form-action 'self' http://gap.local http://localhost:3000",
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
