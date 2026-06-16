# TASK-PC-FE-110 — operators `operators-api.ts` god-file 분할 (client core + concern별 api 모듈)

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js, operators 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (behavior-preserving 기계적 분할 — god-file split 시리즈 연속)

## Goal

operators `api/operators-api.ts`(581줄) god-file 을 **hardened client core + concern별 3개 api 모듈**로 분할. operators 는 단일 엔티티·다중 오퍼레이션이라 erp/ledger의 엔티티별과 달리 **concern(admin CRUD / self-service / assignments)** 축으로 분할. 원파일은 `export *` **barrel** 로 전환 → 8 route handler + operators-state + dashboards/overview-api + page + 테스트의 전 import가 **0변경** 유지. **behavior-preserving** — endpoint·per-endpoint header matrix·logPath·error taxonomy·표면 export 전부 byte-identical.

## Scope

**In scope** (console-web only, operators 피처):

1. `operators-client.ts` — hardened `callGapOperators` HTTP core + `OPERATORS_PREFIX` + CallOptions/HttpMethod + 보안/매트릭스/resilience doc. `callGapOperators`/`OPERATORS_PREFIX` export(**feature-internal** — sibling 모듈이 import; **barrel 미재노출** → 공개표면 불변).
2. `operators-crud-api.ts` — listOperators·createOperator·editOperatorRoles·changeOperatorStatus·setOperatorProfile(privilege-sensitive admin 관리).
3. `operators-self-api.ts` — changeOwnPassword·updateOwnProfile·getSelfOperatorIdOrNull(`/me/*` self).
4. `operators-assignments-api.ts` — listOperatorAssignments·setOperatorOrgScope(org-scope).
5. `operators-api.ts` → `export *` barrel(3 함수 모듈만 재노출; client core 제외) + 분할 설명 doc.

**Out of scope**: 동작/계약/header matrix 변경 일체. types.ts(이미 분리됨)·operators-state.ts(별도)는 무관.

## Acceptance Criteria

- **AC-1 — behavior-preserving.** endpoint·per-endpoint header matrix(create=reason+key / roles·status·set-profile·org-scope=reason-only / me/*=none)·empty-reason fail-safe·error taxonomy(401/403/409/400/404/503/timeout)·password·token redaction 전부 원본과 동일.
- **AC-2 — 표면 안정.** barrel 가 노출하는 함수 = 정확히 기존 10개(listOperators·createOperator·editOperatorRoles·changeOperatorStatus·changeOwnPassword·updateOwnProfile·setOperatorProfile·getSelfOperatorIdOrNull·listOperatorAssignments·setOperatorOrgScope). callGapOperators/OPERATORS_PREFIX 는 비공개 유지(barrel 미재노출). 전 import 사이트 무변경.
- **AC-3 — 분할.** 원 581줄 → client core + 3 concern 모듈 + 얇은 barrel.
- **AC-4 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(기존 operators 테스트 무수정 통과 = behavior-preserving 증명; operators-api.test/org-scope/set-profile/update-profile/self-operator-id/parity-verification 전부 barrel 경유라 무변경).

## Related Specs

- `projects/platform-console/tasks/done/TASK-PC-FE-098-*` 등 — api god-file split 선례(barrel 플레이북)
- `console-integration-contract.md` § 2.4.3 — per-endpoint header matrix(소비 대상, 무변경)

## Related Contracts

- 없음(순수 내부 구조 리팩토링 — IAM admin operators 계약 소비 코드 무변경).

## Edge Cases

- barrel 는 client core(`operators-client`)를 **재노출하지 않음** → `callGapOperators`/`OPERATORS_PREFIX`/CallOptions 가 공개표면에 누출 안 됨(기존도 비공개였음 — 표면 byte-identical). 함수 모듈은 `./operators-client` 에서 직접 import.
- leaf 순환 없음(client→shared, 함수모듈→client+types 단방향). 소스텍스트 단일파일 가드 없음(테스트는 전부 동작 테스트·spec md 읽기).

## Failure Scenarios

- 없음(런타임 동작 무변경). 회귀는 기존 vitest 스위트가 검출.
