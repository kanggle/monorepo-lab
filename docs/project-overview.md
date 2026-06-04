# Project Overview — monorepo-lab

> **목적**: 본 monorepo 전체 (플랫폼 전략 + 5 프로젝트 + 공유 인프라) 의 단일 진입 스냅샷.
> **갱신 시점**: 2026-06-05 (마지막 의미 있는 변화: Phase 5 LAUNCHED 2026-05-13 + Phase 6 finance/erp v1 양쪽 종결 2026-05-19/20 + Phase 7 console-bff LIVE 2026-05-20 + Phase 8 federation hardening MVP 2026-05-25/26 + portfolio architecture.md spec coverage 8/8 cluster 정합 2026-05-28 + **post-Phase-8 multi-tenant SaaS 실체화** 2026-05-31~06-02: ADR-MONO-019/020/021 ACCEPTED [customer-tenant entitlement-trust + operator N:M assume-tenant + `account_type` claim] + console overview consolidation [PC-FE-034] + console-web PR CI gate [MONO-166] + internal-system trait rules [MONO-167] + **console full-stack 로컬 데모 [MONO-170] 가 표면화한 런타임 producer↔consumer drift 청소 wave** 2026-06-02~03: ADR-MONO-020 D6 step4 "의도적 비실행" disposition [MONO-169 → ADR-020 사실상 완료] + SCM/WMS 런타임 federation 정합 8-fix [ERP-BE-006 / SCM-BE-020 decimal-string 계약 / MONO-171 seed UUID / BE-331·BE-332 read-model 42P18 PostgreSQL 쿼리 class / BE-333·BE-334 outbound 기동·security 전제층 / SCM-BE-021 read-model 422→500 관측성] + frontend Docker 빌드 캐시 최적화 [PC-FE-035 / FE-072, 60~82% 빌드시간↓] + **erp read-model-service 라이브화 + org_scope 데이터-스코프 3-stage authz 사슬** 2026-06-03~05: erp **read-model-service 첫 증분** [ERP-BE-007 — masterdata 변경이벤트 구독 → employee org-view 투영; §2.8 가 v2-deferred 로 잘못 분류하던 사실오류 교정] + 콘솔 통합 read 카드 [PC-FE-049] + **org_scope 3-stage** [BE-336 위임 `erp.write` scope / BE-337 assume-tenant `["*"]` bridge / BE-338 멤버십 출처 `operator_tenant_assignment.org_scope` / ERP-BE-008 erp subtree 소비 / BE-339 admin 관리 API / PC-FE-050 콘솔 설정 UI] + 콘솔 erp 마스터 write parity [PC-FE-046~048] + operators/audit active-tenant 스코핑 [MONO-175 / PC-FE-043] + self-service account 이동 [PC-FE-045] + demo redpanda 브로커 [MONO-174]).
> **위치**: `docs/project-overview.md` — `docs/adr/` (결정 기록) · `docs/guides/` (휴먼 워크플로우 가이드) 와 sibling.

---

## 1. 한 줄 요약

**multi-domain 백엔드/풀스택 포트폴리오 monorepo**. 5 도메인 프로젝트가 단일 라이브러리 (rules / platform / .claude / libs) 를 공유하면서 동거하고, 라이브러리가 stabilise 된 시점에 별도 Template 레포로 추출되는 **Discovery → Distribution** 전략을 따른다 ([TEMPLATE.md](../TEMPLATE.md)).

- **현재 단계**: Phase 8 federation hardening **COMPLETE** (2026-05-28, [ADR-MONO-018](adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md) ACCEPTED) — cross-product e2e MVP (2026-05-26) + D4 observability federation ([ADR-MONO-007a](adr/ADR-MONO-007a-trace-layer.md) trace layer, MONO-142~147) + D5 multi-tenant isolation regression 모두 종결. Phase 5 = **LAUNCHED 2026-05-13** ([ADR-MONO-003b](adr/ADR-MONO-003b-phase-5-launch-criteria.md) ACCEPTED, `kanggle/project-template` public + `is_template: true`). Phase 6 = **COMPLETE** finance + erp v1 양쪽 종결 2026-05-19/20 ([ADR-MONO-008](adr/ADR-MONO-008-finance-platform-bootstrap.md) / [ADR-MONO-016](adr/ADR-MONO-016-erp-platform-bootstrap.md) ACCEPTED). Phase 7 = **LIVE** console-bff Operator Overview + Domain Health 2026-05-20 ([ADR-MONO-017](adr/ADR-MONO-017-platform-console-bff-architecture.md) ACCEPTED). 5/5 backend domains (gap·wms·scm·finance·erp) federated via platform-console.
- **AI-driven 운영**: Claude Code 기반 rule-driven · spec-driven · task-driven 워크플로우. 80+ skill / 12+ specialized agent / 도메인-trait 자동 dispatch.

---

## 2. 프로젝트 카탈로그 (7 도메인 + platform-console)

각 프로젝트는 [`projects/<name>/PROJECT.md`](../projects/) 에 `domain` + `traits` 를 선언하고, 그에 따라 [`rules/domains/`](../rules/domains/) + [`rules/traits/`](../rules/traits/) 의 규칙 layer 가 자동으로 활성화된다.

### 2.1 [wms-platform](../projects/wms-platform/PROJECT.md) — 창고 관리 시스템 (v1 ✅)

- **domain**: `wms` · **traits**: `transactional`, `integration-heavy`
- **포지션**: 첫 dogfood 프로젝트, master rule library 의 stress test
- **상태**: v1 종결 (2026-05-09 admin-service v1 closure 포함)
- **service map (7)**:

| Service | Type | 책임 |
|---|---|---|
| `gateway-service` | rest-api | OIDC token 검증 + tenant gate (`tenant_id=wms`) + rate limit |
| `master-service` | rest-api | 6 master entity (Warehouse/Zone/Location/SKU/Partner/Lot) + Lot expiry batch |
| `inventory-service` | rest-api | W1~W5 invariant + receive/adjust/transfer + W4/W5 reservation lifecycle |
| `inbound-service` | rest-api | ASN / inspection / putaway 흐름 + ERP webhook + role-grant W2 |
| `outbound-service` | rest-api | order / picking / packing / shipping + saga orchestrator + TMS post-commit |
| `notification-service` | event-consumer | 6 source-topic Kafka 소비 + Slack adapter (Resilience4j CB/retry) |
| `admin-service` | rest-api (Layered, ⚠️ override) | CQRS read-side (4 ProjectionConsumer × 18 source-topic) + dashboard + ops |

- **architecture override**: `admin-service` 만 sibling 의 Hexagonal 에서 **Layered** 로 승격 (PROJECT.md § Overrides + service architecture.md 에 근거 명시). read-side / 단순 CRUD write surface 가 port/adapter 비용 정당화 못 함.
- **ID provider**: GAP OIDC RS256 + `tenant_id=wms` claim ([specs/integration/gap-integration.md](../projects/wms-platform/specs/integration/gap-integration.md)).
- **런타임 정합 (2026-06-03, MONO-170 데모 표면화)**: `admin-service` read-model 의 nullable temporal `:p IS NULL` 가드가 PostgreSQL `42P18`(could not determine data type) 로 unfiltered 쿼리 시 500 → `CAST(:p AS string)` 일괄 수정 (alerts BE-331 + 전 `@Query` audit BE-332 = wms 7 repo). `outbound-service` non-standalone 기동 복구 (BE-333 `@ConditionalOnMissingBean` by-type 빈충돌 exclude + V4 Flyway 정본 교정 / BE-334 `SecurityConfig` `@ConditionalOnWebApplication(SERVLET)`). 미배포 outbound IT 전체 green+CI 배선은 저-ROI 로 deferred.

### 2.2 [global-account-platform](../projects/global-account-platform/PROJECT.md) (GAP) — SaaS Identity / IdP (v1 ✅)

- **domain**: `saas` · **traits**: `transactional`, `regulated`, `audit-heavy`, `integration-heavy`, `multi-tenant`
- **포지션**: monorepo 의 **표준 OIDC IdP** (ADR-001 ACCEPTED). 모든 프로젝트가 GAP 의 RS256 access token 을 OAuth2 Resource Server 패턴으로 검증.
- **상태**: 5 backend 운영 (**backend-only IdP**). `admin-web` 운영자 콘솔은 2026-05-18 폐기 — 운영자 UI 는 통합 platform console 이 흡수 ([ADR-MONO-013](adr/ADR-MONO-013-platform-console-foundation.md) Phase 3, TASK-BE-299). 표준 `/oauth2/{authorize,token,jwks,userinfo,revoke,introspect}` + `/.well-known/openid-configuration` 노출.
- **service map (5 active + 2 frozen demo)**:

| Service | Type | 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, JWT 검증, JWKS 캐시, rate limit |
| `auth-service` | rest-api | 로그인/로그아웃, JWT 발급, refresh token 회전·재사용 탐지 |
| `account-service` | rest-api | 회원가입, 프로필, 계정 상태 기계, 테넌트 메타 |
| `security-service` | event-consumer | Kafka 보안 이벤트 + 비정상 탐지 + 감사 read-only HTTP |
| `admin-service` | rest-api | 운영자 lock/unlock, 강제 로그아웃, 감사 조회 |
| ~~`admin-web`~~ | ~~frontend-app~~ | **RETIRED 2026-05-18** — 운영자 UI 는 platform console 이 흡수 (ADR-MONO-013 Phase 3) |
| ~~`community-service`~~ | rest-api | **FROZEN** — product-layer demo (신규 기능 금지) |
| ~~`membership-service`~~ | rest-api | **FROZEN** — product-layer demo (신규 기능 금지) |

- **multi-tenancy**: row-level isolation (`accounts.tenant_id`). JWT `tenant_id` claim 으로 cross-tenant 거부. 현재 등록 tenant: `wms` / `scm` / `fan-platform` / `finance` / `erp` + B2C 기본.
- **internal provisioning**: `POST /internal/tenants/{id}/accounts:bulk` 로 enterprise 소비자 (wms/scm/finance/erp) 가 사용자 일괄 생성.
- **OIDC AS 운영 깊이 증명 (2026-05-09 closure)**: SAS public-client (PKCE) `refresh_token` rotation + reuse detection + `revoke` (custom converter + provider-side fallback) + 3 OAuth provider callback (Google/Kakao/Microsoft) 모두 main CI deterministic PASS. 13-cycle 미해결 9 deferred IT 회복 ([ADR-003](../projects/global-account-platform/docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md), [ADR-004](../projects/global-account-platform/docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md)). Cluster A 3/3 + Cluster B 1/1 + Cluster C 5/5 + token customizer bonus 1.
- **multi-tenant SaaS 실체화 (2026-05-31~06-02, [ADR-MONO-019](adr/ADR-MONO-019-platform-console-customer-tenant-model.md)/[020](adr/ADR-MONO-020-operator-multitenant-assignment.md)/[021](adr/ADR-MONO-021-account-type-claim-source.md))**: customer-tenant entitlement-trust (account `entitled_domains` keystone — BE-322/324/325) + operator↔customer **N:M assignment + assume-tenant RFC8693 token-exchange** (`operator_tenant_assignment` dual-read BE-326 + BE-327) + **`account_type` (CONSUMER\|OPERATOR) OIDC claim** (`credentials.account_type` denormalize, BE-329/330 + INT-024 e2e).
- **org_scope 데이터-스코프 authz 출처/전파 (2026-06-04~05, [ADR-MONO-020](adr/ADR-MONO-020-operator-multitenant-assignment.md) D3 amendment)**: 콘솔 도메인-write 가 3중 게이트(tenant → role/scope → **data-scope**)를 통과하도록 GAP 가 위임 scope + 데이터-스코프를 assume-tenant 토큰에 enrich — BE-336 (registered `erp.write` scope 위임 + `authorizedScopes` 전파; entitlement-trust=read visibility 와 직교한 write capability) → BE-337 (assume-tenant 토큰 `org_scope=["*"]` v1 bridge) → BE-338 (**멤버십 출처** `operator_tenant_assignment.org_scope` V0031 nullable 컬럼; `NULL ⟺ ["*"]` net-zero; 부서 subtree-root 배열) → BE-339 (admin 관리 API `GET .../operators/{id}/assignments` + `PUT .../assignments/{tenantId}/org-scope`, tenant-scope 불변식, SQL 시드 아닌 product surface 설정). erp 가 subtree-root 를 소비(ERP-BE-008), 콘솔이 설정(PC-FE-050).
- **workload identity (2026-05-30, [ADR-005](../projects/global-account-platform/docs/adr/ADR-005-service-to-service-workload-identity.md))**: 서비스 간 인증 `X-Internal-Token` → GAP `client_credentials` JWT 무중단 전환 (BE-317~321, account/security `/internal/**` = JWT 단일).

### 2.3 [ecommerce-microservices-platform](../projects/ecommerce-microservices-platform/PROJECT.md) — B2C 이커머스 (v1 ✅ 풀스택)

- **domain**: `ecommerce` · **traits**: `transactional`, `content-heavy`, `read-heavy`, `integration-heavy`
- **포지션**: 첫 풀스택 프로젝트. **분류(taxonomy) 기반 규칙 시스템의 첫 dogfood**.
- **상태**: end-to-end 운영 (12 backend + Next.js 15 storefront + admin dashboard + Playwright E2E)
- **service map (12 backend + 2 frontend)**:

| Layer | Apps |
|---|---|
| Edge | `gateway-service` |
| Identity | `auth-service`, `user-service` |
| Catalog & Search | `product-service`, `search-service` (Elasticsearch) |
| Commerce | `order-service` (saga), `payment-service`, `promotion-service` |
| Fulfillment | `shipping-service`, `notification-service` |
| Engagement | `review-service` |
| Async | `batch-worker` |
| Frontend | `web-store` (Next.js 15), `admin-dashboard` |

- **stack**: Java 21 / Spring Boot 3.4 / Postgres / Kafka / Redis / Elasticsearch / MinIO
- **GAP migration**: 향후 (TASK-MONO-020) — 현재 자체 auth-service.

### 2.4 [scm-platform](../projects/scm-platform/PROJECT.md) — 공급망 통합 (v1 ✅)

- **domain**: `scm` · **traits**: `transactional`, `integration-heavy`, `batch-heavy`
- **포지션**: **Phase 4 catalyst 도메인** ([ADR-MONO-002](adr/ADR-MONO-002-phase-4-template-extraction-trigger.md)). `batch-heavy` trait 의 첫 사용 사례.
- **상태**: v1 = 3 service skeleton + INT-001 series 완료 (2026-05-07)
- **service map (v1)**:

| Service | Type | 책임 |
|---|---|---|
| `gateway-service` | rest-api | OIDC + `tenant_id=scm` gate |
| `procurement-service` | rest-api | PO 발행/확정/취소, supplier ack, ASN 수신 |
| `inventory-visibility-service` | rest-api | cross-node 재고 가시성 (read-model, wms snapshot 구독) |

- **v2 deferred**: `supplier-service`, `demand-planning-service`, `logistics-service`, `settlement-service`, `notification-service`, `admin-service`
- **wms 와의 차별점**: wms = 단일 창고 내부 동선 (한 노드). scm = 노드들의 그래프 (조달 → 운송 → 정산).
- **콘솔 운영 read consumer 런타임 정합 (2026-06-02~03, MONO-170 데모 표면화)**: `procurement` PO read 의 decimal 필드가 Jackson number → 콘솔 `z.string()` parse-fail = decimal-string 계약 위반 → `@JsonFormat(shape=STRING)` (SCM-BE-020); `inventory-visibility` globex 시드 non-UUID id 불변식 위반 (MONO-171) + corrupt read-model 재구성이 무로깅 `422` 로 둔갑 → `ReadModelCorruptException` → `500`+log 재정정 (SCM-BE-021). 이로써 SCM 운영 카드가 acme/globex 양 테넌트 풀 렌더.

### 2.5 [fan-platform](../projects/fan-platform/PROJECT.md) — K-pop 아티스트↔팬 커뮤니티 (v1 ✅ 풀스택)

- **domain**: `fan-platform` · **traits**: `transactional`, `content-heavy`, `read-heavy`, `integration-heavy`, `multi-tenant`
- **포지션**: 두 번째 풀스택 B2C 도메인. **비대칭 콘텐츠 관계** (아티스트 1 : N 팬) 가 도메인 핵심.
- **상태**: v1 종결 (2026-05-03 backend 3 + frontend + e2e + GAP V0011 client + 19 PR 완성)
- **service map (v1)**:

| Service | Type | 책임 |
|---|---|---|
| `gateway-service` | rest-api | OIDC + `tenant_id=fan-platform` gate |
| `community-service` | rest-api | post / comment / reaction / 피드 (팔로우 기반) |
| `artist-service` | rest-api | 아티스트 프로필 + follow 관계 + fandom 메타데이터 |
| `fan-platform-web` | frontend-app | Next.js 15 lean (5~7 페이지) |

- **v2 deferred**: `membership-service` (PG 통합), `notification-service` (FCM/APNs), `admin-service` (모더레이션)

### 2.6 [platform-console](../projects/platform-console/PROJECT.md) — 통합 운영 콘솔 (v1 ✅ Phase 7 LIVE — 5/5 federated domains, 도메인 축 아님)

- **domain**: `saas` · **traits**: `multi-tenant`, `integration-heavy`, `audit-heavy` · **service_types**: `frontend-app`
- **포지션**: 포트폴리오 엔터프라이즈 스위트(gap·wms·scm + 향후 erp·finance)를 **단일 AWS/GCP-콘솔식 화면**으로 통합. [ADR-MONO-013](adr/ADR-MONO-013-platform-console-foundation.md) (ACCEPTED 2026-05-16) 부트스트랩. 6번째 프로젝트이나 도메인 축이 아닌 **가로축 콘솔**.
- **모델**: Model B — 콘솔이 *유일한 프론트엔드*. wms/scm/erp/finance 백엔드-only를 콘솔이 gateway/admin API로 렌더(런처 아님). GAP `admin-web`은 콘솔 운영자 parity 검증 후 **Phase 3 폐기 완료 (2026-05-18, TASK-BE-299)** → GAP 백엔드-only IdP 회귀.
- **상태**: ADR-MONO-013 § D6 Phase 1~6 COMPLETE + Phase 7 (`console-bff` + cross-domain dashboards) LIVE — 5/5 federated backend domains (`gap` + `wms` + `scm` + `finance` + `erp`) `available:true`. console-bff Operator Overview (PC-BE-001~003) + Domain Health (PC-BE-002) + Operator Overview finance card 12-task vertical chain (BE-304~309 producer + PC-FE-014~022 consumer + e2e harness + auth-formLogin + fixture OIDC PKCE migration, 2026-05-21~22). ADR-MONO-017 (console-bff architecture) ACCEPTED 2026-05-20.
- **Phase 8 + multi-tenant 활성화 (2026-05-25~06-02)**: federation hardening (cross-product e2e + observability trace federation + isolation regression, [ADR-MONO-018](adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md)) + **런타임 entitlement-trust active-tenant 전환** (ADR-MONO-019/020 — console active-tenant switcher → assume-tenant A↔B GREEN, MONO-158~162) + **overview 화면 통폐합** (PC-FE-034 — 통합 개요 콘솔 홈 승격 / GAP-only 개요 = GAP-card drill-down / ERP 운영 nav 추가) + **console-web PR CI gate 편입** (MONO-166 — unit/tsc/lint).
- **full-stack 로컬 데모 + 런타임 drift 청소 (2026-06-02~03, [MONO-170](../tasks/done/TASK-MONO-170-console-fullstack-local-dev-demo.md))**: 콘솔을 5/5 도메인 per-domain 운영 화면으로 실제 구동한 첫 full-stack 로컬 데모가 **producer↔consumer 런타임 drift 8건**을 표면화 → 전수 청소 (ERP-BE-006 effectivePeriod nesting / SCM-BE-020 decimal-string / MONO-171 seed / BE-331·332 read-model 42P18 / BE-333·334 outbound 전제층 / SCM-BE-021 422→500). 메타: 머지+CI GREEN ≠ 런타임 federation 정합 — full-stack 데모 구동이 mock/slice 테스트가 못 잡는 cross-service 계약·쿼리·시드 drift 의 sender. federation scm leg 경로는 MONO-162 에서 이미 정합. demo overlay 의 GAP outbox drain 을 위해 **실 redpanda 브로커** 를 federation-e2e 데모에 편입 (MONO-174).
- **erp 운영 surface 심화 + org_scope 설정 + active-tenant 스코핑 (2026-06-03~05)**: 콘솔이 erp 를 read-only 조회를 넘어 **마스터데이터 write parity** 로 운영 — 부서 write 파일럿(PC-FE-046) → 상위부서 드롭다운(PC-FE-047) → **5개 마스터 전체 create/update/retire**(PC-FE-048, 직원/직급/비용센터/거래처) + read-model **통합 read 카드**(PC-FE-049 — ERP-BE-007 read-model-service 의 employee org-view 소비, 부서경로 breadcrumb + eventually-consistent 배너) + **org_scope 설정 UI**(PC-FE-050 — reason-gated `OrgScopeDialog`, 부서 picker + 전체/선택/차단 tri-state; org_scope 사슬 [설정→GAP 저장/전파→erp 소비→read 카드] 완결). **operators/audit active-tenant 스코핑** — 콘솔 스위처가 운영자 목록(MONO-175)·감사 조회(PC-FE-043)를 활성 테넌트로 따라가도록 (effective-scope 게이트, privilege 비확장). **self-service 이동** — 비밀번호/프로필 self-service 를 operators 화면 → account 화면으로 (PC-FE-045). 한글 사유 헤더 인코딩 fix (MONO-176).
- **service map**:

| Service | Type | 책임 |
|---|---|---|
| `console-web` | frontend-app | 단일 콘솔 UI (GAP OIDC public client · data-driven 카탈로그 · 테넌트 스위처 · 도메인 운영 화면) |
| `console-bff` | rest-api | 교차 도메인 집약 API (ADR-MONO-013 Phase 7 LIVE — TASK-PC-BE-001 skeleton + § 2.4.9.1 Operator Overview + § 2.4.9.2 Domain Health) |

### 2.7 [finance-platform](../projects/finance-platform/PROJECT.md) — 비은행 금융 서비스 (v1 live — Phase 5 COMPLETE 2026-05-19/20)

- **domain**: `fintech` · **traits**: `transactional`, `regulated`, `audit-heavy` · **service_types**: `rest-api`, `event-consumer`
- **포지션**: monorepo Phase 6 **첫 Template 다운스트림 부트스트랩** ([ADR-MONO-008](adr/ADR-MONO-008-finance-platform-bootstrap.md), ACCEPTED 2026-05-18, Option C). 6번째 도메인 프로젝트, `regulated + audit-heavy` fintech 표면 동시 첫 사용 (`kanggle/finance-platform` standalone Template fork + monorepo direct-include 병존).
- **상태**: v1 live — 부트스트랩 (TASK-MONO-114, 2026-05-19) + account-service 도메인 구현 chain (TASK-FIN-BE-001 + 002/003/004 honest green-wash TRUE TERMINAL + FIN-BE-005 platform-console operator read consumer reconciliation, 2026-05-19~20) + platform-console federation live (FE-009 `/finance` console section + `console-integration-contract.md § 2.4.7` per-domain credential). ADR-MONO-013 § D6 Phase 5 COMPLETE.
- **service map (v1)**:

| Service | Type | 책임 |
|---|---|---|
| `gateway-service` | rest-api | OIDC + `tenant_id=finance` gate (account-service 활성화와 함께) |
| `account-service` | rest-api | Account 라이프사이클 — KYC / 가용·장부 잔액 hold·release·capture / 계좌 상태기계 / 자금 이동 멱등 / 불변 audit_log |

- **v2 deferred**: `ledger-service` (복식부기/GL/AP — fintech accounting 깊이, ADR-008 §D3), `wallet-service`, `kyc-service`, `notification-service`, `admin-service`
- **framing 정합**: 7축 메모리의 "분개/GL/AP accounting" 은 ADR-MONO-008 SoT 상 명시적으로 v2 (ledger-service); v1 = fintech Account/Balance/Transaction/KYC (PROJECT.md § ADR-MONO-008 vs 7축 framing 정합).
- **ID provider**: GAP OIDC RS256 + `tenant_id=finance` claim (V0017 ×2 시드: account tenant + auth client_credentials `finance-platform-internal-services-client`).
- **frontend**: 없음 — 통합 platform console 이 렌더 (ADR-MONO-013 §3.3, `frontend-app` service_type 없음).

### 2.8 [erp-platform](../projects/erp-platform/PROJECT.md) — 전사 기간계 (v1 live — Phase 6 COMPLETE 2026-05-20)

- **domain**: `erp` · **traits**: `internal-system`, `transactional`, `audit-heavy` · **service_types**: `rest-api`
- **포지션**: monorepo Phase 6 **두 번째 Template 다운스트림 부트스트랩** ([ADR-MONO-016](adr/ADR-MONO-016-erp-platform-bootstrap.md), ACCEPTED 2026-05-19, Option C). 7번째 도메인 프로젝트, `internal-system`-primary 첫 사용 (`kanggle/erp-platform` standalone Template fork + monorepo direct-include 병존; standalone fork = 사용자 셸 hand-off PENDING).
- **상태**: v1 live — 부트스트랩 (TASK-MONO-119, 2026-05-19) + masterdata-service 도메인 구현 chain (TASK-ERP-BE-001 99-file Hexagonal + MONO-124 erp-integration CI job 5/5 IT PASS + ERP-BE-002 platform-console operator read consumer reconciliation, 2026-05-20) + platform-console federation live (FE-010 `/erp` console section + `console-integration-contract.md § 2.4.8` per-domain credential). ADR-MONO-013 § D6 Phase 6 COMPLETE.
- **v1.1 라이브 (2026-06-03~05)**: **read-model-service 첫 증분** (ERP-BE-007 — masterdata 변경이벤트[부서/직원/직급/비용센터] Kafka 구독 → `processed_events` dedupe → 4 projection 테이블 → read-time employee org-view 조립[부서 ancestry walk + 비용센터 + 직급]; no-outbox terminal consumer) + 콘솔 통합 read 카드(PC-FE-049) + **org_scope subtree 데이터-스코프** (ERP-BE-008 — masterdata write subtree containment + read-model symmetric read 필터; 운영자 `org_scope` claim 의 subtree-root 를 자기 부서트리로 descendant 확장; `["*"]`/미설정=net-zero). 콘솔 마스터 write parity(PC-FE-046~048) 로 5개 마스터 create/update/retire.
- **service map (v1)**:

| Service | Type | 책임 |
|---|---|---|
| `gateway-service` | rest-api | OIDC + `tenant_id=erp` gate + internal-only 경계 (masterdata-service 활성화와 함께) |
| `masterdata-service` | rest-api | 조직 마스터데이터 — 부서/직원/직급/비용센터/거래처 / 참조 무결성 / 유효기간 / 불변 audit_log + org_scope subtree data-scope (ERP-BE-008) |
| `read-model-service` | rest-api + event-consumer | 통합 조회 read-model (v1.1 첫 증분) — masterdata 변경이벤트 구독 → employee org-view 투영 (직원+부서경로+비용센터+직급); org_scope subtree read 필터 (ERP-BE-007/008) |

- **v2 deferred**: `approval-service` (결재 워크플로 — ADR-016 §D3), `permission-service`, `notification-service`, `admin-service`. (`read-model-service` 는 v1.1 라이브 — 위 상태 참조; 단 business-partner 등 풀 통합 조회 view 는 read-model v2.)
- **framing 정합**: 7축 메모리의 광의 erp("회계·구매·재고·HR 통합" + 자체 admin SPA) 는 ADR-MONO-016 SoT 상 v1=마스터데이터+결재+통합 read model (도메인 로직 미보유, 7축 책임 경계); UI=platform-console parity slice (ADR-MONO-013 바인딩, 자체 SPA superseded) (PROJECT.md § ADR-MONO-016 vs 7축 framing 정합).
- **ID provider**: GAP OIDC RS256 + `tenant_id=erp` claim (V0018 ×2 시드: account tenant + auth client_credentials `erp-platform-internal-services-client`).
- **frontend**: 없음 — 통합 platform console 이 렌더 (ADR-MONO-013 §3.3, `frontend-app` service_type 없음).

### 2.9 향후 도메인 (계획)

erp = 포트폴리오 **마지막 도메인** (ADR-MONO-002 § D4 ordering `scm → finance → erp → mes` 에서 erp 까지 부트스트랩 완료). `mes`/`hr`/`판매`/`구매`/`생산` 등은 **명시적으로 드롭** (포트폴리오 7축 architecture 결정, 2026-05-07; mes 재제안 금지). 추가 신규 도메인 부트스트랩 ADR 은 예정 없음.

---

## 3. 공유 라이브러리 레이어 (Library/Project Boundary)

라이브러리와 프로젝트 사이의 strict boundary 는 Template 추출의 viability 를 보장하는 **단일 가장 중요한 규칙** ([TEMPLATE.md § Library vs Project Boundary](../TEMPLATE.md)).

### 3.1 Shared (repo root, project-agnostic)

| Path | 역할 |
|---|---|
| [`.claude/config/`](../.claude/config/) | domain / trait / service-type dispatch catalog |
| [`.claude/skills/`](../.claude/skills/) | 80+ implementation skill (`common/` 항상 + `domain/<domain>/` on-demand) |
| [`.claude/agents/`](../.claude/agents/) | 12+ specialized sub-agent (architect, backend-engineer, code-reviewer, qa-engineer, devops-engineer, ...) |
| [`.claude/commands/`](../.claude/commands/) | 사용자-호출 가능 슬래시 커맨드 (skill 진입점) |
| [`rules/`](../rules/) | `common.md` + `domains/<domain>.md` + `traits/<trait>.md` + `taxonomy.md` |
| [`platform/`](../platform/) | architecture / error-handling / testing / observability / security 베이스라인 |
| [`platform/service-types/`](../platform/service-types/) | rest-api / event-consumer / batch-job / frontend-app 별 contract |
| [`libs/`](../libs/) | 도메인-중립 Java 라이브러리 (java-web / java-messaging / java-security / ...) |
| [`tasks/templates/`](../tasks/templates/) | backend / integration / frontend task 템플릿 |
| [`tasks/`](../tasks/) | 모노레포-레벨 task 라이프사이클 (cross-project 작업) |
| [`docs/guides/`](guides/) | **휴먼 전용** 워크플로우 가이드 (AI 는 source of truth 로 읽지 않음) |
| [`docs/adr/`](adr/) | 모노레포-레벨 ADR (MONO-001 / 002 / 003) |
| [`CLAUDE.md`](../CLAUDE.md) | AI 운영 규칙 (Hard Stop, source-of-truth priority, task workflow) |
| [`TEMPLATE.md`](../TEMPLATE.md) | Discovery → Distribution 전략 |

이 영역에 프로젝트-specific 콘텐츠 (서비스명, API 경로, 도메인 엔티티) 가 들어가면 **Hard Stop** ([CLAUDE.md § Hard Stop Rules](../CLAUDE.md)).

### 3.2 Project-specific (`projects/<name>/`)

`PROJECT.md` · `apps/` · `specs/` · `tasks/` · `knowledge/` · `docs/` · `infra/`. 도메인-specific 자유롭게.

---

## 4. AI-Driven 개발 시스템

### 4.1 Rule layer 자동 활성화

각 프로젝트의 `PROJECT.md` 의 `domain` + `traits` 선언 → 그에 매칭되는 rule file 자동 로딩 ([rules/README.md](../rules/README.md), [.claude/config/activation-rules.md](../.claude/config/activation-rules.md)):

```
common.md (always)
  ↓
domains/<declared-domain>.md (if present)
  ↓
traits/<declared-trait>.md for each trait (if present)
```

현재 정의된:
- **Domains**: `wms`, `ecommerce`, `saas`, `fan-platform`, `scm`, `fintech`, `erp`
- **Traits**: `transactional`, `integration-heavy`, `read-heavy`, `content-heavy`, `regulated`, `audit-heavy`, `multi-tenant`, `batch-heavy`, `internal-system`

### 4.2 Source of Truth Priority

[CLAUDE.md § Source of Truth Priority](../CLAUDE.md) — 충돌 시 적용 순서:

1. `PROJECT.md` (domain/traits)
2. `rules/common.md` + indexed canonical files
3. `rules/domains/<domain>.md`
4. `rules/traits/<trait>.md`
5. `platform/` (entrypoint + matching service-type)
6. `<project>/specs/contracts/`
7. `<project>/specs/services/`
8. `<project>/specs/features/`
9. `<project>/specs/use-cases/`
10. `<project>/tasks/ready/`
11. `.claude/skills/`
12. `<project>/knowledge/`
13. `<project>/docs/` (excluding `docs/guides/`)
14. existing code

### 4.3 Task Lifecycle

`backlog → ready → in-progress → review → done → archive`. 오직 `ready/` 의 task 만 구현 가능. 모든 task 는 7개 필수 섹션 (Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios) 을 가져야 한다.

- **Project-internal 작업**: 해당 프로젝트의 `tasks/ready/` 사용
- **Monorepo-level 작업**: 루트 `tasks/ready/` 사용 (shared 영역 변경 시)

### 4.4 Workflow 진입점

| 종류 | 슬래시 커맨드 | 위치 |
|---|---|---|
| 작업 1건 구현 | `/implement-task` | [.claude/commands/](../.claude/commands/) |
| 전체 ready 큐 처리 | `/process-tasks` | (구현 + 리뷰 파이프라인) |
| 신규 task 작성 | `/refactor-spec` 등 | spec 우선 |
| 리뷰 | `/review-task`, `/review`, `/security-review` | review/ 단계 |
| 검증 | `/audit-memory`, `/validate-rules` | drift 점검 |

---

## 5. Local Network 규약 (Traefik Hostname Routing)

**현재 표준** ([ADR-MONO-001](adr/ADR-MONO-001-port-prefix-scaling.md), TASK-MONO-024 적용 완료):

- 단일 공유 Traefik reverse proxy (`:80` / `:443`) 가 모든 프로젝트의 게이트웨이를 hostname 으로 라우팅.
- 각 프로젝트 gateway 는 docker-compose label 로 Traefik 에 등록 (`Host(<project>.local)`).
- backing service (postgres / redis / kafka) 는 `expose:` 만, host port 미공개.

| URL | 대상 |
|---|---|
| `http://wms.local/` | wms-platform gateway |
| `http://gap.local/` | global-account-platform gateway |
| `http://ecommerce.local/` | ecommerce gateway |
| `http://scm.local/` | scm-platform gateway |
| `http://fan-platform.local/` | fan-platform gateway |

**One-time setup**: `bash scripts/dev-setup.sh` (Linux/macOS) 또는 `.\scripts\dev-setup.ps1` (Windows) → `pnpm traefik:up`.

**legacy `PORT_PREFIX` scheme**: TASK-MONO-024 로 완전 폐기. 신규 프로젝트는 day-1 부터 hostname routing.

---

## 6. 빌드 / 테스트

- **JDK**: Java 21 (Temurin)
- **Build**: Gradle 8.14+ (wrapper 포함)
- **DB**: Postgres 16 (Testcontainers)
- **Messaging**: Kafka (Testcontainers)
- **Cache**: Redis 7
- **Frontend**: Node 20+ / pnpm

```bash
./gradlew projects                                      # 멀티 프로젝트 트리
./gradlew build                                         # 전체 빌드
./gradlew :projects:wms-platform:apps:master-service:build   # 특정 서비스
```

**Testing**: Testcontainers 의무 (in-memory substitute 금지). 자세한 룰 — [platform/testing-strategy.md](../platform/testing-strategy.md).

**CI**: [.github/workflows/ci.yml](../.github/workflows/ci.yml) — `dorny/paths-filter@v3` 기반 path-filtered build (TASK-MONO-045 적용). chore PR ~19초 (baseline 15분 대비 47x).

**Frontend Docker 빌드 캐시 (2026-06-02, PC-FE-035 / FE-072)**: Next.js standalone Docker 빌드에 BuildKit `.next/cache` + pnpm store 캐시 마운트 — console-web 229→91s (60%↓), ecommerce web-store/admin-dashboard 445→79s (82%↓); 이미지 크기 불변. (fan-platform-web 은 Dockerfile 미보유 = 대상 외; 공유 base Dockerfile 추출은 미착수 nicety.)

---

## 7. 주요 ADR / 결정

| ADR | Status | 핵심 결정 |
|---|---|---|
| [ADR-MONO-001](adr/ADR-MONO-001-port-prefix-scaling.md) | ACCEPTED | Traefik hostname routing (Option C). `PORT_PREFIX` scheme 폐기. |
| [ADR-MONO-002](adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) | ACCEPTED | Phase 4 catalyst = scm-platform 부트스트랩. Phase 5 deferred 의 평가 항목 명시. |
| [ADR-MONO-003](adr/ADR-MONO-003-phase-5-template-extraction-deferred.md) | SUPERSEDED-on-launch | Phase 5 보류 결정. 2026-05-13 ADR-MONO-003b LAUNCHED 로 SUPERSEDED. historical reference only. |
| [ADR-MONO-003a](adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) | ACCEPTED | ADR-MONO-003 § D4 (churn freeze) OVERRIDE scope 의 canonical source. D1/D2/D3/D4 redefinition + append-only audit-trail. |
| [ADR-MONO-003b](adr/ADR-MONO-003b-phase-5-launch-criteria.md) | ACCEPTED (2026-05-13 LAUNCHED) | Phase 5 launch criteria + post-launch sync cadence. `kanggle/project-template` public + `is_template: true` (TASK-MONO-070). |
| [ADR-MONO-004](adr/ADR-MONO-004-shared-messaging-scaffolding.md) | ACCEPTED | `libs/java-messaging` 신설 — outbox/inbox + ProjectionConsumerSupport + EventEnvelope 표준 (TASK-MONO-049). |
| [ADR-MONO-005](adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) | ACCEPTED | Saga 4-category taxonomy (A=critical / B=best-effort+CB / C=best-effort+counter / D=fire-and-forget) + timeout escalation + DLQ policy. |
| [ADR-MONO-006](adr/ADR-MONO-006-lint-remediation-as-agent-context.md) | ACCEPTED | Lint remediation message standard (4-block format) + hook-injected agent context. |
| [ADR-MONO-007](adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md) | ACCEPTED | Vector + VictoriaMetrics + VictoriaLogs worktree-ephemeral observability stack. |
| [ADR-MONO-007a](adr/ADR-MONO-007a-trace-layer.md) | ACCEPTED (2026-05-28) | Trace Layer — VictoriaTraces + OTLP (direct + Vector) + console-web `@opentelemetry` origination. Resolves ADR-018 D4 ↔ ADR-007 D1 trace-backend deferral. |
| [ADR-MONO-008](adr/ADR-MONO-008-finance-platform-bootstrap.md) | ACCEPTED (2026-05-18) | finance-platform 부트스트랩 — D1 Option C (Template fork `kanggle/finance-platform` + monorepo direct-include). domain=`fintech`, traits=[transactional/regulated/audit-heavy]. |
| [ADR-MONO-009](adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) | ACCEPTED | Chrome DevTools MCP 도입 — visual regression + DOM inspection 자동화. |
| [ADR-MONO-010](adr/ADR-MONO-010-e2e-tag-taxonomy.md) | ACCEPTED | E2E test tag taxonomy (smoke/golden/full) + Playwright projection. |
| [ADR-MONO-011](adr/ADR-MONO-011-nightly-full-e2e-cadence.md) | ACCEPTED | Nightly full e2e cadence — cron `0 19 * * *` UTC + workflow_dispatch. |
| [ADR-MONO-012](adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md) | ACCEPTED | Cross-project `architecture.md` canonical form (Identity 7-row table + ## sections). |
| [ADR-MONO-012a](adr/ADR-MONO-012a-cross-project-architecture-md-canonical-form-corrections.md) | ACCEPTED | ADR-MONO-012 corrections — WMS Identity-table form 채택. |
| [ADR-MONO-013](adr/ADR-MONO-013-platform-console-foundation.md) | ACCEPTED (2026-05-16) | platform-console foundation — Model B (콘솔이 유일한 프론트엔드). Phase 0~8 D6 roadmap. |
| [ADR-MONO-014](adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) | ACCEPTED | platform-console operator-auth — RFC 8693 token exchange + per-domain credential. |
| [ADR-MONO-015](adr/ADR-MONO-015-platform-console-dashboards-model.md) | ACCEPTED | platform-console dashboards composed-overview model. |
| [ADR-MONO-016](adr/ADR-MONO-016-erp-platform-bootstrap.md) | ACCEPTED (2026-05-19) | erp-platform 부트스트랩 — D1 Option C. domain=`erp`, traits=[internal-system/transactional/audit-heavy]. 첫 `internal-system`-primary. |
| [ADR-MONO-017](adr/ADR-MONO-017-platform-console-bff-architecture.md) | ACCEPTED (2026-05-20) | console-bff architecture — D1-D8 (skeleton / Operator Overview / Domain Health / per-domain credential / tenant pass-through / cross-tenant deny / per-domain attribution / MVP scope). |
| [ADR-MONO-018](adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md) | ACCEPTED (2026-05-25) | platform-console Phase 8 federation hardening — D1-D8 cross-product e2e + observability federation + multi-tenant isolation regression. |
| [ADR-MONO-019](adr/ADR-MONO-019-platform-console-customer-tenant-model.md) | ACCEPTED (2026-05-31) | customer-tenant model + 런타임 entitlement-trust — 고객 테넌트가 `entitled_domains` 로 도메인 접근 통제 (acme-corp/globex 시드; BE-322/324/325 + PC-BE-007 BFF pass-through + MONO-154 런타임 증명). |
| [ADR-MONO-020](adr/ADR-MONO-020-operator-multitenant-assignment.md) | ACCEPTED (2026-05-31, 사실상 완료 2026-06-02, D3 amendment 2026-06-05) | operator↔customer N:M assignment + active-tenant 토큰 스코핑 — assume-tenant RFC 8693 exchange (BE-326 dual-read + BE-327 + MONO-158~162 D4 A↔B switch GREEN). D6 step4 legacy read cleanup = **MONO-169 로 "의도적 비실행" disposition** (V0030 net-zero + `'*'` assignment-비표현 → dual-read steady state; 제거 시 전 운영자 회귀) → ADR-020 사실상 완료. **D3 amendment (2026-06-05)**: membership-derived **org_scope** 데이터-스코프 — `operator_tenant_assignment.org_scope` (V0031, 부서 subtree-root; `NULL ⟺ ["*"]` net-zero) 출처 (BE-338) + admin 관리 API (BE-339) + erp subtree 소비 (ERP-BE-008) + 콘솔 설정 UI (PC-FE-050); BE-336/337 위임 scope·v1 bridge 가 전제. |
| [ADR-MONO-021](adr/ADR-MONO-021-account-type-claim-source.md) | ACCEPTED (2026-06-02) | `account_type` (CONSUMER\|OPERATOR) OIDC claim source — `auth_db.credentials` denormalize (BE-329 컬럼+토큰주입 / BE-330 provisioning / INT-024 e2e). gateway 403-on-absent 해소. |
| GAP [ADR-001](../projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md) | ACCEPTED | GAP 를 monorepo 표준 OIDC IdP 로 승급. Spring Authorization Server 도입. |
| GAP [ADR-003](../projects/global-account-platform/docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md) | ACCEPTED — 옵션 B closure | SAS public-client `AuthenticationConverter` (옵션 A) + `SasRefreshTokenAuthenticationProvider` provider-side fallback (옵션 B) 으로 `refresh_token`/`revoke` grant 의 public-client 인증 경로 보강. Cluster A 3/3 회복. |
| GAP [ADR-004](../projects/global-account-platform/docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md) | ACCEPTED — 옵션 1 | OAuth callback 5 IT 의 CI Linux 503 RC = JDK HttpClient HTTP/2 RST_STREAM race. JDK HttpClient 의 protocol 을 HTTP/1.1 강제 (4 outbound client + libs/java-common DRY) 로 회피. Cluster C 5/5 회복. |

---

## 8. Portfolio 배포

각 프로젝트는 [`scripts/sync-portfolio.sh`](../scripts/sync-portfolio.sh) 로 별도 standalone 레포로도 추출 (이중 배포 — hub `ai-workspace` + 개별 독립 레포).

| Project | Standalone repo | Status |
|---|---|---|
| wms-platform | `kanggle/wms-platform` | 2026-04-28 first publish + 2026-05-09 re-sync (228 commits) |
| ecommerce-microservices-platform | `kanggle/ecommerce-microservices-platform` | v1 frozen (GAP cutover 영역만 `PROJECT_EXCLUDE_PATHS` 차단) |
| global-account-platform | `kanggle/global-account-platform` | 2026-05-09 first publish (153 commits) |
| scm-platform | `kanggle/scm-platform` | 2026-05-09 first publish (140 commits) |
| fan-platform | `kanggle/fan-platform` | 2026-05-09 first publish (147 commits) |
| finance-platform | `kanggle/finance-platform` | 2026-05-19 Template fork CONFIRMED (TASK-MONO-116) |
| erp-platform | `kanggle/erp-platform` | 2026-05-19 Template fork CONFIRMED (TASK-MONO-121) |
| platform-console | _(monorepo-only)_ | 가로축 콘솔 — 별 standalone 미발행 |

평가자 시간 예산별 진입 경로 분리 (5분 → standalone README, 30분+ → monorepo 풀 컨텍스트).

---

## 9. 향후 로드맵

| Phase | 상태 | 목표 |
|---|---|---|
| 1. Single project (wms) | ✅ 완료 | 룰/스킬/에이전트 시스템 검증 |
| 2. Second project (ecommerce) | ✅ 완료 | 라이브러리 generality 검증 |
| 3. Third project (GAP) — Rule of Three | ✅ 완료 | true generalization 필터 |
| 4. catalyst (scm + fan-platform) | ✅ 완료 | 5 프로젝트 동거 + churn 안정화 |
| 5. Template 추출 | ✅ **LAUNCHED 2026-05-13** ([ADR-MONO-003b](adr/ADR-MONO-003b-phase-5-launch-criteria.md)) | `kanggle/project-template` public + `is_template: true` (TASK-MONO-070) |
| 6. 새 도메인 부트스트랩 (finance + erp) | ✅ **COMPLETE 2026-05-19/20** (ADR-MONO-008 / ADR-MONO-016) | Template downstream 첫 2회 CONFIRMED — finance v1 + erp v1 monorepo+standalone 양쪽 종결 |
| 7. platform-console federation (console-bff) | ✅ **LIVE 2026-05-20** ([ADR-MONO-017](adr/ADR-MONO-017-platform-console-bff-architecture.md)) | console-bff skeleton + Operator Overview + Domain Health — 5/5 backend domains federated |
| 8. federation hardening (cross-product e2e + observability + isolation) | ✅ **COMPLETE 2026-05-28** ([ADR-MONO-018](adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md) + [ADR-MONO-007a](adr/ADR-MONO-007a-trace-layer.md)) | cross-product e2e (MONO-139/140) + **D4 observability federation** (MONO-142~147: VictoriaTraces stack → propagation gate → unified 62-span trace → per-leg `bff.domain`/`bff.route` attribution) + **D5 multi-tenant isolation regression** (PC-BE-006 console-bff + ERP-BE-004 erp) — 8/8 Playwright specs PASS |
| 8+. multi-tenant SaaS 실체화 (post-Phase-8) | ✅ **COMPLETE 2026-05-31~06-05** ([ADR-MONO-019](adr/ADR-MONO-019-platform-console-customer-tenant-model.md)/[020](adr/ADR-MONO-020-operator-multitenant-assignment.md)/[021](adr/ADR-MONO-021-account-type-claim-source.md)) | customer-tenant entitlement-trust 런타임 (BE-322~325 / MONO-154) + operator↔customer N:M assignment + assume-tenant RFC8693 (BE-326/327 / MONO-158~162 A↔B GREEN) + `account_type` OIDC claim (BE-329/330 / INT-024) + console overview consolidation (PC-FE-034) + console-web PR CI gate (MONO-166) + internal-system trait rules (MONO-167). ADR-020 D6 step4 = MONO-169 "의도적 비실행" disposition (사실상 완료). **MONO-170 full-stack 데모 → 런타임 producer↔consumer drift 8-fix 청소 (ERP-BE-006/SCM-BE-020/MONO-171/BE-331·332/BE-333·334/SCM-BE-021)** + frontend Docker 빌드 캐시 (PC-FE-035/FE-072). **erp read-model-service 라이브화 (ERP-BE-007 — 통합조회 v1.1 첫 증분: masterdata 이벤트 구독→employee org-view 투영) + 콘솔 통합 read 카드 (PC-FE-049) + org_scope 데이터-스코프 3-stage authz 사슬 (ADR-020 D3 amendment: BE-336 위임scope / BE-337 bridge / BE-338 멤버십출처 / ERP-BE-008 erp소비 / BE-339 admin API / PC-FE-050 콘솔 설정 UI) + 콘솔 erp 마스터 write parity (PC-FE-046~048) + operators/audit active-tenant 스코핑 (MONO-175/PC-FE-043) + self-service account 이동 (PC-FE-045) + demo redpanda (MONO-174).** 잔여=0 (deferred=미배포 outbound IT overhaul / read-model v2 풀 통합 view / org_scope 라이브 full-stack 스모크) |
| 9. Ongoing sync | 🔮 Future | 라이브러리 개선의 monorepo → template 주기 sync (ADR-MONO-003b § D3 cadence) |

**Phase 5 LAUNCH**: COMPLETE. Template sync cadence = [ADR-MONO-003b § D3](adr/ADR-MONO-003b-phase-5-launch-criteria.md) (월 1회 또는 on-demand). ADR-MONO-003 SUPERSEDED-on-launch — historical reference only.

---

## 10. 참조 문서

| 문서 | 목적 |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | AI 운영 규칙 (모든 작업 전 필수) |
| [TEMPLATE.md](../TEMPLATE.md) | Discovery → Distribution 전략 |
| [README.md](../README.md) | 포트폴리오 hub (외부 진입점) |
| [platform/entrypoint.md](../platform/entrypoint.md) | spec 읽기 순서 |
| [platform/architecture.md](../platform/architecture.md) | 시스템 아키텍처 베이스라인 |
| [platform/service-types/INDEX.md](../platform/service-types/INDEX.md) | service type catalog |
| [rules/README.md](../rules/README.md) | rule library architecture |
| [rules/taxonomy.md](../rules/taxonomy.md) | domain / trait 분류 권위 |
| [.claude/config/activation-rules.md](../.claude/config/activation-rules.md) | dispatch table |
| [docs/guides/](guides/) | 휴먼 워크플로우 가이드 |

---

_본 문서는 `MEMORY.md` 의 메모리 항목 (`project_*`) 과 동등한 정보를 외부에서 (휴먼 / 다른 도구) 참조 가능한 형태로 스냅샷한다. 메모리는 시간에 따라 갱신되지만 본 문서는 명시적 갱신 시점에만 변경 — 두 소스 간 drift 발생 시 본 문서의 "갱신 시점" 헤더와 git log 를 신뢰._
