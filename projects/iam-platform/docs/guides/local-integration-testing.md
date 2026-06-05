# 로컬 통합 테스트 (Testcontainers) 실행 가이드

> 이 문서는 AI 에이전트가 아닌 **사람 개발자**를 위한 운영 가이드입니다.
> ([rules/common.md](../../rules/common.md)에 따라 `docs/guides/`는 AI의 source of truth가 아닙니다.)

---

## 대상 테스트

다음 테스트 클래스들은 Testcontainers(MySQL + Redis + Kafka)와 WireMock을 사용하며, Docker 환경이 유효해야 실행됩니다. 그렇지 않으면 `@EnabledIf("isDockerAvailable")` 조건에 의해 조용히 skip됩니다.

- [AuthIntegrationTest](../../apps/auth-service/src/test/java/com/example/auth/integration/AuthIntegrationTest.java)
- [DeviceSessionIntegrationTest](../../apps/auth-service/src/test/java/com/example/auth/integration/DeviceSessionIntegrationTest.java)
- [OutboxRelayIntegrationTest](../../apps/auth-service/src/test/java/com/example/auth/integration/OutboxRelayIntegrationTest.java)
- [OAuthLoginIntegrationTest](../../apps/auth-service/src/test/java/com/example/auth/integration/OAuthLoginIntegrationTest.java)

빠른 확인 명령:

```bash
./gradlew :apps:auth-service:test --tests "*IntegrationTest" -i
```

gradle 출력에서 각 테스트가 `SKIPPED`인지 `PASSED`인지 확인하세요. 모든 통합 테스트가 SKIPPED로 나오면 Docker 환경에 문제가 있는 상태입니다.

---

## 일반 요구사항

1. **Docker Desktop (Windows/Mac)** 또는 **Docker Engine (Linux)** 이 실행 중
2. 현재 사용자가 Docker socket/pipe에 접근 가능
3. Testcontainers가 Docker daemon과 정상 통신 가능

아래 진단 명령으로 먼저 점검:

```bash
docker info
docker version
```

둘 다 정상 응답하면 CLI 레벨은 OK. 그래도 Testcontainers가 실패하면 아래 **알려진 증상** 섹션을 확인하세요.

---

## 알려진 증상 — Docker Desktop on Windows

### 증상

Testcontainers가 다음과 같은 에러로 실패:

```
org.testcontainers.dockerclient.DockerClientProviderStrategy
  -- Could not find a valid Docker environment. Please check configuration.
     Attempted configurations were:
       EnvironmentAndSystemPropertyClientProviderStrategy: ... (Status 400: ...)
       NpipeSocketClientProviderStrategy: ... (Status 400: ...)
```

응답 본문에 다음과 같은 빈 필드들이 포함되어 있습니다:

```json
{
  "ID": "",
  "ContainersRunning": 0,
  "ServerVersion": "",
  "Labels": ["com.docker.desktop.address=npipe://\\\\.\\pipe\\docker_cli"]
}
```

### 원인 (Docker Desktop 4.69+ 호환성 이슈)

Docker Desktop 4.69가 `/info` 호출에 대해 다음 형태의 **redirect-style 응답**을 모든 엔드포인트(named pipe, TCP 2375 모두)에서 반환:

```json
{
  "ID": "",
  "ServerVersion": "",
  "Labels": ["com.docker.desktop.address=npipe://\\\\.\\pipe\\docker_cli"]
}
```

HTTP 상태 코드는 400. Docker CLI는 이 Label을 읽어 `docker_cli` pipe로 자동 리디렉트하지만, **`docker-java`(Testcontainers 1.20.x / 1.21.x의 내부 라이브러리)는 이 프로토콜을 모르고 400을 치명적 오류로 간주**해 모든 provider strategy를 실패로 처리.

> 검증: 같은 JVM에서 `java.net.http.HttpClient`로 직접 `http://localhost:2375/info`를 호출하면 **정상 200 응답**이 옴. 문제는 `docker-java` 라이브러리가 이 응답을 해석하는 방식에만 국한됨.

---

## 해결 방법

> 이 프로젝트 개발 중(2026-04) 검증 결과, **Docker Desktop 4.69 환경에서는 아래 5단계 모두 유효하지 않음**을 확인했습니다. TCP 2375 노출, 전체 재시작, `.testcontainers.properties` 조정, Docker context 변경, Testcontainers 버전 업(1.20.4→1.21.4) 전부 동일한 400 응답을 받습니다. **Docker Desktop 측 incompatibility**이므로 아래 5단계 중 1·2·3은 **다른 환경에서만** 효과가 있습니다. Docker Desktop 4.69+ 사용자는 **6단계(Docker 대체)** 로 직행하세요.

### 1) Docker Desktop 재시작 (일반 환경용)

Docker Desktop 트레이 아이콘 → **Quit Docker Desktop** → 다시 시작.

`/info` degraded 응답이 아닌 일반 skip 원인(daemon 미가동)에는 효과적입니다.

### 2) "Expose daemon on tcp://localhost:2375 without TLS" 활성화

> 신뢰된 개발 PC에서만 사용. TLS 없이 TCP 2375를 로컬에 여므로 멀티유저/공용 환경에서는 권장하지 않음.

1. Docker Desktop → **Settings** (⚙) → **General**
2. **"Expose daemon on tcp://localhost:2375 without TLS"** 체크
3. **Apply & restart**

활성화 후 다음 설정을 반영:

```bash
# PowerShell (영구)
[Environment]::SetEnvironmentVariable('DOCKER_HOST', 'tcp://localhost:2375', 'User')

# bash (세션 단위)
export DOCKER_HOST=tcp://localhost:2375
```

또는 `~/.testcontainers.properties`를 편집:

```properties
docker.host=tcp://localhost:2375
```

검증:

```bash
docker -H tcp://localhost:2375 info --format '{{.ServerVersion}}'
# 29.4.0 (또는 유사 버전 문자열)
```

### 3) "Allow the default Docker socket to be used" 활성화 (Docker Desktop 4.18+)

1. Docker Desktop → **Settings** → **Advanced**
2. **"Allow the default Docker socket to be used (requires password)"** 체크
3. **Apply & restart**

이 옵션은 기본 Docker 소켓 사용을 허용하여 Testcontainers가 자동 탐지할 수 있게 합니다.

### 4) `~/.testcontainers.properties` 초기화

Testcontainers가 과거 잘못된 pipe를 기억해 재시도할 수 있습니다. 파일을 삭제해 자동 재탐색을 유도:

```bash
rm ~/.testcontainers.properties
```

### 5) Rancher Desktop / Colima 대체

Docker Desktop 고집 필요가 없다면:

- [Rancher Desktop](https://rancherdesktop.io/) — `dockerd`/`containerd` 선택 가능, Windows 무설정 지원
- [Colima](https://github.com/abiosoft/colima) (macOS/Linux)

설치 후 `docker context use <name>` 명령으로 전환하면 Testcontainers가 즉시 동작.

### 6) **Docker Desktop 4.69+ 사용자 — 권장 경로**

해당 버전의 docker-java 호환성 이슈는 코드·설정으로 우회 불가. 다음 중 택일:

**(A) Docker Desktop 다운그레이드** — 4.34 이하 버전이 안정적으로 알려져 있음. [공식 Release Notes](https://docs.docker.com/desktop/release-notes/)에서 내려받기.

**(B) Rancher Desktop 사용** — 무료, Windows 네이티브 설치 후 `dockerd` 모드 선택. Docker Desktop과 공존 가능(컨텍스트만 전환).

```bash
# Rancher Desktop 설치 후
docker context ls          # rancher-desktop 컨텍스트 확인
docker context use rancher-desktop
./gradlew :apps:auth-service:test --tests "*IntegrationTest"
```

**(C) WSL 내부 Docker Engine** — WSL2 Ubuntu에 `docker-ce` 직접 설치. Docker Desktop 의존 제거.

**(D) CI에서만 통합 테스트 실행** — 로컬에선 skip을 수용, GitHub Actions Linux runner에서는 정상 실행되므로 CI 파이프라인이 실질 안전망 역할.

---

## 환경별 권장 설정

### Windows + Docker Desktop (WSL2 backend)

가장 흔한 조합. 먼저 (1) 재시작, 실패 시 (2) TCP 노출을 시도.

`~/.testcontainers.properties` 권장 값:

```properties
# WSL2 기반 기본 Linux 엔진
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

### Windows + Docker Desktop (Windows 컨테이너)

OAuth/MySQL/Kafka 통합 테스트는 Linux 컨테이너를 사용하므로 Docker Desktop을 **Linux containers** 모드로 전환 필요.

### macOS

일반적으로 문제 없음. `~/.testcontainers.properties`가 비어 있거나 `unix:///var/run/docker.sock`로 자동 설정됨.

### Linux

Docker Engine을 기본 소켓(`/var/run/docker.sock`)에서 실행 중이면 추가 설정 불필요.

---

## 검증 체크리스트

모든 조치 후 다음 3단계로 확인:

1. **CLI 레벨**
   ```bash
   docker info
   ```
   `ServerVersion`이 비어 있지 않고 `OSType: linux`로 표시되어야 함.

2. **Testcontainers 프로브** (어느 auth-service 통합 테스트를 직접 실행)
   ```bash
   ./gradlew :apps:auth-service:test --tests AuthIntegrationTest.loginSuccess
   ```

   - `Passed` → 성공
   - `Skipped` → Docker가 여전히 인식되지 않음. gradle에 `-i`를 붙여 원인 로그 확인.
   - `Failed` → 테스트 로직 문제 (환경은 OK). 다른 원인 조사.

3. **전체 통합 테스트 회귀**
   ```bash
   ./gradlew :apps:auth-service:test --tests "*IntegrationTest"
   ```

---

## 관련 태스크

- [TASK-BE-058](../../tasks/ready/TASK-BE-058-fix-testcontainers-docker-detection.md) — 이 가이드를 낳은 환경 블로커 태스크
- [TASK-BE-053](../../tasks/done/TASK-BE-053-oauth-social-login.md) — OAuth 기본 구현
- [TASK-BE-056](../../tasks/done/TASK-BE-056-oauth-microsoft-provider.md) — Microsoft provider 추가
- [TASK-BE-057](../../tasks/done/TASK-BE-057-fix-oauth-provider-integration-tests.md) — OAuth 통합 테스트

---

## 문의

여전히 해결되지 않는 경우:

1. `./gradlew :apps:auth-service:test --tests AuthIntegrationTest -i 2>&1 | grep -iE "attempted|strategy"` 출력을 수집
2. `docker context ls`, `docker version`, `docker info` 출력 수집
3. 위 정보와 함께 팀에 공유
