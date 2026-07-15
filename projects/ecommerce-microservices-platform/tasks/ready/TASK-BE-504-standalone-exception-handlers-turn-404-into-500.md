# Task ID

TASK-BE-504

# Title

ecommerce 서비스별 독립 GlobalExceptionHandler 가 404(NoResourceFound)를 500 으로 변질 — 갈라진 복사본에 동일 버그

# Status

ready

# Owner

backend

# Task Tags

- code

---

# 🔴 발견 경위 (2026-07-15, TASK-MONO-415 착수 재열거에서 파생)

monorepo-level `TASK-MONO-415`(공유 `libs/java-web-servlet` `CommonGlobalExceptionHandler` 가 404→500 변질) 착수 시 소비자 모집단을 재grep 한 결과, **ecommerce 서비스들은 공유 base 를 상속하지 않고 각자 독립 `GlobalExceptionHandler` 복사본을 갖고 있으며, 그 복사본에 동일한 `@ExceptionHandler(Exception.class)`→`INTERNAL_ERROR` 버그가 전파돼 있음**을 확인했다. 따라서 MONO-415 의 라이브러리 fix 로는 **ecommerce 가 안 고쳐진다** — 별개 결함으로 분리한다.

**최초 관측(가설, 재측정 대상)**: order, product, promotion, search, settlement, shipping, user 및 그 외 서비스가 각자 standalone handler 보유. **착수 시 실제로 몇 개 서비스가 이 버그를 갖는지 전수 재열거할 것**(모집단 물려받기 금지).

---

# Goal

ecommerce 각 서비스의 독립 `GlobalExceptionHandler` 가 **매핑 없는 경로**(Spring `NoResourceFoundException` / `NoHandlerFoundException`)에 대해 **404** 를 반환하게 한다. 현재는 catch-all `@ExceptionHandler(Exception.class)` 가 이를 삼켜 **500 INTERNAL_ERROR** 로 변질시킨다(MONO-415 가 iam 에서 확인·수정한 것과 동일한 버그의 ecommerce 복사본).

## 두 가지 접근 (착수 시 아키텍처 판단)

- **(A) 각 handler 에 404 핸들러 추가** — MONO-415 가 공유 base 에 넣은 것과 동일한 `@ExceptionHandler(NoResourceFoundException.class)`(+`NoHandlerFoundException`)→404 `NOT_FOUND` 를 각 ecommerce handler 에 복제. 최소 변경, 각 서비스 독립성 유지.
- **(B) 공유 base 채택으로 dedup** — ecommerce handler 들을 `CommonGlobalExceptionHandler`(MONO-415 fix 포함) 상속으로 통일. 근본적이지만 각 서비스의 도메인 예외 매핑·응답 형태 차이를 흡수해야 하므로 범위가 큼. **중복이 병인지부터 재라**: handler 들이 (404 버그 말고) 실제로 갈라졌으면 통합은 별 작업이고, 안 갈라졌으면 base 채택이 깔끔하다.

착수 시 (A) 최소 fix 를 코어로 하고 (B) 통합은 별도 판단 권장.

# Scope

## In Scope
- 버그를 가진 ecommerce 서비스의 독립 `GlobalExceptionHandler` 각각(재열거로 확정)에 "없는 경로→404" 처리 추가.
- 각 서비스에 "없는 경로→404" 단위/슬라이스 테스트 추가(현재 이 동작을 단언하는 테스트가 없어 버그가 방치됨).

## Out of Scope
- `libs/java-web-servlet` (MONO-415 소관 — iam 만 상속).
- 각 서비스의 도메인 예외 매핑(정상).
- 에러코드 명칭: MONO-415 가 쓴 `NOT_FOUND`(platform/error-handling.md 등록됨)와 통일 권장.

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: 대표 ecommerce 서비스에서 없는 경로 호출 → **현재 500 INTERNAL_ERROR** 재현. 그리고 standalone `GlobalExceptionHandler` 를 가진 서비스 목록 + 그중 catch-all 500 버그를 가진 서비스를 **전수 재열거**(최초 스냅샷의 "7" 을 진실로 취급 말 것).
- [ ] 버그를 가진 각 서비스가 없는 경로에 **404 NOT_FOUND** 반환.
- [ ] 기존 동작 회귀 0: `VALIDATION_ERROR`/도메인 404(`*_NOT_FOUND`)/진짜 500 그대로. 매핑된 엔드포인트의 `isNotFound`/`isInternalServerError` 단언 무영향.
- [ ] 각 수정 서비스에 "없는 경로→404" 테스트 추가.
- [ ] 관련 서비스 `:test` + 루트 `./gradlew check` 매트릭스(CI 권위) GREEN.

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0 — ecommerce `PROJECT.md` → 도메인/trait 규칙.

- `platform/error-handling.md` (에러코드 카탈로그; `NOT_FOUND` 이미 등록)
- 각 서비스 `specs/services/<svc>/architecture.md` 의 에러 응답 규약

# Related Contracts

- ecommerce 각 서비스 HTTP 계약의 404 응답 형태(정합 확인)

# Target Service

- ecommerce 서비스 다수(착수 시 재열거로 확정 — 최초 관측: order/product/promotion/search/settlement/shipping/user 외)

# Edge Cases

- `NoResourceFoundException` 은 Spring 6.1+ (Boot 3.2+). 서비스별 Boot 버전 확인 — 구성에 따라 `NoHandlerFoundException` 이 던져질 수 있으므로 둘 다 처리(MONO-415 가 취한 방식).
- (B) 통합 선택 시 각 handler 의 도메인 예외 매핑 차이를 base 로 승격하며 유실하지 않도록 — 승격은 무손실이 아니다(대조 필수).
- 서비스마다 handler 이름/패키지가 다를 수 있음(`GlobalExceptionHandler` 외 명칭 가능) — grep 을 클래스명에 한정하지 말 것.

# Failure Scenarios

- 스냅샷 "7" 을 그대로 믿고 일부 서비스를 놓침(모집단 재측정 필수 — MONO-415 가 정확히 이 함정을 정정한 사례).
- 매핑된 도메인 404 를 실수로 generic `NOT_FOUND` 로 덮어 도메인 에러코드 유실.

# Definition of Done

- [ ] AC-0 재측정 + standalone handler 전수 재열거
- [ ] 버그 서비스 각각 404 처리 + 테스트
- [ ] 회귀 0, CI 매트릭스 GREEN
- [ ] Ready for review
