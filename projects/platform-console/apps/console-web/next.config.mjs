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
  // same-origin Next route handlers. connect-src stays 'self'.
  "connect-src 'self'",
  // TASK-MONO-585 / ADR-MONO-067 단계 3 — `form-action` 에서 백엔드 오리진 둘을 뺐다.
  //
  // 🔴 이 두 값(`http://iam.local` `http://localhost:3000`)은 AC-0 재측정에서 **어느
  //    계수에도 안 잡힌 백엔드 오리진**이었다: `fetch(` 가 아니라 CSP 헤더라 «절대 fetch
  //    N건» 에 안 걸리고, zod 가 아니라 «`.default()` N개» 에도 안 걸린다. 그리고 이관
  //    뒤에는 IdP 가 `https://auth.hubwang.com`(ADR-MONO-069 C2)이 되므로 이 목록은
  //    **옛 오리진만** 들고 있게 되고, 증상은 「로그인 폼 제출만 조용히 차단」이다.
  //
  // 🔵 그래서 갱신이 아니라 **제거**다 — 세어 보니 지킬 대상이 없었다. `form-action` 은
  //    **폼 제출**만 지배하는데, 이 앱에서 IdP 로 가는 유일한 경로는 로그인 페이지의
  //    `<a href={loginHref}>`(→ `/api/auth/login` → 302 → `/oauth2/authorize`)이고
  //    그것은 **최상위 내비게이션**이지 폼 제출이 아니다. 앱 전체 `<form` **48개**는
  //    전부 `onSubmit` 핸들러이고 `action=` 속성(문자열·Server Action 모두)을 가진 것이
  //    **0건**이다(2026-09-05 전수 grep).
  //    ⇒ 크로스오리진 폼이 없으므로 `'self'` 로 좁히는 것은 **완화가 아니라 강화**이고,
  //    동시에 이 파일이 배포마다 달라져야 할 이유가 사라진다(런타임 CSP·미들웨어 불필요).
  //
  // 🔴 크로스오리진으로 제출하는 `<form action=...>` 을 새로 만든다면 이 줄을 다시
  //    열어야 한다. 그때 넣을 값은 «그 시점의 IdP 주소» 이지 `.local` 이 아니다.
  "form-action 'self'",
].join('; ');

const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  ...(isStandalone ? { output: 'standalone' } : {}),
  // 🔴 `@demo/backend-resolver` 는 TS 소스를 그대로 내보낸다(`main: ./src/index.ts`,
  //    ADR-MONO-068 § D6 = B2 / TASK-MONO-614). 트랜스파일 대상으로 선언하지 않으면
  //    `next build` 가 그 패키지의 TS 를 파싱하지 못한다. 형제 둘(web-store·
  //    fan-platform-web)과 같은 선언이다.
  transpilePackages: ['@demo/backend-resolver'],
  // TASK-PC-FE-135 — feature-barrel RSC client-reference First Load sweep.
  // The erp/ecommerce sections are multi-route: a single feature barrel
  // re-exports several 'use client' route-entry screens (+ leaves), so each
  // route's Server Component page — importing that barrel — pulled EVERY
  // sibling screen into its client graph (RSC client-reference collection,
  // not tree-shaking). Result: all 4 erp routes / all 11 ecommerce routes
  // shipped a byte-identical First Load (the whole feature). `optimizePackage
  // Imports` rewrites the barrel import into direct per-symbol imports at
  // build time, so each route's client graph includes only the symbols it
  // actually references. Behavior-preserving (import resolution only).
  experimental: {
    optimizePackageImports: ['@/features/erp-ops', '@/features/ecommerce-ops'],
  },
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
