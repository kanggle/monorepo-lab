# TASK-MONO-675 — 비정규화한 코드가 전부 `null` 이다. 코드가 틀린 게 아니라 **먹일 데이터가 없다**

# Status

in-progress

**Type:** TASK-MONO (monorepo-level — wms 투영 + 데모 시드 경로)

**Analysis model:** Opus 5 / **구현 권장:** Opus 5 (원인이 코드가 아니라 파이프라인이다)

**선행:** `TASK-MONO-659`(비정규화) · `TASK-MONO-667`(이 결함이 그 티켓을 막고 있다)

---

# Goal

`TASK-MONO-667` 의 마지막 칸은 *"659 가 닫힌 뒤 wms·scm 을 재촬영해서 싣는다"* 였고,
`TASK-MONO-671` 이 그 게이트를 **「659 + AMI 재굽기」** 로 정정했다.

**2026-09-12 에 둘 다 충족시켰다** — 659 는 닫혔고, AMI 를 다시 구워
(`ami-058f6293d1408f91e`, `RepoCommit = 8cf474346`) `terraform apply` 로 인스턴스를 갈았다.

🔴 **그런데 화면은 여전히 못 쓴다.** 게이트는 **필요조건이었지 충분조건이 아니었다.**

---

# 🔴🔴 실측 — 필드는 **있는데** 값이 전부 `null`

인증 세션으로 콘솔이 부르는 그 경로에 직접 물었다(렌더된 HTML 이 아니라 **API 응답**):

```
GET /api/wms/inventory  → 200
{"content":[{"locationId":"01910000-…-1001","skuId":"01910000-…-0403","lotId":null,
  "warehouseId":"01910000-…-0001",
  "locationCode":null,"skuCode":null,"lotNo":null,"warehouseCode":null,
  "availableQty":85,"reservedQty":10,"onHandQty":95, …}]}
```

⇒ **`TASK-MONO-659` 의 DTO 변경은 배포됐다**(필드가 응답에 있다 = 재굽기가 코드를 배달했다).
🔴 **값이 null 이다.**

## 그 null 의 출처를 잡았다 — ref 테이블이 **전부 비어 있다**

`InventoryProjectionService` 는 코드를 **읽기 모델의 ref 테이블에서** 해석한다:

```java
String locationCode = locationRepo.findById(locationId)…
String skuCode      = skuRepo.findById(skuId).map(SkuRefEntity::getSkuCode).orElse(null);
String lotNo        = lotRepo.findById(lotId)…
warehouseCode       = warehouseRepo.findById(warehouseId)…
```

그 테이블들을 세었다 (`GET /api/wms/master/refs/<type>`, 같은 세션·같은 테넌트):

| ref | totalElements |
|---|---|
| warehouses | **0** |
| locations | **0** |
| skus | **0** |
| lots | **0** |
| partners | **0** |

🔵 **다섯 종 전부 0건이다.** 그래서 모든 `findById` 가 빈손이고 `orElse(null)` 이 발동한다.

## ⇒ 콘솔의 「이름 확인 불가」는 **옳은 동작**이다

콘솔 화면(`/wms/inventory`, demo-corp)은 위치·SKU 열에 **「이름 확인 불가」** 를 그린다.
🔵 그것은 `TASK-PC-FE-281` 이 «id 폴백을 걷어낸다» 로 만든 **의도된 표시**다 — raw UUID 를
보여 주는 것보다 낫다. 🔴 **콘솔은 결백하다. 고칠 곳은 생산자 쪽 파이프라인이다.**

## 🔵 이 창이 아니었으면 못 봤다

`659`의 유닛·IT 는 전부 초록이다 — **픽스처가 ref 행을 넣어 주기 때문**이다.
비어 있는 ref 테이블은 **실제 시드가 도는 환경에서만** 나타난다.

---

# Scope

**먼저 «왜 비었는가» 를 재는 티켓이다.** 원인을 모른 채 고치지 마라.

후보(각각 다른 처방이다):

| 가설 | 확인 방법 |
|---|---|
| ⓐ 시드가 master 이벤트를 **발행하지 않는다** | 시드 스크립트가 마스터를 만드는지 |
| ⓑ 발행하는데 admin-service 가 **구독을 안 한다** | `admin-service` 에 master-ref 소비자가 **없다**(실측: `inbound-service`·`inventory-service` 에만 있다) |
| ⓒ 구독하는데 **순서**가 어긋난다(재고 이벤트가 마스터보다 먼저) | 부팅 로그의 이벤트 순서 |
| ⓓ 컨슈머 그룹이 **과거 이벤트를 못 받는다**(신선 볼륨 + earliest 아님) | 오프셋 설정 |

🔴 **ⓑ 가 가장 유력하다** — 실측에서 `projects/wms-platform/apps/admin-service/` 아래
master-ref **소비자 클래스가 하나도 없다**(있는 것은 `MasterRefController` 뿐 = **읽기만**).
🔵 그러나 **이것도 가설이다.** admin-service 가 다른 이름으로 채울 수도 있다.

**Out of scope**: scm 조달의 공급사 UUID(기전이 다르다 — `TASK-MONO-677`. 🔴 처음엔 여기서 `676` 을
가리켰는데 676 은 그 칸을 다루지 않는다 — 2026-09-15 정정) ·
콘솔 표시 로직(결백하다) · 659 의 비정규화 자체(코드는 옳다).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [x] 🔴 ref 5종의 `totalElements` 를 **다시 세라**. 0 이 아니면 이 티켓은 **phantom** 이고,
      그때는 «언제 채워졌는가» 를 적고 닫아라.
- [x] 🔴 `/api/wms/inventory` 의 `*Code` 필드가 여전히 `null` 인지 확인하라.
      🔵 ref 가 찼는데도 null 이면 **원인이 다른 것**이고 이 티켓의 진단부터 다시 세워야 한다.
- [ ] 🔴 **창이 필요하다.** 못 열면 아무것도 하지 말고 `ready/` 에 그대로 둬라 —
      「켜는 것」은 이 티켓의 범위가 아니다(`TASK-MONO-660` AC-0 과 같은 규율).
      → ⏳ (2026-09-15) 위 두 칸은 **런타임 값**이라 창 없이 못 닫는다 — 열어 두었다.
      저장소 쪽에서 잴 수 있는 것은 아래 AC-1 에 적었다.

## AC-1 — 원인을 **하나로** 지목한다

- [x] 🟢 ⓐ~ⓓ 중 **어느 것인지 실측으로 지목**하고, 나머지를 **왜 배제했는지** 적어라.
      → ⏳ **절반 닫혔다 (2026-09-15 저장소 재측정)** — 🔴 ⓑ 는 **관측으로 기각**, ⓐ 는 **코드로 지지**,
      런타임 확증(토픽 오프셋)이 남았다:

      | 가설 | 판정 | 근거 |
      |---|---|---|
      | ⓐ 시드가 master 이벤트를 발행 안 한다 | 🟠 **코드로 지지** | master-service 시드 `db/seed/R__01..R__05` 는 `INSERT INTO warehouses/zones/locations/skus/partners` **직삽입**이고 outbox 언급 **0건**. 이벤트는 `OutboxDomainEventAdapter` → `MasterOutboxPublisher` 로만 나간다 ⇒ **시드 행에는 이벤트가 없다**. `seed-wms.sh:17-23` 이 이유를 적어 뒀다: API 는 `MASTER_WRITE` 를 아무도 못 받아 403(`TASK-MONO-514`) |
      | ⓑ admin-service 가 구독 안 한다 | 🔴 **기각** | `admin-service/.../infra/messaging/MasterProjectionConsumer.java:39-46` `@KafkaListener` 가 `wms.master.{warehouse,zone,location,sku,partner,lot}.v1` **6개**를 구독하고, 쓰기는 `MasterProjectionService.java` 의 `*Repo.save` 6곳. 스펙도 요구한다(`specs/services/admin-service/architecture.md:214-219`) |
      | ⓒ 순서 어긋남 | ⚪ **해당 없음(ⓐ 가 참이면)** | 올 이벤트가 없으면 순서가 없다 |
      | ⓓ 과거 이벤트를 못 받는다 | 🔵 **약함** | admin `application.yml:47-48` `auto-offset-reset: earliest` |

      🔵 **형제가 왜 멀쩡한지가 ⓐ 의 대조군이다**: 같은 master 이벤트 소비자를 가진
      `inbound`·`inventory`·`outbound` 는 **각자 `db/seed/R__seed_dev_masterref.sql`** 로 ref 를 직접 심는다
      (inventory 파일 머리말: *"boots with an empty master read-model and waits for `master.*` consumer
      events … we pre-load"*). **admin-service 만 그 파일이 없다** — `db/seed/` 에는 `R__seed_dev_data.sql`
      (role·user·setting) 하나다. `infra/demo/wms-devseed.override.yml:141-143` 이 admin 에도
      `classpath:db/seed` 를 연다 ⇒ 파일만 있으면 데모가 먹는다.
      🔴 **남은 런타임 술어**(창에서): `wms.master.*.v1` 토픽의 오프셋이 **0** 인가 · admin 컨슈머 그룹 lag ·
      `*.DLT` 에 레코드가 있는가. 🔴 DLT 에 레코드가 있으면 ⓐ 가 아니라 **소비 실패**다 — 그래서 아직 ⓐ 로 확정하지 않는다.
- [ ] 🔴 «가장 그럴듯한 것» 으로 고르지 마라 — 이 저장소가 반복해서 댄 대가다.
      술어는 **관측**이어야 한다(소비자 클래스의 존재, 토픽 오프셋, 시드 로그).
- [x] 🔵 admin-service 에 소비자가 없다는 내 실측이 **맞는지부터** 다시 확인하라.
      → 🔴 **틀렸다.** 소비자는 있다(위 표 ⓑ). 🔵 틀린 이유: 처음 실측은 **`masterref` 패키지의
      `Master*Consumer` 이름**으로 셌고, admin 은 `infra/messaging/MasterProjectionConsumer` 라 그 모집단 밖이었다
      — «내 레코드의 이름은 그 코퍼스의 이름이 아니다». 🔵 스펙 쪽 이름도 또 다르다
      (`idempotency.md:173-178` 는 `MasterRefProjectionConsumer`).

## AC-2 — 고친다

- [x] 원인이 ⓑ 라면 **소비자를 더하는 것이 맞는지** 먼저 판단하라 — 🔴 형제 서비스
      (`inventory-service`)가 이미 같은 ref 를 들고 있다면 **admin 이 직접 구독할 일이
      아닐 수도 있다**(조회로 풀 수도 있다). 그 판단을 적어라.
      → 🔴 **ⓑ 는 기각됐다(AC-1) — 그래서 이 칸 자체가 해당 없다.** admin 은 이미
      `MasterProjectionConsumer` 로 6개 토픽을 구독하고 있고, 원인은 «구독이 없다» 가
      아니라 «시드가 이벤트를 안 낸다»(ⓐ) 다. **소비자를 더하는 판단도, 조회로 우회하는
      판단도 필요 없다** — 파이프라인은 옳고, 파이프라인에 먹일 데이터가 없었을 뿐이다.
      ⇒ 고친 것은 **admin-service 의 `db/seed/R__seed_dev_masterref.sql` 신설** 하나다
      (형제 셋 — inbound·inventory·outbound — 가 각자 이미 쓰고 있는 것과 같은 우회:
      master-service 의 seed 가 outbox 를 안 거치므로, 소비자 쪽에서 같은 고정 UUID 로
      직접 미리 채워 둔다). 6개 ref 타입 중 admin 이 실제로 투영하는 5종 테이블을
      채웠다(웨어하우스 1·존 3·로케이션 3·SKU 3·랏 1·파트너 3 — 총 14행): 웨어하우스·
      존·로케이션·SKU·파트너는 master-service `R__01..R__05` 원본을, 랏은 마스터에
      랏 시드가 없으므로 inventory-service(=inbound·outbound 와 동일) 의
      `R__seed_dev_masterref.sql` 을 출처로 그대로 옮겼다.
      🔴🔴 **드리프트 위험 — 이제 이 UUID 세트의 5번째 사본이다**(master 원본 +
      inbound·inventory·outbound 사본 3개 + 이 admin 사본). master-service 가 id 나
      코드를 바꾸면 이 파일이 **조용히** 낡는다. 🔴 이미 실제로 갈라져 있다는 것도
      찾았다 — 사본끼리도 서로 다르다: outbound 의 `R__seed_dev_masterref.sql` 은
      로케이션 `...1002` 를 `WH01-A-01-01-02`/존 `Z-A` 로 심는데 master 원본의
      `...1002` 는 `WH01-C-01-01-01`/존 `Z-C` 이고, outbound 는 master 에 없는
      SKU `...404 SKU-APPLE-002` 도 심는다 — 둘 다 master 원본과 안 맞는 **outbound
      자신의 복사 오류**로 보여 이 파일에는 옮기지 않았다(파일 꼬리 주석에 근거를
      남겼다). ⇒ 사본이 늘수록 이런 대조 없는 드리프트가 **더** 생기기 쉽다.
      🔵 **이 어긋남의 집 = `projects/wms-platform/tasks/ready/TASK-BE-588-outbound-masterref-seed-disagrees-with-master-seed.md`**
      (2026-09-15 같은 PR 에서 기안 — 받는 쪽에 행이 있는지 확인했다. 산문 «나중에» 로 남기지 않는다).
      🔴 **가드/테스트 검색 — 없다.** `scripts/` 와 `.github/workflows/ci.yml` 을
      `masterref`/`master_ref`/`MasterRef` 로 훑었고, 형제 masterref 시드끼리(또는
      master-service 시드와) 값을 대조하는 가드나 테스트는 **0건**이다. 방금 찾은
      outbound 의 자기모순도 이번에 손으로 대조하다 발견한 것이지, 어떤 자동화도
      잡아내지 못했다. 이 공백을 메우는 것은 이 티켓의 범위 밖이다(AC-4 가 가드
      여부를 별도로 판단한다).
- [x] **백필**: 이미 들어와 있는 스냅샷 행의 `*Code` 는 어떻게 되나. 🔴 새 행만 채우면
      **옛 행은 영원히 null 이고, 화면에는 그 옛 행이 보인다.**
      → **순서 주장(“신선 볼륨에서 Flyway 가 Kafka 리스너보다 먼저 돈다”)은 설정/코드로
      지지된다, 런타임 확증은 아직 없다**: Spring Boot 는 Flyway 마이그레이션을 빈
      초기화 단계에서(데이터소스가 준비되자마자, 컨텍스트 refresh 중) 실행하고,
      `@KafkaListener` 컨테이너는 `KafkaListenerEndpointRegistry` 가 `SmartLifecycle`
      의 `start()` 단계 — **싱글톤 빈이 전부 만들어지고 컨텍스트 refresh 가 끝난 뒤** —
      에 기동한다(admin `application.yml` 에 `spring.kafka.listener.auto-startup` 을
      끄는 오버라이드 없음, 기본값 `true` 확인). ⇒ **같은 컨테이너 안에서는** 신선
      볼륨이라면 R__ 시드 행이 커밋된 뒤에야 리스너가 첫 이벤트를 받을 수 있다 —
      구조적으로 순서가 보장된다. 🔴 그러나 이것은 **단일 프로세스 기동 순서**를 읽은
      것이고, 데모처럼 **여러 서비스·여러 컨테이너**가 동시에 뜨는 상황에서 admin 의
      리스너가 뜨기 전에 master-service 가 (시드 말고) **실제 쓰기 이벤트**를 이미
      냈다가 admin 이 놓치는 경우는 이 산술이 안 덮는다 — 그 경우의 술어는 컨슈머
      그룹 오프셋/lag 이고, AC-1 이 이미 «남은 런타임 술어» 로 열어 둔 항목과 같다.
      🔴 **옛 스냅샷 행(admin_inventory_snapshot · admin_asn_summary 등)에 대한 백필은
      추가하지 않았다** — 세 가지 근거: (1) **이 데모는 매 창마다 신선 볼륨이다**
      (Edge Cases 표 3행, AMI 재굽기 = 볼륨 소멸) — 다음 창은 시드부터 다시 돌므로
      이 티켓이 고친 뒤에는 애초에 «옛 null 행» 이 생길 수가 없다. (2) 오래 떠 있는
      **로컬 dev 볼륨**에 이미 null 코드로 박힌 스냅샷 행이 있을 수는 있지만, 그
      복구는 이미 이 저장소의 표준 처방(`docker compose down -v` 재기동, R__ 재적용)
      으로 충분하고 비용이 낮다. (3) `V4__denormalise_warehouse_code.sql` 과 달리
      이번 백필 대상(`admin_inventory_snapshot`·`admin_asn_summary` 의 *Code 컬럼)은
      **운영 환경에는 필요가 없다** — 운영 master-service 의 쓰기는 outbox 를 거쳐
      실제 이벤트를 내므로 운영 ref 테이블은 이미 정상적으로 채워진다(이 결함은
      «시드가 outbox 를 우회한다» 는 **비운영 전용** 경로다). 백필 마이그레이션은
      `db/migration` 에 있어야 버전 마이그레이션으로서 운영에도 적용되므로,
      운영에는 불필요한 조인을 매 배포마다 얹는 대가를 치른다. ⇒ **추가하지 않는다.**

## AC-3 — 판정은 **화면**이다

- [x] 🔴 API 응답에 코드가 실렸다는 것으로 닫지 마라. **콘솔 `/wms/inventory` 의 위치·SKU 열에
      「이름 확인 불가」가 사라졌는가**가 판정이다.
- [x] **대조군**: 고치기 전 화면(이 티켓이 첨부한 상태)과 나란히 둬라.
- [ ] 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

## AC-4 — 이 부류가 다시 조용히 지나가지 않게

- [x] 🔴 **유닛/IT 가 왜 못 잡았는지 적어라** — 픽스처가 ref 행을 넣기 때문이다.
      🔵 «픽스처가 현실을 안 담으면 초록도 공허하다» 가 이 저장소에 이미 있는 문장이고,
      이 건이 그 **새 사례**다.
      → **확인했다 — `InventoryProjectionServiceTest` 는 ref 리포지토리를 전부 Mockito
      `@Mock` 으로 갈아 끼운다.** `warehouseRepo.findById(...)` 를 테스트가 직접
      `Optional.of(new WarehouseRefEntity(...))` 로 «있음» 을, 또는
      `Optional.empty()` 로 «없음» 을 **손으로 주입**한다 — 실제 Postgres 도, 실제
      시드도, 실제 `MasterProjectionService` 도 이 테스트 경로에 없다. 🔵 이건 **틀린
      테스트가 아니다**: `inventorySnapshot_warehouseCodeIsNull_whenRefNotProjectedYet`
      은 오히려 «ref 가 없으면 null이어야 한다» 는 **옳은 동작을 정확히 고정**하고 있다
      (AC-2 의 설계 의도와 정확히 일치 — 빈 문자열/UUID 문자열로 채우지 않는다).
      🔴 **못 잡은 이유는 테스트가 틀려서가 아니라 층이 달라서다** — 이 스위트는
      「join 로직이 옳은가」만 재고, 「join 이 읽을 데이터가 실제로 거기 있는가」
      (= 시드가 실제 Postgres 에 행을 넣는가, `MasterProjectionConsumer` 가 실제
      Kafka 에서 그 값을 받는가)는 **어떤 테스트도 안 잰다**. Mockito 목이 «ref 행이
      있다» 는 세계를 매번 성립시켜 주므로, admin-service 의 진짜 ref 테이블이 5종
      전부 0건이어도 이 스위트는 처음부터 끝까지 초록이었다.
- [x] 가드를 만들지 말지 **판단하고 이유를 적어라**. 🔴 `scripts/` 에 파일을 더하면
      분모가 움직인다(전수 스윕 + 두 산문 집).
      → **판단: 만들지 않는다.** 이유 셋:
      (1) 이 결함이 잡히는 층은 정적 grep/스크립트가 아니라 **런타임 IT** 다 — 필요한
      술어는 «`db/seed` 위치를 연 프로파일로 Flyway 를 돌리면 `admin_*_ref` 5종이
      0건이 아니다» 이고, 이건 Testcontainers 로 실제 Postgres 를 띄워야 잴 수 있다
      (이 호스트엔 Docker 가 없어 이 세션에서 직접 만들 수도, 돌릴 수도 없다 — AC-5
      참고). 정적 스크립트로 흉내 내면 «시드 파일이 존재한다» 정도만 재는 가짜 가드가
      된다(파일은 있는데 내용이 틀려도 통과).
      (2) 정확히 이 모양의 세이프티넷이 이미 이 저장소에 있다 — `R__seed_dev_data.sql`
      머리말이 인용하는 `DevSeedScopeIT`(admin 자신의 시드 위치 전환을 검증한 IT)가
      같은 패턴이다. 재발 방지가 필요해지면 **그 옆에 `MasterRefDevSeedScopeIT` 류를
      추가하는 것이 맞는 자리**이지 `scripts/` 가 아니다 — 다만 이 세션은 Docker 가
      없어 그 IT 를 직접 쓰고 돌려 검증할 수 없으므로 **이번 창에서 만들지 않는다**
      (만들어도 로컬에서 못 돌려 본 가드는 «만들었다» 와 «검증했다» 를 섞는다).
      (3) `scripts/` 에 파일을 더하면 `check-ls-files-guard-count.sh` 가 읽는
      분모가 움직여 **관련 없어 보이는 다른 가드**(Guard-count figure)가 빨개질 수
      있다(`TASK-MONO-650` 실측 전례) — 이 티켓의 좁은 수정 하나를 위해 그 비용을
      치를 근거가 없다.
      ⇒ **후속 후보로만 남긴다**: 이 부류가 다시 조용히 지나가는 것을 막고 싶다면,
      다음에 Docker 가 있는 세션에서 `MasterRefDevSeedScopeIT` 를 admin-service 에
      추가하는 것을 권한다(새 티켓 기안은 이 창의 범위 밖).

---

# Related Specs / Contracts

- `projects/wms-platform/apps/admin-service/.../projection/InventoryProjectionService.java`
  (코드 해석 4줄: location · sku · lot · warehouse)
- `projects/wms-platform/apps/admin-service/.../api/dashboard/MasterRefController.java`
  (ref 타입 6종: warehouses · zones · locations · skus · lots · partners)
- `projects/wms-platform/apps/inventory-service/.../masterref/Master*Consumer.java` — **형제가 하는 방식**
- `TASK-MONO-659` — 비정규화를 넣은 티켓(코드는 옳다)
- `TASK-MONO-667` — 이 결함에 막혀 있는 티켓
- `TASK-PC-FE-281` — 「이름 확인 불가」 표시를 만든 티켓(결백하다)

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| ref 는 찼는데 스냅샷이 옛 행이다 | 🔴 백필이 없으면 화면은 그대로다. AC-2 둘째 칸 |
| 일부 ref 만 찬다(예: sku 만) | 🔵 화면은 **부분적으로** 고쳐진다 — 「이름 확인 불가」가 한 열에만 남는다. 그것도 실패다 |
| 신선 볼륨이라 과거 이벤트가 없다 | 🔴 이 데모는 **매 창마다 신선 볼륨**이다(인스턴스 교체 = 볼륨 소멸). 「한 번 채우면 된다」는 처방은 **여기서 안 통한다** |
| 로컬 compose 에서는 재현 안 된다 | 🔵 볼륨이 남아 있어서다. 판정 환경을 먼저 못박아라 |

# Failure Scenarios

1. **콘솔을 고친다** → 결백한 곳을 고치는 것이고, null 은 그대로 남는다.
2. **ref 를 손으로 한 번 채운다** → 다음 창에서 볼륨이 새로 생기면 **원위치**다.
3. **새 행만 채우고 닫는다** → 화면에 보이는 옛 행은 계속 「이름 확인 불가」다.
4. **유닛 테스트 초록으로 닫는다** → 픽스처가 ref 를 넣으므로 **고치기 전에도 초록**이다.

---

# 🔵 창 실측 — 2026-09-17 UTC · AMI `ami-0d30513151d07e163`(RepoCommit `b54296645`) · 창 08:50:37Z~10:08:16Z

- **AC-0 ①** `GET /api/wms/master/refs/<type>`(콘솔 세션, `demo-corp`): warehouses **1** · locations **3** · skus **3** · lots **1** · partners **3**. 🔵 phantom 이 **아니다** — 채운 것은 이 티켓의 admin-service masterref 시드다(행의 `lastEventAt=2026-04-18T00:00:00Z` · `version=0` = 시드 지문, 이벤트가 쓴 행이 아니다).
- **AC-0 ②** `GET /api/wms/inventory` → 1행 `locationCode=WH01-A-01-01-01` · `skuCode=SKU-APPLE-001` · `warehouseCode=WH01` · `lotNo=null`(그 재고 행의 `lotId` 자체가 null) — 더는 null 이 아니다.
- **AC-3** 콘솔 `/wms/inventory` 화면: 위치 칸 `WH01-A-01-01-01`, SKU 칸 `SKU-APPLE-001` (이미지를 열어 확인). **대조군** = 이 티켓 § 관측의 수정 전 API 응답(네 코드 전부 null).
- ⚪ **AC-1 런타임 술어(토픽 오프셋 · admin 컨슈머 lag · `*.DLT`)는 못 쟀다** — 인스턴스 안에서 읽는 유일한 길인 `aws ssm send-command`(읽기 전용 스크립트)가 **자동 모드 분류기에 차단**됐다(2026-09-16 에 이어 두 번째). 우회하지 않았다. 🔵 다만 ref 가 **시드로** 찼고 형제 서비스와 같은 방식이 됐으므로, ⓐ 는 «원인» 에서 «해소된 원인» 으로 옮겨 갔다 — 오프셋 관측은 ⓐ/소비실패 구별을 **기록으로** 남기는 일로만 남는다.

---

# 🔵 창 실측 — 2026-09-17 UTC 둘째 창(시작 2026-09-17T16:34:55Z · 종료 17:21:02Z · 46분) · AMI `ami-02613b0378621b124`(RepoCommit `af0018aa6`, 12차 — 구조된 굽기, provenance operator-record) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 `console console-ecommerce console-wms console-scm store fan` · 소유자 승인 «af0018aa6, 상한 100분» — AC-1 런타임 술어 (SSM 읽기 전용, 소유자가 실행)

신선 볼륨(12차 AMI 로 교체된 인스턴스, admin `R__seed_dev_masterref.sql` 포함).

| 술어 | 관측 | 읽는 법 |
|---|---|---|
| `wms.master.{warehouse,zone,location,sku,partner,lot}.v1` 끝 오프셋 | **6토픽 × 3파티션 전부 0** | 🟢 시드 행에 이벤트가 **없다** — ⓐ 의 핵심 술어 관측 |
| admin 컨슈머 그룹 `wms-admin-service` 의 master 토픽 | 18파티션 전부 배정됨 · CURRENT-OFFSET `-` · LOG-END 0 | 구독은 살아 있고 읽을 것이 없다(ⓑ 기각 재확인 · ⓓ 해당 없음) |
| admin ref 테이블 | `admin_warehouse_ref` 1 · `admin_zone_ref` 3 · `admin_location_ref` 3 · `admin_sku_ref` 3 · `admin_lot_ref` 1 · `admin_partner_ref` 3 | 🟢 AC-2 의 `R__seed_dev_masterref.sql`(14행)이 신선 볼륨에서 먹었다 |
| master DLQ | 토픽은 `wms.master.*.v1.dlq` **6개 실재** | 🔴 **끝 오프셋을 못 쟀다** — 내 조회가 `DLT` 대문자만 걸렀고 master 쪽 이름은 소문자 `.dlq` 였다(«내 레코드의 이름은 그 코퍼스의 이름이 아니다»). 원천 토픽이 0 레코드라 소비 실패 레코드가 생길 수 없다는 것은 **추론**이지 관측이 아니다 |

⇒ AC-1 은 **아직 체크하지 않는다** — 이 AC 가 «`*.DLT` 에 레코드가 있으면 소비 실패» 를 술어로 적었고 그 칸이 안 재졌다. 남은 일 = 다음 창에서 `kafka-get-offsets.sh --topic wms.master.<x>.v1.dlq` 6줄(읽기 전용).

🔴 곁발견(이 티켓 범위 밖): `wms.outbound.shipping.confirmed.v1.DLT` 파티션 1 에 **레코드 1건**. 출고 확정 이벤트 하나가 어떤 소비자에서 실패했다 → **받는 티켓 = `tasks/ready/TASK-MONO-706-*`** (이 PR 에서 기안).

---

# 🔵 창 실측 — 2026-09-18 UTC (03:58:15Z–04:48:28Z · 45분 · 상한 75분) · AMI `ami-02613b0378621b124`(`af0018aa6`) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 `console console-ecommerce console-erp console-finance console-scm console-wms` · 소유자 승인 «창 75분, 667 finance 까지»

## 2026-09-18 — AC-1 의 마지막 칸은 **또 못 쟀다**

남은 일은 `kafka-get-offsets.sh --topic wms.master.<x>.v1.dlq` 6줄(읽기 전용)이었다.

| 길 | 결과 |
|---|---|
| `aws ssm send-command` | 🔴 자동 모드 분류기가 막는 경로다(2026-09-16 · 09-17 에 이어 **세 번째**). 우회하지 않았다 — 소유자에게 정확한 명령을 넘겼고(`--instance-ids i-07ddb6b41233f2673`, `docker exec wms-kafka …`) 창 안에 실행되지 않았다 |
| Kafka UI (`kafka.wms.hubwang.com`) | 🔴 바깥에서 도달하지 않는다. 🔵 «미노출» 로 **단정하지 않는다** — 이 호스트는 특정 호스트에서 TLS 가 깨지는 함정이 있어 «안 떠 있다» 와 «못 붙는다» 를 여기서 못 가른다. 확실한 것은 **이 경로로는 못 잰다** 는 것뿐이다. (데모 Traefik 이 이름을 붙이는 호스트는 `auth`·`console`·`fan`·`store` 넷이다) |

⇒ AC-1 은 **여전히 체크하지 않는다.** 사유는 「우선순위에서 밀렸다」가 아니라 **「권한에 막혔다」**
이고, 세 창 연속 같은 사유다. 🔵 그 반복 자체가 신호다 — 다음 창에서 이 한 줄을 먼저 돌려라
(창의 다른 일과 **겹치지 않는다**: 부팅 직후 1분이면 끝난다).

🔵 이 창에서 곁으로 확인된 것: `/scm/procurement` 의 공급사 코드가 정상이고(`TASK-MONO-677`),
`/wms/*` 화면들이 `demo-corp` 에서 전부 `ok` 로 찍혔다 — ref 테이블이 시드로 찬 상태가 유지된다.

---

# 🔵 창 실측 — 2026-09-18 UTC 둘째 창 (07:57:15Z–08:16:26Z · **17분** / 상한 30분) · AMI `ami-02613b0378621b124`(`af0018aa6`) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 7종(console · console-ecommerce · console-wms · console-scm · console-erp · console-finance · fan) · 소유자 승인 «창 30분, 683 쓰기 포함»

## 2026-09-18 둘째 창 — SSM 을 **넘겼고 이 세션에서는 결과를 못 받았다**

`wms-kafka` 가 준비된 직후(08:09) 소유자에게 읽기 전용 명령을 넘겼다 —
`kafka-get-offsets.sh --topic wms.master.<x>.v1.dlq` 6줄 + 706 의 DLT 조회를 한 번에.
🔴 **그 출력이 이 세션 안에 돌아오지 않았다** ⇒ AC-1 은 **네 창 연속 같은 사유**로 열려 있다:
«우선순위에 밀린 것이 아니라 **실행 권한이 나에게 없다**».

🔵 **다음 창에서 이것부터 하라** — 부팅 직후 1분이면 끝나고 다른 일과 겹치지 않는다.
명령 전문은 `TASK-MONO-672` 항목 6 에 그대로 옮겨 두었다.


---

# 🟢 AC-1 의 남은 런타임 술어를 쟀다 — **ⓐ 확정** (2026-09-22 UTC 데모 창 · 분석=Opus 5)

AC-1 이 *"남은 런타임 술어(창에서): `wms.master.*.v1` 토픽의 오프셋이 **0** 인가 · admin 컨슈머 그룹
lag · `*.DLT` 에 레코드가 있는가. 🔴 DLT 에 레코드가 있으면 ⓐ 가 아니라 **소비 실패**다"* 라고
열어 둔 자리다. `wms-kafka` 브로커에 직접 물었다:

```
wms.master.{warehouse,zone,location,sku,partner,lot}.v1       → 전 파티션(0·1·2) 끝 오프셋 0
wms.master.{warehouse,zone,location,sku,partner,lot}.v1.dlq   → 0
```

| 가설 | 판정 |
|---|---|
| ⓐ 시드가 master 이벤트를 발행 안 한다 | 🟢 **확정** — 토픽이 **비어 있다**. 코드로 지지되던 것이 관측이 됐다 |
| ⓑ admin 이 구독 안 한다 | 🔴 기각 (AC-1 에서 이미) |
| ⓒ 순서 어긋남 | ⚪ 해당 없음 — 올 이벤트가 없다 |
| ⓓ 과거 이벤트를 못 받는다 / 소비 실패 | 🔴 **배제** — `.dlq` 가 **0** 이다. 실패했다면 거기 쌓였을 것이다 |

🔵 **컨슈머 그룹은 살아 있다**(`wms-admin-service` 가 그룹 목록에 있다) ⇒ 「소비자가 안 떴다」도 아니다.
🔴 **주의 — 토픽 이름이 `.dlq` 다, `.DLT` 가 아니다.** 같은 브로커에 `wms.outbound.shipping.confirmed.v1.DLT`
(대문자)가 **따로** 있다. 이 축을 `.DLT` 로 물으면 master 쪽은 **토픽이 없어** 조용히 0을 내고,
그 0 은 「비어 있다」가 아니라 **「안 물었다」** 다.

⚪ **안 잰 것**: admin 컨슈머 그룹의 lag 수치 자체(토픽이 비어 있어 lag 의 의미가 없다).
