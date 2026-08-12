# Task ID

TASK-MONO-524

# Title

auth-service 의 dev 시드가 **V9000 대역**에 있어 이후의 모든 프로덕션 마이그레이션이 out-of-order 가 된다 — 기존 `auth_db` 를 가진 호스트는 어제부터 **크래시 루프**다

# Status

ready

# Owner

monorepo

# Task Tags

- bug
- iam
- data
- demo

---

# 배경 — 다른 티켓을 실측하다 밟았다

`TASK-MONO-519` 의 라이브 검증을 하려고 기존 iam 볼륨 위에 데모 스택을 띄웠더니
`auth-service` 가 **기동하지 못하고 종료**했다. 이 티켓의 변경분과 무관한 지점이었다.

## 실측 (2026-08-12, 이 저장소의 데모 호스트)

```
iam-auth-service-1   Exited (1)

FlywayValidateException: Validate failed: Migrations have failed validation
  Detected resolved migration not applied to database: 0032.
```

`auth_db.flyway_schema_history` 를 직접 읽었다 — 추론이 아니다:

```
9001  V9001__seed_demo_single_identity_credentials.sql   2026-08-05 02:41:08   ← 최고 적용
0031  V0031__add_fan_web_demo_host_redirect_uri.sql      2026-08-05 02:41:07
0030  V0030__grant_fan_platform_client_resource_scopes.sql
...
적용된 최고 version = 9001
version='0032' 인 행 = 0건
```

그리고 `V0032` 는 **어제 들어왔다**:

```
0414d4d03  2026-08-11  feat(fan): artists.account_id + fail-closed follow-target
                       validation (TASK-FAN-BE-045) (#3270)
  → projects/iam-platform/apps/auth-service/src/main/resources/db/migration/
    V0032__grant_community_client_artist_read_scope.sql
```

⇒ dev 시드가 **9001** 로 적용돼 있으므로, 그 뒤에 추가되는 **모든** 프로덕션
마이그레이션(`V0032`, 앞으로의 `V0033`…)은 최고 적용 버전 **아래**로 해소된다 =
out-of-order. 이 저장소 어디에도 `out-of-order: true` 설정은 없다(전수 확인) ⇒
Flyway 가 기본값대로 거부하고, Spring 은 `flywayInitializer` 빈 생성 실패로 죽는다.
`V9002`(`TASK-MONO-512` 의 아티스트 자격증명)도 그래서 **한 번도 적용되지 못했다** —
validate 가 migrate 앞에서 끊는다.

## 🔴 이 저장소는 이 함정을 **이미 문서화해 뒀다** — 형제만 안 따라왔다

`admin-service` 의 `db/migration-dev/R__seed_demo_operator.sql` 헤더가 그대로 적고 있다:

> a dev seed placed in a high band (the V9000+ trick account-service uses) would be
> applied at, say, V9001, and then the NEXT production migration (V0046) would be
> resolved BELOW the highest applied version — an out-of-order migration, which
> Flyway rejects by default … That would not break CI or the demo; it would break
> **every developer's existing local database** the day someone adds V0046.

admin-service 는 그래서 `R__`(repeatable)을 골랐다. **auth-service 는
`TASK-BE-571` 에서 그 대역을 그대로 채택했고, 어제 그 날이 왔다.**

🔴 인용문의 *"CI 나 데모를 깨지 않는다"* 는 절반만 맞다. 볼륨을 새로 만드는 CI 와
신선 기동은 멀쩡하다 — 그래서 **초록인 채로 이 결함이 존재한다**. 깨지는 것은 기존
DB 를 가진 개발자와, 볼륨을 보존하는 데모 호스트다.

## 노출 범위 — 세 서비스를 각각 세어야 한다

| 서비스 | dev 시드 대역 | 프로덕션 최고 | 지금 상태 |
|---|---|---|---|
| `auth-service` | **V9001·V9002** | `V0032` | 🔴 **터짐** (실측) |
| `account-service` | **V9001~V9006** | `V0028` | 🟡 **다음 마이그레이션에 터진다** (적용 최고 9006) |
| `admin-service` | `R__` (대역 없음) | `V0045` | ✅ 구조적으로 안전 |

🔵 `account-service` 는 아직 안 터졌을 뿐이다. `V0029` 를 추가하는 순간 같은 자리다 —
**하나만 고치고 닫으면 형제 파리티 낙오를 또 만든다**(이 저장소가 `@EnableScheduling`
과 봉투 필드에서 두 번 밟은 그것).

---

# Goal

기존 `auth_db` / `account_db` 를 가진 호스트에서 서비스가 **뜬다.** 그리고 앞으로의
프로덕션 마이그레이션이 dev 시드 때문에 out-of-order 가 되는 구조를 **없앤다.**

# Scope

## In Scope

- `auth-service` · `account-service` 의 `db/migration-dev` 시드 배치 방식
- 필요하다면 Flyway 설정(`out-of-order`)의 **프로파일 한정** 적용
- 이미 깨진 DB 의 복구 절차를 문서로 남기기
- 회귀 가드: 새 dev 시드가 다시 V9000 대역으로 들어오는 것을 잡는다

## Out of Scope

- `TASK-MONO-519` 가 추가한 `R__seed_demo_second_operator_credential.sql` — **이미 R__ 다.**
  그 파일 헤더가 이 티켓을 근거로 대역을 거부한 기록을 갖고 있다
- `admin-service` — 구조적으로 안전(위 표)
- 프로덕션 경로. `migration-dev` 는 `e2e` 프로파일에서만 로드된다

---

# Acceptance Criteria

- [ ] **AC-0 (재측정 — 착수 첫 작업)** — 위 표 세 줄을 **다시 잰다**: 각 서비스의
      `flyway_schema_history` 최고 적용 버전과 리포의 프로덕션 최고 버전.
      🔴 이 티켓의 숫자를 물려받지 말 것. `V0033` 이 이미 들어왔다면 범위가 달라진다.
      🔴 그리고 **CI 는 초록이라는 것**을 확인하고 적어라 — 그것이 이 결함이 지금까지
      살아남은 방식이고, "테스트가 잡아 줄 것" 이라는 가정을 깨는 근거다.
- [ ] **AC-1 (방식 결정)** — 셋 중 하나를 고르고 **근거를 적는다**:
      (a) dev 시드를 `R__` 로 전환 — admin-service 의 선례이자 이 저장소의 다수 논증.
          🔴 다만 `V9001`/`V9002`/`V9006` 은 **이미 적용된 DB 가 있다** — 파일을 지우면
          Flyway 가 "applied but not resolved" 로 또 다른 경고/실패를 낸다. 그래서 이것은
          단순 rename 이 아니다. 실제로 어떻게 되는지 **띄워 보고** 적을 것.
      (b) `application-e2e.yml` 에만 `spring.flyway.out-of-order: true` — 프로덕션 프로파일은
          손대지 않는다. 가장 작지만, "순서가 의미를 갖지 않는다" 를 선언하는 것이기도 하다.
      (c) 시드를 프로덕션 타임라인에 편입(다음 번호로 부여) — dev/prod 분리를 포기하는 것.
- [ ] **AC-2 (형제 파리티)** — 고른 방식을 `auth-service` 와 `account-service` **양쪽에**
      적용한다. 🔴 auth 만 고치면 `account_db` 가 `V0029` 가 들어오는 날 똑같이 죽는다.
- [ ] **AC-3 (기존 DB 에서 실제로 뜬다)** — **볼륨을 지우지 않고** 판정한다.
      🔴 이것이 이 티켓의 본론이다. 신선 기동은 결함이 있을 때도 초록이므로 **아무것도
      증명하지 않는다.** 판정: 지금 깨져 있는 `auth_db` 볼륨(또는 같은 이력을 재현한
      볼륨) 위에서 `auth-service` 가 `healthy` 가 되고, `flyway_schema_history` 에
      `0032` 행이 생긴다.
      🔵 재현이 필요하면: 신선 DB 로 띄워 전체 적용 → `DELETE FROM flyway_schema_history
      WHERE version='0032'` → 재기동. 같은 예외가 나오면 재현된 것이다.
- [ ] **AC-4 (복구 절차 문서)** — 이미 깨진 로컬 DB 를 가진 사람이 볼륨을 안 지우고
      복구하는 방법을 적는다(예: `flyway_schema_history` 손질). 🔴 "볼륨 지우세요" 만
      적으면 데모 데이터를 매번 잃는다 — 그 비용을 적어야 선택이 된다.
      `docs/guides/` 가 아니라 해당 시드 파일 헤더 또는 `projects/iam-platform/docs/` 에
      둔다(`docs/guides/` 는 AI 가 읽지 않는 인간용 참조다).
- [ ] **AC-5 (회귀 가드)** — 새 `V9xxx` 파일이 `auth-service`/`account-service` 의
      `migration-dev` 에 추가되면 RED. 🔴 **가드가 무는지 확인**한다(더미 `V9007` 을
      넣어 RED, 지워서 GREEN). 🔴 그리고 그 가드가 **실제로 도는 레인**에 있는지 확인할 것 —
      `ci.yml` 의 `iam` 경로 필터는 `projects/iam-platform/**` 이다.
- [ ] **AC-6 (안 하는 것도 산출물)** — `admin-service` 를 **건드리지 않는 이유**를 적는다
      (위 표). 안 적으면 다음 사람이 비대칭을 결함으로 읽고 조사를 반복한다.

# Related Specs

- `projects/iam-platform/specs/services/auth-service/architecture.md`
- `projects/iam-platform/specs/services/account-service/data-model.md`

# Related Contracts

- 없음 (배포/마이그레이션 층)

# Edge Cases

- **`R__` 로 전환하면서 `V9001` 파일을 지우는 경우** — 그 DB 에는 9001 이 applied 로
  남는데 resolve 되는 파일이 없다. Flyway 는 기본적으로 이것을 경고로 넘기지만
  (`ignoreMissingMigrations` 계열), 버전에 따라 실패한다. **가정하지 말고 띄워서 볼 것.**
- **`out-of-order: true` 를 고르는 경우** — 그 프로파일에서는 마이그레이션 순서가 더 이상
  불변식이 아니다. dev/e2e 에만 걸어도, dev 에서만 통과하고 prod 에서 막히는 새 갈래가 생긴다.
- **`account-service` 는 `V9001~V9006` 이 서로를 참조한다**(V9006 이 V9005 의 테넌트를 쓴다).
  대역을 바꾸면 그 순서 의존을 확인해야 한다.
- **CI 는 신선 볼륨이라 이 결함에 대해 영구히 초록이다** — 가드를 런타임이 아니라
  **파일 배치**에 걸어야 하는 이유(AC-5).

# Failure Scenarios

- **auth 만 고치고 닫음** → `account_db` 가 다음 마이그레이션 날 같은 예외로 죽는다. AC-2.
- **신선 볼륨으로 판정** → 결함이 있어도 초록이다. 이 결함이 지금까지 살아남은 방식 그 자체. AC-3.
- **`V9001` 을 rename/renumber 로 "정리"** → 체크섬이 바뀌어 이미 적용한 DB 에서
  validate 가 또 실패한다. 하나의 크래시를 다른 크래시로 바꾸는 것.
- **증상 오진** — 이 실패는 설정 오류가 아니라 **컨테이너 Exited(1)** 로 나타나고,
  게이트웨이 쪽에서는 로그인 불가로 보인다. 원인이 마이그레이션 순서라는 신호가 없다.

# Definition of Done

- [ ] AC-0 ~ AC-6 충족
- [ ] `auth-service` · `account-service` 가 **기존 볼륨** 위에서 healthy
- [ ] iam 테스트 GREEN
