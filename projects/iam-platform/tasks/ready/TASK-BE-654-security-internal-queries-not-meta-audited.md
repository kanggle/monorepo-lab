# Task ID

TASK-BE-654

# Title

`security-query-api.md` says every internal security query is meta-audited (A5), but security-service has no such audit — for login-history and suspicious-event reads alike

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- docs
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (audit of reads of personal data — what to record, where, and without making every overview load write a row)
> Drafted 2026-10-24 UTC from `TASK-MONO-811`'s implementation report (backend agent). Not re-measured by the drafter: AC-0 is the measurement.

# Dependency Markers

- 출처: `TASK-MONO-811` § 구현 결정 «기존 공백». The new `/internal/security/metrics/daily` endpoint records its own exemption in the contract (aggregates only).
- 관련: admin-service meta-audits `GET /api/admin/audit` on its side (`admin_actions` row, `AUDIT_QUERY`). So the operator-facing read may already be audited one hop earlier. AC-0 decides whether that is the A5 the contract means.

# Goal

The contract and the code agree on who audits a read of login history and suspicious events. Either security-service records it, or the contract says the caller does and names where.

# Scope

## In Scope

- **AC-0:**
  - The A5 sentence in `projects/iam-platform/specs/contracts/http/security-query-api.md`, quoted.
  - The read paths: `GET /internal/security/login-history` · `/suspicious-events`. Find any audit write on them in security-service (expected: none).
  - What admin-service records for the same request: `AuditQueryUseCase` meta-audit row, `source=login_history|suspicious`.
  - Are there other callers of these internal endpoints besides admin-service?
- **Decision (owner):**
  - (a) Contract-only: «audited by the caller (admin-service `admin_actions`)».
  - (b) security-service writes its own audit row.
  - (c) Both.
- Implementation per the decision.

## Out of Scope

- The aggregate metrics endpoint. It returns no personal data, and its exemption is already recorded.

# Acceptance Criteria

- [x] **AC-0** — the above, file:line.
  - → **닫힘 2026-10-24 UTC** (origin/main `60d4d96bd`). 측정은 Explore(opus)가 했다.
  - **A5 문장:** `security-query-api.md:160` 은 security-service 내부 `admin_query_audit` 로그(호출자 operator_id · accountId · 시간)를 적었다.
    - 같은 파일 `:162` 가 «그 코드는 없다 — 구현되지 않은 의도» 라고 스스로 적고 있다.
    - 같은 취지의 문장이 `audit-trail.md:74-75` · `security-service/architecture.md:177` 에도 있다.
    - `admin_query_audit` 는 코드와 마이그레이션(V0001–V0012)에 0 이다.
  - **security-service 읽기 경로:** `LoginHistoryQueryController.java:21-37` · `SuspiciousEventQueryController.java:26-50` → `SecurityQueryService`(`readOnly`)는 조회와 마스킹만 한다. 감사 기록은 0 이다.
    - 요청에 운영자 id 가 실려 오지 않는다(admin `SecurityServiceClient.java:115-123,149-151`).
  - **admin-service:** `AuditQueryUseCase.java:162-179` 가 `AUDIT_QUERY` 행을 남긴다(`target_id`=accountId 또는 `*`). 공백이 셋 있다.
    1. source · 기간 · 필터를 남기지 않는다.
    2. 행을 하위 호출 **뒤에** 써서(:166), 하위 호출이 실패하면 행이 없다.
    3. `permission_used` 가 늘 `audit.read` 라 `rbac.md:541`(`audit.read+security.event.read`)과 어긋난다.
    - DENIED 행은 하위 호출 전에 쓴다(aspect · 합성 권한 · 테넌트 게이트).
  - **다른 호출자:** 코드에서는 admin `SecurityServiceClient` 하나뿐이다. 다만 `internal.invoke` 를 가진 client 6개는 구조상 부를 수 있다(`InternalAuthFilter.java:79-99` 는 client 를 보지 않는다).
  - **호출 빈도:** 콘솔 개요(`overview-api.ts:206` · `iam-overview overview-state.ts:212`)는 `accountId` 없이 불러서 security-service 에 닿지 않는다. 개인 데이터 읽기는 감사 화면에서 계정을 지정했을 때뿐이다. ⚪ 운영 호출량은 지표가 없어 재지 못했다.
  - 🔴 **별도 결함 발견 → `TASK-BE-655`:** `source` 없이 `accountId` 를 주면 `security.event.read` 검사 없이 보안 이벤트가 읽힌다.
- [x] **AC-1** — owner decision recorded.
  - → **소유자 결정 2026-10-24 UTC (선택창): «호출자 감사 + admin 보강».**
    - 계약 `security-query-api.md:160-162` 를 «호출자 admin-service 의 `admin_actions` 가 감사한다» 로 고친다.
    - admin 행이 source · 기간 · 대상 계정을 기록한다.
    - 하위 호출이 실패해도 행이 남는다.
    - `permission_used` 를 `rbac.md:541` 에 맞춘다.
    - 운영자 id 헤더는 새로 만들지 않는다.
    - `internal.invoke` 의 다른 client 직접 호출은 감사되지 않는다는 점을 계약에 적는다.
  - 순서: `TASK-BE-655` 가 먼저 머지된다(같은 파일).
- [ ] **AC-2** — contract and code agree. If (b) or (c): a test shows that one read writes one audit row, and that a denied read writes none (or a DENIED row, per decision).

# Related Specs

- `projects/iam-platform/specs/contracts/http/security-query-api.md`
- `projects/iam-platform/specs/services/admin-service/rbac.md` (meta-audit)

# Related Contracts

- `GET /internal/security/login-history` · `GET /internal/security/suspicious-events`

# Edge Cases

- A per-account read is personal data. The aggregate is not. Keep the exemption explicit, not implied.

# Failure Scenarios

1. (b) is implemented as an audit row per request, and the console's overview calls it often → audit-table growth. Measure call volume in AC-0.
