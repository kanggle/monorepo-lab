# Task ID

TASK-BE-584

# Title

`ADR-MONO-064`·`065` 의 테넌트 축이 **화면까지 닿는지 한 번도 측정되지 않았다** — 데모 볼륨 리셋 + 재시드 합동 검증

# Status

done

# Owner

wms-platform

# Task Tags

- demo
- verification
- read-model

---

# 배경 — 닫힌 티켓 두 개가 **같은 한 칸**을 열어 둔 채 끝났다

| 출처 | 열린 칸 | 원문 |
|---|---|---|
| `TASK-BE-581` AC-3 (`ADR-MONO-064`) | 콘솔 렌더 계층 | *"⚠️ 범위 — 콘솔 렌더 계층은 태우지 않았다. … 남은 미검증 구간은 **BFF/React 가 비어 있지 않은 배열을 실제로 렌더하는가** 뿐"* (기동 중 커밋 차지 92% 도달로 중단) |
| `TASK-BE-583` AC-2 (`ADR-MONO-065`) | 프로젝션 행의 테넌트 | *"🔴 기존 프로젝션 행은 소급 채우지 않는다 — 복구 경로는 064 와 같은 볼륨 초기화 + 재시드이고 두 결정이 **같은 재시드 한 번**으로 함께 풀린다"* |

`TASK-BE-581` 의 Definition of Done 은 지금도 **"콘솔 목록 브라우저 증거" 가 미체크**다.

⇒ **두 칸은 같은 리셋 한 번으로 닫힌다.** 따로 하면 볼륨을 두 번 태우게 되고, 이 저장소는
이미 두 번 "이중 bake 회피" 판단을 내렸다.

## 🔴 손대지 않은 볼륨에서 지금 띄우면 아무것도 증명하지 못한다

`ADR-MONO-065` D1 의 읽기 술어는 두 리포지토리에 같은 모양으로 있다:

```java
// OrderSummaryRepository:24 / ShipmentSummaryRepository:22
"WHERE (:tenantId IS NULL OR o.tenantId = :tenantId) "
```

같은 파일 L17-L20 이 그 의미를 직접 적고 있다 — *"null means an unrestricted caller and
matches every row, **INCLUDING rows whose own tenantId is null**"*. 그리고 `V3` 는
`tenant_id` 를 nullable 로 넣었고 **기존 행을 소급 stamp 하지 않았다**(D1).

조합하면, 데모 운영자(`demo-corp` → `restrictedTo`)에게는:

```
:tenantId = 'demo-corp'   →   o.tenant_id = 'demo-corp'
기존 행     o.tenant_id = NULL
SQL 3치 논리:  NULL = 'demo-corp'  →  UNKNOWN  →  행 제외
```

⇒ 지금 재면 **0원소**가 나온다. 그리고 0 은 아래를 **전부 같은 값**으로 낸다:

- 격리가 동작한다
- 프로젝션이 비었다
- 컨슈머가 죽었다
- 내가 부른 엔드포인트가 틀렸다

**판정 불가**다. 리셋 없는 초록도, 리셋 없는 빨강도 근거가 되지 못한다.

🔵 그리고 이 유도가 맞다면 **`TASK-BE-583` 이 축 도입 *전* 에 실측한
`/dashboard/orders → totalElements=1` 이 지금은 `0` 으로 바뀌어 있다** — 결함이 아니라
그 행이 축보다 오래됐기 때문이다. 즉 현재 데모의 wms 대시보드는 **비어 있을 가능성이 높다.**

⚠️ 🔴 **이것은 JPQL + 알려진 행 상태에서 유도한 예측이고 라이브로 재지 않았다**(티켓 작성 시점
스택 정지 — `monorepo-traefik` 만 가동). **AC-0 이 사실로 만들거나 뒤집는다.** 유도를 실측으로
적지 않는다.

---

# Goal

데모 볼륨을 리셋+재시드한 뒤, `ADR-MONO-064`(생성 경로가 호출자 테넌트를 박는다)와
`ADR-MONO-065`(admin 읽기 평면의 **2 격리 / 6 전역** 분할)가 **콘솔 화면까지 실제로 닿는다**는
것이 **차등 대조군**으로 확인된다. 닿지 않으면 **어느 구간에서 끊기는지**가 특정된다.

---

# Scope

## In Scope

- wms 데모 볼륨 리셋 + 재시드 — **064·065 를 한 번에**
- 기동 범위 = `iam` + `wms` + `console` (`demo-up.sh wms console` — `resolve_deps` 가
  하드 의존 `iam` 을 자동 포함한다)
- 판정 지표 = **BFF/API 응답의 원소 수**
- `ADR-MONO-065` 의 **양쪽 절반**: 격리 2개 표면 + 창고 전역 6개 표면(R1=a)
- `ADR-MONO-064` 의 남은 칸: 콘솔 `/wms/outbound` 가 **비어 있지 않은 목록을 렌더**

## Out of Scope

- `profile=full` 기동 — 이 호스트에서 불가(§ Edge Cases)
- **제품 코드 변경** — 이 티켓은 검증이다. 결함이 나오면 **별도 fix 티켓**을 만든다
- `dbexec` 로 `tenant_id` 를 손으로 박는 것 — `TASK-BE-581` § Out of Scope 가 이미 금지했다
  (제품이 만들 수 없는 행을 만들고, 그 위의 모든 검증이 무효가 된다)
- 기존 행 소급 backfill — `ADR-MONO-065` D1 이 금지

---

# ✅ 실측 (2026-08-14) — 전제는 유지되고, **예측은 기제에서 뒤집혔다**

측정 코드: `origin/main` `9eccea36d`. 🔴 **먼저 이미지를 다시 구웠다** — 호스트에 있던
`wms-*:latest` 는 **2026-08-14 03:09** 산이고 `ADR-MONO-065` 구현(`c6fecfbf4`)은 **15:43** 에
머지됐다. 그대로 쟀으면 **축이 없는 코드**를 재고 065 를 검증했다고 적을 뻔했다.
([[env_pulled_checkout_holds_a_stale_build]] 과 같은 모양 — 소스만 최신이고 산출물은 낡는다.)

## AC-0 ① 리셋 전 DB 기준선 (손대지 않은 `wms_postgres-data`)

```
admin_db flyway 이력   V1 · V2 · V99__seed_dev_data              ← V3 없음
admin_db tenant_id 컬럼 0개 (두 테이블 모두)
admin_order_summary    1행   SO-DEMO-0001 | RECEIVED
admin_shipment_summary 0행
outbound_order         2행   SO-AC3-181910 | MANUAL | demo-corp   ← BE-581 AC-3 이 D1 로 만든 행
                             SO-DEMO-0001  | MANUAL | <NULL>      ← 축보다 오래된 행
```

## AC-0 ② 🔴🔴 `1 → 0` 예측이 **틀렸다 — 0원소가 아니라 500이다**

```
demo-corp 토큰 → /api/v1/admin/dashboard/orders      HTTP 500
demo-corp 토큰 → /api/v1/admin/dashboard/shipments   HTTP 500
```

귀속(추정 아님, 세 지점 대조):

```
wms-admin-service  restarts=12 · health=starting          (크래시 루프)
admin-service 로그 FlywayValidateException
                   "Detected resolved migration not applied to database: 3."
gateway 로그       okhttp connect 실패 — admin-service 에 도달조차 못 함
```

⇒ **필터가 행을 뺀 것이 아니라 표면이 뜨지 않는다.** 화면에서 두 값은 똑같이 "비어 있다" 로
보이지만 원인이 다르고, **리셋하면 이 증거는 영구히 사라진다**(신선 볼륨은 V1→V2→V3→V99 를
순서대로 전부 적용한다). AC-0 을 리셋 **전에** 두라는 순서 제약이 정확히 이것을 건졌다.

### 원인 — `admin-service` 만 V99 를 `db/migration/` 에 둔다 ⇒ **별도 fix 티켓 `TASK-BE-585`**

형제 4개(master·inbound·inventory·outbound)는 같은 `V99` 를 `db/seed/` 에 두고 devseed
오버레이로만 연다. `admin-service` 는 **`db/migration/`** 에 두므로 admin_db 는 **모든 환경에서**
항상 V99 를 적용해 왔고 이력의 마지막 version 이 **99** 다. BE-583 이 새로 넣은 `V3` 는 99
**앞**으로 정렬되므로 Flyway 기본값(`outOfOrder=false`)이 거부한다.

| | 결과 |
|---|---|
| 신선 볼륨(CI · Testcontainers · 리셋 후 데모) | V1·V2·V3·V99 순서대로 적용 → **영원히 초록** |
| 기존 볼륨(운영 중인 데모 · 실 배포) | **admin-service 가 아예 뜨지 못한다** |

⇒ `ADR-MONO-065` 의 축은 **기존 admin_db 에 도달할 수 없다.** BE-583 의 IT 54건은 신선
볼륨에서 돌았으므로 이 칸을 구조적으로 볼 수 없었다
([[env_fresh_volume_ci_is_permanently_green_on_migration_order]] — 결함은 파일이 아니라 **이력 행**에 있다).
🔵 outbound_db 는 tenant 마이그레이션이 `V17` 이고 V99 가 rank 19 로 **뒤에** 적용돼 피했다 —
다만 outbound 의 다음 마이그레이션(V19~)은 같은 함정에 걸린다.

## AC-0 ③ outbound 원시 API — 리셋 전에 이미 차등이 성립한다

```
DB 2행(테넌트만 다름)
demo-corp → GET /api/v1/outbound/orders   200  totalElements=1   (SO-AC3-181910 = 자기 행)
ecommerce → GET /api/v1/outbound/orders   200  totalElements=0   (같은 2행이 안 보인다)
```
⇒ `ADR-MONO-064` D1 의 격리가 라이브에서 유지된다. 200 을 근거로 쓰지 않고 **DB 2행과 대조**했다.

## AC-0 ④ 🔴 타 테넌트 assume — **가능하다** (AC-3·AC-4 판정 가능)

`TASK-BE-583` ⑤ 의 wms-entitled 모집단 5개 전수:

```
demo-corp     ASSUME OK    tenant_id=demo-corp
ecommerce     ASSUME OK    tenant_id=ecommerce      ← 두 번째 테넌트 확보
acme-corp     ASSUME FAIL
initech-corp  ASSUME FAIL
wms           ASSUME FAIL                            ← BE-581 2·3회차와 동일
```

`ecommerce` 토큰: `entitled_domains=["ecommerce","wms"]` ⇒ 합성으로 `WMS_VIEWER` 를 받고,
`tenant_id=ecommerce` 는 unrestricted 집합 `{null,"",wms,*}` 에 없으므로 **restricted** 다.
⇒ 단일 테넌트 대체 초록이 필요 없다.

## AC-1 리셋 + 재시드

`wms_postgres-data` · `wms_kafka-data` · `wms_redis-data` 제거 후
`bash infra/demo/demo-up.sh wms console` (짧은 슬러그 `-p wms` 경로 — 함정 회피).
시드 **생성 2 · 기존 0 · 실패 0**, 앱 9개(wms 7 + console 2) 전부 healthy.
재시드 후 admin_db 이력에 **V3 가 적용**됐다(= 위 결함이 신선 볼륨에서 가려진다는 증거).

## AC-2 `ADR-MONO-064` 가 화면까지 — **통과**

```
DB          outbound_order  SO-DEMO-0001 | MANUAL | tenant_id = demo-corp   ← D1 이 박았다
원시 API    demo-corp → GET /api/v1/outbound/orders        200  totalElements=1
콘솔 BFF    데모 세션 → GET /api/wms/outbound              200  totalElements=1  (orderNo 원소 1)
```

콘솔 측정은 **실제 사용자 경로**를 그대로 밟았다 — `/api/auth/login` → iam 로그인 폼(CSRF) →
콜백 → `POST /api/tenant {"tenant":"demo-corp"}`(assume-tenant 교환) → BFF 호출.
🔴 **HTML grep 은 하지 않았다**(클라이언트 렌더라 0건이 나오고 그 0건은 부재의 증거가 아니다).
⇒ `TASK-BE-581` AC-3 이 열어 둔 칸 *"BFF 가 비어 있지 않은 배열을 실제로 내주는가"* 가 **닫혔다.**

⚠️ **남는 경계(정직하게)**: 판정 지표는 계약대로 **BFF 응답의 원소 수**다. React 컴포넌트가 그
배열을 실제로 페인트하는지는 원소 수로 잴 수 없고, 그것을 재는 유일한 대리지표(HTML grep)는
이 티켓이 금지한 그것이다. **BFF 가 1원소를 내준다**까지가 측정된 사실이다.

🔵 계측 사고 1건(제품 아님): 첫 tenant 전환이 `503 assume-tenant unavailable` 이었다. 콘솔
로그가 `assume_tenant_timeout timeoutMs=5000` — **콜드스타트 5초 타임아웃**이고 워밍 후 재시도는
**0.96초에 200**. [[env_console_operators_create_5s_timeout_false_unavailable]] 과 같은 모양이라
결함으로 적지 않는다.

## AC-3 `ADR-MONO-065` 격리 절반 — **orders 통과 / shipments 판정 불가**

```
                        demo-corp        ecommerce        판정
/dashboard/orders       200  te=1        200  te=0        ✅ 차등 성립 (같은 1행, 한쪽만 보인다)
/dashboard/shipments    200  te=0        200  te=0        ⚠️ 판정 불가 (아래)
```

`orders` 는 **진짜 차등**이다 — AC-5 로 DB 를 먼저 확정했다:
`admin_order_summary` 1행 · `tenant_id=demo-corp` (D2 프로젝션이 봉투 값을 그대로 기록했다).
행이 **존재하는데** 타 테넌트에게만 안 보이므로 상수 비교가 아니다.

🔴 **`shipments` 는 초록이 아니다.** `admin_shipment_summary` 가 **0행**이라 두 토큰이 같은 0 을
내고, 그 0 은 "격리가 동작한다" 와 "테이블이 비었다" 를 **구별하지 못한다**.
제품 API 로 채우려 했으나 **출고가 `PICKING` 을 넘지 못한다**(§ AC-5) ⇒ **`TASK-BE-586`** 으로 분리.
⇒ **065 의 격리 2개 표면 중 1개는 라이브 미검증**이며, 그렇게 기록한다(대체 초록으로 메우지 않는다).

## AC-4 `ADR-MONO-065` 전역 절반 6개 (R1=a) — **통과**

**같은 `ecommerce` 토큰**으로 6개 전부 **막히지 않는다**(403 없음). demo-corp 를 대조군으로 나란히 뒀다:

| 표면 | ecommerce | demo-corp | 상류 행 | 이 칸이 증명하는 것 |
|---|---|---|---|---|
| `/dashboard/asns` | 200 · **1** | 200 · **1** | 1 | 🔴 **전역 공개** — 행이 있고 **양쪽이 같이 본다** |
| `/dashboard/inventory` | 200 · **1** | 200 · **1** | 1 | 🔴 **전역 공개** — 동일 |
| `/dashboard/throughput` | 200 · 동일 payload | 200 · 동일 payload | 1 | 🔴 **전역 공개** — `putawayCount=1 · qtyReceived=95` 가 양쪽 동일 |
| `/dashboard/adjustments` | 200 · 0 | 200 · 0 | 0 | 막히지 않음(200)만. 상류가 비어 전역성은 증명 못 함 |
| `/dashboard/alerts` | 200 · 0 | 200 · 0 | 0 | 동일 |
| `/dashboard/refs/{type}` | 200 · 0 | 200 · 0 | 0 | 동일 (warehouses·skus·partners 3종) |

⇒ **6/6 이 도달 가능하고, 그중 3개는 실제 행으로 "두 테넌트가 같은 것을 본다" 까지 증명**한다.
나머지 3개는 상류가 0행이라 **"막히지 않았다"까지만** 참이다 — 그 한계를 그대로 적는다.

🔵 계측 사고 2건(제품 아님): `refs` 첫 호출이 404, `throughput` 이 400 이었다. 전자는 실제 매핑이
`/refs/{type}` 인데 내가 `/refs` 를 불렀고, 후자는 필수 `warehouseId`·`from`·`to` 를 뺐다.
**내 계측기가 틀린 것**이므로 고쳐 다시 잰 값만 제품 사실로 채택했다.

## AC-5 원소 0 의 귀속 — 전부 DB 를 먼저 읽었다

```
admin_order_summary       1행 (tenant=demo-corp)   → orders 의 1/0 은 격리다
admin_shipment_summary    0행                       → shipments 의 0/0 은 격리가 아니라 공백이다
admin_adjustment_audit    0행 · admin_alert_log 0행 · admin_*_ref 6종 0행 → 그 0 도 공백이다
admin_asn_summary 1 · admin_inventory_snapshot 1 · admin_throughput_inbound_daily 1
outbound_db  picking_request 0 · picking_confirmation 0 · packing_unit 0 · shipment 0
             outbound_saga.status = RESERVED
```

**출고가 왜 멈추는가**(shipments 를 못 채운 이유, 실측):
계약 `outbound-service-api.md` § 2.1 의 `POST /orders/{id}/picking-requests` 가 **405** 다 —
구현에는 `OrderQueryController` 의 **GET 뿐**이고 `PickingController` 는 `/picking-requests` 에
루팅돼 있어 그 POST 가 **존재하지 않는다**. 사가는 `RESERVED` 인데 `picking_request` 가 **0행**이라
`confirmations` 에 넘길 id 도 없다. ⇒ 운영자 경로로 `PICKED → PACKED → SHIPPED` 에 도달할 수 없다.
🔴 `dbexec` 로 shipment 행을 만들지 않았다 — § Out of Scope 가 금지하고, 제품이 만들 수 없는 행
위의 검증은 무효다. ⇒ **`TASK-BE-586`**.

## AC-6 기록

`docs/guides/interview-demo-walkthrough.md` 원장 갱신(`/wms/outbound` 행 · 테넌트 축 행 신설).
🔵 `TASK-BE-581` · `TASK-BE-583` 은 `done/` 이므로 **본문을 고치지 않았다** — 열린 칸이 닫혔다는
사실은 이 티켓에 적고 원장이 가리킨다.

## 발견한 결함 (이 티켓에서 고치지 않는다 — § Out of Scope)

| 티켓 | 결함 | 근거 |
|---|---|---|
| `TASK-BE-585` | `admin-service` V3 가 **기존 admin_db 에 영원히 적용되지 않는다**(V99 가 `db/migration/` 에 있어 out-of-order) ⇒ 기존 볼륨에서 admin-service 기동 불가 · `ADR-MONO-065` 축 미도달 | AC-0 ② |
| `TASK-BE-586` | 출고가 `PICKING` 을 못 넘는다 — 계약 § 2.1 `POST /orders/{id}/picking-requests` **미구현(405)** + 사가가 `picking_request` 를 만들지 않음 ⇒ `admin_shipment_summary` 영구 0행, 065 격리 표면 2개 중 1개 라이브 검증 불가 | AC-3 · AC-5 |

## 🔵 호스트 예산 (기록)

기동 중 커밋 차지 **89% → 93.7%**(여유 2.05GB)까지 갔다. wms/iam 관측 스택 12컨을 내려
여유를 확보하고 측정을 이어갔다(측정 표면이 아니다). `TASK-BE-581` AC-3 이 92% 에서 멈춘 그 지점을
넘겨 **콘솔까지 태웠다**. 티켓이 경고한 대로 판정 지표는 물리 여유가 아니라 커밋 차지였다.

---

# Acceptance Criteria

- [x] **AC-0 (리셋 *전* 실측 — 이 순서가 load-bearing)** — 완료. DB 기준선 ✅ ·
      🔴 예측 `1 → 0` **반증**(0원소가 아니라 **500**, 원인은 Flyway out-of-order 크래시 루프) ·
      타 테넌트 assume **가능**(`ecommerce`) ⇒ AC-3·AC-4 판정 가능. 상세는 위 §
      원문: 리셋하면 되돌릴 수 없으므로 **먼저** 잰다. 이 값이 차등의 한쪽이다.
      - `admin_order_summary` · `admin_shipment_summary` 의 행 수와 `tenant_id` 분포
      - `outbound_order` 의 행 수와 `tenant_id` 분포
      - `demo-corp` 운영자 토큰으로 `/api/v1/admin/dashboard/orders` · `/dashboard/shipments`
        **원소 수** ⇒ 위 § 예측(`1 → 0`)을 **확정하거나 뒤집는다**
      - 🔴 **두 번째 테넌트를 assume 할 수 있는가** — `TASK-BE-583` ⑤ 가 실측한 wms-entitled
        모집단은 `acme-corp` · `demo-corp` · `ecommerce` · `initech-corp` · `wms` 이고,
        `TASK-BE-581` 2회차는 **`assume wms` 가 실패**함을 실측했다. **AC-3·AC-4 가 전적으로
        이것에 달려 있다.** 하나도 assume 되지 않으면 AC-3·AC-4 는 **라이브로 판정 불가**이며,
        그 사실을 그대로 적는다 — 🔴 **대체 초록(단일 테넌트 측정)으로 메우지 않는다**

- [x] **AC-1 (리셋 + 재시드)** — 완료. `wms_{postgres,kafka,redis}-data` 제거 후
      `demo-up.sh wms console`(짧은 슬러그 경로). 시드 생성 2 · 실패 0, 앱 9개 healthy. 원문:
      `wms_*` 볼륨 제거 후 `bash infra/demo/demo-up.sh wms console`.
      🔴 **compose 프로젝트명은 짧은 슬러그 `wms` 다** — `docker compose -f …` 를 그냥 부르면
      프로젝트명이 디렉터리명(`wms-platform`)이 되어 **새 빈 볼륨**을 만든다.
      `TASK-BE-581` 3회차가 이 함정을 밟았고, 그대로 갔으면 신선 볼륨 위에서 전부 0을 보고
      결론지을 뻔했다

- [x] **AC-2 (`ADR-MONO-064` — 생성 경로가 화면까지)** — **통과.** DB `tenant_id=demo-corp` ·
      원시 API 1원소 · **콘솔 BFF `/api/wms/outbound` 1원소**(실제 세션 + assume-tenant 경로).
      HTML grep 미사용. ⚠️ React 페인트 계층은 원소 수로 잴 수 없다(경계 명시). 원문:
      재시드 후 `outbound_order.tenant_id` 가 `demo-corp` 로 **채워져 있고**(D1 이 박는다),
      콘솔 BFF `/api/wms/outbound` 가 **원소 ≥ 1**.
      🔴 **판정은 HTML grep 이 아니다** — 콘솔 wms 화면은 클라이언트 렌더라 SSR HTML 을
      grep 하면 0건이 나오고, 그 0건은 부재의 증거가 아니다

- [~] **AC-3 (`ADR-MONO-065` 격리 절반 — 2개 표면)** — **orders 통과 / shipments 판정 불가.**
      `orders`: demo-corp **1** vs ecommerce **0**, DB 1행(`tenant=demo-corp`) 확정 후 판정 ⇒ 진짜 차등.
      `shipments`: 양쪽 0 이고 `admin_shipment_summary` **0행** ⇒ 격리와 공백을 **구별 못 함**.
      출고가 `PICKING` 을 못 넘어 제품 API 로 채울 수 없다(→ `TASK-BE-586`). **대체 초록 없음.** 원문:

      ```
      demo-corp 토큰    → /dashboard/orders      원소 ≥ 1   (자기 행이 보인다)
      <타 테넌트> 토큰  → /dashboard/orders      원소 0     (같은 행이 안 보인다)
      ```

      `/dashboard/shipments` 도 같은 쌍으로.
      🔴 **한 테넌트만으로 재면 필터가 있든 없든 같은 값이 나온다** — 상수 비교와 구별되지
      않는다. `TASK-BE-583` § Failure Scenarios 1번이 정확히 이 실패다

- [x] **AC-4 (`ADR-MONO-065` 전역 절반 — 6개 표면, R1=a)** — **통과.** 같은 `ecommerce` 토큰으로
      **6/6 이 200**(403 없음). 그중 `asns`·`inventory`·`throughput` 은 **상류에 행이 있고 두 테넌트가
      같은 값을 본다** ⇒ 전역 공개가 실제로 행사됐다. 나머지 3개는 상류 0행이라 "막히지 않았다"까지만
      참이다(그 한계 기록). 원문:
      `/dashboard/adjustments` · `/alerts` · `/asns` · `/inventory` · `/refs` · `/throughput`.
      🔴 이 칸이 없으면 *"격리를 넣었다"* 만 확인하고 **계약의 나머지 절반(창고 전역 공개)은
      한 번도 행사되지 않는다.** 짝을 이루는 축에서 한쪽만 열려 있으면 거의 항상 결함이다

- [x] **AC-5 (원소 0 의 귀속을 강제한다)** — 완료. 0원소가 나온 **모든** 표면에 대해 DB 행 수를
      먼저 읽고 판정했다(위 § AC-5 표). `shipments` 의 0 은 격리가 아니라 **공백**으로 귀속됐고,
      그 공백의 원인(§2.1 405 · `picking_request` 0행)까지 실측했다. 원문:
      어떤 표면이든 0원소가 나오면 **격리 때문인지 프로젝션이 아직 안 왔는지**를 구별한다:
      `admin_order_summary` 를 **DB 에서 직접 읽어 행 존재를 먼저 확정**하고, 그 다음에 API
      원소 수를 판정한다. `TASK-BE-583` § Edge Cases 가 프로젝션이 원본보다 뒤쳐지는 것을
      실측했다(`outbound_order.status=PICKING` vs 프로젝션 `RECEIVED`)

- [x] **AC-6 (기록)** — 완료. 이 티켓 § 실측 + 원장 2행 갱신/신설. done/ 티켓 본문 무변경. 원문:
      결과를 이 티켓과 [`docs/guides/interview-demo-walkthrough.md`](../../../../docs/guides/interview-demo-walkthrough.md)
      원장에 반영.
      🔵 `TASK-BE-581` · `TASK-BE-583` 은 `done/` 이므로 **본문을 고치지 않는다**
      (review/done 파일 편집 금지) — 열린 칸이 닫혔다는 사실은 **이 티켓**에 적고 원장이 가리킨다

---

# Related Specs

- [`docs/adr/ADR-MONO-064`](../../../../docs/adr/ADR-MONO-064-wms-outbound-tenant-visibility-plane.md) — ACCEPTED — B. D1 이 생성 경로에 테넌트를 박고 **소급 stamp 를 금지**한다
- [`docs/adr/ADR-MONO-065`](../../../../docs/adr/ADR-MONO-065-wms-admin-read-plane-tenant-axis.md) — ACCEPTED — `B1` + `R1=a`. D1(2개 표면 격리) · D3(6개 표면 전역, 계약에 명문화)
- `projects/wms-platform/tasks/done/TASK-BE-581-*.md` § AC-3 — 열린 칸 ①
- `projects/wms-platform/tasks/done/TASK-BE-583-*.md` § AC-2 — 열린 칸 ②
- `infra/demo/projects.sh` — `COMPOSE[wms]` · `DEPS[wms]=iam` · `resolve_deps`
- `infra/demo/seed/README.md` — *"넣을 수 있는 것은 실제 API 로 넣는다"*, `dbexec --why` 게이트

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.3 — 격리 2 / 전역 6 의 명문화(`TASK-BE-583` AC-2 산물)
- `projects/wms-platform/specs/contracts/events/outbound-events.md` § Tenant semantics
- `projects/platform-console/specs/contracts/console-integration-contract.md` — 콘솔이 읽는 8개 표면

# Edge Cases

- ⚠️ 🔴 **호스트 예산 — 인용되던 수치가 이 머신 것이 아니다.** 이 호스트는 **물리 15.7GB /
  커밋 한도 31.4GB** 다(2026-08-14 실측). `TASK-MONO-399` AC-2 의 *"31.5GB 중 여유 ~2.8GB"* 는
  **`free -m` 출력 = AWS 데모 인스턴스**이지 이 윈도우 호스트가 아니다(커밋 한도 31.4GB 와
  숫자가 겹쳐 혼동되기 쉽다). ⇒ 제약은 인용값보다 **더 빡빡하다**
- **판정 지표는 물리 여유가 아니라 커밋 차지다** — 이 호스트의 알려진 캐스케이드가
  커밋 고갈로 터진다(여유 <3GB 위험). `TASK-BE-581` AC-3 가 **92% 에서 중단**한 것이 그 이유다.
  기동 중 커밋을 계속 본다
- wms 7앱 ≈ **5.6GiB** (`TASK-BE-581` 2회차 실측)
- 🔴 **기존 볼륨의 DB 롤 비밀번호가 현재 `.env` 와 어긋나 전 서비스가 Flyway 단계에서
  크래시 루프**한 선례가 있다(`TASK-BE-581` AC-3 부수 발견 — 환경 드리프트). **리셋하면
  이 문제는 소멸한다**(새 볼륨이 현재 `.env` 로 초기화되므로). 리셋 없이 재려 했다면 이것부터
  밟았을 것이다
- 재시드는 `master-service` 읽기 모델이 먼저 차야 `POST /api/v1/outbound/orders` 가
  `422 PARTNER_INVALID_TYPE` 를 내지 않는다(`TASK-BE-581` 2회차 — **슬라이스 한계이지 제품 결함이 아니다**)
- `demo.env` 를 소스하지 않고 compose 를 손으로 부르면 `OIDC_ALLOWED_ISSUERS` 가 컨테이너
  기본값으로 굳어 **전건 401** 이 난다. 🔴 **401 은 도메인 판정이 아니라 "물어보지도 못했다"** 다
  (`TASK-BE-581` 2회차가 이것을 결함으로 적을 뻔했다)

# Failure Scenarios

- **리셋 전 AC-0 을 건너뛴다** → 차등의 한쪽이 영구히 사라진다. 리셋 후 숫자만으로는
  *"축이 화면까지 닿았다"* 를 *"원래 그랬다"* 와 구별할 수 없다
- **한 테넌트로만 잰다** → 필터의 유무와 무관하게 같은 결과가 나온다. 상수 비교다
- **격리 절반(2개)만 재고 "065 검증 완료" 로 기록** → 계약의 **6/8** 이 미검증인 채 닫힌다
- **HTML grep 으로 판정** → 콘솔은 클라이언트 렌더. 0건은 부재의 증거가 아니다
- **원소 0 을 격리로 귀속** → 프로젝션 미도달 · 컨슈머 사망 · 엔드포인트 오타가 **전부 같은 0** 을
  낸다. AC-5 가 막는다
- **`profile=full` 로 기동** → 커밋 고갈 → 이 호스트의 알려진 OOM 캐스케이드
- **검증 티켓에서 발견한 결함을 그 자리에서 고친다** → 검증과 수정이 한 커밋에 섞여, 무엇이
  통과를 만들었는지 구별되지 않는다. § Out of Scope 가 막는다

# Definition of Done

- [x] AC-0 ~ AC-6 — **AC-3 의 `shipments` 한 칸만 판정 불가**(사유·귀속 실측 기록, 대체 초록 없음).
      나머지 전부 완료
- [x] 결함 발견 시 **별도 fix 티켓** 등록 — `TASK-BE-585`(Flyway out-of-order) ·
      `TASK-BE-586`(출고가 `PICKING` 을 못 넘음). **제품 코드 변경 0**
- [x] Ready for review

## 두 ADR 의 열린 칸 — 결산

| 출처 | 열려 있던 칸 | 결과 |
|---|---|---|
| `TASK-BE-581` AC-3 (`ADR-MONO-064`) | 콘솔 렌더 계층 | ✅ **닫힘** — BFF `/api/wms/outbound` 1원소(실제 세션). ⚠️ React 페인트는 원소 수의 사정 밖 |
| `TASK-BE-583` AC-2 (`ADR-MONO-065`) | 프로젝션 행의 테넌트 | ✅ **닫힘(절반)** — 재시드 후 `admin_order_summary.tenant_id=demo-corp`, orders 차등 성립 · 전역 6/6 통과. ❌ shipments 는 `TASK-BE-586` 이 풀어야 잴 수 있다 |
| — (이 티켓이 새로 연 칸) | `ADR-MONO-065` 축이 **기존 환경에 도달하는가** | ❌ **도달하지 못한다** — `TASK-BE-585` |
