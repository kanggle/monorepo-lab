# Task ID

TASK-MONO-558

# Title

`config.js` 의 etag 가 **다른 것을 재고 있어** terraform plan 이 영원히 더럽다 — 진짜 드리프트가 그 소음에 묻힌다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- terraform

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

**AC-0 — 재확인 (verify-then-act).** 착수 시점에 `terraform plan` 을 돌려 **여전히
`aws_s3_object.config` 1건이 더러운지** 확인한다. 이미 깨끗하면 누군가 고친 것이므로
phantom 으로 기록하고 **건드리지 않는다**.
🔴 두 etag 값이 각각 무엇의 md5 인지도 **그때 다시 산술로** 확인할 것 — `api_endpoint` 가
바뀌었으면 두 값이 다 달라진다.

**AC-1 — plan 이 `No changes` 를 말한다. 🔴 대조군 필수.**
고친 뒤 `terraform apply` → **곧바로 `terraform plan` 재실행** → `No changes`.
**apply 직후 한 번만 보고 닫지 말 것** — 이 결함은 apply 로 사라지는 것처럼 보였다가
refresh 에서 되살아난다(그게 이 티켓의 발견 경로다).

**그리고 대조군**: 진짜 드리프트를 만들면 plan 이 **여전히 잡아야 한다**. 예를 들어
S3 의 `config.js` 를 콘솔/CLI 로 다른 내용으로 덮은 뒤 plan 이 그것을 보고하는지 확인한다.
🔴 대조군 없이 `No changes` 만 보면 *"etag 를 아예 안 보게 만들어서 조용해진"* 구현과
구별되지 않는다 — 그 구현은 **진짜 드리프트도 못 본다**. `ignore_changes` 로 덮는 안이
정확히 그것이므로 **채택하지 말 것**.

**AC-2 — 형제를 세라.** `main.tf` 의 `aws_s3_object` 전수를 읽고 각각의 `etag` 가
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
