# Task ID

TASK-BE-359

# Title

shipping-service: carrier-webhook 경로 gateway public-route 노출 (ADR-007 D5-2)

# Status

done

# Owner

backend

# Task Tags

- code
- api
- deploy

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

# Goal

TASK-BE-294 가 만든 inbound webhook 엔드포인트 `POST /api/shippings/carrier-webhook` 는 **HMAC 서명으로 자체 인증**(gateway `X-User-Role` 불사용)하지만, 현재 **gateway 라우팅에 public-route 로 노출돼 있지 않다** — 즉 외부 중개 플랫폼이 호출해도 도달하지 못한다. 이 task 는 해당 경로만 인증 우회(JWT 불요) + 외부 도달 가능하도록 gateway 라우트를 추가해, ADR-007 의 inbound leg 를 실제로 열어준다.

이 task 가 끝나면: "중개사가 공개 webhook URL 로 서명된 delivery 를 POST 하면 gateway 를 통과해 shipping-service 에 도달하고, 서명 검증(BE-294)이 그대로 작동한다"가 참이 된다.

---

# Scope

## In Scope

- ecommerce gateway-service 라우팅에 `POST /api/shippings/carrier-webhook` **단일 경로**를 JWT 인증 제외 + 라우트로 추가(나머지 `/api/shippings/**` 는 기존 보안 유지).
- 인증은 다운스트림 shipping-service 의 HMAC(`CarrierWebhookVerifier`)에 전적으로 위임 — gateway 는 경로 노출만, 서명 재검증 안 함.
- 경로 한정 노출: 다른 shipping 관리/조회 엔드포인트가 실수로 열리지 않도록 정확히 이 경로(method+path)만 매칭.
- (해당 시) rate-limit/페이로드 사이즈 제한 등 공개 엔드포인트 최소 방어 설정 검토 + 적용.
- `shipping-api.md` 에 "이 경로는 gateway public-route, 인증=HMAC" 명시. 배포 문서(있으면 `infra`/compose 라우팅 라벨)에 반영.

## Out of Scope

- 중개사 어댑터 매핑/credential (→ TASK-BE-362).
- auto-collect 스케줄러 (→ TASK-BE-360).
- webhook dedup cleanup (→ TASK-BE-361).
- HMAC 검증 로직 변경(BE-294 그대로 사용).
- 도메인/이벤트 계약 변경.

---

# Acceptance Criteria

- [ ] AC-1 — gateway 통해 `POST /api/shippings/carrier-webhook` 에 **유효 서명** delivery 호출 시 200(ADVANCED/IGNORED/DUPLICATE)으로 도달.
- [ ] AC-2 — 같은 경로에 **잘못된/누락 서명** → shipping-service 가 401(서명 검증은 다운스트림). gateway 가 JWT 401 을 먼저 던지지 않음(public-route 확인).
- [ ] AC-3 — 다른 shipping 경로(예: 관리 조회)는 기존 JWT 보안 유지(이 task 로 열리지 않음).
- [ ] AC-4 — blank `secret`(net-zero) 기본값에서 public-route 라도 전부 401(BE-294 fail-closed 유지) — 경로 노출이 보안을 약화시키지 않음.
- [ ] AC-5 — gateway 빌드/테스트 GREEN; 라우트 통합 스모크(가능 시) 또는 라우트 단위 테스트로 경로 매칭 검증.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` + `rules/common.md` + `rules/traits/integration-heavy.md`. gateway 의 Service Type 은 `specs/services/gateway-service/architecture.md` 확인.

- `projects/ecommerce-microservices-platform/docs/adr/ADR-007-logistics-aggregator-carrier-integration.md` (D5-2)
- `projects/ecommerce-microservices-platform/specs/services/gateway-service/architecture.md`
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md`

# Related Skills

- `.claude/skills/backend/...` (gateway 라우팅/보안 관련 — INDEX 확인)

---

# Related Contracts

- `specs/contracts/http/shipping-api.md` (carrier-webhook 경로의 노출/인증 명시 갱신)

---

# Target Service

- `gateway-service` (+ `shipping-service` 계약 문서)

---

# Edge Cases

- 정확한 method 매칭: `POST` 만 public, 같은 경로 다른 method 는 열지 않음.
- trailing slash / 경로 변형으로 인증 우회가 새지 않는지 확인.
- 대용량/악성 페이로드: 사이즈 제한·rate-limit(있으면) 적용.

---

# Failure Scenarios

- **F1 — 과노출**: 와일드카드로 `/api/shippings/**` 가 통째로 인증 우회됨. → AC-3 경로 한정.
- **F2 — 도달 불가 방치**: 라우트 누락으로 inbound leg 가 계속 닫힘(현재 상태). → AC-1.
- **F3 — 공개 엔드포인트 남용**: 무제한 호출. → 최소 방어(rate-limit/사이즈) 검토.

---

# Test Requirements

- gateway 라우트 매칭 단위/슬라이스 테스트(경로·method 한정, JWT 우회 확인).
- 가능 시 통합 스모크: 서명 OK→200, 서명 NG→401, 다른 경로→401(JWT).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added & passing
- [ ] shipping-api.md / 배포 라우팅 반영
- [ ] 과노출 없음 확인(경로 한정)
- [ ] Ready for review
