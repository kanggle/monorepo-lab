# TASK-FE-080 — web-store no-regret 페이지로딩 윈 (한글 폰트 fallback + barrel import 트리셰이킹)

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **App**: web-store (Next.js 15)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (config 수준, 런타임 로직 무변경)

## Goal

web-store 는 이미 페이지 로딩이 잘 최적화돼 있어([[TASK-FE-079]] 분석: ISR
full-route cache · `next/image`+priority · below-fold lazy · standalone ·
SSR waterfall 부재) 안전한 추가 윈은 소폭이다. 그 중 **회귀 위험 0 (no-regret)**
인 두 가지 config 윈을 적용한다.

1. **한글 폰트 fallback 체인** — `lang="ko"` 스토어인데 `Noto_Sans_KR({ subsets:
   ['latin'] })` 라 한글 본문(주 콘텐츠)은 Noto 미적용 → 브라우저 기본 폰트
   (종종 serif)로 렌더된다. korean subset(수 MB)을 self-host 하는 대신
   OS-native fallback 체인을 지정해 **0 바이트로 한글 렌더 품질 + CLS** 를
   개선한다(`adjustFontFallback` Next 기본 true 가 fallback 메트릭 보정).
2. **barrel import 트리셰이킹** — `experimental.optimizePackageImports` 로
   workspace UI barrel(`@repo/ui`)·`@repo/utils`·`@tanstack/react-query` 의
   named import 가 패키지 전체를 클라이언트 번들로 끌어오지 않게 한다.

## Scope

**In scope** (web-store only, config 2파일):

1. `src/app/layout.tsx` — `Noto_Sans_KR(...)` 에 `fallback: ['Pretendard',
   'Apple SD Gothic Neo', 'Malgun Gothic', 'sans-serif']` 추가(+ 의도 주석).
2. `next.config.ts` — `experimental.optimizePackageImports: ['@repo/ui',
   '@repo/utils', '@tanstack/react-query']` 추가.

**Out of scope**: 웹폰트 교체(Pretendard self-host 등 디자인 결정) · korean
subset 추가(수 MB, 역효과) · 측정 게이트 윈(auth 하이드레이션·HeroBanner
preload — baseline 실측 후 별도 판단) · 비현실/중복 윈(generateStaticParams
backend-less 불가 · axios→fetch ISR 중복).

## Acceptance Criteria

- **AC-1 — 폰트 fallback.** `notoSansKR.className` 적용 시 한글 글리프가
  지정한 OS-native 폰트 체인으로 렌더(브라우저 기본 폰트 fall-through 제거).
  latin subset·weight·`display:swap`·preload 기존 유지. 추가 네트워크 폰트
  바이트 0.
- **AC-2 — bundle config.** `experimental.optimizePackageImports` 에 3개 패키지
  등록, `next build` 정상(production 빌드 GREEN). transpilePackages 와 공존.
- **AC-3 — 게이트.** `tsc --noEmit` clean + `next lint` clean + `next build`
  성공(둘 다/빌드 로컬 검증) + CI `vitest run` GREEN (web-store 는 vitest4×Node24
  로컬 미기동 → CI Node20 권위, [[env_webstore_vitest4_node24_module_evaluator]]).
  런타임 로직·렌더 트리 무변경(config-only) → 기존 2046 테스트 무영향.

## Related Specs

- 없음 — `frontend-app` service-type 성능 config 튜닝(특정 도메인 feature 아님).

## Related Contracts

- 없음.

## Edge Cases

- `fallback` 지정 + `adjustFontFallback`(기본 true) 공존: Next 가 메트릭 보정
  `_Fallback` 폰트(latin-only)를 먼저 두고 그 뒤 커스텀 체인 → 한글은 보정
  폰트도 통과해 Pretendard→AppleSDGothicNeo→MalgunGothic→sans-serif 로 안착.
- `optimizePackageImports` 가 transpiled workspace 패키지에 적용 — `next build`
  로 모듈라화 정상 동작 확인(실패 시 해당 패키지만 목록에서 제외).
- type-only 패키지(`@repo/types`)는 런타임 erase 라 목록 제외(이득 없음).

## Failure Scenarios

- optimizePackageImports 가 특정 패키지 배럴에서 빌드 깨면 → 그 패키지만
  목록에서 제거(나머지 유지). 본 task 에선 `next build` 로 사전 차단.
- 폰트 fallback 은 추가만(기존 latin 경로 불변) → 회귀 경로 없음.
- 효과 검증: [[TASK-FE-079]] baseline 으로 `/`·`/products`·`/products/[id]`
  CLS/LCP before/after 측정해 PR 본문에 근거(추측 아님).
