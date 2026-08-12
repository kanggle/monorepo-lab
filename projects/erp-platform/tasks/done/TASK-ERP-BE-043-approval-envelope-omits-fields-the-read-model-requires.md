# Task ID

TASK-ERP-BE-043

# Title

approval-service 의 이벤트 봉투가 read-model 이 **필수로 요구하는 두 필드를 안 싣는다** — 위임 사실 프로젝션이 전량 DLT 로 간다

# Status

done

# Owner

erp-platform

# Task Tags

- bug
- contract
- integration

---

# 배경 — `TASK-ERP-BE-042` 가 릴레이를 살리자 **그 아래에서** 드러났다

BE-042 이전에는 erp 아웃박스가 **한 건도 발행되지 않았다**. 그래서 이 결함은 존재만 하고
관측될 수 없었다. 릴레이를 켜자 첫 메시지가 곧바로 DLT 로 갔다:

```
DelegationFactEventConsumer : Invalid delegation envelope on topic=erp.approval.delegated.v1
                              offset=0; routing to DLT
InvalidEnvelopeException    : Invalid delegation envelope
                              (missing eventId/aggregateId/payload/grantId)
erp.approval.delegated.v1      end-offset 1
erp.approval.delegated.v1.DLT  end-offset 2
delegation_fact_proj           0행
```

## 🔴 대조군이 원인을 확정한다 — 같은 스택, 같은 순간, 두 프로듀서

**masterdata-service 가 낸 것 (프로젝션 정상):**

```json
{"eventId":"…","eventType":"erp.masterdata.department.changed","source":"…",
 "occurredAt":"…","schemaVersion":1,
 "tenantId":"demo-corp","aggregateType":"department","aggregateId":"019fd768-957b-…",
 "partitionKey":"019fd768-957b-…","payload":{…}}
```

**approval-service 가 낸 것 (DLT):**

```json
{"eventId":"…","eventType":"erp.approval.delegated","source":"…",
 "occurredAt":"…","schemaVersion":1,
 "partitionKey":"dgr-019fd768-…","payload":{"grantId":"dgr-019fd768-…", …}}
```

⇒ approval 봉투에는 **최상위 `aggregateId` 와 `tenantId` 가 아예 없다.** masterdata 는 둘 다
싣는다. 두 프로듀서가 같은 아웃박스 라이브러리를 쓰면서 봉투가 갈렸다.

소비자 쪽은 둘 다 그 필드를 **필수로** 요구한다:

- `DelegationEventEnvelope.isValid()` — `eventId` **AND `aggregateId`** AND `payload` AND `payload.grantId`
- `ApprovalEventEnvelope.isValid()` — `eventId` **AND `aggregateId`** AND `payload`

⇒ **`erp.approval.*` 여섯 토픽 전부 같은 운명**이다. 지금 관측된 것이 `delegated` 하나뿐인
이유는 나머지 다섯을 낳는 상신·승인·반려가 **`TASK-ERP-BE-041` 로 막혀 있어** 이벤트가
아직 하나도 없기 때문이다(실측: `erp.approval.submitted.v1` end-offset **0**). BE-041 이
닫히면 **그 순간 다섯 토픽이 전부 DLT 로 간다.**

> ✅ **2026-08-07 — 이 예측의 절반이 실측으로 승격됐다.** `TASK-ERP-BE-041` 이 닫히고
> 상신 2건이 실제로 발행되자:
>
> ```
> erp.approval.submitted.v1            end-offset 0 → 2
> erp.approval.submitted.v1.DLT        end-offset 4     (소비자 2개 × 2건)
> erp.approval.submitted.v1-retry-0/1  end-offset 0     (재시도 없이 직행)
> ```
>
> 즉 이제 **두 토픽(`delegated`·`submitted`)이 실측 대상**이다. AC-0 은 이 둘을 착수 시
> 다시 세는 것으로 충족하고, 나머지 넷은 AC-3 의 회귀 범위로 남긴다.
>
> 🔴 **정정 (같은 날, 조금 뒤).** 위 문단은 처음에 *"나머지 넷이 0 인 것은 막혀서가 아니라
> 아무도 그 전이를 안 눌러서"* 라고 적었다. **확인하지 않고 쓴 문장이었고, 틀렸다.**
> 넷은 한 덩어리가 아니다 — 실측한 신원으로 갈린다:
>
> | 토픽 | 도달 가능? | 근거(실측) |
> |---|---|---|
> | `approved` · `rejected` | ❌ **구조적으로 불가** | 요청의 `approverId` = `019fd768-bc95-…`(EMP-0001), 행위 주체는 항상 `platform-console-web`. 유일한 위임은 `delegatorId=platform-console-web` → `delegateId=019fd768-c180-…` 로 **방향이 반대**라 승인 권한을 주지 않는다 ⇒ `TASK-MONO-515` 그 자체 |
> | `withdrawn` | ⭕ **가능** | withdraw 는 **기안자 전용**이고 `submitterId` = `platform-console-web` = 행위 주체다(발행된 봉투 payload 에서 확인) |
> | `delegation.revoked` | ⭕ **가능해 보인다** | 그 grant 의 `createdBy` = `platform-console-web` |
>
> 🔵 오른쪽 두 칸은 **코드·데이터 읽기로 얻은 도달성 분석이지 아직 쏴 본 적이 없다.**
> 지금 쏘지 않은 이유는 데모 데이터를 파괴하기 때문이다(`SUBMITTED 2` 가 `1` 로 줄고
> 시드는 비-DRAFT 를 복구하지 않는다). AC-3 이 봉투 수정과 **같은 실행에서** 태워라 —
> 그때는 되살아나는 것을 보는 것이므로 파괴가 아니라 검증이다.

## 🔴 두 번째 관문이 그 뒤에 있다 (봉투를 고쳐도 남는다)

`DelegationEnvelopeToCommandMapper` 는 파싱 후 `envelope.hasTenant(requiredTenant)` 를 건다.
`requiredTenant` 기본값은 **`erp`** 이고, `hasTenant` 는 최상위 `tenantId` → 없으면
`payload.tenantId` 순으로 읽는다. 데모의 위임 페이로드는 `"tenantId":"demo-corp"` 다.

🔵 **이 관문은 아직 도달된 적이 없다**(파싱이 먼저 죽는다) — 관측이 아니라 **코드 읽기로
얻은 예측**이다. AC-0 이 이것을 **실측으로 승격**할 것을 요구한다.

그리고 이 관문은 **위임 매퍼에만 있다** — 전수 확인: `hasTenant|requiredTenant` 를 가진
파일은 `read-model-service` 소비자 15개 중 `DelegationEnvelopeToCommandMapper` ·
`DelegationEventEnvelope` **둘뿐**이다. masterdata 소비자에는 없고, 그래서 `tenantId` 가
`demo-corp` 인 마스터 이벤트가 정상 투영됐다(실측: `employee_proj` 4행). **같은 read-model
안에서 테넌트 정책이 갈려 있다** — 어느 쪽이 의도인지가 이 티켓의 두 번째 질문이다.

## ✅ 2026-08-07 착수 전 조사 — **AC-1 은 이미 답이 있다** (BE-041 작업 중 실측)

이 절은 `TASK-ERP-BE-041` 을 하며 얻은 것이다. 아래 셋은 이 티켓 본문의 **전제를 정정**한다.

### ① 계약은 침묵하지 않는다 — 고칠 쪽은 **프로듀서**다

AC-1 이 "계약 문서를 먼저 열어라, 한쪽을 정했다면 그것이 답" 이라고 했다. 열었다:

```json
// specs/contracts/events/erp-approval-events.md § Envelope
{ "eventId":"<uuid>", "eventType":"erp.approval.submitted", "occurredAt":"…",
  "tenantId":"erp", "source":"erp-platform-approval-service",
  "aggregateType":"ApprovalRequest", "aggregateId":"<approvalRequestId>",
  "traceId":"…", "payload":{…} }
```

> `aggregateType` is `"ApprovalRequest"` on **every** approval event.

⇒ 계약이 **`tenantId`·`aggregateType`·`aggregateId` 셋 다 최상위로 선언**한다.
프로듀서가 계약 미이행이다. **AC-1 은 판단이 아니라 확인으로 닫힌다.**

🔴 **Related Contracts 의 파일명이 틀렸다** — 실제 경로는 `erp-` 접두사가 붙는다
(`erp-approval-events.md` / `erp-masterdata-events.md`). 아래 섹션에서 고쳤다.

### ② 🔴 **형제가 이미 같은 결함을 고쳤다 — `TASK-ERP-BE-032`**

`OutboxMasterdataEventPublisher` 의 javadoc 이 그대로 적어 뒀다:

> The legacy `BaseEventPublisher` path emitted a 7-field shape **without a top-level
> `aggregateId`**; the read-model-service consumer requires a top-level `aggregateId`
> and **rejected every real event to `.DLT`**. (TASK-ERP-BE-032)

**증상·원인·수정이 이 티켓과 같다.** approval 쌍둥이만 안 따라왔다 — `@EnableScheduling`
(BE-042)에 이은 **형제 파리티 낙오 두 번째**다. 구현은 masterdata 의 `writeEvent` 를 그대로
미러링하면 된다(3줄):

```java
envelope.put("tenantId", payload.get("tenantId"));   // 페이로드의 테넌트
envelope.put("aggregateType", aggregateType);        // "ApprovalRequest"
envelope.put("aggregateId", aggregateId);
```

approval 페이로드는 **이미 `tenantId` 를 싣고 있다**(실측: `"tenantId":"demo-corp"`).

### ③ 🔴🔴 AC-3 의 "비대칭" 은 **한 프로퍼티가 두 축을 겸하는 것**이다

`erpplatform.oauth2.required-tenant-id`(기본 `erp`)를 **세 곳이 서로 다른 뜻으로** 읽는다:

| 읽는 곳 | 해석 | `demo-corp` 결과 |
|---|---|---|
| `ServiceLevelOAuth2Config` (HTTP JWT) | **도메인** 키 (`trustEntitledDomains`) | **통과** |
| `ReadAuthorizationGate` | **도메인** 키 (entitlement) | **통과** |
| `DelegationEnvelopeToCommandMapper` | **테넌트 값** (`tenantId` 와 등호 비교) | **거절** |

⇒ 같은 값 `erp` 가 HTTP 쪽에선 *"이 도메인에 권한 있으면 통과"*, 이벤트 쪽에선
*"tenantId 가 문자열 `erp` 여야 함"* 이다. **실측으로 확인**: `tenant_id=demo-corp` 토큰이
read-model HTTP API 를 정상 통과하고(시드의 `프로젝션 사원 4/4`), 같은 서비스의 위임 매퍼는
`tenantId=demo-corp` 봉투를 거절한다.

🔴 그래서 **설정값으로는 못 고친다** — `OIDC_REQUIRED_TENANT_ID=demo-corp` 로 바꾸면 이벤트
관문은 열리지만 **HTTP 인증이 도메인 전체에서 깨진다.** AC-3 은 두 축을 **분리**하는
결정이어야 한다.

### ④ 🔵 계약의 `"tenantId": "erp"` 리터럴도 현실과 다르다

두 계약 문서 모두 봉투 예시에 `"tenantId": "erp"` 를 **리터럴**로 적어 뒀다(다른 필드는
`"<uuid>"` 식 플레이스홀더인데 이것만 값이다). 그런데 **두 프로듀서 모두 고객 테넌트를
싣는다** — 실측(같은 스택·같은 순간):

```
masterdata 봉투 : "tenantId":"demo-corp"   ← 최상위
approval  봉투 : (최상위 없음) payload."tenantId":"demo-corp"
```

erp 가 assume-tenant 로 멀티테넌트가 되면서 **구현이 앞서가고 계약이 안 따라온 것**이다.
AC-3 을 닫으려면 이 리터럴도 함께 정정해야 한다 — 안 그러면 다음 사람이 또 `erp` 를 상수로
읽고 같은 관문을 만든다.

> 🔴 **정정 (같은 날, 구현 중).** 위 ④ 는 리터럴 `"tenantId": "erp"` 를 **"스테일"** 이라고
> 단정했다. **읽지 않고 쓴 판정이었고, 거꾸로였다.** `PROJECT.md` 와
> `read-model-service/architecture.md` § Multi-tenancy 를 열어 보면 erp 는 **단일 테넌트로
> 선언**돼 있고("All projected rows belong to the `erp` tenant"), 리터럴은 그 모델의 표현이다.
> 스테일한 쪽은 **프로듀서**다 — 데모가 assume-tenant 로 erp 를 사실상 다중 테넌트로 만들자
> 두 프로듀서가 고객 테넌트를 싣기 시작했다. 어느 쪽이 옳은지는 **결정 사항**이고,
> 아래 HARDSTOP-09 로 정지했다.

---

## 🛑 2026-08-07 구현 기록 — 봉투는 고쳤고, **AC-3/AC-5 는 HARDSTOP-09 로 정지**

### 닫힌 것

- **AC-0 ✅** — 착수 시 재측정: `delegated` 1/DLT 2, `submitted` 2/DLT 4,
  `delegation_fact_proj` **0행**, 대조군 `employee_proj` 4행.
- **AC-1 ✅** — 계약이 이미 정했다(위 조사 절). 프로듀서를 고쳤다.
- **AC-2 ✅** — `writeEvent` 한 곳이 여섯 이미터 전부의 봉투를 만들므로 **여섯 토픽이 동시**에
  고쳐진다. 가드가 여섯을 **표로 열거**해 일곱 번째 이미터가 생기면 RED 다.
- **AC-4 🟡 절반** — 프로듀서 측 가드 + **물기 실측**(`aggregateId` 한 줄 제거 → RED).
  🔴 **AC-4 가 요구한 "프로듀서의 실제 산출물을 소비자 매퍼에 먹여라" 는 못 했다** — 두
  서비스가 **테스트 클래스패스를 공유하지 않고**, 소비자가 프로듀서를 test-depend 하게
  만드는 것은 이벤트 디커플링을 뒤집는다. 크로스서비스 하네스가 따로 필요하다
  (scm/fan 에 `E2E … cross-service smoke` 선례 있음, erp 엔 없음).

### 🔴 AC-0 의 두 번째 관문 — **예측이 관측으로 승격됐다**

봉투만 고친 상태에서 실제 이벤트를 흘려보낸 결과(라이브, 같은 스택):

```
수정 전 : Invalid delegation envelope (missing eventId/aggregateId/payload/grantId)
수정 후 : Non-erp tenant 'demo-corp' on topic erp.approval.delegated.v1
          (DelegationEnvelopeToCommandMapper.java:48)
```

파싱은 통과하고 **테넌트 관문**에서 막힌다. 두 위임 토픽이 **동일하게** 막힌다:

```
erp.approval.delegated.v1          : 2   DLT 4
erp.approval.delegation.revoked.v1 : 1   DLT 2     ← 이 티켓이 처음 흘려보냄
erp.approval.submitted.v1          : 2   DLT 4
retry-0/1 은 전부 0 (재시도 없이 직행)
```

🔵 프로브는 **가산적으로만** 했다 — 위임 grant 를 새로 하나 만들어 관측하고 곧바로 revoke
했다(두 토픽을 한 번에 태우면서 정리까지). 시드 데이터는 그대로다: 시드 grant `ACTIVE`,
결재 `DRAFT 1 · SUBMITTED 2`.

### 🛑 정지 — AC-3 은 결함 정리가 아니라 **아키텍처 결정**이었다

이 티켓은 AC-3 을 *"위임 매퍼만 테넌트를 강제하는 비대칭을 정리한다"* 로 적었다. **틀린
전제였다.** 그 관문은 세 문서가 선언한 모델을 **유일하게 집행하는 코드**다:

- `PROJECT.md` § Out of Scope — erp 는 `multi-tenant` 를 **선언하지 않는다**
- `read-model-service/architecture.md` § Multi-tenancy — "**All projected rows belong to
  the `erp` tenant**"
- 같은 문서 — "invalid envelope (…, **non-`erp` tenant**) → immediate DLT" ← 관문의 명세
- 두 이벤트 계약의 봉투 예시 — `"tenantId": "erp"` 리터럴

실행 중인 시스템은 이미 그 모델 밖에 있다(두 프로듀서 모두 `demo-corp` 를 싣는다). masterdata
가 조용히 통과한 건 **관문이 없어서**이고, 프로젝션 스키마도 갈려 있다 —
`delegation_fact_proj` 엔 `tenant_id` 가 **있고** `employee_proj` 엔 **없다**.

🔴 그리고 **설정으로는 못 고친다**: `erpplatform.oauth2.required-tenant-id` 를 HTTP 두 곳은
**도메인 키**로(→ `demo-corp` 통과), 위임 매퍼는 **테넌트 값**으로(→ 거절) 읽는다. 값 하나로
양쪽을 동시에 만족시킬 수 없다.

```
[VIOLATION] HARDSTOP-09: Task `TASK-ERP-BE-043` requires an architecture decision (event
taxonomy / cross-service contract — erp 이벤트 평면의 테넌트 축) that is not documented in
`projects/erp-platform/specs/services/read-model-service/architecture.md` or any ADR. 해당
문서는 정반대(단일 테넌트)를 선언하고 있고, 실행 중인 시스템은 그것을 이미 벗어나 있다.
[WHY] Architecture decisions made implicitly during implementation produce code that later
cannot be defended against "why was this chosen" review questions — and shape every
downstream task that builds on the same service. 여기서 A(단일 테넌트 고수)는 관측된 다중
테넌트 사실을 버리는 결정이고, B(다중 테넌트 승격)는 PROJECT.md 의 traits 를 바꿔 룰 레이어
로딩까지 바꾸며, C(데모를 erp 테넌트로 회귀)는 단일 계정 데모 이니셔티브의 전제를 바꾼다.
[REMEDIATION] Choose one:
  2. 결정이 중대하므로(크로스서비스 + 다른 서비스의 형태를 좌우) ADR 에 기록하고 ACCEPTED
     까지 PAUSE — `projects/erp-platform/docs/adr/ADR-001-erp-event-plane-tenant-axis.md`
     를 이 PR 에서 **Proposed** 로 제출했다. A/B/C 와 각각의 대가가 그 안에 있다.
[REFERENCE] CLAUDE.md § Layer Rules + platform/architecture-decision-rule.md
```

**해제 조건**: `ADR-ERP-001 ACCEPTED` + **A/B/C/D 중 선택**. 그때 AC-3/AC-5/AC-6 을 잇는다.

> ✅ **2026-08-12 해제됨** — 소유자 정확형 `ADR-ERP-001 ACCEPTED — D` 도착. 아래
> § HARDSTOP-09 해제 참조. 위 VIOLATION 블록은 **기록으로 남긴다**(왜 멈췄는지가 지워지면
> 다음 사람이 같은 판단을 처음부터 다시 한다).

---

## 🛑 2026-08-12 — 정지 유지. 다만 **ADR 의 전제가 틀렸던 것을 고쳤다**

`TASK-MONO-519` 의 라이브 검증(iam + erp 동시 기동)에서 이 티켓의 관문에 대한 새 실측이
나왔다. 결정은 여전히 소유자 몫이라 **이 티켓은 `ready/` 에 그대로 두고**, 잘못된 전제 위에서
결정이 내려지지 않도록 ADR 쪽만 갱신했다(doc-only, Status 는 `Proposed` 유지).

세 가지가 이 티켓 본문과 다르다:

1. 🔴 **"데이터가 다중 테넌트" 가 아니다.** `tenant_id` 컬럼을 가진 erp 테이블 전수
   `GROUP BY tenant_id`: 존재하는 값은 **`demo-corp` 하나**, 문자열 `erp` 는 **0행**.
   불일치는 *단일 vs 다중* 이 아니라 **그 하나를 뭐라고 부르는가**다.
2. 🔴 **관문은 두 곳이고 하나는 다른 서비스다.** 이 티켓의 전수 확인은 read-model 안에서만
   셌다. `notification-service` 의 `EnvelopeToCommandMapper` L67 이 같은 프로퍼티를 같은
   의미로 읽고 **`erp.approval.*` 전 타입**을 막는다 ⇒ 막히는 화면이 `/erp/delegation`
   하나가 아니라 **ERP 알림함까지 둘**이다(실측 `200 / totalElements 0`, DB 0행).
   🔵 이 사실은 `MONO-519` 가 두 번째 콘솔 신원을 심어 결재함을 채운 **뒤에야** 관측
   가능해졌다 — 그전에는 알림을 받을 신원 자체가 없어 "0" 의 원인이 갈리지 않았다.
3. 🔴🔴 **관문은 이미 불변식을 못 지키고 있다.** 같은 DB·같은 런:
   `approval_fact_proj` **2** · `department_proj` **4** · `employee_proj` **4** ·
   `cost_center_proj` **3** · `job_grade_proj` **3** 이 전부 `demo-corp` 데이터로 들어가 있고
   (**16행**), 관문이 있는 소비자는 read-model 6개 중 **1개**(`delegation_fact_proj` **0행**)다.
   ⇒ 관문을 남기는 것은 "불변식을 지킨다" 가 아니라 **"소비자 하나를 굶긴다"** 이다.

⇒ 그래서 ADR 에 **Option D**(관문을 값-등호에서 존재검사로 낮추고 봉투는 사실을 싣는다 +
"distinct tenant_id ≥ 2 이면 RED" 래칫)를 추가했다. 구현자 추천은 D 이지만 **추천은 결정이
아니다** — `deciders` 의 정확형 intent 없이는 AC-3/AC-5 를 재개하지 않는다.

---

## ✅ 2026-08-12 — HARDSTOP-09 해제. `ADR-ERP-001 ACCEPTED — **D**`

소유자 정확형이 도착했다(`ADR-ERP-001 ACCEPTED — D`). **self-ACCEPT 아님** — 직전
메시지의 *"추천 D로 결정"* 은 넘기지 않고 멈춰서 정확형을 다시 요청했다. 근거와 게이트
기록은 ADR § 결정 — **D**.

**이제 구속력을 갖는 것**(ADR 표의 요약이며, 충돌 시 ADR 이 정경):

1. 이벤트 평면의 관문 **두 곳**이 `erpplatform.oauth2.required-tenant-id` 를 **안 읽는다**
   — `read-model`/`DelegationEnvelopeToCommandMapper` + `notification`/`EnvelopeToCommandMapper`.
   🔴 **한 곳만 고치면 알림함은 그대로 0 이다.** 두 번째 사본은 *다른 서비스*에 있다.
2. 봉투 `tenantId` 는 **사실(`demo-corp`)** 을 싣는다. `erp` 상수로 쓰지 않는다.
3. `PROJECT.md` traits 는 **안 바꾼다**(B 배제) · 데모를 `erp` 로 되돌리지 **않는다**(C 배제) ·
   프로젝션 스키마 마이그레이션과 읽기 테넌트 필터는 **범위 밖**.
4. 두 계약 문서의 `"tenantId": "erp"` **리터럴 → 플레이스홀더**.
5. 래칫: **distinct `tenant_id` ≥ 2 이면 RED**.

🔵 **AC-3 의 열린 질문은 이것으로 답이 났다.** AC-3 본문은 *"'그냥 지운다' 도 '전 소비자로
확대한다' 도 결정이다"* 라고 적었는데, ADR 이 **전자**(값-등호를 걷어내고 존재검사로 낮춘다)를
골랐다. AC-3 은 이제 그 결정을 *집행*하고 수치를 보이는 일이지, 고르는 일이 아니다.

🔴 **ACCEPT 는 계약의 *내용* 을 승인하지 않았다.** 봉투 필드 형태·플레이스홀더 표기는 이
티켓이 제안하고 리뷰가 검사한다. 계약 갱신이 코드보다 **먼저**다(CLAUDE.md § Layer Rules).

### 새 AC 셋 — ADR 이 구속력을 준 항목 중 기존 AC 가 안 덮던 것

- **AC-7 (래칫이 실제로 도는가)** — ADR 이 명명한 래칫("distinct `tenant_id` ≥ 2 → RED")을
  **도는 레인에 배선**하고 **무는지** 확인한다(테넌트 하나를 심어 RED, 지워서 GREEN).
  🔴 ADR 은 술어만 정했고 **어디서 도는지는 정하지 않았다** — 그건 구현 몫이다. 이 저장소는
  술어만 있고 레인이 없는 가드를 두 번 만들었고(`TASK-MONO-518`·`524`) 둘 다 **영원히 초록**
  이었다. 🔴 그리고 이 래칫은 *잃는 알람의 대체물*이다 — 안 돌면 교환의 한쪽만 실행된 것이다.
- **AC-8 (두 번째 관문 + 알림함)** — `notification-service` 의 사본까지 고치고, ERP **알림함이
  실제로 찬다**는 것을 보인다(`GET /api/erp/notifications` 의 `totalElements > 0` + DB 행).
  🔵 이것이 `TASK-MONO-519` AC-4 의 잔여이며, 워크스루 § 6 의 별도 🔴 행이 이 티켓을 tracking 한다.
- **AC-9 (계약 리터럴)** — 두 계약 문서의 `"tenantId": "erp"` 를 플레이스홀더로 정정한다.
  🔴 안 고치면 다음 사람이 또 상수로 읽고 같은 관문을 만든다(그것이 이 ADR 의 발생 경로다).

🔴 **AC-0 재개 시 물려받지 말 것**: 위 숫자는 2026-08-12 것이다. `TASK-MONO-524` 가 그 뒤에
iam dev 시드를 `R__` 로 옮겼으므로 **기존 볼륨의 데모 데이터는 그대로지만** 재기동 경로가
달라졌다. 그리고 데모 볼륨에는 승인자가 데모 신원이 아닌 **낡은 DRAFT 행 1건**이 남아 있다
(실측) — 라이브 판정 전에 볼륨 초기화 + 재시드를 권한다.

🔴 **AC-0 을 재개할 때 물려받지 말 것**: 위 숫자는 2026-08-12 것이고, 이 티켓 본문 위쪽의
end-offset 표는 **그 이전** 것이다. `approved` 토픽은 `MONO-519` 의 라이브 승인으로 이번에
처음 발생했으므로 지금 트래픽이 있는 토픽은 셋이 아니라 **넷**이다.

---

## ✅ 2026-08-12 (UTC) DONE — 3차원 검증

impl PR [#3289](https://github.com/kanggle/monorepo-lab/pull/3289) squash `396fc982c`.
(a) `state=MERGED` · `mergedAt=2026-08-12T16:54:14Z` · `mergeCommit=396fc982c96874987476418b0a338e0bf6f9482d`
(b) `origin/main` tip = `396fc982c` (일치)  (c) 머지 전 스냅샷 **failing 0 · pending 0**
(11 pass / 29 skipping — `Integration (erp-platform, Testcontainers)` **실행 확인** 4m1s).

🔴 **첫 푸시에서 CI 가 RED 였고, 잡은 것은 내 래칫이었다.** `SingleTenantRatchetIntegrationTest` 가 `Expected size: 1 but was: 2` 로 떨어졌는데, 원인은 같은 PR 이 새로 넣은 `anotherTenantIsProjectedAndItsValueIsRecorded` 가 `other-corp` 행을 **공유 스키마에 남긴 것**이었다 — 전역 불변식 단언에게는 그것이 *"erp 가 다중 테넌트가 됐다"* 와 구별되지 않는다. 🔴 **로컬이 초록이었던 이유는 그 둘을 한 번도 같이 돌리지 않았기 때문**이다(래칫만 `--tests`, 위임 IT 만 `--tests`). 조각을 각각 확인한 것은 전체를 확인한 것이 아니다. 고친 방향은 **래칫 완화가 아니라 오염원이 `finally` 로 자기 행을 지우는 것**이고, 래칫 실패 메시지에 *"CI 에서만 울면 형제 테스트의 잔여 행부터 의심하라"* 를 적었다(안 적으면 다음 사람이 래칫을 완화한다 — 그러면 D 가 맞바꾼 탐지까지 잃는다). 수정 후 두 스위트를 **함께** 돌려 read-model 28/0 · notification 12/0.

🔵 남은 것(이 티켓 밖): **`TASK-MONO-526`** — erp Kafka 볼륨이 Kafka 가 쓰지 않는 경로에 마운트돼 있다(7개 중 5개 동일). 그리고 기존 데모 볼륨에서는 수정 이전 이벤트가 소급 투영되지 않으므로(원본 `delegation_grant` 4 vs 투영 1) 완전한 데모는 볼륨 초기화 + 재시드가 필요하다 — 그 필요성은 `TASK-MONO-526` 이 없애야 한다.

---

## ✅ 2026-08-12 (UTC) 구현 완료 — AC-0~AC-9 전부 닫힘

전체 서술은 [`docs/erp-tenant-axis.md`](../../docs/erp-tenant-axis.md). 여기엔 **판정에 쓴
숫자와, 티켓/ADR 이 몰랐던 것**만 적는다.

### 🔴 이 티켓도 ADR 도 명명하지 않은 것 셋 (구현 중 실측으로 발견)

1. **관문은 둘이 아니라 셋이었다 — 세 번째는 *읽기* 축이다.**
   `NotificationInboxController` 가 질의 테넌트로 `erpplatform.oauth2.required-tenant-id`
   를 **상수로** 넘기고 있었다. 이벤트 관문만 고쳤다면 `notification` 테이블은 차는데
   **알림함은 여전히 `totalElements 0`** 이다 — 쓰기는 봉투의 `demo-corp` 로, 읽기는
   상수 `erp` 로 하기 때문이다. AC-8("알림함이 실제로 찬다")은 이걸 고쳐야만 닫힌다.
   ⇒ 질의 테넌트를 **호출자 자신의 검증된 `tenant_id` claim** 으로 바꿨다. 이것은 ADR 이
   범위 밖으로 뺀 *"읽기 테넌트 필터 **신설**"* 이 아니다 — 필터는 이미 있었고 **출처**만
   상수에서 호출자로 바뀌었다.
2. **프로젝션에 사실을 안 실으면 D 가 자기 모순이 된다.**
   `delegation_fact_proj.tenant_id` 는 V3 이래 `DEFAULT 'erp'` 인데 JPA 엔티티에 **매핑조차
   안 돼 있었다.** 관문만 걷어내면 이 행은 `erp`, erp 나머지 전부는 `demo-corp` ⇒
   **distinct = 2** — D 가 세운 래칫이 *정상 시스템에서* 즉시 발화한다. 그래서 봉투의
   테넌트를 커맨드→도메인→엔티티까지 관통시켰다(범위 밖인 *스키마 마이그레이션*이 아니라
   **이미 있던 컬럼에 값을 쓰는 일**이다).
3. **계약 문서는 둘이 아니라 셋이다.** ADR 구속 표는 *"두 계약 문서"* 라고 적었지만
   `notification-subscriptions.md` 가 같은 리터럴을 같은 이유로 갖고 있었다(전수 16곳).
   셋 다 고쳤다 — 둘만 고치면 남은 하나가 다음 사람의 근거가 된다.

### AC 판정

| AC | 결과 | 근거(실측) |
|---|---|---|
| **AC-0** | ✅ | 재측정: `delegation_fact_proj` **0** · `notification` **0** · `notification_delivery` **0** · `approval_fact_proj` 2 · `employee_proj` 4. 🔴 **DLT end-offset 은 티켓의 4/4/2 가 아니라 전 토픽 0 이었다** — 물려받지 않고 다시 쟀고, 그 차이의 원인까지 규명했다(아래 § Kafka). |
| **AC-1** | ✅ | 계약이 이미 정했다(2026-08-07 조사). 프로듀서는 그때 고쳤다. |
| **AC-2** | ✅ | `writeEvent` 한 곳이 여섯 이미터 전부의 봉투를 만든다 + 여섯 열거 가드. |
| **AC-3** | ✅ | 관문 **2곳**의 값-등호 제거(부재만 invalid). 수치: 위 ①②. |
| **AC-4** | ✅ | 프로듀서 와이어 미러 가드 **2개 신설**(read-model `DelegationEnvelopeProducerContractTest` 4건 · notification `ApprovalEnvelopeProducerContractTest` 3건). **물기 실측**: 값-등호를 되돌리면 두 서비스 합쳐 **16건 RED**. |
| **AC-5** | ✅ | 라이브(가산 프로브): `delegation_fact_proj` **0 → 1**, `tenant_id=demo-corp`, read-model 위임 목록 `totalElements 1`(HTML 아님 — API 원소 수), **`.DLT` 전 토픽 여전히 0**. |
| **AC-6** | ✅ | **재처리하지 않고 폐기** + 근거 3개 + 수동 재드라이브 절차 → `docs/erp-tenant-axis.md` § 6. |
| **AC-7** | ✅ | 래칫 **두 절반**. CI: `SingleTenantRatchetIntegrationTest` × 2 (`erp-integration-tests` 레인, Testcontainers 실 MySQL). 라이브: `scripts/check-erp-single-tenant-ratchet.sh` (테이블을 손으로 안 적고 `information_schema` 로 발견 — **16개** 찾았다). **물기 실측 3종**: (A) 저장소가 상수를 쓰게 → read-model IT RED, (B) 다른 테넌트를 실제로 흘려보냄 → notification IT `size 1 but was 2`, (C) 라이브 행 하나를 `other-corp` 로 → 스크립트 rc=1, 되돌리니 rc=0. |
| **AC-8** | ✅ | 라이브: `notification` **0 → 2**, `notification_delivery` **0 → 2**(둘 다 `demo-corp`), `GET /api/erp/notifications` → **`totalElements 1`** (APPROVAL_SUBMITTED, 승인자 sub 수신). 🔵 테이블 2 vs 알림함 1 은 정상 — 두 번째는 DELEGATION_GRANTED 이고 수신자가 **사원 마스터 id**(JWT sub 아님)라 아무의 받은편지함에도 안 뜬다. `TASK-MONO-519` AC-4 잔여 닫힘. |
| **AC-9** | ✅ | 계약 **3문서 16곳** 리터럴 → `"<tenantId>"` + § Consumer tenant handling 신설. |

### 🔴 AC-0 이 물려받길 거부해서 드러난 것 — erp Kafka 는 **볼륨을 안 쓴다**

티켓이 적은 DLT 4/4/2 를 다시 재니 **`erp.approval.*` 전 토픽·전 DLT 가 0** 이었다. 원인:

```
compose: kafka-data:/var/lib/kafka/data      ← 마운트된 곳 (비어 있음, 실측)
실제   : log.dirs = /tmp/kafka-logs          ← 세그먼트가 있는 곳 (컨테이너 /tmp)
```

`apache/kafka:3.7.0` 의 기본 로그 디렉터리는 컨테이너 `/tmp` 이고 compose 는 `KAFKA_LOG_DIRS`
를 **설정하지 않는다** ⇒ 선언된 볼륨은 아무것도 담지 않고, **재기동마다 토픽·오프셋·DLT 가
전부 사라진다.** 전수: `apache/kafka:3.7.0` 을 쓰는 7개 프로젝트 중 **5개가 같은 모양**
(erp·fan·iam·scm·wms — 볼륨 선언은 있는데 안 쓰임), 2개(ecommerce·finance)는 볼륨 자체가
없어 정직하게 휘발성이다. **`KAFKA_LOG_DIRS` 를 설정한 프로젝트는 0개.**

🔵 **이 티켓의 범위가 아니다**(테넌트 축이 아니라 compose 내구성) → **`TASK-MONO-526`** 으로
분리 등록. 다만 두 가지를 여기서 기록해야 한다: (a) AC-6 의 DLT 백로그는 **이미 존재하지
않는다** — 폐기 결정이 실행 이전에 사실로 확정됐다. (b) `.DLT` end-offset 0 을 "우리가 고쳐서
0" 으로 읽으면 **틀린다** — 이번 런에서 새로 흘려보낸 이벤트가 DLT 로 안 갔다는 것이 판정이고,
그 판정은 `delegated`/`submitted` 본 토픽이 **각각 1** 로 올라간 것과 함께 봐야 성립한다.

### 라이브 프로브가 남긴 것 (가산만 — 지운 것 없음)

| | 프로브 전 | 후 |
|---|---|---|
| `delegation_grant`(원본) / `delegation_fact_proj` | 3 / **0** | 4 / **1** |
| `approval_request`(원본) / `approval_fact_proj` | 3 / 2 | 4 / 3 |
| `notification` / `notification_delivery` | 0 / 0 | **2 / 2** |

🔴 **원본 4 vs 투영 1 의 차이는 결함이 아니라 AC-6 의 귀결이다** — 시드 위임 2건의 이벤트는
수정 **이전에** 발행됐고 위 § Kafka 때문에 브로커에서도 사라졌다. 소급 투영은 일어나지 않는다.
데모를 완전하게 만들려면 볼륨 초기화 + 재시드(= 사장님 몫인 `down -v`)이며, 그 필요성은
이 티켓이 아니라 `TASK-MONO-526` 이 없애야 한다.

### 테스트

`erp` 5개 모듈 `check` **GREEN — unit 665건 / 실패 0**(masterdata 135 · read-model 176 ·
approval 194 · notification 134 · gateway 26). `integrationTest`: read-model **28** ·
notification **12**, 실패 0. 🔴 **IT 픽스처의 테넌트를 `erp` → `demo-corp` 로 바꿨다** —
현실보다 관대한 픽스처는 초록이어도 아무것도 증명하지 않는다(그 픽스처 때문에 이 결함이
지나갔다). 알림 IT 토큰도 **실제 콘솔 운영자 형태**(`tenant_id=demo-corp` +
`entitled_domains=[erp]`)로 바꿔 쓰기 축과 읽기 축을 같은 값으로 맞췄다.

🔴 **되돌린 네거티브 2건은 지우지 않고 뒤집었다** — `nonErpTenantIsInvalid` ·
`nonErpTenantDelegationIsInvalid` · IT 의 `nonErpTenantEventRoutesToDltAndIsNotProjected` 는
*틀린 불변식*을 지키고 있었으므로, "다른 테넌트는 받아들이고 값을 기록한다" + "테넌트를
**아예 안 적은** 봉투는 여전히 DLT" 로 교체했다. 살아남는 조건이 없으면 "비교를 걷어냈다" 가
"검증을 전부 없앴다" 와 구별되지 않는다.

---

# Goal

`erp.approval.*` 이벤트가 read-model 에 **투영된다.** 그리고 봉투 계약이 두 프로듀서
사이에서 **한 가지**가 된다.

# Scope

## In Scope

- approval-service 의 아웃박스 봉투(또는 read-model 의 요구 필드) — **어느 쪽을 고칠지 결정**
- 위임 매퍼의 테넌트 관문 정책
- 계약 문서 `approval-events.md` 갱신
- 크로스서비스 회귀 가드

## Out of Scope

- 상신이 막혀 이벤트가 안 생기는 문제 → **`TASK-ERP-BE-041`**
- 릴레이 미기동 → **`TASK-ERP-BE-042`** (이 티켓의 선행, 이미 해소)

# Acceptance Criteria

- [x] **AC-0 (재현 + 두 번째 관문 실측)** — DLT end-offset 과 `delegation_fact_proj` 0행을
      다시 잰다. 🔴 그리고 **봉투만 고친 상태에서 한 번 더 흘려보내** 테넌트 관문이 실제로
      거절하는지 확인한다 — 위 예측을 **관측으로 승격**하기 전에는 그것을 근거로 쓰지 말 것
- [x] **AC-1 (계약 방향 결정)** — 프로듀서를 맞출지 소비자를 맞출지 고르고 근거를 적는다.
      🔵 **선례가 이미 있다**: masterdata 프로듀서가 `tenantId`/`aggregateType`/`aggregateId`
      를 싣고 read-model 이 그것을 읽는다 ⇒ 저장소 안의 **다수 형태**는 "봉투가 싣는다" 다.
      계약 문서(`specs/contracts/events/approval-events.md`)가 무엇을 선언하는지 **먼저
      열어라** — 문서가 이미 한쪽을 정했다면 그것이 답이고, 침묵이면 다수 형태를 따른다
- [x] **AC-2 (전 토픽)** — `erp.approval.*` **여섯 토픽 전부**를 고친다. `delegated` 하나만
      고치면 BE-041 이 닫히는 순간 나머지 다섯이 같은 자리에서 깨진다
- [x] **AC-3 (테넌트 관문)** — 위임 매퍼만 테넌트를 강제하는 **비대칭**을 정리한다.
      🔴 "그냥 지운다" 도 "전 소비자로 확대한다" 도 **결정**이다 — 어느 쪽이든 근거를 적고,
      데모(`demo-corp`)가 어떻게 되는지 수치로 보인다
- [x] **AC-4 (회귀 가드)** — 프로듀서가 낸 **실제 봉투**로 소비자 매퍼가 성공하는지 단언한다.
      🔴 손으로 쓴 JSON 픽스처로 하지 마라 — 그것이 바로 이 결함이 통과한 방식이다
      (양쪽이 각자 자기 픽스처로 초록이었다). 프로듀서의 직렬화 산출물을 소비자에 먹여라.
      🔴 가드가 **무는지** 확인한다(필드를 되돌리면 RED)
- [x] **AC-5 (라이브)** — `demo-up.sh iam erp console` 후 `delegation_fact_proj` 1행,
      DLT end-offset 증가 없음. 콘솔 `/erp/delegation` 의 read-model 원소 수로 확인한다
      (HTML 아님 — 콘솔은 클라이언트 렌더)
- [x] **AC-6 (DLT 처리)** — 이미 DLT 에 쌓인 2건을 어떻게 할지 정한다. 재처리/폐기 어느
      쪽이든 **적는다** — 조용히 남겨 두지 말 것
- [x] **AC-7 (래칫이 실제로 도는가)** — `ADR-ERP-001` D 가 명명한 래칫(erp 전체에서 distinct
      `tenant_id` ≥ 2 이면 RED)을 **도는 레인에 배선**하고 **무는지** 확인한다(두 번째 테넌트
      값을 심어 RED, 지워서 GREEN). 🔴 ADR 은 술어만 정했고 레인은 정하지 않았다 — 술어만
      있고 도는 레인이 없는 가드는 영원히 초록이다(`MONO-518`·`524` 에서 두 번). 🔴 이 래칫은
      D 가 **걷어내는 거부의 대체물**이므로, 안 돌면 교환의 한쪽만 실행된 것이다
- [x] **AC-8 (두 번째 관문 + 알림함)** — `notification-service` 의 `EnvelopeToCommandMapper`
      사본까지 고치고 ERP **알림함이 실제로 찬다**는 것을 보인다(`GET /api/erp/notifications`
      의 `totalElements > 0` + DB 행). 🔴 read-model 한 곳만 고치면 위임 뷰는 차고 알림함은
      **그대로 0** 이다 — 두 화면은 원인이 같고 사본이 다르다. 🔵 `TASK-MONO-519` AC-4 의 잔여
- [x] **AC-9 (계약 리터럴)** — 두 계약 문서의 봉투 예시 `"tenantId": "erp"` 를 플레이스홀더로
      정정한다(다른 필드는 `"<uuid>"` 형태인데 이것만 값이다). 🔴 안 고치면 다음 사람이 또
      상수로 읽고 같은 관문을 만든다 — 그것이 이 ADR 이 존재하게 된 경로다

# Related Specs

- `projects/erp-platform/specs/services/read-model-service/architecture.md`
- `projects/erp-platform/specs/services/approval-service/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/events/erp-approval-events.md` — 봉투 스키마의 권위.
  **이미 `tenantId`/`aggregateType`/`aggregateId` 를 최상위로 선언한다** ⇒ AC-1 의 답
- `projects/erp-platform/specs/contracts/events/erp-masterdata-events.md` — 동작하는 선례
- `projects/erp-platform/tasks/done/TASK-ERP-BE-032-masterdata-event-envelope-dlt-mismatch.md`
  — **같은 결함의 masterdata 판**. 미러링할 수정

# Edge Cases

- `aggregateId` 를 `payload.grantId` 로 채울 때 **revoke** 이벤트의 페이로드에도 그 키가
  있는지 확인할 것(위임은 grant/revoke 두 이벤트가 같은 프로젝션 행을 다룬다)
- DLT 컨슈머가 이미 붙어 있으므로, 봉투를 고쳐도 **과거 메시지는 여전히 DLT** 에 있다
- `schemaVersion` 이 이미 1 로 실려 있다 — 봉투를 바꾸면 그 값을 올릴지도 결정 대상이다

# Failure Scenarios

- **`delegated` 만 고치고 닫음** → BE-041 이 닫히는 날 다섯 토픽이 동시에 DLT. AC-2 가 막는다
- **양쪽 픽스처로 각각 테스트** → 이 결함이 통과한 바로 그 방식. AC-4 가 금지한다
- **테넌트 관문을 못 보고 봉투만 수정** → 파싱은 되는데 여전히 투영이 안 된다. AC-0/AC-3

# Definition of Done

- [x] **AC-0~AC-9 충족** (DoD 는 원래 `AC-0~AC-6` 이라고 적혀 있었다 — ADR ACCEPT 로 AC-7/8/9
      가 신설됐는데 이 줄이 안 따라왔다. 범위를 넓힌 게 아니라 **누락을 고친 것**이다)
- [x] approval-service + read-model-service + **notification-service** 테스트 GREEN
      (erp 5모듈 `check` unit 665/0 · `integrationTest` read-model 28 · notification 12, 실패 0)
- [x] 라이브 검증 (AC-5 위임 뷰 · AC-8 알림함 · AC-7 라이브 래칫)
- [x] Ready for review
