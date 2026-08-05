# Task ID

TASK-MONO-510

# Title

WMS · SCM · ERP · Finance 도메인 데이터 시드 — 콘솔 도메인 운영 23개 화면을 채운다

# Status

ready

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

- WMS **흐름** 시드(ASN → 검수 → 적치 → 재고 → 출고주문). 스크립트를 작성했으나 위 500
  때문에 **한 번도 성공하지 못했고, 그래서 커밋하지 않았다** — 이 티켓의 전제
  ("검증하지 않은 시드는 거짓 약속")를 스스로 어기지 않기 위해서다. 엔드포인트·DTO·역할은
  전부 위에 적어 두었으므로 재작성 비용은 낮다. 🔴 출고는 **주문까지**가 현실적 목표다
  (예약은 `INVENTORY_RESERVE` 필요, 배송은 도달 불가 TMS 스텁 의존).
- SCM · ERP · Finance — 미착수. 이미지도 없다(scm 5 · finance 3 서비스 빌드 필요;
  erp 는 이미지 존재).

---

# Acceptance Criteria

- [x] **AC-0 (재측정 + 테넌트 확인)** — 화면 모집단을 `console-nav-config.ts` 에서 다시 센다.
      **그리고 각 도메인이 `TASK-BE-576` 과 같은 테넌트 분리 증상을 갖는지 먼저 확인한다** —
      운영자 토큰으로 목록 API 를 호출해 **원소 수가 DB 실측과 일치하는지** 본다.
      200 은 판정 근거가 아니다(그 결함은 200 이었다). 불일치하면 이 티켓은 BE-576 에
      **블록된다** — 시드를 아무리 넣어도 화면은 비어 있다
- [ ] **AC-1 (API 우선)** — `operator_token` 으로 각 도메인의 쓰기 API 를 쓴다. 직접-DB 는
      `dbexec --why` 로만, 사유는 재검증 가능하게(막힌 엔드포인트와 실제 응답을 적는다)
- [ ] **AC-2 (라이브 검증)** — 23개 화면을 **브라우저로** 연다
- [ ] **AC-3 (프로젝션 의존 화면)** — ERP 통합 조회 · WMS 재고는 read-model 프로젝션에
      의존한다. **프로듀서만 시드하면 화면은 여전히 빈다** — 프로젝션 서비스 기동을 슬라이스
      정의에 포함하고, 프로젝션이 따라잡을 때까지 기다린 뒤 판정한다
- [ ] **AC-4 (대표 쓰기)** — 도메인마다 최소 1건의 쓰기 동작이 브라우저에서 성공한다
      (예: WMS 출고 지시, ERP 결재 승인, Finance 거래 등록)
- [ ] **AC-5 (멱등)** — 각 시드를 연속 2회 실행해도 수렴한다
- [ ] **AC-6 (메모리 실측)** — S4 · S5 각각의 컨테이너 수 + 메모리를 기록한다.
      **S5 가 로컬에서 아예 불가능하면 그 사실을 수치와 함께 적는다**(MONO-399 AC-2 의 입력이며,
      "못 했다" 도 유효한 측정이다)
- [ ] **AC-7 (가드)** — `verify-demo-wrapper.sh` 가드 (y) 통과
- [ ] **AC-8 (발굴 결함 분리)** — 별도 티켓. 0건이면 "0건" 이라고 적는다

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

- [ ] 도메인 시드 4개 커밋 + 각 2회 실행
- [ ] 23개 화면 브라우저 증거
- [ ] S4 · S5 메모리 실측 기록
- [ ] 가이드 한계 표 갱신
- [ ] Ready for review
