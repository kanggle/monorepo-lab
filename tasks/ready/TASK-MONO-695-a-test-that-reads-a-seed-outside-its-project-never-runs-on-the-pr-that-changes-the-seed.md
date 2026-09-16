# Task ID

TASK-MONO-695

# Title

🔴 프로젝트 밖의 시드를 읽는 테스트가 **그 시드를 바꾸는 PR 에서는 안 돈다** — PR 경로 필터는 프로젝트 디렉터리만 보고, 빨강은 머지 뒤 `main` 에서 처음 난다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- path-filter
- guard
- demo-seed

---

# Goal

`TASK-MONO-683`(2026-09-16)이 wms 테스트 `ScmInboundExpectedDemoSeedShapeDltTest#demoSeedMapping_resolvesInWmsDevSeed` 로 하여금
**저장소 루트의 `infra/demo/seed/seed-scm.sh` 를 직접 읽게** 했다. 🔴 시드를 누가 wms 가 모르는 코드로 되돌리면 이 테스트가 빨개져야 한다.
그런데 **시드만 바꾸는 PR 에서는 이 테스트가 돌지 않는다.**

| 이벤트 | `Build & Test` 가 도는 조건 (`.github/workflows/ci.yml`) | `seed-scm.sh` 만 바꾸면 |
|---|---|---|
| PR | 프로젝트 플래그 중 하나 — `wms: 'projects/wms-platform/**'` · `iam: 'projects/iam-platform/**'` … (`ci.yml:436-449`, 조건 `:2527-2539`) | **SKIPPED** |
| `main` push | `code-changed != 'false'` (`**/*.sh` 포함, `ci.yml:952-977`) | 돈다 |

⇒ **PR 은 초록으로 머지되고, 빨강은 `main` 에 처음 나타난다.** 저장소가 이미 두 번 밟은 부류다(메모리 «경로필터 잡은 다음 머지가 그 경로를 안 건드리면 skipped»).

🔵 **추론이 아니라 표본이 있다** (2026-09-16 UTC 실측): PR [#3246](https://github.com/kanggle/monorepo-lab/pull/3246) 은 `infra/demo/seed/seed-scm.sh` · `seed-finance.sh` (+ 티켓 1개) 만 바꿨다 —
PR 런 `Build & Test (JDK 21, Linux)` = **SKIPPED**, 그 머지 커밋 `79287899b` 의 `main` 런 = **success**(돌았다). 당시엔 시드를 읽는 테스트가 없어 무해했을 뿐이다.

🔴 **같은 모양이 하나 더 있고, 그쪽은 이미 «소유자 없음» 으로 기록돼 있었다.** `projects/iam-platform/apps/auth-service/build.gradle:109` 가
`infra/demo/seed/seed-fan.sh` 를 테스트 입력으로 선언하고, 같은 파일 `:93-104` 가 이 틈을 스스로 적었다 — *"`iam` path filter is
`projects/iam-platform/**`, so a PR touching ONLY infra/demo/seed/seed-fan.sh does not wake this lane at all … THIS GAP IS UNOWNED … It needs
its own ticket; it does not have one."* `docs/adr/ADR-MONO-063-artist-directory-write-plane.md:187` 도 같은 항목을 **«미결·무소유»** 로 남겼다.
⇒ **이 티켓이 그 소유자다.** `TASK-MONO-683` 은 모르고 같은 모양을 wms 에 하나 더 만들었다 — 「무소유」 기록이 산문에만 있고 게이트가 없어서다.
(🔵 ACCEPTED ADR 본문은 고치지 않는다. `build.gradle` 주석은 이 기안 PR 에서 «`TASK-MONO-695` 가 소유한다» 로 고쳤다 — «티켓이 없다» 는 이 PR 머지 순간 거짓이 되기 때문이다.)

## 모집단 (2026-09-16 UTC, `origin/main` `34bf34d7d` 계열 트리)

`build.gradle` 의 `inputs.(file|dir)(rootProject.file('…'))` 선언 **8건**:

| 모듈 | 입력 | 그 모듈의 PR 필터 안인가 |
|---|---|---|
| `wms-platform/apps/inbound-service` `:75` | `infra/demo/seed/seed-scm.sh` | 🔴 **밖** |
| `iam-platform/apps/auth-service` `:109` | `infra/demo/seed/seed-fan.sh` | 🔴 **밖** |
| `iam-platform/apps/auth-service` `:106,116,133,136,139,142` (6건) | 전부 `projects/iam-platform/**` | 🟢 안 |

🔴 **이 표는 «선언된» 입력만 센다.** 모듈 밖 파일을 읽으면서 `inputs` 선언을 **안 한** 테스트는 여기 안 잡힌다 — `TASK-MONO-683` 이 바로 그 상태(선언 없음 → `UP-TO-DATE` 로 거짓 초록)를 실측했다. AC-1 이 그 모집단까지 센다.

---

# Scope

## 포함

- 모듈 밖 파일을 읽는 테스트의 **전수**(선언된 것 + 선언 안 된 것).
- 그 파일을 바꾸는 PR 에서 그 테스트가 **돌게** `ci.yml` 경로 필터를 고친다(🔴 순수-양성 패턴만 — 부정 패턴 금지, `CLAUDE.md` § CI path-filter).
- 같은 틈이 다시 생기지 않게 하는 가드: «선언된 모듈 밖 테스트 입력은 그 모듈이 속한 프로젝트 플래그의 필터에 걸린다».

## 제외

- 선언 안 된 모듈 밖 읽기를 **선언으로 고치는 일** 자체 — 찾으면 이 티켓에 목록으로 적고, 고치는 것은 각 프로젝트 티켓으로(받는 쪽 행을 실제로 만든다).
- nightly 전용 스위트의 경로 조건.
- 필수 체크 셋(`scripts/required-check-names.txt`) 변경 — 🔴 잡 이름을 바꾸면 필수 체크 매칭이 조용히 깨진다(`CLAUDE.md`). 이름은 건드리지 않는다.

---

# Acceptance Criteria

- [ ] **AC-0 — 재측정.** 위 «모집단» 표와 `ci.yml` 줄 번호(`if:` `:2527-2539` · 프로젝트 필터 `:436-449` · `code-changed` `:952-977`)를 **그날의 `origin/main`** 에서 다시 잰다(숫자를 물려받지 마라). PR #3246 표본은 역사라 다시 재지 않아도 된다.
- [ ] **AC-1 — 모집단을 둘 다 센다.** ① `inputs.(file|dir|files)(rootProject.file(…))` 전수 ② 테스트 소스(`src/test/**`)가 **선언 없이** 모듈 밖 경로(`infra/` · 저장소 루트 상대 경로 · `../..` 류)를 읽는 자리 전수. 🔴 0건 판정엔 양성 대조군을 넣는다(① 의 `seed-scm.sh` 를 읽는 테스트가 ② 의 술어에도 걸려야 한다).
- [ ] **AC-2 — 고친다.** ① 의 «밖» 행 전부가 그 모듈 프로젝트의 PR 플래그를 켜게 `ci.yml` 필터에 **정확한 파일 경로**(글롭 확장 최소)를 더한다. 🔴 `demo-wrapper` 같은 다른 플래그에 얹어 Build & Test 조건을 넓히지 말고, **그 테스트가 속한 프로젝트 플래그**에 둔다 — 그래야 필요한 모듈만 돈다.
- [ ] **AC-3 — 가드.** `scripts/` 에 «선언된 모듈 밖 테스트 입력 ⊆ 그 프로젝트 필터» 를 재는 가드를 둔다(또는 기존 가드 확장). `ci.yml` 의 필터를 **파싱해서** 판정하고, 경로를 손으로 복제하지 않는다. bite: AC-2 의 한 줄을 되돌리면 그 모듈:줄을 지목하며 rc≠0, 복원하면 rc=0. 🔴 `scripts/` 에 파일을 더하면 **전체 가드**를 돌린다(`check-ls-files-guard-count.sh` 분모가 움직인다).
- [ ] **AC-4 — PR 에서 실제로 돈다.** `seed-scm.sh` 만 (주석 한 줄 등 무해하게) 바꾸는 시험 PR 을 열어 `Build & Test` 가 **SKIPPED 가 아님**을 롤업으로 확인하고 닫는다(머지하지 않는다). 🔴 이 칸을 «필터를 읽으니 걸린다» 로 닫지 마라 — 이 티켓이 생긴 이유가 «읽기와 실행이 달랐다» 다.
- [ ] **AC-5 — 무소유 기록을 닫는다.** 고친 뒤 `auth-service/build.gradle` 의 «Residual gap» 주석과 `inbound-service/build.gradle:70-78` 주석이 **틈이 닫혔다(가드 이름)** 로 바뀐다. `ADR-MONO-063:187` 은 ACCEPTED 본문이라 고치지 않는다 — ADR 정정 규약(`docs/adr/INDEX.md` · CORRECTION 절)이 있으면 그 규약대로 «`TASK-MONO-695` 로 해소» 를 덧붙인다(규약을 먼저 읽는다).

---

# Related Specs

- `.github/workflows/ci.yml` — `changes` 잡의 `dorny/paths-filter` · `Build & Test` 의 `if:`
- `CLAUDE.md` § CI path-filter (순수-양성 규칙) · § Merge verification (필수 체크 이름은 등호 매칭)
- `tasks/in-progress/TASK-MONO-683-the-seed-maps-skus-to-a-supplier-uuid-that-wms-resolves-as-a-code.md` — 발견 경위(시드를 읽는 테스트 + `inputs.file` 선언)
- `projects/wms-platform/apps/inbound-service/build.gradle:70-78` · `projects/iam-platform/apps/auth-service/build.gradle:93-145`
- `docs/adr/ADR-MONO-063-artist-directory-write-plane.md:187` — 같은 틈을 «미결·무소유» 로 기록

# Related Contracts

- 없음 — CI 구성과 가드다.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 한 시드를 여러 프로젝트의 테스트가 읽는다 | 읽는 프로젝트 플래그 **전부**에 더한다 — 가드는 (입력, 프로젝트) 쌍마다 판정 |
| 입력이 디렉터리(`inputs.dir`) | 필터는 `dir/**` 로 걸린다 — 가드는 디렉터리 입력을 접두사 매칭으로 판정 |
| 입력 경로가 이미 그 프로젝트 안 | 할 일 없음(현재 6건) — 가드가 통과시켜야 하는 **음성 대조군** |
| `platform-console` 처럼 프로젝트 필터가 프로젝트 전체가 아니다 | 가드는 «프로젝트 = 이름» 이 아니라 **실제 필터 목록**으로 판정 |
| 필터가 `code-changed` 와 AND 결합(`ci.yml:164`) | `.sh` 는 `code-changed` 에 있으므로 AND 로 꺼지지 않는다 — AC-0 에서 확인 |

# Failure Scenarios

1. **`infra/demo/**` 를 wms 필터에 통째로 넣는다** → 데모 README 한 줄에도 wms 전체가 돈다. 정확한 파일만.
2. **필터만 고치고 가드를 안 둔다** → 다음에 `inputs.file` 을 더하는 사람이 같은 틈을 다시 만든다(683 이 이번에 그렇게 만들었다).
3. **AC-4 를 필터 읽기로 닫는다** → 필터 문법·AND 결합·잡 조건 중 하나만 틀려도 여전히 SKIPPED 다.
4. **선언 안 된 모듈 밖 읽기(AC-1 ②)를 안 센다** → 가드가 «선언된 것» 만 지키고, 선언 안 한 테스트는 필터와 캐시 **둘 다**에서 빠진다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (필터 두 줄 + 가드 하나 + 시험 PR. 판단은 AC-1 ② 의 술어)
