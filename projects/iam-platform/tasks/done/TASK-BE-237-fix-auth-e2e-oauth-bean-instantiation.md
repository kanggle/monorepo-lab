# TASK-BE-237: fix — auth-service e2e profile에서 OAuth 클라이언트 빈 생성 실패 조사 및 수정

## Goal

`docker-compose.e2e.yml` 환경에서 auth-service 시작 시 `OAuthLoginUseCase` → `OAuthClientFactory` → `GoogleOAuthClient` 의존성 체인의 빈 생성이 실패한다.

```
Failed to instantiate [com.example.auth.infrastructure.oauth.GoogleOAuthClient]: No default constructor found
```

`GoogleOAuthClient`는 명시적 생성자(`GoogleOAuthClient(OAuthProperties, ObjectMapper)`)를 갖고 있고 `@Component` 어노테이션 + Spring 6.x의 단일 public 생성자 자동 선택 규칙으로 정상 동작해야 하지만, e2e 컨테이너에서는 Spring이 default constructor를 찾으려다 실패한다. `KakaoOAuthClient`, `MicrosoftOAuthClient`도 동일 패턴이므로 동일 증상이 잠복해 있을 가능성이 있다.

근본 원인을 조사하고, OAuth 빈이 e2e profile에서 정상 생성되도록 수정한다.

## Scope

**In:**
- 빈 생성 실패 근본 원인 분석:
  - `OAuthProperties` 빈이 실제로 등록되는지 확인 (`@EnableConfigurationProperties` 적용 여부)
  - `GoogleOAuthClient`의 두 생성자(public 2-arg, package-private 3-arg)가 Spring 6.x에서 모호성 유발 여부 — 필요 시 `@Autowired` 명시 또는 package-private 생성자 제거/`@Autowired` 부착
  - bootJar 패키징에서 `OAuthProperties` 메타데이터 누락 가능성
  - e2e 컨테이너 환경변수 부재로 `oauth.*` 바인딩이 실패하는지 검증
- 원인 식별 후 최소 변경 적용:
  - 생성자 선택 명시(`@Autowired`) 또는
  - `OAuthProperties`에 명시적 생성자 바인딩 추가 또는
  - `application-e2e.yml`에 noop OAuth 환경변수 추가
- `KakaoOAuthClient`, `MicrosoftOAuthClient`도 동일 패턴 점검
- 단위 테스트 또는 컨텍스트 슬라이스 테스트로 회귀 방지

**Out:**
- OAuth 비즈니스 로직 변경 없음
- 실제 OAuth provider 호출 환경 구성 없음 (e2e에서는 OAuth 흐름을 실행하지 않음)

## Acceptance Criteria

- [ ] auth-service가 e2e profile로 정상 기동
- [ ] `GoogleOAuthClient`, `KakaoOAuthClient`, `MicrosoftOAuthClient` 세 빈 모두 ApplicationContext에 정상 등록
- [ ] 컨텍스트 슬라이스 테스트로 빈 생성 검증 (e.g. `@SpringBootTest(properties="spring.profiles.active=e2e")`로 빈 lookup)
- [ ] 기존 OAuth 단위 테스트 모두 통과 (`./gradlew :apps:auth-service:test --tests "*OAuth*"`)

## Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/oauth-social-login.md` (있을 경우)

## Related Contracts

- `specs/contracts/http/auth-api.md` — OAuth 콜백 엔드포인트 (시그니처 변경 없음)

## Edge Cases

- 두 생성자 모호성으로 인한 실패라면 `@Autowired`를 추가해도 단위 테스트는 영향 없음 (테스트는 직접 인스턴스화)
- `OAuthProperties` 자체가 미생성이라면 `@EnableConfigurationProperties` 누락이 원인 → 클래스 어노테이션 점검 필요
- 컨테이너 환경에서만 발생하고 로컬 dev에서는 발생하지 않는 경우: classpath 차이 또는 Spring Boot devtools 동작 차이 의심

## Failure Scenarios

- 수정 후에도 동일 에러 재발: Spring debug 로깅(`-Ddebug=true` 또는 `logging.level.org.springframework=DEBUG`) 활성화하여 빈 생성 트레이스 확인
- 변경이 prod 동작에 영향: profile-specific 설정으로 격리하거나, 변경이 모든 profile에서 안전한지 회귀 테스트
