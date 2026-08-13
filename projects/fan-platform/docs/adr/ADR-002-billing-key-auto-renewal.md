# ADR-002: 빌링키 기반 자동 갱신(정기결제) — 서버 개시 청구 + BillingKeyEnrollment + fail-closed 실패 정책

**Status:** Accepted
**Date**: 2026-07-25 (proposed) · 2026-07-25 (accepted — owner exact-form intent **"ADR-002 ACCEPTED"**, given together with the companion **"ADR-MONO-057 ACCEPTED"**)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: [`ADR-001`](ADR-001-real-pg-portone-verification-boundary.md)(클라 개시-서버 검증 경계 — 본 ADR이 확장하는 신뢰 모델), [`ADR-MONO-057`](../../../../docs/adr/ADR-MONO-057-recurring-billing-key-capability.md)(`libs/payment`에 `RecurringBillingGateway` 추가 — 본 ADR의 전제 조건, 별도 ACCEPT), `specs/services/membership-service/architecture.md` § State Machine

---

## Context

지금 fan-platform의 구독/갱신은 **매달 팬이 직접 결제창을 여는 일반결제의 반복**이다 — `SubscribeUseCase`/`RenewMembershipUseCase` 모두 클라이언트가 PG 결제창을 열고 얻은 `paymentId`를 서버가 검증(ADR-001)한 뒤 멤버십을 생성한다. 진짜 정기결제(카드 등록 1회 → 매달 자동 청구)는 아니다.

실 정기결제를 넣으려면 신뢰 모델이 근본적으로 바뀐다: 오늘은 **클라이언트가 항상 먼저 움직이고**(결제창을 연다) 서버가 그 결과를 검증한다. 자동 갱신은 **서버가 스케줄에 따라 스스로 청구를 개시**한다 — 그 순간 팬은 관여하지 않는다. "클라이언트 신호를 믿지 않는다"는 ADR-001의 원칙은 그대로 남지만, 이번엔 믿지 말아야 할 신호가 "클라이언트가 보낸 paymentId"가 아니라 **"청구 API 호출의 응답(유실될 수 있음)"과 "웹훅 페이로드"**로 바뀐다.

[`ADR-MONO-057`](../../../../docs/adr/ADR-MONO-057-recurring-billing-key-capability.md)가 그 확장을 라이브러리 레벨에서 정의한다(`libs/payment-core`의 `RecurringBillingGateway` + `libs/payment-portone`의 PortOne 구현 + 웹훅 서명검증 유틸). 본 ADR은 그 위에서 fan-platform이 **무엇을 언제 청구하고, 실패하면 어떻게 하는지**를 정의한다 — 순수 프로젝트-내부 결정.

---

## Decision (Proposed)

### D1 — 별도 엔티티 `BillingKeyEnrollment`. `Membership`/`MembershipStatus`는 무변경

"이 멤버십이 어떻게 갱신되는가"(결제수단)는 "이 멤버십이 지금 활성인가"(접근권한)와 별개 축이다. `Membership.status`(ACTIVE/CANCELED, read-time expiry)를 건드리지 않고, 새 개념을 둔다:

```
BillingKeyEnrollment {
  accountId, tenantId, tier,
  billingKey (vendor-opaque, at-rest 암호화),
  active (bool — 팬이 자동갱신을 껐으면 false),
  createdAt
}
```

- 발급(등록) 시: 프런트 `PortOne.requestIssueBillingKey(...)` → `billingKey` → 서버가 저장(신뢰 모델 확장은 없음 — ADR-MONO-057 §6이 issuance 검증을 열린질문으로 남김; Phase 1은 발급 자체를 결제로 취급하지 않으므로 blast radius가 청구 시점 verify로 한정됨).
- 해지: `active=false`(soft) — 등록 자체는 남겨 재활성화 이력 추적 가능.
- 한 계정·티어에 활성 enrollment는 최대 1개(같은 티어 재등록은 갱신, 다른 티어는 사용자가 먼저 해지해야 함 — 동시 이중청구 방지).

### D2 — 스케줄러가 기존 `RenewMembershipUseCase`를 "서버가 만든 paymentId"로 그대로 재사용

새 갱신 로직을 만들지 않는다. 매일 배치(예: `@Scheduled` cron)가 `validTo`가 임박한(예: D-0) ACTIVE 멤버십 중 활성 `BillingKeyEnrollment`가 있는 것을 찾아:

1. 서버가 `paymentId` 채번(기존 클라 흐름과 동일한 모양 — `pay-<uuid>`).
2. `RecurringBillingGateway.chargeBillingKey(billingKey, paymentId, amountMinor, "KRW", orderName)` 호출.
3. **응답이 왔고 approved** → 기존 `RenewMembershipUseCase.execute(...)`를 그 `paymentId`로 호출(사람이 결제창에서 얻은 것과 동일한 모양의 입력이므로 유스케이스 코드는 **무변경**) — 8090% 코드 재사용, 새 money-safety 표면 최소화.
4. **응답이 왔고 declined** → §D4(실패 정책)로.
5. **응답이 유실/타임아웃** → 즉시 갱신 성공으로 취급하지 않는다. `PaymentGatewayPort.verify(paymentId, ...)`로 재조회(ADR-MONO-057 §1.3의 재조정 패턴, `TASK-BE-438` 선례와 동형) — PAID면 §3으로 진행, 아니면 §D4로.

이 설계의 핵심 이득: **`RenewMembershipUseCase`의 결제 검증·멱등성·이벤트 발행 로직을 한 글자도 안 바꾼다** — 그 유스케이스는 이미 "누가 `paymentId`를 줬는지" 모른다(클라이언트든 스케줄러든 같은 입력 모양). 코드 재사용률 체감상 대부분(유스케이스 본문 무변경, 새 코드는 호출부뿐).

### D3 — 웹훅은 스케줄러가 놓친 케이스의 **재조정 트리거**일 뿐, 진실의 원천 아님

`POST /webhooks/portone`(membership-service 신규 엔드포인트) — `libs/payment-portone`의 서명검증 유틸로 검증 실패 시 즉시 401, 페이로드의 금액/상태는 **절대 그대로 반영하지 않는다**. 검증 통과 시 페이로드의 `paymentId`로 `verify()`를 한 번 더 호출해 진실을 재확인한 뒤에만 상태를 반영한다 — 웹훅은 "무언가 있었다"는 신호일 뿐, "그게 뭐였다"는 답은 항상 `verify()`가 낸다.

웹훅 **멱등성**: PortOne 웹훅은 at-least-once 재전송을 가정 — 같은 `paymentId`에 대한 두 번째 웹훅은 이미 처리된 멱등키(§D2 흐름은 이미 idempotency-key 기반이므로 `RenewMembershipUseCase`의 기존 idempotency 검사가 그대로 중복 처리를 흡수한다). 새 멱등 테이블 불필요.

### D4 — 실패 정책: **fail-closed, 새 상태 없음** (v1)

- 청구 실패(declined 또는 재조정 결과 미결제) → **그 멤버십을 갱신하지 않는다.** `MembershipStatus`/read-time expiry 로직은 무변경 — 갱신이 안 됐으니 `validTo`가 지나면 자연히 read-time 만료된다. **새 상태(PAST_DUE/GRACE 등) 도입하지 않는다** — v1 범위를 좁게 유지.
- **1회 재시도**: 스케줄러가 D-0에 실패하면 D+1에 같은 enrollment로 한 번 더 시도(일시적 카드 문제 흡수). D+1도 실패 → 포기, 팬은 기존처럼 수동 재구독 가능.
- 팬 알림(이메일/UI 배지)은 이 ADR의 범위 밖(notification-service 연동은 별도 task) — v1은 "조용히 만료"도 허용 가능한 최소 동작으로 인정.

### D5 — 시크릿/저장 — `billingKey`는 시크릿급으로 취급

카드번호는 아니지만 **소유자를 대신해 청구를 발생시킬 수 있는 능력**이므로 API secret에 준하는 취급: DB 컬럼 암호화(at-rest), 로그에 절대 미출력, 응답 DTO에 절대 미포함.

---

## Consequences

**긍정**
- `RenewMembershipUseCase`의 검증된 결제·멱등성 로직을 100% 재사용 — 새로 만드는 코드는 "누가 언제 이 유스케이스를 부르는가"뿐.
- 재조정(`verify` 재호출)이 `libs/payment` 기존 op만으로 충분 — 새 read op 불필요(ADR-MONO-057 §1.3).
- state machine 무변경(D4) — 회귀 리스크 최소, 기존 read-time expiry 테스트 표면 보존.

**비용/리스크**
- 서버가 스스로 돈을 움직이는 첫 사례 — 스케줄러 버그(중복 실행 등)가 이중청구로 직결. 완화: `paymentId` 서버 채번 + idempotency-key 재사용(D2/D3)으로 스케줄러가 같은 청구를 두 번 커밋해도 `RenewMembershipUseCase`가 replay로 흡수.
- `BillingKeyEnrollment` 저장/암호화는 신규 인프라(암호화 키 관리) — 구현 task에서 범위 확정 필요.
- 웹훅 엔드포인트 = 신규 공격면 — 서명검증 실패 시 401 필수, 페이로드 신뢰 금지(D3)가 유일한 방어선이므로 구현 시 반드시 첫 커밋부터 테스트로 고정.

### Phase 분할

- **Phase 0 (본 ADR + ADR-MONO-057)** — 코드 불필요. 두 ADR 모두 ACCEPT 대기(별도 게이트 — `ADR-MONO-057 ACCEPTED` + `ADR-002 ACCEPTED` 각각 정확형 intent 필요).
- **Phase 1 (ADR-MONO-057 ACCEPT 후)** — `libs/payment-core` `RecurringBillingGateway` + `libs/payment-portone` 구현 + 웹훅 서명검증 유틸. 소비자 무관(TASK-MONO-478 패턴 반복).
- **Phase 2 (본 ADR ACCEPT 후, Phase 1 선행 필요)** — `BillingKeyEnrollment` 엔티티 + 발급 UI + 스케줄러 + 웹훅 엔드포인트 + `RenewMembershipUseCase` 재사용 배선. **ACCEPT는 설계 승인 = 이 Phase 착수 허가**(ADR-MONO-056 선례와 동일 관례 — 구현 선행조건 아님)이며, 별도로 **"라이브 검증 완료"(실제 빌링키 발급→자동청구 1회 확인)를 이 Phase의 완료 기준**으로 둔다 — Status History에 검증 완료를 별도 기록.

---

## Status History

- 2026-07-25 **Proposed** — 라이브 미검증. self-ACCEPT 금지(`platform/architecture-decision-rule.md § The ACCEPTED Gate`); ADR-MONO-057과 별개로 kanggle의 정확형 intent 필요.
- 2026-07-25 **Accepted** — kanggle 정확형 intent "ADR-002 ACCEPTED"(ADR-MONO-057 ACCEPTED와 동시 지시, 별개 게이트로 각각 명시). **Phase 2 착수 승인** — `TASK-FAN-BE-033`(BillingKeyEnrollment+스케줄러+웹훅+`RenewMembershipUseCase` 배선) + `TASK-FAN-FE-013`(빌링키 발급 UI), 둘 다 `TASK-MONO-482`(ADR-MONO-057 Phase 1, libs/payment 확장) 착륙 전까지 backlog 유지 — 본 ADR "Phase 분할"이 명시한 선행조건. 라이브 검증(실제 자동청구 1회 확인) 전까지는 이 Status 자체가 구현 완료를 뜻하지 않음 — 구현+라이브 검증은 별도 완료 기준.
