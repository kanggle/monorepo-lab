# scm-platform

[![CI](https://github.com/kanggle/scm-platform/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kanggle/scm-platform/actions/workflows/ci.yml?query=branch%3Amain)

> Supply Chain Management 백엔드 플랫폼. monorepo Phase 4 catalyst 도메인 — 조달 → 재고 가시성 → 수요 계획 → 운송의 cross-functional 공급망 흐름을 다수 외부 시스템과 연동하며 관리한다.

| 항목 | 값 |
|---|---|
| Domain | `scm` ([rules/domains/scm.md](../../rules/domains/scm.md)) |
| Traits | `transactional`, `integration-heavy`, `batch-heavy` |
| Service Types | `rest-api`, `event-consumer`, `batch-job` |
| IdP | IAM / GAP (`tenant_id=scm`) — [IAM integration](../iam-platform/PROJECT.md) |
| Hostname | `scm.local` (Traefik routing, ADR-MONO-001) |
| Status | ✅ **v1 — 5 services implemented**: `gateway-service` · `procurement-service` · `inventory-visibility-service` · `demand-planning-service` · `logistics-service`. All in `settings.gradle`, all with real service code (312 `.java` files under `apps/*/src/main/java`), dedicated CI jobs (build/check, Testcontainers integration, e2e smoke), and registered + published to the standalone repo. |

---

## Purpose

조달(Procurement) → 운송(Logistics) → 정산(Settlement) 의 cross-functional 공급망 흐름을 다수 외부 시스템(supplier ERP / carrier API / bank / 자사 wms-platform) 과 연동하면서 일관된 상태 머신으로 관리하는 백엔드 플랫폼.

자세한 도메인 정의·rationale·service map 은 [PROJECT.md](PROJECT.md) 참조.

---

## v1 Service Map (구현 완료)

| Service | Service Type | 핵심 책임 | `.java` 파일 수 |
|---|---|---|---:|
| `gateway-service` | `rest-api` | 엣지 라우팅, GAP RS256 JWT 검증 (OAuth2 Resource Server), `tenant_id=scm` 게이트, rate limit | 5 |
| `procurement-service` | `rest-api` | PO(구매 발주) 작성·확정·취소, supplier ack 교환, ASN 수신 처리, webhook HMAC replay 방어 | 95 |
| `inventory-visibility-service` | `rest-api` + `event-consumer` + `batch-job` | cross-node (자사 wms / supplier / 3PL / in-transit) 재고 가시성 read-model. wms inventory snapshot 이벤트 구독 + staleness 배치 감지 | 81 |
| `demand-planning-service` | `event-consumer` + `batch-job` + `rest-api` | wms 저재고 alert(`wms.inventory.alert.v1`) 구독 → 재주문점 평가 → 발주 추천(DRAFT suggestion) → 운영자 승인 시 procurement DRAFT PO 생성. ADR-MONO-027 Phase 1 | 65 |
| `logistics-service` | `event-consumer` + `rest-api` | wms `outbound.shipping.confirmed` 구독 → `CarrierRouter` 로 carrier 라우팅 → dispatch(EasyPost/굿스플로) + 운영자 `:retry`. ADR-MONO-053 Phase 1 | 66 |

각 서비스의 내부 아키텍처는 `specs/services/<service>/architecture.md`에 선언되어 있습니다.

**v2 (deferred — 별도 부트스트랩 task)**: `supplier-service`(supplier 마스터/contract/SLA), `settlement-service`(정산 기간·PO↔ASN↔invoice reconciliation), `notification-service`(SLA/정산/reorder 알림 fanout), `admin-service`(운영 콘솔 백엔드). 상세는 [PROJECT.md § Service Map](PROJECT.md#service-map).

---

## Local Dev Quick Start

```bash
# 1. 공유 Traefik 인프라 기동 (한 번만)
pnpm traefik:up

# 2. hosts 파일에 scm.local 등록 (한 번만)
#    Linux/macOS: /etc/hosts
#    Windows: C:\Windows\System32\drivers\etc\hosts
echo "127.0.0.1  scm.local" | sudo tee -a /etc/hosts

# 3. scm-platform 전 서비스 기동 (gateway + procurement + inventory-visibility
#    + demand-planning + logistics + postgres/redis/kafka backing services)
pnpm scm:up

# 4. 상태 확인
pnpm scm:ps
pnpm scm:logs

# 5. 정지
pnpm scm:down
```

```bash
curl -i http://scm.local/actuator/health
# → 200 OK from gateway-service
```

dev 토큰 발급 (IAM `scm-platform-internal-services-client` 등록 완료, TASK-MONO-042 V0013):

```bash
curl -u scm-platform-internal-services-client:scm-dev \
     -d "grant_type=client_credentials&scope=scm.read" \
     http://iam.local/oauth2/token
```

---

## IAM IdP Integration

scm-platform 의 모든 서비스는 OAuth2 Resource Server 패턴으로 IAM(GAP) 의 JWKS 기반 RS256 access token 을 검증하며 `tenant_id=scm` claim 만 통과시킨다.

IAM 측 인프라 (TASK-MONO-042 머지 완료):

- `tenants.tenant_id='scm'` (B2B_ENTERPRISE) — account-service V0015
- `oauth_clients.client_id='scm-platform-internal-services-client'` (client_credentials, scopes=`scm.read`/`scm.write`) — auth-service V0013
- `oauth_scopes` — `scm.read`, `scm.write` — auth-service V0013
- **platform-console (ADR-MONO-013 Model B) = 외부 운영자 read consumer**: scm 의 read surface(procurement PO read + inventory-visibility)를 IAM 자체 `platform-console-web` OIDC 토큰으로 server-side 소비 (TASK-SCM-BE-015). scm 자신은 backend-only 유지 — frontend 없음, user-flow client 없음.

통합 상세는 [specs/integration/iam-integration.md](specs/integration/iam-integration.md) · [specs/contracts/http/gateway-public-routes.md](specs/contracts/http/gateway-public-routes.md) · [PROJECT.md § IAM IdP Integration](PROJECT.md#iam-idp-integration).

---

## 테스트 · CI

`.github/workflows/ci.yml`에 scm-platform 전용 job 3종이 등록되어 있습니다 (path-filter로 `projects/scm-platform/**` 변경 시에만 트리거):

- **Build & check** — 5개 서비스 모두 `:projects:scm-platform:apps:<service>:check` (Docker-free unit/slice 테스트)
- **Integration** — `procurement` · `inventory-visibility` · `demand-planning` · `logistics` 의 `@Tag("integration")` 테스트를 Testcontainers 로 실행
- **E2E smoke** — 5개 서비스 boot jar 를 패키징해 `projects:scm-platform:tests:e2e:e2eSmokeTest` 로 cross-service 시나리오 검증 (전체 `e2eFullTest` 는 nightly)

---

## 🛠️ 기술 스택

- **언어**: Java 21
- **프레임워크**: Spring Boot 3.4
- **빌드**: Gradle (멀티 모듈, `projects:scm-platform:apps:*`)
- **영속성**: PostgreSQL 16 (서비스별 독립 DB)
- **메시징**: Apache Kafka (KRaft 모드) — wms-platform 과 클러스터 공유(dev), 저재고 alert / outbound shipping confirmed 이벤트 크로스 프로젝트 구독
- **캐시**: Redis (gateway rate-limit, 토큰 캐시)
- **로컬 개발**: Docker Compose + 공유 Traefik (`scm.local`)

---

## 📁 디렉터리 구조

```
scm-platform/
├── PROJECT.md              ← domain=scm, traits=[transactional, integration-heavy, batch-heavy]
├── README.md                ← 이 파일
├── docker-compose.yml       ← 로컬 스택 (5 서비스 + postgres/redis/kafka)
├── apps/
│   ├── gateway-service/                 ← ✅ 구현 완료
│   ├── procurement-service/             ← ✅ 구현 완료
│   ├── inventory-visibility-service/    ← ✅ 구현 완료
│   ├── demand-planning-service/         ← ✅ 구현 완료
│   └── logistics-service/               ← ✅ 구현 완료
├── tests/e2e/                ← cross-service e2e (Testcontainers)
├── specs/
│   ├── contracts/http/       ← gateway-public-routes, procurement-api, inventory-visibility-api, demand-planning-api
│   ├── services/<service>/   ← architecture · data-model · overview 등
│   └── integration/          ← iam-integration.md
├── tasks/
│   ├── INDEX.md
│   └── done/                 ← 완료 태스크 다수 (TASK-SCM-BE-001 ~, TASK-SCM-INT-001 ~)
├── infra/                    ← Postgres init 등
└── docs/
```

---

## 🧭 개발 방식

이 프로젝트는 **[Claude Code](https://claude.com/claude-code)** 기반의 규칙 주도, 태스크 중심 워크플로우를 따릅니다:

- **스펙 선행**: 컨트랙트, 아키텍처, 도메인 모델을 구현 전에 먼저 작성.
- **분류 체계 기반 규칙 활성화**: `PROJECT.md`가 `domain=scm, traits=[transactional, integration-heavy, batch-heavy]`를 선언 — AI는 해당 도메인/trait 규칙만 로드.
- **태스크 라이프사이클**: `ready → in-progress → review → done`. `tasks/ready/` 항목만 구현. 모든 태스크는 Plan → Implement → Test → Review를 거침.
- **리뷰 규율**: 모든 구현은 독립적인 리뷰 패스를 거침. 결과는 `tasks/INDEX.md`에 판정과 함께 기록.

전체 개발 이력은 **[kanggle/monorepo-lab](https://github.com/kanggle/monorepo-lab)** 에 있습니다 — 이 레포는 `scripts/sync-portfolio.sh`를 통해 추출한 스냅샷입니다.

---

## 🔗 관련 링크

- **개발 워크스페이스**: [kanggle/monorepo-lab](https://github.com/kanggle/monorepo-lab) — 태스크 작성, 리뷰, 머지가 이루어지는 원본 레포
- **포트폴리오 허브**: [github.com/kanggle](https://github.com/kanggle) — 다른 프로젝트

### 스펙 (이 레포 안에 있음)

- [PROJECT.md](PROJECT.md) — domain/traits 선언, 서비스 맵, IAM 통합, 범위 외 목록
- [specs/services/gateway-service/architecture.md](specs/services/gateway-service/architecture.md)
- [specs/services/procurement-service/architecture.md](specs/services/procurement-service/architecture.md) · [data-model.md](specs/services/procurement-service/data-model.md)
- [specs/services/inventory-visibility-service/architecture.md](specs/services/inventory-visibility-service/architecture.md) · [data-model.md](specs/services/inventory-visibility-service/data-model.md) · [staleness-monitoring.md](specs/services/inventory-visibility-service/staleness-monitoring.md)
- [specs/services/demand-planning-service/architecture.md](specs/services/demand-planning-service/architecture.md) · [data-model.md](specs/services/demand-planning-service/data-model.md) · [reorder-policy.md](specs/services/demand-planning-service/reorder-policy.md)
- [specs/services/logistics-service/architecture.md](specs/services/logistics-service/architecture.md) · [external-integrations.md](specs/services/logistics-service/external-integrations.md)
- [specs/integration/iam-integration.md](specs/integration/iam-integration.md)

### 규칙

- [rules/common.md](../../rules/common.md) — 항상 로드되는 규칙 인덱스
- [rules/domains/scm.md](../../rules/domains/scm.md) — SCM 도메인 규칙
- [rules/traits/transactional.md](../../rules/traits/transactional.md) · [integration-heavy.md](../../rules/traits/integration-heavy.md) · [batch-heavy.md](../../rules/traits/batch-heavy.md)

### 결정 기록

- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) — Phase 4 catalyst 결정 (scm 선정 rationale)
- [ADR-MONO-027](../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) — wms↔scm replenishment loop (demand-planning-service)
- [ADR-MONO-053](../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) — logistics-service multimodal fulfillment
- [TASK-MONO-040](../../tasks/done/) — scm-platform 부트스트랩
- [TASK-MONO-042](../../tasks/done/) — IAM V0013/V0015 시드 (scm OAuth client + tenant)
- [TEMPLATE.md § IAM IdP Integration Pattern](../../TEMPLATE.md#iam-idp-integration-pattern-new-projects) — 신규 프로젝트의 IAM 통합 표준 절차

---

## 📄 라이선스

라이선스 미정. 현재 오픈 소스 아님.
