# Architecture Decision Records — iam-platform

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-oidc-adoption.md) | OIDC Authorization Server 채택 — IAM 을 6개 도메인의 공유 인증 공급자로 승격 | ACCEPTED |
| [ADR-002](ADR-002-admin-tenant-scope-sentinel.md) | admin-service 의 멀티테넌트 운영자 격리 — Tenant Scope Sentinel | ACCEPTED |
| [ADR-003](ADR-003-public-client-refresh-token-revoke-converter.md) | SAS public-client `refresh_token`·`revoke` 그랜트용 `AuthenticationConverter` | **ACCEPTED** — 옵션 B closure |
| [ADR-004](ADR-004-oauth-callback-ci-linux-503-isolation.md) | OAuth 콜백 IT 의 CI Linux 503 격리 전략 | **ACCEPTED** — Phase 2 옵션 1 (HTTP/1.1 강제) |
| [ADR-005](ADR-005-service-to-service-workload-identity.md) | 서비스 간(Workload) 인증 — `client_credentials` 단기 JWT | ACCEPTED |
| [ADR-006](ADR-006-external-idp-login-sas-integration.md) | 외부 IdP(소셜) 로그인의 SAS 브라우저 플로우 통합 — Upstream Identity Brokering | ACCEPTED |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
- monorepo-level(cross-cutting·플랫폼) 결정은 repo-root `docs/adr/ADR-MONO-*` 에, 본 프로젝트 도메인-내부 결정은 여기에 기록한다.
- ACCEPTED 승격은 `platform/architecture-decision-rule.md § The ACCEPTED Gate` 를 따른다(라이브 검증 + deciders 의 정확형 intent, self-ACCEPT 금지).
- Status 헤더 표기는 `**Status:** <VALUE>` (콜론을 볼드 **안**에) — `scripts/check-project-adr-index-drift.sh` 가 강제한다. 이 표의 상태 칸과 ADR 본문의 Status 가 갈라지면 CI 가 RED 다.
