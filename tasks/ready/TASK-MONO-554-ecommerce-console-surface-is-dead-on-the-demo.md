# Task ID

TASK-MONO-554

# Title

데모에서 **이커머스 운영 화면 전체가 401** — 허용 issuer 가 로컬 값으로 고정돼 있었다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- ecommerce

---

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
- ~~B~~ 는 **반증됐다**(위 § 참조) — 결함이 아니라 활성 테넌트를 잘못 건 내 측정이었다.
  이 티켓에서 고칠 것이 없다.

## Out of Scope

- ecommerce compose 의 `${OIDC_ALLOWED_ISSUERS:-http://iam.local,iam}` 기본값 자체를 지우는 것.
  로컬 개발 경로가 그 값에 의존할 수 있으므로 **별개 판단**이다. 데모 층에서 덮는 것으로 충분하다.
  🔴 다만 *"항상 채워서 앱의 폴백을 무력화한다"* 는 구조는 **그대로 남는다** — 다음 프로젝트가
  같은 형태를 복사하면 같은 결함이 재생산된다. 그 축은 별도 티켓 감이다.
- `TASK-MONO-553`(재시작 시 라벨 드리프트) · `TASK-MONO-551`(헬스 판정).

# Acceptance Criteria

**AC-0 — 재확인.** `origin/main` 에서 위 3개 파일(compose:1146 · application.yml:233 · demo.env)을
다시 읽고, 게이트웨이 전수의 `OIDC_ALLOWED_ISSUERS` 를 **다시 센다**. 인계된 표는 가설이다.

**AC-1 — A 의 가드.** 저장소만 보고 *"데모에서 모든 게이트웨이의 허용 issuer 가 데모 issuer 를
포함하는가"* 를 판정한다. `verify-demo-wrapper.sh` 정적 구간(CI + packer 7단계가 실제로 돌린다).
🔴 **하드코딩 목록 금지** — 게이트웨이를 **인벤토리로 발견**해야 한다(compose 에서 `gateway` 서비스를
열거). 손으로 나열하면 그 순간 드리프트가 시작된다(이 저장소가 두 번 데인 실패 모드).
🔴 **bite**: `demo.env` 에서 그 줄을 지우면 빨개져야 한다.

**AC-2 — 회귀 방지 실측.** 고침 후 콘솔에서 **활성 테넌트를 `ecommerce` 로 두고**
이커머스 목록이 DB 행수와 일치하는지 확인한다(현재 기준선: products **8** · sellers **1** · users **1**).
🔴 **활성 테넌트를 반드시 기록할 것** — 그것을 안 적으면 다음 사람이 `demo-corp` 로 재서
"0 건 = 결함" 이라고 다시 결론 낸다. **내가 정확히 그렇게 했다.**

**AC-3 — 라이브 실증.** 재굽기 후 새 AMI 로 `/start` → **손대지 않고** 콘솔 이커머스 화면이
200 + 데이터를 보인다. ⚠️ `packer build`/`terraform apply` 는 **사용자 승인 대상**.

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
