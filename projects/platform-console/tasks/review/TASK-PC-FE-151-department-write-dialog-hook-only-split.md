# Task ID

TASK-PC-FE-151

# Title

console-web erp-ops `DepartmentWriteDialog.tsx`(415줄) behavior-preserving 분할 — 평평한 mode-conditional 폼이라 hook-only(`use-department-write.ts`)로 상태/검증/뮤테이션/onConfirm/에러매핑 추출(PC-FE-141 PromotionForm 패턴, prop-drilling 회피)

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

- **builds on**: TASK-PC-FE-046(department write PILOT 다이얼로그 — § 2.4.8) + TASK-PC-FE-047(부모 부서 picker). 본 task는 그 위에서 **렌더 트리·로직 동일**, 모듈 경계만 재편한다.
- **note (구조 판단)**: `DepartmentWriteDialog.tsx`(415줄)는 **단일 평평한 폼**으로, JSX 가 4개 mode(create/update/retire/move-parent)에 강하게 조건부다. 분리 가능한 *반복/재사용* 표현덩어리(리스트 테이블 등)가 **없다** → container/presentational 대신 **hook-only** 분할이 적합(PC-FE-141 PromotionForm 동형). value/setter 를 presentational 로 prop-drilling 하면 평평한 폼에서는 오히려 복잡도가 증가하므로 회피.

# Goal

`DepartmentWriteDialog.tsx`를 hook-only 로 behavior-preserving 분할한다. 모든 폼 state·검증 술어·부모옵션 파생·4개 뮤테이션·`onConfirm` 배선·`newIdemKey`·inline 에러 매퍼를 `use-department-write.ts`로 옮기고, 컴포넌트는 표현 전용으로 남긴다. 렌더 출력·DOM·모든 `data-testid`·ARIA·class·label·wire body·검증 술어는 byte-identical.

# Scope

## In Scope

- **신규 `src/features/erp-ops/components/use-department-write.ts`** (`'use client'`):
  - `DeptWriteMode` / `DeptWriteRequest` 타입(이동; 컴포넌트가 re-export).
  - `newIdemKey()`(파일 내부) + `departmentWriteErrorMessage(err)`(export; 기존 `errorMessage` 동일 코드맵).
  - `useDepartmentWrite(request, onClose, departments)` — create/update/retire/move-parent state, `parentOptions`/`moveParentOptions` 파생, 4개 뮤테이션 훅, `pending`/`error`/`title`/`destructive`/`reasonOk`/`canConfirm` 술어, `onConfirm`(mode 분기 + trimmed wire body + idem-key + onSuccess close).
- **`src/features/erp-ops/components/DepartmentWriteDialog.tsx`** — 훅 소비로 재작성, JSX 표현만 유지. `DepartmentWriteDialog`/`DepartmentWriteDialogProps`/`DeptWriteMode`/`DeptWriteRequest` 공개 export 유지(feature barrel `index.ts` 경로·타입 안정).

## Out of Scope

- department write API·hook(`use-erp-ops.ts`)·types(`types.ts`) 변경.
- create/update 무-reason · retire-required-reason 정책, mode-conditional 필드 가시성, idem-key 생성 변경.
- 다른 erp-ops 컴포넌트.

# Acceptance Criteria

- [x] 4개 mode(생성/수정/폐기/상위이동) 각각의 필드 가시성·label·필수표시·부모 picker(`<select>`, retired 제외; move-parent 는 target 자기 제외)가 기존과 동일 렌더.
- [x] create: code+name gate, retire: reason gate, move-parent: effectiveFrom gate — 술어·wire body·trimmed 처리·idem-key 모두 불변.
- [x] producer 에러코드 inline 매핑(`departmentWriteErrorMessage`) 무변경, 모든 `data-testid`(`erp-dept-*`) 보존.
- [x] `npx tsc --noEmit` clean / `npx next lint` clean / `npx vitest run erp` green(DepartmentWriteDialog 포함, 무회귀). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8(department write binding PILOT) — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. 동일 department write 계약·동일 클라이언트 훅·동일 호출.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/erp-ops/components/{DepartmentWriteDialog.tsx, use-department-write.ts(신규)}`. behavior-preserving hook-only 분할.

# Architecture

- Next.js App Router `'use client'` 다이얼로그의 hook-only 분할 패턴(PC-FE-141). 단일 평평한 mode-conditional 폼은 분리 가능한 반복 표현덩어리가 없어 container/presentational 대신 커스텀 훅으로 상태·로직만 추출하고 JSX 는 컴포넌트에 잔존 — value/setter prop-drilling 회피로 복잡도 증가 없이 god-file 만 해소.

# Edge Cases

- mode 별 분기(`mode === 'create' && target` 등) 가드는 훅 `onConfirm` 내부에 verbatim 유지 — target 부재 시 no-op 동일.
- `parentId`/`newParentId` 빈 문자열 → `null` 송신, optional `effectiveFrom`/`reason` 빈 값 → 키 생략 — buildBody 동등.
- `DeptWriteMode`/`DeptWriteRequest` 타입을 훅으로 옮기되 컴포넌트가 `export type` re-export 하여 barrel import 경로 불변.

# Failure Scenarios

- 타입을 훅으로만 옮기고 컴포넌트 re-export 누락 시 barrel(`index.ts`)의 `DeptWriteMode`/`DeptWriteRequest` export RED → 컴포넌트에서 `export type` re-export 로 가드, tsc 로 검증.
- 훅 추출 중 trim/`null`-coalesce 분기 변형 시 wire body 회귀 → 원본 `onConfirm` verbatim 이동, vitest(create/retire/parent-picker)로 가드.
- lint(no-unused-vars: `ApiError`/`isRetired` import 이동) / tsc RED → push 전 3종 게이트 필수.

# Definition of Done

- [x] `use-department-write.ts` 추출, `DepartmentWriteDialog.tsx` 훅 소비로 재작성(JSX 표현만)
- [x] 렌더 출력·DOM·data-testid·ARIA·wire·검증 술어 behavior-preserving
- [x] vitest(erp/DepartmentWriteDialog) + tsc + lint green, 무회귀; scope = console-web only
- [x] 공개 export 표면(`DepartmentWriteDialog`/Props/`DeptWriteMode`/`DeptWriteRequest` via feature barrel) 불변
- [x] Acceptance Criteria 충족
- [x] Ready for review
