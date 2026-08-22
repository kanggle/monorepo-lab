# Task ID

TASK-MONO-557

# Title

론처 페이지 주소가 **재생성에서 살아남지 못한다** — 정적 페이지를 Vercel 로 옮기고 허용 오리진 배선을 그에 맞춘다

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo
- frontend

---

# 배경 — 2026-08-18(UTC) 비용 실측 중에 드러난 것

사용자 질문(*"기동페이지가 계속 떠 있는 건 비용 안 나가? URL 은 안 바뀌어?"*)에 답하려고
잰 값이 **이 티켓의 전제를 뒤집었다.**

## 🔵 비용은 이유가 아니다 — 재고 이미 무료다

2026-08-18 Cost Explorer 실측(월초~8/18):

| 서비스 | 8월 실제 |
|---|---|
| Amazon CloudFront | **$0.0000** |
| AWS Lambda | **$0.0000** |
| Amazon API Gateway | $0.0006 |
| Amazon S3 | $0.0001 |
| **론처 전체** | **$0.0007** |

⇒ **Vercel 로 옮겨서 아낄 돈은 없다.** 이 티켓을 비용 절감으로 정당화하지 말 것.
그렇게 적으면 다음 사람이 *"$0.0007 을 아끼려고 이걸 했나"* 로 읽고, 그건 정당한 반응이다.

## 🔴 진짜 이유 = 주소가 재생성에서 살아남지 못한다

론처 주소는 **인스턴스 재시작에는 안 바뀐다**(실측: `/stop`→`/start` 왕복 후에도
`d38c06ry1h6rnn.cloudfront.net` 과 `r1tljg51qa.execute-api…` 동일, 둘 다 200).
바뀌는 것은 **데모 서비스 주소** 쪽이고(EIP 없음 → sslip.io 호스트명이 매번 다름),
론처가 존재하는 이유가 정확히 그것을 흡수하는 것이다.

문제는 다른 축이다: **`terraform destroy` → 재-apply 를 하면 CloudFront 도메인과
API Gateway ID 가 새로 발급된다.** 둘 다 AWS 가 정하는 랜덤 문자열이라 같은 값을 다시
받을 수 없다. 그런데 비용 축에서 destroy 는 계속 후보로 남는다(EBS 100 GiB ≈ 월 $9 는
론처가 아니라 **인스턴스를 유지하는 값**이고, 그것만이 유의미한 지출이다).

⇒ 지금 구조는 **"돈을 아끼는 유일한 조작이 주소를 깨뜨린다"** 는 형태다. 이력서·포트폴리오·
README 에 주소를 적는 순간 그 조작은 못 하게 된다.

`*.vercel.app` 은 **프로젝트 이름에 묶여** 재배포·재생성에도 유지된다. 그게 이 티켓이
얻으려는 유일한 것이다.

---

## ✅ 2026-08-21 UTC — **AC-1·AC-3 을 실제 브라우저로 닫는다**

**AC-1 — 페이지가 Vercel 에서 뜬다. ✅** `https://kanggle-portfolio.vercel.app/` 가 200 이고
`config.js` 가 `window.DEMO_API_BASE` 를 실어 온다(빈 값이면 빌드가 죽는 대조군 4종은 이미 기록됨).

**AC-3 — 브라우저에서 실제 조작이 된다. ✅ 실제 Chromium 으로 측정했다.**
🔴 이 AC 는 `curl` 로 만족시킬 수 없다 — CORS 는 브라우저 정책이라 curl 이 우회한다.
그래서 Playwright 로 진짜 브라우저를 띄워 **론처 오리진에서** 실행했다.

| 칸 | 결과 |
|---|---|
| 페이지 로드 (론처 오리진) | JS 가 응답 **본문을 읽음**(98B JSON) · `#dlist` 자식 **8** · `[data-surface]` **3** · `#msg` = *"🟢 대기 중 · 431/600분 사용"* · 콘솔 에러 **0** |
| **실제 POST** (`/heartbeat`, `content-type: application/json` — 버튼과 같은 opts) | **200**, 본문 `{"ok":true}` 읽음 |
| 🔴 **대조군** — 같은 브라우저·같은 API·`https://example.com` 오리진 | **`TypeError: Failed to fetch`** (GET·POST 둘 다) |
| 서버 선언 (`OPTIONS /start`, 론처 오리진) | `allow-origin: https://kanggle-portfolio.vercel.app` · `allow-methods: GET,OPTIONS,POST` · `allow-headers: content-type` |
| 🔴 **대조군** — 같은 preflight, 낯선 오리진 | **204** 인데 CORS 헤더 **0개** |

🔴🔴 **마지막 줄이 이 AC 의 경고를 그대로 재현한다** — *"판정에 200 을 쓰지 말 것: 거부되는
오리진도 200 이다."* 낯선 오리진의 preflight 는 **에러가 아니라 204** 를 받았다. 막히는 것은
응답이 오는 것이 아니라 **읽는 것**이고, 그래서 판정 술어를 *"페이지가 본문을 읽어 DOM 을
채웠는가"* 로 잡았다.

🔵 **preflight 는 네트워크 이벤트에 안 잡혔다 — 그건 Chromium 이 preflight 를 노출하지 않기
때문이지 안 갔다는 뜻이 아니다.** `content-type: application/json` 인 교차 오리진 POST 는
preflight 통과 없이 **성립할 수 없다.** 성공 자체가 증거이고, 서버 선언이 그것을 뒷받침한다.

🔴 **작성 중 술어가 한 번 틀렸다.** 처음엔 임의 헤더 `X-Preflight-Probe` 로 preflight 를
강제했고 **당연히 실패했다** — 서버가 허용할 리 없는 헤더라 POST 가 되든 안 되든 같은 답이
나온다. **재는 경로가 사용자 경로와 달랐다.** 론처가 실제로 붙이는 헤더로 바꾸자 통과했다.

⏳ **남긴 것**: `시작` 버튼 자체는 누르지 않았다. `■ 데모 종료` 는 인스턴스가 `stopped` 라
**정상적으로 `disabled`** 이고(실측), 누를 수 있는 POST 버튼은 `시작` 뿐인데 그건 인스턴스를
켜서 **데모 예산을 쓴다.** CORS 층(이 AC 가 명시한 장애물)은 POST 를 포함해 전부 실증됐다.

**AC-4 — 제어 평면이 안 움직였다. ✅** 배포 후에도 동일 — 이 티켓이 Vercel 에 넣은 값은
`DEMO_API_BASE` 하나이고 공개 URL 이다. **AWS 자격 증명 0개.**

# Goal

론처 정적 페이지가 **재생성에서 살아남는 주소**에서 서빙되고, 제어 API 가 그 새 오리진을
정확히 허용한다. 제어 평면(EventBridge·Lambda·API Gateway)은 **AWS 에 그대로 남는다.**

# Scope

## In Scope

- `infra/demo/aws/site/index.html` 을 Vercel 프로젝트로 배포.
- `window.DEMO_API_BASE` 주입 경로를 Vercel 쪽으로 이식(아래 § 배선 참조).
- 허용 오리진 배선 — **두 곳 다**(아래 § 두 번째 집 참조).
- 옛 CloudFront/S3 경로의 처리 결정: 즉시 제거 / 당분간 병행 / 리다이렉트. **택일하고
  근거를 적을 것.** (병행이면 두 오리진이 다 허용돼야 한다.)

## Out of Scope

- 🔴 **`portfolio-demo-idle-check` 를 옮기는 것.** 이건 협상 대상이 아니다 — 아래 § 왜
  제어 평면은 못 옮기는가 를 읽을 것.
- 🔴 **AWS 액세스 키를 Vercel 환경변수에 넣는 것.** 같은 절 참조.
- 커스텀 도메인 구매. 별건이고 **비용 결정이라 사용자 몫**이다(§ 열린 결정).
- 론처 페이지의 UI/기능 변경. 이 티켓은 **호스팅 이전**이지 리디자인이 아니다.

---

# 🔴 왜 제어 평면은 못 옮기는가 (측정함)

`aws events list-rules` 실측:

```
portfolio-demo-idle-check    rate(5 minutes)    ENABLED
  IDLE_MINUTES=20 · MAX_RUNTIME_MINUTES=180 · MONTHLY_BUDGET_MINUTES=600
```

**5분마다 도는 이 규칙이 예산을 지키는 실체다.** 20분 유휴면 끄고, 180분 넘으면 끄고,
월 600분을 넘기면 못 켜게 한다.

**Vercel Hobby 의 Cron 은 하루 1회가 상한이라 5분 주기를 만들 수 없다.** 이걸 옮기면
유휴 감시가 하루 한 번이 되고, 면접관이 창을 닫고 잊은 데모가 최대 24시간 돈다 —
r6i.2xlarge 약 $0.56/시간이면 **한 번에 $13**, 이 논의의 월 총액보다 크다.

⇒ **비용을 아끼려다 비용 사고의 방아쇠를 당기는 방향이다.**

그리고 자격 증명: 지금 Lambda 는 **IAM 역할**(`portfolio-demo-lambda-…`)로 EC2 를 제어하고
**장기 키가 없다**. Vercel 에서 `ec2:StartInstances` 를 부르려면 액세스 키를 환경변수에
넣어야 하는데, 공개 엔드포인트 뒤에 장기 AWS 키를 두는 것은 **지금보다 명백히 나쁜 자세**다.

⇒ **정적 페이지만 옮긴다. 제어 API 는 AWS.**

---

# 🔴🔴 두 번째 집 — 허용 오리진이 두 곳에 있고, 지금 한쪽만 일한다

이게 이 티켓의 가장 위험한 부분이다. 실측(2026-08-18):

**(a) API Gateway 의 `cors_configuration`** (`main.tf:267-276`)

```hcl
allow_origins = [
  var.allowed_origin != "" ? var.allowed_origin : "https://${aws_cloudfront_distribution.site.domain_name}"
]
```

**(b) Lambda 의 `ALLOWED_ORIGIN` 환경변수** (`main.tf:256` → `handler.py:50,124`)

```python
ALLOWED_ORIGIN = os.environ.get("ALLOWED_ORIGIN", "*")
...
"Access-Control-Allow-Origin": ALLOWED_ORIGIN,
```

`var.allowed_origin` 은 지금 **빈 문자열**이다. 그래서:

- (a)는 폴백을 타서 **CloudFront 도메인**을 허용한다 — 참조라서 정확하다.
- (b)는 `os.environ.get` 이 **키가 존재하므로 기본값 `"*"` 이 아니라 `""` 를 돌려준다**.
  즉 핸들러는 `Access-Control-Allow-Origin: ""` 를 싣는다.

**라이브 대조군 3칸**으로 갈랐다:

| 요청 | 결과 |
|---|---|
| `Origin: …cloudfront.net` (허용) | `access-control-allow-origin: https://d38c06ry1h6rnn.cloudfront.net` |
| `Origin: https://example.com` | **CORS 헤더 없음** (올바른 거부) |
| `OPTIONS /domain/start` preflight | 204 + 허용 오리진·메서드·헤더 정상 |

⇒ **실제로 CORS 를 서빙하는 것은 (a)이고, (b)의 빈 문자열은 응답에 나타나지 않는다.**
오늘은 무해하다. 🔴 **그러나 같은 사실이 두 집을 갖고 있고, 그중 하나는 이미 틀린 값을
들고 있다** — (a)를 걷어내는 변경이 오면 그날 (b)가 전면에 나서서 **모든 오리진을 차단**한다.

## 그래서 이 티켓이 반드시 밟을 함정

Vercel 오리진을 **(a)에만** 넣고 끝내면:

- `curl` 은 통과한다(CORS 는 브라우저 정책이라 curl 로 우회된다 — `main.tf:272` 가 이미
  적어 뒀다).
- **브라우저에서만 실패한다.** 그리고 그 실패는 콘솔에 CORS 오류로 뜨는 게 아니라
  *"API 가 죽었다"* 처럼 보인다.

🔴 그러므로 **판정은 반드시 브라우저에서** 하거나, 최소한 `Origin:` 헤더를 실어서
`access-control-allow-origin` 이 **그 값으로 되돌아오는지** 확인해야 한다.
200 을 받았다는 사실은 **아무것도 증명하지 않는다**(위 표의 두 번째 행이 200 이다).

---

# 배선 — `window.DEMO_API_BASE`

페이지는 API 주소를 **리터럴로 갖고 있지 않다**(`index.html:80-91`):

```js
// API_BASE 는 **이 저장소에 존재하지 않는다.** 예전에는 여기 리터럴이 박혀 있었고 …
const API_BASE = window.DEMO_API_BASE;
if (!API_BASE) throw new Error("DEMO_API_BASE is not set: config.js missing or not deployed");
```

지금은 terraform 이 apply 때 **S3 에 `config.js` 를 생성**해 넣는다(`main.tf:454-461`):

```hcl
content = "window.DEMO_API_BASE = ${jsonencode(aws_apigatewayv2_api.api.api_endpoint)};\n"
```

⇒ Vercel 로 옮기면 **terraform 이 그 파일을 쓸 수 있는 자리가 없어진다.** 주입 경로를
새로 정해야 한다.

🔴 **리터럴을 커밋해서 해결하지 말 것.** 위 주석이 그것을 *"결함 2"* 라고 이름 붙여
두었고, 이 티켓이 그걸 되살리면 원래 고침을 무효로 만든다. 페이지가 `window.DEMO_API_BASE`
부재에 **명시적으로 죽도록** 설계된 것도 같은 이유다 — 그 가드를 약화시키지 말 것.

가능한 경로(구현자가 AC-0 에서 택일하고 근거를 적을 것):
- Vercel 환경변수 → 빌드 시 `config.js` 생성
- 정적 `config.js` 를 Vercel 프로젝트에 두고 API ID 변경 시 갱신 (🔴 수동 = 드리프트)
- terraform `output` 을 읽어 배포 파이프라인이 주입

---

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act).** ✅ **완료 (2026-08-19 UTC) — 네 항목 전부 어제와 동일.**
① `var.allowed_origin` = `""` ② Lambda `ALLOWED_ORIGIN` = `""` ③ CORS 3칸 대조군 결과 동일
④ `portfolio-demo-idle-check` = `rate(5 minutes)` `ENABLED`. 주소도 그대로
(`d38c06ry1h6rnn.cloudfront.net` · `r1tljg51qa.execute-api…`).
🔵 **덤으로 AC-2 의 before 대조군이 확보됐다**: `Origin: https://kanggle-portfolio.vercel.app`
는 **지금 CORS 헤더를 못 받는다**. 고친 뒤 그 자리가 바뀌는 것이 bite 다.

**AC-1 — 페이지가 Vercel 에서 뜬다.** ⏳ **저장소 측 완료, 배포는 사용자 계정 필요.**
`site/vercel.json` + `site/build.sh` 를 추가했다. 빌드는 `DEMO_API_BASE` 환경변수에서
`public/config.js` 를 렌더한다 — terraform 의 `aws_s3_object.config` 와 **같은 모양**이다
(두 배포 경로가 다른 모양을 내면 한쪽에서만 되는 상태가 생기고 진단이 가장 오래 걸린다).

🔴 **리터럴은 커밋하지 않았다.** 값이 없거나 이상하면 빌드가 죽는다 — 실측 대조군 4종:
미설정 / `""` / `http://…`(https 아님) / 끝 슬래시 → **전부 rc=1**, 정상 값만 rc=0.
빈 `config.js` 를 내보내면 빌드는 성공하고 페이지는 200 을 주며 **아무 버튼도 안 듣는다**.
그 상태는 "배포됨" 으로 보고되므로 아무도 안 본다.

**AC-2 — 허용 오리진이 두 곳 다 맞다.** ✅ **저장소 측 완료 — 다만 "두 곳" 을 "한 곳" 으로 만들었다.**

두 집을 맞추는 대신 **일하지 않는 쪽을 지웠다.** 실측이 (b)가 죽은 코드임을 보였기 때문이다
— 라이브 응답에 나타난 값은 전부 (a) 쪽이었고 (b)의 `""` 는 어디에도 없었다. 두 곳에서
실으면 `Access-Control-Allow-Origin` 이 중복될 수 있고 브라우저는 중복을 거부한다.
🔵 실패 방향도 이쪽이 안전하다: (a)가 사라지면 헤더가 **아예 없어져 즉시 깨진다** —
`"*"` 로 폴백해 조용히 전부 허용하는 것보다 낫다.

- `handler.py`: `ALLOWED_ORIGIN` 과 `Access-Control-*` 제거
- `main.tf`: Lambda env 에서 `ALLOWED_ORIGIN` 제거
- `variables.tf`: `allowed_origin`(string) → **`allowed_origins`(list)**.
  🔴 목록인 이유: 문자열 판은 값을 넣는 순간 CloudFront 폴백이 **꺼졌다** ⇒ 옮기는 동안
  두 오리진을 동시에 허용할 방법이 없어 **론처가 죽는 창이 반드시 생겼다**.
  이제 CloudFront 는 `local.cors_allowed_origins` 가 **항상 참조로** 넣고 여기 적은 것이 더해진다.

가드 2종, 둘 다 **주입을 증명한 뒤** bite 확인:
- `tests/test_handler.py::CorsHasOneHome` — 응답에 `Access-Control-*` 이 **없다**.
  고침 전 핸들러에 물린다(두 응답 경로 모두 FAIL). 대조군으로 `Content-Type` 은 남아
  있는지도 본다(헤더를 통째로 지운 구현과 구별). 🔴 픽스처의 `ALLOWED_ORIGIN` 은
  **일부러 남겼다** — 지우면 그 테스트는 *"값이 없어서"* 통과하는 **행사된 적 없는
  네거티브 테스트**가 된다. 그것까지 별도 테스트로 단언한다.
- `verify-demo-wrapper.sh (z9)` — terraform 쪽. Lambda env 에 `ALLOWED_ORIGIN` 이
  되돌아오면 물고, 오리진 목록이 CloudFront 를 **참조**하지 않으면 문다(리터럴 주입으로 확인).
  대조군: 추출한 블록에 `MONTHLY_BUDGET_MINUTES` 가 보이는지 — 추출이 빈 껍데기면
  통과가 아무것도 증명하지 않기 때문이다.

🔴🔴 **z9 의 bite 를 한 번 잘못 읽을 뻔했다.** `ALLOWED_ORIGIN` 을 되돌리는 주입이
**CRLF 때문에 0건**이었는데 결과는 *"안 물었다"* 로 보였다. 주입 건수를 먼저 세니 1건이
되고 그때 정확히 물었다. **판정 전에 주입을 증명할 것.**

**AC-3 — 브라우저에서 실제 조작이 된다.** ⏳ **미완 — Vercel 배포 + `terraform apply` + 인스턴스 기동이 필요하다(전부 사용자 승인 대상).**
🔴 `curl` 로는 만족시킬 수 없다 — CORS 는 브라우저 정책이라 curl 이 우회한다.
🔴 판정에 **200 을 쓰지 말 것**: 거부되는 오리진도 200 이다(AC-0 에서 재확인).
잔여 예산 291/600분.

**AC-4 — 제어 평면이 안 움직였다.** ✅ **저장소 측 확인.** `portfolio-demo-idle-check` =
`rate(5 minutes)` `ENABLED`(AC-0 실측), 이 변경은 EventBridge·Lambda 권한·IAM 역할을
**전혀 건드리지 않았다**. Vercel 쪽에 들어가는 환경변수는 `DEMO_API_BASE` **하나**이고
그건 공개 URL 이다 — **AWS 자격 증명 0개**. 배포 후 재확인은 AC-3 과 같은 실행에서.

**AC-5 — 옛 경로 처리.** ✅ **결정 = 병행(parallel).** 이유: CloudFront 를 먼저 끊으면
Vercel 이 뜨기 전까지 론처가 죽는 창이 생기고, 그 창은 *"데모가 고장났다"* 로 보인다.
`local.cors_allowed_origins` 가 **둘 다** 허용하므로 병행이 표현 가능해졌다(그게 문자열을
목록으로 바꾼 이유다). CloudFront 제거는 Vercel 이 실증된 뒤 **별도 판단**으로 남긴다 —
지금 지우면 롤백 자리가 없어진다.

# 🔴 남은 절차 — 사용자 계정이 필요한 부분 (에이전트가 못 한다)

**✅ 1단계는 완료됐다 (2026-08-19).** 실제 주소는 **`https://kanggle-portfolio.vercel.app`** 다.

| 항목 | 실제 값 |
|---|---|
| Project Name | **`kanggle-portfolio`** |
| Root Directory | `infra/demo/aws/site` |
| Framework Preset | Other (빌드/출력은 `vercel.json` 이 지정한다) |
| Environment Variable | `DEMO_API_BASE` = `https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com` |

라이브 3칸으로 확인했다: `/` **200**(SSO 리다이렉트 없음) · `/config.js` 가 **우리 값**을 서빙 ·
`<title>Portfolio — Live Demo</title>`. 두 번째 칸이 결정적이다 — 남의 사이트에서는 그 자리가 404 였다.

## 🔴 여기서 두 가지를 배웠다 (계획이 틀렸던 지점)

**(1) `*.vercel.app` 서브도메인은 전 Vercel 계정에 걸쳐 전역 고유다.** 이 티켓은 처음에
`portfolio-demo` 를 지정했는데 **그 이름은 이미 남이 쓰고 있었다** — 열어 보면 Next.js Blog
Starter Kit 이 서빙되고 `/config.js` 는 404 다. 원하는 이름이 선점돼 있으면 Vercel 은
`<project>-<팀슬러그>.vercel.app` 로 배정한다.
🔴 **`allowed_origins` 에 남의 오리진을 박을 뻔했다.** 그대로 apply 했으면 우리 페이지는
계속 막히고, CORS 는 "설정했는데 안 된다" 로 보였을 것이다 — 원인이 오리진 문자열 자체에
있으니 어느 층을 봐도 안 나온다. **이름은 정하는 것이 아니라 *확보되는지 확인하는* 것이다.**

**(2) Vercel Deployment Protection 이 기본으로 켜져 있었다.** 첫 배포는 빌드 success 였는데
`/` 가 `302 → vercel.com/sso-api` 였다. **빌드 성공은 "열린다"가 아니다.** 이 데모의 방문자는
로그인 없는 면접관이므로, 그 상태로 옮겼다면 **접근성이 나빠지는 이전**이 됐다.
Settings → Deployment Protection → Vercel Authentication → Production **Disabled** 로 해소.
🔵 Preview 는 켜 둬도 무방하다(PR 배포는 내부용).

**2. `terraform apply`** — 🔴 **사용자 승인 대상.** `terraform.tfvars` 에는
`allowed_origins = ["https://kanggle-portfolio.vercel.app"]` 이 들어가 있다.

```
cd infra/demo/aws/terraform
terraform plan     # 변경이 CORS·Lambda env 두 곳뿐인지 먼저 확인
terraform apply
```

기대 변경: API Gateway CORS 오리진이 **1개 → 2개**, Lambda 환경변수에서 `ALLOWED_ORIGIN`
**제거**. 🔴 인스턴스·EBS·AMI 가 계획에 나오면 **멈출 것** — 이 티켓의 범위가 아니다.

**3. AC-2 판정** — apply 뒤. 🔴 **200 이 아니라 헤더를 본다.**

```
A=$(terraform output -raw api_base_url)
curl -s -D - -o /dev/null -H "Origin: https://kanggle-portfolio.vercel.app" $A/status | grep -i access-control   # ← 값이 되돌아와야 한다
curl -s -D - -o /dev/null -H "Origin: https://d38c06ry1h6rnn.cloudfront.net" $A/status | grep -i access-control   # ← 병행: 여전히 허용
curl -s -D - -o /dev/null -H "Origin: https://example.com" $A/status | grep -i access-control   # ← 대조군: 아무것도 안 나와야 한다
```

세 번째 줄이 비어 있지 않으면 **모든 오리진을 허용해 버린 것**이고, 그건 통과가 아니다.

# Related Specs

- `infra/demo/aws/site/index.html` L80-91 — `DEMO_API_BASE` 계약과 그 부재-가드
- `infra/demo/aws/terraform/main.tf` L256(Lambda env) · L267-276(APIGW CORS) · L442-461(S3 객체)
- `infra/demo/aws/terraform/lambda/handler.py` L50,124 — `ALLOWED_ORIGIN` 소비 지점
- `infra/demo/aws/terraform/variables.tf` L75 — `allowed_origin` 선언

# Related Contracts

없음 (데모 인프라 전용).

# Edge Cases

- **`var.allowed_origin` 을 채우면 (a)의 CloudFront 폴백이 꺼진다** — 병행 운영을 원하면
  값 하나가 아니라 **목록**이 필요하다. 지금 `allow_origins` 는 원소 1개짜리 리스트다.
- **Vercel 프리뷰 배포는 매번 다른 URL 을 받는다** — 프리뷰에서 열면 CORS 에 막힌다.
  정상이며, 판정은 프로덕션 URL 에서 할 것.
- **`config.js` 의 `cache_control` 은 `max-age=60`** 이다. 옮긴 뒤 캐시 정책이 달라지면
  API ID 변경이 최대 그만큼 늦게 보인다.
- **Vercel Hobby 는 약관상 상업적 이용 금지**다. 개인 포트폴리오는 통상 문제없지만,
  구직 용도라는 성격을 알고 선택할 것.

# Failure Scenarios

- **(a)만 고치고 닫는다** — 브라우저에서만 실패하고, 그 실패가 *"API 가 죽었다"* 로 보인다.
  AC-2 가 두 곳을 다 요구하는 이유다.
- **`curl` 200 으로 AC-2 를 통과시킨다** — 거부되는 오리진도 200 이다(실측).
- **`DEMO_API_BASE` 리터럴을 커밋한다** — 이미 한 번 고쳐진 결함(*"결함 2"*)의 재현.
- **예산 감시를 Vercel 로 옮긴다** — 유휴 정지가 하루 1회가 되어 한 번에 $13 이 샌다.
- **비용 절감으로 정당화한다** — 실측 $0.0007/월. 근거가 틀리면 다음 사람이 이 결정을
  되돌린다.

# 열린 결정 (사용자 몫)

**커스텀 도메인($12/년 수준)이 더 나은 선택일 수 있다.** `demo.<본인도메인>` 이면
Vercel 이든 CloudFront 든 **뒤를 바꿔도 주소가 유지**되고, 포트폴리오로서 `*.vercel.app`
보다 낫다. Vercel 은 커스텀 도메인 연결 자체는 무료다.

⇒ 구매 결정이 서면 이 티켓의 AC-1 은 그 도메인으로 바뀐다. **에이전트가 대신 정하지 않는다.**

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Sonnet** — 호스팅 이전 + 오리진 배선이라 난이도는 낮다.
  다만 **AC-2 의 대조군 설계**만은 대충 하면 안 된다(200 으로 판정하면 통과하고 갈린다).
- 선행: 없음. 🔵 `TASK-MONO-477`(론처/제어 평면 도입)이 이 구조를 만든 티켓이다.
- 🔵 이 티켓은 **비용 티켓이 아니다.** 재고 실측이 $0.0007/월이라는 사실이 본문에 박혀
  있는 이유는, 그 오해가 이 작업의 가장 그럴듯한 오독이기 때문이다.
