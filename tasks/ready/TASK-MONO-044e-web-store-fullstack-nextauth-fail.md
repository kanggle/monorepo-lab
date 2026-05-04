# Task ID

TASK-MONO-044e

# Title

web-store full-stack e2e 의 NextAuth fetch 실패 + Playwright timeout fix (compose stack 부팅 후 노출)

# Status

ready

# Owner

backend / frontend

# Task Tags

- ci
- test
- frontend
- backend

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

[TASK-MONO-044b](../done/TASK-MONO-044b-traefik-net-ci-overlay-fix.md) 가 traefik-net 부재를 보상하여 docker compose stack 부팅이 회복되었으나, 후속 Playwright suite 가 NextAuth fetch 실패와 timeout 으로 fail. 044b spec § Out of Scope #2 에 명시적으로 follow-up 분리된 영역.

CI run `25340350845` (PR #200, 044b) 의 `Frontend E2E full-stack (web-store, Playwright + docker compose)` Job 로그:

```
Run docker network create traefik-net || true   # 044b 추가 step PASS
docker compose -f docker-compose.yml -f docker-compose.ci.yml up --build -d  # PASS
docker compose ... logs --tail=50 gateway-service                            # PASS

[WebServer] [auth][error] TypeError: fetch failed   # NextAuth (web-store SSR) — 반복
[WebServer] [auth][error] TypeError: fetch failed
... (8회 이상 반복)

✘ e2e/auth-redirect.spec.ts:41:7 — 인증 필요 라우트 보호 (NextAuth + GAP)
   › 로그인 페이지에 ?error=account_type_mismatch 가 있으면 안내가 표시된다
   strict mode violation: getByRole('alert') resolved to 2 elements:
     1) <div role="alert" class="alert-error">admin 계정으로는 web-store ...</div>

Test timeout of 60000ms exceeded.
Error: locator.fill: Test timeout of 60000ms exceeded.
  - waiting for "http://localhost:3000/login?error=Configuration" navigation to finish...
  - navigated to "http://localhost:3000/login?error=Configuration"
```

두 distinct 이슈가 같은 Job 안에서 동시에 발생:

1. **NextAuth fetch fail** — web-store Next.js SSR 의 NextAuth 가 backend 의 무언가 (gateway? auth-service? OIDC userinfo?) 를 fetch 하다 실패. `?error=Configuration` 페이지로 redirect 됨. 결과적으로 모든 인증 의존 시나리오가 timeout.
2. **Strict-mode locator violation** — `getByRole('alert')` 가 2 개 alert element 에 매칭. Playwright strict mode 위반. test 또는 page 변경.

본 task 가 fix 후 `Frontend E2E full-stack (web-store)` Job FAILURE → SUCCESS, main CI 4 회귀 청소 시리즈 (044/044a/044b/044c/044d/044e) 완전 종결.

---

# Scope

## In Scope

### A. NextAuth fetch fail 진단 + fix

- web-store SSR 의 NextAuth callback 이 fetch 시도하는 endpoint 식별 (auth-service `/oauth2/token`? gateway 경유 `/api/auth/...`? OIDC userinfo?)
- CI 의 docker compose 네트워크 토폴로지에서 web-store webServer (Playwright 가 `http://localhost:3000` 에서 띄움) 가 어떤 host:port 로 backend 를 호출하는지 확인:
  - `playwright.config.ts` 의 webServer block 환경 변수
  - web-store `.env` (또는 CI 가 generate 한 synthetic env)
  - `NEXTAUTH_URL` / `API_URL_INTERNAL` / `BACKEND_GATEWAY_URL` 등
- gateway-service 컨테이너가 healthy 인 시점에 web-store 가 callback 을 시도하는지 race / timing 확인
- root cause 별 fix:
  - **(a) env URL 미설정**: synthetic .env 생성 step 갱신 (TASK-MONO-014 패턴)
  - **(b) host alias 누락**: `host.docker.internal` vs container name 차이 — workflow step 또는 webServer 환경 변수 조정
  - **(c) gateway → auth-service routing 회귀**: 044a 의 servlet leak fix 가 의도치 않게 reactive routing 변경 — gateway 의 RouteLocator 또는 SecurityFilterChain 점검 (044a 회귀 가능성)
  - **(d) auth-service 자체가 GAP IT 33 회귀와 동일 root cause**: TASK-MONO-044c 와 dependency 발생 가능

### B. Strict-mode locator violation fix

- `e2e/auth-redirect.spec.ts:41` — 두 alert element 가 매칭되는 원인:
  - 페이지가 의도적으로 두 alert 표시 (admin 안내 + 일반 안내) 인지
  - 이전 test 의 alert 가 leak (state cleanup 누락) 인지
  - locator 를 더 strict 하게 수정해야 (`.alert-error.first()` 또는 `getByText()` 로 명확화)
- 페이지 또는 테스트 둘 중 어느 쪽이 회귀인지 판정

## Out of Scope

- web-store 의 비즈니스 로직 변경 (인증 외 cart/order 등)
- 다른 Playwright project (fan-platform-web smoke) 의 동일 패턴 점검 — 본 task 가 fix 한 후 nightly 회귀 평가 영역
- 다른 e2e Job (gateway-master / fan-platform v1 / e2e smoke) — 044a/044b/044d 영역
- 044c (GAP integration) — 별 task

---

# Acceptance Criteria

## CI 회복

1. `Frontend E2E full-stack (web-store, Playwright + docker compose)` Job FAILURE → SUCCESS
2. 4 full-stack Playwright spec (`golden-flow`, `cart-management`, `auth-redirect`, `wishlist`) 모두 PASS

## 진단 + 분류

3. NextAuth fetch fail 의 root cause 가 (a)/(b)/(c)/(d) 중 어디인지 PR description 에 명시
4. (c) 또는 (d) 인 경우 cross-task dependency 명시 + sequencing 결정 (044c 와 합칠지 분리할지)
5. strict-mode locator violation 의 책임이 페이지 회귀인지 테스트 회귀인지 PR description 에 명시

## 회귀 0

6. web-store unit test + e2e smoke (TASK-MONO-013 영역) 회귀 0
7. ecommerce backend 12 service `:check` 회귀 0
8. 회귀 보고서 `knowledge/incidents/2026-05-05-ci-regression.md` 에 web-store full-stack 후속 결과 단락 추가

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § Job 4
- [TASK-MONO-044a (servlet leak fix)](../done/TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) — 직접 선행 (gateway 부팅 회복)
- [TASK-MONO-044b (traefik-net CI overlay fix)](../done/TASK-MONO-044b-traefik-net-ci-overlay-fix.md) — 직접 선행 (compose stack 부팅 회복)
- [TASK-MONO-014 (frontend e2e fullstack CI)](../done/TASK-MONO-014-frontend-e2e-fullstack-ci.md) — Job 정의 + synthetic env 패턴
- `projects/ecommerce-microservices-platform/services/web-store/` (Next.js + NextAuth)
- `projects/ecommerce-microservices-platform/specs/contracts/http/auth-api.md`
- `projects/ecommerce-microservices-platform/services/web-store/playwright.config.ts`
- `.github/workflows/ci.yml` § frontend-e2e Job

---

# Related Contracts

- `auth-api.md` § OAuth2 Clients — web-store-client (TASK-MONO-027/043 으로 등록됨)

---

# Target Service / Component

- `projects/ecommerce-microservices-platform/services/web-store/`
  - NextAuth 설정 (`app/api/auth/[...nextauth]/route.ts` 또는 동등 위치)
  - `playwright.config.ts` (필요 시)
  - `e2e/auth-redirect.spec.ts:41` (locator 회귀 fix 시)
- `.github/workflows/ci.yml` § frontend-e2e Job (synthetic env 갱신 시)
- (root cause 가 backend 면 해당 ecommerce service 도 변경 가능성)

---

# Implementation Notes

- **첫 단계**: CI run 에서 web-store webServer log 와 gateway-service log 를 동시 확인 (workflow 가 `docker compose logs` 로 일부 출력). NextAuth 가 어떤 URL 로 fetch 시도하는지 stack trace 로 추적.
- 로컬 재현: `cd projects/ecommerce-microservices-platform && pnpm web-store:e2e:full-stack` (또는 동등 명령) — TASK-MONO-014 가 정의한 절차.
- (c) gateway routing 회귀 검증: 044a 머지 전후 `wms-platform`/`GAP`/`fan-platform`/`ecommerce` gateway 의 `application.yml` route 정의 비교 — 만약 `libs/java-web-servlet` 분리 와중에 ecommerce gateway 의 의존성이 의도치 않게 변경되었으면 ecommerce gateway 도 영향.
- (d) auth-service 가 GAP 의 auth-service 와 동일한 root cause 면 TASK-MONO-044c 의 fix 가 본 task 도 해소. 044c 머지 후 본 task 재실행으로 검증 권장.
- `[auth][error] TypeError: fetch failed` 가 8회 반복은 NextAuth 의 일부 retry/refresh 사이클 — 단일 endpoint 만 막혔는지, 모든 endpoint 가 unreachable 인지 구분.

---

# Edge Cases

1. **race / cold-start**: gateway-service `up -d` 직후 healthcheck 통과 시점이 web-store webServer 의 첫 callback 보다 늦을 가능성. workflow 의 wait-for step 또는 `playwright.config.ts` 의 webServer `timeout` 조정 필요.
2. **CI runner DNS / network**: NextAuth 가 `http://localhost:18080` (gateway host port) 또는 docker network DNS (`gateway-service`) 중 어느 것을 사용하는지 — synthetic env 의 의도와 실제 동작 검증.
3. **strict-mode locator 가 production 회귀**: 페이지가 두 alert 를 의도적으로 표시한다면 design change. test 갱신이 정답. 만약 테스트가 가정하는 1 alert 가 옳다면 페이지가 회귀 → web-store 코드 수정.
4. **TASK-MONO-044c 와 dependency**: GAP auth-service 의 28 회귀가 본 fetch fail 의 직접 원인이면 본 task 진행 전 044c 머지 대기 — sequencing 명확히.

---

# Failure Scenarios

## A. NextAuth fetch fail 의 root cause 가 044c 와 동일

044c 가 fix 하면 본 task 의 절반 (NextAuth 부분) 자동 해소. strict-mode locator violation 만 별도 fix 필요. PR scope 축소.

## B. NextAuth fetch fail 이 (c) gateway routing 회귀 - 044a 회귀

044a 의 fix 가 의도와 다르게 ecommerce gateway 도 영향을 줬다면 production 코드 추가 fix. 본 task 범위 안.

## C. CI runner 자원 한계 (RAM/timeout) 에 의한 sporadic

단일 fix 로 deterministic 해결 안 됨. TASK-MONO-044 § AC #8 (nightly 회귀 방지) 영역으로 분리.

## D. 두 이슈가 sequencing 의존

NextAuth fetch fix 후에야 strict-mode locator 가 가시화 (혹은 그 반대). 단계별 fix + 검증.

---

# Test Requirements

- 4 full-stack Playwright spec 로컬 PASS (CI 환경 mimic 권장)
- web-store unit test + e2e smoke 회귀 0
- main CI `Frontend E2E full-stack (web-store)` Job SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] NextAuth fetch fail root cause 분류 + fix
- [ ] strict-mode locator violation 책임 분류 + fix
- [ ] (필요 시) 044c 와의 dependency 명시 + sequencing 결정
- [ ] 4 full-stack Playwright spec 로컬 PASS
- [ ] main CI `Frontend E2E full-stack (web-store)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — NextAuth + docker compose 네트워크 토폴로지 + 가능한 044a 회귀 추적 + Playwright strict-mode 동시 분석. fix 자체는 작을 수 있으나 진단이 핵심.
- **분량 추정**: synthetic env 1줄 + (가능 시) NextAuth route 1 파일 + (가능 시) 1 spec 파일. 작거나 medium PR.
- **dependency**:
  - `선행`: TASK-MONO-044a + 044b (이미 머지됨). TASK-MONO-044c 가 NextAuth fetch fail 의 root cause 면 044c 우선 머지 후 본 task 재진단.
  - `후속`: 본 task fix 가 main CI 4 회귀 청소 시리즈 의 마지막 종결.
- **CI gating**: 본 PR 자체 영향 = `Frontend E2E full-stack (web-store)` Job FAIL → SUCCESS. 다른 Job 영향 0.
- **시리즈 종결**: 044 series 의 6 task (044/044a/044b/044c/044d/044e) 가 main CI 4 회귀 청소 series 전체. 044e 가 머지되면 main green 회복 + 회귀 incident `2026-05-05` 종결 단락 가능.
