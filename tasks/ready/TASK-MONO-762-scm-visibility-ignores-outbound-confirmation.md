# Task ID

TASK-MONO-762

# Title

SCM 재고 가시성이 **출고를 반영하지 않는다** — wms 는 출고 확정을 `wms.inventory.confirmed.v1` 로 내는데 scm 은 그 토픽을 구독하지도 릴레이하지도 않아, 21차 창에서 같은 SKU·창고가 scm **95** 대 wms **85** 로 갈렸다. 투영할 수량의 뜻(보유 / 가용)을 정하고 그에 맞게 출고 이벤트를 반영한다

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- cross-project
- event
- scm-platform
- wms-platform

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 수량의 뜻을 정하는 결정(AC-0)이 범위를 정한다. «보유 재고 + `confirmed` 차감» 이면 scm 소비자 하나 + 릴레이 허용 목록 한 줄이라 Sonnet 으로 충분하다.
>
> ⏳ **소유자 결정 대기** — AC-0 의 ⓐ/ⓑ 와 착수 순서. 🔴 scm 백엔드·릴레이 설정 변경은 AMI 재굽기 뒤에야 데모에 실린다.

---

# 왜 루트 티켓인가

경로가 세 곳에 걸친다 — 생산자 `projects/wms-platform`(이미 낸다, 변경 없음 예상) · 릴레이 `infra/demo/relay/mm2.properties` 의 `wms->scm.topics`(데모 전용, 프로젝트 밖) · 소비자 `projects/scm-platform/apps/inventory-visibility-service`. 릴레이 한 줄과 소비자가 한 PR 에 같이 가야 main 이 반쪽이 되지 않는다.

# Dependency Markers

- 출처: `TASK-MONO-760`(done) § CORRECTION 2026-10-05 «21차 창 판정» — AC-2 의 «같은 SKU/창고» 는 맞았지만 수량이 갈렸다(06:57:35Z SSM 실측).
- 선행: 없음. 후속: 없음.

# Goal

데모에서 scm `/scm/inventory` 의 수량이 같은 창 wms 재고와 **같은 뜻으로** 일치한다 — 출고가 일어나면 scm 도 줄어든다.

# 실측 (21차 창, 2026-10-05 UTC · `TASK-MONO-760` 에서 옮김)

- wms `inventory_db.inventory`(창고 `01910000-0000-7000-8000-000000000001` · SKU `01910000-0000-7000-8000-000000000403`): `available_qty` **85** · version 2 — 06:47:24 적치(+95, `system:putaway-consumer`) → 06:47:28 출고(−10, `system:shipping-confirmed-consumer`).
- wms `inventory_outbox`: `inventory.received` 1 · `inventory.reserved` 1 · `inventory.confirmed` 1 — 셋 다 발행됨.
- scm `inventory_snapshots`(`demo-corp`): 같은 노드·SKU 수량 **95**.
- 원인: scm 소비자는 `wms.inventory.{received,adjusted,transferred}.v1` 만 구독(`WmsInventory*Consumer`)하고, 릴레이 `mm2.properties` `wms->scm.topics` 도 `adjusted|alert|received|transferred|outbound.shipping.confirmed` 만 허용한다 ⇒ `reserved`·`confirmed` 는 scm 에 닿지도 않는다.
- 계약(`projects/wms-platform/specs/contracts/events/inventory-events.md` § 4 · § 6): `reserved` = 가용 → 예약 이동, `confirmed` = 예약 차감(«AVAILABLE is unchanged»). 즉 보유(= 가용 + 예약)를 줄이는 것은 `confirmed` 하나다.

# Scope

## In Scope

- AC-0 결정에 따른 scm 소비자 추가(또는 변경) + 릴레이 허용 목록 + 구독 계약(`projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md`) 먼저.
- 투영 테넌트(`TASK-MONO-760` 의 `ProjectionTenant`)를 새 소비자도 쓴다.
- 시험(IT: 적치 후 확정 → 스냅샷 감소) + bite.
- 데모 판정(재굽기 뒤 창).

## Out of Scope

- wms 생산자 변경(이미 낸다).
- 스냅샷 화면의 열 구성 변경(가용/예약 따로 보이기 등) — ⓑ 를 고르면 별도 판단.

# Acceptance Criteria

- [ ] **AC-0 (소유자 결정)** — scm 스냅샷 수량의 뜻: **ⓐ 보유 재고**(가용 + 예약 — `confirmed` 를 차감으로만 반영, `reserved`·`released` 는 무시) · **ⓑ 가용 재고**(`reserved` −, `released` +, `confirmed` 무변화). 결정을 이 파일에 원문으로 적는다. (추천: ⓐ — 지금 스냅샷이 이미 «받은 만큼 더한» 보유 의미이고, 이벤트 하나 · 대칭 이벤트 쌍이 없어 경로가 짧다.)
- [ ] **AC-1** — 결정한 뜻대로 적치 95 → 출고 10 시나리오에서 scm 스냅샷이 85 가 된다(IT, 실제 Kafka). 대조군: 출고 없는 적치만이면 95.
- [ ] **AC-2** — 릴레이 허용 목록이 새 토픽을 포함하고, `scripts/check-cross-project-topic-relay.sh` 가 초록이다(구독 ↔ 허용 목록 대조).
- [ ] **AC-3** — bite: 새 소비자의 차감을 끄면 AC-1 의 칸만 빨강.
- [ ] **AC-4 (라이브, ⚪)** — 재굽기 뒤 창: `/scm/inventory` 수량 = 같은 창 wms 의 같은 SKU·창고 수량(ⓐ 면 가용+예약).

# Related Specs

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` · `data-model.md`
- `projects/wms-platform/specs/contracts/events/inventory-events.md` § 4–6

# Related Contracts

- `projects/scm-platform/specs/contracts/events/inventory-visibility-subscriptions.md` — 새 구독(계약 먼저)
- `projects/wms-platform/specs/contracts/events/inventory-events.md` — 읽기만

# Edge Cases

- 같은 출고의 `confirmed` 가 재전송되면 두 번 빼지 않는다 — 기존 `event_dedupe`(eventId) 를 탄다.
- `confirmed` 가 적치(`received`)보다 먼저 도착하면(파티션 순서가 다르다 — 21차 창의 received 는 파티션 1) 노드·SKU 행이 없다 — 음수 스냅샷을 만들지 말고 정한 규칙(보류·DLT·0 하한 중 하나)을 AC-0 에서 같이 정한다.
- 한 `confirmed` 에 여러 줄(`lines[]`) — 줄마다 차감.

# Failure Scenarios

1. **소비자만 추가하고 릴레이를 안 연다** — 데모에서 이벤트가 scm 에 안 닿아 여전히 95(AC-2 가 막는다).
2. **`reserved` 도 빼고 `confirmed` 도 뺀다** — 두 번 차감해 75 가 된다(뜻을 섞은 결과 — AC-0 이 한 뜻만 고른다).
3. **시드로 맞춘다** — 투영은 여전히 출고를 모른다(`TASK-MONO-760` 이 금지한 그것).
