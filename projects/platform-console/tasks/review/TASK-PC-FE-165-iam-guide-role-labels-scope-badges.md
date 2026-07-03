# TASK-PC-FE-165 — IAM 개요 역할 카드: 한글 역할명 + 플랫폼 스코프 배지

- **Status**: review
- **Type**: TASK-PC-FE (console-web)
- **Depends on**: TASK-PC-FE-163 (IAM 개요 가이드 화면)

## Goal

IAM 개요 화면(`/iam`)의 역할 카드 가독성 향상: (1) 각 role.name 옆에 사람이 읽는 **한글 역할명(부제)** 표시, (2) 테넌트 스코프 role 에 `테넌트 스코프` 배지를 붙인 것과 대칭으로 **플랫폼 스코프 role 에 `플랫폼 스코프` 배지**를 붙인다.

## Scope

- `src/features/iam-guide/data.ts` — `SeedRole` 에 `koName: string` + `scope: 'platform' | 'tenant'` 추가, `tenantScoped?` 제거(→ `scope` 로 대체). 6개 role 값 지정:
  - SUPER_ADMIN=플랫폼 관리자/platform(elevated), SUPPORT_READONLY=CS 상담원/platform, SUPPORT_LOCK=CS 상담원 (계정 제어)/platform, SECURITY_ANALYST=보안 분석가/platform, TENANT_ADMIN=테넌트 위임관리자/tenant, TENANT_BILLING_ADMIN=테넌트 구독관리자/tenant.
- `src/features/iam-guide/components/IamGuideScreen.tsx` — 카드 헤더에 `koName`(muted) + scope 배지(`플랫폼 스코프`/`테넌트 스코프`, SUPER_ADMIN 은 `플랫폼 스코프(*)`) 렌더. testid `iam-guide-scope-<ROLE>`.
- `tests/unit/IamGuideScreen.test.tsx` — koName 렌더 + role.scope 별 배지 텍스트 단언 추가.

## Acceptance Criteria

- 6개 role 카드에 한글 부제가 name 옆에 표시된다.
- SUPER_ADMIN·SUPPORT_READONLY·SUPPORT_LOCK·SECURITY_ANALYST = `플랫폼 스코프` 배지(SUPER_ADMIN 은 `(*)` 병기), TENANT_ADMIN·TENANT_BILLING_ADMIN = `테넌트 스코프` 배지.
- `pnpm lint` + `tsc --noEmit` + 타깃 vitest GREEN.

## Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (§ Seed Roles — 스코프 평면 분류의 근거).

## Related Contracts

- 없음(정적 화면 표시 메타데이터).

## Edge Cases

- `scope` 는 표시용 분류(플랫폼 측 운영 role vs 테넌트 confine 위임 role). SUPER_ADMIN 만 `*` 센티넬 보유 → 배지에 `(*)` 병기로 구분.

## Failure Scenarios

- role 카드와 scope 배지 불일치 → 테스트가 role.scope 별 배지 텍스트를 단언하여 검출.
