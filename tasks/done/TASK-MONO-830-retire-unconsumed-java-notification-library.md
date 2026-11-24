# Task ID

TASK-MONO-830

# Title

소비자 0 인 공유 라이브러리 `libs/java-notification` 을 지운다 (`ADR-MONO-043` § 8) + 루트 `.gitignore` 에 `runs/`

# Status

done

# Owner

monorepo

# Task Tags

- build
- ci
- lib
- cleanup

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (삭제 + 가드 전수 실행)
> 기안 2026-11-24 UTC. 출처: 세션 인계(2026-11-24)의 «남은 소유자 결정» 1 · 4 → 소유자 «1번으로 진행 4번을 함께».

# Dependency Markers

- 선행: `ADR-MONO-043` § 8 개정(이 티켓의 기안 PR 에 함께 실린다). 기안 PR 이 머지되기 전에는 착수하지 않는다 — 결정 기록이 `main` 에 없는 채로 공유 라이브러리를 지우게 된다.

# Goal

`libs/java-notification` 은 `ADR-MONO-043` D4/P1b 로 만들어졌지만 어떤 프로젝트도 의존하지 않는다(2026-07-20 · 2026-11-24 UTC 두 번 실측). 소유자가 «폐기» 를 골랐고 그 결정은 `ADR-MONO-043` § 8 에 있다. 이 티켓은 그것을 실행한다: 모듈과 그 등록 지점을 지우고, 지운 뒤에도 모든 가드와 빌드가 초록인지 확인한다.

곁가지로 루트 `.gitignore` 에 `runs/` 를 더한다. 메인 체크아웃의 `runs/`(ktg 작업 공간, git 미추적)가 `git status` 에 늘 `??` 로 떠서 넓은 `git add` 에 휩쓸릴 수 있기 때문이다(소유자 결정 4).

# Scope

## In Scope

- `libs/java-notification/` 전체 삭제(추적 파일 23개 — `build.gradle` + main 16 + test 6).
- `settings.gradle` 의 `'libs:java-notification',` 한 줄.
- `.github/workflows/ci.yml` `GRADLE_TASKS_CORE` 의 `:libs:java-notification:check` 한 줄.
- `platform/shared-library-policy.md` 카탈로그의 `libs/java-notification` 행(기안 PR 이 «폐기 예정» 으로 바꿔 둔 것) 삭제.
- 루트 `.gitignore` 에 `runs/`.

## Out of Scope

- `ADR-MONO-043` 본문(D1~D8 · § 1~§ 7) — 결정 기록이다. § 8 이 현재 상태를 말한다.
- `tasks/done/**` · 각 `tasks/INDEX.md` 의 done 행 — 동결된 기록이다. 거기 남는 `java-notification` 언급은 그대로 둔다.
- erp · fan · wms `notification-service` 코드 — 라이브러리를 쓰지 않으므로 바뀔 것이 없다.
- `platform/error-handling.md` 의 `DELIVERY_*` 코드 — wms · erp 가 각자 정의해 계속 쓴다.

# Acceptance Criteria

- [x] AC-0: 착수 직전 재측정 — `projects/**` 에서 `java-notification` 의존 · `com.example.platform.notification` import 가 **여전히 0**. 0 이 아니면 STOP(새 소비자가 생겼다 = § 8 의 근거 1 이 무너졌다).
- [x] AC-1: 삭제 뒤 `java-notification` · `com.example.platform.notification` 이 `tasks/done/**` · `tasks/INDEX.md` · `projects/*/tasks/INDEX.md` · `docs/adr/ADR-MONO-043-*` **밖에서 0건**(Grep 결과를 PR 본문에).
- [x] AC-2: `./gradlew help` 가 설정 단계 성공(모듈 등록 해제가 깨뜨린 참조 없음) + `./gradlew :assertNoApiOnSharedLibs` rc=0.
- [x] AC-3: `scripts/` 의 가드 **전부** 실행, rc=0 (스테이지 뒤에 — CLAUDE.md § Stage Before You Run a Guard). 특히 `check-libs-ci-coverage.sh`(ci.yml ↔ libs 모듈) · `check-shared-lib-jpa-scan.sh` · `check-error-code-registry.sh`(모집단이 `libs/*/src/main`) · `check-outside-module-input-filter-coverage.sh`. 실행 목록과 rc 를 PR 본문에.
- [x] AC-4: `git status` 가 메인 체크아웃에서 `runs/` 를 더는 보이지 않는다(`git check-ignore -v runs/` 가 `.gitignore` 의 새 줄을 가리킨다).
- [ ] AC-5: PR CI 의 Build & Test 잡(`GRADLE_TASKS_CORE` 를 돌리는 잡)이 **실행되어** success — `SKIPPED` 는 통과가 아니다.

# Related Specs

- `docs/adr/ADR-MONO-043-notification-architecture-unification.md` § 7 · § 8.
- `platform/shared-library-policy.md` § Catalog · § Change Rule(카탈로그 변경은 `libs/` 수정보다 먼저 문서화 — 기안 PR 이 «폐기 예정» 표시로 충족).

# Related Skills

- 없음.

# Related Contracts

- 없음(`platform/contracts/notification-inbox-contract.md` 는 라이브러리와 무관하게 남는다).

# Target Service

- 없음(공유 라이브러리 · 빌드 등록 · CI).

# Architecture

- 아키텍처 결정은 `ADR-MONO-043` § 8 이 했다. 이 티켓은 실행만.

# Implementation Notes

- `git rm -r libs/java-notification` → `settings.gradle` · `ci.yml` · 정책 행 · `.gitignore` → `git add` → 가드 전수.
- 로컬 빌드 디렉터리 `libs/java-notification/build/` 가 남으면 `git rm` 뒤에도 디렉터리가 보인다 — 추적 파일이 아니므로 무해하지만 «안 지워졌다» 로 오독하지 말 것.

# Edge Cases

- `ci.yml` 의 `changes` 경로 필터가 `libs/**` 를 통째로 보므로 이 PR 은 Build & Test 를 **돌린다**. 돌지 않으면(SKIPPED) AC-5 미충족.
- 어떤 가드가 libs 모듈 **개수**를 하한으로 박고 있으면(13 → 12) 그 가드가 빨갛다 — 숫자를 고치기 전에 그 하한이 무엇을 지키려던 것인지 먼저 읽는다.

# Failure Scenarios

- 숨은 소비자(예: `testImplementation project(':libs:java-notification')`)가 있으면 설정 단계에서 `Project with path ':libs:java-notification' could not be found` 로 빨강 — 조용하지 않다. AC-0 의 검색이 `build.gradle` 의 `project(':libs:…')` 형태도 잡는지 확인.
- `GRADLE_TASKS_CORE` 에서 줄만 지우고 `settings.gradle` 을 남기면(또는 반대) `check-libs-ci-coverage.sh` 가 문다 — AC-3.

# Test Requirements

- 코드 테스트 없음(삭제). 검증 = AC-1 Grep · AC-2 Gradle · AC-3 가드 전수 · AC-5 CI.

# Implementation Evidence (2026-11-24 UTC)

- **AC-0** — 착수 직전 `projects/**` 의 `*.gradle` · `*.java` · `*.kts` · `*.yml` · `*.properties` 에서 `java-notification` · `com.example.platform.notification` 검색: **0건**. 소비자 0 유지 → 진행.
- **AC-1** — 삭제 뒤 같은 검색을 리포 전체에 (`tasks/done/**` · 각 `tasks/INDEX.md` · `docs/adr/ADR-MONO-043-*` · 이 티켓 · `build/` 제외): **0건**.
- **AC-2** — `gradlew help` rc=0 · `gradlew :assertNoApiOnSharedLibs` rc=0 · `gradlew projects` 출력에 `java-notification` 없음.
- **AC-3** — 스테이지 뒤 `scripts/check-*.sh` **40개 전부** 실행: **37 rc=0**, 비0 셋:
  - `check-index-queue-drift` rc=1 → 원인은 INDEX 행을 아직 안 옮긴 것(가드가 정확히 이 티켓을 지목). 행을 옮긴 뒤 rc=0.
  - `check-erp-single-tenant-ratchet` rc=1 — «컨테이너 `erp-platform-mysql` 가 떠 있지 않다»(데모 스택 필요, 이 변경과 무관한 환경 전제).
  - `check-prerendered-demo-verdict` rc=1 — «`DEMO_API_BASE` 가 비어 있다»(web-store 빌드 + env 필요, 이 변경과 무관한 환경 전제).
  - 🔵 이름으로 지목한 넷 — `check-libs-ci-coverage` · `check-shared-lib-jpa-scan` · `check-error-code-registry` · `check-outside-module-input-filter-coverage` — 전부 rc=0.
- **AC-4** — `git check-ignore -v --no-index runs/ runs/ktg-x/a.md` → `.gitignore:113:/runs/` (rc=0). 대조: `projects/x/runs/a.md` 는 무시되지 **않음**(rc=1) — 루트 고정이 하위 `runs/` 를 먹지 않는다. 🔴 메인 체크아웃의 `git status` 에서 `?? runs/` 가 사라지는 것은 머지 후 `main` 을 당긴 뒤에 확인한다(close chore).
- **AC-5** — PR CI 대기.
- 범위: 29파일, +9 / −1634.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
