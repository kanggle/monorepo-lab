# TASK-FIN-BE-034 — ledger-service 스펙 오버뷰 정합 (증분 13~25th)

- **Status**: done
- **Project**: finance-platform
- **Service**: ledger-service
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: doc-only (코드/마이그레이션/계약 의미 변경 0 — 스펙 오버뷰 ↔ 본문 정합)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (권위 스펙 다중 섹션 정합 — 정확도 우선)

## Goal

`ledger-service`는 FIN-BE-007~033(27 증분 — ADR-001 FX cost-flow/FIFO-lot + ADR-002 실시간 FX 환율 피드)까지
구현이 완주됐고, `architecture.md` **본문 §섹션**은 13~25th 증분을 모두 문서화했다. 그러나 **오버뷰/요약 섹션들**
(Provenance · Increment Scope IN-목록 · Forward-declared OUT · Layer Structure 트리 · Identity 표 · error 블록)이
**12th 증분(FIN-BE-018)에서 동결**되어, "이미 구현·문서화된 기능(FIFO/lot cost basis, live FX rate feed,
cross-currency 매칭)을 forward-declared(미구현)로 선언"하는 자기모순이 남아 있다. 본 태스크는 **새 아키텍처 결정이
아니라**(본문 §섹션이 이미 모든 결정을 기록 — HARDSTOP-09 비해당) 요약 섹션을 이미-결정된 본문에 맞추는 **정합 +
누적된 stale forward-declaration 정리**다. `ledger-api.md`의 stale out-of-scope 항목도 함께 정리.

## Scope

**In scope** (docs only):

1. `specs/services/ledger-service/architecture.md`:
   - **Provenance 블록** — 서사를 13~25th까지 확장; "FIFO/lot cost basis, live FX rate feed forward-declared" 문장을 실제 잔여로 교체.
   - **Increment Scope** — 13th~25th "IN" 항목을 기존 "First~Twelfth increment — IN" 스타일로 간결히 추가(각 1~3줄, 본문 §섹션 참조). 본문 상세 서사 무변경(heavy restructure 금지).
   - **Forward-declared — OUT** — 구현된 항목(FIFO/lot cost basis, live FX rate feed, cross-currency 양방향) 제거 + 산발적 "now done" 괄호 정리; 실제 잔여만 유지.
   - **Layer Structure 트리** — FX cost-flow/lot/rate-feed 도메인·포트·use-case·어댑터·컨트롤러·Flyway(V9~V12) 보강(본문 §섹션에 등장하는 클래스명과 철자 일치).
   - **Identity 표** — "Outbound integration" 행에 FX 환율 피드 outbound HTTP(config-gated, best-effort) 반영.
   - **error/ 도메인 블록** — `FxRateUnavailableException [→422]` 추가(cost-flow는 VALIDATION_ERROR 재사용 — 새 예외 없음 명시).
2. `specs/contracts/http/ledger-api.md`:
   - **"Out of scope (forward-declared)"** — 구현된 항목(live FX rate feed, FIFO/lot settlement carrying basis) 제거; §1~§14 본문 무변경.
3. `specs/contracts/events/finance-ledger-events.md`:
   - "Published — emitted" 말미에 "FX 환율 피드(ADR-002)는 outbound HTTP fetch — 의도적으로 이벤트 표면 없음(net-zero)" 한 줄 주석.

**Out of scope**: 본문 §섹션 상세 서사(이미 최신), account-service 스펙(FX 무관), docs/adr/ADR-001·ADR-002(정확),
코드/마이그레이션/계약 **의미** 변경 일체.

## Acceptance Criteria

- **AC-1 — 자기모순 0.** 편집 후 `architecture.md`·`ledger-api.md`에서 `forward-declared`/`caller-supplied`/`not fetched`/`no synchronous outbound`가 **구현된** 기능(FIFO·lot·live FX feed·cross-currency)에 더는 붙지 않는다(grep 확인).
- **AC-2 — 증분 번호 일관성.** Increment Scope IN-목록 ordinal(13th~25th) ↔ 본문 §섹션 헤더 ordinal 1:1 일치.
- **AC-3 — Layer 트리 정확성.** 트리에 추가한 클래스명이 본문 §섹션의 실제 명칭과 철자 일치(`FxPositionLot`, `FxRateProviderPort`, `RefreshFxRateQuotesUseCase`, `ResolveEffectiveFxRate`, `FxRateController`, V9~V12 등).
- **AC-4 — net-zero.** 코드·마이그레이션·계약 의미 변경 0. 기존 구현/CI 무영향(doc-only).
- **AC-5 — 링크 무결성.** 신규/수정 상대 링크·`§` 앵커가 실존 파일·섹션을 가리킨다.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (주 대상)
- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md`
- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md`
- `projects/finance-platform/tasks/done/TASK-FIN-BE-020 … 033` (정합 대상 증분의 정본 task 기록)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md`
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md`

## Edge Cases

- 증분 번호 비-연속(FIN-BE-019 doc-note · 022 ADR-001 carrier · 030 ADR-002 carrier = 비-코드 증분 → ordinal 미부여): IN-목록은 본문 §섹션이 부여한 ordinal만 따른다(13=020 … 25=033, 22nd 생략).
- 21st(per-account override, FIN-BE-029)·24th(rate fallback, FIN-BE-032)는 본문에서 상위 §섹션의 하위로 문서화됨 → IN-목록엔 항목으로 등재하되 본문 위치를 정확히 참조.

## Failure Scenarios

- 정합 누락(요약↔본문 잔여 불일치) → AC-1/AC-2 grep·번호 점검에서 검출 → 재수정.
- 링크 깨짐 → AC-5 점검에서 검출(memory `project_mono_085_dead_reference_batch` 패턴: 대형 편집 후 링크 별도 확인).
