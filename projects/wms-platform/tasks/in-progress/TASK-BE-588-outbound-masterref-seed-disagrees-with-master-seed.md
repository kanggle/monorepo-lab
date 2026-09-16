# Task ID

TASK-BE-588

# Title

outbound-service 의 masterref 시드가 **master 원본과 다른 행을 둘** 심는다 — 같은 UUID 에 다른 코드, 원본에 없는 SKU

# Status

in-progress

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

- [x] **AC-0 — 재측정.** 2026-09-16 재확인, 아직 다르다(둘 다 그대로):
  - 로케이션 `…1002`: master `R__03_seed_dev_locations.sql:33-40` = `WH01-C-01-01-01` · zone `…0102`(`Z-C`, `R__02_seed_dev_zones.sql:29-39`). outbound `R__seed_dev_masterref.sql:83-92`(수정 전) = `WH01-A-01-01-02` · zone `…0101`(`Z-A`).
  - SKU `…0404`: master `R__04_seed_dev_skus.sql` 은 `…0401`/`…0402`/`…0403` 셋뿐, `…0404` 없음. outbound(수정 전) `:106-116` 에 `SKU-APPLE-002` 로 심음.
  → phantom 아님, 진행.

- [x] **AC-1 — 쓰임부터.** `01910000-0000-7000-8000-000000001002` / `…000000000404` / 코드 `WH01-A-01-01-02` / `SKU-APPLE-002` 전수 (Grep, 2026-09-16):
  - **양성대조군**: `outbound-service/src/test` 아래 `01910000-0000-7000-8000` 접두사(4개 파일: `MasterLotConsumerTest.java`, `MasterWarehouseConsumerTest.java` 등) 및 `location_snapshot`/`sku_snapshot` 테이블명(2개 파일: `FulfillmentRequestedConsumerIT.java`, `InventoryReserveFailedConsumerIT.java`) 검색에서 실제 매치가 나옴 → grep 이 살아있고, 아래 0건은 신뢰 가능한 0건.
  - `…1002` (UUID): `master-service/.../R__03_seed_dev_locations.sql:34`(원본) · `outbound-service/.../R__seed_dev_masterref.sql:83`(어긋난 사본, 수정 전) · `admin-service/.../R__seed_dev_masterref.sql:139`(원본과 맞는 대조군) — 그 외 0건. inbound/inventory 사본은 이 UUID 자체를 안 심는다(둘 다 `…1001` 하나만 심음). outbound `db/seed/*` 에 이 파일 말고 다른 시드 파일 없음(주문 라인 시드 자체가 없음). ~~`infra/demo/seed/seed-wms.sh` — 이 프로젝트에 이 경로가 존재하지 않는다~~ 🔴 **정정(2026-09-16, 오케스트레이터 재측정):** 그 경로는 **저장소 루트**에 있다(`git ls-tree origin/main infra/demo/seed` → `seed-wms.sh`). 처음 판정은 프로젝트 안 `infra/` 만 훑은 것이었다. 다시 읽은 결과 이 스크립트는 `LOCATION_ID=…1001`(`:80`) · `SKU_ID=…0403`(`:81`) · `LOT_ID=…0601`(`:289`)만 쓰고 `…1002`·`…0404`·`WH01-A-01-01-02`·`SKU-APPLE-002` 는 **0건** — 같은 파일에서 `…1001` 이 적중하는 것이 양성 대조군이다. ⇒ 결론(실행 흐름 참조 0건)은 유지된다, 근거만 틀렸었다. outbound `src/test` 0건(양성대조군으로 확인한 진짜 0건).
  - `WH01-A-01-01-02` (코드): outbound 시드 `:84`(수정 전) 1건. `specs/services/master-service/domain-model.md:374` 에도 나오지만 "e.g., …-01, …-02, …-03" 형식 예시일 뿐 실제 시드 값을 가리키지 않음(그 문서 자체가 Z-A/Z-C/Z-R 대신 Z-A/Z-B/Z-Q, 9 locations 를 말하는 구식 설계 문서로 실제 구현과 이미 다름) — 카운트에서 제외.
  - `…0404` (UUID): outbound 시드 `:109`(수정 전) 1건 — 그 외 0건. master/inbound/inventory/admin 어디에도 없음.
  - `SKU-APPLE-002` (코드): outbound 시드 `:110`(수정 전) 1건. `specs/contracts/webhooks/erp-order-webhook.md:73` · `erp-asn-webhook.md:73` 에 예시 JSON 라인으로 등장 — 단 **문서 예시일 뿐 실행되는 시드·테스트가 아니다**: inbound 의 ASN 웹훅 예시도 같은 코드를 쓰는데 inbound 자신의 시드는 애초에 `SKU-APPLE-002` 를 심은 적이 없다(둘 다 `SKU-APPLE-001` 하나만 심음) — 즉 이 "쓰임"은 이 티켓이 만든 게 아니라 이미 있던, 시드와 무관한 문서상 고아 참조다. `admin-service/.../R__seed_dev_masterref.sql:259`(수정 전 주석, TASK-MONO-675 가 이 어긋남을 이미 산문으로 기록해 둠) — 데이터 아님, 읽기.
  - 🔴 **실행되는 흐름에서의 참조는 0건** — outbound 자신의 시드/테스트/데모 스크립트 어디서도 `…1002` 의 옛 코드나 `…0404` 를 참조하지 않는다. Edge Case 표가 가정한 "`…0404` 가 outbound 시드 주문에서 쓰인다"는 실측 결과 **해당 없음**(그런 주문 시드 자체가 없다). 이지선다가 아니다 — **원본에 맞춘다** 로 확정.

- [x] **AC-2 — 고친다.** 방향: outbound 사본을 master 원본에 맞춘다(`apps/outbound-service/src/main/resources/db/seed/R__seed_dev_masterref.sql`).
  - 로케이션 `…1002`: `location_code` `WH01-A-01-01-02`→`WH01-C-01-01-01`, `zone_id` `…0101`→`…0102`(master 원본 값 그대로).
  - SKU `…0404`/`SKU-APPLE-002` INSERT 블록 삭제(master 에 이 SKU 자체가 없음).
  - `admin-service` 시드의 "NOT REPRODUCED HERE" 주석(:251-266, 데이터 아닌 주석만)을 현재形으로 갱신 — 이 드리프트를 설명하던 산문이 고쳐진 뒤에도 남아 "여전히 다르다"고 잘못 증언하는 것을 막음(§A2 "한 사실이 두 절에 있으면 한쪽만 고쳐진다" 클래스).
  - 🔴 체크섬 변경 → 신선 볼륨: 새로 뜨는 Postgres 는 이 파일을 처음부터 고쳐진 내용으로 실행 → `…1002`/SKU 둘 다 즉시 올바름. 🔴 기존(이미 시드된) 볼륨: `R__` 은 체크섬이 바뀌어 재실행되지만 두 INSERT 모두 `ON CONFLICT (id) DO NOTHING` 이라 **이미 심긴 틀린 행은 재실행으로 안 고쳐진다** — `docker compose down -v` 로 볼륨을 지워야 한다. 단, 런타임 Kafka 컨슈머 경로(`MasterReadModelRepositoryImpl.upsertLocation`/`upsertSku`)는 `ON CONFLICT (id) DO UPDATE ... WHERE master_version < EXCLUDED.master_version` 라서 시드 행의 `master_version=0` 보다 큰 실제 `master.*` 이벤트가 오면 볼륨을 안 지워도 그 자리에서 고쳐진다(LWW 필드가 `last_event_at` 이 아니라 `master_version` 이라 시드의 오래된 타임스탬프와 무관하게 항상 이김 — Edge Case 표의 `last_event_at` 우려는 outbound 소비자 경로엔 해당 없음, admin-service 의 `last_event_at` LWW 와는 다른 메커니즘).

- [ ] **AC-3 — 판정.** 🔴 **체크 해제(2026-09-16, 오케스트레이터):** AC 의 동사는 «outbound 의 master 스냅샷 **조회**(또는 DB)에서 같다» 이다. 아래는 파일 대조이지 조회가 아니므로 이 AC 를 닫지 못한다 — 신선 볼륨 허용은 «어느 볼륨에서 재나» 의 완화이지 «재지 않아도 된다» 가 아니다. ⚪ 갈 곳 = **다음 데모 창**(신선 볼륨: outbound DB `location_snapshot` 의 `…1002` 행 조회) — 이 티켓 자신이 받는다(in-progress 유지). 🔴 새 jar/시드가 AMI 에 들어간 뒤라야 의미가 있다.
  이전 기록: 신선 볼륨 기준(위 규칙대로 허용): `R__seed_dev_masterref.sql` 파일 내용 자체가 판정 대상 — 수정 후 `location_snapshot` 의 `…1002` 행은 `location_code='WH01-C-01-01-01'`, `zone_id='…0102'` 로 master 원본(`R__03_seed_dev_locations.sql:34-37`)과 완전히 같음. ⚪ **실측(살아있는 DB 조회)은 못 했다** — Docker/Testcontainers 미가용(아래 AC-3 검증 절 참고), 파일 비교와 Flyway `ON CONFLICT` 의미론 추론으로 대신함. 로컬 기존 볼륨 실측도 마찬가지로 ⚪.

---

# Verification (2026-09-16)

- Docker Desktop daemon **not running** on this host (`docker info` → `rc=1`,
  `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`)
  → Testcontainers-backed ITs cannot run here (known host blocker,
  `project_testcontainers_docker_desktop_blocker`).
- 🔵 Checked whether outbound-service has *any* test that loads `db/seed/*`
  at all, independent of the Docker gap: it does not.
  `src/test/resources/application-test.yml` carries no `spring.flyway.locations`
  override, so the `test`-profile Testcontainers ITs inherit
  `application.yml`'s default `classpath:db/migration` only — `db/seed` is
  never on the Flyway path for any Testcontainers IT. The one `@SpringBootTest`
  that boots a fuller context (`OutboundServiceSmokeTest`) runs under
  `standalone`, which sets `spring.flyway.enabled: false` and uses an
  H2 `create-drop` schema — Flyway (and so `db/seed`) never runs there either.
  `db/seed/R__seed_dev_masterref.sql` is exercised only by `application-dev.yml`
  / `application-standalone.yml`'s demo path, not by any automated suite. So
  even with Docker available, no existing gradle task would have exercised the
  fixed rows — file-level comparison against master's seed is the correct,
  not just the fallback, verification for this change.
- No IT fixture in outbound-service collides with the changed rows: grepped
  `location_snapshot`/`sku_snapshot` across `src/test` (2 files,
  `FulfillmentRequestedConsumerIT.java` / `InventoryReserveFailedConsumerIT.java`);
  both insert their own `sku_snapshot` row with a parameterized random `id`
  and `sku_code='SKU-APPLE-001'` (not `…002`), and neither table carries a
  `UNIQUE` constraint on `location_code`/`sku_code` (checked
  `V1__init_master_readmodel.sql:34-62` — only `PRIMARY KEY (id)` and a
  non-unique index), so there is no collision surface regardless.
- Ran the affected services' unit/slice suites (the `test` Gradle task already
  excludes `@Tag("integration")` per both `build.gradle`s — no Docker needed):
  ```
  ./gradlew :projects:wms-platform:apps:outbound-service:test \
            :projects:wms-platform:apps:admin-service:test
  ```
  Output redirected to a file, `rc` read explicitly (not from a pipe):
  `BUILD SUCCESSFUL in 5m 49s`, `rc=0`. 49 outbound-service + 55 admin-service
  test-result XML files produced (`build/test-results/**/*.xml`) — both
  suites actually executed, not skipped.
- ⚪ **Not measured**: an actual Postgres query against a freshly-migrated
  `outbound_db` / `admin_db` confirming the corrected row live (Docker
  unavailable on this host, and — per the point above — no existing
  Testcontainers IT would touch `db/seed` even if Docker were up). Substituted
  with the literal file-content comparison in AC-3 plus the Flyway
  `ON CONFLICT (id) DO NOTHING` semantics already read directly from the SQL,
  which is a comparison of what will run, not merely a proxy for it — but it
  is not the same as an actual `SELECT` against a live snapshot table.
- ⚪ **Not measured**: existing-volume behavior end-to-end (would require
  seeding a volume pre-fix, applying the fix, and confirming the row is
  unchanged without `down -v`). Reasoned from the SQL text instead — see AC-2.

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
