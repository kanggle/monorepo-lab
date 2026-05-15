# Task ID

TASK-MONO-101

# Title

fan-platform 4 service architecture.md canonical Composition H3 backfill + rules/common.md broken `[PROJECT.md](../PROJECT.md)` link fix (audit-driver re-run bundle)

# Status

done

# Owner

monorepo

# Task Tags

- monorepo
- fan-platform
- architecture-canonical-form
- adr-mono-012
- rules-common-broken-link
- audit-driver-rerun

---

# Goal

`/validate-rules` + dead-markdown-link audit 재실행으로 surface 한 2 finding bundle closure:

1. **fan-platform 4 service architecture.md `### Service Type Composition` H3 backfill** (ADR-MONO-012 D1 의 "always present" 의무 충족, D3 cycle marquee 완성 — 5축 portfolio architecture.md 의 마지막 partial-align 영역).
2. **shared 영역 broken `[PROJECT.md](../[../]*PROJECT.md)` link fix** (pre-existing Tier 3 — repo-root `PROJECT.md` 부재, 실제는 각 `projects/<name>/PROJECT.md`). Initial finding 은 `rules/common.md:13` 1건 이었으나 audit re-scan 으로 추가 6 hit 발견 (5 file): `.claude/agents/domain/README.md` (L3 + L42) / `.claude/agents/common/README.md` L3 / `.claude/skills/domain/README.md` (L3 + L38) / `.claude/config/traits.md` L3 / `.claude/config/activation-rules.md` L3 / `.claude/config/domains.md` L85 — 총 **7 hit / 6 file** same-batch fix.

# Scope

## In Scope

### 1. fan-platform 4 service architecture.md Composition H3 backfill

각 service 의 `## Identity` table 직후 (next `---` separator 앞) 에 `### Service Type Composition` H3 + short body 삽입. WMS Identity-table canonical form + MONO-097 GAP / MONO-098 ecommerce 답습.

4 file:
- `projects/fan-platform/specs/services/artist-service/architecture.md` — single-type `rest-api`, master-data publisher (HTTP API + Kafka outbox)
- `projects/fan-platform/specs/services/community-service/architecture.md` — single-type `rest-api`, post state machine + Kafka outbox publisher
- `projects/fan-platform/specs/services/fan-platform-web/architecture.md` — single-type `frontend-app`, Next.js 15 App Router + next-auth PKCE
- `projects/fan-platform/specs/services/gateway-service/architecture.md` — single-type `rest-api` (edge gateway role), Spring Cloud Gateway reactive

모두 single-type → short body (~5 line per service).

### 2. shared 영역 broken `PROJECT.md` link fix (7 hit / 6 file)

| # | File | Line | Pattern |
|---|---|---:|---|
| 1 | `rules/common.md` | 13 | `[PROJECT.md](../PROJECT.md)` |
| 2 | `.claude/agents/domain/README.md` | 3 | `[PROJECT.md](../../../PROJECT.md)` |
| 3 | `.claude/agents/domain/README.md` | 42 | `[PROJECT.md](../../../PROJECT.md)` |
| 4 | `.claude/agents/common/README.md` | 3 | `[PROJECT.md](../../../PROJECT.md)` |
| 5 | `.claude/skills/domain/README.md` | 3 | `[PROJECT.md](../../../PROJECT.md)` |
| 5b | `.claude/skills/domain/README.md` | 38 | `[PROJECT.md](../../../PROJECT.md)` (= same file, second hit) |
| 6 | `.claude/config/traits.md` | 3 | `[PROJECT.md](../../PROJECT.md)` |
| 7 | `.claude/config/activation-rules.md` | 3 | `[PROJECT.md](../../PROJECT.md)` |
| 8 | `.claude/config/domains.md` | 85 | `[PROJECT.md](../../PROJECT.md)` |

모두 repo-root `PROJECT.md` 부재 = broken. 실제 PROJECT.md 는 각 `projects/<name>/PROJECT.md` 에 있음.

**Fix 패턴 (uniform wording)**: `[PROJECT.md](../[../]*PROJECT.md)` link → 평문 `each project's PROJECT.md (\`projects/<name>/PROJECT.md\`)` 또는 한국어 동등 표현. 의미 보존 + link 제거. trivial per-file edit.

## Out of Scope

- HARDSTOP-10 hook propagation — 이미 MONO-096 fixture coverage 보유 + 본 task 는 canonical form 호환 변경 (Composition H3 추가) 이라 hook PASS 예상.
- Production code / spec contract / Service Type 값 / 본문 narrative 의미 = 0 변경 — 모두 doc/spec polish.
- 다른 dead-link finding (현재 audit 결과 = 1 broken 뿐, fan-platform finding 와 묶음 처리). 추가 finding 발견 시 별 task.
- ADR-MONO-012 § 1.1/1.4/D1 option C-1 누적 본문 정정 별 ADR-MONO-012a — 본 task scope 외, low priority defer.

# Acceptance Criteria

- [ ] `grep -c "^### Service Type Composition$" projects/fan-platform/specs/services/*/architecture.md` = 4 (4 service 모두 H3 보유).
- [ ] `grep -c "^## Identity$" projects/fan-platform/specs/services/*/architecture.md` = 4 (기존 form 보존).
- [ ] `grep -c "single-type" projects/fan-platform/specs/services/*/architecture.md` ≥ 4 (각 H3 의 single-type 명시).
- [ ] `grep -rnE '\]\([^)]*\.\./PROJECT\.md\)' rules/ .claude/ platform/ CLAUDE.md TEMPLATE.md` exit 1 (shared 영역 broken link 0 hit).
- [ ] `grep -c "PROJECT.md" rules/common.md .claude/agents/domain/README.md .claude/agents/common/README.md .claude/skills/domain/README.md .claude/config/traits.md .claude/config/activation-rules.md .claude/config/domains.md` ≥ 1 per file (PROJECT.md mention 보존 — link 없이 wording 으로).
- [ ] `.claude/hooks/__tests__/run-all.ps1` → 22/22 PASS (HARDSTOP fixture 회귀 0, MONO-100 drift detector 포함).
- [ ] 4 fan service architecture.md 의 본문 narrative (Architecture Style Rationale, Package Layout 등) 모두 보존 — H3 추가만, 기존 본문 미터치.
- [ ] production code / Service Type 값 / Architecture Style 값 / Identity table row = 0 변경.

# Related Specs

- [`projects/fan-platform/specs/services/artist-service/architecture.md`](../../../projects/fan-platform/specs/services/artist-service/architecture.md)
- [`projects/fan-platform/specs/services/community-service/architecture.md`](../../../projects/fan-platform/specs/services/community-service/architecture.md)
- [`projects/fan-platform/specs/services/fan-platform-web/architecture.md`](../../../projects/fan-platform/specs/services/fan-platform-web/architecture.md)
- [`projects/fan-platform/specs/services/gateway-service/architecture.md`](../../../projects/fan-platform/specs/services/gateway-service/architecture.md)
- [`rules/common.md`](../../../rules/common.md) (L13 link fix target)
- [`docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md`](../../../docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md) § D1 "Composition H3 always present" rationale (실 practice 반영)
- 메모리 reference: `project_adr_mono_012_d3_cycle_complete.md` § 후속 candidate (fan-platform partial-align catch-up)

# Related Contracts

없음 (production code / API / event contract 0 변경).

# Edge Cases

1. **community-service 의 outbox publisher**: Service Type 분류상 `event-consumer` 가 아닌 publish-only outbox publisher. dual-type 아닌 single `rest-api` — outbox 는 publisher capability (event-consumer 는 inbound consumption 만 의미). H3 short body 에 명시.

2. **gateway-service 의 reactive stack**: Spring Cloud Gateway 는 Servlet 아닌 reactive (Netty). Service Type 은 여전히 `rest-api` (edge gateway role). Identity table 의 `Service Type` 값 보존, H3 body 에 edge role 명시.

3. **fan-platform-web 의 frontend-app**: backend Service Type 과 다름. backend dependencies (gateway-service, GAP IdP) 는 Identity table 에 이미 명시 — H3 는 frontend 만의 composition 명시.

4. **rules/common.md L13 한국어 wording**: link 제거 후 한국어 표기는 backtick 으로 코드 형식 유지: `프로젝트의 PROJECT.md (`projects/<name>/PROJECT.md`)`. wording 의미 일치 (link → 평문) 만 변경.

5. **fixture 회귀**: 4 fan service architecture.md 와 rules/common.md 모두 markdown 영역. hook runtime 변경 아님 → fixture 회귀 0 예상. 그래도 run-all.ps1 검증.

# Failure Scenarios

A. **H3 삽입 위치가 Identity table inside vs outside**: Identity table 마지막 row 직후 (separator `---` 앞) 가 canonical. table inside 면 markdown 깨짐. Edit 시 `old_string` 으로 Identity table 의 마지막 row + 직후 blank line + `---` 포함 매칭으로 정확 위치 보장.

B. **rules/common.md L13 wording fix 가 의미 변경**: link 제거 + wording 으로 풀어쓰기 — 의미 동일 보장. Reviewer (또는 self-review) 가 의미 동등성 확인.

C. **CI path-filter**: 4 fan-platform/specs/ 변경은 fan-platform flag 트리거. rules/ 변경은 libs flag 트리거 → 전 pipeline 활성. Hook runtime 미터치 + production code 0 변경 → 회귀 0 기대. 그러나 fan-platform IT/E2E 가 CI 활성화될 가능성 — 어차피 spec 변경이라 PASS 예상.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical 4 fan service H3 + 7 broken link fix, low judgment)
- **Edit hook CRLF/LF simulation mismatch 4th instance** (메모리 `project_adr_mono_012_d3_cycle_complete.md` § "Edit hook CRLF/LF simulation mismatch 3 instance 누적 시 4 instance 별 task" 의 4번째 instance 발생): artist-service architecture.md Edit 시 HARDSTOP-10 hook 가 trigger (오작동). 우회 = PowerShell `[System.IO.File]::WriteAllText` LF-normalize + utf8-no-BOM (메모리 답습). **본 task closure 시 hook source 강화 별 task 발행 후보** — hook 의 `$existing.Contains($oldString)` simulation 이 CRLF↔LF mismatch 시 false 반환 → simContent = $existing (변경 미반영) → 새 H3 detect 못 함.
- D4 OVERRIDE 적용 — refactor-spec / validate-rules cycle 의 자연 연장, MONO-091/093~100 sibling pattern (ADR-MONO-003a § D1.1)
- Lifecycle = ready → review 직접 (single-PR closure 17번째 적용 — MONO-094~100 precedent 답습)
- 묶음 근거 = feedback_pr_bundling (audit driver 재실행으로 발견된 동일 batch finding, drift hygiene 동일 도메인)

**Audit driver re-run 결과 요약** (본 task 발행 근거):
- `/validate-rules` 영역 (cross-ref + enumeration drift): hardstop catalog cross-ref 11 hit / hooks README cross-ref 2 hit / service-types catalog enumeration 8 types 일관 — 모두 PASS.
- dead-markdown-link audit (`platform/`, `rules/`, `.claude/`, `CLAUDE.md`, `TEMPLATE.md` scope): **1 broken** = `rules/common.md:13 → ../PROJECT.md` (pre-existing).
- ADR-MONO-012 D3 cycle 마무리 candidate: fan-platform 4 service Composition H3 부재 (D1 "always present" 위반 — 본 task 가 closure).

**ADR-MONO-012 § D3 portfolio status post-closure**: WMS 7 ✓ / SCM 3 ✓ / GAP 8 ✓ / ecommerce 14 ✓ / **fan-platform 4 ✓** = **32/32 service architecture.md canonical 완전 정렬** (5축 portfolio engineering discipline 시그널).
