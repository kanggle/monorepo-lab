# TASK-PC-FE-204 — 프런트 유닛 GREEN 복구 (OperatorsScreen account-existence unhandled-error)

**Status:** ready
**Area:** platform-console / console-web · **Scope:** 유닛 테스트 안정화(테스트-only, 프로덕트 코드 무변화)
**Type:** CI-RED hotfix (main GREEN 복구)
**Analysis model:** Opus 4.8 · **Impl 권장:** Opus

## Goal

origin/main의 `Frontend unit tests` 잡이 RED였다(PC-FE-203 라벨 작업과 무관하게 발견). 원인은 2개였는데
**wms-overview-state 날짜 flake는 동시 세션이 [PC-FE-201](#2267)로 이미 수정·머지**했다. 남은 결정적 실패
1건(OperatorsScreen)을 제거해 main을 GREEN으로 되돌린다.

## 원인 & 수정 — `OperatorsScreen.test.tsx`

PC-FE-179이 CreateOperatorForm에 디바운스(400ms) account-existence lookup(`GET /api/accounts`)을 추가했다.
OperatorsScreen create 테스트에서 이 부수효과 fetch가 create 흐름과 경합해:

- (a) 첫 `fetch`로 끼어들어 `not.toHaveBeenCalled()` 게이트와 `mock.calls[0]` create-body 단언을 깨고,
- (b) 400ms 타이머가 **teardown 이후** 발화하며 `checkAccountExists()`가 (clearAllMocks/clearMocks로 구현이
  지워진 vi.fn이면) `undefined` 반환 → `undefined.then()` 동기 throw → **unhandled error**(전 테스트 pass여도
  `vitest run` exit≠0 → CI RED).

**수정:** `@/features/operators/api/account-existence`를 **평범한 함수**(`() => Promise.resolve(null)`)로 목.
OperatorsScreen 동작 테스트의 관심사가 아니며(자체 동작은 CreateOperatorForm.test가 커버), 네트워크 없이
null(unknown→무경고) 반환. **vi.fn이 아닌 평범한 함수**라 clearMocks에 면역 → 늦게 발화하는 타이머도 안전.

## Acceptance Criteria

- [ ] **AC-1** `OperatorsScreen.test.tsx`가 통과하며 **account-existence 유래 unhandled error 0**.
- [ ] **AC-2** full `vitest run` exit 0 (Errors 0).
- [ ] **AC-3** 프로덕트 코드 변경 0(테스트 파일 1개만). `tsc --noEmit` + `next lint` green.

## Related Specs / Contracts

- 없음 — 테스트 안정화. 프로덕트 동작/계약 무변화. wms 날짜 flake = [[PC-FE-201]] 소관(중복 금지).

## Edge Cases / Failure Scenarios

- **간헐 flake 별건**: `LedgerOpsScreen.test.tsx`("Not implemented: navigation" jsdom)는 full-suite 상호작용
  flake로 격리 시 통과(결정적 아님). 이 task 범위 밖 — CI에서 걸리면 잡 재실행. 근본 안정화는 별도 후속.
