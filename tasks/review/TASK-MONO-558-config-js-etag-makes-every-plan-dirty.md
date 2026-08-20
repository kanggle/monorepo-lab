# Task ID

TASK-MONO-558

# Title

`config.js` 의 etag 가 **다른 것을 재고 있어** terraform plan 이 영원히 더럽다 — 진짜 드리프트가 그 소음에 묻힌다

# Status

review

# Owner

monorepo

# Task Tags

- infra
- demo
- terraform

---

# ✅ 2026-08-20 UTC — AC-0·AC-2 완료, AC-1 은 측정으로 닫고 **apply 한 줄만 잔존**

## AC-0 재측정 — 결함은 그대로다. 그리고 **plan 은 1건이 아니라 2건**이었다

live state(`terraform.tfstate`, 마지막 apply 08-19 20:43)와 `terraform plan`(읽기 전용,
`-lock=false`, state **사본** 위에서 — 원본은 건드리지 않았다)으로 다시 쟀다.

산술은 티켓 기록과 **정확히 일치**한다. 티켓엔 잘린 endpoint 만 있었는데 이번엔 전체로 확인했다:

```
md5("window.DEMO_API_BASE = \"https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com\";\n")
  = 82e241f6e9ed4180b6784450c7638864   ← state 의 etag = S3 가 돌려준 값
md5("https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com")
  = 3b63e987fa7ba5986164c5411200415c   ← 선언이 주장하던 값
```

**그런데 plan 은 `2 to change` 였다** — 티켓이 예상한 1건이 아니다:

| 리소스 | etag 변화 | 성격 |
|---|---|---|
| `aws_s3_object.config` | `82e241f6…` → `3b63e987…` | 🔴 **구조적으로 수렴 불가** — 다른 것을 잰다 |
| `aws_s3_object.index` | `6c7f9fd4…` → `2f4c44af…` | ✅ **진짜 드리프트** — 올바르게 잰 결과다 |

`index` 는 결함이 아니다. `filemd5(로컬 site/index.html)` 을 직접 계산하니
**`2f4c44af…` 로 plan 의 new etag 와 정확히 일치**했다 — 즉 그 etag 는 **같은 것을 재고
있고**, 다만 로컬 파일이 마지막 apply 이후 실제로 바뀌었다(`d7797ac4f` = `TASK-MONO-561`,
론처 로그인 안내). S3 에는 **561 이전 페이지**가 올라가 있다.

🔴🔴 **그래서 이 티켓이 말한 피해가 이미 실현돼 있었다.** *"진짜 드리프트가 늘 있던 한 줄과
섞여 아무도 눈치채지 못한다"* — 지금 plan 이 정확히 그 모양이다. 영구 가짜 한 줄 옆에
**방문자에게 보이는 진짜 staleness** 가 나란히 앉아 있고, `2 to change` 를 흘려보면 둘 다 놓친다.

## AC-1 — 고침 후 plan: 가짜는 사라지고 **진짜는 그대로 잡힌다**

| | plan |
|---|---|
| 고침 전 | `0 to add, **2** to change, 0 to destroy` — config + index |
| **고침 후** | `0 to add, **1** to change, 0 to destroy` — **index 만** |

`terraform fmt -check` rc=0 · `terraform validate` Success.

🔴 **대조군을 조작하지 않았다.** AC-1 은 *"진짜 드리프트를 만들면 plan 이 여전히 잡아야
한다"* 고 요구했는데, **이미 실재하는 드리프트(index)** 가 그 역할을 했다. 이것이
`lifecycle { ignore_changes = [etag] }` 구현을 **정면으로 배제**한다 — 그 구현이었다면
index 도 함께 조용해졌을 것이다.

🔵 **plan 은 refresh 를 포함한다.** 이 결함이 무서웠던 이유가 *"apply 로 사라진 것처럼
보이다 refresh 에서 되살아난다"* 였는데, 그 refresh 를 거친 뒤 `config` 가 **plan 에서
사라졌다** ⇒ 영구 diff 기전은 끊겼다.

⏳ **문자 그대로의 `No changes` 는 apply 한 번이 남는다** — `index` 를 실제로 올려야 하고
`terraform apply` 는 **사용자 승인 대상**이다. 🔵 그 apply 는 덤으로 **561 의 론처 페이지를
S3 판에 올린다**(`TASK-MONO-557` AC-5 의 결정이 *병행* 이므로 S3/CloudFront 경로도 아직 산다).

## AC-2 — 형제 전수 (무엇을 어떻게 셌는지)

`main.tf` 를 파싱해 `aws_s3_object` 블록을 **전수 열거**하고 각 블록의 `content`/`source`/`etag` 를 뽑았다.

| 리소스 | 업로드되는 것 | etag 가 재는 것 | 판정 |
|---|---|---|---|
| `index` | `source = <site/index.html>` | `filemd5(<같은 파일>)` | ✅ **같은 것을 잰다** |
| `config` | `content = "…JS 한 줄…"` | `md5(api_endpoint)` | 🔴 **다른 것을 잰다** |

⇒ 전수 **2건 중 1건**이 결함. 다만 `index` 도 **경로 문자열을 두 번 적고** 있어 같은 종류의
어긋남이 생길 수 있으므로, 두 리소스 모두 `locals` 로 **출처를 하나로** 묶었다
(티켓이 요구한 *"두 자리가 같은 표현식을 참조하게"* 를 형제에도 적용).

---

# 배경 — 2026-08-19(UTC) `TASK-MONO-557` apply 중에 발견

Vercel 오리진을 여는 apply 의 plan 이 **3건**이었는데, 내가 예상한 건 2건이었다.
그 어긋난 한 칸이 이 티켓이다.

```
Plan: 0 to add, 3 to change, 0 to destroy
  ~ aws_apigatewayv2_api.api        ← 의도한 것 (CORS 오리진 추가)
  ~ aws_lambda_function.control     ← 의도한 것 (ALLOWED_ORIGIN 제거)
  ~ aws_s3_object.config            ← 🔴 예상에 없던 것
```

## 🔴 두 etag 가 서로 **다른 것을 잰 값**이다

```
old etag  82e241f6e9ed4180b6784450c7638864
new etag  3b63e987fa7ba5986164c5411200415c
```

산술로 확인했다:

```
md5("window.DEMO_API_BASE = \"https://r1tljg51qa…\";\n")  = 82e241f6…   ← old = 파일 내용
md5("https://r1tljg51qa…")                                = 3b63e987…   ← new = api_endpoint
```

`main.tf` 의 선언(L471·474):

```hcl
content = "window.DEMO_API_BASE = ${jsonencode(aws_apigatewayv2_api.api.api_endpoint)};\n"
etag    = md5(aws_apigatewayv2_api.api.api_endpoint)
```

**`content` 는 그 문자열을 감싼 JS 한 줄인데 `etag` 는 감싸기 전 URL 의 md5 다.**
S3 가 돌려주는 ETag 는 언제나 **객체 내용의 md5** 이므로, 이 둘은 **구조적으로 절대
일치할 수 없다.**

## 영구 반복임을 확정했다 (추론이 아니라 측정)

apply 를 하면 사라지는 일회성 드리프트일 가능성이 있어서 **apply 직후 plan 을 다시 돌렸다**:

| 시점 | 결과 |
|---|---|
| apply 전 | `0 to add, 3 to change, 0 to destroy` |
| **apply 직후** | `0 to add, **1 to change**, 0 to destroy` — 같은 etag, 같은 두 값 |

apply 가 성공해도 refresh 가 state 의 etag 를 **S3 가 준 `md5(내용)`** 으로 되돌리고,
설정은 계속 `md5(endpoint)` 를 주장한다. ⇒ **모든 plan 이 영원히 1건 더럽다.**

## 🔴 왜 이게 소음 이상인가

`plan` 이 **항상** 무언가를 바꾸겠다고 말하면, *"바꿀 게 있다"* 는 신호가 정보를 잃는다.
진짜 드리프트(누가 콘솔에서 CORS 를 만졌다든지, 다른 apply 가 밀렸다든지)가 생겨도
**그 한 줄이 늘 있던 한 줄과 섞여** 아무도 눈치채지 못한다.

이 저장소가 이미 이름 붙인 실패 모드다 — *늘 빨간 신호는 늘 초록인 신호와 같은 값을
한다: 아무도 안 본다*(`TASK-MONO-556` 배경 · `TASK-MONO-360`). 여기서는 그것이
**늘 더러운 plan** 의 모양으로 나타난다.

🔵 **지금 당장 무언가 고장난 것은 아니다.** apply 는 같은 내용을 다시 올릴 뿐이고
페이지도 정상이다. 고쳐야 하는 것은 **판정 능력**이다.

---

# Goal

`terraform plan` 이 변경이 없을 때 **`No changes`** 를 말한다. 그래서 다음에 plan 이
무언가를 보고하면 그것이 **실제 드리프트**를 뜻한다.

# Scope

## In Scope

- `infra/demo/aws/terraform/main.tf` 의 `aws_s3_object.config` — `etag` 가 **업로드되는
  바로 그 내용**의 md5 가 되게 한다. 예:

  ```hcl
  locals {
    config_js = "window.DEMO_API_BASE = ${jsonencode(aws_apigatewayv2_api.api.api_endpoint)};\n"
  }
  resource "aws_s3_object" "config" {
    content = local.config_js
    etag    = md5(local.config_js)   # 같은 문자열을 잰다
  }
  ```

  🔴 **두 자리가 같은 값을 참조하게 만드는 것이 요점**이다. 지금처럼 두 표현식이 각자
  존재하면, 한쪽만 바뀌는 날 또 갈린다(이 결함이 정확히 그 형태다).

- 같은 파일 안의 **형제 점검**: 다른 `aws_s3_object`(예: `index`)가 같은 실수를 하고
  있는지. `index` 는 `etag = filemd5(...)` 라 내용과 일치할 가능성이 높지만 **읽고 확인할 것**.

## Out of Scope

- `config.js` 의 **내용**이나 주입 방식 변경 — `TASK-MONO-557` 이 정한 대로 둔다.
- Vercel 쪽 `build.sh` 의 렌더 — 거긴 S3 가 아니라 etag 개념이 없다.
- terraform 전반의 다른 perpetual diff 사냥. **이 한 건만** 고친다(AC-2 가 그 경계다).

---

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act). ✅ 완료 (2026-08-20 UTC) — 결함 그대로 재현, 그리고 plan 은 1건이 아니라 2건이었다(위 § AC-0).** 착수 시점에 `terraform plan` 을 돌려 **여전히
`aws_s3_object.config` 1건이 더러운지** 확인한다. 이미 깨끗하면 누군가 고친 것이므로
phantom 으로 기록하고 **건드리지 않는다**.
🔴 두 etag 값이 각각 무엇의 md5 인지도 **그때 다시 산술로** 확인할 것 — `api_endpoint` 가
바뀌었으면 두 값이 다 달라진다.

**AC-1 — plan 이 `No changes` 를 말한다. 🔴 대조군 필수. ◑ 측정 완료 · apply 한 줄 잔존 — 고침 후 plan 에서 `config` 가 사라지고 진짜 드리프트(`index`)는 그대로 잡힌다(위 § AC-1). 문자 그대로의 `No changes` 는 `terraform apply`(사용자 승인 대상)가 필요하다.**
고친 뒤 `terraform apply` → **곧바로 `terraform plan` 재실행** → `No changes`.
**apply 직후 한 번만 보고 닫지 말 것** — 이 결함은 apply 로 사라지는 것처럼 보였다가
refresh 에서 되살아난다(그게 이 티켓의 발견 경로다).

**그리고 대조군**: 진짜 드리프트를 만들면 plan 이 **여전히 잡아야 한다**. 예를 들어
S3 의 `config.js` 를 콘솔/CLI 로 다른 내용으로 덮은 뒤 plan 이 그것을 보고하는지 확인한다.
🔴 대조군 없이 `No changes` 만 보면 *"etag 를 아예 안 보게 만들어서 조용해진"* 구현과
구별되지 않는다 — 그 구현은 **진짜 드리프트도 못 본다**. `ignore_changes` 로 덮는 안이
정확히 그것이므로 **채택하지 말 것**.

**AC-2 — 형제를 세라. ✅ 완료 — 전수 2건 중 1건 결함, 방법과 결과는 위 § AC-2.** `main.tf` 의 `aws_s3_object` 전수를 읽고 각각의 `etag` 가
**업로드되는 내용과 같은 것을 재는지** 확인해 결과를 적는다. 0건이면 0건이라고 적되
**무엇을 어떻게 셌는지** 함께.

# Related Specs

- `infra/demo/aws/terraform/main.tf` L465~478 — `aws_s3_object.config` (결함의 자리)
- `TASK-MONO-389` — `config.js` 를 terraform 이 렌더하게 만든 티켓(이 자리를 만든 곳)
- `TASK-MONO-557` — 이 결함이 발견된 apply

# Related Contracts

없음 (데모 인프라 전용).

# Edge Cases

- **`api_endpoint` 가 바뀌면** 두 md5 가 **둘 다** 달라진다. 그래도 서로는 여전히 안 맞는다 —
  값이 아니라 **재는 대상**이 어긋난 것이기 때문이다.
- **`content` 끝의 개행**까지 md5 에 들어간다. 두 자리가 같은 문자열을 참조하지 않고
  손으로 옮겨 적으면 개행 하나로 다시 갈린다 — 그래서 `local` 로 묶는 것이 안이다.
- **S3 버저닝**이 켜져 있으면 apply 마다 새 버전이 쌓인다(지금 plan 에 `version_id` 가
  보인다). 영구 diff 는 그 쓰레기도 매 apply 마다 늘린다.

# Failure Scenarios

- **`lifecycle { ignore_changes = [etag] }` 로 덮는다** — plan 은 조용해지지만 **진짜
  드리프트도 안 보인다.** AC-1 의 대조군이 이것을 막는다.
- **apply 직후 한 번만 보고 닫는다** — 이 결함은 그 순간 사라진 것처럼 보인다.
- **etag 를 지운다** — provider 가 알아서 관리하게 되지만, *"내용이 바뀌면 올린다"* 는
  의도가 사라진다. 지우려면 그게 의도임을 적을 것.

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Sonnet** — 변경은 몇 줄이고 위험도 낮다.
  다만 **AC-1 의 대조군**(진짜 드리프트는 여전히 잡히는가)만은 대충 하면 안 된다.
- 선행: 없음. `TASK-MONO-557` 의 apply 가 이 결함을 **드러냈을 뿐** 원인이 아니다 —
  결함은 `TASK-MONO-389` 때부터 있었고 그동안 아무도 plan 을 두 번 돌리지 않았다.
- 🔵 **발견 경로 자체가 교훈**: *"예상은 2건인데 plan 이 3건"* 이라는 **개수 불일치**가
  단서였다. 계획한 변경 수를 미리 적어 두지 않았다면 셋째 줄은 그냥 지나갔을 것이다.
