# Task ID

TASK-MONO-554

# Title

데모에서 **이커머스 운영 화면 전체가 죽어 있다** — 그리고 살리니 그 아래에 두 번째 결함이 있었다

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

## 🔴 원인 B — A 를 고치니 드러났다. 화면이 200 인데 **전부 0건**

```
/api/ecommerce/orders      200  totalElements=0
/api/ecommerce/products    200  0
/api/ecommerce/sellers     200  0
/api/ecommerce/promotions  200  0
/api/ecommerce/shippings   200  0
/api/ecommerce/users       200  0
```

**그런데 데이터는 있다** — 공개 스토어프런트 API 는 실제 상품을 반환한다:

```
GET http://ecommerce.<도메인>/api/products  → 200
  {"content":[{"name":"슬림핏 데님 청바지","status":"ON_SALE","price":59000, …}]}
```

그리고 `web.ecommerce.<도메인>` 은 **200 으로 정상 렌더**된다. 즉 *"이커머스가 안 된다"* 가
아니라 **콘솔의 운영자 뷰만 비어 있다** — 두 진술은 다르다.

### 유력 가설 (미확정): 테넌트 불일치

콘솔이 붙이는 토큰의 클레임:

```
console_assumed_token:  tenant_id=demo-corp  tenant_type=B2B_ENTERPRISE
                        entitled_domains=[ecommerce,erp,finance,scm,wms]  org_scope=['*']
```

콘솔은 **`demo-corp`** 로 admin 목록을 조회하는데, 시드된 카탈로그가 그 테넌트 소속이
아닐 수 있다. 🔴 **이것은 추론이다** — 실제 행의 테넌트 컬럼을 아직 안 봤다.

🔵 이 저장소가 **이미 같은 형태를 만났다**: wms 에서 게이트웨이 `totalElements=1` ↔
콘솔 BFF `totalElements=0` (`TASK-BE-582`). 원인이 같은 축인지 확인할 것.

---

# Goal

면접관이 콘솔에 로그인했을 때 **이커머스 운영 화면이 열리고, 시드된 데이터가 보인다.**

# Scope

## In Scope

- **A**: `demo.env` 의 `OIDC_ALLOWED_ISSUERS` 치환(적용 완료) + **가드**.
- **B**: 콘솔 이커머스 목록이 0건인 원인 규명과 고침.
  - AC-0 에서 **실제 행의 테넌트 값을 읽는다**(추론 금지).
  - 시드가 잘못된 테넌트로 넣는 것인지, 콘솔이 잘못된 테넌트로 묻는 것인지 **먼저 가른다**.
    양쪽 다 "고칠 수 있는" 자리라 방향을 정하지 않고 고치면 반대편이 깨진다.

## Out of Scope

- ecommerce compose 의 `${OIDC_ALLOWED_ISSUERS:-http://iam.local,iam}` 기본값 자체를 지우는 것.
  로컬 개발 경로가 그 값에 의존할 수 있으므로 **별개 판단**이다. 데모 층에서 덮는 것으로 충분하다.
  🔴 다만 *"항상 채워서 앱의 폴백을 무력화한다"* 는 구조는 **그대로 남는다** — 다음 프로젝트가
  같은 형태를 복사하면 같은 결함이 재생산된다. 그 축은 별도 티켓 감이다.
- `TASK-MONO-553`(재시작 시 라벨 드리프트) · `TASK-MONO-551`(헬스 판정).

# Acceptance Criteria

**AC-0 — 재확인.** `origin/main` 에서 위 3개 파일(compose:1146 · application.yml:233 · demo.env)을
다시 읽고, 게이트웨이 전수의 `OIDC_ALLOWED_ISSUERS` 를 **다시 센다**. 인계된 표는 가설이다.
B 는 **DB 에서 실제 행의 테넌트 컬럼을 읽는 것**이 AC-0 이다.

**AC-1 — A 의 가드.** 저장소만 보고 *"데모에서 모든 게이트웨이의 허용 issuer 가 데모 issuer 를
포함하는가"* 를 판정한다. `verify-demo-wrapper.sh` 정적 구간(CI + packer 7단계가 실제로 돌린다).
🔴 **하드코딩 목록 금지** — 게이트웨이를 **인벤토리로 발견**해야 한다(compose 에서 `gateway` 서비스를
열거). 손으로 나열하면 그 순간 드리프트가 시작된다(이 저장소가 두 번 데인 실패 모드).
🔴 **bite**: `demo.env` 에서 그 줄을 지우면 빨개져야 한다.

**AC-2 — B 의 판정.** 콘솔 이커머스 목록 중 **최소 하나가 0 이 아니어야** 한다.
🔴 **대조군**: 같은 시점에 공개 스토어프런트 API 가 >0 을 낸다는 것을 함께 기록한다 —
그것이 *"데이터가 없다"* 와 *"콘솔이 못 본다"* 를 가르는 유일한 술어다.

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

- **A 만 고치고 닫는다** — 화면은 열리는데 전부 비어 있다. 면접관에겐 여전히 실패로 보인다.
- **B 를 방향 없이 고친다** — 시드의 테넌트를 바꿀지 콘솔의 질의를 바꿀지 정하지 않고 손대면
  반대편이 깨진다. AC-0 에서 **행을 읽고** 정한다.
- **가드를 하드코딩 목록으로 만든다** — 새 프로젝트가 추가되면 조용히 빠진다.
- **로컬 초록으로 닫는다** — 로컬은 값이 맞아서 항상 통과한다.

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Opus** — A 는 한 줄이지만 **가드가 인벤토리 기반**이어야 하고,
  B 는 방향 판단(시드 vs 질의)이 필요하다. 그리고 재굽기 + 라이브 꼬리가 붙는다.
- 선행: 없음. 관련: `TASK-MONO-550`(부팅 고침 — 이 측정이 가능해진 이유),
  `TASK-MONO-552`(호스트 용량), `TASK-MONO-553`(재시작 라벨 드리프트), `TASK-BE-582`(같은 축의 wms 사례).
- 🔵 **A 는 라이브에서 이미 적용해 증명했다**(게이트웨이 1개 재생성). 그 인스턴스는 재시작하면
  원복되므로 **저장소 고침 + 재굽기 전까지는 재현되지 않는다.**
