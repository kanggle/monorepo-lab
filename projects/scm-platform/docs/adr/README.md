# Architecture Decision Records — scm-platform

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-supplier-master-write-surface.md) | v1 공급사 마스터의 쓰기 표면 — `architecture.md` 는 마스터를 **범위 안**("MUST maintain a v1 internal `suppliers` master with AES-GCM-encrypted credentials")이라 선언하는데 계약에는 공급사 엔드포인트가 **0건**이고, 그 부재가 의도적이라는 **유일한 인용이 dangling** 이다(가리키는 § Failure Scenarios 에 그 내용이 없다). A(운영자 엔드포인트) / B(내부 전용) / C(부재를 명시적 결정으로 승격). **A 채택 + 자격증명은 v2 `supplier-service` 로 유보** ⇒ 마스터는 *운영 대상*이고, 자격증명 미보유 공급사가 v1 의 정상 상태다. `TASK-SCM-BE-059` 게이트 **해제** | **ACCEPTED** (2026-08-07) |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
- monorepo-level(cross-cutting·플랫폼) 결정은 repo-root `docs/adr/ADR-MONO-*` 에, 본 프로젝트 도메인-내부 결정은 여기에 기록한다.
- ACCEPTED 승격은 `platform/architecture-decision-rule.md § The ACCEPTED Gate` 를 따른다(라이브 검증 + deciders 의 정확형 intent, self-ACCEPT 금지).
