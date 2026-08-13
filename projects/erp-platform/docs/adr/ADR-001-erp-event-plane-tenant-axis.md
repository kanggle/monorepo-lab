# ADR-001: erp 이벤트 평면의 테넌트 축 — 선언된 단일 테넌트 vs 데모가 만든 다중 테넌트

**Status:** Accepted — **D**
**Date**: 2026-08-07 (proposed) · 2026-08-12 (전제 재측정 + Option D 추가) · **2026-08-12 ACCEPTED — D** (소유자 정확형)
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

---

## 🛑 2026-08-12 재측정 — 위 두 문장이 **모두 부정확했다**

`TASK-MONO-519` 의 라이브 검증(iam + erp 동시 기동) 중에 얻은 실측이다. 이 ADR 은 아직
`Proposed` 이고 아무도 A/B/C 를 고르지 않았으므로, **결정 전에 전제를 고친다.**

### ① 🔴 "데이터가 다중 테넌트" 가 아니다 — **단일인데 이름이 다르다**

`tenant_id` 컬럼을 가진 erp 테이블 전부를 세었다(추정 아님, `GROUP BY tenant_id`):

```
erp_db.departments          demo-corp  4        erp_approval_db.approval_request   demo-corp  3
erp_db.employees            demo-corp  4        erp_approval_db.delegation_grant   demo-corp  3
erp_db.business_partners    demo-corp  3
erp_db.cost_centers         demo-corp  3        문자열 'erp' 를 담은 행 :  0
erp_db.job_grades           demo-corp  3        서로 다른 tenant 값의 수 : 1
```

⇒ erp 는 **실제로 단일 테넌트로 운영되고 있다.** 불일치는 *단일 vs 다중* 이 아니라
**그 하나뿐인 테넌트를 뭐라고 부르는가**(`erp` vs `demo-corp`)다. 이 ADR 의 제목과 Context 가
문제를 한 단계 크게 명명했고, 그 명명이 B(다중 테넌트 승격)를 실제보다 그럴듯하게 보이게 한다.

🔵 다만 **구조적으로는** 다중이 가능하다 — `entitled_domains` 를 가진 어떤 테넌트든 assume 으로
erp 에 들어올 수 있다. 지금 그런 테넌트가 **0개**라는 것이 실측이지, 못 들어온다는 뜻이 아니다.

### ② 🔴 "그걸 알아채는 코드가 한 곳뿐" — 관문은 **두 곳**이고, 하나는 다른 서비스다

원문의 전수 확인은 **read-model-service 안에서만** 셌다. 저장소 전체로 다시 세면:

| 서비스 | 파일 | 막는 범위 |
|---|---|---|
| `read-model-service` | `DelegationEnvelopeToCommandMapper` | 위임 2토픽 |
| `notification-service` | **`EnvelopeToCommandMapper`** | **`erp.approval.*` 전 타입** |

둘 다 같은 프로퍼티를 같은 의미(값 등호비교)로 읽는다. 그래서 영향 화면도 하나가 아니다 —
`/erp/delegation` 뿐 아니라 **ERP 알림함이 구조적으로 0** 이다(실측: `GET /api/erp/notifications`
→ `200` / `totalElements 0`, `erp_notification_db.notification` **0행**). 소비자 로그가 원인을
그대로 적는다:

```
Out-of-contract tenantId 'demo-corp' on topic erp.approval.submitted.v1
  (single-tenant invariant: erp)
```

세 토픽(`delegated` · `submitted`×2 · `approved`)이 전부 DLT.

### ③ 🔴🔴 관문은 **이미 불변식을 지키지 못하고 있다** — 대조군이 같은 DB 안에 있다

같은 스택·같은 순간의 read-model 프로젝션 행 수다:

| 프로젝션 | 행 | 소비자에 테넌트 관문 |
|---|---|---|
| `approval_fact_proj` | **2** | ❌ 없음 (`ApprovalEnvelopeToCommandMapper`) |
| `department_proj` | **4** | ❌ 없음 |
| `employee_proj` | **4** | ❌ 없음 |
| `cost_center_proj` | **3** | ❌ 없음 |
| `job_grade_proj` | **3** | ❌ 없음 |
| `delegation_fact_proj` | **0** | ✅ 있음 |

*"All projected rows belong to the `erp` tenant"* 는 **이미 16행이 위반**하고 있고, 전부
`demo-corp` 데이터다. 관문이 있는 소비자는 read-model 6개 중 **1개**다.

🔴 그러므로 관문을 남기는 것은 *"불변식을 지킨다"* 가 아니라 **"소비자 하나(+ notification)만
굶긴다"** 이다. 그리고 이것은 이 저장소가 반복해서 밟은 모양이다 — **대조군이 결함이면 고쳐질 때
가드가 죽는다**: 여기서는 반대로, 가드가 지키려던 불변식을 **가드 없는 형제 5개가 매일 깨고
있는데도** 아무 신호가 없다.

### ④ 🔵 그래서 A 를 고르면 **새 모순이 하나 생긴다** (Consequences 에 없던 대가)

A(프로듀서가 `tenantId="erp"` 를 찍는다)를 채택하면 `delegation_fact_proj.tenant_id` 는 전 행
`erp` 가 된다. 그런데 그 행의 **원본**인 `erp_approval_db.delegation_grant.tenant_id` 는
`demo-corp` 다(위 ①). ⇒ **투영이 자기 출처와 어긋난다.** 나머지 6개 프로젝션은 `tenant_id`
컬럼이 없어 대조조차 안 된다. A 의 대가를 이 ADR 은 *"어느 고객 테넌트의 사실인지 기록하지
않는다"* 로만 적었는데, 실제로는 *"틀린 값을 기록한다"* 에 가깝다.

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
- 🔴 **2026-08-12 추가**: `TASK-MONO-519` 가 닫은 ERP 결재 루프(상신자 ≠ 승인자, 라이브 검증)가
  통째로 `demo-corp` 위에 서 있다 — `approval_request` 3행 · `operator_tenant_assignment` ·
  `seed-erp.sh` 20건 전부. C 는 방금 닫은 티켓을 되돌리는 방향이다.

**Option D — 관문을 값-등호에서 "테넌트가 존재하는가" 로 낮춘다. 봉투는 사실을 그대로 싣는다.**
*(2026-08-12 추가 — 위 § 재측정에서 나온 안. 원안 A/B/C 는 전부 "값을 무엇에 맞출까" 를 묻는데,
재측정이 가리키는 문제는 **축이 안 갈라진 것**이다.)*

- 이벤트 평면의 관문 **2곳**(`DelegationEnvelopeToCommandMapper` ·
  `notification`/`EnvelopeToCommandMapper`)이 `erpplatform.oauth2.required-tenant-id` 를
  **더 이상 읽지 않게** 한다. 그 프로퍼티는 HTTP 세 곳에서 *도메인 키*로 쓰이는 값이고,
  이벤트 쪽이 그것을 *테넌트 값*으로 재해석한 것이 이 ADR 전체의 원인이다.
- 봉투는 **사실(`demo-corp`)을 그대로** 싣는다 ⇒ 투영이 출처와 일치하고(④의 모순 없음),
  나머지 6개 프로젝션이 담고 있는 값과도 같아진다.
- `PROJECT.md` 의 traits 는 **바꾸지 않는다.** erp 는 여전히 단일 테넌트다(①) — 이름이 `erp` 가
  아닐 뿐이므로 B 의 큰 비용(룰 레이어 로딩 변경 · 읽기 필터 신설 · 스키마 마이그레이션)이
  발생하지 않는다. 격리할 두 번째 테넌트가 **0개**인데 격리 기계를 짓지 않는다.
- 두 계약 문서의 봉투 예시 `"tenantId": "erp"` **리터럴을 플레이스홀더로 정정**한다. 다른 필드는
  `"<uuid>"` 형태인데 이것만 값이라, 고치지 않으면 다음 사람이 또 상수로 읽고 같은 관문을 만든다.
- 🔴 **잃는 알람을 더 나은 알람으로 바꾼다.** 지금 관문은 *정상 이벤트마다* 울려서 정보량이 0 이다
  (③: 지키려는 불변식은 이미 16행이 깨고 있고 관문은 그것을 못 본다). 대신
  **"erp 전체에서 distinct `tenant_id` 가 2 이상이면 RED"** 인 래칫을 둔다 — 실제로 erp 가
  다중 테넌트가 되는 그 순간에만 울고, 그때가 B 를 다시 논의할 시점이다.
- 🔴 대가: 이벤트 평면에서 **테넌트 검증이 사라진다.** 남의 테넌트 봉투가 흘러들어도 그대로
  투영된다. 위 래칫이 그 공백을 대신하지만, 래칫은 **사후 탐지**이지 **거부**가 아니다.
  이 교환을 받아들일지가 D 의 유일한 질문이다.

## 왜 구현자가 못 고르나

A 를 고르면 **선언된 이름을 위해 저장된 값과 어긋나는 값을 쓰는 결정**이고, B 는
**PROJECT.md 의 traits 를 바꾸는 결정**이며, C 는 **데모 이니셔티브의 전제를 바꾸는 결정**이고,
D 는 **이벤트 평면의 테넌트 거부를 사후 탐지와 맞바꾸는 결정**이다. 넷 다 서비스 하나의 클래스
안에서 끝나지 않는다 — `platform/architecture-decision-rule.md` 가 구현 중 아키텍처 선택을
금지하는 정확한 경우다.

🔵 **구현자의 추천은 D 다**(위 ①~④의 실측 근거로). 다만 이것은 추천이지 결정이 아니며,
`deciders` 의 **정확형 intent** 없이는 어느 것도 ACCEPTED 가 되지 않는다.

---

## 결정 — **D** (ACCEPTED 2026-08-12)

**관문을 값-등호에서 존재검사로 낮추고, 봉투는 사실을 그대로 싣는다.** 위 § Decision 의
Option D 텍스트 그대로이며, 이 절은 그것을 **확정**할 뿐 재서술하지 않는다.
§ Context / § 재측정 ①~④ / § 설정으로는 못 고친다 / § Decision(A·B·C·D) / § 왜 구현자가
못 고르나 는 **byte-unchanged** — ACCEPT 는 확정이지 재결정이 아니다.

### 무엇이 구속력을 갖나

| | 구속력 |
|---|---|
| **D 자체** — 이벤트 평면의 관문 **두 곳**(`read-model`/`DelegationEnvelopeToCommandMapper` · `notification`/`EnvelopeToCommandMapper`)이 `erpplatform.oauth2.required-tenant-id` 를 **더 이상 읽지 않는다** | **binding** |
| **봉투는 사실을 싣는다** — `tenantId` 에 `demo-corp`(실제 값). 투영이 출처와 일치하고 나머지 6개 프로젝션의 값과도 같아진다 | **binding** |
| **A 배제** — 선언된 이름에 맞추려고 저장된 값과 다른 값을 쓰지 않는다. ④의 "출처와 어긋나는 프로젝션" 을 만들지 않는다 | **binding** |
| **B 배제** — `PROJECT.md` 의 traits 는 **바꾸지 않는다.** erp 는 단일 테넌트로 남고, 룰 레이어 로딩 변경·읽기 테넌트 필터 신설·프로젝션 스키마 마이그레이션은 **범위 밖** | **binding** |
| **C 배제** — 데모를 `erp` 테넌트로 되돌리지 않는다. `TASK-MONO-519` 가 닫은 결재 루프(`demo-corp` 위)는 **유지** | **binding** |
| **계약 리터럴 정정** — 두 계약 문서의 봉투 예시 `"tenantId": "erp"` 를 플레이스홀더로. 다른 필드가 `"<uuid>"` 형태인데 이것만 값이라, 안 고치면 다음 사람이 또 상수로 읽고 같은 관문을 만든다 | **binding** |
| **래칫** — "erp 전체에서 distinct `tenant_id` 가 2 이상이면 RED". 잃는 알람의 대체물이며, 울리는 그때가 B 를 다시 논의할 시점이다 | **binding** |
| **교환의 수용** — 이벤트 평면에서 테넌트 **거부**가 사라지고 **사후 탐지**로 바뀐다 | **binding** |

### 🔵 rider 점검 — 남은 미결이 **없다**

§ Decision 의 D 는 말미에 *"이 교환을 받아들일지가 D 의 유일한 질문이다"* 라고 자기 안의
질문을 명명해 두었다. **D 를 고르는 것이 그 질문에 대한 "예" 다** — 질문이 D *안쪽*에
남아 있는 형태가 아니라, D 의 선택 그 자체가 답인 형태다. 따라서 `ADR-MONO-060` 의
plain `A`(본문이 `act` 를 미결로 명명해 두어 선택으로 확정되지 않았던 경우)나
`ADR-004` 의 e2e 탈출구 rider 와 **다르다**. 미결로 승격할 항목은 없다.

🔴 다만 **래칫이 어디서 도는지는 D 가 말하지 않는다.** 그것은 아키텍처 결정이 아니라
구현이므로 이 ACCEPT 가 정할 일이 아니지만, 정하지 않으면 래칫은 코드가 아니라 주석이
된다(`TASK-MONO-518`/`524` 에서 두 번 밟았다: 술어만 있고 도는 레인이 없는 가드는 영원히
초록이다). `TASK-ERP-BE-043` 의 **AC-7** 로 승격했다 — 도는 레인 + 무는지 확인까지.

### ACCEPT 가 인가하는 것 / 하지 않는 것

인가되는 것은 `TASK-ERP-BE-043` 의 **AC-3 / AC-5 / AC-6 착수**뿐이다(HARDSTOP-09 해제).
계약(`specs/contracts/events/`)은 CLAUDE.md § Layer Rules 대로 **구현 전에** 갱신돼야 하고,
이 ACCEPT 는 그 계약의 **내용**(필드 형태·플레이스홀더 표기)을 승인하지 않는다 — 그것은
그 티켓이 제안하고 리뷰가 검사할 몫이다.

🔵 아래 § Consequences 는 **byte-unchanged 로 남긴다.** 제목의 *"(미결 상태의)"* 는 그
절이 **결정 전 상태**를 기록한 것임을 뜻하며, 무엇이 언제 풀리는지는 위 표와 `BE-043` 이
말한다. 두 화면(위임 뷰 · 알림함)은 **ACCEPT 로 차지 않는다** — 구현이 차게 한다.

### 게이트 — 통과했지, 우회하지 않았다

`platform/architecture-decision-rule.md` § The ACCEPTED Gate 가 요구하는 정확형이
도착한 뒤에만 전환했다:

```
ADR-ERP-001 ACCEPTED — D
```

🔴 **직전 메시지는 넘기지 않았다.** 소유자가 *"추천 D로 결정"* 이라고 보냈고 글자 `D` 가
분명히 적혀 있었지만, 같은 문장 안의 **"추천" 은 이 문서 § 왜 구현자가 못 고르나 의 🔵 —
즉 구현자 자신의 선호**다. 그것을 승인으로 읽는 것이 규정이 *"launders an agent's own
preference into an accepted decision"* 이라며 금지하는 형태와 구별되지 않는다. 멈춰서
정확형을 요청했고, 그때 위 한 줄이 도착했다. **self-ACCEPT 아님.**
(같은 자리에서 같은 이유로 멈춘 기록: `ADR-MONO-059` · `ADR-004`.)

---

## Consequences (미결 상태의)

- `TASK-ERP-BE-043` 의 **AC-3 / AC-5 는 닫을 수 없다.** 봉투(AC-1/AC-2)와 재현(AC-0)은 닫혔다.
- 콘솔 `/erp/delegation` 의 read-model 위임 뷰는 계속 빈다(원본 목록 `/api/erp/approval/delegations`
  는 정상).
- 🔴 **2026-08-12 정정 — 막히는 화면은 하나가 아니라 둘이다.** ERP **알림함**도 구조적으로 0 이다
  (`GET /api/erp/notifications` → `200` / `totalElements 0`, DB 0행). 원인은 같은 관문의
  두 번째 사본(`notification-service`/`EnvelopeToCommandMapper`)이고, 이 사실은 `TASK-MONO-519`
  가 두 번째 콘솔 신원을 심어 결재함을 채운 **뒤에야** 관측 가능해졌다 — 그전에는 알림을 받을
  신원 자체가 없어서 "0" 이 무엇 때문인지 갈리지 않았다.
  ⇒ erp 콘솔 화면 수는 `TASK-MONO-519` 로 결재함이 차면서 올라가지만, 알림함은 이 ADR 이
  결정될 때까지 빈 채로 남는다.
- `erp.approval.*` 여섯 토픽 중 지금 트래픽이 있는 넷(`submitted`·`approved`·`delegated`·
  `delegation.revoked`)이 전부 이 관문에서 DLT 로 간다 — 실측(`approved` 는 `TASK-MONO-519` 의
  라이브 승인으로 이번에 처음 발생했다).
