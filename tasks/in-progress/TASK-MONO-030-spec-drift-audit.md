# Task ID

TASK-MONO-030

# Title

spec drift audit — 4 프로젝트 간 architecture / contract / integration 패턴 일관성 검증

# Status

ready

# Owner

backend

# Task Tags

- audit
- spec
- chore

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

공통규칙·스펙 정리 시리즈 5개 task 중 두 번째. 각 프로젝트 (`projects/{wms-platform, ecommerce-microservices-platform, global-account-platform, fan-platform}/specs/`) 의 spec 패턴이 4 프로젝트 간 일관되는지 audit 하고 발견 drift 를 정리한다.

TASK-MONO-029 (`rules/` audit) 가 라우팅 레이어 정합성을 검증했다면, 본 task 는 그 라우팅이 가리키는 실제 spec 본문 (architecture / contract / integration) 의 cross-project 일관성에 초점.

---

# Scope

## In Scope

### 1. `specs/services/<service>/architecture.md` 패턴 일관성

각 프로젝트의 active service 의 architecture.md 가 다음 공통 섹션을 동일 형식으로 갖는지:

- **Service Type 선언** (`platform/service-types/INDEX.md` 카탈로그 값)
- **Architecture 선언** (Hexagonal / Layered / Reactive 등 — `platform/architecture-decision-rule.md` 참조)
- **Public Routes** 테이블 (rest-api 서비스 한정)
- **Testing 섹션** (단위 / 슬라이스 / 통합 분류 + Testcontainers 사용 여부)
- **Outbox 섹션** (이벤트 발행 서비스 한정)
- **Authentication 섹션** (gateway / OIDC consumer 한정 — TASK-FE-067 이후 추가된 GAP 통합 섹션 일관성)

검증 대상: wms 5 active (master/inventory/gateway/inbound/outbound) + ecommerce 12 backend (auth-service-deprecated 제외) + fan-platform 3 (gateway/community/artist) + GAP 7+ (auth/account/admin/policy 등) = 약 27 services.

### 2. `specs/integration/gap-integration.md` 패턴 일관성

GAP OIDC consumer 3 프로젝트 (wms / ecommerce / fan-platform) 의 gap-integration.md 가 동일 구조를 따르는지:

- Tenant Identity 섹션
- OIDC Endpoints + 환경변수 표
- Spring Boot 설정 키 스니펫
- OAuth Clients 표 (V0010~V0012)
- Scopes 표
- Token 검증 규칙 5단계
- Error Responses 표
- 운영 체크리스트
- 참조 (ADR-001, consumer-integration-guide, V00XX, TASK-MONO-XXX)

ecommerce 의 spec 이 cutover 직후 작성되어 wms / fan-platform 와 차이가 있을 가능성. 특히 다음 검증:
- account_type cross-app guard 설명 일관성 (CONSUMER vs OPERATOR vs SUPER_ADMIN wildcard)
- `audiences` 필드 사용 여부 (wms = 사용 안 함 / ecommerce = `audiences: ecommerce` / fan-platform = ?)

### 3. `specs/contracts/{http,events}/*.md` deprecated 헤더 일관성

PR #150 (TASK-BE-132) 에서 ecommerce 의 `auth-api.md` / `auth-events.md` 에 DEPRECATED 헤더 추가. 다른 곳 (특히 features/) 의 deprecated 표시 형식과 일관되는지:

- 헤더 패턴 통일 (`> **DEPRECATED — replaced by ...**`)
- 대체 경로 link 명시
- Striked-out (`~~text~~`) vs 문장 추가 패턴 결정

ecommerce specs/features/{authentication,user-management}.md 의 striked-out 패턴이 future template 임을 검증 + 다른 deprecated spec 발견 시 동일 패턴 적용.

### 4. `platform/contracts/*.md` 표준이 각 프로젝트 spec 에 정확히 reflect

`platform/contracts/jwt-standard-claims.md` 가 4 프로젝트 spec 에서 정확히 reference 되는지. 누락 / 다른 표준 사용 / drift 발견.

### 5. `PROJECT.md` ↔ `specs/services/` 디렉토리 일치

각 프로젝트의 `PROJECT.md` § service_types 가 실제 `specs/services/<service>/architecture.md` 에 선언된 Service Type 과 일치하는지. PR #156 에서 rules/ 측 정합성 검증한 것을 spec 측까지 확장.

## Out of Scope

- spec 본문 내용 자체의 정확성 (도메인 로직, contract 필드 정합성) — 스펙 작성자 / 도메인 reviewer 영역
- contract drift (실제 API 응답 ↔ contract 명시 필드 차이) — 별도 contract test 영역 (TASK-MONO-005 / 006 / 007 패턴)
- libs / .claude / TEMPLATE.md 영역 — 별도 031 / 032 / 033

---

# Acceptance Criteria

- [ ] 27+ services 의 architecture.md 공통 섹션 누락 / 형식 drift 매트릭스 PR body 에 첨부.
- [ ] 3 프로젝트 gap-integration.md 의 cross-project drift 매트릭스 + 통일 권장 결정.
- [ ] deprecated 헤더 패턴 통일 정책 결정 (header / striked-out 어느 쪽 default).
- [ ] platform/contracts/ reference 누락 spec 카탈로그.
- [ ] Critical drift (의미적 충돌) 본 PR 에서 fix.
- [ ] Warning drift (형식 일관성) 본 PR 또는 별도 follow-up.
- [ ] Suggestion 은 PR body 에만 카탈로그.

---

# Related Specs

- 모든 4 프로젝트의 `specs/` 디렉토리
- `platform/contracts/`, `platform/service-types/INDEX.md`, `platform/architecture-decision-rule.md`
- 본 시리즈 선행: `tasks/done/TASK-MONO-029-rules-validation-audit.md`

---

# Related Skills

- `validate-rules` (광범위 검사)
- `refactor-spec` (spec 구조 개선)

---

# Target Component

- `projects/*/specs/services/`
- `projects/*/specs/integration/`
- `projects/*/specs/contracts/`
- `platform/contracts/`

---

# Architecture

audit + chore. 코드 변경 없음.

---

# Implementation Notes

- 매뉴얼 grep + 표 형태 매트릭스 작성이 핵심.
- 4 프로젝트 spec 디렉토리 비교는 column-major 표 추천:
  - 행: 공통 섹션 (Service Type / Architecture / Testing / Outbox / Authentication / ...)
  - 열: 27 services
  - 셀: ✅ / ❌ / ⚠️ (있지만 형식 다름)
- 매트릭스 결과 → Critical (의미 충돌, 예: 같은 서비스가 architecture 다르게 선언) vs Warning (형식 drift) 분류.

---

# Edge Cases

- **placeholder 서비스** (wms admin/notification, ecommerce auth-service-deprecated): 검증 제외 또는 명시.
- **GAP 자체는 OIDC IdP 라 gap-integration.md 가 없음**: 검증 대상 3 프로젝트 (wms/ecommerce/fan-platform) 만.
- **fan-platform v2 placeholder spec** (membership/notification/admin): 본 task 는 v1 active 만 검증.

---

# Failure Scenarios

- **drift 가 너무 많아 매트릭스 분량 초과**: 우선순위 (Critical → Warning → Suggestion) 기준으로 본 PR 분량 제한, 나머지는 별도 fix task 분리.
- **통일 결정이 의미적 차이를 가림**: 같은 섹션 이름이지만 각 프로젝트가 다른 의미로 사용 중일 수 있음. 통일 전 의미 동일성 확인.

---

# Test Requirements

- audit 자체가 검증 활동.
- 수정 후 sample build (4 프로젝트 각각 1 service) 로 spec 변경이 빌드에 영향 없음 확인.

---

# Definition of Done

- [ ] 매트릭스 4개 (architecture / gap-integration / deprecated 헤더 / platform reference) PR body 첨부.
- [ ] Critical / Warning / Suggestion 분류.
- [ ] Critical 모두 fix.
- [ ] follow-up task candidate (있다면) 명시.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-029 (rules/ audit) 완료
