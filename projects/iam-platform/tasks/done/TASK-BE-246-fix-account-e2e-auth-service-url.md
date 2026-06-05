# TASK-BE-246: fix — account-service e2e profile에 AUTH_SERVICE_URL 추가

## Goal

`docker-compose.e2e.yml` 환경에서 회원가입(`POST /api/accounts/signup`) 흐름이 항상 503 `AUTH_SERVICE_UNAVAILABLE`로 실패한다.

원인:
```
auth-service credential write failed after retries:
I/O error on POST request for "http://localhost:8081/internal/auth/credentials"
```

account-service는 auth-service에 credential 쓰기를 위탁하면서 `http://localhost:8081`을 호출하는데, Docker 컨테이너 내부에서 `localhost`는 자기 자신(account-service 컨테이너)이므로 도달하지 못한다.

`docker-compose.e2e.yml`의 account-service 블록을 보면 `AUTH_SERVICE_URL`이 명시되지 않아 기본값(application.yml의 `http://localhost:8081`)이 사용된다. admin-service / gateway-service는 동일 변수를 명시하고 있으므로 정합성을 맞춘다.

## Scope

**In:**
- `docker-compose.e2e.yml`의 `account-service.environment`에 다음 env 추가:
  ```yaml
  AUTH_SERVICE_URL: http://auth-service:8081
  ```
- application.yml에 해당 env가 바인딩되는 키 확인 (이미 `${AUTH_SERVICE_URL:http://localhost:8081}` 패턴이라면 추가 작업 없음). 누락 시 `auth.account-service.base-url` 또는 동등 키에 env placeholder 추가
- 기타 account-service가 호출하는 다른 다운스트림(있다면) 동일하게 점검 — admin-service URL, security-service URL 등이 회원가입 흐름에 필요한지 확인

**Out:**
- 다른 서비스 docker-compose 블록 변경 없음 (이미 정합)
- account-service / auth-service 코드 변경 없음

## Acceptance Criteria

- [ ] `docker-compose.e2e.yml`의 account-service 블록에 `AUTH_SERVICE_URL: http://auth-service:8081` 추가
- [ ] e2e 컴포즈 재기동 후 `POST /api/accounts/signup` 호출이 201 또는 적절한 비-503 응답 반환 (실제 가입 성공 또는 contract 명시 다른 에러)
- [ ] account_db.accounts 테이블에 새 row가 생성됨 확인
- [ ] auth_db.credentials 테이블에 대응 credential row 생성 확인
- [ ] 기존 admin-service / gateway-service 등 정상 동작 회귀 없음

## Related Specs

- `specs/contracts/http/account-api.md §POST /api/accounts/signup`
- `specs/contracts/http/internal/auth-internal.md §POST /internal/auth/credentials`
- `specs/services/account-service/architecture.md`

## Related Contracts

- `specs/contracts/http/internal/auth-internal.md` (account → auth credential 쓰기)

## Edge Cases

- application.yml에 env placeholder가 누락되어 있다면 docker-compose 변수만으로는 해결 안 됨 — 그 경우 application.yml도 함께 수정
- gateway-service.environment에는 `AUTH_SERVICE_URL: http://auth-service:8081`이 이미 있으므로 동일 패턴 사용

## Failure Scenarios

- env 추가했으나 application.yml의 키가 다른 이름(예: `account.auth-service.url`)인 경우: grep으로 정확한 키 식별 후 정렬
- account-service가 추가로 admin/security-service를 호출하는 흐름이 있다면 같은 종류의 결함 잠복 가능 — `localhost:` grep 으로 확인

## Implementation Notes

- 빠른 검증 명령:
  ```bash
  curl -s -X POST http://localhost:18080/api/accounts/signup \
    -H "Content-Type: application/json" \
    -d '{"email":"smoke@example.com","password":"SmokeTest123!","displayName":"Smoke"}'
  ```
- 이 PR은 BE-242/243/245 fix 흐름의 후속이며, e2e 환경 정합화 시리즈의 일부임
