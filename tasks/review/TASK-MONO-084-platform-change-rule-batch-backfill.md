# Task ID

TASK-MONO-084

# Title

`platform/` 14 file `# Change Rule` section backfill — 8 service-types + 6 root policy (refactor-spec all 2026-05-14 platform high)

# Status

review

# Owner

monorepo

# Task Tags

- platform
- spec
- refactor
- governance
- batch

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13~14) platform audit high-1+2 finding closure.

`platform/` 23 file 중 **17 file 이 `# Change Rule` section 보유** (api-gateway-policy / coding-rules / dependency-rules / deployment-policy / error-handling / event-driven-policy / glossary / naming-conventions / observability / ownership-rule / refactoring-policy / repository-structure / security-rules / testing-strategy / versioning-policy / architecture-decision-rule + 직전 TASK-MONO-083 머지 후 contracts/jwt-standard-claims). **14 file 이 누락**:

**service-types (8)**: batch-job / event-consumer / frontend-app / graphql-service / grpc-service / identity-platform / ml-pipeline / rest-api

**root policy (6)**: architecture / entrypoint / lint-remediation-message-standard / object-storage-policy / service-boundaries / shared-library-policy

각 file 이 normative platform rule 을 정의하므로 sibling pattern 일관성 측면에서 `# Change Rule` section 보유 권장. 본 task = 14 file 일괄 backfill.

직접 답습 패턴: TASK-MONO-083 (jwt-standard-claims `# Change Rule` 추가, PR #455 merged 2026-05-13) — 본 task 는 그 batch 확장.

provenance: `/refactor-spec all --dry-run` 2026-05-13~14 platform audit high-1+2 (8 service-types + 6 root policy `# Change Rule` 누락). TASK-MONO-083 의 sibling pattern audit 발견.

---

# Scope

## In Scope

### A. service-types/ 8 file `# Change Rule` 추가

각 file 끝의 sibling section (References 또는 본문 끝) 후에 `# Change Rule` section 1 개 추가. file 별 boilerplate (모두 유사 wording):

> Changes to the mandatory requirements, allowed/forbidden patterns, or testing expectations for `<service-type>` services must be documented in this file before applying to existing services. New constraints affecting deployed services require an ADR (`docs/adr/` for monorepo-wide impact or `projects/<project>/docs/adr/` for project-scoped impact) per `architecture-decision-rule.md`.

해당 8 file:
- `platform/service-types/batch-job.md`
- `platform/service-types/event-consumer.md`
- `platform/service-types/frontend-app.md`
- `platform/service-types/graphql-service.md`
- `platform/service-types/grpc-service.md`
- `platform/service-types/identity-platform.md`
- `platform/service-types/ml-pipeline.md`
- `platform/service-types/rest-api.md`

### B. root policy 6 file `# Change Rule` 추가

각 file 의 scope 에 맞게 customize:

- `platform/architecture.md` — platform-wide architecture baseline (HTTP/Events/microservices). Change: structural pattern, service-tier 추가/제거, cross-service interaction pattern 변경은 본 file 먼저, 그 후 service-level adoption.
- `platform/entrypoint.md` — spec reading order (Core / ServiceType / Auxiliary 3 layer). Change: layer composition, 추가 layer, reading order 변경은 본 file + [`rules/README.md`](../rules/README.md) 동시 update.
- `platform/lint-remediation-message-standard.md` — 4-block remediation template. Change: template 구조, emission contract, rule-id namespace 변경은 본 file + 영향받는 rule (CLAUDE.md HARDSTOP-NN, hook scripts) 동시 update.
- `platform/object-storage-policy.md` — content-heavy trait 의 object storage rule. Change: storage abstraction, lifecycle, 새 backend pattern 추가는 본 file 먼저, 그 후 사용 service.
- `platform/service-boundaries.md` — service 간 책임 boundary. Change: service responsibility, cross-service call pattern, ownership shift 는 본 file + 영향받는 service architecture.md 동시 update.
- `platform/shared-library-policy.md` — `libs/` 사용 정책. Change: forbidden/allowed scope, 새 shared library 카탈로그 entry 는 본 file + ADR (`docs/adr/`) 동반.

### C. 검증

- 14 file 모두 `# Change Rule` section 보유 verify (post-edit grep).
- 다른 platform/*.md 의 cross-ref 영향 없음 (section 추가만, 본문 무변경).
- HARDSTOP-03 hook PASS (project-specific content 잔존 0).

## Out of Scope

- 17 기존 `# Change Rule` file 의 wording 정정 (consistency 측면에서 추가 audit 가치 있으나 본 task scope 밖).
- service-types/INDEX.md `# Change Rule` 검토 (INDEX 자체는 navigation, 별 file 추가 후보).
- contracts/ 디렉토리 의 다른 file (jwt-standard-claims.md 외) 미존재 — N/A.

---

# Acceptance Criteria

### Impl PR

- [x] service-types 8 file 모두 `# Change Rule` section 추가 + common boilerplate body (변수 = `<type>` substitute).
- [x] root policy 6 file 모두 `# Change Rule` section 추가 + file-scope wording (각 file scope customize).
- [x] post-edit grep `^# Change Rule` platform = **31 file** (기존 17 + 신규 14) 검증 완료.
- [x] platform/ 다른 cross-ref 영향 0 (본문 무변경, section append only).
- [x] HARDSTOP-03 hook PASS (project-specific content 잔존 0).
- [ ] CI self-CI PASS (path-filter platform 활성화 — workflows flag full pipeline 회귀 가드).
- [x] task lifecycle ready → review (in-progress 우회, mechanical batch, spec-only single-PR closure 패턴).
- [x] tasks/INDEX.md (root) 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `platform/api-gateway-policy.md` (sibling Change Rule wording reference).
- `platform/event-driven-policy.md` (sibling Change Rule wording reference).
- `platform/architecture-decision-rule.md` (ADR escalation reference).
- `platform/contracts/jwt-standard-claims.md` (직전 TASK-MONO-083 머지 후 보유 — 본 batch 의 첫 entry).
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D1 (B common rule cleanup OVERRIDE scope).

---

# Related Contracts

본 task = platform 내부 governance documentation 보강. HTTP API / event payload 변경 0. cross-service contract 무관.

---

# Target Service

`platform/` shared layer (project-agnostic, 모든 프로젝트 영향).

---

# Architecture

shared platform regulations 의 governance section 일관성 보강. `# Change Rule` section 이 sibling 17 file 에 있고 14 file 에 누락 — sibling pattern 답습.

---

# Implementation Notes

## sibling Change Rule wording 패턴 (17 file)

수집된 패턴 (1-3 sentence, "before applying/implementation/deployment" 강조):

- **api-gateway-policy.md** L162: "Any change to gateway behavior — new filter, new rate limit tier, ... — must be documented in this file ... **before** deployment."
- **coding-rules.md** L82: "Changes to these rules require team agreement and must be updated here before applying."
- **error-handling.md** L588: "New error codes must be added to this document before being used in implementation."
- **security-rules.md** L70-71: "Any deviation from these rules requires an explicit ADR ... before implementation."
- **versioning-policy.md** L63: "API version changes must update the related contract in `specs/contracts/` before implementation."
- **architecture-decision-rule.md** L57-58: "If service architecture must change: ..."
- **observability.md** L98: "New metrics or tracing requirements must be documented here before implementation."
- **glossary.md** L115: "Changes to glossary terms must be documented here before applying across specs. If a term is domain-specific, it belongs in `rules/domains/<domain>.md` — not this file."

## 8 service-types 의 공통 wording (boilerplate)

```markdown
# Change Rule

Changes to the mandatory requirements, allowed/forbidden patterns, or testing expectations for `<type>` services must be documented in this file before applying to existing services. New constraints affecting deployed services require an ADR (`docs/adr/` for monorepo-wide impact or `projects/<project>/docs/adr/` for project-scoped impact) per `architecture-decision-rule.md`.
```

각 file 의 `<type>` 만 substitute.

## 6 root policy 의 file-scope wording

위 § Scope B 참조 — file 마다 1 paragraph customize.

## Insertion position

각 file 의 마지막 정상 section (보통 References 또는 본문 끝) 다음. sibling 들의 위치 (api-gateway L160 = 본문 중간, error-handling L586 = 본문 끝, observability L96 = 본문 끝) 가 일관적이지 않음. 본 task = **본문 끝** 으로 통일 (대부분 sibling 패턴).

## D4 churn impact

- 14 file platform/ touch.
- ADR-MONO-003a § D1.1 IN-scope (B common rule cleanup 연장선) — D4 OVERRIDE 적용.
- structural 변경 아니라 governance 보강 (section 1개 추가, body 무변경) → D2 시계 재시작 영향 약함.
- 직전 TASK-MONO-083 (jwt-standard-claims `# Change Rule` 추가, 같은 batch 의 첫 entry) 도 동일 OVERRIDE 적용 precedent.

---

# Edge Cases

- 일부 service-types file (identity-platform, ml-pipeline) 가 sibling 보다 큰 (18 H1 / 14 H1) — Change Rule section 추가 위치는 동일 (본문 끝).
- service-types/INDEX.md 는 navigation 용도 — Change Rule section 추가 후보 별 task (본 task scope 밖).
- root policy 중 entrypoint.md 는 navigation 성격 강해 Change Rule wording 이 다른 file 보다 더 짧을 가능성.

---

# Failure Scenarios

- Change Rule wording 이 sibling 과 너무 어긋남 → consistency audit fail. 본 task = sibling 패턴 답습 강제.
- 일부 file 에서 section 추가가 outline depth 깨뜨림 → markdown lint 영향. spot-check 필수.

---

# Test Requirements

- HARDSTOP-03 hook PASS (project-specific content 잔존 0).
- CI self-CI PASS (workflows flag full pipeline 회귀 가드, 14 file platform 변경이라 path-filter platform 활성화 자연 trigger).
- post-edit grep `^# Change Rule` platform = 31 file (기존 17 + 14 신규).
- production code = 0 (spec only).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13~14 platform audit high-1+2 (8 service-types + 6 root policy `# Change Rule` 누락 finding).
- TASK-MONO-083 (PR #455 머지 2026-05-13, jwt-standard-claims `# Change Rule` 추가) 의 batch 확장.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (B common rule cleanup 연장선) 적용.
- Sibling 답습: TASK-BE-280 / TASK-BE-281 / TASK-SCM-BE-011 / TASK-MONO-083 의 same-day single-PR closure 패턴.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical batch, 14 file × ~3 line 추가).
