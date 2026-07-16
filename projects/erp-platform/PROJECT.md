---
name: erp-platform
domain: erp
traits: [internal-system, transactional, audit-heavy]
service_types: [rest-api, event-consumer]
compliance: []
data_sensitivity: confidential
scale_tier: startup
taxonomy_version: 0.1
---

# erp-platform

## Purpose

전사 기간계(erp) 백엔드 플랫폼. **조직 마스터데이터(부서/직원/직급/비용센터/거래처) → 결재 워크플로 → 통합 조회 read model** 의 흐름을, 마스터데이터 무결성·결재 상태 전이의 추적가능성·통합 조회의 책임 경계와 함께 사내 임직원 전용으로 일관되게 관리한다.

이 프로젝트는 monorepo Phase 6 의 **두 번째 Template 다운스트림 부트스트랩** ([ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md), ACCEPTED 2026-05-19, Option C). 6 프로젝트 동거(wms / ecommerce / GAP / fan-platform / scm / finance) 이후, **7번째 도메인 프로젝트** 로 신규 trait 조합 (`internal-system`-primary 첫 사용) + 사내 기간계 도메인 표면을 검증하는 역할이다. `kanggle/erp-platform` standalone 레포(Template fork)와 monorepo `projects/erp-platform/` direct-include 가 함께 존재한다(Option C).

기존 portfolio 와의 차별점:

- **scm-platform** 은 공급망 노드 간 흐름이고 trait 가 `transactional + integration-heavy + batch-heavy`, **finance-platform** 은 자금 정확성·규제 추적이 핵심이고 trait 가 `transactional + regulated + audit-heavy`. erp 는 사내 기준정보·결재·통합 조회가 핵심이고 trait 가 `internal-system + transactional + audit-heavy` — 라이브러리에 **`internal-system` 을 primary 로 처음** 가하는 신규 stress 축.
- **iam-platform** (GAP) 은 IdP(`saas` 도메인) — erp 는 GAP 의 **B2B_ENTERPRISE** tenant 로 합류(`tenant_id=erp`, V0018 시드).
- **ecommerce / fan-platform** 은 B2C 판매·콘텐츠 도메인. erp 는 외부 공개 트래픽이 없는 사내 전용 시스템 — 정확한 기준정보·결재 추적·권한 경계가 시스템 가치의 중심.

본 프로젝트는 **백엔드 포트폴리오** 로서 프로덕션 지향 설계(internal-system + transactional + audit-heavy 의 동시 트레이드오프)를 보여준다. v1 = backend only — frontend 는 통합 platform console 이 렌더하며([ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) §3.3 backend-only + 콘솔 렌더; GAP/scm/finance 선례) `frontend-app` service_type 을 두지 않는다. erp 의 운영 UI 는 platform-console 의 **parity slice** (마스터 조회·결재함·통합 조회 view; ADR-MONO-013 §3 / §D7.4 parity-checklist 규율) 로 제공된다 — ADR-MONO-016 §D3.1 바인딩.

> **외부 standalone fork 상태 (PENDING)**: 본 부트스트랩 PR-B 머지 시점에는 monorepo side(Option C 의 direct-include)만 landed 된다. 외부 `kanggle/erp-platform` Template fork(`gh repo create … --template kanggle/project-template`)는 classifier-blocked outward-facing op 으로 **사용자 셸 hand-off PENDING** 이다 (finance / TASK-MONO-116 와 정확히 동형 패턴). 사용자 실행 후 별도 append-only resolution recording task 로 객관 검증·기록한다. green-wash 금지 — standalone side 는 사용자 실행 전까지 정직하게 PENDING.

### ADR-MONO-016 vs 7축 메모리 framing 정합

포트폴리오 7축 architecture 메모리는 erp 를 광의의 "회계·구매·재고·HR 통합 기간계" + "admin SPA(대시보드/결재함/마스터관리/통합조회)" 깊이로 기술하나, **본 PROJECT.md 의 Source-of-Truth 는 ADR-MONO-016** (CLAUDE.md § Source of Truth Priority: `platform/` · ADR > 메모리). ADR-016 §D2/D3 가 erp v1 을 **마스터데이터 + 결재 워크플로 + 통합 read model** 로 좁히고, 조달/재고/주문/회계 같은 도메인 비즈니스 로직은 **각 도메인 시스템(scm/wms/ecommerce 등)이 소유** — erp 는 도메인 로직 미보유(7축 책임 경계, `rules/domains/erp.md` E5)라는 점을 명시적으로 못박는다. 또한 ADR-MONO-013(ACCEPTED, Model B) 바인딩으로 "erp 자체 admin SPA" framing 은 **superseded** — erp 는 backend-only, UI 는 platform-console parity slice (ADR-016 §1.5 / §4.3).

## Domain Rationale

`erp` ([rules/taxonomy.md](../../rules/taxonomy.md#erp)) 을 선택한 이유, 그리고 같은 Enterprise & Internal Systems 묶음의 `mes`/`groupware`/`accounting-system`/`scm` 을 (primary 로) 선택하지 않은 이유:

- 핵심 비즈니스가 **전사 기준정보·결재·통합 조회의 일관성**이며, taxonomy `erp` 정의("회계·구매·재고·HR을 통합 관리하는 기간계 시스템 … 전사 기간 업무 데이터가 중심이고 모듈 간 일관성이 핵심일 때")의 portfolio-narrow 슬라이스와 일치한다.
- **`mes` 아님** — 공장 현장 장비/공정 실시간 수집·제어가 핵심이 아니다. mes 는 7축 메모리상 **명시적으로 드롭**된 도메인 (재제안 금지).
- **`scm` 아님** — 공급망 노드 흐름(조달/운송/정산)은 scm-platform 이 소유한다. erp 는 그 사실을 통합 조회만 한다(도메인 로직 미보유).
- **`groupware` 아님** — 사내 협업(문서/일정/메일)이 아니라 기간 업무 기준정보·결재가 핵심.
- **`accounting-system` 아님** — 복식부기/원장 회계 깊이는 erp v1 범위 밖이며 (별 도메인), erp 는 통합 조회 read model 만 — 회계 사실의 권위적 소유 아님.
- `erp` 는 위들과 달리, **전사 기준정보·결재·통합 조회가 핵심이되 단일 도메인 깊이를 소유하지 않을 때** 의 정확한 도메인 선택지.

## Trait Rationale

- **internal-system** ([rules/taxonomy.md](../../rules/taxonomy.md#internal-system)) — 사내 임직원 전용, 외부 공개 트래픽 없음. SSO 인증·권한 매트릭스 인가·운영 추적·외부 노출 금지·내부망 제약이 시스템 설계를 좌우한다(도메인 룰 E6·E7·E8). monorepo 에서 **`internal-system` 이 primary trait 로 처음** 선언되는 사례 — Phase 6 의 신규 stress 축 (scm 의 `transactional+integration-heavy+batch-heavy`, finance 의 `transactional+regulated+audit-heavy`, fan 의 saas/`multi-tenant` 와 구별되는 새 검증 가치).
- **transactional** — 결재 워크플로 상태 전이 + 마스터데이터 변경은 강한 일관성·멱등성 요구. 동일 결재 전이의 중복 요청은 한 번만 처리되어야 하고, 마스터 변경 + 감사 기록 + 이벤트 발행이 한 트랜잭션. `transactional` trait 의 idempotency / 상태기계 규칙이 도메인 룰 E1·E3·E4 에 직접 적용.
- **audit-heavy** — 마스터 변경·결재 전이·권한 변경이 actor/time/before/after 의 불변(append-only) 감사 기록을 남겨야 한다(도메인 룰 E2·E4·E8). 전사 거버넌스상 "누가 무엇을 바꿨는가"의 책임 추적이 필수.

미선언 trait 와 그 이유는 § Out of Scope 참조. `workflow-heavy` 는 11 taxonomy trait 에 없는 서술적 표현 — 선언 금지(HARDSTOP-02 회피); 워크플로 강조는 architecture/scope 레벨에서 표현한다 (ADR-016 §D2 lesson).

## Service Map

### v1 (포트폴리오 1차 목표)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, GAP RS256 JWT 검증 (OAuth2 Resource Server), `tenant_id=erp` 게이트, internal-only 경계 강제 |
| `masterdata-service` | rest-api | 조직 마스터데이터(부서/직원/직급/비용센터/거래처) 라이프사이클 — 참조 무결성, 유효기간, 불변 audit_log. 첫 service skeleton 의 1차 대상 (TASK-ERP-BE-001). |
| `read-model-service` | rest-api + event-consumer | 통합 조회 read model **첫 증분** (TASK-ERP-BE-007) — masterdata 의 `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1` 4 토픽 구독 → projection 투영 → employee org-view(부서경로+비용센터+직급) read-only REST. 도메인 로직 미보유(E5). v2 풀 통합조회는 아래 v2 항목으로 잔존. |

> **frontmatter `service_types` 에 `event-consumer` 추가 (TASK-ERP-BE-007)** — ADR-MONO-016 §D2 의 조건부("`rest-api` minimum **+ `event-consumer` if the integrated read model subscribes to domain events**")가 read-model-service 첫 증분으로 충족되어 발현. read-model-service 는 단일 deployable 에 `rest-api`(read-only 조회) + `event-consumer`(masterdata 이벤트 구독)를 결합한다(scm `inventory-visibility-service` 선례 — "read exactly one service-type file" 의 문서화 예외, 양 service-type 파일 모두 읽음). masterdata-service 는 여전히 `rest-api` 단일(outbox≠event-consumer).

### v2 (deferred — 별도 부트스트랩 task)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `approval-service` | rest-api | 결재 워크플로 (1~2단계 라우팅, 상태기계, 대결/위임, 결재함) — ADR-016 §D3 v2 (룰 라이브러리가 `internal-system` 을 작은 범위에서 먼저 소화한 후) |
| `read-model-service` (풀 통합조회) | rest-api + event-consumer | 첫 증분(employee org-view, TASK-ERP-BE-007)은 위 v1 표에서 활성. **잔여 v2** = business-partner + 결재/권한 등 전 도메인 사실 합성 + per-operator `org_scope` read 필터(membership-derived). |
| `permission-service` | rest-api | 권한 매트릭스 / 데이터 범위 / SSO 신원 ↔ 내부 권한 매핑 |
| `notification-service` | event-consumer | 결재 상신·승인·반려, 마스터 변경, 권한 변경 알림 fanout |
| `admin-service` | rest-api | 운영 콘솔 백엔드 (예외 결재 검토, 권한 이상, 마스터 충돌 큐) |

상세 아키텍처는 각 service 의 `specs/services/<service>/architecture.md` 에서 선언.

## IAM IdP Integration

`erp-platform` 은 [iam-platform](../iam-platform/PROJECT.md) (GAP) 을 표준 OIDC IdP(SSO) 로 사용한다 ([ADR-001](../iam-platform/docs/adr/ADR-001-oidc-adoption.md)). 모든 erp-platform 서비스는 OAuth2 Resource Server 패턴으로 GAP 의 JWKS 기반 RS256 access token 을 검증하고, `tenant_id=erp` claim 만 통과시킨다 (internal-system 경계 — 외부 공개 트래픽 없음).

GAP 측 인프라 (TASK-MONO-119 V0018 시드):
- account-service V0018: `tenants` 에 `erp` row (B2B_ENTERPRISE — scm/finance 와 동일 type, 내부-서비스 모델)
- auth-service V0018: `oauth_clients` 에 `erp-platform-internal-services-client` (client_credentials, scopes=`erp.read`/`erp.write`)
- v1 = backend only. user-flow PKCE client 는 별도 V slot (v1 미발행 — 콘솔이 GAP public client 로 렌더, ADR-MONO-013).
- `platform-console` (ADR-MONO-013 Model B) 은 erp 의 **external operator read consumer** 로서, GAP 자신의 `platform-console-web` 콘솔 클라이언트 토큰으로 v1-live read 표면 (`/api/erp/masterdata/{departments,employees,job-grades,cost-centers,business-partners}` list+detail 10 GET) 을 server-side 소비한다 (write/mutation + v2 services 제외). 이는 **`erp-platform-internal-services-client` 가 아니며** (해당 client 는 v1 의 유일한 erp OAuth client 로 유지·무관) — 검증 경로는 기존 GAP RS256 + `tenant_id ∈ {erp,*}` 체인 그대로, 신규 erp OAuth client / route / auth-model 변경 없음. erp 는 backend-only 를 유지하고 (no erp frontend, no erp user-flow client), 콘솔 traits (`multi-tenant`/`integration-heavy`) 는 콘솔의 것이지 erp 의 것이 아니다 (frontmatter 불변). 상세 = [specs/integration/iam-integration.md § platform-console Operator Read Consumer](specs/integration/iam-integration.md).

dev 환경 토큰 발급 예:
```
curl -u erp-platform-internal-services-client:erp-dev \
     -d "grant_type=client_credentials&scope=erp.read" \
     http://iam.local/oauth2/token
```

> 이 머신(`client_credentials`) 토큰은 `org_scope`(data-scope)가 없어 **부서-타겟 write(employee·cost-center·자식 부서)는 403 `DATA_SCOPE_FORBIDDEN`** 이다 — read + 부서-타겟 없는 write(루트 부서·job-grade·거래처)만 가능. 부서-스코프 write 는 운영자 토큰(`org_scope` 보유) 필요. 상세 = [specs/integration/iam-integration.md § dev smoke test](specs/integration/iam-integration.md) (TASK-ERP-BE-029).

통합 상세는 [specs/integration/iam-integration.md](specs/integration/iam-integration.md).

## Local Network

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C 채택 — `erp.local` 호스트네임으로 Traefik routing. PORT_PREFIX 미사용. 부트스트랩 시점부터 `infra/traefik/` 의 공유 인프라에 join.

## v1 IN / OUT slice

**v1 IN (범위 안)**:
- `masterdata-service` 조직 마스터데이터 라이프사이클: 부서 계층 / 직원(조직 속성) / 직급 / 비용센터 / 거래처 마스터 (참조 무결성, 유효기간).
- 모든 마스터 변경의 불변 audit_log (E2).
- GAP RS256 OAuth2 Resource Server + `tenant_id=erp` fail-closed 게이트 + internal-only 경계.
- 결재 워크플로 1~2단계 모델 (forward-decl — 실 결재 service 는 v2 approval-service) + 통합 read model 책임 경계 선언.
- `read-model-service` 통합 조회 **첫 증분** (TASK-ERP-BE-007) — masterdata 이벤트 4 토픽 구독 → employee org-view(부서경로+비용센터+직급) read-only 투영. 마스터 변경 전파 루프를 닫는다(E5 read-only).
- `gateway-service` 엣지 라우팅 (masterdata-service 활성화와 함께).

**v1 OUT (v2 deferred)**:
- 결재 워크플로 풀 구현 (다단 라우팅·대결·위임·결재함) → `approval-service` (v2, ADR-016 §D3).
- 통합 read model **풀** 구현 (business-partner + 전 도메인 사실 합성 + per-operator `org_scope` read 필터) → `read-model-service` v2 잔여. 첫 증분(employee org-view, masterdata 이벤트 구독)은 v1 IN (TASK-ERP-BE-007).
- 별도 permission-service / notification-service / admin-service.
- 인사 깊이(급여/근태/평가) — erp 는 7축상 도메인 로직 미보유; 인사 깊이는 별 도메인.
- BI / 분석 / 대시보드 깊이 — platform-console parity slice 가 운영 view 제공 (ADR-013).
- frontend — 통합 platform console 이 렌더 (ADR-MONO-013 §3.3, `frontend-app` service_type 없음).

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **mes / groupware / accounting-system / scm** (도메인) — § Domain Rationale 참조. 공장 실행·사내 협업·회계 원장·공급망 흐름은 erp 의 primary 책임이 아니다. mes 는 7축상 명시적 드롭(재제안 금지).
- **integration-heavy** (trait) — taxonomy 정의가 **외부** 벤더 다중 연동(PG/통신사/배송사 3+)을 강조. erp 의 "통합" 은 **내부 cross-service 이벤트 구독/조회** 라 loose fit — 선언하지 않고 통합 read model 경계를 도메인 룰 E5 + architecture 레벨에서 표현한다 (ADR-016 §D2 caveat).
- **read-heavy** (trait) — 통합 read model 의 CQRS read side 가 있으나(TASK-ERP-BE-007 read-model-service 첫 증분 활성) v1 핵심 워크로드가 대량 읽기 트래픽이 아니다 — 사내 운영자 조회 규모. **계속 미선언**(read-model-service 첫 증분은 읽기-무거움이 아님). 풀 통합조회(v2)가 대량 read 트래픽 축이 되면 그때 trait 추가 검토.
- **multi-tenant** (trait) — GAP 의 `tenant_id=erp` claim 은 수신하나, erp-platform 내부에서 다수 organization 을 격리하는 SaaS 가 아님 (단일 사내 기간계 운영). GAP 가 multi-tenant IdP 역할.
- **regulated** (trait) — 규제 준수가 시스템 설계를 좌우하는 도메인(finance/의료/공공)이 아니다. 사내 거버넌스는 `audit-heavy` + `internal-system` 으로 커버.
- **event-driven** — taxonomy 의 11 trait 에 없는 분류 (HARDSTOP-02 회피). 이벤트 흐름은 `transactional` trait + 도메인 룰로 커버.
- **batch-heavy / real-time / data-intensive / content-heavy** (trait) — 야간 대량 배치·초단위 지연·대용량 분석·콘텐츠가 v1 핵심 워크로드가 아니라 기준정보·결재·통합 조회의 정확성.

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
