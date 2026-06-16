# TASK-PC-FE-109 — erp-ops `api/types.ts` god-file 분할 (area별 type 모듈) — masters 수직 완결

- **Status**: done
- **Project**: platform-console
- **App**: console-web (Next.js, erp-ops 피처)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (behavior-preserving 기계적 분할 — PC-FE-107/108 masters 수직의 마지막 1건)

## Goal

erp-ops `api/types.ts`(757줄) god-file 을 **area별 8개 type 모듈**(common + 5 master 엔티티 + org-view + delegation-fact)로 분할. 원파일은 `export *` **barrel** 로 전환 → 모든 `@/features/erp-ops/api/types` import(api 모듈·hooks·route-handler body validator·컴포넌트·테스트)가 **0변경** 유지. **behavior-preserving** — zod 스키마·타입·헬퍼 전부 byte-identical. PC-FE-107(hooks/masters)+108(api/masters)에 이은 **masters 수직 분할의 types 레이어 완결**.

`types.ts` 파일이 `types/` 디렉터리보다 모듈 해석 우선(moduleResolution=bundler: `.ts` 확장자 > 디렉터리 index) → `import '../api/types'` 는 배럴로, 배럴 내부 `./types/common` 등은 서브모듈로 해석(충돌 없음, tsc 검증).

## Scope

**In scope** (console-web only, erp-ops 피처):

1. `api/types/` 신설, 8개 모듈로 분할:
   - `common.ts` — EffectivePeriod·KNOWN_*(MASTER/EMPLOYMENT/PARTNER) enum·Audit·ErpMeta·ReadModelMeta·ERP_*_PAGE_SIZE·ErpList/DetailQueryParams·labelForUnknownEnum·isRetired·ErpRetireBodySchema(공통 — 모든 모듈이 여기서 import).
   - `department.ts` — Department schema+responses + WRITE PILOT input/body(create/update/retire/move-parent, FE-046).
   - `employee.ts`·`job-grade.ts`·`cost-center.ts` — schema+responses + create/update input/body(FE-048).
   - `business-partner.ts` — schema+responses + create/update input/body + 로컬 PaymentTermsSchema(confidential).
   - `employee-org-view.ts` — Department/CostCenter/JobGrade Ref + EmployeeOrgView + responses + OrgViewListQueryParams(read-model, FE-049).
   - `delegation-fact.ts` — DelegationFact + responses + query params(read-model, FE-055).
2. `types.ts` → `export *` barrel(8 모듈 재노출) + 분할 설명 doc.

**Out of scope**: 동작/스키마 변경 일체, 신규 타입.

## Acceptance Criteria

- **AC-1 — behavior-preserving.** 전 zod 스키마(parse 동작·passthrough·optional/nullable)·타입·labelForUnknownEnum·isRetired·body schemas 원본과 동일. 스키마 0변경.
- **AC-2 — 표면 안정.** types.ts 가 노출하던 전 심볼이 barrel 로 동일 재노출. 모든 import 사이트(api/hooks/route body validator/컴포넌트/테스트) 무변경.
- **AC-3 — 분할.** 원 757줄 → 8 area 모듈 + 얇은 barrel. cross-module 참조는 `./common` 단방향(순환 0).
- **AC-4 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(기존 테스트 무수정 통과 = behavior-preserving 증명).

## Related Specs

- `projects/platform-console/tasks/done/TASK-PC-FE-107-erp-masters-hook-split.md` / `TASK-PC-FE-108-erp-masters-api-split.md` — 자매(masters 수직 hook+api 분할)
- `erp-platform/specs/contracts/http/masterdata-api.md` / `read-model-api.md` — 소비 대상 계약(무변경)

## Related Contracts

- 없음(순수 내부 구조 리팩토링 — 계약 소비 코드 무변경).

## Edge Cases

- `types.ts`↔`types/` 동시 존재 해석: `.ts` 파일 우선(bundler/node 공통) → 배럴이 import 받음. tsc 가 모호성 즉시 검출(통과 시 안전).
- barrel `export *` 중복 심볼 0(각 심볼 단일 모듈 소유). PaymentTermsSchema 는 business-partner 로컬(비export) 유지.
- cross-module 참조 전부 `./common` 한 방향(common 은 sibling import 0) → 순환 없음.

## Failure Scenarios

- 없음(런타임/스키마 동작 무변경). 회귀는 기존 vitest 스위트가 검출.
