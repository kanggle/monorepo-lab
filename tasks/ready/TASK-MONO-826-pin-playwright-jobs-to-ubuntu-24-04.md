# Task ID

TASK-MONO-826

# Title

`ubuntu-latest` 가 Ubuntu 26.04 로 넘어가면서 Playwright 설치 단계가 죽는다 — Playwright 를 쓰는 5개 잡의 `runs-on` 을 `ubuntu-24.04` 로 고정한다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- playwright
- nightly

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (워크플로 `runs-on` 5줄 + 디스패치 검증)
> 기안 2026-11-09 UTC (`date -u +%F`). 모든 날짜는 UTC. 이 PR 은 **기안만** — 워크플로 파일은 건드리지 않는다(구현은 별도 PR).

# Dependency Markers

- 선행: 없음. 착수 전에 `git worktree list` · `git branch -a | grep -i 826` · `gh pr list --search "TASK-MONO-826"` 로 동시 세션 확인(기안 시점 0건).
- 후속: 없음. (Playwright 업그레이드 경로는 Edge Cases 의 대안으로만 남기고 별도 티켓은 만들지 않는다 — AC-5 가 «다시 열 조건» 을 적는다.)
- 관련: `.github/workflows/nightly-e2e.yml` · `federation-hardening-e2e.yml` · `ci.yml` (`frontend-e2e-smoke`) · `TASK-MONO-601`(required 체크 이름 — 잡 `name:` 을 바꾸지 않는 이유, Edge Cases).

# Goal

**2026-11-05 이후 매일 밤 빨간 두 야간 워크플로를 다시 초록으로 되돌린다.** 원인은 코드가 아니라 러너 이미지다 — 같은 커밋(`e1ad3f577`)이 11-04 까지는 통과했고 11-05 이후 실패한다. `ubuntu-latest` 라벨이 Ubuntu 26.04 로 옮겨가는 중이고, 이 저장소가 고정한 Playwright(1.59.1 · 1.60.0)는 `ubuntu26.04-x64` 에서 chromium 설치를 거부한다.

가장 작은 수정: Playwright 를 쓰는 잡만 `runs-on: ubuntu-24.04` 로 명시 고정. 나머지 71개 `ubuntu-latest` 잡은 그대로 둔다.

# Scope

## In Scope

**AC-0 측정 (기안 중 실측, 2026-11-09 UTC) — 구현자는 착수 전에 다시 잰다(Failure Scenarios 마지막 항목).**

### M1. 증상과 원인

| # | 사실 | 출처 |
|---|---|---|
| F1 | 실패한 **스케줄** 런은 두 워크플로 모두 11-05 이후 매일: Nightly E2E `42129816599`(11-05) · `42308466114`(11-06) · `42417017825`(11-07) · `42527563501`(11-08) / Federation Hardening `42133251548`(11-05) · `42313413901`(11-06) · `42420982272`(11-07) · `42531469931`(11-08). 마지막 초록: Nightly `41934147576`(11-04) · Federation `41939619409`(11-05 00:12Z). **전부 같은 head SHA `e1ad3f577`.** | `gh run list --workflow nightly-e2e.yml` · `federation-hardening-e2e.yml` |
| F2 | 실패 잡은 전부 **Playwright 설치 단계**에서 죽는다: `Error: ERROR: Playwright does not support chromium on ubuntu26.04-x64`. 잡 3개(Nightly 런 `42527563501`: `128874450685` GAP logout · `128874710778` Platform Console · `128874831912` Frontend E2E full-stack) + 1개(Federation `128886719189`). | 잡 로그 |
| F3 | 실패 잡의 러너 로그 `Image: ubuntu-26.04` / `Operating System Ubuntu 26.04.1 LTS`. 초록 런의 같은 잡은 `Image: ubuntu-24.04`(예: 잡 `126938829836`, 11-04). 어노테이션: *"The ubuntu-latest label will migrate to Ubuntu 26 beginning October 19, 2026"* (runner-images#14748). | 잡 로그 · check-run annotations |
| F4 | 백엔드 전용 잡은 같은 런에서 **초록** — 예: `E2E full (iam docker-compose)` 도 `Image: ubuntu-26.04` 에서 success. 26.04 자체가 깨진 게 아니라 **Playwright 설치만** 깨졌다. | 런 `42527563501` |

### M2. 이전이 **점진적**이었다 (기안자 가설과 다른 점 — 보고에 명시)

런 `42129816599`(11-05 23:30Z) 안에서 Playwright 잡 3개가 **서로 다른 이미지**를 받았다: 2개 `ubuntu-24.04` → success, 1개(Frontend E2E full-stack, `127604038141`) `ubuntu-26.04` → failure. 11-06 부터는 3/3 이 26.04. 따라서 «11-05 부터 매일 같은 잡이 실패» 가 아니라 **11-05 은 1개, 11-06 부터 전부**다. 이것이 의미하는 것: 같은 라벨이 잡마다 다른 OS 를 줄 수 있으므로, 수정 뒤 한 번 초록이 나왔다고 «고쳐졌다» 로 읽지 않는다(AC-3 이 명시 고정 후 `Image:` 줄을 직접 읽는 이유).

### M3. Playwright 를 쓰는 **모든** 잡 (`.github/workflows/*.yml` 전수, `playwright install` · `playwright test` · `@playwright` grep)

| # | 파일:잡 | `runs-on` (줄) | 설치 단계 (줄) | Playwright | 상태 |
|---|---|---|---|---|---|
| J1 | `nightly-e2e.yml` : `frontend-e2e-fullstack` (`Frontend E2E full-stack (web-store, Playwright + docker compose)`) | `ubuntu-latest` (:436) | `pnpm --filter web-store exec playwright install --with-deps chromium` (:684) | `^1.59.1` → 락 1.59.1 | 🔴 실패 중 |
| J2 | `nightly-e2e.yml` : `web-store-iam-logout-e2e` (`Web-store GAP logout e2e …`) | `ubuntu-latest` (:749) | 같은 명령 (:817) | 위와 같음 | 🔴 실패 중 |
| J3 | `nightly-e2e.yml` : `platform-console-e2e-fullstack` (`Platform Console E2E full-stack …`) | `ubuntu-latest` (:1214) | `pnpm exec playwright install --with-deps chromium` (:1369) | `^1.48.2` → 락 1.60.0 | 🔴 실패 중 |
| J4 | `federation-hardening-e2e.yml` : `federation-hardening-e2e-fullstack` (`Federation Hardening E2E full-stack …`) | `ubuntu-latest` (:129) | `pnpm exec playwright install --with-deps chromium` (:519) | `^1.49.0` → 락 1.60.0 | 🔴 실패 중 |
| J5 | `ci.yml` : `frontend-e2e-smoke` (`Frontend E2E smoke (web-store + fan-platform-web + console-web, Playwright)`) | `ubuntu-latest` (:3516) | 3곳: :3557 (web-store) · :3588 (fan-platform-web) · :3611 (console-web) | web-store/fan `^1.59.1` → 락 1.59.1 · console `^1.48.2` → 락 1.60.0 | 🟠 **잠복** — 아래 |

🟠 **J5 는 지금 안 빨갛지만 같은 결함을 갖고 있다.** 이 잡은 `if:` 경로 필터(`needs.changes.outputs.*`)로 프런트/워크플로 변경 PR 과 `code-changed` 푸시에서만 돈다. 최근 `ci.yml` 런 12개 중 이 잡이 실행된 것은 11-03 `125716936691`(`Image: ubuntu-24.04`, success) **하나**이고 나머지는 skipped — **이전이 시작된 11-05 이후로는 한 번도 실행되지 않았다.** 다음 프런트 PR 이 이 잡을 깨운다. 그래서 «야간 두 개만 고친다» 가 아니라 5개를 한 번에 고정한다. 🔵 이 PR 자체가 `ci.yml` 을 고치므로 `workflows == 'true'` 경로로 J5 가 **수정 PR 안에서** 실행된다 — 별도 디스패치 없이 증명된다(AC-2).

### M4. 범위 밖 `ubuntu-latest` (Playwright 비사용)

`runs-on: ubuntu-latest` 총 **76** 줄(`ci.yml` 56 · `nightly-e2e.yml` 12 · `federation-hardening-e2e.yml` 2 · `portfolio-sync.yml` 2 · `_integration.yml` 1 · `_platform-e2e.yml` 1 · `mirror-images.yml` 1 · `vercel-deploy.yml` 1) + `windows-latest` 1 줄(`ci.yml:2706`, 대상 아님). Playwright 5개를 빼면 **71개**가 범위 밖. 이들은 이미 26.04 에서 도는 것이 확인됐거나(F4) 같은 방식으로 깨질 이유가 Playwright 뿐이다. 🔵 `.github/actions/*` 컴포짓에는 Playwright 설치가 없다(`summarise-test-results` 의 언급은 JUnit 리포터 설명뿐).

**변경:**
- J1~J5 의 `runs-on: ubuntu-latest` → `runs-on: ubuntu-24.04` (5줄). 각 줄 위에 한 줄 주석: 왜 고정했는지 + 되돌릴 조건(AC-5) + 이 티켓 ID.
- `federation-hardening-e2e.yml` 의 `federation-hardening-boot-jars`(:61, Playwright 없음)는 **건드리지 않는다.** 산출물이 JAR 이라 OS 무관(Edge Cases 첫 항목).

## Out of Scope

- 워크플로 파일 수정은 **이 기안 PR 에 없다** — 구현 PR 이 따로 간다(`tasks/INDEX.md` PR Separation Rule).
- 나머지 71개 `ubuntu-latest` 잡 — 전부 고정하지 않는다. 이동하는 라벨을 따라가는 것이 이점인 잡이 대부분이다.
- Playwright 업그레이드 · 락 파일 변경 (대안, Edge Cases).
- Federation 로그에 보이는 `ERROR: null value in column "code" of relation "suppliers"` (scm-postgres, 설치 단계 **이전** 시드 구간 노이즈) — 기안 중 관찰만 했고 이 장애와 무관한지 판정하지 않았다. 구현 PR 에서 Federation 디스패치가 초록이 되는지로 자연히 드러난다. 그때 빨갛게 남으면 이 티켓의 범위가 아니라 **새 티켓**이다(AC-2 후반).
- `Node.js 20 is deprecated` 경고 — 별개.

# Acceptance Criteria

- **AC-0** (착수 전) M1~M3 를 구현자가 다시 잰다. 특히 (a) `grep -n "playwright install" .github/workflows/*.yml` 가 J1~J5 의 6개 설치 줄과 일치하는지(= 기안 이후 새 Playwright 잡이 생기지 않았는지), (b) 실패 로그가 여전히 `does not support chromium on ubuntu26.04-x64` 인지(원인이 바뀌었으면 STOP — 고정이 답이 아닐 수 있다). 어긋나면 표를 고쳐 쓰고 진행.
- **AC-1** J1~J5 의 `runs-on` 이 `ubuntu-24.04` 로 바뀌었다. `grep -n "runs-on: ubuntu-latest"` 가 Playwright 잡 5곳에서 0건이고, 다른 잡의 `runs-on` diff 는 0줄이다(`git diff` 로 직접 확인 — 잡 `name:` 은 한 글자도 안 바뀐다).
- **AC-2** 수정 브랜치에서 **영향받는 워크플로 각각을 `workflow_dispatch` 로 1회** 돌려 설치 단계가 통과한다. run id 와 잡별 결과를 티켓에 기록한다:
  - `gh workflow run nightly-e2e.yml --ref <branch>` → J1 · J2 · J3 의 `Install Playwright …` 스텝 success.
  - `gh workflow run federation-hardening-e2e.yml --ref <branch>` → J4 의 `Install Playwright chromium (with system deps)` success.
  - J5 는 수정 PR 의 `ci.yml` 런(`frontend-e2e-smoke` 가 `workflows == 'true'` 로 실행된다)에서 3개 설치 스텝 success. 🔴 `skipped` 는 통과가 아니다 — 실제 실행됐는지 `conclusion` 을 읽는다.
  - 🔴 «설치 스텝이 통과» 와 «잡이 초록» 은 다르다. 설치 뒤 스펙까지 도는 잡이므로 잡 전체 conclusion 도 기록한다. 스펙에서 실패하면 그 원인이 **26.04 와 무관**한지(이미 가려져 있던 11-05 이전의 결함인지) 판정해 새 티켓으로 분리한다 — 설치 통과만으로 이 티켓을 닫지 않고, 그렇다고 무관한 실패까지 이 티켓에서 고치지도 않는다.
- **AC-3** AC-2 의 각 잡 로그에서 `Image: ubuntu-24.04` 줄을 **직접 읽어** 인용한다(M2: 라벨은 잡마다 다른 OS 를 줄 수 있다. 고정이 먹었는지는 결과가 아니라 이미지 줄로 판정).
- **AC-4** 머지 뒤 **main 의 다음 스케줄 런**이 해당 잡에서 초록이다: Nightly E2E 의 J1~J3, Federation Hardening 의 J4. (스케줄 시각: Nightly `0 18 * * *` · Federation `0 19 * * *` UTC.) 🔵 더 빠른 신호: `nightly-e2e.yml` 은 `push: branches [main]` 도 걸려 있어 머지 직후 main 푸시 런이 같은 J1~J3 을 돈다(현재 푸시 런 `42581278748` 도 같은 3개 잡이 설치 단계에서 실패 중). 푸시 런 초록은 **예고**이고, AC-4 의 닫힘은 **스케줄** 런이다. 스케줄 런이 아직 안 돌았으면 닫지 않고 기다린다 — 날짜(UTC)와 run id 를 적는다.
- **AC-5** 고정 주석에 **되돌릴 조건**이 적혀 있다: «Playwright 가 `ubuntu26.04-x64` 를 지원하는 버전으로 올라가고, 그 버전에서 26.04 디스패치가 초록일 때 `ubuntu-latest` 로 되돌린다». 🔴 이 조건이 지켜지는지 감시하는 장치는 만들지 않는다(고정의 비용은 «OS 보안 패치 외 이동을 놓침» 정도) — 단 `ubuntu-24.04` 이미지 자체의 지원 종료 시점이 오면 같은 일이 반복되므로, 그 날짜를 구현자가 GitHub runner-images 공지에서 확인해 주석에 적는다(확인하지 못하면 «미확인» 으로 적는다 — 추정으로 채우지 않는다).
- **AC-6** 닫기 전 `tasks/INDEX.md` 의 `## done` 이동은 `CLAUDE.md` 의 4차원 머지 검증을 따른다. 차원 (d) = 위 AC-2~4 를 **열어서** 읽는다(PR 머지는 AC-4 의 스케줄 런을 증명하지 못한다).

# Related Specs

- `.github/workflows/nightly-e2e.yml` (헤더 · 잡 `frontend-e2e-fullstack` · `web-store-iam-logout-e2e` · `platform-console-e2e-fullstack`)
- `.github/workflows/federation-hardening-e2e.yml`
- `.github/workflows/ci.yml` (`frontend-e2e-smoke`)
- `platform/git-workflow-policy.md` (CI path-filter · 머지 검증)
- `CLAUDE.md` § Task Rules — 필수 체크 4개(`changes` 등)와 잡 이름 일치

# Related Contracts

없음. (CI 설정만 — 서비스 API · 이벤트 계약 변경 없음. `projects/<name>/` 영향: 없음.)

# Edge Cases

- **`federation-hardening-boot-jars` 는 `ubuntu-latest`(26.04) 로 두고 `federation-hardening-e2e-fullstack`(`needs:` :130)만 24.04 로 고정**하면 두 잡의 OS 가 달라진다. 산출물이 업로드 JAR 이라 OS 의존이 없다고 보지만 **측정한 것은 아니다** — AC-2 의 Federation 디스패치가 그 경계를 지난다. Nightly 의 `ecommerce-boot-jars-nightly` / `platform-console-boot-jars-nightly` → J1/J3 도 같은 모양. 경계에서 실패하면 boot-jars 잡도 함께 24.04 로 고정(같은 PR, 같은 사유).
- **잡 `name:` 은 바꾸지 않는다.** `main` 의 필수 체크 4개는 `changes` · `INDEX queue drift …` · `Task ID collision …` · `Walkthrough limitation ledger drift …` 이고 이 잡들은 거기 없지만, 이름이 어디에 매칭되는지(`scripts/check-required-check-names.sh` · 이슈 생성 잡 `nightly-red-arrives` 의 `needs:`) 는 이 티켓이 재검증하지 않는다 — `runs-on` 한 줄만 바꾸면 이름은 구조적으로 안 움직인다.
- **대안: Playwright 를 26.04 지원 버전으로 올린다.** 더 «근본적» 이지만 이 티켓은 택하지 않는다. 이유 (1) **어느 버전이 `ubuntu26.04-x64` 를 지원하는지 기안 중 확인하지 못했다**(1.59.1 · 1.60.0 둘 다 거부는 실측) — 아직 지원 버전이 없을 수 있다. (2) 락 파일이 4개(`projects/ecommerce-microservices-platform` · `projects/fan-platform` · `projects/platform-console/apps/console-web` · `tests/federation-hardening-e2e`)이고 서로 다른 버전(1.59.1 / 1.60.0)을 쥐고 있어 **프로젝트 3개를 가로지르는 의존성 변경**이다 — 스펙 전체 재검증(브라우저 빌드 변경은 스크린샷·타이밍을 흔든다)이 따라온다. (3) 고정은 5줄이고 되돌리기가 줄 단위다. 업그레이드는 AC-5 의 되돌릴 조건이 충족되는 시점의 별도 일이다.
- **`ubuntu-22.04` 로 고정**하는 길도 있으나 24.04 가 직전 초록의 실제 이미지(F3)라 **검증된 값**이다 — 검증되지 않은 값으로 바꾸지 않는다.
- **`--with-deps` 가 apt 로 설치하는 시스템 라이브러리**가 26.04 에서 이름이 바뀐 것이 오류의 실제 층일 수 있다(Playwright 가 OS 를 먼저 거부하므로 그 아래는 이번에 안 보인다). 24.04 고정은 그 층을 우회할 뿐 해결하지 않는다 — 이 티켓의 한계로 인정한다.
- **추출된 포트폴리오 리포**(`github.repository` 가드 밖에서 도는 사본)에도 같은 워크플로가 복사돼 있으면 같은 고정이 따라간다 — `ci.yml` 의 `frontend-e2e-smoke` 가 «추출 리포에서도 돈다» 고 주석한 잡이므로, 고정 값이 거기서도 유효한 라벨(`ubuntu-24.04` 는 공개 호스티드 라벨)인지는 이 저장소 밖에서 재지 않았다.

# Failure Scenarios

- **고정했는데도 `Image: ubuntu-26.04`** → 라벨 오타 또는 `runs-on` 이 다른 줄(재사용 워크플로 호출 쪽)에서 덮임. AC-3 의 이미지 줄이 이를 잡는다. 이때 «Playwright 가 문제» 로 돌아가지 말고 `runs-on` 이 실제로 해석된 값을 먼저 읽는다.
- **설치는 통과, 스펙이 새로 빨강** → M2 의 가림 효과. 11-05 이전에 있던 결함이 설치 단계에서 막혀 안 보였을 수 있다(설치가 죽으면 그 뒤 스텝은 skipped). AC-2 의 분리 규칙대로 새 티켓으로 떼고, 이 티켓은 «설치 통과 + 이미지 24.04» 로 닫되 잡 conclusion 은 사실대로 적는다.
- **디스패치는 초록, 스케줄 런은 빨강** → 디스패치 환경 ≠ 스케줄 환경(예: `if:` 가 `workflow_dispatch` 에서 다른 경로를 탐). AC-4 가 스케줄 런을 요구하는 이유. 이 경우 두 이벤트의 `if:` 분기 차이를 먼저 본다.
- **`ubuntu-24.04` 가 폐기 공지를 받음** → 고정은 시한부다. AC-5 의 지원 종료 날짜 기록이 다음 사람의 입구.
- **기안 이후 상황이 바뀜**: 구현 시점에 GitHub 가 Playwright 호환 이미지를 되돌리거나 Playwright 새 버전이 26.04 를 지원할 수 있다. AC-0 (b) 가 로그를 다시 읽는 이유 — 원인이 달라졌으면 STOP 하고 이 티켓을 고쳐 쓴다.
- **측정 오차**: M3 의 줄 번호는 기안 시점 `main`(`c34671b9b`) 기준. 워크플로가 그 사이 바뀌었으면 AC-0 (a) 에서 어긋난다.
