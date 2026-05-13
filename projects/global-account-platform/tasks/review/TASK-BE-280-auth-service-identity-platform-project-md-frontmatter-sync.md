# Task ID

TASK-BE-280

# Title

`auth-service` Service Type `identity-platform` vs `PROJECT.md` frontmatter mismatch (HARDSTOP-02/10 risk, refactor-spec critical finding)

# Status

review

# Owner

global-account-platform

# Task Tags

- be
- auth-service
- spec
- hardstop
- fix

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13) GAP audit 결과 **`auth-service/architecture.md` Service Type 선언과 PROJECT.md frontmatter 의 mismatch**. HARDSTOP-02 또는 HARDSTOP-10 risk:

- `projects/global-account-platform/specs/services/auth-service/architecture.md:9` 가 **Service Type = `identity-platform`** 선언 (2026-05-11 revision per declaration history at line 13).
- `projects/global-account-platform/PROJECT.md:5` frontmatter 의 `service_types: [rest-api, event-consumer, frontend-app]` 에 `identity-platform` 미포함.
- `platform/service-types/identity-platform.md` 카탈로그에는 존재.

CLAUDE.md HARDSTOP-02 ("PROJECT.md 의 declared `domain`/`trait` 가 taxonomy 미정의") 와 HARDSTOP-10 ("Service Type 미선언/카탈로그 미존재") 의 경계 case. 둘 다 fail-fast 의도이므로 사전 cleanup 필요.

fix path: PROJECT.md frontmatter 의 `service_types` 에 `identity-platform` 추가. 1-line 변경.

또 admin-service 검토 — admin-service 가 self-issuing IdP (admin-service/architecture.md:125-178) 이지만 service_type 은 여전히 `rest-api`. catalog 의 `identity-platform.md` 적용 가능성 검토 (별도 audit).

provenance: refactor-spec audit Top 3 critical finding. HARDSTOP-02/10 prevention.

---

# Scope

## In Scope

### A. `PROJECT.md` frontmatter `service_types` 갱신

`projects/global-account-platform/PROJECT.md:5` 의 `service_types` array 에 `identity-platform` 추가.

```yaml
# before
service_types: [rest-api, event-consumer, frontend-app]

# after
service_types: [rest-api, event-consumer, frontend-app, identity-platform]
```

### B. PROJECT.md body 의 service-type breakdown 갱신

PROJECT.md 본문이 service 별 type 매핑 표를 포함 시 (확인 필요) `auth-service: identity-platform` 명시.

### C. 영향 검증

- HARDSTOP-02/10 hook 통과.
- `platform/service-types/identity-platform.md` 카탈로그가 존재 + reachable (`platform/service-types/INDEX.md` 명시) 검증.

## Out of Scope

- `auth-service/architecture.md` 본문 변경 (이미 `identity-platform` 선언, 정확).
- admin-service Service Type 변경 (별도 audit task 후보 — `rest-api` vs `identity-platform` 검토).
- `platform/service-types/identity-platform.md` 카탈로그 본문 변경.
- 다른 5 service (account/community/membership/security/gateway) frontmatter 변경.

---

# Acceptance Criteria

### Impl PR

- [ ] `projects/global-account-platform/PROJECT.md` frontmatter `service_types` 에 `identity-platform` 추가.
- [ ] PROJECT.md body 의 service-type 매핑 표 (있다면) 동기.
- [ ] HARDSTOP-02/10 hook PASS.
- [ ] CI self-CI 16/16 PASS (gap project flag 발동, GAP Integration / e2e smoke 등).
- [ ] PR-time `Integration (global-account-platform)` PASS (Testcontainers IT 무영향).
- [ ] task lifecycle ready → in-progress → review.
- [ ] GAP tasks/INDEX.md 동기.

### Close chore PR

- [ ] review → done, gap tasks/INDEX.md 동기.

---

# Related Specs

- `projects/global-account-platform/PROJECT.md` (수정 대상, frontmatter line 5).
- `projects/global-account-platform/specs/services/auth-service/architecture.md` (Service Type declaration line 9).
- `platform/service-types/identity-platform.md` (카탈로그 source).
- `platform/service-types/INDEX.md` (8 service type 등록 확인).
- CLAUDE.md § Hard Stop Rules § HARDSTOP-02 / HARDSTOP-10.
- `/refactor-spec all --dry-run` 2026-05-13 GAP audit Top 3 critical finding.

# Related Skills

`.claude/skills/service-types/identity-platform-setup/SKILL.md`.

---

# Related Contracts

None — frontmatter spec only.

---

# Target Service

`auth-service` (Service Type 선언 source).

---

# Architecture

GAP project frontmatter + service-type catalog alignment.

---

# Implementation Notes

## HARDSTOP-02 vs HARDSTOP-10 구분

- HARDSTOP-02: `PROJECT.md` declare 한 `domain`/`trait` 이 taxonomy 미정의 — 본 case 는 `service_types` 가 missing entry (declare 와 service spec mismatch).
- HARDSTOP-10: Service Type 미선언 또는 카탈로그 미존재 — 카탈로그 존재 (`identity-platform.md`) + service spec 명시 (`identity-platform`) 이지만 PROJECT.md frontmatter manifest 누락.

본 case 는 두 rule 의 경계: PROJECT.md frontmatter 가 source of truth 인지 service architecture.md 가 source of truth 인지에 따라 다름. `rules/README.md` 의 Routing Layer 룰 따라 PROJECT.md frontmatter 가 authoritative manifest → fix path = frontmatter 갱신.

## admin-service 검토 (별도 task 후보)

admin-service 가 self-issuing IdP (per `admin-service/architecture.md § Admin IdP Boundary` lines 125-178) 이고 RS256 token 자체 발급. `platform/service-types/identity-platform.md` 카탈로그가 admin 의 dual-role (admin operations REST + IdP) 적용 적절성 검토 필요. 본 task scope 밖, follow-up audit task 후보.

---

# Edge Cases

- `service_types` 가 PROJECT.md frontmatter 외 다른 위치 (body table) 에서 enumerate 되는 경우: 두 곳 모두 동기.
- 향후 `community-service`/`membership-service` 가 identity-related upgrade 시: 동일 패턴 적용.
- `rules/taxonomy.md` 의 service_types 명시 검증 (separate from frontmatter).

---

# Failure Scenarios

- frontmatter parser 가 trailing comma / array syntax error 로 fail: yaml lint 검증.
- HARDSTOP-02/10 hook 이 추가 mismatch 발견: separate audit task.
- 본 fix 후 admin-service 도 `identity-platform` 으로 promote 결정: 별도 spec PR.

---

# Test Requirements

- HARDSTOP-02/10 hook PASS.
- CI self-CI 16/16 PASS.
- Production code 0.
- PR-time GAP Integration test 동일 PASS (Testcontainers MySQL 무관).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, gap tasks/INDEX.md 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13 GAP audit (84 file / 60 finding) Top 3 risk-weighted finding (HARDSTOP-02/10 candidate).
- `auth-service/architecture.md` line 9 + line 13 declaration history (2026-05-11 revision to `identity-platform`).
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (1-line frontmatter edit + verify).
