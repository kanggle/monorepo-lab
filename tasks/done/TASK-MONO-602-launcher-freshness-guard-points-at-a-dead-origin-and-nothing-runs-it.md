# Task ID

TASK-MONO-602

# Title

🔴 **론처 신선도 가드가 죽은 오리진을 가리키고, 아무도 그것을 실행하지 않는다** — 형제 둘은
이미 nightly 러너 + 커스텀 도메인으로 이주했고 이것만 낙오했다.

# Status

done

# Owner

monorepo

# Task Tags

- ci
- guard
- vercel
- adr-mono-067

---

# ⏳ 어떻게 발견됐나 — **다른 것을 재다가 걸렸다**

`TASK-MONO-582` 의 Vercel 프로젝트(`kanggle-store`)가 생긴 뒤 배포된 화면을 찌르려고
`https://kanggle-store.vercel.app` 을 불렀더니 **404** 였다. store 결함인 줄 알고 닫을 뻔했는데
**대조군을 넣으니 셋 다 404** 였다 — 그리고 그중 둘은 **살아 있는 것이 확실한** 프로젝트다.

| 호스트 | 첫 측정 | 45초 뒤 |
|---|---|---|
| `kanggle-portfolio.vercel.app` | 404 | 404 |
| `kanggle-fan.vercel.app` | 404 | 404 |
| `kanggle-store.vercel.app` | 404 | 404 |
| 🔵 **양성 대조군** `hubwang.com` | **200** | **200** |
| 🔵 **양성 대조군** `fan.hubwang.com` | **307** | **307** |

⇒ **`*.vercel.app` 별칭이 이 계정에서 응답하지 않는다. 커스텀 도메인만 산다.**
🔴 응답은 Vercel 엣지가 낸 진짜 `DEPLOYMENT_NOT_FOUND` 문서였다(요청 id 포함) — 네트워크
문제나 캡티브 포털이 아니다. 두 시점 · 양성 대조군 동반으로 확인했다.
[[env_ktg_vercel_deployment_protection_blocks_measurement]]
[[feedback_control_group_design_four_axes]]

---

# Goal

`infra/demo/aws/site/check-launcher-fresh.sh` 가 **실제로 판정할 수 있고, 실제로 돌게** 한다.

지금 이 가드는 **둘 다 아니다**:

1. **기본 `ORIGIN` 이 죽었다** — `https://kanggle-portfolio.vercel.app` 로 하드코딩돼 있다.
2. **아무 러너도 없다** — `ci.yml` 은 이 파일을 `bash -n`(문법)으로만 검사한다.

---

# Context — 실측 (2026-08-29 UTC)

## ① 가드 자체는 **멀쩡하다** — 오리진만 주면 판정한다

```
$ bash infra/demo/aws/site/check-launcher-fresh.sh --origin https://hubwang.com
  서빙 md5 = 6437f1fe61888f634e7dc2ce2dc37946
  기대 md5 = 6437f1fe61888f634e7dc2ce2dc37946   (origin/main)
  서빙 커밋 = 9c83e4538…   기대 커밋 = 9c83e4538…
  ✔ 신선 — 서빙 중인 바이트가 origin/main 과 같습니다.
rc=0
```

🔵 **부수 소득**: 론처는 실제로 **신선하다**(방금 머지한 `9c83e4538` 을 서빙 중).

## ② 🔴🔴 그런데 **자가검사(bite 증명)조차 못 돈다** — 같은 죽은 오리진을 쓴다

```
$ bash …/check-launcher-fresh.sh --self-test
  ✖ … 404 … ⇒ **판정 불가**(낡음이 아니다)          ← 두 칸 모두
  ── 대조군 결과: 현재기준=2  이전판기준=2
  ✖ 기대는 현재기준=0(신선) · 이전판기준=1(낡음) 이었습니다.
rc=2
```

**오리진을 주면 통과한다**:

```
$ bash …/check-launcher-fresh.sh --self-test --origin https://hubwang.com
  ── 대조군 결과: 현재기준=0  이전판기준=1
  ✔ 판정자가 같은 오리진을 두 기준으로 갈랐습니다 (현재=신선 / 이전판=낡음).
rc=0
```

🔴 **이것이 이 티켓의 심각도를 정한다** — 가드가 «틀린 답» 을 내는 것이 아니라, **자기가
무는지조차 증명할 수 없는 상태**다. 그리고 그 사실이 **아무 데서도 발화하지 않았다.**
[[feedback_assert_the_injection_before_reading_the_bite]]

## ③ 🔵 그래도 **fail-closed 다** — 조용히 통과하지는 않는다

404 를 만나면 `rc=2` 로 **«판정 불가(낡음이 아니다)»** 를 명시적으로 말한다.
🔴 **이 항목이 티켓을 «긴급» 으로 만들지 않는 이유**이고, 동시에 **왜 아무도 모르고 있었는지**의
이유이기도 하다 — 돌리는 사람이 없으면 fail-closed 도 아무 소리를 내지 않는다.
[[feedback_why_a_guard_does_not_bite]]

## ④ 🔴🔴 모집단 — **형제 둘은 이미 이주했다. 이것만 낙오했다**

같은 종류의 «라이브 판정» 가드가 셋이다:

| 가드 | 러너 | 오리진 |
|---|---|---|
| `projects/fan-platform/web/fan-platform-web/check-fan-guard-live.sh` | ✅ `nightly-e2e.yml` (`--self-test` + 라이브 2칸) | `https://fan.hubwang.com` |
| `projects/fan-platform/web/fan-platform-web/check-fan-fresh.sh` | ✅ `nightly-e2e.yml` | `https://fan.hubwang.com` |
| **`infra/demo/aws/site/check-launcher-fresh.sh`** | ❌ **없음** (`bash -n` 뿐) | 🔴 **`kanggle-portfolio.vercel.app` (죽음)** |

🔴 **형제의 수정 기록이 낙오 명단이다** — fan 쪽은 nightly 러너를 받으면서 오리진도 커스텀
도메인으로 옮겼는데, 론처는 **둘 다 못 받았다.**
[[feedback_grep_the_siblings_before_fixing_it_yourself]]

## ⑤ `ci.yml` 이 스스로 그 이유를 적어 두었다 — **그리고 그 전제가 낡았다**

`ci.yml` 의 해당 필터에는 이렇게 적혀 있다:

> *"TASK-MONO-563: the two deployment-freshness judges. They are **manual tools**
> (they need the live origin, so **no PR-time job runs them**), which is exactly why their
> syntax must be gated somewhere — a tool nobody ever executes rots silently and is
> discovered at the moment it is needed."*

🔵 **그 판단은 그때 옳았다**(PR 타임에는 라이브 오리진이 없다). 🔴 **그러나 그 뒤 nightly 가
생겼고 fan 두 개는 거기로 갔다.** 주석은 «PR-time» 만 배제하는데, 실제로 남은 선택지는
**nightly** 였고 이 파일만 그 이주에서 빠졌다. 🔴 **주석이 여전히 참으로 읽혀서 아무도 다시
묻지 않았다.** [[feedback_two_correct_exclusions_compose_into_a_hole]]

## ⑥ 🔵 곁가지 — 저장소에 적힌 **증거 URL 들이 도달 불가**가 됐다

`kanggle-fan.vercel.app/api/auth/providers` 류의 URL 이 여러 티켓·문서에 **실측 근거**로 적혀
있는데 지금은 전부 404 다. 🔴 **기록이 틀린 것은 아니다**(그때는 그 주소로 쟀다) — 그러나
**그 근거를 다시 확인하려는 사람은 «없음» 을 만난다.** 이 티켓은 그 URL 들을 **고치지 않는다**
(동결된 기록이다). 대신 **살아 있는 가드/절차가 그 주소를 쓰는 곳만** 고친다.
[[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]

---

# Scope

**In:**

- `infra/demo/aws/site/check-launcher-fresh.sh` — 기본 `ORIGIN`
- `.github/workflows/nightly-e2e.yml` — 론처 레인 신설
- `.github/workflows/ci.yml` — ⑤의 낡은 주석(«manual tools … no PR-time job runs them»)

**Out:**

- 🔴 **동결된 티켓/문서에 증거로 적힌 `*.vercel.app` URL** — 그 날짜의 사실이므로 고치지 않는다(⑥)
- `check-fan-*` 두 가드 — **이미 옳다.** 건드리지 마라
- `*.vercel.app` 별칭이 **왜** 죽었는지 — Vercel 대시보드 사안이고 이 티켓의 판정에 필요 없다
  (필요한 것은 «무엇을 가리켜야 하는가» 이고 그 답은 정본 표에 있다)
- `store.hubwang.com` 연결 — 소유자, `TASK-MONO-582`

---

# Acceptance Criteria

## AC-0 — 🔴 착수 시 **다시 잰다** (verify-then-act)

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://kanggle-portfolio.vercel.app   # 기대 404
curl -s -o /dev/null -w "%{http_code}\n" https://hubwang.com                    # 기대 200 (양성 대조군)
```

🔴 **`.vercel.app` 이 되살아나 있으면 STOP** — 그러면 이 티켓의 전제(«별칭은 안 쓴다»)가
바뀌고, «기본값을 무엇으로 둘 것인가» 를 다시 정해야 한다.
🔴 **양성 대조군 없이 404 하나만 보고 시작하지 마라** — 네트워크·캡티브 포털이 같은 숫자를 낸다.

## AC-1 — 기본 `ORIGIN` 은 **정본 표에서 파생한다**, 하드코딩하지 않는다

`TEMPLATE.md` § 공개 호스트명 배분의 **launcher 행**이 정본이다.
`scripts/check-public-domains.sh` 가 이미 그 구간(`PUBLIC-HOSTNAMES-BEGIN/END`)을 파싱해
launcher 호스트를 뽑는 함수를 갖고 있으므로 **같은 방식**을 쓴다.

🔴 **하드코딩한 오리진은 이 결함을 정확히 재생산한다** — 도메인이 또 바뀌면 같은 자리에서
같은 방식으로 죽는다. `check-public-domains.sh` 헤더가 그 이유를 이미 적어 두었다:
*"하드코딩한 모집단을 쓰는 가드는 대상이 바뀌어도 자기가 적어둔 것을 계속 통과시킨다."*
[[feedback_declaration_files_are_not_the_runtime_state]]

🔵 파생이 실패하면(앵커 깨짐 등) **fail-closed** — 조용히 옛 기본값으로 떨어지지 마라.

## AC-2 — 🔴 **nightly 러너를 붙인다. 형제 레인을 그대로 따른다**

`nightly-e2e.yml` 의 fan 레인이 이미 모양을 정해 두었다 — **베끼되 생각 없이 베끼지 마라**:

- `fetch-depth: 0` — **필수**. 얕은 클론은 «트리거 경로를 마지막으로 바꾼 커밋» 을 못 구해
  가드가 표면과 무관한 이유로 `exit 2` 한다(fan 레인이 그 이유를 주석으로 적어 두었다)
- **`--self-test` 를 먼저** 돌린다 — 판정자가 눈이 있는지부터 증명한다(②)
- 그 다음 `--origin <파생된 launcher 호스트>` 로 라이브 판정
- 🔴 두 스텝이 **서로를 가리지 않게** 한다(fan 레인의 `if: ${{ !cancelled() }}` 규약)

🔴 **«어디서 도는가» 를 먼저 정하라** — 이 저장소는 러너 없는 스위트로 이미 데였고,
이 티켓 자체가 그 사례다. [[feedback_two_correct_exclusions_compose_into_a_hole]]

🔵 **nightly 가 맞는 자리인 이유**: PR 타임에는 라이브 오리진이 없다는 `ci.yml` 의 원래 판단이
**여전히 옳다**. 바뀐 것은 «그래서 아무 데서도 안 돈다» 가 아니라 **«nightly 가 생겼다»** 이다.

## AC-3 — 🔴 **bite**: 러너가 실제로 무는지 증명한다

«초록» 만 보면 «아무것도 안 재고 통과» 와 구별되지 않는다.

- 🔴 **자가검사가 러너 안에서 `0/1` 로 갈리는 것**을 확인한다(현재기준=0 신선 / 이전판기준=1 낡음)
- 🔴 **오리진을 일부러 죽은 값으로 준 사본**이 `rc=2`(판정 불가)를 내는 것을 확인한다 —
  «404 를 낡음으로 오독하지 않는다» 는 이 가드의 핵심 성질이고, 그것이 살아 있어야 한다

## AC-4 — `ci.yml` 의 낡은 주석을 **고친다**

⑤의 *"no PR-time job runs them"* 은 여전히 참이지만, 그 문장이 **«그러므로 아무 데서도 안
돈다»** 로 읽혀 이 결함을 3개월 가렸다. **어디서 도는지**를 명시한다(fan 둘 + 론처 = nightly).

🔴 **`bash -n` 게이팅은 그대로 둔다** — 그 판단은 옳았고, 러너가 생겨도 문법 게이트는 여전히
값을 한다.

## AC-5 — 이 티켓이 **안 고치는 것**을 적는다

동결된 기록의 `*.vercel.app` URL(⑥)과 `store.hubwang.com` 미연결(582)은 범위 밖이다.
🔴 산문으로 흘리지 말고 표로 남긴다 — 안 적으면 «가드를 고쳤다» 가 «Vercel 주소 문제를
고쳤다» 로 읽힌다. [[feedback_a_partial_deletion_reads_as_a_total_one]]

---

# Related Specs

- `infra/demo/aws/site/check-launcher-fresh.sh` — 대상
- `projects/fan-platform/web/fan-platform-web/check-fan-fresh.sh` — 따라야 할 형태
- `projects/fan-platform/web/fan-platform-web/check-fan-guard-live.sh` — 자가검사 규약
- `scripts/check-public-domains.sh` — 정본 표 파생 방식(AC-1)
- `TEMPLATE.md` § 공개 호스트명 배분 — launcher 행이 정본
- `.github/workflows/nightly-e2e.yml` — 러너의 집
- `.github/workflows/ci.yml` — 낡은 주석 + `bash -n` 게이팅
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

없음 — API/이벤트 계약을 바꾸지 않는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 착수 시 `.vercel.app` 이 되살아나 있다 | 🔴 **STOP** — 전제가 바뀐다. AC-0 |
| `hubwang.com` 이 그날 죽어 있다 | 🔴 가드는 `rc=2`(판정 불가)를 내야 한다. **낡음으로 오독하면 안 된다** — AC-3 의 두 번째 칸이 그것을 잰다 |
| 정본 표 앵커가 깨져 파생이 0건 | 🔴 **fail-closed.** 0건은 «위반 없음» 이 아니라 **파싱이 깨진 것**이다(`check-public-domains.sh` 가 같은 문장을 갖고 있다) |
| nightly 가 그날 안 돌았다 | 🔵 이 가드는 «머지 게이트» 가 아니다. 낡음은 시간이 지나야 생기므로 nightly 주기로 충분하다 — **그러나 그 사실을 적어라**(머지 시점에 안 잡힌다) |
| 론처가 실제로 낡아서 빨강 | 🔵 **가드가 일한 것이다.** 원인은 배포이지 가드가 아니다 — 스크립트가 이미 그 문장을 갖고 있다 |
| `store`/`console` 이 나중에 같은 종류의 가드를 갖는다 | 🔵 그때 이 레인이 **모집단을 발견**하게 만들지, 세 번째 하드코딩을 더하지 마라 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| 기본 `ORIGIN` 만 바꾸고 러너를 안 붙인다 | 여전히 **아무도 안 돌린다** — 오늘과 같은 상태 | AC-2 |
| 러너만 붙이고 오리진을 하드코딩한다 | 도메인이 또 바뀌면 **같은 자리에서 같은 방식으로** 죽는다 | AC-1 |
| 자가검사 없이 라이브 칸만 돌린다 | 판정자가 눈이 없어도 초록 | AC-2·AC-3 |
| 404 를 «낡음» 으로 처리하도록 «고친다» | 🔴 **오리진이 죽은 날 «배포가 낡았다»** 로 오보 — 진단이 엉뚱한 곳으로 간다 | AC-3 두 번째 칸이 이 성질을 핀으로 잡는다 |
| PR 타임 잡으로 옮긴다 | 라이브 오리진이 없어 **매 PR 이 `rc=2`** | AC-2 🔵 nightly 가 맞는 자리다 |
| 「가드를 고쳤다」로 ⑥을 덮는다 | 동결 기록의 죽은 URL 은 그대로인데 해결로 기록됨 | AC-5 표 |

---

# 🔵 참고 — 이 티켓이 **긴급하지 않은** 이유, 그리고 그래도 필요한 이유

가드는 **fail-closed** 라 틀린 초록을 낸 적이 없다(③). 그리고 오늘 실측으로 **론처는 실제로
신선하다**(①). ⇒ 지금 숨은 손해는 **0** 이다.

🔴 **그래도 필요한 이유**: 이 가드가 지키려는 것은 *"배포가 조용히 낡아 방문자가 옛 판을
계속 본다"* 이고, 그것은 **아무 색깔도 내지 않는 종류의 손해**다. 지금 상태는 그 방어가
**꺼져 있는데 꺼진 줄 아무도 모르는** 상태다 — `TASK-MONO-562` 가 *"진짜 피해는 색깔이 아니라
그동안 론처가 낡은 판을 계속 서빙한 것"* 이라 적은 바로 그 축이다.

분석=**Opus 5** / 구현 권장=**Sonnet** (형태가 형제 레인에 이미 다 정해져 있다 — 판단이 아니라
이식이 본체다. 🔴 다만 AC-1 의 «정본 표 파생» 과 AC-3 의 bite 두 칸은 베끼기가 아니다).

---

# ✅ 구현 (2026-09-01)

## ✅ AC-0 — 전제는 그대로였다 (양성 대조군 포함)

```
https://kanggle-portfolio.vercel.app  = 404      ← 여전히 죽었다
https://hubwang.com                   = 200 / 29 KB   ← 양성 대조군
```

⇒ STOP 조건(별칭이 되살아남)에 해당하지 않는다. 🔵 두 줄을 **같이** 찍은 이유는 티켓이 적은
그대로다 — 네트워크·캡티브 포털이 404 하나를 그대로 낸다.

## 🔴🔴 착수하자마자 **세 번째 결함**이 나왔다 — 이 티켓의 논지가 자기 자신에게 적중했다

기본 오리진을 고치고 처음으로 인자 없이 돌려 봤더니, 오리진이 아니라 **다른 데서** 죽었다:

```
[launcher-fresh] ✖ vercel.json 에서 ':/...' pathspec 을 하나도 못 뽑았습니다 ⇒ 판정 불가
```

**`TASK-MONO-607` 이 이 프로젝트의 pathspec 을 `vercel.json` 인라인에서 `vercel-ignore.sh`
래퍼로 뽑아냈다.** 그 자체는 형제 둘의 모양을 따른 **옳은 변경**이다. 그런데 이 판정자는
`vercel.json` 을 grep 하고 있었으므로 **그날부터 판정 불가**였고 —
🔴 **아무 데서도 발화하지 않았다. 러너가 없었기 때문이다.**

⇒ 이 티켓의 두 결함(죽은 오리진 · 러너 없음)이 **세 번째를 낳았다.** 그리고 그 인과는
반대로도 성립한다: **이 레인이 있었다면 607 의 PR 에서 즉시 빨간불**이었을 것이다.

🔵 **답은 형제에 있었다** — `check-fan-fresh.sh` 는 처음부터 `$IGNORE_WRAPPER` 에서 읽는다.
소비자 전수(`grep -o "':/[^']*'"` 를 쓰는 곳 5군데)를 세어 낙오한 것이 이 파일 하나임을 확인했다.
[[feedback_grep_the_siblings_before_fixing_it_yourself]] [[feedback_deletion_leaves_survivors_grep_the_consumers]]

## ✅ AC-1 — 기본 오리진을 **정본 표에서 파생**한다. 🔴 파서는 복사하지 않았다

`TEMPLATE.md` § 공개 호스트명 배분의 launcher 행이 정본이고, 그것을 파싱하는 코드는
`scripts/check-public-domains.sh` 에 **이미 있었다**(`table_rows` / `launcher_host`,
그리고 «행 전체를 grep 하면 안 된다» 는 사고 기록까지 주석으로).

🔴 **여기로 복사하지 않았다.** 대신 그 스크립트에 **조회 모드 `--print-launcher-host`** 를
붙이고(가드 본체 실행 전에 빠져나온다), 판정자는 그것을 부른다. **정의는 한 곳에 남는다.**

```
$ bash scripts/check-public-domains.sh --print-launcher-host
hubwang.com
$ bash infra/demo/aws/site/check-launcher-fresh.sh
[launcher-fresh] 기본 오리진을 정본 표에서 파생: https://hubwang.com
… ✔ 신선 — 서빙 중인 바이트가 origin/main 과 같습니다.     rc=0
```

🔵 **fail-closed 를 실측했다** — 파생을 일부러 깨뜨리면(`PUBDOM_GUARD_ROOT=/nonexistent`)
옛 기본값으로 떨어지지 않고 **rc=2(판정 불가)** 이고, 메시지가 *"옛 기본값으로 떨어지지
않습니다 — 그렇게 하면 이 가드가 죽은 주소를 다시 가리키면서 초록으로 보일 것"* 을 말한다.

## ✅ AC-2 — nightly 레인 신설. 형제를 따랐다

`nightly-e2e.yml` 에 `launcher-freshness-watch` 를 넣었다. fan 레인의 규약을 그대로:
`fetch-depth: 0` · **자가검사 먼저** · 라이브 판정 · 두 스텝이 서로를 못 가리게 `!cancelled()`.

🔵 **AC-2 문구에서 한 가지 벗어났고, 강해지는 쪽이다.** AC-2 는 라이브 스텝을
`--origin <파생된 launcher 호스트>` 로 부르라고 적었는데, **인자 없이** 불렀다 —
AC-1 이 기본값을 그 파생값으로 만들었으므로 **값은 같고**, 추가로 **파생 경로 자체가
러너에서 검증된다**. 파생이 깨지면 그 스텝이 그것을 말한다.

## ✅ AC-3 — bite. 🔴 세 값이 **전부** 갈린다

| 입력 | rc | 무엇 |
|---|---|---|
| `--self-test` (같은 오리진, 두 기준) | **0** | `현재기준=0 신선 / 이전판기준=1 낡음` — 판정자가 눈이 있다 |
| `--origin https://kanggle-portfolio.vercel.app` (죽은 주소) | **2** | *"최종 HTTP 404 ⇒ **판정 불가**(낡음이 아니다)"* |
| `--origin https://store.hubwang.com` (**살아 있지만 다른 사이트**) | **1** | *"낡음 — 서빙 중인 바이트가 origin/main 과 다릅니다"* |

🔵 **셋째 줄이 이 표를 대조군으로 만든다.** 404 만 봤다면 «2 를 내는 것»과 «아무 때나 2 를
내는 것»이 구별되지 않는다. 살아 있는 다른 오리진이 **1** 을 내므로, `2` 는 «못 봤다» 를,
`1` 은 «봤는데 다르다» 를 뜻한다는 것이 실측으로 갈린다.

## ✅ AC-4 — `ci.yml` 의 낡은 주석을 고쳤다

원문은 *"They are **manual tools** (they need the live origin, so no PR-time job runs them)"*.

🔵 **괄호 안은 지금도 참이다** — PR 타임에 라이브 오리진은 없다. 🔴 **틀린 것은 «그러므로
manual» 이라는 결론**이고, 그것은 `nightly-e2e.yml` 이 생기기 전까지만 참이었다. fan 둘은
거기로 갔고 론처만 남았는데, **문장이 여전히 참으로 읽혀서 아무도 다시 묻지 않았다.**

⇒ **어디서 도는지**를 명시했다(fan 둘 + 론처 = nightly). 🔵 `bash -n` 게이팅은 **그대로 뒀다** —
그 판단은 옳았고, 문법 오류를 nightly 가 하루 뒤에 발견하는 것보다 PR 에서 잡는 게 낫다.

## ✅ AC-5 — 이 티켓이 **안 고치는 것**

| # | 안 고치는 것 | 왜 |
|---|---|---|
| 1 | 동결된 티켓·문서에 증거로 적힌 `*.vercel.app` URL | 🔴 **그 날짜의 사실이다.** 지금 404 인 것은 기록이 틀려서가 아니다. 고치면 그때 무엇을 쟀는지가 사라진다 |
| 2 | `*.vercel.app` 별칭이 **왜** 죽었는지 | Vercel 대시보드 사안이고 이 판정에 필요 없다 — 필요한 것은 «무엇을 가리켜야 하는가» 이고 답은 정본 표에 있다 |
| 3 | `store.hubwang.com` 연결 | 소유자 · `TASK-MONO-582` |
| 4 | 🔴 **다른 «단일 출처 이동»이 또 있는지** | 이 티켓은 `':/...'` pathspec 소비자 **5곳**만 셌다. 다른 종류의 출처 이동은 안 봤다 |
| 5 | `check-fan-*` 두 가드 | **이미 옳다.** 안 건드렸다 |

🔴 **«가드를 고쳤다»가 «Vercel 주소 문제를 고쳤다»로 읽히면 안 된다** — 죽은 별칭은 그대로 죽어 있고,
이 티켓이 한 일은 **판정자가 그것을 안 가리키게** 한 것이다.

## 🔵 곁가지 — 개행을 한 번 섞었다가 잡았다

`nightly-e2e.yml` 은 **CRLF** 파일인데 새 레인을 LF 로 끼워 넣어 **bare-LF 67줄**이 생겼다.
쓰고 나서 개행을 세어 보다 발견해 전부 CRLF 로 되돌렸다(`.yml`=CRLF, `.sh`=LF 확인).
🔴 산출물을 세지 않았으면 리뷰에 안 보이는 diff 로 남았을 것이다.

## 검증

- `check-launcher-fresh.sh` — 라이브 **0/1/2** 세 방향 + `--self-test` rc=0 + fail-closed rc=2
- `check-public-domains.sh` — 라이브 rc=0 · **`--self-test` rc=0**(조회 모드를 붙였으므로 필수)
- `check-vercel-build-triggers.sh` — 라이브 rc=0
- `ci.yml` · `nightly-e2e.yml` — **YAML 파서로** 로드 확인(51잡 / 13잡), 새 잡·스텝 3개 존재
- 🔴 docker 데몬이 없어 `verify-demo-wrapper.sh` 전체는 못 돌렸다(이 티켓은 그 파일을 안 건드린다)

## CORRECTION (2026-09-01 UTC) — 🔴 § 곁가지 의 개행 주장은 **틀렸다**

위 § 곁가지 에 이렇게 적었다:

> *"`nightly-e2e.yml` 은 **CRLF** 파일인데 새 레인을 LF 로 끼워 넣어 bare-LF 67줄이 생겼다.
> … 🔴 **산출물을 세지 않았으면 리뷰에 안 보이는 diff 로 남았을 것이다.**"*

**마지막 문장이 거짓이다.** 머지 뒤 blob 을 실제로 세어 보고 알았다:

| blob | bare-LF | CRLF |
|---|---|---|
| `404f6e6ea~1` (내 변경 **전**) | **1276** | **0** |
| `404f6e6ea` (내 변경 **후**) | **1343** | **0** |

⇒ **이 저장소는 blob 을 LF 로 저장하고 체크아웃할 때 CRLF 로 준다**(작업 트리에서 계속 보이던
*"LF will be replaced by CRLF"* 경고가 그 말이었다). 즉 내가 LF 를 섞어 넣었어도 **커밋 시점에
git 이 정규화했을 것**이고, 리뷰에 이상한 diff 는 **애초에 안 생겼다.**

🔵 **작업 트리를 CRLF 로 되돌린 것 자체는 무해했다** — 다만 그것을 «사고를 막았다» 로 적은 것이
과대주장이다. 🔴 **내가 잰 것은 «작업 트리의 개행» 이고, 주장한 것은 «커밋될 내용» 이었다.**
두 축이 다르고, 그 차이를 확인하지 않은 채 결론을 적었다.
[[feedback_a_reported_figure_must_name_what_was_measured]]

🔵 **같은 문단의 다른 절반(Status 를 위치로 골랐다가 무동작)은 그대로 참이다** — 그쪽은 실제로
`Status: ready` 인 파일이 `in-progress/` 에 앉을 뻔했고, 검증이 그것을 잡았다.

🔵 **AC 판정에는 영향이 없다.** 이 문장은 AC-0~AC-5 어디에도 걸려 있지 않은 § 곁가지 의 서술이다.
