# Task ID

TASK-MONO-644

# Title

🔴🔴 `TASK-MONO-636` 이 만든 «404 = 아직 배포 안 됨» 분기가 **프로덕션에서 도달 불가능**하다 — 404 응답에 CORS 헤더가 없어 브라우저가 삼킨다

# Status

in-progress

# Owner

monorepo

# Task Tags

- demo
- infra
- launcher

---

# Goal

`TASK-MONO-636` 은 론처가 «왜 잠겼는지» 를 말하게 만들었고, 그 핵심은 **두 상태를 구별**하는
것이었다:

| 상태 | 문구 |
|---|---|
| `/bundles` → **404** | 「이 기능이 제어 API 에 **아직 배포되지 않았습니다** (/bundles → 404)」 |
| `/bundles` → 5xx·무응답 | 「제어 API 가 **응답하지 않습니다** — 잠시 후 자동으로 다시 시도합니다」 |

**프로덕션에서 방문자가 보는 것은 아래쪽이다.** 그리고 그것은 **틀렸다** — 제어 API 는
응답하고 있고(`/status` 200), 재시도로는 영원히 안 풀린다(`terraform apply` 가 필요하다).

🔴 즉 `TASK-MONO-636` 이 고치려던 부류의 거짓을, 636 을 배포한 화면이 그대로 저지르고 있다.

---

# Context — 실측 (2026-09-08 UTC, `main` = `e34f4ae39`, 라이브 `hubwang.com`)

## 브라우저에서 실제로 나간 요청

```
DEMO_API_BASE = "https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com"

200     .../status
FAILED  .../bundles :: net::ERR_FAILED
```

🔴 `/bundles` 는 **404 응답이 아니라 네트워크 실패**로 도착한다. 그래서 `pollBundles()` 의
`catch` 가 잡고 `bundlesErr = "down"` 이 된다 — `r.status === 404` 를 보는 줄까지 **가지도
못한다.**

## 왜 실패하는가 — CORS

`Origin: https://hubwang.com` 을 붙여 직접 쟀다:

| 요청 | 상태 | `access-control-allow-origin` |
|---|---|---|
| `GET /status` | 200 | ✅ `https://hubwang.com` |
| `GET /bundles` | **404** | 🔴 **없음** |
| `OPTIONS /bundles` (프리플라이트) | 204 | ✅ 있음 |

⇒ API Gateway 가 **라우트 없음**에 대해 스스로 내는 기본 404(`{"message":"Not Found"}`)에는
CORS 헤더가 안 붙는다. 프리플라이트만 통과하고 본 요청의 응답을 브라우저가 차단한다.

🔵 프리플라이트가 204 로 통과하는 것이 이 결함을 **더 찾기 어렵게** 만든다 — 네트워크 탭에서
OPTIONS 는 초록이다.

## 라이브 화면 (2026-09-08 UTC)

```
.bnote × 3: 「제어 API 가 응답하지 않습니다 — 잠시 후 자동으로 다시 시도합니다.
             공개 둘러보기는 정상 동작합니다. (EC2 대기 중 · 도메인 console 확인 중)」
배지 × 3:   🔴 확인 불가
기동버튼:    실시간 기능 시작 (disabled)
```

🔵 **잠금과 사유 표시 자체는 동작한다** — `TASK-MONO-636` 이 고친 「사유 없는 죽은 버튼」은
사라졌다. 틀린 것은 **어느 사유를 말하는가** 다.

---

# Scope

## 포함 — 두 축이 있고, **둘 다** 해야 한다

1. **인프라**: API Gateway 의 기본 4xx 게이트웨이 응답에 CORS 헤더가 붙게 한다
   (`DEFAULT_4XX` / `DEFAULT_5XX` 게이트웨이 응답의 CORS 설정).
2. **론처 코드**: `/bundles` 가 던졌을 때 «제어 API 가 죽었다» 로 단정하지 않는다.
   🔴 **론처는 이미 구별할 근거를 들고 있다** — 같은 폴링 주기에 `/status` 가 200 을 줬다.
   `/status` 는 되는데 `/bundles` 만 던지면 그것은 «API 가 죽음» 이 아니라 **«그 라우트만
   없음(또는 차단됨)»** 이다. 지금 코드는 그 사실을 안 쓴다.

🔴 **왜 둘 다인가**: ①만 하면 이 배포는 고쳐지지만, 앞으로 CORS 를 안 붙이는 다른 경로가
생기면 화면이 또 조용히 거짓말한다. ②만 하면 브라우저는 여전히 응답 본문을 못 보므로
「404 인지」는 끝까지 모른다 — 다만 «API 가 죽었다» 는 **거짓말은 멈춘다.**

## 제외

- `terraform apply` 로 `/bundles` 라우트를 실제로 배포하는 것 — 그건 별개이고 **비용이
  드는 작업**이라 소유자 승인 사항이다. 이 티켓은 **라우트가 없는 동안 화면이 정직한가** 다.
  🔵 라우트가 배포되면 이 결함은 «안 보이게» 되지만 **사라지지는 않는다**(다음에 라우트가
  하나 늘 때 똑같이 재발한다).
- 배지 문구 체계 전반 — `TASK-MONO-636` 이 정한 7-상태 표를 유지한다.

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정

- [x] `Origin` 헤더를 붙여 `/status` 와 `/bundles` 를 다시 재고 위 표를 갱신한다.
- [x] 🔴 **`terraform apply` 가 그 사이 돌아 `/bundles` 가 200 이 됐어도 티켓을 닫지 마라.**
      판정은 «라우트가 있나» 가 아니라 **«없는 라우트의 4xx 가 CORS 헤더를 달고 오나»** 다.
      200 이면 존재하지 않는 경로(`/__no_such_route__`)로 같은 축을 재라.
- [x] 브라우저에서 실제 요청을 다시 잡아 `net::ERR_FAILED` 인지 `404` 인지 적는다.

## AC-1 — 존재하지 않는 경로의 4xx 에도 CORS 가 붙는다

🔴🔴 **2026-09-08 정정 — 이 AC 의 첫 판이 적은 처방은 이 API 에 존재하지 않는다.**
원래 이렇게 적혀 있었다: *"게이트웨이의 기본 4xx 게이트웨이 응답에 CORS 헤더가 붙게 한다
(`DEFAULT_4XX` / `DEFAULT_5XX` 게이트웨이 응답의 CORS 설정)"*. 그것은 **REST API(v1)** 의
기능(`aws_api_gateway_gateway_response`)이고, 이 API 는 `protocol_type = "HTTP"` 즉
**HTTP API(v2)** 다. v2 에는 그 손잡이가 **없다.** 실측: `main.tf` 의
`aws_apigatewayv2_api.api`. 🔵 그래서 「plan 만 내 보자」가 **불가능**했고, 그 사실이
plan 을 쓰려다 드러났다.

**남은 경로는 하나다: `$default` 라우트를 더해 람다가 404 를 내게 한다**(라우트가 매치되므로
`cors_configuration` 이 적용된다).

🔴🔴 **그리고 그것은 라우터 하드닝과 같은 PR 이어야 한다.** 지금 라우터는
`path.endswith(...)` 로 갈라진다:

```python
if path.endswith("/start"):
    return start()          # ← EC2 를 켠다
```

`$default` 가 모든 경로를 람다로 보내면 `POST /아무거나/start` 가 `start()` 에 **도달한다.**
지금은 **라우트 목록 자체가 방벽**이고, `$default` 는 그 방벽을 없앤다. 즉 이 변경은
「CORS 를 고친다」가 아니라 **「인증 없는 기동 경로를 연다」가 될 수 있다.**

- [x] 🔴 **라우터를 정확 일치로 먼저 바꾼다.** `endswith` → 정규화된 경로의 등호 비교.
      이것이 `$default` 의 **선행 조건**이다. 순서를 뒤집으면 그 사이에 구멍이 열린다.
- [x] 정확 일치로 바꾼 뒤 기존 라우트가 전부 그대로 도는지 시험으로 확인한다
      (`infra/demo/aws/tests/test_handler.py`, 현재 56칸).
- [x] 🔴 **bite**: `POST /x/start` 가 `start()` 에 도달하지 **않는** 것을 단언하는 칸을
      더한다. 지금 트리에서 그 칸은 «라우트가 없어서» 통과한다 — 그러니 **핸들러를 직접
      부르는** 시험이어야 한다(게이트웨이를 거치는 시험은 이 축을 못 잰다).
- [x] `$default` 라우트를 더한다. 존재하지 않는 경로의 응답에 `access-control-allow-origin`
      이 붙는다.
- [x] 🔴 `terraform plan` 을 내고 **무엇이 바뀌는지 적어라.**
- [ ] 🔴 **적용은 소유자 승인 사항이다**(`terraform apply` 는 상태 변경). plan 까지
      만들고 STOP 한 뒤 승인을 받아라.
- [x] 🔵 **하지 않기로 한 것을 적어라**: REST API 로 옮기는 것(반경이 크고 이 결함에
      비해 과하다). 그 판단의 근거를 남겨야 다음 사람이 다시 묻지 않는다.

## AC-2 — 론처가 «죽었다» 와 «그 라우트만 없다» 를 구별한다

- [x] `/status` 가 같은 주기에 200 이었으면 `/bundles` 실패를 **«API 가 죽음»으로 말하지
      않는다.** 새 상태가 필요하면 `TASK-MONO-636` 의 7-상태 표에 **행을 더해라**(지우지 말고).
- [x] 🔴 문구에 **«잠시 후 자동으로 다시 시도합니다» 를 쓰지 않는다** — 재시도로 안 풀리는
      상태다. 그 문장이 이 티켓의 거짓말이었다.
- [x] 🔴 반대 방향도 유지: 제어 API 가 **정말** 죽었을 때(`/status` 도 실패)는 기존 «응답하지
      않습니다» 가 그대로 나와야 한다. 두 상태를 갈라야지 한쪽으로 통일하면 안 된다.

## AC-3 — 가드

- [x] `infra/demo/verify-demo-wrapper.sh` 의 `(z34)` 시나리오에 **이 상태**를 더한다:
      «`/status` 200 + `/bundles` throw».
- [x] 🔴 **bite**: 이 분기를 지우면 가드가 빨개져야 한다. 대조군도 함께.
- [x] 🔴 `(z34)` 의 기존 7-상태가 여전히 통과하는지 확인한다(행을 더하는 것이지 바꾸는 게
      아니다).

## AC-4 — 라이브 확인

- [ ] 배포 후 `hubwang.com` 에서 `.bnote` 문구를 **다시 읽는다.** 파일이 바뀐 것과 방문자가
      보는 것이 바뀐 것은 다른 축이다.
- [ ] 🔴 AC-1 이 승인 대기로 막히면 AC-2 만으로도 문구는 바뀐다 — **그 상태를 측정하고
      적어라.** 「승인 대기중」은 미측정의 사유이지 측정의 대체가 아니다.

---

# Related Specs / Contracts

- `TASK-MONO-636` — 이 분기를 만든 티켓. **`review/` 에 있고 이 결함 때문에 닫히지 않았다**
- `TASK-MONO-634` — 7-상태 표(§ D5)의 출처
- [`ADR-MONO-071`](../../docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md) — 방문자가 고른 묶음을 올린다
- `infra/demo/aws/site/index.html` — `pollBundles()` · `B_ERR` · `bundleFallback()`
- `infra/demo/verify-demo-wrapper.sh` — `(z34)`

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| `/status` 200 · `/bundles` throw | 🔴 **이 티켓의 상태.** 「라우트 미배포(또는 차단)」로 말한다 |
| `/status` throw · `/bundles` throw | 기존 「응답하지 않습니다」 유지 — 이때는 정말 죽었다 |
| `/bundles` 200 | 정상 경로. 아무 문구도 안 뜬다 |
| `/bundles` 가 CORS 붙은 진짜 404 | `TASK-MONO-636` 의 `absent` 분기가 **드디어 도달 가능**해진다 |

---

# Failure Scenarios

1. **`terraform apply` 로 라우트를 배포하고 닫는다** → 증상이 안 보일 뿐 결함은 남는다.
   다음에 라우트가 하나 늘 때 똑같이 재발하고, 그때도 화면은 「응답하지 않습니다」라 한다.
2. **론처만 고친다** → 이 배포는 정직해지지만 게이트웨이는 여전히 4xx 를 CORS 없이 낸다.
3. **두 상태를 하나로 합친다** → 「제어 API 가 죽음」과 「라우트만 없음」은 방문자가 할 일이
   다르다(전자는 기다린다, 후자는 기다려도 소용없다).
4. **`/status` 도 죽은 경우를 안 본다** → 진짜 장애를 「라우트 미배포」로 신고한다.
5. **프리플라이트가 204 인 것을 «CORS 는 된다» 로 읽는다** → 이 결함이 숨은 이유가 그것이다.
6. 🔴🔴 **`$default` 를 라우터 하드닝 없이 넣는다** → 지금은 라우트 목록이 방벽이라 `POST /x/start` 가 게이트웨이에서 막힌다. `$default` 는 그 방벽을 없애고 `path.endswith("/start")` 가 `start()` 를 부른다 — **CORS 를 고치려다 인증 없는 기동 경로를 연다.**
7. **REST API 로 옮긴다** → 반경이 이 결함에 비해 과하다. 안 하기로 한 근거를 적어라.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** (인프라 축 + 상태기계 축이 붙어 있고, 「두 상태를 어떻게
가를 것인가」가 판단이다. 게이트웨이 전역 설정 변경은 반경이 넓다).

---

# 구현 기록 (2026-09-09 UTC)

## AC-0 재측정 — 결함은 `terraform apply` 뒤에도 그대로다

`Origin: https://hubwang.com` 을 붙여 다시 쟀다:

| 요청 | 상태 | `access-control-allow-origin` |
|---|---|---|
| `GET /status` | 200 | ✅ |
| `GET /bundles` | **200** ← 어제 apply 로 라우트가 생겼다 | ✅ |
| `GET /__no_such_route__` | **404** | 🔴 **없음** |
| `OPTIONS /__no_such_route__` | 204 | ✅ ← 이것이 결함을 숨긴다 |

🔴 즉 apply 는 **증상만 지웠고 결함은 남았다** — § Failure Scenarios 1 이 예고한 상태다.
AC-0 이 «200 이면 존재하지 않는 경로로 같은 축을 재라» 고 지시한 이유가 이것이다.

**브라우저 실측**(Chromium, 오리진 `https://hubwang.com`, 2026-09-09T06:47Z):

```
/status              ✅ 응답 도착 (status=200)
/bundles             ✅ 응답 도착 (status=200)
/__no_such_route__   🔴 fetch 가 던졌다 :: TypeError: Failed to fetch
브라우저가 신고한 실패: /__no_such_route__ :: net::ERR_FAILED
```

🔴 **curl 로는 재현되지 않는다** — curl 은 CORS 를 안 보므로 404 본문을 그대로 받고
«404 였다» 고 보고한다. 이 결함은 **브라우저에서만** 보인다.

## AC-1 — 라우터 하드닝이 먼저, `$default` 가 나중

### 라우터: `endswith` 사슬 → `(메서드, 경로)` 등호 표

`handler.py` 의 `_ROUTES` 는 `main.tf` 의 `aws_apigatewayv2_route.routes` 와 **같은 쌍**을 든다.

🔴🔴 **티켓이 적은 것보다 방벽이 하나 더 있었다.** 라우트 목록은 `"POST /start"` 였다 —
경로와 **메서드**를 함께 걸렀다. 그래서 경로만 등호로 바꾸면 `$default` 아래에서
`GET /start` 가 여전히 `start()` 에 도달한다. 링크 하나, `<img>` 하나, 프리페치 한 번이면
충분하다. 표가 메서드를 든 이유가 그것이다.

`_normalize()` 는 **끝의 `/` 하나만** 뗀다. 소문자화·`..` 해소 같은 관용은 하지 않는다 —
그런 관용은 표를 우회하는 **새 철자를 만들어 주는 쪽으로만** 작동한다.

### bite (핸들러를 **직접** 부른다)

`tests/test_handler.py::RouterIsExactTest` — 56칸 → **65칸**.

- **물 기회(대조군)**: `test_the_real_route_does_start_the_instance` — `POST /start` 가
  실제로 EC2 를 켠다. 이 칸이 없으면 나머지 bite 가 «아무것도 안 켜졌다» 로 조용히 통과한다.
- **bite 실측**: 옛 `endswith` 사슬을 되살렸더니 **22칸 빨강**. 그중
  `POST /x/start`·`/evil/start`·`/api/v2/start`·`//start`·`/bundle/x/start` 가 전부
  `start()` 에 도달했다 — **인증 없는 기동 경로**다. 대조군은 초록으로 남았다(그래야 맞다).
- 🔴 게이트웨이를 거치는 시험은 이 축을 못 잰다: 거기서는 라우트 목록이 여전히 막아 주므로
  사슬이 틀려도 초록이고, 그 초록의 뜻은 «라우터가 안전하다» 가 아니라 «아직 `$default` 를
  안 넣었다» 다.

### 배선 — 두 표 대조

`RouteTableMatchesTerraformTest` 가 `main.tf` 의 라우트 목록을 파싱해 `_ROUTES` 와 **집합
등호**로 비교한다. 🔴 빈(또는 얇은) 집합끼리는 서로 동의하므로 **10개 하한**을 같이 둔다.
`$default` 리소스의 실재도 이 클래스가 지킨다 — 지우면 없는 경로의 404 에 CORS 가 다시
안 붙고, 그러면 론처의 `r.status === 404` 분기가 다시 도달 불가가 된다.

### `terraform plan`

```
# aws_apigatewayv2_route.default will be created   (route_key = "$default")
# aws_lambda_function.control will be updated in-place
    ~ source_code_hash = "SXiiZEY9..." -> "vY3/UJsh..."
Plan: 1 to add, 1 to change, 0 to destroy.
```

🔵 **0 to destroy** — 인스턴스도 볼륨도 건드리지 않는다. AMI 재굽기의 apply 와는 **다른
변경**이다.

🔴🔴 **plan 이 드러낸 것 하나 — 순서는 apply 안에서도 load-bearing 이다.** `$default`
라우트는 통합(integration)에만 의존하고, 통합의 `invoke_arn` 은 람다 **코드**가 바뀌어도
그대로다. 그래서 terraform 은 «라우트 생성» 을 «람다 코드 갱신» 보다 먼저 해도 된다고
보고, 그 몇 초 동안 살아 있는 조합이 정확히 이 티켓이 막으려는 것이다(옛 라우터 +
`$default`). **티켓을 PR 하나로 묶는 것만으로는 이 창이 안 닫힌다** — `depends_on =
[aws_lambda_function.control]` 를 명시해 못 박았다.

🔴 **apply 는 소유자 승인 대기.** (AC-1 의 STOP 지시대로 plan 까지만 했다.)

### 안 하기로 한 것 — REST API(v1) 로 이전

`aws_api_gateway_gateway_response` 로 `DEFAULT_4XX` 에 헤더를 붙이는 것은 v1 기능이다.
v2 → v1 이전은 스테이지·배포·통합·권한이 **전부 다른 리소스**가 되고, 커스텀 도메인 매핑과
스로틀링 설정도 다시 쓴다. 결함 하나(없는 경로의 4xx 에 헤더가 없다)에 비해 반경이 과하고,
`$default` 가 같은 결과를 훨씬 좁은 반경으로 준다. 근거를 `main.tf` 주석에도 남겼다 —
다음 사람이 이 질문을 다시 하지 않도록.

## AC-2 — 론처가 두 상태를 가른다

`pollBundles()` 의 `catch` 가 이제 **그 자리에서 `/status` 를 직접 묻는다.**

🔴 근거를 «호출 위치» 에서 빌리지 않은 이유: `poll()` 이 `/status` 200 뒤에만
`pollBundles()` 를 부르는 것은 **지금** 참이지만, `bundleStart()` 의 `setTimeout` 도 부르고
그 경로엔 그 전제가 없다. 전제를 호출 위치에 두면 위치가 느는 날 조용히 깨지고, 그때 화면은
다시 틀린 사유를 말한다. ⇒ 실패 경로에서만 요청이 한 번 더 나간다(정상 경로는 그대로).

🔵 부작용이 하나 더 있다: 판정 전체가 `GUARD-Z34` 구간 **안**으로 들어와서, 가드가 배선까지
실행으로 잰다. 밖에 뒀다면 «`poll()` 이 그 사실을 정말 넘기는가» 는 아무도 안 봤을 것이다.

새 문구 `B_ERR.route` 는 🔴 **«잠시 후 자동으로 다시 시도합니다» 를 쓰지 않는다** — 그 문장이
이 티켓의 거짓말이었고, 이 상태는 재시도로 안 풀린다. 기존 `absent`/`down`/`shape` 는 **그대로
남는다**(행을 더한 것이지 바꾼 것이 아니다).

## AC-3 — 가드

`(z34)` 를 7시나리오 → **9시나리오**로 넓혔다(기존 7개는 그대로).

| 새 시나리오 | `/bundles` | `/status` | 기대 |
|---|---|---|---|
| `ENET` (기존, 의미 확정) | throw | **throw** | 「응답하지 않습니다」 — 정말 죽었다 |
| `ESTAT5` (신규·대조군) | throw | **503** | 같은 축 — 판정이 «던졌나» 가 아니라 «ok 인가» 를 읽는지 가른다 |
| `EROUTE` (신규·이 티켓) | throw | **200** | 「그 경로에 못 닿는다」 — 재시도로 안 풀린다 |

새 술어 넷: `EROUTE ≠ ENET` · `EROUTE ≠ E404` · `EROUTE` 에 **«잠시 후» 금지** ·
`ESTAT5 = ENET`. 대역은 `/status` 를 서빙하되 **모르는 경로는 여전히 죽인다**(관대한 대역 금지).

**bite-3** 상주: `alive ? "route" : "down"` 을 `"down"` 으로 뭉개면 가드가 빨개진다.

### 🔴🔴 앵커 접두사 충돌 — 같은 사실이 **세 곳**에 있었고 처음엔 한 곳만 고쳤다

`GUARD-Z34-BITE` 는 `GUARD-Z34-BITE3` 의 **접두사**다. 그래서 새 앵커를 더하는 순간
기존 bite-1 의 세 곳이 전부 틀린다:

| 자리 | 증상 |
|---|---|
| `grep -c 'GUARD-Z34-BITE'` (개수 단언) | 앵커가 2개로 세어져 **bite 를 돌리기도 전에** 죽는다 |
| `sed 's\|^.*GUARD-Z34-BITE.*$\|…\|'` (주입) | BITE3 줄까지 갈아엎어 `catch` 안이 `if (!r.ok) return;` 이 된다 |
| `grep -q 'GUARD-Z34-BITE'` (잔존 검사) | BITE3 를 찾아 「주입 실패」로 오진한다 |

🔴 나는 **개수 단언만 고치고 나머지 둘을 놓쳤다.** 첫 전체 실행이 그것을 잡았다:

```
FAIL: (z34) bite-1 실행 실패 — 변형이 문법을 깬 것이므로 이 빨강은 가드가 문 것이 아닙니다:
TypeError: Cannot read properties of undefined (reading 'ok')
```

🔵 **가드 설계가 여기서 값을 냈다.** bite 마다 ①주입 ②실행 ③물기를 **따로** 단언하기
때문에, 이 빨강이 「가드가 물었다」로 집계되지 **않았다.** 뭉쳐 있었다면 «bite 통과» 로
읽히고 bite-1 은 그때부터 아무것도 안 지키는 채로 남았을 것이다.
⇒ 세 곳 전부 `$` 로 줄 끝 고정.

## 검증

| 게이트 | 결과 |
|---|---|
| `python tests/test_handler.py` | **65칸 rc=0** (56 → 65) |
| bite (옛 `endswith` 사슬 되살리기) | **22칸 빨강** · 대조군은 초록 유지 |
| `verify-demo-wrapper.sh` 정적 전량 | **rc=0 · 86 ok · FAIL 0 · 「정적 검증 PASS」** |
| `bash -n verify-demo-wrapper.sh` | rc=0 |
| `terraform plan` | 1 add · 1 change · **0 destroy** |
| `check-index-queue-drift.sh` | rc=0 (스테이지 후) |
| `check-task-id-collision.sh` · `check-walkthrough-ledger-drift.sh` | rc=0 |

🔵 **잰 트리 = 커밋할 트리**를 따로 확인했다 — 래퍼 실행 중에 `deployed-ami.env` 를
잠깐 건드렸으므로, 래퍼가 그 파일을 읽는지(`grep -c` → **0**)와 변경 파일들의 mtime 이
실행 창 안인지를 둘 다 봤다.

## AC-4 — 라이브 확인

- 🔵 론처는 **머지 = 배포**(Vercel). AC-2 의 문구 변화는 apply 없이 머지만으로 측정 가능하다.
- 🔴 **미측정**: 머지 후 `hubwang.com` 의 `.bnote` 문구. AC-1 이 승인 대기이므로 그때
  보이는 것은 `route` 가 아니라 여전히 정상 경로(`/bundles` 200)일 가능성이 높다 —
  **그 사실 자체를 측정해서 적는다.** 「승인 대기중」은 미측정의 사유이지 측정의 대체가 아니다.
- 🔴 **미측정**: apply 후 `/__no_such_route__` 의 `access-control-allow-origin`. 이것이
  AC-1 의 최종 판정이고, 승인 없이는 잴 수 없다.
