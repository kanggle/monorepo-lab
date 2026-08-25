# Task ID

TASK-MONO-579

# Title

`ADR-MONO-067` **단계 1** — 론처가 두 집(Vercel · CloudFront)에 산다. **Vercel 하나로 줄인다.** 코드까지가 이 티켓, `terraform apply` 는 소유자.

# Status

in-progress

# Owner

monorepo

# Task Tags

- adr
- infra
- cleanup

---

# ⏳ 선행 — **없다. 이것이 AC-0 과 독립인 유일한 단계다.**

`ADR-MONO-067` § AC-0 이 못 박았다: *"**단계 1(론처 이중 배포 정리)은 AC-0 과 독립**이므로 먼저
갈 수 있다."* 다른 모든 단계는 D2/D4 와 소유자 게이트에 걸려 있고, **이것만 안 걸린다.**

---

# Goal

D3 를 실행한다 — **론처의 집을 하나로.** 지금 론처는 Vercel(`kanggle-portfolio.vercel.app`)과
CloudFront(S3 오리진) **두 곳**에서 서빙된다. ADR 의 표현대로:

> *"한 사실이 두 집을 가지면 **한쪽만 갱신된다**."*

🔴 **그리고 이 저장소에서 그 드리프트는 「일어날 수도 있는 일」이 아니라 「설계상 일어나는 일」이다** —
Vercel 판은 커밋마다 자동으로 다시 구워지고, S3 판은 **`terraform apply` 때만** 갱신된다.
그리고 `apply` 는 **소유자 승인 대상**이라 몇 주씩 안 돌 수 있다.

---

# Context — 실측 (2026-08-26)

## ① 오늘은 두 사본이 **같다** — 그래서 지금이 옮길 때다

```
bash infra/demo/aws/site/check-launcher-fresh.sh --origin https://kanggle-portfolio.vercel.app   → rc=0
bash infra/demo/aws/site/check-launcher-fresh.sh --origin https://d38c06ry1h6rnn.cloudfront.net  → rc=0
```

| | 서빙 md5 | 크기 | 커밋 축 |
|---|---|---:|---|
| Vercel | `6437f1fe61888f634e7dc2ce2dc37946` | 23206 B | ✅ `53bb4712c` (`build-info.json`) |
| CloudFront | **같음** | 23206 B | ❌ **없음** — *"build-info.json 없음 — S3 사본"* |

🔵 **드리프트가 아직 안 일어난 상태에서 줄이는 것이 가장 싸다.** 어긋난 뒤에 줄이면 *"어느 쪽이
맞는가"* 를 먼저 풀어야 한다.

🔴 **두 사본은 애초에 대칭이 아니다** — S3 판에는 `build-info.json` 이 없어서 **내용 축만** 성립하고
*"어느 판인가"* 를 물을 수 없다. 사본이라기보다 **판정 능력이 낮은 사본**이다.

## ② "동일성 가드가 없다" 는 절반만 맞다 — **도구는 있고, 아무도 안 부른다**

`check-launcher-fresh.sh` 는 이미 `--origin` 을 받고, 자기 docblock 이 이렇게 적어 뒀다:
*"(a) 내용 … **어느 오리진에서나 성립한다. S3/CloudFront 사본에도 쓴다.**"*

🔴 **그런데 CI 는 이 스크립트를 `bash -n`(문법 검사)만 한다.** 실제로 부르지 않는다.
그리고 **CloudFront URL 은 저장소에 없다** — `terraform output site_url`, 즉 **로컬 state 안에만**
있다(저장소 grep 0건, done 티켓의 서술을 제외하면). ⇒ **CI 가 이 축을 잴 방법이 원리적으로 없다.**

⇒ **가드를 새로 만드는 것보다 집을 하나로 줄이는 것이 싸다.** 집이 하나면 잴 것도 하나다.

## ③ 🔴🔴 **CORS 가 CloudFront 를 참조로 붙들고 있다** — 여기가 진짜 결합점

`infra/demo/aws/terraform/main.tf:37`:

```hcl
cors_allowed_origins = distinct(concat(
  ["https://${aws_cloudfront_distribution.site.domain_name}"],   # ← 항상 포함
  var.allowed_origins,                                           # ← Vercel 은 여기로 들어온다
))
```

CloudFront 를 지우면 이 목록은 **`var.allowed_origins` 하나에만** 의존한다. 그런데:

- `terraform.tfvars.example` 은 **`allowed_origins = []` 를 주석 예시로 제시한다.**
- 빈 목록은 **plan 을 통과한다.** 실패는 런타임에, **론처의 Start 버튼이 조용히 죽는 모양**으로 온다.

🔴 **지금은 CloudFront 참조가 그 구멍을 가려 주고 있다.** 그것을 치우는 변경이라면 **같은 변경에서**
구멍을 막아야 한다 — 안 그러면 이 티켓이 결함을 배포한다.

## ④ 🔴🔴 **이 변경을 막는 가드가 이미 있다** (`verify-demo-wrapper.sh` (z9)(2))

```bash
printf '%s' "$z9_local" | grep -q 'aws_cloudfront_distribution.site.domain_name' \
  || fail "(z9) 허용 오리진 목록이 CloudFront 도메인을 **참조**하지 않습니다."
```

이 핀은 `TASK-MONO-389` 가 고친 결함(**AWS 발급 주소를 리터럴로 박으면 재생성마다 썩는다**)을
지키려고 쓰였다. 🔵 **그 주제는 옳고 지금도 옳다.** 틀린 것은 **핀이 그 주제를 「CloudFront 를
참조하라」로 좁혀 적었다**는 것이다 — 집이 하나가 되면 그 문장은 주제를 지키는 대신 **주제의 해소를
막는다.**

⇒ **핀을 지우지 말고 뒤집어라.** 지키려던 명제(*"AWS 발급 주소가 리터럴로 박히면 안 된다"*)는
**남기고**, *"CloudFront 를 참조해야 한다"* 만 *"목록이 비면 안 된다"* 로 바꾼다.
[[feedback_retract_the_exemption_when_the_defect_is_fixed]]

## ⑤ Vercel 판은 **자족적**이다 — 폐기해도 안 깨진다

`site/build.sh` 는 `DEMO_API_BASE` **환경변수**에서 `config.js` 를 만들고, **없으면 빌드를 죽인다**
(fail-closed). CloudFront 판이 주던 것(terraform 이 렌더한 `config.js`)에 **의존하지 않는다.**
⇒ 폐기가 Vercel 판의 어떤 입력도 뺏지 않는다. **확인함.**

---

# Scope

**하는 것** (전부 저장소 안):

- `infra/demo/aws/terraform/` — S3 사이트 버킷 · CloudFront 배포 · OAC · 버킷 정책 · `aws_s3_object.index`/`config` 제거, `local.cors_allowed_origins` 를 `var.allowed_origins` 로, `var.allowed_origins` 에 **fail-closed validation**, `output "site_url"` 정리
- `infra/demo/verify-demo-wrapper.sh` (z9)(2) — 핀 **뒤집기**(삭제 아님)
- `terraform.tfvars.example` · `infra/demo/aws/README.md` — 안내 갱신
- `docs/adr/ADR-MONO-067-…md` § D3 — 상태 기록

**안 하는 것** (🔴 명시):

- **`terraform apply` 를 돌리지 않는다.** 소유자 승인 대상이고, 이 변경의 apply 는 **파괴적**이다
  (CloudFront 배포 + S3 버킷 삭제). AC-5 가 소유자용 절차를 적는다.
- `check-launcher-fresh.sh` 를 CI 에 붙이지 않는다 — 집이 하나가 되면 **잴 것도 하나**이고,
  기존 fan/portfolio 신선도 축이 이미 그 하나를 본다.

---

# Acceptance Criteria

## AC-0 — 전제 재측정 (착수 시점)

🔴 **§ Context ① 을 다시 재라.** 두 사본이 **그 사이에 어긋났으면 이 티켓의 전제가 다르다** —
그때는 *"어느 쪽이 맞는가"* 를 먼저 풀어야 하므로 **STOP** 하고 티켓을 고친다.

```bash
CF="$(cd infra/demo/aws/terraform && terraform output -raw site_url)"
bash infra/demo/aws/site/check-launcher-fresh.sh --origin https://kanggle-portfolio.vercel.app
bash infra/demo/aws/site/check-launcher-fresh.sh --origin "$CF"
```

🔴 **rc=2("판정 불가")를 0 으로 접지 마라** — 스크립트 헤더가 그렇게 못 박았다.

## AC-1 — 🔴 **CORS 구멍을 먼저 막는다** (제거보다 **먼저**)

`var.allowed_origins` 에 `validation` 을 건다. 최소한 이 셋:

| # | 무엇 | 왜 |
|---|---|---|
| 1 | **비어 있으면 실패** | CloudFront 참조가 사라지면 빈 목록 = **모든 오리진 거부** = Start 버튼 사망. 지금은 plan 을 통과한다 |
| 2 | 전부 `https://` 로 시작 | 론처는 HTTPS 다 |
| 3 | 끝 슬래시 금지 | `build.sh` 가 `DEMO_API_BASE` 에 대해 이미 같은 검사를 한다 — **같은 함정, 같은 모양** |

🔴 **실패 시점을 런타임에서 `plan` 으로 옮기는 것이 이 AC 의 전부다.** 조용한 죽음을 시끄러운
죽음으로 바꾼다. [[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

🔵 **`terraform.tfvars.example` 의 `allowed_origins = []` 예시도 같이 고친다** — 안 고치면 문서가
**plan 에서 실패하는 값**을 계속 권한다.

## AC-2 — CloudFront/S3 사이트 리소스 제거

`aws_s3_bucket.site` · `aws_s3_bucket_public_access_block.site` · `aws_s3_bucket_policy.site` +
그 정책 문서 · `aws_cloudfront_origin_access_control.site` · `aws_cloudfront_distribution.site` ·
`aws_s3_object.index` · `aws_s3_object.config`.

`local.cors_allowed_origins` → `distinct(var.allowed_origins)`.

**`output "site_url"`**: CloudFront 도메인을 내던 출력이다. 🔴 **살려서 Vercel URL 을 박지 마라** —
terraform 이 소유하지 않는 값을 terraform 출력으로 두면 **거짓 출처**가 된다. 제거하고, 론처가
어디 사는지는 README 가 말한다.

## AC-3 — 🔴🔴 핀을 **뒤집는다** (`verify-demo-wrapper.sh` (z9)(2))

- **남긴다**: *"AWS 발급 주소(`cloudfront`/`execute-api`)가 **리터럴로** 박히면 실패"* — 주제는 그대로다.
- **바꾼다**: *"CloudFront 를 **참조**해야 한다"* → *"목록이 **`var.allowed_origins` 에서 와야 하고
  비면 안 된다**"*.
- **추출 앵커**: `cors_allowed_origins = distinct(concat(` 로 시작하는 awk 앵커가 **갈라진다**.
  새 모양에 맞춰 고치고, 🔴 **추출이 빈 껍데기가 아님을 증명하는 (3) 대조군을 같은 모양으로 유지**한다
  (원문 주석이 왜 그것이 있는지 적어 뒀다 — *"추출이 빈 껍데기면 (1)은 항상 통과한다"*).

**bite 필수**: 새 핀이 실제로 무는지 **주입해서** 본다.

| 주입 | 기대 |
|---|---|
| `allowed_origins` 를 빈 목록으로 만든 사본 | **RED** |
| 리터럴 `"https://xxx.cloudfront.net"` 을 목록에 박은 사본 | **RED** (기존 주제가 살아 있는가) |
| 무변경 | **GREEN** |

🔴 **bite 를 읽기 전에 주입이 실제로 들어갔는지 먼저 단언하라** — 이 저장소는 CRLF 로 주입이 0건인
것을 *"안 물었다"* 로 읽은 적이 있다. [[feedback_assert_the_injection_before_reading_the_bite]]

## AC-4 — 문서

- `infra/demo/aws/README.md` — `terraform output site_url` 안내가 **없어진 출력**을 가리키게 두지 않는다.
  론처 주소 = `https://kanggle-portfolio.vercel.app` (D3 정본).
- `ADR-MONO-067` § D3 — *"두 사본이 같은지 재는 가드는 지금 없다"* 옆에 **이 티켓의 결론**을 적는다:
  가드를 만드는 대신 **집을 줄였다**. 🔵 § Context ② 의 정정(*도구는 있었고 CI 가 못 부른다*)도 함께.

## AC-5 — 🔴 소유자 실행 절차를 티켓에 **적어 둔다** (실행은 안 한다)

이 변경의 `apply` 는 **파괴적**이다. 순서를 못 박는다:

1. **apply 전** — AC-0 의 두 측정을 다시 돌려 **두 사본이 같은지** 확인. 어긋나 있으면 멈춘다.
2. `terraform plan` — 파괴 대상이 **사이트 리소스뿐**인지 눈으로 확인.
   🔴 **EC2/Lambda/API Gateway 가 plan 에 destroy 로 뜨면 즉시 중단**한다.
3. `terraform apply`.
4. **apply 후** — ① `check-launcher-fresh.sh` (Vercel) **rc=0** ② 론처의 **Start 버튼**이 CORS 를
   통과하는지(실제로 눌러 보는 것 말고, `curl -H "Origin: https://kanggle-portfolio.vercel.app"`
   프리플라이트로 확인 — **기동은 예산을 쓴다**) ③ CloudFront URL 이 **죽었는지**.

🔴 **④의 ②를 「버튼을 눌러 본다」로 하지 마라** — 데모 기동은 월 예산을 쓰고 승인 대상이다.
CORS 는 프리플라이트로 **기동 없이** 잴 수 있다.

## AC-6 — 검증

| 무엇 | 어떻게 |
|---|---|
| terraform 문법·형식 | `terraform fmt -check` + `terraform validate` (🔵 AWS 호출 없음) |
| 래퍼 가드 | `bash infra/demo/verify-demo-wrapper.sh` — (z9) 통과 + AC-3 의 bite 3칸 |
| 큐 드리프트 | `check-index-queue-drift.sh` — 🔴 **`git add` 뒤에** |
| 🔴 **apply 안 함** | `git diff` 에 `.tfstate` 변경 **0** — state 가 바뀌었으면 누군가 apply 한 것이다 |

---

# Related Specs

- `docs/adr/ADR-MONO-067-…md` § D3 · § AC-0(단계 1 독립 명시)
- `tasks/done/TASK-MONO-389-demo-site-has-no-front-door.md` — 리터럴 주소 결함(핀의 주제)
- `tasks/done/TASK-MONO-557-launcher-page-url-is-not-stable.md` — 이름 묶인 Vercel 주소가 생긴 배경
- `infra/demo/aws/site/check-launcher-fresh.sh` — 두 오리진 축을 이미 가진 도구

# Related Contracts

없음 — 배포 위치 정리이고 서비스 간 계약을 바꾸지 않는다. (CORS 허용 목록은 계약이 아니라 설정이다.)

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| AC-0 에서 두 사본이 **어긋나 있다** | **STOP.** 전제가 다르다 — 어느 쪽이 맞는지부터 |
| `terraform output site_url` 이 안 나온다 | state 가 없는 체크아웃이다. **CloudFront 축은 판정 불가(rc=2)** 로 적고 넘어가지 말 것 |
| 소유자가 apply 를 한참 안 한다 | 🔵 **문제 없다.** 코드가 앞서 있고 state 가 뒤에 있는 상태이며, 그동안 CloudFront 판은 계속 뜬다. 다만 **AC-0 측정은 apply 직전에 다시** |
| apply 후 Start 버튼이 CORS 로 막힌다 | AC-1 의 validation 이 빈 목록을 이미 막았으므로, 원인은 **값이 틀린 것**이다(오타·끝 슬래시). validation 2·3 이 그것도 본다 |
| CloudFront URL 을 외부 어딘가가 링크한다 | 실측: 저장소에는 **done 티켓 서술 3건뿐**, 살아 있는 링크 0건. 🔴 저장소 밖(북마크·이력서)은 못 재므로 **못 쟀다고 적는다** |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| CORS 구멍을 안 막고 리소스만 지운다 | plan 초록, apply 후 **Start 버튼 사망** | **AC-1 을 AC-2 보다 먼저.** 이 티켓의 순서가 그렇게 잡힌 이유다 |
| (z9) 핀을 **지워서** 초록을 만든다 | CI 초록, `389` 가 고친 결함이 되돌아올 길이 열림 | 🔴 **뒤집기지 삭제가 아니다.** 리터럴 금지 명제는 남는다 |
| awk 앵커가 갈라져 추출이 빈 껍데기 | (z9) 가 **조용히 통과** | (3) 대조군이 그것을 잡는다 — **같은 모양으로 유지**하라 |
| bite 를 안 하고 "고쳤다" 로 넘어간다 | 새 핀이 실제로 무는지 아무도 모름 | AC-3 의 3칸. **주입 성립을 먼저 단언** |
| 내가 `apply` 를 돌려 버린다 | `.tfstate` 가 diff 에 뜬다 | AC-6 마지막 행이 그것을 본다. **파괴적 조작은 소유자 승인 대상** |
| `site_url` 출력에 Vercel URL 을 박는다 | terraform 이 소유하지 않는 값이 terraform 출처가 됨 | AC-2 가 명시적으로 금지 — **거짓 출처** |
