# Task ID

TASK-BE-588

# Title

outbound-service 의 masterref 시드가 **master 원본과 다른 행을 둘** 심는다 — 같은 UUID 에 다른 코드, 원본에 없는 SKU

# Status

ready

# Owner

wms-platform

# Task Tags

- seed
- demo
- drift

---

# Goal

`TASK-MONO-675` 가 admin-service 에 master-ref 시드를 얹으면서 형제 시드를 master 원본과 **손으로** 대조했고,
outbound-service 사본만 원본과 어긋난다는 것을 찾았다(2026-09-15). 이 티켓은 그 어긋남의 집이다.

| 행 | master-service 원본 | outbound-service `R__seed_dev_masterref.sql` |
|---|---|---|
| 로케이션 `01910000-0000-7000-8000-000000001002` | `WH01-C-01-01-01` · 존 `Z-C` (`master-service/.../db/seed/R__03_seed_dev_locations.sql:34-37`) | 🔴 `WH01-A-01-01-02` · 존 `Z-A` (`outbound-service/.../db/seed/R__seed_dev_masterref.sql:83-84`) |
| SKU `01910000-0000-7000-8000-000000000404` | 🔴 **없다** (`R__04_seed_dev_skus.sql` 은 `…0401`·`…0402`·`…0403` 셋) | `SKU-APPLE-002` (`:109-110`) |

목표: outbound 사본이 master 원본과 **같은 사실**을 말하게 하거나, 다르게 둬야 하는 이유를 적는다.

---

# Scope

## 포함

- 두 행이 outbound 의 다른 시드·데모 흐름에서 **실제로 쓰이는지** 먼저 잰다(주문 라인·피킹·출하가 `…1002`/`…0404` 를 참조하는가).
- 원본에 맞춘다, 또는 원본 쪽에 행을 더한다, 또는 다르게 두는 이유를 적는다 — AC-1 에서 고른다.

## 제외

- admin-service 시드(`TASK-MONO-675` — 원본을 따랐고 이 두 행을 옮기지 않았다).
- 시드 사본끼리 대조하는 가드 신설 — `TASK-MONO-675` AC-4 가 가드 여부를 판단한다. 🔴 그 판단이 «만든다» 로 나면 이 티켓이 첫 bite 표본이다.

---

# Acceptance Criteria

- [ ] **AC-0 — 재측정.** 위 표의 네 칸을 다시 읽어라. 이미 같아졌으면 phantom 으로 닫는다.
- [ ] **AC-1 — 쓰임부터.** `…1002`·`…0404` 를 outbound 시드(`db/seed/*`)·`infra/demo/seed/seed-wms.sh`·outbound 테스트 픽스처가 참조하는지 전수로 세고 file:line 을 적는다. 🔴 참조가 있으면 «원본에 맞춘다» 는 그 흐름을 깬다 — 고치기 전에 고른다.
- [ ] **AC-2 — 고친다.** 고른 방향대로 고치고, 🔴 `R__` 는 체크섬이 바뀌면 **살아 있는 데이터 위에서 다시 돈다** — `ON CONFLICT DO NOTHING` 이면 이미 심긴 틀린 행은 **안 고쳐진다**. 신선 볼륨과 기존 볼륨 둘 다에서 결과가 무엇인지 적는다.
- [ ] **AC-3 — 판정.** outbound 의 master 스냅샷 조회(또는 DB)에서 `…1002` 의 코드가 master 원본과 같다. 데모 볼륨은 창마다 신선하므로 판정은 신선 볼륨 기준이어도 된다 — 🔴 로컬 기존 볼륨이면 그렇게 적는다.

---

# Related Specs

- `projects/wms-platform/apps/master-service/src/main/resources/db/seed/R__03_seed_dev_locations.sql` · `R__04_seed_dev_skus.sql` — 원본
- `projects/wms-platform/apps/outbound-service/src/main/resources/db/seed/R__seed_dev_masterref.sql` — 어긋난 사본
- `projects/wms-platform/apps/{inbound,inventory,admin}-service/src/main/resources/db/seed/R__seed_dev_masterref.sql` — 원본과 맞는 사본들(대조군)
- `tasks/in-progress/TASK-MONO-675-the-denormalised-codes-are-null-because-the-ref-tables-are-empty.md` AC-2 — 발견 경위

# Related Contracts

- 없음 — 개발/데모 시드다. 이벤트·HTTP 계약을 바꾸지 않는다.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| `…0404` 가 outbound 시드 주문에서 쓰인다 | 🔴 지우면 그 주문 시드가 깨진다 — master 원본에 `SKU-APPLE-002` 를 더하는 쪽이 맞을 수 있다(그러면 사본 넷이 다 따라가야 한다) |
| 기존 로컬 볼륨 | `ON CONFLICT DO NOTHING` 이면 틀린 행이 남는다 — `down -v` 필요 여부를 적는다 |
| 실제 master 이벤트가 나중에 온다 | 스냅샷 소비자의 LWW 가 시드 행을 덮어쓰는지 확인(시드 `last_event_at` 이 원본보다 새로우면 영영 안 덮인다) |

# Failure Scenarios

1. **원본에 맞춰 코드만 바꾸고 참조를 안 센다** → 시드 주문이 존재하지 않는 로케이션·SKU 를 가리킨다.
2. **신선 볼륨에서만 보고 닫는다** → 기존 볼륨의 틀린 행은 그대로다. AC-2 가 막는다.
3. **«사본끼리 대조하는 자동화가 없다» 를 이 티켓에서 고치려 한다** → 범위 밖(`TASK-MONO-675` AC-4).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (시드 두 행 + 참조 전수. 판단은 AC-1 한 곳)
