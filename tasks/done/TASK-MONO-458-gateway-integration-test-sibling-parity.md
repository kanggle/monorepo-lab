# Task ID

TASK-MONO-458

# Title

게이트웨이 통합 테스트가 형제 3개에 없다 — 7개 중 4개만 라우팅/필터를 full-context 로 검증한다

# Status

done

# Owner

monorepo

# Task Tags

- code
- test

# Goal

함대의 게이트웨이 7개 중 **4개**(ecommerce · iam · fan · scm)는 라우팅 + JWT 인증 필터 + rate-limit 를 **full-context 통합 수준**에서 검증하는 테스트를 갖는다. 나머지 **3개**(erp · finance · wms)는 **필터·라우트 config 의 unit 테스트만** 있고 통합 테스트가 **0개**다.

## 실측 (2026-07-21, `main` 전수 — ⚠️ AC-0 에서 재측정할 것)

| project | gateway IT 수 | 검출된 통합 하네스 |
|---|---|---|
| iam | **4** | `GatewayIntegrationTest` · `GatewayRateLimitIntegrationTest` · `GatewayTenantPropagationIntegrationTest` · `GatewayResilienceIntegrationTest` (+ `route/GatewayRouteResolutionTest`) |
| fan | 4 | ✅ |
| scm | 4 | ✅ |
| ecommerce | 2 | ✅ |
| **erp** | **0** | ❌ `@SpringBootTest`/`@Testcontainers`/`WebTestClient` 전무 — unit(route config, filter) 만 |
| **finance** | **0** | ❌ 동일 |
| **wms** | **0** | ❌ 동일 (unit 12개) |

## 왜 결함인가 — unit 만으로는 배선이 증명되지 않는다

게이트웨이의 핵심 성질(JWT 인증 필터가 실제 라우트에 걸리는가, rate-limit 키가 라우팅 경로에서 적용되는가, 다운스트림 장애 시 resilience 가 동작하는가)은 **필터 클래스를 홀로 unit 테스트해서는 볼 수 없다**. `platform/testing-strategy.md § A test that bypasses the enforcement layer proves nothing` 이 명시하는 그대로 — 필터가 **라우트에 wiring 되지 않았어도** filter unit 테스트는 동일하게 통과한다. iam 이 갖춘 `GatewayIntegrationTest` 계열은 요청을 **실제 필터 체인 + 라우트 해석기를 통과**시켜 이 배선을 단언한다. erp/finance/wms 는 그 단언이 없어 **배선이 깨져도 초록**이다.

이 task 는 **iam 의 IT 패턴을 형제 3개에 이식**해 형제 정합을 회복한다. 단, **맹목 복사가 아니다**(아래 Scope AC-2) — 각 게이트웨이가 *실제로 배선한* 필터만 단언한다.

# Scope

## In Scope

1. **erp · finance · wms 게이트웨이에 통합 테스트 추가.** 최소 커버리지(각 게이트웨이가 실제 배선한 것에 한해):
   - 라우팅: 알려진 경로가 올바른 다운스트림으로 해석되는가 (라우트 해석 full-context).
   - JWT 인증 필터: 유효 토큰 통과 / 무효·부재 토큰 거부 — **라우트를 통과하는 요청으로**.
   - rate-limit: 배선돼 있으면 통과 경로에서 적용됨을 단언 (Redis 등 백엔드가 필요하면 Testcontainers).
2. 각 게이트웨이의 `specs/services/<gateway>/architecture.md` **Test Requirement 절**을 읽고 그 서비스가 요구하는 통합 커버리지에 맞춘다(형제 iam 의 목록이 아니라 **대상 서비스의 스펙**이 기준).
3. CI 배선 확인 — 세 게이트웨이의 `:integrationTest` 가 `ci.yml` 의 해당 프로젝트 통합 레인에 **이미 포함되는지** 확인하고, 누락이면 추가(그렇지 않으면 테스트를 써도 CI 가 안 돈다 = 없는 테스트).

## Out of Scope

- **iam 테스트의 맹목 복사.** iam 은 `GatewayTenantPropagationIntegrationTest` 를 갖지만, tenant 전파를 하지 않는 게이트웨이에 그 테스트를 이식하면 **없는 성질을 단언하는 픽스처**(`env_test_fixture_impossible_input_proves_nothing`)가 된다. 각 게이트웨이가 배선한 것만 단언한다.
- 게이트웨이의 **라우팅/필터 로직 변경.** 이 task 는 테스트 추가만. 테스트가 실제 결함을 드러내면 **별건 fix task 로 분리**한다.
- ecommerce(IT=2) 를 iam 수준(4)으로 끌어올리는 것 — ecommerce 는 이미 통합 커버리지가 있어 이 형제-straggler 의 대상이 아니다(범위는 IT=0 인 3개).
- 다른 계층(unit/slice)의 보강 — 감사 결과 비-게이트웨이 서비스 40개는 슬라이스+IT 형제 정합이 이미 완전하다.

# Acceptance Criteria

**AC-0 — 착수 전 재측정 (verify-then-act).** 위 표(7 게이트웨이 / IT 4·4·4·2·0·0·0)를 `origin/main` 에서 **다시 센다.** 인계된 숫자는 출처가 아니라 **가설**이다(`feedback_recount_population_dont_inherit_scope`). 특히: ① IT 검출을 파일명(`*IntegrationTest`)이 아니라 **하네스 내용**(`@SpringBootTest`/`@Testcontainers`/`WebTestClient`)으로 세라 — 이 감사의 1차 집계는 파일명만 세다 슬라이스를 과소집계했고(`*ControllerSliceTest`/`*ControllerWebMvcTest` 누락), 게이트웨이도 `@SpringBootTest`+`WebTestClient` IT 가 `*IntegrationTest` 이름 없이 존재할 수 있다. ② 세 대상 중 하나라도 그 사이 IT 가 생겼으면 그 사실이 먼저다. 다르면 표를 정정하고 진행한다.

**AC-1 — 참조 패턴을 먼저 읽는다.** iam 의 `GatewayIntegrationTest` 계열 4종을 **열어** 이 저장소가 게이트웨이 IT 를 어떻게 부트스트랩하는지(base 클래스, 테스트 issuer/JWT 발급, 다운스트림 스텁 = WireMock/MockWebServer, rate-limit 백엔드) 파악한다. 형제의 관행을 재발명하지 않는다.

**AC-2 — 배선을 통과하는 테스트여야 한다(우회 금지).** 추가하는 각 IT 는 요청을 **실제 라우트 + 필터 체인**으로 통과시켜야 한다. 필터를 직접 인스턴스화하거나 라우트 config 만 읽는 테스트는 이 AC 를 만족하지 못한다 — 그건 이미 있는 unit 테스트다(`platform/testing-strategy.md § A test that bypasses the enforcement layer`).

**AC-3 — 배선이 살아있음을 mutation 으로 증명한다.** 대상 게이트웨이 하나에서 **JWT 필터 등록(또는 라우트)을 일시 제거**하면 새 IT 가 **RED** 가 되고, 원복하면 GREEN 이 됨을 보인다(`platform/testing-strategy.md § G3`). 초록은 물었다는 증거가 아니다.

**AC-4 — CI 에서 실제로 실행됨을 확인한다.** 세 게이트웨이의 `:integrationTest` 가 `ci.yml`(또는 `_integration.yml` 호출)에서 해당 프로젝트 레인에 포함돼 **러너에서 도는지** 확인한다. `disabledWithoutDocker` clean-skip 로 로컬 초록을 CI 실행으로 오인하지 않는다(`env_host_timezone_test_fixture_convention` 계열 함정). 로컬(Windows) 이 아니라 **CI Linux 가 권위**(`project_testcontainers_docker_desktop_blocker`).

**AC-5 — 형제 정합을 명시한다.** 세 게이트웨이가 iam 과 **왜 다른 커버리지를 갖는지**(tenant 전파 없음 등)를 각 IT 또는 PR 설명에 적는다. 형제 불일치를 **말하는 정합**으로 만든다 — 다음 감사가 다시 "3개는 왜 없지?" 를 묻지 않도록.

# Related Specs

> **Before reading**: 각 대상 프로젝트의 `PROJECT.md` → `rules/common.md` + 선언된 domain/trait 을 먼저 로드(`platform/entrypoint.md` Step 0). Unknown tag = Hard Stop.

- `platform/testing-strategy.md` — § Integration Tests, § A test that bypasses the enforcement layer proves nothing, § G3 (mutation), Test Pyramid
- `projects/iam-platform/apps/gateway-service/src/test/java/com/example/gateway/integration/` — **참조 패턴**(`GatewayIntegrationTest`, `GatewayRateLimitIntegrationTest`, `GatewayTenantPropagationIntegrationTest`, `GatewayResilienceIntegrationTest`)
- `projects/erp-platform/apps/gateway-service/` · `.../specs/services/gateway-service/architecture.md` — 대상 1, Test Requirement 절
- `projects/finance-platform/apps/gateway-service/` · 동 architecture.md — 대상 2
- `projects/wms-platform/apps/gateway-service/` · 동 architecture.md — 대상 3
- `.github/workflows/ci.yml` + `.github/workflows/_integration.yml` — 세 게이트웨이 `:integrationTest` 가 레인에 포함되는지 (AC-4)

# Related Contracts

없음 (테스트 추가 전용, API/이벤트 계약 무변경). 라우팅 대상 경로는 각 게이트웨이의 라우트 정의(`application.yml`)를 진실원으로 읽는다.

# Edge Cases

- **게이트웨이 IT 는 서비스 IT 와 다르다 — DB 가 없다.** 게이트웨이는 상태가 없어 Postgres Testcontainer 가 불필요할 수 있다. 다운스트림은 **스텁**(WireMock/MockWebServer)으로 세운다. rate-limit 이 Redis 기반이면 그때만 Redis Testcontainer 가 필요.
- **테스트 issuer/JWT 발급.** 인증 필터를 통과시키려면 게이트웨이가 신뢰하는 issuer 로 서명한 테스트 토큰이 필요하다. iam 의 base 가 이걸 어떻게 만드는지(AC-1)를 그대로 쓴다 — 손수 재구현하면 `env_shared_issuer_authenticated_is_not_authorized` 함정.
- **세 게이트웨이의 라우트/필터 구성이 서로 다를 수 있다.** finance/erp 게이트웨이는 `TASK-MONO-357` 로 뒤늦게 신설돼 라우트 수가 적을 수 있다. 커버리지는 **각자 배선한 라우트 집합**에 맞춘다(하드코딩 목록 금지).
- **reactive 테스트의 비결정성.** Spring Cloud Gateway 는 WebFlux — `WebTestClient` 타임아웃/순서 가정이 CI 에서 흔들릴 수 있다(`env_crossservice_consumer_it_assignment_flake` 교훈). 블로킹 대기보다 명시적 단언을 쓴다.
- **`@Testcontainers(disabledWithoutDocker)` 는 로컬에서 조용히 skip.** `BUILD SUCCESSFUL` 이 실행됨을 뜻하지 않는다 — XML 리포트로 실제 실행 확인, 권위는 CI Linux.

# Failure Scenarios

- **가장 위험: 배선을 우회하는 IT 를 써서 초록을 만드는 것.** 라우트 config 만 읽거나 필터를 직접 인스턴스화하면 unit 을 IT 이름으로 다시 쓴 것일 뿐 — AC-2/AC-3 이 이걸 막는다. **필터를 떼도 초록이면 그 테스트는 아무것도 지키지 않는다.**
- **iam 테스트 맹목 이식.** tenant 전파를 안 하는 게이트웨이에 tenant 전파 IT 를 복사하면 존재하지 않는 성질을 단언하는 픽스처가 된다(초록이지만 무의미, 혹은 억지 배선을 유도).
- **테스트만 쓰고 CI 레인에 안 넣는 것.** `:integrationTest` 가 `ci.yml` 에 없으면 러너에서 안 돌아 로컬 밖에서는 없는 테스트다(AC-4). `project_nightly_only_spec_merges_green_then_main_reds` 의 거울상.
- **로컬 초록을 권위로 삼는 것.** Windows 호스트 Testcontainers 는 FLAKY — 로컬 판정을 최종으로 쓰면 안 된다(`project_testcontainers_docker_desktop_blocker`).

# Notes

- **출처**: 2026-07-21 전 프로젝트 테스트 커버리지 정밀 감사(계층별 × 서비스별 매트릭스). 비-게이트웨이 서비스 40개는 슬라이스+IT 형제 정합 완전; 실질 갭은 이 게이트웨이 IT 3건뿐. **1차 집계의 "Controller 있는데 slice 없음" 13건은 전부 오탐**(네이밍 3종 차이)이었고 본문 확인으로 걸러냄 — 이 티켓의 숫자도 같은 이유로 AC-0 에서 재측정 대상.
- **범위 판정**: `,iam` 일몰(`TASK-MONO-367`)과 같은 이유로 **root task 로 승격** — 게이트웨이 3개가 3개 프로젝트에 걸쳐 있어 단일 프로젝트 task 로는 형제-parity 를 조율할 수 없다. 다만 편집 대상은 각 프로젝트 내부 테스트 파일뿐(공유 파일 무변경, HARDSTOP-03 무관). PR 은 프로젝트별 3개로 쪼개도, 하나로 묶어도 무방(독립적, 원자성 요구 없음).
- **구현 모델**: 형제 패턴 이식이 본체지만 "각 게이트웨이가 무엇을 배선했고 무엇을 단언해야 하는가" 는 맹목 복사가 아닌 판정이다. **분석=Opus 4.8 / 구현 권장=Sonnet**(참조 패턴이 명확하고 기계적 이식에 가까움 — 단 AC-2/AC-5 의 배선-판정은 주의). 세 게이트웨이를 병렬 dispatch 하면 각각 `backend-engineer`(model=sonnet).
- 인접: `TASK-MONO-357`(finance/erp 게이트웨이 신설 — 이들이 형제 표준을 못 따라잡은 근인) · `TASK-MONO-367`(게이트웨이 fleet-wide 작업의 root-승격 선례) · `platform/testing-strategy.md § enforcement bypass`.

[[project_enforcement_straggler_sibling_parity]] [[project_iam_e2e_covers_operator_flow_not_gateway_user_path]] [[feedback_recount_population_dont_inherit_scope]] [[env_test_fixture_impossible_input_proves_nothing]] [[project_testcontainers_docker_desktop_blocker]]
