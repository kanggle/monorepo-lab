# Task ID

TASK-PC-FE-376

# Title

샘플 방문자가 «테넌트 생성» 메뉴를 누르면 말없이 개요로 튕긴다 — `/` 리다이렉트 대신 «샘플에서는 만들 수 없습니다 · 로그인하면 사용할 수 있습니다» 안내 화면

# Status

ready

# Owner

frontend

# Task Tags

- code
- console
- sample
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (페이지 분기 1곳 + 정적 안내 컴포넌트 + 계약 문장 1줄 + 테스트)
> Drafted 2026-10-26 UTC. 출처: 소유자 질문 «샘플 콘솔에서 생성 메뉴 클릭 시 개요로 넘어가는 게 의도냐» → 소유자 결정 2026-10-26: **메뉴는 두고 안내 화면을 보여 준다**(메뉴 숨김 안 채택).

# Dependency Markers

- 선행 없음. 관련: `TASK-MONO-773` S6(«테넌트 생성» 단일 화면 · 샘플 → `/` 규칙의 출처), `ADR-MONO-074`(샘플 방문자).

# Goal

샘플 방문자가 사이드바 «테넌트 생성»(`/new-tenant`)을 누르면 개요로 튕기지 않고, 같은 주소에서 «샘플 콘솔에서는 테넌트를 만들 수 없습니다. 로그인하면 이 화면에서 만들 수 있습니다» 안내와 로그인 버튼을 본다. 로그인하면 `/new-tenant` 로 돌아온다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인):**
  - `src/app/(console)/new-tenant/page.tsx:39` `if (await isSampleVisitor()) redirect('/');` — 샘플 방문자의 `/` 는 개요.
  - 계약 `specs/contracts/console-integration-contract.md` § 2.6.4 «Sample mode»(`:4407`): «a sample visitor (branch 1) asking for `/new-tenant` gets `/` (the sample shell has no tenant creation); `/new-tenant…` is rejected as a § 2.6.2 demo-ended target.»
  - 메뉴 `src/shared/ui/console-nav-config.ts:147` 는 방문자 종류와 무관하게 «테넌트 생성» 을 보인다 — 메뉴 노출은 아무 문서도 정하지 않았다.
  - 생성 화면이 샘플에서 못 도는 이유: 자기 모드는 IAM access 쿠키, SUPER_ADMIN 모드는 운영자 토큰이 필요한데 샘플 방문자는 둘 다 없다(`tests/unit/sample-fetch-allowlist.test.ts:140`).
  - 로그인 목적지: `shared/lib/login-redirect.ts` `buildLoginRedirectFor('/new-tenant')` → `/login?redirect=%2Fnew-tenant`(셸 배너 «로그인» 버튼과 같은 함수).
  - 샘플 커버리지 `shared/sample/coverage.ts:235` `'/new-tenant': 'pending'` — `pending` 이면 셸이 «이 화면의 샘플 데이터는 준비 중입니다» 를 얹는다. 안내 화면은 백엔드 호출이 없으므로 가이드들과 같은 `'static'` 이 맞다.
- **1. 계약 먼저 (같은 PR 첫 커밋):** § 2.6.4 «Sample mode» 의 «gets `/`» 를 «gets a static notice on `/new-tenant` itself (no network call): the sample shell cannot create a tenant, and a 로그인 link `/login?redirect=%2Fnew-tenant` returns there after sign-in» 로 바꾼다. demo-ended 대상 거절 문장은 그대로.
- **2.** `new-tenant/page.tsx` 의 샘플 분기를 `redirect('/')` → 정적 안내 컴포넌트 렌더로. 페이지 제목 «테넌트 생성» 은 다른 모드와 같게, 본문은 안내 문구 + 로그인 링크(`buildLoginRedirectFor('/new-tenant')`) + «개요로 돌아가기» 링크. 네트워크 호출 0.
- **3.** `coverage.ts` `'/new-tenant'` → `'static'`, 주석 갱신.
- **4.** `tests/unit/new-tenant-page.test.tsx` 의 «🔴 /new-tenant → redirect /» 를 «안내 렌더 · 로그인 href · fetch 0회 · 생성 양식 미마운트» 로 바꾼다. 페이지 주석(`:36`)·관련 주석(`coverage.ts`, `sample-fetch-allowlist.test.ts:140` 의 why 문구) 정리.

## Out of Scope

- 메뉴 숨김(소유자가 채택 안 함).
- 샘플에서 `pending` 인 다른 생성 화면(`/fan/artists/new` 등) — 그쪽은 셸의 «준비 중» 안내가 이미 뜬다.
- 비운영자·운영자 모드 동작.

# Acceptance Criteria

- **AC-1** 샘플 방문자가 `/new-tenant` 를 열면 리다이렉트 없이 안내 화면이 렌더되고, 생성 양식(`CreateOrganizationForm`·`TenantCreateScreen`)·받은 초대는 마운트되지 않으며 `fetch` 는 0회다.
- **AC-2** 안내 화면의 로그인 링크 href 가 `/login?redirect=%2Fnew-tenant` 이다.
- **AC-3** 샘플 방문자 화면에 «샘플 데이터는 준비 중입니다» 셸 안내가 겹쳐 뜨지 않는다(`'static'`).
- **AC-4** 계약 § 2.6.4 «Sample mode» 가 새 동작을 적고, 코드 주석이 «→ `/`» 를 더 말하지 않는다.
- **AC-5** console-web `lint` · `tsc` · vitest(대상 + `sample-coverage-ledger` · `SampleScreenNotice` · `permission-map-drift`) 초록, CI 프런트 단위·E2E smoke 초록.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6.4.

# Related Contracts

- 위와 같음(콘솔 내부 계약 문장 변경, 외부 API 변경 없음).

# Edge Cases

- 샘플 방문자가 `/onboarding` 옛 주소로 오면 미들웨어가 `/new-tenant` 로 보낸다 → 같은 안내를 본다(추가 작업 없음).
- 데모 종료(§ 2.6.2) 대상 거절 규칙은 바뀌지 않는다.

# Failure Scenarios

- 커버리지를 `pending` 으로 두면 안내 위에 «준비 중» 이 겹친다 → AC-3.
- 로그인 링크를 손으로 조립하면 셸의 로그인 규칙과 갈라진다 → 같은 함수 사용.
