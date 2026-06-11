---
name: scm-platform
domain: scm
traits: [transactional, integration-heavy, batch-heavy]
service_types: [rest-api, event-consumer, batch-job]
compliance: []
data_sensitivity: internal
scale_tier: startup
taxonomy_version: 0.1
---

# scm-platform

## Purpose

공급망 통합(Supply Chain Management) 백엔드 플랫폼. **조달 → 생산/입고 → 운송 → 정산** 의 cross-functional 흐름을 외부 supplier · carrier · ERP · bank 등 다수 시스템과 연동하면서 일관된 상태 머신·정산 추적·가시성으로 관리한다.

이 프로젝트는 monorepo Phase 4 의 **catalyst 도메인** ([ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) D2). 4 프로젝트 동거 (wms / ecommerce / GAP / fan-platform) 시점에서 라이브러리·TEMPLATE 가 stabilise 된 직후, 5번째 프로젝트로 신규 trait 조합 (`batch-heavy` 첫 사용) + cross-functional 도메인을 검증하는 역할.

기존 portfolio 와의 차별점:
- **wms-platform** 은 단일 창고 내부 동선 (입고 → 적치 → 피킹 → 출하). scm 은 그 윗 레이어 — supplier 와의 조달부터, carrier 운송, 정산까지의 노드 간 흐름. wms 가 한 노드라면 scm 은 노드들의 그래프.
- **ecommerce-microservices-platform** 은 B2C 판매 플랫폼. scm 은 B2B 공급망. 엔터프라이즈 buyer, supplier, carrier 가 핵심 actor.
- **GAP** 는 IdP — scm 은 GAP 의 **B2B_ENTERPRISE** tenant 로 합류 (TASK-MONO-042 V0013/V0015 시드 완료).
- **fan-platform** 은 콘텐츠 도메인 (B2C 비대칭 발행). scm 과 도메인 그래프상 거리 멀음 — 본 프로젝트가 라이브러리에 미치는 stress 가 fan-platform 과 다른 축.

본 프로젝트는 **백엔드 포트폴리오** 로서 프로덕션 지향 설계 (transactional + integration-heavy + batch-heavy 의 동시 트레이드오프) 를 보여준다. v1 은 backend only — frontend 도입은 v2+ 의향이 생긴 시점에 별도 task.

## Domain Rationale

`scm` ([rules/taxonomy.md](../../rules/taxonomy.md#scm)) 을 선택한 이유:

- 핵심 비즈니스가 **공급망 흐름의 cross-functional 통합** (조달·운송·정산) 이며, 단일 창고 운영(`wms`) 이나 단일 ERP 모듈(`erp`) 의 깊이가 아닌 **다단계 파이프라인의 일관성** 이 도메인 핵심.
- 외부 다수 시스템 (supplier ERP / carrier API / bank / 자사 wms) 과의 통합 안정성이 시스템 가치의 중심 → `integration-heavy` 자연 동반.
- `logistics` 는 화물의 물리적 이동에 더 가깝고, `wms` 는 한 창고 내부에 한정 — scm 은 그 둘을 모두 외부 통합 대상으로 둔다.

## Trait Rationale

- **transactional** — PO 발행 / ASN 입고 / 정산 lock 등은 강한 일관성 + 멱등성 요구. 같은 PO ack 두 번 받아도 한 번만 처리되어야 하고, settlement period 한 번 lock 후엔 immutable. `transactional` trait 의 idempotency / saga / outbox / 상태 기계 규칙 직접 적용.
- **integration-heavy** — supplier ERP/EDI, carrier API, bank, wms-platform inventory snapshot 등 3+ 외부 시스템 상시 연동. circuit breaker · retry+jitter · DLQ · vendor fallback 필수. fan-platform / ecommerce 보다 통합 지점 다양 — `integration-heavy` trait 가 가장 큰 표면적을 가진다.
- **batch-heavy** — 야간 정산 reconciliation, 주기 demand forecast, supplier catalog sync, settlement period close 등 배치 워크로드가 도메인 핵심. monorepo 4 프로젝트 중 어디에도 declared 안 된 trait 라 본 프로젝트가 첫 적용 사례 (Phase 4 catalyst 의 trait 검증 가치).

미선언 trait 와 그 이유는 § Out of Scope 참조.

## Service Map

### v1 (포트폴리오 1차 목표)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, GAP RS256 JWT 검증 (OAuth2 Resource Server), `tenant_id=scm` 게이트, rate limit |
| `procurement-service` | rest-api | PO(구매 발주) 작성·확정·취소, supplier ack 교환, ASN 수신 처리. 첫 service skeleton 의 1차 대상. |
| `inventory-visibility-service` | rest-api | cross-node (자사 wms / supplier / 3PL / in-transit) 재고 가시성. read-model. wms 의 inventory snapshot 이벤트 구독. |
| `demand-planning-service` | event-consumer + batch-job + rest-api | wms 저재고 alert (`wms.inventory.alert.v1`) 구독 → scm 재주문점 평가 → 발주 추천(DRAFT suggestion) → 운영자 승인 시 procurement DRAFT PO 생성. v1 = 고정 재주문점 규칙 (수요 예측은 v2). ADR-MONO-027 로 v2-deferred 에서 승급. |

### v2 (deferred — 별도 부트스트랩 task)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `supplier-service` | rest-api | supplier 마스터, contract / SLA, supplier 별 adapter, catalog sync. `demand-planning-service` 의 `sku_supplier_map` 최소 매핑이 v2 에서 이관 대상 (ADR-MONO-027 D3). |
| `logistics-service` | rest-api | shipment 단위 생성/조회, carrier 연동, ETA/추적 |
| `settlement-service` | batch-job + rest-api | 정산 기간, PO ↔ ASN ↔ invoice reconciliation, ERP 분개 feed |
| `notification-service` | event-consumer | supplier SLA / settlement / reorder 알림 fanout |
| `admin-service` | rest-api | 운영 콘솔 백엔드 |

상세 아키텍처는 각 service 의 `specs/services/<service>/architecture.md` 에서 선언.

## IAM IdP Integration

`scm-platform` 은 [iam-platform](../iam-platform/PROJECT.md) (GAP) 을 표준 OIDC IdP 로 사용한다 ([ADR-001](../iam-platform/docs/adr/ADR-001-oidc-adoption.md)). 모든 scm-platform 서비스는 OAuth2 Resource Server 패턴으로 GAP 의 JWKS 기반 RS256 access token 을 검증하고, `tenant_id=scm` claim 만 통과시킨다.

GAP 측 인프라 ([TASK-MONO-042](../../tasks/done/) 머지 완료):
- account-service V0015: `tenants` 에 `scm` row (B2B_ENTERPRISE)
- auth-service V0013: `oauth_clients` 에 `scm-platform-internal-services-client` (client_credentials, scopes=`scm.read`/`scm.write`)
- v1 = backend only. user-flow PKCE client 는 frontend 도입 시 별도 V slot.
- **platform-console (ADR-MONO-013 Model B) = 외부 운영자 read consumer**: scm 의 read surface(procurement PO read + inventory-visibility)를 GAP **자체** `platform-console-web` OIDC 토큰으로 server-side 소비한다 ([iam-integration.md § platform-console Operator Read Consumer](specs/integration/iam-integration.md), [gateway-public-routes.md](specs/contracts/http/gateway-public-routes.md), TASK-SCM-BE-015). scm 자신은 backend-only 유지 — scm frontend 없음, scm user-flow client 없음, single-org 불변(이 인정으로 traits 변경 없음).

dev 환경 토큰 발급 예:
```
curl -u scm-platform-internal-services-client:scm-dev \
     -d "grant_type=client_credentials&scope=scm.read" \
     http://iam.local/oauth2/token
```

통합 상세는 [specs/integration/iam-integration.md](specs/integration/iam-integration.md) (TASK-SCM-BE-001 부트스트랩 시 작성).

## Local Network

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C 채택 — `scm.local` 호스트네임으로 Traefik routing. PORT_PREFIX 미사용. 부트스트랩 시점부터 `infra/traefik/` (TASK-MONO-022) 의 공유 인프라에 join.

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **wms** (도메인) — 단일 창고 내부 동선은 wms-platform 이 담당. scm 은 wms 의 inventory snapshot 만 구독.
- **erp** (도메인) — 전사 회계·HR 통합은 별도. scm 은 정산 계산까지, 분개는 ERP 로 feed.
- **multi-tenant** (trait) — GAP 의 `tenant_id=scm` claim 은 수신하나, scm-platform 내부에서 다수 organization 을 격리하는 SaaS 가 아님 (단일 조직의 공급망).
- **regulated** (trait) — 금융·의료급 규제 대상 아님. 단, supplier 계약·SLA audit trail 은 도메인 자체 요구 (S7) — application 레벨 audit_log 로 대응.
- **audit-heavy** (trait) — 도메인 룰 S7 이 audit trail 을 강제하나, 법적 감사 수준의 불변 저장소·외부 보존 규제는 v1 범위 밖. 향후 enterprise customer 가 audit 요구하면 trait 추가.
- **real-time** (trait) — supplier / carrier 와의 통합은 minutes ~ hours 단위. 초 단위 지연 요구 없음. inventory visibility eventual consistency 허용 (S5).
- **read-heavy** (trait) — 운영자/buyer 의 dashboard 조회는 있지만 쓰기 트래픽 (PO / ASN / shipment 이벤트) 와 비슷한 자리수. CDN / 다계층 캐시까지 갈 가치 약함.
- **data-intensive** (trait) — 포트폴리오 규모에서 TB+ 데이터 없음. 향후 enterprise 단위 데이터 누적이 핵심 제약이 되면 trait 추가.
- **internal-system** (trait) — 외부 supplier API 와 양방향 통합 발생 — 순수 internal-only 아님.
- **content-heavy** (trait) — 콘텐츠가 도메인 자산 아님.

이 경계가 바뀌면 본 PROJECT.md 의 traits 를 수정하고 [rules/traits/](../../rules/traits/) 의 해당 파일을 로딩 범위에 포함시킬 것.

## Overrides

현재 명시적 override 없음. 공통/도메인/특성 규칙을 모두 기본값대로 따른다.

예외가 필요한 경우 이 섹션에 다음 형식으로 기록:

```
- **rule**: rules/traits/<trait>.md#<rule-id>
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
```
