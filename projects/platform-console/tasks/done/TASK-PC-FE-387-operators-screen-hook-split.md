# Task ID

TASK-PC-FE-387

# Title

console-web `OperatorsScreen.tsx`(893줄) 상태 · 변이 · 핸들러를 `use-operators-screen.ts` 훅으로 분리(표시 전용 화면) + 두 기능에 글자 그대로 복사된 «부여 가능한 업무 권한 세트» 조회를 `shared/api/` 로 승격 — **동작 불변**

# Status

done

# Owner

platform-console

# Task Tags

- code
- refactor
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기계적 이동 · 기존 시험이 판정)
> 기안 2026-11-24 UTC. 출처: 소유자 «다음 추천 → 진행» (리팩토링 추천 2번).

# Dependency Markers

- 선행: 없음. 선례: `TASK-PC-FE-235`(ApprovalDetail hook-only split, 시험 무수정) · `TASK-PC-FE-105`(이 화면의 1차 분할 — 표 · 확인 문구 분리) · `TASK-PC-FE-259`(글자 동일 중복을 `shared/` 로 승격 + 기능별 re-export).

# Goal

1. **`features/operators/components/OperatorsScreen.tsx` = 893줄**, console-web 에서 가장 큰 화면 컴포넌트다(2026-11-24). 135–630행(약 500줄)이 상태 15개 남짓 · 변이 훅 11개 · 파생값 · 핸들러 16개이고, 631행부터가 JSX 다. TASK-PC-FE-105 의 헤더가 «이 컨테이너가 모든 상태 · 변이 · 게이팅을 소유한다» 고 적은 그 덩어리다. PC-FE-235 와 같은 방식으로 로직을 훅으로 옮기면 화면은 표시만 남는다.
2. **`queryGrantableSets` + `GrantableSetOption(List)Schema` 가 두 기능에 글자 그대로 있다** — `features/operators/hooks/use-domain-access.ts:65-69` · `features/operators/api/domain-access-types.ts:81-94` 와 `features/operator-groups/hooks/use-group-domain-sets.ts:51-55` · `features/operator-groups/api/domain-sets-types.ts:44-57`(같은 `/api/domain-permission-sets` 호출 · 같은 `legacy-compat` 제외). 기능 간 TS import 는 금지(architecture.md § Forbidden Dependencies)라 각자 복사한 것이고, 그 규칙의 해법은 `shared/api/` 승격이다(architecture.md § `shared/api/` 줄 — «2개 이상 feature 가 소비하는 read/계약의 승격 홈»).

# Scope

## In Scope

- `features/operators/hooks/use-operators-screen.ts`(신규): `OperatorsScreen` 의 135–630행을 **순서 · 내용 그대로** 옮긴 `useOperatorsScreen(props)` — JSX 가 쓰는 값 · 핸들러를 객체로 반환.
- `OperatorsScreen.tsx`: 훅 호출 + JSX 만. JSDoc 헤더 유지 + MODULE SPLIT 절에 이 분할 한 줄.
- `shared/api/grantable-domain-sets.ts`(신규): `GrantableSetOptionSchema` · `GrantableSetOption` · `GrantableSetOptionListSchema` · `queryGrantableSets()`(현재 본문 그대로, `legacy-compat` 제외 주석 포함). 두 기능의 types 모듈은 스키마 · 타입을 **re-export**, 두 훅 파일은 이 함수를 import — **각 훅의 query key(`DOMAIN_ACCESS_KEY` / `GROUP_DOMAIN_SETS_KEY`) · 옵션은 그대로**.
- `specs/services/console-web/architecture.md` § `shared/api/` 줄에 새 모듈 이름 한 구절.

## Out of Scope

- 화면 동작 · 문구 · testid · 라우트 변경. 훅 안 로직의 «정리» (이동만 한다).
- `OperatorsTable` · 대화상자 컴포넌트들.

# Acceptance Criteria

- [x] AC-1: `OperatorsScreen.tsx` 에 `useState` · `useMemo` · 변이 훅 호출이 0개(전부 훅으로).
- [x] AC-2: 기존 시험 **수정 없이** 통과 — 특히 `tests/unit/OperatorsScreen.test.tsx` · `OperatorsScreen-domain-access.test.tsx` · `OperatorsScreen-platform-scope.test.tsx` 와 operators · operator-groups 의 domain-sets / domain-access 시험. 시험을 고쳐야 통과하면 동작이 바뀐 것 → 멈추고 보고.
- [x] AC-3: 두 기능에서 `queryGrantableSets` 정의 0개 · `GrantableSetOptionSchema` 정의 0개(re-export 만). `shared/api/grantable-domain-sets.ts` 단위 시험이 `legacy-compat` 제외를 단언 + bite(제외 줄 제거 → 빨강 → 복원 → 초록).
- [x] AC-4: `tsc --noEmit` · `lint` · vitest 전체 · `next build` rc=0(각각 독립 실행 + 명시 rc).
- [x] AC-5: 이동이 «그대로» 였다는 증거 — 훅 파일 본문과 원본 135–630행의 diff 가 들여쓰기 · 반환 객체 · import 말고는 0(PR 본문에 방법과 결과).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Forbidden Dependencies · § `shared/api/`.

# Related Skills

- refactor-code.

# Related Contracts

- 없음(런타임 동작 불변).

# Target App

- `projects/platform-console/apps/console-web`

# Implementation Notes

- 반환 객체의 키는 JSX 가 쓰는 식별자 이름 그대로(JSX 쪽은 구조 분해로 받아 본문을 바꾸지 않는다 — AC-5 를 쉽게 만든다).
- `function` 선언 핸들러를 훅 안으로 옮겨도 렌더마다 새로 만들어지는 것은 원본과 같다(`useCallback` 을 새로 넣지 않는다 — 넣으면 동작이 바뀔 수 있다).

# Edge Cases

- 훅 안의 `useMemo` 의존 배열은 원본 그대로 — 바뀌면 대화상자가 갱신값을 못 받는 원본 주석(«dialog re-fires its useEffect»)의 경로가 깨진다.
- 훅 호출 순서: 원본의 훅 호출 순서를 그대로 유지(React 훅 규칙상 순서가 상태 정체성이다 — 시험이 렌더 중 상태를 보존하는지 판정).

# Failure Scenarios

- 한 식별자를 반환에서 빠뜨림 → `tsc` 가 잡는다. 같은 이름 다른 값(가림) → AC-5 diff 가 잡는다.
- 공유 모듈이 서버 전용 모듈(`next/headers`)에 닿음 → client component 가 import 하므로 빌드 깨짐 — 이 모듈은 `apiClient`(클라이언트 경로) 만 쓴다; `next build` 가 판정.

# Test Requirements

- 기존 시험 무수정 통과 + `shared/api/grantable-domain-sets` 단위 시험(신규).

# Definition of Done

- AC 전부 · PR CI 초록 · 머지.

## CORRECTION (close chore, 2026-11-24 UTC)

- AC-5 재대조(구현 에이전트 보고와 별개로 직접): 원본 `const [statusFilter` ~ `return (` 직전 vs 훅 파일 같은 구간 — 차이 = 원본의 `return (` 앞 빈 줄 1개뿐 · JSX 구간 바이트 동일.
- 구현 뒤 JSDoc MODULE SPLIT 문장(«이 컨테이너가 모든 상태를 소유한다»)이 훅 분리와 어긋나 같은 PR 에서 문구를 맞췄다(커밋 `51a8cee7a`).
