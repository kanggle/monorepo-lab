---
id: TASK-BE-408
title: admin RBAC — tenant.manage rbac.md 정합 + GET tenant 권한 강제 + 매트릭스 IT
status: done
project: iam-platform
service: admin-service
type: bugfix
created: 2026-06-20
---

# TASK-BE-408 — admin RBAC: tenant.manage 정합 + GET tenant 강제 + 매트릭스 IT

## Goal

admin-service 의 RBAC 강제는 이미 `RequiresPermissionAspect`(presentation/aspect/) 로 전 mutation 엔드포인트에 적용·deny-by-default 가드까지 동작 중이다. 남은 진짜 갭 4개를 닫는다: (1) 코드(V0024 seed)·`TenantAdminController` 에는 있으나 `rbac.md` 권한 카탈로그엔 누락된 `tenant.manage` 를 rbac.md 에 정합(HARDSTOP-09 해소), (2) 어노테이션 없는 `GET /api/admin/tenants`·`/{tenantId}` 에 `@RequiresPermission(Permission.TENANT_MANAGE)` 강제, (3) rbac.md 가 요구하는 role×endpoint 매트릭스 통합테스트 추가, (4) architecture.md 의 placeholder 파일명 정리.

## Background

- **오해 정정**: 당초 "OperatorEndpointAccessResolver 미구현 → 전 endpoint 무방비" 는 오기였다. 실제 강제기는 `RequiresPermissionAspect`(= architecture.md:92/148 가 placeholder 이름 `OperatorEndpointAccessResolver.java` 로 forward-ref 한 것). 전 mutation(POST/PUT/PATCH/DELETE)은 `@RequiresPermission` + `AspectCoverageTest` 빌드타임 가드로 이미 보호됨.
- **HARDSTOP-09**: `Permission.TENANT_MANAGE="tenant.manage"` 가 코드에 존재하고 `V0024__seed_tenant_manage_permission.sql` 가 seed 까지 하는데, `rbac.md § Permission Keys` 카탈로그엔 없음. rbac.md 는 "다른 키는 본 문서 업데이트 없이 도입 금지" 라고 규정 → 코드가 이미 shipped 한 현실에 맞춰 rbac.md 를 정합(reconcile)한다. (신규 아키 결정 아님 — 이미 내려진 결정의 문서화.)
- **GET tenant 갭**: `GET /api/admin/tenants`·`/{tenantId}` 는 inline `requirePlatformScope`/`isTenantAllowed` 만 있고 `@RequiresPermission` 없음. → tenant.manage 보유자만 조회하도록 일관 강제(POST/PATCH tenant 이 이미 tenant.manage 요구하므로 read 도 동일 권한이 합리적).

## Scope

- **IN**: rbac.md 권한 카탈로그·seed 매트릭스에 `tenant.manage` 추가(타깃 엔드포인트 GET/POST/PATCH `/api/admin/tenants`, 보유 role=SUPER_ADMIN); `TenantAdminController` GET 2개에 `@RequiresPermission(Permission.TENANT_MANAGE)` 추가(기존 inline 가드는 defense-in-depth 로 유지); `AspectCoverageTest` 가 `@SelfServiceEndpoint` 메서드를 명시 면제하도록 보정(latent 버그); RBAC role×endpoint 매트릭스 IT 추가; architecture.md:92/148 placeholder→실제(`RequiresPermissionAspect`) 정리.
- **OUT**: `RequiresPermissionAspect`/`@RequiresPermission`/`Permission`/평가기/캐시 등 이미 구현된 강제 메커니즘 변경 금지(재구현 금지). `GET /api/admin/accounts`(이메일검색 분기는 의도적 무권한)·`GET /api/admin/me`·`/console/registry`(의도적 무권한) 변경 금지. Flyway 신규 마이그레이션 불요(V0024 가 이미 seed).

## Acceptance Criteria

- [ ] **AC-1 (HARDSTOP-09 해소, spec-first)**: `projects/iam-platform/specs/services/admin-service/rbac.md` 의 § Permission Keys 카탈로그에 `tenant.manage` 행 추가(설명 + 타깃 엔드포인트 `/api/admin/tenants` GET/POST/PATCH), § Seed Matrix 에 SUPER_ADMIN→tenant.manage(✅) 반영. 기존 V0024 seed 와 일치하게 기술. 코드 변경 전 갱신.
- [ ] **AC-2**: `presentation/tenant/TenantAdminController.java` 의 `listTenants()`·`getTenant()` 에 `@RequiresPermission(Permission.TENANT_MANAGE)` 추가. 기존 `requirePlatformScope`/`isTenantAllowed` inline 체크는 제거하지 말고 유지(2차 방어). aspect 권한 게이트가 1차.
- [ ] **AC-3**: `presentation/aspect/AspectCoverageTest.java` 보정 — controller mutation 메서드는 `@RequiresPermission` **또는** `@SelfServiceEndpoint` 중 하나를 가져야 한다는 assertion 으로 수정(현재 `@SelfServiceEndpoint`-only 메서드가 latent 하게 통과/실패하는 구멍 제거).
- [ ] **AC-4 (매트릭스 IT)**: `src/test/java/.../integration/` 에 RBAC role×endpoint 매트릭스 통합테스트 신설(rbac.md 가 요구하는 "seed role × endpoint table-driven 검증"). 기존 `AdminAuditTenantScopeIntegrationTest` 패턴(operator JWT mint + `admin_operator_roles` seed + 403/200 assert + DENIED audit row 검증)을 따른다. 최소: tenant.manage 보유 role 은 GET/POST/PATCH tenant 200(또는 의미적 성공), 미보유 role 은 403 + DENIED audit row. `@Tag("integration")`, MySQL+Redis Testcontainers.
- [ ] **AC-5 (문서정리)**: `architecture.md:92,148` 의 `OperatorEndpointAccessResolver.java (planned, 미구현)` 을 실제 구현 위치(`presentation/aspect/RequiresPermissionAspect.java`, 구현됨)로 정정.
- [ ] **AC-6**: `:admin-service:test` GREEN(단위 + 컴파일). Testcontainers IT 는 CI Linux 권위(로컬 Windows Docker 불가). 기존 `AspectCoverageTest` 회귀 없음.

## Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (AC-1 정합)
- `projects/iam-platform/specs/services/admin-service/architecture.md` (AC-5 문서정리)
- ADR-MONO-033(roles issuance), ADR-MONO-035(operator auth)

## Related Contracts

- 없음(내부 권한 강제. 엔드포인트 경로·DTO 무변경, GET tenant 는 권한 미보유 시 403 추가).

## Edge Cases

- SUPER_ADMIN 외 role 이 GET tenant 호출 → 403 + DENIED audit(AC-4 검증).
- 기존 inline platform-scope 가드와 aspect 권한 게이트 둘 다 존재 → aspect 가 먼저 deny(권한 없으면 inline 도달 전 차단), 권한 있으면 inline 이 추가 scope 검증. 이중이지만 모순 없음.
- `@SelfServiceEndpoint`(me/password, me/profile) → 무권한 유지(AspectCoverageTest 면제 명시).

## Failure Scenarios

- AC-1 누락하고 코드만 변경 → rbac.md drift 잔존(HARDSTOP-09 미해소). spec-first 필수.
- inline 가드 제거 → tenant scope 2차방어 상실. 유지 필수.
- 매트릭스 IT 가 allow-only(deny 케이스 없음) → 회귀가드 무의미. 403+DENIED audit 케이스 필수.
