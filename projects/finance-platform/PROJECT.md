---
name: finance-platform
domain: fintech
traits: [transactional, regulated, audit-heavy]
service_types: [rest-api, event-consumer]
compliance: []
data_sensitivity: confidential
scale_tier: startup
taxonomy_version: 0.1
---

# finance-platform

## Purpose

비은행 금융 서비스(fintech) 백엔드 플랫폼. **계좌 개설 → KYC → 잔액 보유/해제 → 자금 이동 → 정산 대조** 의 흐름을, 모든 자금 영향 연산의 멱등성·불변 감사 기록·규제(KYC/AML) 선행 게이트와 함께 일관되게 관리한다.

이 프로젝트는 monorepo Phase 6 의 **첫 Template 다운스트림 부트스트랩** ([ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md), ACCEPTED 2026-05-18, Option C). 5 프로젝트 동거(wms / ecommerce / GAP / fan-platform / scm) + Phase 5 Template LAUNCHED 이후, 6번째 도메인 프로젝트로 신규 trait 조합 (`regulated + audit-heavy` 동시 첫 사용) + 컴플라이언스 도메인 표면을 검증하는 역할이다. `kanggle/finance-platform` standalone 레포(Template fork)와 monorepo `projects/finance-platform/` direct-include 가 함께 존재한다(Option C).

기존 portfolio 와의 차별점:

- **scm-platform** 은 공급망 노드 간 흐름(조달·운송·정산)이고 trait 가 `transactional + integration-heavy + batch-heavy`. finance 는 자금 정확성·규제 추적이 핵심이고 trait 가 `transactional + regulated + audit-heavy` — 라이브러리에 `regulated + audit-heavy` 동시 stress 를 처음 가한다.
- **iam-platform** (GAP) 은 IdP(`saas` 도메인) — finance 는 GAP 의 **B2B_ENTERPRISE** tenant 로 합류(`tenant_id=finance`, V0017 시드).
- **ecommerce / fan-platform** 은 B2C 판매·콘텐츠 도메인. finance 는 자금 자체가 도메인 자산 — 결제·정산·규제의 정확성이 시스템 가치의 중심.

본 프로젝트는 **백엔드 포트폴리오** 로서 프로덕션 지향 설계(transactional + regulated + audit-heavy 의 동시 트레이드오프)를 보여준다. v1 = backend only — frontend 는 통합 platform console 이 렌더하며([ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) §3.3 backend-only + 콘솔 렌더; GAP/scm 선례) `frontend-app` service_type 을 두지 않는다.

### ADR-MONO-008 vs 7축 메모리 framing 정합

포트폴리오 7축 architecture 메모리는 finance 를 "분개/GL/AP accounting" 깊이로 기술하나, **본 PROJECT.md 의 Source-of-Truth 는 ADR-MONO-008** (CLAUDE.md § Source of Truth Priority: `platform/` · ADR > 메모리). ADR-008 § D2/D3 가 v1 을 fintech 의 **Account/Wallet/Transaction/KYC** 로 고정하고, 복식부기 원장(double-entry ledger)·GL·AP 회계 깊이는 명시적으로 **v2 (ledger-service)** 로 deferred 한다. 7축의 "도메인 이벤트 → 자동 분개" narrative 는 v1 범위가 아니라 v2/future 통합 스토리로 기록된다(이 정합 결정 자체가 ADR-008 § D3 의 결과).

## Domain Rationale

`fintech` ([rules/taxonomy.md](../../rules/taxonomy.md#fintech)) 을 선택한 이유, 그리고 같은 Financial Services 묶음의 `pg`/`banking`/`securities` 를 선택하지 않은 이유:

- 핵심 비즈니스가 **금융 제품 자체**(계좌/지갑/거래/KYC)의 정확성·규제 준수이며, 이는 taxonomy `fintech` 정의("금융 서비스 일반 — 송금/대출/투자/보험 등 비은행 금융, 전형 서브시스템 = Account/Wallet/Transaction/KYC/Risk/Compliance")와 정확히 일치한다.
- **`pg` 아님** — 가맹점-카드사/은행 사이 결제 중계(acquirer 연동/토큰화/chargeback/fraud)가 핵심이 아니다. finance 는 결제망의 중개자가 아니라 자금 제품의 소유자.
- **`banking` 아님** — 은행업 인가를 전제로 한 core banking/예금/대출 기간계가 아니다. 비은행 금융 서비스 범위.
- **`securities` 아님** — 시장 주문/체결/포지션/청산이 도메인 핵심이 아니다.
- `fintech` 는 위 셋이 아닌, **금융 제품이 핵심이되 전통 은행/PG/증권이 아닐 때** 의 정확한 도메인 선택지(taxonomy "언제 고르는가").

## Trait Rationale

- **transactional** — 자금 이동(충전/출금/송금/hold/capture/release)은 강한 일관성 + 멱등성 요구. 같은 idempotency key 의 재요청은 한 번만 처리되어야 하고, 확정 거래는 immutable(반전 거래로만 정정). `transactional` trait 의 idempotency / outbox / 상태기계 규칙이 도메인 룰 F1·F2·F3 에 직접 적용.
- **regulated** — KYC/AML/제재목록 대조가 자금 이동의 **선행 게이트**(도메인 룰 F4). taxonomy `regulated` 정의("금융/의료/공공처럼 규제 준수가 시스템 설계를 좌우")와 일치. 승인 워크플로·규제 데이터 보호가 시스템 가치의 일부.
- **audit-heavy** — 모든 자금·규제 영향 연산이 actor/time/before/after 의 불변(append-only) 감사 기록을 남겨야 한다(도메인 룰 F6). 회계·규제상 "누가 얼마를 어떻게 옮겼는가"의 책임 추적이 필수. monorepo 에서 `regulated + audit-heavy` 가 **동시에** 선언되는 첫 사례(GAP 은 `saas` 도메인에서 둘을 갖지만 fintech 도메인 표면에서는 처음) — Phase 6 의 trait-조합 검증 가치.

미선언 trait 와 그 이유는 § Out of Scope 참조.

## Service Map

### v1 (포트폴리오 1차 목표)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, GAP RS256 JWT 검증 (OAuth2 Resource Server), `tenant_id=finance` 게이트, rate limit |
| `account-service` | rest-api | Account 라이프사이클 — KYC 단계 추적, 잔액(가용/장부) 보유(hold)·해제(release)·capture, 계좌 상태기계, 자금 이동 멱등 처리, 불변 audit_log. 첫 service skeleton 의 1차 대상 (TASK-FIN-BE-001). |

### v2 (deferred — 별도 부트스트랩 task)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `ledger-service` | rest-api | 복식부기 원장 (차변/대변, 잔액 reconciliation, 기간 마감), GL/AP feed — fintech accounting 깊이 (ADR-008 § D3 v2) |
| `wallet-service` | rest-api | 사용자별 잔액 보관 분리, 멀티-통화 지갑 (v1 은 account-service 내 balance 로 통합) |
| `kyc-service` | rest-api | KYC/AML 단계 관리, 외부 신원확인·제재목록 제공사 adapter |
| `notification-service` | event-consumer | 거래/KYC/한도/의심거래 알림 fanout |
| `admin-service` | rest-api | 운영 콘솔 백엔드 (reconciliation 큐, KYC 보류 검토, 한도 정책) |

상세 아키텍처는 각 service 의 `specs/services/<service>/architecture.md` 에서 선언.

## GAP IdP Integration

`finance-platform` 은 [iam-platform](../iam-platform/PROJECT.md) (GAP) 을 표준 OIDC IdP 로 사용한다 ([ADR-001](../iam-platform/docs/adr/ADR-001-oidc-adoption.md)). 모든 finance-platform 서비스는 OAuth2 Resource Server 패턴으로 GAP 의 JWKS 기반 RS256 access token 을 검증하고, `tenant_id=finance` claim 만 통과시킨다.

GAP 측 인프라 (TASK-MONO-114 V0017 시드):
- account-service V0017: `tenants` 에 `finance` row (B2B_ENTERPRISE — scm 과 동일 type, 내부-서비스 모델)
- auth-service V0017: `oauth_clients` 에 `finance-platform-internal-services-client` (client_credentials, scopes=`finance.read`/`finance.write`)
- v1 = backend only. user-flow PKCE client 는 별도 V slot (v1 미발행 — 콘솔이 GAP public client 로 렌더, ADR-MONO-013).
- **platform-console operator read consumer (ADR-MONO-013 Model B)** — `platform-console` (별도 ADR-MONO-013-governed 프로젝트) 가 GAP **자신의** `platform-console-web` 콘솔 클라이언트로 finance 의 v1 read 표면(`GET /accounts/{id}`·`/balances`·`/transactions`)을 **외부 운영자 read consumer** 로서 server-side 소비한다 (read-only; 상세 = [`iam-integration.md` § platform-console Operator Read Consumer](specs/integration/iam-integration.md#platform-console-operator-read-consumer-adr-mono-013)). finance 자체는 backend-only 유지 — finance 프론트엔드 없음, finance user-flow 클라이언트 없음 (deferred `finance-platform-user-flow-client` 는 무관). 콘솔의 trait 은 콘솔의 것이지 finance 의 것이 아니다 — finance 는 `multi-tenant`/`integration-heavy` 미선언 유지 (§ Out of Scope). finance 도메인 거버넌스는 ADR-MONO-008 그대로 (재결정 아님).

dev 환경 토큰 발급 예:
```
curl -u finance-platform-internal-services-client:finance-dev \
     -d "grant_type=client_credentials&scope=finance.read" \
     http://iam.local/oauth2/token
```

통합 상세는 [specs/integration/iam-integration.md](specs/integration/iam-integration.md).

## Local Network

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C 채택 — `finance.local` 호스트네임으로 Traefik routing. PORT_PREFIX 미사용. 부트스트랩 시점부터 `infra/traefik/` 의 공유 인프라에 join.

## v1 IN / OUT slice

**v1 IN (범위 안)**:
- `account-service` Account 라이프사이클: KYC 단계 추적, 가용/장부 잔액, hold / release / capture, 계좌 상태기계, 자금 이동 멱등 처리.
- 모든 자금·규제 영향 연산의 불변 audit_log (F6).
- GAP RS256 OAuth2 Resource Server + `tenant_id=finance` fail-closed 게이트.
- `gateway-service` 엣지 라우팅 (account-service 활성화와 함께).

**v1 OUT (v2 deferred)**:
- 복식부기 원장 / double-entry accounting / GL / AP feed → `ledger-service` (v2, ADR-008 § D3).
- 별도 wallet-service / kyc-service / notification-service / admin-service.
- 외부 은행·송금망·KYC 제공사 실제 통합 (v1 = 도메인 모델 + 멱등/감사 골격; 실제 adapter 는 service spec 단계).
- 정산(settlement) 외부 연동 — reconciliation 모델은 도메인 룰 F8 로 forward-decl, 실 연동은 v2.
- frontend — 통합 platform console 이 렌더 (ADR-MONO-013 §3.3, `frontend-app` service_type 없음).

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **pg / banking / securities** (도메인) — § Domain Rationale 참조. 결제 중계·은행 기간계·증권 주문은 finance 의 책임이 아니다.
- **integration-heavy** (trait) — v1 은 도메인 모델 + 멱등/감사 골격 중심. 외부 은행/KYC/제재목록 제공사 상시 다중 연동은 v2 (ledger/kyc-service) 범위. 단, 자금 이동 경로의 idempotency/circuit-breaker 는 도메인 룰 F1 이 자체 강제 — trait 없이도 적용. v2 에서 외부 통합이 핵심 표면이 되면 trait 추가.
- **batch-heavy** (trait) — v1 에 야간 정산/대량 배치가 핵심 워크로드로 들어오지 않는다. reconciliation 배치·기간 마감은 v2 ledger-service 도입 시 trait 추가 검토.
- **multi-tenant** (trait) — GAP 의 `tenant_id=finance` claim 은 수신하나, finance-platform 내부에서 다수 organization 을 격리하는 SaaS 가 아님 (단일 금융 서비스 운영). GAP 가 multi-tenant IdP 역할.
- **event-driven** — taxonomy 의 11 trait 에 없는 분류 (ADR-008 § D2 가 "optional" 로 언급하나 비-taxonomy 라 선언 금지, HARDSTOP-02 회피). outbox/event 흐름은 `transactional` trait + 도메인 룰 F1 으로 충분히 커버.
- **real-time** (trait) — 자금 이동은 정확성 > 초단위 지연. 실시간 스트리밍 요구 없음.
- **read-heavy** / **content-heavy** / **data-intensive** / **internal-system** (trait) — 도메인 자산이 콘텐츠·대용량 분석·읽기 트래픽이 아니라 자금 정확성. 포트폴리오 규모에서 해당 제약 핵심 아님.

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
