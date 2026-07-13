# Task ID

TASK-MONO-389

# Title

데모에 **정문이 없다** — "Start Demo" 페이지는 어디에도 배포되지 않고, 그 페이지의 `API_BASE` 는 **존재하지 않는 API 를 가리킨다**

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- portfolio

---

# Goal

`TASK-MONO-366` 이 증명한 것: 데모는 **사람 손 0으로 부팅해서 방문자가 로그인한다.**
**증명되지 않은 것: 방문자가 그 데모에 도달할 수 있는가.**

**도달할 수 없다.** `README.md`(포트폴리오 허브)와 `infra/demo/aws/README.md` 는 *"방문자가 **Start Demo** 를 누르면 EC2 가 깨어난다"* 고 적는다. **그 버튼은 존재하지 않는다.**

**이건 `TASK-MONO-366` 이 고친 결함과 같은 계보다** — *저장소가 주장하는데 아티팩트가 뒷받침하지 못한다.* 366 은 *"코드는 부팅 계약을 이행하는데 부팅 경로가 그것을 안 쓴다"* 였고, 이건 *"데모는 동작하는데 방문자가 도달할 길이 없다"* 이다.

---

# 실측 (2026-07-13) — 결함 세 개가 겹쳐 있다

## 결함 1 — 호스팅 자원이 **없다**

`terraform state list` (destroy 전 22개 자원) 에 **S3 도 CloudFront 도 0개**다. `infra/demo/aws/site/index.html` 은 저장소에 있지만 **배포 대상이 정의된 적이 없다.**

⇒ 데모를 켜는 **유일한** 방법은 `curl -X POST <api>/start` 다. 방문자에게는 그 URL조차 없다.

## 결함 2 — `API_BASE` 가 하드코딩돼 있고, **이미 죽은 값이다**

```js
// infra/demo/aws/site/index.html:40
const API_BASE = "https://7l4n2ydrkd.execute-api.ap-northeast-2.amazonaws.com";
```

**`7l4n2ydrkd` 는 존재하지 않는 API 다.** 오늘 실제로 쓴 것은 `mat8kblrt4` 였고(그마저 destroy 로 사라졌다), **다음 `terraform apply` 는 또 다른 id 를 만든다.**

⇒ **이 파일을 지금 어디에 올리든 모든 호출이 실패한다.** 그리고 이건 우연이 아니라 **구조적**이다: API GW id 는 재생성마다 바뀌는데 그 값이 **git 에 커밋된 리터럴**로 박혀 있다. **드리프트가 일어난 게 아니라, 드리프트가 일어나도록 설계돼 있다.**

## 결함 3 — README 의 절차가 **에러를 낸다**

```
infra/demo/aws/README.md:41
  terraform output api_endpoint      # site/index.html 의 API_BASE 에 넣는다
```

**`api_endpoint` 라는 output 은 없다.** 실제 이름은 **`api_base_url`** 이다(`outputs.tf:1`). 절차를 그대로 따르면 `terraform output api_endpoint` 는 *"Output ... not found"* 로 죽는다.

**선언↔진실 축의 아홉 번째 사례다**(`TASK-MONO-372` 계보). 그리고 이 세 결함은 **서로를 가려준다** — 사이트를 배포한 적이 없으니 `API_BASE` 가 죽었는지 알 수 없었고, README 의 절차를 끝까지 밟은 적이 없으니 output 이름이 틀린 것도 몰랐다.

---

# 설계

## D1 — 호스팅: **S3(비공개) + CloudFront(OAC)**

| 안 | 평가 |
|---|---|
| **A. S3 비공개 + CloudFront + OAC** | **CHOSEN** — `*.cloudfront.net` 로 **HTTPS 무료**. 포트폴리오 링크가 `http://` 이면 브라우저가 "안전하지 않음" 을 띄우고, 그게 포트폴리오의 첫인상이 된다. 이 트래픽 규모에서 CloudFront 비용은 사실상 **$0**(프리티어 1TB/월). |
| B. S3 정적 웹사이트 호스팅 (공개 버킷) | HTTP 전용. 더 단순하지만 위 이유로 기각. 버킷 공개 정책도 필요. |
| C. GitHub Pages | 무료·HTTPS·이미 GitHub 저장소다. **그러나 `API_BASE` 주입이 커밋을 요구한다** ⇒ **결함 2 를 구조적으로 되살린다**(git 에 박힌 리터럴). D2 와 정면 충돌. |

## D2 — `API_BASE` 는 **커밋되지 않는다. terraform 이 렌더한다.**

**이게 이 티켓의 본체다.** 호스팅만 붙이고 리터럴을 손으로 고치면 **다음 재생성에 똑같이 죽는다.**

```hcl
resource "aws_s3_object" "site" {
  bucket       = aws_s3_bucket.site.id
  key          = "index.html"
  content      = templatefile("${path.module}/../site/index.html.tftpl", {
    api_base_url = aws_apigatewayv2_api.api.api_endpoint
  })
  content_type = "text/html"
  etag         = ...
}
```

- 저장소의 파일은 **`site/index.html.tftpl`** 이 되고 그 안엔 `const API_BASE = "${api_base_url}";` **플레이스홀더만** 있다.
- 배포된 페이지는 **terraform 상태에서 직접 렌더**되므로 **API 와 불일치하는 것이 표현 불가능**해진다.
- **드리프트를 고치는 게 아니라 드리프트를 불가능하게 만든다.**

## D3 — CORS `allow_origins`

`main.tf:217` 이 `allow_origins = [var.allowed_origin]` 이다. CloudFront 도메인이 배포 시점에야 정해지므로 **`aws_cloudfront_distribution.site.domain_name` 을 그대로 넣는다**(변수에 손으로 박지 않는다 — 그게 결함 2 의 재현이다).

---

# 가드 — **열거하지 않고 대조한다**

`infra/demo/verify-demo-wrapper.sh` 에 추가. (p) 가 확립한 원칙: **손으로 열거한 것이 애초의 결함이므로 가드가 다시 열거하면 같은 실수다.**

- **가드 (q) — 문서가 부르는 output 이 실재하는가.**
  `README`·`docs` 의 `terraform output <name>` 출현 집합 ⊆ `outputs.tf` 의 `output "<name>"` 집합.
  **mutation**: ① `outputs.tf` 에서 `api_base_url` → `api_url` 리네임 → **FAIL**(문서가 지목됨) ② 문서에 없는 output 참조 추가 → **FAIL** ③ 정상 트리 → **PASS** ④ **output 0개/문서 참조 0개 → exit 2**(공허 통과 금지).

- **가드 (r) — 배포될 페이지가 API URL 을 리터럴로 들고 있지 않은가.**
  `site/` 하위에 `execute-api` 를 포함한 **리터럴 URL** 이 있으면 FAIL. 템플릿의 `${api_base_url}` 플레이스홀더는 통과.
  **mutation**: ① 템플릿에 실제 execute-api URL 주입 → **FAIL** ② 플레이스홀더만 → **PASS** ③ **주석 안의 예시 URL → PASS**(오탐 0. 첫날 RED 인 가드는 꺼지고, 꺼진 가드의 skip 은 초록으로 보고된다 — `MONO-360`).

- **도달성**: `code-changed` 필터가 `.tftpl` 을 덮는지 확인한다. `MONO-366` § 4-a 가 정확히 이 자리에서 물렸다 — `.py`/`.tf`/`.hcl`/`.service` 가 빠져 **가드가 자기가 감시하는 파일에서 SKIP=초록**이었다. **`.tftpl` 은 지금 그 목록에 없다.**

---

# Scope

## In Scope

- `infra/demo/aws/terraform/` — S3(비공개) + CloudFront(OAC) + `aws_s3_object` (templatefile) + CORS 를 배포 도메인으로 배선 + `site_url` output.
- `infra/demo/aws/site/index.html` → **`index.html.tftpl`** 로 전환(리터럴 제거).
- `infra/demo/aws/README.md` — `api_endpoint` → **`api_base_url`** 정정 + 사이트 배포 절차 갱신.
- 루트 `README.md`(포트폴리오 허브) — **실제 데모 링크**를 싣는다(지금은 주장만 있고 링크가 없다).
- `verify-demo-wrapper.sh` 가드 (q)·(r) + `code-changed` 에 `.tftpl` 추가.

## Out of Scope

- **커스텀 도메인 / ACM 인증서** — `*.cloudfront.net` 로 충분. 실 도메인 구매는 **사람 결정**(`MONO-358` 이 `sslip.io` + Let's Encrypt 를 실측으로 배제한 것과 같은 자리).
- 데모 인스턴스의 TLS — 데모 자체는 여전히 `http://console.<ip>.sslip.io`(`MONO-358` Out of Scope 그대로). **사이트만 HTTPS 다.**
- `acceptAnyWellFormedTenant` → `TASK-MONO-388`.

---

# Acceptance Criteria

- [ ] **AC-1 — 버튼이 존재한다.** `terraform apply` 후 `site_url` 이 **HTTPS 로 200** 을 내고 "Start Demo" 가 보인다.
- [ ] **AC-2 — 그 버튼이 실제로 데모를 켠다.** 브라우저에서 눌러 → `/start` 200 → 부팅 → **파생 도메인 console 로그인까지** 도달. **curl 이 아니라 브라우저다**(CORS 는 브라우저에서만 강제된다 — curl 로 통과해도 증거가 아니다).
- [ ] **AC-3 — `API_BASE` 가 저장소에 리터럴로 없다.** `grep -r execute-api infra/demo/aws/site/` = **0건**. 배포된 페이지의 값은 `terraform output api_base_url` 과 **일치**한다.
- [ ] **AC-4 — 재생성 내성.** `terraform destroy` → `terraform apply` 후 **아무것도 손으로 고치지 않고** AC-2 가 다시 통과한다. **이게 D2 의 전부다** — 이 항목이 없으면 이 티켓은 리터럴 하나를 새 리터럴로 바꾼 것에 불과하다.
- [ ] **AC-5 — 가드 (q)·(r) mutation 확인** (위 4방향 × 2). **통과는 증거가 아니다.**
- [ ] **AC-6 — README 의 절차를 끝까지 밟는다.** `infra/demo/aws/README.md` 를 **한 줄씩 그대로 실행**해서 완주한다. **아무도 이 문서를 끝까지 따라 해본 적이 없어서 결함 3 이 살아남았다.**
- [ ] CI GREEN.

---

# Edge Cases

- **CloudFront 배포는 느리다** (~5–15분). `terraform apply` 시간이 늘어난다 — README 에 적는다.
- **CloudFront 캐시** — `index.html` 을 바꿔도 옛 것이 나온다. `aws_s3_object` 의 `etag` + 짧은 TTL(또는 invalidation)로 처리. **`API_BASE` 가 캐시된 옛 값이면 결함 2 가 캐시 층에서 부활한다.**
- **`allowed_origin` 순환 참조 주의** — API GW 의 CORS 가 CloudFront 도메인을 참조하고 CloudFront 가 S3 를 참조한다. API↔CloudFront 사이엔 순환이 없다(CloudFront 는 API 를 오리진으로 두지 않는다). 두면 순환이 된다 — **두지 말 것.**
- **공개 `/start` 는 여전히 무제한 지출 버튼이다** — 사이트가 생기면 **접근성이 실제로 올라간다.** 월 예산 가드(`MONTHLY_BUDGET_MINUTES=600`)가 유일한 상한이고, **이제 진짜로 시험받는다.** 값을 재검토할 것.

# Failure Scenarios

- **F1 — 호스팅만 붙이고 리터럴을 손으로 고침** → 다음 `terraform apply` 에서 **똑같이 죽는다.** 그리고 그때는 아무도 안 본다(사이트는 200 을 내고 버튼만 아무 일도 안 한다). **AC-4 가 이걸 막는다.**
- **F2 — curl 로 AC-2 를 검증** → CORS 결함을 못 잡는다. **브라우저에서만 강제된다.**
- **F3 — 가드 (r) 이 주석의 예시 URL 을 잡음** → 첫날 RED → 가드가 꺼진다 → **꺼진 잡의 skip 이 초록으로 보고된다**(`MONO-360` 실측).

# Test Requirements

- 정적: 가드 (q)·(r) + mutation.
- 실기동: AC-2(브라우저) + AC-4(destroy→apply 재현).
- 문서: AC-6(README 완주).

# Definition of Done

- [ ] AC-1~6 + CI GREEN
- [ ] 루트 `README.md` 에 **실제 데모 링크**
- [ ] `tasks/INDEX.md` done entry

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-366`(무인 실기동 증명) 종결 후 **AWS 자원을 정리하다** 드러났다.
`terraform state list` 에 S3 가 없다는 것을 보고서야 *"그럼 방문자는 이 데모를 어떻게 켜지?"* 를 처음 물었다.

**366 은 "데모가 뜨는가" 를 증명했고 그 질문에 답했다. 아무도 "방문자가 도달하는가" 를 묻지 않았다.**
그리고 그 질문을 안 물었기 때문에, `API_BASE` 가 죽은 값을 가리키고 README 의 절차가 에러를 낸다는 것도 **함께 숨어 있었다** — **배포된 적이 없는 것은 고장 났는지 알 수 없다.**

**현재 AWS 상태**: `terraform destroy` 완료(2026-07-13), `ami-0b6b962d3f3f23865` + 스냅샷 1개만 존치(월 ~$1.8). **`terraform apply` 2분이면 부활하고 AMI 재굽기는 불필요하다** — 그래서 이 티켓은 **인프라를 되살리면서 사이트를 함께 붙이는 것이 순서상 맞다**(새 `api_base_url` 이 그대로 템플릿에 렌더된다).

분석=Opus 4.8 / 구현 권장=**Sonnet**(terraform 자원 추가 + 템플릿화 + 가드 2개 — 도메인 판단은 없고 절차가 명확하다. 다만 **AC-4 를 빠뜨리면 이 티켓은 아무것도 고치지 않는다**).
