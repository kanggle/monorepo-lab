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

// TASK-MONO-686 (ADR-MONO-074 실행 8/8) — 은퇴한 콘솔 둘러보기(`/demo`)의 도메인 키 →
// 실제 콘솔 경로 매핑. **손으로 지어내지 않았다** — 이 커밋이 함께 지운
// `infra/demo/public-data/fixtures/console-sample.mjs` 의 `CONSOLE_SAMPLE_DOMAINS`
// (`key` · `liveHref` 필드, 2026-09 작성)에서 그대로 옮겼다. 그 파일은 삭제됐으므로 이
// 목록이 지금 그 사실의 유일한 정본이다 — `tests/unit/demo-tour-redirects.test.ts` 가
// (a) 이 정본과 (b) 실제 `redirects()` 출력이 갈라지지 않는지를 짠다.
export const DEMO_TOUR_DOMAIN_REDIRECTS = Object.freeze([
  { domain: 'overview', destination: '/dashboards/overview' },
  { domain: 'ecommerce', destination: '/ecommerce/orders' },
  { domain: 'wms', destination: '/wms/inventory' },
  { domain: 'scm', destination: '/scm/procurement' },
  { domain: 'erp', destination: '/erp/approval' },
  { domain: 'finance', destination: '/finance/accounts' },
  { domain: 'iam', destination: '/iam' },
]);

const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  ...(isStandalone ? { output: 'standalone' } : {}),
  // 🔴 `@demo/backend-resolver` 는 TS 소스를 그대로 내보낸다(`main: ./src/index.ts`,
  //    ADR-MONO-068 § D6 = B2 / TASK-MONO-614). 트랜스파일 대상으로 선언하지 않으면
  //    `next build` 가 그 패키지의 TS 를 파싱하지 못한다. 형제 둘(web-store·
  //    fan-platform-web)과 같은 선언이다.
  // 🔴 `@demo/public-data` 는 여기 없다 — `TASK-MONO-686` 이 이 앱의 유일한 소비 지점이던
  //    공개 둘러보기(`src/app/(demo)/**` → `src/features/demo-tour/**`)를 통째로 지웠다.
  //    이 앱에는 그 패키지를 임포트하는 코드가 더 이상 없다(package.json 의 `link:` 의존
  //    자체는 lockfile 재생성 없이는 건드리지 않았다 — § Implementation notes 의 편차 참조).
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
  // TASK-MONO-686 — 콘솔 둘러보기(`/demo`, `/demo/<domain>`)가 은퇴하면서 외부에 이미 퍼진
  // 링크(포트폴리오·이력서·북마크)를 살려 둔다. 308(permanent) — 은퇴는 영구적인 사실이고,
  // 검색엔진·브라우저 캐시가 새 주소를 정본으로 배우길 원한다.
  //
  // 🔴 각 도메인 키는 `DEMO_TOUR_DOMAIN_REDIRECTS` 위 정의를 그대로 쓴다(손으로 다시
  // 적지 않는다 — 두 벌이면 한쪽만 고쳐진다).
  // 🔴 모르는 `/demo/<x>` 도 404 가 아니라 **실제 개요 화면**으로 보낸다 — 방문자가 「이
  // 링크는 죽었다」로 읽는 대신 살아 있는 콘솔에 착지해서 스스로 찾아갈 수 있게 한다
  // (`:path*` 는 세그먼트 0개도 매칭하므로 이 한 줄이 맨살 `/demo` 도 함께 덮는다).
  async redirects() {
    return [
      ...DEMO_TOUR_DOMAIN_REDIRECTS.map(({ domain, destination }) => ({
        source: `/demo/${domain}`,
        destination,
        permanent: true,
      })),
      { source: '/demo/:path*', destination: '/dashboards/overview', permanent: true },
    ];
  },
};

export default nextConfig;
