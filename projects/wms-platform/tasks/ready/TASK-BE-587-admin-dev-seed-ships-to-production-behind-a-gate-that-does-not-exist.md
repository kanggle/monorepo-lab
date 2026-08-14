# Task ID

TASK-BE-587

# Title

`admin-service` 의 dev 시드가 **존재하지 않는 게이트 뒤에서 전 환경에 적용된다** — 주석이 있다고 적은 location filter 가 없다

# Status

ready

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
- 형제 4개의 `db/seed/` 밴드 — `TASK-BE-588`

---

# Acceptance Criteria

- [ ] **AC-0 (실측)** — 4개 테이블 × (프로덕션 필수인가 / 무엇이 그것을 요구하는가). 🔴 `WMS_VIEWER`
      를 요구하는 인가 술어 전수를 함께 센다 — 역할을 내리면 무엇이 닫히는지가 결정의 입력이다
- [ ] **AC-1 (결정 + 구현)** — A/B/C 중 하나 + 근거. **파일의 주석이 실제 설정과 일치**하는 것이
      통과 조건이다(지금은 정면으로 어긋나 있다)
- [ ] **AC-2 (가드)** — 결정이 코드에서 유지되는지. 🔴 A 를 고르면 "역할은 있고 부트스트랩 사용자는
      없는" 프로덕션 형상을 실제로 재현해 대시보드가 열리는지 확인한다(대조군 없는 통과 금지)
- [ ] **AC-3 (기존 DB)** — 어떤 결정이든 이미 이 시드를 적용한 DB 가 깨지지 않는다.
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
- 고정 UUID 는 e2e 픽스처가 참조한다 — 값을 바꾸면 그쪽이 깨진다

# Failure Scenarios

- **주석만 고치고 배치를 안 본다** → 프로덕션에 공개 UUID 의 SUPERADMIN 이 그대로 남는다
- **시드를 통째로 내린다** → 내장 역할이 사라져 대시보드 8개가 전부 닫힌다(AC-0 이 막는다)
- **`R__` 파일을 rename 해서 분할** → 기존 DB 전부 validate 실패

# Definition of Done

- [ ] AC-0 ~ AC-3 전부
- [ ] 주석과 설정이 일치
- [ ] Ready for review
