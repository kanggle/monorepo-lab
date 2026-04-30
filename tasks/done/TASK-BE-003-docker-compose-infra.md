# Task ID

TASK-BE-003

# Title

Docker Compose 로컬 인프라 — MySQL, Redis, Kafka (+UI), Zookeeper

# Status

ready

# Owner

backend

# Task Tags

- deploy

# depends_on

- TASK-BE-001

---

# Goal

`docker-compose.yml`을 새로 작성하여 로컬 개발에 필요한 인프라 서비스(MySQL, Redis, Kafka, Zookeeper, Kafka UI)를 `docker compose up -d`로 기동할 수 있는 상태를 만든다. Prometheus/Grafana/Loki는 TASK-BE-012(backlog)로 미룬다.

---

# Scope

## In Scope

- `docker-compose.yml` 작성 (인프라 전용, 애플리케이션 서비스 미포함)
- MySQL 8: 서비스별 DB 4개 (`auth_db`, `account_db`, `security_db`, `admin_db`) 초기화 SQL
- Redis 7: 단일 인스턴스, 패스워드 설정
- Kafka (KRaft 또는 Zookeeper 모드) + Kafka UI
- `.env.example` 연동 (DB 패스워드, Redis 패스워드 등)
- 헬스체크 설정
- `scripts/dev-up.sh` / `scripts/dev-down.sh`가 이 compose를 사용하도록 확인

## Out of Scope

- Prometheus / Grafana / Loki (TASK-BE-012)
- 애플리케이션 서비스의 Docker 이미지 (각 서비스 태스크에서)
- K8s 매니페스트
- 프로덕션 Docker 설정

---

# Acceptance Criteria

- [ ] `docker compose up -d` 성공 (모든 컨테이너 healthy)
- [ ] MySQL에 `auth_db`, `account_db`, `security_db`, `admin_db` 4개 DB 생성됨
- [ ] `mysql -u root -p -h 127.0.0.1` 접속 성공
- [ ] `redis-cli -a $REDIS_PASSWORD ping` → PONG
- [ ] Kafka 브로커 기동 확인 (Kafka UI에서 클러스터 표시)
- [ ] `.env.example`의 변수가 `docker-compose.yml`에서 참조됨
- [ ] `docker compose down -v`로 깨끗하게 정리 가능

---

# Related Specs

- `specs/services/auth-service/dependencies.md` — MySQL `auth_db`
- `specs/services/account-service/dependencies.md` — MySQL `account_db`
- `specs/services/security-service/dependencies.md` — MySQL `security_db`, Kafka consumer
- `specs/services/admin-service/dependencies.md` — MySQL `admin_db`
- `platform/deployment-policy.md`

# Related Skills

- `.claude/skills/infra/docker-build/SKILL.md`

---

# Related Contracts

없음 (인프라 태스크)

---

# Target Service

- root (infrastructure)

---

# Implementation Notes

- MySQL 초기화: `docker-entrypoint-initdb.d/` 에 `init.sql` 마운트하여 4개 DB + 사용자 생성
- Kafka: KRaft 모드 권장 (Zookeeper 불필요). `KAFKA_KRAFT_CLUSTER_ID` 환경 변수
- Kafka UI: `provectuslabs/kafka-ui` 이미지
- Redis: `redis:7-alpine`, `--requirepass` 설정
- 포트 매핑: MySQL 3306, Redis 6379, Kafka 9092(내부)/9093(외부), Kafka UI 8090
- `.env`에서 패스워드를 읽되, `.env` 없으면 기본값으로 동작 (첫 실행 편의)

---

# Edge Cases

- 포트 충돌 (이미 다른 MySQL이 3306에서 실행 중) → docker-compose.yml에 주석으로 안내
- 디스크 부족으로 Kafka 데이터 쌓임 → log retention 1일 설정

---

# Failure Scenarios

- MySQL healthcheck 실패 → init.sql 구문 오류 확인
- Kafka KRaft 초기화 실패 → `KAFKA_KRAFT_CLUSTER_ID` UUID 형식 확인
- `docker compose up` 후 Redis NOAUTH → `.env`의 `REDIS_PASSWORD` 확인

---

# Test Requirements

- `docker compose up -d` → 모든 컨테이너 healthy (30초 이내)
- MySQL 4개 DB 접속 확인
- Redis ping 확인
- Kafka topic 생성 가능 확인 (`kafka-topics.sh --create --topic test`)

---

# Definition of Done

- [ ] docker-compose.yml 작성 완료
- [ ] 모든 인프라 서비스 healthy
- [ ] `.env.example` 연동 확인
- [ ] `docker compose down -v` 정리 확인
- [ ] Ready for review
