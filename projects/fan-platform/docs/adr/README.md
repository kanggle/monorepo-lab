# Architecture Decision Records — fan-platform

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-real-pg-portone-verification-boundary.md) | 실 PG 연동 — PortOne V2 클라이언트 개시 결제 + 서버측 검증 경계(profile 게이팅, mock 은 CI/test 기본값 유지) | Proposed |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
- monorepo-level(cross-cutting·플랫폼) 결정은 repo-root `docs/adr/ADR-MONO-*` 에, 본 프로젝트 도메인-내부 결정은 여기에 기록한다.
- ACCEPTED 승격은 `platform/architecture-decision-rule.md § The ACCEPTED Gate` 를 따른다(라이브 검증 + deciders 의 정확형 intent, self-ACCEPT 금지).
