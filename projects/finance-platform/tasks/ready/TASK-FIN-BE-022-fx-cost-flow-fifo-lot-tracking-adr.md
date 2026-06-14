# Task ID

TASK-FIN-BE-022

# Title

Author **ADR-001 (finance-platform) PROPOSED → ACCEPTED** — 외화 포지션 원가흐름(cost-flow)을 가중평균에서 FIFO 로트(lot) 추적으로(per-tenant 설정·opt-in·net-zero). ledger-service 15번째 증분의 방향 결정으로, 14증분(FIN-BE-007~021)까지의 가중평균 pool 처분원가 모델을 회고적으로 기록하고 FIFO 취득-로트 원가추적으로의 전환을 결정한다. cost-flow method(FIFO vs 가중평균)는 **실현손익 숫자를 바꾸는 회계정책 결정**이라 코드로 묵시 선택하면 HARDSTOP-09 + 기존 테넌트 reconciliation 드리프트 → 결정을 먼저 기록하고 PAUSE. Doc-only; ACCEPTED 게이트는 사용자 명시 "ACCEPTED 승급 + 구현 시작"(같은 PR PROPOSED→ACCEPTED, ADR-MONO-033 동형) 충족. 구현(FIN-BE-023~026)은 별도 user-explicit-intent 태스크(staged-child).

# Status

ready

# Owner

architecture

# Task Tags

- docs
- adr
- fintech

---

# Dependency Markers

- **child of**: ADR-MONO-008 (finance-platform bootstrap, ACCEPTED) — 부트스트랩이 연 ledger 도메인의 **내부 회계방식** 결정. ADR-MONO-008 의 도메인/trait(`fintech` / `[transactional, regulated, audit-heavy]`)을 재결정하지 않는다.
- **finance-platform 첫 프로젝트 ADR** — `projects/finance-platform/docs/adr/` 디렉터리를 신설한다(ecommerce ADR-001~007 / iam ADR-001~005 와 동일한 프로젝트-스코프 넘버링).
- **triggered by**: 사용자 명시 선택 2026-06-14 — ledger 후속 작업으로 "(a) FIFO/lot cost-basis 설계" 채택(ADR-MONO-033 staged-child 직후, 충돌 없는 finance 평면 방향).
- **mirrors**: V7 `reconciliation_fx_tolerance` (FIN-BE-020) — per-tenant 설정 + 미설정시 net-zero 기본(D1 의 설정 패턴 차용).
- **does NOT change**: 결제 엔트리 shape(3-line), 이중기입 KRW 균형, F8(matcher non-auto-post), 멱등(`settle:{key}`) — cost-flow 는 `C_settle` 도출 내부 정책일 뿐(불변식 무영향).

# Goal

ADR-001 (finance-platform) 을 Status PROPOSED 로 발행해, ledger 외화 결제의 cost-flow method 가 **기록된 결정**(per-tenant 설정 · 기본 WEIGHTED_AVERAGE net-zero · FIFO opt-in · materialized 로트 · 재평가 분배 D4-a)에 따라 구현되도록 — ACCEPTED 와 실행은 별도 user-explicit-intent 태스크로 게이팅.

# Scope

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (NEW, Status PROPOSED) — § 1 Context(1.1 현재 가중평균 pool 모델 factual + 1.2 미기록 사유 + 1.3 결정 드라이버) + § 2 Decisions(D1 per-tenant 설정·opt-in / D2 materialized `fx_position_lot` / D3 FIFO 소비·실현손익 / D4 재평가×lot 분배 hazard / D5 additive 마이그레이션·backfill) + § 3 Consequences + § 3.1 실행 로드맵(FIN-BE-023~026 sketch) + § 4 Alternatives + § 5 관계 + § 6 Status Transition History.
- `projects/finance-platform/docs/adr/README.md` (NEW) — finance-platform ADR 인덱스 1줄(첫 ADR 등록).
- Doc-only. **NO 코드/스키마/마이그레이션 변경** (HARDSTOP-09 remediation: 결정 기록 후 PAUSE until ACCEPTED). `apps/` 무변경, V9 마이그레이션 미작성.

# Acceptance Criteria

- **AC-1** ADR-001 이 Status PROPOSED 로 존재하며 D1~D5 가 CHOSEN-PROPOSED 로 기록된다.
- **AC-2** § 1.1 이 현재 가중평균 pool 모델을 factual 하게 기록한다 — `SettleForeignPositionUseCase.accountTotalsForCurrency` 집계 로드 + `C_settle = round(C_total × |F_settle|/|F_total|)` 공식 + "로트 개념 전무·`journal_line` 이 per-line rate/base 를 이미 보유하나 집계로만 조회" 사실.
- **AC-3** D1 의 버린 대안(B 전역교체=net-zero 위반 / C LIFO=IFRS 금지 / D 이동평균=가중평균 동치)이 거부 사유와 함께 기록된다.
- **AC-4** D4 가 재평가×lot 이중계상 hazard 를 명시하고, D4-a(로트 mutable `carrying_base_minor` 에 재평가 델타 pro-rata 분배)를 채택하며, 1차 포함(권장) vs follow-up deferred 의 trade-off 를 구현 태스크 택일로 남긴다.
- **AC-5** § 3.1 실행 로드맵이 post-ACCEPTED 4 태스크(FIN-BE-023 설정 / 024 로트테이블+backfill / 025 FIFO 소비 / 026 재평가분배)를 model 권장과 함께 sketch 한다.
- **AC-6** Doc-only diff — `apps/` 코드 0, 마이그레이션 0, 계약 0. ADR + README 만.
- **AC-7** ADR-001 은 PROPOSED 로 작성된 뒤 **사용자 명시 "ACCEPTED 승급 + 구현 시작"** 지시로 같은 PR 에서 ACCEPTED 로 전환된다(NOT self-ACCEPT — 사용자 직접 지시; ADR-MONO-033 동형). § 6 에 PROPOSED + ACCEPTED 2행 + § 3.1 UNPAUSED. 실행(FIN-BE-023~026)은 본 PR 에 코드 없이 별도 태스크로 남는다.

# Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ FX settlement / revaluation — 본 ADR 이 형식화하는 cost-flow 정책의 상위 명세)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (parent — finance 도메인/trait 정의)

# Related Contracts

- 없음 — cost-flow 는 IdP-internal·도메인-internal 회계정책이며 외부 계약(이벤트·HTTP) shape 를 바꾸지 않는다. `finance.transaction.*` / `entry.posted` outbox 계약 불변.

# Edge Cases

- ADR-001 은 PROPOSED only — Status ACCEPTED self-flip 금지, 코드/마이그레이션 0 (V9·`fx_position_lot`·`fx_cost_flow_config` 는 post-ACCEPTED 실행 태스크).
- finance-platform `docs/adr/` 신설 — 프로젝트-스코프 넘버링(ADR-001), ADR-MONO 네임스페이스와 혼동 금지(cost-flow 는 monorepo-level 아님).
- D5 backfill synthetic 로트의 `carrying` 은 현 pool carrying 과 **정확히 일치**해야(이중계상 0) — ADR 은 이 제약을 기록만 하고 구현은 FIN-BE-024.
- net-zero 불변식 — 미설정 테넌트(WEIGHTED_AVERAGE)는 byte-unchanged 여야 함을 ADR 이 D1/D5 에 명시.

# Failure Scenarios

- ADR 가 작성되면서 동시에 코드/마이그레이션이 구현되면 → HARDSTOP-09 remediation 위반(결정이 구현에 선행·ACCEPTED 되어야). 본 태스크는 doc-only.
- ADR-001 이 **사용자 지시 없이** self-ACCEPTED 되면 → staged-child 패턴 위반. 본 태스크의 ACCEPTED 전환은 사용자 명시 "ACCEPTED 승급 + 구현 시작" 지시에 한해 같은 PR 에서 허용(NOT self-ACCEPT; ADR-MONO-033 동형). 사용자 지시 부재 시 PROPOSED 로 멈춘다.
- cost-flow 기본값을 FIFO 로 두거나 전역 교체로 기록하면 → net-zero 위반(기존 테넌트 실현손익 소급 변경). D1 = 미설정시 WEIGHTED_AVERAGE 고정.
- LIFO 를 옵션으로 추가하면 → IFRS/K-IFRS 금지. method ∈ {WEIGHTED_AVERAGE, FIFO} 로 한정.
