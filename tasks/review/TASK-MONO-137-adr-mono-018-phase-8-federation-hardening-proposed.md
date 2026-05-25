# Task ID

TASK-MONO-137

# Title

`ADR-MONO-018 PROPOSED` — Phase 8 Federation Hardening Architecture (cross-product e2e + observability + multi-tenant isolation regression)

# Status

review

# Owner

architect

# Task Tags

- adr
- architecture
- platform-console
- phase-8

---

# Goal

ADR-MONO-013 § D6 row 8 ("Federation hardening — cross-product e2e, observability, multi-tenant isolation regression / root `tasks/` / all domains integrated / Sonnet; isolation → Opus") 는 Phase 8 을 단일 row 로만 정의하고 sub-axis 의 architectural direction 은 deferred. ADR-MONO-017 §3.3 도 *"Phase 8 (federation hardening) follows Phase 7 (§ D6 row 8)"* 로만 명시.

Phase 8 의 gate ("all domains integrated") 는 2026-05-25 시점 **이미 충족**:
- `projects/platform-console/PROJECT.md` 가 "Phase 1~6 COMPLETE + Phase 7 LIVE" 자체 선언 + `service_types: [frontend-app, rest-api]` 반영
- ADR-MONO-017 ACCEPTED 2026-05-20 + `console-bff` skeleton (PC-BE-001) + Operator Overview MVP (PC-FE-011) + Domain Health (PC-BE-002) + 30+ 후속 PC-FE 종결
- GAP+wms+scm+finance+erp 5/5 backend domains live · `console-bff` D7 per-domain fan-out attribution 베이스라인 ON

본 task 는 ADR-MONO-014/015/017 staged-child 패턴을 답습해 **`ADR-MONO-018 PROPOSED`** 본문을 작성한다. Phase 8 의 3 sub-axis (cross-product e2e + observability federation + multi-tenant isolation regression) 를 **6-8 개의 D-decision** 으로 분해, 각 option table + CHOSEN-PROPOSED + finalised-at-ACCEPTED note + downstream sequencing + ADR-013 additive amendment block (HARDSTOP-04 — D1-D8 byte-unchanged) 을 포함.

**본 task = ADR 본문 작성 1건만**. ACCEPTED transition / 실 cohort 작성 / observability federation 구현 / isolation regression IT 작성은 **모두 post-ACCEPTED future task** (ADR-014/015/017 의 ACCEPTED-flip task 와 execution task 가 별 lifecycle 인 패턴 답습).

**근거 메모**:
- `project_platform_console_adr_013` § "잔여=Phase 8 federation hardening"
- `project_2026_05_25_cross_project_sweep_and_recovery` § 잔여 ready/ = 0 (TRUE 0) → 다음 trigger = 새 retrofit cycle / libs v2 (≥ 2026-06-10) **또는 Phase 8**

---

# Scope

## In Scope

| 산출물 | 위치 | 설명 |
|---|---|---|
| 새 ADR | `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` | PROPOSED 본문. ADR-MONO-017 헤더 패턴 답습: Title / Status=PROPOSED / Date=YYYY-MM-DD / History (PROPOSED 라인) / Decision driver / Supersedes=none / Amends=ADR-013 § D6 row 8 (additive) + § History (additive blockquote) / Related=ADR-013/017/006 (observability stack). 본문 = §1 Context (gate 충족 evidence + § D6 row 8 deferred axis 열거) / §2 Decision (D1-D8 option tables) / §3 Consequences (hard invariants + scope-out + future-self) / §4 Alternatives (cross-cutting) / §5 Relationship to ADR-013/014/015/017 / §6 Audit-trail. |
| ADR-013 additive amendment | `docs/adr/ADR-MONO-013-platform-console-foundation.md` | (1) § D5 line 76 영역에 *additive* parenthetical scope-pointer to ADR-018 (ADR-017 와 동형 추가). (2) § History 에 *additive* "Additive note" blockquote (count 5 → 6, Phase 4 + 5 + 6 + Phase 7 PROPOSED + Phase 7 ACCEPTED 다음 row). **D1-D8 byte-unchanged 엄수** (HARDSTOP-04). |
| INDEX 갱신 | `tasks/INDEX.md` ready 섹션 | 본 task 한 줄 entry 등재 (이미 본 PR 에서 함께) → done 섹션은 후속 close-chore PR 에서. |

## Out of Scope

- ADR-018 의 PROPOSED → ACCEPTED transition (별 task — `TASK-MONO-1xx`, 사용자 명시 intent gate; ADR-014/015/017 의 MONO-110/112/126 패턴 답습).
- cross-product e2e suite 실 작성 (별 task series, post-ACCEPTED).
- observability federation 실 구현 (Vector 추가 routing / VictoriaMetrics dashboard / OTel trace correlation 등).
- multi-tenant isolation regression IT cohort 실 작성 (5 도메인 × 1 IT 또는 분해).
- ADR-013 의 D1-D8 본문 어떤 decision 도 변경/삭제 (additive amendment 만 — 위반 시 HARDSTOP-04).
- 새 ADR 의 ACCEPTED-time finalisation (PROPOSED 가 CHOSEN-direction 만 sketch, ACCEPTED 에서 byte-unchanged finalise — sibling pattern 답습).

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` 생성. Status=`PROPOSED`. Title / Date (오늘) / History (PROPOSED 라인 — 본 task ID + 본 PR 번호 placeholder) / Decision driver (§ D6 row 8 + gate 충족 evidence) / Supersedes=`none` / **Amends=ADR-MONO-013 § D6 row 8 (additive parenthetical scope-pointer) + § History (additive note)** / Related=ADR-MONO-013/017/006/015.
- [ ] §2 Decision 에 **6-8 개의 D-decision** option table 포함. 각 D 는 (a) **CHOSEN-PROPOSED** 1 option + (b) Rejected option 1-3개 + (c) "Finalised at ACCEPTED" annotation. 권장 sketch (구체 mechanics 는 ADR 작성 agent 가 정함):
  - **D1 — cross-product e2e suite location & trigger**: root `tasks/` + root `.github/workflows/` 신규 워크플로우 (ADR-013 명시 일치) vs 기존 platform-console nightly e2e 확장 vs 도메인별 분산.
  - **D2 — cross-product e2e harness**: Playwright extended (PC-FE-019~031 harness 위에 cross-product 시나리오 추가) vs Testcontainers fan-out vs 새 stack 도입.
  - **D3 — e2e scope (MVP)**: golden path per domain × 1 + 핵심 cross-domain composition 검증 (Operator Overview / Domain Health 의 5-domain fan-out 실 e2e) vs 전체 surface.
  - **D4 — observability federation pattern**: 기존 Vector + VictoriaMetrics (ADR-MONO-006) 재사용 + console-bff D7 per-domain attribution 베이스라인 위에 cross-product trace correlation (OTel trace_id propagation 강화) vs 새 stack.
  - **D5 — multi-tenant isolation regression cohort**: per-domain `TenantClaimValidator` IT regression × 5 도메인 + console-bff D6 (ADR-017) tenant pass-through 의 cross-tenant deny IT vs central BFF gate.
  - **D6 — Phasing (3 sub-axis 묶음 vs 분리)**: 본 ADR 가 3 axis 동일 governance 묶음 PROPOSED → 실행 task 는 axis 별 분리 (FE-001..010 / BE-001..005 시리즈 답습).
  - **D7 — Gate & ownership**: gate=Phase 7 LIVE (이미 충족); ownership=root `tasks/` (cross-product) + 일부 project-internal (per-domain isolation IT).
  - **D8 — ACCEPTED transition mechanics**: 사용자 명시 intent forms + commit pattern (PR-A doc-only + PR-B execution task series).
- [ ] §3 Consequences 의 *hard invariants this ADR carries* 에 다음 명시:
  - **§ 3.3 "zero retrofit" — sixth confirmation** (Phase 2/4/5/6/7 다음 Phase 8 — D3/D5 가 기존 producer spec / 도메인 코드 byte-unchanged 보존).
  - **Per-domain credential rule (`console-integration-contract.md` § 2.4.5/6/7/8 + ADR-017 D4)** — Phase 8 도 dispatcher / regress 일 뿐, 절대 rewrite 아님.
  - **ADR-MONO-013 D1-D8 byte-unchanged** (additive amendment 만 — HARDSTOP-04).
  - **ADR-MONO-017 D1-D8 byte-unchanged** (Phase 8 이 console-bff 의 D1-D8 위에서 검증/하드닝, 절대 redirect 아님).
- [ ] §3 의 *what this ADR does NOT do (deferred to ACCEPTED + post-ACCEPTED tasks)* 명시 — 실 cohort 작성 / observability 구현 / IT 작성은 별 task. ADR-017 §3.2 동형.
- [ ] §3.3 Future-self 의 ACCEPTED execution chain (sketch) 4 step 명시 — (1) ADR-018 PROPOSED→ACCEPTED transition task, (2) cross-product e2e 실 cohort task, (3) observability federation 실 구현 task, (4) multi-tenant isolation regression IT cohort task. 각각 sibling-task-id placeholder.
- [ ] §5 Relationship to ADR-013/014/015/017 마지막 row 추가 형식 — Phase 8 관점 (PREREQ for federation hardening execution tasks).
- [ ] `docs/adr/ADR-MONO-013-platform-console-foundation.md` 의 **§ D5 line 76 영역에 additive parenthetical scope-pointer** to ADR-018 (ADR-017 의 추가 form 미러) **+ § History 의 additive blockquote** (count 5→6).
- [ ] `tasks/INDEX.md` ready 섹션에 본 task 한 줄 entry 등재 (본 PR 에 포함). done 섹션 추가는 후속 close-chore PR.
- [ ] **HARDSTOP-04 검증**: `git diff` 로 ADR-013 § D6 / § D7 / § D8 / D1-D5 body 가 byte-unchanged (line 100~143 영역 D1-D8 본문 손대지 않음, additive 만). ADR-017 D1-D8 도 byte-unchanged.
- [ ] **HARDSTOP-09 회피**: ADR-018 가 PROPOSED 상태로 머지되어도, 본 PR 은 실 cohort 코드를 작성하지 않음 (spec-only PR — PR Separation Rule 준수).

---

# Related Specs

- [ADR-MONO-013 § D6 row 8](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — Phase 8 의 raw description (cross-product e2e + observability + multi-tenant isolation regression).
- [ADR-MONO-017 § 3.3 line 133](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) — *"Phase 8 (federation hardening) follows Phase 7"*. staged-child 패턴 reference.
- [ADR-MONO-006](../../docs/adr/ADR-MONO-006-observability-stack.md) — Vector + VictoriaMetrics, D4 (observability federation) 재사용 베이스.
- [ADR-MONO-015](../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) — Composed-overview pattern, D3 scope MVP 의 cross-domain dashboard 검증 reference.
- [ADR-MONO-014](../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) — staged-child PROPOSED→ACCEPTED 패턴 prior precedent (ADR-017 와 함께 본 ADR-018 의 frame model).
- [projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.5/6/7/8](../../projects/platform-console/specs/contracts/console-integration-contract.md) — per-domain credential rule (Phase 8 의 D5 regression cohort 가 보호하는 invariant).
- [projects/platform-console/PROJECT.md](../../projects/platform-console/PROJECT.md) — Phase 7 LIVE 자체 선언 + service_types 반영.

# Related Skills

- `.claude/skills/common/architect/` — ADR drafting + staged-child pattern.
- (없음, agent dispatch 시 추가)

# Related Contracts

- `console-integration-contract.md` § 2.4.5/6/7/8 (per-domain credential — D5 isolation regression 의 target invariant)
- (없음, ADR 가 contract 가 아니라 architectural decision)

---

# Implementation Notes

## Staged-child PROPOSED 패턴 (ADR-014/015/017 답습)

| Stage | 산출물 | 본 task 범위 |
|---|---|---|
| **PROPOSED** | D1-D8 frame + CHOSEN-direction (mechanics 명확, "Finalised at ACCEPTED") + downstream sequencing + ADR-013 additive amendment | **본 task** |
| ACCEPTED transition | Status: PROPOSED → ACCEPTED, History row 추가, byte-unchanged D1-D8 finalise | 별 task (post-MONO-137) |
| Execution | cross-product e2e cohort + observability federation 실 구현 + isolation regression IT cohort | 별 task series (post-ACCEPTED) |

## Phase 8 의 3 sub-axis 분해 (ADR-018 본문 §2 Decision 의 D-decision 골격)

ADR-013 § D6 row 8 의 원문 = *"cross-product e2e, observability, multi-tenant isolation regression"*. 이 3 axis 를 D-decision 으로 매핑하는 권장 sketch:

| sub-axis | 매핑되는 D-decision | CHOSEN-PROPOSED 방향 sketch |
|---|---|---|
| cross-product e2e | D1 (location & trigger), D2 (harness), D3 (scope MVP) | root `tasks/` + 새 `.github/workflows/federation-hardening-e2e.yml`; Playwright extended (PC-FE-019~031 harness 위 cross-product 시나리오); golden path per domain × 1 + Operator Overview / Domain Health 의 5-domain fan-out 실 e2e |
| observability | D4 | 기존 Vector + VictoriaMetrics (ADR-006) 재사용 + console-bff D7 per-domain attribution (ADR-017) 위에 OTel `trace_id` propagation 강화 — 5 도메인 + BFF + console-web 의 cross-product trace correlation |
| multi-tenant isolation regression | D5 | per-domain `TenantClaimValidator` IT regression × 5 + console-bff D6 (ADR-017) tenant pass-through 의 cross-tenant deny IT |

추가 D-decision:
- **D6 Phasing**: 3 axis 묶음 PROPOSED + 실행은 axis 별 분리 (FE 시리즈 답습)
- **D7 Gate & ownership**: gate=Phase 7 LIVE (충족); ownership=root + 일부 project-internal
- **D8 ACCEPTED transition mechanics**

총 D1-D8 8개 (ADR-017 동일 개수 — operational symmetry).

## 본문 § History 의 PROPOSED 라인 형식 (ADR-017 line 5 mirror)

```
**History:** PROPOSED YYYY-MM-DD (TASK-MONO-137, PR #<this> squash `<commit>` — resolves the architecture-decision dimension of ADR-MONO-013 § D6 Phase 8 ahead of any federation-hardening implementation, mirroring the ADR-MONO-014 / ADR-MONO-015 / ADR-MONO-017 staged-child pattern; decision direction **CHOSEN-PROPOSED** per the dispatcher reasoning recorded below, **finalised at ACCEPTED**).
```

## ADR-013 § History 의 additive blockquote 형식

ADR-013 § History 에는 이미 4-5 개의 *additive note* blockquote 가 있어야 함 (Phase 4/5/6/ADR-017 PROPOSED/ADR-017 ACCEPTED). 본 PR 의 추가:

```
> **Additive note (YYYY-MM-DD, ADR-MONO-018 PROPOSED)** — Phase 8 federation hardening 의 architecture-decision 차원이 ADR-MONO-018 으로 분리 (TASK-MONO-137, PR #<this>). § D6 row 8 의 location/trigger/harness/scope/observability federation/isolation regression cohort/phasing/gate 가 ADR-018 D1-D8 로 분해되어 PROPOSED 됨. 본 ADR (013) 의 D1-D8 / § D5/ § D6/ § D7/ § D8 본문은 byte-unchanged (HARDSTOP-04). ADR-018 ACCEPTED 후 execution task series 가 별도 분리됨.
```

ADR-013 의 D5 line 76 영역 (`platform-console` 의 `service_types` 가 `rest-api` 를 가지는 시점 prescribed 영역) 의 parenthetical scope-pointer:

```
(see [ADR-MONO-018](ADR-MONO-018-platform-console-phase-8-federation-hardening.md) for the Phase 8 federation-hardening architecture, sibling to ADR-MONO-017 Phase 7 BFF architecture)
```

ADR-017 가 이미 같은 영역에 자기 pointer 를 가지고 있으므로, 본 task 는 그 옆에 sibling pointer 를 *additive* 로 추가하면 됨.

## ADR 본문 작성 dispatch

본 task 의 ADR 본문은 multi-axis decision 분해 + 5 ADR (013/014/015/017/006/015) 와의 정합 필요 → **Opus 권장** (analyst + architect 역할 분리 가능). 본 task 작성자가 직접 본문 쓰거나, architect agent 에 dispatch.

dispatch 시 agent prompt 골격:
```
You are drafting ADR-MONO-018 PROPOSED. Read ADR-MONO-013/014/015/017
verbatim. Author docs/adr/ADR-MONO-018-platform-console-phase-8-federation-
hardening.md following the ADR-017 staged-child PROPOSED pattern. D1-D8
must cover the 3 sub-axes per task spec. ADR-013 § D6 row 8 stays
byte-unchanged; you may only add an additive § History note blockquote
and an additive parenthetical scope-pointer near line 76.
```

## Spec PR shape

본 PR 산출물:
- `docs/adr/ADR-MONO-018-...` 신규
- `docs/adr/ADR-MONO-013-...` additive amend (§ History 1 blockquote + § D5 line 76 영역 1 parenthetical)
- `tasks/ready/TASK-MONO-137-...` 본 task md
- `tasks/INDEX.md` ready entry

**impl code 절대 0** (PR Separation Rule). PR 본문은 ADR-017 PROPOSED PR (#663) 의 form 답습.

---

# Edge Cases

- **ADR-013 D1-D8 본문에 손이 가야 하는 case** → HARDSTOP-04. 그런 case 가 발견되면 PROPOSED 단계에서 **STOP** + ADR-018 가 ADR-013 의 decision 을 변경하는 형태로 재논의. additive 가 부족하면 PROPOSED 자체가 fail (ADR-014 의 ADR-013 D2 영역 amendment 도 additive 만 — pattern 일치).
- **Phase 8 gate "all domains integrated" 미충족 케이스** → 본 task 는 충족 가정. 미충족 발견 시 PROPOSED 자체 spec-first revert (Phase 7 잔여 task 우선).
- **3 sub-axis 의 D-decision 분해가 8 개 초과** → 묶음 PROPOSED 의 의도 (ADR-013/017 와 operational symmetry) 와 trade-off. 8 개 초과 시 그 자체로 sub-axis 분리 신호 → 그 결정도 PROPOSED 가 명시 (D8 phasing 에서 *"3 axis 가 단일 ADR 로 묶기에 too-coupled — 별 ADR 로 분리"* 형태).
- **ADR-006 observability stack 의 amendment 필요** → D4 observability federation 이 trace_id propagation 강화를 요구 시 ADR-006 byte-unchanged 인지 amendment 필요인지 PROPOSED 가 명시. amendment 필요면 본 task scope 확장 (또는 별 ADR-MONO-019).
- **per-domain producer spec 변경 필요** → § 3.3 "zero retrofit" 위배. 위배 발견 시 D3/D5 의 CHOSEN-direction 재검토 (ADR-018 PROPOSED 가 zero-retrofit 보존 책임).
- **ADR-018 ACCEPTED 전 execution task 생성 시도** → HARDSTOP-09 (architecture decision missing). PROPOSED 머지로는 부족 — ACCEPTED 필수.

---

# Failure Scenarios

- **HARDSTOP-04 위반** (ADR-013 D1-D8 본문 변경): commit 직전 `git diff docs/adr/ADR-MONO-013-platform-console-foundation.md` 로 byte-unchanged 검증. additive 만 통과.
- **D-decision option table 의 "Rejected" reasoning 누락**: ADR-017 의 모든 D-decision 이 rejected option 의 rationale 까지 명시. 본 ADR-018 도 동형. 누락 시 ADR review fail.
- **Phase 8 "all domains integrated" gate 의 evidence link 누락**: § 1 Context 에 PROJECT.md "Phase 1~6 COMPLETE + Phase 7 LIVE" line + ADR-017 ACCEPTED-line + 5/5 federation count 의 명시적 reference. 누락 시 review fail.
- **CHOSEN-PROPOSED 방향이 ADR-013/017 의 invariant 위배**: e.g. D3 가 producer 추가 endpoint 요구 → § 3.3 zero retrofit 6th confirmation 불가능. 위배 발견 시 CHOSEN 재선정.
- **ADR-018 본문이 staged-child 패턴 이탈** (e.g. ACCEPTED 에서 finalise 안 하고 PROPOSED 에서 모든 mechanics 확정 시도): ADR-014/015/017 의 sibling pattern 과 sequence drift. ADR-017 본문 5번 (line 147~157) 의 table 행 형식 답습으로 자기 검증 가능.

---

# Test Requirements

ADR 가 spec 이라 단위/통합 테스트 0. 검증 = **doc review** + **HARDSTOP linter** 자동검사:

- [ ] `.claude/hooks/spec-check.ps1` 가 본 PR diff 에서 spec 변경 명시
- [ ] markdown lint (CI path-filter markdown fast-lane) pass
- [ ] ADR-013 의 D1-D8 영역이 `git diff` 에서 0 line 변경 (additive 만)
- [ ] 새 ADR-018 의 §1~§6 모두 존재 (manual section count check)
- [ ] §2 Decision 의 D-decision count == 6~8 (ADR-017 8개 동형)
- [ ] §3 Consequences 의 hard invariants list 에 § 3.3 6th confirmation + per-domain credential rule + ADR-013/017 D1-D8 byte-unchanged 모두 명시
- [ ] §6 Audit-trail row 형식이 ADR-017 §6 와 column 일치

---

# Definition of Done

- [ ] `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` 작성 완료 (Status=PROPOSED, D1-D8, §1~§6)
- [ ] `docs/adr/ADR-MONO-013-platform-console-foundation.md` additive amendment (§ D5 line 76 영역 parenthetical + § History additive blockquote)
- [ ] HARDSTOP-04 검증 (ADR-013 D1-D8 byte-unchanged)
- [ ] `tasks/INDEX.md` ready entry 등재 (본 PR 에 포함)
- [ ] commit + push (branch `task/mono-137-adr-018-phase-8-federation-hardening-proposed` — substring `main`/`master` 회피 확인 ✓)
- [ ] PR open (사용자 요청 시; ADR-017 PROPOSED PR #663 의 본문 형식 답습)
- [ ] Ready for ACCEPTED transition (별 task; 본 task scope 외)
