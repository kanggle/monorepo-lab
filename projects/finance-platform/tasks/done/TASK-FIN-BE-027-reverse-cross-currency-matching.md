# Task ID

TASK-FIN-BE-027

# Title

**역방향 cross-currency 매칭** (foreign-external → KRW-internal) — FIN-BE-021(14th, base-external → foreign-internal)의 거울. 은행이 **외화로 정산 보고**하는데 ledger 는 그 정산을 **base(KRW) internal 라인**으로 부킹한 경우, 외화 external 라인의 bank-reported base(KRW)값을 KRW internal 라인의 amount 와 tolerance 매칭해 허위 UNMATCHED 2개를 실 매칭으로 전환한다. 순수 matcher fallback 1개 추가(신규 use-case/REST/마이그 0, crossCurrency 플래그·external baseAmount 컬럼 재사용). ledger 19번째 증분.

# Status

done

# Owner

backend

# Task Tags

- fintech
- audit-heavy

---

# Dependency Markers

- **child of / mirrors**: TASK-FIN-BE-021 (14th, done — `findCrossCurrencyCandidate` base-external→foreign-internal). 본 태스크는 그 **반대 방향**(foreign-external→KRW-internal)을 strict fallback 으로 추가. ADR-001 의 forward-declared 잔여 항목("foreign-external → KRW-internal 역방향").
- **선행**: FIN-BE-017(external statement line 의 optional `base_amount` — V6), FIN-BE-020(`FxTolerance` 주입), FIN-BE-021(crossCurrency 플래그 — V8, 같은 audit 의미 재사용). 모두 done.
- **precedence / net-zero**: 동일통화 매칭(`findCandidate`) 1순위 byte-unchanged; KRW-external→foreign-internal(FIN-BE-021) 그대로; **신규 역방향은 그 다음 strict fallback**. 외화 external 이 baseAmount 미보유거나 KRW internal 매칭 없음 → 종전 UNMATCHED_EXTERNAL. 기존 reconciliation 전부 byte-identical.
- **마이그레이션 없음** — `reconciliation_match.cross_currency`(V8) + external line `base_amount_minor`(V6) 이미 존재. 코드-only(matcher + 테스트 + spec).

# Goal

외화 external 라인이 동일통화·기존 cross 매칭 모두 실패할 때, bank-reported base(KRW)값으로 KRW internal 라인과 매칭(crossCurrency=true)해 reconciliation 매칭의 양방향 대칭을 완성한다.

# Scope

- **`ReconciliationMatcher.match()`** else 분기 확장: 동일통화 후보 없음일 때
  - 기존: `ext.currency()==BASE` → `findCrossCurrencyCandidate`(KRW-external→foreign-internal).
  - **신규**: `ext.currency()!=BASE` AND `ext.baseAmount()!=null` → **`findReverseCrossCurrencyCandidate`** = 첫 미소비 **KRW(base)** internal 라인(같은 direction, `internal.money().currency()==BASE`)의 `money().minorUnits()` 가 `ext.baseAmount().minorUnits()` 와 `FxTolerance` 이내 → 매치. crossCurrency=true, **AMOUNT_MISMATCH 없음**(base 비교가 매치키). match `money`=`ext.money()`(외화 external 금액), internal `journalEntryId` 동반.
  - 둘 다 -1 → 종전 UNMATCHED_EXTERNAL.
- **precedence 순서**: ① 동일통화 `findCandidate` ② KRW-external→foreign(`findCrossCurrencyCandidate`) ③ foreign-external(baseAmount)→KRW(`findReverseCrossCurrencyCandidate`) ④ UNMATCHED. ②③ 는 상호배타(ext.currency()==BASE vs !=BASE).
- **matcher 순수 유지**(tolerance 주입, repository 미접근). F8 보존(매치/discrepancy만 기록, auto-post 0).
- **Spec**: `architecture.md` § Reconciliation 의 cross-currency 절에 역방향(foreign-external→KRW-internal) 하위 단락 추가(19th 증분, FIN-BE-021 대칭).
- **NO change**: `findCandidate`·`findCrossCurrencyCandidate`(byte-unchanged), settlement/revaluation/lot, use-case/REST, 마이그레이션.

# Acceptance Criteria

- **AC-1** 외화 external 라인(예 USD, baseAmount=130000 KRW)이 동일통화 후보 없음 + KRW internal 라인(money=130000 KRW, 같은 direction) 존재 → **역방향 cross 매치**(crossCurrency=true, AMOUNT_MISMATCH 없음), KRW internal 소비, external MATCHED. 종전 2개 UNMATCHED(EXTERNAL+INTERNAL) 사라짐.
- **AC-2** tolerance 적용: `|KRW internal.money − ext.baseAmount|` 가 band 이내 → 매치; 초과 → 후보 아님 → UNMATCHED_EXTERNAL.
- **AC-3** **net-zero / precedence**: ① 동일통화 매칭 byte-unchanged ② FIN-BE-021 KRW-external→foreign 경로 byte-unchanged ③ 외화 external 이 baseAmount 미보유 → 역방향 미진입(UNMATCHED_EXTERNAL 종전대로) ④ KRW internal 매칭 없음 → UNMATCHED_EXTERNAL. 기존 reconciliation IT 전부 GREEN.
- **AC-4** F8: 역방향 cross 매치도 매치/discrepancy 기록만(auto-post/correction 0).
- **AC-5** 단위 테스트(`ReconciliationMatcherTest`): 역방향 매치(AC-1), tolerance 경계(AC-2), baseAmount 없음→UNMATCHED, precedence(동일통화·FIN-BE-021 우선), 다중 라인 deterministic.
- **AC-6** Testcontainers IT(`Ledger…IntegrationTest` 신규 또는 기존 reconciliation IT 확장): 외화 external→KRW internal 역방향 reconcile end-to-end. 공유-Kafka predicate 충돌 회피=고유 `ledgerAccountCode`.
- **AC-7** `:test` + `:integrationTest` GREEN(Docker on). IT 권위.

# Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ Reconciliation § Multi-currency / cross-currency — 역방향 단락 추가)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-021-cross-currency-base-leg-matching.md` (대칭 선례)

# Related Contracts

- 없음 — matcher 내부 매칭 정책 + 기존 `crossCurrency` 플래그 노출. reconciliation 이벤트/HTTP 계약 shape 불변(discrepancy type/code/status 신규 0).

# Edge Cases

- 외화 external 이 baseAmount 미보유 → 역방향 미진입(매치키 부재). UNMATCHED_EXTERNAL.
- 동일통화 후보가 있으면 역방향 미진입(precedence). 한 external 은 한 번만 매칭.
- KRW external 은 ② 경로만(FIN-BE-021), 외화 external 은 ③ 경로만 — 상호배타.
- tolerance EXACT(0,0) → KRW internal.money == ext.baseAmount 정확 일치만 매치(net-zero 기본).
- 다중 KRW internal 후보 → 첫 미소비(deterministic, 입력 순서).

# Failure Scenarios

- ③ 가 ① 보다 먼저 실행되면 → 동일통화 매칭 깨짐. precedence 순서(①→②/③→④) 엄수.
- 역방향 매치에 AMOUNT_MISMATCH 를 달면 → 오류(base 비교가 매치키지 불일치 아님). FIN-BE-021 대칭으로 discrepancy 0.
- baseAmount 없는 외화 external 을 역방향 매칭하면 → 매치키 부재로 잘못된 매칭. `ext.baseAmount()!=null` 가드 필수.
- `findCrossCurrencyCandidate`/`findCandidate` 를 바꾸면 → net-zero 위반. 신규 `findReverseCrossCurrencyCandidate` 만 추가.
