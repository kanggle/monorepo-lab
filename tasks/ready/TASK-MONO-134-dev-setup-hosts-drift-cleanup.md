# Task ID

TASK-MONO-134

# Title

`scripts/dev-setup.{ps1,sh}` + `TEMPLATE.md § Hostname allocation` drift cleanup — finance/console/console-bff 누락 + mes 잔존 정리

# Status

ready

# Owner

devops / docs

# Task Tags

- code
- docs

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Local Network Convention (TEMPLATE.md § master) 의 `*.local` hostname allocation 이 portfolio 의 현재 라이브 상태와 drift 가 있다. 본 task 는 4개 surface 를 한 PR 로 정렬한다:

1. **누락 3건 추가** — `finance.local` (finance-platform 라이브, V0011 OIDC client 의 production credential), `console.local` (platform-console web, V0015 GAP OIDC seed 의 callback URL hardcoded), `console-bff.local` (platform-console BFF, project docker-compose 의 Traefik label 라이브) — 모두 `scripts/dev-setup.{ps1,sh}` 의 `$Hosts` / `HOSTS` 배열에도, `TEMPLATE.md § Hostname allocation` 테이블에도, `§ One-time developer setup` 의 hosts 한 줄에도 부재.
2. **stale 1건 제거** — `mes.local` (mes-platform, 7축 architecture 결정 `project_portfolio_7axis_architecture` 메모리의 명시 드롭 후에도 dev-setup 양 스크립트 + TEMPLATE.md 테이블 + § One-time developer setup 한 줄에 잔존; mes-platform 프로젝트 자체는 부트스트랩되지 않음).
3. **user-local stray helper 제거** — `scripts/add-console-hosts.ps1` (untracked, BE-313 회고 메모 `project_operator_overview_finance_card_resolution_complete` ⑤ 에서 `git add -A` stray-pickup 재발 2회 기록; 본 fix 후 정식 `dev-setup.ps1` 가 동일 7 hosts 를 cover → redundant). 사용자 working tree 에서 삭제.

drift 의 결과: 평가자/신규 dev 가 `scripts/dev-setup.ps1` 를 한 번 실행해도 platform-console + finance 프로젝트의 hostname 매핑이 안 돼서 `http://console.local/` `http://finance.local/` 진입이 즉시 깨진다 — portfolio 평가 시 진입 마찰. TEMPLATE.md 테이블이 ground truth 가 아니라서 신규 프로젝트 부트스트랩 시점 (`Adding a new project` § 의 step 1 = "테이블에 추가") 도 stale 테이블을 ground truth 로 본다.

---

# Scope

## In Scope

1. `scripts/dev-setup.ps1` 의 `$Hosts` 배열 mutation — 3건 추가 (`console.local`, `console-bff.local`, `finance.local`) + 1건 제거 (`mes.local`). 정렬 순서는 의미 없음, 그러나 가독성 위해 docker-compose 기동 순 (ecommerce → wms → gap → fan-platform → scm → erp → finance → console.local → console-bff.local) 권장.
2. `scripts/dev-setup.sh` 의 `HOSTS` 배열 — 1:1 동일 변경.
3. `TEMPLATE.md § Hostname allocation` 테이블 (line ~480-486) — 3 row 추가 + mes row 제거. Status 컬럼은 신규 3 entry 모두 `hostname routing` (이미 라이브이므로; bootstrap 시점 표기 `from bootstrap` 은 신규 그린필드 표기로 한정).
4. `TEMPLATE.md § One-time developer setup` 의 한 줄 hosts 예시 (line ~495) — 동일 mutation 반영.
5. `scripts/add-console-hosts.ps1` (untracked, working tree only) 삭제 — `git rm` 불요 (untracked), `Remove-Item` 1회.

## Out of Scope

- `scripts/dev-setup.ps1` / `.sh` 의 marker (`# BEGIN monorepo-lab dev hosts (TASK-MONO-022)`) 변경 — TASK-MONO-022 의 invariant 유지 (재실행 시 기존 사용자의 hosts block 인식 호환성).
- 실제 사용자 머신의 `C:\Windows\System32\drivers\etc\hosts` 파일 자동 mutation — 본 task 는 스크립트 + 문서 정렬만, 사용자 머신 hosts 적용은 사용자 본인의 `dev-setup.ps1` 재실행 책임.
- mes-platform 의 portfolio 명시 드롭 ADR 작성 — `project_portfolio_7axis_architecture` 메모리 + 기존 PR 들의 7축 결정으로 충분, 본 task 는 drift artifact 청소만.
- `.gitignore` 에 `scripts/add-console-hosts.ps1` 패턴 추가 — 사용자가 동일 파일 다시 만들 의도 없음을 본 task 가 전제 (정식 `dev-setup.ps1` 가 cover); 향후 비슷한 stray 가 다시 등장하면 별 task 로 `.gitignore` 룰 도입.
- console 외 platform-console 의 docker-compose / OIDC seed / .env 등 다른 surface 변경 — 이미 라이브 상태, 본 task 는 hostname 배포 entry 동기화에 한정.

---

# Acceptance Criteria

- **AC-1** — `scripts/dev-setup.ps1` `$Hosts` 배열 = `ecommerce.local, wms.local, gap.local, fan-platform.local, scm.local, erp.local, finance.local, console.local, console-bff.local` (정확히 9개, mes.local 부재).
- **AC-2** — `scripts/dev-setup.sh` `HOSTS` 배열 = 동일 9 entry.
- **AC-3** — `TEMPLATE.md § Hostname allocation` 테이블 row 수 = 9 (기존 7 - mes + 3 신규 = 9). 신규 3 entry 의 Status 컬럼 = `hostname routing` (3개 모두 라이브).
- **AC-4** — `TEMPLATE.md § One-time developer setup` 한 줄 hosts 예시 = AC-3 9 entry 와 동일 셋 (순서 무관, mes 부재).
- **AC-5** — `scripts/add-console-hosts.ps1` 파일 부재 (`git status` 에 ?? 항목 사라짐).
- **AC-6** — `Grep mes\.local` 결과 = 0 (TEMPLATE.md + dev-setup 양 파일 모두 정리). node_modules 제외.
- **AC-7** — `scripts/dev-setup.ps1` syntax 유효 (PowerShell parse). `bash -n scripts/dev-setup.sh` syntax 유효.
- **AC-8** — 라이브 host 의 production 코드 byte 변경 0 (V0011 OIDC seed / V0015 OIDC seed / platform-console docker-compose / finance .env.example 등 기존 라이브 파일 모두 untouched — drift 의 source 가 아니라 sink 인 4 파일만 변경).
- **AC-9** — task lifecycle `ready → in-progress → review` 이동 완료, `tasks/INDEX.md § ready/in-progress/review` 정렬 일관.
- **AC-10** — CI Linux push GREEN (markdown + script 변경만 — path-filter fast-lane 예상, code-changed 트리거 가능성 있음; 어느 쪽이든 0 failing).

---

# Related Specs

- [`TEMPLATE.md § Local Network Convention`](../../TEMPLATE.md) — master spec, 본 task 의 source of truth 정렬 대상.
- [`CLAUDE.md § Local Network Convention`](../../CLAUDE.md) — TEMPLATE.md redirect 만 있음, 변경 없음.
- [`docs/adr/ADR-MONO-001-port-prefix-scaling.md`](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — ACCEPTED, hostname routing 의 architectural rationale.
- [`tasks/done/TASK-MONO-022-traefik-hostname-routing-migration.md`](../done/TASK-MONO-022-traefik-hostname-routing-migration.md) — `scripts/dev-setup.{ps1,sh}` 의 최초 introduction.
- [`tasks/done/TASK-MONO-024-existing-projects-traefik-migration.md`](../done/TASK-MONO-024-existing-projects-traefik-migration.md) — PORT_PREFIX retirement.

---

# Related Contracts

없음 (스크립트 + 문서 변경, API/event contract 무관).

---

# Edge Cases

1. **dev-setup.ps1 의 idempotency block 보존** — `$MarkerBegin` 매칭 시 verify-only 분기 가 새 9 entry 셋 기준 으로 verify 해야 함. 기존 사용자가 7-entry marker block 을 hosts 에 가지고 있고 `dev-setup.ps1` 재실행 시 `if ($content -match [regex]::Escape($MarkerBegin))` 진입 + `foreach ($entry in $Hosts)` loop = 3 신규 entry 가 `[WARN] $entry not present` 로 surface (idempotent block 안에 누락된 entry 가 있을 때 정확한 경고). 사용자는 marker block 수동 편집 또는 marker block 삭제 후 재실행 으로 해결 — 본 task 의 스크립트 동작이 이미 이 시나리오 cover, 추가 변경 불요.
2. **stripe ordering** — 9 entry 의 배열 ordering 은 의미 없음 (모두 `127.0.0.1` 매핑, hosts 파일은 line set), 정렬은 가독성 목적. AC 는 셋 동등성만 요구.
3. **TEMPLATE.md 테이블 정렬 컬럼** — 기존 7 row 는 ecommerce → wms → gap → fan-platform → scm → erp → mes 순. 신규 3 추가 시 finance (백엔드 도메인) → platform-console web → platform-console BFF 의 자연 순서 권장 (mes 자리에 finance 가 들어가는 형태가 아니라, mes row 제거 + 끝에 3 추가).
4. **AC-8 의 "drift sink vs source"** — V0011/V0015 OIDC seed 의 callback URL hardcoded 가 hostname allocation 의 source (실제 라이브 contract); dev-setup + TEMPLATE.md 는 sink (개발자 setup ergonomics). 본 task 가 source 변경 0 — 그러므로 platform-console / finance / GAP 어느 프로젝트도 production 코드 byte 변경 없음 = 22회째 zero-retrofit 으로 분류 가능.

---

# Failure Scenarios

- **F1 — `dev-setup.ps1` syntax error** — `$Hosts` 배열 편집 시 trailing comma / quote mismatch 가 PowerShell parser 차단. impl 시 `powershell -NoProfile -Command "& { . scripts/dev-setup.ps1 -WhatIf }"` 같은 dry-validate 가 어려운 이유: 스크립트가 Administrator 검사 + hosts 쓰기 까지 inline. **대신**: parse 만 검증하는 `$null = [scriptblock]::Create((Get-Content scripts/dev-setup.ps1 -Raw))` 1줄 syntax check.
- **F2 — `dev-setup.sh` syntax error** — `bash -n scripts/dev-setup.sh` 가 표준 syntax-only validate, AC-7 의 일부.
- **F3 — TEMPLATE.md 테이블 markdown 표 깨짐** — pipe 정렬 mismatch. CI markdown lint 가 catch 안 할 수 있으나 reviewer 가 시각 확인.
- **F4 — `Grep mes\.local` 잔존 1+ 매치** — TEMPLATE.md 한 줄 hosts 예시에서 한 단어 누락 등. AC-6 가 catch.
- **F5 — `scripts/add-console-hosts.ps1` 가 다른 tracked location 에 복제되어 있음** — `Glob scripts/**/*.ps1` 로 추가 검색 후 확정 (사전 확인 결과: scripts/ 디렉토리 안 *.ps1 = dev-setup.ps1 + add-console-hosts.ps1 2개 only, 후자 untracked).

---

# Test Plan

본 task 는 production 코드 0 변경 + 스크립트 syntax 만 영향:

1. **PowerShell syntax check** — `powershell -NoProfile -Command "$null = [scriptblock]::Create((Get-Content scripts/dev-setup.ps1 -Raw)); 'OK'"` 결과 = `OK`.
2. **Bash syntax check** — `bash -n scripts/dev-setup.sh && echo OK` 결과 = `OK`.
3. **Grep audit** — `Grep "mes\.local"` (node_modules 제외) = 0 매치.
4. **Grep audit** — `Grep "console\.local|console-bff\.local|finance\.local"` 신규 3 hostname 이 TEMPLATE.md + dev-setup 양 파일에 모두 present.
5. **Lifecycle file check** — `tasks/INDEX.md` § ready/in-progress/review 의 TASK-MONO-134 항목 stage 별 정확 위치.
6. **CI Linux push** — 0 failing.

---

# Implementation Notes

3-PR sequence (root strict PR Separation Rule, 본 task 도 root MONO 이므로 적용):

- **spec PR** = 본 파일 `tasks/ready/TASK-MONO-134-*.md` + `tasks/INDEX.md § ready` 추가 1 라인.
- **impl PR** = dev-setup.{ps1,sh} 9-entry mutation + TEMPLATE.md 4-line mutation + stray script 삭제 + lifecycle `ready → in-progress → review` + INDEX move.
- **close chore PR** = `git mv review/ → done/` + Status `review → done` + INDEX move + 1-line outcome.

다만 사용자 [[feedback_pr_on_request]] = "평소엔 commit + push, PR open 은 명시 요청 시" — 본 task 도 3 stage commit + push 만, PR 명시 요청 없으면 open 안 함.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (스크립트 4-line + 문서 4-line + 1 파일 삭제 = 단순 mechanical, complex 판단 0).
