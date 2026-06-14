# Task ID

TASK-FIN-BE-026

# Title

**재평가 × 로트 carrying 정합(D4-a)** — ADR-001 D4 의 마지막 실행 태스크. FX 재평가가 포지션 carrying 을 spot 으로 true-up 할 때, 열린 로트들의 `carrying_base_minor` 도 함께 spot 으로 mark 하여 `Σ 열린 로트 carrying == 포지션 carrying C` 불변식을 유지한다. 이로써 재평가가 개입한 포지션에서도 FIN-BE-025 FIFO 결제가 lot-exact·이중계상 0 으로 정확해진다. 스키마 변경 없음(코드-only).

# Status

ready

# Owner

backend

# Task Tags

- fintech
- transactional
- audit-heavy

---

# Dependency Markers

- **child of**: ADR-001 (finance-platform, ACCEPTED) § 2 D4(D4-a) + § 3.1 step 4.
- **선행**: FIN-BE-024(로트 테이블·취득 훅, done — `FxPositionLotRepository`), FIN-BE-025(FIFO 결제, done — 로트 carrying 을 소비 원가로 사용).
- **closes**: ADR-001 D4 hazard("재평가가 pool carrying 을 바꾸는데 로트는 취득원가 보존 → 이중계상"). FIN-BE-025 가 남긴 "재평가 개입 포지션 정합은 FIN-BE-026" 경계를 해소.
- **shadow-safe / net-zero**: 분배는 `fx_position_lot.carrying_base_minor` 만 갱신한다. 가중평균 결제는 로트가 아니라 집계 carrying 을 읽으므로 **비-FIFO 테넌트 결제 결과 무변경**. 재평가 엔트리(2줄)·`FxRevaluationPolicy`·집계 carrying 산정 byte-unchanged.

# Goal

재평가 직후 열린 로트의 carrying 을 spot 으로 mark 하여 `Σ 로트 carrying == 새 포지션 carrying(revaluedBase)` 를 항상 성립시킨다 — FIN-BE-025 FIFO 결제의 lot-exact·residual 정확성이 재평가 이력과 무관하게 보장되도록.

# Scope

- **분배 컴포넌트** (예 `DistributeRevaluationToLots` 또는 `RevalueForeignBalanceUseCase` 내 private 메서드): 재평가 entry 포스팅 후, 같은 `@Transactional` 안에서:
  - `fxPositionLotRepository.findOpenLots(tenant, code, currency)` 로드(`remaining>0`).
  - **mark-to-spot**: 각 로트 `newCarrying = round(lot.remainingForeignMinor × closingRate, HALF_UP)`(magnitude). **마지막 로트**가 반올림 잔차 흡수 — `last.newCarrying = |revaluedBase| − Σ(이전 로트 newCarrying)` 로 `Σ newCarrying == |revaluedBase|`(= 새 집계 carrying magnitude) 정확 일치. 각 로트 `carrying_base_minor = newCarrying`, `save`.
  - `revaluedBase` = 재평가가 산정한 새 carrying(= `carryingBaseMinor + delta`); use case 가 이미 보유(`RevaluationResult.delta()` + 원 carrying). magnitude 로 처리(저장은 ABS, FIN-BE-024/025 와 일관).
  - **로트 없음**: 분배 skip(집계 재평가는 그대로 포스팅; 가중평균 결제 무영향). **shadow desync**(Σremaining ≠ |F|): 마지막-로트 흡수로 `Σ carrying == revaluedBase` 는 유지(FIN-BE-025 의 fallback 안전판과 호환).
- **호출 위치**: `RevalueForeignBalanceUseCase.revalue(...)` 의 `postJournalEntryUseCase.post(...)` 직후, 같은 Tx. no-op(REPLAY/NO_POSITION/AT_SPOT) 경로에서는 분배 안 함(delta 없음).
- **always-apply**: cost-flow 설정과 무관하게 분배(로트는 항상 생성되므로 일관성 유지; 비-FIFO 는 net-zero). 설정 분기 불필요.
- **Spec**: `architecture.md` § FX gain/loss revaluation(또는 § FX position lots)에 "§ Revaluation lot carrying distribution" 하위절(18번째 증분, ADR-001 D4-a; mark-to-spot·마지막-로트 잔차·불변식).
- **NO change**: `FxRevaluationPolicy`(2줄 엔트리·delta 산정 불변), `FxSettlementPolicy`, 취득 훅, cost-flow 설정, reconciliation. **마이그레이션 없음**(코드-only — 기존 컬럼 값만 갱신).

# Acceptance Criteria

- **AC-1** **불변식**: 다중-로트 외화 포지션을 재평가하면, 재평가 후 `Σ 열린 로트 carrying_base_minor == revaluedBase`(= 새 집계 carrying = `ABS(ΣbaseDebit−ΣbaseCredit)`). 각 로트 carrying ≈ `round(remaining × closingRate)`, 마지막 로트가 잔차 흡수.
- **AC-2** **FIFO×재평가 lot-exact**: 두 취득(이종환율) 후 재평가 → FIFO 부분결제 시 `C_settle` 가 **재평가 반영된 로트 carrying** 으로 산정(이중계상 0). 재평가-후-결제 realized 가 D4 이중계상 버그 없이 정확.
- **AC-3** **net-zero(비-FIFO)**: WEIGHTED_AVERAGE·미설정 테넌트의 재평가→결제 결과 byte-unchanged(가중평균은 집계 carrying 사용; 로트 분배가 결제에 무영향). 기존 revaluation/settlement IT 전부 GREEN.
- **AC-4** **재평가 엔트리 불변**: 재평가 2줄 엔트리(baseAdjustment + FX_GAIN/LOSS contra)·`delta`·집계 carrying·멱등(`reval:{key}`)·closed-period 가드 모두 byte-unchanged.
- **AC-5** 로트 없음/AT_SPOT/REPLAY/NO_POSITION → 분배 skip(no-op 안전). 비음수 유지(`carrying >= 0` CHECK 위반 없음 — spot>0, remaining>=0).
- **AC-6** Testcontainers IT `LedgerRevaluationLotDistributionIntegrationTest`: 다중-로트 재평가 후 Σ carrying == revaluedBase(AC-1) + 재평가-후 FIFO 결제 lot-exact(AC-2) + 비-FIFO net-zero 대조(AC-3). 고유 `ledgerAccountCode`(예 `FX_REVAL_USD_WALLET`).
- **AC-7** `:test` + `:integrationTest` GREEN(Docker on). IT 권위.

# Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (§ 2 D4/D4-a, § 3.1 step 4)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ FX gain/loss revaluation — § Revaluation lot carrying distribution 추가)

# Related Contracts

- 없음 — 로트 carrying 갱신은 도메인-internal 영속 상태. 재평가 엔트리·`entry.posted` outbox payload·외부 계약 shape 불변.

# Edge Cases

- 로트 없음 → 분배 skip(집계 재평가만; 가중평균 결제 무영향).
- shadow desync(Σremaining ≠ |F|) → 마지막-로트 흡수로 `Σ carrying == revaluedBase` 강제(FIN-BE-025 fallback 과 호환). 개별 로트는 자기 foreign×spot 와 다를 수 있음(알려진 제약).
- 손실 재평가(delta<0): 각 로트 carrying 감소하나 `newCarrying = round(remaining × spot) >= 0`(spot>0) → 비음수 유지. 완전 가치소멸(spot→0 극단)은 closingRate>0 가드로 배제.
- 반올림: 마지막 로트만 잔차 흡수(나머지는 round). 단일 로트면 그 로트가 곧 revaluedBase(정확).

# Failure Scenarios

- 분배가 재평가 엔트리/집계 carrying 을 바꾸면 → 범위 위반. `FxRevaluationPolicy`·엔트리 byte-unchanged, 로트 carrying 만 갱신.
- 마지막-로트 잔차 흡수 누락 → `Σ carrying ≠ revaluedBase`(이중계상/누락) → FIN-BE-025 residual 오류. 마지막 로트가 `revaluedBase − Σ(others)` 흡수 필수.
- 분배를 재평가와 다른 Tx 에 하면 → 원자성 위반. 같은 `@Transactional`.
- 비-FIFO 결제 결과가 바뀌면 → net-zero 위반(분배는 집계 carrying 미변경이라 가중평균 무영향이어야).
