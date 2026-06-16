# TASK-PC-FE-107 — erp-ops `use-erp-masters.ts` god-file 분할 (엔티티별 hook 모듈)

- **Status**: done
- **Project**: platform-console
- **App**: console-web (Next.js, erp-ops 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (behavior-preserving 기계적 분할 — PC-FE-098~102 god-file split 시리즈 연속)

## Goal

erp-ops `hooks/use-erp-masters.ts`(755줄) god-file 을 **5개 마스터 엔티티 + read-model org-view** 단위
per-entity hook 모듈로 분할. 원파일은 `export *` **barrel** 로 전환 → `use-erp-ops.ts` 의
`export * from './use-erp-masters'`(및 모든 downstream import)가 **0변경**으로 유지된다.
**behavior-preserving** — query-key·endpoint·staleTime·invalidation·표면 export 전부 byte-identical.
PC-FE-098(erp-api 1113)·099(use-erp-ops 1250)·100~102 의 god-file split 플레이북 연속.

## Scope

**In scope** (console-web only, erp-ops 피처):

1. `hooks/masters/` 신설, 6개 모듈로 분할:
   - `use-departments.ts` — list/detail read + WRITE PILOT mutations(create/update/retire/move-parent, FE-046) + 로컬 `invalidateDepartments`.
   - `use-employees.ts` — list/detail read + create/update/retire(FE-048; PII).
   - `use-job-grades.ts` — list(displayOrder asc)/detail read + create/update/retire.
   - `use-cost-centers.ts` — list/detail read + create/update/retire.
   - `use-business-partners.ts` — list/detail read + create/update/retire(confidential paymentTerms).
   - `use-employee-org-views.ts` — read-model org-view read(FE-049, READ-ONLY; mutation hook 없음 — E5).
2. 공유 `invalidateMaster`(employees/job-grades/cost-centers/business-partners 4개가 공유) → `use-erp-shared.ts` 로 이동·export(masters list/detail builder 들이 이미 거기 있음). `invalidateDepartments`(departments 전용)는 use-departments 로컬 유지.
3. `use-erp-masters.ts` → `export *` barrel(6 모듈 재노출) + 분할 설명 doc. `'use client'` 유지.

**Out of scope**: `api/types.ts`(757)·`api/erp-masters-api.ts`(628) 분할(후속 별 task), 동작/계약 변경 일체, 신규 기능.

## Acceptance Criteria

- **AC-1 — behavior-preserving.** query-key·endpoint·staleTime·seeded 로직·invalidation prefix·retry 전부 원본과 동일. 동작/계약 0변경.
- **AC-2 — 표면 안정.** `use-erp-masters` 가 노출하던 전 심볼(useDepartments/…/useEmployeeOrgViews + Create/Update/Retire/MoveDepartmentParentArgs 등)이 barrel 로 동일하게 재노출. `use-erp-ops.ts` `export *` 0변경 → 전 import 사이트 무변경.
- **AC-3 — 분할.** 원 755줄 → 6 모듈 + 얇은 barrel. 각 모듈 단일 엔티티 응집.
- **AC-4 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(기존 테스트 무수정 통과 = behavior-preserving 증명).

## Related Specs

- `projects/platform-console/tasks/done/TASK-PC-FE-099-*` 등 — use-erp-ops god-file split 선례(barrel 플레이북)
- `projects/platform-console/apps/console-web/src/features/erp-ops/hooks/use-erp-shared.ts` — masters 공유 헬퍼 거처

## Related Contracts

- 없음(순수 내부 구조 리팩토링 — erp masterdata-api.md 계약 소비 코드 무변경).

## Edge Cases

- barrel `export *` 중복 심볼 0(각 심볼은 단일 모듈 소유). leaf 모듈 간 순환 import 없음(전부 use-erp-shared/api 단방향 의존).
- `invalidateMaster` 이동 후 4개 mutation hook 의 onSuccess prefix invalidation 동작 동일.

## Failure Scenarios

- 없음(런타임 동작 무변경). 회귀는 기존 vitest 스위트가 검출.
