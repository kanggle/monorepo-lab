# Task ID

TASK-MONO-083

# Title

`platform/contracts/jwt-standard-claims.md` HARDSTOP-03 cleanup + heading style + orphan fix (refactor-spec all 2026-05-13 critical finding)

# Status

done

# Owner

monorepo

# Task Tags

- platform
- spec
- refactor
- hardstop
- fix

---

# Goal

`/refactor-spec all --dry-run` (2026-05-13) 의 platform audit 결과 **`platform/contracts/jwt-standard-claims.md`** 가 HARDSTOP-03 risk 노출.

문제 3종 결합:

1. **HARDSTOP-03 candidate** — line 5/12/38/50/59 에 project name **"Global Account Platform"** 명시. CLAUDE.md HARDSTOP-03 forbids project-specific content in shared paths (`platform/` 포함). "Global Account Platform" 은 5 active projects 중 하나 (`docs/project-overview.md` § 2.1).
2. **Heading style divergence** — 모든 platform/*.md 파일이 `# H1` 을 top section 으로 사용. 본 파일만 `## H2` 11회 사용 (Account Types / JWT Signing Strategy / Standard Claims / Role Strategy / SSO / Gateway Enforcement / JWKS / Token Examples / Implementation Notes / References).
3. **Double-orphan** — `platform/README.md` "What Lives Here" 표 + `rules/common.md` 14-file index 둘 다 본 파일 미명시. `platform/entrypoint.md`, `security-rules.md`, `api-gateway-policy.md` 도 reference 안 함. 사실상 platform navigation surface 에서 lost.

fix path:
- Rename project-specific terms → generic: "the identity-platform service" 또는 "the central identity service" 사용 (catalog name 활용, `iss` claim 예제는 이미 generic `https://account.example.com` 사용 중이라 일관성 적용 가능).
- `##` → `#` 변환 (top section level 정렬).
- Missing `# Change Rule` 추가 (sibling pattern).
- `platform/README.md` + `rules/common.md` 에 본 파일 entry 추가 (orphan 해소).

provenance: `/refactor-spec all --dry-run` 2026-05-13 Platform audit Top 1 finding (highest-impact). HARDSTOP-03 enforcement.

---

# Scope

## In Scope

### A. `platform/contracts/jwt-standard-claims.md` 본문 정정

1. project name `"Global Account Platform"` 5 site → "the identity-platform service" 또는 generic equivalent. CLAUDE.md HARDSTOP-03 준수.
2. heading hierarchy `## H2` → `# H1` (11 section). other platform/*.md 와 정렬.
3. metadata header (`**Status:** Established` / `**Audience:**` / `**Authority:**` / `**Effective Date:**`) 형식 검토 — platform/ 의 standard policy file 들은 frontmatter 또는 standalone header 사용 안 함. 형식 통일 또는 제거.
4. missing `# Change Rule` section 추가 (sibling 16 root policies 모두 보유).

### B. orphan 해소

1. `platform/README.md` "What Lives Here" 표 또는 별도 § 에 `contracts/jwt-standard-claims.md` 추가.
2. `rules/common.md` 의 14-file index 또는 별도 § 에 cross-reference 추가.

### C. 영향 검증

- HARDSTOP-03 hook 통과 (renaming 이후).
- 본 파일 인용하는 11 file (project gap-integration / identity-platform-setup skill / monorepo task done) 의 link 텍스트 영향 검증 — 본문 내용 정정만, file path 무변경 이므로 안전.

## Out of Scope

- 본 파일 의 contract 본질 (account types / signing strategy / claims) 변경.
- 다른 platform/ 파일의 heading style 일제 변환 (본 파일이 outlier).
- identity-platform service 자체의 implementation 변경.
- 11 consumer file (gap-integration / setup skill 등) 의 content 변경.

---

# Acceptance Criteria

### Impl PR

- [x] `platform/contracts/jwt-standard-claims.md` 의 5 "Global Account Platform" → generic 명명 ("the identity-platform service" / "the identity service").
- [x] heading hierarchy `## H2` 11 site → `# H1` (top H1 1개 + 변환된 11 H1 = 12 H1 / H3 13 → H2 13 동반 shift 로 outline depth 정렬).
- [x] missing `# Change Rule` section 추가 (References 직전).
- [x] metadata header (`**Status:** Established` / `**Audience:**` / `**Authority:**` / `**Effective Date:**`) 제거 — sibling 16 root policies 가 모두 metadata header 미사용 → 통일.
- [x] `platform/README.md` 에 본 파일 entry 추가 + `contracts/` directory 자체 row 동반.
- [x] `rules/common.md` 에 § "Platform Contracts" 신설 + cross-reference 추가.
- [x] HARDSTOP-03 hook PASS — `platform/` 전체 "Global Account Platform" grep 0 hit.
- [ ] CI self-CI 16/16 PASS (workflows flag full pipeline 회귀 가드).
- [x] task lifecycle ready → in-progress → review.
- [x] root INDEX 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (수정 대상).
- `platform/README.md` (orphan 해소 대상).
- `rules/common.md` (orphan 해소 대상).
- `platform/service-types/identity-platform.md` (대체 terminology source).
- CLAUDE.md § Hard Stop Rules § HARDSTOP-03 (정책 source).
- `docs/project-overview.md` § 2.1 (5 active projects, "Global Account Platform" 의 project 식별 source).
- `/refactor-spec all --dry-run` 2026-05-13 Platform audit (Top 1 finding source).

# Related Skills

`.claude/skills/service-types/identity-platform-setup/SKILL.md` (catalog reference).

---

# Related Contracts

본 task 가 contract spec 자체 수정. HTTP API / event payload 변경 0 (terminology + heading 만).

---

# Target Service

`platform/` shared layer (project-agnostic).

---

# Architecture

shared platform regulations. HARDSTOP-03 forbidding project-specific content in shared paths.

---

# Implementation Notes

## Terminology mapping 후보

- "Global Account Platform" → "the identity-platform service" (catalog name)
- "Global Account Platform implements..." → "An identity-platform service implements..."
- "GAP" (만약 file 에 있다면) → "identity-platform"

`iss` claim 예제는 이미 `https://account.example.com` generic 사용 → 무변경.

## platform/README.md 추가 위치

"What Lives Here" 표 (lines 13-33) 가 top-level files + `service-types/` enumerate. `contracts/jwt-standard-claims.md` 도 추가 row + `contracts/` directory 자체 entry (현재 누락).

## rules/common.md 추가 위치

14-file index 의 적절한 위치 (예: § "Platform contracts" subsection 신설 또는 existing security-related § 에 추가).

---

# Edge Cases

- 5 project 중 GAP 외의 4 project (wms/ecommerce/scm/fan) 가 본 spec 을 참조 시 동일 generic 명명 사용 (이미 대부분 generic).
- "GAP" 약어가 본 file 에 별도 expand 없이 사용된 경우: 본 task 에서 처리.
- 다른 platform/*.md 가 "Global Account Platform" 인용 시: 별도 audit (본 task scope 밖).

---

# Failure Scenarios

- HARDSTOP-03 hook 이 renaming 후에도 trigger: 추가 project-specific term 잔존 검증 + cleanup.
- 11 consumer file 의 link text 가 본 spec 의 specific term 인용: 본 task 에서 cross-reference scan + 별도 fix 권장.
- Heading hierarchy 변환 후 outline-style markdown lint regression: lint check + 정정.

---

# Test Requirements

- HARDSTOP-03 hook PASS.
- CI self-CI 16/16 PASS.
- 본 file 의 linked-from 11 file 의 outbound link 정상 (file path 무변경).
- Production code 0.

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → in-progress → review.

### Close chore PR

- [ ] review → done, INDEX 동기.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-13 Platform audit (33 file / 43 finding) Top 1 risk-weighted finding.
- HARDSTOP-03 (Shared library file contains project-specific content) enforcement.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical rename + heading conversion + 2 file orphan resolution).
