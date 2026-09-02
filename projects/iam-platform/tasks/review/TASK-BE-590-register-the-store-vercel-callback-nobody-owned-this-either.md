# Task ID

TASK-BE-590

# Title

🔴🔴 `store.hubwang.com` 의 콜백이 IdP 에 등록돼 있지 않다 — 그리고 이번에도 **아무 티켓의 대상이 아니었다**

# Status

review

# Owner

iam-platform

# Task Tags

- oidc
- migration
- demo

---

# 🔎 어디서 왔나 — **로그인을 재려다 잡혔다**

`TASK-MONO-612` 의 AC-0 이 Vercel 스토어의 런타임 값을 읽으려고 로그인을 시도하다,
그보다 한 겹 위에서 멈췄다. 그 사슬을 끝까지 따라가니 **IdP 쪽 절반**이 비어 있었다.

`TASK-BE-582` 가 **팬**의 Vercel 콜백을(`V0033`), 후속이 **콘솔**의 것을(`V0034`) 등록했다.
🔴 **스토어의 것은 등록되지 않았다** — 그리고 그것은 **누락이 아니라 의도**였다.
`V0033` 헤더가 그렇게 적어 뒀다:

> The canonical table also names `console.hubwang.com` and `store.hubwang.com`, and both are
> **deliberately absent** from this migration. … Each surface gets its own migration **when its
> phase lands and its path has been measured rather than assumed.**

**그 단계가 왔고, 경로도 측정됐다.** 이 티켓이 그 마이그레이션이다.

---

# 📏 이미 측정된 것 (착수 전에 다시 세지 않아도 되는 것)

| 물음 | 값 | 출처 |
|---|---|---|
| 클라이언트가 실재하나 | ✅ `ecommerce-web-store-client` | `V0012` 시드 |
| 콜백 **경로 모양** | ✅ **`/api/auth/callback/iam`** | 🔵 **독립 2회 일치** — ⑴ `web-store/src/shared/auth/auth.ts:76` 의 provider `id: 'iam'` ⑵ `TASK-MONO-606` AC-4 의 **라이브 DB 전수** 표 |
| `store.hubwang.com` 이 등록돼 있나 | 🔴 **없다** | `V0012`·`V0024`·`V0028`·`V0033`·`V0034` 전수 — 등록된 것은 `http://localhost:3000/…`, `http://localhost:3001/…`, `http://web.ecommerce.local/…` |
| 라이브 증상 | `https://store.hubwang.com/api/auth/*` 가 **500** | `TASK-MONO-612` AC-0 (2026-09-02 UTC) |

🔴 **500 은 이 티켓만으로 사라지지 않는다.** 그 사이트는 Vercel 프로젝트 env 도 비어 있다
(`TASK-MONO-610` AC-4b). **이 티켓은 그 다섯 줄 중 4·5 를 «가능하게» 만드는 선행이다** —
등록 없이 env 만 넣으면 소유자는 `redirect_uri_mismatch` 를 만나고, 🔴 **그 오류는 URI 도
클라이언트도 이름으로 대지 않는다**(`V0033` 헤더의 실측).

---

# Goal

`ecommerce-web-store-client` 에 **`https://store.hubwang.com/api/auth/callback/iam`** 과
그 짝인 post-logout URI 를 등록해, `TASK-MONO-610` AC-4b 의 소유자 작업이 **닫힐 수 있게**
만든다.

---

# Scope

**In**

- `V0035__add_store_vercel_domain_redirect_uri.sql` (형태는 `V0033` 을 그대로 따른다)
- `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` 에 새 항목 단언 추가

**Out**

- 🔴 **기존 마이그레이션 수정** — 체크섬을 깬다. `V0033`/`V0034` 도 그래서 새 파일이었다
- `kanggle-store` 의 Vercel env 5줄 — **소유자 몫**이고 `TASK-MONO-610` AC-4b 가 든다
- 죽은 sslip 도메인 회수 — `TASK-MONO-606` (별 축, 그 티켓 Scope 가 마이그레이션을 Out 으로 뺐다)
- `DEMO_PAYMENT_MOCK` 정합 — `TASK-MONO-612`

---

# Acceptance Criteria

## AC-0 — 🔴 **앵커를 추정하지 말고 잰다** (착수 전)

`V0033` 은 *"the LAST element V0031 left in both arrays"* 를 앵커로 골랐다. 같은 것을 해야
한다 — **`REPLACE()` 는 앵커 문자열이 실제로 그 자리에 있어야 동작하고, 없으면 조용히
아무 일도 안 한다**(에러가 아니라 **0행 갱신**이다).

1. `V0012` → `V0016` → `V0024` → `V0028` 을 순서대로 읽어 `ecommerce-web-store-client` 의
   `redirect_uris` 와 `client_settings` 의 **최종 문자열**을 재구성한다.
2. 🔵 후보는 **`http://web.ecommerce.local/api/auth/callback/iam`** 이다(꼬리로 보인다).
   **그러나 `V0028` 이 `localhost:3001` 을 `localhost:3000` **뒤에** 끼워 넣었으므로 순서를
   눈으로 확인하라** — 「마지막」이 직관과 다를 수 있다.
3. post-logout 쪽 앵커도 따로 잰다(`"http://web.ecommerce.local/"` 후보).

## AC-1 — `V0035` 마이그레이션

- `redirect_uris` 에 `https://store.hubwang.com/api/auth/callback/iam` 추가
- `client_settings` 의 post-logout 목록에 `https://store.hubwang.com/` 추가
- 🔴 **`JSON_SET`/`JSON_ARRAY_APPEND` 금지** — SAS 슬라이스 테스트가 **H2** 에서 돈다.
  `REPLACE()` 문자열 조작만 쓴다(`V0011` 헤더가 그 제약의 원장, `V0028`·`V0031`·`V0033` 선례)
- 🔴🔴 **파일 어디에도 `$` + 중괄호 형태를 쓰지 마라 — 주석 안에서도.** Flyway 가 **파일
  전체**에 placeholder 치환을 돌리고, 해소 못 하면 *"No value provided for placeholder"* 로
  마이그레이션이 죽어 **auth-service 기동 자체가 실패**한다. `V0031` 이 이걸로 **두 번**
  죽었다 — 두 번째는 그 사실을 설명하던 **주석 자신**이었다
- **멱등**: `WHERE … NOT LIKE '%store.hubwang.com%'` 가드. 재적용해도 배열이 안 자란다

## AC-2 — 시드 테스트

`OAuthClientPostLogoutRedirectUriSeedIntegrationTest` 에 새 항목을 단언한다.
🔵 그 테스트가 이미 `fan.hubwang.com` 을 단언하고 있으므로 형태는 그대로 따른다.

## AC-3 — 🔵 **런타임 데모 시드가 이 행을 건드리지 않음을 확인**

`infra/demo/seed-demo-domain.sh` 의 술어는 `WHERE jt.uri LIKE '%.local/%'` 다.
`store.hubwang.com` 은 매치되지 않으므로 **부팅마다 바이트 동일**해야 한다.
🔴 **술어를 읽는 것으로 끝내지 말고**, 그 스크립트가 그 사이 바뀌지 않았는지 확인한다
(`V0033` 이 이 성질을 근거로 *"runs once and is done"* 이라 적었다).

## AC-4 — 🔴 **후속을 이름으로 넘긴다**

이 티켓이 머지되면 `TASK-MONO-610` AC-4b 의 **4·5 행에 걸린 선행이 풀린다**. 그 사실을
610 에 적어라(그 티켓은 `in-progress` 라 본문 수정이 정당하다).
🔴 **«풀렸다» 를 «닫혔다» 로 적지 마라** — env 5줄은 여전히 소유자 몫이고, 최종 판정은
**로그인이 되는가**(610 의 V1–V8)다.

---

# Related Specs

- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0033__add_fan_vercel_domain_redirect_uri.sql` — **형태의 원본**
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0034__add_console_vercel_domain_redirect_uri.sql`
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § 단계 2
- `tasks/in-progress/TASK-MONO-610-put-the-idp-behind-one-stable-https-name-that-vercel-terminates.md` § AC-4b
- `projects/ecommerce-microservices-platform/apps/web-store/VERCEL.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (OIDC 콜백)

# Edge Cases

- **앵커가 이미 옮겨졌다** — 다른 마이그레이션이 그 사이 그 배열을 건드리면 `REPLACE()` 가
  **0행을 갱신하고 조용히 통과**한다. ⇒ AC-0 을 착수 시점에 다시 돌려라
- **`store.hubwang.com` 이 이미 있다** — 멱등 가드가 잡는다. 그 경우 이 티켓은 no-op 이고,
  **그렇다면 누가 넣었는지**를 찾아 적어라(원장 없는 변경이 이 축에서 이미 한 번 있었다 —
  `TASK-MONO-611`)
- **스토어의 provider id 가 바뀐다** — 경로 모양이 바뀌므로 등록도 틀려진다. 🔵 지금은
  `iam` 이고 두 출처가 일치한다

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 앵커가 안 맞아 0행 갱신 | 🔴 **에러 없이 통과**하고, 소유자가 나중에 `redirect_uri_mismatch` 를 만난다 | AC-0 + AC-2 의 단언 |
| `$`+중괄호를 주석에 씀 | auth-service **기동 실패** | AC-1 (V0031 이 두 번 겪었다) |
| `JSON_*` 함수 사용 | H2 슬라이스 테스트만 깨진다 | AC-1 |
| 기존 마이그레이션 수정 | Flyway 체크섬 불일치 → 기동 실패 | Scope Out |

---

# 🛠️ 구현 기록 (2026-09-02 UTC)

## ✅ AC-0 — 앵커를 **쟀다**. 그리고 「마지막 원소」는 직관과 달랐다

`V0012` → `V0016` → `V0024` → `V0028` 을 순서대로 읽어 재구성했다:

| 단계 | `ecommerce-web-store-client` 에 한 일 |
|---|---|
| `V0012` | `[localhost:3000/…/gap, web.ecommerce.local/…/gap]` · post-logout `[localhost:3000/, web.ecommerce.local/]` |
| `V0016` | post-logout 을 Jackson 기본타입 배열(`["java.util.ArrayList",[…]]`)로 교정 |
| `V0024` | `/api/auth/callback/gap` → **`/iam`** (redirect 만. post-logout 은 앱 루트라 무관) |
| `V0028` | `localhost:3001` 을 **`localhost:3000` 바로 뒤에** 끼워 넣음 |

⇒ **최종 꼬리는 둘 다 `web.ecommerce.local`** 이다. 🔴 `V0028` 이 중간에 삽입했으므로
「마이그레이션 순서상 마지막」은 `localhost:3001` 인데, **배열의 마지막은 아니다** —
티켓 AC-0 ②가 경고한 그 자리다. 앵커는 **배열 기준**으로 골랐다.

## 🔵 AC-0 이 하나 더 확인했다 — **앵커가 부팅마다 살아남는가**

앵커가 `.local` URI 라, 데모 시드가 그것을 덮어쓰면 두 번째 부팅부터 이 마이그레이션이
**0행 갱신 + SUCCESS** 가 된다(에러가 아니다). 그래서 스크립트를 열어 확인했다:

- 술어는 `WHERE jt.uri LIKE '%.local/%'` 이고 갱신은 **`JSON_MERGE_PRESERVE`** — 즉
  **덧붙이기만** 한다. 그 파일 자신이 그것이 의도라고 적는다(*"원본 `.local` 을 지우면
  같은 DB 를 로컬로 못 쓴다"*).
- `TASK-MONO-606` 의 회수 UPDATE 는 술어가 `%sslip.io%` 라 `hubwang.com` 을 안 건드린다.

⇒ 앵커는 **신선 볼륨에서도 데모 시드된 볼륨에서도** 존재한다. 덧붙여진 형제는
`web.ecommerce.<데모도메인>` 이라 **다른 문자열**이므로 매치는 정확히 1건이다.

## ✅ AC-1 — `V0035__add_store_vercel_domain_redirect_uri.sql`

`V0033` 의 형태를 그대로 따랐다. 티켓이 미리 든 함정 셋을 **산출물에서 실측**했다:

| 함정 | 실측 |
|---|---|
| Flyway placeholder(달러+중괄호) | **0건** |
| `JSON_*` 함수 (실행문 18줄 기준) | **0건** (주석의 금지 문구만 남음) |
| 앵커 부재 시 조용한 0행 | `LIKE '%…web.ecommerce.local…%'` 를 WHERE 에 **명시** — 앵커가 없으면 애초에 안 걸린다 |

🔵 **추가로 하나 더**: 기존 마이그레이션 **34개가 전부 순수 ASCII** 임을 확인하고
이 파일도 ASCII 로 맞췄다. Flyway 의 파일 인코딩은 **기동을 죽이는 축**이라, 주석
편의를 위해 모집단 유일의 예외가 되지 않는다. 🔴 그 검사는 처음에 `grep -P` 로 했다가
**로케일 오류를 `|| echo 0` 이 「0건」으로 위장**해서, python 으로 **양성 대조군과 함께**
다시 쟀다.

## ✅ AC-2 — 시드 통합테스트

`OAuthClientPostLogoutRedirectUriSeedIntegrationTest` 의 web-store 케이스에
post-logout `containsExactly` 꼬리와 redirect_uris `contains` 를 추가했다.
🔵 **그 테스트가 어디서 도는지 먼저 확인했다** — `ci.yml` 의 `iam-integration-tests`
(`:projects:iam-platform:apps:auth-service:integrationTest`, Testcontainers). 러너 없는
단언은 게이트가 아니다.

## ✅ AC-3 — 런타임 시드가 이 행을 안 건드린다

위 AC-0 곁가지에서 함께 확인했다(술어를 **읽는** 데서 그치지 않고 **현재 파일**을 열었다).

## ✅ AC-4 — 후속을 이름으로 넘겼다

`TASK-MONO-610` AC-4b 의 4·5 행을 「🔴 V0035 선행」 → 「✅ 선행 해소」로 바꾸고,
🔴 **「닫혔다」가 아님**을 같은 자리에 적었다 — 등록은 필요조건이고 `/api/auth/*` 는
소유자가 env 다섯 줄을 넣기 전까지 **500 그대로**다.

---

# 📌 남은 것

| | |
|---|---|
| 🙋 소유자 | `kanggle-store` env 5줄 → 기동 창 → V1–V8 (`TASK-MONO-610`) |
| 🔴 순서 | **이 PR 머지가 먼저다.** 4·5 를 먼저 넣으면 `redirect_uri_mismatch` 이고 그 오류는 URI 도 클라이언트도 이름으로 대지 않는다 |
