# Task ID

TASK-BE-032

# Title

docker-compose 전체 구성 — 인프라 + 전체 서비스 컨테이너

# Status

ready

# Owner

backend

# Task Tags

- code
- infra

---

# Goal

로컬 개발 환경에서 `docker-compose up --build` 한 번으로 전체 시스템(인프라 + 6개 서비스)을 실행할 수 있도록 구성한다.

이 태스크 완료 후: `docker-compose up -d --build` 후 `gateway-service:8080` 을 통해 모든 서비스 API에 접근 가능하다.

---

# Scope

## In Scope

- 각 서비스 Dockerfile 작성 (eclipse-temurin:21-jre-alpine 기반)
- docker-compose.yml 전체 재작성:
  - 인프라: Kafka (내부/외부 이중 리스너), PostgreSQL ×4, Redis ×1, Elasticsearch ×1
  - 앱 서비스 ×6: auth, product, search, order, payment, gateway
  - depends_on + healthcheck 설정
  - 볼륨 선언 (DB 데이터 영속성)
- .env 파일: 로컬 개발용 기본 시크릿/환경변수

## Out of Scope

- Kubernetes 배포
- CI/CD 파이프라인
- 프로덕션 시크릿 관리

---

# Acceptance Criteria

- [ ] `./gradlew bootJar` 후 `docker-compose up --build -d` 로 전체 시스템이 기동된다
- [ ] `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `POST http://localhost:8080/api/auth/signup` 이 auth-service로 라우팅된다
- [ ] 각 서비스가 자신의 DB/인프라에 연결되어 Flyway 마이그레이션이 실행된다
- [ ] `docker-compose down -v` 로 정리된다

---

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/api-gateway-policy.md`

# Related Contracts

없음

---

# Target Service

- infra, 모든 서비스

---

# Implementation Notes

- Kafka 이중 리스너:
  - 내부(Docker): `kafka:9092`
  - 외부(호스트 bootRun): `localhost:9093`
- PostgreSQL 포트 매핑: auth=5432, product=5433, order=5434, payment=5435
- Redis: 6379 (auth + gateway 공유)
- Elasticsearch: 9200, xpack.security.enabled=false
- JWT_SECRET: .env에서 주입, auth-service + gateway-service 공유

---

# Edge Cases

- DB 기동 전 서비스 기동 → depends_on + healthcheck condition으로 방지
- Kafka 기동 전 서비스 기동 → kafka healthcheck 후 서비스 기동

---

# Failure Scenarios

- 포트 충돌 → .env에서 포트 재정의
- Elasticsearch 메모리 부족 → ES_JAVA_OPTS 조정

---

# Definition of Done

- [ ] Implementation completed
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
