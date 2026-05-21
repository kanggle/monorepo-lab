---
name: platform-console
domain: saas
traits: [multi-tenant, integration-heavy, audit-heavy]
service_types: [frontend-app, rest-api]
compliance: []
data_sensitivity: internal
scale_tier: startup
taxonomy_version: 0.1
---

# platform-console

## Purpose

포트폴리오 엔터프라이즈 스위트(`gap` · `wms` · `scm` + 향후 `erp` · `finance`)를 **단일 AWS/GCP-콘솔식 운영 화면**으로 통합하는 프로젝트. 운영자가 한 번 로그인(GAP OIDC)하여 테넌트/제품을 전환하고, 각 도메인의 운영 화면을 콘솔 안에서 사용한다.

이 프로젝트는 [ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) (ACCEPTED 2026-05-16)으로 부트스트랩되었다. 핵심 결정:

- **Model B (단일 UI)**: 콘솔이 *유일한 프론트엔드*다. `wms`/`scm`/`erp`/`finance`는 백엔드-only이며, 콘솔이 각 도메인의 gateway/admin REST API를 호출해 운영 화면을 콘솔 안에서 렌더한다. 런처(각 제품 자체 사이트로 redirect) 모델이 아니다.
- **GAP `admin-web` 흡수**: GAP의 운영자 콘솔(`admin-web`)은 콘솔이 GAP 운영자 표면 parity를 검증 달성한 뒤(ADR-MONO-013 Phase 3) **폐기**된다. 그 시점 GAP는 백엔드-only IdP로 회귀한다.
- **data-driven 카탈로그**: 서비스 카탈로그는 GAP의 product/tenant 레지스트리에서 읽는다. 5/5 federated domains (`gap` + `wms` + `scm` + `erp` + `finance`) 모두 V1 live 이며 `available:true` 로 노출된다 (TASK-BE-305 2026-05-21 reality-alignment, ADR-MONO-013 § D6 Phase 5/6 COMPLETE). 향후 새 product 추가는 레지스트리 설정만으로 켜진다(콘솔 재작업 0); 미생성 product 가 등장하면 `available:false` "coming soon" 타일로 표현된다.

부트스트랩은 monorepo direct-include 방식이다(Template-fork 아님 — ADR-MONO-013 § 1.5).

## Domain Rationale

`saas` ([rules/taxonomy.md](../../rules/taxonomy.md#saas))를 선택한 이유:

- 특정 산업(금융·이커머스 등)에 귀속되지 않는 **가로축 내부 플랫폼 표면**. 다수 제품(tenant)을 공유 운영하는 콘솔이 본질. GAP가 `saas`를 택한 동일 논리("내부 제품군이 공유하는 플랫폼 레이어").
- `internal-system`(trait) 고려했으나 **미선언**: 콘솔은 운영자-facing이나, 그 hard constraint는 아래 3 trait로 완전히 포착된다. 선언해도 별 rule layer가 추가되지 않으므로 `rules/README.md` on-demand 최소주의를 따른다.

## Trait Rationale

- **multi-tenant** — 콘솔의 핵심 UX가 테넌트/제품 컨텍스트 전환이다. GAP JWT의 `tenant_id` claim을 받아 cross-tenant 요청을 거부하고, 테넌트별 가시 제품·권한 집합을 분리한다. 격리 회귀 테스트 필수.
- **integration-heavy** — 콘솔은 N개 도메인 gateway/admin API로 fan-out 한다(BFF 패턴, Phase 7). circuit breaker · retry · timeout 을 `platform/` 베이스라인대로 적용 — 한 도메인 장애가 콘솔 전체 장애가 되지 않아야 한다.
- **audit-heavy** — 콘솔은 GAP `admin-web`의 운영자 작업(계정 lock/unlock, 강제 로그아웃, 감사 조회)을 흡수한다. "누가 언제 어떤 운영 작업을 했는가"는 불변 추적되어야 한다.

미선언 trait와 이유는 § Out of Scope 참조.

## Service Map

### v1 (ADR-MONO-013 Phase 1~2 + Phase 7 skeleton)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `console-web` | frontend-app | 단일 콘솔 UI. GAP OIDC Auth Code+PKCE 로그인, data-driven 서비스 카탈로그, 테넌트 스위처, 도메인 운영 화면(gateway/admin API 호출 렌더). Phase 1 = 부트 가능 skeleton, Phase 2 = GAP 운영자 parity. Phase 4~6 = 4개 non-GAP 도메인(wms/scm/finance/erp) 운영 화면 federation 완료. |
| `console-bff` | rest-api | 교차 도메인 집약 BFF (Backend-for-Frontend). 5 도메인(GAP + wms + scm + finance + erp)의 기존 read API 를 서버사이드 fan-out 으로 합성해 단일 화면 대시보드(MVP = "Operator Overview") 를 제공한다. [ADR-MONO-017](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) (ACCEPTED 2026-05-20) D1-D8 — REST orchestrator, server-side fan-out only, 기존 read 재사용 (zero retrofit), 도메인별 credential 규약 (HARD INVARIANT — `console-integration-contract.md` § 2.4.5/6/7/8 verbatim), per-domain CB + 부분 degrade, `tenant_id` pass-through, per-domain attribution observability. v1 = skeleton + `GET /actuator/health`; MVP "Operator Overview" cross-domain dashboard 는 후속 task (`TASK-PC-FE-011`). |

상세 아키텍처는 각 service의 `specs/services/<service>/architecture.md`에서 선언.

## GAP IdP Integration

`platform-console`은 [global-account-platform](../global-account-platform/PROJECT.md) (GAP)를 표준 OIDC IdP로 사용한다 ([ADR-001](../global-account-platform/docs/adr/ADR-001-oidc-adoption.md)). 단, 다른 프로젝트와 달리 콘솔은 **백엔드 Resource Server가 아니라 OIDC public client(Auth Code + PKCE)** 로서 사용자(운영자) 로그인을 수행하며, 동시에 GAP의 **product/tenant 레지스트리**를 카탈로그 소스로 소비한다.

GAP 측 선행 작업 (spec-first, [TASK-BE-296](../global-account-platform/tasks/ready/TASK-BE-296-platform-console-oidc-client-and-product-registry.md)):
- 콘솔용 OIDC public client (`platform-console-web`, Auth Code + PKCE, redirect=`http://console.local/...`) 등록
- 운영자가 가시 가능한 product/tenant 레지스트리 조회 surface (콘솔 data-driven 카탈로그 소스)

통합 계약 상세: [specs/contracts/console-integration-contract.md](specs/contracts/console-integration-contract.md).

## Local Network

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C 채택 — `console.local` 호스트네임으로 Traefik routing. PORT_PREFIX 미사용. 공유 Traefik 스택(`infra/traefik/`, TASK-MONO-022)에 join.

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **internal-system** (trait) — 운영자-facing이나 hard constraint가 multi-tenant/integration-heavy/audit-heavy로 완전 포착. 별 rule layer 없음 → 미선언 (rules/README on-demand).
- **transactional** (trait) — 콘솔은 도메인 트랜잭션을 소유하지 않는다. 쓰기 작업(운영 명령)은 각 도메인 API에 위임하며 콘솔은 멱등 호출 + 결과 표시만. 강한 일관성·saga는 도메인 측 책임.
- **regulated** / **content-heavy** / **read-heavy** / **real-time** / **batch-heavy** / **data-intensive** — 콘솔 자체에 해당 도메인 자산/제약 없음. PII·감사 보존은 GAP/도메인 측.
- **ecommerce / fan-platform 통합** — ADR-MONO-013 § D6 v1 범위는 엔터프라이즈 스위트(gap/wms/scm + erp/finance). B2C 축은 범위 밖(향후 카탈로그 확장 시 재검토).

이 경계가 바뀌면 본 PROJECT.md의 traits를 수정하고 [rules/traits/](../../rules/traits/)의 해당 파일을 로딩 범위에 포함시킬 것.

## Overrides

현재 명시적 override 없음. 공통/도메인/특성 규칙을 모두 기본값대로 따른다.

예외가 필요한 경우 이 섹션에 다음 형식으로 기록:

```
- **rule**: rules/traits/<trait>.md#<rule-id>
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
```
