# Task ID

TASK-MONO-753

# Title

데모 ERP read-model 이 영영 기동하지 않는다 — 부팅 스크립트의 `down: erp` 가 Flyway V2 를 **도중에** 끊어 «테이블은 있고 이력은 없는» 상태가 남았다

# Status

done (2026-10-04 UTC — 4차원 검증 · PR #4110 squash `ce3364931` · AC-1 19차 창)

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

- 원인 ①(직접): ~~**실행 중인 인스턴스에 묶음을 추가**(`POST /bundle/start`, 09:16)하자 부팅 스크립트가 다시 돌며 그 순간 기동 중이던 ERP 스택을 내렸다 — V2 한가운데서.~~ → **틀렸다 — 아래 «원인 정정».** 끊은 것은 **같은 부팅에서 두 번째로 돈 `demo-stack` 유닛**의 전체 down 이다.
- 원인 ②(조건): MySQL DDL 은 트랜잭션이 아니다 — `CREATE TABLE` 은 남고 Flyway 이력 행은 안 남는다. V2 가 `CREATE TABLE IF NOT EXISTS` 가 아니어서 재실행이 실패한다.

# Scope

## In Scope

- **데모 복구(소유자 실행 — 원격 DB 쓰기는 에이전트가 할 수 없다)**: 다음 기동 때, `approval_fact_proj` 가 비어 있으면 실패 이력 행 삭제 + 테이블 삭제 + 컨테이너 재시작. 명령은 아래 «복구 명령».
- 재발 방지 — 둘 중 하나 또는 둘 다를 정한다: (a) 부팅 스크립트가 **기동 중인 도메인을 내리지 않는다**(추가 묶음은 합치기만 — 이미 떠 있는 도메인의 `down` 금지) (b) ERP read-model 마이그레이션(과 같은 모양의 다른 서비스 마이그레이션)을 재실행 안전하게(`IF NOT EXISTS`) — 단 이미 적용된 마이그레이션 파일 수정은 체크섬을 바꾼다(Flyway 규칙 확인 먼저).
- 같은 «도중 중단» 이 다른 도메인 마이그레이션에도 열려 있는지 census.

## Out of Scope

- ERP 기능 변경

# Acceptance Criteria

- [x] **AC-1** — 다음 데모 창에서 `console-erp` 가 `ready` 가 되고 ERP 목록이 보인다(복구 명령 실행 뒤).
- [x] **AC-2** — 재발 방지 갈래(a/b)를 정하고 구현 — (a) 면 «실행 중 인스턴스에 묶음 추가 → 이미 기동 중인 도메인에 `down` 없음» 을 부팅 스크립트 시험으로 고정. → **(a)**, 단 문장을 실제 원인에 맞게 고쳐 «**같은 부팅의 두 번째 유닛 기동** → `down` 없음» 으로 고정(아래 «구현 결과»). «묶음 추가 → `down` 없음» 은 이미 (z24)(5) 가 지키고 있었다.
- [x] **AC-3** — census 결과(같은 위험을 가진 마이그레이션 목록)를 이 파일에 적는다. → 아래 «census».

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

---

# 원인 정정 (2026-10-02 UTC · 분석=구현=Opus 5.5)

기안 때의 원인 ① 은 **코드와 맞지 않는다.**

- `POST /bundle/start` 가 running 인스턴스에서 하는 일은 SSM 으로 `demo-boot.sh selection` 을 부르는 것뿐이다(`handler.py` `bundle_start` 676행). 그 호출에는 `DEMO_BOOT_RESET` 이 **없고**((z24)(3) 이 handler 의 언급 자체를 막는다), 플래그가 없으면 `demo-boot.sh` 는 «잔존 스택 정리 건너뜀» 으로 바로 `demo-up.sh` 를 `exec` 한다. `demo-up.sh` 는 `down` 을 부르지 않는다. **`[demo] down: <p>` 를 찍는 곳은 저장소 전체에 `demo-down.sh` 하나**뿐이다.
- 09:21 의 줄들을 읽은 명령은 `journalctl -u demo-stack` 였다(지난 창의 `erp3.sh`). SSM RunShellScript 의 출력은 그 유닛 저널에 안 남는다 ⇒ `bash[82038]` 의 «저장된 부팅 선택 … profile=…» 과 `bash[83642]` 의 `[demo] down: erp` 는 **`demo-stack` 유닛 자신의 두 번째 실행**이다. 첫 실행은 09:16:34–39(`provision-env` → `down: erp`).
- 즉 **같은 부팅에서 유닛이 두 번 돌았고, 두 번째 실행의 `DEMO_BOOT_RESET=1` 전체 down 이** 첫 실행이 막 올린 스택을 내렸다. ERP read-model 은 그때 JVM 기동 중 Flyway V2 를 막 실행하던 참이었다(`Stopping` 09:21:16 → 10초 유예 → V2 `create_time 09:21:26`).

🔴 **무엇이 두 번째 실행을 일으켰나 — 가설(미측정)**: 새 인스턴스의 **첫 부팅**이었다(terraform 교체 직후). `demo-stack` 은 AMI 에서 `enable` 돼 부팅 때 스스로 돈다. 그리고 첫 부팅에만 도는 user-data(`infra/demo/aws/ec2/user-data.sh`)가 `systemctl start demo-stack.service` 를 한 번 더 부른다. oneshot 유닛이 **active** 면 이 start 는 아무것도 안 하지만, 첫 실행이 **`failed`** 로 끝났으면(`demo-up.sh` 는 부분 실패를 비-0 으로 끝낸다 — 그 파일 438행 주석, 2026-08-19 실측 «스택 정상인데 유닛 failed») 유닛을 **다시 돌린다**. 시각도 맞는다(첫 실행 09:16 → 두 번째 09:21:02). 🔴 **확인은 이전 부팅 저널로만 된다** — 아래 «다음 창 확인».

🔵 **고칠 자리는 방아쇠와 무관하게 같다.** 리셋이 치우려는 것은 «지난 부팅에서 dockerd 가 되살린 컨테이너» 이고, 같은 부팅의 두 번째 실행에는 그런 것이 없다. 그래서 user-data 가 아니라 **리셋 자체**를 «한 부팅에 한 번» 으로 묶었다 — user-data 를 고치면 `user_data` 해시가 바뀌어 terraform plan 이 인스턴스를 건드리고, 다른 방아쇠(수동 `systemctl start` 등)는 여전히 열린다.

# 구현 결과 (2026-10-02 UTC)

- `infra/demo/demo-boot.sh` — 리셋 앞에 boot_id 검사. `/proc/sys/kernel/random/boot_id` 를 `/run/demo-boot-reset.boot-id`(tmpfs — 재부팅에 비워진다)에 적고, 같은 boot_id 면 «잔존 스택 정리 건너뜀 — 이 부팅에서 이미 했습니다» 를 말하고 `demo-up.sh` 로 간다. 정리의 성패와 무관하게 적는다. 마커를 못 쓰거나 boot_id 를 못 읽으면 **예전처럼 정리**하고 그렇게 말한다(B4 경합을 다시 여는 쪽보다 덜 나쁘다). 경로 둘(`DEMO_BOOT_RESET_MARKER` · `DEMO_BOOT_ID_SRC`)은 시험용 주입점.
  - 🔵 `systemctl restart` 는 영향받지 않는다 — 유닛의 `ExecStop=demo-down.sh` 가 먼저 전체 down 을 돈다.
- `infra/demo/verify-demo-wrapper.sh` (z24) — 기존 칸이 실제 `/run` 에 마커를 남기지 않도록 대역 경로 주입(안 하면 쓸 수 있는 호스트에서 (6) 매달림 칸이 «이미 정리함» 으로 건너뛰어 아무것도 안 잰다). 새 칸:
  - **(7)** 같은 boot_id 로 두 번 → 첫째 down 1회 + 마커에 boot_id 기록(주입 확인) · **둘째 down 0회** · up 은 둘 다 · 로그에 «이미 했습니다».
  - **(8)** 대조군 — boot_id 를 바꾸면 마커가 남아 있어도 **다시 정리**하고 마커를 새 값으로 갱신.

## 로컬 판정

- (z24) 단독(블록을 떼어 실행): 고친 판 **rc=0**. bite 셋:
  - 옛 `demo-boot.sh`(origin/main) → rc=1 `(7) 첫 기동이 리셋 마커에 boot_id 를 적지 않았습니다` (주입 칸이 문다)
  - 마커는 쓰되 건너뜀 제거(`reset_already_done=0`) → rc=1 `(7) 같은 부팅의 두 번째 기동이 잔존 정리(전체 down)를 다시 돌렸습니다`
  - boot_id 비교 제거(마커만 있으면 건너뜀) → rc=1 `(8) 다음 부팅(boot_id 변경)인데 잔존 정리가 안 돌았습니다`
- `verify-demo-wrapper.sh` 전체 **rc=0**, `ok` 91 · FAIL 0 (z13 예산 합 · z3/(n) 순서 칸 포함).
- ⚪ 실호스트(systemd·/run·실제 boot_id) 미측정 — 다음 창.

# census (AC-3, 2026-10-02 UTC)

«도중 중단 → 영구 기동 실패» 에는 두 조건이 함께 필요하다: ① DB 가 **MySQL**(DDL 비트랜잭션 — PostgreSQL 은 DDL 이 트랜잭션이라 중단되면 통째로 되돌아간다) ② 마이그레이션이 **재실행 불안전**(`IF NOT EXISTS` 없는 `CREATE TABLE`, `ALTER TABLE`, `CREATE INDEX`).

데모의 MySQL 프로젝트 = **erp · iam · finance**(`docker-compose.yml` 의 `image: mysql:8.0`; 나머지 wms·scm·ecommerce·fan 은 postgres). `src/main/resources/db/migration/V*__*.sql` 전수:

| 프로젝트 | 서비스 | 파일 | DDL 파일 | 재실행 불안전 |
|---|---|---:|---:|---:|
| erp | approval | 5 | 5 | 5 |
| erp | masterdata | 2 | 2 | 2 |
| erp | notification | 3 | 3 | 3 |
| erp | read-model | 4 | 4 | 4 |
| iam | account | 30 | 18 | 18 |
| iam | admin | 45 | 26 | 24 |
| iam | auth | 41 | 14 | 14 |
| iam | security | 11 | 10 | 8 |
| finance | account | 2 | 2 | 2 |
| finance | ledger | 15 | 15 | 15 |
| **합** | | **158** | | **95** |

- 술어: 줄 머리의 `CREATE TABLE`(뒤에 `IF NOT EXISTS` 없음) · `ALTER TABLE` · `CREATE [UNIQUE] INDEX` 가 하나라도 있으면 «불안전». 🔴 줄 머리만 보므로 한 줄에 여러 문장을 쓴 파일은 놓칠 수 있다(과소 계수 쪽).
- ⇒ **갈래 (b) 는 택하지 않는다.** 95 파일을 고치는 것은 범위로도 불가능하고, 이미 적용된 파일을 고치면 체크섬이 바뀌어 V2 가 성공한 다른 볼륨에서 검증 실패가 난다(Failure Scenario 1). 위험은 파일이 아니라 **«기동 중인 스택을 끊는 경로»** 쪽에서 닫는다.
- 🔴 **남는 끊김 경로**(이 티켓이 닫지 않음 — 기록만): 첫 부팅 마이그레이션 도중 방문자의 `/stop`(EC2 정지) · `/bundle/stop`. 둘 다 사람이 누르는 동작이고 첫 부팅 창(신선 볼륨, 전 서비스 마이그레이션)에서만 위험이 크다.

# 다음 창 확인 (AC-1 과 같은 창)

1. 복구 명령(위, 소유자) → `console-erp` `ready` · ERP 목록 (AC-1).
2. 방아쇠 가설: `journalctl --list-boots` 로 18차 첫 부팅(2026-10-02 09:1x)의 인덱스를 찾고 `journalctl -b <idx> -u demo-stack -u cloud-final --no-pager | grep -E 'Started|Finished|Failed|failed|Main process|systemctl'` — 첫 실행이 `failed` 로 끝나고 cloud-final 의 `systemctl start` 직후 두 번째 실행이 시작됐는지. 🔴 저널이 비영속이면(목록에 그 부팅이 없음) ⚪ «측정 불가 — 저널 비영속» 으로 닫는다; 고침은 방아쇠와 무관하므로 AC 에 영향 없음.
3. 이 고침은 **재굽기 뒤에만** 데모에 있다(`demo-boot.sh` 는 구워진 클론에서 돈다).

# 닫기 (2026-10-04 UTC) — 4차원 검증

| 차원 | 판정 |
|---|---|
| (a) | `gh pr view 4110` → `MERGED`, `ce3364931` |
| (b) | `ce3364931` 는 `origin/main` 의 조상 |
| (c) | #4110 `statusCheckRollup` 66 개 · FAILURE 0 |
| (d) | AC-2·3 `[x]`. **AC-1** — 19차 창 (2026-10-04 UTC · 인스턴스 i-08d452973ebf789be · AMI ami-00815e1f9614cda90 · 커밋 2a49dfb48): `console-erp` 가 `ready`(1차 기동 05:14Z · 2차 기동 06:20Z) · 콘솔 ERP 결재함 목록 3건(작성중 1 · 상신됨 2) · 내 미결함 2건 · 상세(결재선 1/1 · 이력) 정상(소유자 화면) |

🔴 **AC-1 의 괄호 «(복구 명령 실행 뒤)» 는 실행하지 않았다 — 필요가 없어졌기 때문이다.** 19차 apply 가 `aws_instance.demo` 를 **교체**해(`ami` force-new) 문제의 docker 볼륨(«테이블 있고 이력 없음») 이 인스턴스와 함께 사라졌다. 그래서 이 판정은 «복구가 됐다» 가 아니라 «**새 볼륨에서** 두 번 기동해도(05:04Z 켬 → 자동 정지 → 06:13Z 다시 켬) read-model 이 걸리지 않았다» 이다. 복구 명령은 옛 인스턴스(`i-05395a5a7baa23bb8`)를 가리키므로 더는 쓸 수 없다. 🔵 재발 방지(AC-2 의 boot_id 마커)가 실제로 두 번째 유닛 기동을 막았는지는 이번 창에서 저널로 확인하지 않았다 — 같은 증상이 다시 나면 그때의 출발점.
