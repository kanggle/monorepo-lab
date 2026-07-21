# web-store — Performance Baseline

> 목적: **측정 없이 추측 최적화하지 않는다.** 이 문서는 web-store 페이지
> 로딩 성능을 관찰하는 두 경로(field RUM + lab Lighthouse)와 판독 기준을
> 정의한다. 향후 최적화 PR 은 여기서 얻은 수치로 before/after 를 근거화한다.

## 1. Field 측정 — Core Web Vitals (RUM)

실제 사용자 브라우저에서 수집하는 상시 계측. Next 내장
`useReportWebVitals` 사용 — **추가 의존성 없음**.

- 수집기: [`src/app/web-vitals.tsx`](src/app/web-vitals.tsx) — 루트 레이아웃에 1회 마운트.
- 임계값 + 등급 + 페이로드 정규화(순수·테스트됨): [`src/shared/lib/web-vitals.ts`](src/shared/lib/web-vitals.ts).
- 수집 엔드포인트: [`src/app/api/vitals/route.ts`](src/app/api/vitals/route.ts) — `type: "web-vitals"` 플랫 로그 1줄/메트릭 (Vector/VictoriaMetrics 스크레이프 대비).

동작:

| 환경 | 출력 |
|---|---|
| dev (`pnpm dev`) | 브라우저 콘솔 `[web-vitals] LCP=2346 (good) @ /products` |
| prod | `navigator.sendBeacon('/api/vitals', …)` → 서버 stdout 구조화 로그 |

### 판독 기준 (공식 Core Web Vitals)

| 메트릭 | good ≤ | poor > | 의미 |
|---|---|---|---|
| LCP | 2500ms | 4000ms | 최대 콘텐츠 렌더 |
| INP | 200ms | 500ms | 상호작용 응답성 |
| CLS | 0.1 | 0.25 | 레이아웃 이동 |
| FCP | 1800ms | 3000ms | 첫 콘텐츠 페인트 |
| TTFB | 800ms | 1800ms | 서버 응답 시작 |

경계는 더 좋은 버킷에 포함(값 == good 경계 → `good`). 임계값 미정 메트릭
(Next 커스텀 `Next.js-hydration` 등)은 `rating: null` 로 통과.

## 2. Lab 측정 — Lighthouse (on-demand)

CI 게이트로 고정하지 않는다(백엔드 의존 + 환경 flake). 필요 시 빌드된
앱에 대해 1회성으로 실행:

```bash
# 1) 풀스택 기동 후 (또는 prod 빌드 미리보기)
pnpm --filter web-store build && pnpm --filter web-store start   # :3001

# 2) 별도 셸에서 주요 경로 측정
npx lighthouse http://localhost:3001/           --only-categories=performance --output=json --output-path=./lh-home.json
npx lighthouse http://localhost:3001/products    --only-categories=performance --output=json --output-path=./lh-products.json
npx lighthouse "http://localhost:3001/products/<id>" --only-categories=performance --output-path=./lh-pdp.html --view
```

리포트 산출물(`lh-*.json`/`.html`)은 **커밋하지 않는다**(휘발성 측정물).

## 3. 향후 최적화 시 절차

1. 위 두 경로로 **baseline 수치 확보**(대상 경로: `/`, `/products`, `/products/[id]`, `/checkout`).
2. 가장 나쁜 메트릭부터 가설 → 변경 → 동일 조건 재측정.
3. PR 본문에 before/after 수치 첨부. 회귀 없는 변경만 머지.
