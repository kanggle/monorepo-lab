# Task ID

TASK-PC-FE-149

# Title

console-web `audit/AuditScreen` 컨테이너/프레젠테이셔널 분할: 필터·source·페이지네이션 상태 + intersection-permission UX·403/422/degrade 파생을 `useAuditScreen` 훅으로, 목록 테이블+페이지네이션을 `AuditTable` 프레젠테이셔널 컴포넌트로 추출 — render 출력·DOM·test-id·ARIA·wire shape 불변(behavior-preserving)

# Status

done

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 컴포넌트 분할, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-003(IAM 통합 감사+보안 read 화면 — § 2.4.2 read-only 슬라이스). 본 분할이 보존해야 하는 동작·test-id·intersection-permission UX·discriminated row(`AuditRowCells`)의 출처.
- **note (현 구조)**: [`AuditScreen.tsx`](../../apps/console-web/src/features/audit/components/AuditScreen.tsx)(471줄)는 단일 `'use client'` 컴포넌트가 (a) 필터/source/페이지네이션 상태 + seeded 서버렌더 fallback + `useAuditQuery`, (b) intersection-permission UX(security source pre-disable·403 PERMISSION_DENIED/TENANT_SCOPE_DENIED 방어·422·degrade 파생·permissionMessage 메모), (c) 헤딩·필터 폼·range/permission/degrade/empty 분기·목록 테이블·페이지네이션 마크업을 모두 보유한다. 로직과 렌더가 한 파일에 응집(PC-FE-103/140 분할 대상과 동형).
- **pattern**: container/presentational + 커스텀 훅 추출(PC-FE-103 `WmsOpsScreen` / PC-FE-140 `useShippingsScreen`+`ShippingsTable`와 동일 계열).

# Goal

`AuditScreen`을 **로직(훅) / 표현(테이블) / 얇은 컨테이너** 계층으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `useAuditScreen({ initial, securityEventReadGranted })` — useId 필드 ID 6종, 필터/쿼리/rangeError 상태, seeded SSR fallback, `useAuditQuery`, `securityKnownDenied`·apiError 분기(`permissionDenied`/`validationDenied`/`degraded`)·`permissionMessage` 메모, `submitFilters`/`resetFilters` 핸들러, 페이지네이션 파생(`prevDisabled`/`nextDisabled`/`goPrev`/`goNext`·`rows`/`totalPages`)을 소유. 로직은 분할 전과 1:1 동일(seeded 분기·degrade 조건·query body·permissionMessage 의존성 배열 포함).
- `AuditTable` — 목록 `<table>`(discriminated `AuditRowCells` 재사용) + 페이지네이션 `<nav>`를 표현 전용으로 렌더. 상태 소유권은 훅(부모)에 유지하고 `rows`/`data`/`totalPages`/`prevDisabled`/`nextDisabled`/`onPrev`/`onNext` props로만 받음(controlled).
- `AuditScreen` — 훅 호출 + 헤딩/소개문 + 필터 폼 + range/permission/degrade/empty 분기 + `AuditTable` 배선만 남는 컨테이너. 공개 props(`AuditScreenProps`)·배럴 export 표면 불변.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·ARIA(section/search/nav label)·class·label·security-source pre-disable UX·403 PERMISSION_DENIED/TENANT_SCOPE_DENIED 방어 처리·422·degrade 분기·페이지네이션 계산은 전부 기존과 동일.

# Scope

## In Scope

- **신규 `src/features/audit/components/use-audit-screen.ts`** — 필드 ID·필터/쿼리/rangeError 상태·seeded fallback·`useAuditQuery`·apiError/permission/degrade 파생·`permissionMessage` 메모·submit/reset·페이지네이션 파생을 그대로 이전. `'use client'` 최상단. `FilterState`/`EMPTY_FILTERS` 이전(컨테이너 select onChange 의 `FilterState['source']` 캐스팅 위해 `FilterState` 재export).
- **신규 `src/features/audit/components/AuditTable.tsx`** — 목록 테이블 + 페이지네이션 JSX(기존 471줄 파일의 `<table>`+`<nav>` 블록)를 그대로 이동. `AuditRowCells`/`auditRowKey` 재사용. test-id(`audit-table`, `audit-row-{i}`, `cell-*`, `audit-prev`, `audit-pageinfo`, `audit-next`) 전부 보존. `'use client'` 최상단.
- **`src/features/audit/components/AuditScreen.tsx`** — 훅 + 테이블로 슬림화. 남는 마크업(헤딩·소개문·필터 폼·source select pre-disable·range/permission/degrade/empty 분기) 유지. `AuditScreenProps`·`SOURCE_LABEL`·doc-comment 유지. `'use client'` 최상단.
- **Tests** — `tests/unit/AuditScreen.test.tsx`(+ `audit-nav`/`audit-api`/`audit-proxy`)가 **무변경으로 green**이어야 한다. barrel `@/features/audit`의 `AuditScreen` export 경로·시그니처 불변이므로 import-site 수정 불필요.

## Out of Scope

- `AuditRowCells` 내부 변경(discriminated row 렌더) — 본 분할은 테이블에서 호출부만 이동.
- intersection-permission 조건·degrade/403/422 분기·query body·seeded 조건의 **동작 변경**(순수 이동만).
- `AuditScreen` 공개 props/배럴 export 표면 변경.
- `useAuditQuery`/`audit-api`/proxy/스펙/계약/백엔드 변경.

# Acceptance Criteria

- [x] `useAuditScreen`/`AuditTable` 추출 후 `AuditScreen`은 훅+테이블 배선만 남고, 렌더 출력·DOM·모든 `data-testid`가 기존과 동일하다.
- [x] 필터/source: 제출 시 page 0 재조회, seeded(page 0·무필터) 진입만 서버렌더 seed fallback, 필터/페이지 진입은 fresh 재조회가 불변. from>to 클라이언트 가드(`audit-range-error`) 동작 불변.
- [x] intersection-permission: `securityEventReadGranted===false` 시 security source 옵션 pre-disable(`audit-source-option-*` disabled + `audit-security-locked-hint`); 미상(default) 시 enabled + 서버 403 inline(`audit-permission-denied`) 방어, crash 없음.
- [x] 분기: 403 permission/TENANT_SCOPE_DENIED → `audit-permission-denied`, 422 → 동일 inline, degrade(≥500/네트워크) → `audit-degraded`, empty → `audit-empty`, 그 외 `AuditTable` 렌더가 기존과 동일. 401은 inline 미렌더(re-login 신호).
- [x] 페이지네이션: prev=`query.page`, next/pageInfo=`data.page`(서버 확정 페이지) 두 소스 구분 유지, disabled 조건·pageInfo 문자열이 불변.
- [x] `npx vitest run audit` green(38 passed·무회귀), `npx tsc --noEmit` clean, `npx next lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.2(IAM 통합 감사·보안 read) — read-only 소비, 변경 없음(동일 호출·동일 wire shape).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / § 2.5 Resilience — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리, degrade-only 분기 유지).

# Related Contracts

- 변경 없음. 동일 감사 목록 조회 API·동일 클라이언트 훅(`useAuditQuery`)·동일 query string(`buildAuditQs`).

# Target Service

- `platform-console` / `apps/console-web` — `src/features/audit/components/{AuditScreen,AuditTable}.tsx` + `use-audit-screen.ts`. behavior-preserving 컨테이너/프레젠테이셔널 + 커스텀 훅 분할.

# Architecture

- React 컨테이너/프레젠테이셔널 + 커스텀 훅 추출 패턴(PC-FE 분할 시리즈 — PC-FE-103 `WmsOpsScreen` / PC-FE-140 `useShippingsScreen`+`ShippingsTable`와 동일 계열). fat `'use client'` 컴포넌트 → (1) 상태·쿼리·파생 훅, (2) 표현 전용 테이블, (3) 얇은 배선 컨테이너. 상태 소유권은 훅(부모)에 유지해 테이블 추출이 데이터 흐름·페이지네이션 가드에 영향을 주지 않음. RSC 경계(`'use client'`)·렌더 트리 불변.

# Edge Cases

- seeded(page 0·무필터) 진입: 서버렌더 `initial` seed fallback. 필터/페이지 이동 후에는 seed 미사용 → fresh 재조회 — 훅의 `seeded` 파생 그대로 이동.
- intersection-permission known-denied vs unknown: `securityEventReadGranted===false` → security 옵션 pre-disable+hint; `undefined` → enabled + 서버 403 방어 inline. 훅 `securityKnownDenied`/`permissionMessage`(source가 security면 SECURITY_EVENT_READ_REQUIRED) 매핑 불변.
- TENANT_SCOPE_DENIED: code 우선 분기로 테넌트 전용 메시지 — `permissionMessage` 메모 순서(code → 403 → 422) 그대로.
- unknown/future source 행: `AuditRowCells` generic 분기(`generic-row-note`) — 테이블 이동 후에도 crash 없음.
- 페이지네이션: prev는 `query.page`(클라이언트), next/pageInfo는 `data.page`(서버 확정) 기준 — 훅 `prevDisabled`/`nextDisabled`/pageInfo가 두 소스 구분 유지.
- 401: re-login 신호(api client redirect), inline permission/degrade 미렌더 — 훅 분기(`!apiError || status>=500` degrade, 403/422 만 inline) 그대로.

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → `AuditScreen.test.tsx` RED 또는 e2e 셀렉터 깨짐: AC로 test-id 전수 보존 가드, `vitest run audit`로 회귀 확인.
- 상태를 테이블로 내리면(props-lift 누락) 페이지 이동 시 상태 리셋 → 상태는 훅(부모)에 유지, 테이블은 표현 전용(controlled).
- `permissionMessage` 의존성 배열(`[apiError, query.source]`)이 추출 중 바뀌면 security-source 메시지 회귀 → 순수 이동, Edge Case로 가드.
- seeded fallback 조건이 바뀌면 필터/페이지 진입 시 미필터 목록 깜빡임 회귀 → `seeded` 파생 그대로 이동.
- 잔여 미사용 import(useId/useMemo/useState/ApiError 등 컨테이너에서 제거) → `next lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC).
- 페이지네이션 disabled가 단일 소스로 합쳐지면 경계 동작 변화 → prev=`query.page`, next=`data.page` 두 소스 구분 유지.

# Definition of Done

- [x] `use-audit-screen.ts`(상태·쿼리·permission/degrade 파생·페이지네이션) + `AuditTable.tsx`(테이블+페이지네이션) 추출, `AuditScreen.tsx`는 배선만
- [x] 렌더 출력·DOM·전체 `data-testid`·ARIA·intersection-permission/403/422/degrade/empty 분기·페이지네이션 behavior-preserving
- [x] 기존 vitest(AuditScreen.test.tsx 포함 38 passed) 무변경 green + tsc + lint clean, 무회귀; scope = console-web only
- [x] Acceptance Criteria 충족
- [x] Ready for review
