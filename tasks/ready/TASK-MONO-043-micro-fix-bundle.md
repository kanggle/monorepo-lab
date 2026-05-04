# Task ID

TASK-MONO-043

# Title

Micro-fix 번들 — hook false-positive + lock gitignore + auth-api.md ecommerce 행 누락

# Status

ready

# Owner

backend

# Task Tags

- cleanup
- hook
- gitignore
- spec-drift

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

scm 부트스트랩 시리즈 (TASK-MONO-040 + 042, PR #185~#189) 종결 직후 발견·축적된 3 micro-fix 를 단일 batch 로 처리하는 cleanup task. ADR-MONO-002 D3 의 "scm 머지 후 1~2주 라이브러리 churn 안정 평가" 기간에 적합한 churn-zero 작업.

**Why batch**: 세 fix 모두 line-level 변경 (regex 1줄 + .gitignore 1줄 + 표 2줄) 이라 분리 PR 로 운영 비용 (3 spec + 3 impl + 3 chore) 이 fix 자체보다 큼. PR Separation Rule 의 정신 ("ready 큐 신호 보존") 은 단일 task 로 batch 하면서도 spec PR / impl PR / chore PR 분리로 충족.

---

# Scope

## In Scope — 3 micro-fix

### A. `protect-main-branch.ps1` 의 `--force-with-lease` false-positive fix

`.claude/hooks/protect-main-branch.ps1` L26-29 의 regex `git\s+push\s+--force` 가 substring 매치로 `--force-with-lease` 까지 차단. 의도 = main/master force-push 방지인데, 피처 브랜치의 안전한 `--force-with-lease` 까지 막아 friction 발생.

**증상 사례** (TASK-MONO-040 시리즈, 2026-05-04):
- spec PR #186 의 042 branch 에 main rebase 후 `git push --force-with-lease origin chore/...-spec` → 차단.
- 우회: 로컬 reset 대신 origin SHA 에서 새 임시 branch + main merge (conflict resolve) + `push HEAD:branch` fast-forward.

**Fix**:
```ps1
# Before
$command -match 'git\s+push\s+--force' -or

# After (negative lookahead — allow --force-with-lease)
$command -match 'git\s+push\s+--force(?!-with-lease)' -or
```

`--force-with-lease` 는 race condition 안전 force-push (remote 가 우리가 마지막으로 본 그 commit 이어야만 push). main/master 대상은 첫 번째 regex (`\b(main|master)\b`) 가 여전히 catch.

### B. `.claude/scheduled_tasks.lock` `.gitignore` 등록

`ScheduleWakeup` tool 이 만드는 `.claude/scheduled_tasks.lock` 가 ephemeral 파일인데 `.gitignore` 미등록 → `git status` 마다 untracked 로 노출, 우발 commit 위험 (TASK-MONO-040 impl 첫 commit 시 실제 발생, reset 으로 제거).

**Fix**:
`.gitignore` 의 `.claude/` 섹션 (현재 `.claude/settings.local.json` + `.claude/worktrees/`) 에 라인 추가:
```
.claude/scheduled_tasks.lock
```

### C. `auth-api.md` 의 ecommerce V0012 행 누락 fix

`projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients 표가 V0010 (wms) / V0011 (fan-platform) / V0013 (scm — TASK-MONO-042 머지 시 추가) 는 등재했으나 V0012 (ecommerce, TASK-MONO-027 머지) 의 2 client 행 누락. 시리즈 042 작업 중 발견.

**Fix**: 표에 2 행 추가 (fan-platform-user-flow-client 다음, scm 행 앞):
```
| `ecommerce-web-store-client`       | `ecommerce` | `authorization_code`, `refresh_token` | Yes (required) | `http://localhost:3000/api/auth/callback/gap`, `http://web.ecommerce.local/api/auth/callback/gap`   | `openid`, `profile`, `email`, `tenant.read`, `ecommerce.consumer` | V0012 |
| `ecommerce-admin-dashboard-client` | `ecommerce` | `authorization_code`, `refresh_token` | Yes (required) | `http://localhost:3001/api/auth/callback/gap`, `http://admin.ecommerce.local/api/auth/callback/gap` | `openid`, `profile`, `email`, `tenant.read`, `ecommerce.operator` | V0012 |
```

V0012 SQL (`projects/global-account-platform/apps/auth-service/src/main/resources/db/migration/V0012__seed_ecommerce_oidc_clients.sql`) 가 진실 소스 — 표는 그것을 반영해야 함.

## Out of Scope

- **Hook 의 regex 전반 정비** — branch 파싱까지 동반한 더 정교한 force-push 검증 (예: target ref 가 main/master 일 때만 force 차단) 은 별도 task. 본 task 의 (A) 는 minimal fix (`--force-with-lease` 한정 허용) 만.
- **Hook 의 다른 false-positive** (있다면) — 현재 관찰된 것은 `--force-with-lease` 만.
- **`.gitignore` 의 다른 ephemeral 파일** — 본 task 는 lock 한 건만. 다른 ephemeral 파일 발견 시 별도 task.
- **`auth-api.md` 의 다른 spec drift** — V0012 행 누락만 catch. 표의 다른 항목 (예: 헤더 인증 방식 표기 일관성) 정비는 spec drift audit (TASK-MONO-030 패턴) 영역.
- **Hook self-modification 정책 검토** — TASK-MONO-039 가 사용자 명시 승인 패턴 정립. 본 task 도 동일 절차.

---

# Acceptance Criteria

## A. Hook fix

1. `protect-main-branch.ps1` L27 의 regex 가 `git\s+push\s+--force(?!-with-lease)` 로 변경.
2. **검증 case**:
   - `git push --force origin some-branch` → blocked (intended)
   - `git push --force-with-lease origin some-feature-branch` → **allowed** (이전 차단되던 케이스)
   - `git push origin main` → blocked (first regex)
   - `git push --force-with-lease origin main` → blocked (first regex catches main substring)
   - `git push -f origin some-branch` → blocked (3rd regex unchanged)
3. 변경 후 hook 의 다른 차단 룰 (main 대상 push, hard reset to origin/main) regression 0.

## B. gitignore

4. `.gitignore` 의 `.claude/` 섹션에 `.claude/scheduled_tasks.lock` 라인 1개 추가.
5. `git check-ignore .claude/scheduled_tasks.lock` 가 매치 (rule 적용 확인).
6. `git status` 출력에 lock 파일이 untracked 로 노출되지 않음.

## C. auth-api.md

7. § OAuth2 Clients 표에 ecommerce 2 행 추가.
8. 추가된 행이 V0012 SQL 의 실 등록 데이터와 일치 (client_id / tenant / grants / PKCE / redirect_uris / scopes / V version).
9. 표 레이아웃 (column 정렬) 깨지지 않음.

## 통합

10. PR Separation Rule 준수: spec PR (본 task) → impl PR (3 fix 모두) → chore PR (lifecycle close).
11. impl PR 에서 hook self-modification 사용자 명시 승인 받음 (TASK-MONO-039 패턴).
12. impl PR 의 검증: `git status` 클린, hook 적용 후 `git push --force-with-lease` smoke test (또는 hook 단위 dry-run), `auth-api.md` 표 렌더링 확인 (no broken table).

---

# Related Specs

- [TASK-MONO-039](../done/TASK-MONO-039-rule-consistency-check-readme-fix.md) — hook false-positive fix 패턴 (rule-consistency-check.ps1 의 README false-positive). 본 task 는 동일 패턴.
- [TASK-MONO-040](../done/TASK-MONO-040-scm-platform-bootstrap.md) — hook false-positive 가 실제로 친 시리즈.
- [TASK-MONO-042](../done/TASK-MONO-042-gap-v0013-scm-oidc-clients.md) — auth-api.md 표 갱신 패턴.
- [TASK-MONO-027](../done/TASK-MONO-027-ecommerce-gap-integration.md) — V0012 ecommerce client 시드 PR. auth-api.md 표 누락이 발생한 PR.
- `protect-main-branch.ps1` (대상 파일).
- `.gitignore` (대상 파일).
- `projects/global-account-platform/specs/contracts/http/auth-api.md` § OAuth2 Clients (대상 파일).

---

# Edge Cases

1. **PowerShell 5.1 의 `(?!...)` lookahead 지원** — Windows PowerShell 5.1 의 regex 엔진은 .NET regex 기반이라 negative lookahead 지원. 사전 검증 OK.
2. **`git push --force-with-lease=ref` 형태** — `--force-with-lease=<refname>` 로 ref 명시 가능. 본 fix 의 negative lookahead (`(?!-with-lease)`) 는 `--force-with-lease=...` 까지 매치 차단. OK.
3. **`--force` 와 `--force-with-lease` 가 같은 명령에 공존** (드물지만 불가능 아님) — `git push --force --force-with-lease` 는 git 이 마지막 옵션 우선. 본 regex 는 `--force` 다음에 즉시 `-with-lease` 가 와야 매치 차단 — 공존 시 차단 (raw `--force` 가 있으니 차단이 의도와 일치).
4. **`.gitignore` 의 `.claude/scheduled_tasks.lock` 위치** — 기존 `.claude/settings.local.json` + `.claude/worktrees/` 와 같은 섹션에 두기 (가독성).
5. **auth-api.md 의 `web.ecommerce.local` / `admin.ecommerce.local` hostname** — TEMPLATE.md hostname 표 + ecommerce docker-compose 와 일치 확인.

---

# Failure Scenarios

## A1. Hook regex 변경이 다른 차단 룰을 깨뜨림

특히 첫 번째 regex (`git\s+push.*\b(main|master)\b`) 가 main 대상 force-push 를 잡아주는지 검증. `git push --force-with-lease origin main` 이 첫 regex 에 의해 차단되는지 확인 — 차단되어야 함.

## A2. `--force-with-lease=<ref>` 의 ref 가 main 인 경우

예: `git push --force-with-lease=main origin main`. 첫 regex 가 main 잡음. 차단됨.

## B. `.gitignore` 에 이미 같은 라인 존재

duplicate line 추가 시 git 동작은 idempotent — duplicate 무시. 단 `.gitignore` 의 line 중복은 readability 저하라 사전 grep 으로 중복 확인.

## C1. auth-api.md 표 헤더와 행 column 수 불일치

기존 표가 7 column (Client ID / Tenant / Grant Types / PKCE / Redirect URIs / Scopes / Flyway Version). 추가 행도 7 column 보장.

## C2. 추가된 redirect URI 가 ecommerce docker-compose 의 frontend 와 불일치

V0012 SQL 의 redirect_uris 와 일관성 (TASK-FE-067 frontend cutover 머지 후 의 web-store / admin-dashboard 의 NextAuth callback URL). 사전 확인.

## D. spec ↔ impl 분리 실패

본 task spec PR 에 코드 변경 (hook / gitignore / auth-api) 포함 시 ready 단계가 main 에서 사라짐. spec PR = task 파일 + INDEX.md ready 등재만.

---

# Notes

- **Recommended impl model**: Sonnet 4.6 또는 Haiku 4.5 — 3 line-level fix 만이라 단순. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6.
- **dependency 표현**:
  - `선행`: 없음.
  - `후속`: 없음 (independent cleanup).
- **ADR-MONO-002 D3 정신과의 정합**: 본 task 는 라이브러리 (`rules/`, `platform/`, `libs/`) churn 0 — spec drift 1 spot + 운영 hook 1 spot + meta config 1 spot 만. 1~2주 churn 안정 평가 입력에 영향 없음.
- **묶음 vs 분리 결정 기록**: 분리 PR 시 운영 비용 (3 spec + 3 impl + 3 chore = 9 PR) 이 fix 자체보다 크고, 세 fix 모두 의미적 무관 (hook ≠ gitignore ≠ spec table) 이지만 **모두 "operational hygiene" 카테고리** 라 단일 task 로 묶음 가능. 패턴 reference: TASK-MONO-035 (W4-W8 follow-up 일괄), TASK-MONO-036 (W1-W5 일괄).
