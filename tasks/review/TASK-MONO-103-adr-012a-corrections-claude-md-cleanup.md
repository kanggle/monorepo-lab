# Task ID

TASK-MONO-103

# Title

ADR-MONO-012a forward ADR (option C-1 누적 3건 corrections) + CLAUDE.md cosmetic cleanup (Tier B bundle)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- adr-mono-012a
- adr-forward-pointer
- claude-md-cleanup
- option-c-1-closure

---

# Goal

Tier B 묶음 (small ROI polish):

1. **ADR-MONO-012a forward ADR 신설** — ADR-MONO-012 본문의 3건 (§ 1.1 / § 1.4 / § D1) 인 implementation 시 사실과 다름 발견. option C-1 patterns (ADR 본문 미터치, audit-trail 만) 누적 3건 — forward pointer ADR 으로 governance closure.
2. **CLAUDE.md cosmetic cleanup** — MONO-099 § Out of Scope #3 의 잔여 polish. Recommending Tasks 마지막 단락 (long sentence) + Cross-Project Changes Branch constraint (long sentence) 를 bullet list 로 정리. 의미 미변경, readability 개선.

# Scope

## In Scope

### 1. `docs/adr/ADR-MONO-012a-cross-project-architecture-md-canonical-form-corrections.md` 신설

ADR-MONO-012 본문의 3건 wording correction. ADR 본문 자체는 미터치 (option C-1 원칙 — acceptance 후 본문 변경 = 신뢰성 손상). 정정은 forward pointer ADR 으로:

- **§ 1.1 (와 § 1.5 L59 / § D2 L113 / § 2 L134)**: "ecommerce 13" → 실제 14 (auth-service-deprecated 포함). MONO-098 closure 시 발견.
- **§ 1.4 (와 § D4 L117 / § 4 L181)**: "WMS-only" → 실제 cross-project (hook 의 detection regex `^#+\s*Service\s+Type` 는 file path 와 무관 — 모든 architecture.md edit 에 fire 가능). MONO-096 fixture (Identity-table canonical form 호환 negative case) closure 시 검증됨.
- **§ D1 ("Service Type Composition required when dual")**: 실 practice + HARDSTOP-10 hook detection logic 은 **always present** (single 도 short body 필요). MONO-095 SCM migration + MONO-097 GAP + MONO-098 ecommerce + MONO-101 fan-platform 답습 시 모두 적용.

ADR 구조:
- Status: ACCEPTED 2026-05-15 (본 task 머지 시점)
- Decision driver: option C-1 audit-only ADR correction 누적 3건 + portfolio engineering discipline 의 documentation 정합성
- Decision: 3 corrections 명시 + ADR-MONO-012 본문은 immutable (역사 record 보존), 본 ADR-MONO-012a 가 cumulative authoritative correction
- Supersedes: none
- Related: ADR-MONO-012 (정정 대상), ADR-MONO-009 (PROPOSED ADR template pattern), MONO-098 / MONO-101 closure references

### 2. `docs/adr/INDEX.md` 갱신

ADR-MONO-012a row 추가.

### 3. CLAUDE.md cosmetic cleanup

#### L184 Cross-Project Changes § Branch name constraint (현재 1 long sentence)

원본 (L184):
```
**Branch name constraint** — never include the substring `master` in branch names (e.g. `task/be-161-master-service-...`). The sandbox `--force` regex protection word-boundary matches `master` as a substring and blocks `git push` even on feature branches. Use the service/scope abbreviation (`ms-`, `mst-`) or rename around the noun (`task/be-161-database-design-...`). Encountered repeatedly across BE-052, BE-161, and similar PRs; workaround is `git push -u origin HEAD` but renaming the branch is cleaner.
```

After (bullet list, 의미 보존):
```
**Branch name constraint** — never include the substring `master` in branch names. The sandbox `--force` regex matches `master` as a substring and blocks `git push` even on feature branches.

- Rename around the noun: `task/be-161-database-design-...` (not `...-master-service-...`).
- Or use the abbreviation: `ms-`, `mst-`.
- Workaround if you hit it: `git push -u origin HEAD` (renaming the branch is cleaner).
- Encountered repeatedly across BE-052, BE-161.
```

#### L215 Recommending Tasks § 마지막 단락 (현재 1 매우 긴 sentence)

원본 (L215):
```
Before recommending the next task, **first run `git fetch origin main`** and check for divergence (`git log HEAD..origin/main --oneline`) — origin may carry recently-merged closures the local tree hasn't picked up, and recommending against stale local state can duplicate already-closed work. Then scan **both** the `ready/` queue (new candidates) **and** the `review/` queue (open impl PRs awaiting review fix, or merged PRs awaiting `review/ → done/` chore). Surface review-side work that should be cleared first to avoid open-PR pile-up. Apply to both root `tasks/` and each affected `projects/<name>/tasks/`.
```

After (bullet list):
```
Before recommending the next task:

1. **`git fetch origin main`** and check divergence (`git log HEAD..origin/main --oneline`) — origin may carry recently-merged closures the local tree hasn't picked up; recommending against stale local state duplicates already-closed work.
2. Scan **both** `ready/` queue (new candidates) and `review/` queue (open impl PRs awaiting fix, or merged PRs awaiting `review/ → done/` chore).
3. Surface review-side work to clear first — avoid open-PR pile-up.
4. Apply to root `tasks/` and each affected `projects/<name>/tasks/`.
```

CLAUDE.md 예상 영향: 215 → 215~220 lines (bullet list 확장 + line break 추가, net 변화 small). 의미 100% 보존, readability 만 개선.

## Out of Scope

- **ADR-MONO-012 본문 직접 수정**: option C-1 원칙 (acceptance 후 본문 미터치). 본 task 가 ADR-MONO-012a forward pointer 로 처리.
- **Layer Rules / Local Network Convention cleanup**: 이미 짧고 명확 — 추가 polish 가치 미달.
- **CLAUDE.md catalog table 변경**: MONO-099 의 결과물, 안정 form. 미터치.
- **다른 ADR 의 option C-1 forward pointer**: MONO-088/089/BE-282 의 ADR-MONO-010/011 option C-1 누적 (메모리 명시) 은 본 task scope 외. 별 task 후보 (필요 시).

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-012a-cross-project-architecture-md-canonical-form-corrections.md` 신설 — Status: ACCEPTED 2026-05-15, 3 corrections (§ 1.1 / § 1.4 / § D1) 명시, ADR-MONO-012 immutable 원칙 명시.
- [ ] `docs/adr/INDEX.md` 에 ADR-MONO-012a row 추가.
- [ ] `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` **본문 미터치** (option C-1 원칙).
- [ ] CLAUDE.md L184 Cross-Project Changes Branch name constraint → bullet list (의미 보존, line break 만 추가).
- [ ] CLAUDE.md L215 Recommending Tasks 마지막 단락 → 4-step numbered list (의미 보존).
- [ ] CLAUDE.md 다른 sections 미터치 (Repository Layout / Identify Target Project / Project Classification / Core Principles / Source of Truth Priority / Task Rules / Required Workflow / Hard Stop Rules catalog / Layer Rules / Local Network Convention / Recommending Tasks § model annotation).
- [ ] CLAUDE.md line count 215~225 범위 (bullet list 로 line count 약간 증가, 200 미만으로 떨어지지 않음 — readability 가 1차 목표).
- [ ] `.claude/hooks/__tests__/run-all.ps1` → 23/23 PASS (회귀 0).
- [ ] HARDSTOP fixture (특히 hardstop-body-canonical-sync) PASS — CLAUDE.md catalog ↔ platform/hardstop-rules.md 본문 sync 유지.
- [ ] production code / Hard Stop body / canonical sync / fixture assertion shape = 0 변경.

# Related Specs

- [`docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md`](../../../docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md) — 정정 대상 (본문 immutable)
- [`docs/adr/ADR-MONO-012a-cross-project-architecture-md-canonical-form-corrections.md`](../../../docs/adr/ADR-MONO-012a-cross-project-architecture-md-canonical-form-corrections.md) — 신설 forward pointer ADR
- [`docs/adr/INDEX.md`](../../../docs/adr/INDEX.md)
- [`CLAUDE.md`](../../../CLAUDE.md) § Cross-Project Changes (L184) + § Recommending Tasks (L215) — cosmetic cleanup target
- [`docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md`](../../../docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) — PROPOSED ADR pattern reference

# Related Contracts

없음.

# Edge Cases

1. **option C-1 vs forward pointer ADR 의 일관성**: option C-1 의 핵심 = ADR 본문 미터치. ADR-MONO-012a 는 ADR-MONO-012 본문을 변경하지 않고 별 ADR 로 정정 — 원칙 100% 준수.

2. **ADR-MONO-012a 의 Status 진입**: PROPOSED 단계 없이 ACCEPTED 직진 (single-session author + accept). ADR-MONO-009 의 indefinite-PROPOSED 패턴과 다른 case — 본 correction 은 이미 확인된 사실 (3 인스턴스에서 검증) 이라 PROPOSED 가 redundant.

3. **CLAUDE.md cleanup 시 hook simulation**: L184 / L215 변경은 markdown 이라 HARDSTOP hook trigger 영역 외. 단 git push hook (`protect-main-branch.ps1`) 의 `master` regex 가 branch 이름에 fire — 본 task branch `task/mono-103-...` 는 안전.

4. **CLAUDE.md 의 line count 증가 가능성**: bullet list 가 1 long sentence 보다 line 수 증가. 215 → 220~225 예상. 절대 200 미만으로 떨어지지 않음 — readability 가 1차 목표지 line count 최소화는 부차.

# Failure Scenarios

A. **ADR-MONO-012a 신설 PR 시 ADR-MONO-012 본문 우발 수정**: 본 task 의 핵심 원칙 (option C-1) 위반. 검출 = `git diff main -- docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` = empty.

B. **CLAUDE.md cleanup 시 의미 변경**: bullet list 변환 시 semantic 손실 가능. 검출 = 본 task PR 의 self-review (각 bullet 가 원문 의 어느 phrase 에 매핑되는지 명시).

C. **HARDSTOP catalog cross-ref drift**: CLAUDE.md catalog (10 row) 의 anchor link 미터치. 검출 = `grep -c "platform/hardstop-rules.md" CLAUDE.md` ≥ 11 (catalog 10 + intro 1).

D. **hook fixture 회귀**: CLAUDE.md / hook code / canonical body 미터치 — fixture 23 PASS 보존.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (ADR authoring + small CLAUDE.md edit, low judgment after design)
- D4 OVERRIDE 적용 — governance polish, MONO-091/093~102 sibling (ADR-MONO-003a § D1.1)
- Lifecycle = ready → review 직접 (single-PR closure 19번째 적용)
- 묶음 근거 = feedback_pr_bundling (Tier B small-ROI 2 후보 동시 closure — governance + cleanup 도메인 다르나 모두 polish 영역)

**3 corrections 의 evidence trace**:
- § 1.1 "ecommerce 13" → 14: MONO-098 closure 시 verification `grep -c "^## Identity$" projects/ecommerce/specs/services/*/architecture.md` = 14 (auth-service-deprecated 포함, ADR 본문은 "13" 으로 가정 후 author).
- § 1.4 "WMS-only" → cross-project: hook code (`.claude/hooks/hardstop-detect.ps1`) 의 detection regex `^#+\s*Service\s+Type` + `^projects/(?<proj>[^/]+)/specs/services/(?<svc>[^/]+)/architecture\.md$` 패턴은 project 무관, 모든 architecture.md trigger 가능. MONO-096 fixture 의 canonical-form negative case 가 GAP/SCM/ecommerce/fan 모두 PASS 검증.
- § D1 "required when dual" → always present: WMS practice (BE-150/154/161 3 instance + MONO-095/097/098 batch 3 cycle + MONO-101 fan-platform single-type 4 service 도 H3 추가) — hook detection 의 trigger condition 충족 시 single-type 도 short body 필요.

**option C-1 누적 11차 (MONO-099 closure 시 9차 → MONO-101/102 의 추가 audit-only 시점에 +2 = 11)**: 본 task 가 누적 패턴의 첫 forward ADR closure.
