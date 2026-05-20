# Task ID

TASK-PC-BE-001

# Title

`console-bff` Spring Boot Hexagonal skeleton — Phase 7 first execution (ADR-MONO-017 ACCEPTED post-execution; spec PR adds architecture + § 2.4.9 + PROJECT.md service_types; impl PR lands skeleton + Traefik + `GET /actuator/health`; MVP composition route deferred to TASK-PC-FE-011)

# Status

ready

# Owner

backend-engineer

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- deploy
- test

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

- **depends on (ADR)**: [ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) ACCEPTED 2026-05-20 (PR #666 squash `5c711e3b`, TASK-MONO-126). The ACCEPTED state authorizes this post-ACCEPTED execution per ADR § 3.3 / § 6 (sibling pattern: ADR-MONO-014 → TASK-BE-298, ADR-MONO-015 → TASK-PC-FE-005).
- **depends on (gate)**: 5/5 backend domains live (GAP + wms + scm + finance + erp). Gate satisfied 2026-05-20 by TASK-PC-FE-010 close PR #661 squash `eaa1de51` (Phase 6 COMPLETE per ADR-MONO-013 § History additive note count = 4).
- **depends on (Local Network Convention)**: shared Traefik stack (`infra/traefik/`, TASK-MONO-022). `console.local` (console-web) already registered; this task adds `console-bff.local` sibling.
- **prerequisite for**: TASK-PC-FE-011 (MVP "Operator Overview" cross-domain dashboard — first concrete § 2.4.9.X composition route on top of this skeleton).
- **precedent (closest shape)**:
  - TASK-FIN-BE-001 (finance account-service Hexagonal bootstrap — Spring Boot 3.4 / Java 21 / OAuth2 Resource Server / Testcontainers IT) — shape reference for the Spring Boot module + test pyramid.
  - TASK-ERP-BE-001 (erp masterdata-service bootstrap — Hexagonal / GAP RS validation / fail-closed) — second precedent, also spec-first 2-PR pattern.
  - TASK-PC-FE-002a (operator-token-exchange wiring — `OPERATOR_COOKIE` / `getOperatorToken()` server-side discipline) — the console-web side this BFF inherits via `X-Operator-Token` forwarding contract (§ 2.4.9 inbound auth).

---

# Goal

`platform-console` 의 첫 backend service `console-bff` 을 Hexagonal architecture 로 부트스트랩한다. 본 task 의 service architecture 결정은 spec PR 에서 `projects/platform-console/specs/services/console-bff/architecture.md` 로 author 되어 머지된 후 (HARDSTOP-09), 그 위에 impl PR 이 Spring Boot Hexagonal skeleton 모듈 + Traefik `console-bff.local` wiring + `GET /actuator/health` 첫 operational endpoint + Testcontainers IT 하니스를 land 한다.

본 task 의 범위는 ADR-MONO-017 § 3.3 #2 verbatim:
1. `apps/console-bff/` Spring Boot Hexagonal skeleton (`platform/service-types/rest-api.md` 요구사항 상속).
2. `specs/services/console-bff/architecture.md` ([ADR-MONO-012](../../../../docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md) D3 canonical form — Identity table + Service Type Composition H3).
3. `projects/platform-console/PROJECT.md` `service_types += rest-api` (ADR-MONO-013 § D5 prescription — *here*, not on PROPOSED).
4. Traefik `console-bff.local` 라벨 + docker-compose wiring ([ADR-MONO-001](../../../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C, hostname-based routing).
5. `specs/contracts/console-integration-contract.md` § 2.4.9 (BFF surface frame, hard invariants, `GET /actuator/health` 첫 endpoint) authoring.

MVP "Operator Overview" cross-domain dashboard 구현은 ADR-MONO-017 § 3.3 #3 명시대로 **본 task 범위 밖** — 별 task `TASK-PC-FE-011` 가 `§ 2.4.9.1` 추가 + `features/operator-overview/` + 첫 composition route 를 land 한다.

# Scope

## In Scope

**spec PR (이 task md 와 함께 머지)**:

1. **service architecture spec** — `specs/services/console-bff/architecture.md` (Hexagonal, Service Type = `rest-api`, HARDSTOP-10 canonical form — Identity table + `### Service Type Composition` H3, ADR-MONO-012 D3). ADR-MONO-017 D1-D8 invariants byte-unchanged 반영.
2. **PROJECT.md 패치** — `service_types: [frontend-app] → [frontend-app, rest-api]` + Service Map § v1 에 `console-bff` row 추가 (v2 row 에서 promote, ADR-MONO-013 § D5 prescription 실행).
3. **contract authoring** — `console-integration-contract.md` § 2.4.9 신규 추가 — 「`console-bff` server-side composition surface」 frame:
   - hard invariants (D4 per-domain credential dispatch verbatim, D6.A tenant pass-through, no mutation at MVP, no producer retrofit),
   - resilience (D5.A per-domain CB inherited from § 2.5, partial degrade, 401-cross-leg vs 403-per-leg discipline),
   - observability (D7.A per-domain attribution metric set, OTel),
   - logging discipline (inherited from § 2.6),
   - edge routing (`console-bff.local` Traefik, internal-only),
   - v1 endpoint surface (this task adds **only** `GET /actuator/health` row; MVP composition route deferred to `§ 2.4.9.1` by TASK-PC-FE-011).
4. **task md + INDEX.md** — 이 파일 + `projects/platform-console/tasks/INDEX.md` `ready` 섹션 업데이트.

**impl PR (별도, spec PR 머지 후)**:

5. **Spring Boot Hexagonal skeleton** — `apps/console-bff/`:
   - `build.gradle` (Java 21, Spring Boot 3.4, `libs/java-web` / `libs/java-security` dependency, Resilience4j),
   - `ConsoleBffApplication.java` (`@SpringBootApplication`),
   - `application.yml` (port, OAuth2 Resource Server JWKS = GAP, no DB),
   - **Hexagonal layout** — `domain/`, `application/` (composition use-case + ports — empty skeleton, MVP route는 TASK-PC-FE-011), `adapter/inbound/web/` (controller stubs + `GlobalExceptionHandler`), `adapter/outbound/http/` (per-domain `RestClient` factory + `CredentialSelectionPort` impl skeleton — selector predicate per § 2.4.9 table, no MVP-route specific clients).
6. **Spring Security wiring** — `SecurityConfig`:
   - inbound: OAuth2 Resource Server RS256 JWKS = GAP issuer (operator JWT validated as bearer),
   - request-scoped `OperatorCredentialContext` bean reading inbound `X-Operator-Token` + `X-Tenant-Id`,
   - `/actuator/health` permitAll (Traefik probe), all other endpoints `authenticated()` (no MVP routes yet, but the chain is set).
7. **`GET /actuator/health`** — first operational endpoint, Traefik health-check target + IT smoke-target.
8. **observability bootstrap** — Micrometer registry + the 3 mandatory BFF metric families registered as empty (no MVP route emits yet) so that TASK-PC-FE-011 just registers labels; OTel `traceparent` propagation enabled on the outbound `RestClient`.
9. **Dockerfile** — finance account-service Dockerfile 패턴; `console-bff.jar`.
10. **docker-compose 활성화** — `projects/platform-console/infra/` (또는 sibling) 의 docker-compose 에 `console-bff` 서비스 블록 추가 + Traefik `console-bff.local` 라벨 (internal-only — `console-web` server-side만 호출하도록 Traefik rule + 외부 CORS 미오픈).
11. **settings.gradle / CI** — `apps/console-bff` 모듈 등록 (`projects:platform-console:apps:console-bff`) + CI path-filter 가 본 모듈을 빌드/테스트 대상에 포함하는지 검증.
12. **Test pyramid (skeleton level)** — `platform/testing-strategy.md` + `service-types/rest-api.md`:
    - domain unit: `CredentialSelectionPort` 선택자 predicate 5-row table (GAP / wms / scm / finance / erp) — § 2.4.9 verbatim 일치,
    - application unit: degrade-policy 규칙 unit (empty composition use-case stub 으로 partial-failure rendering 형태 명세화 — TASK-PC-FE-011 가 첫 실 use-case 추가 시 fixture 로 사용),
    - slice (`@WebMvcTest`): `/actuator/health` happy + `SecurityConfig` 가 미인증 request 거부 (other endpoint stubs),
    - integration (`@SpringBootTest` + Testcontainers + WireMock): BFF 전체 컨텍스트 부트 + WireMock 으로 GAP JWKS 발급 stub + `/actuator/health` 200 + 인증된 stub endpoint 호출 시 inbound auth 체인 + per-domain credential dispatch 의 dry-run (실 outbound 호출 없이 `CredentialSelectionPort` 가 올바른 토큰을 선택하는지 instrumentation assertion).

## Out of Scope

- **MVP "Operator Overview" composition route** — ADR-MONO-017 § 3.3 #3 가 명시한 `TASK-PC-FE-011` 의 범위. § 2.4.9.1 sub-section + `features/operator-overview/` + `<OperatorOverviewScreen>` + composition use-case + per-domain `RestClient` 인스턴스화는 본 task 가 만들지 않는다 (skeleton 의 빈 슬롯만).
- **모든 mutation surface** — ADR-MONO-017 § 3.2 + § 2.4.9 hard invariants — MVP 는 read-only. `Idempotency-Key` / `X-Operator-Reason` / destructive-confirm 스캐폴딩 본 service 에 일체 도입 금지.
- **GraphQL / gRPC / 신규 aggregating producer endpoint** — ADR-MONO-017 D1.B/C/D / D3.B 거부.
- **`console-web` 의 단일 도메인 섹션 BFF 경유** — ADR-MONO-017 D2.C 거부. `features/{wms,scm,finance,erp,accounts,...}` 라우트는 BFF 미경유 (현 머지된 상태 유지).
- **새 auth 경계 / unified BFF token / operator-token-only-across-all-domains** — D4.B / D4.C 거부 (HARD INVARIANT).
- **BFF 자체 DB / Redis / Kafka outbox / Kafka consumer** — stateless v1 — architecture.md § Persistence.
- **frontend** — 본 task 는 backend-only; `console-web` 어느 라우트도 본 task 가 만지지 않는다 (별도 TASK-PC-FE-011).

# Acceptance Criteria

**spec PR**:

- **AC-1 (spec)**: `specs/services/console-bff/architecture.md` 가 머지된 origin/main 에 존재 + WMS canonical Identity table + `### Service Type Composition` H3 PASS (HARDSTOP-10 hook fires correctly when `git mv` 시 사용자-편집한다 — 본 PR 은 신규 작성이라 hook 대상 외).
- **AC-2 (PROJECT.md)**: `projects/platform-console/PROJECT.md` 의 `service_types` frontmatter 가 `[frontend-app, rest-api]` (둘 모두 `rules/taxonomy.md` 분류상 declared) + Service Map § v1 에 `console-bff` row 추가 (v2 row 가 v1 로 promote). HARDSTOP-02 미발화 (재classification 정합).
- **AC-3 (contract)**: `console-integration-contract.md` § 2.4.9 신규 sub-section 존재 + hard invariants (D4 verbatim 표 + tenant pass-through + read-only + no producer retrofit) + observability metric set 3종 + edge routing (`console-bff.local` internal-only) + v1 endpoint table (1 row, `GET /actuator/health`). § 2.4.1~2.4.8 byte-unchanged + § 3 parity 카운트 = exactly **16** (드리프트 0).
- **AC-4 (no orphan refs)**: spec PR 의 어떤 markdown link 도 dangling 아님 (architecture.md ↔ contract ↔ ADR-MONO-017 / 013 / 006 / 012 cross-ref 일치). `validate-rules` (skill) green.

**impl PR**:

- **AC-5 (build)**: `./gradlew :projects:platform-console:apps:console-bff:check` exit 0 (compile + unit + slice + IT all green). settings.gradle 에 모듈 등록.
- **AC-6 (Hexagonal layout)**: `apps/console-bff/src/main/java/...` 의 패키지 구조가 `domain/`, `application/`, `adapter/inbound/`, `adapter/outbound/` 4-레이어 분리. `domain/` 의 어떤 클래스도 Spring/`org.springframework.*` import 없음 (framework-free). `application/` 은 ports 통해서만 outbound 호출 — 직접 `RestClient` 인스턴스화 금지 (adapter 책임).
- **AC-7 (auth wiring)**: `SecurityConfig` 가 OAuth2 Resource Server (RS256 JWKS = GAP issuer) 로 inbound 검증. `/actuator/health` permitAll, 그 외 endpoint `authenticated()`. 인증 없는 `GET /some-stub` → 401 (slice test assertion). request-scoped `OperatorCredentialContext` bean 이 `X-Operator-Token` + `X-Tenant-Id` 읽음 (`@RequestScope` + `HttpServletRequest`).
- **AC-8 (credential dispatcher)**: `CredentialSelectionPort` 의 selector predicate 가 § 2.4.9 표 5-row 와 byte-identical (`GAP → operator token`, `{wms,scm,finance,erp} → GAP OIDC access token`). 어떤 fallback path 도 존재 안 함 (`if(operatorToken==null) use gapOidcToken` 류 코드 부재 — `grep`-assert in code-review). Domain unit test 가 5-row 전부 assert.
- **AC-9 (`GET /actuator/health`)**: 200 OK 응답 + Spring Boot Actuator default `{status: "UP"}` shape + 인증 미요구 + Traefik health-check probe 가 본 endpoint 를 향해 설정. slice + IT 양쪽에서 assert.
- **AC-10 (observability bootstrap)**: Micrometer 가 BFF 3-metric family (`bff_fanout_latency_seconds`, `bff_fanout_errors_total`, `bff_aggregation_degrade_count_total`) 를 0-sample 로 노출 (Prometheus scrape). `/actuator/prometheus` 응답에 3-name 모두 등장 (IT assertion). OTel `traceparent` propagation 이 outbound `RestClient` 빌더에 인터셉터로 설치됨 (인스펙션 + 단위 테스트).
- **AC-11 (Traefik + docker-compose)**: `docker compose --project-directory projects/platform-console/infra config -q` valid + `console-bff` 서비스 블록 존재 + `console-bff.local` Traefik router 라벨 활성 + backing service host port 0건 (`expose:` only) + internal-only (Traefik rule 이 외부 CORS 미오픈, `console-web` 서버사이드만 호출 패턴 보존). docker-compose dry-run 검증.
- **AC-12 (Testcontainers IT)**: `@SpringBootTest` + Testcontainers + WireMock 으로 (a) GAP JWKS stub 으로부터 inbound JWT 검증 통과, (b) `/actuator/health` 200, (c) per-domain `CredentialSelectionPort` dispatch 5-row dry-run all-PASS, (d) `/actuator/prometheus` 의 3-metric name 노출. H2 / 인-메모리 substitute 금지 (현 task 는 DB 부재라 substitute 자체 미해당, IT는 fully real Spring context + WireMock-stubbed downstreams).
- **AC-13 (CI green)**: path-filter 가 본 모듈을 빌드/테스트 jobs 에 포함 (e.g. `platform-console-backend-build` 또는 sibling) + 신규 IT job (예: `platform-console-bff-integration-tests`) 가 self-CI run 에 GREEN 등장. 모든 다른 service IT job baseline 회귀 0.
- **AC-14 (hard invariants byte-unchanged)**: 본 impl PR 이 ADR-MONO-017 D1-D8 한 줄도 수정 안 함 (D1-D8 byte-unchanged enforce). § 2.4.5/6/7/8 + § 2.6 byte-unchanged. § 3 GAP-parity matrix attestation-marker count = 16 unchanged.

# Related Specs

- `rules/common.md` + `rules/domains/saas.md` (선언 domain) + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md` (선언 trait — 있으면 로딩).
- [`platform/service-types/rest-api.md`](../../../../platform/service-types/rest-api.md) (mandatory requirements — contract first, JWT bearer validation, idempotency on mutations [BFF 부재], pagination on list endpoints [BFF skeleton 부재], observability).
- [`platform/architecture-decision-rule.md`](../../../../platform/architecture-decision-rule.md), [`platform/testing-strategy.md`](../../../../platform/testing-strategy.md), [`platform/hardstop-rules.md`](../../../../platform/hardstop-rules.md) (HARDSTOP-09 / -10 / -04 보장).
- [`docs/adr/ADR-MONO-017`](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) (governing ADR, D1-D8 HARD INVARIANT).
- [`docs/adr/ADR-MONO-013`](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D5 / § D6 Phase 7 / § 3.3 (parent prescription 실행).
- [`docs/adr/ADR-MONO-014`](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (operator token exchange — BFF 가 § 2.4.9 GAP leg credential 로 inherit).
- [`docs/adr/ADR-MONO-015`](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) (Composed-overview pattern — Phase 7 MVP 가 5도메인 generalise).
- [`docs/adr/ADR-MONO-006`](../../../../docs/adr/ADR-MONO-006-observability-stack.md) (Vector + VictoriaMetrics — D7.A reuse base).
- [`docs/adr/ADR-MONO-012`](../../../../docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md) D3 canonical form (Identity table + Service Type Composition H3).
- [`docs/adr/ADR-MONO-001`](../../../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C (hostname-based routing).
- [`projects/platform-console/PROJECT.md`](../../PROJECT.md) (classification, Service Map v1 — 본 task 가 갱신).
- [`projects/platform-console/specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) (sibling — BFF inbound auth flow 가 console-web server route 의 token forward 의 consumer).
- precedent: [`projects/finance-platform/specs/services/account-service/architecture.md`](../../../finance-platform/specs/services/account-service/architecture.md) (Spring Boot 3.4 + Hexagonal + OAuth2 RS shape reference); [`projects/erp-platform/specs/services/masterdata-service/architecture.md`](../../../erp-platform/specs/services/masterdata-service/architecture.md) (두 번째 precedent).

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md` (Hexagonal layout)
- `.claude/skills/backend/springboot-api/SKILL.md` (Spring Boot REST API)
- `.claude/skills/backend/jwt-auth/SKILL.md` (OAuth2 Resource Server / JWT 검증)
- `.claude/skills/cross-cutting/observability-setup/SKILL.md` (Micrometer + OTel)
- `.claude/skills/backend/testing-backend/SKILL.md` (Testcontainers IT 패턴)
- `.claude/skills/service-types/rest-api-setup/SKILL.md` (rest-api 서비스 부트스트랩)

# Related Contracts

- **신규 author (spec PR)**: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 — BFF surface frame. v1 endpoint surface row = `GET /actuator/health` 단일.
- **byte-unchanged enforce**: § 2.4.1 ~ § 2.4.8 + § 2.5 + § 2.6 + § 3 parity matrix (attestation-marker count = 16). 한 줄도 본 PR 에서 수정되지 않음.
- **producer 측 contract 변경 0**: GAP `admin-api.md`, wms `gateway-public-routes.md`, scm `gateway-public-routes.md`, finance `account-api.md`, erp `masterdata-api.md` 어느 것도 본 task 가 수정하지 않음 (ADR-MONO-017 § 3.3 zero retrofit 다섯 번째 확인).

# Target Service

- `console-bff` (신규)

# Architecture

Follow:

- `projects/platform-console/specs/services/console-bff/architecture.md` (본 spec PR 이 author)

# Implementation Notes

**spec PR 먼저 머지** — impl PR 은 spec PR 머지된 origin/main 위에서 시작. HARDSTOP-09 회피의 정공법 (architecture decision before code; finance/erp masterdata 동일 패턴).

**상속받는 hard invariants 일체 보존**:

- ADR-MONO-017 D1-D8 (REST orchestrator / server-side fan-out / 기존 read 재사용 / 도메인별 credential / per-domain CB / `tenant_id` pass-through / per-domain attribution / MVP=1) — byte-unchanged enforce.
- console-integration-contract.md § 2.4.5/6/7/8 + § 2.6 — BFF 가 dispatcher 일 뿐 rewriter 아님.
- § 3 GAP-parity matrix 카운트 = exactly 16 (FE-006 no-drift guard 가 다시 한 번 검증).

**Spring Boot 3.4 + Java 21 + Servlet stack** — finance/erp masterdata 동일. WebFlux/Reactive 도입 금지 (resilience+observability 라이브러리 표준 stack 이 servlet).

**`CredentialSelectionPort` 단순 design**:

```java
public sealed interface OutboundCredential {
    record OperatorToken(String token) implements OutboundCredential {}
    record GapOidcAccessToken(String token) implements OutboundCredential {}
}

public interface CredentialSelectionPort {
    OutboundCredential selectFor(DomainTarget domain);  // GAP → OperatorToken; {wms,scm,finance,erp} → GapOidcAccessToken
}
```

선택자 predicate 가 § 2.4.9 표 verbatim 일치 — domain unit test 가 5-row 전부 assert. 어떤 fallback 도 metaprogramming 도 reflection 도 없음 (단순 sealed switch).

**`OperatorCredentialContext`** — `@RequestScope` bean. `HttpServletRequest` injection → `X-Operator-Token` 헤더 + `X-Tenant-Id` 헤더 읽고 record 로 노출. `null`/blank fail-closed.

**`SecurityConfig`** — `oauth2ResourceServer.jwt()` + `JwtDecoders.fromIssuerLocation(gapIssuerUrl)` (RS256, JWKS rotation 자동). audience claim validator 추가 (BFF audience 등록 시 GAP 측 client registration 별도 — 본 task 는 standard validation 으로 충분, audience 강제는 후속).

**docker-compose 신규 서비스 블록 — internal-only Traefik label**:

```yaml
console-bff:
  build:
    context: ../../../apps/console-bff
  expose:
    - "8080"
  labels:
    - "traefik.enable=true"
    - "traefik.http.routers.console-bff.rule=Host(`console-bff.local`)"
    - "traefik.http.routers.console-bff.entrypoints=web"
    - "traefik.http.services.console-bff.loadbalancer.server.port=8080"
    # NOTE: CORS NOT enabled — internal-only; only console-web server-side calls reach this hostname
  networks:
    - traefik-net
  environment:
    SPRING_PROFILES_ACTIVE: docker
    CONSOLE_BFF_GAP_ISSUER_URL: http://gap.local
```

`apps/console-bff/Dockerfile` — finance account-service Dockerfile 패턴 (multi-stage gradle build + JRE 21 alpine + non-root user).

**CI path-filter** — `.github/workflows/` paths-filter config 에 `projects/platform-console/apps/console-bff/**` 추가 (existing `platform-console-frontend-*` job 과 별도의 `platform-console-bff-*` job pair — build + IT). path-filter negation 사용 금지 (MONO-074/075 quirk, project memory `project_ci_path_filter_074_075_quirk`).

**FE side 무관여** — 본 task 는 `apps/console-web/` 어느 파일도 만지지 않는다 (BFF skeleton 만 — console-web 의 BFF 호출 wiring 은 TASK-PC-FE-011 에서). dispatcher 가 frontend file edit 시 그 자체로 scope 초과 신호.

**branch 이름** — `task/pc-be-001-console-bff-bootstrap-spec` (spec PR) + `task/pc-be-001-console-bff-bootstrap-impl` (impl PR). 어디에도 `master` 포함 금지 (project memory `project_branch_hygiene_policy` — sandbox `--force` regex 차단 방지).

# Edge Cases

- **Spec PR 머지 전 impl 시작** → HARDSTOP-09 hook 발화 (architecture.md 부재 상태에서 `apps/console-bff/**` 코드 추가는 spec 선재 가드를 위반). Mitigation: PR Separation Rule 엄수 (spec PR → review/merge → impl PR 의 base = main 갱신).
- **`X-Operator-Token` 부재 + GAP leg 호출 시도** → fail-closed `401 TOKEN_INVALID` (절대 inbound `Authorization` 의 GAP OIDC access token 으로 fallback 금지 — #569 invariant). CredentialSelectionPort 단위 테스트가 명시 assert.
- **`X-Tenant-Id` 부재** → fail-closed `400 NO_ACTIVE_TENANT` before any outbound (mirror `console-web` server-route).
- **GAP JWKS 일시 장애** → spring-security 의 JWKS cache + retry 정책 표준 사용. JWKS unavailability 가 inbound auth 검증 실패로 401 surface (NOT 503) — JWKS는 inbound principal 검증의 일부.
- **outbound leg 의 `403`** → 본 task 에서는 MVP route 부재로 발화 path 없음; § 2.4.9 frame 에 정의된 분류 (`forbidden`) 가 TASK-PC-FE-011 에서 실 use-case 와 만남.
- **outbound leg 의 `5xx` / timeout / circuit_open** → 동일 — MVP route 부재; degrade-policy 단위 테스트가 empty composition use-case stub 으로 shape 만 명세.
- **`console-bff.local` 가 외부 DNS resolution 노출** → 호스트 환경 `/etc/hosts` 또는 `dnsmasq` 가 127.0.0.1 매핑 (LNC TEMPLATE.md 표준). Traefik rule 이 host-header 기반이라 다른 도메인 가져온 트래픽은 404 라우팅. 추가로 docker-compose `networks` 가 `traefik-net` 만 join (internal-only 보존).
- **OAuth2 Resource Server 가 `X-Operator-Token` 을 두 번째 principal 로 오해** → SecurityConfig 가 `jwt()` 단일 만 enable, `X-Operator-Token` 은 SecurityFilterChain 의 일부 아닌 `OperatorCredentialContext` (`@RequestScope`) 가 HttpServletRequest 에서 직접 읽음. 인증 체인이 그것을 principal 로 사용하지 않음.
- **path-filter negation 사용 회귀** → 새 job 추가 시 positive filter 만 사용 (`platform-console-bff/**`). MONO-074/075 회귀 회피.
- **CI build matrix 가 본 모듈 미인지** → settings.gradle include + `gradle` `:projects:platform-console:apps:console-bff` 가 resolve 되는지 사전 검증 (`./gradlew projects | grep console-bff`).

# Failure Scenarios

- **architecture.md 미작성 후 impl 시작** → HARDSTOP-09 발화 (mechanically detected by hook). Mitigation: AC-1, spec PR 머지 선행.
- **HARDSTOP-10 hook 가 신규 console-bff/architecture.md 의 canonical form 미통과** → AC-1 + ADR-MONO-012a (HARDSTOP-10 hook fixture 가 WMS Identity-table form 검증). Mitigation: WMS form 그대로 답습 + `Identity` H2 + `### Service Type Composition` H3 명시.
- **`CredentialSelectionPort` 가 fallback path 코드 도입** → AC-8 + ADR-MONO-017 D4.A 위반 (HARD INVARIANT). Mitigation: sealed `OutboundCredential` + 단순 switch + 도메인 단위 테스트 5-row exhaustive + code review 의 `grep` "fallback|catch|orElse|then.*Token" 검사.
- **`Idempotency-Key` / `X-Operator-Reason` 스캐폴딩 BFF 에 도입** → ADR-MONO-017 § 2.4.9 hard invariants "no mutation at MVP" 위반. Mitigation: code-review grep + 본 PR 의 production code 에 두 헤더 string literal 0건 검증 (`rg "Idempotency-Key|X-Operator-Reason" apps/console-bff/src/main`).
- **producer spec/contract 수정** → ADR-MONO-017 § 3.3 zero-retrofit 위반. Mitigation: spec PR + impl PR 의 변경 파일이 5개 producer spec 디렉토리 (`projects/{global-account,wms,scm,finance,erp}-platform/specs/contracts/`) 에 닿지 않음을 PR diff 검사.
- **`console-web` 어떤 파일이라도 수정** → scope 초과 (TASK-PC-FE-011 의 일). Mitigation: 본 PR diff 가 `apps/console-web/**` 한 줄도 안 닿음을 검사.
- **§ 3 parity 카운트 변동** → ADR-MONO-013 D1-D8 byte-unchanged 위반 (HARDSTOP-04). Mitigation: FE-006 의 `parity-verification.test.ts` no-drift guard + 본 PR 의 `console-integration-contract.md` diff 가 § 3 section 의 16 row 자체에 닿지 않음을 검사.
- **path-filter 가 본 모듈 미커버** → CI 빌드 안 됨 (silent regression — green-wash). Mitigation: self-CI run 에서 `platform-console-bff-*` job 이 visible + GREEN 확인 (run log 에 module compile evidence).
- **GAP V0011/13/14/15/17/18 dependent / GAP issuer URL 잘못** → IT JWKS fetch 실패. Mitigation: WireMock 으로 GAP JWKS stub (IT 가 실 GAP 미의존), production-config 는 `CONSOLE_BFF_GAP_ISSUER_URL` env var 로 외부 주입.

# Test Requirements

- **domain unit**: `CredentialSelectionPort` 5-row (`GAP → operator`, `wms/scm/finance/erp → gap-oidc`) exhaustive switch test; degrade-policy 분류 (`ok`/`degraded`/`forbidden`) 단위 (composition use-case fixture 이지만 outbound port 부재 상태로 분류 함수만 단위 가능).
- **application unit**: empty composition use-case stub (`OperatorOverviewCompositionUseCase` placeholder) 가 outbound port stub 으로 partial-failure rendering shape 단위 (TASK-PC-FE-011 에서 실제 카드 채울 hook).
- **slice (`@WebMvcTest`)**: `/actuator/health` 200 + 인증 미요구 + 다른 endpoint stub 의 인증 없는 호출 → 401 (SecurityConfig 가 `authenticated()` chain 적용 확인). `GlobalExceptionHandler` 가 표준 error envelope 으로 직렬화.
- **integration (`@SpringBootTest` + Testcontainers + WireMock)**: 전체 BFF Spring context boot + WireMock GAP JWKS stub + `/actuator/health` 200 + `/actuator/prometheus` 가 3-metric family name 노출 + per-domain `CredentialSelectionPort` 5-row dispatch dry-run all-PASS + 인증된 stub endpoint 호출 시 `OperatorCredentialContext` 가 `X-Operator-Token` + `X-Tenant-Id` 정상 노출.
- **CI green**: 새 `platform-console-bff-build` + `platform-console-bff-integration-tests` jobs 가 self-CI run 에 등장 + GREEN.

# Definition of Done

- [ ] **spec PR**: architecture.md + PROJECT.md service_types 패치 + Service Map v1 promotion + § 2.4.9 (BFF surface frame, hard invariants, v1 endpoint = `GET /actuator/health`) + 본 task md + INDEX.md ready 섹션 업데이트, all reviewed/merged.
- [ ] **impl PR**: `apps/console-bff/` Hexagonal Spring Boot skeleton + `SecurityConfig` + `CredentialSelectionPort` + `OperatorCredentialContext` + `GET /actuator/health` controller + Dockerfile + docker-compose + Traefik label + settings.gradle 등록 + CI path-filter 추가.
- [ ] Tests added (domain unit + application unit + slice + Testcontainers IT) + all PASS locally + CI green (`platform-console-bff-build` + `platform-console-bff-integration-tests`).
- [ ] Hard invariants byte-unchanged enforce — ADR-MONO-017 D1-D8 + § 2.4.5/6/7/8 + § 2.6 + § 3 parity matrix (count=16) 한 줄도 수정되지 않음.
- [ ] Producer spec/contract 변경 0 (`projects/{global-account,wms,scm,finance,erp}-platform/specs/contracts/` diff 0).
- [ ] `apps/console-web/` 변경 0 (scope 초과 방지).
- [ ] Specs updated first (HARDSTOP-09): spec PR 가 impl PR 전에 main 머지.
- [ ] Ready for review.

# Verification

1. `specs/services/console-bff/architecture.md` 존재 + WMS canonical Identity table + `### Service Type Composition` H3 (구현 commit 전).
2. `projects/platform-console/PROJECT.md` 의 `service_types` = `[frontend-app, rest-api]` + Service Map § v1 에 `console-bff` row.
3. `console-integration-contract.md` § 2.4.9 신규 sub-section + § 2.4.1~2.4.8 + § 2.5 + § 2.6 + § 3 byte-unchanged.
4. 5 producer spec 디렉토리 변경 0 (PR diff inspection).
5. `apps/console-web/` 변경 0 (PR diff inspection).
6. `./gradlew :projects:platform-console:apps:console-bff:check` exit 0.
7. `docker compose --project-directory projects/platform-console/infra config -q` valid + `console-bff.local` Traefik router 라벨 활성 + backing service host port 0건.
8. `/actuator/health` 200 + `/actuator/prometheus` 3-metric name 노출 + Testcontainers IT GREEN + `CredentialSelectionPort` 5-row dispatch dry-run all-PASS.
9. `CredentialSelectionPort` 코드에 fallback path string ("fallback", "orElse" with token, second-token-attempt 류) 0건 + production code 의 `Idempotency-Key` / `X-Operator-Reason` string literal 0건 (HARD INVARIANT enforce — `grep`).
10. self-CI run 에서 `platform-console-bff-*` job GREEN + baseline 다른 service IT job 회귀 0.
11. self-review APPROVED (review-checklist Spec / Arch / Quality / Security / Testing).
