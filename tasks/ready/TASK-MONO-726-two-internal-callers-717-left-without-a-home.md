# Task ID

TASK-MONO-726

# Status

ready (2026-09-24 UTC — 기안. `TASK-MONO-717` 을 `done/` 으로 닫기 **전에** 그 티켓 AC-3 이 남긴 잔여 두 건의 집이 필요해 만들었다)

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

- [ ] **①** 셀러를 정지(SUSPEND/CLOSE — `lockAccount` 를 부르는 전이)시켰을 때
      - product-service 가 어느 URL 을 부르는가(설정값 · 로그),
      - 응답이 **404(라우트 없음)** 인가, 그 밖의 무엇인가,
      - 그리고 🔴 **결과 상태** — `account_db.accounts.status` 가 바뀌었는가. 로그 침묵은 판정이 아니다(717 이 못박은 술어).
- [ ] **②** batch-worker 가 `/internal/**` 를 **실제로 부르는가**(코드 경로 + 설정 + 가능하면 라이브 로그). 🔴 안 부르면 등록하지 않는 것이 답이고, 그 판정을 적는 것으로 ②는 닫힌다.
- [ ] 두 결과를 이 파일에 적고, 갈래를 소유자에게 묻는다.

## AC-1 — (AC-0 이 결함으로 판정한 것만) 고친다 + bite

- [ ] 고친 뒤 결과 상태(①이면 `accounts.status`)로 판정한다. 단위 초록으로 닫지 않는다 — 717·718·721 이 **연속 세 번** 단위/로컬 초록 → 창 판정을 거쳤다.

## AC-2 — 가드 범위

- [ ] `scripts/check-internal-caller-addresses.sh` 의 호출자 목록(지금 한 줄)에 넣을 대상이 생기면 그 파일 헤더의 규칙(«실제로 `/internal/**` 을 부르는가» 먼저)대로 넣는다.

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
