# Task ID

TASK-BE-156

# Title

inventory-service `external-integrations.md` zero-state authoring + `architecture.md § Open Items` #8/#9/#10 status sync (BE-152 surfaced #1 closure)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- inventory-service
- spec
- backfill
- integration-heavy
- be

---

# Goal

[TASK-BE-152](../done/TASK-BE-152-inventory-service-open-items-audit-and-list-correction.md) (commit `3930a523` + close `1ad62091`, 2026-05-14 머지) audit § "candidate inventory" #1 closure — `projects/wms-platform/specs/services/inventory-service/external-integrations.md` 신규 zero-state 작성 + `architecture.md § Open Items` #8 ❌ → ✅ 정정. 추가로 BE-152 audit 이후 closure 된 #9 (LOT_INACTIVE, MONO-087 commit `58a344d3`) + #10 (gateway route, BE-153 commit `7e14a218`) stale status 도 sync.

배경:

- `rules/traits/integration-heavy.md § Required Artifacts (1)` = "외부 연동 카탈로그 — 연동하는 모든 벤더 목록, 담당 서비스, 호출 방향(in/out), 인증 방식. 위치: `specs/services/<service>/external-integrations.md`". integration-heavy trait 가 active 인 모든 service 의 mandatory artifact.
- inventory-service 의 경우 v1 **외부 vendor 0** — ERP webhook / TMS push / 결제 등 모든 외부 트래픽은 sibling service (inbound = ERP ASN webhook, outbound = ERP order webhook + TMS push) 가 흡수. 그러나 trait Required Artifact 는 zero-state 도 명시적 file 형태로 카탈로그화 요구.
- sibling 답습 pattern: `outbound-service/external-integrations.md` (~767 line, TMS 마퀴) + `inbound-service/external-integrations.md` (~423 line) 의 reverse — inventory 는 "no direct external surface" 사실을 명시적으로 선언 + 내부 dependency (Kafka via outbox, PostgreSQL, Redis) 와 외부 vendor 의 구분 + 잠재적 v2 진입로 (cycle-count adapter / serial-tracking adapter 등) 명시.

본 task = **신규 zero-state spec file 1 + architecture.md § Open Items 3-항목 status sync** (production code = 0 변경, markdown only). BE-141~155 의 single-PR closure 답습 (16번째 entry → 17번째).

---

# Scope

## In Scope

### A. 신규 `inventory-service/external-integrations.md` (zero-state)

대상 경로: `projects/wms-platform/specs/services/inventory-service/external-integrations.md` (신규).

구조 (sibling inbound/outbound 답습 + zero-state 축약):

1. **헤더 + intent** — Required Artifact 1 satisfaction + zero-state declaration 명시.
2. **Catalog Summary** — vendor 행 0 + "No direct external vendors in v1" 명시 + sibling reference (inbound + outbound 가 모든 외부 트래픽 흡수).
3. **Why zero direct integrations** — inventory-service 의 도메인 경계 설명 (외부 actor 가 inventory 에 직접 접근 안 함, gateway → master/inbound/outbound 경유 + Kafka event 만).
4. **Internal vs External — Boundary clarification** — Kafka cluster + PostgreSQL + Redis 는 infrastructure (project-shared), not "external vendor". I9 bulkhead 의 적용 대상은 외부 HTTP 호출이며, inventory v1 에는 그런 호출 없음.
5. **Required-Artifact compliance map** — trait Required Artifacts 1-6 각 항목이 zero-state 에서 어떻게 trivially 충족되는지 (or N/A) 표 형태로.
6. **v2 evolution paths** — 잠재적 외부 integration 후보:
   - direct cycle-count vendor (RFID / scanner) adapter
   - serial-number tracking integration
   - external lot-traceability (제약/식품 컴플라이언스) — `compliance: []` 이 변경되면 진입 가능
   각 path 가 발생 시 본 file 이 비어있지 않게 되며 그 시점 trait 의 모든 I1~I10 적용.
7. **References** — sibling files (inbound + outbound) + rules/traits/integration-heavy.md + architecture.md (inventory dependencies section) + platform/observability.md.

예상 크기: ~80-120 line (sibling files 의 1/5 ~ 1/7).

### B. `architecture.md § Open Items` status sync

대상: `projects/wms-platform/specs/services/inventory-service/architecture.md` L577-625.

작업:
- **#8 (external-integrations.md)**: ❌ outstanding → ✅ done — link 활성화 `[external-integrations.md](external-integrations.md)` + BE-156 attribution.
- **#9 (LOT_INACTIVE error code)**: ⚠️ partial → ✅ done — MONO-087 closure 인용 (commit `58a344d3`, `platform/error-handling.md` line 158 에 등록). 4 etc registered error codes 의 "5/6" 언급은 "6/6 모두 등록" 으로 정정.
- **#10 (gateway route)**: ❌ outstanding → ✅ done — BE-153 closure 인용 (commit `7e14a218`, `gateway-service/application.yml` 의 inbound/inventory/outbound/admin 4 route 등록, notification 은 event-consumer 제외). "portfolio-wide gap" 표현은 historical fact 로 유지하되 closure attribution 추가.

### C. `domain-model.md § Open Items` cross-ref 확인

대상: `projects/wms-platform/specs/services/inventory-service/domain-model.md` L675-689 (BE-152 audit 영역).

- architecture.md § Open Items 의 4 outstanding 중 LOT_INACTIVE 항목 status 가 domain-model.md 에도 ⚠️ 로 명시되어 있다면 동일 ✅ sync.
- database-design.md 항목은 본 task scope 밖 (BE-152 § candidate inventory #2 후보로 유지).
- 변경 없을 가능성 — sync 만 확인.

## Out of Scope

- `inventory-service/database-design.md` retrospective authoring (BE-152 § candidate inventory #2, ~200-400 line, 별 task 후보).
- production code 변경 0 — yml / Java / SQL 모두 손대지 않음. spec/markdown only.
- 다른 service 의 external-integrations.md 변경 (master-service / admin-service / notification-service / gateway-service 모두 본 task scope 밖). master/admin/notification/gateway 가 integration-heavy trait active 인지는 PROJECT.md traits = project-level 이라 모두 active — 그러나 본 task 는 BE-152 surfaced 후보 #1 단일 closure 만 (다른 service 의 missing external-integrations.md 는 portfolio-wide audit task 후보, 별개).
- 신규 error code / Kafka topic / API endpoint 신규 = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/inventory-service/external-integrations.md` 신규 file 존재, 약 80-120 line, 7 section 구조 (헤더 / Catalog Summary / Why zero / Internal-vs-External boundary / Required-Artifact compliance map / v2 evolution paths / References).
- [ ] `external-integrations.md § Catalog Summary` 에 vendor 행 0 + "No direct external vendors in v1" 명시.
- [ ] `external-integrations.md § References` 의 sibling links (inbound + outbound external-integrations.md) + rules/traits/integration-heavy.md + architecture.md 모두 dead-reference 0 (`ls` 으로 실재 확인).
- [ ] `architecture.md § Open Items` #8 ❌ → ✅ + link 활성화 + BE-156 attribution.
- [ ] `architecture.md § Open Items` #9 ⚠️ → ✅ + MONO-087 closure attribution + "5/6 registered" → "6/6 모두 등록" 정정.
- [ ] `architecture.md § Open Items` #10 ❌ → ✅ + BE-153 closure attribution.
- [ ] `domain-model.md § Open Items` (L675-689) cross-ref sync (변경 0 또는 LOT_INACTIVE status 1-line 정정).
- [ ] HARDSTOP-03 PASS — 신규 file 이 wms-specific (project-internal) 이고 shared path 진입 0.
- [ ] grep `(Open Item` projects/wms-platform/specs/services/inventory-service/ → 0 (BE-151 outcome 유지 + 본 task 가 #8 closure 로 architecture.md 의 잠재 marker 신설 안 함).
- [ ] production code 변경 = 0 (git diff `--stat` 으로 markdown 만 변경 확인).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (`domain: wms`, `traits: [transactional, integration-heavy]`), then load `rules/common.md` + `rules/domains/wms.md` + `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`. Service Type 은 `inventory-service/architecture.md § Identity` 의 `rest-api` (primary) + `event-consumer` (consumer path) 두 file (`platform/service-types/rest-api.md` + `platform/service-types/event-consumer.md`).

- `rules/traits/integration-heavy.md` — Required Artifacts (1) zero-state 적용
- `projects/wms-platform/specs/services/inventory-service/architecture.md` — § Identity, § Dependencies, § Open Items (L577-625 정정 대상)
- `projects/wms-platform/specs/services/inventory-service/domain-model.md` — § Open Items (L675-689 cross-ref)
- `projects/wms-platform/specs/services/inbound-service/external-integrations.md` — sibling reference (HMAC webhook + Kafka + Postgres + Redis + Secret Manager)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` — sibling reference (TMS marquee + ERP webhook)
- `platform/error-handling.md` (line 158) — MONO-087 closure fact (LOT_INACTIVE)
- `projects/wms-platform/apps/gateway-service/src/main/resources/application.yml` — BE-153 closure fact (4 route 등록)

# Related Skills

- `.claude/skills/INDEX.md` — spec authoring guidance (해당 시)

---

# Related Contracts

- N/A — 본 task = spec authoring + § Open Items status sync 만. 신규 API/event/webhook contract = 0.

---

# Target Service

- `inventory-service`

---

# Edge Cases

1. **integration-heavy trait 가 inventory-service 에 적용 안 된다는 해석 가능?** — PROJECT.md traits = project-level 이고 service-level override 없음. integration-heavy 는 외부 vendor 가 있는 service 뿐만 아니라 project 전체에 active. 따라서 inventory 도 Required Artifact 1 적용 대상. zero-state 가 정답.
2. **Kafka / Postgres / Redis 가 "external" 여부 모호** — sibling inbound/outbound 가 Kafka 등을 catalog 에 포함시켰음. 본 file 도 동일 분류 (infrastructure outbound) 가능하지만, zero-state 의 의도는 "외부 vendor (3rd party HTTPS/SOAP/Kafka-bridge)" 의 부재. Kafka/Postgres/Redis 는 § Internal-vs-External boundary section 에서 명시적으로 "infrastructure, not external vendor" 분류 → catalog 에서 제외. 단, sibling files 와 의 consistency 를 위해 catalog 행에 infrastructure 행 inclusion 옵션도 고려 — implementation 시 sibling pattern 우선 답습.
3. **architecture.md § Open Items #8 closure 후 후속 trigger 없음** — outstanding 4 items 중 1 closure 라 나머지 3 (database-design / LOT_INACTIVE / gateway route 중 LOT_INACTIVE+gateway 는 본 task 로 동시 closure, database-design 만 잔여) 의 추가 follow-up surfacing 의도. closure 후 § Open Items 의 outstanding count = 1 (database-design.md).
4. **gateway-service 의 multi-service route 가 portfolio-wide gap 으로 surfaced 됐었다가 BE-153 으로 wms 만 closure** — 다른 project (ecommerce/fan/gap/scm) 의 gateway route 상태는 BE-153 의 audit 결과 이미 정상 (메모리 `project_wms_be_153_driven_audit_series.md` 참조). 본 task 는 wms-only #10 status 만 sync, 다른 project 영향 0.

---

# Failure Scenarios

1. **신규 file 의 sibling reference link depth 오류** — `[inbound-service/external-integrations.md](../inbound-service/external-integrations.md)` 같은 sibling link 의 path 가 `..` 깊이 misaligned. 사전 `ls` 으로 path 실재 검증 의무 + BE-151 의 `replace_all` substring trap 학습 (5-up vs 6-up over-upgrade) 답습.
2. **architecture.md § Open Items 정정 시 sentence 구조 broken** — #9 의 "5 of 6 registered" 표현이 list+rationale 으로 다층 구조. 정정 시 markdown 들여쓰기 + bullet 보존 필요. Edit 후 Read 로 즉시 확인.
3. **HARDSTOP-10 hook trigger** — 만약 신규 file 이 service-level architecture header pattern 미준수 (예: `### Service Type Composition` standard header 없음) 시 hook block 가능. 본 file 은 external-integrations.md (sibling 답습, architecture.md 가 아님) 이라 hook trigger 안 함이 예상되지만, 작성 후 첫 Edit 에서 검증.
4. **CRLF vs LF 충돌** — Windows 환경에서 신규 file write 시 CRLF 로 생성될 가능성. sibling file 들의 line ending 통일 확인 + LF 강제 작성 (PowerShell `[System.IO.File]::WriteAllText` 패턴, BE-150 절차 답습) 필요 시 적용.
5. **path-filter (TASK-MONO-074/075) 가 wms-spec changed 인식 못 함** — 본 PR 은 markdown only + wms spec path 만 변경. path-filter 의 wms positive flag 가 trigger 되지 않으면 CI minimal job 만 run (markdown-only 답습, ~1 PASS + 15 SKIP). 정상 동작 예상.

---

# Validation Plan

- `ls projects/wms-platform/specs/services/inventory-service/external-integrations.md` → file 존재 확인.
- `wc -l projects/wms-platform/specs/services/inventory-service/external-integrations.md` → 80-120 line 범위 확인.
- `grep -c "(Open Item" projects/wms-platform/specs/services/inventory-service/` → 0 (regression 0 확인).
- `grep -n "❌\|⚠️" projects/wms-platform/specs/services/inventory-service/architecture.md` → #8/#9/#10 의 ❌/⚠️ 가 ✅ 로 정정됐는지 확인 (잔여 ❌ 가 있다면 database-design.md 1 항목만 — domain-model.md 측이고 architecture.md 측은 0).
- sibling reference 검증:
  - `ls projects/wms-platform/specs/services/inbound-service/external-integrations.md` → 실재 ✓
  - `ls projects/wms-platform/specs/services/outbound-service/external-integrations.md` → 실재 ✓
  - `ls rules/traits/integration-heavy.md` → 실재 (depth 5-up = `../../../../../rules/traits/integration-heavy.md`)
- CI = path-filter (TASK-MONO-074/075) → markdown-only path 로 ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~155 의 single-PR closure 답습 — ready → done (또는 ready → in-progress → done) lifecycle.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- BE-152 § "candidate inventory" #1 closure (잔여 #2 database-design.md + #3 LOT_INACTIVE = 본 task 로 #1+#3 동시 closure + #10 gateway route status sync, #2 만 잔존).
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (zero-state spec authoring + § Open Items multi-item status sync, factual verification + sibling pattern adaptation).
