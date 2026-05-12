# Monorepo-level ADR Index

Architecture Decision Records that span the entire monorepo (shared library
boundary, cross-project workflow, infrastructure conventions). Project-internal
ADRs live under `projects/<name>/docs/adr/`.

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-MONO-001](ADR-MONO-001-port-prefix-scaling.md) | PORT_PREFIX 슬롯 부족과 7개+ 프로젝트 동시 운영 정책 | ACCEPTED | 2026-05-02 |
| [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) | Phase 4 (Template 레포 추출 진입 결정 + scm catalyst) | ACCEPTED | 2026-05-04 |
| [ADR-MONO-003](ADR-MONO-003-phase-5-template-extraction-deferred.md) | Phase 5 (Template 레포 추출 발사) 결정 — DEFERRED | DEFERRED | 2026-05-08 (재평가 2026-05-09) |
| [ADR-MONO-004](ADR-MONO-004-shared-messaging-scaffolding.md) | Shared Messaging Scaffolding in `libs/java-messaging` | ACCEPTED | 2026-05-10 |
| [ADR-MONO-005](ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) | Saga Timeout / Escalation / Dead-Letter Policy (4-category taxonomy) | ACCEPTED | 2026-05-11 |
| [ADR-MONO-006](ADR-MONO-006-lint-remediation-as-agent-context.md) | Lint Remediation Message as Agent Context (OpenAI Harness gap A, 4-block standard) | ACCEPTED | 2026-05-12 |
| [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) | Worktree-isolated Ephemeral Observability Stack (OpenAI Harness gap #3, Vector + VictoriaLogs/Metrics) | ACCEPTED | 2026-05-12 |
| [ADR-MONO-008](ADR-MONO-008-finance-platform-bootstrap.md) | finance-platform Bootstrap Criteria (next domain after Phase 5 launch) | PROPOSED | 2026-05-13 |
| [ADR-MONO-009](ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) | Chrome DevTools MCP Visual Regression Loop (OpenAI Harness gap #4, triple-snapshot LOOP UNTIL CLEAN) | PROPOSED | 2026-05-13 |

---

## Authoring Convention

- File name: `ADR-MONO-NNN-short-kebab-title.md`
- Required header fields: `Status`, `Date`, `Decision driver`, `Supersedes`, `Related`
- Status lifecycle: `PROPOSED → ACCEPTED | DEFERRED | REJECTED | SUPERSEDED`
- Sections: `Context`, `Decision` (numbered `D1, D2, …`), `Alternatives Considered`, `Consequences`, `Verification`, `Outstanding follow-ups`
- Reference: `platform/architecture-decision-rule.md`
