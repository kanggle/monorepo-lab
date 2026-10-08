# Task ID

TASK-MONO-778

# Title

호출자 토큰 전달(caller-token propagation) 도우미를 `libs/java-security-servlet` 로 올린다 — erp approval · notification 의 두 사본 정리

# Status

review

# Owner

monorepo

# Task Tags

- lib
- erp
- refactor

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 정해진 승격 트리거가 발화한 기계적 이동 + 두 소비자 배선.

---

# Dependency Markers

- 출처: `TASK-MONO-776` 후속(2026-10-08 UTC), 소유자 «추천대로 진행».
- 트리거(코드가 스스로 적은 것): approval-service `MasterDataRestAdapter.java:253-260` — *«Promote when a **second** service needs the same six lines — that count, not "it looks generic", is the trigger. 🔵 TASK-MONO-776: the trigger has fired»*. 두 번째 사본: notification-service `MasterDataCallerEmployeeAdapter.java:38-40`.
- ⚠️ 접점(다른 세션 알림, 2026-10-08 UTC): `TASK-MONO-771`(iam 2FA)이 운영자 토큰 교환 · assume-tenant 에서 `amr` 을 요구하게 만든다. 이 도우미는 **토큰 전체를 그대로** 전달하므로 `amr` 은 살아남아야 한다 — 그것을 시험으로 고정한다(AC-3).

# Goal

같은 «현재 인증에서 검증된 JWT 를 꺼내 `Authorization: Bearer` 로 다운스트림에 싣는» 여섯 줄이 erp 두 서비스에 복사돼 있다(2026-10-08 UTC 실측: approval `MasterDataRestAdapter.currentCallerToken()` `:261-267` + `.header(AUTHORIZATION, "Bearer " + caller.getTokenValue())` `:136, :206` · notification `MasterDataCallerEmployeeAdapter` `:97`). 사본 둘은 한쪽만 고쳐지는 결함 자리다. 코드가 정한 승격 조건이 충족됐으므로 공유 라이브러리로 올린다.

# Scope

## In Scope

- `libs/java-security-servlet` 에 도우미(예: 현재 호출자 JWT 를 돌려주는 정적 메서드 + `RestClient` 요청에 Bearer 를 다는 작은 유틸) — **프로젝트 비종속**(서비스명 · 도메인 용어 없음, HARDSTOP-03). 기존 `ActorContextResolver` 옆, 같은 패키지 관례.
- erp approval · notification 두 어댑터가 그것을 쓰게 바꾼다(동작 불변). 어댑터 고유의 것(신원 대조 `subject_mismatch`/`identity_mismatch`, 실패 원인 계측)은 서비스에 남긴다.
- `platform/shared-library-policy.md` 의 승격 절차 · 라이브러리 README/문서 갱신.
- Cross-Project 규칙: 라이브러리 변경과 두 소비자 적응을 **한 PR**.

## Out of Scope

- 다른 프로젝트의 서비스 간 호출(현재 전달 사용처 0 — 착수 때 다시 센다).
- `client_credentials` 워크로드 토큰 방식(ERP-BE-041 이 전달을 고른 이유 그대로).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정(2026-10-08 UTC, worktree `monorepo-lab-mono778`):
  - file:line 재확인 — approval `MasterDataRestAdapter.java` `currentCallerToken()` `:262-268`(기안 당시 `:261-267` — 한 줄 드리프트, 내용 동일) · Bearer 헤더 `:136`, `:206`. notification `MasterDataCallerEmployeeAdapter.java` Bearer 헤더 `:97`(기안 수치와 일치), `currentCallerToken()` `:134-137`.
  - 전체 저장소에서 `getTokenValue()` + `"Bearer "` 동시 패턴(`.header(HttpHeaders.AUTHORIZATION, "Bearer " + …getTokenValue())` 모양) 사본 수 = **3건, 전부 erp 안**(approval 2 + notification 1). erp 밖 사본 **0건** — iam-platform `auth-service` 의 `getTokenValue()` 호출 다수는 토큰 저장/재발급용이고 `"Bearer "` 와 동시에 쓰이지 않아 이 모양이 아니다(grep 확인).
  - 라이브러리 소비자 목록 — `implementation project(':libs:java-security-servlet')` 를 선언한 모듈 **19개**: wms-platform(outbound·master·inventory·inbound·admin-service, 5) · scm-platform(procurement·logistics·inventory-visibility·demand-planning-service, 4) · finance-platform(ledger·account-service, 2) · fan-platform(notification·membership·community·artist-service, 4) · erp-platform(read-model·notification·masterdata·approval-service, 4).
- [x] **AC-1** — `libs/java-security-servlet` 에 `com.example.security.servlet.actor.CallerTokenPropagation` 신설(`ActorContextResolver` 옆, 같은 패키지). `CallerTokenPropagationTest` 5건: 인증 없음 → `null` · 비-JWT `Authentication`(`UsernamePasswordAuthenticationToken`) → `null` · `JwtAuthenticationToken` 하위형(라이브러리 자신의 `ActorAuthenticationToken` — erp 의 것과 동형) → 그 토큰 · 평범한 `JwtAuthenticationToken` → 그 토큰 · `withBearerToken` 바이트 동일 전달(AC-3). rc=0, `tests="5" failures="0" errors="0"`.
- [x] **AC-2** — 두 어댑터 모두 `CallerTokenPropagation.currentCallerToken()` / `.withBearerToken(...)` 로 교체, 로컬 `currentCallerToken()` 메서드·`SecurityContextHolder`/`getTokenValue()` 직접 참조 둘 다 제거 확인(grep 0건, 아래 AC-4 로그). 기존 시험 무편집 — approval-service `test` 23 스위트 전부 `failures=0 errors=0`(그중 `SubjectResolveIdentityIntegrationTest` 는 Testcontainers 라 `integrationTest` 전용, 이 머신에선 CI 위임), notification-service `test` 23 스위트 전부 `failures=0 errors=0`.
- [x] **AC-3** — `CallerTokenPropagationTest.withBearerTokenForwardsRawTokenValueByteIdentical` — `getTokenValue()` 원문을 그대로 `"Bearer " + rawTokenValue` 로 검증(클레임 재구성 없음), 동시에 `amr` 클레임이 토큰 객체에 살아있음을 확인. 추가로 approval-service 기존 IT `SubjectResolveIdentityIntegrationTest.propagatesTheCallersBearerToken()`(동작 불변으로 그대로 통과)이 종단에서 같은 사실을 이미 고정하고 있다(`masterSeenAuthorization == "Bearer " + callerToken`).
- [x] **AC-4** — `./gradlew :libs:java-security-servlet:test` rc=0(XML 5/5) · `:projects:erp-platform:apps:approval-service:test` rc=0(XML 23, 전부 failures=0) · `:projects:erp-platform:apps:notification-service:test` rc=0(XML 23, 전부 failures=0) · `:libs:java-security-servlet:check`(=`assertClasspathNeutrality`) rc=0. erp `integrationTest`(Testcontainers) 는 이 머신에서 미실행 — CI 위임(기안 그대로).
- [x] **AC-5** — `git diff -- libs/` + 신규 파일 grep(`erp|approval|notification|masterdata|employee|departments`, 대소문자 무시) = **0건**(최초 시험 `@DisplayName` 에 "erp" 1건 있었고 즉시 고쳤다 — 그 수정 전/후 재측정 기록).

🔵 **구현 기록 (2026-10-08 UTC)** — Scope 의 «라이브러리 README/문서 갱신»: `libs/java-security-servlet` 에는 README 가 없다(`libs/java-web`·`libs/java-web-servlet` 만 있음) — 문서 갱신은 새 클래스의 클래스/메서드 javadoc 으로 충족, approval-service 클래스 javadoc 의 승격 트리거 서술도 라이브러리를 가리키도록 고쳤다(새 README 신설은 스코프 밖).

# Related Specs

- `platform/shared-library-policy.md`
- `projects/erp-platform/specs/services/approval-service/architecture.md` · `notification-service/architecture.md`

# Related Contracts

- 없음(내부 리팩터 — HTTP 계약 불변).

# Edge Cases

- 비동기 스레드(가상 스레드 · `@Async`)에서는 `SecurityContextHolder` 가 비어 있을 수 있다 — 도우미는 «없음» 을 돌려주고, 어댑터가 지금처럼 거절 원인으로 계측한다(동작 불변).

# Failure Scenarios

1. 라이브러리에 erp 용어가 섞인다 — HARDSTOP-03.
2. 승격하면서 신원 대조까지 라이브러리로 옮긴다 — 서비스마다 다른 거절 원인 이름 · 계측이 한 이름으로 뭉개진다.
3. 소비자 하나만 옮긴다 — 사본이 하나 남아 이 티켓의 목적이 사라진다.
