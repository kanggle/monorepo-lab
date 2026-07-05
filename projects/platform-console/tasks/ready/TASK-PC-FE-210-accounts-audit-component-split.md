# TASK-PC-FE-210 — IAM 계정·감사 god-file 컴포넌트 분할 (accounts · audit)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

IAM operators 분할(PC-FE-209)에 이어 `accounts`·`audit` 두 IAM 피처의 god-file 컴포넌트를 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 프레젠테이션 조각으로 분할. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- accounts: `AccountsScreen.tsx`(~323) · `ConfirmActionDialog.tsx`(~234) · `AccountsTable.tsx`(~169, 경계)
- audit: `AuditScreen.tsx`(~341) · `AuditRowCells.tsx`(~139, 이미 조각) · `AuditTable.tsx`(~112, 경계)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration(검색/필터 state·mutation 생명주기·reason-capture·bulk 작업·list-state 분기·페이지네이션)을 유지하는 얇은 컨테이너로 축소. `AccountsScreen`은 검색바·계정 상세·bulk-lock affordance·결과 테이블을, `AuditScreen`은 필터바·감사 로그 테이블·상세를 분리 후보로 본다. `ConfirmActionDialog`(계정 lock/unlock/gdpr/revoke reason-capture)는 focus-trap/reason state 컨테이너 잔류·presentational body만 추출(PC-FE-198 선례). 이미 작은 `AuditRowCells`/`AuditTable`/`AccountsTable`은 경계 — 추가 분할이 순 이득일 때만.

모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처·barrel 공개 API 불변.

**Out of scope:** `api/`·`hooks/`·proxy·producer·contract·테스트 무변경. 컴포넌트(+barrel re-export 경로)만.

## Acceptance Criteria
- **AC-1** 대상 god-file(주로 AccountsScreen·AuditScreen·ConfirmActionDialog)이 의미 있게 축소되고, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿 포함)·aria·요소 순서 보존.
- **AC-3** 계정 변이 reason-capture·PII/GDPR 카피·audit 컬럼 순서·상태 배지 verbatim 보존.
- **AC-4** `index.ts` 공개 API 불변, `tsc --noEmit` 0 + `next lint` 0 + `vitest`(accounts·audit 전 스위트) green, 회귀 0.

## Edge Cases / Failure Scenarios
- **ConfirmActionDialog focus-trap/reason 검증 보존**: reason state·open-reset/auto-focus·Escape/trap·`dialogRef`는 컨테이너 잔류(같은 `role="dialog"` div 하위 body 렌더).
- 경계 파일(AuditRowCells 139·AuditTable 112·AccountsTable 169)은 억지 분할 금지 — prop-drilling만 늘면 skip.
- 계정 PII/마스킹·GDPR-delete 확인 흐름 카피 verbatim.

## Related
- 미러: PC-FE-197/199/209 (컴포넌트 분할).
- 선행(선택): PC-FE-208(iam-gateway dedup) — 파일 disjoint 독립 병렬.
- 기존 테스트(계약): `tests/unit/{AccountsScreen,accounts-*,ConfirmActionDialog,AuditScreen,audit-*}.test.tsx`.
