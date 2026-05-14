# Task ID

TASK-MONO-098

# Title

ecommerce 14 architecture.md migration to WMS Identity-table canonical form (ADR-MONO-012 D3 final batch)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- ecommerce-microservices-platform
- architecture-canonical-form
- adr-mono-012

---

# Goal

ADR-MONO-012 D3 final batch (SCM ✅ → GAP ✅ → **ecommerce**). ecommerce 14 architecture.md (admin-dashboard / auth-service-deprecated / batch-worker / gateway-service / notification-service / order-service / payment-service / product-service / promotion-service / review-service / search-service / shipping-service / user-service / web-store) 를 canonical form 으로 align.

**ADR-MONO-012 § 1.1 wording correction**: 본문 "ecommerce 13" 은 실제 14 (auth-service-deprecated 포함). option C-1 audit-only — INDEX outcome row 에 명시.

# Scope

## In Scope (14 file)

ecommerce flat form 의 simple pattern:
```
# Service Architecture

## <Service|Application>
`<svc>`

## Service Type
`<type>`

## Architecture Style
`<style>`
```

각 file 마다 단일 Edit 으로 canonical form 변환:
- H1: `# Service Architecture` → `# <svc> — Architecture`
- intro paragraph + `---` 추가
- `## Service` (또는 frontend 의 `## Application`) H2 + body 제거
- `## Service Type` H2 + body → Identity table row + `### Service Type Composition` H3 (always present per MONO-096 audit-trail)
- `## Architecture Style` H2 + body → Identity table row (인라인)
- Identity table 10+ rows (Service name / Project / Service Type / Architecture Style / Domain / Primary language / Bounded Context / Deployable unit / Data store / Event publication / Event consumption)
- 기존 본문 (`## Why This Architecture`, `## Internal Structure Rule`, 등) 모두 보존

특수 케이스:
- **frontend (admin-dashboard / web-store)**: `## Application` H2 (Service 대신) + Next.js stack
- **batch-worker**: `batch-job` service-type, batch-specific Identity row
- **notification-service**: `event-consumer` single-type
- **auth-service-deprecated**: 의도된 deprecated, deprecation status 보존
- 단일 dual-type 케이스 없음 — 14 모두 single-type

## Out of Scope

- HARDSTOP-10 hook propagation — 별 task **TASK-MONO-096** ✅ 종결.
- standalone v1 frozen 정책 영향: `kanggle/ecommerce-microservices-platform` standalone repo 는 `PROJECT_EXCLUDE_PATHS` 미적용 (architecture.md 는 frozen 영역 외) — 다음 batch sync 시 정상 반영.
- Production code / spec contract / Service Type 값 / 본문 narrative 의미 = 0 변경.

# Acceptance Criteria

- [ ] `grep -c "^## Identity$" projects/ecommerce-microservices-platform/specs/services/*/architecture.md` = 14.
- [ ] `grep -c "^## Service$" ecommerce/services/*/architecture.md` = 0.
- [ ] `grep -c "^## Application$" ecommerce/services/*/architecture.md` = 0 (frontend 도 standalone Application H2 제거).
- [ ] `grep -c "^## Service Type$" ecommerce/services/*/architecture.md` = 0.
- [ ] `grep -c "^### Service Type Composition$" ecommerce/services/*/architecture.md` = 14.
- [ ] `grep -c "^# Service Architecture$" ecommerce/services/*/architecture.md` = 0 (구 H1 form 모두 제거).
- [ ] `grep -c "^# .* — Architecture$" ecommerce/services/*/architecture.md` = 14 (canonical H1).
- [ ] Production code / spec contract / Service Type 값 / 본문 narrative 의미 = 0 변경.
- [ ] HARDSTOP-10 hook 회귀 없음.

# Related Specs

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` § D1 (canonical form), § D3 (migration order final batch)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` (canonical sample, dual)
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (MONO-095 SCM single-type sample)
- `projects/global-account-platform/specs/services/account-service/architecture.md` (MONO-097 GAP single-type sample)
- `projects/ecommerce-microservices-platform/specs/services/*/architecture.md` (14 targets)

# Related Contracts

해당 없음.

# Target Service

ecommerce-microservices-platform (14 service).

# Edge Cases

- A: **frontend (admin-dashboard / web-store)**: `## Application` H2 가 ecommerce 의 frontend 표기. canonical 형식 통일 — `## Application` 도 제거 (Identity table 의 row 만 source-of-truth).
- B: **auth-service-deprecated**: deprecated status 보존. service-type = `rest-api` 이지만 본문에 deprecated 명시 — Composition H3 body 에 deprecation note 포함.
- C: **batch-worker** Service Type `batch-job` — Identity row 의 Service Type 값 정확 보존.
- D: ADR § 1.1 표 의 "ecommerce 13" wording 부정확 (auth-service-deprecated 포함하면 14). option C-1 audit-only.

# Failure Scenarios

- A: 14 file × multiple section restructure 중 narrative 손상 가능. mitigation: file-by-file Edit + post-edit grep verification + `git diff` review.
- B: Identity table backfill 값 (특히 Data store / Event publication / Bounded Context) file 의 본문에서 추출 — 일부 file 에서 명시 안 됨. 합리적 기본값 (ecommerce standard stack: Java 21, Spring Boot, PostgreSQL, Kafka) 사용.

# Validation Plan

1. 14 file Edit 후 § Acceptance Criteria 모든 grep 검증.
2. `git diff --stat` 으로 file 별 line 변경 확인 (~15-25 line per file × 14 = ~200-350 line total).
3. body content 의미 손상 없음 확인.
4. HARDSTOP-10 hook 회귀 없음.

# Implementation Notes

- **D4 OVERRIDE applied** per ADR-MONO-003a § D1.1 (project-internal spec polish, ADR-MONO-012 governance follow-up). 14번째 누적 task (MONO-085~097 + 본 task).
- monorepo-level task — ADR-MONO-012 D3 final batch.
- 2 commit / 1 branch: (1) ready/ task author + INDEX ready + 14 file Edit, (2) lifecycle move ready → review + INDEX done.
- branch name `task/mono-098-ecommerce-architecture-md-migration` — CLAUDE.md § Cross-Project Changes 준수.
- single-PR closure 패턴 14번째 — MONO-084~097 precedent 답습.
- MONO-097 GAP 의 Edit tool pattern 답습 (entirely fresh form → Edit OK, single Edit 안 H3 동시 추가로 hook PASS).

# Outcome

(to be filled after impl commit)
