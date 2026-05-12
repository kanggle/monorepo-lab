# ADR-MONO-003 — Phase 5 (Template 레포 추출 발사) 결정 — DEFERRED (D4 OVERRIDE 2026-05-11)

**Status:** DEFERRED — D4 churn freeze override accepted (2026-05-11 사용자 의향 변경, § 3.4 risk 2 경로). D1 (Phase 5 보류) 유지, D4 (churn freeze) 해제.
**Date:** 2026-05-08 (PROPOSED + DEFERRED) / 2026-05-09 (재평가, BE-273 Phase 2 closure 후) / 2026-05-10 (TASK-MONO-049 reset) / **2026-05-11 (D4 override)**
**Decision driver:** TASK-SCM-INT-001 series 종결로 ADR-MONO-002 의 D3 deferred 조건 재평가 트리거 도래. `scripts/verify-template-readiness.sh` (full mode) 실행 결과 1 blocker (Check 3: shared-library churn in last 30 days) → Phase 5 발사 보류 결정. **2026-05-09 재평가**: TASK-BE-273 Phase 2 (PR #294, ADR-004 옵션 1) 가 `libs/java-common/.../ResilienceClientFactory.java` 변경 동반 (HTTP/1.1 강제, OAuthLogin 5 IT JDK HttpClient HTTP/2 RST_STREAM race fix) — D4 § "면제" 의 critical regression fix 자연 확장 카테고리. churn 시계 1일 reset (마지막 churn 2026-05-09).
**Supersedes:** none
**Related:** [ADR-MONO-001](ADR-MONO-001-port-prefix-scaling.md), [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md), [ADR-003](../../projects/global-account-platform/docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md) (옵션 B closure, BE-274), [ADR-004](../../projects/global-account-platform/docs/adr/ADR-004-oauth-callback-ci-linux-503-isolation.md) (옵션 1 closure, BE-273), [TEMPLATE.md](../../TEMPLATE.md), [scripts/verify-template-readiness.sh](../../scripts/verify-template-readiness.sh), [scripts/extract-template.sh](../../scripts/extract-template.sh), 메모리 [`project_monorepo_template_strategy`](../../../memory/project_monorepo_template_strategy.md)

**Accepted Decisions:**
- **D1 = DEFERRED** — Phase 5 (실제 Template 레포 추출 + `Use this template` 부트스트랩) 발사 보류
- **D2 = 재평가 트리거** — 마지막 shared-library churn = `e7e9f08f` (2026-05-09 16:27 KST, BE-273 Phase 2 머지 동반 `libs/java-common` 변경) 으로부터 30일 wait. 다음 평가 시점 ≥ **2026-06-09** (이전 ≥ 2026-06-07 에서 1-2일 밀림).
- **D3 = 재평가 게이트** — `scripts/verify-template-readiness.sh` (full mode, `--no-git` 없이) exit 0 시점에 본 ADR 을 ACCEPTED 로 전환 또는 ADR-MONO-003a 발행.
- **D4 = churn freeze 의도** — 재평가 시점까지 `libs/`, `platform/`, `rules/`, `.claude/`, root build files (`build.gradle` / `settings.gradle` / `package.json`) 의 변경은 critical fix / breaking regression 한정. 새 도메인 부트스트랩 (예: finance / erp) 도 동일 wait.

---

## 1. Context

### 1.1 ADR-MONO-002 의 D3 deferred 조건 재평가

ADR-MONO-002 (2026-05-04) 는 Phase 5 (Template 레포 실제 추출) 시점을 별도 ADR-MONO-003 candidate 로 deferred 하면서 평가 항목 3개 명시:

1. 5 프로젝트 동거 시점 libs/java-* cross-project 사용 패턴 변화
2. TEMPLATE.md instruction 의 scm 부트스트랩 실 적용 결과
3. portfolio 평가 사이클 정착 여부

이 후 trigger 도래 — TASK-SCM-INT-001 series (PR #260 + #262 + #263) 종결로 ADR-MONO-002 가 명시한 "scm 머지 + 라이브러리 churn 안정 평가" 의 첫 절반 충족.

### 1.2 verify-template-readiness 결과 (2026-05-08)

`scripts/verify-template-readiness.sh` (TASK-MONO-047 산출, 6 check) full mode 실행:

| Check | 결과 | 비고 |
|---|---|---|
| 1. Shared library boundary (no project-specific service names) | PASS | 6 false-positive ignore 후 0 violation |
| 2. Phase 4 outstanding tasks must be in `done/` | PASS | TASK-SCM-BE-002d + TASK-SCM-INT-001 둘 다 done |
| 3. **No shared-library churn in last 30 days** | **FAIL** | 30일 내 다수 commit (settings.gradle / libs / rules / .claude / platform) |
| 4. CI baseline green on main | PASS | 최신 main ci.yml run success |
| 5. All projects have valid `PROJECT.md` frontmatter | PASS | 5 projects 모두 valid |
| 6. No `PORT_PREFIX` legacy in `projects/` | PASS | TASK-MONO-024 hostname routing 마이그레이션 완료 |

**1 blocker, 0 warnings → Phase 5 NOT READY.**

### 1.3 Check 3 의 의미

verify 스크립트의 Check 3 정의: "최근 1개월간 shared library 변경 0" (Discovery → Distribution 전략의 *Distribution* 단계 진입 게이트). 활발한 churn 은 Template 추출 시점에 stale 위험.

마지막 30일 churn 의 큰 카테고리:

- **scm-platform 부트스트랩 (5 commits)** — TASK-SCM-BE-001/-002/-003 + INT-001 (2026-05-04 ~ 2026-05-08): settings.gradle 모듈 등록 다수
- **fan-platform 부트스트랩 (4 commits)** — TASK-FAN-BE-001/002/003 + FE-001 + INT-001 (2026-05-03)
- **Traefik hostname routing (1 breaking commit)** — TASK-MONO-024 (2026-05-03)
- **공통규칙 정리 시리즈 (5 commits)** — TASK-MONO-029~033 (2026-05-04)
- **libs/java-web split (1 commit)** — TASK-MONO-044a (2026-05-05)
- **libs Docker fix (1 commit)** — DOCKER_API_VERSION forward (2026-05-07)
- **TASK-MONO-047** — 본 ADR 의 평가 도구 자체 (2026-05-07)

특히 TASK-MONO-024 (Traefik), TASK-MONO-034 (java-security 패키지명 정규화), TASK-MONO-044a (libs/java-web split) 는 모두 cross-project breaking 변경. 4월 중반에 비해 churn 강도 평균 이상.

### 1.4 Phase 4 catalyst 평가 결론 (재확인)

ADR-MONO-002 § 3.1 의 "TEMPLATE.md instruction 의 첫 실 적용 검증" 은 **scm 부트스트랩 결과로 충족** — 037 dry-run 의 dummy 검증을 넘어, 실제 도메인 (3 trait + cross-project event consumption + Testcontainers IT + cross-service E2E) 으로 모든 instruction step 통과. TEMPLATE.md 자체 catch 추가 필요 없음.

5 프로젝트 동거의 cross-project 라이브러리 사용 패턴은 안정 — `libs:java-messaging` outbox 패턴이 wms / scm / GAP / fan-platform 모두 정상, `libs:java-web-servlet` (TASK-MONO-044f-2 split) 가 5 service 모두 정상.

→ Discovery 단계는 완료. 남은 게이트는 churn quiet window 만.

---

## 2. Decision

### D1 — Phase 5 발사 DEFERRED

**선택**: DEFERRED (발사 보류).

**대안 비교**:

| 옵션 | 평가 | 결과 |
|---|---|---|
| (a) 즉시 발사 — verify 결과 무시 | 진척 + 사용자 motivation 강. 단 Template 추출 시점이 churn 의 끝부분이라 추출 후 즉시 stale 위험. ADR-MONO-002 § 3.3 의 risk 1 ("scm 부트스트랩이 예상 외 큰 라이브러리 churn 야기 시 → Template 추출 시점 더 늦춤") 가 정확히 시현. | ❌ |
| (b) DEFERRED until churn-quiet window | verify 의 Check 3 은 monorepo 가 자체 정의한 게이트 (TASK-MONO-047 spec). 지금 발사 = 게이트 우회 = 게이트 자체의 신뢰 훼손. 30일 wait = monorepo 자체 표준 일관성 + Template 안정 보장. | ✅ |
| (c) Check 3 의 30일 임계값 단축 | 임계값은 TASK-MONO-047 의 "churn 안정성 휴리스틱" — 변경 시 별도 spec PR 필요. 본 결정의 in-scope 아님. | ❌ |

### D2 — 재평가 트리거

**조건**: 마지막 shared-library churn (`c6140bb2` 2026-05-08 INT-001 머지, settings.gradle 모듈 등록) 으로부터 **30일 wait**. 

**가장 빠른 재평가 시점**: ≥ **2026-06-07**.

재평가는 `scripts/verify-template-readiness.sh` (full mode, `--no-git` 없이) 단순 재실행. 사용자가 임의 시점에 트리거 가능 — 자동 cron 안 둠 (조용한 monorepo 가 트리거의 본질, 외부 푸시 의미 없음).

### D3 — 재평가 게이트

`scripts/verify-template-readiness.sh` (full mode) **exit 0** 시:

- (option α) 본 ADR 의 Status 를 `DEFERRED` → `SUPERSEDED by ADR-MONO-003a` 로 갱신 + ADR-MONO-003a 발행 (실제 Phase 5 발사 결정).
- (option β) 본 ADR 을 in-place 로 ACCEPTED 전환하면서 Status block 에 transition note 추가.

option α 가 권장 (ADR linearity 보존). 실제 Phase 5 발사 행위 (`scripts/extract-template.sh` 실행 + 외부 Template repo 생성) 는 ADR-MONO-003a 가 ACCEPTED 시점에 동시 진행.

### D4 — Churn freeze 의도

본 ADR 머지 후 ~ 2026-06-07 사이 **shared-library 변경 자제**:

- `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, root `build.gradle` / `settings.gradle` / `package.json` 의 변경은 다음 중 하나에 한정:
  - **critical fix** — main CI red 상태 복구
  - **breaking regression** — 5 프로젝트 중 하나라도 부팅 fail / e2e fail 만성화
  - **security** — vulnerable dep 업데이트
- 새 도메인 부트스트랩 (finance / erp / mes) 도 30일 wait 안에는 시작 안 함. 사용자 의향이 강할 시 본 ADR 을 SUPERSEDED 로 전환 + 새 ADR 발행 (D4 의 freeze 도 함께 deviation).
- Project-internal 변경 (`projects/<name>/` 내부) 은 freeze 영향 없음 — 자유. 단 그 변경이 결국 libs/ 패턴 추출을 유발하는 PR 은 30일 wait 후로 스케줄.

### 면제

이미 진행 중인 회귀 fix task 는 freeze 영향 없음 — 즉:

- **TASK-MONO-046-7** (auth-service SAS deferred 8) — Docker reproduce 후 GAP IT fix. 영향: `libs/java-security` 가능. 우선 진행.
- **TASK-MONO-046-8** (consumer pipeline deeper investigation) — Docker reproduce 후 GAP IT fix. 영향: `libs/java-messaging` 가능. 우선 진행.
- **TASK-BE-273 Phase 2 (옵션 1, ADR-004)** — OAuthLogin 5 IT JDK HttpClient HTTP/2 RST_STREAM race fix. 영향: `libs/java-common/.../ResilienceClientFactory.java` (HTTP/1.1 강제, project-agnostic DRY). 046 series 의 자연 확장 (Cluster C 5 회복 = 046-7 의 8 deferred 중 5 method 동일 영역). 진행 완료 (PR #294 머지 2026-05-09).
- **TASK-BE-272 / 274 (ADR-003)** — Cluster A 3 method 회복 시리즈. 영향: project-internal (`apps/auth-service/**`) — shared 영역 무변경. freeze 영향 0.

위 task 들은 freeze 의 "regression fix" 면제. 진행 후 churn timer 가 reset → 재평가 시점이 그만큼 미뤄짐.

---

## 3. Consequences

### 3.1 즉시 영향

- ADR-MONO-002 § 3.1 후속의 ADR-MONO-003 candidate 가 본 ADR 로 발행 — `DEFERRED` Status 로.
- monorepo 운영자 (= 본 세션 사용자 + AI agent) 는 D4 freeze 를 의식하면서 cross-project 변경 의사 결정.
- 새 작업 추천 시 본 ADR § D4 가 가이드 — shared 변경 동반하는 task 는 자제.

### 3.2 중기 영향 (~30 일)

- `libs/`, `platform/`, `rules/`, `.claude/` 안정화 — Template 추출 시점에 minimal stale risk.
- 사용자가 재평가 시점에 단순히 `bash scripts/verify-template-readiness.sh` 실행 후 본 ADR 의 D3 게이트 통과 확인.
- 사용자가 그 사이 새 도메인 의향 발화 시 본 ADR D4 와 충돌 — superseding ADR 발행 path.

### 3.3 장기 영향 (Phase 5 발사 후)

- ADR-MONO-003a (또는 본 ADR 의 ACCEPTED 전환) 시점에 `scripts/extract-template.sh` 실 실행 + 외부 Template repo (`monorepo-template` 가 후보, 별도 ADR 에서 명명) 생성.
- 신규 도메인 (finance / erp / mes) 부트스트랩이 "Use this template" 로 시작 — Discovery 결과의 안정 라이브러리 + scm 부트스트랩의 instruction 검증 결과 재사용.
- 월 1회 Template ↔ monorepo sync 패턴 정착 (메모리 `project_monorepo_template_strategy`).

### 3.4 위험

- **30일 wait 가 사실상 평생 wait 로 늘어남** — 5 프로젝트 동거 + 활발한 portfolio 진척으로 churn 이 멈출 confidence 낮음. 완화: D3 의 verify 결과를 monorepo 운영의 자기 정렬 신호로 받음 (계속 churn = portfolio 가 살아있음의 증거, Template 발사 의향 없음의 증거). 30일 wait 가 60일이 되든 90일이 되든 본 ADR 의 D3 게이트는 그대로 — 발사 시점은 churn 이 자연 안정화될 때.
- **사용자 의향 변경 (즉시 발사 강행 의향)** — 본 ADR 을 SUPERSEDED 로 전환 + ADR-MONO-003-override 발행 path. verify 결과의 명시적 우회 + risk acknowledgement 필수.
- **Check 3 임계값 자체 부적절** — 30일이 monorepo 활발성에 맞지 않음 가능. 별도 TASK-MONO-049 candidate (verify 임계값 재평가). 본 ADR 의 in-scope 아님.

---

## 4. Alternatives Considered (그러나 채택 안 함)

### 4.1 즉시 발사 (verify 무시)

장점: 본 세션의 motivation 시점 보존. INT-001 종결의 의례적 next step 으로 자연스러움.
단점: verify 의 Check 3 신뢰 훼손 — 게이트 자체가 의미 없어짐 (무시 가능 게이트 = 게이트 아님). Template 추출 후 즉시 churn 이 다시 시작되면 sync 부담 즉시 폭증. ADR-MONO-002 § 3.3 risk 1 의 정확한 시현.
→ D1 에서 DEFERRED 채택으로 기각.

### 4.2 30일 wait + 즉시 발사 mixed (소프트 정의)

예: "verify 통과는 monitoring 만, 발사는 사용자 의향 시점". verify 의 게이트 의미 약화 — 결국 4.1 과 같음.
→ 기각.

### 4.3 Check 3 임계값 단축 (예: 14일)

장점: 본 ADR 의 wait 시간 단축 — 2026-05-22 정도에 재평가 가능.
단점: TASK-MONO-047 (verify spec) 의 in-scope deviation. 별도 spec PR + 임계값 휴리스틱 재평가 필요. 본 ADR 의 결정 외부.
→ 기각. 별도 task TASK-MONO-049 candidate 로 분리 (본 ADR § 3.4 risk 3 reference).

### 4.4 Phase 5 자체 폐기 (Template 레포 안 만들고 monorepo 영구 유지)

장점: monorepo 단순성 — 5 프로젝트 동거 패턴 그대로 유지.
단점: ADR-MONO-002 의 상위 결정 (Discovery → Distribution) 폐기 — 더 큰 ADR 변경 필요. 신규 도메인 (finance / erp / mes) 부트스트랩 시 Template 의 부재가 매번 cross-project conflict 위험.
→ 본 ADR 의 in-scope 아님. ADR-MONO-002 부분 SUPERSEDE 가 필요한 큰 결정.

---

## 5. Migration / Implementation Plan

본 ADR 자체는 docs-only — 코드 / 빌드 영향 0.

### 5.1 본 ADR 머지 후 (즉시)

1. 메모리 `project_monorepo_template_strategy` 갱신 — Phase 5 trigger DEFERRED + 재평가 시점 (≥ 2026-06-07) + D4 freeze 의도 기록.
2. 사용자에게 D4 freeze 의도 공유 (ADR 자체가 그 통지).

### 5.2 ~ 2026-06-07 (wait)

- 새 작업 추천 시 본 ADR § D4 freeze 일관 — shared 변경 동반 task 추천 자제.
- TASK-MONO-046-7 / 046-8 진행 가능 (regression fix 면제 — § D4 면제 절).
- Project-internal 변경 자유.

### 5.3 2026-06-07 ≤ X (재평가)

1. 사용자 또는 AI agent 가 임의 시점에 `bash scripts/verify-template-readiness.sh` (full mode) 실행.
2. exit 0 → ADR-MONO-003a 발행 (Phase 5 발사 ACCEPTED) → `scripts/extract-template.sh` 실 실행 → 외부 Template repo 생성.
3. exit ≠ 0 → 새 blocker 분석 → 본 ADR 의 D2 wait 자체를 재평가 시점 (다음 30일) 으로 지연.

### 5.4 후속 ADR (deferred)

- **ADR-MONO-003a** — Phase 5 발사 ACCEPTED 결정. D3 게이트 통과 후.
- **ADR-MONO-004** — Template repo 명명 + 외부 repo URL + sync 정책 (월 1회 vs 트리거 기반). ADR-003a 와 동시 또는 직후.
- **ADR-MONO-005** — finance / erp / mes 부트스트랩 순서 (ADR-MONO-002 § D4 의 deferred). Phase 5 발사 후.

---

## 6. Updates

### 2026-05-09 — 재평가 (BE-272/273/274 closure 후)

**Trigger**: 046 series 의 outcome closure 도래 (ADR-003 ACCEPTED 옵션 B closure / ADR-004 ACCEPTED 옵션 1) — 누적 deferred IT 9/8 회복. monorepo template Phase 5 재평가 사용자 요청.

**verify-template-readiness 결과 (2026-05-09)**:

| Check | 결과 | 비고 |
|---|---|---|
| 1. Shared library boundary | PASS (추정) | 본 conversation 변경: libs/java-common ResilienceClientFactory (project-agnostic) — 0 violation |
| 2. Phase 4 outstanding | PASS | 모든 큐 비어있음 |
| 3. **No shared-library churn (1 month)** | **FAIL** | 본 conversation 의 BE-273 Phase 2 churn (`e7e9f08f`, 2026-05-09) — D4 면제 카테고리 자연 확장이지만 verify 자동 검증은 의도 인식 못 함 |
| 4. CI baseline green | PASS | main 최신 commit `00d25cf6` PR #297 머지 후 |
| 5. PROJECT.md validity | PASS | 5 projects 유효 |
| 6. PORT_PREFIX legacy | PASS | TASK-MONO-024 정리 완료 |

**1 blocker (Check 3) 그대로** — Phase 5 발사 보류 유지.

**D4 면제 자연 확장 인정**:

본 conversation 의 churn 영역 분석:
- `libs/java-common/.../ResilienceClientFactory.java` (BE-273 Phase 2) = HTTP/1.1 강제 enforcement, project-agnostic DRY, 046 series 자연 확장 → § "면제" 의 "regression fix" 카테고리 정확히 해당
- `apps/auth-service/**` (BE-272/273/274) = project-internal, shared 영역 무관
- `docs/adr/**`, `tasks/`, `specs/` (모두) = project-internal 또는 ADR doc, shared 영역 무관

D4 의도와 충돌 0. 단 verify 의 자동 검증은 D4 면제 의미 인식 못 함 (의도적 — 자동화는 실수 방지가 목적).

**D2 시점 갱신**: ≥ 2026-06-07 → ≥ **2026-06-09** (1-2일 밀림, 자연 쿨다운).

**다음 행동**: 추가 churn 발생 안 하면 ≥ 2026-06-09 자연 트리거. 그 전 사용자 의향 변경 시 본 ADR SUPERSEDED 후 ADR-MONO-003-override 발행 path (§ 3.4 risk 2 참조).

**046 series 의 portfolio 가치**: Cluster A 3/3 + Cluster B 1/1 + Cluster C 5/5 + token customizer bonus = 9/8 deferred IT 완전 회복. SAS public-client refresh_token + revoke + userinfo + OAuth callback (3 provider) 모두 main CI deterministic PASS — portfolio 의 OIDC AS 깊이 증명 완성. Phase 5 launch 시점에 Template 추출의 base 도 이 OIDC AS 패턴이 안정 자산으로 포함.

---

### 2026-05-10 — TASK-MONO-049 머지 (D4 시계 reset)

**Trigger**: TASK-MONO-049 (PR #316) 머지 — `libs/java-messaging` outbox/dedupe/envelope scaffolding 신설 + ADR-MONO-004 ACCEPTED. `libs/` + `platform/shared-library-policy.md` + `.claude/skills/messaging/*` 동시 변경.

**ADR-MONO-004 §4.4 의 명시적 D2 reset**: "≥ 2026-06-09 + 1d" → ≥ **2026-06-10**. 본 reset 은 D4 면제 카테고리 외 (구조적 추출, 회귀 fix 아님) 였으나 portfolio 가치 (300+ 라인 publisher 중복 제거 + single source of truth) 가 D4 freeze 비용보다 큼으로 판정 — ADR-MONO-004 D4 가 명시 acknowledgement.

**다음 행동**: ≥ 2026-06-10 자연 트리거. 그 전 사용자 의향 변경 시 § 3.4 risk 2 path.

---

### 2026-05-11 — D4 OVERRIDE (사용자 의향 변경, § 3.4 risk 2 경로)

**Trigger**: 사용자 명시 의향 ("내 말은 지금 바로 freeze unlock하고 작업 시작하라는 거였어"). § 3.4 risk 2 의 정확한 시나리오 — 사용자 의향 변경 → 즉시 발사 강행.

**Decision**:
- **D1 = DEFERRED 유지** — Phase 5 (Template 레포 실 추출) 자체는 보류. D4 override 는 B (common rule cleanup) 작업의 가능성을 여는 것이지 Phase 5 launch 와 무관.
- **D4 = OVERRIDE** — `libs/`, `platform/`, `rules/`, `.claude/`, root build files 의 변경 자제 의도 해제. churn 시계 reset 비용 acknowledged.
- **D2 = 의미 약화** — "30일 quiet window" 의 자연 트리거 신뢰성 떨어짐. verify-template-readiness Check 3 가 영구 FAIL 가능성. Phase 5 발사 시점은 사용자 의향 + 작업 의도적 정지 의 조합으로 결정 (자동 cron 없음).
- **D3 = 게이트 의미 약화** — Check 3 가 자동 통과 신호로 작동 안 함. Phase 5 launch 결정은 사용자 명시 + 별 ADR 가 필수 (자동 ACCEPTED 전환 path 봉인).

**Risk acknowledged**:
1. Template 추출 시점 indefinite — Check 3 가 churn 안정 신호 역할 못 함
2. 매 B PR 이 Check 3 FAIL 갱신 — verify exit code 1+ 지속
3. ADR-MONO-002 § 3.3 risk 1 ("scm 부트스트랩이 예상 외 큰 라이브러리 churn 야기 시 → Template 추출 시점 더 늦춤") 의 시간 무한 확장 시현
4. portfolio narrative 의 "elaborate planning + self-imposed gate 인내" 신호 부분 손상 — 그러나 사용자 평가가 "B cleanup 가치 > Phase 5 launch 가치" 로 의향 명시 = portfolio 의 다른 신호 ("decision discipline + risk acknowledgement + 명시적 trade-off 문서화") 로 보전.

**Override scope (which churn is allowed)**:
- 본 override 는 **B common rule cleanup** (`/validate-rules` 산출 fix) 에 한정 의도. PR #328 = 첫 적용.
- 새 도메인 부트스트랩 (finance / erp / mes) 은 본 override 와 무관 — 별도 ADR 필요 (ADR-MONO-005 candidate).
- 임의의 cross-project breaking 변경은 본 override 와 무관 — 의향 발화 시점에 명시 평가.

**Phase 5 launch trigger 변경 (post-override)**:
- 자연 트리거 봉인. 사용자 명시 의향 발화 시 ADR-MONO-003a 발행 + verify exit-code 무관 force-launch 또는 Phase 5 의 의도 자체 폐기 후 monorepo 영구 유지 path (§ 4.4) 으로 진입.
- "30일 wait" 게이트 무효화 — verify-template-readiness Check 3 의 의미 사용자 정의 (예: "B sweep 종결 시점부터 N일 quiet" 등) 으로 재정의 필요할 시 별 PR.

**B cleanup 진행 status (2026-05-11 시점)**:
- PR #328 (B common-rule sweep, 11 fixes) — 머지 완료 (2026-05-11). first commit under override.
- 후속 B 후보 (메모리 `project_b_common_rule_refactor_pending.md` § "B 후보 5건") 잔여 4건: CLAUDE.md 분리 / error-handling.md 카탈로그 정리 / rules/ audit 2차 / .claude/skills/INDEX 정합 / BaseEventPublisher javadoc — 사용자 의향 따라 본 세션 또는 후속 세션 진행.

**Next ADR action**: 본 override 가 D1 보류 + D4 해제 + D2/D3 의미 약화 모두 포함하므로 ADR-MONO-003 자체를 **SUPERSEDED-by-self** 로 부분 retire 보다는 본 update 단락이 "running addendum" 으로 작동하도록 둠. Phase 5 launch 결정 시점 (또는 폐기 결정 시점) 에 ADR-MONO-003a 발행 + 본 ADR 정식 SUPERSEDE.

---

### Forward pointer (2026-05-12) — ADR-MONO-003a 발행

본 ADR § 2026-05-11 update 의 D4 OVERRIDE scope 정의는 **[ADR-MONO-003a — D4 OVERRIDE Scope Canonicalization](ADR-MONO-003a-d4-override-scope-canonicalization.md)** 로 canonicalize 되었음 (2026-05-12, TASK-MONO-063).

scope 가 2026-05-12 에 "B common rule cleanup ∪ OpenAI Harness gap series" 로 확장 (ADR-MONO-006 § 3.4 / § 4.2 / § 7 + memory L28 multi-doc consensus). ADR-MONO-003a 는 이 multi-doc 상태를 단일 권위로 정리 + IN/OUT scope 기준 명시 + audit trail (11 PR) + Phase 5 trigger 재정의 (verify-template-readiness 자동 게이트 무효화, user-explicit + ADR-MONO-003b 가 새 트리거).

**현재 (2026-05-12 이후) D4 OVERRIDE scope 질문 시 → ADR-MONO-003a § D1 (IN) / § D2 (OUT) / § D3 (메타 룰) 참조.** 본 ADR § 2026-05-11 update 는 historical record 로 보존됨.

---

### Forward pointer (2026-05-13) — ADR-MONO-003b ACCEPTED (Phase 5 launched)

본 ADR § D1 (Phase 5 발사 DEFERRED) 은 **[ADR-MONO-003b — Phase 5 Launch Criteria, Procedure, Sync, Rollback](ADR-MONO-003b-phase-5-launch-criteria.md)** 의 ACCEPTED 전환으로 SUPERSEDED-on-launch (2026-05-13, TASK-MONO-070).

Launch artifact: [`https://github.com/kanggle/project-template`](https://github.com/kanggle/project-template) — public, `is_template: true`, source SHA `68b6877c`, 435 files / 2.7 MiB.

본 ADR 의 D1 (DEFERRED), D2 (30-day quiet window), D3 (verify exit-0 auto-promotion) 는 모두 ADR-MONO-003a § D4 / ADR-MONO-003b § D1 ~ § D5 로 대체됨. **현재 Phase 5 상태 질문 시 → ADR-MONO-003b § Status + § 6 Status Transition History 참조.** 본 ADR 의 body (Context / Decision / Update history) 는 historical record 로 보존.
