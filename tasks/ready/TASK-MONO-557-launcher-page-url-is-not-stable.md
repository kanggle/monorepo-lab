# Task ID

TASK-MONO-557

# Title

론처 페이지 주소가 **재생성에서 살아남지 못한다** — 정적 페이지를 Vercel 로 옮기고 허용 오리진 배선을 그에 맞춘다

# Status

ready

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

**AC-0 — 재확인 (verify-then-act).** 착수 시점에 다시 잴 것. 이 티켓의 숫자는 전부
2026-08-18 실측이고, 그 사이 apply 가 있었으면 **CloudFront 도메인·API ID 가 달라진다**
(이 티켓이 다루는 바로 그 성질이다). 특히 ① `var.allowed_origin` 이 여전히 `""` 인가
② Lambda `ALLOWED_ORIGIN` 이 여전히 `""` 인가 ③ 위 CORS 3칸 대조군의 결과가 그대로인가.

**AC-1 — 페이지가 Vercel 에서 뜬다.** 그 주소로 론처가 렌더되고 `/status` 를 읽어
현재 상태를 표시한다. 🔴 **`window.DEMO_API_BASE` 리터럴 커밋 금지**(위 § 배선).

**AC-2 — 허용 오리진이 두 곳 다 맞다. 🔴 대조군 필수.**
Vercel 오리진에서 `access-control-allow-origin` 이 **그 값으로 되돌아온다**.
**그리고 대조군**: 아무 오리진(`https://example.com`)에는 **여전히 CORS 헤더가 없어야**
한다. 대조군 없이 통과만 보면 *"모든 오리진을 허용해서 통과시킨"* 구현과 구별되지 않는다.
🔴 판정에 **200 을 쓰지 말 것** — 거부되는 오리진도 200 이다(실측).
🔴 (a)와 (b) **둘 다** 확인할 것. 하나만 고치면 오늘은 통과하고 나중에 갈린다.

**AC-3 — 브라우저에서 실제 조작이 된다.** `/start` 를 눌러 인스턴스가 뜬다.
🔴 `curl` 로는 이 AC 를 만족시킬 수 없다 — CORS 는 브라우저 정책이라 curl 이 우회한다.
**인스턴스 기동은 예산을 쓴다(사용자 승인 대상).** 실측 잔여 291/600분(2026-08-18).

**AC-4 — 제어 평면이 안 움직였다.** `portfolio-demo-idle-check` 가 여전히
`rate(5 minutes)` `ENABLED` 이고, Vercel 에 **AWS 자격 증명이 하나도 없다.**
🔴 이건 형식적 확인이 아니다 — 이 티켓이 만들 수 있는 최악의 결과가 정확히 그 둘이다.

**AC-5 — 옛 경로 처리를 결정하고 적는다.** 제거/병행/리다이렉트 중 하나. 병행을 고르면
**두 오리진이 다 허용되는지** AC-2 의 대조군과 함께 확인할 것.

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
