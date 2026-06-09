# Task ID

TASK-FAN-BE-009

# Title

**membership-service v2 implementation — skeleton + domain + PG mock + endpoints + internal access-check + outbox + infra (FAN-BE-008 spec 의 구현).** TASK-FAN-BE-008 이 확정한 `specs/services/membership-service/*` + `contracts/http/membership-api.md` + `contracts/events/fan-membership-events.md` 를 source-of-truth 로 신규 `fan-platform/apps/membership-service` 를 부트스트랩. Layered + 명시적 상태기계(community/artist 컨벤션), Postgres `fanplatform_membership`, Kafka outbox. **핵심**: `Membership` 구독 aggregate(상태기계 ACTIVE/CANCELED + read-time 만료 + 티어 위계 PREMIUM⊇MEMBERS_ONLY) + 결정적 PG mock(`PaymentGatewayPort` + `tok_decline` sentinel) + 4 public 엔드포인트(subscribe/cancel/list/detail, Idempotency-Key) + **1 internal `GET /internal/membership/access`**(community `MembershipChecker.hasAccess(accountId,tier,tenantId)→boolean` 의 원격 짝, fail-closed, workload-identity) + outbox 라이프사이클 이벤트(activated/canceled) + 인프라 배선(settings/package.json/Dockerfile/docker-compose+Traefik/gateway route/CI path filter/Flyway V1). community-service 어댑터 교체(`HttpMembershipChecker`)=FAN-BE-010 별건.

# Status

done

# Owner

backend-engineer (dispatched, model=opus — 신규 서비스 부트스트랩: 구독 상태기계 + PG mock + workload-identity internal 보안체인 + outbox + 인프라; dispatcher 독립 재검증 + Testcontainers IT 권위 게이트)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- deploy
- test
- onboarding

---

# Dependency Markers

- **implements (spec, SoT)**: TASK-FAN-BE-008 (`specs/services/membership-service/{architecture,overview,data-model,dependencies,observability}.md` + `contracts/http/membership-api.md` + `contracts/events/fan-membership-events.md`, main `8b371c88`). architecture.md = 마스터 설계(상태기계/접근의미/PG mock/internal 계약/tenant 3-layer/outbox/Failure Modes/Testing/Deploy deps 全).
- **structural template**: `fan-platform/apps/artist-service`(build.gradle/소스셋/integrationTest 태스크/Dockerfile/docker-compose/CI 엔트리) + `community-service`(Layered + 상태기계 + outbox + tenant 3-layer + `MembershipChecker` 포트 정의처). Redis 는 omit(architecture forbidden deps — community build.gradle 에서 redis 제거).
- **internal auth**: ADR-MONO-005 workload identity(IAM `client_credentials` JWT) — `/internal/**` 전용 보안체인. iam-integration.md 참조.
- **forward (NOT here)**: community-service `HttpMembershipChecker` 어댑터 교체(stub 대체)=**TASK-FAN-BE-010**; 멤버십 게이트/구독 UI=FAN-FE; notification-service(membership 이벤트 소비)=PROJECT § v2.
- **decision (user, 2026-06-09)**: 다음 작업 = FAN-BE-009 membership-service 구현.
- [[feedback_spring_boot_diagnostic_patterns]] §14(Testcontainers IT 권위) §15(micros clock — data-model 선반영) §16(CHECK allow-list — data-model 선반영) §17(MySQL CHECK 단언 — 단 여긴 Postgres, CHECK 위반 단언은 Postgres `DataIntegrityViolationException` 일 수 있음, IT 에서 실측). [[project_fan_platform_v1_complete]] [[env_gap_docker_host_prebuilt_jar_redeploy_trap]]

# Goal

membership-service 를 spec 대로 구현해 구독/접근제어 백엔드를 가동 — fan 이 멤버십 구독(PG mock), 취소, 조회하고, community-service 가 호출할 internal access-check 가 작동. FAN-BE-010(어댑터 교체)이 이 서비스를 가리키면 실 게이트 강제 완성.

# Scope

## In Scope (fan-platform/apps/membership-service 신규 + 인프라 배선)

architecture.md § Package Layout 전체 + Deploy Dependencies 전체:

- **skeleton**: `MembershipServiceApplication` + build.gradle(artist-service 미러, **Redis 제외**) + application.yml(+ test/prod profile) + Dockerfile(host-prebuilt jar 패턴, [[env_gap_docker_host_prebuilt_jar_redeploy_trap]]).
- **domain**: `Membership`(@Entity, 상태기계 통한 전이만) + `MembershipTier`(MEMBERS_ONLY/PREMIUM) + `MembershipStatus`(ACTIVE/CANCELED) + `MembershipStateMachine`(subscribe→ACTIVE, ACTIVE→CANCELED, 멱등 re-cancel no-op, 위반→`InvalidStateTransitionException`) + `AccessPolicy`(tierGrants 행렬 + read-time window 평가, fail-closed) + `PaymentGatewayPort` + `MembershipRepository` 포트 + `TenantContext`.
- **application**: `SubscribeUseCase`(Idempotency-Key T1 + PG mock authorize → ACTIVE, decline→no row/422) + `CancelMembershipUseCase`(멱등) + `List/GetMembershipUseCase`(tenant+account scoped) + `CheckAccessUseCase`(hasAccess 계산) + `MembershipEventPublisher`(outbox, 유일 producer 경로) + `ActorContext`.
- **presentation**: `MembershipController`(/api/fan/memberships subscribe/cancel/list/detail) + `InternalAccessController`(/internal/membership/access) + dto(`{data,meta}` 봉투, community 동형) + `GlobalExceptionHandler`(에러코드→status) + `TenantClaimEnforcer` 필터.
- **infrastructure**: JpaConfig + ClockConfig(`ClockPort.now().truncatedTo(MICROS)` §15) + JPA 어댑터 + `MembershipOutboxPollingScheduler`(libs:java-messaging) + `MockPaymentGatewayAdapter`(결정적: `tok_decline`→decline, else approve+`pgmock_<uuid>`) + security(end-user OAuth2 RS + AllowedIssuers/TenantClaim validators; **`/internal/**` 별도 보안체인=workload-identity client_credentials JWT**).
- **migration** `db/migration/membership/V1__init.sql`: `memberships`(data-model.md 스키마 — tier/status CHECK allow-list §16, valid_from/to TIMESTAMPTZ, version) + `idempotency_keys`(복합 PK + request_fingerprint) + `outbox` + `processed_events`(libs:java-messaging 스키마). 2 인덱스(tenant-leading).
- **인프라 배선**: settings.gradle include(`projects:fan-platform:apps:membership-service`) + package.json 단축스크립트 + docker-compose(membership-service + `fanplatform_membership` Postgres, expose-only, Traefik `fan-platform.local`) + **gateway-service route**(`/api/v1/memberships/**`→`/api/fan/memberships/**`; `/internal/**` 미노출) — gateway-service 의 application.yml/route 설정 + docker-compose + CI workflow path filter(pure-positive `code-changed`, MONO-074/075 negation 금지, membership-service 엔트리 추가).
- **tests**(architecture.md § Testing Strategy): unit(StateMachine/AccessPolicy/MockPG/Subscribe/Cancel/CheckAccess/EventPublisher/validators/TenantEnforcer) + slice(@WebMvcTest Membership/InternalAccess) + IT(@Tag integration, Testcontainers Postgres+Kafka, WireMock JWKS): subscribe→ACTIVE→cancel happy / decline→422-no-row / 멱등 subscribe / access(티어위계/만료/취소/cross-account/cross-tenant/DB-down fail-closed) / multi-tenant 격리 / outbox relay / internal-auth(workload-identity 200, end-user 403, no-token 401) / health 200.

## Out of Scope

- **community-service 어댑터 교체**(`HttpMembershipChecker` + workload-identity token provider) = TASK-FAN-BE-010. (이 task 는 membership-service 의 internal 엔드포인트 *제공*만; community 는 아직 AlwaysAllow stub 유지.)
- 멤버십 게이트/구독 **UI** = FAN-FE.
- **notification-service**(membership 이벤트 소비) = PROJECT § v2. (이 task 는 producer-only outbox.)
- 실 PG 연동(mock 만). `fan.membership.expired.v1`(read-time 만료, scheduler 없음 — forward-declared 미발행).
- 다른 fan 서비스(community/artist) 도메인 변경.
- **데모 working-tree 파일 금지**: `projects/iam-platform/apps/auth-service/**`, `tests/federation-hardening-e2e/**`, `tests/fulfillment-demo/**`, 루트 `compact-*.{log,ps1}` 는 **건드리지 말 것**(미커밋 데모 스택 — staging 시 선택적, `git add -A` 금지).

# Acceptance Criteria

- [ ] **AC-1** `POST /api/fan/memberships`(Idempotency-Key) → PG mock approve → `Membership` ACTIVE(windowed, paymentRef) + `fan.membership.activated.v1` outbox; `tok_decline` → 422 PAYMENT_DECLINED + no row. 멱등 재요청(동일 key+payload) → 동일 membership(중복행 0); 다른 payload → 409 IDEMPOTENCY_KEY_CONFLICT.
- [ ] **AC-2** `POST /{id}/cancel` → ACTIVE→CANCELED(canceledAt + `fan.membership.canceled.v1`); 이미 CANCELED → 멱등 no-op 200(이벤트 0); 미지/cross-account/cross-tenant id → 404.
- [ ] **AC-3** `GET /api/fan/memberships`(+`/{id}`) → caller(account+tenant) scoped; cross-tenant → 빈/404.
- [ ] **AC-4** `GET /internal/membership/access?accountId=&tier=&tenantId=` → `{allowed}` = ACTIVE ∧ now∈window ∧ tierGrants. PREMIUM→MEMBERS_ONLY allowed, MEMBERS_ONLY→PREMIUM deny, 만료/취소/무행/cross-tenant deny. **fail-closed**(DB 오류→allowed=false). community `MembershipChecker.hasAccess(accountId,tier,tenantId)→boolean` 와 파라미터/의미 1:1.
- [ ] **AC-5** `/internal/**` = workload-identity(client_credentials JWT) 보안체인; end-user 토큰→403, no-token→401, gateway 미노출. end-user 경로(`/api/fan/**`) = tenant 3-layer(gateway/JwtDecoder/TenantClaimEnforcer).
- [ ] **AC-6** Flyway V1(memberships + idempotency_keys + outbox + processed_events; tier/status CHECK §16; valid_from/to micros §15). `ddl-auto=validate`. H2 금지.
- [ ] **AC-7** 인프라 배선: settings.gradle/package.json/Dockerfile/docker-compose/Traefik/gateway route/CI path filter(pure-positive). `./gradlew :projects:fan-platform:apps:membership-service:check` GREEN(unit+slice). Testcontainers IT(CI Linux) 전 시나리오 GREEN(권위 §14).
- [ ] **AC-8** outbox 유일 producer 경로(`MembershipEventPublisher`); 직접 Kafka/OutboxWriter 호출 0. tenant 모든 쿼리 scoped.

# Related Specs

- `specs/services/membership-service/*`(architecture SoT + 4 companion) + `membership-api.md` + `fan-membership-events.md`. community-service architecture.md(§ Visibility Tiers + MembershipChecker 포트). gateway-service architecture.md(route). iam-integration.md. rules/traits/{transactional(T1/T4/T5),multi-tenant(M2),integration-heavy(I3/I8)}.md. platform/{service-types/rest-api,event-driven-policy,testing-strategy}.md. ADR-MONO-005.

# Related Contracts

- serve: `membership-api.md`(4 public + 1 internal). produce: `fan-membership-events.md`(activated/canceled.v1). consume: none. internal-auth: ADR-MONO-005.

# Edge Cases

- PG decline(`tok_decline`) → 422, no row, no event.
- 멱등 subscribe(동일 key+payload) → 동일 결과; 다른 payload → 409.
- planMonths<1 → 422/400 VALIDATION_ERROR.
- 만료(now>validTo): access deny, status 는 ACTIVE 유지(read-time, no stored EXPIRED §15/§16 가드).
- 티어위계: PREMIUM→MEMBERS_ONLY allowed; MEMBERS_ONLY→PREMIUM deny.
- cancel 멱등; optimistic-lock race → 409.
- cross-tenant/cross-account access → deny/404(누수 없음).
- internal 엔드포인트 end-user 토큰 → 403; DB down → fail-closed allowed=false.
- 미래 tier/status 값(마이그레이션 없이) → DB CHECK 거부(§16; IT 적발, Postgres).

# Failure Scenarios

- 상태기계 우회(직접 status setter) → 금지(전이는 MembershipStateMachine 경유). unit 검증.
- subscribe 가 decline 에도 행 생성 → §State Machine 위반. Guard: AC-1 IT(decline→no row).
- internal 보안체인이 end-user 토큰 허용 → workload-identity 우회. Guard: AC-5 IT(end-user→403).
- CHECK/micros 누락 → Docker-free :check 거짓 GREEN; Testcontainers IT 적발(§14/§15/§16, data-model 선반영).
- outbox 우회 직접 Kafka → E5/I8 위반. grep 게이트.
- 데모 working-tree 오염 커밋 → 금지(선택적 staging).

# Test Requirements

- architecture.md § Testing Strategy 전체(unit 11종 + slice 2 + IT 8종). IT CI Linux Testcontainers = 권위 게이트.
- `:membership-service:check` GREEN(unit+slice, Docker-free). `:membership-service:integrationTest` GREEN(CI). H2 금지. outbox grep(직접 Kafka 0).

# Definition of Done

- [ ] skeleton + domain + application + presentation + infrastructure + V1 migration(architecture § Package Layout 전체).
- [ ] 4 public + 1 internal 엔드포인트; PG mock; outbox activated/canceled; tenant 3-layer + workload-identity internal.
- [ ] 인프라 배선(settings/package.json/Dockerfile/compose/Traefik/gateway route/CI filter).
- [ ] internal access-check ↔ community MembershipChecker 1:1(AC-4).
- [ ] `:check` GREEN; Testcontainers IT GREEN(전 시나리오).
- [ ] 데모 working-tree 무오염.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus(backend-engineer dispatch — 신규 서비스 부트스트랩: 구독 상태기계 + PG mock + workload-identity 보안체인 + outbox + 인프라 멀티파일; dispatcher 독립 재검증 + Testcontainers IT 권위). 사용자 "FAN-BE-009 membership-service 구현" 선택. 메타: ① FAN-BE-008 spec(architecture.md SoT) 의 충실 구현 — artist-service 구조 미러(Redis 제외) + community Layered/상태기계/outbox/tenant-3-layer 패턴. ② **internal access-check 가 community `MembershipChecker.hasAccess` 의 원격 짝**(1:1, fail-closed, workload-identity) — FAN-BE-010 어댑터 교체의 drop-in 타겟. ③ §15(micros clock)+§16(CHECK allow-list) data-model 선반영 → Testcontainers IT 권위 게이트(§14)가 Docker-free :check 미포착분 적발. ④ 3-task 수직 슬라이스 2/3(spec 008 → **impl 009** → 어댑터 010). ⑤ 데모 working-tree(iam auth roles 패치/federation compose/fulfillment seed) 무오염 — 선택적 staging. [[project_fan_platform_v1_complete]] [[project_adr005_workload_identity_complete]] [[feedback_spring_boot_diagnostic_patterns]] [[project_monorepo_template_strategy]]
