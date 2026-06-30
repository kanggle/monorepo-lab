# Task ID

TASK-PC-FE-152

# Title

console-web erp-ops `MasterWriteDialog.tsx`(383줄) behavior-preserving 분할 — config(`master-write-configs.ts`)는 기추출됨, 남은 config-driven 단일 폼이라 hook-only(`use-master-write.ts`)로 values/검증/buildBody/뮤테이션/onConfirm/에러매핑 추출(공개 type 표면·`useMasterWrite` 헬퍼는 컴포넌트에 잔존)

# Status

review

# Owner

frontend (Opus 4.8 분석·구현 — behavior-preserving 모듈 분할; contract/spec/backend 무변경)

# Task Tags

- code
- refactor
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-048(generic master write dialog — 4개 비-department 마스터 공유 다이얼로그) + `master-write-configs.ts`(필드 config 기추출). 본 task는 그 위에서 **렌더 트리·로직 동일**, 모듈 경계만 재편한다.
- **note (구조 판단)**: config 는 이미 `master-write-configs.ts`로 분리됨. 남은 383줄은 **단일 config-driven 폼**(하나의 `fields.map` 다형 입력 렌더)으로, 분리 가능한 *반복/재사용* 표현덩어리(별도 presentational 컴포넌트로 묶을 리스트 등)가 **없다** → **hook-only** 분할 적합(PC-FE-141 동형). 단, 공개 type 표면(`MasterFieldDef`/`MasterWriteController`/… 등 barrel 이 이 파일에서 export)과 `useMasterWrite` 헬퍼(다이얼로그 ReactNode 반환)는 컴포넌트 파일에 유지.

# Goal

`MasterWriteDialog.tsx`를 hook-only 로 behavior-preserving 분할한다. values/reason state, 검증(required/at-least-one/reason), `buildBody`(number 강제·paymentTerms nested·optional 생략), 뮤테이션 호출, `onConfirm`, `dynamicOptions`/`setField`/`fieldFilled`/`PAYMENT_METHODS`/`newIdemKey`/에러 매퍼를 `use-master-write.ts`로 옮기고, 컴포넌트는 공개 type·표현·`useMasterWrite` 헬퍼만 남긴다. 렌더 출력·DOM·모든 `data-testid`·ARIA·class·label·wire body·검증 술어는 byte-identical.

# Scope

## In Scope

- **신규 `src/features/erp-ops/components/use-master-write.ts`** (`'use client'`):
  - `PAYMENT_METHODS`(export) + `newIdemKey()`(내부) + `masterWriteErrorMessage(err)`(export; 기존 `errorMessage` 동일 코드맵).
  - `useMasterWriteForm(config, request, controller, onClose, optionSources)` — fields 선택, values/reason state, `setField`/`dynamicOptions`/`fieldFilled`, `requiredOk`/`canConfirm`/`destructive`/`title`, `buildBody`, `onConfirm`(mode 분기 + controller.create/update/retire + idem-key + `.then/.catch` 동등).
  - 타입은 컴포넌트(`./MasterWriteDialog`)에서 `import type` (런타임 순환 없음).
- **`src/features/erp-ops/components/MasterWriteDialog.tsx`** — 모든 공개 type/interface(`MasterFieldKind`/`MasterFieldDef`/`MasterWriteConfig`/`MasterOption`/`MasterWriteController`/`MasterWriteRequest`/`MasterWriteDialogProps`) 유지, 다이얼로그는 훅 소비로 재작성, `useMasterWrite` 헬퍼(request-state + dialog ReactNode) 잔존. barrel `index.ts` 경로·type 안정.

## Out of Scope

- master write API·hook·`master-write-configs.ts` 변경.
- create/update 무-X-Operator-Reason · retire-required-reason · paymentTerms nested 빌드 정책 변경.
- 다른 erp-ops 컴포넌트.

# Acceptance Criteria

- [x] config-driven 필드 렌더(text/number/date/select/payment-terms 다형), 동적 옵션(departments/jobGrades/costCenters), 필수표시·`— 선택/없음 —` placeholder 가 기존과 동일 렌더.
- [x] create: required gate, update: at-least-one gate, retire: reason gate — 술어·`buildBody`(number 강제·paymentTerms nested·optional 생략)·idem-key·`.then(close)/.catch(noop)` 불변.
- [x] producer 에러코드 inline 매핑(`masterWriteErrorMessage`) 무변경, 모든 `data-testid`(`${testid}-*`) 보존.
- [x] `npx tsc --noEmit` clean / `npx next lint` clean / `npx vitest run erp` green(MasterWriteDialog 포함, 무회귀). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8(generic master write) — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 동일 master write 계약·동일 controller·동일 호출.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/erp-ops/components/{MasterWriteDialog.tsx, use-master-write.ts(신규)}`. behavior-preserving hook-only 분할.

# Architecture

- Next.js App Router `'use client'` config-driven 다이얼로그의 hook-only 분할 패턴. 단일 `fields.map` 다형 폼은 별도 presentational 로 묶을 반복 덩어리가 없어 커스텀 훅으로 상태·로직만 추출하고 JSX 잔존. 공개 type 표면과 `useMasterWrite` 헬퍼는 컴포넌트 파일에 유지해 barrel export 안정. 훅↔컴포넌트는 `import type` 만 양방향 → 런타임 순환 없음.

# Edge Cases

- payment-terms 필드: `fieldFilled` 은 termDays+method 둘 다, `buildBody` 는 하나라도 있으면 nested 객체 — 둘의 비대칭 술어 verbatim 유지.
- number 필드 `Number(raw)` 강제·optional 빈 값 키 생략 동등.
- `optionSources` 기본값 `{}`(컴포넌트 default param) → 훅은 `NonNullable<...>` 시그니처로 수령, `dynamicOptions` 의 `?? []` 폴백 동등.

# Failure Scenarios

- 훅↔컴포넌트 type import 가 값 import 로 새면 런타임 순환 import → 훅은 `import type` 만 사용으로 가드, tsc/vitest 로 검증.
- `buildBody` 의 paymentTerms/number 분기 변형 시 wire 회귀 → 원본 verbatim 이동, vitest(create required-gate + retire reason)로 가드.
- 공개 type 을 훅으로 이동시켜 barrel export RED → 모든 공개 type 은 컴포넌트 파일에 잔존, tsc 로 검증.
- lint(no-unused-vars: `ApiError`/`useState` import 이동) / tsc RED → push 전 3종 게이트 필수.

# Definition of Done

- [x] `use-master-write.ts` 추출, `MasterWriteDialog.tsx` 훅 소비로 재작성(공개 type·`useMasterWrite` 헬퍼 잔존)
- [x] 렌더 출력·DOM·data-testid·ARIA·wire·검증 술어 behavior-preserving
- [x] vitest(erp/MasterWriteDialog) + tsc + lint green, 무회귀; scope = console-web only
- [x] 공개 export 표면(`MasterWriteDialog`/`useMasterWrite`/모든 type via feature barrel) 불변
- [x] Acceptance Criteria 충족
- [x] Ready for review
