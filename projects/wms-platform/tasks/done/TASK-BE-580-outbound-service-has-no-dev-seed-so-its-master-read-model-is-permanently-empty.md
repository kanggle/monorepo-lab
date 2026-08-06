# Task ID

TASK-BE-580

# Title

outbound-service 의 dev 시드가 `db/dev/` 에 있어 **한 번도 실행된 적이 없다** — 마스터 read-model 영구 0행

# Status

done

---

# 🔴🔴 정정 (2026-08-06 착수) — **이 티켓의 제목과 전제가 틀렸다**

> "outbound-service 만 `db/seed` 가 없다"

**없지 않았다.** 파일은 처음부터 있었다 —
`src/main/resources/`**`db/dev`**`/V99__seed_dev_masterref.sql`. 형제 셋이 전부
`db/seed/` 를 쓰는데 outbound 만 `db/dev/` 였고, **저장소의 어떤
`spring.flyway.locations` 도 `db/dev` 를 부르지 않는다**(전수 grep: 참조 0건).
그래서 그 파일은 **어디서도, 한 번도 실행된 적이 없다.** 게다가 그 헤더는
*"Activated via ... application-dev.yml / application-standalone.yml"* 이라고 적고
있었는데 outbound 에는 **그 두 파일이 다 없었다.**

즉 결함은 "누락" 이 아니라 **경로 불일치 + 활성화 파일 부재로 인한 죽은 코드**다.
증상(read-model 0행, 주문 생성 불가)은 같지만 **고치는 방법이 다르다** — 새로 쓰는 게
아니라 옮기는 것이다.

🔴 **왜 틀렸나.** 착수 조사에서 `db/seed` 디렉터리만 글롭하고 그 0건을 "시드 없음" 으로
읽었다. `db/` 아래를 통째로 셌으면 바로 보였다. [[env_empty_detector_output_is_not_absence]]

🔴 **그 오독이 두 번째 오류를 낳았다.** AC-2 는 *"CUSTOMER 거래처는 어느 시드에도 없다
(마스터 V103 은 SUPPLIER 계열만 심는다)"* 라고 적었는데 **둘 다 거짓**이다:
V103 은 `SUP-001`·`CUST-001`·`BOTH-001` **셋**을 심고, `db/dev` 파일도 `CUST-001` 을
갖고 있었다. 그 사이 `seed-wms.sh` 는 `...802 / CUS-001` 이라는 **유령 거래처**를
`dbexec` 로 만들고 있었다. V103 헤더를 열었다면 거기 이미
*"aligned with the inbound + **outbound** V99__seed_dev_masterref.sql baseline
(SUP-001 / CUST-001)"* 이라고 적혀 있었다 — **도달 불가능한 파일을 가리키는 참조**가
결손의 직접 증거였는데 읽지 않았다.

## 실제로 한 일

1. `git mv db/dev/V99__seed_dev_masterref.sql → db/seed/V99__seed_dev_masterref.sql`
   (**내용 무변경** — 원본이 내가 새로 쓴 것보다 풍부하다: 로케이션 2 · SKU 2)
2. `application-dev.yml` 신설 (형제와 동일한 `spring.flyway.locations`)
3. `wms-devseed.override.yml` 에 `outbound-service` 항목 추가
4. `seed-wms.sh` 의 `dbexec` 블록 + 유령 `...802` 제거, `CUST-001`(`...901`) 사용

🔵 **내가 새로 쓴 파일은 지웠다.** 남겼으면 같은 버전(V99)의 마이그레이션이 두 위치에
생겨, 둘 다 로케이션에 오르는 순간 Flyway 가 "Found more than one migration with
version 99" 로 죽는다 — 이 티켓이 막으려던 "두 벌이 갈린다" 를 내가 만들 뻔했다.

## 검증 (볼륨 삭제 → 이미지 재빌드 → 신선 기동)

```
outbound_db  warehouse 1 · zone 1 · location 2 · sku 2 · lot 1 · partner 1(CUST-001)
             ← 전부 Flyway. dbexec 0건
POST /api/v1/outbound/orders → 201 (실제 API)
DB           SO-DEMO-0001 | PICKING | customer_partner_id = ...901
2회차        생성 0 · 기존 2 · 실패 0, rc=0
가드         verify-demo-wrapper.sh PASS ((y) 포함)
```

🔴 **BE-579 가 이 검증을 네 번 방해했다** — 신선 기동 **4회 중 4회** outbound 또는
inventory 가 갇혔고 게이트웨이가 504 를 냈다. `restart` 후 **실제 HTTP 응답**을 3초
간격으로 폴링해 살아 있는 창에서만 통과시켰다(살아난 시점 = 16번째 폴링 ≈ 48초).
🔴 그 과정에서 새 사실: **Docker 가 `healthy` 라고 보고하는 동안에도 HTTP 는 죽어 있다**
(`retries: 12` × 15s ⇒ 최대 3분 지연) — BE-579 에 반영했다.

# Owner

wms-platform

# Task Tags

- bug
- infra

---

# 배경 — `TASK-MONO-510` 이 발굴 (AC-8)

WMS 시드가 출고 주문을 만들려다 막혀서 드러났다.

## 실측 (2026-08-06)

```
outbound_db.warehouse_snapshot  0행
outbound_db.sku_snapshot        0행
outbound_db.partner_snapshot    0행
```

⇒ `POST /api/v1/outbound/orders` 는 `warehouseId`/`skuId`/`customerPartnerId` 를
전부 read-model 에서 해소하므로 **구조적으로 생성 불가**다.

## 왜 비어 있는가 — 형제 비대칭

이 스냅샷들은 `master.*` Kafka 이벤트로만 채워진다. 그런데 데모/로컬-dev 의 마스터
데이터는 **master-service 의 Flyway 시드(`db/seed/V99..V103`)가 직접 심는다** —
INSERT 이므로 도메인 이벤트가 **한 번도 발행되지 않는다.**

`inbound-service` 와 `inventory-service` 는 이 구멍을 각자
`db/seed/V99__seed_dev_masterref.sql` 로 메웠다. 6개 서비스 전수 확인:

| 서비스 | `src/main/resources/db/seed/` |
|---|---|
| master-service | V99 · V100 · V101 · V102 · V103 |
| inbound-service | **V99__seed_dev_masterref.sql** |
| inventory-service | **V99__seed_dev_masterref.sql** |
| **outbound-service** | **없음** ← 이 티켓 |
| admin-service | 없음 (마스터 read-model 없음 — 해당 없음) |
| notification-service | 없음 (마스터 read-model 없음 — 해당 없음) |

즉 **마스터 read-model 을 가진 세 서비스 중 outbound 만 빠졌다.** inbound/inventory 의
V99 헤더는 *"UUIDs match master-service / inventory-service seeds verbatim — keep these
in sync"* 라고 적고 있는데, 그 "sync" 대상 목록에서 outbound 가 누락된 것으로 보인다.

## 임시 우회 (현재 커밋된 상태)

`infra/demo/seed/seed-wms.sh` 가 `dbexec --why` 로 세 스냅샷을 채운다. **사유가 코드에
적혀 있고** 게이트(y)를 통과하지만, 이것은 시드 스크립트가 제품의 공백을 대신 메우는
것이라 **형제와 다른 모양**이다. 이 티켓이 닫히면 그 블록은 삭제되어야 한다.

🔵 CUSTOMER 거래처(`01910000-0000-7000-8000-000000000802`, `CUS-001`)는 **어느 시드에도
없었다** — master 의 V103 은 SUPPLIER 계열만 심는다. 출고 주문은 `partner_type ∈
{CUSTOMER, BOTH}` 를 요구하므로, 이 티켓은 master-service 쪽 시드에도 CUSTOMER 를
추가할지 함께 판단해야 한다(안 그러면 outbound 의 V99 만 아는 유령 거래처가 된다).

---

# Goal

`outbound-service` 가 형제와 같은 방식으로 dev/데모에서 마스터 read-model 을 갖는다.
`infra/demo/seed/seed-wms.sh` 의 `dbexec` 블록이 **필요 없어진다.**

---

# Scope

## In Scope

- `apps/outbound-service/src/main/resources/db/seed/V99__seed_dev_masterref.sql`
- `infra/demo/wms-devseed.override.yml` 에 `outbound-service` 항목 추가
- master-service 시드의 CUSTOMER 거래처 판단 (추가 여부 + 근거)
- `seed-wms.sh` 의 `dbexec` 블록 제거

## Out of Scope

- master 이벤트를 실제로 발행하게 만드는 것 — 훨씬 큰 설계 변경이고, 형제 두 개가
  이미 택한 해법(dev 전용 Flyway 시드)과 다른 방향이다

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 스냅샷 6종 재측정(착수 시 dbexec 가 넣은 3행뿐, 나머지 0).
      🔴 그리고 **UUID 대조가 이 티켓의 전제를 무너뜨렸다** — `db/dev` 파일이 이미
      형제와 같은 UUID 를 쓰고 있었다. 위 § 정정 참조
- [x] **AC-1** — outbound 의 V99 가 inbound/inventory 와 **같은 UUID** 를 쓴다.
      새로 쓰지 않고 **원본을 옮겨서** 달성했다(내용 무변경 ⇒ 갈릴 여지 0)
- [x] **AC-2** — CUSTOMER 출처 결정: **이미 정해져 있었다.** master V103 이 `CUST-001`
      (`...901`)을 심고 outbound 의 V99 가 같은 UUID 를 미러한다. 추가할 것이 없었고,
      내가 만든 `...802 / CUS-001` 유령을 **제거**하는 것이 실제 조치였다
- [x] **AC-3 (실기동)** — 볼륨 삭제 + 이미지 재빌드 후 기동 → 스냅샷이 Flyway 로 차고
      `POST /api/v1/outbound/orders` 가 **`dbexec` 없이 201**, DB 행 확인
      (`customer_partner_id = ...901`). 2회차 `생성 0 · 기존 2 · 실패 0`
- [x] **AC-4** — `seed-wms.sh` 의 `dbexec` 블록·유령 UUID 제거. 가드 `(y)` 재통과
      (`verify-demo-wrapper.sh` rc=0)
- [x] **AC-5** — `git grep "01910000-0000-7000-8000"` 전수: **20개 파일**(시드 6 · 테스트 11 ·
      `seed-wms.sh` · 이 티켓). 🔴 **갈릴 위험은 남는다** — 마스터 시드 UUID 가 네 벌
      (master/inbound/inventory/outbound)로 존재하고 이를 대조하는 가드가 **없다**.
      각 파일 헤더의 *"keep these in sync"* 주석이 유일한 방어이며, 이번 건이 보여주듯
      **주석은 도달 불가능한 파일도 가리킬 수 있다**. → 후속 가드 후보(미티켓)

---

# Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `infra/demo/seed/README.md` — 시드 규약

# Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §2.1
  (`customerPartnerId` 는 `partner_type ∈ {CUSTOMER, BOTH}` 필수)

# Edge Cases

- `db/seed` 위치는 `application-dev.yml` 에서만 켜진다 — 데모는
  `wms-devseed.override.yml` 이 `SPRING_FLYWAY_LOCATIONS` 로 연다. **양쪽 다** 고쳐야
  한다(하나만 고치면 dev 는 되고 데모는 안 된다, 또는 그 반대)
- 이미 뜬 데모 스택에는 Flyway 가 재실행되지 않는다 — 검증은 볼륨을 지우고 해야 한다

# Failure Scenarios

- **outbound 에만 새 UUID 로 심음** → 같은 창고가 서비스마다 다른 id 를 갖는다.
  출고 주문은 201 이 되지만 입고·재고와 **연결되지 않는다**. AC-0/AC-1 이 막는다
- **`seed-wms.sh` 의 dbexec 를 남겨 둠** → 두 출처가 공존한다. `ON CONFLICT DO NOTHING`
  이라 조용히 넘어가므로 갈려도 아무도 모른다. AC-4 가 막는다

# Definition of Done

- [ ] outbound V99 + override 항목 + CUSTOMER 출처 결정
- [ ] 볼륨 삭제 후 실기동 201 증거
- [ ] `seed-wms.sh` dbexec 제거 + 가드 (y) 통과
- [ ] Ready for review
