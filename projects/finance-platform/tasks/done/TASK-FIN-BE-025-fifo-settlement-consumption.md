# Task ID

TASK-FIN-BE-025

# Title

**FIFO 로트 소비 settlement** — ADR-001 D3 의 실행 태스크. `FIFO` 로 설정된 테넌트의 외화 결제 시, 가중평균 pool 대신 **열린 로트를 `(acquired_at, seq)` 오름차순으로 walk 소비**해 `C_settle` 를 lot-exact 로 산정하고 실현손익을 취득시점 환율에 정확 귀속한다. 결제 엔트리 shape·불변식 불변, `WEIGHTED_AVERAGE`(미설정 포함) 경로는 byte-identical(net-zero).

# Status

done

# Owner

backend

# Task Tags

- fintech
- transactional
- audit-heavy

---

# Dependency Markers

- **child of**: ADR-001 (finance-platform, ACCEPTED) § 2 D3 + § 3.1 step 3.
- **선행**: FIN-BE-023(cost-flow 설정, done — `FxCostFlowConfigRepository`/`CostFlowMethod`), FIN-BE-024(로트 테이블·취득 훅·backfill, done — `FxPositionLotRepository.findOpenLots`).
- **후속**: FIN-BE-026(revaluation 델타를 열린 로트 carrying 에 분배 — FIFO×재평가 정합).
- **D4 hazard 경계**: 본 태스크의 FIFO 소비는 **재평가가 개입하지 않은 포지션**에 정확하다(Σ 열린 로트 carrying == 포지션 carrying C 일 때). 재평가가 C 를 바꿨는데 로트엔 미반영이면 불일치 → 그 정합(D4-a 로트 carrying 분배)은 FIN-BE-026. 본 태스크는 그 전제에서 FIFO 를 배선하고, 불일치 시 안전 폴백(아래)을 둔다.

# Goal

`FIFO` 설정 테넌트의 결제가 로트별 취득원가로 `C_settle` 를 산정(가중평균 대신)하도록 `SettleForeignPositionUseCase` 를 분기하고 소비된 로트의 `remaining/carrying` 을 차감·영속한다. 미설정·`WEIGHTED_AVERAGE` 는 기존 그대로.

# Scope

- **`FxSettlementPolicy`**: 순수성 유지(repository 미접근). private core 추출 — `(F, C_settle, F_settle, rate, …)` 로 proceedsBase/realized/3줄 산출. 기존 public 가중평균 오버로드는 `C_settle = round(C×|F_settle|/|F|)` 계산 후 core 호출(**byte-identical**). **신규 public 오버로드** `settleWithCarrying(... , long carryingSettledMinor, …)` 가 pre-computed `C_settle` 를 받아 core 호출(FIFO 경로용). FIFO 로트 walk 은 policy 가 아니라 use case 에서.
- **`SettleForeignPositionUseCase.settle(...)`**:
  - 포지션 로드 직후 `fxCostFlowConfigRepository.findByTenantId(tenant)` → `CostFlowMethod`(미설정=WEIGHTED_AVERAGE).
  - **WEIGHTED_AVERAGE**(또는 미설정): 현 경로 그대로(`FxSettlementPolicy.settle(...)` 가중평균) — **byte-identical, net-zero**.
  - **FIFO**: `fxPositionLotRepository.findOpenLots(tenant, code, currency)` 로드(`remaining>0`, `(acquired_at, seq)` ASC). `needed = |F_settle|` 를 walk:
    - 각 로트 `consume = min(lot.remaining, needed)`; `slice = round(lot.carrying × consume / lot.remaining, HALF_UP)`; `lot.remaining -= consume`; `lot.carrying -= slice`; `C_settle_fifo += slice`; `needed -= consume`; 소진 시 중단. 차감된 로트 `save`(동일 Tx).
    - **불변식/폴백**: Σ 열린 로트 `remaining` 가 `|F|` 와 일치해야 정상(취득+backfill 후, 비-settlement 감소 없을 때). 로트가 없거나 합이 `|F_settle|` 를 못 채우면 → **가중평균으로 안전 폴백**(net-non-zero 회피) + 경고 로그(`FX_FIFO_LOT_SHORTFALL`). 마지막 로트 잔여 후 needed>0 도 폴백.
    - `FxSettlementPolicy.settleWithCarrying(..., C_settle_fifo, ...)` 호출 → proceedsBase/realized/엔트리(shape 동일).
  - residual `(F − F_settle, C − C_settle_fifo)` 는 use case 의 기존 계산식 그대로(자동 lot-exact — 로트 carrying 합이 곧 residual carrying).
- **감사**: FIFO 소비 시 감사 요약에 method/소비 로트 수를 포함(선택, regulated 가시성). 결제 자체 감사행은 `PostJournalEntryUseCase` 가 기존대로 기록.
- **Spec**: `architecture.md` § FX settlement 에 "§ FIFO settlement consumption" 하위절(17번째 증분, ADR-001 D3; 폴백·불변식 명시).
- **NO change**: `FxRevaluationPolicy`, 취득 훅(`RecordFxAcquisitionLots`), reconciliation, cost-flow 설정 EP. `FxSettlementPolicy` 가중평균 출력 byte-unchanged.

# Acceptance Criteria

- **AC-1** **FIFO lot-exact**: 서로 다른 환율의 두 취득 로트(예 USD@1300 then USD@1400)를 가진 포지션을 부분결제하면, 오래된 로트(1300)가 먼저 소비되어 `C_settle`·realized 가 **가중평균과 다른 lot-exact 값**이 됨. 소비된 로트 `remaining/carrying` 차감, 오래된 로트 우선.
- **AC-2** **full settle FIFO** = 모든 열린 로트 소비, 잔여 carrying 0, realized = proceedsBase − Σ(로트 carrying). residual `(0,0)`.
- **AC-3** **net-zero**: 미설정·`WEIGHTED_AVERAGE` 테넌트 결제는 **byte-identical**(기존 settlement IT 전부 GREEN, `FxSettlementPolicy` 가중평균 출력 불변).
- **AC-4** **안전 폴백**: FIFO 설정인데 열린 로트 부재/부족(Σremaining < |F_settle|) → 가중평균 `C_settle` 로 폴백, 결제 성공(net-non-zero 없음), 경고.
- **AC-5** residual lot-exact: FIFO 부분결제 후 잔여 로트 carrying 합 == 포지션 잔여 carrying `(C − C_settle_fifo)`.
- **AC-6** 멱등·closed-period·over-settle·sign 가드 등 기존 settlement 동작 불변(FIFO 경로도 동일 가드 통과 — use case 상단 가드는 분기 이전).
- **AC-7** Testcontainers IT `LedgerFifoSettlementIntegrationTest`: FIFO 설정 → 2-로트 포지션 부분결제 lot-exact(AC-1) + full settle(AC-2) + 폴백(AC-4). 고유 `ledgerAccountCode`(예 `FX_FIFO_USD_WALLET`)로 공유-Kafka predicate 충돌 회피. **AC-3 대조군**: 동일 시나리오를 WEIGHTED_AVERAGE 로도 돌려 값 차이 입증.
- **AC-8** `:test` + `:integrationTest` GREEN(Docker on). IT 권위.

# Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (§ 2 D3, § 3.1 step 3)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ FX settlement — § FIFO settlement consumption 추가)

# Related Contracts

- 없음 — 결제 엔트리·`entry.posted` outbox payload shape 불변(C_settle 도출 내부 정책만 변경). 외부 계약 무관.

# Edge Cases

- FIFO 설정·로트 없음/부족 → 가중평균 폴백(AC-4). 비-settlement 감소로 인한 shadow desync 의 안전판.
- 단일 로트 부분결제 = 가중평균과 동일 값(로트 1개면 lot-exact == pool). 차이는 ≥2 로트·이종환율에서만.
- 재평가 개입 포지션: 로트 carrying 합 ≠ C(D4) → 본 태스크 범위 밖, FIN-BE-026 이 분배로 정합. 본 태스크 IT 는 재평가 미개입 시나리오로 lot-exact 검증.
- 반올림: slice = round(carrying×consume/remaining) per 로트. 마지막 로트 소비 시 lot.remaining==consume → slice = carrying(정확, drift 0). 가중평균의 self-correcting 대응물.

# Failure Scenarios

- WEIGHTED_AVERAGE/미설정 경로가 byte-identical 이 아니면 → net-zero 위반. core 추출은 가중평균 출력 불변이어야(기존 settlement IT 가 backstop).
- FIFO 폴백 없이 로트 부족 시 throw 하면 → 운영 중단 위험. Σremaining < |F_settle| 는 폴백(가중평균)으로 흡수.
- 로트 carrying 차감을 결제와 다른 Tx 에 하면 → 원자성 위반. 같은 `@Transactional` 안에서 save.
- LIFO/specific-id 를 끼우면 → ADR-001 D1/D3 범위 밖. FIFO `(acquired_at, seq)` ASC 만.
