# TASK-BE-234: fix — admin-service Flyway V0020 마이그레이션 번호 충돌

## Goal

`docker-compose.e2e.yml` 환경에서 admin-service 시작 시 Flyway 검증 단계가 실패한다. 동일 번호 V0020을 가진 두 개의 마이그레이션 파일이 존재해서 e2e profile(`spring.flyway.locations: classpath:db/migration,classpath:db/migration-dev`)에서 충돌이 발생한다.

```
Found more than one migration with version 0020
Offenders:
-> db/migration/V0020__seed_account_read_permission.sql (SQL)
-> db/migration-dev/V0020__seed_dev_short_admin.sql (SQL)
```

dev 시드 마이그레이션을 다음 가용 번호로 rename 하여 충돌을 해소한다.

## Scope

**In:**
- `apps/admin-service/src/main/resources/db/migration-dev/V0020__seed_dev_short_admin.sql` → `V0023__seed_dev_short_admin.sql`로 rename (현재 `db/migration/`의 최대 번호 V0022 다음)
- 파일 헤더 코멘트의 버전 번호 갱신 (있을 경우)

**Out:**
- 마이그레이션 SQL 본문 변경 없음
- prod 마이그레이션(`db/migration/`) 변경 없음

## Acceptance Criteria

- [ ] `db/migration/`과 `db/migration-dev/` 양쪽 합쳐도 동일 번호의 마이그레이션이 없다
- [ ] admin-service가 e2e profile로 정상 기동 (Flyway validation 통과)
- [ ] 기존 dev 시드 데이터(SUPER_ADMIN 등)가 동일하게 적용된다
- [ ] `./gradlew :apps:admin-service:test` 통과

## Related Specs

- `platform/database-migration-policy.md` (있을 경우)
- `specs/services/admin-service/architecture.md`

## Related Contracts

- 없음 (DB 마이그레이션 번호만 변경)

## Edge Cases

- e2e 환경에서 이미 V0020(prod 시드)이 적용된 DB가 있을 경우: rename 후 V0023이 새 마이그레이션으로 인식되어 적용됨 (dev 시드 본문 자체는 idempotent해야 함)
- 다른 작업자가 동일 번호로 신규 마이그레이션을 추가 중인 경우: 머지 충돌로 즉시 인지 가능

## Failure Scenarios

- rename 후에도 Flyway가 새 번호를 인식하지 못함 → 빌드 시 리소스 캐시 정리 필요 (`./gradlew clean :apps:admin-service:bootJar`)
- dev 시드가 이미 V0020으로 적용된 환경: `flyway_schema_history`에 V0020 row가 있으면 새 V0023이 추가로 적용됨 (이중 시드 가능성). 사전에 `docker compose down -v`로 볼륨 초기화 권장.
