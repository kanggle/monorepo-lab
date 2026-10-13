# Task ID

TASK-BE-634

# Title

감사 행 `idempotency_key`(`VARCHAR(100)`) 넘침 — 파트너십 참여자 추가/제거 키는 **계산상 항상 넘친다**(130자) · 같은 패턴의 형제 전수 · 넘치면 fail-closed 감사가 명령 전체를 `500 AUDIT_FAILURE` 로 만든다

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(키 모양 수선) — AC-0 IT 설계만 Opus 검토
> 출처: `TASK-BE-631` 슬라이스 b CI(#4343) — «이 회사에서 내보내기» 가 `Data too long for column 'idempotency_key'` 로 500. 그 자리는 BE-631 에서 고쳤다(`ManageOperatorAssignmentUseCase.auditIdempotencyKey` — 최대 87자). 이 티켓은 **같은 모양의 형제**다.

# Dependency Markers

- **선행**: 없음. `TASK-BE-631` 의 수선 · 시험(`AssignmentAuditIdempotencyKeyTest`)이 본보기.

# Goal

admin-service 의 여러 유스케이스가 감사 행 `idempotency_key` 를 `"<prefix>:" + <ActionCode> + ":" + <식별자들> + ":" + <millis>` 로 손으로 만든다. 칸은 `admin_actions.idempotency_key VARCHAR(100)`(`V0001__create_admin_actions.sql:10`)이고, 넘치면 감사 쓰기가 실패하고 fail-closed 규약대로 **명령 전체가 `500 AUDIT_FAILURE` 로 중단**된다. 단위 시험은 감사기를 목으로 대체해 칸 길이에 닿지 않으므로 이 결함은 통합 시험에서만 보인다.

🔴 **계산상 확정적인 것 하나 — 시험으로는 아직 증명되지 않았다**: `ManagePartnershipParticipantUseCase.java:162` = `"participant:" + code.name() + ":" + partnershipId + ":" + operatorId + ":" + millis` — `partnershipId` 는 UUID(`V0039…sql:22` `VARCHAR(36)`) · `operatorId` 는 UUID ⇒ 12 + 27/30 + 1 + 36 + 1 + 36 + 1 + 13 = **127 / 130자 > 100** ⇒ 참여자 추가 · 제거 API 가 **언제나** 500 이어야 한다. 그 API 를 실제로 부르는 통합 시험이 **없다**(`grep '/participants' src/test` = 0).

# Scope

## In Scope

- AC-0 IT: 참여자 추가 API 를 실제로 부른다 → 지금 500 `AUDIT_FAILURE` 인지(빨강 먼저). 아니면 위 계산의 전제(식별자 형태)를 다시 잰다.
- 형제 전수: admin-service 에서 `idempotency_key` 로 들어가는 문자열을 만드는 모든 자리(`grep '":" + .*toEpochMilli'` 등 — 2026-10-14 실측 8 곳: identity-link · org-scope · participant · partnership · identity-unlink · admin-profile-update · self-profile-update · assignment(BE-631 수선 완료))의 **최대 길이**를 각 입력 칸 길이로 계산한 표.
- 넘칠 수 있는 자리 수선 — 감사 행에 이미 있는 `action_code` 를 키에서 빼는 BE-631 방식 우선(유일 색인 = `(actor_id, action_code, idempotency_key)`), 그래도 넘치면 고정 길이 축약.
- 가드: 한 시험이 모든 키 빌더의 최대 길이 ≤ 100 을 단언(새 빌더가 생기면 그 시험에 들어가야 하는 이유를 시험 javadoc 에).

## Out of Scope

- 칸을 넓히는 마이그레이션(`admin_actions` 는 감사 행 불변 트리거가 있다 — 바꾸려면 별도 판단).

# Acceptance Criteria

- [ ] **AC-0** — 참여자 추가 IT 가 수선 전 500 `AUDIT_FAILURE`(실패 쪽 먼저) — 또는 계산 전제가 틀렸음을 실측으로.
- [ ] **AC-1** — 형제 8 곳 최대 길이 표(입력 칸 길이 근거 포함).
- [ ] **AC-2** — 넘치는 자리 수선 · 같은 IT 가 201/204.
- [ ] **AC-3** — 모든 키 빌더 최대 길이 ≤ 100 단언 시험.

# Related Specs

- `projects/iam-platform/specs/services/admin-service/data-model.md` § admin_actions
- `TASK-BE-631` § 슬라이스 b 기록(발견 경위)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § partnerships participants (응답 코드 무변경 — 500 이 계약 밖의 결과였다)

# Edge Cases

- 같은 밀리초 안의 같은 행위 두 번 — 유일 색인 충돌(지금도 같은 성질, 바꾸지 않는다).

# Failure Scenarios

1. 단위 시험만 고친다 — 목 감사기는 칸 길이를 모른다(이 결함이 지금까지 숨은 이유).
2. 키를 해시로 바꾸며 운영 중 같은 행위의 재시도 판정이 바뀐다 — 키는 이 행위들에서 재시도 판정에 쓰이지 않는지 먼저 확인.

---

# Implementation Record (2026-10-14 UTC)

## AC-0: red first (measured)

- I added `PartnershipCrossOrgLeakIntegrationTest#participantApi_addThenRemove_auditRowsLand` and pushed **the test alone, with no fix** to #4345 (commit `test(iam): TASK-BE-634 AC-0`).
- CI `Integration (iam A, Testcontainers)` reported **255 tests completed, 1 failed**, and the single failure was this test:
  `[add body: {"code":"AUDIT_FAILURE","message":"Audit write failed; command aborted",…}]` ⇒ **adding a participant returned 500 `AUDIT_FAILURE`**.
- ⚪ This IT does not print the cause chain, so the log does not show `Data too long` itself. The cause is attributed by the counter-measurement below: the next commit changes only the key shape, and the same IT turns 201/204 (AC-2).

## AC-1: maximum length of the 8 siblings (widest legal input)

Input widths:
- `admin_operators.operator_id`: `VARCHAR(36)` (`V0004:44`)
- `tenant_partnership.partnership_id`: `VARCHAR(36)` (`V0039:22`)
- assignment and org-scope `tenant_id`: `VARCHAR(32)` (`V0030:28`)
- epoch millis: 13 digits (until 2286)
- the longest `PARTNERSHIP_*` action name: `PARTNERSHIP_REACTIVATE`, 22 characters

| Builder | Before | After | Maximum after |
|---|---|---|---|
| assignment | 87 (BE-631 fix) | unchanged | 87 |
| org-scope | `org-scope:`+36+1+32+1+13 = **93** | byte-unchanged | 93 |
| 🔴 participant | `participant:`+27/30+1+36+1+36+1+13 = **127 / 130** | `ptc:`+36+1+36+1+13 | **91** |
| partnership | 12+22+1+36+1+13 = **85** | byte-unchanged | 85 |
| identity-link | 14+36+1+13 = 64 | byte-unchanged | 64 |
| identity-unlink | 16+36+1+13 = 66 | byte-unchanged | 66 |
| admin-profile-update | 21+36+1+13 = 71 | byte-unchanged | 71 |
| self-profile-update | 20+36+1+13 = 70 | byte-unchanged | 70 |

Only the participant key overflowed. The other 7 produce byte-identical output; the code just moved into one class.

🔵 **Another kind found outside this table, not fixed here, filed as `TASK-BE-635`:** keys the **client** sends.
- 7 `Idempotency-Key` headers have no `@Size`.
- Bulk lock: `header(≤64)+":"+accountId(36)` = 101 at the boundary. This key is also the downstream retry key, so its shape is not changed casually.

## AC-2: fix

- New `AuditIdempotencyKeys` (package-private). All 8 builders now go through this class.
- The participant key drops the action name (`ptc:<partnershipId>:<operatorId>:<millis>`). The row already carries the action name in `action_code`, and the unique index is `(actor_id, action_code, idempotency_key)`.
- No key is read back for a retry decision; every one ends in the instant.

## AC-3: guard

- `AuditIdempotencyKeysTest`:
  - (a) Feeds every builder the widest input and asserts ≤ 100. The partnership key is checked for **every** `PARTNERSHIP_*` code.
  - (b) A control: the old participant forms measure 127 and 130.
  - (c) The keys still separate their subjects.
  - (d) Scans `src/main/java` and **fails on any line outside the builder class that is both `":"`-joined and `toEpochMilli()`**.
- Bite check for (d): applying the same predicate to the `origin/main` tree catches **all 8 old builders**.
- `AssignmentAuditIdempotencyKeyTest` (BE-631) is folded into this test.
