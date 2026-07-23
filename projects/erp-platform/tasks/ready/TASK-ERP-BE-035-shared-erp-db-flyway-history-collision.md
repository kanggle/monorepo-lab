# TASK-ERP-BE-035 — erp 4개 서비스가 공유하는 `erp_db` 의 단일 `flyway_schema_history` 체크섬 충돌 (masterdata·notification 크래시루프)

- **Type**: TASK-ERP-BE (defect — data/migration isolation)
- **Status**: ready
- **Service**: masterdata-service · notification-service · approval-service · read-model-service (erp-platform)
- **Domain/traits**: erp / (declared in PROJECT.md)
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (multi-service migration isolation + baked-volume reset ordering)

---

## 🔧 구현 (2026-07-23) — AC-0 정정 + DB 분리

**AC-0 재측정이 티켓 전제를 두 가지 정정했다:**

1. **충돌은 4개가 아니라 3개 서비스** — `read-model-service` 는 이미 자기 DB(`erp_read_model_db`)를 쓴다(그래서 데모에서 healthy 했다). 공유 `erp_db` 는 **masterdata · approval · notification** 셋뿐. approval 이 관측 부팅에서 healthy 였던 건 그 부팅의 migrate 레이스 승자였기 때문(비결정적).
2. **flyway.table 격리만으론 불충분** — 세 서비스가 `erp_db` 에서 **테이블 이름도 충돌**한다: `outbox`(masterdata+approval) · `idempotency_keys`(masterdata+approval) · `processed_events`(셋 다). history 만 나눠도 CREATE TABLE 이 충돌/공유된다 ⇒ **DB 분리**가 정답(read-model 선례와 동일).

**적용**: masterdata 는 `erp_db` 유지, **approval → `erp_approval_db`**, **notification → `erp_notification_db`**.
- `infra/mysql-initdb/02-create-service-dbs.sql` (신규) — 두 DB + grant(01-* 의 read-model 패턴 미러).
- approval/notification `application.yml` datasource 기본값 + `docker-compose.yml` env(`MYSQL_DB`) + 주석.
- approval/notification `architecture.md` Data store 서술.
- 회귀: `FlywayHistoryIsolationIntegrationTest`(approval) — 전용 DB=GREEN / 공유 history 를 타 서비스가 선점=`FlywayValidateException`. `compileTestJava` GREEN, 실행은 CI Testcontainers 권위.

**baked 볼륨**: 신규 DB 방식이라 as-baked `erp_db` 의 오염된 history 와 무관(신규 DB=신규 history). MONO-399 AC-6 재굽기가 fresh 볼륨을 만들어 세 fix 를 함께 배포한다.

---

## 🔴 발굴 출처 — TASK-MONO-399 데모 호스트 실측 (2026-07-23)

MONO-399 이 데모 AMI(`ami-0b6b962d3f3f23865`, main `f5288a7b1` 동결)를 실측하던 중, erp 4개 서비스 중 **2개가 부팅 시 무한 크래시루프**하는 것을 dmesg/`docker logs` 로 확인했다:

- `erp-platform-masterdata` — **47회** 재시작 (관측 시점, 계속 증가 중)
- `erp-platform-notification` — **41회** 재시작 (계속 증가 중)
- `erp-platform-approval` · `erp-platform-read-model` — healthy (재시작 0~1)

`docker inspect`: 두 크래시 컨테이너 모두 `exitcode=0, OOMKilled=false, memlimit=0`. **메모리 아님, kafka 아님.** `docker logs` 근인:

```
Caused by: org.flywaydb.core.api.exception.FlywayValidateException: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
-> Applied to database : -503845111
-> Resolved locally    : -764052118    (masterdata)  /  54272003 (notification)
Migration checksum mismatch for migration version 2
-> Applied to database : 408726732
-> Resolved locally    : 1801196070   (masterdata)  /  233921704 (notification)
Migration checksum mismatch for migration version 3
-> Applied to database : 639061159
-> Resolved locally    : 1576960364   (notification)
→ BeanCreationException: 'flywayInitializer' → Spring context 기동 실패 → 컨테이너 exit → restart 루프
```

---

## Goal

erp 4개 서비스가 **하나의 `erp_db` MySQL 인스턴스를 공유**하면서 **각자 자기 `V1__init.sql`·`V2__…` 를 같은 버전번호·다른 내용으로** 들고 있어, **공유 `flyway_schema_history` 테이블에서 체크섬이 충돌**한다. 먼저 migrate 한 서비스가 V1/V2/V3 엔트리를 선점하면, 나머지 서비스는 자기 로컬 체크섬이 DB 와 달라 Flyway `validate` 에 실패하고 부팅하지 못한다. 이 충돌을 제거해 **4개 서비스 전부 clean 부팅**하게 한다.

## 근본 원인 (실측 확정)

- **공유 DB**: 4개 서비스 모두 `MYSQL_DB=erp_db` (compose `docker-compose.yml` L117/219/276/316). 설계 의도는 *"separate tables; no shared-table JOIN"* (compose 주석 L200/256) — DB 는 하나, 테이블만 분리.
- **버전번호 충돌**: 각 서비스가 독립 마이그레이션을 보유하는데 버전 네임스페이스가 겹친다:
  - `approval-service`: V1__init · V2__multi_stage_routing · V3__delegation · V4 · V5
  - `masterdata-service`: V1__init · V2__masterdata_outbox_v2
  - `notification-service`: V1__init · V2__delegation_notification · V3__delegation_revoked_notification
  - `read-model-service`: V1__init · V2__approval_fact_proj · V3 · V4
- **단일 history 테이블**: 모두 Flyway 기본값(`flyway_schema_history`)을 같은 `erp_db` 에 쓴다 ⇒ V1·V2·V3 이 **서비스마다 내용이 다른데 같은 슬롯**을 두고 경쟁. 최초 migrate 승자만 살고 나머지는 checksum mismatch.
- **비결정적**: 어느 서비스가 이기는지는 부팅 순서 레이스 — 관측에서는 approval/read-model 이 이기고 masterdata/notification 이 졌지만, 다른 부팅에서 뒤바뀔 수 있다.

## Scope

**In scope**
- erp 4개 서비스의 Flyway history 를 **서비스별로 격리**한다. 두 가지 방식 중 택1(설계 판단):
  1. **서비스별 history 테이블** — 각 서비스 `spring.flyway.table=flyway_schema_history_<service>` (한 DB, 테이블 분리 — compose 주석의 "separate tables" 의도와 정합).
  2. **서비스별 스키마/DB** — 각 서비스 전용 schema. 더 강한 격리이나 compose/DDL 변경 폭이 크다.
- 선택한 방식이 **baked erp_db 볼륨의 기존 오염된 `flyway_schema_history`** 와 충돌하지 않도록 처리(신규 history 테이블이면 자동 회피; 스키마 분리면 초기화 필요).
- `specs/services/*/architecture.md` 의 DB/마이그레이션 격리 서술 갱신.

**Out of scope**
- erp 서비스 로직/스키마 변경(테이블 구조는 그대로 — history 격리만).
- MONO-399 AC-6 재굽기 — 이 task 머지 후 MONO-399 가 수행(재굽기가 fresh 볼륨을 만들어 배포).

## Acceptance Criteria

- **AC-0 (verify-then-act)**: `origin/main` 에서 충돌이 여전히 존재하는지 재확인한다(MONO-399 증거는 `f5288a7b1` as-baked 기준). 4개 서비스 마이그레이션의 버전번호 겹침 + 공유 `flyway_schema_history` 사용을 grep 으로 확인. 다르면 그 사실이 먼저다.
- **AC-1**: 4개 erp 서비스가 **어떤 부팅 순서에서도** clean 기동한다(Flyway validate 실패 0). 서비스별 history 격리가 코드/설정에 반영.
- **AC-2 (재현→회귀)**: fresh `erp_db` 볼륨으로 4개 서비스를 동시 기동하는 IT(또는 compose 기동 스모크)에서 **checksum mismatch 0 · 재시작 0**. 격리 제거 시 RED.
- **AC-3**: `:check` GREEN. 기존 erp Testcontainers IT 회귀 0.
- **AC-4**: architecture.md 의 DB 격리 서술이 구현과 정합.

## Related Specs / Contracts

- `projects/erp-platform/specs/services/{masterdata,notification,approval,read-model}-service/architecture.md` (DB/Concurrency)
- `projects/erp-platform/docker-compose.yml` (공유 `erp_db` 배선)
- 계약 변경 없음(내부 마이그레이션 격리 — API/이벤트 무변경).

## Edge Cases / Failure Scenarios

- **baked 볼륨 잔존 오염**: AMI 가 pre-warm 으로 채운 `erp_db` 볼륨에 이미 오염된 `flyway_schema_history` 가 있다. 서비스별 테이블 방식이면 신규 테이블이라 무해; 스키마 분리 방식이면 볼륨 초기화가 선행돼야 한다.
- **레이스 은폐**: 로컬에서 특정 순서로 통과해도 다른 순서에서 깨진다 — IT 는 4개 동시 기동을 강제할 것(단일 서비스 기동은 충돌을 못 본다).
- **approval/read-model 도 잠재적 피해자**: 관측에서 이겼을 뿐 구조적으로 동일 위험 — 4개 전부 격리.

## Provenance

TASK-MONO-399 데모 실측 (2026-07-23). erp-masterdata/notification 크래시루프의 근인이 메모리도 kafka 도 아닌 **공유 DB Flyway 체크섬 충돌**임을 스택트레이스로 확정. MONO-399 AC-6 재굽기는 이 결함을 고치지 못하므로(main 소스에 그대로 존재), 별도 티켓으로 분리하고 **재굽기는 이 task 머지 후**로 순서를 정했다(사용자 판단, 이중 bake 회피).
