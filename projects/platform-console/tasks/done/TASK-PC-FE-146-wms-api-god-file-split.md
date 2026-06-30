# Task ID

TASK-PC-FE-146

# Title

console-web `wms-ops/api/wms-api.ts` god-file 분할: 506줄 단일 API 클라이언트를 도메인 endpoint 그룹별 sub-module로 분할하고, 원본 `wms-api.ts`는 barrel로 유지해 import-site 0 변경 — behavior-preserving

# Status

done

# Owner

frontend (Sonnet 4.6 — behavior-preserving 구조 분할, contract/spec/backend 무변경)

# Task Tags

- code
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-007 (wms-ops 섹션 도입 — § 2.4.5, ADR-MONO-013 Phase 4 slice 1). 본 task는 그 위에서 **동작 동일·계약 동일**, 파일 구조만 변경한다.
- **pattern reference**: TASK-PC-FE-098 (erp-api god-file 분할 1,113줄→4 sub-module+barrel) / TASK-PC-FE-102 (ledger-api 분할) — 동형 패턴 적용.

# Goal

506줄 단일 파일 `features/wms-ops/api/wms-api.ts`를 도메인 endpoint 그룹별 cohesive sub-module로 분할한다. 원본 경로 `@/features/wms-ops/api/wms-api`는 barrel(`export` re-export)로 유지하므로 **모든 importer는 0 수정**으로 기존과 동일하게 동작한다.

behavior-preserving: export 시그니처·함수 동작·wire(엔드포인트/메서드/바디)·에러 처리·로그 이벤트 문자열·Zod 검증 형상 byte-identical. 동작 변경 0.

# Scope

## In Scope

분할 결과 파일 구성:

- **`api/wms-client.ts`** — HTTP 코어 leaf: `callWmsAdmin<T>()` + `parseWmsError()` + `readLagHeader()` + `CallOptions` 인터페이스 + `WmsResult<T>` 인터페이스 + `pageParams()` 페이지네이션 헬퍼. auth-model 주석(§ 2.4.5 per-domain credential), 에러 envelope 주석, resilience taxonomy 주석 전체 이전. 어떤 sibling wms 모듈도 import하지 않는 leaf.
- **`api/wms-inventory-api.ts`** — inventory snapshot(`listInventory`), inventory by-key(`getInventoryByKey`), throughput(`getThroughput`), orders(`listOrders`). `/dashboard/inventory*` + `/dashboard/throughput` + `/dashboard/orders` endpoint 그룹.
- **`api/wms-shipments-api.ts`** — shipments(`listShipments`), ASNs(`listAsns`), ASN inspection(`getAsnInspection`), adjustments(`listAdjustments`). `/dashboard/shipments` + `/dashboard/asns*` + `/dashboard/adjustments` endpoint 그룹.
- **`api/wms-alerts-api.ts`** — alerts 목록(`listAlerts`), alert acknowledge(`acknowledgeAlert`, THE ONLY mutation). `/dashboard/alerts*` endpoint 그룹.
- **`api/wms-refs-api.ts`** — master refs(`listRefs`), projection-status(`getProjectionStatus`). `/dashboard/refs/{type}` + `/operations/projection-status` endpoint 그룹.
- **`api/wms-api.ts`** (barrel) — `export type { WmsResult }` + 4개 도메인 모듈에서 named re-export. 내부 헬퍼(`callWmsAdmin`, `pageParams`, `CallOptions`)는 barrel에 re-export 하지 않음(비공개 표면 유지).

## Out of Scope

- `shared/api/*`, `shared/lib/*`, route handlers, hooks, components, pages 변경 없음.
- 계약·스펙·백엔드 변경 없음.
- wms-state.ts, wms-proxy.test.ts, wms-api.test.ts 등 테스트 로직 변경 없음(import 경로도 불변).
- 도메인 함수의 동작·wire 변경 없음.

# Acceptance Criteria

- [x] `npx tsc --noEmit` → 0 errors (모든 route handler + state + 테스트가 이동된 export를 동일하게 resolve).
- [x] `npx next lint` → 0 warnings/errors.
- [x] `npx vitest run wms` → 5 test files / 65 tests all green (무회귀).
- [x] `wms-api.ts`는 barrel(36줄; 로직 없음). 원본 506줄 → `wms-client.ts`(246) + `wms-inventory-api.ts`(81) + `wms-shipments-api.ts`(78) + `wms-alerts-api.ts`(52) + `wms-refs-api.ts`(39) + barrel(36).
- [x] `callWmsAdmin` / `pageParams` / `CallOptions`는 barrel에 re-export되지 않음(비공개 표면 유지 — 원본에서도 비공개였음).
- [x] `WmsResult<T>`는 `export type`으로 barrel에 re-export(원본 공개 표면 유지).
- [x] 소스텍스트 가드 없음 확인: `wms-api.test.ts`에 `readFileSync` 없음 → 테스트 파일 수정 불필요.
- [x] 0 change to behavior, contracts, specs, route handlers, hooks, components, log-event strings.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` — Layered by Feature; 분할은 `wms-ops/api/` feature-internal 유지(shared/로 승격 없음).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5 (wms per-domain credential selection) — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 동일 wms read/mutation API·동일 헤더·동일 에러 envelope 처리.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/wms-ops/api/` 내부 구조만 변경. behavior-preserving 구조 분할.

# Architecture

Feature-Sliced Design 내 `features/wms-ops/api/` 내부 sub-module 분리 패턴(PC-FE-098 erp / PC-FE-102 ledger 동형):

- `wms-client.ts` = leaf(HTTP 코어 + 공유 헬퍼). sibling import 없음.
- 4개 도메인 모듈은 `wms-client`에서만 import. barrel ↔ 도메인 순환 없음.
- barrel(`wms-api.ts`)은 named re-export만 — 로직 없음.

분할 전 importer 전수 확인 결과:
- `src/features/wms-ops/api/wms-state.ts` — `./wms-api` 상대 import (barrel re-export로 투명)
- `src/app/api/wms/inventory/route.ts` — `@/features/wms-ops/api/wms-api` (불변)
- `src/app/api/wms/shipments/route.ts` — `@/features/wms-ops/api/wms-api` (불변)
- `src/app/api/wms/alerts/route.ts` — `@/features/wms-ops/api/wms-api` (불변)
- `src/app/api/wms/alerts/[alertId]/acknowledge/route.ts` — `@/features/wms-ops/api/wms-api` (불변)
- `tests/unit/wms-api.test.ts` — `@/features/wms-ops/api/wms-api` (불변)
- `tests/unit/domain-facing-credential.test.ts` — `@/features/wms-ops/api/wms-api` (불변)
- `tests/unit/per-domain-credential.test.ts` — `@/features/wms-ops/api/wms-api` (불변)

# Edge Cases

- **소스텍스트 가드 없음**: `wms-api.test.ts`에 `readFileSync`로 wms-api.ts를 직접 읽는 가드 없음 확인 → 테스트 파일 수정 불필요. (cf. `erp-api.test.ts`는 429-guard가 있어 `erp-client.ts`로 경로 수정이 필요했던 것과 대조적.)
- **`WmsResult<T>` type export**: `callWmsAdmin`은 비공개이나 `WmsResult<T>`는 원본에서 `export interface`였으므로 barrel에 `export type { WmsResult }`로 re-export.
- **`pageParams` 비공개**: 원본에서 export 없음 → sub-module들이 `wms-client`에서 named import; barrel에 노출 안 함.
- **순환 참조**: 도메인 모듈 → `wms-client`만 import. barrel → 도메인 모듈만 import. 순환 없음.

# Failure Scenarios

- barrel에서 `callWmsAdmin`을 re-export하면 public surface 확장 → 원본 비공개이므로 제외(AC).
- 도메인 모듈이 barrel을 import하면 순환 → `wms-client.ts`만 import하도록 각 모듈 설계.
- tsc/lint/vitest 중 하나라도 실패하면 커밋 불가 → 3종 pass 확인 후 커밋.

# Definition of Done

- [x] 5개 sub-module 생성 (`wms-client` + 4 도메인 모듈) + barrel 교체
- [x] export 시그니처·동작·wire byte-identical (behavior-preserving)
- [x] `tsc --noEmit` 0 / `next lint` 0 / `vitest run wms` 65 tests green
- [x] import-site 0 변경 (barrel 경로 유지)
- [x] Acceptance Criteria 충족
- [x] Ready for review
