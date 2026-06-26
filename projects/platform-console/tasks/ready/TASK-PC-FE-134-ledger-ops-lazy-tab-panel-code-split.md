# Task ID

TASK-PC-FE-134

# Title

console-web `/ledger` 초기 청크 코드 스플릿: `LedgerOpsScreen`의 비-기본 탭 6종 패널 콘텐츠를 `next/dynamic` 지연 로드 + 최초-활성화-시-마운트로 전환해, 첫 로드 시 항상 같이 실리던 단일 페이지 청크(~77 KB)를 기본 탭(Trial Balance)만 남도록 축소 — behavior-preserving

# Status

ready

# Owner

frontend (Opus 4.8 분석 — 구현 권장=Sonnet 4.6 또는 Opus; behavior-preserving 번들 코드 스플릿, contract/spec/backend 무변경)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **builds on**: TASK-PC-FE-072(ledger-ops 섹션 도입 — § 2.4.7.1) + TASK-PC-FE-074(Account 탭) + TASK-PC-FE-075(Statement 드릴) + TASK-PC-FE-091(FX Position Lots 탭) + TASK-PC-FE-092(FX Rates 탭) + TASK-PC-FE-104(FX Rate History 드릴) + TASK-PC-FE-106(모듈 분할 — `useLedgerOpsState` 훅 + `LedgerOpsTabs` 추출). 본 task는 그 위에서 **렌더 트리 동일·로직 동일**, 패널 콘텐츠의 *로딩 시점*만 변경한다.
- **note (측정 근거)**: 빌드 산출물 실측 결과 `/ledger`의 단일 페이지 청크(`static/chunks/app/(console)/ledger/page-*.js`)는 ~77 KB로, 콘솔 전체에서 **단일 페이지 전용 청크 중 최대**다(erp `8951`/ecommerce `5858`는 라우트-그룹 공유 청크라 성격이 다르고, 본 task 대상 아님). ledger-ops 피처는 무거운 외부 의존성(`recharts`/`date-fns`/차트·그리드 라이브러리)이 **전무**하며, 77 KB는 순수하게 16개 탭 하위 컴포넌트 + 훅의 코드량이다 → 의존성 제거가 아니라 **컴포넌트 단위 코드 스플릿**이 유일한 축소 수단.
- **note (현 동작)**: [`LedgerOpsScreen.tsx`](../../apps/console-web/src/features/ledger-ops/components/LedgerOpsScreen.tsx)는 `'use client'` 컴포넌트로 16개 탭 하위 컴포넌트를 **정적 import**하고, 7개 `tabpanel` `<div>`를 **모두 렌더한 뒤 `hidden` 속성만 토글**한다. `hidden`은 DOM 잔존 + CSS 숨김일 뿐 React는 비활성 패널도 **마운트·렌더**하므로, 모든 탭 코드가 첫 로드에 실리고 실행된다. 기본 활성 탭은 `trial-balance`(seeded `entryId`→`entry`, `accountCode`→`account`, `statementId`→`reconciliation` 분기 제외; [`use-ledger-ops-state.ts:78-85`](../../apps/console-web/src/features/ledger-ops/components/use-ledger-ops-state.ts)).

# Goal

`/ledger` 첫 로드의 초기 JS를 **현재 노출 탭(기본=Trial Balance)에 필요한 코드로 한정**한다. 비-기본 탭 6종(Periods · Journal Entry · Reconciliation · Account · FX Position Lots · FX Rates)의 패널 콘텐츠를 `next/dynamic`으로 분리하고, **해당 탭이 처음 활성화될 때 마운트**하도록 바꾼다(최초 활성화 후에는 마운트를 유지해 입력·조회 상태를 보존 — 현 UX 불변).

behavior-preserving: 탭 스트립·ARIA(roving keyboard nav)·각 패널의 렌더 출력·게이트/에러/degrade 분기·money 렌더(F5 `formatMoney`)·seeded 진입 시 초기 활성 탭은 모두 기존과 동일. 변경은 **비-기본 패널 콘텐츠의 로딩/마운트 시점**(첫 로드 즉시 → 첫 활성화 시)뿐이다.

# Scope

## In Scope

- **`src/features/ledger-ops/components/LedgerOpsScreen.tsx`** — 비-기본 6개 패널의 콘텐츠를 패널 단위로 추출/지연 로드:
  - 각 비-기본 탭의 패널 내부 JSX(룩업 폼 + 테이블/디테일 + 조건 분기)를 패널 콘텐츠 컴포넌트로 묶고(예: `PeriodsPanel`, `JournalEntryPanel`, `ReconciliationPanel`, `AccountPanel`, `LotsPanel`, `FxRatesPanel`), 각각을 `next/dynamic`으로 import. props는 현재 `useLedgerOpsState(props)`가 돌려주는 값/핸들러를 그대로 전달(상태 소유권은 부모 `LedgerOpsScreen`에 유지 — 마운트/언마운트와 무관하게 조회 상태 보존).
  - **마운트 게이팅**: 각 비-기본 패널은 "한 번이라도 활성화된 적 있으면 마운트 유지" 규칙으로 렌더(예: `visited` Set/플래그를 `useLedgerOpsState` 또는 화면 로컬 상태로 관리). 첫 활성화 전에는 미마운트 → 코드 미로드. 활성화 후에는 `hidden` 토글만(현재처럼 상태·DOM 유지).
  - **기본 탭(Trial Balance)** 패널은 정적 유지(첫 노출 경로 — 지연 불필요).
  - **seeded 초기 활성 탭 처리**: seeded `entryId`/`accountCode`/`statementId`로 초기 활성 탭이 `entry`/`account`/`reconciliation`인 경우, 그 패널은 첫 렌더부터 활성이므로 `visited`에 초기 포함시켜 즉시 마운트(SSR-seeded 데이터가 곧바로 보이도록). → 일반 진입(기본 trial-balance)에서만 6개 패널이 지연된다.
  - `dynamic(..., { ssr: false })` 사용 가부는 구현 판단: 비활성 패널은 어차피 초기 비노출이며 비-기본 탭 데이터는 클라이언트 훅 fetch 기반이므로 `ssr:false`가 자연스럽다. 단, **위 seeded-활성 패널은 SSR 출력이 필요**할 수 있으니 seeded 분기에서는 즉시 마운트(=클라이언트에서 첫 페인트에 로드)로 처리하고, 필요 시 경량 로딩 플레이스홀더를 둔다.
- **Tests** — `src/features/ledger-ops/**`의 기존 vitest가 무회귀로 통과해야 한다. 특히:
  - **기존 테스트가 비활성 패널의 DOM 존재(`data-testid="ledger-panel-*"`)나 hidden 패널 내부 요소를 단언**하고 있으면, 지연 마운트로 인해 초기 DOM에서 빠질 수 있으므로 **탭 활성화 후 단언**으로 보강(또는 `findBy*` 비동기 쿼리로 dynamic 로드 대기). 활성 탭 전환 → 해당 패널 콘텐츠 렌더 + 데이터 표시를 단언하는 회귀 가드를 추가.
  - 핵심 회귀 가드: (i) 기본 진입 시 Trial Balance 패널 + 탭 스트립이 기존과 동일 렌더, (ii) 각 비-기본 탭 활성화 시 해당 패널이 마운트되어 기존과 동일 동작(룩업/조회/에러/degrade), (iii) 한 번 방문한 탭은 다른 탭으로 갔다 와도 입력/조회 상태 보존, (iv) seeded `entryId`/`accountCode`/`statementId` 진입 시 해당 탭이 초기 활성 + seeded 데이터 즉시 표시.
- **번들 축소 검증(수동/CI)**: 구현 후 `pnpm build` 산출물에서 `/ledger` 페이지 전용 청크가 축소되고 비-기본 패널이 별도 청크로 분리됨을 확인(목표: 페이지 전용 초기 청크 77 KB → 대략 15~25 KB 수준; 정확 수치는 구현 후 측정·기록).

## Out of Scope

- `useLedgerOpsState` 훅의 **쿼리/상태 로직 자체 변경**(마운트 게이팅용 `visited` 플래그 추가는 허용; 조회 트리거·refetch 시점·데이터 흐름은 불변).
- erp 그룹 공유 청크(`8951`, ~119 KB)·ecommerce 그룹 공유 청크(`5858`, ~116 KB) 분할 — 별건(라우트-그룹 공유 청크라 분할 전략·ROI 상이; 본 task는 ledger 단일 페이지 전용 청크 한정).
- 콘솔의 다른 라우트(wms/scm/finance/accounts/audit/dashboards 등) 코드 스플릿 — 별건.
- 공유 baseline 청크(framework/react/콘솔 layout ~478 KB) 축소 — 별건(전역 영향, 더 큰 범위).
- `LedgerOpsTabs`(탭 스트립 자체)·`useLedgerOpsState` 추출 구조 재편 — TASK-PC-FE-106에서 완료, 본 task는 그 구조 위에서 패널 콘텐츠만 지연화.
- 백엔드/계약/스펙/ledger read API 변경.

# Acceptance Criteria

- [ ] 일반 진입(seeded 파라미터 없음) 시 초기 활성 탭은 `trial-balance`이고, Trial Balance 패널 + 탭 스트립 + 섹션 헤딩이 기존과 동일 렌더된다(behavior-preserving).
- [ ] 비-기본 6개 탭(periods/entry/reconciliation/account/lots/fx-rates) 각각을 활성화하면 해당 패널 콘텐츠가 마운트되어 기존과 동일하게 동작한다(룩업 폼·테이블·디테일·forbidden/notFound/error/degrade 분기·money `formatMoney` 렌더 불변).
- [ ] 한 번 방문한 탭은 다른 탭으로 전환했다가 돌아와도 입력값·조회 결과 상태가 보존된다(현 hidden-유지 UX와 동일 — 첫 활성화 후 마운트 유지).
- [ ] seeded 진입(`?entryId=` / `?accountCode=` / `?statementId=`)에서 해당 탭이 초기 활성 + SSR-seeded 데이터가 첫 화면에 표시된다(기존 동작 불변).
- [ ] WCAG: 탭 스트립 roving keyboard nav(ArrowLeft/Right/Home/End) + `tablist`/`tabpanel` ARIA가 기존과 동일하게 동작한다.
- [ ] `pnpm build` 산출물에서 `/ledger` 페이지 전용 초기 청크가 축소되고, 비-기본 패널이 별도 lazy 청크로 분리된다(구현 후 실측 수치를 task/PR 본문에 기록).
- [ ] `pnpm exec vitest run` green(무회귀, dynamic 로드 대기는 `findBy*`/`await` 보강), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / § Performance Budget — 소비만, 변경 없음(코드 스플릿은 perf budget 정합 방향).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.7.1(ledger 섹션) — read-only 소비, 변경 없음(동일 read 계약·동일 호출).

# Related Contracts

- 변경 없음. 패널 콘텐츠의 로딩/마운트 시점만 조정(동일 ledger read API·동일 클라이언트 훅·동일 호출 횟수).

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ledger-ops/components/LedgerOpsScreen.tsx`(+ 추출되는 패널 콘텐츠 컴포넌트, 필요 시 `use-ledger-ops-state.ts`에 `visited` 게이팅 플래그). behavior-preserving 번들 코드 스플릿.

# Architecture

- Next.js App Router 클라이언트 컴포넌트 코드 스플릿 패턴. 현재 단일 `'use client'` 화면이 16개 탭 컴포넌트를 정적 import + `hidden` 토글로 **전부 즉시 마운트** → 비-기본 패널 콘텐츠를 `next/dynamic`으로 분리하고 "최초 활성화 시 마운트(이후 유지)"로 게이팅. 상태 소유권은 부모(`LedgerOpsScreen` + `useLedgerOpsState`)에 유지해 마운트 타이밍이 조회 상태에 영향을 주지 않게 한다. RSC/Suspense 경계 재설계 없이 패널 단위 lazy boundary만 도입하는 최소-침습 변경(렌더 출력·ARIA·게이트 분기 구조 불변).

# Edge Cases

- 일반 진입(기본 trial-balance): 6개 비-기본 패널 미마운트 → 초기 청크 축소. 사용자가 탭 클릭 시 1회 async 청크 로드(경량 플레이스홀더 후 콘텐츠).
- seeded `entryId`(초기 활성=entry) / `accountCode`(account) / `statementId`(reconciliation): 해당 패널을 `visited` 초기 포함 → 즉시 마운트, seeded 데이터 표시. 나머지 5개 패널은 지연.
- 탭 왕복: A 방문 → B로 전환(A는 hidden, 마운트 유지) → A 복귀 시 A의 입력/조회 상태 그대로(언마운트하지 않음).
- 느린 네트워크에서 dynamic 청크 로드 지연 → 패널 자리에 로딩 플레이스홀더(접근성: `role="status"` 또는 기존 "불러오는 중…" 패턴 재사용). 로드 완료 후 정상 렌더.
- Trial Balance가 유일 노출 탭이므로 정적 유지 — 첫 페인트 회귀 없음.
- `ssr:false` 채택 시: seeded-활성 패널은 첫 클라이언트 페인트에 로드(SSR HTML에는 미포함). seeded 데이터는 부모가 props로 보유하므로 hydration 후 즉시 표시 — 빈 화면 깜빡임 최소화 위해 플레이스홀더 필수.

# Failure Scenarios

- dynamic 분리로 비활성 패널 DOM이 초기에 사라져 **기존 테스트가 panel testid 즉시 존재를 단언 → RED**: AC대로 활성화-후 단언(`findBy*`)으로 보강. 누락 시 회귀로 잡히도록 6개 탭 각각의 활성화 회귀 테스트 추가.
- 패널을 언마운트하는 구현(activeonly 렌더)으로 잘못 만들면 **탭 왕복 시 입력/조회 상태 소실** → "최초 활성화 후 마운트 유지"(`visited`) 규칙 준수, AC(iii)로 가드.
- seeded 진입에서 해당 패널을 즉시 마운트하지 않아 **SSR-seeded 데이터가 첫 화면에 안 보임** → seeded 분기 `visited` 초기 포함, AC(seeded)로 가드.
- 상태 소유권을 패널로 내리면(props-lift 누락) 마운트 시 상태 리셋 → 상태는 부모 `useLedgerOpsState`에 유지, 패널은 표현 전용.
- lint(no-unused-vars: 정적 import 제거 후 잔여 심볼) / tsc(dynamic 컴포넌트 props 타입) RED → push 전 `pnpm lint` + `tsc --noEmit` 필수(가드 AC).
- money 렌더가 패널 추출 과정에서 `Number()`/`parseFloat()` 경유로 바뀌면 F5 위반 → 추출은 JSX 이동만, `formatMoney` 경로 불변(ledger-ops grep 테스트가 on-disk 소스 단언).

# Definition of Done

- [ ] `LedgerOpsScreen` 비-기본 6개 패널 콘텐츠 `next/dynamic` 분리 + 최초-활성화-시-마운트(이후 유지) 게이팅, 기본 탭 정적 유지
- [ ] 렌더 출력·ARIA·게이트/에러/degrade 분기·seeded 초기 활성 탭·money 렌더 behavior-preserving
- [ ] `pnpm build` 실측으로 `/ledger` 페이지 전용 초기 청크 축소 확인 + 수치 기록
- [ ] vitest(dynamic 대기 보강) + tsc + lint green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
