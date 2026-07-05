# Task ID

TASK-PC-FE-196

# Title

console-web `operators/CreateOperatorForm` 로직 훅 추출 분할: 폼 상태·비밀번호 정책 검증·계정존재 pre-flight 프로브(debounce)·grantable-role 필터·confirm-gated submit 을 `useCreateOperatorForm` 훅으로 분리하고 컨테이너는 마크업 배선만 — render 출력·test-id·draft wire shape 불변(behavior-preserving)

# Status

ready

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 커스텀 훅 추출, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-004(operators 관리 슬라이스 도입) + TASK-PC-FE-179(dangling-operator pre-flight 계정존재 프로브) + feat/iam-grantable-roles-filter(role 체크박스 grantable pre-filter) + ADR-MONO-035 O2/TASK-BE-377(break-glass 비밀번호 OPTIONAL). 본 분할이 보존해야 하는 동작·test-id·프로브/필터/검증의 출처.
- **note (현 구조)**: [`CreateOperatorForm.tsx`](../../apps/console-web/src/features/operators/components/CreateOperatorForm.tsx)(431줄)는 단일 `'use client'` 컴포넌트가 (a) 6종 폼 상태(email/displayName/password/roles/tenant/touched), (b) 다항 검증 술어(emailOk/nameOk/tenantOk/pwOk/canSubmit·`pwError` useMemo·`grantsElevated`), (c) 계정존재 pre-flight 프로브(400ms debounce `useEffect` + AbortController + `AccountProbeState` + showDangling/showAbsentButPw/showExistsOk 파생), (d) grantable-role 필터(`renderableRoles` useMemo)·`toggleRole`, (e) confirm-gated `submit`, (f) 전체 마크업을 모두 보유한다.
- **note (분할 형태 = 훅-only)**: CreateOperatorForm 은 ProductForm 의 variant editor 나 ShippingsScreen 의 table 같은 **반복·분리 가능한 프레젠테이셔널 덩어리가 없는 평평한 단일 폼**이다(4 필드 + roles fieldset + advisory 메시지). 표현부를 별도 컴포넌트로 추출하면 value/setter 다수를 prop-drilling 하게 되어 재사용 이득 없이 간접 비용만 늘어난다. 따라서 PC-FE-141(`usePromotionForm`)/PC-FE-143(`useTemplateForm`)/PC-FE-112(`useOrgScopeForm`)와 동일하게 **로직 훅만** 추출하고 마크업은 컨테이너에 유지하는 것이 올바른 ROI 다.
- **note (배치 = operators/hooks/)**: operators feature 는 form-state 훅을 `hooks/`에 둔다(`use-org-scope-form.ts` 직계 선례). ecommerce-ops 의 components/ 배치와 달리 **operators 로컬 컨벤션을 따라 `hooks/use-create-operator-form.ts`**로 둔다.

# Goal

`CreateOperatorForm`의 상태·로직을 `useCreateOperatorForm` 커스텀 훅으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `useCreateOperatorForm({ onSubmitDraft, pending, grantableRoles, checkAccountExists })` — 6종 필드 상태, 검증 술어(email/name/tenant/pw·`pwError`·`canSubmit`·`grantsElevated`), 계정존재 debounce 프로브(`AccountProbeState`·400ms·AbortController·`*`(플랫폼) skip·fail-soft)·advisory 플래그(showDangling/showAbsentButPw/showExistsOk), `renderableRoles` grantable 필터, `toggleRole`, confirm-gated `handleSubmit`(blank password omit) 을 소유. 로직은 분할 전과 1:1 동일(검증 순서·probe 게이팅·draft body).
- `CreateOperatorForm` — 훅 호출 + 마크업 배선만 남는 컨테이너. `useId` 필드 id 5개는 뷰 관심사로 컨테이너 잔류.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·label/필수 표식·비밀번호 정책 안내문·프로브 advisory 3종·role 체크박스·특권 경고·submit 라벨은 전부 기존과 동일. `CreateOperatorFormProps` 공개 시그니처·직접 import 경로(`@/features/operators/components/CreateOperatorForm`, 배럴 미노출) 불변.

# Scope

## In Scope

- **신규 `src/features/operators/hooks/use-create-operator-form.ts`** — `CreateOperatorForm`에서 상태·검증·프로브·필터·핸들러를 그대로 이전. `AccountProbeState` 타입·`UseCreateOperatorFormArgs` 인터페이스 포함. 반환 객체(fields/setters/플래그/핸들러)로 컨테이너가 필요한 값/핸들러 노출.
- **`src/features/operators/components/CreateOperatorForm.tsx`** — 훅으로 슬림화. 남는 마크업(4 필드 + advisory 3종 + roles fieldset + 특권 경고 + serverError + submit)과 `useId` 5개 유지. `CreateOperatorFormProps` 공개 시그니처·`checkAccountExists` 기본값(`checkAccountExistsForTenant`) 불변.
- **Tests** — 기존 [`tests/unit/features/operators/CreateOperatorForm.test.tsx`](../../apps/console-web/tests/unit/features/operators/CreateOperatorForm.test.tsx)(grantable 필터 4 + 선택 비밀번호 2 + dangling 프로브 5 = 11 케이스)가 **무변경으로 green**이어야 한다. 직접 import 경로·시그니처 불변이므로 import-site(OperatorsScreen + 테스트) 수정 불필요.

## Out of Scope

- 표현부(필드 마크업·advisory·roles fieldset)의 별도 프레젠테이셔널 컴포넌트 추출 — 위 note(훅-only) 사유로 의도적 제외(반복·재사용 없는 평평한 폼).
- 검증 규칙·probe 게이팅·draft body·advisory 조건의 **동작 변경**(순수 이동만).
- `CreateOperatorForm` 공개 props/직접 import 표면 변경.
- 다른 operators 컴포넌트(OperatorsScreen/AssignOperatorForm/OrgScopeDialog 등) — 별건.
- 백엔드/계약/스펙/operators API 변경.

# Acceptance Criteria

- [ ] `useCreateOperatorForm` 추출 후 `CreateOperatorForm`은 훅+마크업 배선만 남고, 렌더 출력·DOM·모든 `data-testid`가 기존과 동일하다.
- [ ] grantable-role 필터: `grantableRoles` subset ⇒ 해당 체크박스만, `null`/부재 ⇒ 전체 `KNOWN_OPERATOR_ROLES`, `[]` ⇒ 빈 그룹(no crash) — 4 케이스 불변.
- [ ] 선택 비밀번호(ADR-MONO-035 O2): 무-비밀번호 submit 가능·draft `password` omit / 비-blank 은 정책 충족해야 submit — 2 케이스 불변.
- [ ] dangling pre-flight: absent+무pw ⇒ 경고, absent+pw ⇒ 완화 노트, exists ⇒ OIDC-ok, null ⇒ fail-soft 무표시, `*` ⇒ probe skip(no lookup) — 5 케이스 불변(400ms debounce·AbortController·probe 게이팅 동일).
- [ ] `canSubmit` 술어(emailOk && nameOk && tenantOk && pwOk && !pending)·`grantsElevated`(roles∋ELEVATED_ROLE)·특권 경고가 불변.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components / Layered-by-Feature — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리, feature-internal 훅 추가).
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3 운영자 생성 — read-only 소비, 변경 없음(동일 draft·동일 create 게이팅).

# Related Contracts

- 변경 없음. 동일 운영자 create draft(email/displayName/optional password/roles/tenantId)·동일 계정존재 프로브 BFF·동일 grantable-roles 필터.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/operators/components/CreateOperatorForm.tsx` + `hooks/use-create-operator-form.ts`. behavior-preserving 커스텀 훅 추출(훅-only, 표현부 컨테이너 유지, operators/hooks/ 로컬 컨벤션).

# Architecture

- React 커스텀 훅 추출 패턴(PC-FE 분할 시리즈 / PC-FE-112 `useOrgScopeForm`·PC-FE-141 `usePromotionForm`와 동일 계열). fat `'use client'` 폼 컴포넌트 → (1) 상태·검증·프로브·필터·submit 훅, (2) 얇은 배선 컨테이너. 평평한 비반복 폼이라 프레젠테이셔널 분리는 prop-drilling 비용만 키우므로 의도적으로 훅 경계만 도입. 상태 소유권은 훅(컨테이너 로컬)에 유지, 렌더 트리·RSC 경계 불변.

# Edge Cases

- 계정존재 프로브 debounce: eligible(emailOk && tenantOk && tenant≠`*`) 전환 시 400ms 타이머 + AbortController, cleanup 시 cancel/abort/clearTimeout — 훅 `useEffect` 그대로 이전(deps `[probeEligible, probeEmail, probeTenant, checkAccountExists]`).
- 플랫폼 sentinel `*`: probe 게이팅에서 제외(`probeTenant !== '*'`) → lookup 미호출·idle — 그대로 이전.
- advisory 조건 분기: showDangling(absent && pw==='')·showAbsentButPw(absent && pw!=='')·showExistsOk(exists) — 훅에서 동일 파생, unavailable/checking/idle 은 무표시(fail-soft).
- 선택 비밀번호: blank ⇒ draft 에서 `password` 키 omit(빈 문자열 전송 금지 — producer `@Size(min=10)`가 `""` 거부), 비-blank ⇒ 정책 검증 후 포함 — `handleSubmit` 그대로.
- grantable 필터 fallback: `null`(fetch 실패/미제공) ⇒ 전체 `KNOWN_OPERATOR_ROLES`(빈 목록 아님), `[]` ⇒ 빈 그룹 — `renderableRoles` useMemo 그대로.
- 필드 id: `useId` 5개(email/name/pw/tenant/roles)는 label htmlFor·aria 연결용 뷰 관심사 → 컨테이너 잔류(훅으로 이동 불필요).

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → `CreateOperatorForm.test.tsx` 11 케이스 RED 또는 e2e 셀렉터 깨짐: AC 로 test-id 전수 보존 가드, `vitest run`으로 회귀 확인.
- probe useEffect deps/cleanup 이 추출 중 바뀌면 debounce/abort 회귀(플랫폼 `*` 케이스 lookup 유출 또는 stale setState) → 순수 이동, AC 로 5 케이스 가드.
- `handleSubmit`의 blank-password omit 분기가 바뀌면 producer 가 `""` 로 `@Size` 거부 → 분기 verbatim 이전.
- `canSubmit` 술어 경계가 바뀌면 submit 활성 조건 회귀 → 술어 순서·경계 그대로 이전.
- 잔여 미사용 import(useEffect/useMemo/AbortSignal 타입 등 컨테이너에서 제거) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC).

# Definition of Done

- [ ] `use-create-operator-form.ts`(상태·검증·프로브·필터·submit) 추출, `CreateOperatorForm.tsx`는 배선만
- [ ] 렌더 출력·DOM·전체 `data-testid`·grantable 필터·선택 비밀번호·dangling 프로브·canSubmit·특권 경고 behavior-preserving
- [ ] `CreateOperatorForm.test.tsx` 11 케이스 무변경 green + 전체 vitest + tsc + lint clean, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
