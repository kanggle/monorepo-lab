# TASK-PC-FE-169 — IAM 개요: 운영자 온보딩 3축(home tenant / tenant assignment / org_scope)

- **Status**: done
- **Type**: TASK-PC-FE (console-web)
- **Depends on**: TASK-PC-FE-163 (IAM 개요 가이드 화면), TASK-PC-FE-157 (테넌트 배정 UI), TASK-PC-FE-050 (org-scope UI)

## Goal

IAM 개요 화면(`/iam`)에 운영자의 **운영 도달 범위**를 정하는 세 직교 축을 한 섹션으로 정리해 추가한다 — `home tenant`(원 소속) · `tenant assignment`(편입/해제) · `org_scope`(부서 subtree tri-state). 이 세 축은 이커머스를 포함한 assume-tenant 도메인 운영에 그대로 적용되며(이커머스 입점사/협력업체 운영자 온보딩이 이 축으로 표현됨), 그중 `home tenant` 는 기존 가이드 어디에도 문서화돼 있지 않았다. 위임 체인("누가 무엇을 부여") 다음, 도메인 롤 파생표("무엇을 받나") 앞에 배치해 "부여 → 도달 범위 → 도메인 롤" 흐름을 완성한다.

## Scope

- `src/features/iam-guide/data.ts` — `OnboardingAxis` 인터페이스 + `OPERATOR_ONBOARDING_AXES`(3원소: home tenant / tenant assignment / org_scope) 추가. 각 원소는 `term`·`koName`·`api`·`desc`·`ecommerceNote`. `DELEGATION_GUARDS` 와 `DOMAIN_ROLE_MAP` 사이에 삽입.
- `src/features/iam-guide/components/IamGuideScreen.tsx` — 위임 가드 다음, 도메인 롤 섹션 앞에 "운영자 온보딩 3축 (운영 도달 범위)" 카드 그리드(3열) 렌더. 카드 testid `iam-guide-onboarding-axis-<term>`(공백→`-`).
- `tests/unit/IamGuideScreen.test.tsx` — 3축 카드 렌더 + term/koName 표시 + 정규 순서 단언.

## Acceptance Criteria

- `/iam` 개요에 3축 카드(home tenant / tenant assignment / org_scope)가 term·한글명·관련 API·설명·이커머스 예와 함께 렌더된다.
- `org_scope` 카드가 tri-state(`null`=전체 · `[]`=차단 · `[ids]`=subtree, `null` ≠ `[]`)를 명시한다.
- 기존 데이터 주도 테스트(role×화면 매트릭스, 도메인 롤, 권한 키)와 axe AA 클린이 그대로 통과한다.
- `pnpm lint` + `tsc --noEmit` + 타깃 vitest(`tests/unit/IamGuideScreen.test.tsx`) GREEN.

## Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (§ Seed Roles / § 위임 — 축의 스코프 근거).
- `projects/iam-platform/specs/services/admin-service/admin-api.md` (§ `POST/DELETE …/assignments/{tenantId}` + § `PUT …/assignments/{tenantId}/org-scope`).

## Related Contracts

- 없음(기존 배정/org-scope 계약을 표시-설명만 함; 새 계약·엔드포인트 추가 없음). 근거 구현: `src/features/operators/api/operators-assignments-api.ts`.

## Edge Cases

- `home tenant` 는 배정 API 가 아니라 운영자 생성 시 tenantId(불변) — `api` 필드에 "생성 시 tenant” 로 명시해 배정 API 와 혼동되지 않게 함.
- `org_scope` tri-state 는 `null`(전체)과 `[]`(차단)을 구분(BE-338 fail-closed) — 카드 설명에 `null` ≠ `[]` 명시.
- 배정이 없는 홈-테넌트-only 운영자는 org_scope 부적용(전체) — `home tenant` 설명에 반영.

## Failure Scenarios

- 3축 카드 누락/순서 뒤바뀜 → 테스트가 testid 별 렌더 + 정규 순서 배열을 단언하여 검출.
- 새 섹션이 기존 매트릭스/도메인 롤 테스트를 깨뜨림 → 데이터 주도 테스트가 각 배열을 독립 순회하므로 회귀 시 즉시 RED.
