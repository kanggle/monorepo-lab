# TASK-PC-FE-108 — erp-ops `erp-masters-api.ts` god-file 분할 (엔티티별 api 모듈)

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js, erp-ops 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (behavior-preserving 기계적 분할 — PC-FE-107 masters hook split 자매·god-file split 시리즈 연속)

## Goal

erp-ops `api/erp-masters-api.ts`(628줄) god-file 을 **5개 마스터 엔티티 + 공유 qs 헬퍼** 단위
per-entity api 모듈로 분할. 원파일은 `export *` **barrel** 로 전환 → `erp-api.ts` 의
`export * from './erp-masters-api'`(및 20 route handler + erp-state + 테스트 전 import)가 **0변경** 유지.
**behavior-preserving** — endpoint·logPath·method·body·idempotencyKey·envelope parse 전부 byte-identical.
PC-FE-107(masters hook split)의 자매 작업 — 둘이 합쳐 erp-ops "masters" 수직 분할을 완결(types 만 잔여).

## Scope

**In scope** (console-web only, erp-ops 피처):

1. `api/masters/` 신설, 6개 모듈로 분할:
   - `masters-qs.ts` — 공유 `listQs`/`detailQs`/`pageParams`/`compact`(서버측 qs 빌더 + body cleaner; feature-internal). use-erp-shared 의 client측 buildListQs 와 별개 레이어(통합 안 함=behavior-risk·scope-out).
   - `departments-api.ts` — list/detail read + WRITE PILOT(create/update/retire/move-parent, FE-046) + `parseDepartmentData`.
   - `employees-api.ts` — list/detail read + create/update/retire(FE-048) + `parseEmployeeData`.
   - `job-grades-api.ts` — list(displayOrder asc)/detail read + CUD + `parseJobGradeData`.
   - `cost-centers-api.ts` — list/detail read + CUD + `parseCostCenterData`.
   - `business-partners-api.ts` — list/detail read + CUD(confidential paymentTerms) + `parseBusinessPartnerData`.
2. `erp-masters-api.ts` → `export *` barrel(5 모듈 재노출) + 분할 설명 doc.

**Out of scope**: `api/types.ts`(757) 분할(후속 별 task — masters 수직 완결 마지막 1건), 동작/계약 변경 일체.

## Acceptance Criteria

- **AC-1 — behavior-preserving.** endpoint·logPath·method·body 구성·idempotencyKey·envelope parse(`{data,meta}`)·E3 asOf thread-through 전부 원본과 동일. 동작/계약 0변경.
- **AC-2 — 표면 안정.** `erp-masters-api` 가 노출하던 전 함수(list*/get*ById/create*/update*/retire*/moveDepartmentParent)가 barrel 로 동일 재노출. `erp-api.ts` `export *` 0변경 → 20 route handler + erp-state + 테스트 전 import 무변경.
- **AC-3 — 분할.** 원 628줄 → 5 entity 모듈 + 공유 qs 모듈 + 얇은 barrel. 각 모듈 단일 엔티티 응집.
- **AC-4 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(기존 테스트 무수정 통과 = behavior-preserving 증명).

## Related Specs

- `projects/platform-console/tasks/done/TASK-PC-FE-098-*` — erp-api god-file split 선례(barrel 플레이북, 본 파일의 형제)
- `projects/platform-console/tasks/done/TASK-PC-FE-107-erp-masters-hook-split.md` — 자매 작업(hooks/masters/ 분할, 같은 5 엔티티)

## Related Contracts

- 없음(순수 내부 구조 리팩토링 — erp masterdata-api.md 계약 소비 코드 무변경).

## Edge Cases

- barrel `export *` 중복 심볼 0(각 함수는 단일 모듈 소유). leaf 모듈 간 순환 import 없음(전부 types/erp-client/masters-qs 단방향).
- `parse*Data` 헬퍼는 엔티티별 소유(중복 아님 — 각 entity schema 전용). 공유는 listQs/detailQs/pageParams/compact 뿐.

## Failure Scenarios

- 없음(런타임 동작 무변경). 회귀는 기존 vitest 스위트가 검출.
