# Task ID

TASK-BE-337

# Title

**assume-tenant 운영자 토큰에 `org_scope=["*"]` gateway-enrichment 주입 — erp 부서 write 의 data-scope 게이트 해소.** BE-336(erp.write scope)이 role/scope 게이트를 뚫은 뒤 라이브에서 `DATA_SCOPE_FORBIDDEN` 으로 막힌 erp WRITE 의 **세 번째(마지막) 게이트**를 GAP 측 토큰 enrichment 로 해소한다. erp masterdata-service 코드/테스트 불변(org_scope 를 erp 만 소비; 토큰이 정당하게 운반).

# Status

done

# Owner

backend-engineer (GAP auth-service — cross-project doc note: erp architecture.md/gap-integration.md + docs/adr/ADR-MONO-020)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
- adr

---

# Dependency Markers

- **follows / completes-runtime-of**: TASK-BE-336 (콘솔 erp.write 위임 scope — role/scope 게이트 통과). 라이브 결과 403 코드가 `PERMISSION_DENIED`(scope 부재) → `DATA_SCOPE_FORBIDDEN`(data-scope 부재)으로 바뀜 = BE-336 성공 + 다음 게이트 노출. TASK-PC-FE-046 (콘솔 부서 write UI).
- **builds-on**: TASK-BE-327 (assume-tenant 교환 `TenantClaimTokenCustomizer.customizeForAssumeTenant` — account_type/entitled_domains 주입 옆에 org_scope 추가). TASK-BE-324 (keystone entitled_domains).
- **authz 3중 게이트(erp masterdata-service E6)**: ① tenant gate(`TenantClaimValidator`/`Enforcer` — tenant_id ∈ {erp,*} ∨ entitled_domains∋erp) ② role/scope gate(`RoleScopeAuthorizationAdapter` — WRITE=erp.write∨operator-role) ③ **data-scope gate**(org_scope='*' ∨ target∈subtree). 운영자 토큰은 ③에서 org_scope 부재로 fail-closed.
- **architecture 의도**: erp `architecture.md` E6 point 3 — human-operator org_scope 는 "조직 멤버십에서 resolve = v2 user-flow", v1 은 client_credentials 만 '*'. 이 task 가 assume-tenant 운영자에 v1 bridge('*') 부여.

# Goal

erp-entitled 테넌트를 assume 한 콘솔 운영자가 부서 create/update/retire/move-parent 시 producer 의 data-scope 게이트를 통과해 성공한다(`DATA_SCOPE_FORBIDDEN` 해소). org_scope='*' 는 tenant 게이트로 이미 격리된 **assume 한 테넌트 내부** 전 부서를 의미(tenant 관리자에게 적합).

# Scope

## In Scope

- **code** `TenantClaimTokenCustomizer.customizeForAssumeTenant` — `org_scope=["*"]` claim 주입(account_type/entitled_domains 주입 옆; `CLAIM_ORG_SCOPE` 상수). assume-tenant 분기 전용 — base authcode 토큰 불변(least-privilege).
- **test** `AssumeTenantExchangeIntegrationTest` happy-path — assumed 토큰 `org_scope` ⊇ `*` 단언(회귀 게이트).
- **doc** ADR-MONO-020 additive note(BE-336 amendment 이어 data-scope 게이트 = 3rd gate). erp `architecture.md` E6 point 3 v1 operator bridge note(cross-project — gateway 가 제공하는 claim 문서화).

## Out of Scope

- erp masterdata-service 코드/테스트(불변 — org_scope 를 정상 경로로 운반받음; MONO-161 entitlement-trust READ invariant 보존). erp 재배포 불요.
- per-operator org-membership 기반 subtree scoping(v2).
- 다른 도메인(wms/scm/finance) — org_scope 미소비(repo 확인), 무영향.

# Acceptance Criteria

- [x] **AC-1** assume-tenant 교환 토큰이 `org_scope` claim 에 `*` 운반(IT 단언). account_type/entitled_domains/scope(erp.write) 모두 보존.
- [x] **AC-2** base(authorization_code) 토큰은 `org_scope` 미운반(least-privilege).
- [x] **AC-3** 라이브: erp-entitled 테넌트(globex 등) assume 후 콘솔 부서 create/update/retire/move-parent 성공(DATA_SCOPE_FORBIDDEN 해소) + DB 영속.
- [x] **AC-4** erp 코드/테스트 불변 — org_scope 를 erp 만 소비, MONO-161 entitlement-trust READ-overview invariant 보존(토큰이 정상 org_scope 운반이라 erp 로직 무변경).
- [x] **AC-5** auth-service test + build GREEN.

# Related Specs

- ADR-MONO-020 (assume-tenant 교환; data-scope 게이트 amendment). erp `masterdata-service/architecture.md` E6 point 3 (org_scope; v1 operator bridge note). erp `gap-integration.md` (gateway enrichment). ADR-MONO-019 §D5 (entitlement-trust=read; 직교 유지).

# Related Contracts

- erp `masterdata-api.md` § Department — WRITE 는 erp.write scope + data-scope 통과 필요(불변 producer; data-scope 는 토큰 org_scope 로 충족).

# Edge Cases

- org_scope='*' 는 assume 한 테넌트 내부 한정(tenant 게이트가 cross-tenant 거부) — 전역 아님.
- CONSUMER 토큰은 account_type≠OPERATOR + assume-tenant 경로 아님 → org_scope 미부여 → erp write 불가(정상).
- erp 가 org_scope 를 `["*"]`(배열) 또는 `"*"`(문자열) 둘 다 파싱(`extractClaim`) — 배열로 주입.

# Failure Scenarios

- assumed 토큰에 org_scope 누락 시 erp WRITE 계속 DATA_SCOPE_FORBIDDEN → IT(AC-1)가 게이트.
- 머지+CI GREEN 인데 라이브 미작동(메모리 ⓛ) → AC-3 라이브 재검증(auth-service bootJar→docker 재배포 + 운영자 테넌트 재선택으로 토큰 재발급 필수 — 기존 세션은 변경前 토큰 보유).

# Test Requirements

- `AssumeTenantExchangeIntegrationTest.happyPath`: assumed 토큰 `org_scope` ⊇ `*`.
- `./gradlew :projects:global-account-platform:apps:auth-service:test` + build.
- Local: auth-service `bootJar`→docker→recreate; 콘솔에서 테넌트 재선택 후 부서 write 성공 + DB 영속.

# Definition of Done

- [x] org_scope 주입 + IT 단언 + ADR-020/erp architecture.md note.
- [x] auth-service test + build GREEN.
- [x] Local: auth-service 재배포 + 콘솔 부서 write 라이브 성공 + DB 영속.
- [x] Task md + INDEX 갱신.
- [x] Reviewed + merged (impl PR #1083 squash `ef1d20d8`, 3-dim verified: state=MERGED + tip=`ef1d20d8` + pre-merge failing required=0, Integration GAP IT `org_scope ⊇ *` GREEN 3m13s). 라이브: auth-service bootJar→docker 재배포(이미지 06:12 UTC / jar 06:09 = BE-337 빌드, healthy). recreate 시 Windows volume-spec/Hyper-V transient → stop+rm 후 fresh `up` 으로 회복.

---

분석=Opus 4.8 / 구현=Opus(직접 — auth/security). 사용자 라이브 403(`DATA_SCOPE_FORBIDDEN`, globex) → 진단: erp WRITE 3중 게이트 중 BE-336이 ②role/scope 뚫음, ③data-scope 가 운영자 org_scope 부재로 막음. erp architecture E6 point 3 이 human-operator org_scope 를 v2 로 명시 → assume-tenant 운영자에 v1 bridge org_scope='*' 부여(client_credentials='*' 패턴의 operator 버전; tenant 게이트가 격리). **메타: 도메인 authz 는 다층(tenant→role/scope→data-scope) — 각 층을 토큰이 개별 충족해야 하고, "한 층 뚫으면 다음 층 노출"(403 코드 변화로 진행 측정). org_scope 주인=gateway/IdP(architecture 명시)라 erp 가 아닌 GAP enrichment 가 올바른 수정 위치 — erp 코드/MONO-161 invariant 불변.** [[project_platform_console_adr_013]] [[project_gap_idp_promotion]]
