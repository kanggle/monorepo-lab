# Architecture Decision Records

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-fx-cost-flow-method-fifo-lot-tracking.md) | 외화 포지션 원가흐름을 가중평균에서 FIFO 로트 추적으로(per-tenant 설정·opt-in) | Accepted |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
- monorepo-level(cross-cutting·플랫폼) 결정은 repo-root `docs/adr/ADR-MONO-*` 에, 본 프로젝트 도메인-내부 결정은 여기에 기록한다.
