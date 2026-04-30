# Task ID

TASK-BE-058

# Title

Testcontainers Docker 감지 복구 — 통합 테스트 skip 해소

# Status

ready

# Owner

backend

# Task Tags

- test

# depends_on

(없음)

---

# Goal

로컬·CI 환경 양쪽에서 `@EnabledIf("isDockerAvailable")` 관례를 쓰는 통합 테스트 4건(`AuthIntegrationTest`, `DeviceSessionIntegrationTest`, `OutboxRelayIntegrationTest`, `OAuthLoginIntegrationTest`)이 현재 개발 머신에서 모두 skip되고 있다. 원인은 Testcontainers가 Docker API에 닿지 못함. 이 태스크는 해당 환경 블로커를 진단·해결하여 통합 테스트가 실제로 실행되는 상태로 복구한다.

관찰된 증상 (TASK-BE-057 리뷰 시점):

- `~/.testcontainers.properties`에 `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine`이 기록되어 있음
- 해당 pipe는 실제로 존재하나, `/_ping` 호출 시 `Status 400`과 함께 `ServerVersion:""`, `Labels:["com.docker.desktop.address=npipe://./pipe/docker_cli"]`만 담은 degrade된 응답을 반환 → Testcontainers가 "유효한 Docker 환경 아님"으로 판단
- `docker info`, `docker volume ls` 등 CLI 명령은 정상 동작 (CLI는 `docker_cli` pipe를 사용)
- Docker Desktop은 `desktop-linux` context가 active 상태

---

# Scope

## In Scope

1. **환경 진단**
   - Docker Desktop의 설정(Use Docker Compose V2, WSL integration, Expose daemon on tcp 등)을 점검하고 Linux 엔진 pipe가 정상 ping 응답을 돌려주는 상태로 복구
   - 대안: `~/.testcontainers.properties`의 `docker.host`를 정상 응답하는 pipe로 조정 (후보: `docker_engine`, `docker_cli`, TCP 소켓)
2. **실행 검증**
   - `./gradlew :apps:auth-service:test`에서 위 4개 통합 테스트 클래스 중 최소 1건이 full lifecycle(container start → HTTP call → 검증 → teardown) 완료 확인
   - 다른 3건도 동일하게 skip이 해제되는지 확인
3. **문서화**
   - `docs/guides/` 또는 `README.md`의 Getting Started 섹션에 "로컬 통합 테스트 실행 요건" 항목 추가
     - 필요한 Docker Desktop 설정
     - `~/.testcontainers.properties` 권장 값
     - 동작 확인 명령 (`./gradlew :apps:auth-service:test --tests "*IntegrationTest"`)
4. **CI 확인** (선택)
   - GitHub Actions 워크플로우에서 Testcontainers가 정상 동작하는지 재검증 (리눅스 runner는 대개 문제 없음)

## Out of Scope

- Testcontainers 미지원 환경(일부 macOS M1 외부, 원격 Docker 등)을 지원하는 공용 설정 표준 제정
- 통합 테스트 자체 로직 변경 (기존 4개 + 신규 `OAuthLoginIntegrationTest` 코드는 그대로 유지)
- Docker Desktop 버전 강제 업그레이드 정책

---

# Acceptance Criteria

- [ ] `./gradlew :apps:auth-service:test` 실행 시 `AuthIntegrationTest` 중 최소 1건이 `Passed` 상태로 기록됨 (skipped 아님)
- [ ] `OAuthLoginIntegrationTest` 중 최소 1건(예: `googleHappyPath`)이 `Passed`로 기록됨
- [ ] `build/test-results/test/*.xml`의 전체 `skipped` 카운트가 0 또는 의도된 값(Testcontainers 비사용 케이스만)
- [ ] 해결 방법이 `docs/guides/` 또는 `README.md`에 문서화됨 (후임 개발자가 같은 증상 만났을 때 참조 가능)
- [ ] CI 파이프라인(GitHub Actions)에서 통합 테스트 실행 결과가 기존 대비 악화되지 않음

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/services/auth-service/architecture.md`

---

# Related Contracts

(테스트 환경 조정이므로 contract 변경 없음)

---

# Target Service

- `apps/auth-service` (Testcontainers 호스트 서비스)

기타 서비스(`account-service`, `security-service`, `admin-service` 등)에도 동일 패턴 통합 테스트가 있으면 함께 영향 받음 → 동일 조정 적용.

---

# Architecture

환경 설정 태스크로 서비스 아키텍처 변경 없음.

---

# Edge Cases

- Docker Desktop이 WSL 2 백엔드 미활성 → WSL integration 활성화 후 재시도
- `docker_cli` pipe가 proxy 역할이라 `/_ping`이 redirect만 돌려줄 가능성 → `docker_engine` 또는 TCP 소켓이 더 적합할 수 있음
- Windows pro에서 Hyper-V/WSL 충돌 → Docker Desktop settings에서 백엔드 고정
- `~/.testcontainers.properties`를 수정해도 Testcontainers가 자동으로 재-감지 후 다시 덮어쓸 수 있음 → `docker.client.strategy`를 명시적으로 고정

---

# Failure Scenarios

- Docker Desktop 재설치해도 pipe 응답이 여전히 degrade → DOCKER_HOST 환경변수로 TCP(`tcp://localhost:2375`) 소켓을 명시하여 우회 (Docker Desktop → Settings → General → "Expose daemon on tcp://localhost:2375 without TLS" 활성화)
- TCP 소켓도 거부되면 Rancher Desktop 또는 Colima 등 대체 Docker 구현체로 이주 검토
- CI에서 호환성 문제 발견 시 이 태스크 롤백하고 `@EnabledIf` 관례를 유지

---

# Test Requirements

본 태스크 자체가 환경 복구 태스크이므로 새 테스트 추가 없음. 기존 통합 테스트 4종이 실제로 실행되는 것으로 검증.

- `AuthIntegrationTest`
- `DeviceSessionIntegrationTest`
- `OutboxRelayIntegrationTest`
- `OAuthLoginIntegrationTest`

---

# Definition of Done

- [ ] Testcontainers가 Docker 환경을 정상 인식하여 container lifecycle 성공
- [ ] 위 4개 테스트 클래스 중 최소 2개에서 1건 이상의 테스트 케이스가 `Passed` (skipped 아님)
- [ ] 해결 절차가 `docs/guides/` 또는 `README.md`에 문서화됨
- [ ] Ready for review
