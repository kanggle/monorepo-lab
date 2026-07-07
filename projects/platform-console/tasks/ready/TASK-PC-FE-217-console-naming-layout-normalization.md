# TASK-PC-FE-217 — console-web 네이밍/레이아웃 정규화 (3-도메인 컨벤션 정렬)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving rename/move (저위험 정리)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet 4.6 (기계적 rename/move + import 갱신)

---

## Goal

WMS/SCM/이커머스 dedup·분할 sweep 후 남은 **네이밍/레이아웃 편차**를 정리해 3 도메인 컨벤션을 완전 정렬한다. 동작 무변경(순수 파일 이동·개명·barrel 신설), 소비처 import만 갱신.

## Scope

**(1) 이커머스 form-hook 배치 정규화** — SCM/WMS는 hook을 전부 `hooks/`에 두지만, 이커머스는 `hooks/` 디렉터리가 있으면서도 `use-*.ts` 폼 hook을 `components/`에 산재시켰다. 다음을 `ecommerce-ops/components/`→`ecommerce-ops/hooks/`로 이동하고 import 갱신:
- `use-product-form.ts`, `use-promotion-form.ts`, `use-shippings-screen.ts`, `use-template-form.ts`, `use-variant-editor.ts`
(이동 전 각 파일이 실제로 `components/`에 있는지 grep 확인 — PC-FE-216 분할 결과와 충돌 없게 순서 조정.)

**(2) outbound 코어 파일명 정규화** — `wms-outbound-ops/api/outbound-core-api.ts`를 코어 파일 관례(`*-client.ts`; scm-client.ts·wms-client.ts·ecommerce-client.ts와 정렬)에 맞춰 `outbound-client.ts`로 개명. `callOutbound`/`WMS_OUTBOUND_PROFILE`/`clampSize` export·소비처(outbound-order/fulfillment/tms-api·barrel) import 갱신.

**(3) 이커머스 api barrel 신설** — 다른 도메인은 `<name>-api.ts` barrel(`scm-api.ts`·`wms-api.ts`·`outbound-api.ts`)로 slice를 re-export하는데, 이커머스는 barrel 없이 262줄 `ecommerce-ops/index.ts`가 대신한다. `ecommerce-ops/api/ecommerce-api.ts` barrel을 만들어 8 slice re-export를 모으고, `index.ts`는 feature 공개표면(컴포넌트/타입/barrel)만 남겨 슬림화. **공개 export 표면은 byte-동일 유지**(소비처 import 경로가 index 경유면 깨지지 않게).

**(4) SCM demand-planning 네이밍 [판단 항목]** — `scm-replenishment/api/demand-planning-api.ts`·`scm-config/api/demand-planning-seed-api.ts`는 feature 아닌 backend 개념으로 명명됐고 `*-client.ts` wrapper가 없다. **단, `demand-planning`이 도메인상 명확한 개념이면 개명이 오히려 가독성 저해** → 구현자가 판단: 개명이 명료성을 높일 때만 `replenishment-api.ts`류로, 아니면 **skip하고 그 근거를 PR 설명에 기록**. `*-client.ts` 강제 신설도 불필요(단일 endpoint 그룹이면 inline `callScmGateway`가 적절).

**Out of scope:** 로직·시그니처·엔드포인트·proxy·contract·컴포넌트 마크업 무변경. PC-FE-213(ecommerce gateway 승격)·PC-FE-216(PromotionForm 분할)과 ecommerce 디렉터리 접촉이 겹치므로 **PC-FE-213/216 머지 후 rebase하여 착수**(순서: 213 → 216 → 217 이커머스 부분).

## Acceptance Criteria
- **AC-1** (1) 5개 hook이 `hooks/`로 이동, 모든 import 갱신, 동작 무변경.
- **AC-2** (2) `outbound-client.ts`로 개명, export/소비처 import 갱신, 동작 무변경.
- **AC-3** (3) `ecommerce-api.ts` barrel 신설·`index.ts` 슬림화, feature 공개 export 표면 byte-동일.
- **AC-4** (4) 개명 or skip 결정 + 근거를 PR 설명에 명시.
- **AC-5** `tsc --noEmit` + `pnpm lint` + `vitest`(전 이동/개명 영향 테스트) green. 신규 테스트 불필요.

## Edge Cases / Failure Scenarios
- **가장 큰 함정: import 경로 누락** — 이동/개명 후 소비처(테스트 포함) import 미갱신 시 CI RED. `pnpm lint`(no-unused/미해결 import)+`tsc`로 전수 확인 필수(env_console_web_local_verify_needs_lint).
- barrel 신설이 순환 import 유발하지 않게(index→api barrel 단방향).
- PC-FE-216과 `components/use-promotion-form.ts` 접촉 충돌 → 216 머지 후 착수.

## Related
- 정렬 대상: PC-FE-189/192/208(gateway 위치·명명), PC-FE-213(ecommerce gateway 승격).
- 선행 권장: PC-FE-213, PC-FE-216 (ecommerce 디렉터리 충돌 회피).
