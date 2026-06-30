# Task ID

TASK-PC-FE-147

# Title

console-web `wms-outbound-ops/api/outbound-api.ts` (495줄) → 도메인 그룹별 sub-module 분할 + 원본 barrel 유지 — behavior-preserving

# Status

review

# Owner

frontend (분석=Sonnet 4.6 / 구현=Sonnet 4.6 — behavior-preserving api 클라이언트 분할, contract/spec/backend 무변경)

# Task Tags

- code
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-057 (wms outbound-service 최초 도입), TASK-PC-FE-085 (cancelOrder), TASK-PC-FE-087 (TMS retry + admin read-model resolver)
- **pattern**: PC-FE-098 / PC-FE-102 동형 behavior-preserving api 파일 분할 시리즈

# Goal

`src/features/wms-outbound-ops/api/outbound-api.ts` 단일 495줄 파일을 도메인 endpoint 그룹별 sub-module 4개로 분할한다. 원본 `outbound-api.ts`는 named re-export barrel로 교체해 모든 import-site를 변경하지 않는다. 함수 시그니처·동작·wire byte 동일(behavior-preserving).

# Scope

## In Scope

- `outbound-core-api.ts` (신규, 비공개 코어): `CallOptions` interface, `parseOutboundError`, `callOutbound`, `clampSize` — 도메인 모듈이 import하는 shared 인프라
- `outbound-order-api.ts` (신규): `listOrders`, `getOrder`, `getSaga`, `listPickingRequests`, `cancelOrder` — § 1.2 / 1.3 / 1.4 / 2.4 / 5.1
- `outbound-fulfillment-api.ts` (신규): `ConfirmPickLine`, `confirmPick`, `PackingUnitLine`, `createPackingUnit`, `sealPackingUnit`, `confirmShipping` — § 2.3 / 3.1 / 3.2 / 4.1
- `outbound-tms-api.ts` (신규): `resolveShipmentIdForOrder`, `retryTmsNotify` — § 4.3 + admin read-model resolver
- `outbound-api.ts` (변경): 구현 제거, named re-export barrel로 교체. 모든 공개 export 유지, 내부 헬퍼(`parseOutboundError`, `callOutbound`) 미누출

## Out of Scope

- 함수 동작·시그니처 변경
- import-site(route handlers, tests, outbound-state.ts) 수정
- 계약·스펙·백엔드 변경

# Acceptance Criteria

- [ ] `outbound-api.ts`가 barrel로만 동작하고, 분할 전과 동일한 named export를 노출한다
- [ ] 내부 헬퍼(`parseOutboundError`, `callOutbound`, `clampSize`)가 barrel을 통해 외부로 누출되지 않는다
- [ ] 모든 import-site(`src/app/api/wms/outbound/**`, `src/features/wms-outbound-ops/api/outbound-state.ts`, `tests/unit/outbound-api.test.ts`)가 경로 변경 없이 동작한다
- [ ] `npx tsc --noEmit` clean
- [ ] `npx next lint` clean
- [ ] `npx vitest run outbound` green (무회귀)

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` — 소비만, 변경 없음
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5.1 — 소비만, 변경 없음

# Related Contracts

- 변경 없음. API 호출 경로·헤더·body·에러 매핑 동일.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/wms-outbound-ops/api/` 디렉터리 내 파일 4개 신규 생성 + 1개 barrel 교체

# Architecture

behavior-preserving 파일 분할(PC-FE-098/102 동형):
- 공유 코어 leaf(`outbound-core-api.ts`)가 인프라 담당, 도메인 모듈 3개가 코어만 import
- 순환 의존 없음: core ← order/fulfillment/tms ← barrel(outbound-api.ts)
- 원본 `outbound-api.ts` 경로 보존으로 import-site 0건 변경

# Edge Cases

- `ConfirmPickLine` / `PackingUnitLine` interface는 `pick/route.ts` / `pack/route.ts`가 `type` import로 사용 → barrel에서 `export type { ConfirmPickLine }` / `export type { PackingUnitLine }` 재노출
- `outbound-state.ts`는 `listOrders`만 사용 → barrel로 투명하게 해결
- 테스트가 `readFileSync`/`resolve(` 패턴으로 소스 파일 경로를 하드코딩하지 않음 → 가드 경로 수정 불필요

# Failure Scenarios

- barrel에서 `export type` 누락 시 `pick/route.ts`, `pack/route.ts` tsc 오류 → `export type { ConfirmPickLine }` / `export type { PackingUnitLine }` 추가로 해결
- 내부 헬퍼를 barrel에서 re-export하면 의도치 않은 공개 API 누출 → barrel은 도메인 export만 포함

# Definition of Done

- [ ] 신규 파일 4개 생성(`outbound-core-api.ts`, `outbound-order-api.ts`, `outbound-fulfillment-api.ts`, `outbound-tms-api.ts`)
- [ ] `outbound-api.ts` barrel 교체 (named re-export, 비공개 헬퍼 미누출)
- [ ] tsc clean / lint clean / vitest green
- [ ] import-site 변경 0건
- [ ] Ready for review
