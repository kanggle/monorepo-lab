# Task ID

TASK-FAN-FE-018

# Title

🔴 **라우트 가드가 프로덕션에서 안 돈다** — `fan.hubwang.com` 의 모든 보호 경로가 로그인 없이 **200** 을 준다.

# Status

ready

# Owner

fan-platform

# Task Tags

- frontend
- auth
- demo
- vercel

---

# Goal

`src/middleware.ts` 는 `/login` · `/api/auth/*` 를 제외한 **모든** 경로를 가드하도록 선언돼
있는데, 배포된 판에서 **한 번도 발화하지 않는다.** 왜 안 도는지 확정하고 고친다.

🔴 **이 티켓은 «유출을 막는다» 가 아니다** — 아래 실측대로 **데이터는 안 샌다**. 고칠 것은
**선언과 실제가 다르다**는 것이고, 그 간극은 다음에 `/me` 가 실제 데이터를 렌더하는 순간
**유출로 바뀐다**.

---

# Context — 실측 (2026-08-27 UTC, `https://fan.hubwang.com`)

## ① 보호돼야 할 경로가 전부 200 이고, 리다이렉트가 **하나도** 없다

| 경로 | 결과 | 기대 |
|---|---|---|
| `/me` | **200** (HTML) | `302 → /login?from=%2Fme` |
| `/artists` | **200** | `302 → /login?...` |
| `/nonexistent-xyz` | **404** | 🔴 `302 → /login?...` — **matcher 에 걸리는 경로다** |
| `/login` | 200 | 200 ✅ (공개) |

🔴 **마지막 행이 판별자다.** 미들웨어는 **라우팅보다 먼저** 돌므로, 미인증 요청은 그 경로가
존재하든 말든 `/login` 으로 **먼저** 꺾여야 한다. **404 가 나왔다 = 미들웨어를 안 거쳤다.**

## ② 응답 헤더에 미들웨어 흔적이 없다

```
HTTP/1.1 200 OK
X-Matched-Path: /me            ← 페이지로 곧장 갔다
X-Vercel-Cache: MISS
(x-middleware-* 헤더 없음)
```

## ③ `/me` 는 **데이터를 안 흘린다** — 껍데기만 렌더된다

렌더된 가시 텍스트 전문:

```
fan-platform 피드 아티스트 멤버십 글쓰기 내 글 Account 로그아웃 내 정보
GAP 토큰에 포함된 클레임을 확인합니다.  tenant_id —  account_id —  roles —
```

클레임 세 칸이 전부 **`—`(빈 값)** 이다. ⇒ **유출 아님.** 그러나 미인증 방문자에게
«로그아웃 / 내 정보» 껍데기를 주는 것은 선언된 동작이 아니다.

## ④ NextAuth 자체가 **500** 이다 (같은 뿌리일 수 있다)

| 경로 | 결과 |
|---|---|
| `/api/auth/providers` | **500** `{"message":"There was a problem with the server configuration..."}` |
| `/api/auth/session` | **500** (동일) |
| `/api/auth/csrf` | **500** (동일) |

⇒ **`AUTH_SECRET`/`NEXTAUTH_SECRET` 계열 env 가 Vercel 프로젝트에 안 들어가 있다**는
전형적인 지문이다. `ADR-MONO-067` 단계 3(팬 env)이 «넣었는지 미확인» 으로 남아 있었다.

## ⑤ 파일명·프레임워크 버전은 **정상이다** (흔한 오진 하나를 미리 지운다)

- `next` = **^15.1.0** ⇒ `src/middleware.ts` 가 **맞는** 이름이다.
  🔵 Next **16** 의 `middleware.ts → proxy.ts` 개명 함정이 **아니다**. 이 저장소가 다른
  앱에서 그 함정에 데인 적이 있어 먼저 지웠다.
- `src/middleware.ts` 는 배포된 커밋의 소스에 **있다**(최종 수정 `02cf68fc9`, 08-25).
- `matcher` 는 `/((?!api/auth|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml).*)`
  ⇒ `/me` 와 `/nonexistent-xyz` 둘 다 **걸린다**.

---

# 가설 — 둘이고, **아직 안 갈렸다**

| # | 가설 | 뜻 |
|---|---|---|
| **A** | 미들웨어가 **배포에 안 실렸다** | 빌드 산출물에 미들웨어가 없다(루트 디렉터리·번들링·pnpm 워크스페이스 추론). env 를 넣어도 **안 고쳐진다** |
| **B** | 미들웨어는 돌지만 `await auth()` 가 **던지고**, 그 실패가 페이지로 **흘러내린다** | ④ 와 같은 뿌리. env 를 넣으면 **둘 다 고쳐진다** |

🔴 **지금 증거는 A 쪽에 조금 더 무겁다** — B 라면 던진 미들웨어가 보통 **500** 을 내는데
페이지 경로는 전부 **200/404** 로 깨끗하다. 🔴 **그러나 무게일 뿐 판정이 아니다.**

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다** (verify-then-act)

🔴 **오늘 값을 상속하지 마라.** 소유자가 그 사이 env 를 넣었거나 재배포했을 수 있고,
그러면 이 티켓의 절반이 이미 사라져 있다. ① ~ ④ 를 **그대로 다시 찍는다.**

🔴 라이브 측정에는 **`curl --ssl-no-revoke`** 를 쓴다. 이 호스트가 캡티브 포털 뒤에 있으면
평문은 302 로 가로채이고 HTTPS 는 `curl 000` 이 되어 **측정이 통째로 거짓**이 된다.
**`000` 을 «죽었다» 로 읽지 마라.**

## AC-1 — **A/B 를 가른다**

**순서가 중요하다** — 싼 것부터:

1. Vercel 프로젝트에 `AUTH_SECRET`(및 필요한 provider env)을 넣고 **재배포**한다.
2. `/api/auth/session` 이 **200** 이 되는지 본다 ⇒ env 축이 고쳐졌다는 **독립 확인**.
3. 그 뒤 `/nonexistent-xyz` 를 다시 찍는다.
   - **302 → `/login`** 이면 **가설 B** 였다 ⇒ 이 티켓은 여기서 끝난다.
   - 여전히 **404** 면 **가설 A** 다 ⇒ AC-2 로 간다.

🔴 **2번을 건너뛰지 마라.** 그것 없이 3번만 보면 «env 를 넣었는데 안 고쳐졌다» 와
«env 가 여전히 안 들어갔다» 가 **같은 출력**을 낸다.

## AC-2 — 가설 A 라면 **산출물에서** 확인한다

Vercel 빌드 로그 / 산출물에 미들웨어 항목이 **있는지** 본다(`ƒ Middleware` 줄, 또는
`.next/server/middleware-manifest.json` 의 `matchers` 가 비었는지).
🔴 **소스에 파일이 있다는 것은 증거가 아니다** — ⑤ 가 이미 그걸 확인했고 그래도 안 돌았다.

## AC-3 — 고친 뒤 **네 칸을 다시 찍는다**

| 경로 | 기대 |
|---|---|
| `/me` (미인증) | `302 → /login?from=%2Fme` |
| `/artists` (미인증) | `302 → /login?...` |
| `/nonexistent-xyz` (미인증) | `302 → /login?...` |
| `/login` | **200** — 🔵 **음성 대조군**. 이게 같이 302 가 되면 리다이렉트 루프다 |

🔴 `/login` 칸을 빼지 마라. 그것이 없으면 «전부 302» 라는 **잘못된 고침**이 초록으로 보인다.

## AC-4 — 가드를 **테스트로** 건다

미들웨어가 안 돌면 **빨개지는** 테스트가 지금 하나도 없다(그래서 08-25 부터 조용히 안 돌았다).
🔴 **선언 파일을 grep 하는 가드는 안 된다** — ⑤ 가 보여주듯 파일은 내내 **있었다**.
가드는 **요청의 결과**를 봐야 한다(미인증 요청 → 리다이렉트).

🔵 **어디서 도는가를 먼저 정한다** — 러너가 없는 스위트는 썩는다. 팬 e2e 는 `nightly-e2e.yml`
에만 있는지 `ci.yml` 에도 있는지 확인하고, **밤에만 도는 곳에 두면 머지 시점에 안 잡힌다**는
사실을 티켓에 적는다.

---

# Related Specs

- `projects/fan-platform/web/fan-platform-web/src/middleware.ts` — 선언된 가드
- `projects/fan-platform/web/fan-platform-web/src/shared/auth/auth.ts` — `auth()` 출처
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` — 단계 4(팬), 단계 3(팬 env)
- `tasks/ready/TASK-MONO-586-fan-is-already-on-vercel-and-cannot-reach-the-backend.md`
- `tasks/review/TASK-BE-582-...` — 팬 Vercel 콜백 `redirect_uri` 등록

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 착수 시 이미 고쳐져 있다 | 🔵 **닫지 마라** — AC-4(가드)는 그대로 필요하다. 고쳐진 이유를 적고 AC-4 만 남긴다 |
| env 를 넣었더니 `/api/auth/*` 는 200 인데 `/me` 는 여전히 200 | **가설 A 확정**. AC-2 로 간다 |
| 리다이렉트가 `/login` 이 아니라 외부로 간다 | 🔴 **멈춘다** — `NEXTAUTH_URL` 오설정이고 열린 리다이렉트가 될 수 있다 |
| 전부 302 가 됐다 (`/login` 포함) | 리다이렉트 루프. AC-3 의 음성 대조군이 잡는다 |
| 로컬에서는 잘 된다 | 🔴 **그것이 이 결함의 모양이다.** 로컬 `next dev` 는 미들웨어를 다르게 싣는다 — 판정은 **배포된 판**에서만 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| env 만 넣고 닫는다 | `/api/auth/*` 는 200 인데 가드는 여전히 안 돎 | AC-1 의 3번(`/nonexistent-xyz`)을 **반드시** 다시 찍는다 |
| «파일이 있으니 됐다» 로 판정 | 소스 grep 초록, 프로덕션 빨강 | AC-2 — **산출물**에서 본다 |
| 가드를 nightly 에만 건다 | 머지 시점에 안 잡히고 다음날 main 이 빨강 | AC-4 § 어디서 도는가 |
| `/me` 가 나중에 실데이터를 렌더 | **오늘의 «유출 아님» 이 유출로 바뀐다** | 이 티켓을 그 전에 닫는다 |
