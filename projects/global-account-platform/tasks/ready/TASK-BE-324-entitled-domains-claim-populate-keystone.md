# Task ID

TASK-BE-324

# Title

ADR-MONO-019 § 3.3 keystone — GAP auth-service 가 토큰 발급 시점에 테넌트의 ACTIVE 도메인 구독을 조회해 **`entitled_domains` 서명 claim 을 주입**. step 3 의 4개 도메인 dual-accept 게이트(finance/erp/scm/wms)를 inert→active 로 활성화하는 producer-side keystone.

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED(MONO-153) § 2 D5("entitlement 은 GAP 가 발급 시점에 결정, 토큰 self-contained, 도메인 per-request callback 無") + step 1(BE-322 — `tenant_domain_subscription` N:M 테이블 + `/internal/tenant-domain-subscriptions` 엔드포인트 + admin consumer) + step 3 게이트 복제 4/4(FIN-BE-006/ERP-BE-005/SCM-BE-019/BE-323 — 도메인 게이트가 이미 `entitled_domains` claim 을 READ).
- **enables (후속)**: step 2(실 고객 테넌트+구독+operator 시드 — entitled_domains 가 실제로 cross-domain 을 의미하게 됨) + E2E 활성화 검증(고객 토큰이 구독 도메인 게이트 통과 / 미구독 도메인 403).
- **orthogonal to**: ADR-005/TASK-BE-317(service-to-service workload identity — cc 토큰 경로, 본 task 가 의도적으로 스킵).
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (토큰 발급 hot-path + 재귀 안전성 + fail-soft + cross-service).

---

# Goal

step 3 에서 finance/erp/scm/wms 4개 도메인의 `TenantClaimValidator`(+finance/scm enforcer)가 **서명된 `entitled_domains` claim 을 READ** 하도록 dual-accept 를 깔았으나, **그 claim 을 WRITE 하는 producer 가 없어** entitlement-trust 분기는 전부 inert(legacy `tenant_id==slug` 만 동작). 본 task 는 그 producer = **GAP auth-service 가 토큰 발급 시점에 테넌트의 ACTIVE 도메인 구독을 조회해 `entitled_domains` list claim 을 주입**하는 keystone 이다.

ADR-019 § 2 D5(CHOSEN A): *"entitlement 결정은 GAP 가 발급 시점에 내린다 — 구독(D2)이 존재하면 도메인-scoped 토큰을 발급. 토큰은 self-contained(per-request callback 없음). 도메인은 claim 을 신뢰하고 row 로 격리한다."* 본 task 는 그 발급-시점 결정의 transport 구현이다(transport=auth→account 내부 API; spec 이 transport 는 implementation 으로 남김 → HARDSTOP-09 아님, D5 의 구현).

## 설계 결정 (구현 메모 — D5 의 implementation, 신규 아키텍처 결정 아님)

1. **발급-시점 sync 조회 + fail-soft**: auth-service 의 SAS `TenantClaimTokenCustomizer` 가 tenant_id 해석 후 account-service `/internal/tenant-domain-subscriptions?tenantId=<tid>` 를 호출(기존 `AccountServiceClient`+`GapClientCredentialsTokenProvider`+resilience4j `accountService` CB/retry 재사용)해 그 테넌트의 ACTIVE domainKey 목록을 받아 `entitled_domains` claim 으로 주입. **실패(account down / CB-open / timeout / 빈 결과) → claim 생략**(예외를 발급으로 전파 금지). 발급 hot-path 가 account 에 hard dependency 를 갖지 않도록 — 생략 시 legacy `tenant_id==slug` 로 degrade = **net-zero**.
2. **client_credentials grant 스킵 (재귀 안전성 + 효율)**: cc grant(workload/INTERNAL 토큰; tenant_id=`global-account-platform`, 구독 테이블에 없음, 고빈도 service-to-service)는 entitled_domains 조회를 **하지 않는다**. ⚠️ **재귀 차단**: authorization_code 발급 → account 호출에 Bearer 필요 → `GapClientCredentialsTokenProvider` 가 cc 토큰 self-mint(`/oauth2/token`) → 그 cc 발급이 다시 customizer 를 invoke. cc 경로가 account 를 호출하면 **무한 재귀**. cc 스킵이 이를 끊는다(필수). 또한 workload 토큰은 `/internal/` JWT 게이트를 치지 entitlement 게이트가 아니라 entitled_domains 가 불필요.
3. **대상 grant = authorization_code + refresh_token** (고객/operator 로그인 경로 — 도메인 entitlement 게이트를 치는 토큰). `customizeForAuthorizationCode`(refresh 도 위임) 에만 주입 로직 추가.
4. **net-zero**: step 2 전엔 모든 발급 토큰의 tenant_id 가 slug-tenant(`wms`/`scm`/`erp`/`finance`)이고 그 구독은 backward-compat self-subscription(`domain_key==slug`)뿐 → entitled_domains=`[slug]` → legacy 와 중복 → 기존 동작 byte-identical.
5. **legacy `JwtTokenGenerator`(Path B) 스킵 (out of scope)**: form-login `POST /api/auth/login` 의 custom-JWT(`iss=global-account-platform`)는 GAP 게이트웨이가 검증하는 별개 user-auth 경로로, 페더레이션 도메인 resource-server(`iss=<OIDC_ISSUER_URL>`)가 검증하는 SAS 토큰이 아니다. 도메인 entitlement 게이트를 치는 것은 SAS 토큰(Path A)이므로 keystone 은 Path A(`TenantClaimTokenCustomizer`)에 한정. Path B populate 필요 여부는 별도 판단(follow-up).

# Scope

## In scope

### account-service (구독 역조회 — tenantId 필터)
1. `TenantDomainSubscriptionJpaRepository`: `findByStatusAndTenantId(status, tenantId)` 추가(기존 `findByStatus` JPQL 에 `AND s.tenantId = :tenantId`, 동일 ORDER BY).
2. `TenantDomainSubscriptionRepository`(port) + `TenantDomainSubscriptionRepositoryImpl`: `findActiveByTenantId(String tenantId)` 추가.
3. `TenantDomainSubscriptionQueryUseCase.listActive(...)`: optional `tenantId` 파라미터 추가(또는 오버로드) — tenantId 주어지면 역조회, 없으면 기존 전체/domainKey 필터 유지(기존 동작 무변경).
4. `TenantDomainSubscriptionController` `GET /internal/tenant-domain-subscriptions`: `@RequestParam(required=false) String tenantId` 추가. tenantId+domainKey 동시 지정 처리는 단순 AND(엣지). 기존 호출(파라미터 無 / domainKey 만) 응답 byte-identical.
5. **contract spec 갱신(impl 전 원칙 — 같은 PR 내)**: `projects/global-account-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md` 에 `tenantId` query param row 추가 + "tenantId 역조회는 auth-service 발급-시점 entitled_domains populate(ADR-019 keystone)용" 명시. 기존 net-zero/gap-부재 노트 보존.

### auth-service (claim populate)
6. `AccountServicePort` + `AccountServiceClient`: `List<String> listEntitledDomains(String tenantId)` 추가 — `GET /internal/tenant-domain-subscriptions?tenantId=<tid>` 호출, 응답 `items[].domainKey` 추출, Bearer(`tokenProvider.currentBearer()`) + resilience4j `accountService` CB/retry(기존 3 메서드와 동일 패턴). 실패 시 기존 sibling 과 동일하게 `AccountServiceUnavailableException` throw(클라이언트는 일관 유지 — fail-soft 는 호출측에서).
7. `TenantClaimTokenCustomizer`:
   - `customizeForClientCredentials`: **무변경**(cc 스킵).
   - `customizeForAuthorizationCode`(authcode + refresh 위임받음): tenant_id 주입 직후, `try { List<String> ed = accountServicePort.listEntitledDomains(tenantId); if (!ed.isEmpty()) context.getClaims().claim("entitled_domains", ed); } catch (RuntimeException e) { log.warn(fail-soft 생략); }` — **fail-soft, 빈 목록이면 claim 생략**(net-zero). `AccountServicePort` 를 customizer 에 주입(생성자).
   - claim 키 = `"entitled_domains"`(step 3 도메인측 `CLAIM_ENTITLED_DOMAINS` 와 일치), 값 = `List<String>` domainKeys.
8. **테스트**:
   - `TenantClaimTokenCustomizerTest`(Mockito 단위): authcode/refresh 에서 `AccountServicePort` mock 이 `[finance]` 반환 시 entitled_domains claim 주입 / `[]` 또는 throw 시 claim 생략(fail-soft) / **cc grant 는 listEntitledDomains 미호출**(verify never) 케이스 추가. 기존 tenant_id/tenant_type 단언 무변경.
   - account-service: `TenantDomainSubscriptionQueryUseCaseTest` + `TenantDomainSubscriptionControllerTest` 에 tenantId 역조회 케이스 추가(역조회 결과 / 미구독 테넌트 빈 목록 / tenantId 없으면 기존 동작).
   - IT(권위 게이트는 CI Linux GAP Integration): `OAuth2AuthCodePkceIntegrationTest`(이미 WireMock 으로 account stub)에 `GET /internal/tenant-domain-subscriptions?tenantId=...` stub 추가 + 발급 access token payload 에 entitled_domains 존재 단언. WireMock stub 누락 footprint 주의(BE-322 교훈 — 같은 account 표면을 치는 IT 전부에 stub 동시 추가; account-down→fail-soft 경로도 1 케이스).

## Out of scope

- step 2 실 고객 테넌트/구독/operator 시드(별 task).
- E2E 활성화 검증(별 task).
- legacy `JwtTokenGenerator`(Path B) populate(별 판단).
- gap/console-bff 게이트 복제(step 3 잔여, 별 task).
- 구독 mutation/admin 표면(후속 step).
- legacy slug 분기 제거(step 4).
- 도메인측 게이트 변경(step 3 에서 완료).

# Acceptance Criteria

- **AC-1**: account-service `/internal/tenant-domain-subscriptions?tenantId=<tid>` 가 그 테넌트의 ACTIVE 구독만 반환(역조회). tenantId 미지정 호출은 기존 응답 byte-identical(전체/domainKey 필터).
- **AC-2 (claim populate)**: authorization_code + refresh_token 발급 시 tenant_id 의 ACTIVE 구독이 1+ 이면 access token 에 `entitled_domains`(domainKey list) claim 주입.
- **AC-3 (fail-soft)**: account-service down/CB-open/timeout/예외 또는 구독 0 → `entitled_domains` 생략(발급 성공, 예외 비전파). 토큰은 legacy tenant_id 로 정상 발급.
- **AC-4 (cc 스킵 + 재귀 안전)**: client_credentials 발급은 `listEntitledDomains` 미호출(verify never) — workload 토큰에 entitled_domains 없음, authcode→cc self-mint 재귀 없음.
- **AC-5 (net-zero)**: slug-tenant(`wms`/`scm`/`erp`/`finance`) 토큰의 entitled_domains 는 backward-compat 시드상 `[<slug>]` → legacy 와 중복, 기존 IT 단언 무회귀. 다른 grant/claim 무변경.
- **AC-6**: claim 키 `entitled_domains` = step 3 도메인측 `CLAIM_ENTITLED_DOMAINS` 와 정확히 일치(list of string).
- **AC-7**: contract spec(`account-tenant-domain-subscriptions.md`) tenantId param 반영.
- **AC-8**: account-service + auth-service 컴파일 + 전 테스트 GREEN — **CI Linux GAP Integration(Testcontainers + WireMock JWKS)** 권위 게이트. 회귀 0.
- **AC-9 (scope-lock)**: 변경 = account-service 구독 역조회 + auth-service AccountServiceClient/Port/customizer + 그 테스트 + contract spec 만. cc 경로/JwtTokenGenerator/도메인 게이트/step 2 시드 무변경.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D5 + § 3.1(GAP single entitlement authority / producer-side isolation) + § 3.3.
- `projects/global-account-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md`(본 task 가 tenantId param 추가).
- `projects/global-account-platform/tasks/done/TASK-BE-322-...md`(엔드포인트/모델 producer).
- step 3 게이트 task 4종(`entitled_domains` consumer; claim 키/형 일치 근거).
- `rules/traits/multi-tenant.md` M1/M2/M6.

# Related Contracts

- `entitled_domains` = GAP 서명 토큰 claim(producer=본 task auth-service, consumer=step 3 도메인 게이트). RS256/JWKS 검증 → 위조 불가.

# Related Code

- account: `TenantDomainSubscription{JpaRepository,RepositoryImpl,Repository}` + `TenantDomainSubscriptionQueryUseCase` + `presentation/internal/TenantDomainSubscriptionController`.
- auth: `application/port/AccountServicePort` + `infrastructure/client/AccountServiceClient`(+`GapClientCredentialsTokenProvider` 재사용) + `infrastructure/oauth2/TenantClaimTokenCustomizer`.
- test: `TenantClaimTokenCustomizerTest`, `OAuth2AuthCodePkceIntegrationTest`, account `TenantDomainSubscriptionQueryUseCaseTest`/`TenantDomainSubscriptionControllerTest`.

# Edge Cases

- **재귀**: cc 스킵으로 차단(authcode→Bearer→cc self-mint→cc customize 가 account 미호출). cc 경로에 절대 populate 추가 금지.
- **fail-soft**: account 예외/CB-open/빈 결과 → 생략, 발급 성공. 발급이 account 가용성에 묶이면 안 됨.
- **tenant_id=global-account-platform(INTERNAL)**: cc 경로라 애초 스킵. 혹 authcode 로 INTERNAL 이 와도 구독 0 → 생략(net-zero).
- **claim 형**: 반드시 `List<String>`. 단일 string/null 금지(도메인 `safeStringList` 가 non-list→빈목록 fail-closed 이나, producer 는 정합 list 발급).
- **WireMock footprint**: account 표면 추가 호출 → 그 표면 치는 모든 auth IT 에 stub. 누락 시 발급 fail-soft 로 빠져 entitled_domains 누락(silent) — IT 가 잡도록.

# Failure Scenarios

- cc 경로 populate 추가 → 무한 재귀(발급 hang) → AC-4.
- 예외 전파 → account down 시 로그인 전면 실패(가용성 회귀) → AC-3 fail-soft 필수.
- claim 키/형 불일치 → 도메인 게이트가 entitlement 인식 못 함 → AC-6.
- tenantId 역조회 누락 → 전체 구독 over-fetch → 타테넌트 도메인 entitlement 누설(보안) → AC-1 정확 필터.

---

# Implementation Design Notes

- 기존 `AccountServiceClient` 3 메서드 패턴(Bearer + resilience4j accountService) 그대로 4번째 추가. customizer 는 `AccountServicePort` 주입 + try/catch fail-soft.
- cc 스킵 = 재귀 안전성의 핵심. 코드/주석에 명시.
- CI Linux GAP Integration(Testcontainers account + WireMock) 권위. 로컬은 두 서비스 compileJava+compileTestJava + 관련 단위.
- 구현 = Opus.

---

# Notes

- ADR-MONO-019 § 3.3 keystone(step 1 BE-322 producer 모델 → step 3 4-도메인 consumer 게이트 → **본 task = producer claim populate** → step 2 실 고객 시드 → E2E 활성화 → step 4 cleanup). 본 task 머지 후 step 2 시드 + E2E 가 entitlement-trust 를 실제 cross-domain 으로 증명.
