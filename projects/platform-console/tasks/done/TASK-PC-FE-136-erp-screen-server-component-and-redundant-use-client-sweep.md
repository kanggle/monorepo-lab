# Task ID

TASK-PC-FE-136

# Title

console-web 클라이언트/서버 컴포넌트 경계 정리: erp 4개 라우트-엔트리 스크린(ErpMastersScreen/ErpOrgViewScreen/ErpApprovalScreen/ErpDelegationScreen)을 **Server Component 로 전환**해 정적 헤더(`<h1>`/설명 `<p>`)·데이터 가공(`toOptions`)을 클라이언트 번들에서 제거 + 부모가 client 라 redundant 한 `'use client'` directive(ledger 4 Panel·approval-common)를 정리 — behavior-preserving

# Status

done

# Owner

frontend (Opus 4.8 분석 — 구현 권장=Opus; behavior-preserving 클라/서버 경계 정리, contract/spec/backend 무변경)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **builds on**: TASK-PC-FE-076(erp 단일 `ErpOpsScreen` → 4개 drill-in 라우트 스크린 분할 — 본 task 가 server 전환하는 그 4개 스크린을 만든 작업), TASK-PC-FE-134/135(같은 피처의 RSC client-reference/barrel First Load 최적화 — 본 task 는 그 위에서 **스크린 자체의 server/client 경계**를 한 단계 더 좁힘).
- **note (메커니즘)**: erp 4개 라우트 page(`src/app/(console)/erp/**/page.tsx`)는 전부 Server Component(`async function`, `export const dynamic = 'force-dynamic'`)이고, 각 page 는 자기 라우트 스크린에 **순수 데이터 prop 만** 전달한다(`initialDepartments`/`initialApprovalRequests`/`mastersWritable` 등 — 함수/이벤트 핸들러 prop 없음). 그런데 4개 스크린(`ErpMastersScreen`·`ErpOrgViewScreen`·`ErpApprovalScreen`·`ErpDelegationScreen`)은 자체 hook/이벤트 핸들러가 전혀 없는데도 최상단 `'use client'` 를 달고 있어 client boundary 가 스크린 최상단(정적 `<section><h1><p>` 헤더 + `ErpMastersScreen.toOptions` 가공 포함)에서 시작한다. directive 를 제거하면 boundary 가 **자식 client 컴포넌트**(`DepartmentList`·`ApprovalScreen`·`DelegationScreen`·`EmployeeOrgViewCard`·`AsOfPicker` 등 — 진짜 상호작용 leaf)로 내려가, 정적 헤더와 `toOptions` 가 서버에서 렌더/실행되고 클라이언트 번들에서 빠진다.
- **note (redundant directive 그룹)**: `ledger-ops/{PeriodsPanel,LotsPanel,AccountPanel,ReconciliationPanel}.tsx` 와 `erp-ops/approval-common.tsx`(StatusBadge + 라벨맵 — 주석에 "Pure presentation, no hooks, no data fetching" 명시)는 자체 상호작용이 없고 **부모가 client**(LedgerOpsScreen 은 `useLedgerOpsState` 훅 소유 + `next/dynamic` lazy import / approval-common 은 client 스크린들이 import)라 directive 를 제거해도 import 하는 쪽 컨텍스트를 따라 그대로 client 그래프에 포함된다 → **순수 redundant directive 정리**(번들 효과 미미, behavior 완전 불변). erp 4 스크린(실질 효과)과 구분해 함께 청소.

# Goal

erp 4개 라우트의 정적 헤더·데이터 가공이 클라이언트로 hydration 되지 않고 서버에서 렌더되도록 4개 라우트-엔트리 스크린을 Server Component 로 전환한다. 더불어 부모가 client 라 redundant 한 `'use client'` directive(ledger 4 Panel + approval-common)를 정리한다.

behavior-preserving: 모든 라우트의 렌더 출력·동작·게이트/에러/degrade·write affordance·`?asOf=` 스레딩 불변. 변경은 **client boundary 의 시작 위치**(스크린 최상단 → 진짜 상호작용 자식)뿐이다.

# Scope

## In Scope

- **erp 4개 스크린 Server Component 전환** (실질 효과) — 각 파일 최상단 `'use client'` 제거:
  - `src/features/erp-ops/components/ErpMastersScreen.tsx` (정적 헤더 + `toOptions` 가공 + 5개 master List 분배; 자식에 boolean `writable`·plain `optionSources` 만 전달)
  - `src/features/erp-ops/components/ErpOrgViewScreen.tsx` (헤더 + `AsOfPicker` + `EmployeeOrgViewCard`)
  - `src/features/erp-ops/components/ErpApprovalScreen.tsx` (헤더 + `ApprovalScreen`)
  - `src/features/erp-ops/components/ErpDelegationScreen.tsx` (헤더 + `DelegationScreen` + `DelegationFactCard`)
- **redundant directive 정리** (behavior-preserving, 효과 미미):
  - `src/features/ledger-ops/components/{PeriodsPanel,LotsPanel,AccountPanel,ReconciliationPanel}.tsx`
  - `src/features/erp-ops/components/approval-common.tsx`
- **번들 실측** — `pnpm build` 산출물에서 erp 4 라우트 First Load JS 의 before/after 를 기록(정적 헤더/`toOptions` 만큼의 소폭 감소 기대; redundant 그룹은 무변동 예상).
- **검증** — `pnpm lint` + `npx tsc --noEmit` + `pnpm exec vitest run` 무회귀 + `pnpm build` 성공(RSC 경계 위반은 build 가 검출).

## Out of Scope

- **상호작용이 실재하는 스크린/leaf** — `ProductsScreen`·`OrdersScreen`·`ApprovalScreen`·`DelegationScreen`·`LedgerOpsScreen` 등 useState/useQuery/폼/이벤트 핸들러를 가진 컴포넌트는 정당한 `'use client'` → 손대지 않음.
- **Server Action 전환** — ecommerce `OrderDetail`/`UserDetail`/`SellerDetail`(useRouter refresh 1줄)을 Server Action 으로 바꾸는 것은 리팩토링 비용이 번들 이득을 상쇄 → 별건/보류.
- **barrel/`optimizePackageImports`** — FE-135 에서 처리됨. 본 task 는 스크린 모듈 자체의 directive 만 조정(barrel 무변경).
- 백엔드/계약/스펙 변경.

# Acceptance Criteria

- [ ] erp 4개 라우트-엔트리 스크린이 Server Component(파일 최상단 `'use client'` 없음)이며, 각 라우트(`/erp`·`/erp/orgview`·`/erp/approval`·`/erp/delegation`)의 렌더 출력·write affordance·degrade/notice·`?asOf=` 동작이 기존과 동일.
- [ ] ledger 4 Panel + approval-common 의 redundant `'use client'` 제거 후 ledger 라우트(/ledger 탭 전환·lazy panel)·approval StatusBadge 렌더가 기존과 동일.
- [ ] `pnpm build` 성공(server 전환한 스크린에서 RSC 경계 위반 없음 — 함수 prop/클라이언트 전용 API 미사용 재확인), erp 4 라우트 First Load before/after 기록.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(`env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components — 본 task 가 "상호작용 있는 부분만 client" 원칙을 erp 스크린에 적용. read-only 정합, 신규 규칙 없음.

# Related Contracts

- 변경 없음. client boundary 위치만 조정(동일 read 계약·동일 호출).

# Target Service

- `platform-console` / `apps/console-web` — `src/features/erp-ops/components/{ErpMastersScreen,ErpOrgViewScreen,ErpApprovalScreen,ErpDelegationScreen,approval-common}.tsx`, `src/features/ledger-ops/components/{PeriodsPanel,LotsPanel,AccountPanel,ReconciliationPanel}.tsx`. behavior-preserving 클라/서버 경계 정리.

# Architecture

- Next App Router RSC 규칙: Server Component 는 Client Component 를 자식으로 렌더할 수 있고(데이터 prop 전달 가능), client 가 import 한 모듈은 directive 없이도 client 그래프에 포함된다. 따라서 (a) server page 가 직접 렌더하고 자체 hook 이 없으며 자식에 데이터 prop 만 넘기는 erp 4 스크린은 directive 제거 시 server 로 내려가 실효(정적 헤더 server 렌더); (b) 부모가 client 인 ledger Panel·approval-common 은 directive 제거가 redundant 정리에 그침(behavior 불변).
- RSC/Suspense 경계 재설계·렌더 트리 변경 없음 — directive 제거만.

# Edge Cases

- `ErpMastersScreen.toOptions` 는 순수 함수 → server 실행 안전. 자식(`DepartmentList`·`EmployeeList`·`CostCenterList` 등)에 넘기는 `optionSources` 는 plain 객체 배열(직렬화 가능).
- 4개 스크린이 client 컴포넌트에서 import 되는 경로가 생기면 자동 client 화되어 효과가 줄 수 있음 → 현재는 server page 에서만 렌더됨을 확인(barrel re-export 자체는 directive 를 전파하지 않음).
- approval-common 의 `StatusBadge` 가 server 로 풀리면 이를 import 하는 client 스크린에서 여전히 client 로 쓰임(동작 동일). server-only API 미사용이라 양쪽 안전.
- ledger Panel 은 `LedgerOpsScreen` 이 `next/dynamic` 으로 lazy import → directive 유무와 무관하게 청크 분할 동작 동일.

# Failure Scenarios

- server 전환한 스크린이 실은 클라이언트 전용 API(이벤트 핸들러/브라우저 API/함수 prop 수신)를 쓰고 있었다면 `pnpm build` 가 RSC 경계 에러로 검출 → build 필수.
- 기존 vitest 가 스크린을 client 로 가정한 테스트(렌더/상호작용)면 RED 가능 → vitest 무회귀로 가드, 필요 시 테스트 조정(렌더 자체는 server/client 무관하게 동일 출력).
- lint(no-unused-vars: directive 제거 후 잔여 import) / tsc RED → push 전 `pnpm lint`+`tsc` 필수.

# Definition of Done

- [ ] erp 4 스크린 Server Component 전환 + ledger 4 Panel/approval-common redundant directive 정리
- [ ] 전 erp·ledger·approval 라우트 behavior-preserving (build·vitest·tsc·lint green)
- [ ] erp 4 라우트 First Load before/after 실측 기록
- [ ] scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review

---

# Implementation Result (2026-06-27)

**변경**: 9개 파일에서 `'use client'` directive 제거.

- **erp 4 스크린(실질 효과)** — `ErpMastersScreen`·`ErpOrgViewScreen`·`ErpApprovalScreen`·`ErpDelegationScreen` 을 Server Component 로 전환. 각 라우트 page(이미 Server Component, `force-dynamic`)가 데이터 prop 만 전달하므로 client boundary 가 스크린 최상단(정적 `<section><h1><p>` + `ErpMastersScreen.toOptions`)에서 진짜 상호작용 자식(`DepartmentList`·`ApprovalScreen`·`DelegationScreen`·`EmployeeOrgViewCard`·`AsOfPicker` 등)으로 내려감.
- **redundant directive 정리(효과 미미)** — `ledger-ops/{PeriodsPanel,LotsPanel,AccountPanel,ReconciliationPanel}` + `erp-ops/approval-common`. 부모가 client(`LedgerOpsScreen` 의 `next/dynamic` lazy import / client 스크린의 import)라 directive 제거 후에도 client 그래프 유지 — behavior 완전 불변.

**번들 실측 (`pnpm build`, 동일 worktree·toolchain 에서 stash before/after)** — per-route Size:

| route | Size before | Size after | Δ | First Load |
|---|---|---|---|---|
| `/erp` | 7.99 kB | 7.72 kB | −0.27 | 159 kB |
| `/erp/approval` | 6.16 kB | 6.01 kB | −0.15 | 157 kB |
| `/erp/delegation` | 5.40 kB | 5.24 kB | −0.16 | 156 kB |
| `/erp/orgview` | 2.62 kB | 2.43 kB | −0.19 | 153 kB |
| `/ledger` (sanity) | 9.38 kB | 9.38 kB | 0 | 153 kB |

erp 4 라우트는 정적 헤더+`toOptions` 가 서버로 이동해 per-route 청크가 각 −0.15~0.27 kB 축소. `/ledger` 0 = redundant-directive 그룹은 번들 무변동(예측대로 — 효과는 코드 명확성/일관성). First Load JS 는 공유 baseline 청크가 지배해 반올림상 동일.

**검증**: `npx tsc --noEmit` clean · `pnpm lint` clean(0 warnings) · `pnpm exec vitest run` 2120 pass(전체 동시 실행 시 `LedgerOpsScreen.test.tsx` 의 next/dynamic lazy 경계 1건이 리소스 포화 timeout flake → 단독 재실행 53/53 green 으로 회귀 아님 확인) · `pnpm build` 성공(server 전환 스크린의 RSC 경계 위반 없음). scope = console-web only, contract/spec/backend 무변경.
