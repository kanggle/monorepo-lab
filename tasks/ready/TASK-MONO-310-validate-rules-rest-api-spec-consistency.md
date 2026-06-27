# Task ID

TASK-MONO-310

# Title

`/validate-rules` remediation — align `platform/service-types/rest-api.md` with versioning-policy + testing-strategy (URL prefix + unit-test layer)

# Status

ready

# Owner

architect

# Task Tags

- governance
- docs
- platform
- validate-rules

---

# Goal

The 2026-06-27 `/validate-rules` scan surfaced two **spec-consistency** drifts in `platform/service-types/rest-api.md` (a service-type spec read as the authoritative contract for REST-API services per `platform/entrypoint.md`):

1. **URL versioning prefix** — `rest-api.md § Versioning` showed bare `/v1/`, `/v2/`, while the authoritative `platform/versioning-policy.md` (`/api/v{n}/{resource}`) and `platform/naming-conventions.md` (`/api/v1/<resource>`) both mandate the `/api/` prefix. A developer reading only `rest-api.md` would omit `/api/`, breaking gateway routing conventions.
2. **Unit-test layer omitted** — `rest-api.md § Testing Requirements` listed controller-slice / contract / integration tests but **not** unit tests, which `platform/testing-strategy.md` defines as the base of its five-level pyramid.

This task makes both lines additive-consistent with the higher-priority platform specs. Doc-only, no implementation code.

The remaining `/validate-rules` findings are out of scope here: the `.claude/` items (refactor-code category vocabulary, agent boundary asymmetries, duplicate `implementation-workflow` skill `name`) are **classifier-blocked** and are handed to the user as patches; the CLAUDE.md-workflow-vs-entrypoint.md and identity-platform `/v1/oauth` items were judged **intentional-by-design** (CLAUDE.md is a catalog that delegates to `entrypoint.md`; OAuth/OIDC endpoints are conventionally unprefixed) and left unchanged.

**근거**: `/validate-rules` 2026-06-27 보고서 § Warning.

---

# Scope

## In Scope

| 수정 | 위치 | 권위 출처 |
|---|---|---|
| URL 버저닝을 `/api/v{n}/{resource}` 정규형으로 명시(`/v1` = 버전 세그먼트임을 분명히) | `platform/service-types/rest-api.md § Versioning` | `versioning-policy.md` + `naming-conventions.md` |
| Testing Requirements 최상단에 Unit test 레이어 추가 + 5단 피라미드 포인터 | `platform/service-types/rest-api.md § Testing Requirements` | `testing-strategy.md` |

## Out of Scope

- `.claude/commands/refactor-code.md` 카테고리 어휘 정렬 (classifier-blocked → 사용자 패치).
- `.claude/agents/common/` 경계 보강 (architect↔coordinator, backend↔refactoring, event-architect service_types) (classifier-blocked → 사용자 패치).
- `.claude/skills/{backend,frontend}/implementation-workflow` 중복 `name` + `review-checklist` `category: root` (classifier-blocked → 사용자 패치).
- CLAUDE.md § Required Workflow ↔ entrypoint.md (의도적 catalog→detail 위임, 미수정).
- `identity-platform.md`/`auth-service` `/v1/oauth` (OAuth 표준 무접두 경로, 미수정).
- `platform/contracts/` 레이아웃 카탈로그 미기재 (최소 가치, 미수정).

---

# Acceptance Criteria

- [ ] `rest-api.md § Versioning`이 `/api/v{n}/{resource}` 정규 전체경로를 명시하고 `versioning-policy.md`+`naming-conventions.md`를 권위로 인용.
- [ ] `rest-api.md § Testing Requirements`에 Unit test 레이어 + `testing-strategy.md` 5단 피라미드 포인터 추가.
- [ ] 두 수정 모두 additive (기존 의미 유지, contract 변경 없음).
- [ ] impl code 0 (doc-only).

# Related Specs

- [platform/versioning-policy.md](../../platform/versioning-policy.md) — URL 버저닝 권위.
- [platform/naming-conventions.md](../../platform/naming-conventions.md) — `/api/v1/` 경로 컨벤션.
- [platform/testing-strategy.md](../../platform/testing-strategy.md) — 5단 테스트 피라미드 권위.
- [platform/service-types/rest-api.md](../../platform/service-types/rest-api.md) — 수정 대상.

# Related Contracts

- 없음 (service-type spec 정합, 계약 무변경).

---

# Edge Cases

- **`/v1/` 가 의도적 shorthand 였던 경우**: 정규형 명시는 의미를 좁히지 않고 명확화만 하므로 안전 (기존 독자도 `/api/v1/`로 구현해야 정상).
- **identity-platform OAuth 경로**: `/v1/oauth/*`는 OAuth 표준상 `/api/` 무접두가 정상 → rest-api.md 일반 규칙과 별개, 손대지 않음.

# Failure Scenarios

- **기존 문장 변경(non-additive)**: commit 직전 `git diff`로 의미보존 확인.
- **테스트 레이어 중복 오인**: Unit test가 controller-slice와 별개 레이어임을 피라미드 포인터로 명확화.

---

# Definition of Done

- [ ] `rest-api.md` 2개 섹션 수정 (impl PR).
- [ ] `tasks/INDEX.md` 갱신.
- [ ] commit + push (branch `task/mono-310-validate-rules-remediation`).
- [ ] PR open (사용자 요청 시) → merge → close-chore review→done.
