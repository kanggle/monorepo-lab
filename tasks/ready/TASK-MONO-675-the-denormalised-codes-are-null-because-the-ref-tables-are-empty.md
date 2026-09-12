# TASK-MONO-675 — 비정규화한 코드가 전부 `null` 이다. 코드가 틀린 게 아니라 **먹일 데이터가 없다**

# Status

ready

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

**Out of scope**: scm 조달의 공급사 UUID(같은 부류지만 다른 서비스 — `TASK-MONO-676` 참조) ·
콘솔 표시 로직(결백하다) · 659 의 비정규화 자체(코드는 옳다).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [ ] 🔴 ref 5종의 `totalElements` 를 **다시 세라**. 0 이 아니면 이 티켓은 **phantom** 이고,
      그때는 «언제 채워졌는가» 를 적고 닫아라.
- [ ] 🔴 `/api/wms/inventory` 의 `*Code` 필드가 여전히 `null` 인지 확인하라.
      🔵 ref 가 찼는데도 null 이면 **원인이 다른 것**이고 이 티켓의 진단부터 다시 세워야 한다.
- [ ] 🔴 **창이 필요하다.** 못 열면 아무것도 하지 말고 `ready/` 에 그대로 둬라 —
      「켜는 것」은 이 티켓의 범위가 아니다(`TASK-MONO-660` AC-0 과 같은 규율).

## AC-1 — 원인을 **하나로** 지목한다

- [ ] ⓐ~ⓓ 중 **어느 것인지 실측으로 지목**하고, 나머지를 **왜 배제했는지** 적어라.
- [ ] 🔴 «가장 그럴듯한 것» 으로 고르지 마라 — 이 저장소가 반복해서 댄 대가다.
      술어는 **관측**이어야 한다(소비자 클래스의 존재, 토픽 오프셋, 시드 로그).
- [ ] 🔵 admin-service 에 소비자가 없다는 내 실측이 **맞는지부터** 다시 확인하라.

## AC-2 — 고친다

- [ ] 원인이 ⓑ 라면 **소비자를 더하는 것이 맞는지** 먼저 판단하라 — 🔴 형제 서비스
      (`inventory-service`)가 이미 같은 ref 를 들고 있다면 **admin 이 직접 구독할 일이
      아닐 수도 있다**(조회로 풀 수도 있다). 그 판단을 적어라.
- [ ] **백필**: 이미 들어와 있는 스냅샷 행의 `*Code` 는 어떻게 되나. 🔴 새 행만 채우면
      **옛 행은 영원히 null 이고, 화면에는 그 옛 행이 보인다.**

## AC-3 — 판정은 **화면**이다

- [ ] 🔴 API 응답에 코드가 실렸다는 것으로 닫지 마라. **콘솔 `/wms/inventory` 의 위치·SKU 열에
      「이름 확인 불가」가 사라졌는가**가 판정이다.
- [ ] **대조군**: 고치기 전 화면(이 티켓이 첨부한 상태)과 나란히 둬라.
- [ ] 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

## AC-4 — 이 부류가 다시 조용히 지나가지 않게

- [ ] 🔴 **유닛/IT 가 왜 못 잡았는지 적어라** — 픽스처가 ref 행을 넣기 때문이다.
      🔵 «픽스처가 현실을 안 담으면 초록도 공허하다» 가 이 저장소에 이미 있는 문장이고,
      이 건이 그 **새 사례**다.
- [ ] 가드를 만들지 말지 **판단하고 이유를 적어라**. 🔴 `scripts/` 에 파일을 더하면
      분모가 움직인다(전수 스윕 + 두 산문 집).

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
