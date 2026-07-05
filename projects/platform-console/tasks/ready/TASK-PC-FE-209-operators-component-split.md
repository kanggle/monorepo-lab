# TASK-PC-FE-209 — IAM 운영자 관리 god-file 컴포넌트 분할 (operators)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

SCM(PC-FE-190)·WMS(PC-FE-197/198)·EC(PC-FE-199/200)와 동일한 접근으로, IAM `operators` 피처(콘솔에서 가장 크고 privilege-sensitive한 피처)의 god-file 컴포넌트를 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 프레젠테이션 조각으로 분할. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- `OperatorsScreen.tsx`(~514) · `OrgScopeDialog.tsx`(~380) · `OperatorProfileEditDialog.tsx`(~304) · `OperatorConfirmDialog.tsx`(~304) · `OperatorsTable.tsx`(~275)
- **폼(hook-only 판단 대상, PC-FE-196 선례)**: `CreateOperatorForm.tsx`(~345, **PC-FE-196에서 이미 useCreateOperatorForm 훅 추출됨** → 프레젠테이션 강제 분할 금지, 잔여 정리만) · `ChangePasswordForm.tsx`(~203) · `MyProfileForm.tsx`(~197)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration(state·검증·mutation 생명주기·reason-capture·idempotency-key·list-state 분기·페이지네이션/필터 state)을 유지하는 얇은 컨테이너로 축소. **폼은 per-file 판단**: 이미 훅 위임된 flat 폼(CreateOperatorForm 등)은 hook-only로 두고(프레젠테이션 추출 시 prop-drilling만 증가), 반복/재사용 덩어리가 있으면 필드 그룹만 추출. 모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처·barrel 공개 API 불변.

**다이얼로그 focus-trap 특별 주의**(PC-FE-198 OutboundCancelDialog 선례): OrgScopeDialog·OperatorProfileEditDialog·OperatorConfirmDialog의 open-reset/auto-focus effect·Escape/focus-trap 키핸들러·`dialogRef` 프레임은 컨테이너 잔류, presentational body만 추출(같은 `role="dialog"` div 하위 렌더로 querySelectorAll trap 불변).

**Out of scope:** `api/`·`hooks/`(신규 폼 훅 추출이 선택 구조일 때만, PC-FE-196 컨벤션)·proxy·producer·contract·테스트 무변경. 컴포넌트(+barrel re-export 경로)만.

## Acceptance Criteria
- **AC-1** 대상 god-file이 의미 있게 축소(폼은 hook-only로 정당하게 무변경 가능)되고, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿 포함)·aria(다이얼로그 `role`·`aria-labelledby`/`aria-modal`)·요소 순서 보존.
- **AC-3** privilege-sensitive 카피(특권 경고·break-glass·role 체크박스·draft wire shape[blank-password omit]) verbatim 보존.
- **AC-4** `index.ts` 공개 API 불변, `tsc --noEmit` 0 + `next lint` 0 + `vitest`(operators 전 스위트) green, 회귀 0.

## Edge Cases / Failure Scenarios
- **OperatorsScreen.test.tsx는 Windows 로컬 fetchMock env-flake 존재**([[env_console_web_local_verify_needs_lint]] 계열) — 로컬 단독 실행 시 간헐 실패, CI Linux가 권위. 분할 회귀와 구분할 것(clean origin/main에서도 동일 실패 = 무관).
- 다이얼로그 3종 focus-trap/Escape/auto-focus 동작 보존(위 Scope 특별 주의).
- 폼 hook-only 정당성: flat 폼 강제 분할 금지(PC-FE-196/200 선례).
- **동시 세션 조율**: `pc-fe-203-operator-register-label`(운영자 라벨 카피)이 operators 표면을 건드릴 수 있음 → 착수 시 rebase/충돌 확인.

## Related
- 미러: PC-FE-197/199 (컴포넌트 분할), PC-FE-196 (폼 hook-only 선례).
- 선행(선택): PC-FE-208(iam-gateway dedup) — 파일 disjoint(components vs api)라 독립 병렬 가능.
- 기존 테스트(계약): `tests/unit/{OperatorsScreen,OrgScope*,OperatorProfile*,OperatorConfirm*,operators-*,CreateOperatorForm,ChangePassword*,MyProfile*}.test.tsx`.
