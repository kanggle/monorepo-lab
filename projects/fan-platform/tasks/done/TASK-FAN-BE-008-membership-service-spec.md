# Task ID

TASK-FAN-BE-008

# Title

**membership-service v2 bootstrap — SPEC/design increment (architecture + contracts; impl = FAN-BE-009, community-service adapter swap = FAN-BE-010).** fan-platform PROJECT.md § v2 가 `membership-service`(rest-api, "멤버십/구독/프리미엄 접근 제어 (PG 통합 mock)")를 forward-declare 했고, community-service 의 `MEMBERS_ONLY`/`PREMIUM` 콘텐츠 티어는 현재 `AlwaysAllowMembershipChecker`(항상 통과 + WARN) stub 으로 **미강제** 상태(`@ConditionalOnMissingBean(MembershipChecker.class)` 훅은 이미 코딩됨). 이 task 는 **새 서비스의 아키텍처 + 계약을 설계**(specs/services/membership-service/* + contracts/http/membership-api.md + contracts/events/fan-membership-events.md + PROJECT/gateway/gap-integration 노트) — 구현 전 source-of-truth 확정(HARDSTOP-09 준수, erp spec-PR-우선 선례 동형). fan-platform 은 ADR 디렉터리 부재 → 결정은 service `architecture.md` 가 담지(기존 community/artist/gateway 동일 컨벤션); PROJECT.md § v2 가 forward-declaration 권위라 신규 ADR 불요.

# Status

done

# Owner

architect (dispatched, model=opus — 신규 서비스 아키텍처 설계: 구독 상태기계 + PG mock + 서비스간 access-check 계약 + tenant 격리; dispatcher 독립 재검증)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- event
- onboarding

---

# Dependency Markers

- **realises**: fan-platform PROJECT.md § Service Map v2 (`membership-service` rest-api — 멤버십/구독/프리미엄 접근 제어 PG mock) + community-service architecture.md § Visibility Tiers (MEMBERS_ONLY/PREMIUM → `MembershipChecker.hasAccess`; v1 stub = AlwaysAllow, "Replace via `@ConditionalOnMissingBean` in v2") + FAN-BE-002 § Out of Scope (membership-service v2).
- **integration contract (existing, MUST honor)**: `community-service` `domain/membership/MembershipChecker.hasAccess(accountId, tier, tenantId) → boolean`(fail-closed). 이 task 의 internal access-check 계약이 그 어댑터(FAN-BE-010)의 원격 짝.
- **service-to-service auth**: ADR-MONO-005 workload identity (GAP client_credentials JWT) — community→membership internal call 은 그 패턴 사용(설계에 명시; 구현은 FAN-BE-009/010). [[project_adr005_workload_identity_complete]]
- **forward (NOT this task)**: 구현=**TASK-FAN-BE-009**(membership-service skeleton + domain + PG mock + endpoints + outbox + infra); community-service `HttpMembershipChecker` 어댑터 교체=**TASK-FAN-BE-010**; 멤버십 게이트/구독 UI=FAN-FE(후속); notification-service(membership 이벤트 소비)=PROJECT.md § v2 별건.
- **decision (user, 2026-06-06)**: 비-erp 다양화 → fan membership-service v2.
- 선례: community-service architecture.md(rest-api + 상태기계 + tenant 3-layer + outbox 템플릿), gateway-service architecture.md(라우팅/JWT), gap-integration.md(OIDC). bootstrap 선례=FAN-BE-001/002.

# Goal

membership-service 의 아키텍처·데이터모델·HTTP/이벤트 계약을 구현 가능한 수준으로 확정 — 구독 상태기계, PG mock 결제 경로, community-service 가 호출할 internal access-check 계약, tenant 격리, outbox 멤버십 라이프사이클 이벤트(notification v2 forward). 이 spec 이 FAN-BE-009 구현 + FAN-BE-010 어댑터의 source of truth.

# Scope

## In Scope (spec/docs authoring only — NO production code)

작성 산출물:

1. **`specs/services/membership-service/architecture.md`** (community-service architecture.md 템플릿 답습):
   - Identity (rest-api 단일, Layered + 명시적 상태기계, Postgres `fanplatform_membership`, tenant=fan-platform, Kafka outbox).
   - **도메인 모델**: `Membership`(구독) aggregate — accountId(fan), tier(MEMBERS_ONLY|PREMIUM), status(상태기계), validFrom/validTo(windowed), planMonths, paymentRef, createdAt, version. **티어 위계**: PREMIUM ⊇ MEMBERS_ONLY(PREMIUM 구독은 MEMBERS_ONLY 접근도 부여).
   - **상태기계**(transactional T4): subscribe(PG mock 성공) → ACTIVE(windowed) → cancel → CANCELED(terminal); 만료=read-time(now ∉ window → 비활성, delegation `isActiveAt` 선례). PENDING_PAYMENT 중간상태 여부 명시(권장: PG mock 동기 성공 → 직접 ACTIVE, 실패 → 미생성/422).
   - **PG mock**: `PaymentGatewayPort` + mock 어댑터(결정적 — 예: test token/amount 규칙으로 승인/거절). subscribe = Idempotency-Key(T1) + PG mock authorize → ACTIVE. PG 통합은 mock 경계 명시(외부 실 PG 미연동).
   - **access-check**(핵심): internal 엔드포인트 `GET /internal/membership/access?accountId=&tier=&tenantId=` → `{ allowed }` — community-service `MembershipChecker.hasAccess` 의 원격 계약. 의미=ACTIVE 구독이 요구 tier 이상이고 now ∈ window. **fail-closed**(인프라 오류 시 deny — MembershipChecker 포트 계약). 인증=GAP client_credentials JWT(ADR-005 workload identity), `/internal/**` 전용.
   - tenant 격리 3-layer(gateway + service JwtDecoder + TenantClaimEnforcer; community 동형). 모든 쿼리 tenant-scoped.
   - **outbox 이벤트**(producer-only forward, notification v2 소비): `fan.membership.activated.v1` / `fan.membership.canceled.v1` / `fan.membership.expired.v1`(만료는 read-time 이므로 발행 방식 명시 — 권장: v1 미발행/forward 또는 배치 추후; activated/canceled 는 전이 시 발행). BaseEventPublisher/outbox 패턴(community 동형).
   - Failure Modes / Testing Strategy(unit 상태기계·PG mock·access 계산, slice 컨트롤러, IT Testcontainers Postgres+Kafka+WireMock JWKS — subscribe/cancel/access happy + fail-closed + tenant 격리).
   - **Deploy dependencies**(mention-only, FAN-BE-009 가 구현): settings.gradle include, package.json, Dockerfile, docker-compose + Traefik `fan-platform.local` 라우팅, gateway-service 라우팅(`/api/fan/memberships/**` → membership-service), CI path filter(pure-positive, MONO-074/075 negation 금지), Flyway V1.
2. **`specs/services/membership-service/overview.md` + `data-model.md` + `dependencies.md` + `observability.md`**(기존 community/artist 서비스 4종 세트 동일 포맷).
3. **`specs/contracts/http/membership-api.md`**: `POST /api/fan/memberships`(subscribe, Idempotency-Key, PG mock), `POST /api/fan/memberships/{id}/cancel`, `GET /api/fan/memberships`(내 멤버십), `GET /api/fan/memberships/{id}`, **internal** `GET /internal/membership/access`(community 호출, workload-identity). 에러코드(MEMBERSHIP_*: PAYMENT_DECLINED 422, MEMBERSHIP_NOT_FOUND 404, …) + 봉투 형식(기존 community-api 동형).
4. **`specs/contracts/events/fan-membership-events.md`**: membership 라이프사이클 outbox 이벤트(activated/canceled[/expired]) payload + 봉투(community-events 동형) + 소비자=notification-service v2(forward).
5. **노트 갱신**: PROJECT.md § Service Map(v2 membership-service "spec 확정, 구현 FAN-BE-009" 표식) + gateway-service architecture.md(라우팅 항목에 memberships 추가 — 또는 구현 task 가 한다고 명시) + gap-integration.md(membership-service 도 GAP OIDC RS + workload-identity 소비 노트).

## Out of Scope

- **production code 0** — 이 task 는 spec/docs 만. skeleton/도메인/엔드포인트/infra = TASK-FAN-BE-009.
- **community-service 어댑터 교체**(`HttpMembershipChecker` 가 stub 대체) = TASK-FAN-BE-010(별 서비스 + workload-identity provider).
- **frontend 멤버십 게이트/구독 UI** = FAN-FE 후속.
- **notification-service**(membership 이벤트 소비) = PROJECT.md § v2 별건.
- **실 PG 연동** — mock 경계만(설계). admin-service 무관.
- 신규 ADR — fan-platform 컨벤션(ADR 부재, architecture.md 가 결정 담지); PROJECT.md § v2 가 forward-declaration 권위.

# Acceptance Criteria

- [ ] **AC-1** `specs/services/membership-service/architecture.md` 가 community-service 템플릿 깊이로 작성: Identity / 상태기계(subscribe→ACTIVE→cancel→CANCELED + read-time 만료) / 티어 위계(PREMIUM⊇MEMBERS_ONLY) / PG mock 경계 / internal access-check 계약(fail-closed, workload-identity) / tenant 3-layer / outbox / Failure Modes / Testing / Deploy deps.
- [ ] **AC-2** `membership-api.md` 가 4 public + 1 internal 엔드포인트 + 에러코드 + 봉투를 정의; internal access-check 가 community `MembershipChecker.hasAccess(accountId,tier,tenantId)→boolean` 와 1:1 대응(파라미터/의미/ fail-closed).
- [ ] **AC-3** `fan-membership-events.md` 가 activated/canceled[/expired] outbox 이벤트(payload+봉투, `.v1`) 정의 + 소비자=notification v2 forward 명시.
- [ ] **AC-4** overview/data-model/dependencies/observability 4종 세트 작성(기존 서비스 포맷 일치).
- [ ] **AC-5** PROJECT.md § v2 + gateway/gap-integration 노트 갱신(membership-service spec 확정 + 라우팅/인증 반영 또는 구현 task 위임 명시).
- [ ] **AC-6** production code 0(spec/docs only). 신규 ADR 0(컨벤션 준수). 구현/어댑터/UI 는 FAN-BE-009/010/FE 로 forward-declare.
- [ ] **AC-7** doc 정합: community `MembershipChecker` 포트 시그니처 불일치 0, 기존 community-service architecture.md § Visibility Tiers 의 "v2 will hard fail-closed" 와 정합(PREMIUM 도 실강제).

# Related Specs

- community-service architecture.md(§ Visibility Tiers + MembershipChecker port), gateway-service architecture.md, gap-integration.md, PROJECT.md § v2. rules/traits/{transactional(T1/T4),multi-tenant(M2),integration-heavy(I3/I8)}.md. platform/{architecture-decision-rule,service-types/rest-api,event-driven-policy,testing-strategy}.md.

# Related Contracts

- 신규 `membership-api.md`(HTTP) + `fan-membership-events.md`(events). community-api.md(봉투/에러 포맷 선례). ADR-MONO-005(workload identity, internal 인증).

# Edge Cases (설계가 다뤄야)

- PG mock 결제 거절 → subscribe 422 PAYMENT_DECLINED(구독 미생성).
- 중복 subscribe(Idempotency-Key 재사용) → 멱등 동일 결과(T1).
- 만료(now > validTo): read-time 비활성(access deny); status 는 ACTIVE 유지 vs EXPIRED 전이 — 설계 결정 명시(권장: delegation 선례=상태는 lifecycle, 만료는 read-time 계산).
- cancel 후 access → deny. 이미 CANCELED cancel → 멱등 no-op.
- 티어 위계: PREMIUM 구독자가 MEMBERS_ONLY 요청 → allowed; MEMBERS_ONLY 구독자가 PREMIUM 요청 → deny.
- access-check 인프라 오류(membership-service down) → community 어댑터 fail-closed deny(포트 계약; 어댑터는 FAN-BE-010, 계약은 여기 명시).
- cross-tenant access-check(tenantId 불일치) → deny/404.

# Failure Scenarios

- spec 이 internal access-check 계약을 community `MembershipChecker` 와 어긋나게 정의 → FAN-BE-010 어댑터 불가. Guard: AC-2(시그니처/의미 1:1) + AC-7(포트 정합).
- PG mock 경계를 모호하게 → 구현이 실 PG 연동으로 오해. Guard: architecture.md 가 mock 결정적 경계 명시.
- 만료 의미 미결정 → 구현이 상태기계/배치 혼선. Guard: read-time vs 전이 명시.
- HARDSTOP-09: 신규 서비스 아키텍처 미결정 상태로 impl 진입 방지 = 이 spec 의 존재 이유.

# Test Requirements

- spec/docs task — 코드 테스트 0. **검증**: ① community `MembershipChecker` 포트 시그니처 ↔ internal access-check 계약 1:1 대조. ② 기존 fan-platform 서비스 spec 4종 세트 포맷 일치(community/artist 대조). ③ doc 내부 링크/참조 유효(gateway 라우팅, gap-integration, PROJECT.md). ④ markdown lint(있으면).

# Definition of Done

- [ ] architecture.md + overview + data-model + dependencies + observability(membership-service) 작성.
- [ ] membership-api.md(4 public + 1 internal) + fan-membership-events.md 작성.
- [ ] PROJECT.md § v2 + gateway/gap-integration 노트 갱신.
- [ ] production code 0 / ADR 0 / FAN-BE-009·010·FE forward-declared.
- [ ] community MembershipChecker 포트 정합(AC-7).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim; docs-lane CI).

---

분석=Opus 4.8 / 구현 권장=architect(Opus dispatch — 신규 서비스 아키텍처 설계; dispatcher 독립 재검증). 사용자 "fan membership-service v2"(비-erp 다양화) 선택. 메타: ① **신규 서비스 = spec 먼저**(HARDSTOP-09 / source-of-truth; erp spec-PR→impl-PR 선례 동형). ② fan-platform ADR 부재 → architecture.md 가 결정 담지(community/artist/gateway 컨벤션), PROJECT.md § v2 = forward-declaration 권위 → 신규 ADR 불요. ③ 핵심 계약 = internal access-check 가 기존 community `MembershipChecker.hasAccess(accountId,tier,tenantId)→boolean`(fail-closed, `@ConditionalOnMissingBean` 훅) 의 원격 짝 — 1:1 대응이 FAN-BE-010 어댑터 교체의 전제. ④ 3-task 수직 슬라이스(spec FAN-BE-008 → impl FAN-BE-009 → community 어댑터 FAN-BE-010), frontend 게이트 UI 후속. ⑤ 서비스간 인증 = ADR-005 workload identity(GAP client_credentials) 재사용. [[project_fan_platform_v1_complete]] [[project_adr005_workload_identity_complete]] [[project_monorepo_template_strategy]]
