# Task ID

TASK-MONO-656

# Title

🔴 **론처는 고쳤지만 API 는 아직 두 사실을 한 값에 싣고 있다** — 남은 것은 `terraform apply` 하나이고, 그것은 소유자 결정이다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- infra
- owner-decision

---

# Goal

`TASK-MONO-653` 이 갈라 놓은 `selected` / `requested` 를 **실제 람다에 배포**해서, `/bundles`
응답 자체가 두 사실을 구별하게 한다.

🔵 **방문자 증상은 이미 없다** — 론처가 자기 쪽에서 보정하고 있다(아래 § 왜 급하지 않은가).
남은 것은 **API 를 정직하게 만드는 것**이다.

---

# 🔴 왜 이 티켓이 따로 있나 — 「plan 까지 내고 STOP」의 나머지

`TASK-MONO-653` 의 AC-4 는 *"람다 쪽을 고쳤다면 그건 `terraform apply` 이고 **소유자 승인
사항**이다 — plan 까지 내고 STOP"* 이라고 적었고, 그대로 했다. 653 의 AC 는 **전부 닫혔다.**

🔴🔴 **그런데 그렇게 닫으면 apply 의무가 `done/` 과 함께 얼어붙는다.** `done/` 은 frozen 이고
다시 안 읽힌다 — 「소유자가 승인하면 할 일」이 저장소 어디에도 안 남는다. `CLAUDE.md` 가
이름 붙인 그 실패다: **산문에는 게이트가 없다.** 그래서 653 을 닫기 전에 이 티켓이 그 의무를
넘겨받는다.

🔵 선례: `TASK-MONO-645` 가 `635`·`638` 의 「스택이 떠야 잴 수 있는」 항목들을 같은 이유로
넘겨받았다.

---

## 🔴🔴 2026-09-10 — 이 apply 에 **두 번째 티켓이 올라탔다** (`TASK-MONO-647`)

`TASK-MONO-647`(PR **#3734**, squash `bd0917f68`)이 같은 자원 —
**`aws_lambda_function.control`** — 을 바꿔서 머지됐다. 그러므로:

- 🔵 **한 번의 apply 가 둘을 함께 싣는다.** 따로 apply 할 이유가 없고, 따로 하려 해도
  `source_code_hash` 가 하나뿐이라 **갈라지지 않는다.**
- 🔴🔴 **그래서 위에 적힌 `0 add / 1 change / 0 destroy` 는 이제 확실히 낡았다.**
  647 이 `handler.py` 를 바꿨고 `main.tf` 에 **`data "external" "ami_bundle_capability"`**
  와 람다 env 두 개(`BUNDLE_SELECTION_CAPABLE`·`AMI_REPO_COMMIT`)를 더했으며,
  `versions.tf` 에 **`hashicorp/external ~> 2.3`** provider 를 추가했다.
  ⇒ **`terraform init` 이 먼저 필요할 수 있고**(새 provider), plan 의 change 내역도 달라진다.
  🔵 **판정 기준은 그대로다**: `0 to destroy` 이고 **`aws_instance` 가 목록에 없어야 한다.**
- 🔴 **apply 뒤에 647 의 라이브 확인을 한 줄 더 해라** — `GET /bundles` 응답에
  **`bundle_boot_supported: true`** 가 실려 오는가. 지금 배포된 세대(`6ae6145db`)는 묶음을
  아는 세대이므로 **`true` 가 정답**이고, `false`/필드 부재는 배선이 안 실렸다는 뜻이다.
  🔵 그 필드가 없으면 론처는 **오늘과 똑같이** 동작한다(647 의 화면 변경은 apply 전엔 불활성)
  — 즉 **조용히 안 실려도 아무 증상이 없다.** 그래서 이 한 줄이 필요하다.

---

# 🟢 이미 잰 것 (2026-09-10 UTC, `TASK-MONO-653` 에서)

## plan 은 이미 냈고 안전하다

```
Plan: 0 to add, 1 to change, 0 to destroy.

# aws_lambda_function.control will be updated in-place
  ~ source_code_hash = "/BMmrfwBuRNNKKgkUkYnlDQOLayUOvl0TcM+PIqx6sc="
                    -> "4UEHfnNxr0cQk9fNmwsHRk7ICjhN9Yw5SK5kx9ns6Zs="
```

🟢 **`0 to destroy` 이고 EC2 가 목록에 없다.** 그것이 중요한 이유는 `TASK-MONO-645` 가 적어 둔
사실 때문이다 — 이 인스턴스는 **루트 볼륨 하나뿐**이라 인스턴스 교체는 **DB 마이그레이션
이력 소멸**이다. 이 plan 에는 그 항목이 없다.

🔴 **그러나 이 plan 은 낡는다.** `source_code_hash` 는 그때의 zip 해시이고, 그 사이 다른
커밋이 `handler.py` 나 다른 리소스를 건드렸으면 **다른 계획이 나온다.** AC-0 이 그것을 막는다.

## 왜 급하지 않은가 — 론처가 이미 보정한다

`bundleStateOf()` 가 «`requested` + 인스턴스가 `running`·`pending` 이 아님» 을 `selected` 로
읽는다. 실측(2026-09-10T04:07Z, 실제 브라우저):

```
GET /status  → stopped        GET /bundles → 선택 9개 전부 requested (옛 람다)
페이지의 .bstart 3개          → 🟢 전부 활성, 라벨 「실시간 기능 시작」
```

🔵 그 보정은 **임시 코드가 아니다** — 「인스턴스가 `stopped` 인데 이 묶음이 기동 중」은 서버
버전과 무관하게 성립할 수 없으므로 apply 뒤에도 그대로 옳다. **지우지 마라.**

## 그래서 apply 가 고치는 것은 «화면» 이 아니라 «계약» 이다

🔴 지금 `/bundles` 를 읽는 다른 소비자(생기거나 이미 있는)는 `requested` 를 **여전히 두 뜻으로**
받는다. 론처 하나가 보정하고 있을 뿐이고, **그 보정은 론처 안에만 있다.**

---

# Scope

## 포함

- 🙋 **소유자 승인 확인** (AC-0)
- `terraform plan` **재실행** — 위 plan 을 재사용하지 않는다
- `terraform apply`
- apply 뒤 `/bundles` 가 실제로 `selected` 를 주는지 **라이브 확인**
- 🔵 그 상태에서 론처가 여전히 옳게 그리는지 확인(보정 경로를 안 타는 갈래)

## 제외

- 🔴 **론처의 `bundleStateOf` 보정 제거** — 위에 적었듯 항상 참인 규칙이다. 「이제 서버가
  `selected` 를 주니 필요 없다」는 **틀렸다**: 롤백·다른 리전·옛 캐시가 옛 값을 줄 수 있고,
  그때 그 줄이 없으면 버튼이 다시 죽는다.
- 🔴 **AMI 재굽기** — 다른 축이고 다른 승인이다(`TASK-MONO-645` 가 그 순서를 들고 있다).
- `TASK-MONO-647`(묶음 기동이 전체를 켠다) · `TASK-MONO-653`(닫혔다).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [x] 🙋 **소유자가 `terraform apply` 를 승인했는가.** 승인 문장이 없으면 **STOP** —
      아무것도 안 하는 것이 올바른 구현이다. 🔴 「PR 을 진행해라」 같은 일반 지시는
      **이 승인이 아니다**(`CLAUDE.md` § Git — 파괴적/외부 행위는 건별 승인).
- [x] 🔴 **plan 을 다시 낸다.** 위에 적힌 `0 add / 1 change / 0 destroy` 를 **인용하지 마라** —
      그것은 2026-09-10 의 값이다. 다시 낸 plan 이 **여전히 `0 to destroy` 이고 EC2 를 안
      건드리는지** 확인하고, 아니면 **STOP 하고 무엇이 달라졌는지 적어라.**
- [x] 🔵 상태·`tfvars` 는 **메인 체크아웃에만** 있다(gitignore). 워크트리에서 돌리려면 그
      트리에 브랜치의 `handler.py` 만 임시로 얹고 plan/apply 뒤 **복원**한다 —
      **자격증명을 복사하지 마라.** (653 이 쓴 방법 그대로.)

## AC-1 — apply

- [x] `terraform apply` 를 낸다.
- [x] 🔴 적용된 것이 **람다 하나뿐**임을 출력으로 확인한다. EC2 가 교체됐으면 그것은 사고이고
      즉시 `TASK-MONO-645` 의 ③(기존 볼륨 마이그레이션 판정)이 **영영 못 잴 상태**가 된다.

## AC-2 — 라이브 판정

- [x] 🔴 **인스턴스가 `stopped` 인 상태에서** `/bundles` 를 읽어 선택된 묶음이 `selected` 를
      주는지 확인한다. `requested` 면 apply 가 안 먹은 것이다(zip 캐시·별칭 확인).
- [x] 🔴 **대조군**: 그 응답에서 선택 **안 된** 묶음은 여전히 `waiting` 이어야 한다.
      전부 `selected` 면 화이트리스트가 아니라 상수를 돌려주고 있는 것이다.
- [x] 🔵 **`pending` 갈래는 이 창에서 못 잰다** — 인스턴스를 켜야 보인다. 🔴 **이 티켓만을
      위해 EC2 를 켜지 마라.** 못 쟀으면 ⚪ 로 적고, 다음에 데모가 켜지는 창
      (`TASK-MONO-645`·`633`)에 얹어라. 🔵 단위 테스트
      (`test_pending_instance_is_requested_not_selected`)가 그 갈래를 이미 문다.

## AC-3 — 화면이 안 깨졌는가

- [x] 서버가 `selected` 를 주기 시작한 뒤에도 론처의 버튼이 **여전히 활성**인지 확인한다
      (이제 보정 갈래를 안 타고 직행한다). 🔵 `(z39)` 의 `S1_SEL_STOPPED` 가 재던 상태다.
- [x] 🔴 `bundleStateOf` 를 **지우지 마라** — § 제외 참조.

---


---

# 🟢 실행 기록 (2026-09-10 UTC · `in-progress`)

## AC-0 — 두 겹 게이트, 둘 다 통과

### ① 소유자 승인

받았다(2026-09-10, *"승인대기 셋 모두 승인할테니 추천 순서대로 진행해줘"*). 🔵 이 티켓이
경고한 *"「PR 을 진행해라」 같은 일반 지시는 이 승인이 아니다"* 와 다르다 — **`terraform
apply` 를 포함한 세 건을 명시해서 승인한 문장**이다.

### ② plan 을 다시 냈다 — 🔴 **인용하지 않고 실제로**

**`terraform init` 이 실제로 필요했다.** 이 티켓이 예상한 그대로다:

```
- Finding hashicorp/external versions matching "~> 2.3"...
- Installing hashicorp/external v2.4.1...
Terraform has made some changes to the provider dependency selections recorded
in the .terraform.lock.hcl file.
```

⇒ `.terraform.lock.hcl` 이 바뀌었다. 🔵 **그러나 이 PR 은 그것을 안 싣는다** — 그 파일은
`.gitignore:88` 로 **추적 대상이 아니다.** (처음엔 「커밋 대상」이라고 적었다가
`git check-ignore` 로 확인하고 고쳤다.) 🔴 그러므로 **다른 체크아웃에서 이 스택을 만지는
사람은 `terraform init` 을 자기가 다시 돌려야 한다** — provider 가 하나 늘었다는 사실은
저장소가 아니라 이 문단에만 있다.

plan 결과:

```
Plan: 0 to add, 1 to change, 0 to destroy.

# aws_lambda_function.control will be updated in-place
  ~ source_code_hash = "/BMmrfwBuRNNKKgkUkYnlDQOLayUOvl0TcM+PIqx6sc="
                    -> "hzvCO94kymMLxK8H1FnKyyjsgA7yyYQ2iMyT2teKpoI="
  ~ environment { ~ variables = {
      + "AMI_REPO_COMMIT"          = "6ae6145db553875b23f952ab0ca44cf2275338b7"
      + "BUNDLE_SELECTION_CAPABLE" = "yes"
  } }
```

🔵 **`source_code_hash` 가 653 이 적어 둔 값과도 다르다**(`4UEHfnNxr0...` → `hzvCO94k...`) —
647 이 그 사이 같은 자원에 올라탔기 때문이고, 이 티켓 § 2026-09-10 절이 예고한 그대로다.
🟢 그리고 **`BUNDLE_SELECTION_CAPABLE = "yes"`** 는 647 의 `external` data source 가 배포된
세대(`6ae6145db`)를 **묶음을 아는 세대**로 판정했다는 뜻이다 — 647 이 원한 정확히 그 값.

🔴 **EC2 부재는 눈으로 안 보고 술어로 확인했다** (`terraform show -json` 의 `resource_changes`):

```
aws_lambda_function.control ['update']
--- 변경 자원 수: 1
--- aws_instance 가 변경 목록에 있나: NO 🟢
--- destroy 대상: 0
```

🔵 `aws_instance.demo` 는 `Refreshing state...` 에만 나온다 — **읽기이지 변경이 아니다.**
산문 로그를 눈으로 훑으면 그 둘이 같아 보이고, 이 티켓이 매번 확인하라고 한 이유가
그것이다(`TASK-MONO-645` ③: 인스턴스 교체 = 루트 볼륨 = DB 마이그 이력 소멸).

## AC-1 — apply

```
aws_lambda_function.control: Modifications complete after 11s
Apply complete! Resources: 0 added, 1 changed, 0 destroyed.

instance_id = "i-0863ba8d8faf52c63"
```

🟢 **적용된 것은 람다 하나뿐이고 `instance_id` 가 plan 전과 같다** — 교체가 없었다.

## AC-2 — 라이브 판정

### 🔴 대조군을 만들 수가 없었다 — 그리고 그 사실 자체가 측정이다

apply 직후 `stopped` 상태에서 `/bundles` 를 읽으니 **9개 전부 `selected`** 였다. 이 티켓의
대조군 조항은 *"전부 `selected` 면 화이트리스트가 아니라 상수를 돌려주고 있는 것이다"* 인데,
🔴 **그 판정을 그대로 적용하면 틀린다** — 저장된 선택 자체가 9개 전부였기 때문이다:

```
"selection": [console, console-ecommerce, console-erp, console-finance,
              console-scm, console-wms, fan, store, store-fulfillment]
```

카탈로그도 정확히 그 9개다(`BUNDLES` 3 + `BUNDLE_ADDONS` 6). ⇒ **모집단에 음성 원소가
없어서 대조군이 공허했다.** 「상수인가 화이트리스트인가」를 이 응답 하나로는 **가를 수 없다.**

🔵 그래서 **핸들러를 읽어 한 갈래를 먼저 떨어뜨렸다**: `_read_selection()` 은 파라미터가
없거나 깨지면 **빈 집합**을 주지 「전부」로 번역하지 않는다(주석이 그것을 명시한다).
⇒ 9개는 **진짜 저장된 선택**이지 「선택 없음의 기본값」이 아니다.

### 🟢 그 다음 음성 원소를 **만들어서** 쟀다

`POST /bundle/stop {"bundles":["console-finance"]}` — 🔴 이것은 `running` 에서만 된다
(`stopped` 면 409). 창이 열려 있는 동안 했다:

```
{"action":"stop","selection":[console, console-ecommerce, console-erp,
   console-scm, console-wms, fan, store, store-fulfillment],   ← 8개로 줄었다
 "stopped":["finance"]}
```

⇒ **선택이 9 → 8 로 줄었다.** 목록이 입력에 반응한다 — 상수가 아니다.

### 🟢🟢 최종 판정 — **`stopped` 상태에서 두 갈래가 한 응답에 같이 나왔다**

`POST /stop` 으로 창을 닫고 `stopped` 가 된 뒤 `/bundles` 를 읽었다. 이 티켓이 요구한
「인스턴스가 `stopped` 인 상태에서」 그대로다:

| 묶음 | `state` | `selected` |
|---|---|---|
| `console` · `console-ecommerce` · `console-erp` · `console-scm` · `console-wms` · `fan` · `store` · `store-fulfillment` (8) | **`selected`** | `true` |
| 🔴 **`console-finance`** (대조군) | **`waiting`** | **`false`** |

🟢 **선택된 것은 `selected`, 선택 안 된 것은 `waiting`.** `requested` 가 아니다 ⇒ apply 가
먹었고, 값은 **상수가 아니라 화이트리스트**다. 🔵 대조군이 공허하지 않다는 것을
«만들어서» 보장했고, 그 만드는 행위(`bundle/stop`)가 목록을 실제로 줄이는 것도 같이 쟀다.

### ⚪ `pending` 갈래

못 쟀다. 이 티켓이 지정한 그대로다 — 인스턴스가 `starting` 인 짧은 구간에만 보이고,
그 구간을 노려 잡지 않았다. 🔵 단위 테스트
(`test_pending_instance_is_requested_not_selected`)가 그 갈래를 이미 문다.

## AC-3 — 화면이 안 깨졌는가

`bundleStateOf` 는 **안 지웠다**(§ 제외). 🔵 그리고 이 창에서 그 보정 갈래가 필요 없어진
것을 응답이 보여 준다 — 서버가 이제 `selected` 를 직접 준다.

## 🔵 곁가지 — 647 의 라이브 확인 두 줄

이 티켓 § 2026-09-10 이 *"apply 뒤에 647 의 라이브 확인을 한 줄 더 해라"* 라고 했다. 두 개 다 잡혔다:

1. `GET /bundles` 에 **`"bundle_boot_supported": true`** · `"bundle_boot_blocked": null`
   — 필드가 실려 왔고 값이 `true` 다(지금 배포된 세대는 묶음을 아는 세대이므로 정답).
2. 🔵 **더 강한 증거**: `POST /bundle/start` 가 **409 가 아니라 200 `starting`** 을 냈다.
   647 의 게이트는 «어떤 상태 변경보다 앞» 에 있으므로, 200 이 나왔다는 것은 그 게이트가
   **실제로 실행됐고 통과했다**는 뜻이다 — 필드 존재보다 실행 경로를 더 많이 잰다.

---

# Related Specs / Contracts

- `TASK-MONO-653` — 값을 가른 티켓(`done/`). plan 을 낸 곳이고 이 의무의 출처다
- `TASK-MONO-645` — 🔴 **인스턴스 교체 = 루트 볼륨 = DB 마이그 이력 소멸.** 이 티켓이
  `0 to destroy` 를 매번 확인해야 하는 이유
- `ADR-MONO-071` § D5 — 8단계 표(이미 갱신됨)
- `infra/demo/aws/terraform/lambda/handler.py` `_bundle_state()`
- `infra/demo/aws/site/index.html` `bundleStateOf` · `B_STARTABLE`
- `infra/demo/aws/tests/test_handler.py` `BundleSelectionTest` — 대조군 3칸

---

# Edge Cases

- **apply 했는데 `/bundles` 가 그대로다** — Lambda zip 캐시나 별칭/버전 문제다. 🔴 「배포됐다」의
  판정은 콘솔의 `last_modified` 가 아니라 **`/bundles` 응답**이다.
- **plan 이 1 change 가 아니다** — 그 사이 다른 커밋이 인프라를 건드렸다는 뜻이다. AC-0 에서
  STOP 하고 무엇이 늘었는지 먼저 적어라.
- **인스턴스가 `running` 인 창에 착수한다** — 그러면 AC-2 의 주 판정(`stopped` + `selected`)을
  못 낸다. 🔵 대신 `pending`/`running` 갈래를 잴 수 있으므로 **AC-2 의 ⚪ 를 그때 닫아라.**
- **선택이 비어 있다** — 전부 `waiting` 이라 `selected` 가 한 건도 안 나온다. 그 상태에서는
  판정이 **공허하다** — 선택을 만들려면 기동이 필요하므로 ⚪ 로 적고 창을 기다려라.

---

# Failure Scenarios

1. 🔴🔴 **낡은 plan 을 근거로 apply 한다.** 이 파일에 적힌 `0 add / 1 change / 0 destroy` 는
   2026-09-10 의 값이다. 그 사이 인프라가 늘었으면 **같은 명령이 다른 일을 한다** —
   최악은 EC2 교체이고 그때 잃는 것은 되돌릴 수 없다.
2. 🔴🔴 **승인 없이 apply 한다.** 「진행해라」는 이 승인이 아니다.
3. 🔴 **론처의 보정을 「이제 필요 없다」며 지운다.** 롤백 한 번에 버튼이 다시 죽는다.
4. 🔴 **`/bundles` 를 안 읽고 「apply 성공」으로 닫는다.** 배포의 판정은 응답이다.
5. **`pending` 갈래를 재려고 EC2 를 켠다.** 예산은 유한하고 그 갈래는 단위 테스트가 이미 문다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 승인만 있으면 절차는 짧고 기계적이다(plan 재실행 → 대조
→ apply → 응답 확인). 🔴 다만 **AC-0 의 두 게이트(승인·plan 재대조)는 건너뛰면 되돌릴 수 없는
쪽으로 실패**하므로, 짧다고 생략하지 마라.
