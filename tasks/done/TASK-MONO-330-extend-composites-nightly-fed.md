# Task ID

TASK-MONO-330

# Title

⏳ DEFERRED — `setup-java-gradle` + `setup-node-pnpm` composite를 nightly-e2e.yml / federation-hardening-e2e.yml로 확장 (버전 단일화 완성). **버전 bump / nightly 손댈 때 착수.**

# Status

done

# Owner

monorepo

# Task Tags

- ci
- chore

---

# ⏳ DEFERRAL GUARD (read first)

이 task는 **의도적으로 보류된 백로그**다. root 리프사이클에 `backlog/` 폴더가 없어 `ready/`에 두되, **AC-0(아래)를 먼저 검증**한다. 트리거 없으면 **착수하지 말고 그대로 둔다**(보류 유지). `/process-tasks` 등 일괄 픽업 시에도 AC-0에서 STOP.

**착수 트리거 (둘 중 하나 관측 시)**:
1. **JDK / Node / pnpm 버전 bump가 실제로 발생** — composite(`setup-java-gradle` = JDK 21/temurin, `setup-node-pnpm` = Node 20/pnpm 9.15.0)의 버전을 올려야 하는 순간. 지금 확장해두면 그 bump가 nightly/fed까지 자동 반영(드리프트 함정 현실화 직전 차단).
2. **nightly-e2e.yml / federation-hardening-e2e.yml을 어차피 손대는 작업** — 그 작업의 nightly/fed 실행으로 composite 치환을 함께 검증(별도 검증 사이클 절약).

둘 다 없으면: 지금의 하드코딩된 셋업 블록이 "동작하는" 상태이므로 유지.

---

# ✅ AC-0 관측 기록 (2026-07-12) — 트리거 2 충족, 착수

**트리거 2("nightly-e2e.yml / federation-hardening-e2e.yml 을 어차피 손대는 작업")가 관측됐다.**

`TASK-MONO-359`(이미지-실재 가드에 time 트리거 부여)가 `nightly-e2e.yml` 에 신규 잡 `demo-image-liveness` 를 추가한다. 즉 nightly 를 **이미 열어 편집하는 중**이고, 그 PR 의 머지-후 nightly 사이클이 **어차피 관측 대상**이다. MONO-330 의 DoD 가 요구하는 "머지 후 nightly/fed 1사이클 GREEN" 을 **같은 사이클로 처리**할 수 있다 — 따로 하면 검증 사이클을 두 번 태워야 한다. 이것이 트리거 2 가 노린 정확한 상황이다.

→ MONO-359 와 **한 PR 로 묶어 착수**한다.

## 782 no-cache 변이 — 결정: **인라인 유지도, composite 분기도 아니다. 그건 누락이었다.**

이 task 는 `nightly-e2e.yml:782` 의 cache 없는 `setup-node` 블록을 "규격 불일치 변이"로 보고 (a) 인라인 유지 / (b) composite 에 cache-optional 분기 추가 중 하나를 고르라고 남겨 두었다. **둘 다 틀린 전제였다** — 조사 결과 그 블록은 *의도된 변이가 아니라 누락*이다:

- 그 잡(`platform-console-e2e-fullstack`)은 `projects/platform-console/apps/console-web` 에서 `pnpm install --frozen-lockfile` 을 돌린다.
- **`federation-hardening-e2e.yml` 은 동일한 console-web install 을 하면서 `cache: 'pnpm'` 을 정확히 그 lockfile 로 걸어 둔다.**
- 즉 "cache 를 걸지 않을 이유"가 있는 게 아니라, `#726`(PC-FE-019)에서 블록이 들어올 때 **빠뜨린 것**이다. lockfile 은 실재한다.

→ **composite 를 그대로 적용하고 cache 를 붙인다.** `--frozen-lockfile` 이므로 해석되는 의존성 집합은 불변이고, 얻는 것은 캐시다. composite 에 optional 분기를 파는 복잡도(이 task 가 우려한 것)는 **필요 없다.**

## 두 번째 변이 — `nightly-e2e.yml:355` (이 task 가 예상하지 못한 것)

`web-store-iam-logout-e2e` 잡은 JDK 만 설치하고 **`gradle/actions/setup-gradle` 이 없다** — 그런데 바로 다음 스텝이 `./gradlew … bootJar` 를 돌린다. 이 레포의 다른 모든 JVM 잡은 둘을 짝으로 갖는다. composite 적용은 여기에 `setup-gradle` 을 **추가**한다(Gradle 의존성·빌드 캐시).

→ **byte-등가 치환이 아니라 의도된 superset 이다.** 이 사실을 워크플로 주석과 PR 본문에 명시한다. 숨기면 리뷰어가 "동작 보존" 이라는 이 task 의 약속과 diff 가 어긋나는 것으로 읽는다.

---

# Goal

`TASK-MONO-326`(setup-java-gradle) · `TASK-MONO-329`(setup-node-pnpm)이 두 셋업 composite를 도입했으나 **ci.yml에만** 적용했다(nightly/fed는 `pull_request` 미트리거라 PR 검증 불가 → 유예). 그 결과 JDK/Node/pnpm 버전이 **ci.yml에서만 단일 소스**이고, nightly-e2e.yml / federation-hardening-e2e.yml은 **하드코딩 셋업 블록이 잔존**한다.

이 반쪽 상태는 **드리프트 함정**이다: composite에서 버전을 올려도 ci.yml만 따라오고 nightly/fed는 옛 버전에 멈춰 → "PR은 새 버전, nightly는 옛 버전" 툴체인 불일치("PR 초록, nightly만 깨짐" 또는 버전-스큐 버그). composite 도입의 목적(버전 한 곳 관리)이 반만 달성된 착각 상태.

이 task는 두 composite를 nightly/fed의 잔존 셋업 블록에 확장 적용해 **버전 단일화를 완성**한다(버전 bump = 진짜 한 곳만 수정). **단, 트리거 전에는 하지 않는다**(검증이 nightly-gated라 급하지 않음).

---

# Scope

## In Scope (트리거 관측 후에만)

- **`nightly-e2e.yml` JVM 셋업**: `setup-java-gradle` composite 미적용 잡의 `Set up JDK 21` + `Set up Gradle` 2스텝 → composite 호출. (주의: e2e-full 3잡 wms/fan/scm은 MONO-326 Phase 3b에서 이미 `_platform-e2e.yml` reusable 경유 composite 사용 중 → 대상 아님. 남은 인라인 JVM 셋업 잡: iam-e2e-full / ecommerce-fulfillment-e2e-full / ecommerce-boot-jars-nightly / platform-console-boot-jars-nightly 등 실측 확인.)
- **`nightly-e2e.yml` Node 셋업**: `setup-node-pnpm` 적용 가능한 uniform 블록(2곳: frontend-e2e-fullstack / web-store-iam-logout-e2e 등, `cache: 'pnpm'` 있는 것) → composite 호출.
- **`federation-hardening-e2e.yml`**: JVM 셋업 1 + Node 셋업 1 → composite.
- 잡 이름·`if:`·개수 무변경(동작 보존).

## Out of Scope

- **782 no-cache 변이** — `setup-node` 에 `cache: 'pnpm'`이 없는 nightly 블록. `setup-node-pnpm`(cache 필수)과 규격 불일치 → **둘 중 하나 결정 필요**: (a) 그 블록만 인라인 유지, 또는 (b) `setup-node-pnpm`의 `cache-dependency-path`를 optional로 만들어 cache 없는 모드 지원. Implementation Notes 참조.
- ci.yml(이미 완료).
- reusable workflow화(별개; 이 task는 setup composite 확장만).
- 필터/게이팅(MONO-328 소관).

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act, 필수 선행)**: § DEFERRAL GUARD 트리거 2개 중 최소 1개 관측 확인. **미관측이면 STOP — 보류 유지.**(근거 기록.)
- [ ] nightly-e2e.yml / federation-hardening-e2e.yml의 잔존 uniform JVM/Node 셋업 블록이 composite 호출로 치환.
- [ ] 782 no-cache 변이 처리 방침 결정·적용(인라인 유지 or composite cache optional).
- [ ] 치환 후 **버전 문자열이 두 composite 파일에만 존재**(nightly/fed에 `java-version: '21'` / `version: '9.15.0'` / `node-version: '20'` 잔존 0, cache-optional 예외 제외).
- [ ] **검증 (nightly-gated)**: 머지 후 push-to-main/cron nightly + fed schedule 1사이클에서 해당 잡 GREEN(또는 기존 상태 대비 회귀 0). PR self-CI로는 nightly/fed 잡이 안 도므로 syntax(js-yaml)만 PR 검증.

---

# Related Specs

- `tasks/done/TASK-MONO-326-ci-workflow-dry-refactor.md` (setup-java-gradle, ci.yml만)
- `tasks/done/TASK-MONO-329-ci-setup-node-pnpm-composite.md` (setup-node-pnpm, ci.yml만; 782 변이 유예 명시)
- Memory `project_mono_326_ci_workflow_dry`

# Related Skills

N/A — CI YAML 편집.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — composite 확장 적용, 동작 불변, ADR 없음.

---

# Implementation Notes

- **로컬 composite = checkout 선행 필수** — 각 대상 잡이 checkout 보유 확인(대부분 보유).
- **검증 nightly-gated** — MONO-326 Phase 3b 선례처럼 syntax는 PR(js-yaml)에서, 런타임은 머지 후 nightly/fed에서. push-to-main이 nightly-e2e.yml `push` 트리거를 발화하므로 머지 즉시 nightly 검증 가능(3b에서 확인).
- **782 결정 가이드**: cache 없는 블록이 1개뿐이면 인라인 유지가 단순. 여러 개면 composite에 `cache-dependency-path` optional(빈 값 → cache 스텝 skip) 추가가 나음. 후자는 composite 분기 → setup-node의 `cache` 조건부 처리 주의.
- 로컬 검증: `npx --yes js-yaml`로 파싱만.

---

# Edge Cases

- e2e-full 3잡(reusable 경유)은 이미 composite 사용 → 이중 적용 금지(대상 제외).
- fed는 schedule만 → 검증 지연 가장 김. 머지 후 다음 fed 실행까지 확인 필요.
- nightly 잡별 cache-dependency-path 상이 → 입력으로 보존(MONO-329와 동일).

---

# Failure Scenarios

- 치환 오류가 PR에서 안 잡히고 nightly에서 늦게 발현 → 저위험(byte-등가)이나 지연. 완화: 머지 후 nightly 1사이클 즉시 관측(push 트리거).
- 782 처리에서 composite cache-optional 분기 버그 → cache 미스(성능) 또는 setup-node 오류. 완화: 782만 인라인 유지가 안전 기본.

---

# Test Requirements

- PR: js-yaml 파싱(nightly/fed 잡은 PR 미실행).
- 머지 후: nightly(push/cron) + fed(schedule) 1사이클 해당 잡 GREEN 확인.

---

# Definition of Done

- [ ] AC-0 트리거 관측 확인(미관측이면 보류 유지, done 아님).
- [ ] nightly/fed 잔존 셋업 블록 composite 치환 + 782 처리.
- [ ] 버전 문자열 두 composite에만 존재(단일화 완성).
- [ ] 머지 후 nightly/fed 1사이클 GREEN.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

Surfaced 2026-07-04 — MONO-326/329(ci.yml composite) 완료 후 사용자와 "composite를 nightly/fed로 확장하면 뭐가 좋나" 논의. 결론: 이득의 본질 = **버전 단일화 완성 / 반쪽 상태의 드리프트 함정 제거**(새 DRY 아님). 검증이 nightly-gated라 급하지 않음 → 버전 bump 또는 nightly-touching 작업 시 착수하는 백로그로 등록(사용자 지시). MONO-328(게이팅 hoist)과 같은 "신호 시 착수" 성격. task-id: 326/329 mine, 327 타세션, 328 mine(backlog) → 330.

분석=Opus 4.8 / 구현 권장=Sonnet (mechanical composite 확장; nightly 782 변이 판단만 주의).
