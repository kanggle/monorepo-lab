# TASK-FIN-BE-030 — ADR-002 실시간 FX 환율 피드 (PROPOSED → ACCEPTED, doc-only carrier)

- **Status**: done
- **Project**: finance-platform
- **Service**: ledger-service (architecture 결정 — 코드 없음)
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: 22nd ledger increment (방향 결정 — finance-platform 두 번째 프로젝트 ADR, 첫 외부 HTTP 통합)
- **Analysis model**: Opus 4.8 / **Implementation model**: — (doc-only)

## Goal

ledger-service 의 환율 출처를 **운영자 수동입력**에서 **config-gated 외부 피드 + 캐시 + staleness 가드**로
전환하는 방향을 ADR 로 기록하고 ACCEPTED 로 확정한다. 외부 HTTP 의존·새 실패모드·감사 정책은
HARDSTOP-09(코드로 묵시 결정 금지) 대상이므로 결정을 먼저 문서화한다.

이 태스크는 **doc-only 캐리어**(ADR-001 의 FIN-BE-022 패턴) — ADR 문서 + ADR README 인덱스만 건드리고
**코드는 0**. 실행은 별도 post-ACCEPTED 태스크(FIN-BE-031 → 032).

## Scope

**In scope**:
- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` 신설 — Context(현 수동입력 모델 회고
  + architecture.md forward-declared "live FX rate feed") / Decisions D1~D6 / Consequences / Alternatives /
  Status Transition History(PROPOSED + ACCEPTED 2행, governance trail PR 내 보존).
- `projects/finance-platform/docs/adr/README.md` 인덱스에 ADR-002 행 추가(Accepted).

**Out of scope**: 코드·마이그레이션·테스트 일체(전부 FIN-BE-031/032). architecture.md 본문 변경(구현 태스크에서).

## Acceptance Criteria

- **AC-1** — ADR-002 문서가 ADR-001 형식(Context → Decisions → Consequences → Alternatives → History)을 미러,
  D1~D6 각각에 채택 결정 + 버린 대안 + 이유 명시.
- **AC-2** — Status = ACCEPTED, § 6 History 에 PROPOSED + ACCEPTED 2행(self-ACCEPT 아님 — 사용자 명시 게이트 기록).
- **AC-3** — § 3.1 실행 로드맵 UNPAUSED, FIN-BE-031(port+캐시+폴러 shadow) → 032(소비+staleness) 순서.
- **AC-4** — README 인덱스에 ADR-002(Accepted) 등재. 코드 변경 0(doc-only).

## Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (sibling ADR — 형식·staged-child 패턴)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (Increment Scope preamble — "live FX rate feed" forward-declared)
- `.claude/skills/backend/external-http-integration/SKILL.md` (config-gated outbound port + noop 기본 + ResilienceClientFactory)

## Related Contracts

- 없음 (doc-only; 계약 변경은 FIN-BE-031/032)

## Edge Cases

- ADR 가 PROPOSED 독립 머지 전 ACCEPTED 됨 → governance trail 을 동일 PR 내 2행으로 보존(ADR-001 FIN-BE-022 선례).

## Failure Scenarios

- 없음 (doc-only).
