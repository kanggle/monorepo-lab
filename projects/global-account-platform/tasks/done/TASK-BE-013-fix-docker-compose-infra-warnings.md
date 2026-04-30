# Task ID

TASK-BE-013

# Title

Docker Compose 인프라 개선 — init.sql 패스워드 파라미터화 및 Kafka UI 이미지 태그 고정

# Status

review

# Owner

backend

# Task Tags

- deploy

# depends_on

- TASK-BE-003

---

# Goal

TASK-BE-003 코드 리뷰에서 발견된 경고 수준 이슈를 수정한다.

1. `docker/mysql/init.sql`의 서비스 유저 패스워드가 하드코딩되어 있음 → `.env.example` 변수로 파라미터화
2. `docker-compose.yml`의 `provectuslabs/kafka-ui:latest` → 특정 버전 태그로 고정
3. `docker/mysql/init.sql`의 서비스 유저 권한이 `ALL PRIVILEGES` → 필요 최소 권한으로 축소

---

# Scope

## In Scope

- `docker/mysql/init.sql`: 서비스 유저 패스워드를 MySQL 환경 변수 또는 init 시점 셸 변수를 통해 주입받도록 변경. `.env.example`에 `AUTH_DB_PASSWORD`, `ACCOUNT_DB_PASSWORD`, `SECURITY_DB_PASSWORD`, `ADMIN_DB_PASSWORD` 변수 추가
- `docker-compose.yml`: `kafka-ui` 이미지를 `provectuslabs/kafka-ui:v0.7.2` (또는 최신 stable pinned 버전)으로 고정
- `docker-compose.yml`: MySQL 환경 변수 블록에 서비스 유저 패스워드 변수 전달
- `docker/mysql/init.sql`: `ALL PRIVILEGES` → `SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER, REFERENCES`로 축소

## Out of Scope

- 애플리케이션 서비스 코드
- Prometheus/Grafana/Loki (TASK-BE-012)
- K8s 매니페스트
- 기존 init.sql 이외 파일의 구조 변경

---

# Acceptance Criteria

- [ ] `.env.example`에 4개 서비스 DB 패스워드 변수가 추가됨 (`AUTH_DB_PASSWORD`, `ACCOUNT_DB_PASSWORD`, `SECURITY_DB_PASSWORD`, `ADMIN_DB_PASSWORD`)
- [ ] `docker/mysql/init.sql`에서 하드코딩된 패스워드가 제거됨 (환경 변수 참조로 대체)
- [ ] `docker compose config --quiet` 성공
- [ ] `docker compose up -d` 성공 (모든 컨테이너 healthy)
- [ ] `kafka-ui` 이미지 태그가 `latest`가 아닌 고정 버전임
- [ ] 서비스 유저가 `ALL PRIVILEGES` 대신 최소 필요 권한만 가짐
- [ ] `.env` 파일이 `.gitignore`에 계속 포함됨 (시크릿 커밋 금지)

---

# Related Specs

- `platform/deployment-policy.md` — "Hard-coded secrets are forbidden"
- `specs/services/auth-service/dependencies.md`
- `specs/services/account-service/dependencies.md`
- `specs/services/security-service/dependencies.md`
- `specs/services/admin-service/dependencies.md`

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

- MySQL init.sql은 `docker-entrypoint-initdb.d/`에서 실행될 때 환경 변수를 직접 받을 수 없음. 해결책: `init.sql` 대신 `init.sh`로 전환하여 `envsubst` 또는 셸 변수 치환으로 패스워드 주입. 또는 `mysql` 서비스에 `MYSQL_AUTH_PASSWORD` 등의 env를 전달하고 `.sh` 래퍼로 처리.
- `kafka-ui` 버전: `https://github.com/provectus/kafka-ui/releases`에서 최신 stable 확인 후 pinned. 현재 권장: `v0.7.2`.
- `GRANT` 권한 범위: Flyway migration에서 `CREATE TABLE`, `ALTER TABLE`, `CREATE INDEX`, `DROP TABLE` 등이 필요할 수 있으므로 DDL 포함 여부를 확인하고 필요 시 포함.

---

# Edge Cases

- `init.sh`로 전환 시 Windows 개발자 환경에서 CRLF 줄 바꿈 주의 → `.gitattributes`에 `*.sh text eol=lf` 확인
- 기존에 이미 MySQL 볼륨이 생성된 경우 init script가 재실행되지 않음 → `docker compose down -v` 후 재기동 안내 README에 추가

---

# Failure Scenarios

- `envsubst` 또는 셸 변수 치환 실패 → 패스워드가 빈 문자열로 설정됨. 방어: 변수 미설정 시 기본값(`:-default`) 사용
- Flyway가 부족한 권한으로 마이그레이션 실패 → 필요 DDL 권한을 목록에 추가

---

# Test Requirements

- `docker compose up -d` → 모든 컨테이너 healthy
- MySQL 서비스 유저로 접속 확인 (패스워드 변수 값 사용)
- `GRANT` 권한이 최소 권한으로 설정됨 확인 (`SHOW GRANTS FOR 'auth_user'@'%'`)

---

# Definition of Done

- [ ] init.sql/init.sh에서 하드코딩 패스워드 제거
- [ ] `.env.example` 업데이트
- [ ] `docker-compose.yml` kafka-ui 태그 고정
- [ ] 서비스 유저 최소 권한 설정
- [ ] `docker compose config --quiet` 통과
- [ ] Ready for review
