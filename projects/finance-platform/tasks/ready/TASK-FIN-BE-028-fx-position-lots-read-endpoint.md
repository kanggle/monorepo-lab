# Task ID

TASK-FIN-BE-028

# Title

**FX position lots read endpoint** — 운영자가 외화 포지션의 **열린 로트**(취득시각·취득량·잔량·carrying·취득환율)를 조회하는 read-only 표면. FIN-BE-024~026이 만든 로트 상태(취득·FIFO 소비·재평가 mark)를 가시화해, FIFO/lot 회계가 실제로 어떻게 구성됐는지 운영자/콘솔이 확인할 수 있게 한다. 순수 read(net-zero·마이그레이션 0), 기존 `GET /cost-flow-config` 패턴 미러. ledger 20번째 증분. (향후 콘솔 lot drill-in의 백엔드 토대.)

# Status

ready

# Owner

backend

# Task Tags

- fintech
- audit-heavy

---

# Dependency Markers

- **child of**: ADR-001 (finance-platform, ACCEPTED) § 3.1 deferred("lot 콘솔 drill-in")의 백엔드 선행. 로트 데이터모델=FIN-BE-024(done), 소비=025, 재평가 mark=026.
- **선행**: FIN-BE-024(`fx_position_lot` + `FxPositionLotRepository.findOpenLots`, done). 모두 done.
- **mirrors**: `SettlementController` `GET /cost-flow-config`(FIN-BE-023) + `GetFxCostFlowConfigUseCase` — tenant-scoped read 패턴.
- **read-only / net-zero**: 신규 read EP + use case + view/DTO만. 쓰기 경로·로트 생성/소비/mark·settlement/revaluation 무변경. **마이그레이션 없음**.

# Goal

`GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots` 로 테넌트의 그 포지션 **열린 로트 목록**(+ 요약)을 반환해 FIFO/lot 상태를 조회 가능하게 한다.

# Scope

- **Endpoint**: `SettlementController` 에 `GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots`. `@PathVariable ledgerAccountCode` + `currency`(문자열→`Currency` 파싱, 미지값→`VALIDATION_ERROR` 400; base/KRW 도 허용하되 외화 아님→빈 목록일 뿐). 테넌트=`ActorContext`. read-only(no `@Transactional` write).
- **Application**: `GetFxPositionLotsUseCase`(@Transactional(readOnly=true)) → `fxPositionLotRepository.findOpenLots(tenant, code, currency)`(remaining>0, `(acquired_at, seq)` ASC) → `FxPositionLotsView`. 빈 포지션→빈 목록(net-zero).
- **View/DTO**: `application/view/FxPositionLotsView`(로트 리스트 + 요약: `totalRemainingForeignMinor`=Σremaining, `totalCarryingBaseMinor`=Σcarrying[=포지션 carrying], `lotCount`) + per-lot `FxPositionLotView`(lotId, currency, acquiredAt, seq, originalForeignMinor, remainingForeignMinor, originalBaseMinor, carryingBaseMinor, sourceJournalEntryId). `presentation/dto/FxPositionLotsResponse` + `FxPositionLotResponse`(**money minor=문자열** F5 wire form; acquiredAt=ISO instant).
- **Repository**: 기존 `findOpenLots` 재사용(신규 메서드 불요; 필요 시 read 전용 정렬 보장 확인).
- **Spec**: `architecture.md` § FX position lots 에 read endpoint 단락 추가(20th 증분; read-only·요약).
- **NO change**: 로트 생성/소비/mark, settlement/revaluation, cost-flow 설정, 마이그레이션, reconciliation.

# Acceptance Criteria

- **AC-1** 외화 취득 2건 후 `GET …/{account}/USD/lots` → 열린 로트 2개를 `(acquired_at, seq)` ASC 로 반환, 각 로트 필드(취득/잔량 foreign, original/carrying base, acquiredAt, sourceJournalEntryId) 정확. 요약 `lotCount=2`, `totalRemainingForeignMinor`=Σ, `totalCarryingBaseMinor`=Σ(=포지션 carrying).
- **AC-2** FIFO 부분결제(FIN-BE-025) 후 조회 → 소비된 로트 `remainingForeignMinor`/`carryingBaseMinor` 차감 반영, 완전소비 로트(remaining=0)는 목록에서 제외(open만). 재평가(026) 후 → 로트 `carryingBaseMinor` 가 mark-to-spot 반영.
- **AC-3** 빈 포지션(로트 없음)→ 빈 목록 + 요약 0(net-zero, 404 아님).
- **AC-4** 미지 currency(예 `XYZ`)→ 400 VALIDATION_ERROR(기존 파싱 패턴). 테넌트 격리: 다른 테넌트 로트 미노출(`ActorContext` tenant scope).
- **AC-5** money minor 가 JSON 에서 **문자열**(F5 wire form, 기존 `MoneyResponse`/lot 컨벤션 일치).
- **AC-6** **net-zero**: 쓰기 경로·로트 상태·settlement/revaluation 무변경. 기존 IT 전부 GREEN. 마이그레이션 0.
- **AC-7** Testcontainers IT `LedgerFxPositionLotsReadIntegrationTest`: 취득→조회(AC-1) + 부분결제후 차감(AC-2) + 빈 포지션(AC-3) + 미지 currency 400. 고유 `ledgerAccountCode`. 단위: `GetFxPositionLotsUseCaseTest` + `SettlementControllerSliceTest` lots 케이스.
- **AC-8** `:test` + `:integrationTest` GREEN(Docker on). IT 권위.

# Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (§ 3.1 deferred — lot 콘솔 drill-in 백엔드 선행)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ FX position lots — read endpoint 단락 추가)

# Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md`(있으면) § settlements — read EP 추가 기술. 없으면 architecture.md 만.
- read EP 는 신규 응답 계약이나 기존 도메인 상태 노출일 뿐(쓰기·이벤트 계약 무변경).

# Edge Cases

- base/KRW currency 조회 → 외화 로트 없음 → 빈 목록(에러 아님).
- 완전 소비/청산된 포지션 → 열린 로트 0 → 빈 목록.
- 다중 동일-시각 로트 → `seq`(journal_line.id) tiebreak 로 deterministic 순서.
- money minor 문자열 직렬화(F5) — long→문자열, 음수 없음(magnitude 저장).

# Failure Scenarios

- read EP 가 로트를 생성/수정하면 → 범위 위반. 순수 read(@Transactional readOnly), `findOpenLots` 만 호출.
- 닫힌(remaining=0) 로트를 노출하면 → "열린 포지션 구성" 의미 흐림. `findOpenLots`(remaining>0)만.
- 테넌트 격리 누락 → 데이터 유출. `ActorContext.tenantId()` 로 스코프.
- 마이그레이션/쓰기 추가 → net-zero 위반. 이 태스크는 read 표면만.
