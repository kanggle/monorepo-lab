# ADR-001: 외화 포지션 원가흐름(cost-flow)을 가중평균에서 FIFO 로트(lot) 추적으로 — per-tenant 설정·opt-in

- **Status**: PROPOSED (2026-06-14)
- **Date**: 2026-06-14
- **Authors**: architecture (ledger-service 15번째 증분 방향 — finance-platform 첫 프로젝트 ADR)
- **Supersedes**: —
- **Superseded by**: —
- **History**: PROPOSED 2026-06-14 — 사용자가 ledger 후속 작업으로 **"FIFO/lot cost-basis 설계"** 를 명시 선택(ADR-MONO-033 staged-child 패턴 직후, 충돌 없는 finance 평면 방향). 14증분(FIN-BE-007~021)까지 외화 결제는 **가중평균 pool** 으로 처분원가를 산정해 왔고, 처분된 외화가 **어느 취득분(lot)** 이었는지는 회계상 소실된다. 본 ADR 은 (a) 현 가중평균 모델을 회고적으로 기록하고 (b) **취득 로트별 원가추적(FIFO)** 으로의 전환을 per-tenant 설정·opt-in·net-zero 로 결정한다. cost-flow method(FIFO vs 가중평균)는 **실현손익 숫자를 바꾸는 회계정책 결정**이므로 코드로 묵시 결정하면 HARDSTOP-09 + 기존 테넌트 reconciliation 드리프트 → 결정을 먼저 기록하고 PAUSE. **ACCEPTED 전환 + 구현은 별도 user-explicit-intent 태스크**(sibling ADR-MONO-019/020/032/033 staged-child 패턴). **Self-ACCEPT 금지.**

---

## 1. Context

### 1.1 현재 상태 (조사 결과, factual)

`ledger-service` 의 외화(FX) 처리는 두 정책 객체로 구성된다 (모두 순수 도메인, 별도 write boundary 없음 — 기존 `PostJournalEntryUseCase` 단일 guarded write 경로로 funnel):

| 증분 | 클래스 | 역할 |
|---|---|---|
| 9th (FIN-BE-015) | `domain/journal/FxRevaluationPolicy` | **미실현** 손익 — 열린 포지션을 마감환율로 mark-to-market |
| 10th (FIN-BE-016) | `domain/journal/FxSettlementPolicy` | **실현** 손익 — 결제 시 포지션을 carrying 으로 제거, 처분차익을 `FX_GAIN`/`FX_LOSS` 인식 |
| 12th (FIN-BE-018) | (위 + 부분결제 residual) | 부분결제 후 residual OPEN 포지션 `(F−F_settle, C−C_settle)` 반환 |

**처분원가 산정 = 가중평균 pool.** 결제 진입점 `application/SettleForeignPositionUseCase.settle()` 은 포지션을 **단일 집계**로 로드한다:

```java
// SettleForeignPositionUseCase lines 130-137
Optional<AccountTotals> totals = journalRepository.accountTotalsForCurrency(
        cmd.ledgerAccountCode(), cmd.currency(), cmd.tenantId());
AccountTotals t = totals.get();
long foreignBalanceMinor = t.debitMinor()  - t.creditMinor();    // F  (외화 잔량)
long carryingBaseMinor   = t.baseDebitMinor() - t.baseCreditMinor(); // C  (장부 원가, KRW)
```

`accountTotalsForCurrency` 는 `(ledger_account_code, currency)` 의 **모든 라인을 합산**한다(`GROUP BY ledger_account_code, currency`). 부분결제 원가는:

```
C_settle = round(C_total × |F_settle| / |F_total|, HALF_UP)   // FxSettlementPolicy lines 190-202
realized = proceedsBase − C_settle
```

즉 **포지션 전 생애의 모든 포스팅을 하나의 평균 환율 pool 로 융합**한다. `FxSettlementPolicy` Javadoc 도 "weighted-average unit cost … 잔량 최종 결제 시 round(C×F/F)=C 로 drift 없이 0 수렴" 으로 명시.

**로트 개념은 전무.** `Lot`/`Position`/`Tranche`/`Acquisition`/`CostBasis` 클래스·테이블 없음. `journal_line` 은 **per-line `exchange_rate DECIMAL(20,8)` + `base_amount_minor BIGINT` 를 이미 보유**(V5)하지만, cost-basis 목적으로 개별 조회된 적이 없고 집계로만 쓰인다 — 즉 **FIFO 의 데이터 토대(취득별 환율·원가)는 이미 행 단위로 존재하나, 취득을 로트로 식별·소비할 수단이 없다.**

- **DB**: MySQL 8 / InnoDB / utf8mb4. money=`BIGINT` minor units, 시각=`DATETIME(6)`, `BOOLEAN`=`TINYINT(1)`, id=`VARCHAR(36)`. `FLOAT`/`DOUBLE` 전무(F5).
- **Money**: `Money(minorUnits:long ≥0, currency)`, 부호는 외부(debit-positive). `Currency{KRW(0),USD(2),EUR(2),JPY(0)}`, `LedgerReportingCurrency.BASE = KRW`. 이중기입 균형은 KRW(base) 로 검증(`Σ baseDebit == Σ baseCredit`).
- **Migrations**: V1(init)~V8(reconciliation_match.cross_currency). FX 설정 테이블 선례 = **V7 `reconciliation_fx_tolerance(tenant_id PK, tolerance_bps, floor_minor)`** — per-tenant 설정·미설정시 net-zero 기본(EXACT) 패턴.

### 1.2 기록되지 않은 것 (이 ADR 이 필요한 이유)

가중평균은 14증분 동안 "구현된 사실"일 뿐 **선택으로 기록된 적이 없다.** 더욱이 ledger architecture.md 는 이 전환을 **명시적으로 예약**해 두었다 — § Increment Scope 전문(preamble)이 "A FIFO / lot-level cost basis, a bulk/period-close revaluation hook, a live FX rate feed … **remain forward-declared**" 로 FIFO/로트 원가를 forward-declared 증분으로 못박았다. 즉 본 ADR 은 spec 이 예약한 미래 증분의 **방향을 형식화**하는 것이다. cost-flow method 전환은 다음을 건드린다:

1. **실현손익 숫자가 바뀐다** — 같은 결제라도 FIFO 와 가중평균의 `realized` 가 다르다(취득환율이 시점별로 다를 때). 전역 교체는 모든 기존 테넌트의 과거·미래 실현손익을 소급 변경 → reconciliation 드리프트 + 기존 IT 깨짐.
2. **새 데이터모델** — 취득 로트 추적 테이블 + 소비 상태(부분소비 residual).
3. **재평가와의 상호작용** — 미실현 재평가가 pool carrying 을 바꾸는데 로트는 취득원가를 보존 → 이중계상 hazard(§ D4).

코드로 이 셋을 묵시 결정하면 회계정책을 silently bake → **HARDSTOP-09**. `fintech` / `regulated` / `audit-heavy` trait 상 명시 기록이 필수.

### 1.3 결정 드라이버

- **감사 추적성(audit-heavy)** — "어느 취득분이 처분됐는가" 가 가중평균에선 소실. 로트는 처분손익을 **취득시점 환율에 정확 귀속**.
- **회계기준(regulated)** — K-IFRS/IFRS 는 재고·외화자산 원가흐름으로 **FIFO 또는 가중평균을 허용하고 LIFO 를 금지**한다. FIFO 는 표준적·결정론적(운영자 개입 없이 자동) 정책.
- **net-zero 규율** — 기존 14증분이 전부 이전 동작 byte-identical 보존(FxTolerance EXACT 기본, cross_currency 기본 false). cost-flow 전환도 **미설정 테넌트엔 무영향**이어야 한다.

---

## 2. Decisions

### D1 — cost-flow method 는 **per-tenant 설정**, 기본 `WEIGHTED_AVERAGE`(net-zero), `FIFO` opt-in

새 테이블 `fx_cost_flow_config(tenant_id PK, method, updated_by, updated_at)`, `method ∈ {WEIGHTED_AVERAGE, FIFO}`. **미설정 = `WEIGHTED_AVERAGE`** → 기존 결제·IT byte-unchanged. 운영자가 테넌트 단위로 `FIFO` 를 켜면 그 테넌트의 이후 결제부터 로트 소비로 산정.

- **왜**: net-zero. V7 `reconciliation_fx_tolerance` 와 동형(per-tenant 설정 + 미설정시 기존 동작). 전역 교체는 모든 테넌트의 실현손익을 소급 변경.
- **세분화 범위**: 1차는 **per-tenant**. per-(tenant, account, currency) 세분화는 deferred(§ 3 로드맵) — 한 테넌트가 일부 포지션만 FIFO 로 운영하는 요구가 실증되면 추가.
- **버린 대안**:
  - **(B) FIFO 전역 교체** — net-zero 위반, 과거 실현손익 소급 변경, 기존 IT 전면 재작성. 거부.
  - **(C) LIFO** — IFRS/K-IFRS 금지. 거부.
  - **(D) 이동평균(moving-average)** — 현 가중평균과 사실상 동치(매 취득마다 평균 재계산)라 새 감사가치 없음. 거부.

### D2 — lot 모델 = **materialized `fx_position_lot` 테이블**, 취득 시 로트 생성, `remaining_foreign_minor` 트랜잭션 갱신

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `lot_id` | VARCHAR(36) PK | 로트 식별자 |
| `tenant_id` | VARCHAR(36) | 테넌트 |
| `ledger_account_code` | VARCHAR(...) | 포지션 계정 |
| `currency` | VARCHAR(3) | 외화 |
| `acquired_at` | DATETIME(6) | 취득 시각 (FIFO 정렬 1차키) |
| `seq` | BIGINT | 동시각 tiebreak (FIFO 정렬 2차키) |
| `original_foreign_minor` | BIGINT | 취득 외화량 |
| `original_base_minor` | BIGINT | 취득원가(KRW), **불변** |
| `remaining_foreign_minor` | BIGINT | 미소비 외화 잔량 (결제 시 차감) |
| `carrying_base_minor` | BIGINT | 현 장부원가(KRW), 초기=original, **재평가 반영 가능**(§ D4) |
| `source_journal_entry_id` | VARCHAR(36) | 취득 포스팅 추적 |
| `created_at` | DATETIME(6) | 행 생성 |

- **로트 생성**: 포지션을 **증가**시키는 외화 취득 포스팅(자산 DR / 부채 CR 방향, |F| 증가) 시 로트 1행 생성. 훅 위치 = `PostJournalEntryUseCase.post()` 의 외화 라인 감지. **설정 무관 항상 생성(shadow)** — FIFO 를 나중에 켜도 로트 이력이 연속되도록. 소비/원가 산정만 D1 설정으로 분기.
- **소비 상태**: 부분결제는 `remaining_foreign_minor`/`carrying_base_minor` 를 차감해 추적(로트가 0 이 되면 closed). 동시성 = 결제 트랜잭션 내 로트 행 비관적 락 또는 조건부 UPDATE(`remaining >= consumed`).
- **버린 대안**:
  - **(B) journal_line 에서 매 결제 on-the-fly 파생** — 부분소비 residual 상태를 소비원장 없이 추적 불가, 매 결제 O(history) 재계산, 감사 빈약. 거부.
  - **(C) event-sourced 로트 프로젝션** — over-engineering(현 ledger 는 상태기반 JPA). 거부.

### D3 — FIFO 소비 + 실현손익 공식

`FIFO` 설정 테넌트의 결제 시 `SettleForeignPositionUseCase` 가 집계 로드(현 `accountTotalsForCurrency`) 대신 **열린 로트를 `(acquired_at, seq)` 오름차순으로 walk**:

```
needed = |F_settle|;  C_settle_fifo = 0
for lot in open_lots order by (acquired_at, seq):
    consume = min(lot.remaining_foreign_minor, needed)
    slice_base = round(lot.carrying_base_minor × consume / lot.remaining_foreign_minor, HALF_UP)
    lot.remaining_foreign_minor -= consume
    lot.carrying_base_minor     -= slice_base
    C_settle_fifo += slice_base
    needed -= consume
    if needed == 0: break
realized = proceedsBase − C_settle_fifo
```

- 결제 엔트리 **shape 불변**(3-line: 포지션제거 + base proceeds + 실현 FX contra). **`C_settle` 도출만** 가중평균→로트 walk 으로 교체. residual carrying = 남은 로트들의 `carrying_base_minor` 합(lot-exact, pool pro-rata 아님).
- **불변식 보존**: matcher 는 여전히 auto-post 안 함(F8), 결제는 `PostJournalEntryUseCase` 단일 경로(closed-period guard·audit·outbox 상속), 이중기입 KRW 균형 불변, 멱등(`settle:{key}`) 불변.

### D4 — 재평가(unrealized revaluation) × lot 상호작용 (설계 hazard, 명시 결정)

**문제**: `FxRevaluationPolicy` 가 pool carrying 을 spot 으로 조정(미실현 인식, 외화량 불변·`baseAdjustment` 라인)하지만 로트는 취득원가를 보존한다. 로트의 `original_base` 합 ≠ 재평가 반영된 pool carrying. FIFO 결제가 `original_base` 만 쓰면 **재평가분 이중계상**(현 가중평균 모델은 "C 가 직전 재평가를 이미 흡수" 라 회피하던 것).

**결정 D4-a**: 로트에 **mutable `carrying_base_minor`** 를 두고(초기=original), 재평가가 **열린 로트들에 델타를 pro-rata 분배**(각 로트 `carrying_base_minor` 갱신). FIFO 결제는 consumed 로트의 **`carrying_base_minor`** 를 원가로 사용. → 이중계상 없음, 현 "C_settle 가 직전 재평가를 흡수" 불변식과 정합. `original_base_minor` 는 취득원가 감사용으로 불변 유지.

- **단계 trade-off (구현 태스크에서 택일, 권장 명시)**:
  - **권장(정확성 우선, regulated)**: D4-a 를 FIFO 1차 구현에 **포함** — revaluation 델타 로트 분배까지.
  - **축소 옵션**: 1차는 "FIFO 포지션에 revaluation 미적용"(설정 상호배제·가드)으로 시작하고 D4-a 분배를 follow-up 으로 deferred. 단 이 경우 제약을 운영자에게 노출(FIFO+재평가 동시 불가)해야 하며, regulated 도메인에선 권장하지 않음.

### D5 — 마이그레이션 net-zero (additive)

- **V9**: `fx_position_lot` + `fx_cost_flow_config` 신설(additive, 기존 테이블·행 byte-unchanged).
- **backfill**: 기존 열린 외화 포지션을 `(tenant, account, currency)`별 **단일 synthetic 로트**로 생성 — `acquired_at` = 그 포지션 최초 라인의 `posted_at`, `original/remaining/carrying` = 현 집계값. 미설정 테넌트(=`WEIGHTED_AVERAGE`)엔 로트가 **shadow**(소비 분기 미진입)라 무영향.
- 기존 동작·IT byte-unchanged(net-zero) — FIFO 를 켠 테넌트에서만 거동 변화.

---

## 3. Consequences

**긍정**:
- 로트별 처분 추적(감사) — 어느 취득분이 어느 결제로 빠졌는지 영속.
- 처분손익을 취득시점 환율에 정확 귀속 — K-IFRS/IFRS FIFO 정합.
- per-tenant opt-in·net-zero — 기존 테넌트 무영향, 포트폴리오상 "회계정책 선택권" 표면.

**부정/리스크**:
- 쓰기경로에 로트 생성·소비 추가(복잡도↑), 결제 시 로트 행 락(동시성).
- **D4 재평가-분배 정확성 hazard** — 가장 미묘. 분배 반올림 누적·잔량 drift 를 IT 로 강하게 고정해야(가중평균의 "round(C×F/F)=C 자기보정" 대응물 필요).
- backfill 정합성 — synthetic 로트의 `carrying` 이 현 pool carrying 과 정확히 일치해야(이중계상 0).

**불변식(F-invariants) 영향: 없음** — 이중기입·KRW 균형·F8(matcher non-auto-post)·결제 엔트리 shape·멱등 모두 불변. cost-flow 는 `C_settle` 도출 내부 정책일 뿐.

---

## 4. Alternatives Considered (요약)

- **D1**: (B) FIFO 전역 교체 = net-zero 위반·소급변경; (C) LIFO = IFRS 금지; (D) 이동평균 = 가중평균 동치. 모두 거부.
- **D2**: (B) journal_line on-the-fly 파생 = residual 상태 추적 불가·O(history); (C) event-sourced = over-engineering. 모두 거부.
- **D4**: "원가만 쓰고 재평가 무시" = 이중계상; "FIFO+재평가 상호배제 영구" = regulated 부적합. D4-a(로트 carrying 분배) 채택.

---

## 5. 관계 (ADR-MONO-008 / 14증분 / reconciliation)

| | ADR-MONO-008 (finance bootstrap) | FxSettlementPolicy (10th) | ReconciliationMatcher (FIN-BE-017/020/021) | V7 reconciliation_fx_tolerance |
|---|---|---|---|---|
| 관계 | **하위** — 부트스트랩이 연 ledger 도메인의 내부 회계방식 결정 | **교체-대상-내부** — 결제 엔트리 shape 불변, `C_settle` 도출만 분기 | **무관** — matcher 는 결제 라인을 외부명세와 대사할 뿐, 원가흐름 비관여 | **미러** — per-tenant 설정·미설정시 net-zero 기본 패턴 차용 |

---

## 6. Status Transition History

| Date | Transition | Decision summary | Trigger | PR |
|---|---|---|---|---|
| 2026-06-14 | created PROPOSED | D1 = per-tenant cost-flow 설정, 기본 WEIGHTED_AVERAGE net-zero, FIFO opt-in(전역교체 B·LIFO C·이동평균 D 거부). D2 = materialized `fx_position_lot`(취득시 생성·shadow·remaining 차감; on-the-fly B·event-sourced C 거부). D3 = FIFO 로트 walk 으로 `C_settle` 산정, 엔트리 shape·불변식 불변. D4 = revaluation 델타를 열린 로트 carrying 에 pro-rata 분배(D4-a, 이중계상 회피; 권장=1차 포함). D5 = additive V9 + synthetic 로트 backfill, net-zero. | 사용자 명시 선택 — ledger 후속 "FIFO/lot cost-basis 설계" (2026-06-14, ADR-MONO-033 직후 충돌 없는 finance 방향) | (this) |

> **PROPOSED only.** ACCEPTED 전환 + § 3.1 실행 로드맵 착수는 별도 user-explicit-intent 태스크(staged-child 패턴, ADR-MONO-032/033 sibling). Self-ACCEPT 금지.

### 3.1 실행 로드맵 (post-ACCEPTED; sketch, ACCEPTED 시 확정)

1. **`TASK-FIN-BE-023`** (D1) — `fx_cost_flow_config` + per-tenant method 설정 read/write(V7 `reconciliation_fx_tolerance` 패턴 미러). Model = **Sonnet**.
2. **`TASK-FIN-BE-024`** (D2/D5) — `fx_position_lot` + V9 + 취득 로트 생성 훅(shadow, 설정무관) + synthetic 로트 backfill. Model = **Opus**.
3. **`TASK-FIN-BE-025`** (D3) — `SettleForeignPositionUseCase` FIFO 분기(설정=FIFO 시 로트 walk) + 실현손익 lot-exact + 부분결제 residual lot-exact. Model = **Opus**.
4. **`TASK-FIN-BE-026`** (D4-a) — revaluation 델타 열린 로트 carrying pro-rata 분배. Model = **Opus**.
5. **(deferred)** per-(account,currency) 세분화 · operator 콘솔 finance lot drill-in · (필요시) specific-identification.
