# Task ID

TASK-MONO-717

# Title

🔴 셀러 프로비저닝이 `localhost:8081` 을 불러 매번 실패하는데 **아무 화면도 빨개지지 않는다**

# Status

review (2026-09-23 UTC — 소유자 결정 **ⓐ 등록** 구현. AC-0·AC-2·AC-3 닫힘 · 🔴 **AC-1 판정은 창**(`TASK-MONO-672` 항목 12) — 배선했다고 고쳐진 것이 아니다)

# Owner

미지정

# Task Tags

demo, ecommerce, iam, fail-soft, observability

---

# 배경 — 이것은 추론이 아니라 **데모 창 실측**이다

`TASK-MONO-713` 이 곁발견으로 *"`ACCOUNT_SERVICE_BASE_URL` 을 설정하는 compose / env / override 가
저장소에 하나도 없다"* 를 적었고, 판정에 창이 필요해 `TASK-MONO-672` 항목 7 로 갔다.
**2026-09-22 데모 창에서 쟀다** (`TASK-MONO-672` § 14차 창 수확):

```
docker inspect ecommerce-product-service … | grep -i account   →  (없음)

{"level":"WARN","logger":"com.example.product.infrastructure.client.AccountServiceSellerProvisioner",
 "message":"seller provisioning failed (fail-soft, seller stays PENDING) tenant=ecommerce
            seller=demo-seller: I/O error on POST request for
            \"http://localhost:8081/oauth2/token\": Connection refused"}
{"level":"WARN","logger":"com.example.product.application.service.RegisterSellerService",
 "message":"seller left PENDING_PROVISIONING (IAM unavailable, retryable)
            tenant=ecommerce seller=demo-seller"}
```

부팅 시드 1회 + 재시드 1회 = **네 번 다 같은 줄**. 🔵 예상(«localhost 는 컨테이너 자기 자신이니
refused 일 것이다»)이 **측정이 됐다.** 🔴 그리고 예상보다 한 단계 **더 앞에서** 죽는다 —
실패하는 호출은 accounts 엔드포인트가 아니라 **IAM 토큰 엔드포인트**(`/oauth2/token`)다.
프로비저닝은 **자격증명을 얻는 단계에서** 끝난다.

## 왜 조용한가

이 호출은 **fail-soft** 다(`ADR-MONO-042` D3 — account-service 가 응답하지 않아도 셀러 등록 자체는
진행된다). ⇒ 실패해도 아무 화면도 빨개지지 않는다. 🔴 **그러나 결과는 무해하지 않다**:
셀러가 `PENDING_PROVISIONING` 에 남는다. 데모 시드의 「셀러 활성화」 줄은 **다른 경로**(상태 PATCH)라
화면상으로는 활성으로 보이고, **프로비저닝이 안 됐다는 사실만 사라진다.**

---

# Goal

`ACCOUNT_SERVICE_BASE_URL`(그리고 그 클라이언트가 읽는 IAM 토큰 주소)이 **어디에서 와야 하는지**를
정하고, 「설정이 없으면 조용히 localhost 로 떨어진다」를 끝낸다.

# Scope

## 포함

- `AccountServiceSellerProvisioner` 가 읽는 주소들의 **출처 확정**(compose / override / 기본값).
- 데모 체인(`infra/demo/*.override.yml`)에 그 값을 싣는 것.
- 「설정이 없다」를 **조용하지 않게** 만드는 판정 — 아래 AC-3.

## 제외

- fail-soft 정책 자체를 바꾸는 것(`ADR-MONO-042` D3). 🔴 그것은 ADR 결정이다.
- `TASK-MONO-713` 갈래 ⓑ(`/internal/tenants/**` 를 게이트웨이 뒤로 넣기) — 그 배선은 별건이고,
  이 티켓이 그 면제의 만료 경로이기도 하다(672 항목 7 § 곁가지).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [x] 🔴 **주소가 정말 둘인지 하나인지부터 읽어라.** 로그의 실패 대상은 `/oauth2/token` 인데
      티켓(713)이 적은 기본값은 `${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}` 이다.
      **같은 상수를 토큰과 accounts 양쪽에 쓰는지**, 아니면 IAM 주소가 따로 있는지 코드에서
      확인하고 그 답을 여기 적어라. 🔵 답에 따라 고칠 값이 하나가 아니라 둘일 수 있다.
- [x] 🔴 **운영/로컬에서도 같은 상태인지** 확인하라. 데모에서만 빠진 것인지, 어디에서도 설정된
      적이 없는 것인지는 다른 문제다(713 의 전수 grep 은 후자를 가리킨다).

## AC-1 — 고친다 — 🟡 **배선 완료 · 판정은 창(`TASK-MONO-672` 항목 12)**

- [x] 데모 체인에 값을 싣고, **창에서** 그 두 WARN 줄이 사라지는 것을 본다.
- [ ] ⏳ 🔴 판정은 로그 부재가 아니라 **결과 상태**다: 셀러가 `PENDING_PROVISIONING` 에서
      벗어나는가 · `account_db` 에 그 셀러-운영자 계정 행이 **생기는가**.
      🔵 로그가 조용해도 행이 없으면 고쳐진 것이 아니다(672 항목 7 이 그 술어를 못박았다).

## AC-2 — bite — ✅

- [x] 🔴 **「설정이 없으면 조용히 localhost」를 무는 술어**를 놓아라. 날짜나 로그 문구로 재지 마라.
      후보: 기동 시 그 주소가 `localhost` 이고 프로파일이 데모/운영이면 **WARN 이 아니라 실패**,
      또는 compose 렌더에서 그 env 의 부재를 세는 가드.
      **bite**: 그 설정을 지우면 빨개진다.
- [x] 🔴 fail-soft 를 fail-closed 로 바꾸는 것이 아님을 분명히 하라 — 무는 것은 **설정 부재**이지
      account-service 의 일시 장애가 아니다. 둘을 같은 술어로 묶으면 장애 때 셀러 등록이 죽는다.

## AC-3 — 한계를 적는다 — ✅

- [x] 이 수정으로도 **안 보이는 채로 남는** fail-soft 경로가 또 있는지 훑고, 있으면 목록을 적어라.
      🔴 「이제 다 보인다」로 적지 마라.

---

# Related Specs / Contracts

- `ADR-MONO-042` D3 — 셀러 프로비저닝의 fail-soft 정책.
- `projects/ecommerce-microservices-platform/apps/product-service/.../AccountServiceSellerProvisioner.java`
- `tasks/done/TASK-MONO-713-…` § AC-0 곁발견 · `tasks/ready/TASK-MONO-672-…` § 항목 7 (실측)

# Edge Cases

- **데모 IP 가 매 부팅 바뀐다** — 값을 리터럴 IP 로 박으면 다음 창에 낡는다. 형제들이 쓰는
  `${DEMO_DOMAIN}` 파생 또는 컨테이너 이름(`iam-auth-service:8081`)을 따라야 한다.
- **로컬(`DEMO_DOMAIN=local`)에서도 성립해야 한다** — 데모만 고치면 로컬이 다시 조용히 깨진다.

# Failure Scenarios

1. 주소만 채우고 **결과 상태를 안 재면** 「로그가 조용해졌다」를 고쳐진 것으로 읽는다 (AC-1 이 막는다).
2. bite 를 로그 문구로 만들면 문구가 바뀔 때 조용히 죽는다 (AC-2 가 막는다).

# 분석 / 구현 권장

(분석=Opus 5 / 구현 권장=Sonnet — 배선과 기본값 문제이고 도메인 결정이 아니다. 단 AC-2 의 술어 설계는 Opus)

---

# 🔴🔴 AC-0 수행 결과 (2026-09-22 UTC) — **전제가 무너졌다. 이 티켓은 「배선 문제」가 아니다**

AC-0 이 *"주소가 정말 둘인지 하나인지부터 읽어라"* 라고 했고, 읽으니 **셋 이상**이었다.

## ① 실패하는 주소는 713 이 지목한 그것이 **아니다**

`product-service/src/main/resources/application.yml` (실측):

```yaml
iam:
  internal-client:
    token-uri:     ${IAM_TOKEN_URI:http://localhost:8081/oauth2/token}   # ← 실패한 호출
    client-id:     ${IAM_CLIENT_ID:product-service-client}
    client-secret: ${IAM_CLIENT_SECRET:secret}
  account-service:
    base-url:      ${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}     # ← 713 이 지목
```

⇒ **`ACCOUNT_SERVICE_BASE_URL` 만 채웠으면 관측된 실패는 그대로였다.** 프로비저닝은 자격증명을
얻는 단계에서 끝나므로 고쳐야 할 첫 값은 `IAM_TOKEN_URI` 다.

🔵 713 이 **이름 자체는 맞았다** — `application.yml` 이 명시 플레이스홀더를 쓰므로 relaxed
binding 문제는 없다(내가 한 번 의심했고, 근거가 없었다).

## ② 「어디에서 설정되는가」 — 전수: **어디에서도 안 된다**

`*.yml`·`*.yaml`·`*.env`·`*.sh`·`*.properties` 전수에서 네 값 모두 **오직 `application.yml` 의
기본값**만 존재한다. 데모만 빠진 것이 아니라 **로컬·CI·운영 어디에도 없다.** (AC-0 둘째 칸 답)

🔵 형제가 옳은 모양을 이미 갖고 있다 — fan `community-service`:
`token-uri: ${IAM_TOKEN_URI:${OIDC_ISSUER_URL:http://iam.local}/oauth2/token}`
(데모 체인이 `OIDC_ISSUER_URL` 을 채운다: `demo.env:80` → `IAM_PUBLIC_URL`).

## ③ 🔴🔴 그런데 주소를 고쳐도 **안 된다** — 그 클라이언트가 IdP 에 없다

`oauth_clients` 시드 **전수**(`projects/iam-platform/**/db/migration/*.sql`):

```
community-service-client · ecommerce-admin-dashboard-client · ecommerce-web-store-client
fan-platform-user-flow-client · membership-service-client · platform-console-web
scm-platform-internal-services-client · wms-internal-services-client · wms-user-flow-client
V0019(워크로드): admin-service-client · auth-service-client · security-service-client · account-service-client
```

**`product-service-client` 가 없다.** ⇒ 주소를 고치면 실패가 `Connection refused` 에서
**`invalid_client`** 로 옮겨갈 뿐이다. ⚠ batch-worker 의 기본값
`ecommerce-internal-services-client` 도 **같은 상태**다.

🔴 **그러므로 이 프로비저닝은 이 저장소에서 한 번도 동작한 적이 없다.** 데모 배선이 빠진 것이
아니라 **호출자 자격이 만들어진 적이 없는 것**이다. 티켓 말미의 *"배선과 기본값 문제이고
도메인 결정이 아니다"* 는 이 측정으로 **틀렸다**.

## ④ 🔵 다만 내가 단정할 뻔한 것도 틀렸다 — **역할이 아니라 스코프다**

account-service 의 `/internal/**` 게이트는 **`internal.invoke` 스코프**다
(`IamTokenProviderConfig.INTERNAL_INVOKE_SCOPE`, TASK-BE-514/MONO-422).
⇒ `WorkloadRoleCatalog`(ADR-MONO-061 ACCEPTED, *"admin-tier 를 주지 않는다"*)를 **건드리지
않는다** — GRANTS 에 없는 클라이언트는 역할을 못 받고, 여기서는 그래도 된다.

🔴 이 확인을 안 했으면 «ADR 개정 필요» 로 보고할 뻔했다. 그것은 **과장**이었을 것이다.

## ⑤ 남는 진짜 구멍 하나 — 게이트웨이 라우트

iam 게이트웨이는 `Path=/internal/tenants/**` **만** 라우트한다. 프로비저너의 네 호출 중

| 호출 | 라우트 |
|---|---|
| `POST /internal/tenants/{t}/accounts` | 🟢 있다 |
| `POST /internal/tenants/{t}/identities:resolveOrCreate` | 🟢 있다 |
| `PATCH /internal/tenants/{t}/accounts/{a}/status` | 🟢 있다 |
| `POST /internal/accounts/{a}/lock` | 🔴 **없다** |

⇒ `ACCOUNT_SERVICE_BASE_URL` 을 게이트웨이 호스트로 주면 **셋은 되고 `lock` 은 404** 다.
🔵 이것이 `TASK-MONO-713` 갈래 ⓑ 가 말한 바로 그 구멍이고, 이 티켓 § 제외가 별건으로 둔 것이다.

---

# ⇒ 🔴 소유자 결정이 필요하다 — 「다섯 번째 워크로드 클라이언트를 만드는가」

선례는 완전하다(`V0019__seed_internal_service_workload_clients.sql`): `tenant_id='global-account-platform'` ·
`tenant_type='INTERNAL'` · `scopes='["internal.invoke"]'` · `client_secret_hash` = 핀된 BCrypt("secret") ·
운영은 `<SERVICE>_SERVICE_CLIENT_SECRET` 로 회전. **기계적으로는 마이그레이션 한 장이다.**

🔴 **그런데 V0019 는 «왜 이 넷인가» 를 헤더에 열거한다.** 다섯 번째를 **조용히** 더하는 것은
그 기록의 성격을 바꾼다 — 「/internal/** 을 부를 수 있는 주체」의 명단이기 때문이다.

| 갈래 | 무엇을 한다 | 대가 |
|---|---|---|
| **ⓐ 등록한다** | `product-service-client` 를 V0019 모양으로 시드 + 데모 체인에 주소 둘 | 명단이 다섯이 된다. `lock` 은 여전히 404(별건) |
| **ⓑ 기존 클라이언트를 재사용** | `ecommerce-*` 중 하나로 `IAM_CLIENT_ID` 를 덮는다 | 🔴 그 클라이언트들은 **user-flow** 용이고 `internal.invoke` 가 없다 ⇒ 결국 시드 변경이 필요하다 |
| **ⓒ 프로비저닝을 끈다** | 설정이 없으면 **명시적으로 비활성**(WARN 대신 시작 시 1회 INFO) | 🔵 가장 정직하다 — 「한 번도 동작한 적 없다」는 사실과 일치한다. 🔴 그러나 기능을 포기하는 결정이다 |

🔵 **추천: ⓐ.** 선례가 형식을 다 정해 뒀고, 스코프 게이트라 ADR 을 건드리지 않으며, ⓒ 는
«fail-soft 로 이미 조용히 꺼져 있는 것» 을 공식화할 뿐 셀러가 `PENDING_PROVISIONING` 에 남는
문제를 안 고친다. 🔴 **내 추천이지 소유자 선택이 아니다** — 명단에 이름을 더하는 일이다.

🔴 그리고 **어느 갈래든 AC-1 의 판정(`account_db` 행이 생기는가)은 창이 있어야 한다** ⇒
`TASK-MONO-672` 로 간다.

---

# 구현 기록 (2026-09-23 UTC · 소유자 결정 **ⓐ 등록**)

## 🔴 고침이 compose 가 아니라 **IdP 에서 시작한다**

AC-0 이 밝힌 대로 고칠 것은 셋이었다:

| # | 무엇 | 어디 |
|---|---|---|
| 1 | **워크로드 클라이언트 등록** | `V0036__seed_product_service_workload_client.sql` (auth-service) |
| 2 | 그 클라이언트의 **역할 결정을 명시** | `WorkloadRoleCatalog` — **빈 맵**(재 봤고 답은 없음) |
| 3 | 주소 **둘** | 베이스 compose 기본값 + `infra/demo/demo.env` 파생 |

🔵 그리고 **한 가지를 더 고쳤다**: product-service 가 토큰을 받을 때 `scope` 를 **생략**하고
있었다(`null`). 수신 측은 `internal.invoke` 를 **핀으로 요구**하므로(TASK-BE-514,
account-service `internalTokenValidator()`), 이제 **명시적으로 요청**한다.
🔴 서버 기본(생략 시 등록된 스코프 전부 부여)에 기대지 않는 이유: 그러면 이 서비스의 토큰
내용이 **아무 데도 안 적힌 서버 측 기본값**에 달리고, 등록에 스코프가 하나 더 붙는 날
이 토큰이 조용히 넓어진다.

## 🔴🔴 그 등록이 **조용히 안 된다** — 저장소가 막고 있고, 그게 설계다

`WorkloadRoleCatalogTest.catalogEnumeratesExactlyTheSeededWorkloadClients()` 가 **등호**로
단언한다: 시드된 모든 `client_credentials` 클라이언트는 카탈로그에 **명시 항목**이 있어야 한다.

> *"A missing entry means a workload client nobody decided about"*

⇒ 시드만 더하면 **빨개진다.** 그래서 카탈로그에 빈 맵을 **이유와 함께** 넣었다:
`/internal/**` 는 스코프로 막히지 역할로 막히지 않으므로, 역할을 주면 ecommerce 워크로드
자격이 도메인 표면으로 넓어진다. ⇒ `ADR-MONO-061` · `ADR-MONO-063` 은 **안 건드린다.**

## 🔴 issuer 호스트 ≠ 게이트웨이 호스트 — 둘을 따로 뒀다

데모에서 `OIDC_ISSUER_URL` 은 **auth-service** 의 공개 호스트다. 그런데
`/internal/tenants/**` 를 라우트하는 것은 **iam 게이트웨이**다
(`gateway-service` application.yml § `account-service-internal`).

⇒ `ACCOUNT_SERVICE_BASE_URL` 을 `OIDC_ISSUER_URL` 에서 파생시키면 **토큰은 받고 accounts
호출만 404** 가 되고, 그 증상은 fail-soft 라 **아무 화면도 빨개지지 않는다** — 이 티켓이
존재하는 바로 그 모양이다. 그래서 둘을 따로 뒀고, 그 이유를 두 파일 모두에 적었다.

🔵 리터럴 IP 를 박지 않았다(Edge Case) — `demo.env` 가 `${DEMO_DOMAIN}` 에서 파생시키고,
베이스 compose 기본값은 `http://iam.local` 이라 **로컬도 성립한다**(같은 Edge Case).

---

# AC 판정

## AC-0 — ✅ (앞 절 § AC-0 수행 결과, PR #3957 에서 머지됨)

## AC-1 — 🟡 **배선은 했고, 판정은 창이 한다**

- [x] 데모 체인에 값을 실었다(`demo.env` + 베이스 compose 기본값).
- [ ] 🔴 **창에서 WARN 두 줄이 사라지는가 · `account_db` 에 행이 생기는가** — **미측정**.
      🔴 티켓이 못박은 대로 **로그 침묵은 판정이 아니다**. ⇒ `TASK-MONO-672` **항목 12** 로 넘겼다.

🔴 **그리고 이 배선이 옳다는 보장은 아직 없다.** `http://iam.${DEMO_DOMAIN}` 이 ecommerce
컨테이너 **안에서** 해소되는지는 안 쟀다(시드 스크립트는 **호스트에서** 그 주소를 쓴다).
⇒ 창의 첫 질문이 그것이고, 아니면 컨테이너 이름/공유 네트워크로 바꾸는 것이 다음 갈래다.

## AC-2 — ✅ 「설정이 없으면 조용히 localhost」를 무는 가드

`scripts/check-internal-caller-addresses.sh` — 모집단은 `git ls-files`, 판정은 **그 서비스의
compose 블록에 그 키가 선언돼 있는가**.

- [x] 🔴 **날짜·로그 문구로 재지 않는다.** 무는 대상은 **설정의 부재**다(Failure Scenario 2).
- [x] 🔴 **fail-soft 를 fail-closed 로 바꾸지 않는다** — 이 가드는 **저장소를** 보지 런타임을
      보지 않는다. IAM 일시 장애에는 아무 반응도 하지 않는다.
- [x] **bite**: self-test 3칸 + **실제 트리에서** 그 줄을 지우니 rc=1, 복원하면 rc=0.
      🔵 self-test (b) 는 **다른 서비스**에 같은 키를 둔다 — 파일 전체를 grep 하는 술어였다면
      통과시켰을 트리이고, 블록을 잘라 보는 것이 그 칸의 요점이다.
- [x] 비공허성: 검사한 키가 0개면 통과가 아니라 rc=2.

## AC-3 — ✅ **이제 다 보인다고 적지 않는다**

이 수정으로도 **안 보이는 채로 남는** 경로:

| 경로 | 왜 여전히 조용한가 |
|---|---|
| `POST /internal/accounts/{a}/lock` | 🔴 iam 게이트웨이에 **라우트가 없다**(`/internal/tenants/**` 만). 이 호출만 404 이고 fail-soft 라 조용하다 — `TASK-MONO-713` ⓑ 소관 |
| `batch-worker` 의 `ecommerce-internal-services-client` | 🔴 **그 client_id 도 IdP 에 없다.** 이 티켓은 product-service 만 고쳤다 — batch-worker 가 그 자격을 실제로 쓰는지 안 쟀고, 안 재고 등록하면 명단이 근거 없이 자란다 |
| `notification-service` `AccountCreatedEventConsumer` | fail-soft (별건) |
| `NotificationSendService` 구독별 fail-soft | 설계상 의도 (별건) |
| `OrderPiiAnonymizationService` · `AccountDeletedConsumer` | ADR-MONO-037 P5 의 fail-soft (별건) |

🔴 위 다섯 중 **앞의 둘만** 이 티켓의 축(「설정/등록이 없어서 조용히 죽는다」)이고, 나머지
셋은 **의도된 fail-soft**다. 섞어서 「fail-soft 가 문제다」로 적지 않는다.

🔵 **가드가 덮는 범위도 그만큼이다** — `check-internal-caller-addresses.sh` 의 호출자 목록은
지금 **한 줄**이다. batch-worker 를 넣으려면 먼저 «그것이 실제로 `/internal/**` 을 부르는가»
를 재야 한다(그 파일 헤더가 그 규칙을 적고 있다).

---

# 🔵 CI 배선 — 가드가 **로컬에서만 도는 것**이 되지 않게

`.github/workflows/ci.yml` 에 `internal-caller-addresses` 잡을 붙였다(문법 검사 → self-test →
본 판정, 형제 가드들과 같은 3단). 트리거는 **compose 와 가드 자신**이다 — 값이 사라지는 사건은
그 둘에서만 온다. 🔵 `application.yml` 의 기본값이 바뀌는 것은 **다른 축**이고 이 가드는 그것을
안 본다(선언된 공백).

## 🔴 그 배선에서 한 번 미끄러졌고, 그 모양을 적어 둔다

`ci.yml` 을 문자열 앵커로 편집했더니 잡 블록이 **필터 절 안으로** 들어갔다. 원인은
`"  demo-resolver-copies:\n"`(2칸)이 12칸 들여쓴 필터 줄
`"            demo-resolver-copies:\n"` 의 **접미와 일치**한 것이다 — `str.replace` 는 줄이 아니라
**부분 문자열**을 본다.

🔵 잡은 것은 `yaml.safe_load` 였다(`expected <block end>`). ⇒ **YAML 을 문자열 앵커로 편집하면
파싱으로 검산하라.** 되돌리고 줄 단위 인덱스로 다시 넣었고, 이번엔
`'internal-caller-addresses' in d['jobs']` 까지 확인했다.

---

# 게이트 기록

| 게이트 | 결과 |
|---|---|
| `WorkloadRoleCatalogTest` | 🟢 **9칸 실행 · 실패 0 · skip 0**(XML 로 확인 — `rc=0` 만으로는 「돌았다」의 증거가 아니다) |
| 그 테스트의 **bite** | 🟢 카탈로그 항목 제거 → `rc=1`, 복원 → 존재 확인 |
| `check-internal-caller-addresses.sh` | 🟢 self-test 3칸 + **실제 트리 bite**(설정 줄 제거 → rc=1 / 복원 → rc=0) |
| `check-ls-files-guard-count.sh` | 🟢 rc=0 (`25/63`, 산문 집 둘 갱신) |
| `check-flyway-version-collision.sh` | 🟢 rc=0 (V0036 충돌 없음) |
| 필수 3종 | 🟢 rc=0 |
| **가드 전수**(`scripts/check-*.sh` + `*.mjs`) | 🟢 **환경 의존 2건 외 전부 초록** |
| `ci.yml` YAML 파싱 + 잡 존재 | 🟢 OK |

🔴 환경 의존 2건(`check-erp-single-tenant-ratchet.sh` = 컨테이너 필요 ·
`check-prerendered-demo-verdict.sh` = env 필요)은 **이 세션에서 `main` 에서도 rc=1 임을
실측**했다(TASK-MONO-720 § 게이트 기록). 이 PR 의 책임이 아니다.

🔴 **여기서 안 돌린 것**: Testcontainers 통합 · e2e · nightly. 그리고 무엇보다 —
**이 변경이 실제로 셀러를 프로비저닝하는지는 안 쟀다.** 그것이 AC-1 이고 `TASK-MONO-672`
항목 12 다. 🔵 배선했다고 고쳐진 것이 아니다.
