# Task ID

TASK-PC-FE-301

# Status

review

# Title

선택 가능한 테넌트가 **0개**인 운영자에게 «테넌트를 선택하세요» 대신 «접근 가능한 테넌트가 없습니다(권한 없음)» 를 보인다

# Owner

platform-console

# Task Tags

- console-web
- ux
- rbac

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 분기 하나와 문구. 게이트 화면이 여럿이라 **한 곳**에서 판정하게 하는 것이 요점.

---

# Goal

`TASK-MONO-730` AC-1 창(2026-09-26 UTC, 16차 AMI)에서 `viewer@demo.com`(역할 0)으로 로그인 → **스위처 없음** · 모든 게이트 화면이 «테넌트를 먼저 선택하세요».
선택할 수 없는데 선택하라고 한다 — 막다른 길이고, 무권한 계정이 보여야 할 «권한 없음» 에 도달하지 못한다.
- 선택 가능한 테넌트 = 쓸 수 있는 상품의 테넌트(`src/shared/lib/active-tenant-default.ts:44` `selectableTenants`) ⇒ 역할 0 ⇒ 0개.
- 게이트 화면들은 권한보다 테넌트를 먼저 본다(`app/(console)/{operators,audit,tenants,accounts,permissions,permission-sets,operator-groups,org-hierarchy,subscriptions,partnerships,dashboards/*}/page.tsx` · `widgets/domain-tenant-gate/DomainTenantGate.tsx` · 도메인 `layout.tsx` 들).

**소유자 결정 (2026-09-26 UTC) = ⓒ** — 선택 가능한 테넌트가 0 이면 안내를 바꾼다(기각: ⓐ viewer 에 권한 없는 테넌트 부여 · ⓑ 현 동작을 정답으로).

# Scope

## In Scope

- «선택 가능 0» 판정을 **한 곳**에 두고, 위 화면들의 «테넌트를 먼저 선택하세요» 분기가 그것을 쓰게 한다(문구 복제 금지).
- 문구: «이 계정에는 접근 가능한 테넌트가 없습니다 — 권한이 필요합니다(관리자에게 요청하세요)» 수준. 정확한 문구는 구현에서 기존 권한 거부 문구(`/partnerships` 의 «… 권한이 필요합니다») 톤에 맞춘다.
- 스위처가 **있지만 아직 안 고른** 경우(선택 가능 ≥ 2)는 지금 문구 유지.

## Out of Scope

- viewer 계정의 시드/권한 변경(ⓐ, 기각).

# Acceptance Criteria

- [x] **AC-1** — 단위: 선택 가능 0 → 새 안내 · 1 → 자동 선택(기존 ②) · ≥2 미선택 → 기존 «선택하세요». 게이트 화면 대표 3곳 이상에서 같은 판정 함수를 쓰는지(렌더 테스트). **CLOSED** — 구현 기록 참조.
- [ ] **AC-2** — 🔴 **창 판정**: `viewer@demo.com` → 게이트 화면에서 새 안내 · `demo@demo.com`(대조군) → 스위처 · 정상 화면. 판정 뒤 `TASK-MONO-730` AC-1 · `TASK-BE-597` AC-2 를 이 결과로 다시 닫는다. **OPEN** — 아래 § 창 판정 런북 참조 (다음 AMI 창에서 수행).

# Related Specs

- `specs/…/console-integration-contract.md` § 2.2(테넌트 범위는 OIDC 토큰에서 파생하지 않는다)
- `TASK-PC-FE-292`(활성 테넌트 기본값) · `TASK-MONO-730` § CORRECTION (2026-09-26)

# Related Contracts

- 없음(프런트 표시).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 레지스트리 조회 실패 | «0개» 로 읽지 않는다 — 기존 오류/저하 표시(없음 ≠ 못 읽음) |
| 익명 방문자(샘플 모드, ADR-MONO-074) | 영향 없음 |

# Failure Scenarios

1. **레지스트리 실패를 «테넌트 0» 으로 처리** → 장애 때 모든 운영자에게 «권한 없음» 이 뜬다.
2. **화면마다 문구를 고친다** → 한 곳이 빠진다(«테넌트를 먼저 선택하세요» 는 20곳 넘게 있다).

---

# 구현 기록 (2026-09-26 UTC)

## 판정 함수 — 한 곳

`shared/lib/active-tenant-default.ts` `noTenantNoticeKind(): Promise<'zero' | 'select'>` — 레지스트리를 읽어 `selectableTenants(registry).length === 0` 이면 `'zero'`, 그 외(≥1, 등록 실패 포함)는 `'select'`. 등록 실패는 `try/catch` 로 잡아 `'select'` 로 폴백한다(Edge Case: 없음 ≠ 못 읽음 — 장애 때 «권한 없음» 이 전체 운영자에게 뜨는 Failure Scenario 1 을 구조적으로 막는다).

## 렌더 — 한 곳

새 위젯 `widgets/no-tenant-notice/NoTenantNotice.tsx`:
- `NoTenantNoticeBody({ kind, testId, description, linkHref?, linkLabel? })` — 순수/동기 함수 컴포넌트. `kind==='zero'` 면 «이 계정에는 접근 가능한 테넌트가 없습니다.» + «권한이 필요합니다 — 관리자에게 요청하세요.» (링크 없음). `kind==='select'` 면 기존 «테넌트를 먼저 선택하세요.» + 화면별 `description` + 카탈로그/스위처 링크(byte-identical, 기존 동작 그대로).
- `NoTenantNotice(props)` — 비동기 래퍼. `noTenantNoticeKind()` 를 직접 resolve 한 뒤 `NoTenantNoticeBody` 로 위임. 모든 `page.tsx`/`DomainTenantGate` 게이트는 `{await NoTenantNotice({...})}` **함수 호출**로 내장한다(JSX 태그 `<NoTenantNotice/>` 로 중첩하면 반환 트리에 미해결 async 컴포넌트 참조가 남아 `@testing-library/react`/`renderToStaticMarkup` 로 렌더하는 기존 페이지 테스트가 전부 깨진다 — 실측 후 전환).

## 배선된 15곳

`widgets/domain-tenant-gate/DomainTenantGate.tsx`(도메인 레이아웃 6개: wms·scm·erp·finance·ledger·ecommerce 전부 커버) · `app/(console)/{operators,audit,accounts,tenants,tenants/[tenantId],subscriptions,permissions,permission-sets,partnerships,org-hierarchy,operator-groups,dashboards/health,dashboards/overview,dashboards}/page.tsx`.

**+1 (티켓 목록 밖, 직접 발견)**: `features/iam-overview/components/IamOverviewScreen.tsx`(`/iam` 개요) — 동일 문제(«테넌트를 먼저 선택해주세요», 다른 어미)를 가진 22번째 화면. 단, 이 컴포넌트는 **동기/프레젠테이션**이고 `@testing-library/react` 로 직접 렌더 테스트되므로 그 안에 async `NoTenantNotice` 를 못 얹는다 — `features/iam-overview/api/overview-state.ts` `getIamOverviewState()` 가 서버에서 `noTenantNoticeKind()` 를 미리 resolve 해 `IamOverviewState.tenantNoticeKind` 로 내려주고, 화면은 `NoTenantNoticeBody` 를 동기로 직접 호출한다 — 판정 함수도 문구도 여전히 한 곳. 부수효과: 이 화면의 문구가 «선택해주세요»→«선택하세요» 로 다른 19곳과 통일됨(의도적 — 톤 일관화).

## `/tenants` 판정

`/tenants` 는 SUPER_ADMIN 전용이지만, 선택 가능 0인 운영자는 애초에 그 역할 여부와 무관하게 noTenant 게이트에 먼저 막혀 화면에 도달 못 한다 → 동일한 공유 `NoTenantNotice` 를 그대로 적용(별도 문구 분기 없음). `/tenants/[tenantId]` 도 동일하되 `linkHref="/tenants"`(목록으로) 로 기존 동작 보존.

## 명시적 범위 제외 (판단 기록)

- `features/catalog/components/ServiceCatalog.tsx` `healthState==='no-tenant'` — 카탈로그 페이지 자체가 테넌트 **선택 화면**이고, 이 문구는 차단이 아니라 "선택하면 상태 점이 뜬다"는 부가 안내(TASK-MONO-711 ③). 게이트가 아니므로 미변경.
- `shared/api/errors.ts` `NO_ACTIVE_TENANT: '테넌트를 먼저 선택해주세요.'` — 클라이언트 뮤테이션 실패 토스트 매핑(페이지 진입 게이트와 다른 순간·다른 UX). 페이지 게이트를 통과한 뒤 레이스로 뮤테이션이 실패하는 예외적 경로라 이번 스코프 밖으로 유지.

## 테스트

- `tests/unit/active-tenant-default.test.ts` — `noTenantNoticeKind` 단위: 0개→zero · 1개→select · ≥2개→select · 등록 실패(throw)→select(Edge Case) · 등록 성공+빈 배열(진짜 0, throw 아님)→zero.
- `tests/unit/no-tenant-notice.test.tsx`(신규) — `NoTenantNoticeBody`(zero/select 각 문구·링크 유무·`linkHref`/`linkLabel` 오버라이드) + `NoTenantNotice`(비동기 위임) 렌더 테스트.
- 대표 3곳 이상 렌더 테스트(AC-1): `domain-tenant-gate.test.tsx`(도메인 6종 커버) · `operators-page-parallel.test.tsx` · `tenants-page.test.tsx` — 각각 zero/select 두 갈래 모두 같은 판정 함수(`noTenantNoticeKind`, 모듈 모킹으로 통제)를 타는지 확인. `IamOverviewScreen.test.tsx` 보너스(동기-prop 배선 경로).
- **bite**: `noTenantNoticeKind` 의 판정식을 일시적으로 `return 'select'` 로 고정 → `active-tenant-default.test.ts` 의 신규 zero 단정 2건이 정확히 실패(`expected 'select' to be 'zero'`) 확인 → 원복 → 재실행 green.

## 로컬 게이트 (전부 이 워크트리에서 직접 실행, rc 명시)

- `npx tsc --noEmit` → **rc=0**, 출력 0줄.
- `npx next lint` → **rc=0**, "No ESLint warnings or errors".
- `npx vitest run --minWorkers=2 --maxWorkers=2`(전체 스위트) → **rc=0**, **325 files / 3634 tests 전부 통과**.
- 타깃 스위트(위 신규/수정 10파일) 단독 재실행 → **rc=0**, 97/97.
- bite → 원복 후 재실행 → **rc=0**, 6/6(타깃 파일).

## e2e 영향

`tests/e2e/**` 그렙: `테넌트를 먼저 선택` / `no-tenant` / `noTenant` testid 를 **단언하는** e2e 스펙 0건(유일한 매치는 `fixtures/login.ts` 의 과거 결함을 설명하는 주석 하나, 단언 아님). e2e 계정은 전부 ≥1 테넌트 보유(zero 경로 미도달) + `'select'` 분기 문구는 byte-identical 이므로 **수정 없음**.

## 게이트/스크립트

`git add` 후 `check-index-queue-drift.sh` / `check-task-id-collision.sh` / `check-walkthrough-ledger-drift.sh` 통과 확인(PR 생성 직전 재확인 예정).

## § 창 판정 런북 (AC-2, OPEN — 다음 AMI 창)

1. `viewer@demo.com` 으로 로그인 → 스위처 없음(역할 0) 확인.
2. `/operators`·`/audit`·`/accounts`·`/tenants`·`/permissions`·`/permission-sets`·`/partnerships`·`/org-hierarchy`·`/operator-groups`·`/subscriptions`·`/dashboards`·`/dashboards/overview`·`/dashboards/health`·`/iam`·도메인 6종(`/wms`·`/scm`·`/erp`·`/finance`·`/ledger`·`/ecommerce`) 각각에서 새 안내(«이 계정에는 접근 가능한 테넌트가 없습니다.») 확인, 옛 문구(«테넌트를 먼저 선택하세요») 미노출 확인.
3. 대조군 `demo@demo.com` 로그인 → 스위처 존재 · 위 화면들 정상 렌더(회귀 없음) 확인.
4. 결과로 `TASK-MONO-730` AC-1 · `TASK-BE-597` AC-2 재판정.
