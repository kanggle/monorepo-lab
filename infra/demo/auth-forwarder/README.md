# `auth-forwarder` — `auth.hubwang.com` 의 현관

`ADR-MONO-069 § C2` (2026-09-01 ACCEPTED, rider = *"apex 쿠키는 호스트 한정"*) 의 구현.
`TASK-MONO-610`.

```
브라우저 ──HTTPS──> Vercel(kanggle-auth) ──HTTP──> http://iam.<그날 IP>.sslip.io
```

Vercel 이 TLS 를 끝내므로 브라우저는 평문을 **한 번도 보지 않는다.** 인증서도 DNS 쓰기도
필요 없다 — `C1`(EC2/Traefik 종단)과 갈리는 지점이 정확히 그것이다.

그날 IP 는 [`@demo/backend-resolver`](../backend-resolver/)(`ADR-MONO-068 § D6 = B2`)가 준다.
🔵 **이 앱은 해석기를 구현하지 않는다** — `scripts/check-demo-resolver-copies.sh` 가
«앱이 자기 구현을 갖지 않는가» 를 재고, 이 앱은 그 모집단 안에 있다.

---

## 🔵 이 앱이 `projects/` 밖의 **첫 Next 앱**이다

`TASK-MONO-613` 이 해석기 가드의 모집단을 «경로 규약» 에서 «선언(`next.config.*`)» 으로
옮기면서 *"오늘 그 구멍은 잠재적이다 — 그러나 `TASK-MONO-610` 이 만들려는 것이 정확히
**첫 번째 예외**다"* 라고 적었다. 그 예외가 이것이고, 가드는 지금 **선언 앱 4개**를 본다.
🔴 613 이 없었다면 이 앱은 모집단 **밖**이었고, 여기에 해석기를 복사해 두어도
아무 가드도 물지 않았을 것이다.

---

## 🔴 두 가지 설계 결정 — 둘 다 실측에서 나왔다

### ① 업스트림에 보내는 `Host` 는 `iam.<DEMO_DOMAIN>` 이다 (공개 이름이 아니다)

데모 앞단은 Traefik 이고 라우터를 **`Host` 로** 고른다. `auth.hubwang.com` 을 그대로
넘기면 DNS 도 TCP 도 성공하는데 **Traefik 이 404** 를 낸다 — 이 저장소에서 진단이 가장
오래 걸리는 종류다(`TASK-MONO-389`).
🔵 `fetch()` 는 URL 에서 `Host` 를 스스로 만든다 ⇒ 들어온 `host` 헤더를 **복사하지 않는 것**이
곧 이 결정의 구현이다.

### ② 공개 이름은 `X-Forwarded-*` 로 넘긴다

`TASK-MONO-610 AC-0` 실측 (2026-09-01, 로컬 IdP 컨테이너):

| 조건 | IdP 가 낸 `Location` |
|---|---|
| `X-Forwarded-Proto` **없음** (음성 대조군) | `http://auth.hubwang.com/login` |
| `X-Forwarded-Proto: https` | ✅ `https://auth.hubwang.com/login` |

🔵 **대조군이 반대로 움직였다** — 빼면 http 로 돌아간다. 그래서 그 헤더가 원인이라고 말할 수 있다.
🔴 IdP 쪽 전제는 `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` 이고, 그것이 iam-oidc 라우터와
**한 쌍**임을 `infra/demo/verify-demo-wrapper.sh` (l) 이 지킨다. **이 앱의 정확성은 그 가드가
지키는 조건 위에 서 있다** — 그 env 가 빠지면 여기 아무 변화가 없어도 브라우저가 평문으로 튄다.

---

## 🔴 로컬 하네스가 잡은 결함 둘 — 둘 다 «조용히 깨지는» 종류였다

착수 중 스텁 IdP + 스텁 컨트롤 플레인으로 **종단 간** 실측했다(`next start`, 12칸).

### (a) `X-Forwarded-Host` 를 `new URL(req.url).host` 로 만들면 **틀린다**

실측: `Host: 127.0.0.1:3003` 으로 불렀는데 `new URL(req.url).host` 는 **`localhost:3003`**
이었다. 그것은 들어온 `Host` 헤더가 아니라 **런타임이 만든 요청 URL 의 host** 다.
🔴 Vercel 에서는 커스텀 도메인이 아니라 **배포 URL** 이 될 수 있고, 그러면 IdP 가
`https://<deployment>.vercel.app/login` 을 광고한다 — `auth.hubwang.com` 을 만들려던
이 프로젝트의 **존재 이유가 무너진다**. ⇒ `req.headers.get('host')` 를 직접 읽는다.

### (b) 응답의 `content-encoding` 을 그대로 넘기면 **본문이 깨진다**

실측 (undici):

```
upstream : content-encoding: gzip · content-length: 35 · body = gzip 바이트
fetch()  : content-encoding: gzip **그대로** · content-length: 35 **그대로**
           · body = "HELLO-GZIP-BODY"  ← **이미 해제됨**
```

undici 는 자기가 붙인 `Accept-Encoding` 때문에 본문을 **자동 해제하면서 헤더는 남긴다.**
그대로 통과시키면 브라우저가 평문을 gzip 으로 읽으려다 실패하고, 증상은 «프록시가 죽었다»
가 아니라 **«어떤 페이지만 깨진다»** 라 원인을 못 찾는다. ⇒ `content-encoding` 과
`content-length` 를 **버린다**.

🔵 그리고 하네스 자신도 한 번 거짓말했다 — 앞 실행의 `next start` 가 포트를 물고 있어
**낡은 빌드를 재고 있었다**(코드를 고쳐도 결과가 안 바뀌었다). 지금은 시작 전에 포트가
비어 있음을 **단언**하고, 끝나면 포트를 물고 있는 PID 를 직접 죽인다.

---

## 다른 앱과 다른 점

| | 이 앱 | 형제 셋 |
|---|---|---|
| 화면 | **없다.** 라우트 핸들러 하나(`[[...path]]/route.ts`) | 있다 |
| pnpm 워크스페이스 | **어디에도 속하지 않는다** — 자기 `pnpm-lock.yaml` 을 가진다 | ecommerce / fan 워크스페이스 |
| `output: 'standalone'` | 안 쓴다(컨테이너로 굽지 않는다) | web-store 는 쓴다 |
| lint | **eslint 설정이 없다** — 화면이 없어 규칙 대부분이 무의미하다 | `next lint` |

## CI

`Frontend lint & build` 잡이 이 앱을 **따로 설치하고** `typecheck` + `build` 를 돌린다.
🔴 그 스텝이 없으면 이 앱의 유일한 빌드 게이트가 **머지 뒤에 도는 Vercel** 이 된다 —
그때 깨지면 `auth.hubwang.com` 이 죽은 채로 발견된다.
`ci.yml` 의 `auth-forwarder` paths-filter 가 그 스텝의 도달 경로다(기존 `ecommerce`/`fan`
필터는 이 앱을 **하나도 안 덮는다**).

---

## 🙋 소유자가 해야 하는 것 (저장소가 못 한다)

| # | 무엇 | 왜 소유자인가 |
|---|---|---|
| 1 | Vercel 프로젝트 **`kanggle-auth`** 생성, Root Directory = `infra/demo/auth-forwarder` | 대시보드 전용 |
| 2 | 도메인 **`auth.hubwang.com`** 부착 | 대시보드 전용. 🔵 `hubwang.com` 은 등록·NS 가 **둘 다 Vercel** 이라 DNS 쓰기·인증서가 **불필요**하다 |
| 3 | 프로젝트 env **`DEMO_API_BASE`** = 컨트롤 플레인 베이스 | 없으면 이 앱은 «데모가 아님» 화면만 낸다 |
| 4 | Deploy Hook 생성 → 저장소 secret **`VERCEL_DEPLOY_HOOK_AUTH`** | 🔴 그때까지 `vercel-deploy.yml` 의 `kanggle-auth` 잡은 *"secret 이 비어 있습니다"* 로 **빨갛다**. 그것이 정확한 신호다 — **앱은 있는데 배포할 곳이 없다** |
| 5 | `infra/demo/demo.env` 의 `IAM_PUBLIC_URL` 을 `https://auth.hubwang.com` 으로 뒤집기 | 🔴 **1~3 이 끝난 뒤에** 해야 한다. 먼저 뒤집으면 **데모 로그인이 통째로 죽는다** (`TASK-MONO-610 AC-1` 이 그 뒤집기를 **한 줄**로 만들어 두었다) |

🔴 **그리고 그 뒤에도 «로그인이 된다» 는 증명되지 않았다.** `ADR-MONO-069` § Consequences:
*"어느 안도 «지금 당장 로그인이 된다» 를 보장하지 않는다."* 판정은 `TASK-MONO-610` 의
**V1–V8**(기동 창)이 한다.

### ✅ 1~4 실측 원장 (2026-09-02 UTC)

소유자가 1~4 를 실행했고, **선언이 아니라 조회로** 확인한 것만 적는다.

| # | 실측 | 출처 |
|---|---|---|
| 1 | 프로젝트 `kanggle-auth` **존재** | `vercel env ls --project kanggle-auth` 가 응답 |
| 2 | `auth.hubwang.com` 이 **이 계정으로 붙었다** | 그 호스트가 Vercel `icn1` 에서 응답(당시 배포가 없어 `404 DEPLOYMENT_NOT_FOUND`) |
| 3 | `DEMO_API_BASE` = **`Production, Preview`** | `vercel env ls` 두 환경 모두에서 조회됨. 🔵 값이 정본과 **바이트 동일**함을 두 출처로 대조했다 — `terraform output api_base_url` ↔ `kanggle-store` 의 실배선 |
| 4 | secret `VERCEL_DEPLOY_HOOK_AUTH` **등록** (`2026-09-02T10:19:56Z`) | `gh secret list` — 🔵 **이름과 시각만** 본다. 그 URL 자체가 인증이므로 값은 조회하지 않는다 |

🔵 **`DEMO_API_BASE` 는 Preview 에도 넣어야 한다.** `vercel.json` 이 `preview/*` 브랜치는
배포하도록 두므로, Production 에만 넣으면 프리뷰가 **«데모 아님» 모드로 조용히 돈다**
(배포는 초록이다). 처음 등록은 Production 만이었고 이 원장을 쓰다가 잡았다.

### 🔴🔴 그런데 **첫 배포는 만들어지지 않았다** — 그리고 그것이 옳았다

프로젝트 Import 가 만든 배포는 **7초 만에 `Canceled`** 였다. install 에 닿지도 않았다.

```
Age  Project       Deployment                    Status     Environment  Duration
6m   kanggle-auth  …-g1oodqfde-…vercel.app       Canceled   Production   7s
```

기전: Import 는 그때의 `main`(`e5bc99e` = 무관한 클로즈 chore)을 클론했고,
`vercel-ignore.sh` 가 *"이 커밋은 위 `SPECS` 를 안 건드렸다"* 로 **건너뜀**을 냈다.
**판정자는 정확히 설계대로 동작했다.**

🔴 **그래서 새 프로젝트에는 「첫 배포가 영원히 안 생기는」 구간이 있다.** 대시보드
Redeploy 도 **같은 커밋**을 다시 재므로 또 `Canceled` 다. 빠져나오는 길은 하나뿐이다 —
**`SPECS` 안의 경로를 건드리는 커밋**. (이 절이 실린 커밋이 그것이다: 이 파일이
`:/infra/demo/auth-forwarder` 아래에 있다.)

🔵 **이것을 판정자의 결함으로 고치지 마라.** 목록을 넓히면 무관한 커밋마다 배포가
나고, 그 비용은 계정 전체가 공유하는 한도에서 나간다(`ADR-MONO-067`). 대가가 큰 쪽은
**«한 번의 수동 트리거»** 가 아니라 **«매 커밋 배포»** 다.

### ⏳ 아직 판정되지 않은 것 — **Root Directory 밖 포함**

이 앱은 `"@demo/backend-resolver": "link:../backend-resolver"` 로 **Root Directory 밖**을
가리킨다. Vercel 프로젝트 설정의 *"Include files outside of the Root Directory in the
Build Step"* 이 꺼져 있으면 install 이 죽는다.

🔴 **그 설정은 대시보드에서만 읽을 수 있고, 지금까지 어떤 빌드도 install 에 닿지 않았다**
⇒ **미측정**이다. 형제(`kanggle-fan`·`kanggle-store`)가 켜져 있다는 사실을 이리로
**이전하지 마라** — `TASK-MONO-590` 이 같은 종류의 이전을 두 번 금지했다.
판정은 **이 커밋이 만드는 첫 빌드의 install 단계**가 한다:

| 결과 | 뜻 |
|---|---|
| `Packages: +N` 뒤 빌드 진행 | ✅ 켜져 있다 |
| `ERR_PNPM_LINKED_PKG_DIR_NOT_FOUND` / `ENOENT … backend-resolver` | 🔴 꺼져 있다 ⇒ 대시보드에서 켜고 재배포 |

