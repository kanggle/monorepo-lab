# Task ID

TASK-MONO-309

# Title

Promote four audit-surfaced operational rules from auto-memory into canonical shared docs (CLAUDE.md / platform/)

# Status

ready

# Owner

architect

# Task Tags

- governance
- docs
- platform
- memory-promotion

---

# Goal

The 2026-06-27 `/audit-memory` sweep surfaced **four repo-wide operational rules** that currently live only in per-session auto-memory. By the audit-memory "promote-to-CLAUDE.md" criterion (a rule that must apply identically to other developers and other AI sessions belongs in a canonical shared source, not memory), each should be lifted into a tracked canonical doc so it survives outside the memory store and binds every session.

This is a **documentation/governance task** — no implementation code. Each promotion is an additive insertion into an existing tracked shared file (`CLAUDE.md`, `platform/git-workflow-policy.md`, `platform/testing-strategy.md`). The originating memory files stay as worked-example detail (the established "CLAUDE.md = catalog / memory = detail" split), each gaining a `승격됨(MONO-309)` annotation.

**근거**: `/audit-memory` 2026-06-27 보고서 § 5 (공통 규칙 후보).

---

# Scope

## In Scope

| 승격 규칙 | 출처 메모리 | 대상 캐논 파일 | 형태 |
|---|---|---|---|
| **R1 — `docker image prune -a` 안전**: `-a`(미사용 전부)는 base 이미지(`monorepo/java-service-base:v1` 등 ADR-MONO-041 D2)를 파괴해 후속 Java 빌드를 깨뜨림 → 재빌드 직후엔 `docker image prune -f`(dangling만) 사용, `-a`는 base 보존 확인 후에만. | `feedback_prune_old_image_after_rebuild` | `TEMPLATE.md § Local Network Convention` 또는 신규 `platform/local-docker-safety.md` 1-2줄 | additive |
| **R2 — Bash 툴 commit+push 분리**: 한 Bash 호출에 `git commit && git push`를 체인하면 branch-protection classifier가 호출 전체를 차단해 commit조차 안 됨 → commit과 push는 **별도 Bash 호출**로 분리. | `project_console_web_ecommerce_ops_bug_class` | `platform/git-workflow-policy.md` (Bash-tool 절) | additive |
| **R3 — OIDC authed 검증 = headless browser 필수**: Secure 쿠키 + PKCE 흐름은 `curl`로 완주 불가 → 로그인-후-검증은 Playwright 등 headless browser 필수. | `env_console_demo_local_redeploy` | `platform/testing-strategy.md` (로컬/E2E 검증 절) | additive |
| **R4 — fed-e2e `kafka:9092`=스텁**: federation-hardening-e2e의 `kafka:9092`는 alpine sleep 스텁(실 브로커=`redpanda:9092`) → 신규 Spring 서비스 배선 시 `KAFKA_BOOTSTRAP_SERVERS=redpanda:9092` + `MANAGEMENT_HEALTH_KAFKA_ENABLED=false`. | `env_fed_e2e_kafka_stub_health_block` | **[?] 대상 미정** — fed-e2e docker 자산이 untracked 데모 트리라 그 디렉터리 README는 커밋 안 됨. tracked 대안(`platform/` 또는 nightly-e2e.yml 주석 패턴) 검토. | additive 또는 보류 |
| 출처 메모리 4건에 `승격됨(MONO-309 2026-06-27)` 주석 + MEMORY.md 인덱스 한 줄 유지 | (4 파일) | 각 메모리 본문 | annotation |

## Out of Scope

- `.claude/` 하위 수정 (classifier 하드블록 — 무관: 대상은 CLAUDE.md/platform/, .claude/ 아님).
- 메모리 파일 삭제 — 4건 모두 worked-example detail 보존 (catalog/detail 분리).
- R4의 untracked fed-e2e 자산 자체를 tracked로 전환 (별 결정).
- 코드 변경 일체 (doc-only).

---

# Acceptance Criteria

- [ ] R1: 대상 캐논 파일에 `prune -a` base-이미지 파괴 경고 1-2줄 additive 삽입. dangling-only(`-f`) vs `-a` 구분 명시.
- [ ] R2: `platform/git-workflow-policy.md`에 Bash-tool commit+push 분리 규칙 additive 1줄.
- [ ] R3: `platform/testing-strategy.md`에 "OIDC authed 검증 = headless browser (curl 불가)" 규칙 additive.
- [ ] R4: 대상 결정 후 삽입, **또는** tracked 대상 부재로 판단 시 보류 사유를 task에 기록하고 메모리 유지로 종결.
- [ ] 출처 메모리 4건에 `승격됨(MONO-309)` 주석 추가 (본문 detail은 보존).
- [ ] 삽입은 전부 additive (기존 문장 byte-unchanged — `git diff`로 확인). HARDSTOP-04 회피.
- [ ] impl code 0 (doc-only).

# Related Specs

- [CLAUDE.md](../../CLAUDE.md) — Hard Stop / Git discipline / Local Network Convention 절.
- [platform/git-workflow-policy.md](../../platform/git-workflow-policy.md) — R2 host.
- [platform/testing-strategy.md](../../platform/testing-strategy.md) — R3 host.
- [TEMPLATE.md](../../TEMPLATE.md) — R1 candidate host (Local Network Convention master).

# Related Contracts

- 없음 (거버넌스 doc 변경, 계약 무관).

---

# Edge Cases

- **R1 대상 모호**: TEMPLATE.md(Local Network master) vs 신규 platform 파일 — 기존 docker 운영 규칙이 모이는 곳 우선. 둘 다 부적합하면 CLAUDE.md § Local Network Convention 인접에 1줄.
- **R4 tracked 대상 부재**: fed-e2e compose/nginx가 untracked라 그 자리 README는 main에 안 남음 → 보류가 정당. 메모리 유지 + task에 사유 기록으로 종결(승격 강제 금지).
- **승격이 기존 문장과 중복**: 이미 존재하면 삽입 생략하고 그 사실 기록 (audit가 놓친 기존 커버리지).

# Failure Scenarios

- **HARDSTOP-04 위반** (기존 캐논 문장 변경): commit 직전 `git diff`로 additive-only 검증.
- **메모리 detail 삭제**: 4건은 incident/worked-example 보존이 목적 — 본문 삭제 금지, 주석만 추가.
- **R4를 tracked 대상 없이 무리 삽입** (untracked README): main에 안 남아 무의미 → 보류 판정이 올바름.

---

# Definition of Done

- [ ] R1/R2/R3 캐논 삽입 + R4 결정(삽입 또는 보류-사유).
- [ ] 출처 메모리 4건 `승격됨(MONO-309)` 주석.
- [ ] `git diff` additive-only 검증 (HARDSTOP-04).
- [ ] `tasks/INDEX.md` ready entry.
- [ ] commit + push (branch `task/mono-309-promote-memory-rules`; substring `main`/`master` 회피 ✓).
- [ ] PR open (사용자 요청 시).
