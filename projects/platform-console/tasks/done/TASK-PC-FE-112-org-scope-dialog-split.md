# TASK-PC-FE-112 — operators `OrgScopeDialog.tsx` god-component 분할 (fat container → 커스텀훅)

- **Status**: done
- **Project**: platform-console
- **App**: console-web (Next.js, operators 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (behavior-preserving — PC-FE-106 fat-container→custom-hook 패턴)

## Goal

operators `components/OrgScopeDialog.tsx`(522줄) fat god-component 의 **폼 로직(tri-state 상태·assignment 시드 effect·degrade-aware id 파생·submit mutation, ~140줄)을 `hooks/use-org-scope-form.ts` 커스텀훅으로 추출**. 컴포넌트는 view 관심사(useId·focus container·Escape-to-close)+JSX 만 갖는 presentational shell 로 슬림화. **behavior-preserving** — 외부 동작·data-testid·tri-state(null≠[]) 의미·reason gate 전부 동일 → `OrgScopeDialog.test.tsx`(컴포넌트 동작) 무수정 통과. PC-FE-106(use-erp-ops fat container→훅) 플레이북 재적용.

## Scope

**In scope** (console-web only, operators 피처):

1. `hooks/use-org-scope-form.ts` 신설 — `useOrgScopeForm({ open, operatorId, onClose })`:
   - useOperatorAssignments/useOrgScopeDepartments/useSetOperatorOrgScope 구독 + departments/activeDepartments/assignment/currentScope 파생.
   - tri-state useState(mode/selected/manual/reason/blockConfirmed) + open/currentScope 반응 시드 effect.
   - subsetIds(degrade=manual parse / else selected)·payload(null/[]/[ids])·subsetEmpty·blockNotConfirmed·canSubmit·submitError·currentSummary 파생.
   - toggleDept·submit(mutate + onSuccess onClose) 핸들러. parseManualIds 로컬.
   - `ScopeMode` 타입 export.
2. `components/OrgScopeDialog.tsx` → presentational shell: useId×4·useRef·Escape effect + `useOrgScopeForm` 소비 + JSX(f.* 참조). props 표면(OrgScopeDialogProps) 불변.

**Out of scope**: 동작/data-testid/계약 변경 일체, 신규 기능.

## Acceptance Criteria

- **AC-1 — behavior-preserving.** tri-state(전체 null / 차단 [] / subtree [ids]) 의미·시드 reactive effect·degrade 수동입력 파생·reason gate·canSubmit 조건·submit mutate+onClose·submitError 매핑·전 data-testid 원본과 동일.
- **AC-2 — 표면 안정.** `OrgScopeDialog` props/export 불변(feature index barrel 무변경). 훅은 feature-internal(컴포넌트가 직접 import). 호출처(OperatorsScreen) 무변경.
- **AC-3 — 분할.** 원 522줄 → presentational shell + 커스텀훅. hook-order 안정(컴포넌트가 useOrgScopeForm 무조건 호출 후 early-return).
- **AC-4 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(`OrgScopeDialog.test.tsx` 무수정 통과 = behavior-preserving 증명).

## Related Specs

- `projects/platform-console/tasks/done/` PC-FE-106(use-erp-ops fat container→hook 추출) — 동형 플레이북
- TASK-PC-FE-050 — OrgScopeDialog 원 구현(org_scope 설정 표면)

## Related Contracts

- 없음(순수 내부 구조 리팩토링 — IAM operators org-scope 계약 소비 코드 무변경).

## Edge Cases

- hook-order: 컴포넌트가 useId×4→useRef→useOrgScopeForm→useEffect 순으로 무조건 호출 후 `if(!open) return null` → open 토글에도 hook 순서 불변(원본도 동일 구조).
- currentSummary 는 훅이 항상 계산(원본은 early-return 뒤 계산했으나 closed 시 컴포넌트가 null 반환 전이라 무영향).
- 단일파일 소스 가드 없음(테스트는 컴포넌트 렌더 동작 테스트).

## Failure Scenarios

- 없음(런타임 동작 무변경). 회귀는 `OrgScopeDialog.test.tsx` + 전체 vitest 가 검출.
