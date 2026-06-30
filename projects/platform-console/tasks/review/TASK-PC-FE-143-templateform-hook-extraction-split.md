# Task ID

TASK-PC-FE-143

# Title

console-web `ecommerce-ops/TemplateForm` 로직 훅 추출 분할: 알림 템플릿 폼 상태·검증·confirm-gated submit·create/update 뮤테이션을 `useTemplateForm` 훅으로 분리하고 컨테이너는 마크업 배선만 — render 출력·test-id·wire shape 불변(behavior-preserving)

# Status

review

# Owner

frontend (Opus 4.8 분석 / 구현 권장=Sonnet 4.6 또는 Opus — behavior-preserving 커스텀 훅 추출, contract/spec/backend 무변경)

# Task Tags

- code
- test
- refactor

---

# Dependency Markers

- **builds on**: TASK-PC-FE-089(TemplateForm 도입 — ADR-031 Phase 5b 알림 템플릿 등록/수정 폼) + TASK-PC-FE-125(notification template 에러 메시지 매핑). 본 분할이 보존해야 하는 동작·test-id의 출처.
- **note (현 구조)**: [`TemplateForm.tsx`](../../apps/console-web/src/features/ecommerce-ops/components/TemplateForm.tsx)(277줄)는 단일 `'use client'` 컴포넌트가 (a) 4종 폼 상태(type/channel/subject/body), (b) 검증(`formValid` — edit 시 type/channel immutable 분기), (c) create/update 뮤테이션(producer는 type+channel 생성 후 불변 → update는 subject+body만 PUT) + 에러 매핑, (d) 전체 마크업(type/channel select 또는 edit-readonly 분기 + subject + body + ConfirmDialog)을 모두 보유한다.
- **note (분할 형태 = 훅-only)**: TemplateForm은 PromotionForm(PC-FE-141)과 같이 **반복·분리 가능한 프레젠테이셔널 덩어리가 없는 평평한 단일 폼**이다(type/channel의 create-vs-edit readonly 분기는 재사용 덩어리가 아님). 표현부를 별도 컴포넌트로 추출하면 value/setter를 prop-drilling 하게 되어 재사용 이득 없이 간접 비용만 늘어난다. 따라서 PC-FE-106(`useLedgerOpsState`) / PC-FE-141(`usePromotionForm`)과 동일하게 **로직 훅만** 추출하고 마크업은 컨테이너에 유지하는 것이 올바른 ROI다.

# Goal

`TemplateForm`의 상태·로직을 `useTemplateForm` 커스텀 훅으로 분리해 가독성·테스트 용이성을 높인다. 동작은 완전 불변:

- `useTemplateForm(existing)` — 4종 필드 상태, 검증(`formValid`: subject/body non-empty + create 모드에서만 type/channel enum 검사), confirm-gated `onSubmit`/`confirmSubmit`/`cancelConfirm`, create/update 뮤테이션(update는 subject+body만 PUT — type/channel immutable)과 에러 매핑을 소유. 로직은 분할 전과 1:1 동일(검증 술어·wire body·라우팅 포함).
- `TemplateForm` — 훅 호출 + 마크업 배선만 남는 컨테이너.

behavior-preserving: 렌더 출력·DOM 구조·**모든 `data-testid`**·label/필수 표식·edit 모드 type/channel readonly 표시(`template-form-type-readonly`/`-channel-readonly` + "변경 불가" 안내 + `TEMPLATE_TYPE_LABELS` 표시)·create vs update 분기·검증 규칙·confirm 다이얼로그·PUT/POST wire body는 전부 기존과 동일.

# Scope

## In Scope

- **신규 `src/features/ecommerce-ops/components/use-template-form.ts`** — `TemplateForm`에서 상태·검증·핸들러·뮤테이션·에러 매핑을 그대로 이전. 반환 객체(ids/fields/플래그/핸들러)로 컨테이너가 필요한 값/핸들러 노출.
- **`src/features/ecommerce-ops/components/TemplateForm.tsx`** — 훅으로 슬림화. 남는 마크업(type/channel select-or-readonly 분기 + subject + body + 에러 alert + submit/cancel + ConfirmDialog)과 `inputCls`/`labelCls`/`readonlyInputCls` 스타일 상수 유지. `TemplateFormProps` 공개 시그니처 불변.
- **Tests** — 기존 [`tests/unit/error-messages-notification-template.test.ts`](../../apps/console-web/tests/unit/error-messages-notification-template.test.ts) 및 `tests/unit/**` 전체가 **무변경으로 green**이어야 한다. barrel `@/features/ecommerce-ops`의 `TemplateForm` export 경로·시그니처 불변이므로 import-site 수정 불필요.

## Out of Scope

- 표현부(필드 마크업)의 별도 프레젠테이셔널 컴포넌트 추출 — 위 note(훅-only) 사유로 의도적 제외(반복·재사용 없는 평평한 폼).
- 검증 규칙·wire body·에러 코드 매핑의 **동작 변경**(순수 이동만).
- type/channel immutable 정책 변경(producer 계약) — 동작 불변.
- `TemplateForm` 공개 props/배럴 export 표면 변경.
- 다른 ecommerce-ops 폼(ProductForm[PC-FE-139]/PromotionForm[PC-FE-141] 등) — 별건.
- 백엔드/계약/스펙/notification API 변경.

# Acceptance Criteria

- [ ] `useTemplateForm` 추출 후 `TemplateForm`은 훅+마크업 배선만 남고, create/update 양 모드의 렌더 출력·DOM·모든 `data-testid`가 기존과 동일하다.
- [ ] create 모드: type/channel select + subject + body 입력, 검증(subject/body non-empty + type/channel enum), 성공 시 `/ecommerce/notifications/templates` 라우팅·refresh 동작이 불변.
- [ ] update 모드: type/channel readonly 표시("변경 불가" + `TEMPLATE_TYPE_LABELS`), subject+body만 PUT, 성공 시 목록 라우팅·refresh가 불변. 검증은 subject/body만(type/channel enum 검사 skip — `isEdit` 단락).
- [ ] confirm-gated submit(다이얼로그 open → confirm 시 mutate, cancel 시 에러 리셋)·인라인 에러 alert(`template-form-error`)·409 TEMPLATE_ALREADY_EXISTS 인라인 표시 동작 불변.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean(no-unused-vars 등 CI 두 프런트 잡 가드 — `env_console_web_local_verify_needs_lint`). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 알림 템플릿(ADR-031 Phase 5b) — read-only 소비, 변경 없음(동일 호출·동일 wire shape, update=subject+body PUT).
- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components — 소비만, 변경 없음(클라이언트 컴포넌트 내부 구조 정리).

# Related Contracts

- 변경 없음. 동일 템플릿 create/update API·동일 클라이언트 훅(`useCreateTemplate`/`useUpdateTemplate`)·동일 POST/PUT body.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/ecommerce-ops/components/TemplateForm.tsx` + `use-template-form.ts`. behavior-preserving 커스텀 훅 추출(훅-only, 표현부 컨테이너 유지).

# Architecture

- React 커스텀 훅 추출 패턴(PC-FE 분할 시리즈 / PC-FE-106 `useLedgerOpsState` / PC-FE-141 `usePromotionForm`과 동일 계열). fat `'use client'` 폼 컴포넌트 → (1) 상태·검증·submit·뮤테이션 훅, (2) 얇은 배선 컨테이너. 평평한 비반복 폼이라 프레젠테이셔널 분리는 prop-drilling 비용만 키우므로 의도적으로 훅 경계만 도입. 상태 소유권은 훅(컨테이너 로컬)에 유지, 렌더 트리·RSC 경계 불변.

# Edge Cases

- create vs update: update는 `existing!.templateId`로 subject+body PUT + 목록 라우팅, create는 type/channel 포함 POST + 목록 라우팅 — 훅 `confirmSubmit` 분기 그대로 이동.
- type/channel immutable: edit 모드에서 select 대신 readonly div 렌더("변경 불가" 안내, `TEMPLATE_TYPE_LABELS[type]` 또는 raw) — 컨테이너 마크업 분기 유지.
- 검증 단락: `formValid`의 type/channel enum 검사는 `isEdit ||` 로 create 모드에서만 평가 — 훅 술어 그대로 이동(edit는 subject/body만).
- 초기값: edit 진입 시 existing.type/channel/subject/body, create 시 enum 첫 값 + 빈 문자열 — 훅 `useState` 초기화 그대로.
- 409 TEMPLATE_ALREADY_EXISTS: `handleError`가 코드 매핑하여 인라인 표시 — 훅 그대로.

# Failure Scenarios

- 추출 과정에서 test-id 오타/누락 → 기존 테스트/e2e 셀렉터 깨짐: AC로 test-id 전수 보존 가드, `vitest run`으로 회귀 확인.
- 검증 단락(`isEdit ||`)이 바뀌면 edit 모드에서 type/channel 미설정으로 false가 되어 저장 불가 회귀 → 술어 그대로 이동, AC(update)로 가드.
- update wire body에 type/channel이 섞이면 producer immutable 계약 위반 → `UpdateTemplateBody`는 subject+body만, 순수 이동.
- 상태를 (가상의) 표현 컴포넌트로 내리면 입력 시 리셋 → 훅-only 분할로 상태는 컨테이너 로컬 훅에 유지(표현 분리 안 함).
- 잔여 미사용 import(useId/useState/useRouter/ApiError 등 컨테이너에서 제거) → `pnpm lint` no-unused-vars RED: push 전 lint+tsc 필수(가드 AC; PC-FE-142에서 동일 클래스 1건 적발 선례).

# Definition of Done

- [ ] `use-template-form.ts`(상태·검증·submit·뮤테이션) 추출, `TemplateForm.tsx`는 배선만
- [ ] 렌더 출력·DOM·전체 `data-testid`·create/update 분기·type/channel readonly·검증 단락·confirm·wire body behavior-preserving
- [ ] `error-messages-notification-template` + 전체 vitest + tsc + lint clean, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
