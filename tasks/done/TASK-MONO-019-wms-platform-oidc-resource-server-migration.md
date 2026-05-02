# Task ID

TASK-MONO-019

# Title

wms-platform OIDC Resource Server 전환 + service-to-service `client_credentials` 도입

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- adr

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

GAP의 ADR-001 (D1=A) 결정에 따라 GAP가 표준 OIDC Authorization Server로 승격된 결과, **별도 프로젝트인 `projects/wms-platform/`** 의 모든 서비스가 GAP가 발급한 access token을 표준 OAuth2 Resource Server 패턴으로 검증하도록 전환한다.

이 태스크는 monorepo 의 두 프로젝트 (`global-account-platform`, `wms-platform`) 를 동시에 변경하는 cross-project 구조 변경이므로 root `tasks/` 의 monorepo-level 라이프사이클을 따른다.

완료 시점:

1. `wms-platform/apps/*` 모든 서비스가 `spring-boot-starter-oauth2-resource-server` 의존성을 사용해 GAP의 JWKS URI 기반으로 access token 검증.
2. wms 가 사용자 인증으로 받는 토큰의 `tenant_id` claim 이 `wms` 인 경우만 통과 (cross-tenant 거부).
3. wms 내부 service-to-service 호출 (e.g. inventory → master, gateway → inventory) 에서도 `client_credentials` token 사용 — 별도 internal token 인프라 (`X-Internal-Token`) 정리.
4. GAP 에 wms 용 OAuth client 등록:
   - `wms-user-flow-client` (`tenant_id=wms`, `allowed_grants=[authorization_code, refresh_token]`, redirect_uris=wms web 주소)
   - `wms-internal-services-client` (`tenant_id=wms`, `allowed_grants=[client_credentials]`, scopes=`[wms.master.read, wms.inventory.read, wms.outbound.write, ...]`)
5. wms-platform 의 `PROJECT.md` / specs 에 OIDC 통합 명시.

---

# Scope

## In Scope

**TASK-BE-251, TASK-BE-252 머지 완료 가정.** 본 태스크는 해당 두 태스크의 의존 후속.

- `wms-platform/apps/*` 의 각 service:
  - `spring-boot-starter-oauth2-resource-server` 의존성 추가
  - `application.yml`: `spring.security.oauth2.resourceserver.jwt.issuer-uri = ${OIDC_ISSUER_URL}`
  - `tenant_id` claim 검증 인터셉터 (`wms` 만 허용; 검증 실패 시 403)
  - 기존 자체 JWT 검증 코드 제거
- service-to-service 호출:
  - 모든 inter-service `WebClient` / `RestTemplate` 호출에 `client_credentials` token 자동 첨부 (`ServerOAuth2AuthorizedClientExchangeFilterFunction`)
  - wms 의 기존 `X-Internal-Token` 또는 mTLS 패턴 정리 — Out of Scope 의 일부 잔존 가능
- GAP 측:
  - wms 용 OAuth client 2건 등록 (Flyway seed 또는 admin API 경유)
  - wms 사용자 정의 scopes (`wms.master.read`, `wms.inventory.read` 등) 를 `oauth_scopes` 에 등록
- wms specs 갱신:
  - `projects/wms-platform/specs/contracts/http/*-api.md` 에 인증 헤더 OAuth2 명시
  - `projects/wms-platform/PROJECT.md` 또는 별도 `specs/integration/gap-integration.md` 에 GAP 통합 방식 1쪽 요약
- E2E 검증:
  - wms 의 기존 e2e Playwright / boot-jars CI job (TASK-MONO-013/014/015 참조) 가 OIDC 흐름으로 동작
  - GAP testcontainer + wms testcontainer 조합으로 통합 검증 (현실적이라면)
- 영향 enumeration:
  - `projects/wms-platform/` 내 변경 파일 목록을 PR 설명에 명시
  - `projects/global-account-platform/` 내 변경 파일 (oauth_clients seed, oauth_scopes seed) 목록 명시

## Out of Scope

- wms 의 기능 변경 — 인증 검증 경로만 교체.
- wms 의 Frontend (Next.js 등) 가 있다면 OIDC redirect flow 통합 — 별도 follow-up.
- GAP 에 admin UI 로 wms client 등록 — 시드 데이터 또는 admin API (TASK-BE-256 후속) 사용.
- DPoP, mTLS 등 고급 보안 — 후속 ADR.
- wms 외 다른 프로젝트 (ecommerce, 향후 erp/scm/mes) 마이그레이션 — 각각 별도 cross-project 태스크.

---

# Acceptance Criteria

- [ ] `wms-platform/apps/*` 의 모든 service 가 OAuth2 Resource Server 의존성 추가 + 자체 JWT 검증 코드 제거.
- [ ] 모든 protected endpoint 가 SAS 발급 OIDC token (`Bearer`) 으로 인증.
- [ ] `tenant_id != wms` 토큰 호출 시 403 (cross-tenant 거부).
- [ ] wms 내부 service-to-service 호출이 `client_credentials` token 으로 동작.
- [ ] GAP 에 `wms-user-flow-client`, `wms-internal-services-client` 2건 등록 (Flyway seed 또는 admin API).
- [ ] wms 정의 scopes (`wms.master.read` 등) 가 `oauth_scopes` 에 등록.
- [ ] wms specs 의 모든 contract 에서 인증 헤더 명시가 OAuth2 로 일치.
- [ ] PR 설명에 영향 받는 파일 목록 enumerate.
- [ ] CI green:
  - `./gradlew :projects:global-account-platform:check`
  - `./gradlew :projects:wms-platform:check`
  - GitHub Actions `e2e-tests` job (wms-platform 의 e2e Playwright 또는 testcontainer)

---

# Related Specs

> Step 0: read **두 프로젝트의 PROJECT.md** 모두 + rules layers per 각 분류.

- `docs/adr/ADR-001-oidc-adoption.md` (GAP 의 결정 — wms 가 따름)
- `projects/global-account-platform/specs/services/auth-service/architecture.md`
- `projects/global-account-platform/specs/contracts/http/auth-api.md` (TASK-BE-251 후 oauth2 섹션)
- `projects/global-account-platform/specs/features/multi-tenancy.md`
- `projects/wms-platform/PROJECT.md`
- `projects/wms-platform/specs/services/*/architecture.md`
- `projects/wms-platform/specs/contracts/http/*-api.md`
- `projects/global-account-platform/tasks/ready/TASK-BE-254-consumer-integration-guide-doc.md` (가이드 적용 케이스)

# Related Skills

- `.claude/skills/backend/` OAuth2 Resource Server 관련

---

# Related Contracts

- `projects/wms-platform/specs/contracts/http/*-api.md` (인증 헤더 표기 갱신)
- `projects/global-account-platform/specs/services/auth-service/data-model.md` (oauth_clients seed)

---

# Target Service / Project

- `projects/wms-platform/apps/*` (primary)
- `projects/global-account-platform/apps/auth-service` (client/scope seed)

---

# Architecture

- `wms-platform`:
  - 각 service 의 `infrastructure/security/`: OAuth2 Resource Server 설정 + `tenant_id` 인터셉터
  - `infrastructure/client/`: `WebClient` + `client_credentials` 자동 첨부
  - 자체 JWT 검증 코드 제거
- `global-account-platform`:
  - `auth-service` 의 `db/migration/seed/`: wms client 2건 + scopes seed (이상적으로는 admin API 호출이지만 본 태스크에서는 시드로 단순화)

---

# Implementation Notes

- **Cross-project 의존성**: `TASK-BE-251` (SAS 도입) 와 `TASK-BE-252` (OAuth schema) 가 머지된 후 본 태스크 시작 가능. 그 전에는 in-memory client 로만 검증되므로 wms 의 통합 검증 불완전.
- **Wms 정의 scopes 설계**: scope 명명 규칙은 `<tenant>.<resource>.<action>` 권장 (예: `wms.master.read`, `wms.inventory.write`). GAP 의 scope 등록 시 `tenant_id=wms` 로 격리.
- **`X-Internal-Token` 정리**: wms 내부 호출에서 사용되던 internal token 패턴은 `client_credentials` token 으로 대체. 단, 다른 외부 서비스 (예: 모니터링 cron) 가 사용 중이면 잔존 가능 — 본 PR 에서 enumerate.
- **CI 가시성**: 본 PR 은 두 프로젝트의 build.gradle / docker-compose / e2e config 모두 영향 가능. CI 의 `build-and-test` job 이 두 프로젝트를 동시 검증하므로 PR 단위 적발.
- **Cross-project commit 정책**: `feat!: GAP IdP migration for wms-platform` 같은 단일 PR. CLAUDE.md § "Cross-Project Changes" 참조 — atomic.

---

# Edge Cases

- **wms 의 e2e Playwright 가 GAP 의 OIDC redirect 처리**: Playwright 가 SAS 의 authorize → callback redirect 흐름을 통과해야 함. 기존 e2e config 에 OIDC mock 또는 실제 GAP testcontainer 필요.
- **GAP 가 다운된 상태**: wms 신규 로그인 모두 차단. wms graceful degradation 정책 (이미 발급된 token 의 TTL 동안만 동작) 검증.
- **wms 가 발행하는 audit/이벤트의 `tenant_id`**: 항상 `wms` 로 고정 (claim 에서 추출). 자체 발행 이벤트 페이로드 정합 검증.
- **scope mismatch**: `wms.master.write` 가 필요한 endpoint 를 `wms.master.read` scope 만 가진 client 가 호출 → 403 + `INSUFFICIENT_SCOPE`.

---

# Failure Scenarios

- **Cross-project commit 부분 실패**: 본 PR 이 GAP 변경만 머지되고 wms 변경이 빠지면 wms 빌드 실패. 단일 PR + atomic commit.
- **wms client secret 노출**: 시드 데이터의 평문 secret 이 git 에 커밋되면 leak. → `.env.example` + `BCryptPasswordEncoder` 해시만 시드, 평문은 별도 secret manager.
- **fan-platform / community 의 OIDC 마이그레이션 (TASK-BE-253) 와 충돌**: 두 태스크가 동시 진행되면 GAP 의 oauth_clients 시드 파일에 두 곳에서 변경 발생. PR 머지 순서 조정 또는 분리된 마이그레이션 파일.
- **wms-platform 의 e2e 가 OIDC redirect 미처리**: 별도 e2e 수정 필요 — 본 태스크 scope 에 포함.

---

# Test Requirements

- 단위 테스트:
  - 각 wms service 의 `tenant_id` claim 인터셉터: `wms` 통과, 그 외 403.
- 통합 테스트:
  - SAS 발급 token 으로 wms protected endpoint 호출.
  - `client_credentials` E2E (wms inventory → wms master).
  - cross-tenant 거부 회귀.
  - GAP testcontainer + wms testcontainer 조합 e2e (현실적이라면 — Docker 28 호환성은 TASK-MONO-015 패턴 적용).

---

# Definition of Done

- [ ] Implementation completed in both projects (atomic PR)
- [ ] Unit + integration + e2e tests passing
- [ ] wms specs 의 인증 헤더 표기 갱신
- [ ] GAP oauth_clients seed 추가
- [ ] CI green (양 프로젝트 :check + e2e job)
- [ ] PR 설명에 영향 파일 enumerate
- [ ] Ready for review
