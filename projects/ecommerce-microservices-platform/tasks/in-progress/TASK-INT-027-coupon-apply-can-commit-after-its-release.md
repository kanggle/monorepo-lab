# Task ID

TASK-INT-027

# Title

쿠폰 되돌림이 늦게 도착한 apply 를 막지 못한다 — 없는 주문에 `USED` 로 남는다

# Status

in-progress

# Owner

integration

# Task Tags

- api
- code
- test

---

# Goal

주문이 저장되지 않은 쿠폰이 `USED` 로 남지 않게 한다. `TASK-INT-026` 이 보상(release)을 붙였지만
**순서를 보장하지는 못했다** — release 가 apply 보다 먼저 도착하면 되돌릴 것이 없어 아무 일도 하지
않고, 그 뒤에 apply 가 커밋되면 쿠폰은 존재하지 않는 주문에 묶인다. 그 티켓이 「남는 위험(닫지 않음,
기록만)」으로 남긴 문장이 이 티켓이다.

---

# 사실 (코드를 **읽어** 얻었다 — 숫자로 돌린 것은 아니다. AC-0 이 그 일이다)

| # | 사실 | 출처 |
|---|---|---|
| 1 | order-service 는 release 동기화를 apply **호출 전에** 등록하고, 트랜잭션이 커밋되지 않으면 `afterCompletion` 에서 `releaseCoupon(couponId, orderId)` 를 부른다 | `OrderPlacementService.java:106-128` |
| 2 | promotion-service 의 release 는 쿠폰이 **그 `orderId` 로 `USED` 가 아니면** 로그만 남기고 끝난다. 「되돌릴 것이 없었다」는 사실을 **어디에도 기록하지 않는다** | `CouponCommandService.releaseCoupon:166-180`, `Coupon.releaseFor:85-91` |
| 3 | `Coupon.apply` 는 `ISSUED` 면 `USED` 로 바꾸고 `orderId` 를 박는다. 그 주문이 실재하는지는 묻지 않는다 — 서비스 경계상 물을 수도 없다 | `Coupon.java:59-76` |
| 4 | apply 클라이언트는 읽기 타임아웃 3s · 재시도 2회다. **타임아웃은 서버의 처리를 취소하지 않는다** | `PromotionServiceCouponClient.java:58-62, 72-99` |
| 5 | release 는 실패해도 로그만 남긴다 — 「여기서 되돌릴 수 있는 것은 더 없다」가 코드 주석으로 적혀 있다 | `PromotionServiceCouponClient.java:115-119` |
| 6 | 고아가 된 쿠폰은 스스로 풀리지 않는다. 취소 복원 경로(`order.order.cancelled` → `restoreCouponsByOrderId`)는 **주문이 있어야** 발화하는데, 그 주문은 없다 | `promotion-service OrderCancelledEventConsumer` |
| 7 | 고아 apply 는 `CouponUsed` 도 발행한다. 다만 `promotion.coupon.used` 는 **v1 구독자가 없다** — 하류 피해는 오늘은 없고, 미래 구독자에게는 없는 주문의 사용 이력으로 보인다 | `promotion-events.md:33-39` |

## 창(window)이 열리는 순서

1. 주문 생성 트랜잭션이 release 동기화를 등록한다 (사실 1)
2. apply 요청이 promotion-service 에 **도달**하고, 아직 커밋되지 않았다
3. order-service 쪽에서 읽기 타임아웃 → `CouponServiceUnavailableException` → 주문 트랜잭션 롤백
4. `afterCompletion` 이 release 를 보낸다 → 쿠폰은 아직 `ISSUED` → **no-op** (사실 2)
5. 2번의 apply 가 커밋된다 → 쿠폰 `USED`, `order_id` = 저장된 적 없는 주문 (사실 3)

재시도(사실 4)는 창을 넓힌다. 1차 시도가 서버에서 계속 돌고 있는 동안 2차 시도가 나가므로, release 가
두 요청 **사이**에 도착할 수 있다.

**사용자가 보는 것**: 그 쿠폰으로 다시 주문하면 `422 COUPON_ALREADY_USED`. 쿠폰은 만료일까지 묶인다.
운영자에게도 복구 수단이 없다 — 되돌릴 주문이 없으므로 취소도 못 한다.

---

# Scope

## In Scope

- promotion-service 가 **「되돌릴 것이 없었던 release」를 기록**한다(펜스). 같은 `(couponId, orderId)` 의
  이후 apply 는 쿠폰을 `USED` 로 만들지 않고 거절한다 — 그 주문은 이미 죽었기 때문이다.
- 기록은 멱등하다: release 가 재시도로 여러 번 와도 한 행이다.
- apply 가 펜스에 걸렸을 때의 응답 코드·의미를 계약에 적는다(`promotion-api.md`, `platform/error-handling.md`).
- 정상 경로 회귀: 펜스가 없는 apply, apply 후의 정상 release, 같은 주문의 멱등 재적용, 다른 주문의 release.
- 실제 Postgres 로 마이그레이션·제약·매핑을 확인한다(CI 통합 레인).

## Out of Scope

- promotion-service 가 order-service 에 「이 주문 있느냐」를 되묻는 것 — 역방향 동기 의존이 생긴다
  (`dependencies.md` § Forbidden Dependencies). 펜스는 **release 가 이미 들고 온 정보**만 쓴다.
- 이미 고아가 된 과거 쿠폰의 소급 정리 — 별도 티켓. 이 티켓은 **새로 생기는 것을 막는다**.
- 프로모션 비용 조회 API(`TASK-BE-592` 에서 보류).
- 펜스 행의 TTL/청소 — 선례(`coupon_issue_request`, `processed_events`)를 따라 두지 않는다.

---

# Acceptance Criteria

- [x] **AC-0 (재현 먼저)** — release → apply 순서를 실제로 돌려 쿠폰이 `USED` + 없는 `orderId` 로 끝나는지
  측정하고, 명령·결과를 이 파일에 적는다. 🔴 재현되지 않으면 위 「사실」 표의 **어느 항목이 틀렸는지**를
  적고, 나머지 AC 없이 이 티켓을 닫는다.
  - 닫힘: 재현됐다. `expected: ISSUED but was: USED` (`CouponApplyAfterReleaseTest.java:94`), 서비스
    로그가 순서를 그대로 찍었다 — 기록은 아래 § AC-0 재현 측정. 틀린 사실 없음.
- [x] **AC-1 (계약 먼저)** — 펜스에 걸린 apply 의 상태 코드·오류 코드와 「release 는 되돌릴 것이 없어도
  기록한다」를 `promotion-api.md` 와 `platform/error-handling.md` 에 적는다. 코드 변경은 이 AC 이후
  커밋에만 들어간다.
  - 닫힘: `COUPON_PLACEMENT_RELEASED` / **422**. 형제 거절(`ALREADY_USED`·`EXPIRED`·`NOT_OWNED`)이
    모두 422 이고, 409 는 이 저장소에서 멱등키 경합에 쓰고 있어 그쪽을 피했다. 적은 곳 **다섯**:
    `promotion-api.md` apply 절(순서 없음 + 쌍 단위 펜스) · 같은 문서 apply 오류 표 · 같은 문서
    internal release 「Behaviour」(되돌릴 것이 없어도 기록, 멱등) · `platform/error-handling.md`
    Promotion 표 · promotion-service `architecture.md` 통합 규칙 + `overview.md` 책임 문장·공개 표면 행.
  - 🔵 `platform/` 은 공유 경로라 원칙상 루트 태스크의 몫이지만, 이 표는 `[domain: ecommerce]` 처럼
    프로젝트별 행을 담도록 설계된 문서이고 바로 위 두 행(`COUPON_NOT_APPLICABLE`,
    `COUPON_SERVICE_UNAVAILABLE`)이 `TASK-INT-026` 이 같은 방식으로 넣은 것이다. 같은 선례를 따랐다.
  - 🔴 promotion-service 가 order-service 에 「그 주문 있느냐」를 되묻지 않는다는 것도 계약에 적었다.
    되물으면 의존이 역류한다(`dependencies.md` § Forbidden Dependencies). 펜스는 release 가 이미
    들고 온 `(couponId, orderId)` 만 쓴다.
- [x] **AC-2** — AC-0 이 재현한 그 순서가 이제 쿠폰을 `ISSUED` 로 남긴다. apply 는 거절되고 `CouponUsed`
  는 발행되지 않는다.
  - 닫힘: 같은 테스트가 초록으로 뒤집혔다 — `CouponApplyAfterReleaseTest` **tests=4 failures=0**,
    apply 는 `CouponPlacementReleasedException`, 쿠폰 `ISSUED` · `orderId=null` ·
    `publishCouponUsed` 호출 0. 아래 § 구현 측정.
- [x] **AC-3 (정상 경로 회귀)** — 펜스가 없는 apply, apply 후 도착한 release(→ `ISSUED` 복원), 같은 주문의
  멱등 재적용, 다른 주문이 쓴 쿠폰의 release(→ 무변화)가 모두 변경 전과 같다.
  - 닫힘: promotion-service 단위 스위트 **전체 105건 실패 0**(변경 전과 같은 수). 기존
    `CouponCommandServiceTest` 19 · `CouponCommandServiceReplayReleaseTest` 4 ·
    `Coupon` 도메인 11+5 가 **단언을 하나도 고치지 않고** 통과했다(생성자 인자만 늘렸다).
    새 `aReleaseThatFindsItsOwnOrderRevertsTheCouponAndLeavesNoFence` 가 「apply 가 먼저면
    펜스를 남기지 않는다」를 따로 못박는다.
- [x] **AC-4** — 같은 쿠폰을 **다른 새 주문**으로는 여전히 쓸 수 있다. 펜스는 `(couponId, orderId)` 쌍이지
  쿠폰 전체를 막는 것이 아니다.
  - 닫힘: `theFenceIsScopedToThePair_soAnotherOrderCanStillUseTheCoupon` — order-1 을 실제로
    release 해 펜스를 남긴 뒤 order-2 로 apply 하면 할인 5,000 이 나오고 쿠폰이 order-2 로 `USED`.
- [ ] **AC-5** — 마이그레이션·유니크 제약·JPA 매핑을 실제 Postgres 에서 확인한다(로컬 Docker 차단 →
  CI ecommerce integration 레인이 권위). 못 쟀으면 ⚪ 로 «못 쟀다 + 이유» 를 적는다.
  - 진행 중: `CouponReleaseFenceIntegrationTest` 4건을 작성했고 **컴파일은 통과**했다(같은 실행의
    `compileTestJava`). 로컬 `test` 실행 결과에 이 클래스의 XML 은 없다 — 프로젝트 수준
    `projects/ecommerce-microservices-platform/build.gradle:43` 이 모든 ecommerce 앱의 `test` 에서
    `excludeTags 'integration'` 을 걸고, `@Tag("integration")` 은 promotion-service 의 별도
    `integrationTest` 태스크(`includeTags 'integration'`, Docker 필요)만 돌린다. 🔵 이 기전은 모듈
    자신의 `build.gradle` 에 없어서 처음엔 못 찾았다 — 「태그 제외가 없는데 왜 안 도나」를 추측으로
    적지 않고 저장소 전체를 grep 해 확인했다. CI `ecommerce-integration-tests` 레인이
    `:promotion-service:integrationTest` 를 부르므로(ci.yml:3683) 그 실행이 권위다.
    **PR CI 에서 확인한 뒤 닫는다.**

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` →
> 선언된 domain/trait 파일. 모르는 태그는 Hard Stop(`CLAUDE.md`).

- `platform/error-handling.md` (쿠폰 오류 코드 표 — 321~324, 364~369행)
- `specs/services/promotion-service/architecture.md` (106행 — apply/release 서술)
- `specs/services/promotion-service/overview.md` (22, 36~37행)
- `specs/services/order-service/dependencies.md` (24행 — 유일한 아웃바운드 HTTP 의존)

# Related Skills

- `.claude/skills/INDEX.md` 에서 트랜잭션·보상 관련 항목

---

# Related Contracts

- `specs/contracts/http/promotion-api.md` (257~262행 apply, 303~310행 internal release)
- `specs/contracts/events/promotion-events.md` (33~39행 `CouponUsed`)

---

# Participating Components

- promotion-service (펜스 기록·판정, 마이그레이션)
- order-service (호출자 — 이 티켓에서 **변경 없음**이 기대값이다)

---

# Trigger

쿠폰을 고른 주문 생성 중 apply 응답이 늦거나 유실되어 주문 트랜잭션이 커밋되지 않을 때.

---

# Expected Flow

1. order-service 가 release 동기화를 등록하고 apply 를 부른다 (현행 유지)
2. 주문이 커밋되지 않으면 release 가 `(couponId, orderId)` 로 도착한다
3. promotion-service: 그 주문이 쓴 쿠폰이면 `ISSUED` 로 되돌린다 (현행 유지)
4. promotion-service: **되돌릴 것이 없으면 그 쌍을 펜스로 기록한다** (신규)
5. 뒤늦게 같은 쌍의 apply 가 커밋을 시도하면 펜스를 보고 거절한다 — 쿠폰은 `ISSUED` 로 남는다 (신규)

---

# Edge Cases

- release 가 재시도로 두 번 이상 도착 → 펜스는 한 행(멱등)
- apply 가 먼저 커밋되고 release 가 뒤에 도착 → 기존 경로대로 `ISSUED` 복원, 펜스 기록 없음
- apply 와 release 가 동시에 커밋을 다툼 → 쿠폰 행 잠금(`findByIdForUpdate`)과 펜스의 유니크 제약이 심판
- 같은 쿠폰, **다른 주문** → 펜스 무관, 정상 사용 (AC-4)
- 펜스가 있는 쌍의 apply 를 order-service 가 재시도 → 매번 같은 거절(멱등)
- 존재하지 않는 쿠폰의 release → 지금처럼 조용히 끝난다. 펜스를 남길지 여부를 AC-1 에서 정해 적는다

---

# Failure Scenarios

- 펜스 insert 가 유니크 위반으로 실패 → 이미 기록된 것이므로 성공으로 다룬다
- 펜스 테이블 접근 실패(DB 장애) → release 전체가 실패한다. order-service 는 이미 로그만 남기므로
  (사실 5) 창은 그대로 남는다. 이 경우 「고칠 수 없다」가 아니라 「기존과 같아진다」임을 티켓에 적는다
- apply 가 펜스에 걸려 거절될 때 order-service 쪽에는 **듣는 사람이 없다**(그 주문은 이미 롤백됨).
  거절 코드는 사용자 화면이 아니라 promotion-service 의 관측을 위한 것이다
- 계약만 바꾸고 코드가 따라오지 않음 → AC-1 이후 커밋에만 코드가 들어가는지 커밋 순서로 확인

---

# Test Requirements

- AC-0 재현 테스트(수정 전 빨강 → 수정 후 초록으로 남긴다) — `CouponCommandService` 수준,
  기존 `CouponCommandServiceReplayReleaseTest` 의 Mockito 하네스와 같은 모양
- 펜스 멱등·정상 경로 회귀 단위 테스트
- 실제 Postgres 통합 테스트(마이그레이션·유니크 제약·매핑)
- 계약 문서 변경이 있으면 계약 테스트

---

# Definition of Done

- [ ] AC-0 재현 기록
- [ ] 계약 선행 정렬
- [ ] 구현 완료
- [ ] 테스트 추가·통과
- [ ] `TASK-INT-026` 이 남긴 「남는 위험」 문장이 이 티켓으로 닫혔음을 기록
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=Opus — 두 서비스에 걸친 커밋 순서와 보상 설계다. 테이블 하나를 더하는 일로
보이지만, 판정이 틀리면 **정상 결제가 거절된다**(펜스가 산 주문을 막는 경우). 회귀 범위가 돈이다.

🔵 선례: `coupon_issue_request`(V8, `TASK-BE-536`) — 「청구를 먼저 잡고 그다음에 만든다」, TTL 없음.
`processed_events`(V5) — 이벤트 중복 제거. 펜스는 같은 계열이고, 새 개념이 아니다.

---

# AC-0 재현 측정 (2026-09-16 UTC)

**돌린 것** — worktree `int-027-impl`, base `a4f4aa920`

```
./gradlew :projects:ecommerce-microservices-platform:apps:promotion-service:test \
          --tests '*CouponApplyAfterReleaseTest*'
```

**결과** — `BUILD FAILED in 49s`, `1 test completed, 1 failed`. 단언 값:

```
org.opentest4j.AssertionFailedError:
expected: ISSUED
 but was: USED
        at CouponApplyAfterReleaseTest.java:94
```

**서비스 로그가 순서를 그대로 찍었다** (같은 실행의 `system-out`)

```
Coupon release skipped — not used by this order: couponId=04e6871a…, orderId=order-1, status=ISSUED
Coupon applied: couponId=04e6871a…, orderId=order-1, discount=5000
```

release 가 먼저 도착해 `status=ISSUED` 를 보고 **아무 일도 하지 않고 끝났다** — 되돌릴 것이 없었다는
사실을 남기지도 않았다. 그 뒤 apply 가 같은 `orderId` 로 커밋됐고 쿠폰은 `USED`, `order_id=order-1` 이
됐다. 그 주문은 저장된 적이 없다.

⇒ 「사실」 표 1~5 는 맞았다. **틀린 항목 없음.** 사실 6(고아 쿠폰은 스스로 풀리지 않는다)과 7(`CouponUsed`
구독자 없음)은 이 테스트의 범위 밖이라 코드·계약을 읽어 확인한 그대로 두었다 — 여기서 측정한 것은
**창이 실재한다**는 사실이다.

🔵 증거 커밋은 이 재현 테스트 **하나만** 담는다. 테스트는 고친 뒤에도 남는다(수정 전 빨강 → 수정 후 초록).
⚪ 실제 두 서비스를 띄운 종단 재현은 하지 않았다 — 로컬 Docker 가 막혀 있고, 이 창은 promotion-service
안에서 두 호출의 **순서**만으로 재현되므로 단위 수준이 창을 그대로 담는다.

---

# 구현 측정 (2026-09-16 UTC)

**돌린 것** — `./gradlew :projects:ecommerce-microservices-platform:apps:promotion-service:test`

| | 수정 전(증거 커밋 `1d4e5f31f`) | 수정 후 |
|---|---|---|
| `CouponApplyAfterReleaseTest` | tests=4 **failures=1** (`expected: ISSUED but was: USED`) | tests=4 **failures=0** |
| 모듈 전체 | 105 tests, 1 failed, `BUILD FAILED` | 105 tests, **0 failed**, `BUILD SUCCESSFUL in 1m 46s` |

기존 스위트는 단언을 하나도 고치지 않았다. 생성자에 `CouponReleaseRepository` 가 늘어 세 테스트 클래스의
**인자 목록만** 바뀌었다(`CouponCommandServiceTest`, `CouponCommandServiceReplayReleaseTest`, 그리고 이 티켓의
새 클래스). 직접 생성하는 곳이 그 셋뿐인 것은 grep 으로 확인했다.

## 🔴 목(mock)을 페이크로 바꾼 이유 — 처음 초록이 공허했다

첫 수정 후 실행에서 AC-0 이 **여전히 빨갰다**. 원인은 제품 코드가 아니라 하네스였다: `CouponReleaseRepository`
를 Mockito 목으로 두면 기억이 없어서, release 가 `record(...)` 를 불러도 목은 아무것도 담지 않고 뒤이은
apply 의 `existsFor` 가 `false` 를 돌려준다. 펜스가 실제로는 동작하는데 테스트에는 없는 것처럼 보였다.

`willReturn(false, true)` 로 스텁하면 초록이 되지만 그건 **내가 시킨 값을 되읽는 것**이라 아무것도 증명하지
않는다. 그래서 `InMemoryCouponReleaseRepository` 페이크로 바꿨고, **유니크 제약까지 흉내 내게** 했다 —
`record` 가 중복 쌍에 던진다. 덕분에 제품 코드가 `existsFor` 검사를 빼면 「재시도 release 는 한 번만
기록한다」 테스트가 소리내어 깨진다. 스텁이었다면 그 삭제를 통과시켰을 것이다.

## 설계에서 자리가 중요한 곳

펜스 검사는 `findByIdForUpdate`(쿠폰 행 잠금) **뒤**에 있다. release 도 같은 행을 잠그므로 두 트랜잭션이
거기서 직렬화되고, 어느 쪽이 먼저 잡아도 결과가 옳다 — apply 가 먼저면 release 가 자기 주문의 `USED` 를
찾아 되돌리고, release 가 먼저면 그 펜스가 apply 에 보인다. 잠금 **앞**에 두면 검사와 사용 사이가 다시
이 티켓이 닫으려는 바로 그 창이 된다.
