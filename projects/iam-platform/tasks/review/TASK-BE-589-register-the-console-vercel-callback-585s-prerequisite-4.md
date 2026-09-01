# Task ID

TASK-BE-589

# Title

콘솔의 **Vercel 콜백 `redirect_uri`** 를 IdP 에 등록한다 — `TASK-MONO-585` 선행 4 의 저장소 몫.

# Status

review

# Owner

iam-platform

# Task Tags

- oidc
- migration
- adr-067

---

# ⏳ 왜 이 티켓이 생겼나

`TASK-MONO-585`(= `ADR-MONO-067` 단계 3) 는 선행 넷을 들고 있는데, 그중 **4번**만
성격이 다르다.

| # | 선행 | 성격 |
|---|---|---|
| 1 | `ADR-MONO-069` D4 (OIDC 왕복) | 🟡 **결정은 났다**(`C2`), 배선은 `TASK-MONO-610` |
| 2 | `ADR-MONO-068` 승격 트리거 | ✅ 결정 끝(`C`) |
| 3 | Vercel 프로젝트 추가 가능한가 | ⏳ `TASK-MONO-575` |
| **4** | **콘솔 `redirect_uri` 시드** | 🔵 **지금 할 수 있다** — 아래 |

🔵 **585 가 직접 적었다**: *"이것은 D4 의 선택지와 무관한 공통 선행이다 — `ADR-MONO-069` 의
A·B·C·D 어느 쪽이든 필요하다(`redirect_uri` 는 **앱 주소**라 issuer 를 어떻게 고정하든 안
바뀐다)."* 그리고 *"착수 시 `TASK-BE-582` 와 같은 모양의 iam-platform 마이그레이션을
기안하라."* — 이 티켓이 그것이다.

🔴 `TASK-MONO-610` 은 이 자리를 **명시적으로 범위 밖**에 뒀다. 585 는 **블로킹된 채로**
이것을 들고 있었다. ⇒ 585 안에 두면 610 이 끝날 때까지 아무도 못 한다.

## 🔴🔴 이 저장소는 정확히 같은 공백을 **팬에서 이미 한 번 밟았다**

`TASK-MONO-584` 가 `TASK-MONO-574` 에게 넘기고, 574 가 *"내 범위 밖"* 이라고 적어서
**아무도 안 들었다.** 그래서 `TASK-BE-582`(`V0033`)를 새로 기안해야 했다.
*"중복보다 공백이 조용하다"* — 중복은 답이 하나 와서 티가 나지만, 공백은 **아무 일도
안 일어나서** 기동 창을 한 번 낭비한 뒤에야 드러난다.

---

# Goal

`platform-console-web` 클라이언트에 **`https://console.hubwang.com`** 의 콜백과 로그아웃
랜딩을 등록해서, `TASK-MONO-585`(단계 3 이관)가 착수할 때 **로그인이 죽어 있지 않게** 한다.

🔴 **이 티켓은 콘솔을 Vercel 로 옮기지 않는다.** 그건 585 다. 여기는 585 의 선행 4 를
없애는 일이고, 585 의 나머지 선행(610 배선)과 **독립**이다.

---

# Context — 실측 (2026-09-01)

## ① 콘솔 클라이언트에 등록된 것 — **4건, 전부 `http://`**

파일에서 셌다(마이그레이션 전수 `V0011`–`V0033`, `client_id='platform-console-web'`):

| 컬럼 | 값 | 출처 |
|---|---|---|
| `redirect_uris` | `http://console.local/api/auth/callback` | `V0015` |
| | `http://localhost:3000/api/auth/callback` | `V0015` |
| `post-logout-redirect-uris` | `http://console.local/login` | `V0021` |
| | `http://localhost:3000/login` | `V0021` |

`console.hubwang.com` 은 **저장소 어디에도 없다** — 유일한 등장이 `V0033` 의 주석
(*"console 과 store 는 일부러 뺐다"*)과 `ADR-MONO-069` 의 공백 기록이다.

🔵 **이후 마이그레이션이 이 넷을 안 건드렸음을 확인했다**: `V0020`(토큰교환 grant) ·
`V0023`(erp.write scope) · `V0024`(tenant slug `gap`→`iam`) 은 콘솔 행을 건드리지만
**URI 컬럼은 아니고**, `V0028`(dev 포트)은 web-store·fan 만 건드린다.

## ② 경로 모양은 **팬과 다르다** — 추정하지 말고 실측한 값을 쓴다

🔴 `V0033` 헤더가 미리 경고한 자리다: *"등록된 콜백 **경로**는 클라이언트마다 다르다 —
오늘 네 가지 모양이 공존한다. 호스트명을 틀린 경로 모양으로 등록하면
`redirect_uri_mismatch` 로 죽고, 그 에러는 URI 도 클라이언트도 이름 대지 않는다."*

콘솔의 두 값을 **앱 소스에서** 확인했다(마이그레이션이 아니라):

| 값 | 앱이 실제로 보내는 것 | 파일 |
|---|---|---|
| `redirect_uri` | `env.OIDC_REDIRECT_URI` **그대로** | `app/api/auth/login/route.ts:49` · `app/api/auth/callback/route.ts:106` |
| `post_logout_redirect_uri` | `new URL('/login', publicOrigin(env))` — **쿼리스트링 없음** | `app/api/auth/logout/route.ts:71` |

⇒ 콘솔은 `/api/auth/callback` (**`/iam` 접미사 없음** — `TASK-MONO-460` 이 이미 기록) ·
로그아웃 랜딩은 `/login` (**팬은 `/`**). 팬을 복사하면 둘 다 틀린다.

🔵 `OIDC_REDIRECT_URI` 는 `z.string().url()` 로 **기본값이 없다**(`env.ts:52`) — 환경마다
주입되는 값이다. Vercel 환경에 무엇을 넣을지는 **소유자 몫**이고 이 티켓 밖이다. 이 티켓은
그 값이 `https://console.hubwang.com/api/auth/callback` 일 때 **IdP 가 거절하지 않게**만 한다.

## ③ 부팅 시드는 이 행을 **안 건드린다** — 그리고 그게 요점이다

`infra/demo/seed-demo-domain.sh` 의 술어는 여전히 `WHERE jt.uri LIKE '%.local/%'` 다.
`console.hubwang.com` 은 매치하지 않으므로 **부팅마다 바이트 동일**하게 남는다.
🔵 그 스크립트 헤더가 이미 적어 뒀다: *"넓은 술어를 쓰지 마라 —
`.local`·`localhost`·`hubwang.com` 을 같이 지운다."*

## ④ 정본 호스트명

`TEMPLATE.md` `<!-- PUBLIC-HOSTNAMES-BEGIN -->` 블록 (`check-public-domains.sh` 가 지킨다):

```
| console | console.hubwang.com | console.local | 미생성 | ⏳ 단계 3 (404) |
```

---

# Scope

## 포함

- `V0034__add_console_vercel_domain_redirect_uri.sql` — `platform-console-web` 에
  `https://console.hubwang.com/api/auth/callback`(콜백) +
  `https://console.hubwang.com/login`(로그아웃 랜딩) 추가.
- `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` 의 콘솔 케이스 `containsExactly`
  갱신 — **같은 커밋**.
- `PlatformConsoleOidcClientSeedIntegrationTest` 에 `redirect_uris` 단언 추가 — **같은 커밋**.
- `specs/features/multi-tenancy.md` § OIDC public client 의 redirect 문장 갱신.

## 제외

- 🔴 **Vercel 프로젝트 생성 / 대시보드 env**(`OIDC_REDIRECT_URI` 주입) → **소유자** · `TASK-MONO-585`.
- 🔴 **콘솔의 Vercel 이관 자체** → `TASK-MONO-585`.
- 🔴 **issuer 를 HTTPS 로 고정하는 배선** → `TASK-MONO-610`(`ADR-MONO-069` `C2`).
- 🔴 **왕복 실측** → 610 의 V1–V7 (기동 창).
- 기존 `V0015`/`V0021` 편집 — **체크섬 잠긴 역사 기록**. 값 변경은 **전진 UPDATE** 로만.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다** (verify-then-act)

기억이 아니라 파일이 근거다. 하나라도 어긋나면 **STOP** 하고 이 티켓을 고친다.

1. 마이그레이션 최대 버전이 여전히 `V0033` 인가 — 아니면 다음 번호를 쓴다.
   ```bash
   ls projects/iam-platform/apps/auth-service/src/main/resources/db/migration/ | sort -V | tail -3
   ```
2. 콘솔 post-logout `containsExactly` 의 원소가 여전히 **2개**인가.
3. 🔴 정본 표의 콘솔 호스트명이 여전히 `console.hubwang.com` 인가 (`TEMPLATE.md`).
4. 🔵 `seed-demo-domain.sh` 술어가 여전히 `LIKE '%.local/%'` 인가.
5. 🔴 **콘솔의 콜백 경로가 여전히 `/api/auth/callback` 이고 로그아웃 랜딩이 `/login` 인가**
   — 마이그레이션이 아니라 **앱 라우트**에서 확인한다(② 의 두 파일).

## AC-1 — 마이그레이션은 **`V0031`/`V0033` 의 모양을 따른다**

🔴 취향이 아니라 **데인 자리**다:

| 규율 | 이유 (실측) |
|---|---|
| **문자열 `REPLACE` 만** — `JSON_SET`/`JSON_ARRAY`/`JSON_ARRAY_APPEND` 금지 | MySQL 전용이라 **H2 SAS 슬라이스 테스트가 깨진다** |
| **앵커 splice** | `client_settings` 배열은 `["java.util.ArrayList", [...]]` 라 **원소 [0] 이 타입 태그**다. 직렬화 텍스트에 작업하면 그 함정을 통째로 피한다 |
| **`NOT LIKE` 멱등 가드** | 재적용해도 배열이 안 늘어난다 |
| 🔴🔴 **달러+중괄호 형태를 주석에도 쓰지 마라** | Flyway 가 **주석 포함 파일 전체**에 플레이스홀더 치환을 건다. `V0031` 은 이걸로 **두 번** 죽었다 |

🔵 `V0021` 은 `JSON_SET` 을 썼지만 그건 **이 규율이 생기기 전**이고, 그 파일 자신이
*"H2 슬라이스는 Flyway 를 끈다"* 로 정당화한다. `V0028`·`V0031`·`V0033` 이 셋 다
`REPLACE` 로 갔다 — **최신 형제를 따른다.**

## AC-2 — 앵커는 **마지막 원소**여야 한다

`containsExactly` 는 **순서를 단언**한다. 새 항목이 꼬리에 붙도록 두 배열 모두
**마지막 원소**를 앵커로 삼는다(`http://localhost:3000/api/auth/callback` /
`"http://localhost:3000/login"`).

🔴 **가드를 `contains` 로 완화하지 마라.** 늘어난 이유를 단언 메시지에 적는다 —
그래서 오늘 그 목록이 **왜 2개인지** 읽을 수 있었다.

## AC-3 — 🔴 **콘솔 클라이언트의 첫 `https` 라는 것을 확인한다**

`V0033` 이 테이블 전체의 첫 `https` 였다. 이 티켓은 **콘솔 행의** 첫 `https` 다.
스킴을 검증하는 코드가 있는지 **찾아보고**, 없으면 *"찾았고 없었다"* 를 **찾은 자리와 함께**
적는다 — 🔵 **0건은 "없음" 이 아니다.**

## AC-4 — 판정은 **DB 에서** 한다, 마이그레이션 파일이 아니라

```sql
SELECT redirect_uris, client_settings FROM oauth_clients
 WHERE client_id = 'platform-console-web';
```

🔴 파일이 존재한다 ≠ 행이 바뀌었다. 🔴 그리고 **CI 는 항상 신선 볼륨**이라
마이그레이션 순서/멱등성 결함에 **영구히 초록**이다.

| 축 | 어디서 |
|---|---|
| **신선 볼륨** | CI `Integration (iam A/B, Testcontainers)` — 이 티켓이 닫는다 |
| **기존 볼륨** | 🔴 **기동 창** — 이 티켓이 닫지 **않는다**. AC-5 로 넘긴다 |

## AC-5 — 기존-볼륨 판정을 **기동 창 번들에 명시적으로 넘긴다**

`TASK-BE-582` 는 이 칸을 열어 둔 채 닫았고, 그것을 줍는 데 별도 티켓(`TASK-MONO-605`)이
필요했다. 같은 일을 반복하지 않는다 — `TASK-MONO-610` 의 기동-창 검증 목록에
**한 줄을 추가**한다:

```sql
SELECT redirect_uris FROM oauth_clients WHERE client_id='platform-console-web';
-- https://console.hubwang.com/api/auth/callback 이 실제로 있는가
```

## AC-6 — `TASK-MONO-585` 의 선행 4 를 ✅ 로 바꾸고 이 티켓을 링크한다

🔴 585 의 **실측 블록은 고치지 마라** — 선행 표와, 선행 4 를 서술한 § 만 손댄다.
🔴 **"585 가 풀렸다" 로 쓰지 마라** — 585 의 진짜 게이트는 선행 1(610 배선)이고
이 티켓은 거기에 아무 영향이 없다.

---

# ✅ 구현 결과 (2026-09-01)

## AC-0 — **5/5 통과**

| # | 잰 것 | 결과 |
|---|---|---|
| 1 | 마이그레이션 최대 버전 | `V0033` ⇒ **`V0034` 를 쓴다** |
| 2 | 콘솔 post-logout 핀 원소 수 | **2개** (`V0021` 그대로) |
| 3 | 정본 표의 콘솔 호스트명 | `console.hubwang.com` **유지** |
| 4 | `seed-demo-domain.sh` 술어 | `LIKE '%.local/%'` **유지** |
| 5 | 콘솔 콜백 경로 · 로그아웃 랜딩 | `/api/auth/callback` · `/login` — **앱 라우트에서 재측** |

🔵 **표에 없던 것 하나를 더 셌다**: 시드 이후 마이그레이션이 이 넷을 안 건드렸는지.
`V0020`(grant) · `V0023`(scope) · `V0024`(tenant slug) 는 콘솔 행을 건드리지만 **URI
컬럼은 아니고**, `V0028`(dev 포트)은 web-store·fan 만 건드린다. ⇒ 「시드 값 그대로」를
**상속하지 않고 확인**했다.

## AC-1 — 🔴 **팬을 복사했으면 두 칸이 틀렸다**

| | 콜백 | 로그아웃 랜딩 |
|---|---|---|
| fan (`V0033`) | `/api/auth/callback/iam` | `/` |
| **console (`V0034`)** | `/api/auth/callback` (**접미사 없음**) | **`/login`** |

`V0033` 헤더가 미리 겨눈 자리다 — *"경로 모양은 클라이언트마다 다르고, 호스트명을 틀린
모양으로 등록하면 `redirect_uri_mismatch` 로 죽는데 그 에러는 URI 도 클라이언트도 이름
대지 않는다."* 값은 마이그레이션이 아니라 **앱 라우트**에서 가져왔다.

규율 4칸: `REPLACE` 만(`JSON_SET` 금지) · 앵커 splice · `NOT LIKE` 멱등 가드 ·
달러+중괄호 **0건**(파일 전체 `$` 문자가 **0개**, `V0033` 과 동일).

🔵 `V0021` 은 **이 클라이언트에** `JSON_SET` 을 썼지만 그건 이 규율 이전이고 자기 헤더에서
*"H2 슬라이스는 Flyway 를 끈다"* 로 정당화한다. **최신 형제 셋**(`V0028`·`V0031`·`V0033`)이
`REPLACE` 로 수렴했으므로 그쪽을 따랐다.

## AC-2 — 🔴🔴 **DB 없이 두 `REPLACE` 를 시뮬레이션하고, 대조군을 세 칸 놓았다**

Docker 미가동이라 Testcontainers IT 를 로컬에서 못 돌린다. 그래서 문자열 연산만이라도
**눈이 있는지 먼저 증명**했다. 🔴 앵커·치환 문자열은 **마이그레이션 파일에서 파싱**한다 —
다시 타이핑하면 파일이 바뀌어도 이 하네스는 계속 초록이다.

| 칸 | 결과 |
|---|---|
| **양성** — 앵커가 시드값에 몇 번 나오나 | 두 컬럼 다 **정확히 1회** |
| **양성** — 1회 적용 결과 | 기대 3원소, 순서 일치, **타입 태그가 원소 [0] 에 그대로** |
| **음성 A** — 가드가 없었다면 2회차가 뭘 하나 | 새 호스트 **2회 = 중복을 실제로 만든다** ⇒ 이 대조군은 눈이 있다 |
| **음성 B** — 앵커에 오타를 내면 | **조용한 무동작** (`REPLACE` 는 rc=0) |

🔵 시뮬레이션은 `client_settings` 를 **MySQL 이 렌더하는 `", "` 간격**으로 재구성해서
돌렸다 — 앵커가 콤마 뒤 공백에 의존하지 않는다는 것까지 같이 잰 셈이다.

🔴 **이건 「DB 판정」이 아니다.** 문자열 연산의 대조군일 뿐이고, 행 판정은 AC-4 다.

## AC-3 — 🔴 **스킴을 검증하는 코드는 0건. 찾은 자리를 적는다**

0건은 「없음」이 아니므로 **어디를 봤는지**가 근거다:

| 본 자리 | 결과 |
|---|---|
| `OAuthClientMapper` 읽기 경로 | 저장된 URI 를 `builder.redirectUri(uri)` 로 **그대로 통과** — 검사 없음 |
| `OAuthLoginUseCase.validateRedirectUri` | **소셜 로그인 축**의 provider allowlist **멤버십** 테스트. 스킴을 안 본다 |
| `infrastructure/oauth2` 전체 grep (`https?://`·`scheme`·`requireHttps`·`isSecure`) | 3건, 전부 무관 (issuer `@Value` 기본값 1 + 인증 *scheme* 주석 2) |

⇒ `http` 오타는 **기동 때 안 잡히고** 브라우저 왕복에서야 죽는다. 값을 정본 표에서
한 번에 쓴 이유다.

## AC-4 — **절반**. 나머지는 AC-5 가 들고 간다

| 축 | 상태 |
|---|---|
| **신선 볼륨** | ⏳ CI `Integration (iam, Testcontainers)` 가 닫는다 — 🔴 **CI 가 권위다**(로컬 미실행) |
| **기존 볼륨** | 🔴 **이 티켓이 안 닫는다** — CI 는 항상 신선 볼륨이라 순서/멱등성 결함에 **영구히 초록** |

핀은 세 곳에 걸었다:

- `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` — post-logout **List**,
  `containsExactly`(**순서 단언**) + 매퍼 복사본 `containsExactlyInAnyOrder`.
- `PlatformConsoleOidcClientSeedIntegrationTest` — `getRedirectUris()`
  **`containsExactlyInAnyOrder`**. 🔵 여기서 `containsExactly` 를 안 쓴 이유: SAS 가 이
  값을 **`Set`** 으로 노출한다(jar 를 `javap` 로 확인). 순서를 단언하면 마이그레이션
  순서가 아니라 **셋의 순회 순서**를 단언하게 된다. 옆의 post-logout 은 커스텀 세팅을
  통해 **`List`** 로 왕복하므로 거기선 순서가 진짜다 — **그릇이 다르면 술어도 달라야 한다.**
- 🔴🔴 **그리고 위 셋 중 어느 것도 이 마이그레이션의 진짜 실패 모드를 못 본다.**
  `REPLACE` 의 실패는 **원소 중복**인데 `Set` 이 조용히 dedupe 한다 ⇒ 중복된 행에서도
  전부 초록이다. 그래서 **생컬럼을 JDBC 로 읽어 「따옴표로 감싼 완전한 배열 원소」가
  정확히 1회인지** 세는 보조 단언을 넣었다(**0 = UPDATE 가 한 행도 안 침 · >1 = 중복**).
  🔵 따옴표는 장식이 아니다 — 없으면 나중에 `…/callback/iam` 처럼 **이것을 접두사로 갖는
  URI** 가 등록될 때 있지도 않은 중복을 신고한다.

## AC-5 — 기동-창 판정을 **미리** 넘겼다

`TASK-MONO-610` AC-3 에 **`V8` 행**을 추가했다(기동 창 공유 · `flyway_schema_history`
최대 버전이 `V0034` 미만인지 확인하는 유효성 술어 포함).

🔵 **`TASK-BE-582` 가 이 칸을 열어 둔 채 닫았고, 줍는 데 티켓 하나(`TASK-MONO-605`)가 더
들었다.** 같은 일을 반복하지 않으려고 착수 시점에 붙였다.

## AC-6 — 585 갱신

선행 표 4행 ✅ 전환 + § 신규 절에 「닫혔다」 기록 + 루트 `tasks/INDEX.md` 의 585 행.
🔴 **「585 가 풀렸다」로 쓰지 않았다** — 진짜 게이트는 여전히 선행 1(610 배선)이다.
🔴 585 의 **실측 블록은 손대지 않았다.**

## 곁가지 — 안 깨진 것을 **확인**했다

- `verify-demo-wrapper.sh` 의 `(k)` 가드(마이그레이션의 `.local` 콜백 전수)는 모집단이
  **14 → 14, 차집합 공집합**이다. `V0034` 가 새로 넣은 것은 `.hubwang.com` 뿐이고, 앵커로
  쓴 두 `.local` 리터럴은 이미 `V0015`/`V0021` 에 있었다.
- `compileTestJava` **rc=0** — 🔵 파이프 없이 파일로 받아 `$?` 를 직접 읽었다.

## 🔴 곁가지 — 리듬 하나를 틀렸다

이 파일을 `ready/` 에서 **곧장 `review/` 로** 옮기고 구현 결과를 쓰려다 훅이 막았다
(HARDSTOP-05). `review/` 는 얼어 있고, **작업 문서는 `in-progress/`** 다 —
`TASK-MONO-589` 가 정확히 이 혼동을 고쳤다. 되돌려 `in-progress/` 에서 쓰고 마지막에
`review/` 로 옮겼다. 🔵 훅이 없었으면 결과가 **frozen 파일에 쓰인 채로** 넘어갔을 것이다.

---

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § OIDC public client `platform-console-web`
- `TEMPLATE.md` § Local Network Convention — `<!-- PUBLIC-HOSTNAMES-BEGIN -->`
- `docs/adr/ADR-MONO-067-*.md` 단계 3
- `docs/adr/ADR-MONO-069-*.md` § 공백 표 (이 공백을 기록한 곳)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3 (콜백 라우트)

# Edge Cases

- **`OIDC_REDIRECT_URI` 가 다른 값으로 주입된다** — 등록은 정확 일치이므로 `redirect_uri_mismatch`.
  🔵 이 티켓은 정본 표의 호스트명 하나만 등록한다. 다른 값이 필요하면 그때 **또 하나의 전진
  마이그레이션**이지, 이 파일 편집이 아니다.
- **부팅 시드가 이 행을 덮어쓴다** — ③ 에서 술어로 배제. 술어가 넓어지면 깨지므로 AC-0 ④ 가 잰다.
- **`http://console.hubwang.com` 로 오탈자** — Vercel 은 HTTPS 전용이라 브라우저가 그 값을
  보내지 않는다. 스킴은 선택이 아니다.
- **재적용** — `NOT LIKE` 가드로 멱등.

# Failure Scenarios

- **행이 안 바뀐다** (앵커 오타 / `WHERE` 불일치) → `UPDATE` 가 0행을 치고 **rc=0**.
  🔴 그래서 판정을 파일이 아니라 **DB 에서** 한다 (AC-4).
- **`containsExactly` 순서가 어긋난다** → CI RED. 🔵 이건 원하는 실패다 — 앵커가
  의도한 자리에 안 붙었다는 뜻이다.
- **Flyway 플레이스홀더 미해결** → *"No value provided for placeholder"* 로 **auth-service
  기동 자체가 죽는다**. AC-1 의 마지막 행.
