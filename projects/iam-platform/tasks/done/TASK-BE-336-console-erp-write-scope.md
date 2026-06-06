# Task ID

TASK-BE-336

# Title

**콘솔 운영자가 erp 부서를 쓸 수 있도록 `platform-console-web` 클라이언트에 `erp.write` scope 부여 + assume-tenant 교환이 client 등록 scope를 도메인-facing 토큰에 전파.** PC-FE-046(콘솔 erp 부서 write UI)가 라이브에서 403 PERMISSION_DENIED 로 막힌 근본 원인(콘솔 토큰에 write scope/role 부재)을 GAP auth-service 측에서 해소한다. scope-기반 위임(ADR-MONO-020) 모델 — entitlement-trust 는 read-only 라 write 를 못 넓힘.

# Status

done

# Owner

backend-engineer (GAP auth-service — project-internal; + docs/adr/ADR-MONO-020 additive note)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
- adr

---

# Dependency Markers

- **follows / fixes-runtime-gap-of**: TASK-PC-FE-046 (콘솔 erp 부서 write UI; 머지됨 impl #1079). 그 task 는 콘솔 wiring 을 완성했으나 라이브에서 운영자 토큰이 erp WRITE 권한을 못 가져 403. 이 task 가 그 토큰 권한을 채운다.
- **builds-on**: TASK-BE-327 (assume-tenant RFC8693 교환 — `AssumeTenantAuthenticationProvider` + `TenantClaimTokenCustomizer`; ADR-MONO-020 §3.3 step2). V0018 (erp.read/erp.write scope 정의), V0015 (platform-console-web client 시드), V0020 (platform-console token-exchange grant — 동일 idempotent UPDATE precedent).
- **authz 모델**: erp `RoleScopeAuthorizationAdapter` — WRITE = `erp.write` scope ∨ operator role; entitlement-trust(`entitled_domains`)는 READ-only widening(ADR-MONO-019 §D5). 콘솔 토큰은 entitlement 로 read 만 통과, write 는 scope 가 있어야 함.
- **decision (user, 2026-06-04)**: AskUserQuestion "scope 부여 (권장)" — operator role 전파(global role 을 tenant-scoped 토큰에 싣는 벡터) 대신 scope-기반 위임.

# Goal

erp-entitled 테넌트를 assume 한 콘솔 운영자가 `ERP 운영 → 부서`에서 생성/수정/폐기/이동을 수행하면 producer(masterdata-service)가 `erp.write` scope 로 인가하여 성공한다. base(home) 로그인 토큰은 불변(콘솔이 authorize 에서 `erp.write` 미요청) — write 권한은 **tenant 전환(assume) 토큰에만** 실린다(least-privilege).

# Scope

## In Scope

- **migration** `V0023__add_erp_write_scope_to_platform_console.sql` — `platform-console-web` 의 `scopes` JSON 배열에 `erp.write` 추가(idempotent, V0020 의 `JSON_*` + `JSON_SEARCH` 가드 패턴 미러).
- **code** `AssumeTenantAuthenticationProvider` line ~138 — `authorizedScopes(Set.of())` → `authorizedScopes(registeredClient.getScopes())`: assume-tenant 도메인-facing 토큰이 client 등록 scope(이제 erp.write 포함)를 `scope` claim 으로 운반. erp `ActorContextJwtAuthenticationConverter` 가 `scope` claim 을 파싱 → `hasScope("erp.write")` → WRITE 인가.
- **test** `AssumeTenantExchangeIntegrationTest` happy-path — assumed 토큰의 `scope` claim 이 `erp.write` 포함 단언(회귀 게이트). `PlatformConsoleOidcClientSeedIntegrationTest` — registered scopes 에 `erp.write` 포함 단언 보강.
- **adr** ADR-MONO-020 additive note: assume-tenant 교환이 client 등록 scope 를 전파(scope-기반 도메인 위임; entitlement-trust 와 독립).

## Out of Scope

- erp masterdata-service 코드(불변 — 이미 `erp.write` scope 로 WRITE 인가; consume only).
- operator role 전파 방식(기각 — ADR-020 은 account_type 만 보존, global role 을 tenant 토큰에 안 실음).
- 나머지 도메인(wms/scm/finance) write scope — 이 task 는 erp 만. (다른 도메인 write 파일럿 시 동일 패턴.)
- base 로그인 토큰 scope 변경(콘솔 OIDC_SCOPE 불변).

# Acceptance Criteria

- [x] **AC-1** `platform-console-web` registered scopes 에 `erp.write` 추가(V0023, idempotent). 기존 `openid/profile/email/tenant.read` 보존.
- [x] **AC-2** assume-tenant 교환으로 발급된 도메인-facing 토큰이 `scope` claim 에 `erp.write` 를 운반(IT 단언).
- [x] **AC-3** 라이브: erp-entitled 테넌트를 assume 한 콘솔 운영자가 부서 create/update/retire/move-parent 성공(403 해소). producer DB 영속 확인.
- [x] **AC-4** base(non-assume) 토큰은 `erp.write` 미운반(콘솔이 authorize 에서 미요청 — least-privilege 보존).
- [x] **AC-5** auth-service `./gradlew :...:auth-service:test` GREEN(assume-tenant IT + seed IT 갱신 포함), build GREEN.

# Related Specs

- ADR-MONO-020 (assume-tenant 교환; 이 task 가 additive note). ADR-MONO-019 §D5 (entitlement-trust=read-only — 이 task 가 그 경계를 존중, write 는 scope 로). ADR-001 (GAP OIDC scope-기반 인가).
- console-integration-contract §2.4.8 *Department write binding (PILOT)* (credential 불변 — 동일 토큰, 이제 erp.write scope 운반). erp `masterdata-api.md` § Department (불변 producer).

# Related Contracts

- erp `masterdata-api.md` § Department — WRITE 는 `erp.write` scope 필요(불변).

# Edge Cases

- non-erp 테넌트를 assume 한 토큰도 `erp.write` 를 운반(client 등록 scope 라 균일) — 그러나 erp tenant gate(`tenant_id ∈ {erp,*}` ∨ `entitled_domains ∋ erp`)가 non-erp 테넌트의 erp write 를 거부 → inert(과다부여 아님).
- 운영자가 tenant 미전환(home) 상태면 base 토큰(erp.write 없음) → erp write 불가. erp write 는 erp-entitled 테넌트 assume 가 전제.
- entitlement-trust 는 여전히 read-only — 이 task 는 그 모델을 안 바꾸고, write 권한 원천을 client 위임 scope 로 둠(직교).

# Failure Scenarios

- assume 토큰에 scope claim 누락 시 erp WRITE 계속 403 → IT(AC-2)가 게이트.
- base 토큰에 erp.write 누수 시 least-privilege 위반 → 콘솔 OIDC_SCOPE 불변 + base 요청 scope subset 검증으로 방지.
- 머지+CI GREEN 인데 라이브 미작동(메모리 ⓛ) → AC-3 라이브 재검증 필수(auth-service 는 Java=호스트 prebuilt jar COPY → `bootJar` 선행 후 docker build).

# Test Requirements

- `AssumeTenantExchangeIntegrationTest.happyPath`: assumed 토큰 `scope` ⊇ `erp.write` 단언.
- `PlatformConsoleOidcClientSeedIntegrationTest`: registered scopes ⊇ `erp.write` 단언.
- `./gradlew :projects:global-account-platform:apps:auth-service:test` + build.
- Local: auth-service `bootJar` → docker build → recreate; 콘솔에서 erp-entitled 테넌트 assume 후 부서 write 성공 + DB 영속 확인.

# Definition of Done

- [x] V0023 migration + provider scope 전파 + IT/seed 단언 + ADR-020 note.
- [x] auth-service test + build GREEN.
- [x] Local: auth-service 재빌드(bootJar+docker)+재기동; 콘솔 부서 write 라이브 성공 + DB 영속.
- [x] Task md + INDEX 갱신.
- [x] Reviewed + merged (impl PR #1081 squash `ebf29436`, 3-dim verified: state=MERGED + tip=`ebf29436` + pre-merge failing required=0). 라이브: V0023 적용 확인(console client scopes ⊇ erp.write) + auth-service 재배포 healthy; assume-tenant IT 가 scope ⊇ erp.write 증명(CI Integration GAP GREEN).

---

분석=Opus 4.8 / 구현=Opus(직접 — auth/security 도메인). 사용자 "이 작업을 수행할 권한이 없습니다. 왜?" → 진단: 콘솔 도메인-facing 토큰이 entitlement-trust 로 erp READ 만 통과, WRITE 는 `erp.write` scope∨operator role 필요한데 둘 다 부재(assume-tenant 교환이 scope/role 미주입). → "scope 부여" 선택. 메타: PC-FE-046 의 런타임 완성편 — 콘솔 wiring(머지)+CI GREEN 만으론 기능 미작동, 인증 위임 scope 까지 채워야 라이브 동작(메모리 ⓛ). write affordance=콘솔 / write 권한 원천=client 위임 scope / write logic=백엔드 producer.
