# TASK-PC-FE-163 — IAM 개요(가이드) 화면 추가

- **Status**: done
- **Type**: TASK-PC-FE (console-web)
- **Depends on**: TASK-PC-FE-157 (operator↔tenant assignment UI — TENANT_ADMIN 선택가능화), TASK-BE-345/346 (TENANT_ADMIN / TENANT_BILLING_ADMIN seed)

## Goal

IAM 콘솔에 **개요(가이드) 화면**을 추가한다. 운영자가 6개 admin-console RBAC seed role 의 의미·보유 권한·부여 예시와, 3개 IAM 화면(계정 운영 / 운영자 관리 / 감사·보안)의 **메뉴 접근 권한 매트릭스**를 한 화면에서 참조할 수 있게 한다. 순수 설명(정적) 화면 — 백엔드 호출·권한 게이트 없음(가이드는 누구나 열람 가능).

## Scope

- 신규 라우트 `src/app/(console)/iam/page.tsx` (정적 server component, `force-dynamic` 불필요 — 데이터 페치 없음).
- 신규 feature `src/features/iam-guide/`:
  - `data.ts` — role 카탈로그 + permission 키 카탈로그 + 접근 매트릭스(타입드, `rbac.md` seed 매트릭스가 SoT).
  - `components/IamGuideScreen.tsx` — 정적 렌더(‘use client’ 불필요).
  - `index.ts` — barrel.
  - `components/IamGuideScreen.test.tsx` — 렌더 스모크 + 매트릭스/롤 존재 단언.
- `src/shared/ui/ConsoleSidebarNav.tsx` — IAM drill 부모의 **첫 자식**으로 `개요`(`/iam`, testid `nav-iam-guide`) leaf 추가.

## Non-Goals

- 권한별 화면 pre-hide(생산자 권위 원칙 유지 — 다른 IAM 화면과 동일하게 서버 403 을 각 화면이 인라인 처리). 가이드 화면 자체는 게이트하지 않는다.
- 도메인 롤(WMS_OPERATOR 등) 파생 로직 변경 — 설명만 참조.

## Acceptance Criteria

- `/iam` 진입 시 6개 role(SUPER_ADMIN / SUPPORT_READONLY / SUPPORT_LOCK / SECURITY_ANALYST / TENANT_ADMIN / TENANT_BILLING_ADMIN) 각각의 보유 권한 + 의도 설명이 렌더된다.
- 접근 매트릭스가 `rbac.md` § Seed Matrix 와 일치한다:
  - 계정 운영 = `account.read` 게이트 → SUPER_ADMIN ✅ · SUPPORT_READONLY ✅ · 나머지 ❌.
  - 운영자 관리 = `operator.manage` 게이트 → SUPER_ADMIN ✅ · TENANT_ADMIN ✅ · 나머지 ❌.
  - 감사·보안 = `audit.read` 게이트(기본) → SUPER_ADMIN / SUPPORT_READONLY / SUPPORT_LOCK / SECURITY_ANALYST ✅; 보안이벤트(login_history/suspicious)는 `security.event.read` 추가 요구 → SUPER_ADMIN / SUPPORT_READONLY / SECURITY_ANALYST 는 "보안이벤트 포함", SUPPORT_LOCK 은 "기본만".
- 운영 시 롤 부여 위임 체인(SUPER_ADMIN → TENANT_ADMIN → 직원) + confinement(TenantScopeGuard) + no-escalation(RoleGrantGuard ≤-own) 설명 포함.
- 테넌트 직원/협력업체 = 도메인 롤(별도 축, assume-tenant 파생) 설명 + org_scope 협력업체 축소 언급.
- IAM 사이드바 drill 에 `개요` 진입점이 계정 운영보다 먼저 표시된다.
- `pnpm lint` + `tsc --noEmit` + 타깃 vitest GREEN.

## Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (Seed Roles / Seed Matrix / Permission Keys — 매트릭스 SoT).
- ADR-MONO-024 (tenant-admin delegation: TenantScopeGuard D2 / RoleGrantGuard D3 / assign-surface D3-i).
- `OperatorRoleDerivation.java` (auth-service — 도메인 롤 파생 축).

## Related Contracts

- 없음(정적 화면, 신규 API 없음).

## Edge Cases

- 매트릭스가 seed 와 드리프트하지 않도록 `data.ts` 에 SoT 주석 + 근거(권한 키) 병기. seed role 추가 시 이 화면의 `data.ts` 동반 갱신이 필요함을 명시.
- 협력업체/직원 도메인 롤은 이 6개 admin-console role 과 **다른 축**임을 화면에서 명시(혼동 방지).

## Failure Scenarios

- role 카탈로그와 매트릭스가 불일치 → 테스트가 매트릭스 셀을 role×화면으로 단언하여 조기 검출.
- 사이드바 leaf href `/iam` 가 기존 라우트와 충돌 → 신규 세그먼트라 충돌 없음(검증: glob).
