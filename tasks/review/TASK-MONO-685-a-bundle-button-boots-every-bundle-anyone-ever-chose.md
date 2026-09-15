# Task ID

TASK-MONO-685

# Title

묶음 버튼 하나가 **지금까지 누가 고른 모든 묶음**을 올린다 — 그리고 켜지 않은 묶음이 «일부만 실행 중» 으로 보인다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- control-plane
- adr

---

# Goal

소유자 확인 요청(2026-09-15): *"데모 켜기가 각 도메인 별로 해당 도메인에 필요한 것만 동작시키는지 확인"* ·
*"운영자 콘솔 데모켜기만 해도 이커머스 스토어와 팬플랫폼에서도 데모켜기 버튼이 활성화되는 것 같은데 확인해봐"* →
조사 결과 두 결함, 소유자 승인 「진행」.

## ① 선택이 세션을 넘어 쌓인다

카드→도메인 **매핑은 옳다**(fan→fan+iam · store→ecommerce+iam · console→console+iam, Lambda·`projects.sh` 두 표 일치).
그런데 `POST /bundle/start` 는 선택에 **합집합으로 더하기만** 하고, 빼는 경로는 론처에 버튼이 없는 `/bundle/stop` 뿐이며,
`stop()`·`idle_check()` 는 인스턴스만 끈다. 다음 부팅은 쌓인 선택 **전체**를 읽는다(`demo-boot.sh selection`).

라이브 실측(2026-09-15 10:59Z, 읽기 전용 `GET /bundles`):

```
selection = console, console-ecommerce, console-erp, console-scm, console-wms, fan, store, store-fulfillment
→ 도메인: iam · wms · scm · erp · ecommerce · fan · console   (8 중 7, finance 만 빠짐)
```

⇒ 꺼진 상태에서 「팬 플랫폼 · 데모 서버 켜기」 하나만 눌러도 거의 전체 스택이 뜬다. ADR-MONO-071 의 출발점
*"프런트엔드 버튼만 나누고 내부적으로 전체 스택을 시작하는 구현은 금지"* 를 **시간이 지나며** 어기는 모양이다.
소유자가 본 «콘솔만 켰는데 스토어·팬도 켜짐» 의 첫 번째 원인이 이것이다 — 둘이 선택에 남아 있어 **실제로** 떴다.

## ② 공유 의존 iam 하나 때문에 켜지 않은 묶음이 «일부만 실행 중»

`_bundle_state()` 는 필수 도메인(iam 포함) 중 **하나라도** up/partial 이면 선택 안 된 묶음을 `partial` 로 낸다.
iam 은 모든 묶음이 공유하므로 콘솔 하나만 켜도 스토어·팬이 「🟠 일부만 실행 중」이 되고, 론처는 `partial` 을
startable 로 그린다. 라이브 증거: 선택 안 된 `console-finance` 가 **finance=down · iam=up** 인데 `partial`.

---

# Scope

| 파일 | 무엇 |
|---|---|
| `infra/demo/aws/terraform/lambda/handler.py` | ① 꺼진 인스턴스(stopped·stopping)에 온 첫 요청은 선택을 **비우고** 시작(방금 120초 안에 세션이 시작됐으면 합집합 유지) — 비우기는 세대·예산 거절 **뒤** ② 선택 안 된 묶음의 `partial` 은 **자기 도메인**(`BUNDLE_OWN_DOMAINS`)만 근거 |
| `infra/demo/aws/tests/test_handler.py` | 위 두 성질 + 대조군 |
| `infra/demo/verify-demo-wrapper.sh` (z40) | 순서 술어에 `_reset_selection` 추가 — 거절될 요청이 남의 선택을 지우지 않는다 |
| `docs/adr/ADR-MONO-071-…md` | History · **D4.1 세션 경계** · D5 `partial` 문장 · Consequences |

프로젝트 영향: 없음(`projects/**` 무변경). 론처 `index.html` 무변경 — 서버가 옳은 값을 주면 기존 표·버튼 규칙이 그대로 옳다.

---

# Acceptance Criteria

- [x] **AC-1** 꺼진 인스턴스 + 지난 세션 선택(라이브의 8묶음 그대로)에서 `fan` 요청 → 저장 선택이 `["fan"]` 뿐이다.
- [x] **AC-2** `stopping` 에 온 요청도 새 세션으로 본다(선택 = 그 요청).
- [x] **AC-3** 🔵 대조군 — 켜진 세션 안(`running`) 요청은 D4 합집합 그대로다. 방금(창 안) 시작된 세션에서 describe 가 `stopped` 를 줘도 합집합이다. 창 밖이면 다시 새 세션이다.
- [x] **AC-4** 세대(`BUNDLE_CAPABLE=no`)·예산 거절 요청은 선택을 **지우지 않는다**. (z40) 이 `_reset_selection` 을 거절보다 앞에 두면 **문다**.
- [x] **AC-5** 선택 안 된 묶음은 iam 만 떠 있으면 `waiting` 이다(라이브의 `console-finance` 모양 포함). 🔵 대조군: 자기 도메인이 up/partial 이면 `partial`, 선택된 묶음은 iam 만 떠도 `booting`.
- [x] **AC-6** 새 테스트는 **옛 handler 에서 빨갛다** — 성질 칸이 실제로 결함을 문다.
- [ ] **AC-7** 🙋 **`terraform apply` 뒤 라이브** — 인스턴스가 꺼진 창에서 `GET /bundles` 로 (a) 선택이 누른 묶음 하나로 바뀌는지, (b) 켜진 뒤 선택 안 된 카드가 `waiting` 인지 판정한다. apply 는 인프라 변경이라 **소유자 승인 사항**이고 머지로는 반영되지 않는다.

---

# Related Specs

- `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` — D1(선택 영속화) · D4(합집합/잠금) · D5(8단계 상태). 이 티켓이 D4.1 을 더한다.
- `TASK-MONO-634` · `TASK-MONO-647` · `TASK-MONO-653` · `TASK-MONO-668`.
- 🔴 `TASK-MONO-653` § 제외: *"SSM 선택을 비우는 운영 조치 — 증상은 지워지지만 다음에 또 그렇게 된다"* — 그 «다음» 을 막는 것이 ①이다.

# Related Contracts

`GET /bundles` 응답 **모양 무변경** — 선택 안 된 묶음이 공유 의존만 떠 있을 때 `partial` 대신 `waiting` 을 낸다(값 집합 동일). `POST /bundle/start` 요청·응답 모양 무변경.

---

# Edge Cases

- **describe 지연**: `start_instances` 직후에도 `stopped` 가 잠깐 나올 수 있다 → `STARTED_PARAM` 이 120초 안이면 비우지 않는다.
- **창을 닫지는 못한다**: 첫 요청이 선택을 쓰고 `STARTED_PARAM` 을 찍기까지의 짧은 구간에 두 번째 첫 요청이 끼면 지워질 수 있다(SSM 에 CAS 없음) — 코드 주석에 적었다.
- **`STARTED_PARAM` 이 terraform 초기값 `"0"`**: `started > 0` 이 아니면 «최근 시작 아님».
- **(z40) bite-3 앵커** `selection, changed, converged = _add_to_selection(names)` 는 **글자 그대로 남는다** — 비우기는 그 줄 앞의 별도 문장이다.
- **`_selection_ready`**: 선택 묶음 판정은 무변경(선택된 묶음 분기는 그대로).

# Failure Scenarios

- 🔴 **켜진 세션에서도 비우는 경우** — 스토어를 쓰는 방문자가 있는데 누가 팬을 누르면 스토어가 선택에서 빠진다. ⇒ `pending`/`running` 은 합집합(AC-3 대조군).
- 🔴 **거절 전에 비우는 경우** — 구세대 AMI·예산 소진으로 거절될 요청이 남의 선택을 지운다. ⇒ AC-4 + (z40).
- 🔴 **`partial` 을 전부 `waiting` 으로 뭉개는 경우** — 자기 도메인이 반쯤 떠 있는 묶음이 「꺼짐」으로 보인다. ⇒ AC-5 대조군.
- 🔴 **머지를 반영으로 읽는 경우** — Lambda 는 `terraform apply` 로만 나간다. ⇒ AC-7.

---

# Implementation Notes (2026-09-15 UTC)

- 교체는 기대 개수 치환표로(`handler.py` 4건 · ADR 4건, 전부 1회 일치) + (z40) bite-3 앵커 줄이 **정확히 1번** 남는지 사후 단언.
- 로컬 게이트:
  - `python -m unittest discover -s infra/demo/aws/tests` → **95 tests OK** (기존 86 + 새 9)
  - bite A — 옛 handler(`origin/main`)로 같은 스위트: **rc=1**, 실패 5 = 성질 칸 전부(`cold_start_replaces…` · `request_while_stopping…` · `second_click_during_describe_lag…` · `unselected_bundle_is_waiting…` · `after_the_window…`[ERROR, 상수 없음]). 대조군 4칸(합집합 유지 · 자기 도메인 partial · 선택 booting · 거절 시 보존)은 옛 코드에서도 초록 — 대조군이 맞게 선 것.
  - (z40) 순서 판정기(`verify-demo-wrapper.sh` 의 order.py 를 잘라 실행): 실물 **문제 0**
  - bite B — `_reset_selection()` 을 `bundle_start` 의 세대 거절 블록 **앞**으로 옮긴 변형(문법 파싱 확인): 판정기가 **문다** — `거절(579행)이 _reset_selection(578행)보다 뒤입니다 — 선택이 이미 지워진 뒤에 거절합니다`. 🔵 주입 과정에서 `_bundle_capability()` 호출이 **두 곳**(`bundles()` 536행 · `bundle_start` 578행)이라 한 줄 앵커가 안 먹었고, z40 bite-3 과 같은 3줄 블록으로 좁혀 주입했다.
- ⚪ CI: `Demo wrapper smoke`(verify-demo-wrapper.sh --live 전체) · `pytest infra/demo/aws/tests`.
- 🙋 반영: `terraform apply`(소유자 승인) — AMI 재굽기 불필요(부팅 셸 무변경).

분석=Opus 5 / 구현=Opus 5.

## CORRECTION — 머지·apply 뒤 (2026-09-15 UTC): AC-7 은 반만 닫혔다

**머지**: PR [#3831](https://github.com/kanggle/monorepo-lab/pull/3831) `MERGED` 2026-09-15T11:17:59Z · `bd50ed513` · 롤업 SUCCESS 9 · SKIPPED 52 · FAILURE 0(`Demo wrapper smoke` 안의 `pytest infra/demo/aws/tests` 포함).

**apply (소유자 「apply까지해」)** — 메인 체크아웃(`bf0114aec`, 깨끗함; state·tfvars 가 거기만 있다)에서:

| 단계 | 결과 |
|---|---|
| `plan -out` | `aws_lambda_function.control` **in-place 1건** · `0 to add, 1 to change, 0 to destroy` |
| `show` | 바뀌는 속성 = `source_code_hash`(`hzvCO94…` → `tc93P/Uw…`) · `last_modified` 뿐. env·AMI 무변경 |
| `apply "<plan 파일>"` | `Apply complete! Resources: 0 added, 1 changed, 0 destroyed.` |
| `aws lambda get-function-configuration` | `CodeSha256 = tc93P/UwPBmo75XUupNIJZkh0KXNtWzMkI96Dd859AQ=`(plan 과 일치) · `Active` · `Successful` |
| 재-plan `-detailed-exitcode` | rc=0 · `No changes.` |

🔵 AMI 핀(`deployed-ami.env` `ami-058f6293d1408f91e`) = state 의 AMI ⇒ 인스턴스 교체 없음 — plan 이 그것을 확인했다.

**AC-7 (b) 켜진 뒤 선택 안 된 카드: ✅ PASS (라이브 전/후 대조)**

| 시각(UTC) | Lambda | `console-finance`(선택 안 됨) | 도메인 |
|---|---|---|---|
| 11:29:46 | 옛 코드 | `partial` | finance=down · iam=up |
| 11:30:58 | 새 코드 | **`waiting`** | finance=down · iam=up |

같은 순간 선택된 묶음(console · store · fan · console-ecommerce · store-fulfillment)은 전부 `ready` 로 **그대로** — 선택 분기는 안 건드렸다는 대조군.

**AC-7 (a) 꺼진 인스턴스의 첫 요청이 선택을 교체: ⚪ 미측정 — 그리고 왜**

- 이 판정은 **인스턴스가 꺼진 상태에서 `POST /bundle/start`** 가 있어야 난다. 그 요청은 EC2 를 켠다(과금 부작용) — 소유자 승인은 `apply` 까지였고 **기동은 아니었다.**
- 측정 시각에 인스턴스는 **running**(다른 세션, 예산 738/1200분)이었고, 저장 선택은 여전히 8묶음이다. 🔴 이것은 결함이 아니다 — 새 코드는 **종료 때 비우지 않고 다음 꺼진-상태 첫 요청 때 비운다**(D4.1). 그래서 «지금 선택이 8개» 는 (a) 의 반증도 증거도 아니다.
- 🔵 코드 수준 증거는 이미 있다: `test_cold_start_replaces_the_previous_sessions_selection` 이 **라이브의 8묶음 그대로**를 심고 `["fan"]` 만 남는 것을 단언하며, 옛 handler 에서 빨갛다(bite A).
- ⏳ 닫는 조건: 다음에 인스턴스가 `stopped` 인 창에서 누군가(소유자·방문자) 카드 하나를 누른 뒤 `GET /bundles` 의 `selection` 이 **그 묶음 하나**인가. 읽기만 하면 된다 — 그 클릭을 **일부러 만들 필요는 없다.**
- 🔴 이 칸 때문에 이 파일은 `review/` 에 남는다. `done/` 으로 옮기려면 (a) 를 재거나, 소유자 결정으로 `TASK-MONO-672`(스택이 떠야 잴 수 있는 것들의 집)에 넘긴다.
