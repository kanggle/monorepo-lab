# Task ID

TASK-MONO-599

# Title

🔴 **「required 는 넷」을 적으면서 그 넷의 **이름**을 틀리게 적었다** — 문서대로 재등록하면
`main` 이 영구 교착한다. `TASK-MONO-598` 이 막으려던 바로 그 실패다.

# Status

done

# Owner

monorepo

# Task Tags

- ci
- docs
- guard
- merge-verification

---

# ⏳ 어떻게 발견됐나 — **가드가 무는 것을 증명했는데, 문서를 안 봤다**

`TASK-MONO-598`(2026-08-28)은 `main` 에 branch protection 을 켜고 required contexts 를 넷으로
정했다. AC-4 가 그것이 **실제로 무는지**까지 증명했다 — 프로브 PR 은 `BLOCKED` 로 머지 거부,
비-required 만 빨간 PR 은 `UNSTABLE` 로 통과. 게이트는 옳다.

🔴 **그런데 그 증명은 «살아 있는 설정» 을 읽었고, 같은 날 세 문서에 적힌 «그 넷의 이름» 은
아무도 대조하지 않았다.** 다음 PR(#3513)에서 required 넷을 이름으로 조회하려다 **셋이 빈
결과**를 냈고, 그때 드러났다.

🔵 이 저장소가 이미 이름 붙인 함정의 **거울상**이다 — [[feedback_declaration_files_are_not_the_runtime_state]]
는 «선언 파일 grep 은 런타임 상태가 아니다» 를 경고한다. 여기서는 **런타임이 옳고 선언이
틀렸으며**, 검증이 런타임만 읽어서 선언의 결함이 통과했다.

---

# Context — 실측 (2026-08-28 UTC)

## ① 실제 등록된 이름 vs 문서에 적힌 이름

```bash
gh api repos/:owner/:repo/branches/main/protection/required_status_checks --jq '.contexts[]'
```

| # | **실제 등록된 context** | 문서에 적힌 것 | 일치 |
|---|---|---|---|
| 1 | `changes` | `changes` | ✅ |
| 2 | `INDEX queue drift (INDEX.md tables vs queue directories)` | `INDEX queue drift` | ❌ |
| 3 | `Task ID collision (duplicate IDs in active queues)` | `Task ID collision` | ❌ |
| 4 | `Walkthrough limitation ledger drift (§ 6 rows vs task queues)` | `Walkthrough limitation ledger drift` | ❌ |

**넷 중 셋이 틀렸다.** 맞은 하나는 `name:` 이 없어서 job id 가 그대로 context 가 된 `changes`
뿐이다 — 즉 **우연히 맞았다.**

## ② 이름의 출처는 `ci.yml` 의 `name:` 이다

| context | 출처 |
|---|---|
| `changes` | `.github/workflows/ci.yml:142` — `changes:` (job id, `name:` 없음) |
| `Task ID collision (…)` | `ci.yml:861` — `name:` |
| `INDEX queue drift (…)` | `ci.yml:903` — `name:` |
| `Walkthrough limitation ledger drift (…)` | `ci.yml:1020` — `name:` |

🔴🔴 **그래서 두 번째 위험이 따라온다**: required context 는 **문자열로** 매칭된다. `ci.yml` 의
`name:` 을 **한 글자라도 고치면** 등록된 문자열에 대응하는 체크런이 영원히 안 생기고,
required 는 **영구 pending** → `main` 이 모든 PR 에서 `BLOCKED` 가 된다. 그 리네임은
«문구 다듬기» 처럼 보이고 PR 에서 초록으로 통과한다 — **자기 자신은 안 막는다.**

## ③ 모집단 — 정확히 세 곳 (전수)

```bash
grep -rn --include='*.md' -E 'Walkthrough limitation ledger drift|Task ID collision|INDEX queue drift' .
```

| 파일 | 줄 |
|---|---|
| `CLAUDE.md` | 117 |
| `platform/git-workflow-policy.md` | 248 |
| `.claude/commands/review-task.md` | 58 |

🔵 나머지 매치는 `projects/*/tasks/INDEX.md` 의 «3-dim verified» 문구로, 이 축과 무관하다.
🔴 **셋 다 같은 커밋에서 고친다** — 한 사실이 세 절에 살면 한쪽만 고쳐진다.
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

## ④ 피해가 실제로 어떻게 나는가 — 두 방향

| 방향 | 시나리오 | 결과 |
|---|---|---|
| **문서 → 설정** | 누군가 문서의 넷으로 protection 을 재등록/복구한다 | 셋이 존재하지 않는 context ⇒ **영구 pending** ⇒ `main` 영구 `BLOCKED`. 🔴 `TASK-MONO-598` 이 티어 3(29개)을 **바로 이 이유로** 제외했는데, 문서가 그 배제를 무효화한다 |
| **문서 → 감사** | 「required 넷이 초록인가」를 이름으로 조회한다 | 셋이 빈 결과. 🔵 **부분문자열 매칭이면 우연히 맞는다** — 그래서 이 결함은 **조회 방식에 따라 보이기도 하고 안 보이기도 한다** |

🔴 두 번째 줄이 이 결함이 하루를 살아남은 이유다. `grep 'INDEX queue drift'` 는 맞고
`select(.name=="INDEX queue drift")` 는 틀리다. **같은 문자열이 도구에 따라 참이 된다.**

---

# Goal

세 문서가 required contexts 를 **실제 등록된 문자열 그대로** 적게 하고, `ci.yml` 의 `name:`
리네임이 `main` 을 조용히 교착시키지 못하게 한다.

🔴 **이 티켓은 required 집합의 «구성원» 을 바꾸지 않는다** — 그것은 `TASK-MONO-598` 의 판정이고
티어 2(33개)는 그 티켓에 미결로 남아 있다. 여기서 고치는 것은 **이름뿐**이다.

# Scope

**포함**: `CLAUDE.md` · `platform/git-workflow-policy.md` · `.claude/commands/review-task.md`
세 곳의 문자열, 그리고 AC-2 가 정하는 가드.

**제외**: branch protection 설정 자체(현재 값이 **옳다** — 고칠 것이 없다) · required 집합의
크기 · 티어 2 판정 · `ci.yml` 의 job 이름 변경(🔴 지금 고치면 그 순간 교착한다).

---

# Acceptance Criteria

## AC-1 — 세 곳을 실제 문자열로 고친다, **API 를 출처로**

- 넷을 **전부** 등록된 그대로 적는다(괄호 포함). 🔴 «괄호는 생략해도 된다» 는 주석을 달지
  마라 — 생략 가능한 것이 아니라 **다른 문자열**이다.
- 출처는 문서나 기억이 아니라 `required_status_checks` API 응답이다. 고친 뒤
  **집합 동등성으로 검증**한다:

```bash
gh api repos/:owner/:repo/branches/main/protection/required_status_checks --jq '.contexts[]' | sort > /tmp/live
# 문서에서 뽑은 넷을 같은 형식으로 정렬해 비교
comm -3 /tmp/live /tmp/doc     # 출력이 비어야 통과
```

- 🔵 `changes` 가 **왜** 유일하게 짧은지도 한 줄로 적는다(job id 가 그대로 context 가 된다).
  안 적으면 다음 사람이 «괄호를 빼는 게 맞나 보다» 로 읽는다.

## AC-2 — 🔴 **가드 — 리네임이 `main` 을 교착시키기 전에 빨개져야 한다**

이 결함의 **재발 경로는 문서가 아니라 `ci.yml` 의 `name:` 이다**(§②).

- 🔴 **가드가 protection API 를 읽어야 하는가를 먼저 정한다.** 읽어야 한다면 `GITHUB_TOKEN`
  의 권한이 되는지 **실측**하고, 안 되면 그 사실을 적고 **다른 축을 고른다** — 러너에서 못
  도는 가드는 초록으로 썩는다. [[feedback_two_correct_exclusions_compose_into_a_hole]]
- 🔵 **API 없이도 무는 축이 있다**: required 넷의 문자열을 **저장소 안에 핀으로 두고**,
  `ci.yml` 이 그 이름의 job 을 여전히 선언하는지 본다. 리네임 방향(더 흔한 쪽)을 잡는다.
  🔴 다만 **반대 방향(누가 protection 을 바꿈)은 안 잡힌다** — 그 공백을 명시적으로 적는다.
  [[feedback_a_partial_deletion_reads_as_a_total_one]]
- 🔴 **핀이 결함을 얼리지 않게 하라** — 핀의 기대값은 «지금 값» 이 아니라 «API 가 말한 값»
  이어야 한다. [[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]
- **양방향 bite 증명**: job 이름을 한 글자 바꾸면 RED, 되돌리면 GREEN. 주입이 실제로
  적용됐는지 먼저 단언한다. [[feedback_assert_the_injection_before_reading_the_bite]]

## AC-3 — 🔴 **이 결함이 왜 하루를 살아남았는지 한 줄 남긴다**

`TASK-MONO-598` AC-4 는 게이트가 **무는 것**을 증명했다. 그 증명은 유효하다 — 그러나
**살아 있는 설정을 읽었고 문서를 안 읽었다.** 「가드가 문다」와 「그 가드를 설명한 문서가
맞다」는 **다른 명제**이고, 후자는 아무 검증도 받지 않았다.

- `platform/git-workflow-policy.md` 의 merge-verification 절에 한 줄로 적는다.
- 🔴 산문으로 흘리지 마라 — 다음에 같은 형태(설정을 켜고 문서 세 곳에 적는 작업)를 할 때
  **읽힐 자리**에 둔다.

## AC-4 — **안 고치는 것**

| 안 고침 | 근거 |
|---|---|
| required 집합의 **크기**(넷) | `TASK-MONO-598` 의 판정. 티어 2(33개)는 거기 미결 |
| `ci.yml` 의 job `name:` | 🔴 **지금 고치면 그 순간 교착한다.** 바꾸려면 protection 을 먼저 갱신하는 **순서**가 필요하고, 그것은 별도 결정 |
| protection 설정값 | **현재 옳다** |
| `enforce_admins` / approval 정책 | 598 의 판정 그대로 |

---

# Related Specs

- `platform/git-workflow-policy.md` § Merge-Verification Worked Incident / § Agent Self-Modification
- `CLAUDE.md` § Task Rules — Objective merge verification before any close chore
- `.claude/commands/review-task.md`
- `.github/workflows/ci.yml` (142 · 861 · 903 · 1020)
- `tasks/done/TASK-MONO-598-*.md`

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 소유자가 그 사이 required 집합을 바꿨다 | 🔴 **문서를 고치기 전에 API 를 다시 읽는다.** 이 티켓의 표는 08-28 스냅샷이지 진실의 출처가 아니다 |
| `GITHUB_TOKEN` 이 protection API 를 못 읽는다 | AC-2 가 미리 정한 대로 **다른 축**으로 간다. 🔴 «PAT 를 secret 으로» 는 새 비밀 하나를 들이는 결정이므로 **별도로 승인받는다** |
| 가드를 만들었더니 **첫날 빨강** | 🔴 끄지 마라 — 빨강이 옳다면 그것이 산출물이다. 끌 거면 이유를 적고 티켓을 남긴다 |
| job 이름에 `§` 같은 비-ASCII 가 들어 있다 | 🔴 이미 들어 있다(`§ 6 rows`). 핀 파일과 비교기가 **인코딩을 통과시키는지** 실측한다 — 이 호스트는 CP949 다 [[env_grep_korean_false_zero_two_mechanisms]] |
| 문서 셋 중 하나만 고친다 | 🔴 세 곳이 같은 사실을 갖고 있다 — 한 커밋에서 셋 다 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| 문서의 짧은 이름으로 protection 을 재등록한다 | 셋이 영구 pending ⇒ **모든 PR 이 `BLOCKED`**, `main` 교착 | AC-1 — 등록 문자열 그대로 |
| 「부분문자열로 grep 하면 맞더라」로 닫는다 | 도구에 따라 참이 되는 결함이 그대로 남는다 | AC-1 의 **집합 동등성**(`comm -3`) |
| 가드를 protection API 로 만들었는데 토큰 권한이 없다 | 스텝이 조용히 건너뛰거나 항상 초록 | AC-2 — **권한을 먼저 실측** |
| 핀 파일에 «지금 ci.yml 값» 을 적는다 | 이미 어긋나 있으면 결함을 얼린다 | AC-2 — 기대값의 출처는 **API** |
| `ci.yml` 의 `name:` 을 다듬는 PR 이 초록으로 머지된다 | **그 다음 PR 부터** 전부 BLOCKED, 원인은 두 PR 전 | AC-2 가드 |
| 「가드가 무는 걸 증명했으니 문서도 맞다」 | 다음에 같은 형태로 또 난다 | AC-3 |

---

# 구현 기록 (2026-08-28 UTC)

## AC-1 ✅ — 세 곳, API 를 출처로

`CLAUDE.md:117` · `platform/git-workflow-policy.md:248` · `.claude/commands/review-task.md:57`
셋 다 **한 커밋**에서 고쳤다. 🔵 **네 문자열을 손으로 타이핑하지 않았다** —
API 응답을 그대로 파일로 받아 헤더만 앞에 붙였다(`scripts/required-check-names.txt`).
`§` 가 `302 247`(UTF-8 C2 A7)로, LF 로, BOM 없이 들어간 것을 `od -c` 로 확인했다.

각 문서에 **왜 `changes` 만 짧은지**(그 job 에 `name:` 이 없어 job id 가 그대로 context)를
같이 적었다 — 안 적으면 다음 사람이 「괄호를 빼는 게 맞나 보다」로 읽는다.

## AC-2 ✅ — 가드는 **핀 축**으로. 그리고 API 축을 왜 안 골랐는지

`scripts/check-required-check-names.sh` — 핀 ↔ `ci.yml` ↔ 문서 3곳.

- 🔴 **protection API 를 판정에 넣지 않았다.** 그 엔드포인트는 admin 권한을 요구하고
  워크플로 `GITHUB_TOKEN` 이 읽는다는 보장이 없다 — **못 도는 가드는 초록으로 썩는다.**
  대신 **한 번 쏘는 probe 스텝**을 넣어 그 답을 *측정*하게 했다(`continue-on-error`,
  절대 잡을 실패시키지 않는다). 🔴 **이 티켓은 그 답을 아직 모른다** — 첫 런의
  `PROBE-RESULT:` 줄이 기록이고, 「읽힌다」로 나오면 반대 방향 가드가 후속 사안이 된다.
- 🔴 **못 무는 방향을 헤더와 «성공» 출력 양쪽에 적었다** — 누가 protection 쪽을 바꾸면
  이 가드는 여전히 초록이다. 핀은 사본이고, 사본은 원본이 바뀐 것을 모른다.
- 판정은 **등호**다. `ci.yml` 은 `name:` 줄 **전체**와 `grep -qxF`, 문서는 고정 문자열.
  🔴 그리고 칸 (3)이 **짧은 형태의 코드 스팬**을 따로 잡는다 — 전체 이름이 있어도
  짧은 형태가 백틱에 싸여 남아 있으면 RED. **이 티켓의 결함 자체가 그 모양이었다.**

### bite 증명 — `--self-test` **6/6**, 주입을 먼저 단언

| 칸 | 주입 | 기대 | 결과 |
|---|---|---|---|
| (a) | 없음 (양성 대조군) | rc=0 | ✅ |
| (b) | `ci.yml` 의 `name:` 을 `queue directories)` → `queue dirs)` | rc=1 | ✅ |
| (c) | 문서에서 전체 이름 소실 | rc=1 | ✅ |
| (d) | 핀 4→3 (FLOOR) | rc=1 | ✅ |
| (e) | 전체 이름은 **두고** 짧은 형태를 코드 스팬으로 추가 | rc=1 | ✅ |
| (f) | `§` 이름이 실제로 매치되는지 (인코딩 대조군) | 매치 | ✅ |
| (g) | 핀이 **CRLF** 로 체크아웃됨 | rc=0 (**안 깨져야** 한다) | ✅ |

🔵 **(g) 는 이 호스트가 만든 칸이다.** `git add` 가 *"LF will be replaced by CRLF"* 를
경고했고, 핀의 각 줄은 **등록된 문자열 그 자체**라 `\r` 하나면 넷 다 «없음» 이 된다 ⇒
**Windows 에서만 빨간 가드**가 되고, 원인이 내용이 아니라 **체크아웃 바이트**라 진단이
오래 걸린다. 이중 방어: `.gitattributes` 로 blob 을 LF 고정 + 가드가 읽을 때 `\r` 제거.

🔴🔴 **그 «이중 방어» 를 증명하려던 대조군이 처음엔 거짓 통과했다.** 벗기는 줄을 `sed` 로
지우고 CRLF 핀에 돌려 「rc=1 이 나와야 한다」를 보려 했는데 **`sed` 패턴이 안 맞아 그 줄이
그대로 남은 사본**을 돌렸고, rc=0 이 나왔다 — 「벗기지 않아도 통과한다 ⇒ 이 수정은 무의미」로
읽힐 뻔했다. 남은 줄 수를 함께 찍어서 잡았다(`0` 이어야 하는데 `1`). 다시 하니
**벗김 없음 + CRLF 핀 = rc=1**(넷 다 «없음»)로, 그 한 줄이 실제로 지탱한다.
[[feedback_my_verification_predicate_is_the_likeliest_defect]] [[feedback_assert_the_injection_before_reading_the_bite]]

🔵 **(f) 가 필요한 이유**: (a) 가 초록인 것으로는 부족하다. 그 이름이 핀에서 **빈 줄로
읽혔어도** 초록이 나온다 — 「아무것도 안 하고 통과」와 「검사하고 통과」가 같은 색이다.
🔴 각 칸은 판정을 읽기 **전에** 주입이 적용됐는지 단언하고, 실패하면 `exit 3` 으로
«칸을 신뢰할 수 없음» 을 낸다 — 초록으로 넘어가지 않는다.

### 어디서 도는가

`ci.yml` 의 `required-check-names` 잡. 🔴 필터는 **`code-changed` 와 AND 하지 않았다** —
도착 경로가 전부 워크플로 `name:` 아니면 마크다운 줄이라, AND 하면 **이 가드가 존재하는
이유인 diff 에서 정확히 건너뛴다**. 경로: `ci.yml` · 가드 · 핀 · 문서 3곳.

## AC-3 ✅

`platform/git-workflow-policy.md` § Merge-Verification 에 한 문단:
**「가드가 문다」와 「그 가드를 설명한 문서가 맞다」는 다른 명제**이고, 기전을 켠 뒤 쓴
설명은 **두 번째 산출물이라 자기 검사가 필요하다.**

## AC-4 ✅ — 안 고친 것 (그대로)

required 집합의 크기 · 티어 2(33개) · `ci.yml` job `name:` · protection 설정값.
🔵 protection 설정은 **지금 옳다** — 이 티켓이 고친 것은 그것을 **적은 문장**뿐이다.

## 🔴🔴 착수 중 당한 것 — 한 줄 고친 커밋이 **985 줄을 다시 썼다**

AC-4 가 «안 고칠 것» 을 못박아 뒀지만 이건 **범위 확장이 아니라 내가 낸 피해의 수습**이다.

`tasks/INDEX.md` 의 행 하나를 스크립트로 ready→review 로 옮겼더니 커밋 diff 가
**985 insertions / 985 deletions** 으로 나왔다. 파일 전체가 **CRLF 로 다시 쓰였고**, 실제
내용 변경 한 줄이 그 안에 묻혔다(blame 도 같이 죽는다).

**관측** (`core.autocrlf=true` 인 이 호스트):

| | |
|---|---|
| `origin/main` blob | CR **0** · LF 985 |
| 내가 만든 커밋의 blob | CR **985** · LF 985 |
| 체크아웃 | LF → **CRLF 로 바꿔 준다**(`git add` 가 *"LF will be replaced by CRLF"* 를 계속 경고한다) |
| `git add` | CRLF → LF **로 되돌리지 않았다** |

🔴 **변환이 한 방향만 돈다.** 그러니 바이트를 보존하는 도구(스크립트 · `sed -i`)로 이 파일을
쓰면 **CRLF 가 그대로 blob 에 들어간다.** 같은 커밋의 다른 마크다운 5개는 전부 CR=0 이었다 —
**이 파일만** 그랬다. 🔵 짐작되는 이유는 한국어·이모지 비중이 높아 git 의 text/binary
휴리스틱이 한쪽에서 «binary» 로 갈리는 것이지만, **기전보다 관측이 판정이다.**

**수습**: 파일을 LF 로 되돌려 diff 를 **1/1** 로 만들었고, `.gitattributes` 에
`tasks/INDEX.md` · `projects/*/tasks/INDEX.md` 를 `text eol=lf` 로 고정했다.
🔴 되돌리는 과정에서 **줄 끝이 아닌 자리에 `\r` 하나**가 남아 있었다 — 스크립트가 CRLF 상태의
행 문자열 뒤에 텍스트를 이어 붙여 `\r` 이 줄 **중간**에 들어갔기 때문이고, `\r\n`→`\n` 치환은
그것을 못 지운다. CR 을 **전수로 세어 0** 을 확인했다.

## ✅ probe 의 답 — **못 읽는다.** 추정이 아니라 실측이다

impl PR #3515 의 첫 런(`33169913385`, job `98844292219`, `2026-08-28T12:09:20Z`):

```
rc=1
{"message":"Resource not accessible by integration",
 "documentation_url":".../branches/branch-protection#get-status-checks-protection",
 "status":"403"}
PROBE-RESULT: not readable — the pin axis is the only one available
```

⇒ **워크플로 `GITHUB_TOKEN` 은 `required_status_checks` 를 못 읽는다**(403). AC-2 가
「권한을 **먼저 실측**하고, 안 되면 다른 축을 고른다」로 정해 둔 그대로 **핀 축이 유일한
선택지**였음이 확인됐다. 🔵 이 줄이 없었다면 같은 결론을 «아마 admin 이 필요할 것» 이라는
**추정**으로 적었을 것이고, 다음 사람이 그걸 의심하며 같은 실험을 반복했을 것이다.

🔴 **그러므로 「못 무는 방향」은 당분간 영구적이다** — protection 쪽 변경(집합 추가·제거·개명)은
이 가드가 **못 본다**. 유일한 대안은 admin PAT 을 secret 으로 들이는 것이고, 그것은
**새 비밀 하나를 들이는 결정**이라 Edge Cases 가 미리 «별도로 승인받는다» 로 못박아 뒀다 ⇒
🔴 **이 티켓에서 하지 않는다.** 소유자가 required 집합을 바꾸면 **핀을 API 로 다시 만들어야
한다**(핀 파일 헤더에 명령이 적혀 있다).

🔵 같은 런에서 `--self-test` **7/7**, 새 잡 `Required check names (…)` **SUCCESS**,
required 넷도 전부 SUCCESS 였다.
