# Task ID

TASK-MONO-569

# Title

분류기 지도가 **재현되지 않은 차단**을 단정한다 — `.claude/hooks/` "hard-blocked, intent-resistant" 는 2026-08-14 실측에서 통과했다. 그리고 지도의 축이 **경로**로 적혀 있는데 실제 축은 **권한을 바꾸는 편집**이다.

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- agent-config

---

# Goal

`CLAUDE.md` § Git/branch/worktree discipline 의 분류기 불릿과 `platform/git-workflow-policy.md` § *`.claude/` Self-Modification* 표를, **실측된 두 사실**에 맞춰 고친다:

1. `.claude/hooks/` 의 ❌ **hard-blocked / intent-resistant** 단정은 **재현되지 않았다** — TASK-MONO-530(2026-08-14)에서 `.claude/hooks/**` Edit + commit + push 가 통과했다.
2. 차단의 축은 **경로가 아니라 "권한을 바꾸는 편집"** 이다 — `.claude/` **밖**의 도메인 코드(역할 카탈로그·권한 매트릭스·인가 상수)를 편집할 때도 막힌다(MONO-522). 같은 파일의 **설명·주석**은 통과한다(MONO-528).

🔴 **이 절은 자기 자신이 이 실패를 이미 한 번 기록해 두었다** — *"this section previously asserted `agents/` and `commands/` were blocked … the agent that tried anyway landed PR #2616"*. 같은 모양이 `hooks/` 행에서 반복됐다. 고칠 것은 한 행의 값이 아니라 **표가 단정을 담는 형식** 이다.

---

# 배경 — 실측

## ① `hooks/` 행의 단정이 반증됐다

현재 `platform/git-workflow-policy.md`:

| Path | Agent edit + commit | Note |
|---|---|---|
| `.claude/hooks/` | ❌ **hard-blocked** | **Intent-resistant** — an explicit user instruction does *not* clear it. |
| `.claude/settings.json` | ❌ **hard-blocked** | Not a hook file, but hook **wiring** — same treatment. |

TASK-MONO-530(2026-08-14) 실측: **`.claude/hooks/**` Edit + commit + push 통과.** 그리고 같은 세션 안에서 `gh pr merge` 가 #3319 에서 **차단**되고 #3322 에서 **통과**했다 — 즉 **분류기는 세션 내에서도 움직인다.** `settings.json` 은 그 세션에서 **재검증되지 않았다**(미지, 차단 확인이 아니다).

⇒ 이 표는 *"측정된 시점의 관측"* 이라고 아래 문단에서 스스로 말하면서, **행 자체는 영구 사실의 어조**(hard-blocked / intent-resistant)로 적혀 있다. 그 어조가 다음 세션의 시도를 막는다.

## ② 축이 틀렸다 — 경로가 아니라 편집의 성격

- **MONO-522**: `.claude/` 밖 도메인 코드에서 차단됐다 — cc 클라이언트에 `ADMIN` 부여(`Map.of("artist.read", List.of("ADMIN"))`)가 **권한 상승**으로 읽혔다.
- **MONO-528 정정**: 차단 축은 **파일이 아니라 "권한을 바꾸는 편집"** 이다. **같은 파일의 설명·주석 편집은 통과**한다.

⇒ 경로 표만 읽은 에이전트는 (a) `.claude/` 밖에서 막히면 **원인을 못 찾고**, (b) 권한 표를 실제로 바꾸는 편집을 *"경로가 안 걸리니 괜찮다"* 로 판단한다.

## ③ CLAUDE.md 불릿은 한 문장 안에서 자기모순이다

현재 `CLAUDE.md` L201 은 앞에서 *"hard-blocked **even with explicit approval** (intent-resistant)"* 라고 단정하고, 같은 불릿 뒤에서 *"don't pre-emptively hand off on assumption — attempt once, and hand over only on an actual block"* 이라고 말한다. **앞 절반이 뒤 절반을 무효화한다** — hard-blocked 라고 읽으면 시도할 이유가 없다.

## ④ 별개 축이 하나 더 있다

`docker volume rm` 도 같은 분류기에 걸린다(경로와 무관). 현 문서 어디에도 없다.

---

# Scope

## In Scope

1. `platform/git-workflow-policy.md` § *`.claude/` Self-Modification* — 표를 **"관측 시각 + 마지막 결과"** 형식으로 바꾸고, `hooks/` 행을 MONO-530 실측으로 갱신, `settings.json` 을 **미재검증**으로 표기.
2. 같은 절에 **축 정정**: 차단 판정은 경로가 아니라 **권한을 바꾸는 편집**이며 `.claude/` 밖에도 적용된다. 같은 파일의 설명·주석은 통과한다.
3. 같은 절에 **`docker volume rm`** 을 별개 축으로 추가(§ Self-Merge and Force-Push 옆).
4. 절 제목 조정 — *"Which Paths the Classifier Actually Blocks"* 는 축이 경로라는 전제를 제목에 박고 있다.
5. `CLAUDE.md` L201 불릿 — 자기모순 제거. 단정을 걷고 **"시도해 보고 실제 차단에만 hand-off"** 를 주절로, 마지막 관측 결과는 부속으로.
6. `tasks/INDEX.md` 행 이동.

## Out of Scope

- **`.claude/hooks/` 파일을 실제로 편집하는 것** — 이 티켓은 문서를 고치는 것이지 차단을 시험하는 것이 아니다. 시험이 필요하면 별건.
- **`.claude/settings.json` 재검증** — 실측이 필요하고, 실패 시 사용자 hand-off 가 걸리는 별개 작업. 이 티켓은 **"미재검증"으로 정직하게 표기**하는 데까지다. 🔴 **모르는 것을 차단으로 적지 않는다** — 그게 이 티켓이 고치려는 실패다.
- **분류기 자체의 동작 변경** — 외부 정책이고 이 저장소의 소관이 아니다.
- **에이전트 메모리(`env_classifier_claude_self_mod_block`)** — 이미 실측대로 갱신돼 있다(이 티켓의 근거 출처).

---

# Acceptance Criteria

## AC-0 — 착수 시점 재측정 (verify-then-act)

```bash
grep -n 'classifier-blocked' CLAUDE.md
sed -n '/^## `.claude\/` Self-Modification/,/^---$/p' platform/git-workflow-policy.md
grep -rn 'hard-blocked\|intent-resistant' CLAUDE.md platform/ .claude/
```

- 세 곳 모두에서 현재 문구를 확인한다. 이미 고쳐져 있으면 STOP.
- 🔴 **`grep` 범위를 `.claude/` 와 `platform/` 로 한정하지 말 것** — 이 단정이 `docs/guides/` 나 스킬에 복제돼 있을 수 있다. 한 사실이 여러 곳에 있으면 한쪽만 고쳐진다.

## AC-1 — 표가 단정이 아니라 관측을 담는다

- 각 행이 **마지막으로 관측된 결과 + 그 시각/티켓**을 갖는다. `hooks/` 행 = **2026-08-14 통과(MONO-530)**.
- `settings.json` 행이 ❌ 가 아니라 **미재검증(unknown)** 이다.
- **"intent-resistant" 라는 단정이 파일에서 0건**이다 — 그 성질은 관측된 적이 있지만 **재현되지 않았고**, 단정으로 남으면 시도를 막는다. 필요하면 *"2026-06 관측: 명시 승인으로도 안 열렸다"* 처럼 **시각을 붙인 서술**로 남긴다.
- 표 머리에 **"이것은 외부 정책의 관측이고 세션 내에서도 움직인다"** 가 한 줄로 있다(#3319 차단 / #3322 통과가 같은 세션이었다는 실측을 근거로).

## AC-2 — 축 정정이 표보다 **먼저** 온다

절의 첫 문단이 **"판정 축 = 권한을 바꾸는 편집"** 이고, 경로 표는 그 아래의 *관측 사례*로 배치된다.

- `.claude/` **밖** 에서도 막힌다는 것과 그 실례(MONO-522 역할 카탈로그)가 있다.
- **같은 파일의 설명·주석 편집은 통과**한다는 대조(MONO-528)가 함께 있다 — 대조가 없으면 "그 파일은 못 건드린다" 로 과잉 일반화된다.

🔴 **표를 먼저 두면 축을 못 읽는다.** 지금 제목부터 *"Which Paths"* 라 경로가 축이라고 말하고 있다.

## AC-3 — CLAUDE.md 불릿의 자기모순 제거

L201 불릿이 다음을 만족한다:

- **주절 = 행동 규칙**(*"attempt once; hand over only on an actual block"*).
- 마지막 관측 결과는 **시각과 함께** 부속으로. `hard-blocked even with explicit approval` 형태의 무조건 단정 **0건**.
- 축(권한을 바꾸는 편집, `.claude/` 밖 포함)이 한 구절로 들어간다.
- 상세는 `platform/git-workflow-policy.md` 로 포인터. 🔴 **요약을 두 벌 만들지 않는다** — 요약이 원본보다 짧으면 어긋남이 아니라 **소실**이고 비교 검사가 발화하지 않는다.

## AC-4 — 별개 축 추가

`docker volume rm` 이 경로와 무관한 별개 차단 축으로 § Self-Merge and Force-Push 와 같은 층위에 기록된다.

## AC-5 — bite 없음을 명시한다

이 변경에는 **자동 가드를 붙이지 않는다.** 판정 대상이 *외부 분류기의 그때그때 반응*이라 저장소 파일에서 셀 수 있는 것이 아니다.

🔴 대신 **회수 지점**을 남긴다: 누군가 `settings.json` 이나 `hooks/` 를 실제로 시도해 결과를 얻으면 **그 행을 갱신**하도록, 표 아래에 *"이 표를 갱신하는 방법"* 한 줄(관측 시각 + 티켓/PR 번호 + 통과/차단)을 적는다. 게이트가 없는 표는 반드시 낡는데, 이 표는 **낡았다는 것이 방금 드러난** 표다.

---

# Related Specs

- `platform/git-workflow-policy.md` § `.claude/` Self-Modification · § Self-Merge and Force-Push — 변경 대상 본체
- `CLAUDE.md` § Cross-Project Changes → Git/branch/worktree discipline 카탈로그 — 변경 대상
- `tasks/done/TASK-MONO-409-*` (#2616) · `TASK-MONO-396-*` (#2525) · `TASK-MONO-167-*` (#1021) — 표의 ✅ 행 근거
- `tasks/done/TASK-MONO-234-*` — `skills/` 의 per-action 승인 근거

# Related Contracts

없음. 에이전트 운영 문서이고 서비스 간 계약을 건드리지 않는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 이 티켓 구현 중 실제로 `platform/` 편집이 막힌다 | 표가 *"`platform/` 은 대상 아님"* 이라고 적어 둔 것이 반증된 것이므로, **그 사실 자체가 산출물**이다. 티켓에 기록하고 그 행을 갱신한다. |
| `hooks/` 가 지금은 다시 막힌다 | 그것도 관측이다 — AC-1 의 형식(시각 + 결과)이 **두 관측을 모순 없이 담는다.** "지금은 막힌다" 는 "언제나 막힌다" 가 아니다. |
| 축을 "권한을 바꾸는 편집" 으로 좁혔더니 실제로는 더 넓다 | 축 문장에 **"관측된 축"** 이라고 붙이고, 반례가 나오면 넓힌다. 단정으로 적지 않는 것이 이 티켓의 요지다. |
| 다른 세션이 같은 두 파일을 동시에 고친다 | `CLAUDE.md` 는 공유 파일 시리즈의 전형이다 — 이 티켓은 **단일 worktree 직렬**로 진행한다. |
| 표를 지우고 산문으로만 쓴다 | 하지 않는다. 표는 **관측 대장**으로서 값이 있고, 문제는 표가 아니라 **어조와 순서**다. |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| `hooks/` 행을 ❌ → ✅ 로 **뒤집기만** 했다 | 다음에 막히면 표가 또 틀림 | 값을 뒤집는 것이 아니라 **형식을 바꾼다**(AC-1). 이 표는 이미 한 번(agents/commands) 값만 고쳐 놓고 같은 실패를 반복했다. |
| "미재검증" 을 "통과" 로 낙관 기입 | `settings.json` 시도 시 예상 못 한 차단 | 🔴 모르는 것은 **모른다고 적는다.** 숫자를 낙관 쪽으로 고치지 않는 이 저장소의 규율 그대로. |
| CLAUDE.md 와 platform/ 이 또 갈라진다 | 다음 감사에서 같은 지적 | AC-3 이 CLAUDE.md 를 **포인터**로 만든다. 두 벌의 요약을 두지 않는다. |
| 축 정정을 표 안 각주로 넣었다 | 표만 읽는 사람이 못 봄 | AC-2 가 **순서**를 요구한다 — 축이 표보다 먼저. |
| 승격했는데 메모리 쪽과 어긋난다 | 개인 메모리가 다시 정경을 앞섬 | 메모리 `env_classifier_claude_self_mod_block` 는 이미 실측대로다. 정경을 그것에 맞추는 것이 이 티켓이고, 이후 **정경이 보유자**다. |

---

분석=**Opus 5** / 구현 권장=**Sonnet** — 문서 2파일이지만 "단정을 관측으로 바꾸는" 편집이라 어조 판단이 필요하다.
