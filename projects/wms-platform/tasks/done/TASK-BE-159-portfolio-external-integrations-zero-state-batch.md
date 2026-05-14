# Task ID

TASK-BE-159

# Title

portfolio-wide external-integrations.md gap **완전 종결** — master + admin + gateway zero-state 3-service batch (BE-156 패턴 답습)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- master-service
- admin-service
- gateway-service
- spec
- backfill
- integration-heavy
- be

---

# Goal

[TASK-BE-156](../done/TASK-BE-156-inventory-service-external-integrations-zero-state.md) (inventory zero-state) + [TASK-BE-158](../done/TASK-BE-158-notification-service-external-integrations.md) (notification Slack marquee) 의 portfolio-wide gap audit (메모리 surfaced) 결과 wms 7 service 중 **external-integrations.md 잔존 gap = 3 service** (master + admin + gateway, **모두 외부 vendor 0 라 zero-state 답습 가능**). 본 task 가 batch 단일 PR 으로 3 file 동시 closure → **portfolio external-integrations.md gap 완전 종결**.

3 service 모두 외부 vendor 0 인 근거 (overview.md 정찰 결과):

- **master-service** = `rest-api` (upstream anchor). § Dependent Systems = PostgreSQL + Kafka 만 (`no outbound dependency`). § Out of scope v1 = ERP / PIM sync adapters (v2 placeholder).
- **admin-service** = `rest-api` + `event-consumer` (dual, CQRS read-side, Layered exception). § Dependent Systems = PostgreSQL + Kafka + `gateway-service` (JWT validation 참조). § Out of scope = external IdP / SAML / SCIM (v2).
- **gateway-service** = `rest-api` (edge gateway, Spring Cloud Gateway WebFlux, Layered, no domain). § Dependent Systems = 5 downstream sibling + Redis (ephemeral rate-limit) + OAuth2 Authorization Server (JWT public keys / JWKS). OAuth2 AS = `gap-platform` sibling project — **project-internal infrastructure 분류** (inventory zero-state § Internal-vs-External Boundary 답습).

본 task = **3 신규 spec file 동시 authoring** (production code 0, schema 0, application.yml 0, markdown only). BE-156 inventory zero-state pattern (97 line, 7 section: 헤더 / Catalog Summary vendor 0 / Why Zero / Internal-vs-External Boundary / Required-Artifact Compliance Map / Evolution Paths / References) 답습 batch.

PR 횟수 감축 목표 (`feedback_pr_bundling.md` 메모리) — 3 service single-PR closure 패턴. INDEX.md done entry 1 줄 + 3 file `New: ` 형식.

---

# Scope

## In Scope

### A. `master-service/external-integrations.md` (zero-state)

대상: `projects/wms-platform/specs/services/master-service/external-integrations.md` (신규).

content 차별점 (inventory zero-state 답습 + master-specific):
- master = **upstream anchor** — 다른 service 가 master 의 event 를 consume; master 는 outbound dependency 0.
- v2 evolution: ERP / PIM sync adapters (overview § Out of scope v1 명시) — 외부 master data 시스템과 sync 발생 시 진입.

예상 크기: ~80-100 line.

### B. `admin-service/external-integrations.md` (zero-state)

대상: `projects/wms-platform/specs/services/admin-service/external-integrations.md` (신규).

content 차별점:
- admin = **CQRS read-side** projector + user/role/settings authoritative. § Dependent Systems = PostgreSQL + Kafka (5 sibling consume + own publish) + `gateway-service` (JWT validation 참조, internal sibling).
- v2 evolution: external IdP (SAML / SCIM), notification preference UI, real-time stream KPI.
- Layered architecture exception 명시 (read-heavy CQRS rationale 인용, architecture.md cross-link).

예상 크기: ~90-110 line.

### C. `gateway-service/external-integrations.md` (zero-state with OAuth2 AS nuance)

대상: `projects/wms-platform/specs/services/gateway-service/external-integrations.md` (신규).

content 차별점:
- gateway = **edge gateway** (Spring Cloud Gateway WebFlux). 모든 외부 client traffic 이 통과하지만 gateway 자체는 외부 vendor 직접 호출 안 함.
- **OAuth2 Authorization Server (JWT JWKS) 정확한 분류 명시** — `gap-platform` (sibling project) 가 OAuth2 AS owner. WMS deploy 시 GAP 가 internal infrastructure 로 동거 (monorepo deployment) — inventory § Internal-vs-External Boundary 답습 + 추가로 "AS 가 external SaaS (Auth0 / Cognito 등) 로 swap 되면 본 file 이 zero-state 종료" v2 placeholder.
- **Redis** (rate-limit counters, ephemeral) = infrastructure 분류.
- **downstream 5 sibling** (master/inventory/inbound/outbound/admin) = internal service, not external vendor.
- v2 evolution: 다중 IdP (SAML / SCIM), 외부 SaaS IdP swap.

예상 크기: ~90-110 line.

### D. INDEX.md done entry (1 줄, 3 service inline)

3 file 동시 authoring 을 INDEX done entry 1줄로 명시. BE-156 single-service entry 와 달리 batch 명시 + portfolio gap 종결 fact 포함.

## Out of Scope

- database-design.md authoring (BE-157 답습) — portfolio-wide 5 service 잔존 gap, 별 task (BE-160~164 batch 또는 single 후보).
- 3 service 의 architecture.md / domain-model.md / idempotency.md 변경 0 — § Open Items 정정 대상 0 (inventory 와 달리 § Open Items 에 external-integrations.md outstanding 명시 없음, 확인 의무 — 부재 시 변경 없음).
- 외부 vendor 진짜 0 검증을 위해 각 service 의 `apps/<svc>/src/main/java/.../adapter/out/` 정찰 — code-level vendor adapter 있다면 zero-state 가정 무효. 본 task 시작 시 검증 의무.
- shared path 진입 0 (`platform/` / `rules/` / `libs/` / `.claude/` 모두 변경 0).

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/master-service/external-integrations.md` 신규 file 존재, 80-100 line, 7 section 구조 (BE-156 답습).
- [ ] `projects/wms-platform/specs/services/admin-service/external-integrations.md` 신규 file 존재, 90-110 line, 7 section 구조 (Layered architecture exception 명시).
- [ ] `projects/wms-platform/specs/services/gateway-service/external-integrations.md` 신규 file 존재, 90-110 line, 7 section 구조 (OAuth2 AS = gap-platform sibling = infrastructure 분류 명시).
- [ ] 3 file 모두 Required-Artifact Compliance Map (1-6) table 포함 + zero-state N/A 명시.
- [ ] 3 file 모두 Evolution Paths section + v2 placeholder (master: ERP/PIM sync / admin: external IdP / gateway: external SaaS IdP swap).
- [ ] 3 file 모두 sibling reference (inbound + outbound + inventory + notification external-integrations.md) cross-link.
- [ ] 모든 cross-reference (~10-12개 per file) `ls` 으로 실재 확인.
- [ ] **외부 vendor 진짜 0 사전 검증** — `find projects/wms-platform/apps/<master|admin|gateway>-service/src/main/java -path "*/adapter/out*" -type d` 결과 audit 후 vendor adapter 부재 또는 internal-only 확인.
- [ ] HARDSTOP-03 PASS — 3 file 모두 wms-specific (project-internal).
- [ ] grep `(Open Item` projects/wms-platform/specs/services/{master,admin,gateway}-service/ → 0 (regression 0 / 신규 마커 신설 X).
- [ ] production code 변경 = 0 (git diff `--stat` 으로 markdown only).
- [ ] CI = path-filter (TASK-MONO-074/075) → markdown-only wms-spec → ~1 PASS + 15 SKIP 예상.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (`domain: wms`, `traits: [transactional, integration-heavy]`), then load `rules/common.md` + `rules/domains/wms.md` + `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`. 3 service 각 Service Type:
> - master: `rest-api`
> - admin: `rest-api` + `event-consumer` (dual, Layered exception)
> - gateway: `rest-api` (edge gateway role)

- `rules/traits/integration-heavy.md` — Required Artifacts (1) zero-state 적용
- `projects/wms-platform/specs/services/master-service/{overview,architecture,domain-model,idempotency}.md` — § Dependent Systems / § Out of scope v1
- `projects/wms-platform/specs/services/admin-service/{overview,architecture,domain-model,idempotency}.md` — CQRS read-side rationale + § Dependent Systems
- `projects/wms-platform/specs/services/gateway-service/{overview,architecture}.md` — edge gateway / OAuth2 AS / Redis ephemeral
- `projects/wms-platform/specs/services/inventory-service/external-integrations.md` — primary sibling answering pattern (BE-156, 97 line)
- `projects/wms-platform/specs/services/inbound-service/external-integrations.md` — non-zero sibling (ERP webhook reference)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` — non-zero sibling (TMS marquee)
- `projects/wms-platform/specs/services/notification-service/external-integrations.md` — non-zero sibling (Slack marquee, BE-158)
- `platform/api-gateway-policy.md` — gateway routing tier (gateway file ref)
- `platform/security-rules.md` — OAuth2 AS / Secret Manager policy
- `platform/observability.md` — required metrics

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- N/A — 3 file 모두 spec authoring only, contract 변경 0.

---

# Target Service

- `master-service`
- `admin-service`
- `gateway-service`

---

# Edge Cases

1. **gateway-service OAuth2 AS 분류** — JWT 검증을 위해 JWKS endpoint 호출이 발생. AS 가 `gap-platform` sibling project (monorepo deploy) 이면 infrastructure, 외부 SaaS IdP 이면 vendor. v1 wms 배포 시점에서는 GAP sibling 으로 동거 → infrastructure 분류. spec 본문에 "AS swap 시점에 zero-state 종료" v2 placeholder 명시.
2. **admin-service 의 `gateway-service` 의존** — overview.md § Dependent Systems 에 "gateway-service — JWT validation references user records (read-only)" 명시. 그러나 admin 이 gateway 를 호출하는 게 아니라 gateway 가 admin user record 를 조회 (or admin event 를 consume) 하는 구조 → admin 입장에서 outbound dependency 0. 본 file 의 § Internal vs External 에 cross-link 패턴 명시.
3. **external vendor 0 검증 fail-safe** — 사전 정찰 (`find adapter/out`) 에서 vendor adapter 가 있다면 zero-state 가정 무효, scope 변경 필요. 본 task 시작 시 의무 검증 (Acceptance Criteria 항목).
4. **3 file 동시 작성으로 inline 답습 drift** — file 별 service-specific 차별점 보존 필요 (master upstream anchor / admin CQRS read-side Layered exception / gateway edge OAuth2 nuance). 답습 cost 절감 위해 boilerplate 공통 부분은 sibling 답습 명시 cross-link.
5. **HARDSTOP-10 hook trigger 가능성** — external-integrations.md 는 architecture.md 와 달리 hook 검사 대상 아닐 가능성 (이미 BE-156 + BE-158 이 trigger 0 PASS). 그러나 file 작성 후 첫 Edit 으로 검증.

---

# Failure Scenarios

1. **사전 정찰 누락으로 vendor adapter 발견** — 작성 후에 발견 시 zero-state 가정 무효 → 별 task 로 swap. 본 task 시작 시 의무 검증.
2. **3 file 답습 drift** — boilerplate 부분 (Catalog Summary 표 header / Required-Artifact Compliance Map 6 row / sibling references 패턴) 가 file 별 미세 차이로 inconsistency 발생 가능. 작성 후 각 file pair 비교 검증.
3. **gateway-service OAuth2 AS 분류 contested** — reviewer 가 "AS 는 external vendor 분류해야 한다" 입장이면 zero-state 가정 영향. inventory § Internal-vs-External Boundary 답습 + ADR-MONO-007 또는 architecture-decision-rule 인용으로 분류 root 확립. v2 SaaS swap placeholder 가 reviewer concern 흡수.
4. **CRLF vs LF on Windows** — BE-156/157/158 답습 (LF 작성). hook 검증.
5. **path-filter (TASK-MONO-074/075) markdown-only PASS** — 정상 (BE-156/157/158 답습).

---

# Validation Plan

- `find projects/wms-platform/apps/{master,admin,gateway}-service/src/main/java -path "*/adapter/out*" -type d` → vendor adapter 부재 또는 internal-only 사전 검증.
- `ls projects/wms-platform/specs/services/{master,admin,gateway}-service/external-integrations.md` → 3 file 존재 확인.
- `wc -l` per file → 80-110 line 범위 확인.
- 각 file 의 sibling reference path resolution (`ls`) — depth 4 (`../<service>-service/external-integrations.md`) + 5-up (`../../../../../rules/traits/integration-heavy.md`).
- `grep -rn "(Open Item" projects/wms-platform/specs/services/{master,admin,gateway}-service/` → 0 (regression 0).
- CI = path-filter → ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~158 single-PR closure 답습 — ready → done (batch 단일 PR).
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio external-integrations.md gap 완전 종결** = BE-156 (inventory) + BE-158 (notification) + BE-159 (master + admin + gateway) 누적 5 service authoring 종결.
- **잔존 portfolio-wide gap** = database-design.md 5 service (master/inbound/outbound/admin/notification; gateway N/A schemaless) — BE-157 답습 batch 또는 single 별 task 후보.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (3 file 동시 zero-state + service-specific 차별점 정밀 보존 + sibling pattern 답습 batch).
