# Task ID

TASK-MONO-421

# Title

포괄 예외 핸들러가 405(잘못된 메서드)·415(미지원 미디어타입)를 500 으로 변질 — 공유 lib + 함대 전역, MONO-415 가 "동반 점검" 으로 미룬 결함

# Status

ready

# Owner

backend / platform

# Task Tags

- code
- lib
- cross-project

---

# 🔴 발견 경위 (2026-07-16 형제 게이트웨이 스윕 후속, **라이브 실증**)

MONO-420(404→500 함대 파급) 종결 후, 그 티켓이 Out-of-Scope 로 미룬 *"405/415 동반 점검"* 과 MONO-415 In-Scope 의 *"포괄 핸들러가 다른 프레임워크 예외(405/415)를 500 으로 삼키는 곳이 더 있는지 동반 점검"* 을 실측했다.

**실행 중 iam account-service(:18082, fed-hardening-e2e 스택)에 라이브 확인:**

| 호출 | 결과 | 기대 |
|---|---|---|
| `DELETE /actuator/health` | **500** (`Allow` 헤더 없음) | 405 + `Allow: GET` |
| `POST /api/accounts/me` (GET 전용, **unauth**) | **500** | 405 |
| `PATCH /actuator/info` | **500** `{"code":"INTERNAL_ERROR",...}` | 405 |

`INTERNAL_ERROR` 바디는 **공유 `libs/java-web-servlet/CommonGlobalExceptionHandler`** 의 catch-all(L85 `@ExceptionHandler(Exception.class)`)이 삼킨 증거다. 즉 이 결함은 iam 만이 아니라 **공유 lib 을 상속하는 서비스 + 독립 사본 전부**에 있다.

**404 와의 차이 — 노출이 더 넓다:**
- 404(NoResourceFoundException)는 컨트롤러 매칭이 없어 **인증된** 요청에서만 표면화했다(unauth 는 401 게이트).
- **405/415 는 컨트롤러 경로가 매칭되고 메서드/컨텐츠타입만 틀린 경우** → 예외가 던져지고 catch-all 이 잡는다. 위 `POST /api/accounts/me` 가 **unauth 로도 500** 인 것이 증거. 스캐너·오타 메서드·API 버전 불일치가 전부 500 을 유발 → **client 4xx 를 server 5xx 로 오귀속**(audit-heavy/regulated 함대에서 에러율 왜곡·오탐 인시던트).
- 405 는 RFC 7231 §6.5.5 상 **`Allow` 헤더 필수** — 현재 유실.

**정적 shape 가 아니라 라이브로 확정했다**(404 는 EC-504 실증에 기댔지만, 405/415 는 이 repo 에서 실증된 적이 없어 직접 몰았다).

---

# Goal

프레임워크 client-error 예외 — **`HttpRequestMethodNotSupportedException`(405)** 와 **`HttpMediaTypeNotSupportedException`(415)** — 가 catch-all `@ExceptionHandler(Exception.class)` 에 삼켜져 **500** 이 되는 것을, 공유 lib + 모든 독립 핸들러 사본에서 각각 **405 / 415** + 표준 에러 바디로 고친다. 405 응답에는 **`Allow` 헤더**(`e.getSupportedMethods()`)를 포함한다.

## Root Cause (실측, 재검증 대상)

Spring MVC 의 `ExceptionHandlerExceptionResolver`(사용자 `@ExceptionHandler` 처리)가 `DefaultHandlerExceptionResolver`(405/415 를 올바른 상태로 매핑)**보다 먼저** 돈다. unscoped `@ExceptionHandler(Exception.class)` 는 `HttpRequestMethodNotSupportedException`/`HttpMediaTypeNotSupportedException`(둘 다 `Exception` 하위)를 매칭 → 프레임워크 기본 매핑이 실행될 기회를 뺏고 500 을 반환한다. 404 와 동일 메커니즘이되, 프레임워크 예외 집합이 다르다.

## 결함 모집단 (정적 그물, AC-0 재계수 + 라이브 재확인 대상 — **인계된 가설**)

`@ExceptionHandler(Exception.class)` catch-all 이 있고 405/415 전용 핸들러도 `extends ResponseEntityExceptionHandler` 도 **없는** 곳(grep 실측 30 파일 + 공유 lib 1):

- **공유 lib**: `libs/java-web-servlet/.../CommonGlobalExceptionHandler` (→ iam 4서비스 상속 자동 수정).
- **ecommerce**(11): auth·notification·order·payment·product·promotion·review·search·settlement·shipping·user. *(404 는 EC-504 로 고쳤지만 405/415 는 미처리 — 같은 파일에 추가.)*
- **erp**(4)·**scm**(3)·**fan**(4, `AbstractDomainExceptionHandler`)·**finance**(2)·**wms**(5, master 포함).
- **console-bff**(1): `@RestControllerAdvice(basePackages=...)` **스코프**지만 — 404 와 달리 405 는 **매칭된 컨트롤러(스코프 내)에서** 던져지므로 스코프 advice 가 **잡는다** → console-bff 도 405 영향 가능(404 는 안전했으나 405 는 다르다). **AC-0 에서 라이브로 판별.**

**⚠️ ecommerce auth-service** 는 폐기(TASK-BE-132, Gradle 빌드 제외) — 제외.

# Scope

## In Scope (shared path — `libs/`)
- `CommonGlobalExceptionHandler` 에 `HttpRequestMethodNotSupportedException`→**405**(+ `Allow` 헤더, `e.getSupportedMethods()`) · `HttpMediaTypeNotSupportedException`→**415** 전용 `@ExceptionHandler` 추가. 포괄보다 구체적이라 우선 매칭. `METHOD_NOT_ALLOWED`/`UNSUPPORTED_MEDIA_TYPE` 코드가 `platform/error-handling.md` 레지스트리에 있는지 확인, 없으면 **동반 등록**(레지스트리 CI 잡이 잡는다).

## In Scope (per-project adaptation — `projects/<name>/`)
- 위 독립 사본(ecommerce 11 + erp 4 + scm 3 + fan 4 + finance 2 + wms 5 + console-bff 1)에 동일 405/415 핸들러 추가, **각 프로젝트 에러 바디 타입 보존**(EC-504/MONO-420 선례; `ApiErrorBody`/`ApiErrorEnvelope`/`ErrorResponse` 등). 405 는 `Allow` 헤더 포함.
- **수정 방식 판단(아키텍처 게이트 가능성)**: (A) 405/415 전용 핸들러 개별 추가(MONO-415/420 선례, 최소 침습) vs (B) 핸들러들을 `ResponseEntityExceptionHandler` 상속으로 전환(400/405/406/415/… 프레임워크 4xx 전체를 한 번에 올바르게 — 단 기본 `ProblemDetail` 바디로 바뀌어 **각 프로젝트 에러 바디 계약과 충돌** → override 필요). **기본 = (A)**(계약 보존, 범위 한정). (B) 는 fleet 에러-바디 표준화라는 별도 결정이므로 채택 시 ADR 게이트(HARDSTOP-09) — 이 티켓에서 하지 말 것.

## Out of Scope
- 404(NoResourceFound) — 이미 MONO-415(공유·iam) + EC-504(ecommerce) + MONO-420(형제 함대) 로 종결.
- 400(`HttpMessageNotReadable`) 등 이미 각 핸들러가 처리하는 것.
- 406/기타 프레임워크 예외 — 405/415 외 다른 것이 실측 500 이면 **별도 판단**(스코프 폭주 방지; 이 티켓은 405·415 확정분만).
- 게이트웨이(리액티브) — servlet 예외 아님.

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정, 코드가 이긴다)**:
  - (a) 모집단 **재계수** — catch-all + 405/415 핸들러 부재 + `extends ResponseEntityExceptionHandler` 부재를 전 프로젝트 + 공유 lib 에서 재grep, 위 목록과 대조(**인계된 가설**). console-bff(스코프)가 실제 405 영향인지 포함.
  - (b) **라이브 405/415 재현** — 대표 서비스에 잘못된 메서드(GET 전용 경로에 `POST`/`DELETE`) → **현재 500** 재현(공유 lib 소비자 1 + 독립 사본 프로젝트별 1 이상). 415 는 미지원 `Content-Type` POST 로.
- [ ] 잘못된 메서드 → **405** + `Allow` 헤더 + 표준 에러 바디. 미지원 미디어타입 → **415** + 표준 바디.
- [ ] 기존 동작 회귀 0: 404(MONO-420), 400/409/422, 진짜 500 은 그대로.
- [ ] 각 수정 지점에 "잘못된 메서드 → 405(+Allow)" / "미지원 타입 → 415" 테스트 추가(드리프트 재발 시 red).
- [ ] **cross-project 원자 PR** — 공유 lib + 영향 프로젝트 전부 한 PR 에서 GREEN(CLAUDE.md § Cross-Project Changes). **부분 수정 금지**(survivor 방지 — MONO-415→420 의 교훈).
- [ ] 루트 `./gradlew check` GREEN + 에러코드 레지스트리 잡 GREEN.

# Related Specs

> Before reading: 루트 `tasks/INDEX.md` § "When to Use Root vs Project Tasks"(공유 `libs/` + cross-project → monorepo-level).

- `platform/error-handling.md` — 405/415 표준 코드/바디, `Allow` 헤더 규약.
- `tasks/done/TASK-MONO-415-shared-exception-handler-turns-404-into-500.md` — 공유 lib 404 선례 + In-Scope 의 "405/415 동반 점검" 문구(이 티켓이 그 미완 항목).
- `tasks/done/TASK-MONO-420-fleet-404-into-500-sibling-copies.md` — 형제 함대 404 선례(수정 방식·에러바디 보존·AC-0 게이트의 참조 구현).
- 각 수정 서비스의 `specs/services/<svc>/architecture.md` 에러 응답 규약.

# Related Contracts

- 각 프로젝트 HTTP 계약의 405/415 응답 형태(정합 확인). 405 `Allow` 헤더 명세 여부. 바디 형태 변경이 기존 계약/테스트를 깨지 않을 것.

# Edge Cases

- **unauth 도달**: 405/415 는 컨트롤러 경로 매칭 후 메서드/타입 불일치라 **인증 전에도** 던져질 수 있다(라이브: `POST /api/accounts/me` unauth→500). 404 보다 노출 넓음 — 테스트도 unauth 케이스 포함.
- **`Allow` 헤더**: `HttpRequestMethodNotSupportedException.getSupportedMethods()` 로 구성. null 가능성(방어).
- **console-bff 스코프**: `basePackages` 스코프라 404 는 안전했으나, 405 는 스코프 내 컨트롤러에서 던져지면 잡힌다 — AC-0(b) 로 실판별 후 필요 시만 수정.
- **actuator 경로**: `DELETE /actuator/health`→500 도 재현됐다 — actuator 예외까지 공유/사본 핸들러가 잡는지, 아니면 actuator 는 별도 처리가 맞는지 판단(actuator 는 permitAll·프레임워크 관리라 기본 처리에 맡기는 편이 옳을 수 있음 — console-bff 가 정확히 이 이유로 스코프됨).
- **ResponseEntityExceptionHandler 대안(B)**: 채택 시 `ProblemDetail` 기본 바디가 각 프로젝트 에러 코드/형태를 깨므로 override 필수 — 그래서 기본은 (A).

# Failure Scenarios

- **부분 수정 → survivor**: 공유 lib 만 고치고 독립 사본을 빠뜨리면(또는 그 반대) 일부 서비스가 계속 500. MONO-415 가 정확히 이래서 형제를 놓쳤다(→420). AC-0 재계수 + 원자 PR.
- **`Allow` 헤더 누락**: 405 상태만 고치고 헤더를 빠뜨리면 RFC 위반이 남는다 — 술어는 "405 인가"가 아니라 "405 + Allow 인가".
- **REH 전환으로 계약 파손**: (B) 를 무심코 택하면 30+ 서비스의 에러 바디가 `ProblemDetail` 로 바뀌어 프런트/계약/테스트가 대량 깨진다.
- **로컬 초록에 속음**: 이 호스트 로컬 IT flaky, Docker 다운 시 SKIPPED 이 `BUILD SUCCESSFUL` → CI(Linux) 권위. 라이브 재현은 `docker ps` 확인 후.

# Target

- `libs/java-web-servlet/src/main/java/com/example/web/exception/CommonGlobalExceptionHandler.java` (+ 단위 테스트)
- `projects/ecommerce-microservices-platform/apps/{notification,order,payment,product,promotion,review,search,settlement,shipping,user}-service/.../GlobalExceptionHandler.java`
- `projects/erp-platform/apps/{approval,masterdata,notification,read-model}-service/.../GlobalExceptionHandler.java`
- `projects/fan-platform/apps/{artist,community,membership,notification}-service/.../AbstractDomainExceptionHandler.java`
- `projects/scm-platform/apps/{demand-planning,inventory-visibility,procurement}-service/.../GlobalExceptionHandler.java`
- `projects/finance-platform/apps/{account,ledger}-service/.../GlobalExceptionHandler.java`
- `projects/wms-platform/apps/{admin,inbound,inventory,master,outbound}-service/.../GlobalExceptionHandler.java`
- `projects/platform-console/apps/console-bff/.../GlobalExceptionHandler.java` (AC-0 판별 후 필요 시)
- 각 수정 지점 test 디렉터리에 405(+Allow)/415 테스트.

# 분석/구현 권장 모델

- 분석 = Opus 4.8 (1M) — 이 스윕 (라이브 실증 포함)
- 구현 권장 = **Sonnet** — 결함 클래스 단일·기계적(MONO-420 참조 구현 존재), 30+ 지점 반복. 단 AC-0 라이브 재현·`Allow` 헤더·console-bff 스코프 판별·에러코드 레지스트리 등록은 주의.
