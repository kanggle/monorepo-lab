# Task ID

TASK-FIN-BE-023

# Title

per-tenant **FX cost-flow method 설정**(`fx_cost_flow_config`) — ADR-001 D1 의 첫 실행 태스크. cost-flow method(`WEIGHTED_AVERAGE` | `FIFO`)를 테넌트 단위로 저장/조회하는 설정 표면만 추가한다(**shadow** — 아직 settlement 가 이 설정을 읽지 않음; FIFO 소비 배선은 FIN-BE-025). V7 `reconciliation_fx_tolerance`(FIN-BE-020)를 **그대로 미러**한 additive·net-zero 증분.

# Status

ready

# Owner

backend

# Task Tags

- fintech
- transactional

---

# Dependency Markers

- **child of**: ADR-001 (finance-platform, ACCEPTED 2026-06-14) § 2 D1 + § 3.1 실행 로드맵 step 1. cost-flow method 의 **저장/조회** 만 — FIFO 소비 산정은 FIN-BE-025, 로트 테이블은 FIN-BE-024.
- **mirrors**: `reconciliation_fx_tolerance` 전체 파일셋(V7 + `ReconciliationFxToleranceConfig` + `Get/SetFxToleranceUseCase` + `FxToleranceView` + repository 3종 + `ReconciliationController` GET/PUT `/fx-tolerance` + request/response DTO + `FxToleranceInvalidException`). 동일 구조로 cost-flow 버전 생성.
- **shadow / net-zero**: 이 태스크는 설정을 **persist 만** 한다. `SettleForeignPositionUseCase`·`FxSettlementPolicy` 는 **변경하지 않는다** — settlement 는 이 증분 후에도 가중평균 그대로(미설정·FIFO 설정 무관). 기존 settlement IT byte-unchanged.
- **선행**: 없음(ADR-001 ACCEPTED on main `a34bb3563`). **후속**: FIN-BE-024(로트 테이블), FIN-BE-025(FIFO 소비가 이 설정을 읽음).

# Goal

테넌트가 외화 결제 원가흐름 방식을 `WEIGHTED_AVERAGE`(기본) 또는 `FIFO` 로 설정/조회할 수 있는 표면을 추가하되, 실제 settlement 산정은 바꾸지 않는다(shadow). FIN-BE-025 가 이 설정을 읽어 FIFO 소비로 분기할 토대.

# Scope

- **Migration** `V9__add_fx_cost_flow_config.sql` — `fx_cost_flow_config(tenant_id VARCHAR(64) PK, method VARCHAR(20) NOT NULL DEFAULT 'WEIGHTED_AVERAGE', updated_by VARCHAR(64) NOT NULL, updated_at DATETIME(6) NOT NULL)`, InnoDB/utf8mb4, `CHECK (method IN ('WEIGHTED_AVERAGE','FIFO'))`. **ADDITIVE, NO backfill** — 행 부재 = `WEIGHTED_AVERAGE`(net-zero).
- **Domain**: `domain/journal/CostFlowMethod` enum `{WEIGHTED_AVERAGE, FIFO}` (+ `fromString` 으로 미지값 → `CostFlowMethodInvalidException`). `domain/journal/FxCostFlowConfig` JPA 엔티티(미러 `ReconciliationFxToleranceConfig`: `tenant_id` PK, `method`, `updated_by`, `updated_at`; `of(...)` 팩토리 + `method()` projection). `domain/journal/repository/FxCostFlowConfigRepository`(`findByTenantId`, `save`).
- **Infra**: `infrastructure/persistence/jpa/FxCostFlowConfigJpaRepository` + `...RepositoryImpl`.
- **Application**: `GetFxCostFlowConfigUseCase`(findByTenantId → `FxCostFlowConfigView`, 미설정 시 `FxCostFlowConfigView.weightedAverageDefault()`), `SetFxCostFlowConfigUseCase`(method 검증 → upsert last-write-wins + **동일 Tx 감사행** `FX_COST_FLOW_METHOD_SET`), `SetFxCostFlowConfigCommand`, `application/view/FxCostFlowConfigView`.
- **Presentation**: `presentation/dto/FxCostFlowConfigRequest`(`method:String`) + `FxCostFlowConfigResponse`. `SettlementController` 에 `GET /api/finance/ledger/settlements/cost-flow-config`(effective, 미설정=WEIGHTED_AVERAGE) + `PUT /api/finance/ledger/settlements/cost-flow-config`(upsert). 테넌트 스코프=`ActorContext`.
- **Error**: `LedgerErrors.CostFlowMethodInvalidException`(VALIDATION_ERROR) — 미지 method 문자열.
- **Spec**: `specs/services/ledger-service/architecture.md` § FX settlement 에 "§ FX cost-flow method config" 하위절 추가(15번째 증분, ADR-001 D1; shadow·설정만).
- **NO change**: `SettleForeignPositionUseCase`, `FxSettlementPolicy`, `RevaluationController`, 기존 settlement/reconciliation 코드·IT.

# Acceptance Criteria

- **AC-1** `GET …/cost-flow-config` 가 미설정 테넌트에 `WEIGHTED_AVERAGE` 를 반환(effective default).
- **AC-2** `PUT …/cost-flow-config {method:"FIFO"}` 가 upsert 후 `GET` 이 `FIFO` 를 반영(last-write-wins). 감사행 1개(`FX_COST_FLOW_METHOD_SET`) 동일 Tx 기록.
- **AC-3** 미지 method(예 `"LIFO"`/`"xyz"`) → `CostFlowMethodInvalidException`(VALIDATION_ERROR), 아무것도 persist 안 됨.
- **AC-4** **net-zero**: `SettleForeignPositionUseCase`/`FxSettlementPolicy` 무변경 — FIFO 로 설정해도 이 증분에서는 settlement 산정이 가중평균 그대로(기존 settlement IT byte-unchanged). shadow 임을 IT 가 (설정 FIFO 후에도 결제 결과 동일) 증명하거나, 최소한 settlement 코드 미변경을 보장.
- **AC-5** V9 는 additive(기존 테이블·행 무변경), `method` CHECK 가 enum 과 일치, 행 부재=WEIGHTED_AVERAGE.
- **AC-6** Testcontainers IT `LedgerFxCostFlowConfigIntegrationTest`: GET 기본값 → PUT FIFO → GET 반영 → PUT 잘못된 method **400 VALIDATION_ERROR**(`FxToleranceInvalidException` 미러 — `GlobalExceptionHandler.STATUS_BY_CODE` 가 VALIDATION_ERROR→400 매핑; 422 강제는 tolerance 엔드포인트까지 바꿔 net-non-zero 라 미채택). (결제 미수행이라 공유-Kafka predicate 충돌 없음; 만약 결제를 섞으면 고유 `ledgerAccountCode` 사용.)
- **AC-7** `:test` + `:integrationTest` GREEN(Docker on). Docker-free `:check` 는 wiring 미적발이므로 IT 가 권위.

# Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (§ 2 D1, § 3.1 step 1)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ FX settlement — 본 증분이 § FX cost-flow method config 하위절 추가)

# Related Contracts

- 없음 — cost-flow config 는 도메인-internal 운영자 설정(테넌트 스코프 JWT)이며 외부 이벤트/계약 shape 불변. `entry.posted` outbox·`finance.transaction.*` 무관.

# Edge Cases

- 행 부재 = `WEIGHTED_AVERAGE`(GET 항상 effective 반환). 미설정 테넌트 net-zero.
- method 대소문자/공백 — `fromString` 정규화 정책 결정(권장: 정확 일치 대문자, 그 외 invalid). 미러 `FxTolerance` 검증 위치(use case 가 도메인 생성자 앞에서 검증)와 동형.
- enum 에 `LIFO` 추가 금지(ADR-001 D1: IFRS 금지). method ∈ {WEIGHTED_AVERAGE, FIFO}.
- V9 가 settlement/revaluation/reconciliation 테이블을 건드리지 않음(순수 신규 테이블).

# Failure Scenarios

- 이 태스크에서 `SettleForeignPositionUseCase` 를 FIFO 로 분기시키면 → 범위 위반(그건 FIN-BE-025, 로트 테이블 FIN-BE-024 선행 필요). 본 태스크는 **설정 저장만**(shadow).
- 미설정 기본값을 FIFO 로 두면 → net-zero 위반(ADR-001 D1). 기본=WEIGHTED_AVERAGE.
- 감사행 누락 → regulated/audit-heavy 위반. upsert 와 동일 Tx 에 `FX_COST_FLOW_METHOD_SET` 기록(미러 `FX_TOLERANCE_SET`).
