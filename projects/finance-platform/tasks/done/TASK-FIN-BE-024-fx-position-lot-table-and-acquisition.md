# Task ID

TASK-FIN-BE-024

# Title

**`fx_position_lot` 로트 테이블 + 취득 로트 생성 훅(shadow) + synthetic 로트 backfill** — ADR-001 D2/D5 의 실행 태스크. 외화 포지션의 취득분(lot)을 materialized 테이블로 추적한다. 이 증분은 **shadow** — 로트를 **생성/backfill 만** 하고 **아무도 소비하지 않는다**(FIFO 소비 산정은 FIN-BE-025). settlement/revaluation 거동 byte-unchanged(net-zero).

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

- **child of**: ADR-001 (finance-platform, ACCEPTED) § 2 D2(로트 모델) + D5(additive 마이그레이션·backfill) + § 3.1 step 2.
- **선행**: FIN-BE-023(cost-flow 설정, done — V9). 본 태스크는 V10.
- **후속**: FIN-BE-025(settlement 가 `FIFO` 설정 시 이 로트를 walk 소비), FIN-BE-026(revaluation 이 열린 로트 carrying 분배).
- **shadow / net-zero**: 로트는 **write-only** — 취득 시 생성, backfill 로 기존 포지션 재구성. `SettleForeignPositionUseCase`·`FxSettlementPolicy`·`FxRevaluationPolicy` 는 **변경하지 않는다**. settlement 는 이 증분 후에도 가중평균 그대로. 기존 settlement/revaluation IT byte-unchanged.

# Goal

외화 취득분을 로트 행으로 영속화하는 토대를 만든다 — (a) 신규 취득 포스팅마다 로트 생성, (b) 기존 열린 포지션을 단일 synthetic 로트로 backfill(carrying 이 현 pool carrying 과 정확히 일치). FIN-BE-025 가 이 로트를 FIFO 소비할 기반.

# Scope

- **Migration** `V10__add_fx_position_lot.sql`:
  - 테이블 `fx_position_lot`: `lot_id VARCHAR(36) PK`, `tenant_id VARCHAR(64)`, `ledger_account_code VARCHAR(100)`, `currency VARCHAR(3)`, `acquired_at DATETIME(6)`, `seq BIGINT`, `original_foreign_minor BIGINT`, `original_base_minor BIGINT`, `remaining_foreign_minor BIGINT`, `carrying_base_minor BIGINT`, `source_journal_entry_id VARCHAR(36) NULL`, `created_at DATETIME(6)`. InnoDB/utf8mb4. `KEY idx_fx_lot_position (tenant_id, ledger_account_code, currency, acquired_at, seq)`. CHECK: `original_foreign_minor > 0`, `original_base_minor >= 0`, `remaining_foreign_minor >= 0`, `remaining_foreign_minor <= original_foreign_minor`, `carrying_base_minor >= 0`.
  - **Backfill** (같은 마이그레이션, INSERT…SELECT): 기존 외화 포지션을 `(tenant_id, ledger_account_code, currency)`별 **단일 synthetic 로트**로 — `currency <> 'KRW'`, `GROUP BY` 3키, `HAVING` 부호있는 외화합 `<> 0`. `original_foreign_minor = ABS(Σ signed amount)`, `original_base_minor = ABS(Σ signed base)`, `remaining = original_foreign`, `carrying_base_minor = original_base`(현 pool carrying 과 정확히 일치 = D5 이중계상 0 제약), `acquired_at = MIN(posted_at)`, `seq = MIN(journal_line.id)`, `lot_id = UUID()`, `source_journal_entry_id = NULL`, `created_at = NOW(6)`. (signed = `CASE WHEN direction='DEBIT' THEN +x ELSE -x END`.) **신규 CI/테스트 DB 에선 pre-V10 데이터가 없어 no-op** — backfill 은 실배포 기존 포지션용.
- **Domain**: `domain/journal/FxPositionLot` JPA 엔티티(위 컬럼 + `acquire(...)` 팩토리 + getter). `domain/journal/repository/FxPositionLotRepository`(`save`, `findOpenLots(tenant, code, currency)` = `remaining_foreign_minor > 0` 정렬 `(acquired_at, seq)` — FIN-BE-025 용 read; 본 태스크는 `save` 만 실사용하되 인터페이스 정의).
- **Infra**: `infrastructure/persistence/jpa/FxPositionLotJpaRepository` + `...RepositoryImpl`.
- **취득 훅** (shadow): `PostJournalEntryUseCase.post(...)` 의 `journalRepository.save(entry)` **이후**, entry 의 각 라인에 대해 **취득 라인**이면 로트 생성. **취득 라인 정의**: `line.currency() != LedgerReportingCurrency.BASE` AND `line.amountMinor() > 0`(zero-amount `baseAdjustment` 재평가 라인 제외) AND `line.direction() == account.type().normalSide()`(포지션 증가 방향 — ASSET/EXPENSE=DEBIT 증가, LIABILITY/INCOME/EQUITY=CREDIT 증가). 로트: `original_foreign = amountMinor`, `original_base = baseAmountMinor`, `remaining = amountMinor`, `carrying = baseAmountMinor`, `acquired_at = postedAt`, `seq = line.id()`(저장 후 IDENTITY), `source_journal_entry_id = entryId`. **반대 방향(감소) 외화 라인은 로트 생성 안 함** — 비-settlement 감소는 shadow desync 로 알려진 제약(소비는 FIN-BE-025 settlement 경로). 별도 컴포넌트(예 `RecordFxAcquisitionLots`)로 추출해 `post()` 에서 호출 권장.
- **Spec**: `specs/services/ledger-service/architecture.md` § FX settlement 에 "§ FX position lots (acquisition / backfill)" 하위절 추가(16번째 증분, ADR-001 D2/D5; shadow).
- **NO change**: `SettleForeignPositionUseCase`, `FxSettlementPolicy`, `FxRevaluationPolicy`, `RevaluationController`, `SettlementController`, 결제/재평가 산정 로직.

# Acceptance Criteria

- **AC-1** 외화 취득 포스팅(예 USD asset DEBIT, base 동반) 후 `fx_position_lot` 에 로트 1행: `original_foreign=remaining=amountMinor`, `original_base=carrying=baseAmountMinor`, `acquired_at=postedAt`, `source_journal_entry_id=entryId`.
- **AC-2** 한 entry 의 다중 외화 취득 라인 → 라인당 로트 1행(`seq` 오름차순 = 포스팅 순서). base-currency(KRW) 라인·zero-amount `baseAdjustment` 재평가 라인 → 로트 생성 안 함.
- **AC-3** 반대 방향(포지션 감소) 외화 라인 → 로트 생성 안 함(shadow; 소비는 FIN-BE-025).
- **AC-4** **net-zero**: `SettleForeignPositionUseCase`/`FxSettlementPolicy`/`FxRevaluationPolicy` 무변경 — settlement·revaluation 결과 byte-unchanged. 기존 settlement/revaluation/reconciliation IT 전부 GREEN.
- **AC-5** V10 backfill SQL: 기존 외화 포지션의 synthetic 로트 `carrying_base_minor` 가 그 포지션의 현 pool carrying(`ABS(Σ signed base)`)과 **정확히 일치**, `original_foreign = ABS(Σ signed amount)`. (검증: IT 에서 로트 훅을 우회해 journal_line 을 직접 seed → backfill SQL 과 동일한 INSERT…SELECT 를 재실행 → synthetic 로트가 집계와 일치함을 단언. 또는 단위 테스트로 SQL 동치 입증.)
- **AC-6** V10 은 additive — 기존 테이블/행 byte-unchanged. `AbstractLedgerIntegrationTest.cleanLedgerState()` 에 `DELETE FROM fx_position_lot` 추가.
- **AC-7** Testcontainers IT `LedgerFxPositionLotIntegrationTest`: 취득→로트 생성(AC-1/2/3) + net-zero(설정 FIFO 여부 무관 settlement 동일). 공유-Kafka predicate 충돌 회피 위해 **고유 `ledgerAccountCode`**(예 `FX_LOT_USD_WALLET`) 사용.
- **AC-8** `:test` + `:integrationTest` GREEN(Docker on). IT 가 권위(Docker-free `:check` 는 wiring 미적발).

# Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (§ 2 D2/D5, § 3.1 step 2)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ FX settlement — 본 증분이 § FX position lots 하위절 추가)

# Related Contracts

- 없음 — 로트는 도메인-internal 영속 상태이며 외부 이벤트/HTTP 계약 shape 불변. `entry.posted` outbox payload 무변경(로트는 별도 테이블, 이벤트에 미노출).

# Edge Cases

- zero-amount `baseAdjustment`(재평가) 라인은 외화지만 취득 아님 → 제외(`amountMinor() > 0` 가드).
- KRW(base) 라인 → 외화 아님 → 제외.
- 포지션 감소 외화 라인(반대 normalSide) → 로트 생성 안 함(shadow desync 알려진 제약, FIN-BE-025/문서화).
- backfill: 부호있는 외화합 `= 0`(완전 청산된 포지션)은 `HAVING` 으로 제외. 신규 DB 는 no-op.
- `seq` = `journal_line.id`(IDENTITY, 저장 후 할당) — `post()` 가 `save(entry)` 후 훅을 호출하므로 라인 id 가 존재.

# Failure Scenarios

- 이 태스크에서 settlement/revaluation 을 로트 소비/분배로 바꾸면 → 범위 위반(FIN-BE-025/026). 본 태스크는 **로트 생성·backfill 만**(shadow).
- backfill synthetic 로트 carrying 이 pool carrying 과 불일치하면 → D5 이중계상 위반. `carrying = original_base = ABS(Σ signed base)` 정확 일치 필수.
- 취득 훅이 zero-amount 재평가 라인이나 KRW 라인에 로트를 만들면 → 오염. `currency != BASE && amountMinor > 0 && direction == normalSide` 3중 가드.
- 로트 생성이 `PostJournalEntryUseCase` 의 단일 Tx 밖에서 일어나면 → 원자성 위반. 같은 `@Transactional` 안에서 save.
