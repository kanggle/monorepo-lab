# Task ID

TASK-MONO-554

# Title

데모에서 **이커머스 운영 화면 전체가 401** — 허용 issuer 가 로컬 값으로 고정돼 있었다

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo
- ecommerce

---

# ✅ 2026-08-20 UTC — AC-0 재측정 + AC-1 가드 완료. **인계된 모집단이 틀렸고, 형제 낙오가 하나 더 있었다.**

## AC-0 — 인계된 표를 다시 셌다 (달랐다, 그 사실이 먼저다)

`origin/main` `d7797ac4f` 에서 데모 compose 집합 8도메인을 **렌더해서** 셌다(선언이 아니라 렌더).

| 항목 | 인계(이 티켓 § 설정 대조) | 실측 |
|---|---|---|
| 게이트웨이 수 | 6 (wms·scm·erp·finance·fan·ecommerce) | **7** — iam 자신의 `gateway-service` 가 빠져 있었다 |
| `OIDC_ALLOWED_ISSUERS` 를 받는 서비스 | "게이트웨이" | **게이트웨이 + wms 리소스 서버 5개**(admin·inbound·inventory·master·outbound) |
| issuer/JWKS 를 나르는 env 총량 | 세지 않음 | **83건** (8도메인 전수) |

⇒ **"게이트웨이 전수" 라는 모집단 자체가 틀렸다.** AC-1 이 요구한 대로 하드코딩 목록을 피해도,
*게이트웨이* 라는 **셰이프**로 모집단을 정의했다면 wms 리소스 서버 5개는 사각지대였다.
그래서 가드는 서비스 이름도 키 이름도 박지 않는다 — **렌더된 env 에서 발견**한다.

## 🔴 그리고 그 모집단을 넓히자 **같은 결함이 fan 에 한 벌 더 있었다**

```
fan:membership-service  INTERNAL_JWT_ISSUER      = http://iam.local
fan:membership-service  INTERNAL_JWT_JWK_SET_URI = http://iam.local/oauth2/jwks
```

`projects/fan-platform/docker-compose.yml:194` 가 `${INTERNAL_JWT_ISSUER:-http://iam.local}` 로
**항상 값을 채워** `application.yml:150` 의 안전한 폴백(`${INTERNAL_JWT_ISSUER:${OIDC_ISSUER_URL:…}}`)을
덮는다 — **ecommerce 와 글자 그대로 같은 모양**이다. 이 티켓의 Out of Scope 가
*"다음 프로젝트가 같은 형태를 복사하면 같은 결함이 재생산된다"* 고 적었는데, **이미 복사돼 있었다.**

무엇이 깨지는가: `membership-service` 의 `/internal/**` 체인이 이 값으로 워크로드 신원 issuer 를
**핀**하고, `community-service` 가 IAM client_credentials 토큰으로 `GET /internal/membership/access`
를 부른다(`HttpMembershipChecker`). 데모에서 그 토큰의 iss 는 `http://iam.<데모도메인>` 이라
**핀과 불일치**한다. JWKS 쪽은 더 나쁘다 — `http://iam.local` 은 데모 호스트에서 **해소조차 되지
않아** Spring 이 fail-closed 401 로 바꾼다(MONO-507 과 같은 기전).

🔵 **값의 근거는 추측이 아니라 형제다**: 같은 서비스의 다른 디코더가 이미 `JWT_JWKS_URI` 로
`iam-auth-service` 를 쓰고(그 alias 는 데모에서 실제로 해소된다), `artist-service` 의 기본값
체인도 `${OIDC_ISSUER_URL:…}` 로 폴백한다. ⇒ 고침 = `demo.env` 에 두 줄 추가.

## 🔴🔴 이 결함이 지금까지 보이지 않은 이유 — **판정 축이 대조 축과 같은 문자열이었다**

`demo.env` 의 기본값은 `DEMO_DOMAIN=${DEMO_DOMAIN:-local}` 이다. 그 값으로 렌더하면 데모 issuer
자체가 `http://iam.local` 이 되어 **하드코딩된 `iam.local` 이 정답과 구별되지 않는다.**
데모 호스트의 `DEMO_DOMAIN` 은 IMDSv2 파생이라 결코 `local` 이 아니므로 결함은 거기서만 나타난다.

**음성 대조군으로 확정했다** — 같은 술어를 `DEMO_DOMAIN=local` 로 돌린 판:

| 판 | OIDC_ALLOWED_ISSUERS 결함 | INTERNAL_JWT_* 결함 |
|---|---|---|
| 프로브 도메인(`z12-probe.invalid`) | **위반 1건 검출** | **위반 2건 검출** |
| `local`(저장소 기본값) | ✅ **위반 0건 — 초록** | ✅ **위반 0건 — 초록** |

⇒ CI 의 기본 환경은 이 결함을 **구조적으로 볼 수 없었다.** 가드 (w)(JWKS 도달성)도 같은 이유로
못 봤고, 추가로 **서비스당 JWK env 를 첫 건만** 본다(`membership-service` 는 두 개를 갖는다).

## AC-1 — 가드 (z12) 신설

`verify-demo-wrapper.sh` **정적 구간**(CI "Demo wrapper smoke" + nightly `--require-coverage`
+ packer 7단계가 전부 실행). 술어: 프로브 도메인으로 렌더한 뒤, `ISSUER|JWK` 를 나르는 모든 env 의
호스트가 **프로브 파생**이거나 **점 없는 컨테이너 이름**이어야 한다. 점이 있는데 프로브 밖이면 FAIL.

검증 — **실제 `verify-demo-wrapper.sh` 를 돌린 판**(술어만 떼어낸 하네스가 아니다):

| 칸 | 주입 확인 | rc | 가드가 댄 이름 |
|---|---|---|---|
| 대조군 | `OIDC_…`=1줄 · `INTERNAL_JWT_`=2줄 | **0** | `ok: … 83건 · 도메인 8개 전수` |
| bite A | `OIDC_…`=**0줄** | **1** | `ecommerce:gateway-service OIDC_ALLOWED_ISSUERS = http://iam.local` |
| bite B | `INTERNAL_JWT_`=**0줄**, `OIDC_…`=**1줄** | **1** | `fan:membership-service` 두 키 모두 |

- ✅ **주입을 판정보다 먼저 증명했다** — 각 칸마다 `grep -c` 로 그 줄이 실제로 사라졌는지(그리고 다른
  칸의 주입이 남아 있지 않은지) 확인하고, 어긋나면 하네스를 즉시 중단시켰다. bite B 칸이
  `OIDC_…`=1줄임을 확인했으므로 **그 실패는 bite A 의 잔재가 아니다**.
- ✅ **0건 방어 2중** — 총 추출 0건이면 FAIL(0건을 "없음" 으로 읽지 않는다), **도메인마다도** 0건이면
  FAIL(한 도메인의 렌더가 조용히 실패해도 합계는 멀쩡하다)
- ✅ **음성 대조군** — 위 표. 프로브를 `local` 로 되돌리면 두 결함 모두 통과 ⇒ 프로브가 load-bearing
- ✅ **로컬 무영향** — `DEMO_DOMAIN=local` 에서 fan 렌더가 **바이트 동일**(diff 0). 이 고침은
  도메인 치환 한 축만 움직인다.

### 🔴 계측 사고 기록 — 첫 bite 판정은 **폐기했다**

이 표는 **두 번째** 하네스의 결과다. 첫 하네스는 판정을 낼 수 없는 상태였고, 그 사실이 로그
타임스탬프로만 드러났다:

- `TaskStop` 으로 멈춘 하네스가 **실제로는 계속 돌았다**(멈춘 뒤 15분 동안 3개 로그를 더 씀).
  그 사이 두 번째 하네스를 띄워 **두 프로세스가 가드와 그 입력을 동시에 고쳐 썼다.**
  한쪽이 wrapper 를 `probe="local"` 로 sed 한 창에서 다른 쪽이 bite 를 재는 식이라, control 을
  포함해 어느 칸도 자기가 의도한 판을 재지 않았다.
- 증상은 *"bite B 가 안 물었다"* 였다. 그런데 **주입 확인부터 했더니 원본에 그 줄이 0개**였다 —
  결함이 아니라 계측 실패였다.
- 더 나쁜 것: 셸 cwd 가 어느 시점에 **main 체크아웃**으로 돌아가 있어서, 상대경로로 돌던 하네스가
  worktree 가 아니라 main 을 물어뜯고 있었다. main 의 변경이 worktree 와 동일한 사본임을 diff 로
  확인한 뒤 `git restore` 로 정리했다.

⇒ 두 번째 하네스는 **worktree 절대경로를 박고 브랜치 이름까지 단언**한 뒤에야 돈다. 교훈은
*"bite 가 안 물면 술어를 의심하라"* 보다 한 칸 앞이다: **하네스가 어느 트리를 재고 있는지,
그리고 그 트리를 나 말고 누가 또 쓰고 있는지를 먼저 확정하라.**

# 배경 — 2026-08-18 UTC 라이브 화면 커버리지 측정 중 발견

`TASK-MONO-552` (b) 로 호스트를 키운 뒤 **면접관이 로그인 직후 무엇을 보는가**를
콘솔 BFF 원소 수로 전수 측정했다(SSR HTML 은 클라이언트 렌더라 판정 불가 —
[[env_console_screen_verdict_needs_the_bff_not_the_html]]).

**이커머스 운영 화면 6개 전부가 401** 이었고, 그 여파로 운영자 개요 대시보드까지 무너졌다.

```
/api/ecommerce/{orders,products,sellers,promotions,shippings,users}
   → 401 {"code":"UNAUTHORIZED","message":"session expired"}
/api/console/dashboards/operator-overview
   → 401 {"code":"TOKEN_INVALID","message":"Upstream leg returned 401 — composition collapses to 401"}
```

같은 쿠키·같은 요청으로 **wms·scm·erp·원장은 200** 이었다. 즉 세션 문제가 아니다.

---

## ✅ 원인 A — 확정. `OIDC_ALLOWED_ISSUERS` 가 로컬 값으로 고정돼 있었다

### 판별 (대조군이 갈랐다)

콘솔이 붙이는 **같은 `assumed` 토큰**(유효, 만료 26분 남음, `roles` 에 `ECOMMERCE_OPERATOR` 포함)을
각 게이트웨이에 직접 넣었다:

| 게이트웨이 | 결과 |
|---|---|
| **wms** | **200** — 시드된 `ASN-DEMO-0001` 반환 |
| scm · erp · finance | 404 (경로 오추측 — **인증은 통과**) |
| **ecommerce** | **401 `Authentication required`** |

⇒ 토큰은 유효하고 다른 전부가 받는다. **이커머스 게이트웨이만** 거부한다.

### 설정 대조 (전수)

| 게이트웨이 | `OIDC_ISSUER_URL` | `OIDC_ALLOWED_ISSUERS` |
|---|---|---|
| wms | 데모 값 ✓ | **데모 값** ✓ |
| scm · erp · finance · fan | 데모 값 ✓ | **미설정**(폴백) |
| **ecommerce** | 데모 값 ✓ | **`http://iam.local,iam`** ✗ |

### 🔴 기전 — *값을 설정한 것* 이 결함이다

```
apps/gateway-service/.../application.yml:233
  allowed-issuers: ${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:...}}   ← 안전한 폴백이 있다
projects/ecommerce-microservices-platform/docker-compose.yml:1146
  - OIDC_ALLOWED_ISSUERS=${OIDC_ALLOWED_ISSUERS:-http://iam.local,iam}  ← 항상 채운다
infra/demo/demo.env:72
  WMS_OIDC_ALLOWED_ISSUERS=${IAM_PUBLIC_URL}   ← 데모는 WMS 접두사 판만 치환했다
```

compose 가 변수를 **항상** 채우므로 앱의 폴백에 **한 번도 도달하지 못한다.**
scm·erp·finance·fan 이 통과하는 이유는 *"제대로 설정해서"* 가 아니라 **아무것도 설정하지 않아서**다.

🔵 **형제 낙오 패턴** — 데모 층이 `WMS_` 하나만 파라미터화했고 ecommerce 가 빠졌다.
[[project_enforcement_straggler_sibling_parity]] · [[feedback_grep_the_siblings_before_fixing_it_yourself]]

### ✅ 고침을 **실물로 증명했다** (기전 ≠ 원인이므로)

라이브 호스트에서 그 변수만 데모 issuer 로 주고 게이트웨이를 재생성:

| | 전 | 후 |
|---|---|---|
| 게이트웨이 `/api/v1/admin/orders` | **401** `Authentication required` | **404**(인증 통과, 경로만 오추측) |
| **콘솔 BFF `/api/ecommerce/orders`** | **401** `session expired` | **200** |
| `operator-overview` | 401 (합성 붕괴) | **200 `cards:6`** |
| 이커머스 화면 6개 | 전부 401 | **전부 200** |

**적용한 고침**: `infra/demo/demo.env` 에 `OIDC_ALLOWED_ISSUERS=${IAM_PUBLIC_URL}` 추가
(제네릭 이름이라 ecommerce compose 의 `${OIDC_ALLOWED_ISSUERS:-…}` 가 이 값을 받는다.
`demo-up.sh` 가 `set -a; source demo.env` 하므로 셸 환경이 프로젝트 `.env` 보다 우선한다).

⚠️ **재굽기 필요** — `infra/demo/` 는 baked 층이다.

---

## ❌ ~~원인 B~~ — **반증됐다. 결함이 아니라 내 측정이 틀렸다.**

A 를 고친 뒤 이커머스 화면이 200 이 되었으나 **원소가 전부 0** 이었다. 나는 이것을
*"두 번째 결함"* 으로 적고 테넌트 불일치를 **유력 가설**로 세웠다. 기전은 맞았지만
**결론이 틀렸다** — 저장소는 이미 그 설계를 알고 있었고, 틀린 것은 내 질의였다.

### 판정 경로 (기록)

1. DB 를 읽었다: `products` **8** · `sellers` **1** · `user_profiles` **1** 이 존재하고
   전부 `tenant_id='ecommerce'`. `orders`·`promotions`·`shippings` 는 **DB 자체가 0 행**이다
   ⇒ 그 화면들이 0 인 것은 **정상**이고 결함이 아니었다(내 첫 진술은 이 구분을 안 했다).
2. 형제 대조: WMS 의 행은 `tenant_id='demo-corp'`(`outbound_order`·`admin_order_summary`).
   ⇒ *"이커머스만 도메인 이름을 테넌트로 쓴다"* 로 읽고 **시드를 고치는 방향**을 잡을 뻔했다.
3. 🔴 **그 직전에 시드 스크립트를 열었다** — `infra/demo/seed/seed-ecommerce.sh` L138~151 이
   이미 그 결정을 **명시적으로** 적고 있었다:

   > `demo-corp` 가 아니라 `ecommerce` 를 assume 한다 (`TASK-BE-576`).
   > demo-corp → **권한** · ecommerce → **가시성**(스토어프런트 행이 실제로 사는 곳).
   > 백오피스를 demo-corp 로 넣으면 콘솔이 **반쪽**이 된다.
   > 카탈로그가 `tenant_id='ecommerce'` 인 이유는 product-service **V8 이 tenant 컬럼을 안 적어
   > 기본값을 타기** 때문이고, 게이트웨이가 소비자 토큰에 그 테넌트를 강제한다.

4. 그대로 해봤다 — `POST /api/tenant {"tenant":"ecommerce"}` 후 재측정:

| 화면 | 콘솔 | DB 실제 행 |
|---|---:|---:|
| products | **8** | **8** ✓ |
| sellers | **1** | **1** ✓ |
| users | **1** | **1** ✓ |
| orders · promotions · shippings | 0 | **0** ✓ |

**모든 숫자가 DB 와 정확히 일치한다.** 콘솔은 처음부터 옳게 동작하고 있었다.

### 🔵 배운 것

- **내 대조군이 한 칸 모자랐다.** *"공개 API 는 데이터를 내는데 콘솔은 0"* 까지는 옳게 봤지만,
  거기서 **콘솔 쪽의 파라미터(활성 테넌트)를 변수로 두지 않았다.** 두 표면의 차이를
  *상류의 결함* 으로 귀속하기 전에 **내가 건 조건이 같은지** 먼저 물었어야 한다.
- **"저장소가 모르는 문제" 라고 단정하기 전에 그 자리의 주석을 열어라.** 답이 15줄짜리
  주석으로 이미 적혀 있었고, 티켓 번호(`TASK-BE-576`)까지 달려 있었다.
  [[feedback_grep_the_siblings_before_fixing_it_yourself]] · [[feedback_my_own_ticket_cited_a_spec_that_says_otherwise]]
- **형제 대조가 옳은 방향을 가리키지 않을 수도 있다.** WMS=`demo-corp` ↔ ecommerce=`ecommerce`
  라는 차이는 **실재**하지만 그것이 곧 *"ecommerce 가 낙오"* 를 뜻하지 않았다. 그 차이에는
  이유가 있었다(카탈로그 마이그레이션 V8). **차이의 존재와 차이의 잘못됨은 다른 진술이다.**

### 남는 것 — 결함이 아니라 **데모 경험** 항목

면접관은 이커머스 화면을 보려면 콘솔에서 **활성 테넌트를 `ecommerce` 로 바꿔야 한다.**
그 사실이 어디에도 안내되지 않는다. 이건 버그가 아니라 **안내의 공백**이고,
런처 페이지나 데모 워크스루에 한 줄 적으면 해소된다. 별도 티켓 감(작음).

🔵 그리고 `product-service` **V8 마이그레이션이 tenant 컬럼을 안 적어 기본값을 탄다**는
사실은 시드 주석에만 있다. 그것이 진짜 근본 원인이라면 그쪽이 티켓이지 콘솔이 아니다 —
다만 이 티켓의 범위는 아니다.

---

# Goal

면접관이 콘솔에 로그인했을 때 **이커머스 운영 화면이 열리고, 시드된 데이터가 보인다.**

# Scope

## In Scope

- **A 뿐이다**: `demo.env` 의 `OIDC_ALLOWED_ISSUERS` 치환(적용 완료) + **가드** + 재굽기 실증.
- 🔴 **A 의 형제 낙오 (2026-08-20 추가)**: `demo.env` 의 `INTERNAL_JWT_ISSUER` /
  `INTERNAL_JWT_JWK_SET_URI` 치환. **가드 (z12) 가 main 에서 이것을 물기 때문에 같은 PR 에 넣는다** —
  빨간 가드는 머지할 수 없고, 고침은 A 와 글자 그대로 같은 한 줄짜리 데모 층 치환이다.
  범위 확대가 아니라 **가드가 자기 모집단에서 찾아낸 것**이다(위 § 형제 낙오).
- ~~B~~ 는 **반증됐다**(위 § 참조) — 결함이 아니라 활성 테넌트를 잘못 건 내 측정이었다.
  이 티켓에서 고칠 것이 없다.

## Out of Scope

- ecommerce compose 의 `${OIDC_ALLOWED_ISSUERS:-http://iam.local,iam}` 기본값 자체를 지우는 것.
  로컬 개발 경로가 그 값에 의존할 수 있으므로 **별개 판단**이다. 데모 층에서 덮는 것으로 충분하다.
  🔴 다만 *"항상 채워서 앱의 폴백을 무력화한다"* 는 구조는 **그대로 남는다** — 다음 프로젝트가
  같은 형태를 복사하면 같은 결함이 재생산된다. 그 축은 별도 티켓 감이다.
- `TASK-MONO-553`(재시작 시 라벨 드리프트) · `TASK-MONO-551`(헬스 판정).

# Acceptance Criteria

**AC-0 — 재확인. ✅ 완료 (2026-08-20 UTC).** `origin/main` 에서 위 3개 파일(compose:1146 ·
application.yml:233 · demo.env)을 다시 읽고, 게이트웨이 전수의 `OIDC_ALLOWED_ISSUERS` 를 **다시 센다**.
인계된 표는 가설이다. → **가설이 틀렸다**: 게이트웨이는 6이 아니라 7이고, 이 변수를 받는 것은
게이트웨이만이 아니었다(wms 리소스 서버 5개). 상세는 위 § AC-0.

**AC-1 — A 의 가드. ✅ 완료 (2026-08-20 UTC) — 가드 (z12).** 저장소만 보고 *"데모에서 모든 게이트웨이의
허용 issuer 가 데모 issuer 를 포함하는가"* 를 판정한다. `verify-demo-wrapper.sh` 정적 구간(CI + packer
7단계가 실제로 돌린다).
🔴 **하드코딩 목록 금지** — 게이트웨이를 **인벤토리로 발견**해야 한다(compose 에서 `gateway` 서비스를
열거). 손으로 나열하면 그 순간 드리프트가 시작된다(이 저장소가 두 번 데인 실패 모드).
🔴 **bite**: `demo.env` 에서 그 줄을 지우면 빨개져야 한다.

🔴 **이 AC 의 지시 한 줄에서 벗어났고, 그 이유를 적는다.** AC 는 *"compose 에서 `gateway` 서비스를
열거"* 하라고 적었지만 실측 결과 **그 모집단이 결함을 다 담지 못한다**(wms 리소스 서버 5개 · fan 의
다른 키 이름). 하드코딩 목록을 피하라는 이 AC 의 **의도**를 지키려면 `gateway` 라는 셰이프도 함께
버려야 했다 — 셰이프로 정의한 모집단은 새 셰이프에 무반응 초록이기 때문이다. 그래서 (z12) 는
**issuer/JWKS 를 나르는 env 전수**를 모집단으로 삼는다. 이 확장이 곧 fan 낙오를 찾아냈다.

**AC-2 — 회귀 방지 실측.** 고침 후 콘솔에서 **활성 테넌트를 `ecommerce` 로 두고**
이커머스 목록이 DB 행수와 일치하는지 확인한다(현재 기준선: products **8** · sellers **1** · users **1**).
🔴 **활성 테넌트를 반드시 기록할 것** — 그것을 안 적으면 다음 사람이 `demo-corp` 로 재서
"0 건 = 결함" 이라고 다시 결론 낸다. **내가 정확히 그렇게 했다.**

**AC-3 — 라이브 실증. ⏳ 잔존 (재굽기 필요 — `infra/demo/demo.env` 는 baked 층).**
재굽기 후 새 AMI 로 `/start` → **손대지 않고** 콘솔 이커머스 화면이 200 + 데이터를 보인다.
⚠️ `packer build`/`terraform apply` 는 **사용자 승인 대상**.

🔴 **형제 낙오도 같은 기동에서 실증한다** — fan 의 `INTERNAL_JWT_*` 고침은 **라이브에서 한 번도
행사된 적이 없다**(정적 가드와 bite 만 통과했다. 이 저장소가 다섯 번 배운 명제: `packer validate`
통과가 동작을 뜻하지 않는다). 판정은 `community-service` → `membership-service` 의
`GET /internal/membership/access` 가 **200 을 내는가**로 한다 — 컨테이너 healthy 는 이 축의 판정이
아니다(이 결함은 healthy 인 채로 401 을 낸다).

# Related Specs

- `projects/ecommerce-microservices-platform/docker-compose.yml` L1146
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/resources/application.yml` L233
- `projects/ecommerce-microservices-platform/.env.example` L49 (같은 값의 두 번째 집)
- `infra/demo/demo.env` — 데모 층의 치환 지점
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.5 — 401 → *"session expired"* 번역의 근거
- `infra/demo/seed/seed-ecommerce.sh` — B 의 시드 주체

# Related Contracts

`console-integration-contract.md` § 2.4.10 (이커머스 leg 는 **운영자 토큰이 아니라** 도메인용 IAM OIDC
토큰을 붙인다) — 이 설계가 A 의 발화 경로다.

# Edge Cases

- **`.env.example` 에도 같은 값이 있다**(L49). `provision-demo-env.sh`(MONO-550)가 그것을 `.env` 로
  복사하므로 **한 사실이 두 집에 산다**. 데모 층 셸 환경이 우선하지만, 한쪽만 고치면 나머지가
  살아남는다 — [[feedback_one_fact_in_two_sections_only_one_gets_fixed]].
- **로컬에서는 안 보인다** — 로컬 issuer 가 실제로 `iam.local` 이라 값이 맞다. **데모에서만** 터진다.
- **401 을 "세션 만료" 로 읽으면 엉뚱한 곳을 판다** — 실제로 토큰은 26분 남아 있었다.
  콘솔의 문구는 상류 401 의 **번역**이다.

# Failure Scenarios

- **활성 테넌트를 안 적고 판정한다** — `demo-corp` 로 재면 이커머스는 0 건이 나오고,
  그것을 결함으로 오인해 **시드나 콘솔 질의를 고치려 든다.** 시드 주석(L138~151)이
  그 방향이 왜 틀렸는지 이미 적어 두었다. **고치기 전에 그 주석을 열어라.**
- **`orders`/`promotions`/`shippings` 가 0 인 것을 결함으로 센다** — DB 자체가 0 행이다.
  시드 커버리지 항목이지 이 티켓이 아니다.
- **가드를 하드코딩 목록으로 만든다** — 새 프로젝트가 추가되면 조용히 빠진다.
- **로컬 초록으로 닫는다** — 로컬은 값이 맞아서 항상 통과한다.

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Opus** — A 는 한 줄이지만 **가드가 인벤토리 기반**이어야 하고,
  B 는 방향 판단(시드 vs 질의)이 필요하다. 그리고 재굽기 + 라이브 꼬리가 붙는다.
- 선행: 없음. 관련: `TASK-MONO-550`(부팅 고침 — 이 측정이 가능해진 이유),
  `TASK-MONO-552`(호스트 용량), `TASK-MONO-553`(재시작 라벨 드리프트), `TASK-BE-582`(같은 축의 wms 사례).
- 🔵 **A 는 라이브에서 이미 적용해 증명했다**(게이트웨이 1개 재생성). 그 인스턴스는 재시작하면
  원복되므로 **저장소 고침 + 재굽기 전까지는 재현되지 않는다.**
