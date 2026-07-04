# Task ID

TASK-BE-478

# Title

ADR-MONO-045 §3.4 step 2b — auth-service assume-tenant 토큰에 파트너십 `delegatedScope` 도메인/역할 cap 적용 (cross-org participant 가 host 를 assume 할 때 토큰의 `entitled_domains`/`roles` 를 delegated slice 로 confine) + federation assume-tenant IT

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

ADR-MONO-045 §3.4 로드맵의 **step 2b**. step 2a(BE-477)가 admin-service `/internal/operator-assignments/check` 응답에 **additive** `delegatedScope {domains, roles}` 블록을 추가했다 — partner 테넌트 B 의 participant 인 운영자가 host 테넌트 A 를 assume 할 때의 **capped** cross-org 도메인-운영 스코프(`delegated_scope ∩ participant_scope ∩ host-holds`, admin-service 가 계산 완료). 그러나 **auth-service 는 아직 이 필드를 소비하지 않는다** — 현재 assume-tenant 토큰은 정상 운영자 경로처럼 host A 의 **전체** entitled domains + 전체 파생 operator roles 를 방출한다. 즉 cross-org participant 가 delegated slice 를 **넘어서는** 도메인/역할을 토큰으로 받는 latent over-grant 상태다.

이 task 는 auth-service `TenantClaimTokenCustomizer.customizeForAssumeTenant` 가 `delegatedScope` 가 실린 경우(=파트너십-파생 host reach)에 assume-tenant 토큰을 delegated slice 로 **confine** 하도록 완성한다:

- `entitled_domains` 클레임 = host A 의 ACTIVE entitled domains **∩** `delegatedScope.domains`
- `roles` 클레임 = `delegatedScope.roles` **verbatim**(admin-service 가 이미 `delegated ∩ participant ∩ host-holds` 로 캡 완료 — auth-service 는 재-derive 금지: `OperatorRoleDerivation` 을 돌리면 delegated slice 를 넘어 확대된다)

**Load-bearing 불변식(byte-불변으로 보존)**: 파트너십은 도메인-운영 reach 만 넓히고 **admin scope 는 절대 넓히지 않는다.** auth-service 는 애초에 admin scope 클레임을 방출하지 않으므로(admin scope 는 admin-service 가 `admin_operator_roles` 에서 서버-측 평가) 구조적으로 불변 — cross-org actor 는 host A 에 `admin_operator_roles` row 가 없어 `effectiveAdminScope` 공집합 → `/api/admin/**` 403. `delegatedScope.roles` 는 invite-time `ScopeSet.containsAdminRole()`(→422)로 admin role 이 구조적으로 배제돼 있으므로 `roles` 클레임에 실려도 도메인-운영 role 뿐이다.

정상(비-파트너십) 운영자 경로와 모든 login/authorization_code/client_credentials/refresh 경로는 **NET-ZERO**(byte-불변) 유지 — `delegatedScope` 가 null 일 때 코드 경로가 기존과 동일해야 한다.

# Scope

## In scope

- `application/port/OperatorAssignmentPort.java` — `AssignmentResult` 에 additive nullable `DelegatedScope delegatedScope` 필드 추가 + nested `record DelegatedScope(List<String> domains, List<String> roles)`. javadoc.
- `infrastructure/client/AdminAssignmentClient.java` — `doCheck` 가 응답 body 의 additive `delegatedScope` 오브젝트를 파싱(`parseDelegatedScope`); 부재/null/malformed → null(net-zero). 정상 응답 shape byte-불변.
- `infrastructure/oauth2/AssumeTenantAuthenticationToken.java` — additive `delegatedScope` 필드 + provider-side 생성자(7-arg) + getter. 기존 생성자 back-compat 유지.
- `infrastructure/oauth2/AssumeTenantAuthenticationProvider.java` — `AssignmentResult.delegatedScope()` 를 resolvedGrant 로 thread.
- `infrastructure/oauth2/TenantClaimTokenCustomizer.java` — `customizeForAssumeTenant` 에 cross-org cap 분기: `delegatedScope != null` 이면 `entitled_domains = host∩delegated.domains`, `roles = delegated.roles verbatim`, `OperatorRoleDerivation` 미실행. `null` 이면 기존 경로 byte-불변(`populateEntitledDomains` + derive). `populateEntitledDomains` 를 `fetchEntitledDomains`(fetch-only, fail-soft) + set 으로 소폭 리팩터(정상 경로 동작 byte-보존).
- 단위 테스트: `TenantClaimTokenCustomizer` cross-org cap(도메인 교집합·roles verbatim·빈 교집합→클레임 omit·null=net-zero) · `AdminAssignmentClient` delegatedScope 파싱(present/absent/malformed) · `AssumeTenantAuthenticationProvider` delegatedScope 스레딩.
- federation IT: `AssumeTenantExchangeIntegrationTest` 에 cross-org 케이스 추가 — mock 이 `delegatedScope` 를 반환할 때 발급 토큰의 `entitled_domains`/`roles` 가 delegated slice 로 capped 임을 assert(정상 운영자 케이스 회귀 0).

## Out of scope

- admin-service 변경 일체(step 2a=BE-477 에서 완료; `delegatedScope` 계산·`host-holds` seam 은 거기 소유).
- `HostEntitledScopeResolver` 를 unbounded seam(`UnboundedHostEntitledScopeResolver`, `Optional.empty()`)에서 bounded 구현으로 교체 — 별 후속(host-holds 실측 소스 결정 필요). 현재는 admin-service 가 unbounded 로 두므로 cap = `delegated ∩ participant`.
- partner-console UI(step 3).
- `org_scope` 의미 변경 — cross-org 경로는 `delegatedScope` 케이스에서 admin-service 가 `orgScope=null` 반환 → 기존 `null→["*"]` net-zero 그대로(파트너십은 부서 subtree 로 confine 하지 않음).
- 새 계약/스펙 변경 — `auth-to-admin.md` 는 BE-477 이 step 2b 소비 규약(line 36–48)까지 이미 기술함(소비만).

# Acceptance Criteria

- [ ] **AC-1** `delegatedScope != null` 인 assume-tenant 발급 시 `entitled_domains` 클레임 = host A ACTIVE entitled domains ∩ `delegatedScope.domains`(host 가 delegated 도메인을 구독하지 않으면 그 도메인은 빠짐).
- [ ] **AC-2** `delegatedScope != null` 시 `roles` 클레임 = `delegatedScope.roles` verbatim(순서·원소 그대로), `OperatorRoleDerivation` 미실행. 빈 roles → `roles` 클레임 omit.
- [ ] **AC-3** host∩delegated.domains 교집합이 비면 `entitled_domains` 클레임 omit(least-privilege — 도메인 게이트 403).
- [ ] **AC-4** `delegatedScope == null`(정상 운영자·모든 비-파트너십 경로) → 코드 경로·클레임 byte-불변(`populateEntitledDomains` 전체 host entitled + `OperatorRoleDerivation` 파생 roles). NET-ZERO 회귀 0.
- [ ] **AC-5** admin scope 불변 확증 — auth-service 는 admin scope 클레임 미방출; cross-org 토큰에 admin role 부재(`delegatedScope.roles` 는 invite-time containsAdminRole→422 로 구조적 admin-role-free). `roles` 클레임에 `SUPER_ADMIN`/`TENANT_ADMIN`/`TENANT_BILLING_ADMIN` 절대 미포함(테스트로 확증).
- [ ] **AC-6** `AdminAssignmentClient` 가 응답 `delegatedScope` 오브젝트 파싱: present `{domains,roles}` → `DelegatedScope`; 부재/null/non-object → null(구버전 admin·정상 응답 graceful). 정상 `{assigned,orgScope}` shape 파싱 byte-불변.
- [ ] **AC-7** federation IT: mock check 가 `delegatedScope {domains:[wms], roles:[OUTBOUND_WRITE,...]}` 반환 시 발급 assume-tenant 토큰의 `entitled_domains`=[wms](host 가 wms 구독 시), `roles`=delegated verbatim. 기존 정상-운영자 IT 케이스 회귀 0.
- [ ] **AC-8** `./gradlew :projects:iam-platform:apps:auth-service:test` green(신규 + 기존). **iam Integration Testcontainers 레인(CI Linux)이 권위** — 로컬 Docker-free 통과만으로 머지 금지.

# Related Specs

- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` — D3(cross-org 접근은 host-authored delegated_scope 국한, ≤-own across org, admin 불가) / D5(감사 legibility) / §3.4 로드맵 step 2b.
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Cross-Org Partner Delegation Confinement — `effectiveAdminScope` byte-unchanged 불변식(이 토큰 cap 이 그 도메인-운영 평면만 attenuate).
- ADR-MONO-020 D2/D3(assume-tenant 발급·`org_scope`), ADR-MONO-035 O1(BE-376 `OperatorRoleDerivation`), ADR-MONO-019(`entitled_domains` keystone).

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` § GET /internal/operator-assignments/check — `delegatedScope {domains,roles}` additive 필드(BE-477 기술; line 36–48: "auth-service(step 2b)가 이 값으로 assume-tenant 토큰의 `entitled_domains` 를 `domains` 와 교집합하고 role 을 `roles` 로 캡한다. admin scope 는 절대 확장되지 않는다"). 이 task 가 그 소비를 구현.

# Edge Cases

- **delegatedScope 있지만 host 가 delegated 도메인 미구독** → 교집합 공집합 → `entitled_domains` omit → 도메인 게이트 403(least-privilege). roles 는 verbatim 방출되나 도메인 게이트가 tenant-entitlement 없어 거부.
- **delegatedScope.domains 비어있음(admin-service 가 빈 cap 반환)** → 교집합 공집합 → 위와 동일. roles 도 비면 양 클레임 omit → 실질 무권한 토큰(발급은 되나 도메인 게이트 전면 403). 파트너십 자체는 assigned=true 였으므로 토큰 발급은 정상(fail-closed 아님 — 접근이 0).
- **구버전 admin 이 delegatedScope 필드 omit** → 파싱 null → net-zero 정상 경로(단, cross-org 케이스라면 정상 경로가 host 전체를 방출 = step 2a 이전 동작. step 2a/2b 는 동일 릴리스이므로 실배포엔 항상 동반).
- **정상 운영자 + delegatedScope null** → 기존 BE-338/376 경로 byte-불변.
- **platform-scope 운영자** → admin-service 가 assigned=true, delegatedScope=null → 정상 경로(파트너십 무관).

# Failure Scenarios

- **admin-service 장애/timeout/4xx/5xx** → `AdminAssignmentClient` fail-CLOSED(`AssumeTenantDeniedException` → invalid_grant, 토큰 미발급) — 기존 정책 불변. delegatedScope 파싱은 assigned=true 응답 본문에서만 일어나므로 fail-closed 게이트를 우회하지 않음.
- **delegatedScope.roles 에 admin role 이 (버그로) 실려 옴** → 구조적으로 불가(invite-time containsAdminRole→422). 방어적으로도 admin scope 는 auth-service 가 방출하지 않으므로 `roles` 클레임의 admin-role 문자열은 도메인 게이트에서만 해석되며 admin-service `/api/admin/**` 는 `admin_operator_roles`(서버-측)로만 인가 → cross-org actor 공집합 403. NET-ZERO admin 평면.
- **ImmutableCollections 직렬화 트랩** → `roles` 클레임은 `new ArrayList<>(delegated.roles())` 로 래핑(BE-376 선례: `List.of`/`List.copyOf` 는 SecurityJackson2Modules allowlist 밖 → authorization read-back 파손).
