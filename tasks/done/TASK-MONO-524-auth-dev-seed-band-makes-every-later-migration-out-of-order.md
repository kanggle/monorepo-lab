# Task ID

TASK-MONO-524

# Title

auth-service 의 dev 시드가 **V9000 대역**에 있어 이후의 모든 프로덕션 마이그레이션이 out-of-order 가 된다 — 기존 `auth_db` 를 가진 호스트는 어제부터 **크래시 루프**다

# Status

done

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

- [x] **AC-0 (재측정 — 착수 첫 작업)** — 위 표 세 줄을 **다시 잰다**: 각 서비스의
      `flyway_schema_history` 최고 적용 버전과 리포의 프로덕션 최고 버전.
      🔴 이 티켓의 숫자를 물려받지 말 것. `V0033` 이 이미 들어왔다면 범위가 달라진다.
      🔴 그리고 **CI 는 초록이라는 것**을 확인하고 적어라 — 그것이 이 결함이 지금까지
      살아남은 방식이고, "테스트가 잡아 줄 것" 이라는 가정을 깨는 근거다.
- [x] **AC-1 (방식 결정)** — 셋 중 하나를 고르고 **근거를 적는다**:
      (a) dev 시드를 `R__` 로 전환 — admin-service 의 선례이자 이 저장소의 다수 논증.
          🔴 다만 `V9001`/`V9002`/`V9006` 은 **이미 적용된 DB 가 있다** — 파일을 지우면
          Flyway 가 "applied but not resolved" 로 또 다른 경고/실패를 낸다. 그래서 이것은
          단순 rename 이 아니다. 실제로 어떻게 되는지 **띄워 보고** 적을 것.
      (b) `application-e2e.yml` 에만 `spring.flyway.out-of-order: true` — 프로덕션 프로파일은
          손대지 않는다. 가장 작지만, "순서가 의미를 갖지 않는다" 를 선언하는 것이기도 하다.
      (c) 시드를 프로덕션 타임라인에 편입(다음 번호로 부여) — dev/prod 분리를 포기하는 것.
- [x] **AC-2 (형제 파리티)** — 고른 방식을 `auth-service` 와 `account-service` **양쪽에**
      적용한다. 🔴 auth 만 고치면 `account_db` 가 `V0029` 가 들어오는 날 똑같이 죽는다.
- [x] **AC-3 (기존 DB 에서 실제로 뜬다)** — **볼륨을 지우지 않고** 판정한다.
      🔴 이것이 이 티켓의 본론이다. 신선 기동은 결함이 있을 때도 초록이므로 **아무것도
      증명하지 않는다.** 판정: 지금 깨져 있는 `auth_db` 볼륨(또는 같은 이력을 재현한
      볼륨) 위에서 `auth-service` 가 `healthy` 가 되고, `flyway_schema_history` 에
      `0032` 행이 생긴다.
      🔵 재현이 필요하면: 신선 DB 로 띄워 전체 적용 → `DELETE FROM flyway_schema_history
      WHERE version='0032'` → 재기동. 같은 예외가 나오면 재현된 것이다.
- [x] **AC-4 (복구 절차 문서)** — 이미 깨진 로컬 DB 를 가진 사람이 볼륨을 안 지우고
      복구하는 방법을 적는다(예: `flyway_schema_history` 손질). 🔴 "볼륨 지우세요" 만
      적으면 데모 데이터를 매번 잃는다 — 그 비용을 적어야 선택이 된다.
      `docs/guides/` 가 아니라 해당 시드 파일 헤더 또는 `projects/iam-platform/docs/` 에
      둔다(`docs/guides/` 는 AI 가 읽지 않는 인간용 참조다).
- [x] **AC-5 (회귀 가드)** — 새 `V9xxx` 파일이 `auth-service`/`account-service` 의
      `migration-dev` 에 추가되면 RED. 🔴 **가드가 무는지 확인**한다(더미 `V9007` 을
      넣어 RED, 지워서 GREEN). 🔴 그리고 그 가드가 **실제로 도는 레인**에 있는지 확인할 것 —
      `ci.yml` 의 `iam` 경로 필터는 `projects/iam-platform/**` 이다.
- [x] **AC-6 (안 하는 것도 산출물)** — `admin-service` 를 **건드리지 않는 이유**를 적는다
      (위 표). 안 적으면 다음 사람이 비대칭을 결함으로 읽고 조사를 반복한다.

---

# 실행 기록 (2026-08-12)

## AC-0 — ✅ 재측정했고, 이 티켓의 표를 **한 줄 정정한다**

기존 iam 볼륨 위에서 직접 읽었다(추론 아님):

```
auth_db    적용 최고 = 9002   (9001,9002)   production 최고 = V0032
account_db 적용 최고 = 9006   (9001…9006)   production 최고 = V0028
admin_db   적용 최고 =   45   (9xxx 0건)    production 최고 = V0045, repeatable 3건
```

CI 는 초록이다 — main tip `182bcd82b` 의 CI = success. 🔴 그리고 **초록일 수밖에 없다**:
CI 는 매번 빈 볼륨을 만들고, 빈 볼륨에서는 모든 버전이 오름차순으로 적용되어
out-of-order 가 성립하지 않는다. "테스트가 잡아 줬을 것" 은 이 결함에 대해 거짓이다.

🔴 **정정** — 이 티켓의 노출 표는 `admin-service` 를 *"`R__`(대역 없음)"* 이라 적었는데,
실제로는 **버전 있는 dev 시드가 셋 있다**(`V0014` · `V0023` · `V0028`). admin 이 안전한
이유는 `R__` 를 써서가 아니라 그 셋이 **production 시퀀스 안쪽의 예약된 공백**에 있고
production 이 이미 `V0045` 까지 왔기 때문이다. 대역이 아니라 **위치**가 차이다.
가드의 술어를 "V9xxx 금지" 가 아니라 "**production 최고 버전 이하**" 로 잡은 근거다.

## AC-1 — ✅ 방식 결정: **R__ 전환 + `out-of-order: true`(profile-scoped)**. 실측으로 골랐다

기존(오염된) `auth_db` 볼륨 위에서 후보를 실제로 띄웠다. `0032` 행을 지워 *"새 production
마이그레이션이 도착한다"* 를 재현한 상태다.

| | 조건 | 결과 |
|---|---|---|
| **A** | 2026-08-11 시점 코드 그대로 | 🔴 `Exited` — `Detected resolved migration not applied to database: 0032` |
| **B** | `V9xxx` 파일 제거(= R__ 전환 시뮬) | 🔴 `Exited` — **여전히 out-of-order**. 파일 없는 `9001/9002` 에 대해선 Flyway 가 아무 말도 안 했다 |
| **C** | `spring.flyway.out-of-order: true` | ✅ `healthy`, `0032` 적용됨 |
| **D** | B + `ignore-migration-patterns: "*:missing"` | 🔴 `9001/9002` 가 그제야 "applied migration not resolved locally" 로 표면화 |
| **E** | **실제 배포할 코드**(R__ 전환 완료) + `out-of-order: false` | 🔴 `Exited` — 같은 메시지 |

두 발견이 결정을 만들었다:

1. 🔴 **결함은 파일이 아니라 이력 테이블에 있다.** B 와 E 가 같은 말을 두 번 한다 —
   파일을 어떻게 바꾸든 **이미 9xxx 를 적용한 DB 는 안 낫는다.** 그래서 `R__` 전환만
   골랐다면 AC-3 을 통과하지 못했을 것이다. E 는 시뮬이 아니라 **머지할 코드 그대로**
   측정한 것이라, "R__ 이 고쳤다" 라고 잘못 귀속하는 것을 막는다.
2. 🔴 **없어진 versioned 마이그레이션은 조용히 용인되고, 없어진 repeatable 은 아니다.**
   B 에서 Flyway 는 `9001/9002` 를 `future`(해소된 모든 것보다 높은 버전)로 분류했고
   기본값 `ignore-migration-patterns: *:future` 가 그것을 무시했다. D 가 확증한다 —
   값을 `*:missing` 으로 주면 그 **기본값이 교체되어** 두 행이 즉시 드러난다.
   ⇒ **이미 적용된 `R__` 파일은 절대 rename 하지 말 것.** description 이 그 파일의
   정체성이라 rename 은 용인되지 않는 validate 실패가 된다. 이 비대칭 때문에
   `TASK-MONO-519` 가 만든 `R__seed_demo_second_operator_credential.sql` 은
   **이름을 그대로 뒀다**(순서상 마지막이고 독립적이라 무해하다).

세 후보 중 (a)를 고른 이유: 대역은 *충돌*을 막으려고 도입됐는데(`TASK-MONO-207`,
production `V0021` 과 globex `V0021` 충돌), `R__` 는 **버전이 아예 없어서** 충돌도
순서 위반도 불가능하고, 게다가 항상 versioned 뒤에 실행된다 — 시드 데이터가 원하는
순서 그 자체다. (b)만 고르면 대역이 남아 다음 사람이 또 밟는다. (c)는 이미 적용된
행이 그대로 남으므로 크래시를 다른 크래시로 바꿀 뿐이다.

`out-of-order: true` 는 **정책 변경이 아니라 호환 슬림**이고, 일몰 조건을 명시했다 —
유통 중인 DB 에 `version >= 9000` 행이 없어지면 no-op 이 되어 지울 수 있다.
`migration-dev` 를 로드하는 **유일한 프로파일**(`e2e`)에만 걸었다. `application.yml` 과
production 은 `db/migration` 만 보므로 9xxx 행을 가질 수 없고, 순서는 여전히 불변식이다.

## AC-2 — ✅ 형제 파리티: 두 서비스 모두

`account-service` 는 **안 깨져 있었다.** production 이 `V0028` 이고 그게 이미 적용돼
있어 대기 중인 게 없었을 뿐, `V0029` 가 들어오는 날 같은 자리다 — `auth_db` 가
2026-08-10 에 있던 바로 그 위치다. 깨진 쪽만 고치면 형제의 장애를 **예약**하는 것이다.

| 서비스 | 변경 | 결과 |
|---|---|---|
| `auth-service` | `V9001/V9002` → `R__01/R__02` + `out-of-order` | ✅ |
| `account-service` | `V9001…V9006` → `R__01…R__06` + `out-of-order` | ✅ |
| `admin-service` | 없음 (AC-6) | ✅ |

8개 시드는 전부 `INSERT IGNORE` 뿐이라 checksum 변경 시 재실행돼도 무해하다(전수 확인).
`NN_` 접두는 **순서 계약**이다 — repeatable 은 description 순으로 돌고
`account-service` 의 `R__06` 은 `R__05` 가 심는 테넌트에 의존한다.

## AC-3 — ✅ **볼륨을 지우지 않고** 판정했다

두 DB 에 각각 오염을 재현한 뒤(auth `0032` 행 삭제, account `0025` 행 삭제 —
`V0025` 는 `INSERT IGNORE` 라 재실행이 무해함을 확인하고 골랐다) 새 이미지로 기동:

```
auth=running/healthy   account=running/healthy
auth_0032 적용=1   auth repeatables = "seed demo second operator credential |
                                       01 seed demo single identity credentials |
                                       02 seed fan artist credentials"
account_0025 적용=1  account repeatables=6
9xxx 행은 양쪽 모두 그대로 남아 있다(= future 로 무시됨)
데모 데이터 보존 확인: 자격증명 4건 · demo-corp/globex-corp 테넌트 2건
```

🔴 이미지 재빌드 후 **jar 안에 V9xxx 가 없는지 직접 확인**했다(`unzip -l | grep
migration-dev`) — 지난 세션에 `bootJar` 를 안 돌리고 이미지를 만들어 옛 마이그레이션이
그대로 적용된 적이 있다.

## AC-4 — ✅ 복구 절차

`projects/iam-platform/docs/flyway-dev-seed-migrations.md` § 6. 핵심은 **볼륨을 지울
필요가 없다**는 것이다 — 이 변경이 머지되면 기존 DB 는 스스로 뜨고, 이력을 진짜로
깨끗하게 만들고 싶으면 `DELETE FROM flyway_schema_history WHERE CAST(version AS
UNSIGNED) >= 9000` 한 줄이면 된다(가리키는 시드가 이제 전부 재적용되는 `R__` 이고
전부 `INSERT IGNORE` 라 안전하다). "볼륨 지우세요" 는 매번 데모 데이터를 잃는다.

## AC-5 — ✅ 가드 + **네거티브 5건 실측**

`scripts/check-dev-seed-migration-band.sh`, `.github/workflows/ci.yml` 의
`dev-seed-migration-band` 잡. 술어: `<svc>/db/migration-dev/V<n>__*.sql` 의 `n` 은
형제 `<svc>/db/migration/` 최고 버전 **이하**여야 한다.

| 네거티브 | 결과 |
|---|---|
| `auth-service/migration-dev` 에 새 `V9007__`, **스테이징 안 함** | 🔴 RED |
| `Vabc__` (버전 파싱 불가) | 🔴 RED — 건너뛰지 않고 fail-closed |
| `migration-dev` 가 0개인 저장소 | 🔴 RED — **0건은 통과가 아니라 계측 실패** |
| `V0030__` (production 범위 안쪽) | ✅ GREEN — 술어가 "dev 시드 금지" 가 아니라 **범위**임을 증명 |
| 대조군(아무것도 안 함) | ✅ GREEN |

🔴 **도달성**을 위해 `git ls-files --cached --others --exclude-standard` 를 쓴다. 순수
`git ls-files` 면 작성자가 `git add` 하기 전 로컬 실행이 **아무 의미 없는 초록**이 된다
(CI 는 체크아웃 후 전부 tracked 라 안 걸린다 — 로컬만 거짓말한다).

🔴 **예외 목록이 없다.** admin 의 `V0014/V0023/V0028` 은 면제가 아니라 **측정으로**
통과한다(≤ V0045). 면제 목록이었다면 admin 이 실제로 왜 안전한지가 기록되지 않았을 것이다.

## AC-6 — ✅ `admin-service` 를 안 건드리는 이유

`projects/iam-platform/docs/flyway-dev-seed-migrations.md` § 5 + 가드 스크립트 헤더.
요지: admin 의 versioned dev 시드는 **production 시퀀스 안쪽 예약 공백**에 있고
production 이 `V0045` 까지 왔다. production 워터마크 **아래**에 있는 dev 시드는 이후의
production 마이그레이션을 out-of-order 로 만들 수 없다. 대역은 만든다. 그게 전부의 차이고,
가드가 그 차이를 **술어 자체로** 담고 있어 admin 은 예외 없이 통과한다.

🔵 그리고 admin 의 `R__seed_demo_operator.sql` 헤더는 이 사고가 나기 전에 이미
*"a dev seed placed in a high band … would break every developer's existing local
database the day someone adds V0046"* 라고 **정확히 예측해 뒀다**. 예측은 맞았고,
값을 치른 것은 그 문장을 안 읽은 형제 서비스였다.

## 🔴 이름이 바뀐 파일의 소비자 — 12개 파일을 고쳤다

`git mv` 는 참조를 안 고친다. `V900x` 전수 grep 으로 잡았고, 그중 **5곳은 실제로 깨졌을
것**이다(문자열 경로):

- `auth-service/build.gradle` — `inputs.file(... V9006 ...)`. 🔴 `bootJar` 는 통과하고
  `test` 에서 죽는다. 빌드 성공을 검증으로 오독하기 쉬운 자리다.
- `DemoSeedCredentialTest` · `DemoSecondOperatorSeedTest` · `FanArtistDemoSeedTest`
  (auth) · `FanArtistRoleSeedIntegrationTest` (account) — 전부 파일명을 상수로 들고 있다.

나머지는 산문이지만 파일을 **찾아가게 만드는** 참조라 함께 고쳤다: federation-e2e 스펙
3개 + `fixtures/seed.sql`, `seed-fan.sh`, `interview-demo-walkthrough.md` § 6 2행,
`DemoOperatorSeedIntegrationTest` javadoc.

🔴 그 과정에서 **별개의 스테일 참조**를 하나 찾았다 — `seed-erp.sh` 가 실패 메시지에서
*"auth `V9003` 시드가 적용됐는지 확인하십시오"* 라고 안내하는데, **`V9003` 은 존재한
적이 없다.** `TASK-MONO-519` 가 그 자격증명을 처음부터 `R__` 로 만들었기 때문이다
(바로 이 티켓을 근거로 대역을 거부한 기록이 그 파일 헤더에 있다). 운영자를 없는 파일로
보내는 메시지였다. 실제 파일명으로 고쳤다.

🔵 **안 고친 것도 적어 둔다**: `tasks/done/**` 의 V9xxx 언급(작업 기록이므로 당시가 맞다),
`docs/adr/ADR-MONO-034`(ADR 의 결정 본문은 사후 수정 대상이 아니다), 각 시드 파일 안쪽의
역사 서술("`V0021` → `V9001` 로 renumber 했다" 등 — 최상단 헤더가 개명 사실을 먼저 알린다).

## 🔵 부수 수정 1건 (원래 범위 밖 — 명시해 둔다)

이 티켓의 검증으로 영향 가드를 전부 돌리다가 `check-walkthrough-ledger-drift.sh`
(TASK-MONO-518)가 **로컬에서만** RED 인 것을 발견했다:

```
DRIFT: exception '' in scripts/walkthrough-ledger-exceptions.txt carries no reason.
```

원인은 예외 파일이 아니라 **줄끝**이다. 이 저장소의 `.gitattributes` 는 `*.sh` 와
`*.sql` 에만 LF 를 강제하고 `*.txt` 는 안 덮는다 ⇒ `core.autocrlf=true` 인 Windows
클론에서 그 파일이 CRLF 로 체크아웃되고, 빈 줄이 `"
"` 로 도착해 `''` 케이스를
빠져나가 "사유 없는 예외" 로 오판된다. 저장된 blob 은 LF 라 **CI 는 영원히 초록**이다
(main tip 에서 확인) — 즉 이 가드는 *푸시 전에 돌려 보는 사람*, 곧 그 가드의 대상
독자에게만 RED 였다. 한 줄(`line="${line%$'
'}"`)로 고쳤고, 사유 필수 네거티브가
여전히 무는지 확인했다(사유 없는 예외 한 줄 추가 → RED, 제거 → GREEN).

🔴 `.gitattributes` 를 고치지 않고 **스크립트**를 고른 이유: 전자는 재체크아웃이 있어야
효과가 나고 클론 설정에 의존하지만, 후자는 어떤 체크아웃에서도 옳다.

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

- [x] AC-0 ~ AC-6 충족
- [x] `auth-service` · `account-service` 가 **기존 볼륨** 위에서 healthy
- [x] iam 테스트 GREEN
