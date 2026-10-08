# Task ID

TASK-MONO-778

# Title

호출자 토큰 전달(caller-token propagation) 도우미를 `libs/java-security-servlet` 로 올린다 — erp approval · notification 의 두 사본 정리

# Status

ready

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

- [ ] **AC-0** — 착수 시 재측정: 위 file:line · 전체 저장소에서 같은 모양(`getTokenValue()` + `"Bearer "`)의 사본 수(erp 밖 포함) · 라이브러리 소비자 목록(`libs/java-security-servlet` 의존 모듈).
- [ ] **AC-1** — 라이브러리 단위 시험: 인증 없음 → 없음(null/empty) · `JwtAuthenticationToken` 하위형(erp `ActorAuthenticationToken`) → 그 토큰.
- [ ] **AC-2** — 두 어댑터의 기존 시험이 바뀌지 않고 초록(동작 불변) + 두 어댑터에서 사본이 사라졌음을 grep 으로 확인.
- [ ] **AC-3** — 전달된 Bearer 가 원 토큰과 **바이트 동일**(클레임 재구성 없음 ⇒ `amr` 등 모든 클레임 보존) — 시험으로 고정.
- [ ] **AC-4** — `./gradlew` 로 라이브러리 + 두 서비스 `test` rc=0(JUnit XML 실측) · erp 통합 잡은 CI.
- [ ] **AC-5** — HARDSTOP-03 확인: 라이브러리 diff 에 서비스명 · erp 용어 0(grep).

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
