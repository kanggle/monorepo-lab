# Task ID

TASK-MONO-308

# Title

`ADR-MONO-043 PROPOSED` — Notification architecture unification (four per-domain notification-service silos → shared contract + lifted consumer/delivery library + console-bff aggregator)

# Status

done

# Owner

architect

# Task Tags

- adr
- architecture
- notification
- platform-console
- cross-project

---

# Goal

The monorepo has **four independently-bootstrapped per-domain notification-services** (`erp`, `ecommerce`, `wms`, `fan`), each a near-isolated silo with its own REST path convention, auth/recipient model, schema, idempotency store, external-channel set, and UI consumer (code-verified 2026-06-26, four-service topology sweep). The platform-console **shared-shell** header bell is wired to **exactly one** of them (erp), so (a) only erp approval notifications ever reach the bell, and (b) every console page hard-depends on the erp gateway's availability — surfaced as the 2026-06-26 incident where a downed `erp-gateway` made the bell 503 on `/wms/outbound` and every other page.

Unifying this estate is **not** a mechanical refactor: deciding whether to merge vs keep-four-behind-a-contract, where a shared notification envelope/inbox contract lives, what consumer/dedupe/DLT/delivery machinery is lifted into `libs/`, and how the console gets "one bell" without recoupling to a single upstream — each bakes ownership/contract/failure-isolation postures. Doing it in code without a record → **HARDSTOP-09**.

This task authors **`ADR-MONO-043 PROPOSED`** following the staged-child PROPOSED pattern (ADR-MONO-016/017/038): D1–D8 with each a CHOSEN-PROPOSED direction + Rejected options + "finalised at ACCEPTED" annotation. **ADR body authoring only** — ACCEPTED transition + the shared-contract/library/aggregator implementation are all separate post-ACCEPTED tasks.

**근거 메모**: auto-memory `env_console_erp_gateway_and_notification_wiring` (the incident + the four-service split), and the 2026-06-26 topology sweep recorded in ADR-043 § 1.

---

# Scope

## In Scope

| 산출물 | 위치 | 설명 |
|---|---|---|
| 새 ADR | `docs/adr/ADR-MONO-043-notification-architecture-unification.md` | PROPOSED 본문. ADR-MONO-038 헤더 패턴 답습: Title / Status=PROPOSED / History (PROPOSED 라인 — 본 task ID + PR placeholder) / Builds on (incident + topology sweep) / Related (ADR-016/017/004/038/005 + console-integration-contract). 본문 = §1 Context (4-service 토폴로지 + shared-shell coupling smell + HARDSTOP-09 근거) / §2 Decision (D1–D8 option tables) / §3 Consequences (hard invariants + scope-out + future-self) / §4 Alternatives / §5 Relationship / §6 Verification/Audit / §7 Outstanding follow-ups. |
| ADR-MONO-016 additive pointer | `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` | § D3 ("notification first increment") 영역에 **additive** forward-pointer to ADR-043 (cross-domain 일반화). **§ D3 본문 byte-unchanged** (HARDSTOP-04 — additive 한 줄만). |
| INDEX 갱신 | `tasks/INDEX.md` ready 섹션 | 본 task 한 줄 entry 등재. |

## Out of Scope

- ADR-043 의 PROPOSED → ACCEPTED transition (별 task — 사용자 명시 intent gate; ADR-016/017/038 의 ACCEPTED-flip task 패턴 답습).
- 공유 notification 계약 spec 작성 (post-ACCEPTED, D7 phase 1).
- `libs/` consumer/delivery/channel-SPI 라이브러리 추출 (post-ACCEPTED, D7 phase 1).
- 도메인별 conformance (erp/ecommerce/wms/fan 4 project tasks, post-ACCEPTED, D7 phase 2 — wms inbox-vs-delivery-only 결정 포함).
- `console-bff` aggregator + 콘솔 벨 rewire (post-ACCEPTED, D7 phase 3).
- 어떤 도메인 producer spec/코드 변경 (zero-retrofit invariant — 위반 시 PROPOSED 재논의).
- `docs/adr/INDEX.md` 갱신 — 012a 이후 stale (013–042 미등재)인 de-facto 컨벤션 따라 손대지 않음.

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-043-notification-architecture-unification.md` 존재. Status=`PROPOSED`. History (PROPOSED 라인 — TASK-MONO-308 + PR placeholder + "CHOSEN-PROPOSED, finalised at ACCEPTED, self-ACCEPT prohibited"). Builds on (incident + topology sweep). Related = ADR-016/017/004/038/005 + console-integration-contract § 2.4.5–8.
- [ ] §2 Decision 에 **D1–D8** 8개 option table. 각 D 는 (a) CHOSEN-PROPOSED 방향 + (b) Rejected option 1–3 + rationale + (c) ACCEPTED-finalise 함의. 골격:
  - **D1** — 4 서비스 유지 + 공유 contract/library 로 통합 (merge 거부).
  - **D2** — `console-bff` aggregator 로 per-domain inbox fan-in → 단일 벨 (erp-only / 중앙 store 거부).
  - **D3** — 도메인-무관 notification envelope + inbox REST 계약 (3 path 컨벤션 / 단일 auth 강제 거부).
  - **D4** — consumer/dedupe/DLT/Category-C/channel-SPI 를 `libs/` 로 lift (composition-over-inheritance; ADR-038 posture).
  - **D5** — aggregator failure isolation = hard invariant (one domain down ≠ whole bell down).
  - **D6** — per-domain credential/tenant posture 보존 (정규화 아님; ADR-017 D4 invariant).
  - **D7** — phasing (shared-first → conformance ×4 → aggregator) + ownership (root vs project).
  - **D8** — ACCEPTED transition mechanics (staged-child, self-ACCEPT 금지, byte-unchanged finalise).
- [ ] §3 Consequences 의 hard invariants 에 zero-retrofit / per-domain credential 보존 / failure isolation / domain ownership / ADR-016 § D3 byte-unchanged 명시.
- [ ] §3 의 "does NOT do" 에 계약/라이브러리/서비스/aggregator 미구현 (spec-only, HARDSTOP-09 회피) + wms inbox-vs-delivery-only deferred 명시.
- [ ] §3.3 Future-self ACCEPTED 실행 체인 4 step sketch.
- [ ] `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D3 영역에 **additive** forward-pointer 한 줄 (§ D3 본문 byte-unchanged — HARDSTOP-04).
- [ ] `tasks/INDEX.md` ready 섹션에 본 task 한 줄 entry.
- [ ] **HARDSTOP-04 검증**: `git diff docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` 가 additive 한 줄 외 0 변경.
- [ ] **HARDSTOP-09 회피**: PROPOSED 머지가 구현을 authorise 하지 않음 — spec-only PR (impl code 0).

---

# Related Specs

- [ADR-MONO-016 § D3](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) — erp notification "first increment" (본 ADR 가 일반화; additive pointer target).
- [ADR-MONO-017 D4/D7](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) — per-domain credential + fan-out attribution (D2/D6 reuse base).
- [ADR-MONO-038](../../docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md) — shared-abstraction lift precedent + posture (D4 template).
- [ADR-MONO-004](../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md) — `libs/java-messaging` (D4 host neighbourhood).
- [ADR-MONO-005](../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) — DLT / Category-C delivery taxonomy (D4 must preserve).
- [console-integration-contract.md § 2.4.5–8](../../projects/platform-console/specs/contracts/console-integration-contract.md) — per-domain credential rule (D6 invariant).

# Related Skills

- `.claude/skills/common/architect/` — ADR drafting + staged-child PROPOSED pattern.

# Related Contracts

- ADR 가 contract 가 아니라 architectural decision — 신규 notification 계약(D3)은 post-ACCEPTED 산출물.

---

# Implementation Notes

## Staged-child PROPOSED 패턴 (ADR-016/017/038 답습)

| Stage | 산출물 | 본 task 범위 |
|---|---|---|
| **PROPOSED** | D1–D8 frame + CHOSEN-direction + downstream sequencing + ADR-016 additive pointer | **본 task** |
| ACCEPTED transition | Status PROPOSED→ACCEPTED, History/§6 ACCEPTED row, byte-unchanged D1–D8 finalise | 별 task |
| Execution | 계약 spec + libs 추출 + 4 도메인 conformance + console-bff aggregator | 별 task series (post-ACCEPTED, D7 phase 1–3) |

## 4-service 토폴로지 (ADR §1 의 근거, 2026-06-26 sweep)

| 서비스 | consume | producer | REST | UI | terminal | 외부채널 |
|---|---|---|---|---|---|---|
| erp | `erp.approval.*` 6 | approval-service | `/api/erp/notifications` | console 헤더 벨 | terminal | Slack |
| ecommerce | order.placed / payment.completed / shipping.status-changed / **account.created** | order/payment/shipping + iam/account | `/api/notifications` (+/templates) | web-store(고객) + console(템플릿 어드민) | terminal | email |
| wms | `wms.inventory/inbound/outbound.*` 6 | inventory/inbound/outbound | **없음** | 없음 | **re-emit** `wms.notification.delivered.v1` | Slack/none |
| fan | `fan.membership.*` 3 | membership-service | `/api/fan/notifications` | fan-platform-web | terminal | email+FCM |

## ADR 본문 작성 dispatch

본 ADR 는 4-service 정합 + 5 ADR(016/017/004/038/005) 참조 + console-bff aggregator 설계 → **Opus 권장**. architect agent dispatch 가능; agent prompt 골격:
```
You are drafting ADR-MONO-043 PROPOSED. Read ADR-MONO-016/017/038/004/005
verbatim. Author docs/adr/ADR-MONO-043-notification-architecture-unification.md
following the ADR-038 staged-child PROPOSED pattern. D1–D8 per this task's AC.
ADR-016 § D3 stays byte-unchanged — additive forward-pointer only.
Spec-only: no contract/library/service/aggregator code.
```

## Spec PR shape

본 PR 산출물: `docs/adr/ADR-MONO-043-...` 신규 + `docs/adr/ADR-MONO-016-...` additive pointer 1줄 + `tasks/ready/TASK-MONO-308-...` 본 task md + `tasks/INDEX.md` ready entry. **impl code 0** (PR Separation Rule). branch `task/mono-308-adr-043-notification-unification-proposed` (substring `main`/`master` 회피 ✓).

---

# Edge Cases

- **ADR-016 § D3 본문에 손이 가야 하는 case** → HARDSTOP-04. additive forward-pointer 로 부족하면 PROPOSED 단계 STOP + ADR-043 가 ADR-016 decision 을 amend 하는 형태로 재논의.
- **merge(단일 서비스)가 더 낫다는 판단** → D1 의 CHOSEN-PROPOSED 재선정. 단, 4 도메인 audience 의미 차이(operator/customer/internal/fan) 가 merge 의 cost 라는 § 1 근거를 반박해야 함.
- **per-domain credential 정규화 압력** → D6 invariant(ADR-017 D4 + console-integration-contract § 2.4.5–8) 위반. shape 만 공유, auth 는 도메인 소유 유지.
- **wms 가 inbox surface 를 가져야 하는지 PROPOSED 에서 확정 시도** → § 3.2 대로 D7 phase-2 conformance 로 defer (PROPOSED 가 미리 못박지 않음).
- **producer spec 변경 필요** → zero-retrofit invariant 위배. D3/D4 CHOSEN 재검토.

---

# Failure Scenarios

- **HARDSTOP-04 위반** (ADR-016 § D3 본문 변경): commit 직전 `git diff docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` 로 additive 한 줄 외 byte-unchanged 검증.
- **D-decision Rejected rationale 누락**: ADR-038 의 모든 D 가 rejected option rationale 명시 — 본 ADR 도 동형. 누락 시 review fail.
- **§1 토폴로지 evidence 누락**: 4-service consume/producer/REST/UI/terminal/channel 표 + shared-shell coupling 근거(layout.tsx:110 벨 + 503 incident) 명시. 누락 시 review fail.
- **CHOSEN-PROPOSED 가 invariant 위배** (e.g. D2 aggregator 가 per-domain credential rewrite): § 3.1 hard invariants 불가 → CHOSEN 재선정.
- **staged-child 패턴 이탈** (PROPOSED 에서 모든 mechanics 확정 / self-ACCEPT): ADR-016/017/038 sibling pattern 과 drift. D8 가 ACCEPTED-finalise 를 명시해 자기검증.

---

# Test Requirements

ADR=spec → 단위/통합 테스트 0. 검증:

- [ ] markdown lint (CI path-filter markdown fast-lane) pass.
- [ ] ADR-016 § D3 가 `git diff` 에서 additive 1줄 외 0 변경 (HARDSTOP-04).
- [ ] ADR-043 §1~§7 모두 존재 (manual section count).
- [ ] §2 D-decision count == 8.
- [ ] §3 hard invariants 에 zero-retrofit + per-domain credential + failure isolation + domain ownership + ADR-016 byte-unchanged 명시.
- [ ] PROPOSED PR 에 impl code 0 (spec-only — HARDSTOP-09 회피).

---

# Definition of Done

- [ ] `docs/adr/ADR-MONO-043-notification-architecture-unification.md` 작성 완료 (Status=PROPOSED, D1–D8, §1~§7).
- [ ] `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` § D3 additive forward-pointer 1줄.
- [ ] HARDSTOP-04 검증 (ADR-016 § D3 byte-unchanged).
- [ ] `tasks/INDEX.md` ready entry 등재.
- [ ] commit + push (branch `task/mono-308-adr-043-notification-unification-proposed`).
- [ ] PR open (사용자 요청 시; ADR-038 PROPOSED PR form 답습).
- [ ] Ready for ACCEPTED transition (별 task; 본 task scope 외).
