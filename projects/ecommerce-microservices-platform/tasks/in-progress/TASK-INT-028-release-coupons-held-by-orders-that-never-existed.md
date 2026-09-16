# Task ID

TASK-INT-028

# Title

저장된 적 없는 주문에 묶인 쿠폰을 배치로 대조해 푼다 — release 가 도착하지 못한 경우의 안전망

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

쿠폰이 **존재하지 않는 주문**에 `USED` 로 묶여 만료까지 풀리지 않는 상태를, 주기적인 대조로 찾아 푼다.

`TASK-INT-026` 이 보상(release)을 붙였고 `TASK-INT-027` 이 release 와 apply 의 순서 역전을 막았다. 둘 다
**release 가 쿠폰 서비스에 도착한다**는 전제 위에 있다. 도착하지 못하면(쿠폰 서비스 장애·네트워크) 아무것도
그 쿠폰을 풀지 않는다. 이 티켓은 그 경우의 안전망이고, 이미 묶여 버린 과거분도 같은 경로로 풀린다.

---

# 사실 (코드와 스펙을 **읽어** 얻었다 — AC-0 이 확인한다)

| # | 사실 | 출처 |
|---|---|---|
| 1 | order-service 의 release 호출은 재시도 2회 뒤 실패하면 **로그만 남기고 끝난다**. 코드 주석이 「여기서 되돌릴 수 있는 것은 더 없다 — 쿠폰이 저장 안 된 주문에 `USED` 로 남을 수 있다」고 적고 있다 | `PromotionServiceCouponClient.java` `releaseCoupon`, 테스트 `release_failure_isSwallowed` |
| 2 | 고아 쿠폰은 스스로 풀리지 않는다 — 취소 복원 경로(`order.order.cancelled`)는 주문이 있어야 발화한다 | `TASK-INT-027` 사실 6 |
| 3 | `TASK-INT-027` 의 펜스는 release 가 **도착한** 경우만 막는다. 도착하지 못한 release 는 펜스도 남기지 않는다 | `CouponCommandService.releaseCoupon` |
| 4 | promotion-service 의 쿠폰 조회·잠금은 **요청 테넌트로 걸러진다**(`TenantContext.currentTenant()`). 테넌트 헤더 없이 온 release 는 다른 테넌트의 쿠폰을 찾지 못한다 | `CouponRepositoryImpl.findByIdForUpdate` |
| 5 | order-service 에는 테넌트를 가로지르는 주문 조회 선례가 있다(`findByIdAcrossTenants`, 사가·운영자 경로 다수) | `OrderRepository` |
| 6 | batch-worker 는 이미 `client_credentials` 로 order-service 내부 엔드포인트를 주기적으로 부른다(`confirm-paid-stale`, 10분 주기, ShedLock, 실패 시 FAILED 이력만 남기고 스케줄러는 살린다) | `StalePaidOrderConfirmationJob`, `order-confirm-paid-stale.md` |
| 7 | batch-worker 스펙은 「비조회 내부 호출은 `confirm-paid-stale` **하나뿐**, 다른 내부 엔드포인트 호출 금지」라고 못박고 있다 | `batch-worker/dependencies.md` § Allowed Service Interactions, § Forbidden Dependencies |
| 8 | promotion-service 의 `/api/internal/**` 는 인증 없이 **내부망 전용**이고, order-service 의 `/api/internal/**` 는 `client_credentials` JWT 를 fail-closed 로 검증한다 | `InternalCouponController`, `OrderSecurityConfig` |

---

# AC-1 결정 기록 (2026-09-16 UTC)

소유자 답(선택창), 원문 그대로: **「배치 대조 (Recommended)」**

선택지 원문 설명: *batch-worker 가 일정 시간 지난 USED 쿠폰의 주문 ID 를 order-service 에 확인하고, 없는 주문의
쿠폰만 release. 사실의 주인에게 묻기 때문에 저장된 주문의 쿠폰을 잘못 푸는 일이 없고, 과거분·미래분을 모두 푼다.
비용: 내부 API 2개 + 배치 잡 1개. 기존 confirm-paid-stale 구조를 그대로 따른다.*

고르지 않은 안: 「이벤트 관찰」(outbox 지연 시 정상 주문의 쿠폰을 잘못 풀 위험, 과거분 판단 불가) ·
「release 재시도 내구화」(과거분을 못 푼다) · 「지금은 보류」.

🔴 **이 결정이 승인하는 것**: 사실 7 의 batch-worker 경계 확장 — 내부 호출이 하나에서 셋(order 존재 조회,
promotion 목록 조회, promotion release)으로 는다. AC-2 에서 스펙을 먼저 고친다.

---

# Scope

## In Scope

- **order-service**: 주문 ID 목록을 받아 **어느 테넌트에든 존재하는** ID 를 돌려주는 내부 조회 엔드포인트
  (`/api/internal/orders/**`, 기존 `client_credentials` 체인). 읽기 전용.
- **promotion-service**: 일정 시간(`olderThanMinutes`) 이상 `USED` 인 쿠폰을 **테넌트를 가로질러** 모아
  `(couponId, orderId, tenantId, usedAt)` 로 돌려주는 내부 조회 엔드포인트. 읽기 전용, `limit` 상한.
- **batch-worker**: 대조 잡 — ① promotion 에서 오래된 `USED` 쿠폰 목록 → ② order-service 에 그 주문 ID 들의
  존재를 묻고 → ③ **존재하지 않는** 주문의 쿠폰만 기존 `POST /api/internal/coupons/{couponId}/release` 로,
  **쿠폰 자신의 테넌트**를 실어 푼다. 실행 이력·지표·ShedLock·on/off 스위치는 기존 잡과 같은 모양.
- 두 내부 계약 문서와, 경계 문장이 바뀌는 스펙(batch-worker overview·dependencies, order-service·promotion-service
  dependencies/overview)을 코드보다 먼저 정렬한다.

## Out of Scope

- promotion-service `/api/internal/**` 에 JWT 인증을 붙이는 일 — 사실 8 의 비대칭은 `TASK-INT-026` 부터 있었다.
  이 티켓은 그 경로를 **호출**할 뿐 새로 열지 않는다. 필요하면 별도 티켓.
- 주문은 있는데 쿠폰이 안 풀린 경우(취소 이벤트 유실 등) — 다른 결함 부류다. 이 잡은 **주문이 없는** 쿠폰만 다룬다.
- 운영 환경의 실제 고아 쿠폰 수를 세는 일 — 잴 수 있는 환경이 이 작업 호스트에 없다(AC-0 ⚪).

---

# Acceptance Criteria

- [x] **AC-0 (측정 먼저)** — ① release 실패 뒤 그 쿠폰을 푸는 경로가 **코드 어디에도 없음**을 기존 테스트와
  전수 검색으로 확인해 적는다. ② 실제 환경의 고아 쿠폰 수는 잴 수 있으면 재고, 없으면 ⚪ 로 «못 쟀다 + 이유» 를
  적는다. 🔴 ① 이 틀렸으면(이미 푸는 경로가 있으면) 그 경로를 적고 나머지 AC 없이 닫는다.
  - 닫힘 ①: **푸는 경로 없음 — 사실 1~3 이 맞았다.** 기준 `2563bba1f`. 쿠폰 상태의 주인은 promotion-service
    하나이므로 `USED → ISSUED` 전이는 어느 경로든 그 코드를 거친다. 그래서 `releaseFor` · `.restore()` ·
    `restoreCouponsByOrderId` · `releaseCoupon(` 호출자를 promotion·order·batch-worker 의 `src/main` 전체에서 셌다:

    | 호출 | 발화 조건 | release 가 도착 못 한 고아에 닿나 |
    |---|---|---|
    | `CouponCommandService.releaseCoupon` ← `InternalCouponController` | order-service 가 주문 롤백 직후 보낼 때만(`OrderPlacementService:126`) | 닿지 않음 — 바로 그 호출이 실패한 경우다 |
    | `restoreCouponsByOrderId` ← `OrderCancelledEventConsumer` | `order.order.cancelled` 수신 | 닿지 않음 — 주문이 없으면 취소 이벤트도 없다 |

    만료 배치(`CouponExpirationScheduler`)는 `status = 'ISSUED'` 인 쿠폰만 보므로(`findExpiredIssuedCoupons`)
    `USED` 고아를 건드리지 않는다. release 실패가 조용히 끝난다는 것은 기존 테스트
    `PromotionServiceCouponClientTest.release_failure_isSwallowed` 가 고정하고 있다.
  - ⚪ ②: **못 쟀다.** 이 작업 호스트의 Docker 데몬이 꺼져 있어 로컬 스택이 없고, 데모 환경 DB 는 자격증명이
    필요해 이 작업의 판단으로 접속하지 않았다. 따라서 이 티켓은 「고아가 몇 개인가」가 아니라 「고아가 **생길 수
    있고** 생기면 **아무것도 풀지 않는다**」는 확인 위에 선다 — 0개여도 안전망은 필요하다(release 실패는 앞으로도
    일어난다).
- [x] **AC-1 (결정)** — 소유자 답을 원문 그대로 적는다.
  - 닫힘: 위 § AC-1 결정 기록.
- [x] **AC-2 (스펙 먼저)** — 두 내부 계약(order 존재 조회, promotion 오래된 USED 목록)과 batch-worker 의 경계 문장
  (사실 7), order-service·promotion-service 의 인바운드 서술이 결정과 같은 방향을 가리킨다. 코드는 이 AC 이후 커밋에만.
  - 닫힘: 새 계약 `specs/contracts/http/internal/order-existence.md`, `promotion-api.md` § `POST /api/internal/coupons/stale-used`
    (+ release 절에 두 번째 호출자·테넌트 헤더). 경계 문장 **열한 곳**: batch-worker `dependencies.md`(허용 호출 4개 명시 ·
    Consumes From 2행 · Forbidden 「모름으로 풀지 않음」) · `overview.md`(책임 · 스케줄러 행 · 아웃바운드 2행 · 의존 시스템) ·
    `architecture.md`(Consumed Interfaces · Dependencies · Key Jobs) · order-service `dependencies.md` 23행 · promotion-service
    `dependencies.md` · `overview.md`. 🔵 「한 사실이 두 곳에 있으면 한쪽만 고쳐진다」 — 옛 문장 전수 검색에서 batch-worker
    `architecture.md:80` 의 「One internal system-command exception」이 남아 있던 것을 찾아 같이 고쳤다.
  - 계약에서 새로 정한 것:
    - **존재 조회의 「없음」은 주문이 지워지지 않는 동안에만 「저장된 적 없음」이다.** `2563bba1f` 기준 order-service 에 주문
      삭제·보존 정리 경로가 없음을 확인했다. 🔴 주문 삭제를 도입하려면 이 배치부터 다시 봐야 한다고 계약 불변식으로 적었다.
    - **본문 없는 응답은 「모름」이다.** 기존 `OrderServiceClient` 는 null 본문을 0건 기본값으로 바꾸는데, 존재 조회에서
      그렇게 하면 「존재 0건 = 전부 없음」이 되어 전부 푼다. 계약에 금지로 적었다.
    - **전부-없음 브레이크**: 한 회차에 ≥ 20건을 봤는데 존재하는 주문이 0건이면 아무것도 풀지 않고 `FAILED`. 존재 조회
      주소가 빈 환경을 가리키는 설정 실수를 막는다(그 경우 모든 주문이 「없음」으로 답해진다).
    - **유예 하한 30분은 promotion-service 가 강제한다**(기본 60분). 호출자 설정을 믿지 않는다.
    - **`order_id IS NULL` 인 `USED` 쿠폰은 제외** — 물어볼 주문이 없으므로 「판단 불가」이고, 판단 불가는 풀지 않는다.
    - 🔵 기안이 미뤄 둔 **만료 지난 `USED` 쿠폰은 포함**한다: 풀면 `ISSUED` 가 되고 만료 배치가 `EXPIRED` 로 넘긴다 —
      한 번도 안 쓴 쿠폰과 같은 끝 상태다.
- [ ] **AC-3** — 주문이 **어느 테넌트에도 없는** 쿠폰만 풀린다. 주문이 존재하면 상태(`PENDING`·`CANCELLED`·
  `DELIVERED` 무엇이든)와 무관하게 건드리지 않는다.
- [ ] **AC-4 (「모름」≠「없음」)** — order-service 가 실패(연결 실패·타임아웃·4xx/5xx·토큰 실패)하거나 응답이 요청한
  ID 를 판단하지 못하면 그 회차는 **아무 쿠폰도 풀지 않는다.** 이력은 `FAILED`.
- [ ] **AC-5 (유예)** — `olderThanMinutes` 보다 최근에 `USED` 가 된 쿠폰은 대상이 아니다. 기본값은 주문 생성 트랜잭션이
  끝날 수 있는 시간보다 충분히 길다(≥ 30분). 진행 중인 주문의 쿠폰을 풀지 않는다.
- [ ] **AC-6 (테넌트)** — 다른 테넌트의 고아 쿠폰도 풀린다. release 는 그 쿠폰의 테넌트로 보낸다.
- [ ] **AC-7 (격리·멱등)** — release 한 건의 실패가 나머지를 멈추지 않는다. 잡 실패가 스케줄러를 죽이지 않는다.
  같은 회차를 두 번 돌려도 결과가 같다(이미 풀린 쿠폰은 목록에 다시 안 나온다).
- [ ] **AC-8** — 두 새 조회의 SQL(테넌트 가로지르기, 인덱스, `limit`)을 실제 Postgres 에서 확인한다
  (로컬 Docker 차단 → CI 통합 레인이 권위). 못 쟀으면 ⚪.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` →
> 선언된 domain/trait 파일.

- `specs/services/batch-worker/overview.md`, `dependencies.md`, `architecture.md`
- `specs/services/order-service/dependencies.md` (23행 — 게이트웨이 예외가 하나뿐이라는 문장)
- `specs/services/promotion-service/overview.md`, `dependencies.md`, `architecture.md`
- `platform/service-types/batch-job.md` (batch-worker 의 Service Type)

# Related Skills

- `.claude/skills/INDEX.md` 에서 배치·보상·서비스 간 호출 항목

---

# Related Contracts

- `specs/contracts/http/internal/order-confirm-paid-stale.md` — 인증·게이트웨이 배제·응답 모양의 선례
- `specs/contracts/http/promotion-api.md` — `POST /api/internal/coupons/{couponId}/release`
- 신규(AC-2): order 존재 조회 계약, promotion 오래된 USED 목록 계약

---

# Participating Components

- batch-worker (호출자·오케스트레이터)
- order-service (주문 존재의 권위, 읽기 전용)
- promotion-service (쿠폰 목록·release)

---

# Trigger

batch-worker 스케줄(기존 잡과 같은 주기 계열, ShedLock).

---

# Expected Flow

1. batch-worker → promotion-service: `olderThanMinutes` 이상 `USED` 인 쿠폰 목록(`limit` 건)
2. batch-worker → order-service: 그 주문 ID 들 중 **존재하는** 것
3. 존재하지 않는 주문의 쿠폰마다 batch-worker → promotion-service release (쿠폰의 테넌트 헤더)
4. 이력 `COMPLETED` + 집계(조회·존재·풀림·실패), 지표 증가

---

# Edge Cases

- 목록이 비어 있음 → order-service 를 부르지 않고 `COMPLETED`
- 같은 주문 ID 에 쿠폰이 둘 이상 → 각각 판단
- order-service 응답에 요청하지 않은 ID 가 섞여 옴 → 무시(요청한 ID 만 판단)
- release 시점에 쿠폰이 이미 풀림(다른 경로·재실행) → release 는 no-op 이고 **펜스를 남긴다**(`TASK-INT-027`) —
  그 주문은 없으므로 펜스가 남아도 무해하다
- `limit` 에 걸려 남은 고아 → 다음 회차가 이어서 처리
- 만료가 지난 `USED` 쿠폰 → 대상에 포함할지 AC-2 에서 정해 적는다(풀면 곧 만료 배치가 `EXPIRED` 로 넘긴다)

---

# Failure Scenarios

- order-service 장애 → AC-4: 아무것도 풀지 않음, `FAILED`
- promotion-service 목록 조회 장애 → `FAILED`, 아무것도 풀지 않음
- release 일부 실패 → 나머지는 계속, 실패 건수 집계, 다음 회차가 재시도(목록에 다시 나온다)
- 토큰 발급 실패 → order 호출 전 실패 → AC-4
- 🔴 order-service 가 테넌트로 걸러진 조회를 써서 **다른 테넌트의 주문을 「없음」으로 답함** → 저장된 주문의 쿠폰이
  풀린다. 이 티켓에서 가장 비싼 실패다. AC-3·AC-6·AC-8 이 함께 막는다

---

# Test Requirements

- order-service: 존재 조회 서비스 단위 + 테넌트 가로지르기 실제 Postgres IT + 내부 체인 인증(401) 슬라이스
- promotion-service: 오래된 USED 목록 단위 + 유예·테넌트 실제 Postgres IT
- batch-worker: 잡 단위(AC-3~AC-7, 특히 order 실패 시 release 0건), 클라이언트 단위, 기존 잡과 같은 IT 모양
- 계약 문서 변경이 있으면 계약 테스트

---

# Definition of Done

- [x] AC-0 측정 기록
- [x] AC-1 소유자 결정 기록
- [x] 스펙·계약 선행 정렬
- [ ] 구현 완료
- [ ] 테스트 추가·통과
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=Opus — 세 서비스에 걸친 경계 확장이고, 판정이 한 번만 틀려도(다른 테넌트 주문을 「없음」으로 읽거나
장애를 「없음」으로 읽으면) 저장된 주문의 쿠폰이 풀려 할인을 두 번 받게 된다.
