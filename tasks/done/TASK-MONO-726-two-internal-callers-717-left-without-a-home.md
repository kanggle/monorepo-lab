# Task ID

TASK-MONO-726

# Status

done (2026-10-04 UTC — 4차원 검증 · PR #3991 squash `83e802c7a`)

# Title

🔴 **717 이 «한계» 로 적고 떠난 내부 호출자 둘 — 집이 없었다**: `lockAccount` 의 iam 게이트웨이 라우트 부재 · batch-worker 의 미등록 client

# Owner

미지정

# Task Tags

- iam-platform
- ecommerce
- internal-caller
- fail-soft

---

> **분석 모델:** Opus 5.5 / **구현 권장:** AC-0 = Sonnet(측정) · 이후 갈래는 AC-0 결과를 보고 다시 정한다(인증 경로라면 Opus).

---

# Goal

`TASK-MONO-717` § AC-3 의 표가 «이 수정으로도 안 보이는 채로 남는 경로» 다섯 줄을 적었고, 그중 **앞의 둘만** 717 의 축(「설정/등록이 없어서 조용히 죽는다」)이라고 명시했다. 그 둘은 아래와 같다.

| 경로 | 717 이 적은 것 | 실제 집 (2026-09-24 확인) |
|---|---|---|
| ① `POST /internal/accounts/{a}/lock` (product-service `SellerAccountProvisioner.lockAccount`) | iam 게이트웨이에 **라우트가 없다** — `TASK-MONO-713` ⓑ 소관 | 🔴 **없음.** `tasks/done/TASK-MONO-713-…` 본문에 `lock` 이 **한 번도 안 나온다.** 게이트웨이 `application.yml` 의 내부 라우트는 지금도 `Path=/internal/tenants/**` 하나뿐 |
| ② batch-worker 의 `ecommerce-internal-services-client` | 그 client_id 가 **IdP 에 없다** — 실제로 쓰는지 안 쟀다 | 🔴 **없음.** 활성 큐 어디에도 그 이름이 없다(717 파일에만 있다) |

717 은 `TASK-MONO-721` 의 창 판정(2026-09-24 PASS)으로 닫힐 수 있게 됐는데, 닫히면 `done/`(frozen)이고 **이 두 줄은 다시 읽히지 않는다.** 이 티켓이 그 두 줄의 집이다.

🔴 **목표는 «고친다» 가 아니라 «먼저 잰다» 다.** 두 줄 모두 717 이 **측정하지 않고** 적은 한계다. 측정 전에 라우트를 뚫거나 client 를 등록하면, 717 AC-3 이 경고한 대로 «명단이 근거 없이 자란다».

# Scope

## 포함

- AC-0: 두 경로를 각각 **실측**한다(아래).
- 측정 결과에 따른 갈래 제안 → 소유자 결정.
- 결정된 갈래의 구현(별도 impl PR — 이 티켓의 spec PR 과 섞지 않는다).

## 제외

- 717 § AC-3 표의 나머지 셋(`AccountCreatedEventConsumer` · `NotificationSendService` · `OrderPiiAnonymizationService`/`AccountDeletedConsumer`) — 717 이 **의도된 fail-soft** 라고 가려 둔 것이다. 섞지 않는다.
- `ADR-MONO-076` 의 카탈로그 확장 — ②를 등록하게 되더라도 그 client 가 어느 테넌트를 assume 할 수 있는지는 ADR 의 규칙대로 따로 정한다.

# Acceptance Criteria

## AC-0 — 🔴 먼저 잰다 (고치기 전에)

- [x] **①** 셀러를 정지(SUSPEND/CLOSE — `lockAccount` 를 부르는 전이)시켰을 때
      - product-service 가 어느 URL 을 부르는가(설정값 · 로그),
      - 응답이 **404(라우트 없음)** 인가, 그 밖의 무엇인가,
      - 그리고 🔴 **결과 상태** — `account_db.accounts.status` 가 바뀌었는가. 로그 침묵은 판정이 아니다(717 이 못박은 술어).
- [x] **②** batch-worker 가 `/internal/**` 를 **실제로 부르는가**(코드 경로 + 설정 + 가능하면 라이브 로그). 🔴 안 부르면 등록하지 않는 것이 답이고, 그 판정을 적는 것으로 ②는 닫힌다.
- [x] 두 결과를 이 파일에 적고, 갈래를 소유자에게 묻는다.

## AC-1 — (AC-0 이 결함으로 판정한 것만) 고친다 + bite

- [x] 고친 뒤 결과 상태(①이면 `accounts.status`)로 판정한다. 단위 초록으로 닫지 않는다 — 717·718·721 이 **연속 세 번** 단위/로컬 초록 → 창 판정을 거쳤다.

## AC-2 — 가드 범위

- [x] `scripts/check-internal-caller-addresses.sh` 의 호출자 목록(지금 한 줄)에 넣을 대상이 생기면 그 파일 헤더의 규칙(«실제로 `/internal/**` 을 부르는가» 먼저)대로 넣는다.

# Related Specs

- `tasks/done/TASK-MONO-717-…` § AC-3 (출처 표) · § CORRECTION 두 절
- `tasks/done/TASK-MONO-713-apply-rule-5-at-the-iam-gateway-edge.md` (717 이 가리킨 곳 — lock 을 다루지 않았다)
- `docs/adr/ADR-MONO-076-which-workload-credential-may-act-on-which-tenant.md`
- `projects/iam-platform/apps/gateway-service/src/main/resources/application.yml` (내부 라우트)

# Related Contracts

- `projects/iam-platform/specs/contracts/` 의 account-service 내부 API(`/internal/accounts/**`) — 라우트를 여는 갈래라면 **계약 먼저**.
- `platform/contracts/jwt-standard-claims.md` § 워크로드 테넌트 축(721 AC-3).

# Edge Cases

| 상황 | 기대 |
|---|---|
| ① 이 404 인데 account-service 쪽 상태가 다른 경로(이벤트)로 이미 바뀐다 | 결함이 아니라 **이중 경로**다 — 결과 상태로 판정하고, 죽은 HTTP 호출을 지울지가 질문이 된다 |
| ② 의 client 를 batch-worker 가 설정에만 들고 호출하지 않는다 | 등록하지 않는다. 설정에서 지울지는 별개 판단 |
| 데모 창 없이 ①을 재야 한다 | 로컬 compose 로 가능한지 먼저 본다. 안 되면 `TASK-MONO-672` 가 아니라 **이 티켓**에 창 항목을 적는다(672 는 «닫히면서 집을 잃는» 의무만 받는다) |

# Failure Scenarios

1. 🔴 **재지 않고 게이트웨이에 `/internal/accounts/**` 라우트를 연다** → 717 이 밟은 그 모양이다(게이트웨이 경유는 `TenantScopeGuard` 와 부딪힐 수 있다 — 717 § CORRECTION 의 403 `TENANT_SCOPE_DENIED`). 라우트가 답인지부터 잰다.
2. 🔴 **②를 「IdP 에 없으니 등록」 으로 닫는다** → 쓰지 않는 자격이 명단에 생긴다. 717 AC-3 이 이미 경고했다.
3. **717 을 done 으로 옮기면서 이 두 줄을 그 파일에만 남긴다** → 이 티켓이 없었을 때의 상태. 이 티켓의 존재 이유다.

---

# 측정 기록

## AC-0 ② — 🔴 batch-worker 는 그 client 를 **쓴다**, 그리고 그 경로는 **세 군데서 동시에 끊겨 있다** (2026-09-24 UTC · 창 없음 · 정적 측정)

🔵 먼저 정정: 이 티켓 § Goal 은 ②를 «`/internal/**`(iam) 호출자» 처럼 적었다. 아니다 — batch-worker 가 그 자격으로 부르는 곳은 **order-service 의 `/api/internal/orders/**`** 다.

| 층 | 저장소가 말하는 것 | 결과 |
|---|---|---|
| 호출 | `OrderServiceClient` — `POST /api/internal/orders/confirm-paid-stale`(`StalePaidOrderConfirmationJob`) · `POST /api/internal/orders/existence`(`OrphanCouponReleaseJob`) — 둘 다 `IamClientCredentialsTokenProvider` 로 bearer 를 붙인다 | **쓴다** ⇒ «안 쓰면 등록하지 않는다» 갈래는 닫혔다 |
| 수신 | order-service `OrderSecurityConfig` — `/api/internal/**` 는 resource-server 체인, `sub` 가 `order.internal.oauth2.allowed-client-ids`(기본 **`ecommerce-internal-services-client`**)여야 통과 (TASK-BE-505) | 그 client 의 토큰만 받는다 |
| 🔴 ① IdP 등록 | `projects/iam-platform/apps/auth-service/src/main/resources/db/**` 에 그 client_id **0건** | 토큰 발급 불가(`invalid_client` 예상 — 717 이 product-service 에서 본 모양) |
| 🔴 ② 토큰 주소 | batch-worker `application.yml:89` 기본 `http://iam-service:8081/oauth2/token` · 에코머스 `docker-compose.yml` batch-worker 블록(900–938행)에 `IAM_TOKEN_URI` **없음**. `infra/demo/demo.env:91` 의 `IAM_TOKEN_URI` 는 compose 가 그 키를 **선언한 서비스에만** 들어간다 | `iam-service` 호스트는 어느 compose 에도 없다 ⇒ 연결 실패 |
| 🔴 ③ order-service 주소 | batch-worker `application.yml:81` 기본 `http://order-service:8082` · compose 에 `ORDER_SERVICE_BASE_URL` **없음**. order-service 는 `SERVER_PORT=8086`(compose 823행 · `application.yml` `port: 8086`), 형제 서비스들은 `ORDER_SERVICE_URL=http://order-service:8086` | **포트가 틀렸다** |

⇒ 🔴 **셋 중 하나만 고치면 다음 것에서 죽는다.** 717 이 product-service 에서 «주소 → 등록 → 테넌트» 순으로 한 겹씩 벗겨 낸 모양과 같다 — 이번엔 **세 겹이 처음부터 보인다.**
⇒ 두 잡은 `log.error("StalePaidOrderConfirmationJob FAILED …")` 로 **로그에는 남지만** 아무 화면도 빨개지지 않는다(717 의 축 그대로). `enabled` 기본값은 둘 다 `true`.

🔵 **아직 안 잰 것 (창 또는 로컬 compose)**: 실제 로그에 그 FAILED 줄이 주기적으로 찍히는가 · 그 결과로 PAID 에 머무는 주문이 데모에 있는가(결과 상태). 🔴 로그 침묵이 판정이 아니듯 **로그 발화도 결과 상태가 아니다** — 판정은 `orders` 테이블의 PAID 체류 건수다.

🔵 **가드와의 관계**: `scripts/check-internal-caller-addresses.sh` 가 717 AC-2 로 «설정이 없으면 조용히 localhost» 를 문다 — 호출자 목록이 한 줄(product-service)이라 **batch-worker 는 그 가드 밖**이다. ②·③ 은 정확히 그 가드가 무는 모양(설정 부재 → 코드 기본값)이고, 기본값이 `localhost` 가 아니라 **존재하지 않는 호스트/틀린 포트**라는 것만 다르다. ⇒ AC-2 에서 목록에 넣을 근거가 이 측정이다(파일 헤더 규칙 «실제로 부르는가» 충족).

## AC-0 ① — ⏳ 창 필요 (변동 없음)

정적으로 확인한 것만: iam 게이트웨이 내부 라우트는 `Path=/internal/tenants/**` 하나(`gateway-service/src/main/resources/application.yml:61`) ⇒ `lockAccount` 의 `/internal/accounts/{a}/lock` 은 **라우트 없음**. 판정(결과 상태 `accounts.status`)은 셀러 정지를 일으켜야 하므로 창/로컬 compose.

## AC-0 ② 추가 — 🔴 **받는 쪽도 두 군데서 끊겨 있다**, 그리고 내 전제 하나가 틀렸다 (2026-09-24 UTC · 창 없음)

🔴 **정정**: 같은 날 세션 요약에서 «batch-worker 를 고치려면 ADR-MONO-076 에 따라 이 client 가 assume 할 테넌트부터 정해야 한다» 고 적었다. **아니다.** order-service `OrderSecurityConfig` 의 `/api/internal/**` 체인은 서명 · 시각 · **issuer** · **`sub` 허용목록**(TASK-BE-505)만 본다 — **테넌트 클레임을 안 읽는다.** 717/721 이 부딪힌 것은 iam 게이트웨이 + `TenantScopeGuard` 쌍이었고, 이 경로에는 그 쌍이 없다. ⇒ 721 식 테넌트 assume 은 **필요 없다.**

그 대신 받는 쪽 설정이 비어 있다 — 에코머스 `docker-compose.yml` 의 order-service 블록에 `ORDER_INTERNAL_OAUTH2_*` **0개**:

| 층 | 코드 기본값 (`order-service/application.yml:82–83`) | 실제 | 결과 |
|---|---|---|---|
| 🔴 ④ JWKS | `http://auth-service:8081/oauth2/jwks` | `auth-service` 는 iam 프로젝트 네트워크의 컨테이너(`iam-auth-service-1`) — ecommerce 네트워크에서 그 이름은 해소되지 않는다(717 이 측정한 «공유 네트워크 없음») | 서명 검증 불가 → 401 |
| 🔴 ⑤ issuer | `http://auth-service:8081` | 2026-09-24 창에서 디코드한 실제 cc 토큰 `iss` = **`https://auth.hubwang.com`** (721 § AC-4 ③ 원문) | issuer 불일치 → 401 |

⇒ **다섯 겹**이다: 보내는 쪽 ①등록 ②토큰주소 ③order 포트 + 받는 쪽 ④JWKS ⑤issuer. 🔴 앞 셋만 고치면 이 경로는 **401 로** 죽는다 — 그리고 잡은 `log.error` 만 남기므로 «고쳤는데 여전히 조용히 실패» 가 된다.

🔵 **갈래 (소유자 결정용 — 아직 결정 아님)**:
- 등록(①)은 **자격증명 하나를 새로 만드는 일**이다 — 보안 표면. scope·허용 grant·secret 출처를 정해야 한다(717 의 `product-service-client` 등록이 선례: `V0036`).
- ②~⑤ 는 배선이다. 🔴 717 AC-2 의 가드(`check-internal-caller-addresses.sh`)가 무는 모양(«설정 없으면 코드 기본값») 그대로이고, 기본값이 `localhost` 가 아니라 **없는 호스트/틀린 포트/틀린 issuer** 라는 것만 다르다 — 가드의 호출자·수신자 목록 확장이 같은 PR 에 들어가야 재발을 문다.
- 판정은 결과 상태: 데모에서 결제 뒤 PAID 에 머무는 주문이 `older-than-minutes`(30) 뒤 CONFIRMED 로 넘어가는가.

---

# 구현 기록 (2026-09-24 UTC · 분석=Opus 5.5)

## 소유자 결정

«추천대로 진행» — **`ecommerce-internal-services-client` 의 IdP 등록을 승인**하고 다섯 곳을 한 PR 로.
🔵 등록은 새 권한을 만드는 것이 아니다: order-service 는 이미 `/api/internal/**` 에서 **정확히 이 client id 하나**만 받도록 핀돼 있었다(TASK-BE-505). 설계가 비워 둔 자리를 채운 것이다.

## AC-1 — ② 를 고쳤다 (다섯 겹)

| # | 층 | 고침 |
|---|---|---|
| ① | IdP 등록 | `auth-service` **`V0038__seed_ecommerce_internal_services_client.sql`** — V0036 과 같은 모양(`client_credentials` 하나 · `internal.invoke` · `global-account-platform`/`INTERNAL` · 공유 BCrypt "secret"). `WorkloadRoleCatalog` 에 **빈 맵**으로 명시 + `WorkloadRoleCatalogTest` 인구 17/11/6 → **18/12/6** |
| ② | 보내는 쪽 토큰 주소 | 에코머스 compose batch-worker `IAM_TOKEN_URI=${IAM_TOKEN_URI:-http://iam.local/oauth2/token}` — 데모는 기존 `demo.env` 의 값을 그대로 받는다 |
| ③ | 보내는 쪽 order 주소 | batch-worker `ORDER_SERVICE_BASE_URL=http://order-service:8086`. 🔵 틀린 기본값 `:8082` 는 **product-service 의 포트**였다 |
| ④ | 받는 쪽 JWKS | order-service `ORDER_INTERNAL_OAUTH2_JWK_SET_URI` — 로컬 `http://iam.local/oauth2/jwks`, 데모 `http://iam.${DEMO_DOMAIN}/oauth2/jwks`(같은 망의 product-service 가 09-24 창에서 그 호스트의 `/oauth2/token` 에 **실제로 도달**) |
| ⑤ | 받는 쪽 issuer | order-service `ORDER_INTERNAL_OAUTH2_ISSUER` — 로컬 `http://iam.local`, 데모 `${IAM_PUBLIC_URL}`(= 실제 토큰 iss `https://auth.hubwang.com`) |

🔵 **테넌트 assume 은 넣지 않았다** — 이 경로엔 테넌트 핀이 없다(§ AC-0 ② 추가의 정정).

## AC-2 — ✅ 가드 확장 + bite

`scripts/check-internal-caller-addresses.sh` 의 목록 1 → **3행**(batch-worker 보내는 쪽 · order-service 받는 쪽). 둘 다 «실제로 부르는가/받는가» 를 코드로 확인하고 넣었다(파일 헤더 규칙).

```
real run                                   → checked=6 · rc=0
bite: batch-worker ORDER_SERVICE_BASE_URL 삭제   → rc=1 · DRIFT § batch-worker
bite: order-service ORDER_INTERNAL_OAUTH2_ISSUER 삭제 → rc=1 · DRIFT § order-service
복원                                        → rc=0
```

🔴 **self-test 가 한 번 깨졌고 그 모양을 적는다**: 픽스처에는 product-service 블록 하나뿐인데 목록이 셋으로 자라자 새 두 서비스가 `MISSING` 이 돼 (a)「있다 → 통과」가 실패했다. self-test 는 **술어**(블록 자르기 · 키 존재 · 비공허성)를 재는 것이지 목록을 재는 것이 아니므로, self-test 안에서 **자기 목록 한 행**으로 돌게 했다. ⇒ 목록이 자라도 술어 시험이 오염되지 않는다.

## 게이트 기록

| 게이트 | 결과 |
|---|---|
| `auth-service:test` 전체 | 🟢 rc=0 · **724 tests · 0 fail · 0 error · 28 skip**(XML 합산, skip 은 기존) |
| `WorkloadRoleCatalogTest` bite | 🟢 카탈로그 새 줄 삭제 → **9칸 중 1 FAILED**(rc=1) → 복원 초록 |
| `check-internal-caller-addresses.sh` | 🟢 self-test 3칸 · 실제 6키 · 새 두 행 bite 각각 rc=1 |
| `check-flyway-version-collision.sh` · `check-dev-seed-migration-band.sh` · `check-flyway-unresolvable-placeholder.sh` | 🟢 rc=0 (359 마이그레이션, 플레이스홀더 0) |
| `infra/demo/verify-demo-wrapper.sh` (정적) | 🟢 rc=0 — demo.env 에 두 키를 더한 뒤 |
| 필수 3종 | 🟢 rc=0 (스테이지 후) |

🔴 **안 돌린 것**: ecommerce·iam 통합(Testcontainers) · e2e · **데모 창**. 🔴🔴 그리고 무엇보다 — **이 경로가 실제로 PAID 주문을 CONFIRMED 로 넘기는지는 안 쟀다.** 717·718·721 이 연속으로 보인 대로 배선은 고침이 아니다. ⇒ `TASK-MONO-672` 항목 14.

## ⏳ 창으로 넘긴 것 → `TASK-MONO-672` 항목 14

- ② 결과 상태: 신선 볼륨(재굽기 필요 — V0038 과 compose 가 구워지는 표면)에서 batch-worker 로그에 `StalePaidOrderConfirmationJob FAILED` 가 **없고**, 결제 뒤 PAID 인 주문이 30분 뒤 CONFIRMED 로 넘어가는가.
- ① `lockAccount` 의 `/internal/accounts/{a}/lock` — 셀러 정지를 일으켜 `accounts.status` 로 판정(§ AC-0 ①, 변동 없음).

---

## CORRECTION (2026-09-26 UTC) — AC-0 두 결과 + 소유자 결정

`TASK-MONO-672` 항목 14 가 15차 AMI 창(2026-09-26 UTC)에서 두 결과를 냈고, 소유자가 갈래를 결정했다.

**② — 배선 PASS, 결과 상태는 측정 불가 + 이유로 닫는다.** 15차 창: `StalePaidOrderConfirmationJob completed … scanned=0` 이
02:50Z·03:00Z **2회**, FAILED **0**. 이 잡은 토큰 실패·4xx/5xx 면 `FAILED` 를 남기므로(`StalePaidOrderConfirmationJob.java:23`),
FAILED 가 없다는 것은 **토큰 발급 + order-service 2xx 가 성립했다**는 뜻이다 — AC-0 ② 가 물은 "batch-worker 가 `/internal/**` 를
실제로 부르는가"는 **PASS**로 닫힌다. 그러나 **결과 상태**(PENDING+payment 주문 → CONFIRMED)는 후보가 없었다 — 시드 주문은
PENDING 1건뿐이고 `payment_id` 가 NULL(이 잡의 대상 아님), 02:50Z 에 order-service 자신의 결제 타임아웃 탐지기가 이미 CANCELLED
로 바꿨다. 합성 후보(행에 `payment_id` 삽입)는 원격 DB 쓰기라 자동 모드 분류기(Remote Shell Writes)가 막는다. 소유자 결정
(2026-09-26 UTC): 이 결과 상태 칸은 **⚪ 측정 불가 + 이유**로 닫는다 — 로컬 장애 주입 IT 는 후속 후보로만 남기고 티켓은
기안하지 않는다.

**① — OPEN 으로 남는다.** `lockAccount`(`POST /internal/accounts/{a}/lock`)의 라이브 판정은 `TASK-MONO-735`(#4048, 2026-09-26
UTC 병합)가 그 잠금 호출 경로 자체를 고친 뒤라야 뜻이 있다 — **17차 AMI 창**(735 이후)에서 셀러를 정지시켜 `account_db.accounts.status`
가 바뀌는지로 판정한다. 그때까지 이 항목은 `review/` 에 남는다.

⇒ **AC-0 «두 결과를 이 파일에 적고, 갈래를 소유자에게 묻는다»** 의 답: 위 두 문단. 이 티켓은 ① 이 열려 있는 한 `done/` 으로
옮기지 않는다.

## CORRECTION (2026-09-27 UTC) — ① 창 판정: 🔴 FAIL — `account_db.accounts.status` 가 안 바뀐다 → `TASK-MONO-737` 대기

17차 AMI 창(`ami-01a237c49e6a385d1`, RepoCommit `a6f0ab791`, `TASK-MONO-735`(#4048) 병합 이후)에서 위 CORRECTION 이 요구한 판정을 했다:
신규 셀러 `seller-test`(계정 `c20bdafc-1bd2-4316-a3f0-c165da543ff9`, ecommerce)를 정지시켰다 — 셀러는 `SUSPENDED` 로 전이했지만
**`account_db.accounts.status` 는 `ACTIVE` 그대로**다. product-service 로그: `seller account lock failed (fail-soft) tenant=ecommerce
account=c20bdafc-… : 404 Not Found` — `AccountServiceSellerProvisioner.lockAccount`가 이제 `X-Tenant-Id: ecommerce`(구체 테넌트, 735 § AC-0
표대로)를 싣는데도 account-service 가 404 를 낸다.

🔴 이것은 735 가 고친 라우팅/헤더 로직 자체의 회귀가 아니다 — 같은 창에서 **콘솔 SUPER_ADMIN 잠금**(admin-service, 역시 구체 `X-Tenant-Id:
ecommerce`)도 같은 모양으로 404였다(`TASK-MONO-735` § CORRECTION 2026-09-27 스텝 2). ⇒ **원인은 공통**이고 새 티켓 **`TASK-MONO-737`**(account-service
에 실제로 도착하는 `X-Tenant-Id` 값부터 확인)로 넘겼다.

⇒ **항목 14 ② (= 이 티켓의 ①)는 🔴 FAIL 로 판정됐다.** `TASK-MONO-737` 이 닫힌 뒤 같은 절차(셀러 정지 → `accounts.status`)로 다시 잰다.
이 티켓은 여전히 `review/` 에 남는다 — `done/` 으로 옮기지 않는다(4차원 close 대상 아님, ① 미충족).

---

## CORRECTION (2026-10-02 UTC) — AC-0 ① 의 결과 상태를 18차 창에서 쟀다

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

- 셀러 정지(일회용 셀러 `live18-1790934860`, SUSPEND `204`) → 그 기계 계정 **`accounts` LOCKED**(콘솔 API·UI 재조회). 즉 product-service 의 잠금 호출이 **도착해서 효과를 냈다**(`TASK-MONO-737` 이 경로를 테넌트 경로 `PATCH …/status` 로 바꾼 뒤). ⚪ product-service 가 부른 URL·응답 코드의 로그는 읽지 않았다(결과 상태로 판정).
- ② batch-worker · 갈래 질문 · 고친 뒤 판정 등 나머지 항목은 이 기록으로 닫히지 않는다 — `review/` 유지.

# 닫기 (2026-10-04 UTC) — 4차원 검증

| 차원 | 판정 |
|---|---|
| (a) | `gh pr view 3991` → `MERGED`, `83e802c7a` |
| (b) | `83e802c7a` 는 `origin/main` 의 조상 |
| (c) | #3991 `statusCheckRollup` 69 개 · FAILURE 0 |
| (d) | 아래 AC 대조 |

**AC 를 그 동사대로 대조**:

- **AC-0 ①** «결과 상태 — `accounts.status` 가 바뀌었는가» → ✅ 18차 창(2026-10-02, § CORRECTION 2026-10-02): 셀러 정지 → 그 기계 계정 `accounts` **LOCKED**. 17차의 404 는 `TASK-MONO-737`(#4055, done)이 고친 뒤의 판정이다.
- **AC-0 ②** «batch-worker 가 `/internal/**` 를 실제로 부르는가» → ✅ 15차 창 `StalePaidOrderConfirmationJob completed … scanned=0` 2회 · FAILED 0(§ CORRECTION 2026-09-26).
- **AC-0 셋째** «두 결과를 적고 갈래를 소유자에게 묻는다» → ✅ § CORRECTION 2026-09-26 의 소유자 결정.
- **AC-1** «고친 뒤 결과 상태로 판정» → ✅ ② 는 다섯 겹 고침(§ 구현 기록) 뒤 배선 PASS, 결과 상태(PAID → CONFIRMED) 칸은 **⚪ 측정 불가 + 이유로 닫는다는 소유자 결정**(2026-09-26 — 후보 주문이 없고 합성 후보는 원격 DB 쓰기). ① 은 737 이후 결과 상태 PASS.
- **AC-2** «호출자 목록에 넣을 대상이 생기면 넣는다» → ✅ 목록 1 → 3행 + bite 2종(§ AC-2).

🔵 **정정**: § CORRECTION 2026-10-02 의 마지막 줄 «② batch-worker · 갈래 질문 · 고친 뒤 판정 등 나머지 항목은 이 기록으로 닫히지 않는다» 는 그 시점의 18차 기록만으로는 닫히지 않는다는 뜻이었고, 그 항목들은 **이미 09-26 소유자 결정으로 닫혀 있었다**. 이번 닫기에서 새로 잰 것은 없다 — 기록을 모아 AC 동사와 대조했을 뿐이다. 체크박스 5개를 `[x]` 로 바꿨다.
