# TASK-BE-302 — inventory-service architecture.md systemic-pattern reconciliation

Status: ready

## Goal

`projects/wms-platform/specs/services/inventory-service/architecture.md` 의 두 문구를 established + reviewed-and-approved convention 과 정렬한다 (Tier 2 spec-correction; impl 변경 0):

- **L165 Layer Rules #3** — "Adapters depend inward. They implement outbound ports or call inbound ports." 가 strict 해석 시 inbound adapter (`adapter/in/messaging/...`) 가 outbound port 호출하지 못한다고 읽힐 수 있으나, **7/7 consumer** (`PutawayCompletedConsumer` + `MasterLocationConsumer` + `MasterLotConsumer` + `MasterSkuConsumer` + `PickingCancelledConsumer` + `PickingRequestedConsumer` + `ShippingConfirmedConsumer`) 모두 `EventDedupePort` 또는 `ReservationRepository` 등 outbound port 직접 사용. **TASK-BE-027 review APPROVED** 가 이 패턴을 명시적으로 정당화 ("Layered-idempotency Javadoc explains outer (eventId) + inner (terminal-state / pickingRequestId) guards"; eventId dedupe row 가 consumer-side TX 에 join 해야 하므로 application service 거쳐 두 번 wrap 하는 것은 비효율 + Propagation.MANDATORY 의도와 부합 안 함). spec wording 을 loose 해석 (= "adapters depend inward — outbound port 또는 inbound port 호출 모두 inward 방향이며 합법") 으로 명시화한다.
- **L433 Security #2** — "Authorization in the application layer — not in controllers." 가 strict 해석 시 controller-level `@PreAuthorize` 가 위반으로 읽히나, **5/5 controller × 17 endpoint** 모두 `@PreAuthorize` 사용 (`AdjustmentController` + `ReservationController` + `TransferController` + `InventoryQueryController` + `MovementQueryController`). `AdjustmentController.java:47` + `ReservationController.java:42` 가 inline-doc 으로 "Method-level @PreAuthorize mirrors the contract's role table" 명시. **TASK-BE-028 review APPROVED** 가 layered-auth 패턴 정당화: route-level ROLE-baseline 은 controller `@PreAuthorize`, fine-grained invariant-tied authorization (예: bucket=RESERVED requires `INVENTORY_ADMIN`) 은 application service 의 programmatic check. spec wording 을 layered pattern 으로 명시화한다.

### Approach

세 가지 옵션 중 **Option A (spec wording 명시화)** 채택:

- **A**: architecture.md L165 + L433 두 문구를 established convention 명시 (single PR, refactor-spec, 안전). ← 채택
- **B**: 본격 refactor-code 로 7 consumer + 17 endpoint 전체 application-layer 화. ≥6 PR + medium-high risk + saga review. BE-027/028 의 APPROVED 결정을 뒤집는 격이라 거부.
- **C**: 다른 scope 으로 redirect (Cohort B L5 long-method polish / gateway dry-run). 본 conflict 는 미해결 채로 남아 미래 refactor cycle 에서 동일 over-zealous 분류 재발 가능. 채택 안 함.

## Scope

In:

- `projects/wms-platform/specs/services/inventory-service/architecture.md` L165 Layer Rules #3 amendment — outbound port 호출이 inward 방향임을 명시, eventId dedupe wiring 의 TX-join 요구사항을 합법 패턴으로 인정.
- `projects/wms-platform/specs/services/inventory-service/architecture.md` L433 Security bullet 2 amendment — route-level `@PreAuthorize` (role-baseline, contract role table mirror) + application-layer programmatic check (invariant-tied) 의 layered auth pattern 명시.

Out:

- impl 변경 0 (production code byte-unchanged).
- 다른 service 의 architecture.md (master / inbound / outbound / admin) 동일 문구 점검은 별 task (BE-303 후보).
- consumer / controller 의 어떤 코드 변경도 본 task 의 scope 외.
- Reservation TTL job / 새 endpoint / 새 event 등 어떤 feature 작업도 scope 외.

## Acceptance Criteria

AC-1. architecture.md L165 Layer Rules #3 가 다음과 같이 amend:

> 3. **Adapters depend inward.** Inbound adapters call inbound ports (use cases) and may additionally call outbound ports when the call must join the same TX as the inbound dispatch (e.g., `EventDedupePort` write keyed by Kafka `eventId` with `Propagation.MANDATORY`; see TASK-BE-027 saga). Outbound adapters implement outbound ports. Adapter-internal types (JPA entities, Kafka records) never leak into ports.

AC-2. architecture.md L433 Security bullet 2 가 다음과 같이 amend:

> - Authorization is layered: (a) controller-level `@PreAuthorize` enforces the **role baseline** (mirrors the role table in `specs/contracts/http/inventory-service-api.md`); (b) application-layer programmatic checks enforce **invariant-tied authorization** (e.g., `bucket=RESERVED` adjustment requires `INVENTORY_ADMIN` — see TASK-BE-028). Direct `jwt.getClaim("role")` parsing in controllers is forbidden; role extraction goes through `Authentication.getAuthorities()` populated by `SecurityConfig`.

AC-3. production code (`apps/inventory-service/src/main/`) 변경 0 — `git diff main -- apps/inventory-service/src/main/` 결과가 빈 diff.

AC-4. 다른 spec file 의 cross-reference 무변경 — `grep -rn "Authorization in the application layer" projects/wms-platform/specs/` + `grep -rn "Adapters depend inward" projects/wms-platform/specs/` 가 inventory-service/architecture.md 만 매치하고 다른 spec 위치는 변경 없음. (cross-product drift 없음 확인.)

AC-5. test 변경 0 — `git diff main -- apps/inventory-service/src/test/` 결과가 빈 diff.

## Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md` (대상)
- `projects/wms-platform/specs/contracts/http/inventory-service-api.md` (role table 참조 — 변경 없음)
- `projects/wms-platform/tasks/done/TASK-BE-027-eventid-dedupe-on-outbound-consumers.md` (Layer Rules amendment 근거)
- `projects/wms-platform/tasks/done/TASK-BE-028-reserved-bucket-admin-guard-relocation.md` (Security amendment 근거)
- `platform/architecture-decision-rule.md` (architecture.md 가 single source of truth)
- `rules/traits/transactional.md` § T8 (eventId dedupe wiring 의 spec-mandated 요구사항)

## Related Contracts

본 task 는 contract 변경 0. inventory-service-api.md role table 은 `@PreAuthorize` 와 일치하므로 unchanged.

## Edge Cases

- **다른 service 가 같은 문구 사용 중**: master / inbound / outbound / admin / notification 의 architecture.md 도 동일 또는 유사 문구가 있을 수 있음. AC-4 grep 으로 확인. 발견 시 본 task scope 외 (별 task 후보 = BE-303 cross-service spec sweep).
- **TASK-BE-027/028 의 review verdict 변경**: BE-027/028 의 APPROVED 가 미래에 뒤집힐 경우 본 task 의 amendment 도 revert 가능. 현 시점 (2026-05-27) 에서는 두 task 모두 main DONE 으로 안정.
- **`adapter/in/web/JwtHelper`**: BE-301 에서 도입된 `JwtHelper.actorId(Jwt)` 는 actor-identification 만 (authorization 아님) — Security bullet 2 amendment 와 무관, byte-unchanged.

## Failure Scenarios

- **spec PR open 전 직접 amend**: HARDSTOP-05 위반 가능. ready/ 진입 → spec PR open 순서 유지.
- **architecture.md 다른 섹션 byte-drift**: AC-1/AC-2 두 위치 외 변경 시 unintended scope. minimal diff 유지.
- **다른 service architecture.md 도 동시 amend 시**: cross-service 변경은 별 task 로 분리, 본 task 는 inventory-service 한정.

## Approach Notes (impl 참고)

- L165 amendment: outbound port 호출이 inward 방향임을 명시하되 "TX-join 요구 시" 라는 정당화 조건 추가. 일반적 use-case orchestration 은 여전히 inbound port 거치도록 유도 (spec drift 가 anything-goes 로 미끄러지지 않게).
- L433 amendment: (a) role-baseline + (b) invariant-tied 의 두 단계 layered 명시. BE-028 의 `Set<String> callerRoles` command-extension 패턴 (Pattern B) 을 implicit 정당화.
- 두 amend 모두 sibling task ID (BE-027, BE-028) cross-reference 추가 — 미래 reviewer 가 reasoning 을 트레이스 가능.

## 분석/구현 권장 모델

- 분석=Opus 4.7 (verification + spec wording precision)
- 구현 권장=Sonnet 4.6 (single-file 2-line amend, mechanical)
