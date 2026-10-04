# Task ID

TASK-MONO-760

# Title

데모의 SCM **재고 가시성 투영이 비어 있다** — `/scm/inventory` «표시할 스냅샷이 없습니다» · 운영 개요 SCM «스냅샷 행 수 0» (19·20차 창). WMS 재고 이벤트가 투영에 닿지 않는 이유를 먼저 재고, 닿게 하거나 데모 처분을 정한다

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

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 원인이 wms 생산자(아웃박스) · 프로젝트 간 릴레이(MM2, `infra/demo/relay`) · scm 소비자(테넌트·그룹·토픽 이름) 중 어디인지가 이 티켓의 전부다. 원인이 한 프로젝트 안으로 좁혀지면 Sonnet 으로 충분하다.
>
> ⏳ **소유자 우선순위 결정 대기** — 착수 순서는 소유자가 정한다. 🔴 백엔드·compose·릴레이 변경은 **AMI 재굽기** 뒤에야 데모에 실린다(소유자 승인).

---

# 왜 루트 티켓인가

이벤트 경로가 세 곳에 걸쳐 있다 — 생산자 `projects/wms-platform`(inventory-service 아웃박스) · 릴레이 `infra/demo/relay/mm2.properties`(`wms->scm.topics` — 데모 전용, 프로젝트 밖) · 소비자 `projects/scm-platform/apps/inventory-visibility-service`(`WmsInventory{Received,Adjusted,Transferred}Consumer`, 그룹 `scm-inventory-visibility-v1`). 어디를 고칠지는 AC-0 이 정한다. AC-0 이 원인을 **한 프로젝트 안**으로 좁히면 구현은 그 프로젝트의 `tasks/ready/` 로 옮겨도 된다(이 파일에 그 결정을 적고).

# Dependency Markers

- 출처: 19차 창(2026-10-04 UTC, `ami-00815e1f9614cda90`)·20차 창(2026-10-04 UTC, `ami-0d78d476824493d77`, `f0927bcd0`) 운영 개요 SCM 카드 «스냅샷 행 수 0»(카드 상태는 `ok` — `TASK-MONO-758` 19차 AC-1 표 · 20차 절).
- 데모 시드가 이미 이 사실을 알고 있다: `infra/demo/seed/seed-scm.sh:369-384`(§ 4) — «200 인데 스냅샷 0건 / /scm/inventory 는 빈다. 투영에는 wms 재고 이벤트가 필요하다(이 슬라이스 범위 밖)». 시드는 의도적으로 **쓰지 않는다**(«시드가 넣었다» 는 «투영이 동작한다» 의 증거가 아니다 — 같은 파일 머리 주석).
- 선행: 없음. 후속: 없음.

# Goal

데모에서 `/scm/inventory` 와 운영 개요 SCM 카드가 **WMS 재고 이벤트를 투영한 결과**로 채워진다 — 또는, 데모에서 그것이 불가능하거나 비용이 크다는 측정이 나오면 소유자가 데모 처분(예: 빈 화면의 안내 문구, 시드로 채우기)을 고른다.

# Scope

## In Scope

- **AC-0 측정**(아래) — 왜 이벤트가 투영에 안 닿는가.
- 측정이 가리킨 한 곳의 수리(생산자 · 릴레이 · 소비자 중 하나) + 그 경로를 지키는 시험/가드.
- 데모 판정(재굽기 뒤 창).

## Out of Scope

- 투영 규칙 자체의 변경(받은 이벤트를 어떻게 합산하는가 — 소비자 계약 그대로).
- 시드로 `inventory_visibility` 행을 직접 넣는 것 — **소유자 결정이 그 길을 고를 때만**(AC-2 의 갈래 ⓑ). 기본은 경로 수리다.

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act — 측정 먼저, 코드 0줄)** — 데모 창에서 다음을 순서대로 재고 각 칸을 값·시각(UTC)·명령과 함께 이 파일에 적는다. 첫 번째로 «없다» 가 나오는 칸이 원인 후보다 — 그 뒤 칸은 재지 않아도 된다(대신 «안 잼» 으로 적는다).
  1. **생산**: wms 가 재고 이벤트를 냈는가 — wms inventory-service 아웃박스 테이블의 `wms.inventory.received.v1`/`adjusted.v1` 행 수와 `published` 상태, 그리고 `wms-kafka` 의 그 토픽 오프셋(end offset > 0). 🔴 WMS 카드 «재고 행 수 1» 은 **재고가 있다**는 뜻이지 **이벤트가 나갔다**는 뜻이 아니다.
  2. **릴레이**: MM2 가 `wms->scm` 으로 그 토픽을 복제했는가(`mm2.properties:100-101` 의 화이트리스트에 received·adjusted·transferred 가 있다) — `scm-kafka` 의 복제 토픽 이름(`:53` `IdentityReplicationPolicy` ⇒ 원본 이름 그대로여야 한다)과 end offset, MM2 컨테이너 로그. `infra/demo/relay/probe-relay.sh` 가 이미 있다(`:109` 가 `wms.inventory.adjusted.v1` 을 `scm` 으로 탐침) — 먼저 그것을 돌린다.
  3. **소비**: scm inventory-visibility 의 그룹 `scm-inventory-visibility-v1` 이 그 토픽을 구독하고 있는가(컨슈머 그룹 lag · 할당) · 처리 실패 로그(`Failed to process wms.inventory.*`) · DLT 존재 여부.
  4. **투영/조회**: 행은 있는데 조회가 안 보이는가 — `inventory_visibility` 스냅샷 테이블 행 수와 그 `tenant_id`, 그리고 콘솔이 묻는 테넌트(`demo-corp`)·화면 질의(`GET /api/v1/inventory-visibility/snapshot`)의 테넌트 일치.
  - 🔴 **0 행을 «이벤트 없음» 으로 읽지 않는다** — 각 칸은 «도구가 답을 못 함»(401 · 연결 실패 · 빈 출력)과 «0» 을 가르는 유효성 술어를 같이 적는다(예: 같은 명령으로 다른 토픽의 오프셋이 0 이 아님을 함께 읽는다).
- [ ] **AC-1** — AC-0 의 원인 칸에 맞는 수리를 **한 PR** 로: 그 경로를 지키는 시험(생산자면 아웃박스 발행 시험, 릴레이면 `scripts/check-cross-project-topic-relay.sh` 가 이미 지키는 화이트리스트와의 대조, 소비자면 테넌트/토픽 IT). 수리 전 빨강 → 수리 후 초록의 bite 를 적는다.
- [ ] **AC-2** — 데모 판정(재굽기 뒤 창): `/scm/inventory` 에 스냅샷 행이 ≥ 1 보이고, 운영 개요 SCM «스냅샷 행 수» ≥ 1, 그 값이 같은 창 WMS 의 재고와 모순되지 않는다(같은 SKU/창고가 보인다). 🔴 AC-0 이 «데모에서는 근본적으로 안 된다» 를 보이면 이 AC 는 소유자 결정으로 대체한다 — ⓐ 빈 화면 안내 문구만 고친다 · ⓑ 시드로 채운다(시드 머리 주석의 원칙을 어기는 결정이므로 그 이유를 기록) · ⓒ 그대로 둔다.

# Related Specs

- `projects/scm-platform/specs/services/inventory-visibility-service/`(투영 · 소비자)
- `projects/wms-platform/specs/services/inventory-service/`(재고 이벤트 · 아웃박스)
- `docs/adr/` 프로젝트 간 릴레이 결정(`infra/demo/relay/mm2.properties` 머리 주석이 가리키는 것)
- `infra/demo/seed/seed-scm.sh` § 4 · `infra/demo/seed/seed-wms.sh`

# Related Contracts

- `projects/wms-platform/specs/contracts/events/inventory-events.md` — `wms.inventory.received.v1` · `wms.inventory.adjusted.v1` · `wms.inventory.transferred.v1`
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` — `GET /api/v1/inventory-visibility/snapshot`

# Edge Cases

- **시드 순서** — scm 시드(§ 4)가 wms 시드의 입고보다 먼저 돌면 그 순간 0 은 정상이다. 그러나 19·20차 창은 시드 뒤 수십 분이 지나서도 0 이었다 ⇒ 순서만으로는 설명이 안 된다(AC-0 이 판정).
- **재굽기/신선 볼륨** — 이벤트는 기동 후 시드 때만 난다. 볼륨을 재사용하면 이미 소비된 오프셋 뒤로 새 이벤트가 없을 수 있다(«이벤트가 안 온다» 와 «이미 다 받았는데 행이 안 남았다» 를 구별).
- **릴레이 이름 정책** — 지금 `mm2.properties:53` 은 `IdentityReplicationPolicy`(복제 토픽 = 원본 이름)다. MM2 의 기본 정책은 원본 클러스터 접두를 붙이고, 소비자는 원본 이름(`wms.inventory.adjusted.v1`)을 구독한다 — 정책이 바뀌면 조용히 0 이 된다. AC-0 ② 에서 실제 복제 토픽 이름을 읽어 확인한다.
- **테넌트** — wms 이벤트의 테넌트와 콘솔이 묻는 `demo-corp` 가 다르면 행은 있어도 화면은 빈다.

# Failure Scenarios

1. **시드로 행을 넣고 «고쳤다»** — 화면은 채워지지만 투영은 여전히 고장이다(시드 머리 주석이 금지한 바로 그것). AC-2 ⓑ 는 소유자 결정으로만.
2. **도구 실패를 0 으로 읽기** — `docker exec … kafka-consumer-groups` 가 연결 실패로 빈 출력을 낸 것을 «lag 0» 으로 적는다(AC-0 의 유효성 술어가 막는다).
3. **재굽기 없이 판정** — 수리는 AMI 재굽기 뒤에만 데모에 있다. 옛 AMI 로 띄워 «안 고쳐졌다» 고 읽지 않는다(구운 커밋이 수리 머지의 자손인지 먼저 확인).
