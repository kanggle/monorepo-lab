# ADR-MONO-002 — Phase 4 (Template 레포 추출) 진입 결정 + scm catalyst

**Status:** ACCEPTED
**Date:** 2026-05-04 (PROPOSED 2026-05-04, ACCEPTED 2026-05-04)
**Decision driver:** 공통규칙 정리 시리즈 029~039 종결로 monorepo Phase 4 진입 ready 상태. 새 도메인 (scm/erp/mes) 부트스트랩 의향 발화 → 진입 trigger 결정 필요.
**Supersedes:** none
**Related:** [ADR-MONO-001](ADR-MONO-001-port-prefix-scaling.md), [TEMPLATE.md](../../TEMPLATE.md), [CLAUDE.md](../../CLAUDE.md), 메모리 [`project_monorepo_template_strategy`](../../../memory/project_monorepo_template_strategy.md), 메모리 [`project_cleanup_series_029_033`](../../../memory/project_cleanup_series_029_033.md) (외부 메모리 — 본 ADR reader 가 monorepo session 안이면 자동 로드)

**Accepted Decisions:**
- **D1 = (b)** — Phase 4 catalyst 는 1 도메인 추가 (5 프로젝트 동거)
- **D2 = scm 우선** — wms 시너지 + 구현 난이도 중 + 첫 도메인 churn 최소화
- **D3 = deferred** — Template 레포 실제 추출 시점은 scm 머지 + 라이브러리 churn 안정 평가 후 별도 ADR (ADR-MONO-003 candidate)
- **D4 = deferred** — erp / mes 부트스트랩 순서는 scm 종결 후 결정 (별도 ADR 또는 사용자 도메인 의향 시점)

---

## 1. Context

### 1.1 monorepo 의 단계별 타임라인 (메모리 [project_monorepo_template_strategy] 기준)

| Phase | 시점 | 상태 | 주요 사건 |
|---|---|---|---|
| 1 | ~2026-04-21 | 완료 | wms-platform 단일. 라이브러리 가설 초기. |
| 2 | 2026-04-21~04-25 | 완료 | 모노레포 전환. wms + ecommerce 동거. 공용층 1차 reconciliation. |
| 3 | 2026-05-01~05-04 | 완료 | GAP + fan-platform 추가 (4 프로젝트). GAP IdP 표준화 (ADR-001) + Traefik hostname routing (ADR-MONO-001) + 공통규칙 정리 시리즈 029~039. |
| **4** | **본 ADR 결정 영역** | pending | **Template 레포 실제 추출. 라이브러리 안정화 후.** |
| 5 | Phase 4 이후 | future | 신규 프로젝트는 "Use this template" 로 시작. 월 1회 sync. |

### 1.2 Phase 4 catalyst 의 정의

`Discovery → Distribution` 전략 (메모리):
- **Discovery (Phase 2~3)**: monorepo 안에서 여러 프로젝트가 같은 라이브러리 사용하며 *진짜 범용* 패턴 자연 선택.
- **Distribution (Phase 4)**: 안정화된 라이브러리를 Template 레포로 추출 → 신규 프로젝트가 "Use this template" 로 부트스트랩.

Phase 4 진입 trigger 는 두 조건:
1. **Rule of Three 강화**: 3+ 프로젝트에서 라이브러리 사용 안정. 현재 4 프로젝트 (wms / ecommerce / GAP / fan-platform) — 충족.
2. **라이브러리 churn 안정**: 최근 큰 변화 (cutover 시리즈, cleanup 시리즈) 후 잠잠한 창. 2026-05-04 cleanup 시리즈 029~039 종결로 진입.

### 1.3 사용자 의향

2026-05-04 세션에서 사용자가 새 도메인 (scm/erp/mes) 부트스트랩 의향 발화. 어느 도메인을 어떤 순서로 진입할지 + Template 레포 실제 추출 시점 결정 필요.

---

## 2. Decision

### D1 — Phase 4 catalyst 는 1 도메인 추가

**선택**: (b) 1 도메인 추가 (5 프로젝트 동거).

**대안 비교**:

| 옵션 | 평가 | 결과 |
|---|---|---|
| (a) Phase 3 유지 (4 프로젝트, 도메인 추가 안 함) | Rule of Three 충족하나 Template 추출 catalyst 없음. 라이브러리 발견 단계가 자연 종결 안 됨. | ❌ |
| (b) 1 도메인 추가 (5 프로젝트) | 라이브러리 churn 단계적. 첫 도메인의 부트스트랩 결과로 TEMPLATE.md instruction 실 검증 (037 dry-run 의 dummy 검증을 실 도메인으로 보강). | ✅ |
| (c) 3 도메인 동시 추가 (7 프로젝트) | 큰 portfolio 진척 but root 공유 파일 (settings.gradle / package.json / ci.yml / GAP V seed / sync-portfolio.sh PROJECT_REMOTES) 동시 변경 conflict. 사용자 인풋 (도메인 모델 / service map / Traefik hostname 등) bottleneck. 라이브러리 churn 한 번에 폭발. | ❌ |

### D2 — 첫 도메인 = scm

**선택**: scm (Supply Chain Management).

**대안 비교**:

| 도메인 | wms 시너지 | 구현 난이도 | portfolio 차별화 | 첫 도메인 risk |
|---|---|---|---|---|
| **scm** | ✅ 자연 (창고 ↔ 공급망) | 중 (Supplier / Procurement / Logistics — bounded context 명확) | 중 | 낮음 |
| erp | ❌ (회계 hub) | 큼 (GL / AP / AR / HR 모듈 5+) | 큼 | 큼 |
| mes | ❌ (제조 현장) | 중상 (도메인 진입 장벽 높음) | 가장 큼 | 큼 |

**선택 근거**:
- wms (이미 5 active service 구현) 와 자연 시너지 — 첫 부트스트랩에서 cross-project 통합 demo 즉시 가능 (wms inbound / outbound ↔ scm procurement / logistics).
- B2B 도메인 다양성 한 번에 넓힘 (wms+scm 페어 = 창고관리 + 공급망 통합 스토리).
- 구현 난이도 적절 — erp / mes 보다 첫 도메인 risk 낮음.
- TEMPLATE.md instruction 의 첫 실 검증 (037 dry-run 은 dummy 였음).

### D3 — Template 레포 실제 추출 시점은 별도 ADR (deferred)

scm 머지 + 라이브러리 churn 안정 평가 후 결정. 평가 항목:

- 5 프로젝트 동거 시점에 libs/java-* 의 cross-project 사용 패턴 변화 (특히 scm 이 추가하는 새 라이브러리 영역)
- TEMPLATE.md instruction 의 scm 부트스트랩 실 적용 결과 (037 dry-run 의 catch 외 추가 catch 있는지)
- 사용자의 portfolio 평가 사이클 (월 1회 sync 패턴 정착 여부)

별도 **ADR-MONO-003 candidate** 발행. 본 ADR 머지 후 scm 부트스트랩 종결까지 약 1-2주 후 결정.

### D4 — erp / mes 부트스트랩 순서 (deferred)

scm 종결 후 결정. 추천 순서 (이번 세션 분석): **scm → erp → mes**. 근거:

- erp 가 hub (회계 / 구매 / 재고 통합) — scm + erp 통합 demo (scm procurement → erp AP/GL feed) 가능. erp 가 마지막에 들어가면 통합 demo 어색.
- mes 가 가장 specialized — 다른 두 도메인 + wms 위에 마지막 차별화 (mes 생산 → erp inventory feed). 도메인 진입 장벽 높아 첫이 아닌 마지막.

별도 ADR (ADR-MONO-004 candidate) 또는 사용자 도메인 의향 시점에 task 발행.

---

## 3. Consequences

### 3.1 즉시 영향

- 새 도메인 (scm) 부트스트랩 task 발행 (TASK-MONO-040 가 새 세션에서 작성 중 — 본 ADR 과 동시 진행).
- TEMPLATE.md instruction (037 dry-run 산출) 의 첫 실 적용 검증.
- 사용자가 scm 부트스트랩 진행 시 본 ADR 의 D2 선택 근거를 reference 가능.

### 3.2 중기 영향

- 5 프로젝트 동거 후 라이브러리 churn 안정 평가 → ADR-MONO-003 (Template 레포 실제 추출 결정).
- erp / mes 부트스트랩 의향 시점에 ADR-MONO-004 (순서 결정) — 또는 본 ADR 의 D4 추천 (scm → erp → mes) 이 그대로 적용.

### 3.3 위험

- scm 부트스트랩이 예상 외 큰 라이브러리 churn 야기 시 → Template 레포 추출 시점 더 늦춤. 본 ADR 의 D3 가 deferred 인 이유.
- 사용자 의향 변경 (예: erp 우선 의향) 시 D2 변경. 본 ADR 을 SUPERSEDED 표시 + ADR-MONO-005 등으로 대체.

---

## 4. Alternatives Considered (그러나 채택 안 함)

### 4.1 Phase 3 무한 유지

장점: 라이브러리 churn 0. 안정 portfolio.
단점: Template 레포 추출 catalyst 없음. portfolio 진척 정체. 사용자 의향 (새 도메인) 무시.
→ 사용자 의향 발화로 자동 기각.

### 4.2 3 도메인 동시 (scm + erp + mes)

장점: portfolio 진척 3x. 한 번에 7 프로젝트 동거.
단점: root 공유 파일 conflict (settings.gradle / ci.yml / package.json / GAP V seed / sync-portfolio.sh PROJECT_REMOTES). 사용자 인풋 bottleneck. 라이브러리 churn 한 번에 폭발 → Phase 4 진입 결정 어려움.
→ D1 에서 (b) 채택으로 기각.

### 4.3 첫 도메인 = erp (가장 broad)

장점: portfolio 깊이 (전사 시스템) 즉시 확보.
단점: 가장 broad — 첫 도메인 churn 위험 가장 큼. 모듈 5+ 동시 부트스트랩 부담.
→ D2 에서 scm 채택으로 기각. erp 는 두번째 후보.

### 4.4 첫 도메인 = mes (가장 specialized)

장점: portfolio 차별화 가장 큼.
단점: 도메인 진입 장벽 높음 — 첫 도메인 부담. wms 와 시너지 약함 (제조 현장 ≠ 창고 / 공급).
→ D2 에서 scm 채택으로 기각. mes 는 마지막 후보.

---

## 5. Migration / Implementation Plan

본 ADR 자체는 docs-only — 코드 / 빌드 영향 0.

후속 task:
1. **TASK-MONO-040** (새 세션 진행 중): scm 프로젝트 부트스트랩 — TEMPLATE.md Option A Greenfield Step 1~12 + GAP Integration Step 1~5 따라.
2. (deferred) ADR-MONO-003: scm 머지 후 Template 레포 실제 추출 결정.
3. (deferred) ADR-MONO-004 또는 task: erp / mes 부트스트랩 순서 결정.

---

### Forward pointer (2026-05-13) — D4 ordering progression

§ D4 의 `scm → finance → erp → mes` 순서 중 scm 단계 완료 (2026-05-04~07, TASK-MONO-040 / 042 + SCM-BE-001~003 + INT-001 series). 다음 단계 **finance** 결정은 **[ADR-MONO-008 — finance-platform Bootstrap](ADR-MONO-008-finance-platform-bootstrap.md)** (PROPOSED 2026-05-13, TASK-MONO-071) 로 이관. ADR-MONO-008 ACCEPTED 전환 시 finance 부트스트랩 실행 + § D4 ordering 한 칸 진행. erp / mes 단계는 ADR-MONO-008 ACCEPTED 후 별도 ADR (ADR-MONO-009 / 010 candidate) 으로 분기.

### Forward pointer (2026-05-18) — D4 ordering progressed: scm → **finance**

**[ADR-MONO-008](ADR-MONO-008-finance-platform-bootstrap.md) ACCEPTED 2026-05-18** (TASK-MONO-113, D5.1–D5.6 + § D6.1 user-explicit intent 충족; D1 Option C — Template fork `kanggle/finance-platform` + monorepo `projects/finance-platform/` direct-include; domain `fintech` / traits `[transactional, regulated, audit-heavy]`). § D4 의 `scm → finance → erp → mes` 순서가 **finance 단계로 한 칸 진행** — finance 부트스트랩 artifact = PR-B / TASK-MONO-114. 다음(`erp`) 단계는 ADR-MONO-008 § 3.2 + 본 § D4 대로 별도 ADR (ADR-MONO-009 candidate) 으로 분기, 미착수.

### Forward pointer (2026-05-19) — D4 ordering progressed: finance → **erp** (next-ADR identifier corrected)

**finance 단계 완전 종결 2026-05-19**: finance v1 이 monorepo 행위-증명 chain (TASK-MONO-115 → FIN-BE-002 → FIN-BE-003 → FIN-BE-004, `finance-integration-tests` CI 12/12) + standalone Template fork `kanggle/finance-platform` CONFIRMED + TASK-MONO-116 append-only 기록으로 **양쪽 모두 종결, ADR-MONO-008 전 항목 해소**. § D4 의 `scm → finance → erp → mes` 순서가 **erp 단계로 분기** — erp 부트스트랩 fresh ADR = **[ADR-MONO-016 — erp-platform Bootstrap](ADR-MONO-016-erp-platform-bootstrap.md)** (PROPOSED 2026-05-19, TASK-MONO-117; ADR-008/003b PROPOSED pre-author 동형, self-ACCEPT 금지). **⚠️ identifier 정정**: 위 2026-05-13 / 2026-05-18 forward-pointer 의 "ADR-MONO-009 candidate" 는 **stale** — `ADR-MONO-009` 는 Chrome DevTools MCP visual regression (OpenAI Harness gap #4, 기존 PROPOSED, TASK-MONO-072) 로 이미 점유됨. `ls docs/adr` 객관 = ADR-MONO-001..015 contiguous → erp 부트스트랩 ADR 의 정확 번호 = **016** (다음 free). 본 블록이 authoritative; 상위 dated 블록은 append-only audit history 로 byte-unchanged 보존. erp ACCEPTED 전환 + 실 부트스트랩은 ADR-MONO-016 § D6 대로 별 task / user-explicit intent 시점. erp 다음 `mes` 는 memory `project_portfolio_7axis_architecture` 에서 **의도적 드롭** (재제안 금지).

### Forward pointer (2026-05-19, post-PROPOSED) — D4 ordering progressed: erp **ACCEPTED**

**[ADR-MONO-016](ADR-MONO-016-erp-platform-bootstrap.md) PROPOSED → ACCEPTED 2026-05-19** (TASK-MONO-118, PR-A doc-only — § D6.1 user-explicit intent `"ADR-016 ACCEPTED"` [exact form] + AskUserQuestion D1 = Option C (Both) / D3 = `masterdata-service` 확정; D5.1–D5.7 평가; NOT self-ACCEPT — governed § D6 transition, ADR-MONO-008/TASK-MONO-113 동형). § D4 의 `scm → finance → erp → mes` 순서가 **erp 단계로 한 칸 진행** — erp 부트스트랩 artifact = PR-B / TASK-MONO-119 (7번째 portfolio 프로젝트, domain `erp` / `[internal-system, transactional, audit-heavy]` / `[rest-api]`, D1 Option C). 다음(`mes`) 단계는 **의도적 드롭** (memory `project_portfolio_7axis_architecture` — portfolio 최종 도메인 = erp; 별도 부트스트랩 ADR 미예정, 재제안 금지). 2026-05-13/05-18/05-19(PROPOSED) 상위 dated block byte-unchanged (append-only); 본 block 이 erp 단계의 authoritative pointer.
