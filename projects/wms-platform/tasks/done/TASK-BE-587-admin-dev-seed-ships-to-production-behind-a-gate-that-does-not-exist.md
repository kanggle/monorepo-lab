# Task ID

TASK-BE-587

# Title

`admin-service` 의 dev 시드가 **존재하지 않는 게이트 뒤에서 전 환경에 적용된다** — 주석이 있다고 적은 location filter 가 없다

# Status

done

# Owner

wms-platform

# Task Tags

- bug
- migration
- security

---

# 배경 — `TASK-BE-585` AC-0 이 발굴

`TASK-BE-585` 가 `V99__seed_dev_data.sql` 을 `R__` 로 바꾸는 동안, 그 파일 헤더가 적고 있던
주장이 사실인지 확인했다:

> *"Production deployments must run a separate manual procedure … V99 is safe to skip in prod
> because Flyway numbers ascending and **the prod migration profile gates this file out via
> callback / location filter at the platform level**."*

## 실측 (2026-08-14) — 그런 게이트는 없다

```
admin-service 의 yml 전수          application.yml 하나뿐 (프로파일 변형 0개)
  spring.flyway.locations          classpath:db/migration      ← 항상 켜지는 위치
  callback / location filter        0건 (저장소 전체 grep)
infra/demo/wms-devseed.override.yml  master · inbound · inventory · outbound  ← admin 없음
```

형제 4개는 시드를 `db/seed/` 에 두고 **오버레이가 열어 줄 때만** 적용한다. admin 만 시드가
**프로덕션 마이그레이션 위치 안**에 있고, 그것을 걸러내는 장치가 어디에도 없다.

⇒ 이 시드는 **모든 환경에서 적용된다.** 심는 것:

| 테이블 | 내용 |
|---|---|
| `admin_role` | 내장 역할 4개(`WMS_VIEWER`/`OPERATOR`/`ADMIN`/`SUPERADMIN`) |
| `admin_user` | `admin@wms.internal` — `USR-0001` |
| `admin_user_role_assignment` | 위 사용자에게 **`WMS_SUPERADMIN` 전역 부여** |
| `admin_setting` | 기본 설정 4건 |

🔴 **프로덕션에 부트스트랩 SUPERADMIN 계정이 고정 UUID 로 심긴다.** 시드 UUID 는 파일이
직접 밝히듯 *"stable so dev tools / e2e fixtures can refer to them"* 이라 **공개된 값**이다.

## 🔴 그런데 단순히 지울 수 없다 — 이것이 유일한 출처다

```
admin_role 을 INSERT 하는 파일 전수 = 1개 (이 시드)
```

⇒ 시드를 프로덕션 위치에서 걷어내면 그 환경에는 **내장 역할이 하나도 없다.** `WMS_VIEWER` 는
`admin-service` 의 8개 대시보드 인가 술어가 요구하는 역할이므로, 대체 provisioning 경로 없이
제거하면 읽기 표면이 전부 닫힌다. **그래서 이 티켓은 "지운다" 가 아니라 "무엇이 맞는지 정한다" 다.**

🔵 `TASK-BE-585` 는 이 파일을 `R__` 로 바꿔 **순서 결함만** 고쳤다 — 적용 범위는 **바꾸지 않았다**
(현상 유지). 범위를 바꾸는 것은 프로덕션 provisioning 을 바꾸는 결정이라 분리했다.

---

# Goal

`admin-service` 가 프로덕션에서 내장 역할·부트스트랩 계정을 **어떻게** 갖추는지가 결정되고,
파일의 주석과 실제 설정이 일치한다.

---

# Scope

## In Scope

- **AC-0 실측**: 이 시드가 심는 4개 테이블 각각에 대해 *프로덕션에 필요한 것* vs *dev 편의*를
  가른다. 🔴 역할(필요)과 부트스트랩 사용자·설정(편의)은 답이 다를 수 있다
- **AC-1 결정**. 후보:
  - (A) **분할** — 내장 역할·기본 설정은 정식 프로덕션 마이그레이션(`V4__builtin_roles.sql`)으로
    승격하고, 부트스트랩 **사용자 + SUPERADMIN 부여**만 dev 전용(`db/seed/` + 오버레이)으로 내린다
  - (B) 전체를 dev 전용으로 내리고 프로덕션 provisioning 절차를 문서화한다(주석이 원래 주장하던 것)
  - (C) 현상 유지 + **주석을 사실로 고친다** — 이 배치가 의도된 것이라면 그렇게 적는다
- 결정에 따른 구현 + `specs/services/admin-service/domain-model.md § Reference Data Snapshot` 정합

## Out of Scope

- 마이그레이션 **순서** 문제 — `TASK-BE-585` 가 닫았다
- 형제 4개의 `db/seed/` 밴드 — **`TASK-MONO-531`** (2026-08-15 닫힘: 8개 전부 `R__` 전환).
  🔴 이 줄은 원래 존재하지 않는 `TASK-BE-588` 을 가리키고 있었다 — 그 밴드는 공유 가드
  (`scripts/check-dev-seed-migration-band.sh`)를 함께 고쳐야 해서 root 티켓으로 갔다

---

# Acceptance Criteria

- [x] **AC-0 (실측)** — 4개 테이블 × (프로덕션 필수인가 / 무엇이 그것을 요구하는가). 🔴 `WMS_VIEWER`
      를 요구하는 인가 술어 전수를 함께 센다 — 역할을 내리면 무엇이 닫히는지가 결정의 입력이다
- [x] **AC-1 (결정 + 구현)** — A/B/C 중 하나 + 근거. **파일의 주석이 실제 설정과 일치**하는 것이
      통과 조건이다(지금은 정면으로 어긋나 있다)
- [x] **AC-2 (가드)** — 결정이 코드에서 유지되는지. 🔴 A 를 고르면 "역할은 있고 부트스트랩 사용자는
      없는" 프로덕션 형상을 실제로 재현해 대시보드가 열리는지 확인한다(대조군 없는 통과 금지)
- [x] **AC-3 (기존 DB)** — 어떤 결정이든 이미 이 시드를 적용한 DB 가 깨지지 않는다.
      🔴 `R__` 는 **rename 하면 validate 가 실패**한다(`TASK-MONO-524` § finding 2) — 파일을
      쪼개거나 옮길 때 이 비대칭을 반드시 고려할 것

# Related Specs

- `projects/wms-platform/tasks/ready/TASK-BE-585-*.md` — 출처(순서 결함을 고치며 발굴)
- `projects/wms-platform/specs/services/admin-service/domain-model.md` § Reference Data Snapshot · § 4 Seed Settings
- `projects/wms-platform/specs/services/admin-service/architecture.md` § Security § Roles

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § Authorization mapping

# Edge Cases

- 🔴 `R__seed_dev_data.sql` 은 이미 적용된 repeatable 이다 — **파일명을 바꾸면** Flyway 가
  *"applied migration not resolved locally"* 로 거부한다. 분할하려면 새 파일을 **추가**하고
  기존 파일은 이름을 유지한 채 내용을 줄이는 편이 안전하다
  - 🔴🔴 **이름을 유지해도 마찬가지다**(실측 § AC-3). 거부 조건은 "이름이 바뀌었나" 가 아니라
    **"지금도 resolve 되는가"** 이고, location 목록에서 빠지는 것만으로 같은 예외가 난다.
    이 티켓은 그것을 **덮지 않고** 핀 + 한 문장 복구로 처리했다
- 고정 UUID 는 e2e 픽스처가 참조한다 — 값을 바꾸면 그쪽이 깨진다

# Failure Scenarios

- **주석만 고치고 배치를 안 본다** → 프로덕션에 공개 UUID 의 SUPERADMIN 이 그대로 남는다
- ~~**시드를 통째로 내린다** → 내장 역할이 사라져 대시보드 8개가 전부 닫힌다~~
  **틀렸다** — 인가는 JWT 에서 오고 `admin_role` 을 읽지 않는다(실측 § AC-0). 이 시나리오는
  일어나지 않으며, 그것을 확인한 것이 AC-0 의 실제 산물이다
- **`R__` 파일을 rename 해서 분할** → 기존 DB 전부 validate 실패

# Definition of Done

- [x] AC-0 ~ AC-3 전부
- [x] 주석과 설정이 일치
- [x] Ready for review

---

# 실측 (2026-08-15)

## AC-0 — 🔴🔴 이 티켓의 **핵심 전제가 틀렸다**. 역할을 내려도 아무것도 안 닫힌다

티켓은 *"`WMS_VIEWER` 는 8개 대시보드 인가 술어가 요구하는 역할이므로, 대체 provisioning
경로 없이 제거하면 읽기 표면이 전부 닫힌다"* 고 적었고, Failure Scenario 에도
*"시드를 통째로 내린다 → 대시보드 8개가 전부 닫힌다"* 를 넣었다. **둘 다 거짓이다.**

인가 권한은 **JWT 에서만** 온다:

```
SecurityConfig  JwtGrantedAuthoritiesConverter(prefix=ROLE_) + role claim
                + entitled_domains 에 wms 가 있으면 ROLE_WMS_VIEWER 합성 (L112-140)
                + RoleHierarchy  SUPERADMIN > ADMIN > OPERATOR > VIEWER
@PreAuthorize   역할 **문자열**을 직접 지명
```

`admin_role` 을 인가에서 읽는 코드는 **0건**이다. `permissions_json` 은 역할 CRUD 응답
(`RoleResponse`)으로 되돌려주는 것 외에 어디서도 소비되지 않는다. DB 를 읽는 유일한 지점은
`AssignmentService.createAssignment` 의 `roleRepository.findById` **하나**다.

| 테이블 | 프로덕션에 필수인가 | 요구하는 것 |
|---|---|---|
| `admin_role` | **아니다** — 인가는 JWT | 배정 생성(`AssignmentService`) · 역할 관리 화면 |
| `admin_user` | 아니다 | 사용자 관리 화면 · 배정 |
| `admin_user_role_assignment` | 아니다 | 배정 화면 |
| `admin_setting` | 아니다(런타임 소비자 0건 — `SettingsService` CRUD 뿐, 부재 시 404) | 설정 화면 |

⇒ 네 테이블 전부 **관리 데이터**이지 보안 기반이 아니다. 티켓이 "지울 수 없다" 고 판단한
근거가 사라지므로 선택지가 달라진다.

🔴 그리고 `architecture.md` 도 같은 오독을 유발하고 있었다 — *"Role-to-permission mapping
stored in `admin_role.permissions_json`"*. **저장은 맞고 강제는 아니다.** 그 문장 아래에
실측을 적었다(결정이 이 전제 위에 서 있으므로, 바꾸려는 사람이 먼저 보는 자리에 둔다).

## AC-1 — **B 를 골랐다**(전부 dev 전용으로 내리고 위치가 게이트가 된다)

| 후보 | 판정 |
|---|---|
| A 분할(역할·설정을 프로덕션 마이그레이션으로 승격) | ✗ AC-0 이 근거를 없앴다 — 프로덕션이 그 행을 **필요로 하지 않는다**. 승격하면 아무도 안 읽는 데이터를 스펙에 고정하게 된다 |
| **B 전부 dev 전용 + 프로덕션 provisioning 문서화** | ✅ **스펙이 이미 그렇게 적고 있었다** — `domain-model.md § Reference Data Snapshot` 은 처음부터 *"profile `dev` or `standalone`"* 이라고 선언했다. 즉 이것은 새 결정이 아니라 **코드를 스펙에 맞추는 일**이다(그래서 ADR 게이트 대상이 아니다) |
| C 현상 유지 + 주석만 사실로 | ✗ 공개 UUID 의 전역 `WMS_SUPERADMIN` 이 프로덕션에 남는다 |

**구현**: `db/migration/R__seed_dev_data.sql` → `db/seed/R__seed_dev_data.sql`(**이름 유지**,
내용 무변경) + `application-dev.yml` 신설 + 데모 오버레이에 `SPRING_FLYWAY_LOCATIONS` 추가.
이제 **위치가 게이트**이고 그것을 여는 것은 정확히 둘뿐이다. 파일 헤더가 주장하던 문장이
비로소 참이 됐다.

## AC-2 — 대조군 없는 통과를 막았다

`DevSeedScopeIT` (admin-service, **4칸 전부 통과**) — 변수는 **location 목록 하나**뿐이다:

| 칸 | locations | 결과 |
|---|---|---|
| 프로덕션 | `db/migration` | 4개 테이블 **전부 0행** + `admin_order_summary.tenant_id`·`admin_shipment_summary.tenant_id` **존재**(대시보드가 읽는 스키마는 온전) |
| **대조군** | `db/migration,db/seed` | 역할 4 · 사용자 1 · 배정 1 · 설정 4 |
| 기존 DB | 아래 AC-3 | — |
| 데모 재적용 | `db/migration,db/seed` ×2 | 중복 없음 |

앞의 두 칸이 짝이다 — "프로덕션이 아무것도 안 심는다" 가 *다른 이유로* 0 을 낸 것이 아님을
같은 코드 경로로 보인다.

## AC-3 — 🔴🔴 **내가 틀렸고 테스트가 잡았다**

`R__` 를 **rename 하지 않고 디렉터리만 옮기면** 이력 행이 바이트 단위로 동일하므로
(repeatable 의 정체성은 description, `script` 는 location 기준 상대경로) 투명할 것이라고
추론했다. **전부 맞는 말이고, 도움이 되지 않았다.** Flyway 가 묻는 것은 "이름이 바뀌었나" 가
아니라 **"적용된 마이그레이션이 지금도 resolve 되는가"** 다:

```
FlywayValidateException: Detected applied migration not resolved locally: seed dev data.
```

**덮지 않고 핀으로 박았다.** 덮는 방법은 `application.yml` 에
`ignore-migration-patterns: repeatable:missing` 인데, 그러면 **앞으로 삭제되는 모든
repeatable 을 프로덕션이 영구히 묵인**한다 — 애초에 게이트가 없어서 생긴 DB 하나를 봐주려고
진짜 검사를 영구 손실하는 거래다.

**영향 범위 실측**: dev 는 `application-dev.yml`, 데모는 `wms-devseed.override.yml` 로 열고
그 오버레이는 `infra/demo/projects.sh` 의 wms 스택 목록에 **무조건 포함**되어 있다 ⇒ 양쪽 다
계속 resolve 된다. 남는 것은 **손으로 `docker compose` 를 부른 기존 볼륨** 하나다.
복구는 한 문장이고, 그 DB 는 공개 UUID SUPERADMIN 도 아직 들고 있으므로 **의도적으로 한 번
거치는 편이 낫다**:

```sql
DELETE FROM flyway_schema_history WHERE version IS NULL AND description = 'seed dev data';
```

IT 가 이 전부를 재현한다 — 예외 → 복구 실행 → 같은 부팅 성공 → 데이터는 남아 있음.

## 🔴 부수 결함: 표면 각각은 옳았는데 **합성이 깨졌다**

`FlywayMigrationIntegrationTest` 는 `locations=db/migration` 을 고정한 채 시드 행을
단언하고 있었다 — 즉 **"프로덕션 위치가 부트스트랩 SUPERADMIN 을 만든다" 를 핀으로 박고
있었다.** 그것은 계약이 아니라 결함이므로 dev/demo 목록으로 옮겼다.

그런데 그 클래스만 고치자 **`ReadModelPersistenceIntegrationTest` 가 16/16 RED** 가 됐다.
둘은 `AdminServiceIntegrationBase` 의 **같은 Postgres 컨테이너**를 공유하는데 location 목록이
달라져서, 형제가 적용한 repeatable 을 자기는 resolve 하지 못한 것이다. 각 클래스는 혼자서는
옳았고 **합성만 틀렸다.** 세 지점(`ReadModelPersistence…` · `ProjectionKafkaIntegrationBase`)을
같은 목록으로 맞추고 **왜 이제 그 합의가 선택 사항이 아닌지**를 주석에 적었다.

검증: `admin-service:integrationTest` **8클래스 61칸 전부 GREEN** · `:check` rc=0.

## 스펙 정합

| 파일 | 조치 |
|---|---|
| `domain-model.md § Reference Data Snapshot` | 제목을 `(dev / demo only)` 로, 파일 경로 갱신, **여는 것 2개 표**, 왜 이 문단이 예전엔 거짓이었는지, 복구 절차 |
| `architecture.md § Authorisation` | `permissions_json` = **저장은 하고 강제는 안 한다** 실측 |
| `database-design.md` § References | 죽은 `V99__seed_dev_data.sql` 포인터 → `db/seed/R__…` + `V3` 추가 |

## 남은 것

- 이 티켓은 admin 시드의 **적용 범위**만 닫았다. 형제 4개의 밴드는 `TASK-MONO-531` 이 닫았다.
- 🔵 프로덕션 provisioning 절차 문서 자체는 **아직 없다** — 프로덕션 배포가 존재하지 않으므로
  지금 쓰면 검증 불가능한 문서가 된다. 스펙이 "admin API 로 provisioning 한다" 를 선언하는
  선에서 멈췄고, 실제 절차는 첫 프로덕션 배포가 생길 때 그 형상에 맞춰 쓰는 것이 맞다.
