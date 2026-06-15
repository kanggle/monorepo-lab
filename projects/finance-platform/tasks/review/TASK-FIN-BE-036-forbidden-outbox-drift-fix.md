# TASK-FIN-BE-036 — ledger architecture.md "Forbidden: outbox path" 드리프트 정정

- **Status**: review
- **Project**: finance-platform
- **Service**: ledger-service
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: doc drift fix (TASK-FIN-BE-035 § Findings DF-1 후속)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (1줄 정정, 정확도 우선)

## Goal

`ledger-service/architecture.md` `### Forbidden dependencies` 의 "an outbox/publish path
(terminal consumer)" 는 **3rd 증분(FIN-BE-009) 이후 stale** 이다 — ledger 는 그 시점부터
**publishing consumer**(per-service `ledger_outbox` + `AbstractOutboxPublisher`)다. FIN-BE-034
오버뷰 정합 결과(outbox 구현됨)와 정면 모순. FIN-BE-035 refactor 는 의미보존 원칙상 verbatim
보존 + report-only(DF-1)로 남겼고, 본 태스크가 그 1줄 드리프트를 정정한다.

## Scope

**In**: `ledger-service/architecture.md` `### Forbidden dependencies` 한 항목 정정 —
"outbox/publish path (terminal consumer) 금지" → **실제 잔존 제약**으로 교체: libs
`OutboxAutoConfiguration` / `OutboxWriter` 경로 금지(그 `ProcessedEventJpaEntity` 가
consumer-dedupe `processed_events` 와 충돌 → per-service `AbstractOutboxPublisher` /
`LedgerOutboxJpaEntity` 경로 사용, **3rd 증분 이후**; terminal consumer 는 1~2nd 증분에 한함).
본문 § Event publication / 3rd 증분 결정과 일치.

**Out**: 다른 Forbidden 항목(float/double·finance_db write·외부 SDK)·다른 섹션·코드/마이그/계약.

## Acceptance Criteria

- **AC-1 — 모순 해소.** Forbidden 항목이 더 이상 "outbox 전면 금지"로 읽히지 않고, 본문(§ Event
  publication, 3rd 증분 "per-service OutboxRow path, NOT libs OutboxWriter")·events 계약과 일치.
- **AC-2 — 정확성.** 정정 후 제약 = libs `OutboxAutoConfiguration`/`OutboxWriter` 금지(충돌 사유 명시),
  per-service outbox 는 허용/사용.
- **AC-3 — 국소성.** 해당 1항목만 변경; 코드/마이그레이션/계약 무영향(doc-only).

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (§ Layer Structure §
  Forbidden dependencies, § Event publication, Increment Scope 3rd 증분)
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (Outbox path 블록쿼트)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-035-finance-spec-readability-refactor.md` (DF-1 출처)

## Edge Cases

- terminal-consumer 표현은 1~2nd 증분 한정으로 정확히 한정(역사적 사실 보존).

## Failure Scenarios

- 과교정(per-service outbox 까지 금지로 오기) → AC-2 위반. 본 정정은 libs 경로만 금지로 명시해 방지.
