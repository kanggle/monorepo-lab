# Task ID

TASK-MONO-247

# Title

ecommerce 에러코드 정합성 보정 — 택배 webhook 계약↔코드 정렬(F4) + 마켓플레이스 정산 registry 신설(E-01) + 위생(F1/E-05/E-02). iam 정합성 작업의 ecommerce 후속 (doc-only)

# Status

done

# Owner

monorepo (root tasks/ — shared `platform/error-handling.md` + ecommerce specs)

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

- **origin**: iam 정합성 작업(MONO-244/245/246 + BE-356) 직후, 동일 부류 이슈를 ecommerce(중첩테넌시·택배) 영역에서 read-only 조사하여 발견. green-wash 가드용 emit 출처는 전부 코드 확인 완료.
- **scope placement**: shared `platform/error-handling.md` 를 건드리므로 root tasks/ (CLAUDE.md shared-path 규칙; MONO-106/244 lineage). ecommerce contract 도 같은 atomic PR.
- **doc-only**: production code 변경 0. 전 항목이 spec/contract/registry 문서를 **실제 코드 emit 에 정렬**하는 작업.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (계약·registry 정합).

---

# Goal

ecommerce 에러코드 표면을 실제 코드 emit 에 정렬하여, (a) 계약↔코드 불일치(택배 webhook), (b) referenced-but-unregistered(정산), (c) registry 위생(크로스도메인 cross-note, 미존재 코드 doc 오류)을 해소한다. 전부 doc-only, 코드 변경 0.

---

# Scope

## In Scope (emit 출처 코드 확인 완료)

**F4 — 택배 webhook 계약↔코드 정렬 (Critical).**
- `specs/contracts/http/shipping-api.md` L142: `POST /api/shippings/carrier-webhook` 의 401 코드가 `UNAUTHORIZED` 로 게시돼 있으나, 코드(`shipping-service` `GlobalExceptionHandler.java:81`)는 `WEBHOOK_SIGNATURE_INVALID` 를 emit. **계약을 실제 emit 코드로 정정**(더 서술적, wms 에 이미 등록된 동일 코드). 코드 변경 없음.

**F1 — 택배 webhook 코드 registry 등재 (Warning).**
- `platform/error-handling.md` `## Shipping [domain: ecommerce]` 섹션에 `WEBHOOK_SIGNATURE_INVALID` (401) 행 추가. wms Inbound 의 동일 코드와 크로스도메인 재사용임을 서술(같은 HMAC 거부 semantic).

**E-01 — 마켓플레이스 정산 registry 신설 (Critical).**
- `platform/error-handling.md` 에 `## Settlement [domain: ecommerce]` 섹션 신설. `settlement-service`(ADR-MONO-030 BE-365) 가 emit 하는 `SETTLEMENT_NOT_FOUND`(404) + `COMMISSION_RATE_INVALID`(422) 등록. SETTLEMENT_NOT_FOUND 는 seller-scope/cross-tenant 거부 시에도 existence 비노출 위해 404 로 반환됨을 서술.

**E-05 — TENANT_FORBIDDEN ecommerce cross-note (Warning).**
- `platform/error-handling.md` ecommerce 영역에 `TENANT_FORBIDDEN`(saas) 크로스도메인 재사용 cross-note 추가(erp 가 L728 에 동형 note 보유; ecommerce gateway + settlement 도 소비하나 미표기).

**E-02 — order-api INVALID_REQUEST doc 오류 정정 (Warning).**
- `specs/contracts/http/order-api.md` L160: `GET /api/orders/verify-purchase` 의 400 코드가 `INVALID_REQUEST` 로 적혀 있으나 코드 emit 0 — order-service 는 `VALIDATION_ERROR` 를 emit(`GlobalExceptionHandler.java:55`). 문서를 `VALIDATION_ERROR` 로 정정.

## Out of Scope

- production code 변경 — 전 항목 doc-only(코드는 이미 올바른 문자열 emit; 문서를 코드에 맞춤).
- `DLQ_RETRY_EXHAUSTED` 미등록·미사용 / `EXTERNAL_*` wms-only 등재 — MONO-106 이 의도적 deferred 한 (A) restructure, 본 task 무관.
- finance failureCode 노트 — 별도 task(다른 project).
- 크로스프로젝트 이벤트 reason 통일 등 별개 사안.

---

# Acceptance Criteria

- [x] **AC-1 (F4)**: `shipping-api.md` carrier-webhook 401 = `WEBHOOK_SIGNATURE_INVALID` (코드 emit 과 일치, `GlobalExceptionHandler:81`).
- [x] **AC-2 (F1)**: `error-handling.md:391` Shipping 섹션에 `WEBHOOK_SIGNATURE_INVALID`(401) 등록 + wms Inbound 와의 크로스도메인 재사용 서술.
- [x] **AC-3 (E-01)**: `error-handling.md:395` `## Settlement [domain: ecommerce]` 신설 + `SETTLEMENT_NOT_FOUND`(404)/`COMMISSION_RATE_INVALID`(422) 등록. emit 확인(`settlement-service GlobalExceptionHandler:29,36`).
- [x] **AC-4 (E-05)**: Settlement 섹션에 `TENANT_FORBIDDEN` cross-note 추가(erp L728 동형).
- [x] **AC-5 (E-02)**: `order-api.md` verify-purchase 400 = `VALIDATION_ERROR`; `INVALID_REQUEST` grep 0(코드 emit 0, 정렬).
- [x] **AC-6 (scope-lock)**: 변경 = `platform/error-handling.md` + `shipping-api.md` + `order-api.md` + task lifecycle. production code 0.
- [x] **AC-7**: 등록 코드 전부 emit 출처 확인(green-wash 0); 신규 broken-ref 0.

---

# Related Specs

- `platform/error-handling.md` — shared registry(F1/E-01/E-05).
- `projects/ecommerce-microservices-platform/specs/contracts/http/shipping-api.md` (F4), `order-api.md` (E-02), `settlement-api.md`(E-01 참조원).
- `docs/adr/ADR-MONO-030-*` (마켓플레이스), `docs/adr/ADR-MONO-007-*` (택배 aggregator).

# Related Contracts

- `shipping-api.md` carrier-webhook 401 — 코드 문자열 정정(계약을 emit 에 정렬; 실 동작 무변경, 코드는 이미 WEBHOOK_SIGNATURE_INVALID emit).
- `order-api.md` verify-purchase 400 — doc 정정.

---

# Edge Cases

- **F4 가 코드를 바꾸는 것으로 오해** — 아님. 코드는 이미 `WEBHOOK_SIGNATURE_INVALID` emit; 계약 문서만 정렬. 실 동작/응답 무변경.
- **SETTLEMENT_NOT_FOUND 가 not-found 와 forbidden 두 의미** — 의도적(existence 비노출 위해 cross-seller/tenant 거부도 404). registry 설명에 명시.
- **WEBHOOK_SIGNATURE_INVALID 크로스도메인 중복 등록처럼 보임** — wms Inbound(ERP webhook) + ecommerce Shipping(carrier webhook) 둘 다 동일 HMAC-거부 semantic. cross-note 로 의도 명시(CONCURRENT_MODIFICATION alias 선례와 동형).

# Failure Scenarios

- **계약을 코드로 정렬하지 않고 코드를 UNAUTHORIZED 로 바꿈** → 서술적 코드 손실 + 실제 webhook 핸들러 동작 변경(범위 초과). doc-only 정렬만.
- **emit 미확인 코드를 registry 등록** → green-wash. 전 등록 코드 emit 출처 확인 완료(AC-7).
- **다른 파일/코드 수정** → AC-6 fail.

---

# Verification

- 미수행(ready). 구현 시: 전 편집 후 `git diff origin/main --stat`(shared registry + ecommerce 2 contract + task) + 등록 코드 emit grep 첨부.
- CI: spec/registry `.md` only → path-filter fast-lane GREEN 예상(코드 무변경).
- 분석=Opus 4.8 / 구현=Opus 4.8.
