# Task ID

TASK-ERP-BE-043

# Title

approval-service 의 이벤트 봉투가 read-model 이 **필수로 요구하는 두 필드를 안 싣는다** — 위임 사실 프로젝션이 전량 DLT 로 간다

# Status

ready

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
> 즉 이제 **두 토픽(`delegated`·`submitted`)이 실측 대상**이다. 나머지 넷
> (`approved`/`rejected`/`withdrawn`/`delegation.revoked`)이 여전히 0 인 것은
> **막혀서가 아니라 그 전이를 아직 아무도 실행하지 않아서**다 — 승인/반려를 한 번씩
> 태우면 같은 자리에서 관측된다. AC-0 은 두 토픽을 착수 시 다시 세는 것으로 충족하고,
> 나머지 넷은 AC-3 의 회귀 범위로 남긴다.

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

- [ ] **AC-0 (재현 + 두 번째 관문 실측)** — DLT end-offset 과 `delegation_fact_proj` 0행을
      다시 잰다. 🔴 그리고 **봉투만 고친 상태에서 한 번 더 흘려보내** 테넌트 관문이 실제로
      거절하는지 확인한다 — 위 예측을 **관측으로 승격**하기 전에는 그것을 근거로 쓰지 말 것
- [ ] **AC-1 (계약 방향 결정)** — 프로듀서를 맞출지 소비자를 맞출지 고르고 근거를 적는다.
      🔵 **선례가 이미 있다**: masterdata 프로듀서가 `tenantId`/`aggregateType`/`aggregateId`
      를 싣고 read-model 이 그것을 읽는다 ⇒ 저장소 안의 **다수 형태**는 "봉투가 싣는다" 다.
      계약 문서(`specs/contracts/events/approval-events.md`)가 무엇을 선언하는지 **먼저
      열어라** — 문서가 이미 한쪽을 정했다면 그것이 답이고, 침묵이면 다수 형태를 따른다
- [ ] **AC-2 (전 토픽)** — `erp.approval.*` **여섯 토픽 전부**를 고친다. `delegated` 하나만
      고치면 BE-041 이 닫히는 순간 나머지 다섯이 같은 자리에서 깨진다
- [ ] **AC-3 (테넌트 관문)** — 위임 매퍼만 테넌트를 강제하는 **비대칭**을 정리한다.
      🔴 "그냥 지운다" 도 "전 소비자로 확대한다" 도 **결정**이다 — 어느 쪽이든 근거를 적고,
      데모(`demo-corp`)가 어떻게 되는지 수치로 보인다
- [ ] **AC-4 (회귀 가드)** — 프로듀서가 낸 **실제 봉투**로 소비자 매퍼가 성공하는지 단언한다.
      🔴 손으로 쓴 JSON 픽스처로 하지 마라 — 그것이 바로 이 결함이 통과한 방식이다
      (양쪽이 각자 자기 픽스처로 초록이었다). 프로듀서의 직렬화 산출물을 소비자에 먹여라.
      🔴 가드가 **무는지** 확인한다(필드를 되돌리면 RED)
- [ ] **AC-5 (라이브)** — `demo-up.sh iam erp console` 후 `delegation_fact_proj` 1행,
      DLT end-offset 증가 없음. 콘솔 `/erp/delegation` 의 read-model 원소 수로 확인한다
      (HTML 아님 — 콘솔은 클라이언트 렌더)
- [ ] **AC-6 (DLT 처리)** — 이미 DLT 에 쌓인 2건을 어떻게 할지 정한다. 재처리/폐기 어느
      쪽이든 **적는다** — 조용히 남겨 두지 말 것

# Related Specs

- `projects/erp-platform/specs/services/read-model-service/architecture.md`
- `projects/erp-platform/specs/services/approval-service/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/events/approval-events.md` — 봉투 스키마의 권위
- `projects/erp-platform/specs/contracts/events/masterdata-events.md` — 동작하는 선례

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

- [ ] AC-0~AC-6 충족
- [ ] approval-service + read-model-service 테스트 GREEN
- [ ] Ready for review
