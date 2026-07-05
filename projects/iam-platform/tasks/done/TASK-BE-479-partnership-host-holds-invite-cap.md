# Task ID

TASK-BE-479

# Title

ADR-MONO-045 후속 — 파트너십 invite-time ≤-own(host-holds) cap 실구현 (도메인=host 활성 구독 ∈, 역할=delegatable-operator-role 허용목록 ∈; request-time host-holds 는 의도적 unbounded 로 확정) + rbac.md 정합

# Status

done

# Owner

backend

# Task Tags

- backend
- iam
- security-sensitive

---

# Goal

BE-477(step 2a)이 남긴 **host-holds seam** 을 닫는다. 현재 admin-service 의 `HostEntitledScopeResolver` 기본 구현은 `UnboundedHostEntitledScopeResolver`(항상 `Optional.empty()`)라, `PartnershipManagementUseCase.validateDelegatedScopeCap` 의 ≤-own 검사(`delegated.isSubsetOf(hostHolds)`)와 `OperatorAssignmentCheckUseCase` 의 request-time `∩ host-holds` 가 **양쪽 다 no-op** 이다. 그 결과 host `TENANT_ADMIN` 이 **host 가 실제로 보유하지 않는 도메인/역할**(예: 미구독 도메인, `WMS_ADMIN` 같은 admin-tier 도메인 역할)을 delegatedScope 로 위임할 수 있는 latent over-delegation 이 남아 있다(admin-role 3종만 현재 차단). admin-api.md 는 이미 이 위반에 `422 PARTNERSHIP_SCOPE_INVALID`("host 미보유")를 명세하므로 — 이 task 는 **명세된 계약 동작의 stub 을 실구현**한다(계약 변경 없음).

**설계 판정(사용자 확정, 2026-07-05):**
1. **request-time `∩ host-holds` 는 unbounded 유지가 정답.** (a) ADR-MONO-020 §3.1 이 assume-tenant hot-path 의 cross-service 콜백을 금지하고, (b) **step 2b(BE-478)가 이미 assume-tenant mint 시 `entitled_domains = host-ACTIVE ∩ delegated.domains` 로 도메인을 클립**하므로 request-time 의 role 재클립은 inert(도메인 게이트가 미보유 도메인을 이미 403). 따라서 request-time host-holds = identity(unbounded), 실질 enforcement 는 **invite-time**.
2. **invite-time 에서 ≤-own 을 구체 강제**(command 경로 → account-service 조회 허용):
   - **도메인 ≤-own**: 각 `delegatedScope.domain ∈ host 의 활성 도메인 구독`(admin-service 기존 `TenantDomainSubscriptionPort.listActiveSubscriptions()` 를 host 로 필터). 위반 → 422.
   - **역할 ≤-own**: 각 `delegatedScope.role ∈ DELEGATABLE_OPERATOR_ROLES` 정적 허용목록(operator-tier 도메인 역할만; admin-tier `*_ADMIN`/tenant-admin 3종 제외). 위반 → 422. 허용목록은 auth-service `OperatorRoleDerivation` 값집합의 flatten 미러(keep-in-sync 주석).
   - account-service 장애 시 **fail-CLOSED**(위임 인가 게이트 — 보유 검증 불가 시 invite 거부, 기존 `DownstreamFailureException` 전파).

# Scope

## In scope

- `projects/iam-platform/specs/services/admin-service/rbac.md` — Cross-Org Partner Delegation Confinement 섹션 정합: pseudocode 의 `scope ∩ hostEntitledScope(hostTenant)`(현재 request-time 재확인으로 서술) → **request-time host-holds = identity(unbounded, 의도)** 로 명시 + **invite-time 이 concrete ≤-own(도메인=구독 ∈, 역할=delegatable-operator-role 허용목록 ∈)을 강제**함을 서술. ADR-020 §3.1 + step-2b 도메인 클립 근거 명시.
- `domain/rbac/DelegatableRoleCatalog.java` (신규) — `DELEGATABLE_OPERATOR_ROLES` 정적 Set(auth `OperatorRoleDerivation` 값집합 flatten: WMS_OPERATOR/OUTBOUND_{READ,WRITE}/INBOUND_{READ,WRITE}/INVENTORY_{READ,WRITE}/MASTER_READ/ADMIN[ecommerce operator]/SCM_OPERATOR/ERP_OPERATOR/FINANCE_OPERATOR/MES_OPERATOR/FAN_OPERATOR) + `isDelegatable(role)` / `allDelegatable(roles)`. keep-in-sync 주석(auth OperatorRoleDerivation 이 도메인 추가 시 여기도 갱신 — fail-closed drift).
- `application/PartnershipManagementUseCase.java` — `validateDelegatedScopeCap` 확장: 기존 (empty domains, containsAdminRole) 유지 + **도메인 구독 ∈ 검사**(신규 `TenantDomainSubscriptionPort` 주입, host 필터) + **역할 허용목록 ∈ 검사**. 무효 → `PartnershipScopeInvalidException`(422, 구체 메시지). 기존 `hostEntitledScopeResolver` 필드/subset-check 제거(invite 경로에서 분리 — request 경로가 별도 보유).
- `application/HostEntitledScopeResolver.java` + `UnboundedHostEntitledScopeResolver.java` — javadoc 확정: "deferred seam" → **request-time 의도적 unbounded(최종 결정)**. ADR-020 §3.1 hot-path 금지 + step-2b 도메인 클립으로 request-time host-holds = identity. invite-time 이 실 enforcement.
- `application/OperatorAssignmentCheckUseCase.java` — `resolveCrossOrgDelegatedScope` 의 host-holds 주석 확정(deferred → intentional unbounded; 동작 불변).
- 테스트: `PartnershipManagementUseCaseTest` — invite 케이스 추가(미구독 도메인→422, admin-tier/미지 역할→422, 유효[구독 도메인+operator 역할]→PENDING, account-service 장애→fail-closed 전파). `DelegatableRoleCatalogTest`(허용/거부/normalize). 기존 cross-org-leak IT 에 over-delegation-rejected 케이스 추가(선택, 여력 시).

## Out of scope

- request-time host-holds 의 bounded 구현(위 판정으로 **의도적 미구현** 확정 — ADR-020 §3.1 + step-2b 중복).
- auth-service `OperatorRoleDerivation` 의 공유 lib 추출(정적 허용목록 미러로 대체; drift 는 fail-closed. 공유화는 별 후속 후보).
- 계약(admin-api.md) 변경 — "host 미보유 → 422" 는 이미 명세됨(실구현만).
- step 2b/step 3 재변경(완료).
- ADR-045 §3.4-4 deferred(N-way/broker/billing/ABAC/rate-limit/discovery).

# Acceptance Criteria

- [ ] **AC-1** invite 시 `delegatedScope.domains` 중 host 활성 구독에 없는 도메인이 하나라도 있으면 `422 PARTNERSHIP_SCOPE_INVALID`(구체 메시지: 미보유 도메인). host 구독은 `TenantDomainSubscriptionPort.listActiveSubscriptions()` 를 hostTenantId 로 필터해 판정.
- [ ] **AC-2** invite 시 `delegatedScope.roles` 중 `DELEGATABLE_OPERATOR_ROLES` 에 없는 역할(admin-tier `WMS_ADMIN` 등, tenant-admin 3종, 미지 역할)이 하나라도 있으면 `422 PARTNERSHIP_SCOPE_INVALID`. (tenant-admin 3종은 기존 containsAdminRole 로도 차단 — 이중.)
- [ ] **AC-3** 유효 invite(모든 도메인 ∈ 구독 + 모든 역할 ∈ 허용목록 + admin-role 없음 + 도메인 비어있지 않음) → 정상 PENDING 생성(기존 동작 보존).
- [ ] **AC-4** invite 중 account-service 장애(`DownstreamFailureException`) → **fail-CLOSED**(invite 거부, 예외 전파; 보유 검증 불가 시 위임 미발급). 삼켜서 통과시키지 않음.
- [ ] **AC-5** `DELEGATABLE_OPERATOR_ROLES` = auth-service `OperatorRoleDerivation` 의 전 도메인 operator-role 값집합과 정확히 일치(테스트로 두 집합 동치 확인 또는 명시 목록 단언). admin-tier(`WMS_ADMIN`/`*_ADMIN`)·tenant-admin 3종 미포함.
- [ ] **AC-6** request-time(`OperatorAssignmentCheckUseCase`) 동작 **byte-불변**(unbounded 유지) — 기존 cross-org-leak IT 7 AC green.
- [ ] **AC-7** rbac.md 정합: request-time host-holds = identity(unbounded, ADR-020 §3.1 + step-2b 근거), invite-time = concrete ≤-own 서술. pseudocode/prose 일치.
- [ ] **AC-8** `./gradlew :projects:iam-platform:apps:admin-service:test` green(신규+기존). **iam Integration Testcontainers 레인(CI)이 권위**.

# Related Specs

- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` D3(≤-own across org, admin 불가) — 이 task 가 D3 의 invite-time enforcement 를 실구현.
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Cross-Org Partner Delegation Confinement — 정합 대상(pseudocode line ~311 host-holds).
- ADR-MONO-020 §3.1(assume-tenant hot-path cross-service 콜백 금지 — request-time unbounded 근거), ADR-MONO-019(entitled_domains 구독 authority), ADR-MONO-035(OperatorRoleDerivation).

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Partnership Management — `422 PARTNERSHIP_SCOPE_INVALID`("delegatedScope 가 admin role 포함 또는 **host 미보유(≤-own across org 위반)**") 이미 명세. 이 task 는 실구현(계약 변경 없음).

# Edge Cases

- **delegated.domains 일부만 미구독**: 하나라도 미보유면 전체 invite 거부(부분 수락 없음).
- **역할은 유효한데 도메인 미구독**: 도메인 검사에서 거부(role 이 held 도메인에 속하는지까지는 안 봄 — 정적 허용목록 판정, 도메인은 구독 판정; 불일치 role 은 어차피 step-2b 에서 inert).
- **account-service 가 host 구독 0 반환**: 모든 도메인 미보유 → 거부(구독 없는 테넌트는 위임할 도메인 없음).
- **normalize**: ScopeSet 이 trim/dedup 하므로 허용목록 비교는 정규화된 값 기준.
- **request-time 변화 없음**: 이미 unbounded 라 회귀 0.

# Failure Scenarios

- **account-service down/timeout/5xx at invite** → `DownstreamFailureException` 전파 → 503(기존 매핑) → invite 미발급(fail-closed). 위임 인가 게이트는 가용성 의존 금지.
- **허용목록 drift(auth 가 새 도메인 operator-role 추가)** → 그 역할 위임이 invite 에서 422(fail-CLOSED, 과다-허용 아님). keep-in-sync 주석으로 완화; 공유 lib 추출은 후속.
- **over-delegation(미구독 도메인 or admin-tier role)** → 이 task 로 invite 에서 차단. (이전엔 통과했으나 step-2b 도메인 클립으로 실 피해는 제한적이었음 — 이제 authoring 시점에 거부.)
