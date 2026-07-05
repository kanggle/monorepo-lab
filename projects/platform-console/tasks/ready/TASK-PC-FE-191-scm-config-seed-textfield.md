# TASK-PC-FE-191 — SCM 설정 폼 공유 텍스트 필드 추출 (SeedTextField)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving field extraction
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (소규모 · 동작 불변)

---

## Goal

TASK-PC-FE-190 후속. `scm-config` 설정 폼의 인라인 텍스트 입력 블록을 공유 `SeedTextField`로 추출해 `SeedNumberField`(FE-190)와 대칭되는 seed-form 필드 패밀리를 완성한다. `SupplierMapForm`의 두 텍스트 입력 — **supplierId**(plain text)와 **currency**(uppercase + `<datalist>` + `maxLength=3` 코드 필드) — 이 각각 label+input+error 마크업을 인라인으로 반복하고 있다. 하나의 컨트롤드 컴포넌트로 통합한다. (PolicyForm 은 숫자 필드뿐이라 무변경.)

## Scope

**신규** `scm-config/components/SeedTextField.tsx`:
- 컨트롤드 텍스트 필드: label + `<input type="text">` + inline `role="alert"` 에러, `aria-invalid`/`aria-describedby` 배선. `SeedNumberField`와 동일한 props 형태(id/label/testid/value/error/onChange) + 코드 필드용 선택 props:
  - `options?: readonly string[]` — `<datalist>` 자동완성(currency=COMMON_CURRENCIES)
  - `maxLength?: number` — 최대 길이(currency=3)
  - `uppercase?: boolean` — 입력을 대문자로 변환(onChange 전) + `uppercase` 표시 클래스(currency)

**`SupplierMapForm` 재작성**:
- supplierId 인라인 블록 → `<SeedTextField ...>` (plain).
- currency 인라인 블록 + `<datalist>` → `<SeedTextField ... options={COMMON_CURRENCIES} maxLength={3} uppercase>`.
- 기존 testid(`map-supplierId`·`map-supplierId-error`·`map-currency`·`map-currency-error`)·label·검증·submit·404-empty·uppercase 동작 전부 보존.

**Out of scope:** PolicyForm·API·hook·proxy·producer·contract 무변경. 새 기능 0.

## Acceptance Criteria
- **AC-1** `SupplierMapForm`의 supplierId·currency 필드가 `SeedTextField`로 렌더되며 기존 마크업·testid·aria·검증·submit 동작 불변. `SeedConfigScreen.test.tsx` 무수정 통과.
- **AC-2** currency 는 여전히 대문자 변환 + `maxLength=3` + `COMMON_CURRENCIES` datalist 자동완성을 유지.
- **AC-3** `SeedTextField`는 순수 프레젠테이션 컨트롤드 컴포넌트(props 계약 명확) — `SeedNumberField`와 일관된 필드 패밀리.
- **AC-4** `tsc --noEmit` + `next lint` + `vitest run` green(회귀 0).

## Edge Cases / Failure Scenarios
- currency uppercase 변환을 `SeedTextField`(uppercase prop)로 옮겨도 부모 onChange 결과 동일해야 함(`v` 는 이미 대문자).
- testid/aria/문구는 화면 테스트가 pin — 순수 이동만.
- datalist id 는 `${id}-options`로 컴포넌트 내부에서 파생(현행과 동일).

## Related
- 선행: TASK-PC-FE-190 (`SeedNumberField` 추출) · TASK-PC-FE-189 (client dedup).
- 기존 테스트(계약): `tests/unit/SeedConfigScreen.test.tsx`.
- 방법론: [[project_console_web_godfile_split_series]].
