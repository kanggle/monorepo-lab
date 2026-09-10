# Task ID

TASK-MONO-657

# Title

🔴 **지원자가 건네는 사본 7개가 낡았다** — 어제 배선한 스크린샷 12장도, 어제 쓴 포트폴리오 문서도 그 사본엔 없다

# Status

in-progress

# Owner

monorepo

# Task Tags

- portfolio
- docs
- ops

---

# Goal

`scripts/sync-portfolio.sh` 가 만드는 **독립 리포 사본**이 모노레포와 얼마나 벌어져 있는지를
소유자 앞에 놓고, **동기화를 돌릴지 말지를 결정**한다. 그리고 `TASK-MONO-651` 이 열어 둔 채
닫힌 질문 하나 — **포트폴리오 문서의 언어** — 에 답을 받는다.

🔴 **이 티켓은 «돌려라» 가 아니다.** `sync-portfolio.sh` 는 대상 리모트에 **force-push** 한다
(스크립트 머리말 § Strategy 4단계). 그것은 `CLAUDE.md` § Git 규율이 **명시적 승인**을 요구하는
축이다. AC-0 이 그 게이트다.

---

# 🔴 왜 이 티켓이 있나 — 두 티켓이 이것을 들고 있다가 `done/` 으로 갔다

`TASK-MONO-650`(README 스크린샷 12장 배선)과 `TASK-MONO-651`(지원용 포트폴리오 문서)이
2026-09-10 UTC 에 닫혔다. 둘 다 **자기 AC 는 전부 닫고**, 각자 발견한 사실 하나씩을
본문에만 적어 두었다:

| 어디서 | 무엇을 |
|---|---|
| `TASK-MONO-651` § 구현 | 독립 리포 5개가 **2026-08-04** 에 멈춰 있고 erp·finance 는 **부트스트랩 상태**다. ⇒ 문서의 링크를 전부 **모노레포 경로**로 걸어서 회피했다 |
| `TASK-MONO-651` § Edge Cases | *"읽는 사람이 한국어를 못 읽는다 — 🔵 이 문서의 언어를 소유자가 정해야 한다(AC-0 에 붙일 수 있는 **두 번째 질문**이다)"* |

🔴🔴 **둘 다 «그 티켓의 AC» 가 아니었다.** 그래서 651 은 AC 를 100% 닫은 채로 옳게 닫혔고,
이 둘은 **아무 큐에도 없이** 남았다. `done/` 은 frozen 이므로 거기 적힌 채로는 다시 읽히지
않는다 — 이 저장소가 `TASK-MONO-537` 에서 정확히 그 모양으로 9일을 잃은 적이 있다
(*"그 티켓의 시드 AC 가 패치 인계된 뒤 티켓이 `done` 으로 닫혔고, **이어받는 티켓이 어느
큐에도 없었다**"*). 이 티켓이 그 집이다.

---

# 🔴 실측 (2026-09-10 07:00 UTC · REST `pushed_at`)

| 사본 | 마지막 푸시 | 벌어진 날 | 크기 | 판정 |
|---|---|---|---|---|
| `wms-platform` | 2026-08-04T10:22Z | **37일** | 6,120KB | 🔴 낡음 |
| `iam-platform` | 2026-08-04T10:54Z | **37일** | 7,284KB | 🔴 낡음 |
| `ecommerce-microservices-platform` | 2026-08-04T11:29Z | **37일** | 9,241KB | 🔴 낡음 |
| `fan-platform` | 2026-08-04T11:57Z | **37일** | 3,761KB | 🔴 낡음 |
| `scm-platform` | 2026-08-04T12:18Z | **37일** | 3,621KB | 🔴 낡음 |
| `erp-platform` | 2026-05-19T10:01Z | **114일** | **647KB** | 🔴🔴 부트스트랩 상태 |
| `finance-platform` | 2026-05-19T03:42Z | **114일** | **647KB** | 🔴🔴 부트스트랩 상태 |
| `platform-console` | — | — | — | 🔵 **리포가 없다. 정상이다** |

🔵 **`platform-console` 은 결함이 아니다.** `PROJECT_REMOTES` 에 등록돼 있지 않고(스크립트
38–46행, 7개만 등록), 루트 `README.md` 도 *"(monorepo-only)"* 라고 적는다. **셋이 일치한다.**

🔴 **`erp` · `finance` 의 647KB 는 «작은 프로젝트» 가 아니라 «내용이 없다» 는 뜻이다** —
형제 다섯이 3.6~9.2MB 다. `ADR-MONO-008` 17단계와 `ADR-MONO-016` 이 그 둘을 `PROJECT_REMOTES`
에 등록만 해 두고 **한 번도 실제 추출을 돌리지 않았다.**

## 🔴 술어 함정을 기록해 둔다 — 651 이 여기서 한 번 틀렸다

`gh api repos/... --jq .pushedAt` 는 **전부 `-`** 를 낸다. `pushedAt` 은 **GraphQL 이름**이고
REST 는 `pushed_at` 이다. 651 이 이것을 「푸시 기록이 없다」로 읽을 뻔했고, **대조군**
(`wms` — 내용이 있는 것이 확실한 리포)이 같은 `-` 를 낸 것이 오답을 잡았다.
⇒ 다시 잴 때 **`pushed_at`** 을 쓰고, **내용이 확실한 리포를 대조군으로** 같이 재라.

---

# 🔴 무엇이 사본에 없는가 — 「낡았다」를 구체적인 손실로 적는다

2026-08-04 이후 모노레포에 들어간 것 중 **지원 자료에서 보이는 것**:

1. **서비스 README 스크린샷 12장**(`TASK-MONO-650`, 2026-09-09). erp 3 · iam 2 · finance 1 ·
   fan 4 · ecommerce 2. 🔴 사본의 README 에는 그 절이 **아예 없다.**
2. **`docs/portfolio.md`**(`TASK-MONO-651`, 2026-09-09) — 이것은 루트 문서라 애초에 사본으로
   안 간다. 🔵 손실이 아니라 **경계**다.
3. 🔴 **README 의 CI 배지가 사본 리포의 Actions 를 가리킨다.** 사본이 5주 안 돌았으므로
   그 배지는 **5주 전 실행**을 보여준다. 방문자에게 그것은 「이 프로젝트는 8월에 멈췄다」로
   읽힌다 — 사실이 아니다.

---

# Scope

## 포함

- 위 표를 **다시 재고**(수치는 낡는다) 소유자에게 제출
- 🔴 **소유자 승인 후에만**: `sync-portfolio.sh --dry-run` → 실제 동기화
- `TASK-MONO-651` 이 남긴 **언어 질문**에 소유자 답을 받아 적기
- 답이 「영어판이 필요하다」면 **그 작업의 티켓을 내는 것까지**(여기서 쓰지 않는다)

## 제외

- 🔴 **`sync-portfolio.sh` 자체를 고치는 일.** 안 돌려 봤으므로 고장났는지 **모른다** —
  dry-run 이 실패하면 그때 별도 티켓이다. 미리 고치면 없는 결함을 고치는 것이다.
- 🔴 **낡음을 무는 가드를 다는 일.** 그것은 「사본을 계속 유지한다」는 결정이 선행이고,
  그 결정이 AC-0 의 답이다. 🔵 결정이 «유지» 로 나오면 후속 티켓으로 낸다.
- 🔴 **`platform-console` 을 `PROJECT_REMOTES` 에 등록하는 일** — 그것은 「이 프로젝트를
  추출할 것인가」라는 별도 결정이고, 지금 셋(스크립트 · README · 실제 리포)이 **일치**한다.
- 포트폴리오 문서·README 의 **내용**을 고치는 일 — 650·651 이 닫았다.

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act, 두 겹)

- [x] 🔴🔴 **① 소유자가 «동기화를 돌려라» 라고 명시적으로 말했는가.** 안 말했으면 **STOP** —
      no-op 이 올바른 구현이다. `sync-portfolio.sh` 는 **force-push** 한다(스크립트 머리말
      § Strategy 4). `CLAUDE.md` § Git 규율: *"a general 'proceed to completion' instruction
      does not authorize … `git push --force-with-lease`"*. 🔴 **「진행해라」는 이 승인이
      아니다.**
- [x] 🔴 **② 위 실측 표를 다시 재라.** 2026-09-10 값이고 반드시 낡는다. 술어는 **REST
      `pushed_at`** 이고 **내용이 확실한 리포를 대조군으로** 같이 재라(§ 술어 함정).
- [x] 🔴 **③ `--dry-run` 을 먼저 돌리고 그 출력을 본문에 적어라.** 사본은 5주,
      erp·finance 는 **114일** 벌어져 있다 — force-push 가 무엇을 덮는지 **보고 나서** 민다.

## AC-1 — 동기화 (승인된 경우에만)

- [ ] 프로젝트 **하나씩** 돌린다(`./scripts/sync-portfolio.sh <name>`). 🔴 전체 일괄로
      돌리지 마라 — 하나가 깨지면 어느 것이 깨졌는지 출력에서 갈라내기 어렵고,
      **이미 밀린 것은 되돌릴 수 없다.**
- [ ] 🔴🔴 **`erp-platform` · `finance-platform` 을 먼저 돌리지 마라.** 그 둘은 114일짜리
      **부트스트랩 사본**이라 변화폭이 가장 크다 ⇒ 스크립트가 깨진다면 거기서 깨진다.
      **형제 다섯 중 하나를 먼저** 돌려 스크립트가 오늘도 동작하는지 확인한 뒤에 간다.
- [ ] 각 사본에 대해 **무엇이 도착했는지 실측하라.** 🔴 「푸시 성공」은 판정이 아니다 —
      술어는 **«그 리포의 README 에 스크린샷 절이 있는가»** 다(650 이 배선한 5개 프로젝트에
      대해). 🔵 `wms` · `scm` 은 스크린샷이 0장인 것이 **정상**이다(650 § ⚪).
- [ ] ⚪ **못 돌린 것은 «돌렸다» 로 적지 마라.** 사유와 함께 ⚪ 로 남긴다.

## AC-2 — `TASK-MONO-651` 이 남긴 언어 질문

- [ ] 🔴 **소유자에게 묻는다**: `docs/portfolio.md` 를 한국어로 유지하는가, 영어판을
      추가하는가, 영어로 바꾸는가. 🔵 651 의 Edge Case 가 *"소유자가 정해야 한다"* 로
      명시적으로 남긴 질문이고, **내 추천을 결정으로 적지 마라**(651 Failure 2 가 그것이다).
- [ ] 답을 **`docs/portfolio.md` 본문이 아니라 이 티켓에** 적는다. 답이 「유지」면 그것으로
      끝이고, 「영어판 추가」·「영어로 교체」면 **그 작업의 티켓을 낸다**(여기서 쓰지 않는다 —
      번역은 이 티켓의 축이 아니고, 범위가 터진다).

## AC-3 — 결정을 기록이 아니라 «다음 사람이 읽는 곳» 에 남긴다

- [ ] 🔴 소유자가 **「사본을 유지한다」** 고 답했으면: `docs/portfolio.md` 맨 아래의
      *"🔴 다만 그 사본들은 지금 낡았습니다 — 마지막 동기화가 2026-08-04 …"* 문장을
      **실제 값으로 갱신**한다. 🔴🔴 **그 문장은 날짜를 손으로 들고 있고 무엇도 그것을
      못 잰다** — 갱신을 안 하면 사본은 새것인데 문서가 「낡았다」고 말한다(반대 방향의
      드리프트이고, 하필 지원자가 건네는 쪽이다).
- [ ] 🔴 소유자가 **「사본을 버린다」** 고 답했으면: 그 문장과 링크 5개를 지우는 것이 아니라
      **«더 이상 유지하지 않는다» 를 적는다.** 리포는 남아 있고 방문자는 검색으로 도달한다.
- [ ] 🔵 어느 쪽이든 **그 결정을 이 티켓 본문에 소유자의 말 그대로** 적는다.

---

# Related Specs / Contracts

- `scripts/sync-portfolio.sh` — 추출기(`PROJECT_REMOTES` 7개 · force-push)
- `docs/guides/monorepo-workflow.md` § 5 — 사용법
- `TEMPLATE.md` § Discovery → Distribution — 왜 사본이 존재하는가
- `TASK-MONO-650` — README 스크린샷 12장(사본에 없는 것 ①)
- `TASK-MONO-651` — 포트폴리오 문서. **이 티켓의 두 항목을 넘겨준 곳**
- `ADR-MONO-008` 17단계 · `ADR-MONO-016` — erp·finance 를 `PROJECT_REMOTES` 에 등록한 결정
- `TASK-MONO-537` — 「의무가 티켓과 함께 사라진」 선례(9일)

---

# Edge Cases

- **force-push 가 사본의 로컬 커밋을 덮는다** — 사본 리포에 모노레포에 없는 커밋이
  있는가. 🔴 **AC-0 ③ dry-run 전에 확인하라.** 있으면 그것은 별도 결정이다.
- **`git-filter-repo` 가 docker 컨테이너로 돈다** — 스크립트 § Requirements. 🔴 데모
  예산과 무관하지만 **docker 가 떠 있어야** 한다.
- **SHA 가 전부 바뀐다** — 스크립트가 *"SHAs change (expected)"* 라고 적는다. 사본을 이미
  클론한 사람이 있으면 그 클론은 갈라진다. 🔵 지원 자료 사본이라 실질 위험은 낮다.
- **스크린샷이 사본으로 따라가는가** — `projects/<name>/docs/screenshots/` 안에 있으므로
  `--path-rename` 으로 따라간다(`TASK-MONO-650` AC-1 이 경로를 그렇게 정한 이유다).
  🔴 **그러나 그것은 설계이고 실측이 아니다** — AC-1 의 술어가 그것을 잰다.
- **README 의 CI 배지가 사본에서 초록이 되는가** — 사본 리포에 workflow 가 따라가고 실제로
  도는가는 **안 쟀다.** ⚪ 로 남길 수 있는 항목이다.

---

# Failure Scenarios

1. 🔴🔴 **승인 없이 force-push 한다.** 되돌릴 수 없고, 이 저장소가 명시적 승인 축으로
   못 박아 둔 것이다.
2. 🔴 **dry-run 없이 114일짜리 사본에 밀어 넣는다.** 무엇이 덮이는지 모른 채 덮는다.
3. 🔴 **전체를 일괄로 돌리고 「성공」만 읽는다.** 어느 사본에 무엇이 도착했는지 판정하지
   않으면 **「푸시됐다」와 「스크린샷이 도착했다」가 같은 말이 된다.** 아니다.
4. 🔴 **언어 질문에 내가 답한다.** 651 이 그 구분을 명시적으로 남긴 이유가 있다.
5. 🔴 **동기화만 하고 `docs/portfolio.md` 의 「낡았습니다」 문장을 그대로 둔다.** 사본은
   새것인데 지원자가 건네는 문서가 「낡았다」고 말한다 — 아무 가드도 그것을 못 잰다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 스크립트를 돌리고 실측을 적는 것이 본체다.
🔴 단, **AC-0 은 소유자**이고 대리 판단이 불가능하다(force-push).

---

# 🟢 AC-0 — 착수 게이트 세 겹 전부 통과 (2026-09-10 UTC)

## ① 소유자 승인 — **범위가 좁혀졌다**

소유자의 말 그대로: **「erp·finance 둘만」**

🔵 선행 승인은 *"승인대기 셋 모두 승인할테니 추천 순서대로 진행해줘"* 였고, 그 뒤 사본
6개를 어떻게 할지 물었을 때 나온 답이 위 한 줄이다. ⇒ **`wms` · `iam` ·
`ecommerce` · `fan` 은 이번에 안 민다.** 그 넷은 37일 낡은 채로 남고, 그것이
소유자의 결정이다 — 🔴 **「나중에 마저 하겠다」로 적지 않는다**(아무도 그 문장을 못 잰다).
안 민 채로 남는 사실은 AC-3 에서 `docs/portfolio.md` 가 **직접 말하게** 한다.

## ② 재측정 — REST `pushed_at` + 대조군

🔴 티켓 본문의 표는 **낡았다.** 다시 쟀다(`gh api repos/kanggle/<r> --jq .pushed_at`,
2026-09-10 UTC):

| 사본 | 마지막 푸시 | 크기 | 본문 표 대비 |
|---|---|---|---|
| `wms-platform` | 2026-08-04T10:22:26Z | 6,120KB | 변화 없음 |
| `iam-platform` | 2026-08-04T10:54:32Z | 7,284KB | 변화 없음 |
| `ecommerce-microservices-platform` | 2026-08-04T11:29:55Z | 9,241KB | 변화 없음 |
| `fan-platform` | 2026-08-04T11:57:39Z | 3,761KB | 변화 없음 |
| `scm-platform` | **2026-09-10T13:11:34Z** | **4,183KB** | 🟢 **바뀌었다** (3,621KB → +562KB) |
| `erp-platform` | 2026-05-19T10:01:15Z | 647KB | 변화 없음 |
| `finance-platform` | 2026-05-19T03:42:37Z | 647KB | 변화 없음 |

🟢 **`scm-platform` 이 대조군 겸 캐너리다.** 어제(같은 세션) 그것 하나를 먼저 밀었고,
표에서 **날짜와 크기가 둘 다 움직였다** — 그러므로

1. **술어가 살아 있다.** 여섯이 안 움직이고 하나가 움직였으므로 이 측정은 「전부 `-`」
   같은 죽은 측정이 아니다(§ 술어 함정이 경고한 그 모양). 🔵 티켓이 요구한 *"내용이
   확실한 리포를 대조군으로"* 를 **더 강한 형태**로 만족한다 — 대조군이 아니라
   **변화한 원소**가 하나 있다.
2. **스크립트가 오늘 동작한다.** AC-1 의 🔴🔴 *"형제 다섯 중 하나를 먼저 돌려라"* 는
   **이미 충족돼 있다.** erp·finance 로 바로 갈 수 있다.

## ③ dry-run — 무엇이 덮이는지 보고 나서 민다

```
$ ./scripts/sync-portfolio.sh erp-platform --dry-run     # rc=0
[sync] Project:  erp-platform
[sync] Remote:   https://github.com/kanggle/erp-platform.git
[sync] Type:     direct-include
[sync] [dry-run] Would clone monorepo, run filter-repo, force-push to …
[sync] [dry-run] Kept paths:
             libs/ platform/ rules/ .claude/ tasks/templates/ docs/guides/
             build.gradle settings.gradle gradle/ gradlew gradlew.bat
             gradle.properties .gitignore .gitattributes .dockerignore
             .editorconfig .github/ CLAUDE.md TEMPLATE.md
             projects/erp-platform/
```
`finance-platform` 도 동일(rc=0, 마지막 줄만 `projects/finance-platform/`).

### 🟢 Edge Case 「force-push 가 사본의 로컬 커밋을 덮는가」 — 실측으로 닫았다

티켓 Edge Cases 첫 줄이 *"사본 리포에 모노레포에 없는 커밋이 있는가 — dry-run 전에
확인하라"* 다. 쟀다:

```
$ gh api repos/kanggle/erp-platform/commits --jq '.[] | .sha[0:8]+"  "+.commit.message'
fb0a763c  Initial commit          ← 이것뿐이다
$ gh api repos/kanggle/finance-platform/commits …
82ad83ba  Initial commit          ← 이것뿐이다
```

⇒ **두 사본 다 커밋이 하나뿐이고 그것은 부트스트랩 커밋이다.** force-push 가 덮는
것은 «누군가 사본에서 한 작업» 이 아니라 **빈 초기화**다. 🔵 이것은 가정이 아니라
측정이고, 그래서 이 Edge Case 는 「위험 없음」으로 닫힌다.
