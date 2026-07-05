# Task ID

TASK-PC-FE-206

# Title

console-web `/onboarding` `CreateOrganizationForm` 에 "무엇이 생기고 다음 단계" 정보 패널 추가 — 조직 생성 시 얻는 것(테넌트+관리자)·confinement·구독 0으로 태어남·다음 단계 안내 (신규 사용자 오리엔테이션, additive)

# Status

ready

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 또는 Opus — 정적 정보 패널 additive + 테스트, 백엔드/계약/동작 무변경)

# Task Tags

- code
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-182(self-service onboarding UI 도입 — `CreateOrganizationForm`/`/onboarding` 페이지) + TASK-BE-483(repo 가이드 § 6 "② 만드는 법" — 개념 SoT) + ADR-MONO-044(온보딩 트랜잭션 D1/D2/D6).
- **note (갭)**: `/onboarding` 페이지는 "새 조직을 만들면 그 조직의 관리자가 된다"만 안내하고, **무엇이 정확히 생기는지(테넌트+TENANT_ADMIN/BILLING_ADMIN)·confinement(내가 만든 조직에만)·구독 0으로 태어남·다음 단계(구독 켜기)**가 없다. 신규 사용자는 조직 생성 후 "왜 운영 화면이 비어있지?"에 부딪힘(구독 0). 정보 패널로 사전 안내.

# Goal

`CreateOrganizationForm`에 **"무엇이 생기고 다음 단계"** 정적 정보 패널(`onboarding-what-happens`)을 폼 입력 위에 additive 로 추가한다:

- **생기는 것**: 새 조직(테넌트) + 당신이 그 조직의 **관리자(TENANT_ADMIN · 구독 관리자)**.
- **confinement**: 이 권한은 **방금 만든 조직에만** 적용(다른 조직 접근 불가) — ADR-044 D2.
- **구독 0으로 태어남**: 조직은 도메인 구독 없이 시작 → 콘솔 입장 후 **구독** 화면에서 WMS·이커머스 등을 직접 켜야 운영 화면이 열림 — ADR-044 D6.
- **다음 단계**: 조직 생성 → 도메인 구독 켜기 → 운영자 초대·도메인 운영.

기존 폼 동작(검증·submit·outcome 네비게이션·에러)·testid 전부 불변, 순수 additive 정보 패널.

# Scope

## In Scope

- **`src/features/onboarding/components/CreateOrganizationForm.tsx`** — return 을 `<div className="space-y-6">{info panel}{form}</div>` 로 감싸 정보 패널(`<section data-testid="onboarding-what-happens">` — 3~4 항목 리스트 + 다음 단계) 추가. 폼 요소(입력/검증/submit/에러 testid) 전부 불변.
- **Tests** — `tests/unit/features/onboarding/CreateOrganizationForm.test.tsx` 에 패널 렌더 테스트 추가(핵심 문구·다음 단계). 기존 5 테스트(검증 게이트·201 ready/not-ready·409·503) 무회귀.

## Out of Scope

- 폼 동작(검증 규칙·submit·outcome 네비게이션·에러 매핑) 변경.
- `/onboarding` 페이지(`page.tsx`) 헤더 카피 변경(폼 컴포넌트에만 additive; 페이지 intro 유지).
- 온보딩 클라이언트/프록시/백엔드/계약 변경.
- repo 가이드 § 6 "② 만드는 법"(BE-483, 별건).

# Acceptance Criteria

- [ ] **AC-1** `CreateOrganizationForm` 에 `onboarding-what-happens` 정보 패널 렌더 — 생기는 것(테넌트+관리자)·confinement(내가 만든 조직에만)·구독 0으로 태어남·다음 단계 포함.
- [ ] **AC-2** 기존 폼 동작·testid(`onboarding-tenant-id`/`-error`/`onboarding-org-name`/`onboarding-error`/`onboarding-submit`) 전부 불변, 검증 게이트·201 ready/not-ready·409·503 5 테스트 무회귀.
- [ ] **AC-3** 패널은 정적(입력/네비게이션 없음), a11y 무해(section+aria-label 또는 heading).
- [ ] **AC-4** `pnpm exec vitest run` green, `npx tsc --noEmit` clean, `pnpm lint` clean. scope = console-web only. 백엔드/계약 0.

# Related Specs

- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` (D1 생성 내용 · D2 confinement · D6 entitlement-empty — 패널 문구 근거).
- `projects/iam-platform/docs/guides/operator-auth-token-model.md` § 6 "② 만드는 법"(BE-483, 개념 SoT).
- `projects/iam-platform/specs/contracts/http/onboarding-api.md` (온보딩 계약 — 소비만, 변경 없음).

# Related Contracts

- 변경 없음(정적 정보 패널 additive).

# Target Service

- `platform-console` / `apps/console-web` — `src/features/onboarding/components/CreateOrganizationForm.tsx`. additive 정적 정보 패널 + 테스트.

# Architecture

- 정적 UI additive — 기존 `'use client'` 폼 컴포넌트 return 을 wrapper div 로 감싸 정보 패널 + 폼. 상태/동작 무변경(순수 표현 추가). 백엔드/계약 무관.

# Edge Cases

- 정보 패널은 순수 정적(상태·핸들러 없음) → 폼 상태(pending/error)와 독립, 리렌더 영향 0.
- 기존 폼 동작 계약: 패널 추가로 DOM 구조가 폼 바깥에 한 겹 늘지만 폼 요소 testid·검증·submit 은 불변 → 5 기존 테스트 셀렉터 무영향.
- a11y: 패널을 `<section aria-label>` 또는 heading 으로 랜드마크화, 폼 label/aria 무영향.

# Failure Scenarios

- 패널 추가가 폼 요소 testid/구조를 건드리면 기존 5 테스트 RED → additive-only, wrapper 만 추가(AC-2).
- 잔여 미사용 import 등 → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(AC-4).
- 패널 문구가 ADR-044 와 어긋남(예: 구독 자동 활성으로 오해) → D6 entitlement-empty("직접 켜야")·D2 confinement 에 맞춰 서술.

# Definition of Done

- [ ] `onboarding-what-happens` 정보 패널 추가(생기는 것·confinement·구독 0·다음 단계), 폼 동작·testid 불변
- [ ] 패널 렌더 테스트 추가 + 기존 5 테스트 무회귀, tsc+lint+vitest clean; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
