# Task ID

TASK-MONO-760

# Title

데모의 SCM **재고 가시성 투영이 비어 있다** — `/scm/inventory` «표시할 스냅샷이 없습니다» · 운영 개요 SCM «스냅샷 행 수 0» (19·20차 창). WMS 재고 이벤트가 투영에 닿지 않는 이유를 먼저 재고, 닿게 하거나 데모 처분을 정한다

# Status

in-progress

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
> 🔵 **소유자 결정(2026-10-04 UTC): 착수.** ⏳ 수리 방향은 § 정적 분석 · 결정 갈래 — 소유자 결정 대기. (원문: 소유자 우선순위 결정 대기 — 착수 순서는 소유자가 정한다.) 🔴 백엔드·compose·릴레이 변경은 **AMI 재굽기** 뒤에야 데모에 실린다(소유자 승인).

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

---

# 정적 분석 (2026-10-04 UTC · `1427ef425` · 코드 0줄)

> 소유자 결정(2026-10-04 UTC): 새 티켓 3건 중 두 번째로 착수 · «창 전에 코드로 원인 가설을 좁힌다». 분석=Opus 5.5.
> 🔴 이 절은 **AC-0 을 닫지 않는다.** AC-0 의 동사는 «데모 창에서 잰다» 다. 이 절은 다음 창에서 무엇을 먼저 잴지를 정한다.

## 결론 — 행이 있어도 화면은 0 이 되게 되어 있다

| 쪽 | 테넌트 | file:line |
|---|---|---|
| **쓰기** — wms 이벤트 소비자 셋 | 상수 `TENANT_ID = "scm"` | `projects/scm-platform/apps/inventory-visibility-service/src/main/java/.../adapter/inbound/messaging/WmsInventoryReceivedConsumer.java:32` · `WmsInventoryAdjustedConsumer.java:27` · `WmsInventoryTransferredConsumer.java:28` |
| 쓰기 — 노드 조회·자동 등록 | 위 테넌트로 `findByTenantIdAndExternalId` → 없으면 `autoRegisterWmsWarehouse` | `.../application/service/InventoryVisibilityApplicationService.java:520-533` |
| **읽기** — `/snapshot` · `/sku/{sku}` · `/nodes` · staleness | 토큰의 `tenant_id` 클레임(없을 때만 `"scm"`) | `.../adapter/inbound/web/TenantClaimExtractor.java:34-37` · `InventoryVisibilityController.java:62,101,144` · `NodeStalenessController.java:30` |
| 읽기 — 질의 | `WHERE s.tenantId = :tenantId` | `.../adapter/outbound/persistence/jpa/InventorySnapshotJpaRepository.java:27` |
| 콘솔 운영 개요 SCM 레그 | 운영자 토큰(`demo-corp`)으로 `GET /api/v1/inventory-visibility/snapshot` | `projects/platform-console/apps/console-web/src/shared/composition/console-composition.ts:370-374` |

권한 게이트는 entitlement dual-accept 이므로, `demo-corp` 토큰(`entitled_domains ∋ scm`)도 **200** 으로 통과한다. 그 뒤 행 범위는 `demo-corp` 가 된다. 투영이 끝까지 정상이어도 행은 `scm` 아래에 있으므로 응답은 빈 목록이다. 그래서 «200 인데 0 건»이 된다(`seed-scm.sh:369-384` 가 적은 바로 그 모양).

## 🔴 스펙 자신이 두 문장으로 갈라져 있다

- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md:251` — «All persisted rows belong to the `scm` tenant … there is only one tenant».
- 같은 파일 `:271-274` — «`TenantClaimExtractor` (which extracts `tenant_id` for row scoping, defaulting to `scm`) … is unchanged».
- `:276` — «Consumed `wms-platform` events … are projected into the single `scm` tenant scope».

토큰 테넌트가 `scm` 이던 시절에는 두 문장이 같은 말이었다. ADR-MONO-020(운영자가 고른 고객 테넌트로 토큰을 다시 발급하는 active-tenant 범위)과 ADR-MONO-019 entitlement 이후로는 **둘이 갈라진다**. 🔴 그리고 토큰 범위 쪽이 **의도된** 설계라는 근거가 저장소에 있다. `TASK-MONO-171`(done)은 globex 가 테넌트를 고른 토큰이 `tenant_id='globex-corp'` 행을 읽는 것을 전제로 fed-e2e 픽스처(`tests/federation-hardening-e2e/fixtures/seed-scm-inv.sql`)를 고쳤고, `tenant-switch-rescope.spec.ts:139-143` 이 그 행에 기댄다.

## 홉별 정적 판정 (위 원인과 별개로, 이벤트가 닿는가)

> 이 표는 위임한 분석 에이전트가 읽은 file:line 이다. 위 «결론» 표는 내가 직접 열어 확인했다.

| 홉 | 판정 | 근거 |
|---|---|---|
| 생산 — 시드가 이벤트를 내는 경로를 타나 | 일치 | `infra/demo/seed/seed-wms.sh:167-211` 가 ASN → 검수 → 적치 확정을 **API로** 진행(SQL 직접 삽입 없음). 적치가 `ReceiveStockService.receive()` → 같은 트랜잭션 `outboxWriter.write(InventoryReceivedEvent)`(`:85-135`)로 이어진다. 시드는 adjusted/transferred 를 만들지 않으므로 received 만 기대한다(추론) |
| 생산 — 퍼블리셔 | 일치 | `OutboxPublisher` `@Profile("!standalone")` · 데모 env 에 `standalone` 없음 · 토픽 `wms.inventory.received.v1` |
| 봉투 형식 | 일치 | 생산 `InventoryEventEnvelopeSerializer.java:47-56,101-119` ↔ 소비 `WmsInventoryReceivedConsumer.java:59-75` |
| 릴레이 — 화이트리스트·이름 정책·주소 | 일치 | `infra/demo/relay/mm2.properties:49-53,100-101`(`IdentityReplicationPolicy`) |
| 🔴 릴레이 — **기동** | **창 의존** | `demo-up.sh:363-381` — 한 번의 호출에 iam·ecommerce·wms·scm 넷이 **모두** 있어야 릴레이를 띄운다. 도메인 단위 기동(`domain_start` → `demo-boot.sh scm`)은 띄우지 않는다. `demo-down.sh:72-82` 는 넷 중 하나만 내려도 릴레이를 내린다 ⇒ 20차 창의 scm 내림·올림(15:28–15:38Z) 뒤로는 릴레이가 없었을 가능성이 있다(코드에서 추론, 관측 아님). 릴레이 healthcheck 는 `iam-kafka` 만 본다(`docker-compose.relay.yml:51`) |
| 소비 — 토픽·그룹·오프셋 | 일치 | 그룹 `scm-inventory-visibility-v1` · `auto-offset-reset: earliest`(`application.yml:39` · `KafkaConsumerConfig.java:34`) ⇒ 늦게 붙어도 놓치지 않는다 |

## 곁에서 본 잠재 결함 (관측 아님)

`ScmThirdPartyInboundExpectedConsumer.java:46,92` 도 `TENANT_ID = "scm"` 으로 3PL 노드를 찾는다. 그런데 3PL 노드 등록 API 는 토큰 테넌트로 쓴다(`NodeRegistrationController.java:57,83`). 그러면 `demo-corp` 운영자가 등록한 3PL 노드를 이 소비자가 못 찾을 것이다(추론 — 소비자→서비스의 조회 술어는 아직 안 읽었다). 🔵 데모 시드는 3PL 노드를 등록하지 않으므로(`seed-scm.sh` 에 해당 호출 0건) 이 결함은 데모에서 관측되지 않았다. **같은 결정이 이것도 정한다.**

## 결정 갈래 (⏳ 소유자)

- **ⓐ 읽기를 `scm` 고정** — `TenantClaimExtractor` 를 상수로 바꾼다. `:251` 과는 맞지만 `:271-274` 와 ADR-MONO-020 의 active-tenant 범위를 이 서비스에서만 되돌린다. fed-e2e globex 행(171)의 전제도 깨진다 ⇒ **ADR 개정이 선행**한다. 비추천.
- **ⓑ 투영 테넌트를 설정값으로** — 소비자 넷의 상수를 설정 하나(예: `scm.inventory-visibility.projection-tenant-id`, 기본 `scm`)로 바꾸고, 데모 오버라이드가 `demo-corp` 를 넣는다. 선례는 `infra/demo/scm-identity.override.yml:35-46`(`ACK_TENANT_ID=demo-corp` — «프로젝트 기본값 `scm` 을 그대로 두면 …»). 스펙 `:276` 을 «설정된 투영 테넌트(기본 `scm`)»로 고친다(스펙 먼저). 기본값이 그대로라 데모 밖 동작은 바뀌지 않는다. scm 프로젝트 하나 + 데모 오버라이드 1줄로 끝난다. **추천**(구현 권장=Sonnet 으로 충분 — 상수 넷 → 설정 하나 + 테넌트 IT).
- **ⓒ 둘 다 아니고 데모 처분** — AC-2 의 ⓐ 안내문 / ⓑ 시드 / ⓒ 그대로.

## 다음 창 측정 (어느 갈래든 먼저)

1. `select tenant_id, count(*) from inventory_snapshots group by 1;` (scm inventory-visibility DB · 🔴 테이블명은 티켓 본문의 `inventory_visibility` 가 아니라 **`inventory_snapshots`**). 유효성 술어: 같은 세션에서 `select count(*) from inventory_nodes;` 가 답하는지.
   - `scm` ≥ 1 · `demo-corp` = 0 ⇒ 테넌트 불일치 **확정**(이벤트 경로는 정상).
   - 둘 다 0 ⇒ 경로 쪽에도 결함이 있다 → 2 로.
2. `docker ps -a --filter name=demo-event-relay` · demo-up 로그의 릴레이 생략 문구 · `bash infra/demo/relay/probe-relay.sh`. 🔴 `probe-relay.sh:109` 는 봉투가 아닌 `{"probe":...}` 를 `wms.inventory.adjusted.v1` 에 넣는다 ⇒ scm 쪽 adjusted **DLT 증가는 탐침 탓**일 수 있다(결함으로 읽지 마라 — 추론).
3. (2 가 정상인데 1 이 둘 다 0 일 때만) wms `inventory_outbox` 의 `event_type='inventory.received'` 행 수·`published_at`, `wms-kafka` 의 `wms.inventory.received.v1` end offset(대조로 `wms.inbound.putaway.completed.v1` 도 함께 — 도구 무응답과 0 을 가른다), 그룹 `scm-inventory-visibility-v1` 의 `--describe`.

---

# 소유자 결정 · 구현 (2026-10-04 UTC)

> 🔵 **소유자 결정: ⓑ 투영 테넌트를 설정값으로.** 분석=Opus 5.5 / 구현=Opus 5.5.
> 🔴 **AC-0 순서와 다르게 갔다**: AC-0 은 «창에서 먼저 재고 코드 0줄»을 요구했다. 그런데 정적 분석이 «경로가 다 돌아도 0» 인 결함을 이미 보였고, 소유자가 창 전에 수리를 골랐다. 그래서 AC-0 의 창 측정은 **수리 뒤 판정 창**으로 옮긴다. 그 창에서 잴 것은 위 «다음 창 측정»이고, 1 의 기대값만 바뀐다(아래).

## 무엇을 바꿨나

| 층 | 변경 |
|---|---|
| 스펙(먼저) | `inventory-visibility-service/architecture.md` § Multi-tenancy — `:251` 에 정정 문장(ADR-019/020 이후 행은 토큰 테넌트로 읽힌다) · `:276` 을 «투영 테넌트(기본 `scm`)»로 · **Projection tenant** 문단 신설. `data-model.md` § tenant_id Policy — 쓰기 경로별 테넌트(이벤트 → 투영 테넌트, API → 토큰 테넌트). 계약 `contracts/events/inventory-visibility-subscriptions.md` — 3PL 소비자는 `payload.tenantId` 를 **읽지 않고** 투영 테넌트로 비교한다(코드가 원래 그랬다 — 계약 문장이 «읽는 필드» 목록에 넣어 둔 것을 바로잡음) |
| 코드 | `config/ProjectionTenant` 빈 신설 — `inventory-visibility.projection-tenant-id`(env `INVENTORY_VISIBILITY_PROJECTION_TENANT_ID`, 기본 `scm`), 빈 값이면 기동 실패, 앞뒤 공백 제거. `"scm"` 상수를 쓰던 소비자 넷(`WmsInventory{Received,Adjusted,Transferred}Consumer` · `ScmThirdPartyInboundExpectedConsumer`)이 이 빈을 쓴다. `StalenessDetectionScheduler` 도 같은 빈을 쓴다 — 🔴 예전에는 `scmplatform.oauth2.required-tenant-id` 를 빌려 썼고, 소비자 상수와는 **우연히** 같았다. 투영 테넌트만 바꾸면 배치가 새 노드를 안 훑어 신선도가 영원히 비게 되는 길이었다 |
| `application.yml` | `inventory-visibility.projection-tenant-id: ${INVENTORY_VISIBILITY_PROJECTION_TENANT_ID:scm}` |
| 데모 | `infra/demo/scm-identity.override.yml` — `inventory-visibility-service` 에 `INVENTORY_VISIBILITY_PROJECTION_TENANT_ID=demo-corp`(같은 파일 `supplier-mock` 의 `ACK_TENANT_ID` 와 같은 이유 · `projects.sh:57` 이 scm 묶음에 이 파일을 건다) |

데모 밖(기본값 `scm`)의 동작은 바이트 단위로 같다. fed-e2e 의 globex 행(`TASK-MONO-171`)은 API/픽스처 행이라 무관하다.

## 시험

- `adapter/inbound/messaging/ProjectionTenantConsumersTest`(단위 6칸): 소비자 넷이 각각 서비스에 **설정된 테넌트**를 넘기는지, 배치가 그 테넌트를 훑는지, 빈 값은 기동 실패·공백은 제거되는지. 🔴 시험의 테넌트는 일부러 `scm` 이 **아니다**(`demo-corp`) — 기본값으로 재면 상수가 남아 있어도 통과한다.
- `integration/ProjectionTenantIntegrationTest`(IT, `@Tag("integration")`): `projection-tenant-id=demo-corp` 로 띄운 컨텍스트에 실제 Kafka 로 received 를 넣는다 → 노드가 `demo-corp` 아래에 생기고, `getCrossNodeSnapshot("demo-corp")` 가 그 SKU 를 돌려준다. 대조군: `scm` 아래에는 노드가 없고 `getCrossNodeSnapshot("scm")` 은 그 SKU 를 못 본다. ⚪ 이 호스트에는 Docker 가 없어서(`docker info` → npipe 없음) **CI 통합 레인에서만 돈다**.
- 로컬(worktree `mlab-mono760`): `./gradlew :projects:scm-platform:apps:inventory-visibility-service:test` rc=0 · BUILD SUCCESSFUL. 새 단위 시험 6/6, 기존 시험 무수정.
  - 첫 실행은 새 시험 3칸이 빨강이었다. 원인은 시험 픽스처였다: `ObjectMapper.findAndRegisterModules()` 가 클래스패스의 jackson-module-scala 를 올려 페이로드가 Scala 컬렉션이 됐다. `JavaTimeModule` 만 명시 등록하도록 고쳤다. 운영 코드는 Spring 의 ObjectMapper 라 해당 없다.
- **bite**: `WmsInventoryReceivedConsumer` 의 인자만 `"scm"` 리터럴로 되돌리면 → **received 칸 하나만 빨강**(6 중 1 실패, rc=1). 원본은 scratchpad 백업으로 복원했고 `cmp` 일치를 확인했다.

## 재굽기 뒤 판정 창에서 (AC-0 이관 · AC-2)

- 🔴 이 수리는 **AMI 재굽기 뒤에만** 데모에 있다(백엔드 + compose 오버라이드). 판정 전에 구운 커밋이 이 PR 머지의 자손인지 먼저 확인한다(실패 시나리오 3).
- 🔴 **볼륨**: 수리 전에 `scm` 아래로 쌓인 행은 옮겨지지 않는다(스펙 Projection tenant 문단). `terraform apply` 의 인스턴스 교체가 신선 볼륨을 사므로, 재굽기 창에서는 시드가 다시 낸 이벤트가 `demo-corp` 로 쌓인다.
- 측정 1 의 기대값: **`demo-corp` ≥ 1**(이 수리가 동작) · `scm` 은 0 이거나 수리 전 잔여. `demo-corp` = 0 이면 테넌트 말고 경로에도 결함이 있다 → 측정 2(릴레이 기동)로.
- AC-2 의 화면 판정(`/scm/inventory` ≥ 1 행 · 운영 개요 SCM 스냅샷 수 ≥ 1 · WMS 재고와 같은 SKU/창고)은 그대로다.
