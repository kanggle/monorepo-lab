# Task ID

TASK-BE-254

# Title

소비 서비스 OIDC 통합 가이드 문서 신규 — `specs/features/consumer-integration-guide.md`

# Status

ready

# Owner

backend

# Task Tags

- onboarding

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

ADR-001 (D4=D4-c, "라이브러리 없이 표준 OIDC + 가이드 문서") 에 따라, 신규 B2B 도메인(`erp`, `scm`, `mes` 등) 또는 외부 파트너가 GAP에 OIDC client로 통합할 때 **단일 진입 문서** 를 제공한다.

현재 정보가 분산되어 있어(`account-internal-provisioning.md` + `multi-tenancy.md` + `auth-service/architecture.md` + `gateway-api.md` + 3개 이벤트 계약) 신규 도메인 합류 시 동일 탐색 비용이 매번 발생한다. 본 가이드는 그 비용을 일회성으로 전환한다.

완료 시점:

1. `specs/features/consumer-integration-guide.md` 가 존재하며 6개 영역(Tenant 등록 → OAuth Client 등록 → JWKS 설정 → Service-to-Service 인증 → 이벤트 구독 → 캐시·라이프사이클 처리) 을 단계별로 설명.
2. 코드 예시(Java/Spring, Node.js 두 언어 — Java만 무조건, Node는 best-effort)를 포함.
3. 기존 분산 문서들에서 본 가이드를 backlink.

---

# Scope

## In Scope

- 신규 문서 작성: `specs/features/consumer-integration-guide.md`
- 문서 구조:
  1. **개요** — 소비 서비스가 GAP를 사용하는 두 가지 패턴 (사용자 인증 위임 vs. service-to-service)
  2. **Phase 1: 테넌트 등록** — admin → `POST /api/admin/tenants`, 응답 페이로드, `tenant_id` slug 규칙
  3. **Phase 2: OAuth client 등록** — admin → `POST /api/admin/oauth-clients` (TASK-BE-252 후속에 의존), client_id/secret 발급, redirect URIs / allowed grants 결정 가이드
  4. **Phase 3: 표준 OIDC discovery + JWKS 설정** — `OIDC_ISSUER_URL` 환경변수, Spring Security `issuer-uri` 설정, JWKS 캐싱 동작
  5. **Phase 4: Service-to-Service 인증 (`client_credentials`)** — Spring Security OAuth2 Client 설정 예시, `WebClient` filter 패턴, Node.js `openid-client` 예시
  6. **Phase 5: 이벤트 구독 — 사용자 라이프사이클** — 구독해야 할 4개 이벤트 (`account.created`, `account.locked`, `account.deleted`, `account.status.changed`) + 각 이벤트의 소비 의무 (캐시 무효화, GDPR downstream 처리)
  7. **Phase 6: 운영 체크리스트** — `tenant_id` claim 검증, cross-tenant 거부, JWKS endpoint 가용성 모니터링, 토큰 만료/갱신 동작
- 코드 예시:
  - Java/Spring Boot: `application.yml`, `WebClient` 빈, `tenant_id` 검증 필터
  - Node.js: `openid-client` 라이브러리 사용 예시 (간단 수준)
- 기존 문서 backlink 추가:
  - `specs/features/multi-tenancy.md` § "Tenant Registration" → "신규 테넌트는 [consumer integration guide](consumer-integration-guide.md) 를 따라 등록"
  - `specs/contracts/http/internal/account-internal-provisioning.md` → 가이드 링크
  - `docs/adr/ADR-001-oidc-adoption.md` § 6 → 가이드 링크

## Out of Scope

- TASK-BE-251/252/253이 아직 구현되지 않은 상태 — 가이드는 **목표 상태**(SAS 도입 후) 기준으로 작성. 251/252 머지 전이라도 가이드는 선행 작성 가능 (구현이 가이드의 사양에 맞춰지는 효과).
- `admin-service`의 `POST /api/admin/oauth-clients` API 자체 구현 — 본 태스크는 가이드 작성, API 구현은 후속.
- Node.js 외 언어(Python/Go) 예시 — 필요 시 후속 추가.
- 외부 파트너용 별도 portal 또는 self-service 등록 UI — 후속.

---

# Acceptance Criteria

- [ ] `specs/features/consumer-integration-guide.md` 신규 작성, 6개 Phase 섹션 + 코드 예시 + 운영 체크리스트 포함.
- [ ] 가이드를 따라 신규 시뮬레이션 도메인(`erp` 가정) 통합 시 필요한 모든 정보가 가이드 단일 문서로 충족됨 (가이드 외부의 spec 참조는 5회 이하).
- [ ] Java/Spring 예시 동작 가능 (실제 컴파일 검증은 별도, 본 태스크는 문서 정확성).
- [ ] 기존 분산 문서 3개 이상에서 본 가이드로 backlink 추가.
- [ ] 가이드의 모든 endpoint URL, 헤더 명, claim 명이 ADR-001/현행 specs와 일치.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `docs/adr/ADR-001-oidc-adoption.md` (특히 § 5 D4-c)
- `specs/features/multi-tenancy.md`
- `specs/features/authentication.md`
- `specs/contracts/http/internal/account-internal-provisioning.md`
- `specs/contracts/http/auth-api.md` (TASK-BE-251 갱신 후 oauth2 섹션 반영)
- `specs/contracts/http/gateway-api.md`
- `specs/contracts/events/account-events.md`
- `specs/contracts/events/auth-events.md`

# Related Skills

- 해당 없음 (문서 작성 태스크)

---

# Related Contracts

- 본 태스크는 신규 contract 정의 없음. 기존 contract들을 참조·요약.

---

# Target Service

- 해당 없음 (cross-cutting docs)

---

# Architecture

- 해당 없음.

---

# Implementation Notes

- **선행 의존성 처리**: TASK-BE-251/252가 미완료 상태에서 가이드를 작성하면 일부 endpoint URL이 추정값. 가이드 머지 전 251/252 PR과 정합성 cross-check 1회 권장.
- **문서 길이 가이드**: 단일 문서 1500~2500 라인 목표. 더 길어지면 Phase별로 분할.
- **언어 정책**: 본문 한국어, 코드 식별자 영어 (project convention).
- **변경 추적**: 가이드 자체가 spec이므로 향후 `auth-api.md` 변경 시 본 가이드도 동기 갱신. 가이드 첫 줄에 "이 문서는 ADR-001을 구현하는 specs (251/252/...) 와 동기 유지" 명시.

---

# Edge Cases

- **TASK-BE-251/252가 본 태스크보다 먼저 머지되는 경우**: 가이드 내용이 실제 구현과 일치하는지 정합성 검토 후 작성.
- **본 태스크가 251/252보다 먼저 머지되는 경우**: 가이드는 "목표 상태" 문서로 작동. 251/252 PR이 가이드 사양을 따라야 함 — PR 설명에 가이드 참조 의무.

---

# Failure Scenarios

- **가이드 사양이 실제 구현과 drift**: 모든 OIDC contract 변경 시 가이드 동기 갱신 의무. 별도 Definition of Done 항목으로 enforce.

---

# Test Requirements

- 해당 없음 (문서 작성). 단, 가이드의 모든 URL/claim/scope 명을 현행 specs와 grep으로 cross-check 권장.

---

# Definition of Done

- [ ] `specs/features/consumer-integration-guide.md` 작성 완료
- [ ] 기존 3개 이상 spec 문서에서 backlink 추가
- [ ] ADR-001 § 6 권장 후속 태스크 표에 본 태스크 ID 기재
- [ ] Ready for review
