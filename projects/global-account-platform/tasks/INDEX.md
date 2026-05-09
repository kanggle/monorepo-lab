# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-INT-XXX`: integration

(`TASK-FE-XXX` is reserved but not used in this backend-only project.)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
- Do not modify a task file after it moves to `review/` or `done/`.

## Move Method (all transitions)
All task file moves between directories must use `git mv`, never copy-then-delete or Write-new-file.
Example: `git mv tasks/review/TASK-BE-001.md tasks/done/TASK-BE-001.md`
Rationale: preserves git history and prevents duplicate files across directories.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty — tasks will be added after TASK-BE-000 cleanup is merged)

## ready

- `TASK-BE-272-public-client-refresh-token-revoke-converter.md` — **선행=ADR-003**. SAS public-client `AuthenticationConverter` 신설로 Cluster A 3 IT (refresh × 2 + revoke) 회복. 11-cycle PR #264 의 4 anti-pattern (A1 Customizer timing / A2 DomainSync race / A3 manual instantiation @Transactional / A4 test order pollution) 회피 설계. SAS stock `PublicClientAuthenticationConverter` 가 `authorization_code + code_verifier` 만 매칭 → public-client refresh_token / revoke 인증 경로 부재 = 본 task 의 핵심 RC. Phase 1 converter + unit test (≤2 cycle) → Phase 2 IT enable + CI verify (≤2 cycle) → Phase 3 Cluster C 부수효과 검증 (≤1 cycle). 총 cycle ≤ 5. 분석=Opus 4.7 / 구현 권장=Opus.

- `TASK-BE-273-oauth-callback-ci-linux-503-diagnostic.md` — **선행=ADR-004**. `OAuthLoginIntegrationTest` 5 IT (Google/Kakao/Microsoft happy + Microsoft preferredUsername + Microsoft existingEmailAutoLink) 의 CI Linux 503 회복. PR #264 + 046-7a 13-cycle 학습 — CB pollution / JVM-shared static state 가설 모두 falsified, 더 깊은 Linux-specific RC (HTTP client / WireMock binding / DNS / Spring DynamicPropertySource 순서 후보). Phase 1 diagnostic harness (`AccountServiceClient` log 강화 + WireMock request listener, ≤2 cycle) → Phase 2 옵션 B (별 Gradle source set) 또는 옵션 C (embedded fake controller, ≤4 cycle) 분기. 6 cycle 초과 시 ADR-004 § 옵션 D fallback (영구 demote). 분석=Opus 4.7 / 구현 권장=Opus.

Cross-project (root `tasks/done/`): TASK-MONO-019 APPROVED 2026-05-02. TASK-MONO-046-7/7a/8/8a closed 2026-05-08~09 (046-7a doc-only, 0/7 IT 회복; 후속 = 본 BE-272/273).

## in-progress

(empty)

## review

(empty — TASK-BE-271 reviewed and moved to done on 2026-05-02)

## done

264 backend tasks + 26 frontend tasks completed. Latest BE batch (2026-05-01..02):
TASK-BE-248 (security tenant events), TASK-BE-249 (admin tenant audit schema),
TASK-BE-250 (admin tenant lifecycle API), TASK-BE-251 (Spring Authorization Server),
TASK-BE-252 (OAuth schema), TASK-BE-256 (tenant onboarding API contract), and
follow-ups 260/261/262/268.
Newly reviewed (2026-05-02): TASK-BE-254 (consumer integration guide,
FIX_NEEDED → 263), TASK-BE-255 (account_roles schema, FIX_NEEDED → 265),
TASK-BE-259 (auth.token.reuse.detected tenant_id, APPROVED), TASK-BE-263
(auth-api.md discovery, APPROVED), TASK-BE-265 (255 review fix, FIX_NEEDED → 267),
TASK-BE-267 (267 review, APPROVED), TASK-BE-253 (community/membership OIDC RS,
FIX_NEEDED → 269), TASK-BE-269 (269 OAuth2 WebClient timeout fix, APPROVED),
TASK-BE-258 (GDPR downstream contract + security ref impl, FIX_NEEDED → 270),
TASK-BE-270 (258 review fix: salt + data-model.md, APPROVED), TASK-BE-257
(bulk provisioning API, FIX_NEEDED → 271), TASK-BE-271 (257 review fix:
readOnly tx + enum + BULK_LIMIT routing, APPROVED).
Numbers TASK-BE-238/239/240/244 were not assigned.
