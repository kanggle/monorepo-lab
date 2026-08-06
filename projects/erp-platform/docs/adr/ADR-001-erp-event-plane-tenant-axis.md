# ADR-001: erp 이벤트 평면의 테넌트 축 — 선언된 단일 테넌트 vs 데모가 만든 다중 테넌트

**Status**: Proposed
**Date**: 2026-08-07 (proposed)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: `TASK-ERP-BE-043`(이 ADR 이 게이트하는 티켓), `TASK-ERP-BE-032`(masterdata 봉투 선례), `TASK-MONO-510`(데모 시드), `projects/erp-platform/PROJECT.md` § Out of Scope, `specs/services/read-model-service/architecture.md` § Multi-tenancy, `specs/contracts/events/erp-approval-events.md` § Envelope

---

## Context

`TASK-ERP-BE-043` 은 approval 봉투가 최상위 `tenantId`/`aggregateType`/`aggregateId` 를 안
실어 `erp.approval.*` 전량이 DLT 로 가는 문제였다. 그 **구조** 부분은 이 티켓에서 고쳤다
(계약이 셋 다 선언하고 있었고, 형제 `masterdata` 는 `TASK-ERP-BE-032` 로 이미 고쳐져 있었다).

고치자 **그 아래의 관문이 드러났고, 그것은 결함이 아니라 스펙이었다.** 봉투 수정 후
같은 스택에서 실측:

```
수정 전 : InvalidEnvelopeException: Invalid delegation envelope
                                    (missing eventId/aggregateId/payload/grantId)
수정 후 : InvalidEnvelopeException: Non-erp tenant 'demo-corp'
                                    on topic erp.approval.delegated.v1
          (DelegationEnvelopeToCommandMapper.java:48)
```

파싱은 통과하고 **테넌트 관문**에서 막힌다. 그리고 그 관문은 세 문서가 명시한 모델이다:

| 출처 | 문장 |
|---|---|
| `PROJECT.md` § Out of Scope | **multi-tenant** (trait) — "erp-platform 내부에서 다수 organization 을 격리하는 SaaS 가 아님 (단일 사내 기간계 운영)" |
| `read-model-service/architecture.md` § Multi-tenancy | "**N/A as SaaS row-level isolation — single-tenant by project classification.** … **All projected rows belong to the `erp` tenant.**" |
| 같은 문서 § 위임 프로젝션 | "invalid envelope (null `eventId`/`grantId`/`payload`, **non-`erp` tenant**) → immediate DLT" |
| `erp-approval-events.md` / `erp-masterdata-events.md` § Envelope | 봉투 예시가 `"tenantId": "erp"` — 다른 필드는 `"<uuid>"` 식 플레이스홀더인데 이것만 **리터럴** |

즉 `DelegationEnvelopeToCommandMapper` 의 관문은 **선언된 모델을 유일하게 집행하는 코드**다.

## 🔴 그런데 실행 중인 시스템은 그 모델을 이미 벗어나 있다

데모(`TASK-MONO-510` 단일 계정 올-도메인)는 콘솔 운영자를 assume-tenant 로 `demo-corp` 에
태우고, erp 게이트웨이는 `entitled_domains` 로 그것을 받아들인다. 그 결과 **두 프로듀서 모두
고객 테넌트를 싣는다** — 같은 스택·같은 순간 실측:

```
masterdata 봉투 : "tenantId":"demo-corp"                      ← 최상위
approval  봉투 : "tenantId":"demo-corp"                      ← 이 티켓이 실은 뒤
```

masterdata 쪽이 **조용히 통과한 이유는 관문이 없어서**다. 전수 확인: `hasTenant|requiredTenant`
를 가진 파일은 read-model 소비자 중 **위임 2개뿐**이다. 그리고 프로젝션 스키마도 갈려 있다 —
`delegation_fact_proj` 에는 `tenant_id` 컬럼이 **있고**, `employee_proj` 에는 **없다**.

🔴 그래서 지금 상태는 *"위임만 이상하다"* 가 아니라 **"선언은 단일 테넌트인데 데이터는 다중
테넌트이고, 그걸 알아채는 코드가 한 곳뿐"** 이다. `employee_proj` 4행은 전부 `demo-corp`
데이터인데 그 사실을 기록조차 하지 않는다.

## 🔴 설정으로는 못 고친다

`erpplatform.oauth2.required-tenant-id`(기본 `erp`)를 **세 곳이 서로 다른 축으로** 읽는다:

| 읽는 곳 | 해석 | `demo-corp` 결과(실측) |
|---|---|---|
| `ServiceLevelOAuth2Config` (HTTP JWT) | **도메인** 키 (`trustEntitledDomains`) | **통과** |
| `ReadAuthorizationGate` | **도메인** 키 (entitlement) | **통과** |
| `DelegationEnvelopeToCommandMapper` | **테넌트 값** (등호 비교) | **거절** |

`OIDC_REQUIRED_TENANT_ID=demo-corp` 로 바꾸면 이벤트 관문은 열리지만 **HTTP 인증이 도메인
전체에서 깨진다.** 한 프로퍼티가 두 축을 겸하고 있어 값 하나로는 양쪽을 동시에 만족시킬 수 없다.

## Decision (필요 — 미결)

**Option A — erp 는 선언대로 단일 테넌트다. 프로듀서가 `tenantId="erp"` 를 찍는다.**
- 계약 리터럴·PROJECT.md·read-model 스펙과 **전부 일치**. ADR 없이 스펙 준수로 닫힌다.
- 위임 프로젝션이 즉시 찬다(관문 통과). 데모 화면 해결.
- 🔴 대가: erp 는 **어느 고객 테넌트의 사실인지 기록하지 않는다.** `delegation_fact_proj.tenant_id`
  는 전 행 `erp` 가 된다. `payload.tenantId` 에는 남으므로 와이어에서 유실되진 않는다.
- 🔴 그리고 이것은 **현실을 문서에 맞추는 것이 아니라 현실을 지우는 것**일 수 있다 — 실제로
  여러 테넌트가 erp 를 쓰고 있다면(`omni-corp` 등) 그 구분이 프로젝션에서 사라진다.

**Option B — erp 를 다중 테넌트로 승격한다.**
- `PROJECT.md` 의 traits 에 `multi-tenant` 추가 + `rules/traits/multi-tenant.md` 를 로딩 범위에.
- 위임 관문을 등호 비교에서 **"테넌트가 존재하는가"** 로 바꾸고, 읽기 경계에서 호출자 테넌트로
  필터링(현재 **읽기 경로에 테넌트 필터가 전혀 없다** — 실측).
- `employee_proj` 등 masterdata 프로젝션에 `tenant_id` 컬럼 추가(마이그레이션).
- 🔴 대가: 범위가 크고, PROJECT.md 분류 변경은 룰 레이어 로딩까지 바꾼다.

**Option C — 데모를 erp 테넌트로 되돌린다.**
- erp 데모 데이터를 `demo-corp` 가 아니라 `erp` 테넌트로 시드한다.
- 🔴 단일 계정 올-도메인 데모의 전제(assume-tenant 로 `demo-corp` 선택)와 충돌한다 — 콘솔은
  테넌트를 골라야 erp 를 읽는다(실측: 테넌트 미선택 시 `TENANT_FORBIDDEN`).

## 왜 구현자가 못 고르나

A 를 고르면 **관측된 다중 테넌트 사실을 버리는 결정**이고, B 는 **PROJECT.md 의 traits 를
바꾸는 결정**이며, C 는 **데모 이니셔티브의 전제를 바꾸는 결정**이다. 셋 다 서비스 하나의
클래스 안에서 끝나지 않는다 — `platform/architecture-decision-rule.md` 가 구현 중 아키텍처
선택을 금지하는 정확한 경우다.

## Consequences (미결 상태의)

- `TASK-ERP-BE-043` 의 **AC-3 / AC-5 는 닫을 수 없다.** 봉투(AC-1/AC-2)와 재현(AC-0)은 닫혔다.
- 콘솔 `/erp/delegation` 의 read-model 위임 뷰는 계속 빈다(원본 목록 `/api/erp/approval/delegations`
  는 정상). erp 콘솔은 **5/6** 유지.
- `erp.approval.*` 여섯 토픽 중 지금 트래픽이 있는 셋(`submitted`·`delegated`·`delegation.revoked`)
  이 전부 이 관문에서 DLT 로 간다 — 실측.
