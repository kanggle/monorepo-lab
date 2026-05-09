# Task ID

TASK-FE-069

# Title

web-store 상품 상세 / 홈 LCP·TTI 개선 — Next.js Image 최적화 활성화 + ReviewList 코드 스플리팅

# Status

review

# Owner

frontend

# Task Tags

- code
- perf

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`web-store` 의 상품 상세(`/products/[id]`) 와 홈(`/`) 페이지의 체감 로딩 속도를 개선한다. 두 가지 변경:

1. **Next.js Image 최적화 활성화** — `next.config.ts` 의 글로벌 `images.unoptimized: true` 를 제거하고 `remotePatterns` 화이트리스트(unsplash, placehold.co, MinIO/S3)를 도입. 컴포넌트 측 선택적 `unoptimized={...}` opt-in 패턴은 그대로 유지(local 개발 + placeholder 안전망). 기대 효과: HeroBanner / ProductImage / ProductCard 의 LCP 30~50% 개선(Sharp 기반 WebP 변환 + 사이즈별 srcset).

2. **ReviewList 코드 스플리팅 + Suspense boundary** — 상품 상세 페이지에서 `<ReviewList>` 를 `next/dynamic` 으로 lazy import 하여 메인 번들에서 분리. Suspense boundary 도 함께 설치 (현 시점은 redundant — `ReviewList` 가 throw 하지 않음 — 그러나 향후 PPR / `useSuspenseQuery` 마이그레이션 시 zero-diff 진입점). `ReviewListSkeleton` 을 loading fallback 으로 재사용. 기대 효과: 상품 상세 메인 chunk 크기 감소 → TTI 개선, ReviewList JS 평가/하이드레이션 비용을 첫 paint 이후로 미룸.

이 task 완료 후:
- `images.unoptimized: true` 제거됨
- `remotePatterns` 에 `images.unsplash.com`, `placehold.co`, `localhost` (dev MinIO presigned URL), 그리고 prod 외부 객체 스토리지 hostname 1건이 명시됨 (env-driven)
- `app/(store)/products/[id]/page.tsx` 가 `<ReviewList>` 를 `next/dynamic` 으로 import + `<Suspense>` 로 감쌈
- 기존 vitest 단위 테스트 전부 PASS (`vi.mock('next/image', ...)` 패턴이라 config 변경에 영향받지 않음)
- `next build` 성공 (Sharp 의존성 자동 검출)

---

# Scope

## In Scope

### 1. `apps/web-store/next.config.ts` 수정

```typescript
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',
  transpilePackages: ['@repo/ui', '@repo/types', '@repo/api-client', '@repo/utils'],
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: 'images.unsplash.com' },
      { protocol: 'https', hostname: 'placehold.co' },
      { protocol: 'http', hostname: 'localhost' },
      // dev / prod object storage (MinIO / S3) — env-driven hostname.
      ...(process.env.NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME
        ? [{ protocol: 'https' as const, hostname: process.env.NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME }]
        : []),
    ],
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
```

핵심 변경:
- `images.unoptimized: true` 삭제
- `remotePatterns` 화이트리스트 도입
- 기존 `transpilePackages`, `output`, `eslint.ignoreDuringBuilds` 유지

### 2. `apps/web-store/src/app/(store)/products/[id]/page.tsx` 수정

```tsx
import { Suspense } from 'react';
import dynamic from 'next/dynamic';
import { ReviewListSkeleton } from '@/features/review/ui/ReviewListSkeleton';

const ReviewList = dynamic(
  () => import('@/features/review').then((m) => ({ default: m.ReviewList })),
  { loading: () => <ReviewListSkeleton count={3} /> }
);
```

JSX 안:

```tsx
<ProductDetailWithCart product={product} />
<Suspense fallback={<ReviewListSkeleton count={3} />}>
  <ReviewList productId={product.id} />
</Suspense>
```

기존 `revalidate = 60`, `getCachedProduct`, `generateMetadata` 모두 그대로 유지.

### 3. 단위 테스트

- 기존 `__tests__/product-detail*.test.tsx` 가 `vi.mock('next/image', ...)` 패턴을 사용하므로 config 변경에 영향 없음 — 별도 추가 불필요
- `__tests__/next-config.test.ts` (신규, 옵션) — `next.config.ts` 의 `images.remotePatterns` 가 4개 이상 항목을 포함하고 `unoptimized` 가 정의되지 않음을 assert. 작성하면 회귀 가드.
- ReviewList 가 dynamic import 로 바뀌면 기존 `__tests__/product-detail*.test.tsx` 의 `<ReviewList>` 직접 import / mock 패턴이 영향받을 수 있음. 영향이 있으면 mock 경로 갱신.

### 4. CI

- 별도 변경 없음. 기존 `frontend-unit-tests` / `frontend-checks` / `frontend-e2e-smoke` job 이 검증.

### 5. 문서

- `specs/services/web-store/architecture.md` § "Rendering Strategy" 갱신:
  - 기존: "Server Components by default, `'use client'` only when needed"
  - 추가: "Heavy below-the-fold client components (e.g., ReviewList) are code-split via `next/dynamic` + Suspense boundary to reduce main bundle size and defer hydration."
  - § "Image Strategy" (신규 미니 섹션) — `remotePatterns` 화이트리스트 정책 + env-driven object storage hostname 1줄.

## Out of Scope

- 실제 PPR (`experimental.ppr`) 활성화 — Next.js 15.x 에서 여전히 experimental, portfolio v1 published 직후 시점이라 별도 task 로 분리 (TASK-FE-070 candidate).
- `useSuspenseQuery` 로 ReviewList 데이터 fetching 전환 — 본 task 의 Suspense boundary 는 현재 redundant 이지만 향후 zero-diff 진입점 확보 목적 (별도 task 분리 가능).
- `/my/*` CSR 영역의 RSC 화 — 큰 리팩터, ROI 낮음 (architecture.md 의 GAP NextAuth bridge 흐름 변경 필요).
- `admin-dashboard` 의 동일 변경 — 별도 task 가 필요하면 후속.
- 다른 image 컴포넌트 가 placehold.co/localhost 외 hostname 사용 시 추가 화이트리스트 — 본 task 의 env 변수로 표현되거나 별도 task.

---

# Acceptance Criteria

1. `apps/web-store/next.config.ts` 에서 `images.unoptimized: true` 가 제거되고 `images.remotePatterns` 가 최소 3개(unsplash + placehold.co + localhost) 항목을 포함
2. `pnpm --filter web-store build` 성공 (Sharp 자동 인스톨 + `output: 'standalone'` 와 호환)
3. `pnpm --filter web-store test` 모든 기존 vitest PASS (회귀 0)
4. `pnpm --filter web-store lint` 통과 (ESLint `ignoreDuringBuilds: true` 가 lint task 에는 영향 없음)
5. `app/(store)/products/[id]/page.tsx` 가 `next/dynamic` 으로 `ReviewList` 를 import 하고 `<Suspense fallback={<ReviewListSkeleton />}>` 으로 감쌈
6. `next build` 산출물의 Route `/products/[id]` chunk 사이즈가 변경 전 대비 감소 (정량은 빌드 로그 비교 — 기준치 명시 안 함, 감소만 확인)
7. (수동 검증) `pnpm ecommerce:up` 후 `http://ecommerce.local/products/<id>` 접속 시:
   - Network 탭에서 `_next/image?url=...` 변환 요청 발생 (이미지 최적화 활성 증거)
   - ReviewList chunk 가 별도 chunk 로 분리되어 lazy-load
   - 첫 paint 시 ProductDetail 영역 hydrated, ReviewList 영역은 skeleton → 데이터 로드 후 교체
8. `frontend-unit-tests` / `frontend-checks` / `frontend-e2e-smoke` CI job 통과
9. `specs/services/web-store/architecture.md` § "Rendering Strategy" 가 코드 스플리팅 정책을 반영, § "Image Strategy" (신규) 가 `remotePatterns` 정책을 명시

---

# Related Specs

- [`projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md`](../../specs/services/web-store/architecture.md) § "Rendering Strategy", § "Tech Stack" — 본 task 가 갱신
- [TASK-INT-022](../done/TASK-INT-022-object-storage-infrastructure.md) (done) — MinIO / S3 객체 스토리지 인프라. 본 task 의 `NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME` env 와 정합
- [TASK-FE-066](../done/TASK-FE-066-admin-product-image-upload-ui.md) (done) — 상품 이미지 업로드 (admin-dashboard 측). web-store 가 같은 객체 스토리지에서 상품 이미지를 fetch
- `platform/service-types/frontend-app.md` — frontend-app 서비스 타입 규약

# Related Skills

- `.claude/skills/frontend/nextjs-config/SKILL.md` (있다면)
- `.claude/skills/frontend/code-splitting/SKILL.md` (있다면)

---

# Related Contracts

- 변경 없음. frontend perf 개선만 — API contract / event contract 영향 0.

---

# Edge Cases

1. **Sharp 미설치 환경에서 `next build` 실패**: Next.js 15+ 는 Sharp 자동 인스톨이지만 standalone Docker 빌드 시 추가 의존성 가능. mitigation: Dockerfile 의 `RUN pnpm install` 단계가 `sharp` 자동 인스톨 — node_modules 에 포함됨.
2. **placehold.co fallback 이미지 변환 비용**: fallback-images.ts 가 생성하는 placehold.co URL 도 `_next/image` 변환 대상 — placehold.co 는 외부 호출 비용 발생. 컴포넌트 측 `unoptimized={url.includes('placehold.co')}` opt-in 패턴이 fallback 을 무변환 처리하여 상쇄. 기존 코드 그대로 유지.
3. **MinIO presigned URL 의 query string 변동**: presigned URL 은 매 요청 다른 signature → `_next/image` 캐시 효율 저하. mitigation: presigned URL 의 base path 만 hostname 화이트리스트 매칭에 영향, `_next/image` 는 query string 포함 캐시 키 — 동일 base + 동일 query 인 경우만 캐시 히트. 자연 동작.
4. **prod object storage hostname 가 env 미설정 시**: `NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME` 누락 시 prod 에서 상품 이미지가 hostname 화이트리스트 미스 → Next/Image 가 400 응답. mitigation: `.env.example` 와 k8s configmap 에 변수 명시. 본 task 의 #5 문서 갱신에 명시.
5. **dynamic import 로 인한 vitest 환경 영향**: vitest jsdom 환경에서 `next/dynamic` 은 즉시 resolve — 기존 테스트가 ReviewList 를 직접 mock 하지 않으면 영향 없음. 영향 시 `vi.mock('next/dynamic', ...)` 추가.
6. **Suspense boundary 가 redundant 인 점에 대한 lint warning**: 현재 `ReviewList` 는 throw 하지 않으므로 Suspense fallback 이 표시되지 않음. 의도된 redundancy — 향후 PPR 진입점. lint warning 없음 (Suspense 자체는 valid React 18 pattern).
7. **Next/Image priority 옵션 충돌**: `HeroBanner` 와 `ProductImage` 가 `priority={current === 0}` 사용 중 — 최적화 활성 시 LCP 후보 이미지의 preload 가 자동 — 기존 `priority` 의 의미는 그대로.

---

# Failure Scenarios

## A. Vercel/Self-host 가 아닌 환경에서 Next/Image 동작

`output: 'standalone'` 모드 + Next.js 자체 image optimization endpoint (`_next/image`) 가 standalone 서버에서 작동. mitigation: 기존 Dockerfile 가 `node server.js` 로 standalone 진입 — endpoint 자동 노출. 추가 작업 없음.

## B. 외부 hostname 화이트리스트 누락 → Next/Image 400 응답

상품 데이터의 thumbnailUrl 이 화이트리스트 외 hostname 인 경우 Next/Image 400. mitigation:
- 컴포넌트의 `onError` fallback (이미 ProductCard / ProductImage 에 구현) → 안전 fallback
- task 전 grep 으로 실제 사용 hostname 전수 확인 → remotePatterns 보강

## C. Build artifact size 회귀 (코드 스플리팅 misconfig)

`next/dynamic` 사용 시 ssr 옵션 default true 라 server bundle 에는 여전히 포함 — client bundle 만 분리. mitigation: `next build` 출력의 First Load JS 비교로 검증 (Acceptance #6).

## D. dynamic import 로 인한 SSR HTML 누락 → SEO 회귀

`next/dynamic` 의 `ssr: false` 사용 시 SSR HTML 에 review 영역 누락 → SEO 손실. mitigation: ssr 옵션 명시 안 함 (default true) → SSR HTML 그대로 유지 + client bundle 만 분리. 본 task 는 ssr 옵션 미사용.

## E. Suspense fallback 이 트리거되지 않아 dead code 화

현재 `ReviewList` 는 promise throw 하지 않음 → Suspense fallback 발동 안 함. 단, `next/dynamic` 의 loading prop 이 동일 skeleton 을 제공 → 사용자 경험 동일. Suspense 는 향후 PPR 진입점 확보용. 의도된 dead code 의미.

---

# Notes

- **Recommended impl model**: **Sonnet 4.6** — config 1 줄 + page tsx 5 줄 + 문서 2 섹션. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6.
- **분량 추정**: production code 변경 ~20 줄, 문서 ~30 줄, 단위 테스트 추가 옵션 ~30 줄. 단일 PR.
- **dependency 표현**:
  - `선행`: 없음. ecommerce v1 published 상태에서 자체 perf 개선
  - `후속`: TASK-FE-070 candidate — `experimental.ppr` 활성화 + `useSuspenseQuery` 로 ReviewList 데이터 fetching 전환
- **리스크**: 낮음. 컴포넌트 측 `unoptimized={...}` opt-in 패턴이 placehold.co/localhost 안전망으로 작용. dynamic import 는 default ssr:true 라 SEO 회귀 없음.
- **PR 묶음**: image config + dynamic import + 문서 갱신 = 단일 PR (perf 패키지 일관성).
