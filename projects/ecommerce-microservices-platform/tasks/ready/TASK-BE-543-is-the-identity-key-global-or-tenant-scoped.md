# TASK-BE-543 — 신원 키가 전역인가 테넌트별인가: 한 번 재면 도달성 판정 3건이 동시에 끝난다 (그리고 내 "도달 가능 확정" 은 과장이었다)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (측정 자체는 작지만, 결론이 이미 머지된 3개 티켓의 문구를 뒤집는다)

> `TASK-BE-542` 가 만든 backstop IT 의 전제를 `TASK-BE-540` 형제 훑기가 간접적으로 반증하면서 발견. **이 티켓의 첫 산출물은 내가 이미 머지한 주장을 되돌리는 것이다.**

---

## Goal

### 🔴 정정 대상 — 내가 과장한 것

`TASK-BE-542`(PR #2775)는 백스톱 핸들러의 도달성을 실 DB 로 증명하려고 `ReviewDataIntegrityBackstopIntegrationTest` 를 썼다. 그 테스트는 **같은 `X-User-Id` 를 서로 다른 두 `X-Tenant-Id`(`tenant-a`, `tenant-b`)로** 보낸다. 통과했고, 나는 그걸 근거로 `TASK-BE-540` 사례 B 를 **"도달 가능으로 확정"** 이라고 티켓·INDEX·PR 본문에 적었다.

**그런데 그 입력 상태가 프로덕션에서 만들어질 수 있는지는 재지 않았다.** `user-service/V4__add_tenant_id.sql:8` 은 이렇게 적는다:

> `user_id stays globally unique (uq_user_profiles_user_id) — **an IAM user belongs to one tenant**; tenant_id is the isolation column, not part of the identity key.`

이 주장이 참이면 **한 사용자가 두 테넌트로 리뷰하는 상황 자체가 존재할 수 없고**, 내 IT 는 플랫폼 불변식이 금지하는 상태를 직접 만들어 놓고 그 위에서 단언한 것 — 즉 **불가능한 픽스처**다. 내가 이 저장소에서 반복해 인용해 온 바로 그 실패 모드다.

**IT 가 실제로 증명한 것과 아닌 것을 갈라야 한다:**

| 증명됨 (유효) | 증명 안 됨 (내가 주장한 것) |
|---|---|
| SQLState 23505 가 Hibernate → Spring → 핸들러까지 전달된다 | 그 입력 상태가 프로덕션에서 생성 가능한가 |
| `uq_reviews_user_product_active` 가 테넌트-블라인드다 | `TASK-BE-540` 사례 B 가 실제로 도달 가능한가 |

Layer 2 의 **원래 목적(23505 전달 확인)은 여전히 유효**하다. 무효인 것은 거기서 파생시킨 도달성 주장이다.

### 왜 이게 한 건이 아니라 세 건인가

`TASK-BE-540` AC-4 형제 훑기가 **같은 지문 2건**을 더 찾았고, 셋 다 **같은 질문 하나**에 도달성이 걸려 있다:

| 사이트 | 선체크 | 제약 | 도달성이 걸린 질문 |
|---|---|---|---|
| review `reviews` (BE-540 사례 B) | 테넌트 스코프 | `(user_id, product_id)` 전역 | 같은 `user_id` 가 두 테넌트에? |
| user-service `wishlist_items` | `existsByUserIdAndProductIdAndTenantId` | `UNIQUE (user_id, product_id)` (V3:8) | **동일** |
| payment `payments` | `findByOrderIdAndTenantId` | `order_id` UNIQUE 전역 (V1:3) | 같은 `order_id` 가 두 테넌트에? |

⇒ **신원 키(`user_id` / `order_id`)가 전역인지 테넌트별인지를 한 번 확정하면 세 건이 동시에 판정된다.** 셋을 따로 재는 것은 낭비이고, 더 나쁘게는 서로 다른 답이 나올 수 있다.

---

## Scope

### In Scope

1. **AC-0 — 신원 키 판정** (아래). 이 티켓의 실질 전부.
2. 판정 결과로 이미 머지된 **과장 문구 정정**(아래 목록).
3. `ReviewDataIntegrityBackstopIntegrationTest` 의 Javadoc·단언을 판정에 맞게 조정.

### Out of Scope

- **범위 불일치 자체의 수정** — 그건 `TASK-BE-540`(진행 중, PR #2780). 이 티켓은 **도달성 판정만** 한다.
- **무근거 선체크 2건**(`sellers.account_id`, `commission_accrual` — 백스톱 자체가 없는 건)은 별 축이다. BE-540 AC-4 목록에 남아 있고 별도 티켓 대상.
- 함대 표준 판정은 `TASK-MONO-450`.

---

## Acceptance Criteria

- **AC-0 (gate — 신원 키 판정)** — 다음을 **배선에서** 확정한다. **산문 주장은 증거가 아니다** — `V4__add_tenant_id.sql:7-9` 과 `ShippingRepositoryImpl:70`("orderId 는 전역 유일")은 **주석 속 주장**이며, BE-540 형제 훑기가 이미 그 이유로 채택을 거부했다:
  - (a) 게이트웨이가 `X-User-Id` ↔ `X-Tenant-Id` 짝을 **강제**하는가? 한 `sub` 가 두 테넌트 컨텍스트로 하류에 도달할 수 있는가? (**assume-tenant / omni-corp 다도메인 테넌트 경로를 반드시 포함해 볼 것** — 운영자 신원은 일반 쇼퍼와 다를 수 있다.)
  - (b) `order_id` 발급이 전역 유일인가? (`OrderJpaEntity` 는 할당식 `@Id`)
  - (c) (a)/(b) 가 "전역·1:1 강제됨" 이면 세 사이트 모두 **도달 불가**이고, 결함은 "500/409 결함" 이 아니라 **정합성 결함**으로 강등된다.
- **AC-1 (정정)** — 판정이 "도달 불가" 면 아래를 **전부** 되돌린다. 한 곳만 고치면 다음 사람이 남은 곳을 믿는다:
  - `tasks/done/TASK-BE-542-...md` § 구현 결과 — "결정적 도달 경로 1개", "도달 가능으로 확정"
  - `tasks/INDEX.md` BE-542 done 행 + BE-540 ready 행
  - `tasks/ready/TASK-BE-540-...md` 의 2026-07-20 블록
  - 루트 `tasks/ready/TASK-MONO-450-...md` — **2026-07-20 확인 결과 이 문서는 도달 경로 수를 인용하지 않아 정정 불필요.** 다만 판정이 "도달 불가" 로 나오면 *"결정적 백스톱 경로가 0 개"* 라는 사실이 그 티켓 AC-1(모양 판정)의 **새 입력**이 되므로 그때 반영할 것.

  위 네 곳은 **이 티켓 착수 시점에 이미 "미측정" 으로 약화돼 있다**(2026-07-20 선제 정정). AC-1 의 일은 판정 결과를 **확정형으로** 다시 쓰는 것이다.
- **AC-2 (테스트 조정)** — `ReviewDataIntegrityBackstopIntegrationTest` 를 판정에 맞춘다. **삭제가 기본값이 아니다** — 그 테스트는 23505 전달이라는 **유효한 성질**을 증명하며, 그게 사라지면 선별 판별식 전체가 다시 미검증이 된다. 도달 불가로 판정되면 **"프로덕션 도달성이 아니라 예외 전파 메커니즘을 고정한다"** 로 Javadoc 을 정직하게 다시 쓰고, 픽스처가 인위적임을 명시한다.
- **AC-3 (BE-540 과의 조율)** — PR #2780 이 `uq_reviews_user_product_active` 를 테넌트화하면 이 IT 는 **어차피 바뀌어야 한다**(두 번째 삽입이 정당히 201). **그 RED 는 결함이 아니라 신호다.** 착수 시 #2780 의 상태를 먼저 확인하고, 이미 머지됐다면 이 티켓은 그 위에서 정리한다.
- **AC-4** — 판정이 "도달 가능" 으로 나오면(즉 내 원래 주장이 옳았으면) **그것도 명시적으로 기록**한다. 결론이 바뀌지 않는 것 자체는 실패가 아니다 — **재측정 없이 유지하는 것**이 실패다.

---

## Related Specs

- `apps/user-service/src/main/resources/db/migration/V4__add_tenant_id.sql:7-9` — 판정 대상 산문 주장
- `apps/review-service/src/test/java/com/example/review/ReviewDataIntegrityBackstopIntegrationTest.java` — 문제의 픽스처
- `specs/features/multi-tenancy-and-marketplace.md` §2 — 테넌트 격리 SoT (M1~M7)
- `rules/traits/multi-tenant.md` — 신원 축 정의
- `tasks/ready/TASK-BE-540-...md` § AC-4 형제 훑기 — 나머지 2 사이트의 출처
- `../../../../tasks/ready/TASK-MONO-450-data-integrity-mapping-has-no-fleet-standard.md` — 도달 경로 수를 입력으로 쓰는 하류

## Related Contracts

- 없음 — 측정과 문서 정정이다. 다만 판정이 "도달 가능" 이면 `review-api` / `wishlist-api` / `payment-api` 의 교차 테넌트 거동 서술을 확인할 것.

---

## Edge Cases

1. **🔴 "테스트가 통과했으니 도달 가능하다" 는 추론이 이 티켓을 낳았다** — 통과는 **그 입력을 넣었을 때** 무슨 일이 일어나는지만 말한다. **그 입력이 생길 수 있는지는 별개의 질문**이고, 픽스처를 내가 직접 만들었다면 더더욱 그렇다.
2. **운영자 경로가 예외일 수 있다** — assume-tenant 로 한 운영자가 여러 테넌트에 작용한다. 그때 하류에 도달하는 `X-User-Id` 가 운영자 `sub` 인지 대상 테넌트의 사용자인지에 따라 답이 갈린다. **쇼퍼 경로만 보고 결론내지 말 것.**
3. **세 사이트의 답이 다를 수 있다** — `user_id` 와 `order_id` 는 다른 키다. 하나로 묶어 재되 **결론은 사이트별로** 적을 것.
4. **강등해도 결함은 남는다** — "도달 불가" 여도 선체크와 제약이 서로 모순된 주장을 하는 상태는 결함이다(`TASK-BE-540` Edge 1). 우선순위만 내려간다.

---

## Failure Scenarios

- **F1 — 이 티켓을 "문서 정정" 으로만 처리한다.** AC-0 을 안 재고 문구만 약하게 바꾸면, 반대 방향의 같은 결함(근거 없는 주장)을 만드는 것이다.
- **F2 — backstop IT 를 지운다.** 23505 전달 증명이 함께 사라지고 선별 판별식이 미검증으로 돌아간다. AC-2 참조.
- **F3 — 산문을 증거로 채택.** `V4:8` 이 "an IAM user belongs to one tenant" 라고 적었다는 사실은 **그게 강제된다는 증거가 아니다.** 강제 지점을 코드에서 찾아야 한다.
- **F4 — 정정을 한 곳만 한다.** 다섯 곳에 흩어져 있고(위 AC-1), 남은 곳을 다음 사람이 믿는다.

---

## Test Requirements

- 판정 자체는 **코드 읽기 + 배선 확인**이 주다.
- (a) 가 불확실하면 **라이브 프로브**: 게이트웨이를 통해 한 토큰으로 두 테넌트 컨텍스트에 도달 가능한지 실제로 시도. **픽스처가 아니라 실 경로로.**

---

## Definition of Done

- [ ] AC-0 신원 키 판정 (배선 근거, 운영자 경로 포함)
- [ ] AC-1 다섯 곳 문구 정정 (도달 불가 판정 시)
- [ ] AC-2 backstop IT Javadoc·단언 조정 (삭제 아님)
- [ ] AC-3 PR #2780 상태 확인 후 조율
- [ ] AC-4 "도달 가능" 판정이어도 명시적 기록

---

## Notes

- **분량**: small~medium. 측정은 작고 **정정 범위가 넓다.**
- **dependency**: `선행` = `TASK-BE-540`(PR #2780, 진행 중 — 인덱스를 테넌트화하면 이 IT 가 어차피 바뀐다). `하류` = `TASK-MONO-450`.
- **출처**: `TASK-BE-540` AC-4 형제 훑기가 산문 주장을 증거로 채택하지 않은 것을 보고, 같은 기준을 내 IT 에 적용했더니 내 것이 통과하지 못했다.
- **이 task 가 방어하는 실패 모드**: **초록 테스트는 "이 입력에 이렇게 반응한다" 를 말하지 "이 입력이 존재한다" 를 말하지 않는다.** 픽스처를 내가 만들었다면 그 픽스처의 생성 가능성 자체가 별도의 주장이고, 나는 그걸 재지 않은 채 "도달 가능 확정" 을 세 문서에 적어 머지했다. **남에게 적용한 기준을 내 산출물에 적용하지 않은 것**이 이 결함의 진짜 원인이다. [[env_test_fixture_impossible_input_proves_nothing]] [[feedback_guard_predicate_wrong_verify_the_artifact]] [[feedback_recount_population_dont_inherit_scope]]
