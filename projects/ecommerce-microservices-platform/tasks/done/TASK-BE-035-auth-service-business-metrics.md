# TASK-BE-035: auth-service 비즈니스 메트릭 구현 — Micrometer Counter 6종

## Goal
auth-service의 비즈니스 메트릭 6종을 Micrometer Counter로 구현하여 Prometheus에서 수집 가능하게 한다.
`specs/services/auth-service/observability.md`에 정의된 모든 메트릭을 구현한다.

## Scope
- `auth_signup_total` Counter: 회원가입 성공 시 증가
- `auth_login_total` Counter: 로그인 시도 시 증가 (success/failure 태그)
- `auth_login_failure_total` Counter: 로그인 실패 시 증가 (reason 태그: invalid_credentials, rate_limited)
- `auth_logout_total` Counter: 로그아웃 요청 시 증가
- `auth_token_refresh_total` Counter: 토큰 갱신 시도 시 증가 (success/failure 태그)
- `auth_session_eviction_total` Counter: 동시 세션 제한으로 세션 퇴거 시 증가
- 메트릭 등록/증가 로직은 application 또는 infrastructure 레이어에 배치
- 단위 테스트 포함

## Acceptance Criteria
- 6종 Counter가 Micrometer `MeterRegistry`에 등록된다
- 각 비즈니스 이벤트 발생 시 해당 Counter가 정확히 증가한다
- `GET /actuator/prometheus` 응답에 6종 메트릭이 노출된다
- 메트릭 이름과 태그가 `specs/services/auth-service/observability.md`와 일치한다
- 기존 비즈니스 로직 변경 없이 메트릭 수집이 추가된다
- 단위 테스트: 각 Counter가 이벤트 발생 시 증가하는지 검증

## Related Specs
- `specs/services/auth-service/observability.md`
- `specs/platform/observability.md`

## Related Contracts
- 없음 (메트릭 수집, API 계약 변경 없음)

## Edge Cases
- MeterRegistry가 주입되지 않는 환경(테스트 등)에서도 NPE 없이 동작해야 한다
- 로그인 실패 reason이 예상 외 값인 경우 "unknown" 태그로 기록한다

## Failure Scenarios
- Prometheus 미연결 시에도 Counter 증가 로직은 정상 동작 (메트릭은 메모리에 유지)
- MeterRegistry Bean 미존재 시 메트릭 수집이 비활성화되지만 서비스 기동에 영향 없음
