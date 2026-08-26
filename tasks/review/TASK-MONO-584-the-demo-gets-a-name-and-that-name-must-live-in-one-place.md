# Task ID

TASK-MONO-584

# Title

데모에 이름이 생겼다 — **`hubwang.com`**. 그 이름을 **한 곳에** 두고, 흩어지는 것을 가드가 막는다.

# Status

review

# Owner

monorepo

# Task Tags

- demo
- adr
- ci

---

# ⏳ 선행 — **없다. 지금 착수 가능하다.**

소유자가 **2026-08-26 에 `hubwang.com` 을 결제**했다. 이 티켓은 그 사실을 저장소에 내린다.
🔵 **DNS/Vercel 연결은 이 티켓이 아니다** — 그건 대시보드 작업이고 AC-5 가 목록만 남긴다.

---

# Goal

**공개 도메인 배치를 확정해 기록하고, 그 값이 흩어지지 않게 만든다.**

이 값은 최소 **다섯 곳**에 나타난다(아래 § 실측). 이 저장소는 *"한 사실이 두 절에 있으면
한쪽만 고쳐진다"* 로 반복해서 데였고, **바로 지난주 `TASK-MONO-579` 가 론처의 두 집을
없애느라 티켓 하나를 썼다.** 같은 일을 도메인으로 다시 하지 않는다.

---

# Context — 실측 (2026-08-26)

## 결정: 화면별 배치

| 화면 | 도메인 | Vercel 프로젝트 | 지금 붙일 수 있나 |
|---|---|---|---|
| **기동 사이트**(론처) | **`hubwang.com`** (+ `www` → 301) | `kanggle-portfolio` | ✅ 지금 |
| **팬** | **`fan.hubwang.com`** | `kanggle-fan` | ✅ 지금 |
| **웹스토어** | **`store.hubwang.com`** | *미생성* | ⏳ `TASK-MONO-575` 게이트 |
| **콘솔** | **`console.hubwang.com`** | *미생성* | ⏳ 단계 3 |

**예약(지금 쓰지 않는다)**: `auth.hubwang.com`(IAM) · `api.hubwang.com`(컨트롤 API).
🔵 예약을 적어 두는 이유는 **나중에 다른 뜻으로 쓰이는 것을 막기 위해서**다.

**론처를 apex 에 둔 근거**: 방문자가 실제로 타이핑하는 주소이고 이 저장소의 front door 가
곧 론처다(README 첫 절). `demo.hubwang.com` 도 성립하지만 **집은 하나여야 한다** —
`TASK-MONO-579` 가 방금 그 이유로 론처의 두 집을 없앴다.

## 🔴 이 값이 떨어지는 자리 — **다섯 곳** (실측)

| # | 자리 | 지금 값 | 누가 고치나 |
|---|---|---|---|
| 1 | `terraform.tfvars.example` `allowed_origins` | `https://kanggle-portfolio.vercel.app` | **이 티켓** |
| 2 | `variables.tf` 의 예시 문자열 | 같음 | **이 티켓** |
| 3 | 루트 `README.md` front door 문장 | `kanggle-portfolio.vercel.app` | **이 티켓** |
| 4 | IdP `redirect_uri` Flyway 시드 | 없음 | 🔵 **`TASK-MONO-574`** (이미 소유) |
| 5 | 론처 `index.html` 의 화면 링크 | sslip.io 파생 | 🔵 **`TASK-MONO-583`** (이미 소유) |

🔴 **4·5 를 이 티켓으로 가져오지 않는다.** 574 는 *"IdP 에 Vercel 도메인 등록"* 을 선행 표에
이미 갖고 있고 583 은 (z14) 뒤집기와 한 몸이다. **같은 일을 두 티켓이 들면 한쪽만 답을 받는다**
— 이 저장소가 바로 오늘 `TASK-MONO-582` 에서 그 실수를 했다(AC-0 이 575 의 중복이었다).

⇒ **이 티켓이 그 둘에게 주는 것은 「값」과 「가드」다.**

## 🔵 574 의 블로커가 이걸로 풀린다

574 는 *"최종 도메인(vercel.app vs 커스텀)이 미정이라 지금 넣으면 곧 고칠 값을 시드에 박는다"*
로 `redirect_uri` 시드를 보류 중이었다. **그 미정이 해소됐다.**

## 🔴 576(D4)에 넘길 입력 — **여기서 정하지 않는다**

apex 에 쿠키를 심으면 **모든 서브도메인에 전송된다.** SSO 가 그걸 원하는지(공유 세션),
아니면 호스트 한정으로 가둘지는 **D4 의 결정**이고 `ADR-MONO-067` 이 별도로 뗀 축이다.
이 티켓은 **그 선택지가 생겼다는 사실만** 576 에 전달한다.

---

# Scope

**In:**

- `TEMPLATE.md` — **공개 호스트명 배분 표**(정본). 기존 `*.local` 표 옆에 둔다
- `infra/demo/aws/terraform/terraform.tfvars.example` · `variables.tf` — `allowed_origins`
- 루트 `README.md` — front door 문장
- `docs/adr/ADR-MONO-067-...md` — History 에 결정 기록
- `scripts/check-public-domains.sh` (신규) — 정본 표와 소비자의 일치 단언

**Out:**

- IdP `redirect_uri` 시드 → **`TASK-MONO-574`**
- 론처 링크 전환 → **`TASK-MONO-583`**
- 쿠키 스코프 결정 → **`TASK-MONO-576` / D4**
- DNS 레코드 · Vercel 도메인 연결 → **소유자**(AC-5 가 절차만)

---

# Acceptance Criteria

## AC-0 — 정본을 **한 곳**에 세운다

`TEMPLATE.md` 에 공개 호스트명 표를 만든다. 기존 `*.local` 표와 **같은 자리**에 두어
두 표가 **행 단위로 대응**하게 한다(로컬↔공개가 갈라지는 것이 이 이관의 알려진 위험이다).

🔴 표에는 **아직 안 붙은 것도 적되 상태를 함께** 적는다. 상태 없이 적으면 다음 사람이
*"이미 붙어 있다"* 로 읽는다.

## AC-1 — 지금 값이 필요한 세 곳을 고친다

`allowed_origins`(2곳) · README front door.

🔴 **`allowed_origins` 는 목록을 유지한다** — 옮기는 동안 **두 오리진을 동시에** 허용해야
론처가 죽는 창이 안 생긴다(`TASK-MONO-557` 이 이 변수를 목록으로 만든 이유가 그것이다).
⇒ `vercel.app` 판을 **지우지 말고 함께 둔다.** 전환이 끝난 뒤 회수하는 것은 별건이다.

🔵 기존 fail-closed validation 3종(비어있지 않음 · `https://` 로 시작 · 끝에 `/` 없음)을
새 값이 **통과하는지** 확인한다.

## AC-2 — 가드: **정본과 어긋나면 문다**

`scripts/check-public-domains.sh`:

1. **모집단은 정본 표에서 파생**한다 — 스크립트에 도메인을 적지 않는다.
   (`scripts/` 는 repo-root 공유 경로다. HARDSTOP-03.)
2. 추적되는 파일에 나타나는 **모든** `hubwang.com` 출현이 **정본 표에 선언된 호스트명**인지
   단언한다. 🔴 오타·미선언 서브도메인이 조용히 들어오는 것을 막는다.
3. 정본이 「론처」로 지목한 오리진이 `tfvars.example` 의 `allowed_origins` 에 **있는지** 단언.
4. **하한** — 선언된 화면이 4개 미만이면 실패(표가 비면 1·2 가 공허하게 통과한다).
5. **탐지기 생존 대조군** — 정본 표 자체를 못 읽으면 통과가 아니라 **실패**.

## AC-3 — bite

- 미선언 서브도메인(`typo.hubwang.com`)을 심으면 **문다**
- `allowed_origins` 에서 론처 오리진을 빼면 **문다**
- 정본 표를 비우면 **하한으로 문다**(0건이 "위반 없음" 으로 보이지 않는다)
- 🔴 **무는지 읽기 전에 주입이 실제로 들어갔는지 단언**한다

## AC-4 — ADR-067 History 에 기록

*"단계별 공개 도메인이 정해졌다"* 를 **날짜·값과 함께**. 🔵 결정 본문(`B + 단계 1~4 + …`)은
**바꾸지 않는다** — 도메인은 그 결정의 **구현**이지 새 결정이 아니다.

## AC-5 — 소유자 절차를 목록으로 남긴다

저장소가 못 하는 일. 🔴 **순서가 있다** — 틀리면 화면이 죽는 창이 생긴다:

1. 등록기관에서 DNS 를 Vercel 로 위임(또는 A/CNAME 직접)
2. `kanggle-portfolio` 에 `hubwang.com` + `www` 추가, **apex 를 primary** 로
3. `kanggle-fan` 에 `fan.hubwang.com` 추가
4. `kanggle-fan` 의 `NEXTAUTH_URL` 을 새 도메인으로 — 🔴 **3 보다 먼저 하면 로그인이 깨진다**
5. `terraform apply` 로 새 `allowed_origins` 반영 (⚠️ `TASK-MONO-579` 의 apply 와 **같은 건**)

## AC-6 — 검증

- 가드 rc=0 · bite 전건 · `bash -n`
- `terraform validate`(가능하면) 또는 최소 `allowed_origins` 가 validation 3종을 통과하는지 수동 대조

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — D3(Vercel 정본) · D4(별도)
- `TEMPLATE.md` § Local Network Convention — 대응시킬 `*.local` 표
- `TASK-MONO-574`(redirect_uri) · `TASK-MONO-583`(론처 링크) · `TASK-MONO-576`(D4)

# Related Contracts

없음 — API 계약을 바꾸지 않는다.

---

# Edge Cases

- 🔴 `allowed_origins` 항목에 **끝 `/` 를 붙이면** 기존 validation 이 문다(의도된 동작).
  CORS 오리진은 **스킴+호스트**까지다.
- 🔴 `www` 와 apex 는 **다른 오리진**이다. 301 로 합치되, 리다이렉트가 걸리기 전까지는
  `www` 로 들어온 방문자의 CORS 프리플라이트가 실패할 수 있다 ⇒ AC-5 의 순서.
- 🔴 **가드의 검색 축이 자기 문서에 걸린다** — 이 티켓과 `TEMPLATE.md` 가 도메인 문자열을
  잔뜩 담고 있다. 정본 표에 선언된 이름만 허용하므로 **문서에 적힌 정상 이름은 통과**하고,
  선언 안 된 이름만 문다. (z12·z14 가 밟은 함정과 같은 자리이니 대조군으로 확인한다.)
- 아직 안 붙은 도메인(`store`·`console`)을 표에 적는 것은 **선언**이지 배포가 아니다.
  상태 열이 그것을 구분한다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| `vercel.app` 을 지우고 새 값만 남김 | 전환 창에서 **론처 Start 버튼이 죽는다** | AC-1 — 목록을 유지 |
| 서브도메인 오타 | 그 화면만 죽고 나머지가 멀쩡해 **원인이 안 보인다** | AC-2 (2) |
| 표만 고치고 소비자를 안 고침 | 문서와 배포가 갈라짐 | AC-2 (3) |
| 표가 비었는데 가드는 초록 | **안 재고 통과** | AC-2 (4)(5) |
| `NEXTAUTH_URL` 을 도메인 추가보다 먼저 바꿈 | 로그인이 깨진다 | AC-5 순서 |
| 574/583 이 이 값을 다시 정함 | 같은 사실이 두 곳 | Scope Out + 가드가 일치를 잰다 |
