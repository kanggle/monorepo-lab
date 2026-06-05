# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-FE-XXX`: frontend
- `TASK-INT-XXX`: integration

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.


## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
- Do not modify a task file after it moves to `review/` or `done/`.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

| ID | Title | Service | Tags |
|---|---|---|---|
| TASK-BE-077 | 쿠폰/프로모션 시스템 — 할인 쿠폰 발급, 사용, 프로모션 관리 기능 | promotion-service (신규) | code, api, event |
| TASK-BE-078 | 알림 서비스 — 이메일/SMS/푸시 알림 발송 기능 | notification-service (신규) | code, api, event |
| TASK-BE-079 | 리뷰/평점 시스템 — 상품 리뷰 작성, 평점 관리, 평균 평점 집계 | review-service (신규) | code, api, event |
| TASK-BE-080 | 위시리스트/찜 기능 — 사용자별 관심 상품 목록 관리 | user-service | code, api |
| TASK-BE-081 | 배송 추적 서비스 — 주문 배송 상태 관리 및 추적 | shipping-service (신규) | code, api, event |
## ready

| ID | Title | Service | Tags |
|---|---|---|---|
| TASK-BE-143 | product-service `ProductImageService` 의 `infrastructure.storage.StorageProperties` 직접 import 제거 — `domain/port/ProductImageBucketResolver` 추출 + `ProductImageRepository.saveAll` dead code 제거 (trivial bundling). 2026-05-15 dry-run finding A1 single-PR closure (B/C/D polish 는 DEFER). 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — small refactor. | product-service | code, test, refactor |

## in-progress

_(없음)_

## review

| ID | Title | Service | Tags |
|---|---|---|---|

## done

| ID | Title | Service | Tags |
|---|---|---|---|
| TASK-FE-072 | **DONE (2026-06-02, 3-dim verified)**. ecommerce 프런트(`web-store`+`admin-dashboard`) Docker 빌드 최적화 (build-infra; bundled spec-less per `feedback_pr_bundling`). `# syntax=docker/dockerfile:1` + BuildKit 캐시 마운트 2건(`deps`: pnpm store, `builder`: 앱별 `.next/cache` `/app/apps/<app>/.next/cache`). runner 스테이지 무변경(standalone/static/public만, `.next/cache` 미복사 → 이미지 크기 불변). **실측 2026-06-02 (web-store, buildx --provenance=false): cold 445s → warm(소스 1줄) 79s (~82%↓, 366s 절감) → 무변경 4s.** platform-console TASK-PC-FE-035(console-web, 60%↓) 검증 패턴의 이식. **impl PR #1027** (squash `38ffebdb`). **3-dim 검증**: (a) state=MERGED + mergeCommit `38ffebdb` + mergedAt 2026-06-02T05:54:06Z; (b) `git log origin/main` tip=`38ffebdb` 일치; (c) pre-merge failing required=0 (**Frontend E2E smoke incl web-store 3m48s** = 새 Dockerfile 로 web-store 빌드+Playwright PASS / **Frontend lint & build (ecommerce) 2m9s** = 두 프런트 빌드 PASS / 全 Integration·E2E·Build·Package GREEN; Observability footprint=skipping). diff scope=Dockerfile×2 + task lifecycle + INDEX only (앱 src/producer/contract/spec/ADR grep NONE). AC-1..AC-6 ALL PASS. provenance off는 빌드-명령 플래그라 문서화만(미커밋). 분석=Opus 4.8 / 구현=Opus 4.8(직접; build-infra 기계적 이식) / 리뷰=Opus 4.8(직접 3-dim). **메타**: ecommerce 두 프런트는 console-web 보다 cold 가 커(445s vs 229s, turbo workspace 의존성 多) `.next/cache` 증분 절감 비율도 더 큼(82% vs 60%). 후속 후보=공유 frontend base Dockerfile 통합(console-web+ecommerce, root TASK-MONO 성격). | web-store, admin-dashboard | code, deploy, test |
| TASK-INT-024 | **DONE (2026-06-02)**. ADR-MONO-021 §3.3 step 3(D4 step3) — web-store GAP e2e 가 `account_type=CONSUMER` 클레임의 full OIDC 왕복(GAP id_token→NextAuth profile→jwt→session→`/api/auth/session`)을 단언. `account-type-claim.spec.ts`(`shouldSkipGap()` 게이트→기존 CI skip, nightly GAP suite 실행) + `iam-consumer-seed.sql` account_type='CONSUMER' 명시(+구식 "미발급" 주석 정정). 정적검증: tsc clean + compose config valid. 클레임 발급은 BE-329 `FormLoginIntegrationTest`(실 MySQL, 실제 SAS 토큰 디코드)로 결정론적 증명 — 본 spec 은 NextAuth 전파 레이어 추가(AC-1 라이브=nightly GAP CI 위임; 실행 중 GAP 이미지가 account_type 이전이라 로컬 재빌드는 호스트 fragility 대비 비례성 낮음). impl PR #1014 squash `aa3ec383`. 3차원 ✓. **ADR-MONO-021 §3.3 steps 1–3 완료.** 분석=Opus 4.8 / 구현=Opus. | web-store | test, e2e, auth, multi-tenant |
| TASK-INT-023 | **DONE (2026-06-01)**. web-store real-GAP e2e — RP-initiated logout AC-1 자동화. TASK-FE-070 라이브 검증 갭 종결. **lean** GAP 스택(mysql+redis+kafka-placeholder+auth-service, **account-service 불필요** — 로그인은 `auth_db.credentials`만; account_type 클레임 미발급도 signIn 통과) + consumer 시드 1행 + `loginAsSeededConsumer`(실 Spring `#username`/`#password` 폼) + `rp-initiated-logout.spec.ts`(`shouldSkipGap()` 게이트→기존 CI 무회귀) + CI 핸드오프 문서(`.github` classifier 차단). **라이브 실증** 실행 중 federation-e2e GAP(V0012+BE-328) 대상 `1 passed (51.0s)`. compose config+tsc clean. impl PR #1004 squash `73b7b01c`. 3차원 ✓. 공유 federated-logout(fan/admin/console) transitively 검증. 분석=Opus 4.8 / 구현=Opus. | web-store | test, e2e, infra, auth |
| TASK-FE-071 | **DONE (2026-06-01)**. admin-dashboard RP-initiated OIDC logout (GAP `end_session`) — web-store(FE-070) client-driven 패턴 기계적 미러(client=`ecommerce-admin-dashboard-client`, port 3001, OPERATOR). id_token 캡처 + `federated-logout.ts`(server-only `getToken`→end_session URL) + route `/api/auth/end-session-url` + `auth-context` logout=fetch→`signOut({redirect:false})`→navigate(fallback `/login`). 테스트 갱신 불필요(AuthGuard/Sidebar가 `useAuth` 모킹). **BE-328**로 V0012 admin URI effective. tsc+lint clean. impl PR #1002 squash `25e7fff7`. 3차원 ✓. **= 모든 GAP-OIDC 프런트(console/fan/web-store/admin) 로그아웃 패리티 sweep 완결.** AC-1 라이브는 후속 스모크((A) 결정). 분석=Opus 4.8 / 구현=Opus. | admin-dashboard | code, security, auth |
| TASK-FE-070 | **DONE (2026-06-01)**. web-store RP-initiated OIDC logout (GAP `end_session`) — 콘솔(PC-FE-033)·fan(FAN-FE-002) 동일 silent-relogin 결함 패리티. id_token 캡처(jwt callback) + `federated-logout.ts`(server-only `getToken`→end_session URL) + route `/api/auth/end-session-url` + client `logout()`=URL fetch→`signOut({redirect:false})`→navigate; cart/token-bridge cleanup 보존; 2 vitest 갱신(CI green). **depends on BE-328**(매퍼 post-logout→RegisteredClient → V0012 web-store URI effective). tsc+lint clean. impl PR #1000 squash `158253bf`(fan+ecommerce 묶음). 3차원 ✓. **AC-1 라이브 브라우저는 후속 스모크**(GAP 수락 BE-328 일반증명 + fetch-fail→local-only fallback; 사용자 (A) 결정). 분석=Opus 4.8 / 구현=Opus. | web-store | code, security, auth |
| TASK-BE-144 | impl PR #928 (squash `fb2a0786`) + this close chore. user-service `RestProductInfoProvider` 의 `RestTemplate` → `RestClient` 마이그레이션 — 외부 prototype 이관 잔재였던 monorepo 마지막 production `RestTemplate` 사용처를 표준(Spring 6 `RestClient`)으로 정렬. `RestTemplateConfig`→`RestClientConfig`(`wishlistRestClient` bean, Boot 3.4 `ClientHttpRequestFactoryBuilder`, connect 3s/read 5s 보존) + provider(`getForObject`→`get().uri().retrieve().body()`, 병렬 fan-out·`DELETED` fallback 무변경) + unit test(`RestClient` fluent-chain mock, 5 시나리오 보존) + `dependencies.md` spec 1줄. HTTP wire 동작 byte-identical. CI 그린(0 fail), 3-dim 머지 검증 통과. 분석=Opus 4.7 / 구현=Opus 4.7 — small refactor. 2026-05-29. | user-service | code, test, refactor |
| TASK-BE-292 | **REVIEWED → approved** (2026-05-16, `/review-task` single-task, review-checklist Spec/Arch/Quality/Security PASS · Perf N/A · Testing PASS via task Verification; fix task 0; on-disk 재검증: 0 envelope-field change + feature/use-case non-orphan + dead-ref 0). impl `task/spec-drift-cohort-2026-05-16` (spec-only, no `apps/`, decision-bearing). **WI-1 (E11) 결정 = (B) accept-inline + remove false promise**: 근거 = 5개 portfolio 프로젝트 0/5 가 shared-schema 미이행 (GAP `.gitkeep` placeholder, wms/scm/fan `schemas/` 부재) → de-facto 관행 = inline-per-contract. `schemas/README.md` 를 "intentionally unused / inline-by-choice" 로 rewrite (19 inline copy 무변경, envelope field diff 0 = semantically inert). **ADR 불요** — (B) 는 새 portfolio convention 미설정·기존 현실 비준 (shared-schema model 채택 시에만 ADR 필요 → README 에 명시 deferred). **WI-2 (E22) 결정 = (A) Anchor**: wishlist 실재 (`user-service/architecture.md` Domain Scope + `dependencies.md` `wishlist_items` 테이블 + product-service enrich + 4-endpoint 완전 계약) → Edge Case "real but lacks feature/use-case → (A)". `specs/features/wishlist-management.md` (English, user-management.md 구조) + `specs/use-cases/wishlist.md` (Korean, user-profile-and-address.md 구조 4 UC) authoring + `wishlist-api.md` Overview 에 upstream traceability (envelope 무변경). (B) retire 면 user-service live 모델과 모순 (Failure Scenario). dead-ref 4-file 0, apps/ 0. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7. | contracts/schemas, wishlist | api, event, adr |
| TASK-BE-291 | **REVIEWED → approved** (2026-05-16, `/review-task` single-task, review-checklist Spec/Arch/Quality/Security PASS · Perf N/A · Testing PASS via task Verification). impl `task/spec-drift-cohort-2026-05-16` (spec-only, no `apps/`). G6/E6 deprecated in-tree auth-service 잔재 reconcile. **rewrite-to-GAP** (live feature): `features/user-management.md` Profile-Creation/Withdrawal flow + Business Rule + Related Events 표 → GAP `AccountSignedUp` 발행/credential-invalidation GAP-internal (L12 guard 와 정합) · `use-cases/user-profile-and-address.md:200` → GAP 세션 무효화 · `services/user-service/overview.md:46-47` → GAP owner. **guard** (historical): `features/authentication.md` body steps + Related Events 표 strikethrough (자체 L6-18 배너와 정합, Failure Scenario 표 미guard 회피) · `use-cases/signup-and-login.md` Korean deprecation 배너(authentication.md 미러) + actor 토큰 strike · `services/batch-worker/overview.md:21` REMOVED guard · `user-service/overview.md:66` 동파일 L21/70 형식 정합 guard(내부 일관성). 리뷰 재검증: AC grep unguarded current-actor=0 (`signup-and-login.md:5` 는 DEPRECATED 배너 내부 = guarded, false-positive), dead-ref 6-file 0, apps/ 0. Edge Case(consumer behavior 보존·언어 유지) 준수. fix task 0. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7. | features/use-cases | adr |
| TASK-BE-143 | spec PR #528 (6c1fca14) + impl PR #529 (f8cf59ef) + this chore. product-service `ProductImageService` 의 `infrastructure.storage.StorageProperties` 직접 import 제거 — `domain/port/ProductImageBucketResolver` interface + `S3ProductImageBucketResolver` adapter 추출 (3 call site port 경유). CHERRY-2 trivial bundling: `ProductImageRepository.saveAll` dead-code 제거 (0 production caller). 9 files (+70 / -20) — 3 신규 prod + 1 신규 test + 4 modified + 1 lifecycle move. 205/205 unit pass (16 ProductImageServiceTest signature 갱신 + 1 S3ProductImageBucketResolverTest 신규). HTTP API / Kafka event / S3 bucket name byte-identical. CI 15/15 PASS + 1 SKIP. **메타**: day-one DDD cohort 4번째 검증 (ecommerce/order BE-140 + wms/master BE-141 + scm/procurement DEFER + ecommerce/product 본 task). sweep gain = retrofit era specific 가설 확정. 분석=Opus 4.7 / 구현=Sonnet 4.6. 2026-05-15. | product-service | code, test, refactor |
| TASK-BE-142 | spec PR #475 (1fa73ae5) + impl PR #476 (578ba7b2) + this chore. BE-141 deferred polish 직접 후속. ecommerce 10 backend service overview.md (order / payment / product / promotion / review / search / shipping / user / notification / batch-worker) 일괄 enhancement — fan-platform sibling-equivalent depth (Service identity table + Public surface + 5 Key invariants + Out of scope (v1) 4 신규 섹션). 기존 7 섹션 lower-half (Owned Data / Published Interfaces / Dependent Systems) 보존. 12 files / +624 / -255. 완료 시 ecommerce 13 service overview.md 전체 일관성 완성 (auth-service-deprecated 1개 제외, deprecation). CI markdown-only path-filter 15 SKIP + 1 PASS. production code 0. BE-141 / FAN-BE-006 / MONO-084 같은 same-day single-PR closure 패턴 답습. 분석=Opus 4.7 / 구현=Opus. 2026-05-14. | 10 services | spec, overview, be |
| TASK-BE-141 | spec PR #472 (2bb0591b) + impl PR #473 (5dbc2c9d) + this chore. `/refactor-spec all --dry-run` 2026-05-14 ecommerce HIGH-B closure. 3 frontend-edge service overview.md (gateway-service 33 → ~70 / web-store 34 → ~85 / admin-dashboard 32 → ~80 line) enhancement to fan-platform sibling-equivalent depth — Service identity table + Public surface (routes\|pages) + 6 Key invariants + Out of scope (v1) 4 신규 섹션. 기존 7 섹션 (Service / Responsibility / Owned Data / Published Interfaces / Dependent Systems) 보존. 나머지 10 service overview.md = sibling consistency 유지, deferred polish. CI markdown-only path-filter 15 SKIP + 1 PASS. production code 0. TASK-FAN-BE-006 같은 same-day single-PR closure 패턴 답습. 분석=Opus 4.7 / 구현=Opus. 2026-05-14. | gateway-service / web-store / admin-dashboard | spec, overview, be, fe |
| TASK-BE-140 | PR #380 머지 (2026-05-12, commit cfe27566). order-service `AdminOrderController` domain 모델 직접 import 제거 — `AdminOrderStatusService.changeStatus()` 가 raw `Order` 대신 application DTO 반환. dry-run 2026-05-11 finding A1 single-PR closure (B/C polish 는 DEFER). architecture.md § Forbidden Dependencies + § Boundary Rules 위반 1건 해소. spec/impl bundled PR. CI 15/15 PASS. 분석=Opus 4.7 / 구현=Opus. | order-service | code, test |
| TASK-BE-138 | order-service choreographed-saga stuck-detector — ADR-MONO-005 § D6 ecommerce order 행 **Gap → Compliant**. PR #366 (spec) + PR #367 (impl) + this chore. `OrderStuckDetector` (60 s `@Scheduled`) + `OrderStuckRecoveryHandler` (REQUIRES_NEW per-order, AOP-split mirror of wms `SagaSweeper` + `SagaRecoveryHandler` from TASK-BE-050) sweep `orders WHERE status='PENDING' AND payment_id IS NULL AND created_at < NOW() - 1800 s`. attempt cap 5 → terminal `STUCK_RECOVERY_FAILED` + `OrderSagaRecoveryExhausted` outbox event (T3 co-commit) → Kafka topic `order.alert.saga.recovery.exhausted`. Flyway V7 (2 cols + composite `idx_orders_status_created_at`). 3 metrics (run / recovery.fired{from_state} / exhausted{from_state}). All knobs externalised under `order.saga.stuck-detector.*`. **Test verification:** unit baseline 5+5 PASS via `./gradlew :order-service:check` (no regression on existing 30+ unit). New IT (`OrderStuckRecoveryIT`, 4 methods) authored — local Testcontainers npipe blocker per `project_testcontainers_docker_desktop_blocker` memory; ecommerce IT excluded from CI baseline by project convention. ADR-MONO-005 § D6 + § 5 + History updated; ADR § 1.1 audit row updated; service `architecture.md` Saga row Gap → Compliant; `order-events.md` adds `OrderSagaRecoveryExhausted` section. 분석=Opus 4.7 / 구현=Opus. 2026-05-11. | order-service | code, event, saga, adr |
| TASK-BE-139 | `TossPaymentsAdapter` Resilience4j wrap — Category B compliance per ADR-MONO-005 § D6. PR #363 (spec, f806bc00) + PR #364 (impl, d483f396). **CI 15/15 PASS** (cycle 3 — cycle 1 BulkheadFullException API + cycle 2 HttpServerErrorException subclass simple-name 2 fix). `@CircuitBreaker(toss-payments)` + `@Retry` + `@Bulkhead` on `confirmPayment` + `cancelPayment` (mirror procurement reference, bulkhead 10 concurrent). HTTP client connect 5s + read 10s timeouts. 4xx → `PgConfirmFailedException` (payment row → FAILED, 502); 5xx/timeout/CB-OPEN/bulkhead-full → fallback → `PgGatewayUnavailableException` (row unchanged, 503 `PG_GATEWAY_UNAVAILABLE`). `PaymentConfirmService` + `PaymentRefundService` distinguish two exception kinds. R4j `ignore-exceptions = [HttpClientErrorException, PgConfirmFailedException]` so 4xx bypasses retry. **ADR-MONO-005 status: PROPOSED → ACCEPTED 2026-05-11** (gate 2/2 satisfied; gate 1/2 = TASK-MONO-055 PR #361). 분석=Opus 4.7 / 구현=Opus. 2026-05-11. | payment-service | code, adapter, resilience4j, integration |
| TASK-BE-137 | payment-service outbox housekeeping — BE-136 self-review W3 (PaymentEventPublishIntegrationTest 에 Refunded 풀 round-trip IT method 추가) + W4 (JpaConfig 가 config/ 에 있는 이유 javadoc 추가, Hexagonal layout 정당화; file move 0). CI 15/15 path-filter 적용 (5 PASS + 10 skipped). PR #354 머지 2026-05-11. | payment-service | code, test, housekeeping |
| TASK-BE-136 | payment-service transactional outbox migration — ADR-006 Scenario A impl (PaymentCompleted/PaymentRefunded → outbox). Producer-side silent-loss 가능성 제거; at-least-once 그룹 합류. PR #345 머지 2026-05-11. | payment-service | code, event |
| TASK-BE-135 | ecommerce at-least-once delivery consistency audit + ADR-006 (per-service decision; payment=Scenario A→TASK-BE-136, user/notification=Scenario B). PR #344 머지 2026-05-11. | 7 services | spec, adr |
| TASK-BE-134 | `user.user.withdrawn` topic alignment — production silent-loss bug fix + canonical Topics table 정합. PR #338 머지 2026-05-11. | user-service | code, event, bug |
| TASK-BE-133 | ecommerce 7 service `dependencies.md` spec backfill — Consumes From / Publishes To / Notes 표준화. PR #337 머지 2026-05-11. | 7 services | spec |
| TASK-FE-069 | web-store 상품 상세/홈 LCP·TTI 개선 — Next.js Image 최적화 활성화(`remotePatterns` 화이트리스트: unsplash + placehold.co + localhost/127.0.0.1 + env-driven `NEXT_PUBLIC_OBJECT_STORAGE_HOSTNAME`) + ReviewList `next/dynamic` 코드 스플리팅 + Suspense boundary(향후 PPR 진입점). 컴포넌트 측 `unoptimized={...placehold.co \|\| localhost}` opt-in 패턴은 fallback 안전망으로 유지. specs/services/web-store/architecture.md § Rendering Strategy + § Image Strategy 갱신. 직접 push (PR 미경유), commit a1260d80. CI green: CI 3m58s + Nightly E2E (full-stack web-store) 8m3s. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6. 2026-05-09. | web-store | code, perf |
| TASK-FE-068 | admin-dashboard SSR `API_URL_INTERNAL` fallback — web-store 와 정합성. PR #238 머지. axios baseURL 결정 로직 SSR/CSR 분기 (`typeof window === 'undefined'` 가드): SSR=`API_URL_INTERNAL ?? NEXT_PUBLIC_API_URL ?? localhost:8080`, CSR=`NEXT_PUBLIC_API_URL ?? localhost:8080`. web-store 와 1:1 일치. docker-compose admin-dashboard env 에 `API_URL_INTERNAL=http://gateway-service:8080` 추가. `.env.example` 주석 갱신. vitest 4-case 양쪽 (admin-dashboard + web-store drift 회귀 방지) 추가. CI green: frontend lint+build 2m9s, unit tests 2m25s, e2e smoke 2m42s, ecommerce boot jars 53s. 2026-05-06. | admin-dashboard, web-store | code, bug |
| TASK-BE-132 | ecommerce auth-service 컴포넌트 폐기 — docker-compose / k8s (services + network-policies + secrets + gateway env) / settings.gradle / CI (build-and-test + boot-jars + frontend-e2e) / .env / spec (services rename + auth-api/auth-events/features deprecated). PR #150 (`chore(ecommerce): TASK-BE-132`) 머지. apps/auth-service/ 소스는 보존 (settings.gradle 에서만 제외). follow-up: TASK-MONO-028 candidate — sync-portfolio.sh standalone v1 freeze policy + AUTH_SECRET grep + done task 깨진 spec 경로 cleanup. 2026-05-04. | auth-service (제거), gateway-service, infra | code, security |
| TASK-FE-067 | web-store + admin-dashboard 의 GAP OIDC cutover — NextAuth v5 신규 도입 + 자체 auth flow 폐기 + sync-portfolio.sh exclusion. PR #148 (`feat(ecommerce)!: TASK-FE-067`) 머지. signIn callback 으로 account_type_mismatch URL 전파 + sync exclusion 7건. follow-up: TASK-BE-132 (auth-service 컴포넌트 폐기), 별도 admin-dashboard SSR baseURL fallback (TASK-FE-068 candidate). 2026-05-04. | web-store, admin-dashboard, packages/api-client | code, security, api, breaking |
| TASK-BE-131 | ecommerce gateway-service JWT 검증 방식을 HS256 → JWKS/RSA(RS256)로 교체 | gateway-service | code, security, test |
| TASK-BE-130 | [Security P2] ReviewController sort 파라미터 허용 값 화이트리스트 검증 추가 | review-service | code, security, test |
| TASK-BE-129 | [Security P1] SearchAdminController admin role check 누락 수정 | search-service | code, security, test |
| TASK-BE-126-fix-002 | TASK-BE-126-fix-001 리뷰 수정 — register / addVariant / updateVariant / deleteVariant role 검증 테스트 누락 추가 | product-service | code, test, security |
| TASK-BE-126-fix-001 | TASK-BE-126 리뷰 수정 — 기존 테스트 X-User-Role 헤더 누락 및 role 검증 신규 테스트 추가 | product-service | code, test, security |
| TASK-BE-126 | [Security P0] AdminProductController / AdminProductImageController admin role check 누락 수정 | product-service | code, security, test |
| TASK-BE-128 | [Security P0] POST /api/payments userId 요청 바디 신뢰 취약점 수정 | payment-service | code, security, api, test |
| TASK-BE-127 | [Security P0] AdminAccountSeeder prod 프로파일 노출 수정 — 하드코딩 관리자 계정 씨딩 제거 | auth-service | code, security, test |
| TASK-FE-066 | admin-dashboard 상품 등록/수정 폼 이미지 업로드 UI — 다중 업로드, 미리보기, 순서 변경, 삭제 | admin-dashboard | code, api |
| TASK-BE-125-fix-001 | TASK-BE-125 리뷰 수정 — 계약 위반 13건 + 최적화 4건 | product-service, search-service | code, api, event |
| TASK-BE-125 | product-service 상품 이미지 업로드/삭제 API + ProductImagesUpdated 이벤트 | product-service, search-service | code, api, event |
| TASK-INT-022-fix-001 | TASK-INT-022 리뷰 수정 — MinIO /tmp 마운트, .env.example, infra NSP PSA, minio-init 리소스 제한 | infra | deploy, code |
| TASK-INT-022 | 상품 이미지 객체 스토리지 인프라 구성 — MinIO(local/dev) + S3(staging/prod) 배선 | infra | deploy, code |
| TASK-FE-065-fix-001 | TASK-FE-065 리뷰 fix: 테스트 파일 3개 widgets import 경로 kebab-case 갱신 | web-store | fix, naming, test |
| TASK-FE-065 | web-store widgets 디렉토리 kebab-case 네이밍 적용 | web-store | code, naming |
| TASK-BE-124-fix-001 | TASK-BE-124 리뷰 지적 수정 — wishlistItemId 미구현 전면 보완 | user-service | code, test |
| TASK-BE-124 | GET /api/wishlists/me/check 응답에 wishlistItemId 추가 — 콘트랙트와 구현 불일치 수정 | user-service | code, test |
| TASK-FE-064-fix-001 | TASK-FE-064 리뷰 이슈 수정: 로그아웃 카트 클리어 테스트 누락 및 토큰 만료 시 카트 즉시 삭제 | web-store | code, test |
| TASK-BE-123 | TASK-BE-122 리뷰 fix: DATA_INTEGRITY_VIOLATION 에러 코드 등록 및 wishlist-api 컨트랙트 갱신 | user-service | code |
| TASK-FE-063-fix-001 | TASK-FE-063 리뷰 fix: 대시보드 위젯 테스트 3종 추가 및 집계 한계 경고 표시 | admin-dashboard | code, test |
| TASK-FE-063 | admin-dashboard 홈 화면 1순위 위젯 구현 — 주문/매출/재고 KPI 및 최근 주문 | admin-dashboard | code, api, test |
| TASK-FE-062-fix-001 | TASK-FE-062 리뷰 fix: cross-feature import 제거 + 에러/로딩 fallback 테스트 추가 | admin-dashboard | code, test |
| TASK-FE-062 | admin-dashboard 주문관리 목록/상세에 주문자 이메일 표시 | admin-dashboard | code, test |
| TASK-BE-119-fix-003 | TASK-BE-118-fix-001 리뷰 fix: RepublishSignupEventsIntegrationTest AdminAccountSeeder 충돌 수정 | auth-service | code, test |
| TASK-BE-119-fix-002 | TASK-BE-119 리뷰 fix: 통합 테스트 파일명 컨벤션 + Kafka producer acks/idempotence 설정 추가 | auth-service | code, test |
| TASK-BE-119 | auth-service AuthEvent → Kafka 발행 브리지 구현 — 인메모리 이벤트를 실제 Kafka로 전송 | auth-service | code, event, test |
| TASK-BE-118-fix-002 | TASK-BE-118-fix-001 리뷰 수정 — SecurityConfig TODO 주석에 연결된 태스크 ID 추가 | auth-service | code |
| TASK-BE-118-fix-001 | TASK-BE-118 리뷰 수정 — 재발행 엔드포인트 통합 테스트 누락 추가 및 보안/로깅 보완 | auth-service | code, test |
| TASK-BE-118 | auth-service 가입 이벤트 재발행 내부 엔드포인트 — user-service user_profiles 누락 복구 수단 제공 | auth-service | code, api, event, test |
| TASK-BE-116-fix-001 | TASK-BE-116 리뷰 수정 — payment-service AmountMismatchException HTTP 상태 코드 422 → 400 수정 | payment-service | code, api |
| TASK-BE-114 | auth-service Google OAuth 2.0 로그인 구현 — Authorization Code Flow, 사용자 find-or-create, JWT 발급 | auth-service | code, api, test |
| TASK-BE-098 | order-service API 컨트랙트 불일치 수정 — POST 요청 필드 차이 및 GET 목록 status 필터 미구현 | order-service | code, api, test |
| TASK-BE-097 | gateway-service 통합 테스트 user-service 라우트 누락 수정 | gateway-service | code, test |
| TASK-BE-096 | gateway-service rate_limited 메트릭 실제 연동 — RequestRateLimiter 429 응답 시 gateway_rate_limited_total 기록 | gateway-service | code, test |
| TASK-BE-095 | gateway-service 외부 클라이언트 X-User-Id / X-User-Email 헤더 스푸핑 방어 — 모든 경로에서 수신 헤더 제거 | gateway-service | code, test |
| TASK-BE-105 | order-service ErrorResponse 컨트랙트 위반 수정 — timestamp 필드 제거 및 IllegalArgumentException 핸들러 추가 | order-service | code, api, test |
| TASK-FE-061 | web-store product API mock 폴백 제거 — non-UUID mock id가 쓰기 API로 흘러 들어가는 경로 차단 | web-store | code, test |
| TASK-BE-116 | 전 서비스 GlobalExceptionHandler HttpMessageNotReadableException 핸들러 추가 — 잘못된 JSON/UUID 본문 시 500 → 400 VALIDATION_ERROR | auth-service, user-service, product-service, order-service, payment-service, shipping-service, review-service, promotion-service, notification-service, search-service | code, api, test |
| TASK-FE-047 | TASK-FE-040에서 발견된 LoginForm oauth_failed 에러 메시지 테스트 누락 수정 | admin-dashboard | code, test |
| TASK-BE-113-fix-001 | TASK-BE-113 리뷰 수정 — order-service 회원 탈퇴 시 주문 취소 배치 저장 실제 구현 | order-service | code, event, test |
| TASK-BE-113 | order-service 회원 탈퇴 시 주문 취소 배치 저장 최적화 | order-service | code, event |
| TASK-BE-109 | order-service 이벤트 발행 Transactional Outbox 패턴 적용 | order-service | code, event |
| TASK-BE-110 | order-service 이벤트 컨슈머 event_id 기반 중복 처리 구현 | order-service | code, event |
| TASK-BE-103 | order-service OrderMetrics 취소 reason 매핑 누락 수정 — user_withdrawn 카운터 추가 | order-service | code, test |
| TASK-BE-099 | order-service Application 레이어 Infrastructure 직접 의존 제거 — OrderMetrics, KafkaTemplate 포트 인터페이스 분리 | order-service | code, test |
| TASK-BE-099-fix-001 | TASK-BE-099 리뷰 수정 — OrderEventPublisher 포트 인터페이스 및 SpringOrderEventPublisher 구현체 추가 | order-service | code, test |
| TASK-BE-092 | gateway-service 스펙 헤더 불일치 수정 — X-User-Role vs X-User-Email 명확화 | gateway-service | code |
| TASK-BE-093 | gateway-service 미사용 RateLimiter Bean 및 Properties 제거 | gateway-service | code, test |
| TASK-BE-094 | gateway-service public 라우트 요청 라우팅 메트릭 누락 수정 | gateway-service | code, test |
| TASK-BE-091 | gateway-service RequestLoggingFilter resolveTargetService 중복 제거 — RouteService 위임 | gateway-service | code, test |
| TASK-INT-021 | Kubernetes 보안 강화 — SecurityContext, NetworkPolicy, secrets 관리 개선 | infra | deploy, code |
| TASK-INT-021-fix-002 | TASK-INT-021 리뷰 수정 2차 — Ingress server-snippets 대체, batch-worker NetworkPolicy kubelet probe, web-store/admin-dashboard SSR egress | infra | deploy, code |
| TASK-INT-021-fix-001 | TASK-INT-021 리뷰 수정 — default-deny Egress, seccompProfile, automountServiceAccountToken, PSS 레이블, Ingress 보안 헤더, PDB, 검증 스크립트 보완 | infra | deploy, code |
| TASK-BE-088 | search-service, payment-service Kafka DLQ 설정 추가 — DeadLetterPublishingRecoverer 적용 | search-service, payment-service | code, event, test |
| TASK-BE-088-fix-001 | TASK-BE-088 리뷰 수정 — payment-service catch 블록 특정 예외 변경 및 DLQ 라우팅 테스트 보강 | search-service, payment-service | code, event, test |
| TASK-FE-030 | isApiError 타입 가드 및 ERROR_MESSAGES 상수 공유 모듈 통합 — web-store 5곳 중복 제거 | web-store, packages/types | code, test |
| TASK-FE-031 | web-store, admin-dashboard 중복 공유 UI 컴포넌트 @repo/ui 통합 — LoadingSpinner, EmptyState, ErrorMessage | web-store, admin-dashboard, packages/ui | code, test |
| TASK-FE-032 | 대형 프론트엔드 컴포넌트 분해 — ProductForm(352줄), AddressForm(339줄), AddressList(263줄) | admin-dashboard, web-store | code, test |
| TASK-FE-033 | 프론트엔드 인라인 스타일 정리 — 스타일 객체 추출 및 CSS Modules 전환 | admin-dashboard, web-store | code, test |
| TASK-BE-084 | auth-service, user-service 이메일 검증 중복 제거 — 서비스별 Email Value Object 추출 | auth-service, user-service | code, test |
| TASK-BE-084-fix-001 | TASK-BE-084 리뷰 수정 — Email VO 정규식 불일치, 정규화 불일치, 최대 길이 검증 누락 | auth-service, user-service | code, test |
| TASK-INT-020 | E2E 테스트 커버리지 확대 — 핵심 비즈니스 흐름 전체 시나리오 추가 | infra | test, deploy |
| TASK-INT-019 | 모니터링 알림 규칙 세분화 — Prometheus AlertManager 알림 정책 구성 | infra | deploy, code |
| TASK-INT-018 | 성능/부하 테스트 구성 — 전체 서비스 부하 테스트 스크립트 및 기준 수립 | infra | test, deploy |
| TASK-INT-017 | specs/use-cases/ 유즈케이스 스펙 정의 — 핵심 사용자 시나리오 문서화 | all | code |
| TASK-FE-029 | admin-dashboard React key 수정 및 api-client 하드코딩 설정 외부화 | admin-dashboard, api-client | code, test |
| TASK-BE-076 | product-service 통합 테스트 Kafka 설정 수정 | product-service | code, test |
| TASK-BE-075 | LoginRateLimitFilter 인터페이스 사용 수정 | auth-service | code |
| TASK-BE-074 | gateway-service CORS ConfigMap 누락 수정 | gateway-service | code |
| TASK-BE-073 | auth-service application→infrastructure 직접 의존 제거 및 gateway CORS 외부화 | auth-service, gateway-service | code |
| TASK-BE-072 | payment-service, batch-worker 도메인 모델 프레임워크 의존성 분리 | payment-service, batch-worker | code |
| TASK-BE-071 | payment-service 이벤트 envelope snake_case 수정 및 토픽명 설정 외부화 | payment-service | code, event |
| TASK-BE-069 | order-service DTO 네이밍 수정 및 gateway-service 서비스 레이어 도입 | order-service, gateway-service | code, test |
| TASK-BE-068 | search-service Elasticsearch 응답 null 안전성 및 예외 처리 개선 | search-service | code, test |
| TASK-FE-028 | 프론트엔드 타입 안전성 강화 및 에러 바운더리 추가 | web-store, admin-dashboard | code, test |
| TASK-BE-067 | product-service 패키지명 수정 — interfaces → presentation | product-service | code, test |
| TASK-BE-066 | 전 서비스 이벤트 소비 실패 처리 개선 — 예외 로깅 강화, 메트릭 추가 | all | code, event, test |
| TASK-BE-065 | 전 서비스 입력 검증 강화 — @Valid 누락, 금액/헤더 검증 보완 | all | code, test |
| TASK-INT-013 | Docker 보안 강화 — Java 서비스 non-root 사용자, OTEL JAR 체크섬 검증 | infra | deploy, code |
| TASK-BE-064 | batch-worker 아키텍처 완성 — 빈 설정 클래스 제거, 도메인 검증 추가 | batch-worker | code, test |
| TASK-BE-063 | product-service soft-delete 쿼리 버그 수정 — findById에서 삭제된 상품 조회 가능 | product-service | code, test |
| TASK-BE-062 | auth-service, user-service PII 로그 노출 제거 | auth-service, user-service | code |
| TASK-BE-061 | 전체 서비스 하드코딩 시크릿 제거 — DB 크레덴셜, JWT 시크릿 설정 외부화 | all | code |
| TASK-INT-016 | specs/features/ 기능 스펙 정의 — 구현 완료된 핵심 기능별 feature 스펙 문서화 | all | code |
| TASK-INT-015 | 누락된 서비스 overview.md 스펙 작성 — user-service, gateway-service, batch-worker, admin-dashboard | all | code |
| TASK-INT-015-fix-001 | admin-dashboard overview.md 포맷 수정 — `## Application` → `## Service` | admin-dashboard | fix |
| TASK-INT-014 | 프론트엔드 Docker 이미지 빌드 및 docker-compose 통합 | infra | deploy, code |
| TASK-INT-013 | Docker 보안 강화 — Java 서비스 non-root 사용자, OTEL JAR 체크섬 검증 | infra | deploy, code |
| TASK-INT-011 | 모니터링 대시보드 — Grafana 프로비저닝 및 Prometheus 메트릭 시각화 | infra | deploy, code |
| TASK-INT-010 | Kubernetes 매니페스트 — 프로덕션 배포 Deployment, Service, Ingress 구성 | infra | deploy |
| TASK-INT-009 | 프론트엔드 ↔ 백엔드 통합 검증 — web-store, admin-dashboard API 연동 확인 | infra | deploy, test |
| TASK-INT-008 | CI/CD 파이프라인 구성 — GitHub Actions 빌드, 테스트, 이미지 빌드 워크플로우 | infra | deploy, code |
| TASK-INT-007 | E2E 통합 테스트 — docker compose 환경 전체 흐름 검증 스크립트 | infra | deploy, test |
| TASK-BE-070 | 전 서비스 한국어 로그 메시지 영어 통일 — 운영 일관성 확보 | all | code |
| TASK-BE-070-fix | TASK-BE-070 리뷰 수정 — order-service, product-service 잔여 한국어 에러 메시지 영어 전환 | order-service, product-service | code |
| TASK-INT-012 | 코드 품질 크로스 리뷰 — 전체 서비스 기술 부채 점검 및 개선 태스크 도출 | all | code |
| TASK-INT-006-fix | TASK-INT-006 리뷰 이슈 수정 — Dockerfile 빌드 실패, restart 정책 누락, NEXT_PUBLIC 환경변수 | infra | deploy, code |
| TASK-INT-006 | docker-compose 리소스 제한 및 프론트엔드 헬스체크 추가 | infra | deploy |
| TASK-BE-060 | product-service Metrics Counter 메모리 누수 테스트 추가 | product-service | code, test |
| TASK-BE-057-fix | 이벤트 발행 실패 메트릭 수정 | all | code, event |
| TASK-BE-057 | 전 서비스 이벤트 발행 실패 모니터링 — 실패 메트릭 및 로그 레벨 통일 | all | code, event, test |
| TASK-BE-056 | search-service ProductUpdated 재고 초기화 버그 수정 및 컨슈머 테스트 추가 | search-service | code, event, test |
| TASK-BE-055 | auth-service 클라이언트 IP 해석 로직 통일 — Rate Limit 우회 방지 | auth-service | code, test |
| TASK-BE-054 | order-service, gateway-service Metrics Counter 등록 메모리 누수 수정 | order-service, gateway-service | code, test |
| TASK-BE-053 | order-service, product-service 동시성 제어 — Optimistic Locking 적용 | order-service, product-service | code, test |
| TASK-FE-026 | web-store, admin-dashboard 인증 컨텍스트 및 API 설정 중복 코드 공통 패키지 추출 | web-store, admin-dashboard | code, test |
| TASK-FE-027 | web-store 메모리 누수 수정 및 에러 핸들링 개선 | web-store | code, test |
| TASK-BE-059 | gateway-service 테스트 컴파일 오류 수정 및 Rate Limiter 설정 외부화 | gateway-service | code, test |
| TASK-BE-058 | user-service 주소 관리 동시성 수정 및 입력 검증 강화 | user-service | code, test |
| TASK-FE-023 | admin-dashboard 사용자 관리 기능 구현 — 사용자 목록, 상세 조회 | admin-dashboard | code, api |
| TASK-FE-024 | TASK-FE-021 리뷰 수정 — 토스트 알림 미구현 및 메시지 auto-dismiss 누락 | web-store | code, test |
| TASK-FE-025 | TASK-FE-022 리뷰 수정 — 빈 상태 UI 중복 렌더링 수정 | web-store | code, test |
| TASK-FE-022 | web-store 배송지 관리 UI 구현 — 목록 조회, 추가, 수정, 삭제 | web-store | code, api |
| TASK-FE-021 | web-store 사용자 프로필 페이지 구현 — 프로필 조회 및 수정 | web-store | code, api |
| TASK-BE-052 | TASK-BE-051 리뷰 수정 — invalidate 보조 인덱스 제거 누락 및 Redis 키 spec 반영 | auth-service | code |
| TASK-BE-051 | TASK-BE-050 리뷰 수정 — 탈퇴 사용자 토큰 무효화 누락 보완 | auth-service | code, event |
| TASK-BE-050 | auth-service UserWithdrawn 이벤트 소비 — 탈퇴 사용자 인증 정보 무효화 | auth-service | code, event |
| TASK-BE-049 | batch-worker 부트스트랩 — 프로젝트 구조, DB 스키마, Spring Batch 설정, 초기 잡 구성 | batch-worker | code |
| TASK-BE-048 | TASK-BE-045 리뷰 수정 — 예외 처리 패턴 통일, 로그 언어 일관성 | order-service | code, event |
| TASK-BE-047 | TASK-BE-043 리뷰 수정 — 서비스 네이밍, CANCELLED 주문 예외 처리, 테스트 보완 | order-service | code, event |
| TASK-BE-046 | order-service Kafka DLQ 설정 추가 — 전 컨슈머 DeadLetterPublishingRecoverer 적용 | order-service | code, event |
| TASK-BE-045 | order-service UserWithdrawn 이벤트 소비 — 탈퇴 사용자 활성 주문 취소 처리 | order-service | code, event |
| TASK-BE-044 | order-service PaymentRefunded 이벤트 소비 — 환불 완료 시 주문 환불 상태 반영 | order-service | code, event |
| TASK-BE-043 | order-service PaymentCompleted 이벤트 소비 — 결제 완료 시 주문 결제 상태 반영 | order-service | code, event |
| TASK-BE-042 | user-service 리뷰 수정 2차 — Kafka 발행 실패 처리, 불필요 이벤트 방지, 쿼리 최적화, 예외 위치 수정 | user-service | code |
| TASK-BE-041 | user-service 리뷰 수정 — 이벤트 트랜잭션 분리, DLQ 설정, Admin 권한 체크, 쿼리 최적화 | user-service | code, event |
| TASK-BE-040 | 배송 주소 관리 API — 목록 조회, 추가, 수정, 삭제 | user-service | code, api |
| TASK-BE-039 | 사용자 프로필 조회/수정 API + UserProfileUpdated 이벤트 발행 | user-service | code, api, event |
| TASK-BE-038 | user-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델, UserSignedUp 이벤트 소비 | user-service | code, event |
| TASK-FE-020 | 주문 목록 status 필터 미동작 수정 — 계약 업데이트 및 클라이언트 필터 연동 | admin-dashboard | code, api |
| TASK-FE-019 | admin-dashboard 주문 관리 기능 구현 — 주문 목록, 상세, 취소 | admin-dashboard | code, api |
| TASK-FE-018 | FilterBar searchValue prop 동기화 버그 수정 | admin-dashboard | code, test |
| TASK-FE-017 | 상품 목록 name 필터 구현 — API 계약 업데이트 및 프론트엔드 연동 | admin-dashboard | code, api |
| TASK-FE-016 | TASK-FE-015 리뷰 수정 — 버그 수정, 타입 안전성, 누락 테스트 추가 | admin-dashboard | code, test |
| TASK-FE-015 | admin-dashboard 기반 구성 및 상품 관리 기능 구현 | admin-dashboard | code, api |
| TASK-FE-014 | 주문 상세 페이지 결제 정보 표시 | web-store | code, api |
| TASK-FE-013 | TASK-FE-011 주문 플로우 누락 테스트 추가 | web-store | code, test |
| TASK-FE-012 | TASK-FE-011 주문 플로우 구현 수정 — FSD 위반, 페이지네이션, 계약 불일치, 코드 품질 | web-store | code, api |
| TASK-FE-011 | web-store 주문 플로우 구현 — 주문 생성, 주문 내역, 주문 상세 | web-store | code, api |
| TASK-FE-010 | web-store 장바구니 기능 구현 — 클라이언트 상태 관리 및 UI | web-store | code |
| TASK-FE-009 | web-store 인증 플로우 구현 — 로그인, 회원가입, 세션 관리 | web-store | code, api |
| TASK-FE-008 | api-client 테스트 보완 — 토큰 갱신 재시도 및 엣지 케이스 | frontend | code, test |
| TASK-FE-007 | web-store 컴포넌트 및 페이지 테스트 추가 | web-store | code, test |
| TASK-FE-006 | @repo/api-client 및 @repo/types 테스트 추가 | frontend | code, test |
| TASK-FE-005 | web-store 가격 필터 버그 및 UX 개선 수정 | web-store | code |
| TASK-FE-004 | api-client Payment API X-User-Id 헤더 누락 수정 (불필요 — Gateway 자동 주입) | frontend | code, api |
| TASK-FE-003 | web-store 상품 목록 및 검색 페이지 구현 | web-store | code, api |
| TASK-FE-002 | @repo/api-client 및 @repo/types 구현 — Gateway API 연동 기반 | frontend | code, api |
| TASK-FE-001 | 프론트엔드 모노레포 부트스트랩 — Turborepo + 공유 패키지 초기 구성 | frontend | code |
| TASK-BE-037 | 전 서비스 비즈니스 메트릭 구현 — Micrometer Counter/Histogram 등록 | all | code |
| TASK-BE-036 | docker-compose OpenTelemetry Java Agent 설정 — 트레이싱 전파 및 Jaeger 연동 | infra | code, infra |
| TASK-BE-035 | auth-service 비즈니스 메트릭 구현 — Micrometer Counter 6종 | auth-service | code |
| TASK-BE-034 | 전 서비스 Observability 적용 — 트레이싱, 메트릭 노출, Health Check 표준화 | all | code, infra |
| TASK-BE-033 | libs/java-observability 공통 라이브러리 — 구조화 로깅, MDC 필터, Micrometer 공통 설정 | libs | code |
| TASK-BE-032 | docker-compose 전체 구성 — 인프라 + 전체 서비스 컨테이너 | infra | code, infra |
| TASK-BE-031 | gateway-service 부트스트랩 — Spring Cloud Gateway, JWT 검증 필터, 라우팅, Rate Limiting | gateway-service | code, api |
| TASK-INT-005 | payment-service Kafka 전환 — 컨슈머 @KafkaListener, 프로듀서 KafkaTemplate | payment-service | code, kafka |
| TASK-INT-004 | order-service Kafka 전환 — 프로듀서/컨슈머 전환 | order-service | code, kafka |
| TASK-INT-003 | search-service Kafka 컨슈머 전환 — @KafkaListener + 이벤트 envelope | search-service | code, kafka |
| TASK-INT-002 | product-service Kafka 프로듀서 전환 — KafkaProductEventPublisher | product-service | code, kafka |
| TASK-INT-001 | Kafka 인프라 구성 — docker-compose, build.gradle, application.yml | infra | code, kafka |
| TASK-BE-030 | 결제 조회 API + OrderCancelled 소비 — 환불 처리 + PaymentRefunded 이벤트 | payment-service | code, api, event |
| TASK-BE-029 | payment-service OrderPlaced 이벤트 소비 — 결제 생성 + 처리 + PaymentCompleted 이벤트 | payment-service | code, event |
| TASK-BE-028 | payment-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델 | payment-service | code |
| TASK-BE-027 | order-service StockChanged 이벤트 소비 — ORDER_RESERVED 시 PENDING → CONFIRMED 전이 | order-service | code, event |
| TASK-BE-026 | 주문 취소 API — POST /api/orders/{orderId}/cancel + OrderCancelled 이벤트 | order-service | code, api, event |
| TASK-BE-025 | 주문 조회 API — GET /api/orders, GET /api/orders/{orderId} | order-service | code, api |
| TASK-BE-024 | 주문 생성 API — POST /api/orders + OrderPlaced 이벤트 | order-service | code, api, event |
| TASK-BE-023 | order-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델 | order-service | code |
| TASK-BE-022 | 상품 검색 API — GET /api/search/products (키워드, 필터, 팩싯) | search-service | code, api |
| TASK-BE-021 | 상품 이벤트 소비 및 인덱스 동기화 — ProductCreated/Updated/Deleted/StockChanged | search-service | code, event |
| TASK-BE-020 | search-service 부트스트랩 — 프로젝트 구조, Elasticsearch 설정, 도메인 모델 | search-service | code |
| TASK-BE-019 | 재고 조정 API + StockChanged 이벤트 | product-service | code, api, event |
| TASK-BE-018 | 상품 수정 + 삭제 API + ProductUpdated/ProductDeleted 이벤트 | product-service | code, api, event |
| TASK-BE-017 | 상품 등록 + 조회 API + ProductCreated 이벤트 | product-service | code, api, event |
| TASK-BE-016 | product-service 부트스트랩 — 프로젝트 구조, DB, 도메인 모델 | product-service | code |
| TASK-BE-015 | auth-service 동시 세션 제한 및 비활성 타임아웃 — 사용자당 최대 세션 수 제한, 비활성 세션 자동 만료 | auth-service | code |
| TASK-BE-014 | auth-service 도메인 이벤트 발행 — UserSignedUp, UserLoggedIn, UserLoggedOut, TokenRefreshed, LoginFailed | auth-service | code, event |
| TASK-BE-013 | auth-service 감사 로그 구현 — 로그인 시도, 토큰 갱신, 로그아웃 이력 기록 | auth-service | code, api |
| TASK-BE-012 | auth-service Token Rotation 원자성 보강 및 SHA-256 해시 중복 제거 | auth-service | code |
| TASK-BE-011 | auth-service 누락 테스트 추가 — LoginRateLimitFilter, RedisAccessTokenBlocklist, JsonAuthenticationEntryPoint, DTO 검증 | auth-service | code, test |
| TASK-BE-010 | auth-service 보안 및 에러 처리 수정 — Authorization 헤더 검증, AccessDeniedException 처리, 에러 코드 등록 | auth-service | code, api |
| TASK-BE-009 | auth-service refresh API 계약 수정 — 토큰 로테이션 응답 명시 | auth-service | code, api |
| TASK-BE-008 | auth-service 계약 준수 및 DB 최적화 — 타임스탬프 타입 수정, 중복 인덱스 제거, 트랜잭션 설정 | auth-service | code, api |
| TASK-BE-007 | auth-service 보안 및 데이터 정합성 수정 — 비밀번호 최대 길이 제한 및 Redis 원자적 연산 | auth-service | code |
| TASK-BE-006 | auth-service 레이어 의존성 위반 수정 — application→presentation DTO 제거 및 infrastructure config 추상화 | auth-service | code |
| TASK-BE-005 | auth-service refresh API 계약 준수 및 아키텍처 개선 | auth-service | code, api |
| TASK-BE-004 | auth-service 보안 버그 수정 — DUMMY_HASH, JWT secret 검증 | auth-service | code |
| TASK-BE-003 | 토큰 갱신 + 로그아웃 API | auth-service | code, api |
| TASK-BE-002 | 회원가입 + 로그인 + JWT 발급 API | auth-service | code, api |
| TASK-BE-001 | auth-service 부트스트랩 — 프로젝트 구조, DB/Redis, 도메인 모델 | auth-service | code |