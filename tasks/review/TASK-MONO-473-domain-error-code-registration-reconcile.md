# Task ID

TASK-MONO-473

# Title

도메인 규칙 에러코드 20개 레지스트리 미등록 gap 화해 — erp/fan-platform/scm (MONO-472 가드 측정 산물)

# Status

review

# Owner

monorepo (root tasks/ — shared `platform/error-handling.md`)

# Task Tags

- api

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

- **origin**: `TASK-MONO-472` 의 드리프트 가드 **측정**이 발굴 — erp/fan-platform/scm 도메인 규칙파일이 선언한 에러코드 20개가 `platform/error-handling.md` 에 **전무**(pre-existing drift, 등록 전무). wms/ecommerce/fintech/saas 는 clean.
- **선행(prerequisite)**: `TASK-MONO-472`(가드) — 본 task 완료 시 각 도메인을 가드 `UNRECONCILED` 에서 제거하여 편입.
- **execution constraint**: `platform/error-handling.md`(레지스트리) + 각 도메인 규칙파일 검증. **코드별 HTTP status 배정 = platform 계약 결정** → 신중, 도메인 서비스 spec 대조 필수. 사용자/도메인 판단 요소 有.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (status 배정은 계약 결정).

---

# Goal

MONO-472 가 측정한 20개 미등록 도메인 에러코드를 `platform/error-handling.md` 에 등록(또는 실제 미사용 판정 시 도메인 파일에서 제거)하여, 도메인 spec(layer 4) ↔ 레지스트리(layer 2) 드리프트를 해소하고 MONO-472 가드에 3도메인을 편입한다.

---

# Scope

## In Scope — 20 코드 (도메인별), 각 코드에 대해 판정: 등록 vs 제거

**AC-0 (착수 시 재측정 필수)**: 아래 목록은 2026-07-23 MONO-472 가드 측정의 산물 = **가설**. 착수 시 `DOMAIN_ERRCODE_EXCLUDE="" bash scripts/check-domain-error-code-registry.sh`(또는 도메인별) 로 전수 재측정 — 그사이 등록됐거나 도메인 파일이 바뀌었을 수 있음. 코드가 이긴다.

1. **erp (1)**: `READ_MODEL_SOURCE_UNAVAILABLE` — read-model-service 통합조회 원천 응답불가(503 후보).
2. **fan-platform (7)**: `ARTIST_INACTIVE`·`FOLLOW_SELF_FORBIDDEN`·`REACTION_INVALID_EMOJI`·`MEMBERSHIP_EXPIRED`·`MEMBERSHIP_DOWNGRADE_BLOCKED`·`POST_REPORTED_PENDING_REVIEW`·`MODERATION_DECISION_REQUIRED`.
3. **scm (12)**: `SUPPLIER_CONTRACT_EXPIRED`·`SLA_VIOLATION`·`CATALOG_SYNC_TIMEOUT`·`FORECAST_PERIOD_INVALID`·`REORDER_POINT_NEGATIVE`·`FORECAST_DATA_INSUFFICIENT`·`SAFETY_STOCK_BELOW_MINIMUM`·`CARRIER_TIMEOUT`·`ROUTE_UNAVAILABLE`·`ETA_EXPIRED`·`INVOICE_AMOUNT_MISMATCH`·`SETTLEMENT_NOT_READY`.

**코드별 판정 축**: (a) 실제 서비스가 emit/계약에 사용 → 등록(HTTP status 는 semantic + 도메인 spec 근거로 배정: `_NOT_FOUND`→404, `_FORBIDDEN`/`_BLOCKED`→403, `_TIMEOUT`/`_UNAVAILABLE`→503, `_EXPIRED`/`_INVALID`/`_MISMATCH`/`_NEGATIVE`/`_INSUFFICIENT`/`_REQUIRED`→400/422, `_TRANSITION_INVALID`/`_ALREADY_*`→409). (b) spec 상 미구현/미사용 phantom → 도메인 파일에서 제거(MONO-471 OPERATION_NOT_PERMITTED 선례). **status 는 추측 아닌 도메인 spec/서비스 architecture 대조로 확정**.

## Out of Scope

- wms/ecommerce/fintech/saas — 이미 clean(MONO-472 커버).
- reverse gap·DUP1 포인터화.

---

# Acceptance Criteria

- [ ] **AC-0**: 20 코드 전수 재측정(가드 override). 목록·상태 물려받지 말 것.
- [ ] **AC-1**: 각 코드 = 등록(status+desc, spec 근거) 또는 제거(미사용 확증) 판정 완료.
- [ ] **AC-2**: 등록 코드는 `error-handling.md` 해당 `[domain: X]` 섹션에 `| CODE | status | desc |` 행 추가.
- [ ] **AC-3 (가드 편입)**: 해소된 도메인을 `scripts/check-domain-error-code-registry.sh` `UNRECONCILED` 에서 제거 → `DOMAIN_ERRCODE_EXCLUDE=""` 가드 GREEN(전 7도메인).
- [ ] **AC-4**: status 배정 근거를 각 코드 행 desc 또는 커밋에 기록(추측 아님).

---

# Related Specs

- `platform/error-handling.md`(등록 대상) / `rules/domains/{erp,fan-platform,scm}.md`(선언측).
- 각 도메인 서비스 `specs/services/<svc>/architecture.md`(status 근거).
- `scripts/check-domain-error-code-registry.sh`(가드, 편입 대상).

# Related Contracts

- 등록 코드는 API error-envelope 계약에 영향 — status 배정이 계약 결정. 서비스 spec 대조 필수.

---

# Edge Cases

- **status 배정이 추측이 됨** → AC-4 위반. 도메인 spec 근거 없으면 그 코드는 (b)제거 후보로 재검토하거나 사용자 확인.
- **일부 코드는 실제 미구현** → 제거가 정답(phantom). MONO-471 선례.

---

# Failure Scenarios

- **20개 일괄 status 추측 등록** → 계약 오염. 코드별 spec 대조 필수.
- **도메인 편입 누락** → AC-3 fail(가드가 여전히 제외).

---

# Verification

- (미착수 — ready 백로그) MONO-472 가드 랜딩 후 착수. 도메인별로 나눠 진행 가능(erp 1 → fan 7 → scm 12).
- 분석·구현=Opus 4.8.
