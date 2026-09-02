# Vercel 배선 — web-store (`TASK-MONO-582`)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격하다** — 이 저장소는 그 문으로 두 번 죽었다(§ 아래).

| | |
|---|---|
| Vercel 프로젝트 | ✅ **`kanggle-store`** — 소유자가 **2026-08-29** 에 생성 |
| Root Directory | `projects/ecommerce-microservices-platform/apps/web-store` |
| 프레임워크 | Next.js 15 App Router — **감지는 대시보드에 맡긴다** |
| `vercel.json` 이 선언하는 것 | `installCommand` + `ignoreCommand` 둘뿐 |
| 무시 규칙의 경로 목록 | **[`vercel-ignore.sh`](./vercel-ignore.sh)** — JSON 안이 아니다 |
| 공개 호스트명 | `store.hubwang.com` — ✅ **연결됨** (`200`, 08-29 소유자 작업 후 실측). 정본 표는 [`TEMPLATE.md § 공개 호스트명 배분`](../../../../../TEMPLATE.md) |

## ✅ 생성 확인 (2026-08-29) — **배선이 살아 있다**

프로젝트가 생기자마자 **커밋 상태 행이 셋이 됐다**(`61fe38721`):

```
Vercel – kanggle-portfolio | success | Canceled by Ignored Build Step
Vercel – kanggle-fan       | success | Canceled by Ignored Build Step
Vercel – kanggle-store     | success | Canceled by Ignored Build Step   ← 새 프로젝트
```

🔵 **첫 배포가 「취소」인 것은 정상이고, 오히려 배선이 옳다는 증거다** — 그 커밋은 `tasks/` 만
건드렸으므로 `vercel-ignore.sh` 의 pathspec 에 안 걸린다. 🔴 **이것을 「실패」로 읽지 마라.**

🔴 **판정 술어가 바뀐다** — 이제 *"`vercel.json` 이 셋인가"* 가 아니라 **"커밋 상태 행에
`Vercel – ` 컨텍스트가 셋인가"** 로 본다. 파일은 08-29 이전에도 셋이었지만 프로젝트가 없으면
Vercel 은 그 파일을 **읽지 않았다**. [[feedback_declaration_files_are_not_the_runtime_state]]

🔵 **로그를 오독하지 않도록**: Vercel 은 배포를 **만들고 clone 까지 한 뒤** `ignoreCommand` 를
돌린다. 그래서 *"Cloning completed"* 가 찍힌 뒤에 취소되는 것이 정상이다 — clone 로그를 보고
*"빌드가 돌고 있다"* 로 읽으면 틀린다(`TASK-MONO-590` 이 잰 그 순서다).

---

## ~~🔴🔴 먼저 — **`TASK-MONO-575` 의 판정 없이 프로젝트를 만들지 마라**~~ → ✅ **해제됨 (2026-08-29, 아래 § 게이트 해제)**

> 🔴 **이 절은 기록으로 남긴다.** 08-26~08-29 사이에 이 게이트가 **실제로 클릭을 막고 있었고**,
> 무엇이 그것을 막았는지가 587·590 이 나온 경로다. 지우면 그 인과가 사라진다.
> **지금 유효한 지시는 아래 § 게이트 해제**이고, 이 절의 「만들지 마라」는 **더 이상 유효하지 않다.**

이 파일의 초판은 *"대시보드에서 새 프로젝트를 만들라"* 로 시작했다. **그 문장에 게이트가
빠져 있었다.** 저장소에는 이미 그 질문의 티켓이 있고, 그 티켓의 잠정 방향은 **「못 견딘다」** 다.

`TASK-MONO-575`(`ADR-MONO-067` AC-0 ④, **in-progress**)의 AC-1 실측 — 프로젝트가 **2개**일 때:

| | |
|---|---|
| 2026-08-22 배포 이벤트 | **72건/일** (🔴 **하한** — 닫힌 PR·force-push·재실행을 못 셈) |
| 그날 실제로 일어난 일 | **rate limit 에 걸렸다** (08-22 · 08-23 두 번) |
| 프로젝트 4개 환산 | 하루 **92~144** |
| AC-2 판정 | ⏸️ **판정 불가**(AC-0 이 소유자 대시보드 대기) — 단 **방향은 「못 견딘다」** |

그리고 575 의 AC-0 은 숫자보다 이 질문이 판정을 뒤집는다고 적는다:

> **`Canceled by Ignored Build Step` 이 일일 배포 한도를 소비하는가?**

건너뛴 커밋도 **`pending` 을 거친다**(Vercel 이 배포를 만들었다가 취소). 즉 `ignoreCommand` 가
확실히 아끼는 것은 **빌드 시간**이고, **배포 건수**까지 아끼는지는 **아직 아무도 모른다.**
먹는다면 무시 규칙을 아무리 촘촘히 해도 프로젝트를 늘리는 순간 이벤트는 **그대로 배수**다.

### 그래서 지금 이 저장소의 상태

🔵 **여기 있는 `vercel.json` 은 아직 불활성이다.** Vercel 은 대시보드에 없는 프로젝트를 굽지
않는다 — 실측: 이 배선을 머지한 PR(#3467)의 Vercel 프로젝트 체크는 여전히
`kanggle-portfolio` · `kanggle-fan` **둘뿐**이었다. **배포 압력은 아직 안 늘었다.**

🔴 **늘어나는 순간은 「대시보드에서 프로젝트를 만드는 순간」이다.** 그 한 번의 클릭이
575 가 아직 판정하지 못한 축을 되돌리기 어렵게 건드린다. ⇒ **575 의 AC-2 가 「견딘다」로
닫힌 뒤에** 아래 절차를 밟아라. 「못 견딘다」면 575 가 나열한 선택지
(① 유료 플랜 ② 프로젝트 수를 안 늘리는 다른 배치 ③ `TASK-MONO-572` 선행)가 먼저다.

---

## ✅ 게이트 해제 — **2026-08-29. 이제 절차를 밟아도 된다**

🔴🔴 **위 문단을 그대로 따르면 이 게이트는 영원히 안 열린다** — 「575 의 AC-2 가 **「견딘다」로
닫힌 뒤에**」라고 적혀 있는데, 575 는 **「못 견딘다」로 닫혔다**(2026-08-27, `done/`). 조건이
문자 그대로는 **결코 성립하지 않는 형태**였다. 게이트가 실제로 물어야 했던 것은
*"AC-2 가 「견딘다」인가"* 가 아니라 **"575 가 나열한 선택지 중 하나가 실행됐는가"** 다.

**그 선택지 ①(유료 플랜)이 실행됐다.** 소유자가 **2026-08-29 에 Vercel 플랜을 Pro 로 올렸다.**

| | |
|---|---|
| 계측기 | 커밋에 붙는 `Vercel – *` **commit-status 행** (🔴 과금 축인 `Deployments Created` 카운터가 **아니다**) |
| 한도 체제 `08:04:47Z–08:57:53Z` | 10행 중 **6행** `Deployment rate limited` |
| Pro 전환 후 `09:26:29Z–09:29:53Z` | 프로브 4푸시 = **8행 전부 통과**, 그중 **6건이 11초 안에** |
| 시간축 배제 | 24h 재시도 시계는 `08-30T08:04Z`, UTC 일일 리셋은 이미 지남 ⇒ 두 시점 사이의 **유일한 국면 변화가 Pro 전환** |

근거 SHA 는 `TASK-MONO-590` § 축소. ⇒ **아래 절차를 밟아도 된다.**

🔴 **다만 이 문장을 「한도가 6,000 이다」로 읽지 마라.** 잰 것은 *"100 이 더 이상 안 걸린다"*
이고, Pro 의 `6,000/일` 은 **문서 값**이다 — 575 AC-0 이 *"문서 값을 적지 마라, 계정에 적용된
값이 진실이다"* 를 규칙으로 세웠고 그 출처는 **소유자 대시보드**뿐이다.

🔴 **이미지 축(`TASK-MONO-587`)은 별개였고, 그쪽도 함께 풀렸다 — 그러나 다른 방식으로.**
`web-store` 는 `next/image` 를 5곳에서 쓰고 계정의 이미지 변환은 **112% / 320% 초과**였다.
Pro 에서 그 초과는 **차단(402)이 아니라 과금**이 된다. 🔴🔴 **초과분 자체는 하나도 안 줄었다** —
5.6K/5K · 320K/100K 그대로다. 첫 배포 뒤 사용량을 한 번 확인하라(587 AC-0).

🔵 **그리고 이 프로젝트가 생기는 순간 새로 재야 할 것이 하나 있다**: 같은 디렉터리의
`vercel.json` 에 `{"**": false, "main": true, "preview/*": true}` 가 이미 들어 있지만
(`TASK-MONO-590` AC-6), **프로젝트가 없어 Vercel 이 그 파일을 읽은 적이 없다** ⇒ 그 키는
**측정된 적이 없다.** 형제(`kanggle-fan`·`kanggle-portfolio`)의 결과를 이리로 **이전하지 마라** —
590 이 두 번 못박은 금지다. 첫 푸시에서 브랜치 배포가 실제로 안 생기는지 직접 확인하라.

---

## 🔴 소유자 절차 — 저장소가 못 하는 일 (✅ 위 게이트 2026-08-29 해제됨)

1. Vercel 대시보드에서 **새 프로젝트**를 만들고 이 저장소를 연결한다.
2. **Root Directory** 를 `projects/ecommerce-microservices-platform/apps/web-store` 로 지정한다.
3. **환경변수 `DEMO_API_BASE`** 를 넣는다:

   ```
   cd infra/demo/aws/terraform && terraform output api_base_url
   ```

   🔴 이 값은 **손으로 하는 배선**이다. 예전에는 terraform 이 S3 사본의 `config.js` 를 자기
   상태에서 렌더했으므로 *"배포된 페이지가 자기 API 와 어긋나는 것"* 이 표현 불가능했는데,
   `TASK-MONO-579` 가 그 사본을 폐기하면서 그 성질이 사라졌다. **API 를 재생성하면 사람이
   두 자리를 맞춰야 한다.** *없는 것* 은 앱이 잡지만(해석기가 아예 호출하지 않는다),
   ***낡은 것*** 은 아직 아무도 안 잡는다.

4. 첫 배포 로그를 확인하고 **§ 첫 배포에서 깨질 수 있는 지점** 을 이 문서에 회신한다.

🔵 `DEMO_API_BASE` 가 **없으면** 앱은 조용히 정상 동작한다 — 데모가 아닌 환경이라고 보고
기존 env 사슬(`API_URL_INTERNAL` → `NEXT_PUBLIC_API_URL` → `localhost:8080`)로 간다.
그래서 **환경변수를 빠뜨린 배포는 초록으로 뜨고 데이터만 안 나온다.** 3번을 건너뛰지 마라.

---

## ✅ 첫 배포 판정 (2026-08-29) — **둘 다 안 깨졌다**

아래 두 지점은 **첫 실빌드에서 판정됐다**(스쿼시 `9c83e4538`):

| 지점 | 판정 | 근거 |
|---|---|---|
| `output: 'standalone'` | ✅ **안 깨졌다** | `Vercel – kanggle-store \| success \| Deployment has completed`. 게다가 배포본이 **SSR 로 서빙된다** — 아래 ③ |
| `NEXTAUTH_SECRET` | ✅ **빌드를 안 죽였다** | 같은 빌드가 통과. 🔵 **미리 좁혀 뒀던 대로다** — 세 OIDC 상수가 전부 `??` 폴백을 갖고(`auth-callbacks.ts:34-38`) 그 파일에 `throw` 가 **0개**다 |

**라이브 판정 (같은 날, `store.hubwang.com` 연결 후):**

| 찌른 곳 | 응답 |
|---|---|
| `https://store.hubwang.com/` | **200** (34,963 B) |
| `https://store.hubwang.com/products` | **200** (40,618 B) |
| 🔵 대조군 `console.hubwang.com` (같은 순간) | **404** ⇒ 와일드카드가 아니라 **이 프로젝트에 실제로 물렸다** |

### ③ 🔵 `DEMO_API_BASE` 가 실제로 배선됐다 — **술어를 코드에서 뽑아 확인했다**

서빙 HTML 에 **`data-testid="demo-backend-notice"`** 가 있다.
🔴 **이것이 판별자인 이유**: 그 위젯은 `DEMO_API_BASE` 가 **있을 때만** 렌더된다 — 없으면 앱이
«데모 아닌 환경» 으로 보고 **아무것도 그리지 않는다**(`(store)/layout.tsx:12-14`).
⇒ 리졸버가 컨트롤 플레인을 실제로 불러 `stopped` 를 받고 올바르게 표시한 것이고,
그 렌더는 **서버에서** 일어난다 ⇒ `output: 'standalone'` 이 런타임에도 문제를 안 만든다.

🔴 **안 잰 것**: 빌드 로그의 **경고 문구**는 확보하지 못했다(성공/실패만 봤다).
`standalone` 이 경고를 냈는지는 **모른다** — 여기서 주장하는 것은 *"빌드를 죽이지 않았고
서빙도 정상"* 이지 *"경고가 없었다"* 가 아니다.
[[feedback_a_reported_figure_must_name_what_was_measured]]

---

## 🔴 (원문 보존) 첫 배포에서 깨질 수 있는 지점 — **저장소에서 판정 불가**

아래 둘은 로컬에서 재현할 수 없다. *"아마 괜찮다"* 라고 적지 않는다 — **판정 출처는 소유자의
첫 배포 로그 하나뿐**이고, 그 로그를 본 뒤 이 절을 사실로 갱신한다.
🔵 **이 문단을 지우지 않는다** — 무엇을 왜 미리 지목했는지가 위 판정의 의미를 만든다.

### 1. `output: 'standalone'`

`next.config.ts` 에 있고 **Docker 용**이다(`Dockerfile` 이 그 산출물을 쓴다). Vercel 은 자기
빌드 출력을 쓰므로 충돌하면 **첫 배포에서** 드러난다.

🔵 **지우지 마라.** 그 값은 데모 호스트의 컨테이너 빌드가 쓰고 있고, 지우면 이쪽이 아니라
**저쪽이** 죽는다. 충돌이 실제로 확인되면 환경 분기로 가른다.

### 2. `next-auth` v5 의 `NEXTAUTH_SECRET`

`src/shared/auth/auth.ts:69` 가 모듈 최상위에서 `NextAuth({ secret: process.env.NEXTAUTH_SECRET })`
를 부른다. 빌드 중 정적 생성이 그 경로를 타면 **값 부재가 빌드를 죽일 수 있다.**

🔴 OIDC 축 전체(`OIDC_ISSUER_URL` · 클라이언트 시크릿 · 콜백 URL)는 **`ADR-MONO-067` 이 D4
로 떼어 낸 별도 결정**이고 `TASK-MONO-576` 이 그 ADR 을 맡는다. **여기서 즉흥으로 정하지
마라** — HTTPS 프런트 ↔ 평문 IdP 는 이 저장소가 두 방향 모두 데인 축이다.
빌드만 통과시키면 되는 경우라도, 넣은 값과 그 이유를 여기에 적는다.

#### 🔴🔴 그 지시가 지켜지지 않았고, 이 절이 그 원장이다 (`TASK-MONO-611`, 2026-09-01)

**2026-08-29, `kanggle-store` 프로덕션에 `OIDC_ISSUER_URL` 이 들어갔다. 이 문서엔 한 줄도
남지 않았다.** 바로 위 문단이 *"넣은 값과 그 이유를 여기에 적는다"* 라고 지시한 그 변수다.
`TASK-MONO-611` 이 `ADR-MONO-069` § R1 을 조사하다가 **형제 프로젝트의 env 목록에서**
발견했다 — 저장소 안에서는 발견할 방법이 없었다.

| | |
|---|---|
| 값 | **`http://iam.3-38-176-240.sslip.io`** |
| 생성 | 2026-08-29 (≈ ADR-069 가 다루는 장애와 같은 날) |
| 2026-09-01 실측 | **`code=000` — 연결 타임아웃, 죽었다** (양성 대조군 `https://store.hubwang.com/` = `200 / 34,940 bytes`; 음성 대조군 `http://neverssl.com/` = `200 / 3,961 bytes` ⇒ 평문 http 를 먹는 캡티브 포털이 아니다) |
| 왜 죽었나 | **평문 `http://`** 이고 **부팅마다 바뀌는 EC2 IP 가 호스트명에 박혔다.** 3일 만에 시체가 됐다 |
| 처분 | 🔵 **삭제했다** (2026-09-01, 소유자 지정. `vercel env rm OIDC_ISSUER_URL production` — 삭제 전 2개 → 후 1개 확인) |

🔴 **왜 «지운다» 가 «둔다» 보다 나은가 — 이것이 취향 문제가 아닌 이유.**
`web-store/src/shared/auth/auth-callbacks.ts:34` 는 `process.env.OIDC_ISSUER_URL ??
'http://iam.local'` 이다. 형제 `fan-platform-web` 은 같은 날 이 모양에 **«Vercel 인데 값이
없으면 변수 이름을 부르며 죽는다»** 는 가드를 얻었다(`TASK-MONO-611` AC-1). 그 가드의
술어는 **«값이 있나»** 다 ⇒ **시체는 그 검사를 통과한다.** 부재는 탐지되고 시체는 안 된다.
죽은 값을 남기는 것은 «자리 표시» 가 아니라 **가드를 무력화하는 값**이다.
[[feedback_a_fallback_is_not_a_placeholder]]

🔵 **오늘 동작 차이는 0이다** — 지운 값도 폴백 `iam.local` 도 Vercel 함수에서는 똑같이
해소되지 않는다. 그리고 env 변경은 **다음 배포부터** 적용되므로 현재 살아 있는 배포는
건드리지 않았다. 되돌리려면 위 표의 값을 그대로 다시 넣으면 된다.

🔴 **이것으로 web-store 의 OIDC 축이 «해결» 된 것은 아니다.** 넣을 수 있는 값이 아직
없다는 사실이 그대로다 — 그 이름을 만드는 것이 `TASK-MONO-610`(`auth.hubwang.com`)이고,
`ADR-MONO-067` § D4 / `ADR-MONO-069` 가 그 결정을 들고 있다. **여기서 즉흥으로 IP 를 다시
박지 마라.** 같은 실험이 이미 한 번 실행됐고 3일 만에 실패했다.

---

## 🔴 결제 mock — 이 프로젝트 env 가 **불변식의 절반**을 들고 있다 (`TASK-MONO-612`)

데모 결제는 **두 곳이 동의**해야 성립한다. mock PG 는 아무 `paymentKey` 나 받아주지만 실
Toss 어댑터는 거부하므로, 한쪽만 켜면 체크아웃이 죽는다.

| 절반 | 어디 | 기대값 | 2026-09-02 UTC 실측 |
|---|---|---|---|
| 백엔드 | `payment-service` `SPRING_PROFILES_ACTIVE` | `demo-pg` | ✅ `infra/demo/demo.env:276` |
| 프런트 | 🔴 **이 프로젝트의 env** `DEMO_PAYMENT_MOCK` | `1` | 🔴 **없다** — 프로덕션 키 **전수 1개**(`DEMO_API_BASE`) |

⇒ **백엔드만 켜진 조합이다.** 🔵 두 번째 선언 경로도 없음을 확인했다(같은 앱의
`vercel.json` · `next.config.*` · `package.json` 에 `DEMO_PAYMENT_MOCK` **0건**).

**왜 이 문서가 원장인가**: `infra/demo/verify-demo-wrapper.sh` 의 가드 (x) 가 이 쌍을
강제하는데, `TASK-MONO-604` 가 데모에서 `web-store` 를 억제한 뒤 **프런트 절반이 compose
렌더에서 사라졌다**. 그 절반은 없어진 게 아니라 **여기로 이사 왔다**. `TASK-MONO-612` AC-2
가 「저장소는 이 축을 판정할 수 없다」를 **명시적으로 수용**했고(선택지 2), 그래서 판정은
사람이 한다:

- **누가·언제** — 소유자가, 데모 기동 창마다 그리고 이 프로젝트 env 를 만질 때마다
- **명령** — `vercel env ls production --project kanggle-store | grep DEMO_PAYMENT_MOCK`
- 🔴 **최종 판정은 env 목록이 아니다** — `/api/store-config` 가 `{"demoPayment":true}` 를
  내야 한다. env 변경은 **다음 배포부터** 적용된다.

🙋 **방향 지정 대기**: 권장은 「프런트를 백엔드에 맞춘다」(여기에 `DEMO_PAYMENT_MOCK=1`).
데모는 돈을 받지 않는다. 🔴 그러나 이 문서가 요구하는 대로 **넣은 값과 그 이유를 위 표에
적고** 나서 넣어라.

---

## 🔴🔴 이 프로젝트의 auth env 는 **통째로 비어 있다** — `/api/auth/*` 가 500 이다

**2026-09-02 UTC 실측.** `TASK-MONO-612` 의 AC-0 이 「런타임 값을 읽으려고」 하다가 잡았다.

| 찌른 곳 | 🔵 형제 `kanggle-fan` (양성 대조군) | 🔴 `kanggle-store` |
|---|---|---|
| `/api/auth/providers` | **200** · 167 B · `iam` 를 나열 | **500** · 108 B |
| `/api/auth/csrf` | **200** · 80 B · 토큰 발급 | **500** · 108 B |
| `/` (음성 대조군 — 앱 자체는 사나) | 307 → `/login` | **200** · 34,963 B |
| 프로젝트 auth env | `NEXTAUTH_SECRET` · `NEXTAUTH_URL` · `OIDC_CLIENT_ID` · `OIDC_CLIENT_SECRET` (**4개**) | **0개** |

🔴 **이 문서의 위 절이 「✅ `NEXTAUTH_SECRET` 이 빌드를 안 죽였다」라고 적은 것은 거짓이
아니다** — 잰 것이 **빌드**였다. 런타임은 그 문장의 사정거리 밖이었고, 그래서 아무도
안 봤다. [[feedback_a_reported_figure_must_name_what_was_measured]]

🔴 **단일 변수 귀속은 하지 않는다**: 두 팔 사이에 변수가 네 개 다르다. 말할 수 있는 것은
*"auth env 가 통째로 없고 `/api/auth/*` 가 전부 500 이다"* 까지다.

⇒ **`OIDC_ISSUER_URL` 만 꽂아서는 이 사이트의 로그인이 살아나지 않는다.** 그 사실을
`TASK-MONO-610` AC-4b 에 넣었다. 🔴 **여기서 즉흥으로 값을 넣지 마라** — 무엇을 왜 넣었는지
이 표에 적는 것이 이 문서의 규칙이고, 그 규칙이 지켜지지 않아 바로 위 절이 생겼다.

### ✅ 그 규칙대로 적는다 — 소유자가 넣은 값 (2026-09-02 UTC)

| Key | 값 | 환경 | 왜 |
|---|---|---|---|
| `NEXTAUTH_SECRET` | 🙋 소유자 생성 (`openssl rand -base64 32`) | Production | `auth.ts:69` 가 모듈 최상위에서 `NextAuth({secret})` 를 부른다. **부재가 `/api/auth/*` 전부를 500 으로 만들고 있었다** |
| `NEXTAUTH_URL` | `https://store.hubwang.com` | Production·Preview | `federated-logout.ts:30` 의 기본값이 `http://localhost:3001` 이다 |
| `ECOMMERCE_WEB_STORE_CLIENT_SECRET` | `ecommerce-dev` | Production·Preview | `V0012` 시드의 dev 전용 평문. 🔴 이름이 **`OIDC_CLIENT_SECRET` 이 아니다** — 그건 팬의 규약이다(`auth-callbacks.ts:37`) |
| `DEMO_PAYMENT_MOCK` | `1` | Production·Preview | `TASK-MONO-612` AC-1. 데모 백엔드가 `demo-pg` 이므로 프런트도 mock 이어야 한다 |

🔴 **아직 안 넣은 것: `OIDC_ISSUER_URL`.** `infra/demo/demo.env` 의 `IAM_PUBLIC_URL` 이
뒤집힌 **뒤**다. 먼저 넣으면 issuer 불일치로 거절되고, 지금(500)보다 나아지는 게 없다.

🔵 `ECOMMERCE_WEB_STORE_CLIENT_ID` 는 **안 넣었다** — 코드 기본값이 이미
`ecommerce-web-store-client` 다(`auth-callbacks.ts:35`). 넣어도 무해하지만 넣을 이유가 없다.

🔵 `NEXTAUTH_SECRET` 만 **Production 전용**이다(형제 `kanggle-fan` 과 같은 모양). 프리뷰
배포의 `/api/auth/*` 는 계속 500 이다 — 알고 두는 것이지 누락이 아니다.

🔴 **env 는 다음 배포부터 적용된다.** 넣은 직후 실측: `/api/auth/providers` **여전히 500**
(그 시점 라이브는 env 이전 배포다). 이 문단이 실린 커밋이 `web-store` 경로를 건드리므로
판정자가 **빌드**로 판정하고, 그 배포가 첫 반영본이다.

---

## 무시 규칙 — 왜 목록이 JSON 밖에 있는가

**Vercel 스키마는 명령 문자열에 `maxLength: 256` 을 건다.** `TASK-MONO-562` 가 형제
프로젝트(`kanggle-fan`)의 `ignoreCommand` 에 pathspec 5개를 직접 넣어 **261자**(+5)가 됐고,
그래서 `vercel.json` 이 거부되어 **모든 배포가 0초에 죽었다** — 빌드 로그조차 남지 않았다.

| 파일 | `ignoreCommand` 길이 | 한도 256 |
|---|---:|---|
| `kanggle-fan` (562 판 — 죽은 쪽) | **261** | ❌ |
| `kanggle-fan` (563 이 고친 판) | 99 | ✅ |
| **이 파일** | **113** | ✅ |

목록은 [`vercel-ignore.sh`](./vercel-ignore.sh) 에 있고 거기엔 길이 제한이 없다. 제한 자체는
`scripts/check-vercel-build-triggers.sh` 의 **칸 (5)** 가 지킨다 — 그 칸은 원문이 아니라
**디코드된 값**의 길이를 잰다(`\"` 는 원문 2자 · 값 1자라 grep 으로 세면 틀린다).

---

## 이 프로젝트를 만들면 Vercel 프로젝트가 **셋**이 된다

`scripts/check-vercel-build-triggers.sh` 의 하한(`FLOOR`)이 **3** 으로 올라간다. 그 가드는
모집단을 `git ls-files '*vercel.json'` 로 **발견**하므로, 이 파일을 놓는 것만으로 새 무시
규칙을 **실제로 실행해서** 대조한다(문자열 grep 이 아니다).

### 🔴🔴 배포 압력 — 잰 축과 **안 잰 축**

최근 30일(`origin/main` 커밋 **479개**) 중 각 프로젝트의 트리거 경로를 건드린 커밋:

| 프로젝트 | 빌드 트리거 커밋 | 비율 |
|---|---:|---:|
| `kanggle-portfolio` (론처) | 10 | 2.1% |
| `kanggle-fan` | 14 | 2.9% |
| **web-store** | **10** | **2.1%** |

**이 표는 「빌드」를 세었다.** Vercel 은 푸시마다 **배포를 먼저 만들고** `ignoreCommand` 는
그 뒤에 *빌드할지*를 정한다 — `Canceled by Ignored Build Step` 은 **배포가 생긴 뒤의 상태**다.
즉 세 번째 프로젝트는 **푸시마다 배포 1건을 무조건 더 만든다.**

🔴 **그리고 푸시 수는 저장소에서 잴 수 없다** — `git log` 는 커밋을 세지 푸시를 세지 않고,
PR 한 건의 5커밋 푸시는 배포 1건이다. **그러니 위 표를 "한도에 여유가 있다"의 근거로 쓰지 마라.**

🔴🔴 **그리고 이 표는 새 발견이 아니다.** 이 파일의 초판은 `ADR-MONO-067` AC-0 ④ 를
*"아직 미측정"* 이라 적었는데, **틀렸다** — `TASK-MONO-575` 가 그 축의 티켓이고 **AC-1 은
2026-08-23 에 이미 끝났다**(08-22 = **72건/일**, 하한). 위 표는 그것을 **다시 센 것**이고,
같은 결론(빌드 축 ≠ 과금 축)에 **독립적으로** 도달했을 뿐이다.

⇒ **판정의 출처는 `TASK-MONO-575` 의 AC-0·AC-2** 이고, 그것은 소유자 대시보드 대기다.
여기서 다시 묻지 마라 — **같은 질문을 두 티켓이 각각 들고 있으면 한쪽만 답을 받는다.**
↑ 이 파일 맨 위 § 게이트를 보라.
