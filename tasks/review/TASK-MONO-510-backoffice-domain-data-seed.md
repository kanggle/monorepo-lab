# Task ID

TASK-MONO-510

# Title

WMS · SCM · ERP · Finance 도메인 데이터 시드 — 콘솔 도메인 운영 23개 화면을 채운다

# Status

review

# Owner

monorepo

# Task Tags

- infra
- demo
- test

---

# 배경 — `TASK-MONO-506` 의 S4 · S5 슬라이스

MONO-506 이 시드 프레임워크와 ecommerce 시드를 만들고 라이브 검증까지 마쳤다. 나머지 네
도메인은 **의도적으로 분리했다**:

1. **호스트 자원.** 도커 가용 11.7 GiB 중 iam + console + ecommerce(축소본) 35 컨테이너가
   9.2 GiB 를 쓴다(실측 2026-08-05). S5 는 iam + scm + erp + finance + console 을 동시에
   요구하므로 별도 슬라이스가 필요하고, 그 자체로 메모리 실측 대상이다.
2. **검증하지 않은 시드는 거짓 약속이다.** 띄워 보지 않고 스크립트만 커밋하면 MONO-506 이
   세운 원칙("넣는 행위가 곧 검증")을 스스로 어긴다.

## 이미 확보된 것 — **운영자 토큰 하나가 네 도메인을 전부 연다**

MONO-506 이 `infra/demo/seed/lib.sh` 에 `operator_token` 을 만들었다. 콘솔 로그인
(공개 클라이언트 `platform-console-web`, PKCE) → RFC 8693 assume `demo-corp` 를 거치면:

```
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_READ/WRITE, INBOUND_READ/WRITE,
         INVENTORY_READ/WRITE, MASTER_READ]
entitled_domains = [ecommerce, erp, finance, scm, wms]
```

즉 이 티켓은 **인증 문제를 새로 풀지 않는다.** 각 도메인의 쓰기 엔드포인트와 필수 필드만
확인하면 된다.

보조 경로로 도메인별 워크로드 클라이언트(`client_credentials`)도 있다 —
`wms-internal-services-client` · `scm-platform-internal-services-client` ·
`erp-platform-internal-services-client` · `finance-platform-internal-services-client`
(secret 은 각 마이그레이션 헤더에 명시된 dev 리터럴; wms 는 `"secret"` 로 실측 확인).
운영자 역할로 막히는 항목이 있을 때만 쓴다.

---

# Goal

`bash infra/demo/demo-up.sh iam wms console` (S4) 과
`bash infra/demo/demo-up.sh iam scm erp finance console` (S5) 로 뜬 스택에서, 콘솔의
해당 도메인 섹션 화면이 **비어 있지 않다.**

---

# Scope

## In Scope

- `infra/demo/seed/seed-wms.sh` · `seed-scm.sh` · `seed-erp.sh` · `seed-finance.sh`
- 콘솔 도메인 운영 화면 23개 라이브 검증
- 슬라이스별 메모리 실측
- 워크스루 가이드의 한계 표 갱신

## Out of Scope

- 제품 코드 변경 — 발굴 결함은 별도 티켓
- 팬 도메인 — `TASK-MONO-509`
- 콘솔이 스토어프런트 테넌트 데이터를 못 보는 문제 — `TASK-BE-576`
  (**이 티켓과 상호작용한다** — 아래 AC-0 참조)

---

# 대상 화면 (2026-08-05 `console-nav-config.ts` 실측 — 착수 시 재측정)

| 도메인 | 리프 | 경로 |
|---|---|---|
| WMS | 7 | `/wms` `/wms/guide` `/wms/inbound` `/wms/inventory` `/wms/outbound` `/wms/master` `/wms/operations` |
| SCM | 6 | `/scm` `/scm/guide` `/scm/procurement` `/scm/inventory` `/scm/replenishment` `/scm/config` |
| Finance | 4 | `/finance` `/finance/guide` `/finance/accounts` `/ledger` |
| ERP | 6 | `/erp` `/erp/guide` `/erp/masters` `/erp/orgview` `/erp/approval` `/erp/delegation` |

MONO-506 의 콘솔 전수 스윕(iam + console + ecommerce 슬라이스)에서 이 중 **15개가
데이터 부재로 degrade** 했다(가이드 페이지와 일부 개요는 통과). 그 15개가 이 티켓의 최소 표적이다.

---

# 🟡 착수 1회차 실측 (2026-08-05) — **AC-0 은 끝났다. 다시 유도하지 말 것**

## AC-0 ① 화면 모집단 = **23** (변동 없음)

`console-nav-config.ts` 재측정: WMS 7 · SCM 6 · Finance 4(`/ledger` 포함) · ERP 6.
전체 리프 47.

## AC-0 ② 테넌트 — **BE-576 형태의 블록은 WMS 에 없다. 이유가 구조적이다**

세 가지를 실측했고 셋 다 티켓의 가정과 달랐다:

1. **콘솔은 assume 토큰을 쓴다.** 페이지 주석은 *"NOT the IAM exchanged operator token"*
   이라 적혀 있지만 실제 해석자는
   `getDomainFacingToken() = getAssumedToken() ?? getAccessToken()` 이다. 실측이 갈랐다:

   ```
   base 콘솔 토큰    tenant_id=iam        → 전 엔드포인트 403 TENANT_FORBIDDEN
   assume demo-corp  tenant_id=demo-corp  → 전 엔드포인트 200
   ```

   ⇒ **주석보다 코드가 권위**였고, 테넌트를 고른 뒤에만 화면이 찬다(워크스루와 일치).

2. **WMS 는 데이터에 테넌트가 거의 없다.** `tenant_id` 컬럼을 가진 테이블은 5개 DB
   (master/inbound/inventory/outbound/admin) 통틀어 **`outbound_db.outbound_order`
   하나뿐**이다. 테넌시는 게이트웨이 엣지 admission 으로만 강제된다 ⇒ "200 + 빈 배열"
   위험은 **구조적으로 해당 없음**. AC-0 의 블록 조건 불성립.

3. 🔴 다만 그 하나가 예외라는 사실 자체가 위험이다 — ecommerce 풀필먼트가 살아 있는
   슬라이스에서는 `outbound_order` 만 테넌트로 갈리고 그 ASN·재고는 안 갈린다.
   이 슬라이스에 ecommerce 가 없어 **미측정**이다.

## AC-1 마스터 데이터 — API 로 못 넣는다. 그리고 **직접-DB 도 답이 아니었다**

```
POST /api/v1/master/warehouses  (assume demo-corp)                 403 FORBIDDEN
POST /api/v1/master/warehouses  (wms-internal-services-client,
                                 scope=wms.master.write)           403 FORBIDDEN
```

master-service 는 `@PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")`
인데 `OperatorRoleDerivation` 은 `MASTER_READ` 까지만 준다(outbound/inbound/inventory 에는
READ+WRITE 를 주는 **비대칭**). 워크로드 토큰은 **scope 만** 싣고 role 을 안 싣는다.
→ **`TASK-MONO-514`**.

🔵 **답은 새 SQL 이 아니었다** — 이 저장소가 마스터 시드를 **이미 갖고 있다**
(`master`/`inbound`/`inventory` 각각 `db/seed/V99..V103`, 고정 UUID). 그 위치는
`application-dev.yml` 에서만 활성화되므로 데모에서는 한 줄도 돌지 않았다(실측: 5개 테이블
전부 0행). `infra/demo/wms-devseed.override.yml` 이 `SPRING_FLYWAY_LOCATIONS` 로 그 위치만
연다. **검증 완료**: 창고 1 · 존 3 · 로케이션 3 · SKU 3 · 거래처 3.

## 이 도메인의 계측 함정 (재현 시 먼저 읽을 것)

- `Idempotency-Key` 는 **UUID** 여야 한다(아니면 400 `must be a UUID`).
- 🔴 **같은 키는 실패 응답까지 재생한다** — 두 번째 403 의 타임스탬프가 첫 번째와
  **바이트 단위로 동일**했다. 키를 바꾸기 전까지는 실측이 아니다.
- 게이트웨이는 `/api/v1/**` 만 받는다(`/api/...` 는 404).
- 운영자가 **못 가진** 역할: `MASTER_WRITE` · `INVENTORY_RESERVE` · 모든 `*_ADMIN`.

## AC-6 메모리 — S4 실측, S5 는 **거의 확실히 불가**

```
iam + wms + console = 33 컨테이너 = 9.96 GiB / 가용 11.68  (85%)
  iam 4,308 MiB · wms 5,589 MiB · console 264 MiB
```

🔴 이 포화가 실제로 물었다: inbound-service 의 Kafka 컨슈머가 `poll timeout` 으로
리밸런싱을 반복하고 게이트웨이가 **504 / 500(`NoRouteToHostException`)** 을 냈다.
관측 사이드카 12개를 내려 21컨까지 줄이고 서비스를 clean recreate 해도 ASN 생성은 500 이
유지됐다 — **호스트 포화인지 서비스 결함인지 아직 갈리지 않았다.** 다음 착수의 첫 일이다.

S5(iam + scm + erp + finance + console)는 앱만 13개 + 인프라 3세트다. wms 하나(앱 7)가
5.6 GiB 를 썼으므로 **로컬 동시 기동은 불가로 본다**(AC-6 이 "못 했다도 유효한 측정"
이라고 적어 두었다). 도메인을 **한 번에 하나씩** 띄우는 슬라이스 분해가 필요하다.

## 남은 것

- SCM · ERP · Finance — 미착수. 이미지도 없다(scm 5 · finance 3 서비스 빌드 필요;
  erp 는 이미지 존재).

---

# 🟢 착수 2회차 (2026-08-06) — WMS 흐름 시드 **완료**. 1회차의 판정을 정정한다

## ⓪ 🔴🔴 **1회차 AC-0 의 결론이 틀렸다** — 정확히 반대다

1회차는 이렇게 적었다: *"`tenant_id` 컬럼을 가진 테이블은 `outbound_db.outbound_order`
하나뿐 ⇒ '200 + 빈 배열' 위험은 **구조적으로 해당 없음**."*

**그 하나가 정확히 데모 운영자가 스코프당하는 대상이다.** "하나뿐" 은 위험이 작다는
뜻이 아니라 **위험이 거기 전부 몰려 있다**는 뜻이었다. 실측:

```
시드가 실제 API 로 생성   POST /api/v1/outbound/orders           → 201
DB                        outbound_order SO-DEMO-0001 | PICKING | tenant_id = NULL
같은 토큰으로 조회         GET  /api/v1/outbound/orders?size=100  → 200 {"content":[]}
토큰                      tenant_id = "demo-corp"
```

**만든 주체가 만든 것을 못 본다.** `demo-corp` 는 `wms`·`*` 가 아니라 **restricted** 로
판정되고, 조회는 `tenant_id=demo-corp` **AND `source=FULFILLMENT_ECOMMERCE`** 로 고정된다
(`OrderQueryCommand.withTenantScope` Javadoc: *"may only ever see its own ecommerce
orders"*). 그런데 `withTenantScope` 는 **쿼리 커맨드에만 있고 생성 경로는 `tenant_id` 를
설정하지 않는다**(호출처 전수 확인) ⇒ 수동 생성 주문은 자기 조건에 **절대** 안 걸린다.

⇒ 콘솔 `/wms/outbound` 는 **시드가 무엇을 넣든 빈다.** → `TASK-BE-581`.

🔵 **왜 1회차가 놓쳤나** — AC-0 을 "테넌트 컬럼이 있는 테이블을 세어" 판정했다.
그것은 대리지표다. AC-0 이 원래 요구한 것은 **"운영자 토큰으로 목록 API 를 호출해
원소 수가 DB 실측과 일치하는지"** 였고, 그 술어를 outbound 목록에 대해 실제로
돌렸으면 1회차에 잡혔다. **200 은 판정 근거가 아니다** 라고 티켓이 스스로 적어 두었는데,
스키마를 세는 것으로 대체하면서 그 문장을 비켜 갔다.

## ① 500 판별 — **서비스 결함이 아니었다. 그리고 "실패" 도 아니었다**

1회차가 남긴 첫 일("호스트 포화인지 서비스 결함인지")을 갈랐다. **축소 토폴로지**
(전체 wms 33컨이 아니라 gateway+inbound+inventory+outbound+master+admin+인프라 = 10컨)에서
같은 `POST /api/v1/inbound/asns` 가 **201 Created**. ⇒ ASN 경로 자체는 결함이 아니다.

🔴 그리고 더 중요한 정정: **504 는 실패가 아니었다.** 504 를 받은 뒤 DB 를 보니
`inbound_db.asn` 에 `status=CREATED` 행이 **실재**했고, 출고 주문도 504 뒤 재시도가
`409 ORDER_NO_DUPLICATE` 를 냈다(= 첫 요청이 성공해 있었다는 뜻). 즉 **엣지가 타임아웃
하는 동안 쓰기는 완료된다.** 1회차의 "ASN 생성은 500 이 유지됐다" 는 관측은 사실이지만
**결론("생성 불가")은 틀렸을 수 있다** — 그때 DB 를 보지 않았다.

⇒ 이 위양성이 시드에 그대로 새면 중복 생성 또는 허위 실패가 된다. `seed-wms.sh` 는
그래서 409 를 "이미 존재" 로 센다(lib.sh `api_create` 와 같은 규약).

## ② 진짜 블로커는 따로 있었다 — `TASK-BE-579`

`inventory-service` · `outbound-service` 가 **HTTP 를 한 건도 서빙하지 않는 상태로
갇힌다.** 컨테이너는 `Up`, Kafka 컨슈머는 계속 도는데 `/actuator/health/liveness`(순수
in-memory)와 없는 경로 `/nope` 까지 매달린다. 🔴 **기동 경합이 아니다** — 같은 컨테이너가
+3분 healthy → +9분 unhealthy(그 사이 요청 0건). 대조군 `inbound`/`master` 는 같은 순간
즉답. 단독 `restart` 로 복구(2회). 게이트웨이는 이때 504 를 낸다 ⇒ ①의 위양성과 겹쳐
1회차에 "서비스 결함" 으로 보였던 것의 정체다.

## ③ WMS 흐름 시드 — 커밋했다 (`infra/demo/seed/seed-wms.sh`)

**볼륨을 지우고 새로 띄운 스택**에서 끝까지 통과한 뒤에만 커밋했다.

```
[seed:wms] 생성  ASN ASN-DEMO-0001
[seed:wms] 진행  검수 시작
[seed:wms] 진행  검수 기록 (합격 95)
[seed:wms] 진행  적치 지시
[seed:wms] 진행  적치 확정 → 재고 반영 (비동기)
[seed:wms] 존재  출고 주문 SO-DEMO-0001 (HTTP 409)
```

**결과가 DB 에 실제로 있다**(추론 아님):

| 확인 | 값 |
|---|---|
| `inbound_db.asn` | `ASN-DEMO-0001` / `PUTAWAY_DONE` |
| `inbound_db.putaway_instruction` | `COMPLETED` |
| `inventory_db.inventory` | `available_qty=95`, 창고·로케이션·SKU 일치, `created_by=system:putaway-consumer` |
| `inventory_db.inventory_movement` | 1행 |
| `outbound_db.outbound_order` | `SO-DEMO-0001` / `PICKING` |

⇒ **AC-3(프로젝션 의존)이 WMS 재고에 대해 충족**됐다. 적치 확정 → Kafka → 재고 반영이
실제로 따라왔다.

**AC-5(멱등)**: 2회차 = `생성 0 · 기존 2 · 실패 0`, rc=0.

🔵 **멱등 가드를 "있으면 건너뜀" 으로 만들지 않았다.** ASN 이 **중간 상태**로 남아 있으면
이전 실행이 흐름 도중 깨졌다는 뜻인데, 그것을 건너뛰면 화면은 빈 채 시드는 초록이 된다.
그래서 종착 상태(`PUTAWAY_DONE`/`CLOSED`)일 때만 건너뛰고 중간이면 **실패로 센다** —
실제로 이 가드가 ①의 위양성을 잡아냈다.

## ④ 시드를 쓰며 밟은 함정 (다음 사람 몫)

- 🔴 `sed -E 's/.*"id":"([^"]*)".*/\1/'` 는 `.*` 가 greedy 라 **마지막** `"id"` 를 집는다.
  ASN 생성 응답에서 그것은 `lines[].id` 라, 뒤이은 호출이 라인 id 를 ASN id 로 보내
  `404 ASN_NOT_FOUND` 가 났다. `grep -oE ... | head -1` 로 바꿨다.
- 🔴 같은 `"lines"` 라도 **키가 다르다**: ASN 응답은 `id`, 적치 지시 응답은 `putawayLineId`.
  틀리면 URL 이 조용히 깨져 curl 이 **상태코드조차 못 낸다**(빈 실패).
- 출고 필드는 `orderedQty` 가 아니라 **`qtyOrdered`**, `requestedShipDate` 가 아니라
  **`requiredShipDate`**, `lineNo` **필수**, 그리고 `tenantId` 는 바디에 없다.
- Git Bash(msys)에는 `/proc/sys/kernel/random/uuid` 가 **없다** → openssl 로 만든다.
- 🔵 `putaway_confirmation` 은 append-only 를 **트리거와 권한 양쪽으로** 강제한다.
  그래서 `putaway_line` 삭제의 FK 검사(`SELECT ... FOR KEY SHARE`)까지 막혀 소유자
  `inbound` 로도 지울 수 없다. 픽스처 정리에서만 부딪혔고 **제품 경로에서 도달
  가능한지는 미확인** — 그래서 티켓으로 올리지 않고 여기 기록만 남긴다.

## ⑤ AC-6 메모리 — 축소 WMS 슬라이스 실측

```
wms 축소 슬라이스(앱 6 + 인프라 4 = 10컨) ≈ 1.9 GiB
  gateway 294 · master 558 · inventory 568 · kafka 380 · postgres 68 · redis 4 MiB
전체 호스트 27컨(fan 9 + iam 7 + wms 10 + traefik) 시점 VM 사용 ≈ 6.9 / 11.68 GiB
```

⇒ 1회차의 "wms = 5,589 MiB" 는 **관측 사이드카 12개를 포함한 전체 스택**의 값이다.
앱만 골라 띄우면 1/3 이다. **S5 도 도메인별로 쪼개면 가능성이 있다** — 1회차의
"로컬 동시 기동 불가" 는 전체 스택 기준이지 앱 기준이 아니다.

## ⑥ 이번 회차에서 하지 **않은** 것 (조용한 누락 없이)

- **23개 화면 브라우저 검증(AC-2)** — console 을 띄우지 않았다. 시드는 API·DB 로
  검증했고 화면은 **미확인**이다. "데이터가 있다" 와 "화면이 찬다" 는 다른 명제다.
- **AC-4(브라우저 쓰기 1건)** — 같은 이유로 미착수.
- **SCM · ERP · Finance** — 미착수. SCM(5)·Finance(3) 은 이미지부터 없다.

---

# 🟢 착수 4회차 (2026-08-06) — **ERP 시드 완료.** 그리고 이 도메인의 이벤트 평면이 죽어 있었다

## ⓪ AC-0 (erp) — **BE-576 형 분리는 없다.** 이번엔 대리지표가 아니라 원문의 술어로 쟀다

2회차가 스스로 적어 둔 교훈("테넌트 컬럼 세기는 대리지표다 — **목록 API 원소 수 대 DB
실측**을 대조하라")을 그대로 적용했다:

```
                      BFF 원소 수   DB COUNT(*)
departments                3            3
employees                  4            4
job-grades                 3            3
cost-centers               3            3
business-partners          3            3
approval/requests          3            3
approval/delegations       1            1
```

**전 항목 일치** ⇒ erp 는 "200 + 빈 배열" 이 아니다. 🔵 `information_schema.table_rows`
는 InnoDB 에서 **추정치**라 판정에 쓰지 않았다 — 전부 `COUNT(*)` 다.

테넌트 게이트도 갈렸다(같은 엔드포인트, 토큰만 교체):

```
base 콘솔 토큰   tenant_id=iam        → 10/10 엔드포인트 403 TENANT_FORBIDDEN
assume demo-corp tenant_id=demo-corp  → 10/10 200
```

게이트웨이 `required-tenant-id` 는 compose 에 `erp` 로 **하드코딩**돼 있는데 `demo-corp`
가 통과한다 — `tenantGate()` 가 `.trustEntitledDomains()` 를 걸어 두었고 운영자 토큰의
`entitled_domains` 에 `erp` 가 있기 때문이다. **설정값만 보고 403 을 예단하지 말 것.**

## ① `infra/demo/seed/seed-erp.sh` — 커밋했다. 직접-DB **0건**

**볼륨을 지우고 새로 띄운 스택**에서 통과한 뒤에만 커밋했다. 마스터 5종 + 결재 3건 +
위임 1건 = **20건 전부 실제 API**. 1회차가 `MASTER_WRITE` 로 막혔던 wms 와 달리 erp 는
다섯 마스터 전부에 생성 엔드포인트가 있고 운영자 토큰이 그것을 연다.

```
1회차(깨끗한 볼륨)  생성 20 · 기존  0 · 실패 0 · 차단 3   rc=0
2회차(멱등)         생성  0 · 기존 20 · 실패 0 · 차단 3   rc=0
```

**AC-5 충족.** 멱등은 자연키(`code`/`employeeNumber`)를 **목록에서 먼저 찾는** 방식이다 —
409 응답에는 id 가 없어서 2회차에 하위 마스터를 만들 수 없기 때문이다.

## ② 🔴 시드의 준비성 게이트가 **깨진 술어**였다 (다음 사람 몫)

첫 깨끗한-볼륨 기동에서 **16건 전부 실패**했다. `wait_http` 는 통과했는데:

```
wait_http /api/erp/masterdata/departments   → 401 ⇒ "살아 있음" 으로 판정, 통과
POST     /api/erp/masterdata/job-grades     → 500
게이트웨이 로그: Connection refused: masterdata-service/172.24.0.9:8080
masterdata 로그: 에러 0건                     ← 요청이 도달조차 하지 않았다
```

**토큰 없는 401 은 게이트웨이 자신의 시큐리티 필터가 낸다 — 뒤의 서비스에 닿지 않는다.**
게이트웨이 단독이면 맞는 술어지만, 백엔드 준비성에 대해서는 **아무것도 증명하지 않는다.**
`seed-erp.sh` 에 `wait_backend`(토큰을 얻은 뒤 **인증된** GET 이 2xx 일 때까지)를 넣고
masterdata·approval·read-model **셋을 각각** 확인한다 — 하나로 나머지를 추정하면 같은
함정을 한 겹 아래에서 반복한다.

## ③ 🔴🔴 발굴 결함 3건 — 그중 둘은 **erp 이벤트 평면 전체가 죽어 있다**

| 티켓 | 증상 | 근거 |
|---|---|---|
| `TASK-ERP-BE-041` | 상신이 **항상** 422 `subject_unresolved` | `MasterDataRestAdapter` 가 masterdata 를 **토큰 없이** 호출 → 컨테이너 안 실측 `HTTP/1.1 401` → `onStatus(4xx)` 가 삼켜 "ACTIVE 아님" 이 된다 |
| `TASK-ERP-BE-042` | read-model 프로젝션 **영구 0** | 아웃박스 UNPUBLISHED 17/1, **kafka end-offset 전 토픽 `:0:0`**. `@Scheduled` 는 있는데 `@EnableScheduling` 이 **없다** — erp 5앱 중 notification 하나만 갖고 있다(wms 6/6 · scm · finance 는 전부 보유) |
| `TASK-MONO-515` | 결재함이 **어떤 콘솔 사용자에게도** 비어 있다 | assume 토큰 `sub` = `platform-console-web`(계정 아님) + 자기결재 금지 + token-exchange grant 클라이언트가 **1개뿐** ⇒ `demo-corp` 의 actorId 는 정확히 하나 |

🔴 **BE-042 의 판정은 소비자가 아니라 브로커에서 냈다.** "프로젝션이 0" 은 지연일 수도
있고 발행 부재일 수도 있다. `kafka-get-offsets` 이 전 토픽 0 을 보여야 후자가 확정된다.

🔵 **MONO-515 는 결함으로 단정하기 직전에 멈춰서 확인한 것이다.** `sub` 손실을 iam 버그로
티켓 낼 뻔했는데, `AssumeTenantExchangeIntegrationTest` 가 *"the assumed token's own sub
is the acting console client … per the RFC 8693 flow"* 라고 **명시적으로 단언**하고
있었다. 그래서 티켓의 방향이 "버그 수정" 이 아니라 **"문서화된 결정과 erp 결재의 신원
모델이 충돌한다 → ADR"** 이 됐다.

## ④ 시드가 **알려진 결함**과 **자기 실패**를 구별한다

BE-041/042 는 시드가 고칠 수 없다(Scope: 제품 코드 변경 별도 티켓). 그것을 `seed_fail`
로 세면 `demo-up.sh` 가 매 실행 빨개져 진짜 회귀가 묻히고, 조용히 넘기면 데모가 비었는데
시드는 초록이 된다. 그래서 **세 번째 분류(`⛔ 차단`)** 를 뒀다 — 실패 신호가 그 결함의
**정확한 지문**(`"cause":"subject_unresolved"` / 프로젝션 0)과 맞을 때만 차단으로 세고
티켓 번호를 찍는다. 지문이 어긋나면 그대로 실패다. ⇒ 결함이 고쳐지면 그 경로는 **저절로
다시 성립하고**, 새로운 고장은 여전히 빨개진다.

## ⑤ AC-2 (erp 6화면) — BFF 원소 수로 판정

| 화면 | 상류 원소 수 | 판정 |
|---|---|---|
| `/erp` 개요 | (마스터 집계) | ✅ |
| `/erp/guide` | 정적 | ✅ |
| `/erp/masters` | 3·4·3·3·3 (5탭 전부) | ✅ |
| `/erp/approval` | 요청 **3** / 결재함 **0** | 🟡 목록만 — 결재함은 `MONO-515` |
| `/erp/delegation` | 위임 **1** / read-model **0** | 🟡 원본만 — 프로젝션은 `ERP-BE-042` |
| `/erp/orgview` | **4** ✅ | ✅ (`ERP-BE-042` 수정 후 0 → 4) |

⇒ **6화면 중 5개 충족.** 남은 하나는 `/erp/approval` 의 결재함(`MONO-515`)이고,
`/erp/delegation` 은 원본 목록은 차되 read-model 뷰만 빈다(`ERP-BE-043`).

🔴 **페이지 HTML 로 판정하려던 시도는 오탐을 냈다.** "degrade 문구" 를 grep 했더니 3개
화면이 걸렸는데, 실제로 매치된 것은 정상 안내 문구(*"권한이 없는 작업은 실행 시
안내됩니다"*)였다. 콘솔은 클라이언트 렌더라 **판정은 BFF 원소 수로만** 한다.

## ⑥ AC-6 (erp 메모리) — 8컨 **3,854 MiB**

```
kafka 890 · notification 723 · masterdata 580 · approval 542 · mysql 428
gateway 310 · read-model 375 · redis 7                       = 3,854 MiB
iam + console + erp = 25컨,  VM 사용 9.85 / 11.96 GiB
```

🔵 **S5 판정을 다시 한다.** erp(앱 5) 하나가 3.85 GiB 다. scm(5) + erp(5) + finance(3) +
iam + console 을 **동시에** 올리면 인프라 3세트까지 더해 10 GiB 를 넘겨 호스트 한계에
닿는다 ⇒ **S5 동시 기동은 여전히 불가**로 본다. 도메인을 하나씩 올리는 슬라이스 분해가
정답이고, 이번 회차가 그 방식으로 성립함을 보였다.

🔴 이 회차를 위해 **wms · fan 슬라이스를 내렸다**(`demo-down.sh wms fan`, 볼륨 보존).
착수 시점 available 이 1.27 GiB, swap 3.68/4.0 GiB 로 포화라 erp 를 올릴 자리가 없었다.

## ⑦ 이번 회차에서 하지 **않은** 것

- **AC-4(브라우저 쓰기 1건)** — erp 의 자연스러운 쓰기는 결재 승인인데 `MONO-515` 로
  막혀 있다. 부서 생성(콘솔 PC-FE-046 write pilot)이 대안이나 **미실행**이다
- **SCM · Finance** — 미착수. 이미지부터 없다(scm 5 · finance 3 서비스 빌드 필요)
- **erp 이벤트 평면 수정** — Scope 상 별도 티켓(`ERP-BE-042`)

---

# 🟢 착수 6회차 (2026-08-07) — **AC-4 를 4도메인 전부 실측.** 5회차의 판정이 두 곳에서 뒤집혔다

## ① 결과 — **2/4 충족.** 그리고 "도달 불가" 의 명단이 바뀌었다

5회차는 AC-4 를 **종이 위에서** 판정했다: *"ERP=`MONO-515`, SCM=G2, Finance=G3 로
3개 도달 불가. 남는 후보는 WMS 출고 지시."* 실제로 눌러 보니 **정반대**였다.

| 도메인 | 판정 | 무엇을 눌렀나 | 근거 (상태만이 아니라 **상태 변화**) |
|---|---|---|---|
| ERP | ✅ | `/erp/masters` 부서 생성 | `POST /api/erp/masterdata/departments` → **201**, 목록 원소 **3 → 4** |
| SCM | ✅ | `/scm/config` 재주문 정책 upsert | `PUT …/policies/SKU-DEMO-A1` → **200**, 값 `42/12/77 → 55/15/88`, `version 7 → 8` |
| WMS | ❌ | 출고 지시 · 알림 확인 | 목록 **0건**(`BE-581`) · id 를 DB 에서 조달해 직접 호출해도 pick **403** · 알림 **0건** |
| Finance | ❌ | 환율 refresh · 대사 해소 · 계좌 개설 | **200 인데 no-op** · 대상 **0건** · 콘솔에 쓰기 라우트 **없음**(404) |

🔴 **왜 5회차가 반대로 적었나 — AC 의 술어가 아니라 "자연스러운 쓰기" 를 봤다.**
AC-4 가 요구한 것은 *아무* 쓰기 1건인데, 도메인마다 **가장 대표적인** 쓰기(PO 상신 ·
이체 · 결재 승인)만 후보로 두고 그것이 막히면 도메인 전체를 불가로 셌다. 실제로는
`/scm/config` 의 정책 upsert 가 supplier-mock 과 **무관하게** 열려 있고(G2 는 상신
경로만 막는다), 반대로 WMS 는 열려 있다고 본 출고 지시가 세 겹으로 막혀 있었다.
⇒ **"이 도메인에서 무엇이 막혔나" 가 아니라 "이 도메인에서 무엇이든 되나" 를 물어야 했다.**

## ② 🔴 계측기가 **양방향으로** 거짓말했다 — 이번엔 두 번 다 잡았다

| 방향 | 무슨 일 | 무엇이 고쳤나 |
|---|---|---|
| 거짓 **양성** | Finance 환율 refresh 가 **200** `{"feedEnabled":false,"refreshed":0}`. 상태코드만 봤으면 AC-4 를 충족으로 셌다 | 쓰기 **전후의 읽기**를 대조 — `{feedEnabled:false,n:0}` 불변 ⇒ no-op |
| 거짓 **음성** | SCM 정책 upsert 가 실제로 성립했는데 "상태 불변" 으로 찍혔다 | GET 과 PUT 의 **봉투가 다르다**(GET 은 404-as-empty-state 규약이라 `{found,value}` 로 감싼다). `j.data ?? j` 만 보던 추출기가 GET 을 `{}` 로 읽었다 |

⇒ **판정 규칙**: 쓰기 검증은 (a) 상태 (b) 본문 (c) **전후 읽기 차이** 셋을 따로 적는다.
(c) 가 없으면 200 은 아무것도 뜻하지 않는다.

🔵 그리고 **프로브 자신의 실패를 제품 판정으로 보고하지 않았다** — WMS 첫 판의
`400`/`422` 는 내가 목록 경로를 `/api/wms/outbound/orders`(→ `[orderId]="orders"`)로
잘못 잡고 pick 바디를 비워 보낸 결과였다. 계약을 읽고 고친 **뒤의** 200/403 만 위 표에 적었다.

## ③ 🔴🔴 실측이 진짜 결함을 하나 더 꺼냈다 — **시드 준비성 게이트가 erp 에만 있었다**

4회차가 `seed-erp.sh` 에 `wait_backend` 를 넣으며 *"`wait_http` 는 401 을 살아 있음으로
세므로 백엔드에 대해 아무것도 증명하지 않는다"* 라고 적어 두었다. 그 교훈이 **형제
스크립트로 전파되지 않았다**. 이번에 그것이 두 번 물었다:

```
demo-up.sh scm  직후 → 8건 전부 500   (게이트웨이는 이미 healthy)
demo-up.sh wms  직후 → 2건 전부 500
컨테이너가 healthy 가 된 뒤 같은 스크립트 재실행 → 양쪽 다 실패 0 으로 수렴
```

⇒ **시드가 아니라 게이트가 틀렸다.** 착수 시점 분포는 `wait_backend` 보유 **1/6**
(erp 만; wms·scm·finance·ecommerce·fan 없음). 처방:

- `wait_backend` 를 **`lib.sh` 로 승격**(절대 URL 을 받도록 일반화). 정의부 주석이
  세 도메인의 실측 지문을 함께 싣는다
- `seed-erp.sh` 의 지역 사본 삭제(정의 두 벌이 갈라지는 것을 막는다)
- `seed-scm.sh`(procurement · demand-planning · inventory-visibility 3개),
  `seed-wms.sh`(inbound · outbound 2개)에 배선. 🔵 **각각** 확인한다 — 한 서비스로
  나머지를 추정하면 같은 함정을 한 겹 아래에서 반복한다

🔵 **왜 finance 는 이번에 배선하지 않았나(조용한 누락 없이)** — account-service 에는
**2xx 를 내는 GET 이 없다**(목록 라우트 부재 = G3 와 같은 미완성; 단건 조회는 없는 id 에
404). 404 를 "도달함" 으로 세는 것은 `wait_http` 가 401 로 저지른 것과 **같은 종류의
느슨한 술어**라 넣지 않았다. ledger 쪽만 거는 반쪽 게이트도 넣지 않았다 — 그것이
"게이트가 있다" 는 인상을 주면서 정작 물지 않는다. 후속 티켓 후보로 남긴다.

## ④ 🔴 데모 재현성 함정 — **wms postgres 볼륨이 옛 자격증명으로 굳는다**

`demo-up.sh wms` 후 admin·inventory·inbound·master·outbound 가 **재시작 루프**에 빠졌다
(`Up 3초` 를 8분간 반복, `RestartCount=8`, `ExitCode=0`, OOM 아님). 원인:

```
inbound 로그  FATAL: password authentication failed for user "inbound"
컨테이너 env  DB_PASSWORD=inbound-changeme-local     ← projects/wms-platform/.env
볼륨 실측     PGPASSWORD=inbound 로 접속 성공        ← compose 의 `${…:-inbound}` 기본값
```

⇒ 볼륨이 **`.env` 가 안 실린 상태로 초기화**됐고, postgres init 은 빈 데이터 디렉터리
에서만 돌기 때문에 이후 어떤 기동도 이것을 고치지 못한다. **영구 불일치**다.
복구는 비파괴적으로 했다(6개 롤 `ALTER ROLE … WITH PASSWORD`, 데이터 보존) — 그 뒤
전 서비스가 healthy 로 회복했다.

🔴 이 실패는 **설정 오류처럼 보이지 않는다** — 앱 크래시 루프로 나타난다. 데모를
처음 돌리는 사람이 가장 만나기 쉬운 형태다. 가이드 한계 표에 넣는다.

## ⑤ 이번 회차에서 하지 **않은** 것

- **finance 준비성 게이트** — ③의 이유(느슨한 술어를 넣느니 안 넣는다)
- **G1~G3 제품 수정** — Scope 상 별도 티켓
- `wms-notification-service` 가 계속 `unhealthy` — 이 흐름의 경로가 아니라 미조사

---

# Acceptance Criteria

- [~] **AC-0 (재측정 + 테넌트 확인)** — 🔴 **1회차의 [x] 를 되돌린다.** 화면 모집단(23)은
      맞지만 **테넌트 판정이 틀렸다** — 2회차 ⓪ 참조. WMS 는 `BE-576` 형태로
      **실제로 블록된다**(`/wms/outbound`). 나머지 3도메인은 여전히 미측정.
      🔵 판정을 "테넌트 컬럼 있는 테이블 세기" 로 하지 말 것 — 아래 원문이 요구하는 것은
      **목록 API 원소 수 대 DB 실측의 대조**다.
      원문: 화면 모집단을 `console-nav-config.ts` 에서 다시 센다.
      **그리고 각 도메인이 `TASK-BE-576` 과 같은 테넌트 분리 증상을 갖는지 먼저 확인한다** —
      운영자 토큰으로 목록 API 를 호출해 **원소 수가 DB 실측과 일치하는지** 본다.
      200 은 판정 근거가 아니다(그 결함은 200 이었다). 불일치하면 이 티켓은 BE-576 에
      **블록된다** — 시드를 아무리 넣어도 화면은 비어 있다
      → **ERP 완료(4회차)**: 7개 목록의 **BFF 원소 수 = DB `COUNT(*)`** 전항 일치 ⇒
      BE-576 형 분리 **없음**. 🔵 `information_schema.table_rows` 는 InnoDB 추정치라
      판정에 쓰지 않았다. SCM/Finance 미측정
- [~] **AC-1 (API 우선)** — `operator_token` 으로 각 도메인의 쓰기 API 를 쓴다. 직접-DB 는
      `dbexec --why` 로만, 사유는 재검증 가능하게(막힌 엔드포인트와 실제 응답을 적는다)
      → **WMS 완료**: 입고 5단계 + 출고 주문 전부 실제 API. 직접-DB 는 outbound 스냅샷
      1건뿐이고 사유가 코드에 있다(`TASK-BE-580` 이 닫히면 삭제).
      → **ERP 완료(4회차)**: 마스터 5종 + 결재 3 + 위임 1 = **20건 전부 실제 API,
      직접-DB 0건**. SCM/Finance 미착수
      → **SCM 완료(5회차, 2026-08-07)**: config 4건 + PO 3건 전부 실제 API.
      직접-DB **1건(공급사)** — 사유가 `dbexec --why` 에 있다: **scm 에는 공급사 등록
      API 가 없다**(저장소 전 컨트롤러 0건, scm e2e 도 `ProcurementDbFixtures` 로 직접
      넣는다). 이 도메인에선 직접-DB 가 편법이 아니라 **유일한 경로**다.
      → **Finance 완료(5회차)**: 계좌 2 + KYC 승급 2 전부 실제 API, **직접-DB 0건**.
      🔵 원장은 **일부러 직접 넣지 않았다** — 이벤트 기반 전기가 `ensureAccountExists`
      로 계정을 자동 생성하므로, 이체가 성립했다면 `/ledger` 데이터는 *투영이 동작한다*
      는 증거가 됐을 것이다. 손으로 넣으면 *시드가 넣었다* 는 증거밖에 안 된다.
- [~] **AC-2 (라이브 검증)** — 23개 화면을 **브라우저로** 연다
      → **WMS 7화면 스윕 완료(3회차, 2026-08-06)**: 콘솔 기동 + 헤드리스 로그인 +
      `POST /api/tenant {"tenant":"demo-corp"}` 성립, 7화면 전부 `200`.
      🔴 **그러나 200 은 판정이 아니었다** — BFF 원소 수로 다시 재니
      **`/wms/inventory` 만 데이터가 있고, `/wms/inbound`·`/wms/outbound` 는 빈 배열,
      `/wms/operations` 는 403** 이었다. SCM/ERP/Finance 16화면 미착수.
      → **갱신(2026-08-06, BE-582 수정 후 재측정)**: `/wms/inbound` **0 → 2** ✅.
      남은 것은 `/wms/outbound` **0** (원인은 BE-582 가 아니라 **`TASK-BE-581`**
      테넌트 스코프 — 프로젝션을 고친 뒤에도 0 인 것으로 확정) 과 `/wms/operations`
      403(권한, `TASK-MONO-514`). ⇒ **WMS 목록 화면 3개 중 2개 충족.**
      🔴 **상류를 정적 grep 으로 정하지 마라** — `/wms/outbound` 의 상류를 소스에서
      `callWmsAdmin('/dashboard/orders')` 로 읽었는데, 그 함수는 **죽은 코드**였고
      실제로는 outbound-service 원시 API 였다(런타임 로그로 확정). 같은 이름의
      `listOrders` 가 코드베이스에 셋 있다.
      → **SCM 6화면 완료(5회차, 2026-08-07)**: 전 화면 `page 200`, BFF 원소 수 =
      `/scm/procurement` **3** · `/scm/config` 정책 **1** + 매핑 **1** ·
      `/scm/inventory` **0** · `/scm/replenishment` **0** (`/scm`·`/scm/guide` 는 데이터 무의존).
      ⇒ **4/6 충족.**
      → **Finance 4화면 완료(5회차)**: 전 화면 `page 200`, `/ledger` 는 trial-balance ·
      periods · fx-rates · discrepancies **전부 0**. `/finance/accounts` 는 **목록 라우트가
      아예 없다**(id 조회뿐) — 화면이 뜨지만 "무엇을 보여줄지" 를 운영자가 알 수 없다.
      🔴 **계측기가 거짓 양성을 냈다** — 처음엔 trial-balance/fx-rates 가 **1** 로 나왔는데
      실물은 `{"accounts":[],…}` 와 `{"feedEnabled":false,"rates":[]}` 로 **둘 다 빈 배열**
      이었다. 카운터의 폴백("키 있는 객체 → 1")이 껍데기를 셌다. 도메인 배열을 이름으로
      찾도록 고쳤다 — [[env_empty_detector_output_is_not_absence]] 의 역방향(부재를 존재로).
      ⇒ **23화면 중 WMS 7 + ERP 6 + SCM 6 + Finance 4 = 23 전부 측정 완료.**
      🔵 **계측 함정**: 콘솔 페이지는 **클라이언트 렌더**라 SSR HTML 에 데이터가 없다 —
      HTML grep 은 전 화면 0건을 내는 **깨진 탐지기**였다. 판정은 BFF API 원소 수로 한다
      (🔵 로그인 진입점도 `/` 가 아니라 **`/api/auth/login`** 이다 — `/` 는 콘솔 자체
      로그인 화면 HTML 만 주고 IAM CSRF 를 못 찾는다)
      → **ERP 6화면 스윕 완료(4회차)**: `/erp`·`/erp/guide`·`/erp/masters`(5탭 3·4·3·3·3)
      ·`/erp/delegation`(위임 1) 충족, `/erp/approval` 은 **목록 3 / 결재함 0**(`MONO-515`),
      `/erp/orgview` **0**(`ERP-BE-042`) ⇒ **4/6.** 🔴 페이지 HTML 로 degrade 를 판정하려던
      휴리스틱은 **오탐**을 냈다(정상 안내 문구에 매치) — 판정은 BFF 원소 수만.
      ⇒ 23화면 중 **WMS 7 + ERP 6 = 13 측정 완료**, SCM 6 · Finance 4 미착수
- [~] **AC-3 (프로젝션 의존 화면)** — ERP 통합 조회 · WMS 재고는 read-model 프로젝션에
      의존한다. **프로듀서만 시드하면 화면은 여전히 빈다** — 프로젝션 서비스 기동을 슬라이스
      정의에 포함하고, 프로젝션이 따라잡을 때까지 기다린 뒤 판정한다
      → **WMS 재고 충족**: 적치 확정 → Kafka → `inventory.available_qty=95`
      (`created_by=system:putaway-consumer`) 실측.
      → **ERP 도 충족**(2026-08-07): 위 판정은 `TASK-ERP-BE-042` 가 닫히며 **뒤집혔다** —
      아웃박스 릴레이가 `@EnableScheduling` 부재로 한 번도 안 돌던 것을 고치자 백로그가
      **전량 발행**되고 `/api/erp/read-model/employees` 원소수가 **0 → 4** 가 됐다.
      🔴 그 구분(지연 vs 발행 부재)은 **브로커 offset** 으로만 났다. 🔵 그리고 시드는
      **한 줄도 안 고쳤는데** `⛔ 차단` 줄이 `프로젝션 사원 4/4 반영 확인` 으로 뒤집혔다 —
      지문 기반 차단 분류가 의도대로 동작했다. 남은 read-model 공백은 위임 사실 뷰 하나이고
      원인은 별개다(`TASK-ERP-BE-043`, approval 봉투가 `aggregateId`/`tenantId` 미탑재)
- [~] **AC-4 (대표 쓰기)** — 도메인마다 최소 1건의 쓰기 동작이 브라우저에서 성공한다
      → **4도메인 전부 실측 완료(6회차, 2026-08-07). 결과 2/4.** 상세는 아래 6회차 ①.
      🔴 그리고 이 AC 의 5회차 판정("ERP=MONO-515·SCM=G2·Finance=G3 로 3개 도달 불가,
      남는 건 WMS")은 **WMS 와 SCM 에서 정확히 뒤집혀 있었다** — 실측은
      **ERP ✅ · SCM ✅ · WMS ❌ · Finance ❌** 다
- [~] **AC-5 (멱등)** — 각 시드를 연속 2회 실행해도 수렴한다
      → **WMS 충족**: 2회차 `생성 0 · 기존 2 · 실패 0`, rc=0
      → **ERP 충족**: 깨끗한 볼륨 1회차 `생성 20 · 기존 0 · 실패 0`, 2회차
      `생성 0 · 기존 20 · 실패 0`, 양쪽 rc=0
      → **SCM 충족**: 3회 실행 후 `purchase_orders` **3행**, `suppliers` **1행** 수렴.
      🔴 로그 라벨만으로 판정하면 틀린다 — 멱등 replay 도 2xx 라 첫 판은 "생성"이 9번
      찍혔는데 실제 행은 3이었다. 그래서 시드가 **POST 전후 행 수**를 세어 갈라 적도록
      고쳤다(`존재 … (멱등 replay — 행 수 N 불변)`).
      → **Finance 충족**: 여러 회 실행 후 `finance_db.accounts` **2행**(둘 다 ACTIVE/FULL),
      `idempotency_keys` 6행. mysql 자격증명이 컨테이너 env 안이라 scm 식 행-수 판정을
      스크립트에 넣지 않았고, **그 한계를 주석에 명시**했다(로그는 요청 수, 수렴 증거는 행 수).
- [~] **AC-6 (메모리 실측)** — S4 · S5 각각의 컨테이너 수 + 메모리를 기록한다.
      **S5 가 로컬에서 아예 불가능하면 그 사실을 수치와 함께 적는다**(MONO-399 AC-2 의 입력이며,
      "못 했다" 도 유효한 측정이다)
      → 축소 WMS 슬라이스(10컨 ≈ 1.9 GiB) 실측. 🔴 1회차의 5,589 MiB 는 관측 사이드카
      포함값이었다 — **앱만이면 1/3**. S5 판정을 그 수치로 다시 해야 한다
      → **ERP 슬라이스 8컨 = 3,854 MiB** 실측(iam+console 포함 25컨에서 VM 9.85/11.96 GiB).
      ⇒ **S5 동시 기동은 여전히 불가**로 판정한다 — erp 하나가 3.85 GiB 이므로
      scm(5앱)+erp(5)+finance(3)+iam+console 은 10 GiB 를 넘긴다. 도메인별 슬라이스가 정답
      → **5회차 확증(2026-08-07)**: 착수 시 Docker VM **8.95 / 11.68 GiB**(iam 14 + erp 8 +
      console 2)로 여유 2.7 GiB 였고 scm(5앱)+finance(3앱)+인프라는 ≈5 GiB 가 필요했다
      ⇒ **S5 동시 기동 불가 재확인**. 2패스로 갈랐다: erp 내림(→4.67 GiB) → scm 8컨 →
      측정 → scm 내림 → finance 7컨 → 측정. 🔴 **호스트 RAM 이 진짜 제약이다** — 호스트
      여유 1.0 GiB, `vmmemWSL` 4.44 GB 로 컨테이너 사용량을 따라간다(VM 이 반환한다).
      🔵 그리고 **디스크는 제약이 아니었다** — 이미지 8개(≈3.7 GB)는 vhdx 안에 들어가고
      거기엔 18.4 GB 회수분이 있었다. 착수 전 "디스크 때문에 막혔다" 는 전제가 틀렸다.
      🔴 `demo-up.sh <도메인>` 은 **의존 도메인의 스택 전체를 다시 올린다** — 메모리를
      아끼려 내려 둔 iam 관측 컨테이너 6개가 되살아났다. 부분 종료로 아낀 분은 상쇄된다.
# 🔴 5회차(SCM · Finance)가 찾은 구조적 갭 3건 — 후속 티켓 후보

셋 다 **시드의 한계가 아니라 제품의 상태**다. 시드는 이것들을 우회하지 않고 관측으로 남겼다.

| # | 도메인 | 갭 | 실측 근거 | 막히는 화면 |
|---|---|---|---|---|
| G1 | scm | **공급사 등록 API 가 없다** | `POST /po` 가 `SUPPLIER_NOT_FOUND`; 저장소 전 컨트롤러에 suppliers 생성 매핑 **0건**; scm e2e 도 `ProcurementDbFixtures.insertActiveSupplier` 로 직접 DB | 공급사 관리 화면 자체가 없음 |
| G2 | scm | **PO 가 DRAFT 밖으로 못 나간다** | `submit` 이 `http://supplier-mock:9090` 을 실제 호출하는데 그 서비스가 **어느 compose 에도 없다**(application.yml 의 URL 로만 존재). federation 스펙 주석도 같은 말 | `/scm/procurement` 이 DRAFT 만 |
| G3 | finance | **계좌에 돈을 넣을 수 없다** | deposit/topup/credit 매핑 **0건**; `topUp()` 은 있으나 *"internal funding (v1 stub)"* 이고 **프로덕션 호출자 0건**(테스트 6회만); `@KafkaListener` **0건** | `/ledger` 4피드 전부 0 |

**G3 가 가장 넓다** — 자금 이동이 불가능하므로 이체·홀드·캡처가 전부 도달 불가이고,
전기가 없으니 원장 전 화면이 빈다. `/finance/accounts` 에 **목록 라우트가 없는 것**
(id 조회뿐)도 같은 방향의 미완성이다.

🔵 **셋 다 "고칠 수 있었지만 안 한" 것이 아니다.** G1 은 직접-DB 로 우회했고(그 외 길이
없다), G2·G3 는 우회하면 화면은 차지만 **제품이 하지 못하는 일을 시드가 대신한 것**이
되므로 하지 않았다. 데모에서 그 화면이 비는 것은 정직한 상태다.

- [x] **AC-7 (가드)** — `verify-demo-wrapper.sh` 가드 (y) 통과
      → PASS(rc=0). (y) 가 "도메인 시드 **4개**(erp 추가) · 전부 dbexec 경유" 로 센다
- [~] **AC-8 (발굴 결함 분리)** — 별도 티켓. 0건이면 "0건" 이라고 적는다
      → **WMS 3건**: `TASK-BE-579`(서비스가 HTTP 서빙을 멈추고 갇힘) ·
      `TASK-BE-580`(outbound 만 `db/seed` 없음) · `TASK-BE-581`(데모 운영자가 자기가 만든
      출고 주문을 못 본다 — 1회차 AC-0 을 뒤집는 건).
      → **ERP 3건**: `TASK-ERP-BE-041`(상신이 항상 422 — 내부 호출이 토큰 없이 나가 401) ·
      `TASK-ERP-BE-042`(아웃박스 릴레이가 한 번도 안 돈다 — `@EnableScheduling` 부재,
      erp 만 형제 파리티 낙오) · `TASK-MONO-515`(콘솔 운영자가 결재함을 영원히 못 쓴다 —
      assume 토큰 `sub` 가 클라이언트 id).
      🔵 MONO-515 는 **결함 단정 직전에 멈춰서** iam IT 의 반대 단언을 찾아냈고, 그래서
      "버그 수정" 이 아니라 **ADR 필요**로 방향이 바뀌었다.
      → **SCM 2건 + Finance 1건 (6회차 파일 완료)**: `TASK-SCM-BE-059`(공급사 등록 API
      부재 — 저장소 전 컨트롤러 0건, 제품 자신의 e2e 도 DB 픽스처를 쓴다) ·
      `TASK-SCM-BE-060`(상신이 `supplier-mock` 을 실제 호출하는데 그 서비스가 어느
      compose 에도 없다 — 🔴 착수 전 AC-0 결정 필요) · `TASK-FIN-BE-068`(계좌에 돈이
      들어갈 길이 없다 — 매핑 0건 · stub 의 프로덕션 호출자 0건 · `@KafkaListener` 0건).
      🔵 셋 다 **"고칠 수 있었지만 안 한" 것이 아니다** — G2·G3 를 우회하면 화면은 차지만
      *제품이 하지 못하는 일을 시드가 대신한 것*이 된다. 데모에서 그 화면이 비는 것은
      정직한 상태다

---

# Related Specs

- `infra/demo/seed/README.md` — 시드 규약 + 도메인 추가 절차
- `infra/demo/seed/seed-ecommerce.sh` — 선례
- 각 프로젝트 `specs/` — **enum·필수 컬럼의 권위는 DB 제약이지 spec 이 아니다**

# Edge Cases

- WMS 출고는 외부 TMS 스텁에 의존한다(`WMS_TMS_BASE_URL` = 도달 불가 스텁) — 서킷브레이커로
  degrade 하는 것이 정상 경로인지 확인
- ERP 결재는 위임 규칙과 얽힌다 — 결재함이 비지 않으려면 결재선이 필요하다
- Finance 시산표는 원장 마감 상태에 의존한다
- SCM 보충 추천은 `sku-supplier-map` 이 없으면 `SKU_SUPPLIER_UNMAPPED` 다 —
  `/scm/config` 가 그 fix-path 다(시드가 그것을 써야 한다)

# Failure Scenarios

- **BE-576 을 확인하지 않고 착수** → 시드는 201 인데 화면은 계속 빈다. AC-0 이 막는다
- **프로젝션을 기다리지 않고 판정** → 방금 넣은 것이 아직 안 보이는데 "빈 화면" 으로 오진
- **S5 를 로컬에서 강행** → 호스트 OOM. 소배치 기동(2~3 서비스씩)과 실측 기록이 정답

# Definition of Done

- [x] **도메인 시드 4개 커밋** — `seed-wms.sh` · `seed-erp.sh` · `seed-scm.sh` · `seed-finance.sh`.
      각 2회 이상 실행하고 **수렴을 행 수로** 확인(AC-5)
- [x] **23개 화면 증거** — WMS 7 + ERP 6 + SCM 6 + Finance 4. 전부 `page 200`,
      판정은 **BFF 원소 수**로(클라 렌더라 SSR HTML 은 데이터가 있어도 0건)
- [x] **S4 · S5 메모리 실측 기록** — S5 동시 기동 **불가** 재확인, 2패스로 수행(AC-6)
- [x] 가이드 한계 표 갱신 — WMS·ERP 에 더해 **SCM·Finance 행(G1~G3)** 과 6회차가 찾은
      **wms postgres 볼륨 자격증명 함정**을 반영
- [~] **AC-4(브라우저 쓰기)** — **4도메인 전부 실측, 2/4 충족**(ERP ✅ · SCM ✅ ·
      WMS ❌ · Finance ❌). 6회차 ① 참조. 🔴 5회차의 "3개 도달 불가" 명단은 **WMS 와
      SCM 에서 뒤집혔다** — 원인은 제품이 아니라 판정 방식이었다(대표 쓰기만 후보로 봤다).
      **남은 2개는 이 티켓에서 못 닫는다** — 둘 다 제품 상태이고 Scope 가 제품 코드
      변경을 배제한다:
      - WMS — 세 겹(`TASK-BE-581` 테넌트 스코프로 목록 0건 · pick 403 · 알림 0건)
      - Finance — 콘솔에 동작하는 쓰기 라우트가 없다(`TASK-FIN-BE-068`)
- [x] **시드 준비성 게이트 파리티** — `wait_backend` 를 `lib.sh` 로 승격하고 scm·wms 에
      배선(6회차 ③). 착수 시점 보유 1/6 → 3/6. finance 는 **의도적 미배선**(2xx GET
      부재; 느슨한 술어를 넣지 않는다)
- [x] Ready for review
