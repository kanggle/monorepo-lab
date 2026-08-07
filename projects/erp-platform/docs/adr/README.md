# Architecture Decision Records — erp-platform

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-erp-event-plane-tenant-axis.md) | erp 이벤트 평면의 테넌트 축 — 선언(`PROJECT.md`·read-model 스펙 = **단일 테넌트**, "All projected rows belong to the `erp` tenant")과 실행 중인 시스템(두 프로듀서 모두 `demo-corp` 를 싣는다)이 갈라져 있다. A(선언 고수) / B(다중 테넌트 승격) / C(데모 회귀). 🔴 **설정으로는 못 고친다** — `required-tenant-id` 를 HTTP 두 곳은 도메인 키로, 위임 매퍼는 테넌트 값으로 읽는다. `TASK-ERP-BE-043` 의 AC-3/AC-5 를 게이트 | Proposed |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
- monorepo-level(cross-cutting·플랫폼) 결정은 repo-root `docs/adr/ADR-MONO-*` 에, 본 프로젝트 도메인-내부 결정은 여기에 기록한다.
- ACCEPTED 승격은 `platform/architecture-decision-rule.md § The ACCEPTED Gate` 를 따른다(라이브 검증 + deciders 의 정확형 intent, self-ACCEPT 금지).
