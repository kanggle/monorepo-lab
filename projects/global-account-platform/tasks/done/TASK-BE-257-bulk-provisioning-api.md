# Task ID

TASK-BE-257

# Title

Bulk provisioning API — `POST /internal/tenants/{tenantId}/accounts:bulk`

# Status

ready

# Owner

backend

# Task Tags

- code
- api

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

ADR-001 (D3=D3-b "경량 bulk provisioning") 결정에 따라, B2B 신규 도메인(ERP/MES/SCM 등) 의 초기 onboarding 시 수백~수천 명 직원을 일괄 등록할 수 있는 API 를 추가한다.

SCIM 풀 지원은 별도 ADR 로 미루고, 현 단계는 단일 endpoint 의 batch insert 로 충분.

완료 시점:

1. `POST /internal/tenants/{tenantId}/accounts:bulk` 엔드포인트가 최대 1000 건의 account 를 단일 요청으로 생성.
2. 부분 실패 처리 정책 명확 (all-or-nothing vs partial-success — 본 태스크는 partial-success 채택).
3. outbox 이벤트는 individual `account.created` 이벤트 N 건 발행 (downstream consumer 는 단건 처리).
4. 응답 페이로드에 성공/실패 결과 per-row 반환.

---

# Scope

## In Scope

- 신규 endpoint:
  - `POST /internal/tenants/{tenantId}/accounts:bulk`
  - 인증: `X-Internal-Token` (단기) 또는 `client_credentials` 토큰 with scope `accounts.bulk.write` (TASK-BE-251 후 권장)
- 요청 body:
  ```json
  {
    "items": [
      {
        "external_id": "string (caller-side dedup key, optional)",
        "email": "string",
        "phone": "string?",
        "display_name": "string",
        "roles": ["string"],
        "status": "ACTIVE | DORMANT (default ACTIVE)"
      }
    ]
  }
  ```
- 제한:
  - 최대 1000 items per request
  - 단일 tenant scope (path 의 tenantId 와 caller tenant 일치 필수)
- 응답 body:
  ```json
  {
    "created": [{"external_id": "...", "account_id": "uuid"}],
    "failed": [{"external_id": "...", "error_code": "EMAIL_DUPLICATE", "message": "..."}],
    "summary": {"requested": 1000, "created": 950, "failed": 50}
  }
  ```
- 트랜잭션 모델: per-row transaction (partial-success). 한 row 실패가 다른 row 에 영향 없음.
- outbox: 성공한 N 건 각각 `account.created` 이벤트 발행.
- audit: bulk 호출 1건당 `admin_actions.action_code = ACCOUNT_BULK_CREATE` 1 row + `target_count = N`.
- contract: `account-internal-provisioning.md` 에 신규 endpoint 명세 추가.

## Out of Scope

- SCIM 2.0 (`/scim/v2/Users`, `/scim/v2/Groups`) 풀 지원 — 별도 ADR (D3-a 옵션 채택 시).
- bulk update / bulk delete — 본 태스크 create 만.
- async bulk (job + polling) — 동기 1000건 한도 내 처리. 1만 건+ 시나리오는 후속 ADR.
- CSV / Excel 입력 형식 — JSON only. UI / 변환은 별도.
- 비밀번호 일괄 설정 — bulk 생성 시 password reset token 자동 발행, 초기 비밀번호는 외부 채널로 사용자 전달 (메일링 후속).

---

# Acceptance Criteria

- [ ] `POST /internal/tenants/{tenantId}/accounts:bulk` 구현, 200 응답에 partial-success summary 포함.
- [ ] 1000 건 초과 시 400 `BULK_LIMIT_EXCEEDED`.
- [ ] caller tenant ≠ path tenant 시 403 (tenant scope 위반).
- [ ] 부분 실패 시 성공한 row 는 commit, 실패한 row 는 응답에 error_code + message.
- [ ] N 건 성공 시 N 건의 `account.created` outbox 이벤트 발행 (단건 처리 가능).
- [ ] `admin_actions` 에 1건 audit row + `target_count = N`.
- [ ] 동일 email 중복 (DB 제약) 시 `failed[]` 에 `EMAIL_DUPLICATE` 로 분류.
- [ ] 1000 건 처리 시간이 30초 이하 (배치 insert + 적절한 batch size).
- [ ] `specs/contracts/http/internal/account-internal-provisioning.md` 갱신 — 신규 endpoint 명세 + 응답 schema.
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:check` + `:integrationTest` PASS.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `docs/adr/ADR-001-oidc-adoption.md` § 5 D3
- `specs/features/multi-tenancy.md` § "Provisioning Flow"
- `specs/contracts/http/internal/account-internal-provisioning.md` (확장 대상)
- `specs/services/account-service/architecture.md`
- `specs/services/account-service/data-model.md`

# Related Skills

- `.claude/skills/backend/` API design / outbox / batch processing 관련

---

# Related Contracts

- `specs/contracts/http/internal/account-internal-provisioning.md`
- `specs/contracts/events/account-events.md` (`account.created` 이벤트 — 변경 없이 N 회 발행)
- `specs/contracts/events/admin-events.md` (audit `ACCOUNT_BULK_CREATE` 명시 추가)

---

# Target Service

- `account-service`

---

# Architecture

- `interfaces/internal/`: `BulkAccountController` 신규
- `application/account/`: `BulkAccountCreator` (per-row transaction wrapper, error collection)
- `infrastructure/persistence/`: JDBC batch insert 또는 JPA `saveAll` 채택 결정 (Performance Notes 참조)

---

# Implementation Notes

- **Batch insert 전략**: 1000 건 single insert vs JPA `saveAll` (per-row). per-row transaction 정책이므로 JPA `saveAll` 은 한 row 실패 시 전체 rollback 위험. 권장: JPA save per row + try/catch + collect errors. Hibernate batch_size=50 으로 IO 최적화.
- **Outbox 이벤트 일괄 insert**: 성공한 N 건의 `account.created` outbox row 는 동일 트랜잭션에서 한 번에 insert (각 row 의 트랜잭션이 독립이지만, outbox row 는 동일 row 의 트랜잭션 내에 있어야 atomicity 보장).
- **Audit 1행 vs N행**: bulk 호출 자체에 대해 1 행 (`ACCOUNT_BULK_CREATE`, `target_count=N`) + 개별 row 는 outbox 이벤트로 추적. 운영자 관점에서 "한 번의 bulk 호출" 이 audit 단위.
- **External_id 중복**: caller-side dedup key. 본 태스크는 검증 책임 없음 (caller 가 보장). DB 에 unique constraint 추가하지 않음.
- **Password 초기 발급**: 각 account 생성 시 password reset token 자동 발행, 응답에 token 포함하지 않음 (보안). 별도 메일링은 후속.

---

# Edge Cases

- **빈 items 배열**: 200 + summary `{requested: 0, created: 0, failed: 0}`. 또는 400. 본 태스크는 200 + 빈 결과로 결정.
- **동일 요청 내 동일 email 2건**: 첫 번째 commit, 두 번째 `EMAIL_DUPLICATE` 로 failed.
- **caller token 의 tenant 와 path tenant 불일치**: 403 + `TENANT_SCOPE_VIOLATION`. 검증 시점은 endpoint 진입 직후.
- **1000건 이상 시도 with `Content-Length` over limit**: nginx / gateway 단에서 413, application 단까지 도달하지 않음.
- **partial-success 응답이 client 입장에서 retry 어려움**: client 가 `external_id` 로 재요청하면 중복 row 가 생기지 않도록 server 측 중복 체크 (email 기반) 가 자연스럽게 작동.

---

# Failure Scenarios

- **DB connection pool 소진**: 1000건 per-row 처리가 long-running 트랜잭션을 만들면 pool 점유. → batch_size 50 + 짧은 트랜잭션 권장.
- **outbox 폭주**: N 건 (1000) 이벤트 동시 발행 시 Kafka producer queue 점유. → outbox publisher polling 주기 단축 + producer batch 설정.
- **응답 size 초과**: 1000건 응답 + 상세 error 가 수MB 가능. → response compression (gzip).

---

# Test Requirements

- 단위 테스트:
  - `BulkAccountCreator`: per-row transaction, error collection, summary 계산.
  - tenant scope 검증.
- 통합 테스트 (`@Tag("integration")`):
  - 1000건 정상 (Testcontainers MySQL).
  - 부분 실패: 100건 중 10건 email 중복 → 90 created + 10 failed.
  - audit row 1건 + outbox N건 검증.
  - cross-tenant 거부.
  - 1001건 → 400.
  - 빈 배열 → 200 + 빈 결과.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added and passing
- [ ] `account-internal-provisioning.md` 갱신
- [ ] `admin-events.md` 에 `ACCOUNT_BULK_CREATE` audit code 추가
- [ ] CI green
- [ ] Ready for review
