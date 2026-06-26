# Task ID

TASK-PC-FE-135

# Title

console-web feature-barrel RSC client-reference First Load sweep: erp (4 routes) + ecommerce (11 routes) 섹션이 각 라우트마다 **피처 전체**를 초기 로드하는 문제(단일 barrel 이 복수 'use client' 라우트-엔트리 스크린을 재-export → Server Component page 가 barrel import 시 RSC client-reference 수집이 형제 스크린·leaf 전부를 모든 라우트 클라이언트 그래프로 끌어옴)를 해소해, 각 라우트가 **자기 슬라이스만** 로드하도록 — behavior-preserving (TASK-PC-FE-134 패턴의 멀티-라우트 일반화)

# Status

done

# Owner

frontend (Opus 4.8 분석 — 구현 권장=Opus; behavior-preserving 번들/RSC client-reference 최적화, contract/spec/backend 무변경; **접근법 결정(spike) 선행**)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **builds on / generalizes**: TASK-PC-FE-134(ledger-ops 코드 스플릿 — 동일 RSC client-reference ↔ barrel 메커니즘을 **단일 라우트·단일 스크린**에서 처음 규명·해소). 본 task 는 같은 메커니즘이 **멀티-라우트 피처 barrel**(erp 4 라우트 / ecommerce 11 라우트)에서 더 크게 발현되는 케이스를 다룬다.
- **note (측정 근거 — 결정적 지문)**: 빌드 산출물 실측상 **erp 4개 라우트(`/erp`·`/erp/orgview`·`/erp/approval`·`/erp/delegation`)의 First Load JS 가 전부 동일(618.5 KB / unique 140.3 KB)**, **ecommerce 11개 라우트(products/orders/users/sellers/shippings/promotions/notifications + [id]/new/edit)도 전부 동일(607.4 KB / unique 129.3 KB)**. 라우트마다 컨텐츠가 완전히 다른데 First Load 가 byte-동일하다는 것은 **각 라우트가 자기 코드가 아니라 피처 전체를 로드**하고 있다는 결정적 증거다(`/erp/orgview` 스크린은 38줄·EmployeeOrgViewCard 1개인데도 5개 master List + Approval/Delegation 스크린 + 모든 dialog 까지 140 KB 전부 로드).
- **note (메커니즘)**: erp barrel(`features/erp-ops/index.ts`)은 23개 컴포넌트 중 **20개가 'use client'** 이고 4개 라우트-엔트리 스크린(ErpMastersScreen/ErpOrgViewScreen/ErpApprovalScreen/ErpDelegationScreen) + 12+ leaf(DelegationScreen 499L·ApprovalDetail 451L·DepartmentWriteDialog 435L·MasterWriteDialog 409L·DelegationFactCard 310L 등)를 재-export. ecommerce barrel 은 **24/24 'use client'**(ProductsScreen/ProductForm/ProductDetail/OrdersScreen/… + ConfirmDialog/VariantEditor/StockAdjustDialog/ShipFormDialog/CouponIssueDialog 등 dialog). 각 라우트 page(Server Component)가 이 단일 barrel 을 import → FE-134 에서 규명한 **RSC client-reference 수집**(일반 tree-shaking 아님)이 barrel 의 모든 'use client' 재-export(형제 라우트 스크린 포함)를 그 라우트의 클라이언트 그래프로 eager 수집 → 라우트-그룹 공유 청크(erp `8951`≈119 KB / ecommerce `5858`≈116 KB)에 피처 전체가 적재.
- **note (FE-134 와의 차이 — 더 큰 작업)**: ledger 는 barrel 에 스크린이 **1개**뿐이라 leaf 재-export 만 trim 하면 끝이었다. 여기는 barrel 이 **복수 라우트-엔트리 스크린**을 재-export 하므로 leaf 만 trim 해도 형제 스크린이 여전히 끌려온다 → **각 라우트 page 가 단일 monolithic barrel 대신 자기 라우트 슬라이스만 import** 하게 만들어야 한다(접근법은 § Architecture 의 결정 사항). 또한 ledger 와 달리 **일부 테스트가 leaf 를 barrel 경유로 import**(`features/erp-ops/MasterWriteDialog.test`·`DelegationScreen.test`·`ApprovalScreen.test`·`DepartmentWriteDialog.test`, `features/ecommerce-ops/ShipFormDialog.test`, `ProductForm.test`)하므로 접근법에 따라 그 import 의 direct-path 리다이렉트가 필요할 수 있다.

# Goal

콘솔의 두 멀티-라우트 섹션(erp·ecommerce)에서 **각 라우트가 피처 전체가 아니라 자기 라우트가 실제로 렌더하는 코드만** First Load 로 받도록 한다. 라우트별 컨텐츠가 다른데 First Load 가 byte-동일한 현 상태(피처-전체 로드)를 깨고, 라우트별 First Load 를 그 라우트의 실 사용 컴포넌트 그래프로 한정한다.

behavior-preserving: 모든 라우트의 렌더 출력·동작·게이트/에러/degrade·money/표시 로직 불변. 변경은 **각 라우트가 로드하는 코드의 범위**(피처 전체 → 라우트 슬라이스)뿐이다.

# Scope

## In Scope

- **접근법 결정(spike, AC-0 선행)** — 다음 중 측정으로 택1(§ Architecture):
  - **(A) Next.js `optimizePackageImports`** — `next.config` 에 `@/features/erp-ops`·`@/features/ecommerce-ops`(필요 시 추가 피처) 등록. Next 의 공식 barrel-최적화로, barrel import 를 빌드 시 named 직접 import 로 변환해 미사용 재-export 의 client-reference 수집을 차단. **장점**: barrel 구조·테스트·page import 무변경(최소 코드), 회귀 방지로 일반화. **검증 필요**: `@/` path-alias 로컬 barrel 지원 여부 + 라우트별 First Load 가 실제로 분리되는지 빌드 실측 + 전 라우트 무회귀.
  - **(B) 라우트별 sub-barrel** — `@/features/erp-ops/<route>`(masters/orgview/approval/delegation) 식으로 라우트 슬라이스 barrel 분리, 각 page 가 자기 sub-barrel import. ecommerce 도 facet 별. **장점**: 명시적·결정적. **비용**: barrel 재구성 + page import 갱신 + 일부 테스트 direct-path 리다이렉트 + "app 은 피처 barrel 만 import" 레이어링 규칙과의 정합성 판단(sub-barrel = 피처 공개면의 일부로 볼지) → § Architecture 에서 명문화 필요.
  - **(C) page 의 라우트-엔트리 모듈 직접 import** — page 가 monolithic barrel 대신 스크린 모듈을 직접 import. 레이어링 규칙 위반 소지 → 비권장(결정 시 근거 명시).
  - **권장**: (A) 를 우선 spike 해 실측 확인 → 작동 시 채택(가장 작은 변경, 회귀 방지 일반화). 미작동/부분작동 시 (B) 폴백.
- **erp-ops** — 4 라우트가 각자 슬라이스만 로드하도록 적용 + 라우트별 First Load 실측 기록.
- **ecommerce-ops** — 11 라우트(facet list/detail/form)가 각자 슬라이스만 로드하도록 적용 + 라우트별 First Load 실측 기록. (선택 2차: detail/screen 의 dialog — ConfirmDialog/VariantEditor/StockAdjustDialog/ShipFormDialog/CouponIssueDialog 등 사용자 액션 트리거 모달 — 을 `next/dynamic`/`React.lazy` 로 지연; **접근법 (A) 채택 시 dialog 지연은 별 효과/별건일 수 있으니 실측 후 판단**.)
- **Tests** — 접근법에 따라:
  - (A) 채택 시: 테스트 무변경 기대(barrel 그대로) — 전 콘솔 vitest 무회귀 확인.
  - (B)/(C) 채택 시: barrel 에서 trim/이동된 leaf 를 import 하던 테스트(위 note 의 6개 파일)를 direct `./components/<Name>` 경로로 리다이렉트(FE-134 의 테스트가 이미 쓰는 패턴) + 무회귀 확인.
- **번들 실측(필수)** — `pnpm build` 산출물에서 erp 4 / ecommerce 11 라우트의 First Load JS 가 **더 이상 byte-동일이 아니고 각자 축소**됨을 확인, before/after 수치를 task/PR 본문에 기록.

## Out of Scope

- **wms-ops / scm-ops** — 조사 완료, **본 task 대상 아님**: wms-ops barrel 은 컴포넌트 2개(WmsOpsScreen·AcknowledgeAlertDialog)뿐·단일 라우트·3 섹션 전부 first-paint 필요 → barrel-trap 미발현(WEAK). scm-ops barrel 은 3개·단일 주 라우트(/scm)·SKU breakdown 섹션만 지연-적격(작은 win) → 별도 선택 task 후보(LOW). 두 피처는 First Load 가 라우트별로 이미 다르며 피처-전체-로드 지문이 없음.
- 라우트-그룹 공유 청크(`8951`/`5858`)를 **무조건 제거**하는 것 — 진짜 공유되는 인프라(AsOfPicker·badges·공통 hooks·공통 dialog)는 공유 청크에 남는 게 정상. 목표는 **라우트가 안 쓰는** 형제-라우트 코드를 그 라우트 First Load 에서 빼는 것.
- 공유 baseline 청크(framework/react/콘솔 layout ≈478 KB) 축소 — 전역, 별건.
- 백엔드/계약/스펙 변경.

# Acceptance Criteria

- [ ] **AC-0 (spike·선행)**: 접근법 (A)/(B)/(C) 중 하나를 `pnpm build` 실측으로 선택하고 근거를 task/PR 에 기록(특히 (A) `optimizePackageImports` 가 `@/features/*` 로컬 barrel + 라우트별 First Load 분리에 실제 효과가 있는지 확인 결과).
- [ ] erp 4 라우트의 First Load JS 가 더 이상 byte-동일이 아니며, 컨텐츠가 가벼운 라우트(`/erp/orgview`)가 무거운 라우트(`/erp`)보다 작게 로드된다(실측 수치 기록).
- [ ] ecommerce 11 라우트의 First Load JS 가 더 이상 byte-동일이 아니며 각 라우트가 자기 슬라이스만 로드한다(실측 수치 기록).
- [ ] 모든 erp·ecommerce 라우트의 렌더 출력·동작·게이트/에러/degrade·dialog 동작이 기존과 동일(behavior-preserving).
- [ ] `pnpm exec vitest run` green(무회귀; 접근법 (B)/(C) 시 barrel-leaf 테스트 import 를 direct 경로로 리다이렉트), `npx tsc --noEmit` clean, `pnpm lint` clean(`env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / § Performance Budget / § Allowed Dependencies(barrel 레이어링 규칙) — 접근법 (B) 채택 시 sub-barrel 패턴을 이 문서에 명문화해야 할 수 있음(§ Architecture 참조).
- `projects/platform-console/specs/contracts/console-integration-contract.md` — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 라우트가 로드하는 코드 범위만 조정(동일 read 계약·동일 호출).

# Target Service

- `platform-console` / `apps/console-web` — `src/app/(console)/erp/**` + `src/app/(console)/ecommerce/**` page.tsx, `src/features/erp-ops/**` + `src/features/ecommerce-ops/**`(barrel/구조), 접근법 (A) 시 `next.config.*`. behavior-preserving 번들/RSC client-reference 최적화.

# Architecture

- **결정 필요(AC-0)**: 멀티-라우트 피처에서 각 라우트가 자기 슬라이스만 로드하게 하는 방법.
  - FE-134 가 규명한 메커니즘: Next App Router 에서 Server Component page 가 import 하는 모듈 그래프의 모든 `'use client'` 모듈은 그 라우트의 **client reference** 로 수집되며, barrel 의 named 재-export 도(미사용이라도) 일반 tree-shaking 으로 제거되지 않고 수집된다. 따라서 복수 라우트-엔트리 스크린을 재-export 하는 단일 barrel 을 모든 라우트가 import 하면 각 라우트가 형제 스크린 전부를 적재.
  - **(A) `optimizePackageImports`** 가 이 문제의 Next 공식 해법(barrel→직접 import 변환). 로컬 `@/` alias barrel 지원과 효과를 spike 로 확인하면 최소 변경으로 erp+ecommerce(및 향후 회귀)를 동시 해소. **우선 검증 대상.**
  - **(B) sub-barrel** 은 결정적이지만 barrel 재구성 + 레이어링 규칙(§ Allowed Dependencies "app 은 피처 barrel 만 import")과의 정합성 명문화가 필요 → 채택 시 architecture.md 에 sub-barrel 을 피처 공개면으로 인정하는 노트 추가(이 노트 자체가 작은 아키텍처 결정이므로 AC-0 에서 함께 확정; 미확정 상태로 구현 착수 시 HARDSTOP-09 소지).
  - **(C)** 는 레이어링 위반 소지로 비권장.
- RSC/Suspense 경계 재설계 없이 import 범위/배럴 형태만 조정하는 최소-침습 방향(렌더 트리·동작 불변).

# Edge Cases

- (A) `optimizePackageImports` 가 로컬 `@/` alias barrel 을 지원하지 않거나 부분 효과 → (B) 폴백(AC-0 에서 판정).
- erp/ecommerce barrel 이 재-export 하는 진짜-공유 인프라(AsOfPicker·badges·공통 dialog·공통 hooks)는 여러 라우트가 실제 사용 → 그건 공유 청크에 남는 게 정상(라우트별 First Load 에 공통분은 계속 포함). 목표는 안 쓰는 형제-라우트 코드 제거.
- (B)/(C) 채택 시 barrel-leaf 를 import 하던 테스트(6개 파일)가 RED → direct path 리다이렉트(FE-134 패턴). 누락 시 vitest 가 잡도록.
- ecommerce dialog 지연(2차) 적용 시 dialog 가 사용자 액션으로 처음 열릴 때 async 로드 → 기존 즉시-열림 대비 1회 지연(경량). 접근법 (A) 가 이미 충분하면 dialog 지연은 생략 가능.
- 라우트가 실제로 형제 스크린을 참조하는 경우가 있는지 확인(예: 공통 레이아웃/네비) — 있으면 그 공통분은 의도된 공유.

# Failure Scenarios

- 접근법 미결정 상태로 (B) 구현 착수 → 레이어링 규칙 충돌(HARDSTOP-09). 방지: AC-0 에서 접근법 + (B 시) architecture.md 노트를 먼저 확정.
- barrel 재구성/optimize 가 라우트 렌더를 바꿈(엉뚱한 스크린 로드) → 라우트별 스모크 + 기존 라우트 테스트 무회귀로 가드.
- (B)/(C) 에서 barrel-leaf 테스트 import 리다이렉트 누락 → vitest RED 로 즉시 검출, direct path 로 수정.
- First Load 가 줄지 않음(접근법 무효) → AC(byte-동일 해소 + 라우트별 축소 실측)로 검출; 무효면 접근법 재선택.
- lint(no-unused-vars: barrel 정리 후 잔여 import) / tsc RED → push 전 `pnpm lint`+`tsc` 필수.

# Definition of Done

- [ ] AC-0 접근법 결정(spike 실측 근거 기록); (B 채택 시) architecture.md sub-barrel 노트 확정
- [ ] erp 4 / ecommerce 11 라우트 First Load 가 byte-동일 해소·각자 슬라이스만 로드(before/after 실측 기록)
- [ ] 전 erp·ecommerce 라우트 behavior-preserving
- [ ] vitest + tsc + lint green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review

---

# Discovery Notes (조사 기록 — 2026-06-26)

FE-134 종료 직후 동일 패턴 적용 후보를 콘솔 무거운 피처 4종(erp-ops·ecommerce-ops·wms-ops·scm-ops)에 대해 3차원(barrel 재-export 규모 / 라우트→barrel import / 스크린 구조) 병렬 조사. 결과:

- **erp-ops — STRONG**: barrel 20/23 'use client', 4 라우트 First Load byte-동일(618.5 KB) = 피처-전체-로드 지문. → 본 task.
- **ecommerce-ops — STRONGEST(집계)**: barrel 24/24 'use client', 11 라우트 First Load byte-동일(607.4 KB) = 피처-전체-로드, 블래스트 반경 최대. → 본 task.
- **wms-ops — WEAK**: barrel 2 컴포넌트·단일 라우트·3 섹션 first-paint 필요. → 제외.
- **scm-ops — WEAK~MEDIUM**: barrel 3 컴포넌트·단일 주 라우트·SKU breakdown 섹션만 지연-적격(작은 win). → LOW, 별도 선택 task 후보(필요 시 별건).

---

# Implementation Result (2026-06-26)

**AC-0 결정: 접근법 (A) `optimizePackageImports` 채택** — spike 로 실측 확인됨. `next.config.mjs` `experimental.optimizePackageImports: ['@/features/erp-ops', '@/features/ecommerce-ops']` 한 블록만 추가. barrel 구조·page import·테스트 **전부 무변경**(접근법 B/C 의 sub-barrel 재구성·테스트 direct-path 리다이렉트 불필요 — 레이어링 규칙도 그대로). `@/` path-alias 로컬 barrel 에서 정상 동작 확인.

**측정 (`pnpm build`, baseline → with (A))** — byte-동일 지문 해소, 각 라우트가 자기 슬라이스만 로드:

| route | baseline | with (A) | Δ |
|---|---|---|---|
| `/erp` | 619.7 | 557.7 | −62.0 |
| `/erp/orgview` | 619.7 | **522.2** | **−97.5** |
| `/erp/approval` | 619.7 | 536.9 | −82.8 |
| `/erp/delegation` | 619.7 | 535.1 | −84.6 |
| `/ecommerce/products` | 612.7 | 507.5 | −105.2 |
| `/ecommerce/products/[id]` | 612.5 | 498.7 | −113.8 |
| `/ecommerce/orders` | 612.7 | 501.0 | −111.7 |
| `/ecommerce/orders/[id]` | 612.5 | **495.7** | **−116.8** |
| `/ecommerce/users` | 612.7 | 500.9 | −111.8 |
| `/ecommerce/sellers` | 612.7 | 499.5 | −113.2 |
| `/ecommerce/shippings` | 612.7 | 507.9 | −104.8 |
| `/ecommerce/promotions` | 612.7 | 507.2 | −105.5 |
| `/ecommerce/notifications/templates` | 612.7 | 499.8 | −112.9 |
| `/ecommerce/products/new` | 612.7 | 508.6 | −104.1 |
| `/ledger` (sanity) | 517.8 | 517.8 | 0 |
| `/wms` (sanity) | 515.6 | 515.6 | 0 |

erp 4 라우트 −62~−97.5 KB, ecommerce 11 라우트 −105~−117 KB. 더 이상 byte-동일이 아니며 가벼운 라우트(`/erp/orgview`)가 가장 적게 로드. sanity(ledger/wms) 무변동 = 전역 회귀 없음. behavior-preserving(import 해석만 변경). vitest 무회귀·tsc·lint·build green.

**확장성 노트**: `optimizePackageImports` 는 behavior-preserving 한 Next 공식 barrel 최적화로, 향후 멀티-라우트 피처 추가 시 같은 지문(라우트 간 First Load byte-동일)이 보이면 해당 barrel 을 목록에 추가하면 된다. 본 task 는 실측된 erp·ecommerce 두 피처로 범위 한정.
