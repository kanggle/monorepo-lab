# TASK-PC-FE-235 — `ApprovalDetail.tsx` hook-only split (erp-ops)

**Status:** done
**Area:** platform-console / console-web · **Target:** `src/features/erp-ops/components/ApprovalDetail.tsx` (~450줄) → `+ use-approval-detail.ts` hook
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (refactoring-engineer 위임) — 기계적 hook 추출, 행동/DOM/testid 불변 · **검증:** Opus 재검증.
**Source:** finance/erp 리팩토링 진단(2026-07-09) **P2**. **Pattern:** `*-hook-only-split` 시리즈(TASK-PC-FE-150 DelegationScreen→use-delegation-screen, PC-FE-151 DepartmentWriteDialog→use-department-write, PC-FE-152 MasterWriteDialog→use-master-write)와 **동일 렌즈**.

---

## Goal

`features/erp-ops/components/ApprovalDetail.tsx`(~450줄)는 erp-ops의 detail/action 컴포넌트 중 **유일하게 hook-only split을 안 받은** 파일이다(분할된 형제들은 ~250–320줄). 4개 mutation hook + `reasonFor` state + `runTransition`/`onAction`/`newIdemKey` 오케스트레이션 + 에러 파생 + `actions` 파생(상태/변이 로직)과 두 개의 큰 렌더 블록(stage-progress 타임라인, history 타임라인) + 중첩 `ApprovalReasonDialog`가 한 파일에 섞여 있다.

이 task는 **상태/변이/오케스트레이션을 `use-approval-detail.ts` 훅으로 추출**하고, `ApprovalDetail.tsx`는 presentation-only(다이얼로그 shell + stage/history 타임라인 + reason-dialog 렌더)로 남긴다. **순수 구조 변경 — 렌더 출력·DOM·testid·행동 불변**(FE-150/151/152와 동일 규율).

## Scope
1. **신규 `src/features/erp-ops/components/use-approval-detail.ts`** — `useApprovalDetail(id)`(또는 착수자가 형제 훅 시그니처에 맞춤): 4개 mutation hook · `reasonFor` state · `runTransition`/`onAction`/`newIdemKey` · `actionError`/`actionErrorTransition` 파생 · `actions` 파생을 보유·반환.
2. **`ApprovalDetail.tsx`** — 위 로직을 훅 호출로 대체, presentation-only로 축소. **순수-폼-입력 state(reason-dialog의 로컬 `reason` 입력)는 컴포넌트에 잔류**(FE-150/151/152 선례: 로컬 입력 state는 presentational에 둠). `ApprovalReasonDialog` 중첩 컴포넌트는 그대로 유지(또는 형제 패턴대로 분리 — 착수자 판단, 단 행동 불변).
3. **테스트** — 기존 `ApprovalDetail` 관련 테스트 그대로 GREEN 유지(행동/DOM/testid 불변이므로 테스트 수정 불필요가 정상). 훅 단위 테스트는 형제(use-delegation-screen 등)가 별도 훅 테스트를 두는지 확인해 관례 답습(있으면 추가, 없으면 컴포넌트 테스트로 커버).

## Acceptance Criteria
- **AC-1** `use-approval-detail.ts` 신설, 상태/변이/오케스트레이션 전부 이관. `ApprovalDetail.tsx`는 presentation-only(+로컬 입력 state).
- **AC-2** 렌더 출력·DOM 구조·testid·mutation 동작(idempotency key 포함) 불변 — 기존 테스트 무수정 GREEN.
- **AC-3** `ApprovalDetail.tsx` 라인 수 유의미 감소(훅으로 이동), 두 파일 각각 단일 책임.
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest`(erp 전체 + 전체) GREEN. (`[[env_console_web_local_verify_needs_lint]]`)

## Out of Scope
- stage/history 타임라인 렌더 로직 변경·최적화 — 이동만, 개선 금지.
- 다른 erp-ops 컴포넌트 분할 — 이미 처리됨(진단이 재분할 불필요로 판정).
- 진단 다른 후보(guide atoms cross-domain) — 별개 task.

## Failure Scenarios
- 훅 추출 중 idempotency-key 생성/전달 타이밍 변경 → mutation 중복/누락 회귀. 기존 approval transition 테스트가 가드.
- 로컬 `reason` 입력 state를 훅으로 잘못 올림 → 리렌더/포커스 회귀. FE-150/151/152 선례대로 입력 state는 presentational 잔류.
- 이동 중 testid/aria 변경 → 접근성/E2E 회귀. AC-2 무수정 테스트 GREEN이 가드.

## Related
- 선례: `tasks/done/TASK-PC-FE-150-delegation-screen-container-presentational-split.md`, `PC-FE-151-department-write-dialog-hook-only-split.md`, `PC-FE-152-master-write-dialog-hook-only-split.md`.
- 진단: finance/erp 리팩토링 스캔(2026-07-09) P2.
