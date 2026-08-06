# Task ID

TASK-BE-580

# Title

outbound-service 만 `db/seed` 가 없어 마스터 read-model 이 **영구히 0행**이다 — 형제 파리티 결손

# Status

ready

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

- [ ] **AC-0 (재측정)** — 착수 시 세 스냅샷의 행수를 다시 센다. 🔴 그리고 **UUID 가
      형제와 바이트 단위로 같은지** 확인한다 — 다르면 같은 마스터를 가리키지 않는
      두 벌이 되어, 이 티켓이 고치려는 병을 그대로 재생산한다
- [ ] **AC-1** — outbound 의 V99 가 inbound/inventory 와 **같은 UUID** 로 창고·SKU·거래처를
      심는다
- [ ] **AC-2** — CUSTOMER 거래처의 출처를 하나로 정한다(master V103 에 추가 vs
      각 read-model 시드에만). 정한 이유를 파일 헤더에 적는다
- [ ] **AC-3 (실기동)** — `demo-up.sh iam wms` 로 **볼륨 없는 상태에서** 띄운 뒤
      `POST /api/v1/outbound/orders` 가 `dbexec` 없이 201 이다
- [ ] **AC-4** — `seed-wms.sh` 에서 `dbexec` 블록과 그 주석을 지운다. 가드 (y) 재통과
- [ ] **AC-5** — `git grep` 으로 마스터 시드 UUID 가 흩어진 곳을 전수 확인하고,
      네 벌(master/inbound/inventory/outbound)이 갈릴 위험을 문서화한다

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
