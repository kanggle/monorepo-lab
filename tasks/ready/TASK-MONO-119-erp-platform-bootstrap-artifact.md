# Task ID

TASK-MONO-119

# Title

erp-platform 부트스트랩 artifact — 7번째 portfolio 프로젝트 (ADR-MONO-016 §D6.2 PR-B, Option C; ADR-008/TASK-MONO-114 동형)

# Status

ready

# Owner

architecture / backend

# Task Tags

- bootstrap
- code
- governance

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

ADR-MONO-016 ACCEPTED (TASK-MONO-118, PR-A) 가 authorize 한 erp-platform **부트스트랩 artifact** 를 생성한다 — **7번째 portfolio 프로젝트**. ADR-016 §D4 procedure + §D6.2 PR-B 규정, **TASK-MONO-114 (finance/fintech PR-B) 와 정확히 동형**(scm-platform 5번째 → finance 6번째 → erp 7번째 동일 bootstrap shape).

확정값 (ADR-016 §D5 / TASK-MONO-118 ACCEPTED): domain **`erp`** / traits **`[internal-system, transactional, audit-heavy]`** / service_types **`[rest-api]`** (frontend-app 제외 = ADR-013 바인딩, UI=platform-console parity slice) / data_sensitivity **confidential** / D1 **Option C (Both)** / 첫 서비스 **`masterdata-service`** (Hexagonal skeleton, 구현=TASK-ERP-BE-001 deferred; approval/통합 read model = v2).

dependency-correct base = **TASK-MONO-118 (PR-A) 머지된 main** (ADR-016 ACCEPTED = 본 artifact 의 authorization; ADR-008 PR-A #593 → PR-B #595 base 동형).

---

# Scope

## In Scope (impl PR = PR-B)

TASK-MONO-114 (finance/fintech) shape 정확 미러:

### 1. on-demand domain rule — `rules/domains/erp.md` (NEW, project-agnostic)

`rules/domains/fintech.md` 작성 패턴 동형 — erp 도메인의 project-agnostic 강제 규칙 (master data 무결성 / approval workflow 상태·감사 / internal-system 경계: SSO·권한 매트릭스·외부 노출 금지·내부망 / 통합 read model = 도메인 비즈니스 로직 미보유 [procurement/inventory/order 는 각 서비스 소유, 7축 책임 경계]). **service 이름·API path·엔티티 미포함** (shared 영역 = HARDSTOP-03; fintech.md 와 동일 추상도). `rules/README.md` 해결 순서 + taxonomy `erp`(L75) 정합.

### 2. `.claude/config/` drift-sync (taxonomy.md L26 drift 규칙)

`erp` domain 은 `.claude/config/domains.md` L28 등재됨 but `activation-rules.md` L315 "Other domains (on-demand)" 목록에만 존재 (전용 activation mapping/rules 파일 부재 — fintech 도입 전 상태와 동일). → `activation-rules.md` 에 `erp` 전용 activation block 추가 + L315 "Other domains" 목록에서 `erp` 제거 (fintech/MONO-114 정확 동형). `internal-system` trait = traits.md L25 + activation-rules.md L102 mapping **이미 존재** → trait drift 작업 **불요**; `rules/traits/internal-system.md` **미생성** (on-demand 정책, stub 금지 — CLAUDE.md). taxonomy.md `erp`(L75)/`internal-system`(L308) 기존 = 변경 불요.

### 3. `projects/erp-platform/` direct-include 트리

`projects/finance-platform/` 트리 동형:
- `PROJECT.md` — domain `erp` / traits `[internal-system, transactional, audit-heavy]` / service_types `[rest-api]` / data_sensitivity confidential + v1 슬라이스 IN/OUT (IN: 마스터데이터[부서/직원/직급/비용센터/거래처], approval workflow 1~2단계, 통합 read model, platform-console parity slice; OUT: 인사 깊이[급여/근태/평가]·다단 결재·BI → v2; 7축 메모리 v1 슬라이스 + ADR-016 D2/D3) + 책임 경계(도메인 로직 미보유)
- `docker-compose.yml` — Traefik hostname `erp.local` (TEMPLATE.md Local Network Convention), MySQL + Redis `expose:` only (host port 0; PORT_PREFIX 미사용)
- scaffold (`.env.example` / gradle / 디렉터리 구조) + gap-integration (GAP OIDC 연동 설정, finance gap-integration 동형)
- 첫 서비스 `apps/masterdata-service/` **Hexagonal skeleton** (domain/application/infrastructure/presentation 디렉터리 + build.gradle + bootstrap class + `integrationTest` Gradle task 등록 [scm MONO-048/finance MONO-115 IT-job 후속 대비]; 비즈니스 로직 0 — 구현=TASK-ERP-BE-001 deferred)
- `tasks/ready/TASK-ERP-BE-001-masterdata-service-bootstrap.md` (skeleton task; finance TASK-FIN-BE-001 동형, 구현 deferred) + `tasks/INDEX.md` (erp-platform project lifecycle, finance INDEX 동형)

### 4. GAP erp seed (V00XX ×2)

GAP V0017 (finance) 동형 — 다음 free GAP Flyway migration version (impl 이 `grep -oE 'V00[0-9]+' projects/global-account-platform/.../db/migration` 로 실측): tenant `erp` (B2B_ENTERPRISE) + `erp-platform-internal-services-client` client_credentials (scopes `erp.read`/`erp.write` TTL 1800, BCrypt(10) `erp-dev` secret). V0017 byte-shape 정확 미러 (tenant/client row 만 erp 치환).

### 5. monorepo 배선

- `settings.gradle` — `include 'projects:erp-platform:apps:masterdata-service'`
- root `package.json` — erp shortcut script (finance 패턴)
- `docs/project-overview.md` roster 6 → **7**
- `README.md` portfolio hub — erp 행 추가
- `scripts/sync-portfolio.sh` PROJECT_REMOTES — `erp-platform` 등록 (Option C only; ADR-016 §D4 step)
- `.github/workflows/ci.yml` — erp per-project path-filter (scm/finance filter **정확 미러**, pure-positive, negation 미사용 = MONO-074/075 규율; `outputs.erp` flag). **Testcontainers IT CI job 은 본 task scope 아님** — scm MONO-048 = finance MONO-115 동형 별 follow-up (skeleton 단계라 IT 0).

### 6. 외부 `kanggle/erp-platform` Template fork — classifier-blocked, user-셸 hand-off (PENDING-tracked)

ADR-016 §D4 step 4 / TASK-MONO-116 (finance) 정확 동형: `gh repo create kanggle/erp-platform --template kanggle/project-template --public --clone` 은 classifier-blocked outward-facing op → **우회 금지, 정확 명령 사용자 셸 hand-off**. 본 PR-B 머지 시점 monorepo side(Option C 의 direct-include)만 landed → ADR-016 §6 / 메모리에 **standalone side PENDING 정직 명기** (green-wash 금지, silently "done" 금지). 사용자 실행 후 객관 검증(`gh repo view` templateRepository/visibility) → **별 append-only resolution recording task** (TASK-MONO-116 = finance 동형 패턴; 본 task 후속). `--clone` 은 monorepo 루트 밖에서 실행 권고 (finance nested-clone 잔재 교훈).

## Out of Scope

- `masterdata-service` 비즈니스 로직 / 도메인 모델 / API 구현 — TASK-ERP-BE-001 (skeleton 만, finance TASK-FIN-BE-001 deferred 동형).
- erp Testcontainers Integration CI job — 별 follow-up (scm MONO-048 = finance MONO-115 패턴; skeleton 단계 IT 0).
- ADR-016 / ADR-002 / ADR-003a 변경 — PR-A (TASK-MONO-118) 가 ACCEPTED+recording 완료; 본 PR-B 는 ADR 무변경 (단 §6 ACCEPTED row 의 PR-B # 는 PR-A 의 close chore 가 backfill, 본 task 아님).
- platform-console 실제 erp parity row UI 구현 — ADR-013/FE 별 task (본 task 는 PROJECT.md 에 "UI=platform-console parity slice" 선언만, ADR-013 바인딩 기록).
- ledger/accounting 깊이 (erp 는 7축상 도메인 로직 미보유; accounting=별 도메인).
- agent memory 동기화 = repo task 외부 (dispatcher 가 본 PR-B 종결 후 직접; ADR-016 §D4 step 9).

---

# Acceptance Criteria

1. `rules/domains/erp.md` 신규 (project-agnostic, fintech.md 추상도 동형, HARDSTOP-03 clean) + `rules/README.md`/taxonomy 정합.
2. `.claude/config/activation-rules.md` erp activation block 추가 + L315 on-demand 목록서 erp 제거 (drift-sync; domains.md/taxonomy.md 정합; internal-system 무변경).
3. `projects/erp-platform/` 트리 = finance 트리 동형 (PROJECT.md 확정 classification / docker-compose erp.local expose-only / scaffold / gap-integration / masterdata-service Hexagonal skeleton + integrationTest task / TASK-ERP-BE-001 + project INDEX).
4. GAP erp seed = 다음 free V00XX ×2 (tenant erp + erp client_credentials), V0017 byte-shape 미러, BCrypt(10) `erp-dev` 독립 재검증 (dispatcher BE-301: GAP lib 재실행, scm-dev=false·erp-dev=true·strength10).
5. 배선 전부 (settings.gradle/package.json/project-overview 6→7/README/sync-portfolio/ci.yml erp filter=scm·finance 정확 미러 pure-positive) + `./gradlew :projects:erp-platform:apps:masterdata-service:tasks` exit 0 + 무regression.
6. 외부 fork = 정확 명령 user-셸 hand-off + ADR-016 §6/메모리에 standalone PENDING 정직 명기 (green-wash 금지); 본 PR-B = monorepo side(Option C)만 landed.
7. **dispatcher BE-301 독립 재검증** (agent report 불신·재실측): HARDSTOP-02/03=0, classification 확정값 일치, GAP seed byte-shape+BCrypt, gradle 무regression, ci.yml filter scm/finance 동형·negation 0, diff scope 0 stray.
8. doc/skeleton-only — `masterdata-service` 비즈니스 로직 0 (skeleton). ADR-016/002/003a 무변경.

---

# Related Specs

- [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) §D4 procedure + §D6.2 PR-B + §D2/D3 (확정 classification) — 권위 절차
- [TASK-MONO-118](./TASK-MONO-118-adr-mono-016-accepted-erp-bootstrap-transition.md) — PR-A (ADR ACCEPTED authorization; **선행, dependency-correct base**)
- [TASK-MONO-114](../done/TASK-MONO-114-finance-platform-bootstrap-artifact.md) — **finance/fintech PR-B 정확 동형 선례** (bootstrap shape 미러 원본)
- [TASK-MONO-116](../done/TASK-MONO-116-finance-external-fork-resolution-recording.md) — 외부 fork classifier-blocked hand-off + PENDING→append-only resolution recording 패턴 (erp fork 후속 동형)
- [ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — erp backend-only / UI=parity slice 바인딩 (PROJECT.md 선언 근거)
- [`rules/taxonomy.md`](../../rules/taxonomy.md) `erp` L75 / `internal-system` L308 — classification source

---

# Related Contracts

- 없음 (skeleton bootstrap; masterdata-service contract = TASK-ERP-BE-001 spec-first 단계). GAP seed = OIDC tenant/client row (DB migration, 외부 API contract 아님).

---

# Target Service / Component

- `rules/domains/erp.md` (NEW) · `.claude/config/activation-rules.md` (erp block)
- `projects/erp-platform/**` (PROJECT.md / docker-compose / scaffold / gap-integration / `apps/masterdata-service/` skeleton / tasks/)
- GAP `projects/global-account-platform/.../db/migration/V00XX*` ×2
- `settings.gradle` / `package.json` / `docs/project-overview.md` / `README.md` / `scripts/sync-portfolio.sh` / `.github/workflows/ci.yml`

---

# Edge Cases

1. **GAP migration version race**: 다음 free V00XX 는 impl 직전 실측 (V0017 finance 이후 추가 migration 있을 수 있음). 점유 시 다음 free.
2. **ci.yml erp filter**: scm/finance filter 정확 미러 — pure-positive, negation 0 (MONO-074/075). `erp` flag 신규 + dedicated job 은 별 task (skeleton IT 0).
3. **HARDSTOP-03 (shared 영역)**: `rules/domains/erp.md` + `.claude/config` 는 shared — project-specific(service명/path/엔티티) 금지. fintech.md 추상도 정확 동형.
4. **nested clone 잔재**: 외부 fork `--clone` 은 monorepo 루트 밖 실행 권고 (finance 교훈 — 본 PR-B 는 명령 hand-off 만, 실행 안 함).
5. **PROJECT.md frontend-app 유혹**: ADR-013 바인딩 — service_types 에 frontend-app 절대 미포함 (GAP backend-only 동형). UI 책임 = "platform-console parity slice" 선언만.

---

# Failure Scenarios

## A. bootstrap 중 finance/scm 트리와 구조 divergence 발견

→ TASK-MONO-114 (finance) shape 가 정확 미러 기준. divergence = 동일 class 면 본 task 내 finance 동형으로 교정; 다른 class(예: ADR-013 parity-slice 가 새 구조 요구) 면 STOP+별 follow-up, scope 확장 금지 (TASK-MONO-116 Failure Scenario 패턴).

## B. 외부 fork 를 dispatcher 가 직접 시도 유혹

→ classifier-blocked = STOP+정확 명령 user-셸 hand-off (TASK-MONO-116 검증된 규율). 우회/자동화 금지. monorepo side PENDING 정직 명기, silently "fork 완료" 금지 (green-wash).

## C. agent dispatch report 신뢰 유혹

→ skeleton + GAP seed + 배선 = agent dispatch (backend-engineer Opus, TASK-MONO-114 동형) 가능하나 **dispatcher BE-301 독립 재검증 필수** (agent report 불신·재실측: HARDSTOP/classification/BCrypt/gradle/diff scope). MONO-114 의 BE-301 discipline 정확 동형.

---

# Test Requirements

- `./gradlew :projects:erp-platform:apps:masterdata-service:tasks` exit 0, 무regression (다른 6 프로젝트 빌드 영향 0).
- GAP erp seed: GAP Testcontainers IT (있으면) 또는 BCrypt 독립 재실행으로 `erp-dev` strength10 verify (MONO-114 동형).
- ci.yml: actionlint/gh workflow view valid; erp filter = scm/finance 동형 pure-positive (negation 0).
- dispatcher BE-301 재검증 결과 impl PR description 명시 (HARDSTOP-02/03=0, classification 일치, GAP byte-shape, gradle, diff scope).

---

# Definition of Done

- [ ] `rules/domains/erp.md` + `.claude/config` erp drift-sync (HARDSTOP-03 clean)
- [ ] `projects/erp-platform/` 트리 (PROJECT.md 확정 classification / docker-compose / masterdata-service skeleton / TASK-ERP-BE-001 / project INDEX) = finance 동형
- [ ] GAP erp seed V00XX ×2 (V0017 미러, BCrypt erp-dev 독립검증)
- [ ] 배선 (settings/package/overview 7/README/sync-portfolio/ci.yml erp filter) + gradle exit0 무regression
- [ ] 외부 fork = user-셸 hand-off + standalone PENDING 정직 명기
- [ ] dispatcher BE-301 독립 재검증 통과, impl PR 명시
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: 분석=Opus 4.7 / **구현 권장=backend-engineer (Opus dispatch)** + **dispatcher(Opus) BE-301 독립 재검증** — TASK-MONO-114 정확 동형 (agent dispatch + dispatcher report-불신 재실측). skeleton+seed+배선 = mechanical-but-precise (finance shape 미러), 검증이 correctness-critical.
- **분량**: medium-large — finance MONO-114 (39 file/1646+) 규모 동형 (rules/domains/erp.md + config + projects/erp-platform/ 트리 + GAP seed ×2 + 배선 6).
- **dependency**: `선행` = **TASK-MONO-118 (PR-A) 머지** (ADR-016 ACCEPTED = artifact authorization; dependency-correct base = PR-A 머지된 main, ADR-008 PR-A→PR-B 동형). `후속` = (a) TASK-ERP-BE-001 (masterdata-service 구현, spec-first — finance TASK-FIN-BE-001 패턴); (b) 외부 fork resolution recording (TASK-MONO-116 동형, user 셸 실행 후); (c) erp Testcontainers IT CI job (scm MONO-048=finance MONO-115 동형, IT 생기면).
- **green-wash 금지 연계**: 외부 fork classifier-blocked → PENDING 정직 추적 (finance/MONO-116 검증 패턴); agent dispatch → dispatcher 독립 재검증 (report 불신); skeleton = 비즈니스 로직 0 정직 명기 (구현 미완을 "완료"로 위장 금지).
- erp = portfolio **마지막 도메인**; mes = 의도적 드롭 (메모리 `project_portfolio_7axis_architecture`, 재제안 금지). 본 PR-B 머지 = 7축 monorepo side 완성 (standalone fork PENDING 잔여).
