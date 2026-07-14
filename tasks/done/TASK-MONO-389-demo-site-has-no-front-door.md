# Task ID

TASK-MONO-389

# Title

데모에 **정문이 없다** — "Start Demo" 페이지는 어디에도 배포되지 않고, 그 페이지의 `API_BASE` 는 **존재하지 않는 API 를 가리킨다**

# Status

done

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

# 실측 (2026-07-14) — 절차를 **실제로 밟자** 결함이 넷 더 나왔다

셋은 문서가 이미 거짓말하고 있던 자리고, 넷째는 **데모가 애초에 뜬 적이 없었다**는 사실이다. 전부 AC-6("README 를 한 줄씩 밟는다")과 AC-4("destroy → apply") 를 실제로 수행하다 나왔다 — **정적 검증만 했으면 넷 다 못 봤다.**

## 결함 4 — `ec2/user-data.sh` 가 **저장소에 없다**

`main.tf:101` 이 `file("${path.module}/../ec2/user-data.sh")` 로 읽는데 **그 파일이 없다.** 저장소 사본으로는 `terraform validate` **조차 통과하지 못했다** — 저장소가 *"이 코드로 데모를 재현할 수 있다"* 고 주장하는 동안.

`TASK-MONO-366` 의 승격이 빠뜨렸고, 그때 그 승격은 **파일 해시 대조로 "검증"** 됐다. **없는 파일은 해시할 것이 없어 대조에 잡히지 않는다.** 있는 것만 세는 대조는 없음을 증명하지 못한다.

## 결함 5 — `terraform.tfvars.example` 이 CORS 를 **와일드카드로 열어 둔다**

```
# 정적 사이트의 오리진으로 좁히는 것을 권장(CORS).
allowed_origin = "*"
```

**주석은 좁히라 하고 코드는 넓혀 놓는다.** 그리고 이 파일을 `cp` 해서 채우는 것이 **README 가 시키는 절차다** ⇒ 권장을 따른 사람이 와일드카드를 얻는다.

## 결함 6 — 재현 계약이 **필요 권한을 한 번도 말하지 않는다**

README 0단계는 *"`aws configure`"* 뿐이다. **그 자격증명이 무엇을 할 수 있어야 하는지는 아무도 말하지 않았다.** 정문(S3+CloudFront)은 기존 권한 집합 밖이라, 첫 `apply` 가 **29개 중 14개를 만들고 `AccessDenied` 로 멈췄다.**

⇒ `iam/deployer-site-policy.json` + README 「배포 주체 권한」 절. `AmazonS3FullAccess` 를 붙이지 않고 **버킷 접두사로 스코프**했다.

## 결함 7 — **유휴 가드가 웜업 중인 인스턴스를 죽인다. 그래서 데모는 뜬 적이 없다.**

**이게 이 티켓에서 가장 큰 것이고, 정문을 열어야만 볼 수 있었다.**

```python
# terraform:  aws_ssm_parameter.beat  { value = "0" }
last_beat = int(_get(BEAT_PARAM, now))   # 파라미터가 "있으므로" 기본값 now 는 안 쓰인다
idle_sec  = now - 0                      # ≈ 17억 초 → 첫 틱(5분)에서 즉시 정지
```

handler 에는 `_get(BEAT_PARAM, now)` 라는 **안전 기본값이 있었다.** 그러나 파라미터가 **없을 때만** 쓰이고 terraform 이 항상 만들어 두므로 **한 번도 도달할 수 없었다.** `MONO-359/360` 이 이름 붙인 그 축이다 — **가드가 존재하는 것과 물 기회를 얻는 것은 다른 명제다.** (`started = "0"` 도 같아서 `max-runtime` 가드까지 함께 오발한다.)

피해는 정지에서 끝나지 않는다. 정지가 스택 웜업 한복판을 자르는 바람에 **kafka 의 KRaft 로그 디렉터리가 반쯤 쓰인 채 남고**(`log dir ... does not have a topic ID`), 그 뒤 **모든** 부팅에서 kafka 가 기동을 거부한다 → `demo-boot.sh` 가 `set -e` 로 중단 → **부트 순서상 마지막인 fan·console 은 영영 뜨지 않는다.**

```
15:31:19  dependency failed to start: container ecommerce-kafka is unhealthy
15:31:19  demo-stack.service: Failed with result 'exit-code'
```

방문자가 **Start Demo** 를 눌렀다면 **영원히 기동 중을 봤을 것이다.**

⚠️ **kafka 손상의 인과는 확정하지 않는다.** 처녀 인스턴스에서 kafka 는 `healthy` 가 됐지만, **그 인스턴스에서도 kafka 는 메모리 리밋(512 MiB) OOM 으로 14회 재시작했다** — OOM-킬 역시 파티션 디렉터리를 만든 뒤 메타데이터 커밋 전에 JVM 을 죽여 **같은 손상**을 만들 수 있다. 어느 쪽이 그 로그 디렉터리를 망가뜨렸는지 이 티켓은 **증명하지 않았다** → **`TASK-MONO-397`** 이 리밋을 고친 뒤 재현으로 가른다.
**유휴가드 수정 자체는 독립적으로 증명됐다** — 배포된 Lambda 를 직접 호출하니 `{"stopped": false, "idle_sec": 342, "run_sec": 342}` 즉 **가동 초 수와 정확히 일치**한다(옛 코드였다면 17.8억 + `stopped: true`). 그 명제는 397 의 결론과 무관하게 참이다.

**고침** — 두 시계를 EC2 의 `LaunchTime` 으로 하한한다. 누가 켰든(terraform·콘솔·`/start`) 참인 유일한 사실이다:

```python
anchor    = launched_at or now
last_beat = max(beat_param,    anchor)   # 센티널 0 도, 지난 부팅의 하트비트도 진다
started   = max(started_param, anchor)   # /start 가 방금 찍은 값은 그대로 이긴다
```

테스트 4건을 **대칭으로** 넣는다 — 오탐만 없애는 수정은 **가드를 끈 것과 구별되지 않는다**: 센티널이 웜업을 못 죽인다(1) · 옛 하트비트도 못 죽인다(2) · **그러나 진짜 유휴는 여전히 문다(3)** · **하트비트로 max-runtime 을 우회할 수 없다(4)**. 앵커를 되돌리면 4건 중 **3건이 빨개진다**(실측).

## 결함 8 — **"데모 사이트 열기" 버튼이 404 로 간다.** 점 하나 때문에.

결함 7 을 고쳐 스택이 완주하자 **그제야 보였다.**

| | |
|---|---|
| `demo-boot.sh:64` 가 파생하는 것 | `console.15-164-177-22.sslip.io` — **대시** (`tr '.' '-'`) |
| `index.html` 이 만드는 링크 | `console.15.164.177.22.sslip.io` — **점** |

**Traefik 라우터는 대시 표기로만 존재한다.** 그런데 sslip.io 는 **두 표기를 모두 같은 IP 로 해석한다** — 그래서 DNS 는 풀리고 TCP 도 붙고, Traefik 이 매치되는 라우터를 못 찾아 **404** 를 낸다. 에러 로그 0건. 실측:

```
점  → 404
대시 → 307 → /dashboards/overview → 200 /login   ← 콘솔이다
```

**사이트 200, `/start` 200, 96개 컨테이너 healthy — 그런데 방문자는 데모에 못 들어간다.** 두 반쪽이 **각자 일관되게** 서로 어긋나 있었고, 어느 쪽도 혼자서는 틀리지 않았다. 정문을 만들면서 **정문 안에 같은 결함을 하나 더 넣어 둔 셈**이다.

**가드 (t)** 는 문자열을 열거하지 않고 **두 규칙을 같은 IP 로 실행해 대조한다** — `demo-boot.sh` 의 `tr '.' '-'` 와 페이지의 `demoHost()` 를 `203.0.113.7` 로 돌려 결과가 갈리면 빨개진다. 어느 쪽이 바뀌어도(대시→점, 접미사 변경, 서브도메인 추가) 잡힌다.

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

**구현은 `index.html.tftpl` 이 아니라 `config.js` 다** — 위 스케치에서 벗어났고, 이유가 있다:

```hcl
resource "aws_s3_object" "config" {
  key          = "config.js"
  content      = "window.DEMO_API_BASE = ${jsonencode(aws_apigatewayv2_api.api.api_endpoint)};\n"
  content_type = "application/javascript; charset=utf-8"
  etag         = md5(aws_apigatewayv2_api.api.api_endpoint)
}
```

`index.html` 전체를 `templatefile` 로 돌리면 **terraform 의 `${...}` 보간이 JS 템플릿 리터럴과 충돌한다** — 페이지 안엔 `` `http://console.${ip}.sslip.io/` `` 같은 코드가 있고, 이들을 전부 `$${ip}` 로 이스케이프해야 한다. **이스케이프를 하나 빠뜨리면 terraform 이 "그런 변수 없다" 로 죽거나 — 더 나쁘게는 조용히 빈 문자열을 렌더한다.**

그래서 **렌더되는 부분만 한 줄짜리 파일로 격리한다.** `index.html` 은 순수 정적 자산이 되고, 저장소에는 API URL 이 **없다.** `config.js` 가 없으면 페이지는 **크게 실패한다**(버튼 비활성 + 경고) — 조용히 `undefined` 로 두면 200 을 내고 버튼만 아무 일도 안 하는데, **그게 정확히 이 task 가 고친 결함의 모양**이다.

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
  `site/` 하위 **코드 줄**에 `execute-api` 리터럴이 있으면 FAIL.
  **mutation**: ① `index.html` 에 실제 execute-api URL 주입 → **FAIL**(file:line 지목) ② 같은 URL 을 **주석 안에** → **PASS**(오탐 0. 첫날 RED 인 가드는 꺼지고, 꺼진 가드의 skip 은 초록으로 보고된다 — `MONO-360`).

- **가드 (s) — 저장소 어디에도 배포마다 바뀌는 엔드포인트가 **커밋**돼 있지 않은가.** (구현 중 추가)
  (r) 은 `site/` 만 본다. 그런데 **같은 결함은 문서로도 들어온다** — 나는 이 티켓을 하면서 *"실제 `site_url` 을 루트 README 에 넣겠다"* 고 적었다. CloudFront 도메인은 배포마다 새로 할당되고 `destroy` 하면 죽는다. **가드를 세워 놓고 그 옆에서 같은 짓을 할 뻔했다.**
  **`git grep` 이지 `grep -r` 이 아니다.** 첫 판본은 `terraform.tfstate` 를 물었다 — **gitignore 되어 커밋되지 않는 파일**이다. 명제("커밋됐는가")와 모집단(파일시스템)이 어긋났고, 그런 가드는 첫날 빨개져 꺼진다.
  **mutation**: ① 루트 README 에 실제 CloudFront 도메인 → **FAIL** ② 산문의 "CloudFront" 낱말 → **PASS** ③ task 본문의 죽은 URL **인용** → **PASS**(task/ADR 은 *무엇이었는지*를 기록하고, README/infra 는 *무엇을 하라*고 지시한다 — 부패하는 것은 지시뿐이다) ④ gitignore 된 tfstate → **PASS**.

- **도달성 — `demo-wrapper` 잡의 `&& code-changed` AND 를 제거한다.**
  `MONO-341` 이 의도적으로 넣은 것이고, 그 전제는 *"infra/demo 의 README-only 편집은 스모크를 건너뛴다"* 였다. 모든 가드가 `.sh`/`docker-compose.yml` 을 볼 때는 옳았다.
  **가드 (q) 는 README 를 본다.** 그 드리프트는 **markdown 편집으로 도착**하는데 `.md` 는 (의도적으로) `code-changed` 에 없다 ⇒ AND 를 걸면 **가드가 감시하는 바로 그 변경에서 꺼진다.** `MONO-360` 이 실측으로 이름 붙인 규칙: **트리거는 결함의 도착 경로를 따른다.**

---

# Scope

## In Scope

- `infra/demo/aws/terraform/` — S3(비공개) + CloudFront(OAC) + `aws_s3_object.config` + CORS 를 배포 도메인으로 배선 + `site_url` output.
- `infra/demo/aws/site/index.html` — API 리터럴 제거, `config.js` 소비(D2 참조: `.tftpl` 이 아닌 이유).
- `infra/demo/aws/ec2/user-data.sh` — **승격**(결함 4: 저장소에 없어 `terraform validate` 조차 실패했다).
- `infra/demo/aws/terraform/lambda/handler.py` + `tests/test_handler.py` — **유휴 가드가 웜업을 죽이는 결함 7**. 이게 없으면 정문은 **고장 난 데모로 이어진다.**
- `infra/demo/aws/iam/deployer-site-policy.json` + README 「배포 주체 권한」 — 결함 6.
- `infra/demo/aws/terraform/terraform.tfvars.example` — CORS 와일드카드(결함 5).
- `infra/demo/aws/README.md` — `api_endpoint` → **`api_base_url`** 정정 + 배포 절차 갱신.
- 루트 `README.md`(포트폴리오 허브) — 온디맨드 데모 절. **URL 은 싣지 않는다** — 가드 (s) 참조. 상시 URL 은 실 도메인 없이는 존재할 수 없는 물건이다.
- `verify-demo-wrapper.sh` 가드 (q)·(r)·(s) + `ci.yml` 의 `demo-wrapper` 도달성.
- `.gitignore` — `tfplan*`(저장된 plan 은 tfvars 의 공인 IP 를 품는다. `tfplan` 이라는 **이름 하나**만 막혀 있었다).

## Out of Scope

- **커스텀 도메인 / ACM 인증서** — `*.cloudfront.net` 로 충분. 실 도메인 구매는 **사람 결정**(`MONO-358` 이 `sslip.io` + Let's Encrypt 를 실측으로 배제한 것과 같은 자리).
- 데모 인스턴스의 TLS — 데모 자체는 여전히 `http://console.<ip>.sslip.io`(`MONO-358` Out of Scope 그대로). **사이트만 HTTPS 다.**
- `acceptAnyWellFormedTenant` → `TASK-MONO-388`.

---

# Acceptance Criteria

- [x] **AC-1 — 버튼이 존재한다.** `terraform apply` 후 `site_url` 이 **HTTPS 로 200** 을 내고 "Start Demo" 가 보인다.
      ✅ `GET site_url` → **200**, `id="start"` 존재, HTML 에 `execute-api` **0건**. `GET /config.js` → 200, `application/javascript`, 본문이 **방금 만들어진 API** 를 가리킨다.
- [x] **AC-2 — 그 버튼이 실제로 데모를 켠다.** 브라우저에서 눌러 → `/start` 200 → 부팅 → **파생 도메인 console 로그인까지** 도달. **curl 이 아니라 브라우저다**(CORS 는 브라우저에서만 강제된다 — curl 로 통과해도 증거가 아니다).
      ✅ **Chromium(Playwright) 실주행.** 모든 고리가 **배포된 아티팩트에서** 나왔다 — 내가 타이핑한 값이 아니다:
      `CloudFront HTML` → `config.js`(terraform 렌더) → `window.DEMO_API_BASE` → 그 API 의 `/status` → **배포된 페이지 자신의 `demoHost()`** → `console.15-164-177-22.sslip.io` → **HTTP 200 `/login?redirect=/dashboards/overview`, `<title>Platform Console</title>`**.
      **CORS**: `#msg` 가 `"✅ 준비 완료"` 를 표시했다 — 브라우저에서 fetch 가 **성공**했다는 뜻이다(막혔다면 `"상태 확인 실패"`). **콘솔 에러 0건, 실패 요청 0건.** 반대 방향도 확인: `Origin: evil.example` 에는 `Access-Control-Allow-Origin` 이 **아예 없다.**
      가는 길에 **결함 7·8** 이 나왔다 — `/start` 의 200 에서 멈췄다면 **둘 다 PR 을 통과했을 것이다.**
- [x] **AC-3 — `API_BASE` 가 저장소에 리터럴로 없다.** `grep -r execute-api infra/demo/aws/site/` = **0건**. 배포된 페이지의 값은 `terraform output api_base_url` 과 **일치**한다.
      ✅ 배포된 `config.js` = `window.DEMO_API_BASE = "https://<id>.execute-api…"`, `terraform output api_base_url` 과 **문자열 일치**. 그리고 **옛 리터럴 `7l4n2ydrkd` 는 오늘 만든 API(`l6cifcvrea`)와 다르다** — "리터럴이 썩는다" 는 주장이 한 줄로 실증됐다.
- [ ] **AC-4 — 재생성 내성.** `terraform destroy` → `terraform apply` 후 **아무것도 손으로 고치지 않고** AC-2 가 다시 통과한다. **이게 D2 의 전부다** — 이 항목이 없으면 이 티켓은 리터럴 하나를 새 리터럴로 바꾼 것에 불과하다.
      진행: 이 세션의 apply 는 **빈 state(serial=106, resources=0)에서 시작**했고 29개 자원을 처음부터 만들었다(손 편집 0 — IAM 정책만 예외였고, 그것이 결함 6 으로 저장소에 들어왔다). CORS 오리진도 CloudFront 도메인으로 **자동 배선**됐다.
- [x] **AC-5 — 가드 (q)·(r)·(s) mutation 확인.** **통과는 증거가 아니다.**
      ✅ (q) 4방향 · (r) 2방향 · (s) 4방향. 특히 (s) 는 **내가 하려던 커밋**(루트 README 에 실제 `site_url`)에서 RED 를 냈고, 산문 낱말·task 인용·gitignored tfstate 에서는 오탐 0.
- [x] **AC-6 — README 의 절차를 끝까지 밟는다.** `infra/demo/aws/README.md` 를 **한 줄씩 그대로 실행**해서 완주한다. **아무도 이 문서를 끝까지 따라 해본 적이 없어서 결함 3 이 살아남았다.**
      ✅ 완주했고, **밟았기 때문에 결함 4·5·6 이 나왔다.** 0단계(자격증명)에서 권한 부족으로 apply 가 절반에서 멈췄고, 2단계에서 `user-data.sh` 부재로 `terraform validate` 가 실패했으며, `tfvars.example` 은 권장과 반대되는 값을 담고 있었다.
- [ ] CI GREEN.

---

# Edge Cases

- **CloudFront 배포는 느리다** (~5–15분). `terraform apply` 시간이 늘어난다 — README 에 적는다.
- **CloudFront 캐시** — `index.html` 을 바꿔도 옛 것이 나온다. `aws_s3_object` 의 `etag` + 짧은 TTL(또는 invalidation)로 처리. **`API_BASE` 가 캐시된 옛 값이면 결함 2 가 캐시 층에서 부활한다.**
- **`allowed_origin` 순환 참조 주의** — API GW 의 CORS 가 CloudFront 도메인을 참조하고 CloudFront 가 S3 를 참조한다. API↔CloudFront 사이엔 순환이 없다(CloudFront 는 API 를 오리진으로 두지 않는다). 두면 순환이 된다 — **두지 말 것.**
- **공개 `/start` 는 여전히 무제한 지출 버튼이다** — 사이트가 생기면 **접근성이 실제로 올라간다.** 월 예산 가드(`MONTHLY_BUDGET_MINUTES=600`)가 유일한 상한이고, **이제 진짜로 시험받는다.** 값을 재검토할 것.
- **웜업 시간의 선언이 거짓이다.** 페이지와 Lambda 응답이 둘 다 *"약 2~4분"* 이라 말한다. **실측(데모 호스트 저널)은 iam→ecommerce 구간만 8분**이고 fan·console 이 뒤에 남는다. 방문자가 4분에 포기하도록 만드는 문구다 — 실측값으로 정정한다.
- **`terraform apply` 가 만든 인스턴스는 하트비트가 없다.** 이건 예외가 아니라 **정상 경로**다(`apply` → 인스턴스 running). 유휴 가드는 그 사실을 알아야 하며, 몰랐기 때문에 결함 7 이 났다. `LaunchTime` 앵커가 이 경로를 덮는다.

# Failure Scenarios

- **F1 — 호스팅만 붙이고 리터럴을 손으로 고침** → 다음 `terraform apply` 에서 **똑같이 죽는다.** 그리고 그때는 아무도 안 본다(사이트는 200 을 내고 버튼만 아무 일도 안 한다). **AC-4 가 이걸 막는다.**
- **F2 — curl 로 AC-2 를 검증** → CORS 결함을 못 잡는다. **브라우저에서만 강제된다.**
- **F3 — 가드 (r) 이 주석의 예시 URL 을 잡음** → 첫날 RED → 가드가 꺼진다 → **꺼진 잡의 skip 이 초록으로 보고된다**(`MONO-360` 실측).
- **F4 — 정문만 만들고 그 뒤를 확인하지 않음.** ✅ **실제로 일어났다** — 사이트는 200 을 내고, 버튼은 `/start` 200 을 받고, 인스턴스는 뜬다. **그리고 스택은 영영 안 뜬다**(결함 7). **"버튼이 200 을 낸다" 와 "방문자가 데모를 본다" 는 다른 명제고, 전자만 확인하면 후자가 거짓인 채로 초록이다.** AC-2 가 *"파생 도메인 console 로그인까지"* 를 요구하는 이유가 이것이다 — `/start` 의 200 에서 멈췄다면 이 결함은 **PR 을 통과했을 것이다.**
- **F5 — 가드 (s) 를 `grep -r` 로 구현.** ✅ 실제로 일어났다 — `terraform.tfstate`(gitignored)를 물어 첫날 RED. **명제는 "커밋됐는가" 인데 모집단은 파일시스템이었다.** `git grep` 이 정답이다.

# Test Requirements

- 정적: 가드 (q)·(r) + mutation.
- 실기동: AC-2(브라우저) + AC-4(destroy→apply 재현).
- 문서: AC-6(README 완주).

# Definition of Done

- [ ] AC-1~6 + CI GREEN
- [x] 루트 `README.md` 에 온디맨드 데모 절 — **URL 은 싣지 않는다.** 원래 계획은 *"실제 데모 링크"* 였고 **그게 틀렸다**: CloudFront 도메인은 배포마다 새로 할당되고 `destroy` 하면 죽는다 ⇒ **이 티켓이 죽이려는 바로 그 썩는 리터럴**이다. 가드 (s) 가 이제 그 커밋을 막는다. 상시 URL 은 실 도메인을 사기 전엔 존재할 수 없는 물건이고, 없는 것을 있는 척하는 대신 **재현 절차를 싣는다.**
- [x] 후속 티켓 **`TASK-MONO-397`** — 데모 스택 컨테이너 OOM 크래시루프(512 MiB Kafka).
- [ ] `tasks/INDEX.md` done entry

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-366`(무인 실기동 증명) 종결 후 **AWS 자원을 정리하다** 드러났다.
`terraform state list` 에 S3 가 없다는 것을 보고서야 *"그럼 방문자는 이 데모를 어떻게 켜지?"* 를 처음 물었다.

**366 은 "데모가 뜨는가" 를 증명했고 그 질문에 답했다. 아무도 "방문자가 도달하는가" 를 묻지 않았다.**
그리고 그 질문을 안 물었기 때문에, `API_BASE` 가 죽은 값을 가리키고 README 의 절차가 에러를 낸다는 것도 **함께 숨어 있었다** — **배포된 적이 없는 것은 고장 났는지 알 수 없다.**

**현재 AWS 상태**: `terraform destroy` 완료(2026-07-13), `ami-0b6b962d3f3f23865` + 스냅샷 1개만 존치(월 ~$1.8). **`terraform apply` 2분이면 부활하고 AMI 재굽기는 불필요하다** — 그래서 이 티켓은 **인프라를 되살리면서 사이트를 함께 붙이는 것이 순서상 맞다**(새 `api_base_url` 이 그대로 템플릿에 렌더된다).

분석=Opus 4.8 / 구현 권장=**Sonnet**(terraform 자원 추가 + 템플릿화 + 가드 2개 — 도메인 판단은 없고 절차가 명확하다. 다만 **AC-4 를 빠뜨리면 이 티켓은 아무것도 고치지 않는다**).
