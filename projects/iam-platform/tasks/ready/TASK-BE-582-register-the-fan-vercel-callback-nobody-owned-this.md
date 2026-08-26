# Task ID

TASK-BE-582

# Title

팬의 **Vercel 콜백 `redirect_uri`** 를 IdP 에 등록한다 — 🔴 **두 티켓이 서로에게 떠넘겨 아무도 안 들고 있었다.**

# Status

ready

# Owner

iam-platform

# Task Tags

- oidc
- migration
- adr-067

---

# ⏳ 왜 이 티켓이 생겼나 — **떠넘기기를 실측했다 (2026-08-26)**

`ADR-MONO-067` 단계 4(팬)의 선행 중 **저장소 몫**이 하나 있는데, 그것을 든 티켓이 없다.

| 티켓 | 자기가 뭐라고 적었나 |
|---|---|
| `TASK-MONO-584` | *"IdP `redirect_uri` Flyway 시드 → 🔵 **`TASK-MONO-574`**(이미 소유)"* (§ 표 4행) |
| `TASK-MONO-574` | § Related Contracts — *"`redirect_uri` 등록이 계약이면 여기가 근거다. **변경은 이 티켓 범위 밖**(측정만 한다)."* |

⇒ **584 는 574 에게 넘겼고, 574 는 자기 범위 밖이라고 적었다. 아무도 안 든다.**

🔴 584 는 **같은 커밋에서** *"같은 일을 두 티켓이 들면 한쪽만 답을 받는다"* 를 경고했다
(`TASK-MONO-582` 의 AC-0 이 575 의 중복이었던 사건을 인용하면서). 그런데 여기서 일어난 것은
그 **반대**다 — **둘 다 안 들어서 일이 사라졌다.** 중복보다 이쪽이 조용하다: 중복은 답이 하나
와서 티가 나지만, 공백은 **아무 일도 안 일어나서** 기동 창을 한 번 낭비한 뒤에야 드러난다.

🔵 574 가 직접 적어 놨다: *"2·3 없이 기동하면 **예산만 쓰고 아무것도 못 잰다**."* 잔여 예산은
**104분**, 부팅 1회 **~11분**이다.

---

# Goal

`fan-platform-user-flow-client` 에 **`https://fan.hubwang.com`** 의 콜백과 로그아웃 랜딩을
등록해서, `TASK-MONO-574`(OIDC 왕복 실측)가 **측정하려던 것을 측정할 수 있게** 만든다.

🔴 **이 티켓은 왕복을 재지 않는다.** 재는 것은 574 다. 여기는 574 의 선행 3을 없애는 일이다.

---

# Context — 실측 (2026-08-26)

## ① 등록된 `redirect_uri` 전수 = **14건, 전부 `http://`**

```
http://admin.ecommerce.local/api/auth/callback/gap
http://console.local/api/auth/callback
http://fan-platform.local/api/auth/callback/gap
http://fan-platform.local/api/auth/callback/iam
http://localhost:3000/api/auth/callback          http://localhost:3000/api/auth/callback/gap
http://localhost:3000/api/auth/callback/iam      http://localhost:3000/callback
http://localhost:3001/api/auth/callback/gap      http://localhost:3001/api/auth/callback/iam
http://localhost:3002/api/auth/callback/iam      http://localhost:9001/callback
http://web.ecommerce.local/api/auth/callback/gap http://web.fan-platform.local/api/auth/callback/iam
```

🔴 **`https://` 는 전례가 0건이다.** 이 마이그레이션이 첫 번째다.

## ② 🔵 데모 시드 스크립트는 이 행을 **안 건드린다 — 그게 요점이다**

`infra/demo/seed-demo-domain.sh` 의 술어:

```sql
REPLACE(jt.uri, '.local/', @dom)   WHERE jt.uri LIKE '%.local/%'
```

`V0031` 헤더가 이름 붙여 뒀다: *"It **REWRITES** what is already registered; it does not
**INVENT** hostnames."*

⇒ `.local` 엔트리는 **부팅마다** `<ip-대시>.sslip.io` 로 재작성되지만, `fan.hubwang.com` 은
`.local/` 에 안 걸리므로 **한 번 넣으면 고정**이다.

🔵 **이것이 `TASK-MONO-574` 가 적은 "자체 도메인은 시드가 한 번으로 끝난다" 의 기전이다** —
산문이 아니라 술어로 확인했다. `vercel.app` 이었으면 preview URL 이 배포마다 달라 이게 성립 안 했다.

## ③ 🔴 모집단 — **왜 fan 하나만인가** (게으름이 아니다)

정본 표(`TEMPLATE.md` § 공개 호스트명 배분)에 호스트는 4개인데, 지금 등록할 것은 **1개**다:

| 호스트 | 지금? | 왜 |
|---|---|---|
| **`fan.hubwang.com`** | ✅ | 574 가 이걸로 재고, 586 이 이걸 요구한다. Vercel 프로젝트(`kanggle-fan`)가 **이미 존재** |
| `console.hubwang.com` | ❌ | 단계 3(`TASK-MONO-585`) 이후. Vercel 프로젝트가 **아직 없다** |
| `store.hubwang.com` | ❌ | 단계 2. 프로젝트 생성이 **`TASK-MONO-575` 게이트**에 걸려 있다 |
| `hubwang.com` (론처) | ❌ | 정적 페이지, 로그인 없음 |

🔴 **그리고 더 결정적인 이유: 콜백 경로 모양이 클라이언트마다 다르다.** 실측:

```
/api/auth/callback        ← platform-console (V0015)
/api/auth/callback/gap    ← ecommerce, fan 의 구 경로
/api/auth/callback/iam    ← fan 의 현 경로 (V0024 가 재작성)
/callback                 ← localhost:9001 등
```

**4가지가 공존한다.** console·store 의 것을 지금 넣으면 그건 실측이 아니라 **추측**이고,
틀리면 `redirect_uri_mismatch` 로 죽는데 그 오류는 **어느 URI 가 틀렸는지 말하지 않는다**(아래 ⑤).
각 표면은 **자기 단계에서 자기 마이그레이션**을 갖는다 — `V0031` 이 정확히 그 선례다.

## ④ 🔵 가드가 문다 — **좋은 소식이다**

`OAuthClientPostLogoutRedirectUriSeedIntegrationTest`:

| 단언 | 모양 | 이 변경에 |
|---|---|---|
| `post-logout-redirect-uris` | **`containsExactly(4개)`** | 🔴 **하드 핀 — 늘리면 RED** |
| `redirectUris` | `contains(...)` | 추가에 안전 |

⇒ 마이그레이션만 넣고 테스트를 안 고치면 **CI 가 잡는다.** 🔴 **같은 커밋에서 고친다** —
그리고 **가드를 느슨하게(`contains` 로) 바꿔서 통과시키지 마라.** 그 `containsExactly` 는
*"이 목록에 뭐가 있는지 아무도 모르게 되는 것"* 을 막으려고 거기 있다.

## ⑤ 🔴 이게 틀렸을 때의 증상 — `V0031` 헤더가 실측해 뒀다

```
HTTP/1.1 401
{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
```

*"The message names neither the redirect_uri nor the client. Every container is healthy and
the login form returns 200 — only the callback dies."*

⇒ **컨테이너가 전부 healthy 하고 로그인 폼이 200 을 준다.** 증상만 보면 인증 설정 문제로
보이지 않는다. 이 티켓을 건너뛰고 574 를 재면 **그 401 을 「스킴 경계 문제」로 오독**하게 되고,
574 는 자기 Edge Case 표에 그것을 이미 적어 뒀다 — *"이건 설정 미비이지 스킴 경계 문제가
아니다. 시드를 맞춘 뒤 다시 잰다."*

---

# Scope

## 포함

- `V0033__add_fan_vercel_domain_redirect_uri.sql` — `fan-platform-user-flow-client` 에
  `https://fan.hubwang.com/api/auth/callback/iam`(콜백) + `https://fan.hubwang.com/`(로그아웃 랜딩) 추가.
- `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` 의 `containsExactly` 갱신 — **같은 커밋**.

## 제외

- 🔴 **Vercel 대시보드 env** (`NEXTAUTH_URL` 등) → **소유자**. 이 티켓은 IdP 쪽만 한다.
- 🔴 **왕복 측정** → `TASK-MONO-574`. 이 티켓은 그 선행을 없앨 뿐이다.
- 🔴 **console / store 호스트** → ③ 참조. 각 단계의 자기 마이그레이션.
- 🔴 **D4 결정**(쿠키 스코프 · 움직이는 `issuer`) → `TASK-MONO-576`.
- 기존 `V0011` / `V0031` 편집 — **체크섬 잠긴 역사 기록**이다. 값 변경은 **전진 UPDATE** 로만.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다** (verify-then-act)

기억이 아니라 파일이 근거다. 하나라도 어긋나면 **STOP** 하고 이 티켓을 고친다.

1. 마이그레이션 최대 버전이 여전히 `V0032` 인가 — 아니면 다음 번호를 쓴다.
   ```bash
   ls projects/iam-platform/apps/auth-service/src/main/resources/db/migration/ | sort -V | tail -3
   ```
2. `containsExactly` 의 원소가 여전히 **4개**인가 (`V0028`·`V0031` 이후 누가 더 늘렸을 수 있다).
3. 🔴 **정본 표의 팬 호스트명이 여전히 `fan.hubwang.com` 인가** — `TEMPLATE.md` 의
   `<!-- PUBLIC-HOSTNAMES-BEGIN -->` 블록을 읽는다. 도메인이 바뀌었으면 이 티켓 전체가 낡았다.
4. 🔵 `seed-demo-domain.sh` 의 술어가 여전히 `LIKE '%.local/%'` 인가 — 이게 넓어졌으면
   ② 의 "고정이다" 가 깨진다.

## AC-1 — 마이그레이션은 **`V0031` 의 모양을 따른다**

🔴 이건 취향이 아니라 **세 번 데인 자리**다. `V0031` 헤더가 각각의 이유를 적어 뒀다:

| 규율 | 이유 (실측) |
|---|---|
| **문자열 `REPLACE` 만** — `JSON_SET`/`JSON_ARRAY`/`JSON_ARRAY_APPEND` 금지 | MySQL 전용이라 **H2 SAS 슬라이스 테스트가 깨진다**. 프로덕션·Testcontainers=MySQL, 슬라이스=H2 |
| **앵커 splice** — 기존 URI 를 앵커로 삼아 그 뒤에 끼운다 | `client_settings` 의 배열은 `["java.util.ArrayList", [...]]` 형태라 **원소 [0] 이 타입 태그**다. 직렬화 텍스트에 작업하면 그 함정을 통째로 피한다 |
| **`NOT LIKE` 멱등 가드** | 재적용해도 배열이 안 늘어난다 |
| 🔴🔴 **달러+중괄호 형태를 주석에도 쓰지 마라** | Flyway 가 **주석 포함 파일 전체**에 플레이스홀더 치환을 건다. 미해결이면 *"No value provided for placeholder"* 로 **auth-service 기동 자체가 죽는다**. `V0031` 은 이걸로 **두 번** 죽었다 — 헤더에서 한 번, 그리고 **그 수정을 설명한 주석에서 또 한 번** |

## AC-2 — 테스트를 **같은 커밋에서** 갱신하고, 왜 늘었는지 적는다

`containsExactly` 에 `"https://fan.hubwang.com/"` 를 **어느 위치에** 넣는지가
마이그레이션의 앵커와 일치해야 한다(순서가 단언된다).

🔴 **가드를 `contains` 로 완화하지 마라.** 늘어난 이유를 단언 메시지에 적는다 —
`V0028`·`V0031` 이 그렇게 해 뒀고, 그래서 오늘 그 목록이 **왜 4개인지 읽을 수 있었다.**

## AC-3 — 🔴 **`https` 가 처음이라는 것을 확인한다**

등록 URI 14건이 전부 `http://` 였다(§ ①). 스킴 검증이 어딘가에 있는지 **찾아보고**,
없으면 *"찾았고 없었다"* 를 적는다 — 🔵 **0건은 "없음" 이 아니다**, 찾은 자리를 적어야 0건이 근거가 된다.

## AC-4 — 판정은 **DB 에서** 한다, 마이그레이션 파일이 아니라

```sql
SELECT redirect_uris FROM oauth_clients WHERE client_id='fan-platform-user-flow-client';
```

🔴 파일이 존재한다 ≠ 행이 바뀌었다. Flyway 는 **이미 적용된 이력**이 있으면 새 파일만 돌린다 —
신선한 볼륨에서만 확인하면 **순서 결함에 영구히 초록**이다. 기존 볼륨에서도 확인한다.

## AC-5 — `TASK-MONO-574` 의 선행 표를 갱신한다

574 의 선행 3(`redirect_uri` 등록)을 ✅ 로 바꾸고 **이 티켓을 근거로 링크**한다.
🔴 574 의 **실측 블록(2026-08-23 관측)은 고치지 마라** — 처방과 선행 표만 손댄다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § 단계 4 · § D4
- `TEMPLATE.md` § 공개 호스트명 배분 (정본, `TASK-MONO-584`)
- `projects/iam-platform/specs/features/consumer-integration-guide.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/` — `redirect_uri` 등록이 계약 표면이라면
  🔴 **계약 문서가 먼저 갱신돼야 한다**(specs win over tasks). 착수 시 확인한다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 소유자가 아직 도메인을 Vercel 에 안 붙였다 | 🔵 **그래도 이 티켓은 진행 가능**하다 — IdP 등록은 도메인 응답과 독립이다. 다만 574 의 왕복 측정은 못 한다 |
| `containsExactly` 원소가 4개가 아니다 | 누가 사이에 더 넣었다는 뜻. **STOP**, AC-0 대로 티켓을 고친다 |
| Flyway 가 *"No value provided for placeholder"* 로 죽는다 | 🔴 주석에 달러+중괄호가 들어갔다. AC-1 참조 — `V0031` 이 이걸로 두 번 죽었다 |
| H2 슬라이스 테스트만 깨진다 | MySQL 전용 JSON 함수를 썼다는 신호. `REPLACE` 로 되돌린다 |
| 콜백이 401 인데 컨테이너는 전부 healthy | § ⑤ — 그게 정확히 `redirect_uri` 미등록의 지문이다 |

---

# Failure Scenarios

| 시나리오 | 결과 | 방지 |
|---|---|---|
| 이 티켓 없이 574 를 착수 | 기동 예산 **11분** 소진 후 401. 그 401 을 **「스킴 경계가 막는다」로 오독** → D4 결정이 틀린 근거 위에 서게 된다 | 574 선행 3 (AC-5) |
| console/store 호스트를 같이 추측 등록 | 경로 모양 4종 중 틀린 것을 고르면 `redirect_uri_mismatch`. 그 오류는 **어느 URI 가 틀렸는지 말하지 않는다** | § ③ — 각 단계의 자기 마이그레이션 |
| 테스트를 `contains` 로 완화 | 등록 목록에 **뭐가 있는지 아무도 모르게 된다**. 다음 사람이 오늘 읽은 것을 못 읽는다 | AC-2 |
| `V0011`/`V0031` 을 직접 편집 | 체크섬 불일치로 **auth-service 기동 실패** | Scope § 제외 |
