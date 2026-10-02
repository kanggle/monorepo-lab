# Task ID

TASK-MONO-753

# Title

데모 ERP read-model 이 영영 기동하지 않는다 — 부팅 스크립트의 `down: erp` 가 Flyway V2 를 **도중에** 끊어 «테이블은 있고 이력은 없는» 상태가 남았다

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- erp
- flyway

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (원인이 두 층 — 마이그레이션 멱등성 · 부팅 순서 — 어느 쪽을 고칠지가 결정)

---

# Dependency Markers

- **발견**: 18차 재굽기 창(2026-10-02 UTC, `TASK-MONO-744` 라이브 판정 중)

# Goal

데모 인스턴스의 `erp-platform-read-model` 이 재시작을 반복한다(관측 시점 44회). `console-erp` 묶음은 끝내 `ready` 가 안 됐고, 콘솔 ERP 화면의 목록이 전부 비었다. 같은 인스턴스를 껐다 켜도 데이터 볼륨이 남으므로 **다음 기동에서도 그대로**다. 데모를 복구하고, 재발을 막는다.

# 실측 (2026-10-02 UTC, SSM 읽기)

| 시각(UTC) | 사건 | 출처 |
|---|---|---|
| 09:16:39 | `[demo] down: erp` (첫 부팅 경로) | `journalctl -u demo-stack` |
| 09:21:25–26 | read-model 첫 컨테이너가 V1 적용(`installed_on 09:21:26`) · V2 의 `CREATE TABLE approval_fact_proj` 실행(`create_time 09:21:26`) | `flyway_schema_history` · `information_schema.tables` |
| 09:21:02–16 | 부팅 스크립트가 저장된 선택(9 묶음)으로 다시 돌며 `down: erp` → read-model **Stopping** | `journalctl` |
| 09:28:04 | 새 컨테이너가 V2 다시 → `Table 'approval_fact_proj' already exists`(1050) → 이력에 `version 2 success=0` | 컨테이너 로그 · 이력 |
| 이후 | 매 기동 `Detected failed migration to version 2` → 검증 실패 → 종료 → 재시작 | 컨테이너 로그 |

- 원인 ①(직접): **실행 중인 인스턴스에 묶음을 추가**(`POST /bundle/start`, 09:16)하자 부팅 스크립트가 다시 돌며 그 순간 기동 중이던 ERP 스택을 내렸다 — V2 한가운데서.
- 원인 ②(조건): MySQL DDL 은 트랜잭션이 아니다 — `CREATE TABLE` 은 남고 Flyway 이력 행은 안 남는다. V2 가 `CREATE TABLE IF NOT EXISTS` 가 아니어서 재실행이 실패한다.

# Scope

## In Scope

- **데모 복구(소유자 실행 — 원격 DB 쓰기는 에이전트가 할 수 없다)**: 다음 기동 때, `approval_fact_proj` 가 비어 있으면 실패 이력 행 삭제 + 테이블 삭제 + 컨테이너 재시작. 명령은 아래 «복구 명령».
- 재발 방지 — 둘 중 하나 또는 둘 다를 정한다: (a) 부팅 스크립트가 **기동 중인 도메인을 내리지 않는다**(추가 묶음은 합치기만 — 이미 떠 있는 도메인의 `down` 금지) (b) ERP read-model 마이그레이션(과 같은 모양의 다른 서비스 마이그레이션)을 재실행 안전하게(`IF NOT EXISTS`) — 단 이미 적용된 마이그레이션 파일 수정은 체크섬을 바꾼다(Flyway 규칙 확인 먼저).
- 같은 «도중 중단» 이 다른 도메인 마이그레이션에도 열려 있는지 census.

## Out of Scope

- ERP 기능 변경

# Acceptance Criteria

- [ ] **AC-1** — 다음 데모 창에서 `console-erp` 가 `ready` 가 되고 ERP 목록이 보인다(복구 명령 실행 뒤).
- [ ] **AC-2** — 재발 방지 갈래(a/b)를 정하고 구현 — (a) 면 «실행 중 인스턴스에 묶음 추가 → 이미 기동 중인 도메인에 `down` 없음» 을 부팅 스크립트 시험으로 고정.
- [ ] **AC-3** — census 결과(같은 위험을 가진 마이그레이션 목록)를 이 파일에 적는다.

# 복구 명령 (소유자 — 데모가 켜져 ERP 컨테이너가 떠 있을 때, PowerShell)

```powershell
aws ssm send-command --instance-ids i-05395a5a7baa23bb8 --document-name AWS-RunShellScript --parameters 'commands=["n=$(docker exec erp-platform-mysql sh -c ''mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" -N -e \"SELECT COUNT(*) FROM erp_read_model_db.approval_fact_proj\" 2>/dev/null''); echo rows=$n; if [ \"$n\" = 0 ]; then docker exec erp-platform-mysql sh -c ''mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" -e \"DELETE FROM erp_read_model_db.flyway_schema_history WHERE version=2 AND success=0; DROP TABLE erp_read_model_db.approval_fact_proj;\" 2>/dev/null''; docker restart erp-platform-read-model; echo repaired; fi"]'
```

🔴 이 명령은 에이전트 창에서 «원격 쓰기» 로 분류기가 막았다(2026-10-02) — 그래서 소유자 실행으로 남긴다. 실행 뒤 `aws ssm get-command-invocation` 으로 `rows=0 · repaired` 를 확인하고, 몇 분 뒤 `GET /bundles` 의 `console-erp` 가 `ready` 인지 본다.

# Related Specs

- `infra/demo/demo-boot.sh` · `infra/demo/demo-up.sh`(묶음 → 도메인 기동/정지)
- ERP read-model `db/migration/V2__approval_fact_proj.sql`

# Related Contracts

- 없음

# Edge Cases

- 테이블에 행이 있으면(프로젝션이 이미 채워짐) 복구 명령은 아무것도 하지 않는다 — 그때는 이력만 `repair` 하는 다른 절차가 필요하다.

# Failure Scenarios

1. 마이그레이션 파일만 `IF NOT EXISTS` 로 고쳐 체크섬이 바뀌고, 이미 V2 가 성공한 다른 볼륨에서 검증 실패가 난다.
