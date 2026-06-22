# TASK-FE-079 — web-store 성능 측정 baseline (Core Web Vitals RUM + Lighthouse 절차)

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **App**: web-store (Next.js 15)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (계측 추가, 런타임 동작 무변경)

## Goal

"웹스토어 속도 향상" 요청 분석 결과, web-store 는 **이미 페이지 로딩이 잘
최적화**되어 있다(공개 카탈로그 ISR `revalidate=60` Full Route Cache ·
`next/image`+`priority` · below-fold lazy import · Toss SDK 지연 로드 ·
`output: standalone` · 6개 라우트 스켈레톤). 추가로 안전한 코드 개선은
소폭이며, **측정 baseline 부재**가 진짜 갭이다 — "측정 없는 추측 최적화"는
회귀를 못 잡는다.

따라서 먼저 **성능 측정 인프라**를 구축해 향후 최적화를 데이터 기반으로
만든다. (1) field RUM: Core Web Vitals 상시 계측, (2) lab: Lighthouse 1회성
측정 절차 문서화.

## Scope

**In scope** (web-store only):

1. `src/shared/lib/web-vitals.ts` — 순수 로직: `WEB_VITALS_THRESHOLDS`(공식
   good/poor 경계) · `rateMetric(name,value)` · `buildVitalsPayload(metric,path)`.
2. `src/shared/lib/index.ts` — 바렐 re-export 추가.
3. `src/app/web-vitals.tsx` — `'use client'` 수집기. Next 내장
   `useReportWebVitals`(의존성 추가 없음). dev=콘솔, prod=`sendBeacon` →
   `/api/vitals`(fetch keepalive 폴백).
4. `src/app/api/vitals/route.ts` — POST 빈 204 sink, `type:"web-vitals"` 플랫
   구조화 로그 1줄/메트릭(Vector/VictoriaMetrics 스크레이프 대비). malformed
   body 무시.
5. `src/app/layout.tsx` — `<WebVitals />` 루트 마운트(import 1 + 엘리먼트 1).
6. `src/__tests__/web-vitals.test.ts` — rateMetric(good/mid/poor + 경계 포함 +
   null) · buildVitalsPayload(ms 반올림 · CLS 3자리 · null rating 통과).
7. `PERFORMANCE.md` — baseline 판독 기준 + Lighthouse on-demand 절차 + 향후
   최적화 워크플로.

**Out of scope**: 실제 최적화 변경(폰트 fallback·optimizePackageImports 등은
baseline 확보 후 별도 task) · Lighthouse CI 게이트 고정(백엔드 의존+flake) ·
측정 산출물 커밋.

## Acceptance Criteria

- **AC-1 — rating 정확성.** `rateMetric('LCP',2500)='good'`,
  `('LCP',4000)='needs-improvement'`, `('LCP',5000)='poor'`;
  `('CLS',0.1)='good'`, `('CLS',0.25)='needs-improvement'`; 미정 메트릭
  (`Next.js-hydration`) → `null`. 임계값 테이블 = {LCP,INP,CLS,FCP,TTFB,FID}.
- **AC-2 — payload 정규화.** 타이밍 메트릭 정수 ms 반올림, CLS 3자리 유지,
  `rating`+`path` 부착, 커스텀 메트릭 `rating:null` 통과.
- **AC-3 — 계측 배선.** `<WebVitals />` 루트 레이아웃 마운트, dev=콘솔/
  prod=beacon 분기, `/api/vitals` 204 응답. 의존성(package.json) 변경 0 —
  Next 내장 `next/web-vitals` 사용.
- **AC-4 — 게이트.** `next lint` clean + `tsc --noEmit` clean(둘 다 로컬 검증)
  + CI `vitest run` GREEN(web-store 는 vitest4×Node24 로컬 미기동 → CI Node20
  권위, [[env_webstore_vitest4_node24_module_evaluator]]). 런타임 동작 무변경
  (계측 추가만, 기존 렌더 경로 unchanged).

## Related Specs

- 없음 — 횡단 계측(특정 도메인 feature 아님). `frontend-app` service-type
  성능 관측성 보강.

## Related Contracts

- 없음 — `/api/vitals` 는 same-origin 내부 텔레메트리 sink(외부 계약 아님,
  fire-and-forget 204).

## Edge Cases

- beacon body 가 `text/plain` 으로 도착 → route 는 `request.text()` 후
  `JSON.parse`(빈/malformed → 무시, 항상 204).
- `navigator.sendBeacon` 미지원 브라우저 → `fetch(keepalive:true)` 폴백.
- CLS 외 메트릭 음수/0 불가 가정(web-vitals 보장); 경계값은 더 좋은 버킷.
- Next 커스텀 타이밍(`Next.js-hydration`/`-route-change-to-render`/`-render`)
  → 임계값 없음 → `rating:null` 로 그대로 수집(노이즈 아님, 관측 대상).

## Failure Scenarios

- 계측 자체 실패가 페이지를 깨면 안 됨 → 수집기는 렌더 `null`, route 는
  예외 삼킴. RUM 경로 장애가 사용자 경험에 영향 0.
- 회귀 검출: baseline 확보 후 최적화 PR 이 메트릭을 악화시키면 before/after
  비교로 차단(본 task 는 그 검출 장치를 설치).
