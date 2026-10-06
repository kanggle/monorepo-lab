# Task ID

TASK-PC-FE-310

# Status

ready

# Title

사이드바 맨 위에 첫 화면처럼 보이는 «개요»(`/dashboards/overview`)와 «카탈로그»(`/console`)가 나란히 있다. 카탈로그를 개요 안으로 접어 넣는다: 테넌트가 없을 때 개요가 카탈로그 그리드를 보여주고, 테넌트가 있을 때는 개요 아래에 «제품·테넌트 전체» 섹션을 둔다. 사이드바 «카탈로그» 항목은 지우고 `/console` 은 개요로 넘겨주는 주소로 남긴다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- navigation

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 기존 컴포넌트 재배치 · 리다이렉트 · 문구 일괄 교체 · 시험 이전. 도메인 로직 없음.
>
> 🔵 **소유자 결정(2026-10-06 UTC):** 대화에서 「개요와 카탈로그의 차이 · 둘 다 필요한가」 → 합치기 권고 → «진행».

---

# Dependency Markers

- 선행/후속: 없음.
- 같은 시기 진행 중인 `TASK-PC-FE-309`(ERP 결재 UUID)는 `features/erp-ops/components/Approval*.tsx` 만 건드린다 — 이 티켓과 겹치는 파일 없음. 단 `ErpSectionNotice.tsx` 는 이 티켓이 문구를 바꾸므로, 309 가 그 파일을 건드리게 되면 직렬 머지.

# Background (착수 전 측정, `origin/main` `3eb21c693`)

| 사실 | 근거 |
|---|---|
| 개요 = 활성 테넌트 범위의 6도메인 합성 + 도메인 상태 요약 카드. 테넌트 없으면 `NoTenantNotice`(«상단 스위처에서 테넌트를 선택») 만 | `apps/console-web/src/app/(console)/dashboards/overview/page.tsx:62-82` |
| 카탈로그 = IAM 레지스트리의 제품 × 테넌트 그리드 + 도메인 상태 점. 테넌트 버튼 = assume-tenant + 그 제품 화면으로 이동. 테넌트 없이도 열린다 | `src/app/(console)/console/page.tsx` · `features/catalog/components/CatalogGrid.tsx:15-19` |
| 레이아웃이 **매 화면** `getCatalog()` 로 상단 `TenantSwitcher` 를 채운다 — 테넌트 선택 자체는 카탈로그 없이도 된다 | `src/app/(console)/layout.tsx:183-188, 279-283` |
| 정상 로그인 착지는 이미 개요(`/` → `redirect('/dashboards/overview')`). `/console` 착지는 **이미 로그인된 채 `/login` 을 연 경우**뿐 | `src/app/page.tsx:29` · `src/app/(auth)/login/page.tsx:103` |
| 상단 로고도 개요로 간다 | `layout.tsx:272-277` |
| `ServiceCatalog` 의 제목이 `<h1>서비스</h1>` 로 고정 — 개요 안에 넣으면 h1 이 둘 | `features/catalog/components/ServiceCatalog.tsx:54-57` |
| 화면 문구 «카탈로그로 이동» = **31곳**(도메인 페이지 27 + `NoTenantNotice` 기본값 · `DomainTenantGate` · `ErpSectionNotice` · `FanSectionNote`) · `/console` 을 가리키는 소스 파일 37개 | `git grep` (Scope 참조) |
| 콘솔 e2e(`tests/e2e`, `e2e-smoke`)와 federation e2e 는 `nav-catalog`·`tile-*`·`/console` 착지를 **단언하지 않는다**(로그인 픽스처는 `/dashboards` 접두사를 기다림) | `tests/e2e/fixtures/login.ts:213` · `tests/federation-hardening-e2e/fixtures/login.ts:141` |
| 루트 `scripts/capture-portfolio.mjs:301,346` 이 `/console` 로 `goto` 한다 — 리다이렉트로 남기면 그대로 동작(공유 경로라 이 티켓에서 수정하지 않음) | |
| «권한 카탈로그»(`/permissions`, RBAC) 는 **다른 개념** — 범위 밖 | |

# Goal

1. 사이드바 1뎁스 묶음이 «가이드 · 개요» 둘이 된다. 첫 화면은 개요 하나다.
2. 카탈로그만 하던 두 일은 개요가 이어받는다: (a) 테넌트 없이 열리는 착지점에서 제품·테넌트를 골라 들어가기, (b) 운영자가 들어갈 수 있는 제품 × 테넌트 전체를 상태 점과 함께 보기.
3. 옛 주소·옛 링크는 깨지지 않는다.

# Scope

## In Scope

- **스펙 먼저**: `specs/services/console-web/architecture.md` — 디렉터리 트리 주석(`:88` 상단 nav 목록에서 «카탈로그» 제거 · `:89` `page.tsx` 설명을 «`/dashboards/overview` 로 리다이렉트(TASK-PC-FE-310)» 로) · E2E 저니 문장(`:384` «로그인 → 카탈로그 → 테넌트 전환» → «로그인 → 개요(제품·테넌트) → 테넌트 전환»).
- `ServiceCatalog` — 제목 단계/문구를 prop 으로 받는다(기본값 = 지금 그대로라 다른 소비자 무변경). 개요 안에서는 `<h2>`.
- `dashboards/overview/page.tsx`
  - 테넌트 없음 분기: `NoTenantNotice` 대신(또는 그 아래에) `ServiceCatalog` 그리드. 안내 문구는 «아래에서 제품의 테넌트를 고르면 그 테넌트로 전환됩니다» 취지.
  - 성공 분기: 개요 카드 + 도메인 상태 요약 아래에 «제품·테넌트 전체» 섹션(`<details>` 접힘 기본). 상태 점은 이미 받아 둔 `healthState` 에서 만든다(`console/page.tsx:46-52` 와 같은 매핑 — 복사하지 말고 공용 함수로 뺀다).
  - BFF 불가 분기: 배너 아래에 같은 섹션(펼침) — 개요가 죽었을 때도 제품으로 가는 길을 남긴다.
  - `getCatalog()` 는 기존 `healthPromise` 처럼 맨 앞에서 동시에 시작한다. 401 은 `console/page.tsx:40` 과 같이 `redirect('/login?error=session_expired')`. 그 밖의 실패는 섹션만 저하(`catalog.degraded` 경로) — 개요를 비우지 않는다.
- `src/app/(console)/console/page.tsx` → `redirect('/dashboards/overview')` 한 줄 페이지.
- `src/app/(auth)/login/page.tsx:103` 의 `redirect('/console')` → `redirect('/dashboards/overview')` (한 홉 줄임). `relogin-loop.test.tsx:109` 등 그 값을 단언하는 시험 함께.
- `console-nav-config.ts:119` «카탈로그» 항목 삭제 + 위 주석(`:115-118`) 갱신. `console-nav-matching.ts:45` 의 `/console` 특례는 남은 소비자가 없으면 삭제.
- «카탈로그로 이동» 31곳 → «개요로 이동» + `href` `/dashboards/overview`. `NoTenantNotice` 기본값 · `DomainTenantGate` · `ErpSectionNotice` · `FanSectionNote` 포함.
- 안내 데이터: `shared/guide/permission-map.ts:234-240`(메뉴 항목) · `features/global-guide/data.ts:80` 의 «콘솔 카탈로그» 문구 · 샘플 커버리지 원장 `shared/sample/coverage.ts:192`(리다이렉트 전용 라우트를 원장이 어떻게 다루는지 AC-0 에서 확인).
- 시험: 기존 단위 시험 갱신(`domain-health-nav.test.tsx:106` `nav-catalog` 존재 단언 → 부재 단언 · `no-tenant-notice.test.tsx:70-72` · `console-nav-matching.test.ts:40-41` · `sample-coverage-ledger.test.ts:201` · `fan-nav`/`sidebar-nav-order-icons` 의 `mockPath='/console'`) + 신규(아래 AC).

## Out of Scope

- 레지스트리 계약·`features/catalog` 의 데이터 경로(`getCatalog`, `CatalogGrid` 동작) — 그대로.
- «권한 카탈로그»(`/permissions`) 문구.
- 루트 `scripts/capture-portfolio.mjs`(공유 경로) — 리다이렉트로 계속 동작한다. 경로 갱신이 필요하면 루트 티켓.
- 상단 `TenantSwitcher` — 그대로.
- `tests/federation-hardening-e2e/specs/*.spec.ts` 머리 주석의 낡은 `/console/...` 경로(이 티켓과 무관한 기존 낡음).

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 위 Background 표의 file:line 재측정(`origin/main` 이 움직였으면 숫자 갱신). «카탈로그로 이동» 실제 개수를 `git grep` 으로 다시 세어 구현 노트에 적는다. 샘플 커버리지 원장이 리다이렉트 라우트를 어떻게 다뤄야 하는지(원장 가드 `sample-coverage-ledger.test.ts` 가 `page.tsx` 모집단을 어떻게 만드는지) 읽고 결정을 적는다.
- [ ] **AC-1 (스펙 먼저)** — `architecture.md` 세 곳이 구현 커밋보다 **먼저**(같은 PR 안 별도 커밋 또는 앞 커밋) 바뀌어 있다.
- [ ] **AC-2** — 사이드바 렌더 DOM 에 `nav-catalog` 가 **없고** `nav-dashboards`(«개요»)는 있다.
- [ ] **AC-3** — 개요, 테넌트 없음: 렌더 DOM 에 카탈로그 그리드(`tile-<product>-tenant-<id>` 버튼)가 있고, h1 은 «운영자 통합 개요» 하나뿐이다.
- [ ] **AC-4** — 개요, 성공: 카드 + 도메인 상태 요약 + «제품·테넌트 전체» 섹션(기본 접힘)이 있고, 섹션 안 타일의 상태 점이 `healthState` 에서 온다(건강한 도메인 하나 · 아닌 도메인 하나로 단언).
- [ ] **AC-5** — 개요, BFF 불가: 배너 + 펼쳐진 카탈로그 섹션.
- [ ] **AC-6** — 카탈로그 레그만 실패(레지스트리 5xx): 개요 카드는 그대로, 섹션만 `catalog-degraded` 문구. 레지스트리 401: `/login?error=session_expired` 로 리다이렉트.
- [ ] **AC-7** — `/console` 요청 → `/dashboards/overview` 리다이렉트(단위 시험) · 이미 로그인된 `/login` → `/dashboards/overview`.
- [ ] **AC-8** — `git grep "카탈로그로 이동"` = 0 · `git grep -E "href=\{?['\"]/console['\"]|linkHref = '/console'"` = 0 (console-web `src`). 로그인 페이지 리다이렉트 외에 `'/console'` 문자열이 남으면 각각 이유를 구현 노트에 적는다.
- [ ] **AC-9 (bite)** — AC-3 의 분기를 되돌리면(테넌트 없음 → `NoTenantNotice` 만) AC-3 칸만 빨강. AC-2 는 nav 항목을 되살리면 빨강.
- [ ] **AC-10** — `tsc --noEmit` · `lint` · `vitest run` 전체 rc=0. 콘솔 e2e 디렉터리 `git grep` 으로 `nav-catalog|tile-|/console` 의존이 0 임을 재확인(nightly 전용 스위트라 PR 레인에서 안 돈다). 머지 뒤 첫 `nightly-e2e.yml` 의 콘솔 잡 결과를 확인해 기록.
- [ ] **AC-11 (라이브, ⚪)** — 다음 데모 창: 로그인 → 개요에서 테넌트가 이미 있으면 «제품·테넌트 전체» 를 펼쳐 다른 테넌트 버튼 → 그 제품 화면 착지 · 샘플 방문자(익명)로 개요 → 섹션이 샘플 테넌트로 렌더.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (디렉터리 트리 `:83-` · E2E `:384`)
- `docs/adr/` ADR-MONO-074 (샘플 방문자 — 개요·카탈로그 둘 다 샘플 원장 `ready`)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2(레지스트리 소비) · § 2.5(resilience) — **변경 없음**. 소비 위치만 옮긴다.

# Edge Cases

- **레지스트리 제품 0개 / 테넌트 0개** — 섹션은 `catalog-empty` 문구(기존). 테넌트 없음 분기에서 그리드가 비면 고를 것이 없으므로 `NoTenantNotice` 문구도 함께 남긴다.
- **샘플 방문자** — 레이아웃·카탈로그 모두 샘플 레지스트리에서 온다. 개요 샘플 원장은 6장 전부 `ok` 라 성공 분기로 간다 ⇒ 섹션이 샘플 테넌트 하나로 접혀 렌더.
- **`CatalogGrid` 의 클릭 = 전체 페이지 이동**(httpOnly 쿠키라 SPA push 불가, `CatalogGrid.tsx:32-36`) — 개요 안에서도 같은 동작이어야 한다. 개요로 돌아오는 게 아니라 그 제품 화면으로 간다.
- **h1 중복** — `ServiceCatalog` 를 개요에 넣을 때 기본 h1 을 그대로 쓰면 접근성 위반(axe 시험이 있는 화면이면 빨강). prop 으로 h2.
- **개요 지연** — 카탈로그 fetch 를 순차로 붙이면 개요 전체가 그만큼 느려진다 → 동시 시작(TASK-PC-FE-117 패턴).

# Failure Scenarios

1. **nav 항목만 지우고 개요에 그리드를 안 넣는다** — 테넌트 없는 운영자는 «상단 스위처에서 선택» 문구만 보고, 제품 × 테넌트 전체 보기가 콘솔에서 사라진다(AC-3·AC-4).
2. **`/console` 페이지를 지운다** — 북마크·이미 로그인된 `/login`·루트 캡처 스크립트가 404(AC-7).
3. **링크 문구만 바꾸고 `href` 를 그대로 둔다** — «개요로 이동» 이 리다이렉트 한 홉을 더 거친다. 동작은 하지만 AC-8 이 막는다.
4. **카탈로그 레그 실패가 개요 전체를 비운다** — `getCatalog()` 예외를 페이지에서 그냥 던지면 개요 카드까지 사라진다(AC-6).
5. **상태 점 매핑을 복사한다** — `console/page.tsx` 가 리다이렉트가 되면서 원본이 사라지므로 복사가 유일본이 되지만, 그 전에 두 벌이 공존하는 순간이 생긴다 → 공용 함수로 빼서 한 벌.
