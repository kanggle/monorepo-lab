# TASK-PC-FE-218 — operators use-operators.ts hook god-file 모듈 분할

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving hook module split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (React Query 훅 다수·per-endpoint 헤더/무효화 매트릭스 보존)

---

## Goal

IAM sweep(PC-FE-208~212) 이후에도 안 다뤄진 유일 hook god-file `operators/hooks/use-operators.ts`(460줄) 분할. 이 파일은 **단일 거대 훅이 아니라 ~11개 focused React Query 훅 + 공유 헬퍼를 한 모듈에 묶은 것** — cohesion 경계로 모듈을 나눠 각 파일을 응집 단위로 만든다. `OperatorsScreen.tsx`(503) 등 컨테이너는 PC-FE-209가 orchestration 이유로 의도적으로 남긴 것이라 **이 task 범위 아님**(hook 모듈만).

## Scope

`use-operators.ts`를 cohesion별 서브모듈로 분할(각 훅 본문 byte-동일, 위치만 이동):

- **공유 키/헬퍼** (`OPERATORS_KEY`·`listKey`·`assignmentsKey`·`invalidateOperators`): 작은 `operators-keys.ts`(또는 list 모듈)로 추출해 서브모듈이 공유.
- **list read** (`useOperatorsList` + `queryList`): `use-operators-list.ts`
- **mutations** (`useCreateOperator`·`useEditOperatorRoles`·`useChangeOperatorStatus`·`useChangeOwnPassword`·`useUpdateOwnProfile`·`useSetOperatorProfile`): `use-operator-mutations.ts`
- **org-scope/assignments** (`useOperatorAssignments`+`queryAssignments`·`useOrgScopeDepartments`+`queryOrgScopeDepartments`·`useSetOperatorOrgScope`·`useAssignOperator`·`useUnassignOperator`): `use-operator-assignments.ts`

(정확한 그룹 경계는 구현자가 응집도로 판단 — 위는 권장 3분할. 각 파일 목표 ≤ ~200줄, 강제 아님.)

**`use-operators.ts`는 re-export 배럴로 유지**(전 훅을 `export *` 또는 명시 re-export) → 소비처(OperatorsScreen·다이얼로그·폼 등) import 경로 **불변**. 또는 소비처를 새 모듈로 직접 repoint(구현자 판단; 배럴 유지가 blast 최소).

**Out of scope:** 훅 로직·per-endpoint 헤더 매트릭스·무효화 규칙·엔드포인트·proxy·컨테이너 컴포넌트 무변경. 크로스-피처 import(`ERP_KEY`·erp `types` client-safe import)는 그대로 이동(주석 포함).

## Acceptance Criteria
- **AC-1** 각 훅의 mutationFn/queryFn 본문(URL·method·body·schema.parse·onSuccess 무효화)이 byte-동일.
- **AC-2** per-endpoint 매트릭스 보존: create=idempotencyKey+reason / roles·status·set-profile·org-scope·assign·unassign=reason-only(no key) / me/password·me/profile=self(no reason/key). 하나라도 어긋나면 회귀.
- **AC-3** 무효화 규칙 보존: `invalidateOperators`·assignments 키·org-scope의 `[ERP_KEY,'read-model']` 무효화(PC-FE-050) verbatim.
- **AC-4** 공개 훅 export 표면 불변(배럴 경유든 직접 repoint든 소비처 컴파일·동작 불변).
- **AC-5** `tsc --noEmit` + `pnpm lint` + `vitest`(operators 관련 훅/화면/다이얼로그 전체) green. 기존 테스트 무수정 통과(=behavior-preservation 계약).

## Edge Cases / Failure Scenarios
- **client-safety import**(`ERP_KEY`·erp `types`는 배럴 아닌 모듈 직접 import — 서버 전용 코드 드래그 방지) 주석과 함께 정확히 이동. 배럴 경유로 바꾸면 client 번들 오염 회귀.
- 공유 헬퍼를 한 모듈에만 두고 순환 import 없게(keys 모듈은 leaf).
- `'use client'` 지시어를 각 신규 모듈 상단에 유지.

## Related
- 미러: PC-FE-190/215(hook 추출)·PC-FE-209(operators 분할, 컨테이너 잔류 판단).
- 파일 disjoint 병렬 가능: PC-FE-219(audit/accounts).
- 기존 테스트(계약): `tests/unit/operators-*.test.ts(x)` 등 operators 스위트.
