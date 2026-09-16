# Task ID

TASK-MONO-695

# Title

🔴 프로젝트 밖의 시드를 읽는 테스트가 **그 시드를 바꾸는 PR 에서는 안 돈다** — PR 경로 필터는 프로젝트 디렉터리만 보고, 빨강은 머지 뒤 `main` 에서 처음 난다

# Status

in-progress

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

- [x] **AC-0 — 재측정.** 위 «모집단» 표와 `ci.yml` 줄 번호(`if:` `:2527-2539` · 프로젝트 필터 `:436-449` · `code-changed` `:952-977`)를 **그날의 `origin/main`** 에서 다시 잰다(숫자를 물려받지 마라). PR #3246 표본은 역사라 다시 재지 않아도 된다.
- [x] **AC-1 — 모집단을 둘 다 센다.** ① `inputs.(file|dir|files)(rootProject.file(…))` 전수 ② 테스트 소스(`src/test/**`)가 **선언 없이** 모듈 밖 경로(`infra/` · 저장소 루트 상대 경로 · `../..` 류)를 읽는 자리 전수. 🔴 0건 판정엔 양성 대조군을 넣는다(① 의 `seed-scm.sh` 를 읽는 테스트가 ② 의 술어에도 걸려야 한다).
- [x] **AC-2 — 고친다.** ① 의 «밖» 행 전부가 그 모듈 프로젝트의 PR 플래그를 켜게 `ci.yml` 필터에 **정확한 파일 경로**(글롭 확장 최소)를 더한다. 🔴 `demo-wrapper` 같은 다른 플래그에 얹어 Build & Test 조건을 넓히지 말고, **그 테스트가 속한 프로젝트 플래그**에 둔다 — 그래야 필요한 모듈만 돈다.
- [x] **AC-3 — 가드.** `scripts/` 에 «선언된 모듈 밖 테스트 입력 ⊆ 그 프로젝트 필터» 를 재는 가드를 둔다(또는 기존 가드 확장). `ci.yml` 의 필터를 **파싱해서** 판정하고, 경로를 손으로 복제하지 않는다. bite: AC-2 의 한 줄을 되돌리면 그 모듈:줄을 지목하며 rc≠0, 복원하면 rc=0. 🔴 `scripts/` 에 파일을 더하면 **전체 가드**를 돌린다(`check-ls-files-guard-count.sh` 분모가 움직인다).
- [ ] **AC-4 — PR 에서 실제로 돈다.** `seed-scm.sh` 만 (주석 한 줄 등 무해하게) 바꾸는 시험 PR 을 열어 `Build & Test` 가 **SKIPPED 가 아님**을 롤업으로 확인하고 닫는다(머지하지 않는다). 🔴 이 칸을 «필터를 읽으니 걸린다» 로 닫지 마라 — 이 티켓이 생긴 이유가 «읽기와 실행이 달랐다» 다. **관측 대기 — 구현 PR CI 가 돈 뒤 별도 시험 PR 로.**
- [~] **AC-5 — 무소유 기록을 닫는다.** 고친 뒤 `auth-service/build.gradle` 의 «Residual gap» 주석과 `inbound-service/build.gradle:70-78` 주석이 **틈이 닫혔다(가드 이름)** 로 바뀐다. `ADR-MONO-063:187` 은 ACCEPTED 본문이라 고치지 않는다 — ADR 정정 규약(`docs/adr/INDEX.md` · CORRECTION 절)이 있으면 그 규약대로 «`TASK-MONO-695` 로 해소» 를 덧붙인다(규약을 먼저 읽는다). **build.gradle 두 곳은 닫음. ADR 쪽은 아래 § 구현 기록 참고 — 부분 닫힘.**

---

# 구현 기록 (in-progress, 2026-09-17 UTC)

## AC-0 — 재측정 결과 (이 브랜치 tip, `origin/main` `1eb1a210c` 계열)

`ci.yml` 의 세 줄 참조는 **그대로 정확**했다 — 프로젝트 필터 블록 `:436-449`, `Build & Test` 의 `if:` `:2527-2539`, `code-changed`(`.sh` 포함) `:952-977` 전부 이 트리에서 재확인.

**`build.gradle` 줄 번호는 어긋나 있었다** — 티켓 원문의 `:109`(seed-fan.sh)·`:106,116,133,136,139,142`(6건)는 2026-09-16 이후 파일이 갈리며 밀렸다. 실측(2026-09-17):

| 모듈 | 입력 | 실제 줄 | 안/밖 |
|---|---|---|---|
| `wms-platform/apps/inbound-service` | `infra/demo/seed/seed-scm.sh` | `:75` (변동 없음) | 🔴 밖 |
| `iam-platform/apps/auth-service` | `infra/demo/seed/seed-fan.sh` | `:112` (was `:109`) | 🔴 밖 |
| `iam-platform/apps/auth-service` | `projects/iam-platform/apps/account-service/…/R__06_seed_fan_artist_accounts_and_artist_role.sql` | `:109` (was `:106`) | 🟢 안 |
| `iam-platform/apps/auth-service` | `projects/iam-platform/apps/admin-service/…/R__seed_demo_operator.sql` | `:119` (new position) | 🟢 안 |
| `iam-platform/apps/auth-service` | `projects/iam-platform/apps/account-service/…/migration` (dir) | `:136` (was `:133`) | 🟢 안 |
| `iam-platform/apps/auth-service` | `projects/iam-platform/apps/account-service/…/migration-dev` (dir) | `:139` (변동 없음) | 🟢 안 |
| `iam-platform/apps/auth-service` | `…/CreateTenantUseCase.java` | `:142` (변동 없음) | 🟢 안 |
| `iam-platform/apps/auth-service` | `projects/iam-platform/specs/features/multi-tenancy.md` | `:145` (was `:142`) | 🟢 안 |

여전히 8건, 밖 2건 / 안 6건 — 개수는 티켓과 같다, 줄만 밀렸다. PR #3246 표본은 역사 표본이라 다시 재지 않았다(티켓 지시대로).

## AC-1 — 전수 census

**① 선언된 `inputs.(file|dir|files)(rootProject.file('…'))`**: 저장소 전체 `**/build.gradle` grep, 위 8건이 **전부**다(다른 어떤 build.gradle 에도 없음 — `rootProject.file(` 자체가 이 두 파일에만 존재).

**② 테스트 소스가 «모듈 밖» 경로를 읽는 자리** — 술어: `src/test/**/*.java` 안에서 `infra/`·`Paths.get(/Path.of(`·`resolve(`·`../..` 패턴을 전수 grep, 그 결과 하나하나를 실제로 열어 (a) 진짜 파일 read 인지 (주석/Javadoc 인용이 아닌지) (b) 읽는 경로가 그 모듈 밖인지 (c) declare 됐는지 판정.

- **양성 대조군 통과**: `ScmInboundExpectedDemoSeedShapeDltTest.java` (`Paths.get("infra","demo","seed","seed-scm.sh")`, 조상 디렉터리를 걸어 올라가며 찾는다) — ① 의 declared 목록에도, ② 의 코드-스캔에도 **둘 다** 걸린다. 술어가 declared 여부와 무관하게 실제 코드-레벨 read 를 잡는다는 증거.
- **② 가 잡은 나머지는 전부 declared (①) 와 1:1 대응**했다 — `FanArtistDemoSeedTest`(ACCOUNT_SEED→`:109`, FAN_DEMO_SEED→`:112`), `OAuthClientTenantReferenceIntegrationTest`(→`:136,139,142,145`), `DemoSeedCredentialTest`/`DemoSecondOperatorSeedTest`(OPERATOR_SEED, 둘 다 →`:119`, 같은 파일을 두 테스트가 읽는다), `WorkloadRoleCatalogTest`(자기 모듈 안 마이그레이션, in-module).
- **선언 없이 모듈 밖을 읽는 자리 — 0건.** 후보였다가 기각한 것들, 근거를 남긴다:
  - `DevSeedScopeIT` / `ExistingVolumeMigrationOrderIT`(wms admin-service) / `ExistingSeedVolumeMigrationOrderIT`(wms master-service) — `infra/demo/wms-devseed.override.yml` 을 **Javadoc 으로만 언급**, 실제 `Paths.get`/`Files.*` 읽기 없음(러닝 compose 스택을 전제하는 IT). 실제 read 아님.
  - `FanArtistRoleSeedIntegrationTest`(iam account-service), `SupplierRegistrationIntegrationTest`(scm procurement-service) — 코멘트에 `seed-fan.sh`/`seed-scm.sh` 를 역사적으로 언급할 뿐, 실제 read 는 자기 모듈 안 SQL.
  - `MicrometerNotificationMetricsTest`, `NoriElasticsearchContainer`(ecommerce) — "infra" 는 Javadoc 의 `infra/prometheus`·`infra/elasticsearch` 언급, 오탐.
  - `FanPlatformE2ETestBase`/`ScmPlatformE2ETestBase`/`EcommerceFulfillmentE2EBase`(nightly e2e) — `infra/postgres/init/*.sh` 를 읽지만 **프로젝트-스코프 복사본**(`projects/<p>/infra/postgres/init/...`, 실존 확인)을 우선 찾는 폴백 헬퍼이고, 애초에 `nightly-e2e.yml` 전용이라 **Scope § 제외**(nightly 전용 스위트 경로 조건) 대상 — AC-2/AC-3 밖.
  - 프런트엔드(`*.ts`/`*.tsx`) — `infra/demo`/`seed-*.sh` 문자열이 매치된 16개 파일을 열어봤으나 전부 demo-heartbeat/store-config 라우트이지 시드 스크립트 read 가 아니고, 애초에 이 티켓의 메커니즘(Gradle `inputs.*` UP-TO-DATE 캐싱)은 npm/vitest 러너에 적용되지 않는다 — 해당 없음으로 판단.
  - **`scripts/check-outside-module-input-filter-coverage.sh --self-test`** 안에도 이 술어의 자동화된 재현이 있다(양성/음성 대조군을 코드로 고정).

⇒ **선언 안 된 모듈 밖 읽기 = 0건.** 이번 티켓이 고쳐야 하는 것은 Gradle 선언이 아니라 **ci.yml 필터**뿐이라는 뜻 — 티켓의 원래 우려(683 처럼 선언 자체가 없는 새 사례)가 실측으로는 아직 나타나지 않았다.

## AC-2 — 고침

`.github/workflows/ci.yml`: `wms` 필터에 `infra/demo/seed/seed-scm.sh`, `iam` 필터에 `infra/demo/seed/seed-fan.sh` 를 정확한 파일 경로로 추가(글롭 확장 없음, `infra/demo/**` 아님).

## AC-3 — 가드 + bite

`scripts/check-outside-module-input-filter-coverage.sh` 신설. `ci.yml` 의 `filters: |` 블록을 **구조적으로 파싱**(12-스페이스 `key:`, 14-스페이스 `- 'pattern'`)해서 판정 — 경로를 손으로 복제하지 않는다. 디렉터리 입력은 `prefix/**` 접두사 매칭으로 판정(Edge Case 대응). 프로젝트 디렉터리명 → ci.yml 필터 키 매핑도 **하드코딩하지 않고** ci.yml 자신의 `projects/<dir>/**` 베어-루트 패턴에서 역산한다(`platform-console` 처럼 베어-루트가 없는 프로젝트는 의도적으로 fail-closed).

**실제 트리에서 bite 확인**(자동화된 `--self-test` 픽스처 말고, 진짜 작업 트리에서 직접): `ci.yml` 의 `- 'infra/demo/seed/seed-scm.sh'` 한 줄을 지우고 실행 → `rc=1`, `projects/wms-platform/apps/inbound-service/build.gradle:75: 'wms' filter does not cover infra/demo/seed/seed-scm.sh` 로 모듈:줄을 정확히 지목. 수동으로 그 줄을 복원(`git checkout --` 사용 안 함) → `rc=0`.

`--self-test` (기존 `check-libs-ci-coverage.sh` 컨벤션을 따름, 실제 트리의 최소 카피를 mutate) 10/10 PASS: 실제 진입점(별도 프로세스) · unmutated 통과 · 음성 대조군(iam 6건이 population A 에서 제외됨, 필드 3만 검사 — 초판 버그로 필드 4 의 경로 문자열까지 같이 매치돼 거짓 FAIL 났던 것을 고침) · 양성 대조군(wms seed-scm.sh 가 population A 에 잡힘) · bite/restore · 새 undeclared 갭 주입 시 bite · 빈 population 양쪽 fail-closed.

🔴 **msys 성능 메모**: `--self-test` 는 이 Windows 호스트에서 실측 **4분 16초**(반면 실제 검사 1회는 17초) — `project_key_map` 이 필터 패턴 한 줄마다 `sed` 를 fork 하던 최초 버전은 그보다 더 걸려 120s/150s 하네스 타임아웃을 두 번 초과했다(msys fork exhaustion 클래스); 단일 `awk` 패스로 바꿔 고쳤다. CI(Linux)는 실제 검사 1회만 돌리므로(아래) 영향 없음 — 로컬 개발 편의 기능만 느리다.

`ci.yml` 에 `outside-module-input-filter-coverage` 잡을 신설, `outside-module-input-guard` changes 플래그(패턴: `projects/*/apps/*/build.gradle`, `.github/workflows/ci.yml`, 이 스크립트 자신 — raw, `code-changed` 와 AND 안 함, `ci-baseline` 과 같은 근거: 자기 자신을 감시하는 가드는 AND 로 꺼질 수 있으면 안 된다)로 게이팅.

**`scripts/` 에 파일을 더했으므로 전체 가드를 돌렸다** — 아래 § 전체 가드 실행 참고.

## AC-5 — 무소유 기록

- `projects/iam-platform/apps/auth-service/build.gradle` — "🔴 Residual gap … THIS GAP IS UNOWNED" 블록을 "🔴 Former residual gap, now CLOSED" 로 고치고 가드 이름(`scripts/check-outside-module-input-filter-coverage.sh`)을 적었다.
- `projects/wms-platform/apps/inbound-service/build.gradle:70-78` — 원래 이 코멘트는 CI 필터 갭을 아예 언급하지 않았다(683 이 몰랐던 갭이라 스스로 기록도 안 했다). 갭 발생·폐쇄를 새로 적었다.
- **`ADR-MONO-063:187` — 손대지 않았다. 부분 닫힘으로 남긴다.** `docs/adr/INDEX.md` 를 읽었고, 이 저장소가 실제로 쓰는 ACCEPTED-ADR 정정 관례는 **"forward-ADR / amendment-section" 패턴**이다 — 원문을 인라인으로 고치거나 한 줄을 덧붙이는 게 아니라, **완전히 새 ADR 문서**를 filing 해서 이전 ADR 의 주장을 정정한다(전례: `ADR-MONO-012a`가 `ADR-MONO-012`를 "forward correction" — 새 ADR 파일 + INDEX 등록, 원문 무수정; `ADR-MONO-054` §D6 "amendment-section pattern"이 `ADR-MONO-053`의 주장 두 개를 정정 — 역시 새 ADR). 이 관례를 이 항목에 적용하면 **CI 가드 티켓 하나가 새 ADR 을 authoring** 하게 되는데, 이 티켓의 Scope·분석/구현 권장(Sonnet, "필터 두 줄 + 가드 하나 + 시험 PR")은 그런 architecture-decision 권한을 주지 않는다. `platform/architecture-decision-rule.md` 축으로 봐도 이건 HARDSTOP-09 인접 — 임의로 새 ADR 을 만들지 않고 여기서 멈춘다. **owner 판단 필요**: (a) 이 정도 "미결 항목이 이제 소유자가 생겼다"는 운영 상태 갱신도 새 ADR 을 요구하는가, 아니면 (b) ADR 정정 관례에 이런 가벼운 케이스를 위한 별도 경로가 있어야 하는가(있다면 그건 이 티켓이 아니라 관례 자체에 대한 별도 논의).

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
