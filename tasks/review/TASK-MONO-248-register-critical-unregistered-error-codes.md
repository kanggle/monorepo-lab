# Task ID

TASK-MONO-248

# Title

미등록 Critical 에러코드 표면 일괄 등록 — scm demand-planning(6) + fan membership(5) + ILLEGAL_STATE + iam SELF_PROFILE + wms ALERT_NOT_FOUND(계약 정정 포함). 포트폴리오 registry drift Critical 정리 (doc-only)

# Status

review

# Owner

monorepo (root tasks/ — shared `platform/error-handling.md` + wms contract)

# Task Tags

- contract-change

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: iam/ecommerce/finance 정합성 작업 후, 나머지 5영역(wms/scm/erp/fan/console) read-only 조사에서 발견한 **systemic registry drift** 중 Critical(referenced-but-unregistered, live emitter)만. emit 출처·status·예외클래스 전부 코드 확인 완료(green-wash 0).
- **scope placement**: shared `platform/error-handling.md` → root tasks/. 전 코드가 같은 registry 파일을 건드리므로 1 atomic task(병렬 PR 시 충돌 회피). wms 계약 1건 동반.
- **deferred (별도 Warning 배치)**: dead/ghost 행(wms PICKING_QUANTITY_EXCEEDED/EXTERNAL_TIMEOUT/ORDER_NO_DUPLICATE, fan ARTIST_INVALID_STATE/MEMBERSHIP_TIER_INSUFFICIENT, scm SNAPSHOT_NOT_FOUND), status 불일치(wms TRANSFER_SAME_LOCATION/RESERVATION_NOT_FOUND), cross-note(erp/scm/wms), console BFF wire코드(TIMEOUT/NO_ACTIVE_TENANT/MISSING_PREREQUISITE), console CIRCUIT_OPEN(forward-compat reserved, BFF CB 미배선 — broken 아님), erp IDEMPOTENCY_STORE_UNAVAILABLE(fintech 섹션에 등록은 됨, 계약 "not v1-emitted" drift) — 전부 본 task out of scope.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (registry 정합).

---

# Goal

코드가 live emit 하나 `platform/error-handling.md` 에 미등록된 Critical 에러코드 표면을 등록하여(파일 자체 "사용 전 등록" Change Rule 충족), 계약↔코드 불일치(wms ALERT_NOT_FOUND) 1건을 함께 정정한다. 전부 doc-only, production code 변경 0.

---

# Scope (emit 출처·status 코드 확인 완료)

**WI-1 — scm `## Demand Planning [domain: scm]` 섹션 신설.** `demand-planning-service GlobalExceptionHandler` emit:
- `SUGGESTION_NOT_FOUND`(404), `POLICY_NOT_FOUND`(404), `MAPPING_NOT_FOUND`(404), `INVALID_SUGGESTION_STATE`(422), `SKU_SUPPLIER_UNMAPPED`(422), `PROCUREMENT_UNAVAILABLE`(503). (CONCURRENT_MODIFICATION 409 은 Platform-Common, 등록 불요.)

**WI-2 — fan `## Membership [domain: fan-platform]` 섹션 신설.** `membership-service GlobalExceptionHandler` emit:
- `MEMBERSHIP_NOT_FOUND`(404), `PAYMENT_DECLINED`(422), `MEMBERSHIP_TIER_INVALID`(422), `MEMBERSHIP_NOT_RENEWABLE`(422), `MEMBERSHIP_STATE_INVALID`(422, `InvalidStateTransitionException`). (IDEMPOTENCY_KEY_CONFLICT 409 은 등록된 alias, 불요.)

**WI-3 — `ILLEGAL_STATE`(422) Platform-Common `## General` 등록.** scm procurement + fan 4서비스 `AbstractDomainExceptionHandler` 의 `IllegalStateException` catch-all. 설명에 "domain code/`STATE_TRANSITION_INVALID` 우선, unclassified fallback" 명시.

**WI-4 — iam `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`(400) `## Admin [domain: saas]` 등록.** `admin-service AdminExceptionHandler:278` emit, platform-console 계약이 참조.

**WI-5 — wms `ALERT_NOT_FOUND`(404) `## Admin [domain: wms]` 등록 + 계약 정정.** `admin-service AlertNotFoundException` emit(WebMvcTest 단언). `admin-service-api.md:349` 가 alert ack 엔드포인트 404 를 `NOT_FOUND` 로 잘못 게시 → `ALERT_NOT_FOUND` 로 정정(계약↔코드 일치).

## Out of Scope

- 위 Dependency Markers 의 deferred Warning 전부.
- production code 변경(전 항목 doc-only; 코드는 이미 올바른 문자열 emit).
- erp/console Critical-처럼 보였으나 재분류된 건(console CIRCUIT_OPEN=forward-compat reserved; erp IDEMPOTENCY_STORE_UNAVAILABLE=등록은 되어 있음) — Warning 배치.

---

# Acceptance Criteria

- [x] **AC-1 (WI-1)**: `## Demand Planning [domain: scm]`(L447) + 6코드 등록. emit 확인(`GlobalExceptionHandler:30/36/42/48/54/74`).
- [x] **AC-2 (WI-2)**: `## Membership [domain: fan-platform]`(L612) + 5코드 등록. emit 확인(`GlobalExceptionHandler:40/46/52/58/73`).
- [x] **AC-3 (WI-3)**: `ILLEGAL_STATE`(422, L133) General 등록 + fallback 우선순위 노트.
- [x] **AC-4 (WI-4)**: `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`(400, L547) Admin[saas] 등록.
- [x] **AC-5 (WI-5)**: `ALERT_NOT_FOUND`(404, L246) Admin[wms] 등록 + `admin-service-api.md:349` alert-ack 404 = `ALERT_NOT_FOUND`.
- [x] **AC-6 (scope-lock)**: 변경 = `platform/error-handling.md` + wms `admin-service-api.md` + task lifecycle. production code 0.
- [x] **AC-7 (green-wash 0)**: 등록 11코드 전부 핸들러 emit + HTTP status 코드 확인(§Verification).

---

# Related Specs

- `platform/error-handling.md` — shared registry(WI-1~5).
- scm `demand-planning-service` GlobalExceptionHandler / `demand-planning-api.md`.
- fan `membership-service` GlobalExceptionHandler / `membership-api.md`.
- iam `admin-service` AdminExceptionHandler.
- wms `admin-service` AlertNotFoundException / `specs/contracts/http/admin-service-api.md` (WI-5 계약 정정).

# Related Contracts

- wms `admin-service-api.md` alert-ack 404 코드 정정(계약을 emit 에 정렬; 실 동작 무변경).

---

# Edge Cases

- **ILLEGAL_STATE vs STATE_TRANSITION_INVALID 중복처럼 보임** — ILLEGAL_STATE 는 generic IllegalStateException fallback(422), STATE_TRANSITION_INVALID 는 명시적 state-machine 코드. 노트로 우선순위 명시.
- **MEMBERSHIP_STATE_INVALID(fan) vs POST_STATUS_TRANSITION_INVALID(fan community)** — 같은 `InvalidStateTransitionException` 클래스를 서비스별로 다른 코드로 매핑. 도메인별 의도, 둘 다 등록.
- **Demand Planning / Membership 섹션 배치** — 각 도메인 기존 섹션 인접(scm: Inventory Visibility 뒤 / fan: Artist 뒤).

# Failure Scenarios

- **emit 미확인 코드 등록** → green-wash. 전 코드 핸들러에서 확인 완료(AC-7).
- **wms ALERT 계약만 고치고 registry 누락(또는 반대)** → 부분 정합. WI-5 는 둘 다.
- **production code/다른 파일 수정** → AC-6 fail.

---

# Verification

- 미수행(ready). emit 확인 완료: scm DP(GlobalExceptionHandler:27-76), fan membership(37-75), ILLEGAL_STATE(AbstractDomainExceptionHandler:60-64=422), iam SELF_PROFILE(AdminExceptionHandler:275-279=400), wms ALERT(AlertNotFoundException:7 + WebMvcTest:144).
- CI: registry/계약 `.md` only → fast-lane GREEN 예상(코드 무변경).
- 분석=Opus 4.8 / 구현=Opus 4.8.
