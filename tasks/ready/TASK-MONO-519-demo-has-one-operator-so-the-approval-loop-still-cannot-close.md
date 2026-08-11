# Task ID

TASK-MONO-519

# Title

데모 운영자가 **한 명뿐**이라 ERP 결재 루프는 여전히 닫히지 않는다 — `MONO-515` 가 토큰을 고치자 그 아래에서 드러난 데이터 공백

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- iam
- data

---

# 배경 — 막힌 것을 고치자 그 아래가 드러났다

`TASK-MONO-515`(`ADR-MONO-060` A, impl PR #3260 squash `4ad33c98d`) 가 assume 토큰의
`sub` 를 acting 클라이언트(`platform-console-web`)에서 **계정 UUID** 로 고쳤다. 그
전까지 콘솔 운영자는 도메인 전체에서 **통틀어 한 사람**이었고, 이제는 **콘솔 계정마다
한 사람**이다.

그런데 **콘솔 계정이 하나뿐**이다. 그래서 `MONO-515` 가 열려던 화면 —
콘솔 `/erp/approval` 결재함 — 은 여전히 0 이다. 남은 것은 **토큰 결함이 아니라
데모 데이터 공백**이고, `MONO-515` 는 그것을 AC-4 미충족으로 정직하게 고지한 뒤
후속으로 미뤘다. 이 티켓이 그 후속이다.

## 실측 (이 티켓 작성 시점, 추론 아님)

세 술어가 맞물려 **두 번째 신원 없이는 결재 요청이 만들어지지조차 못한다**:

1. **결재함 술어** —
   `ApprovalRequestJpaRepository.findInboxPending` 는
   `r.approverId = :approverId AND r.status IN (SUBMITTED, IN_REVIEW)` 다.
   `approverId` 는 **현재 단계 승인자로 비정규화**된 값이고, 호출자는
   `ApprovalApplicationService` L247 의 `actor.actorId()` = JWT `sub` 다.
2. **자기결재 금지** — `ApprovalRoute.multiStage` 가 각 단계마다
   `SelfApprovalGuard.ensureNotSelfApproval(submitterId, approverId)` 를 호출하고,
   `submitterId.equals(approverId)` 면 거부한다
   (`ApprovalErrors.CAUSE_SELF_APPROVAL = "self_approval"`).
   🔴 **이것은 조회 필터가 아니라 생성 게이트다** — 즉 결재함이 "비어 보이는" 게 아니라
   **애초에 넣을 행을 만들 수 없다**.
3. **신원이 하나뿐** —
   `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql`
   의 `INSERT INTO admin_operators` 는 **1건**(`operator_id='demo-operator'`,
   `tenant_id='demo-corp'`, `oidc_subject='0199de70-0000-7000-8000-00000000ad03'`).
   링크 키는 auth-service `db/migration-dev/V9001__seed_demo_single_identity_credentials.sql`
   의 `credentials.account_id` 와 **문자 그대로 같아야** 한다(그 SQL 헤더가 그렇게 못박고 있다).

⇒ `demo-corp` 안의 `actorId` 는 정확히 하나 ⇒ (2) 가 (1) 에 넣을 행의 생성을 막는다
⇒ 결재함 **구조적으로 0**, 승인/반려 버튼 도달 불가.

## 🔴 두 번째 결재함을 잊지 말 것

`MONO-515` AC-0 이 발굴했다 — 결재함은 **둘**이다. erp `notification-service` 의
`NotificationInboxController.recipient(jwt) = jwt.getSubject()` 가 같은 모양이다.
운영자가 둘이 되면 알림 수신자도 갈라지므로, 이 티켓의 판정은 **두 화면 모두**에서 낸다.

---

# Goal

데모 테넌트 `demo-corp` 에 **두 번째 운영자 신원**을 시드해서, 콘솔에서
상신 → (다른 운영자로) 승인 의 ERP 결재 루프가 **실제로 닫히는 것**을 실측한다.
`TASK-MONO-515` AC-4 가 남긴 유일한 미충족 항목을 해소한다.

# Scope

## In Scope

- auth-service dev 시드에 **두 번째 credential**(별도 `account_id` UUID) 추가
- admin-service `R__seed_demo_operator.sql` 에 두 번째 `admin_operators` +
  `admin_operator_roles` + `operator_tenant_assignment`(→ `demo-corp`) 추가
- `infra/demo/seed/seed-erp.sh` — 결재 시드를 **두 신원**으로 갈라 상신자 ≠ 승인자로 만들기
- `docs/guides/interview-demo-walkthrough.md` § 한계 표의 해당 행 갱신
  (`MONO-515` 가 🔴 → 🟡 로 낮춰 두고 "남은 사유 = 데모 데이터 1건" 이라 적은 그 행)

## Out of Scope

- **제품 코드 변경 0** — `SelfApprovalGuard` · 결재함 술어 · 토큰 발급 경로는 손대지 않는다.
  이 티켓의 전제는 *그 셋이 이미 옳다* 는 것이다(`ADR-MONO-060` 이 토큰 축을 닫았다).
- 운영자 RBAC 모델 변경, 권한 세트 신설 — `demo-corp` 안에서 기존 역할로 해결한다.
- 프로덕션 시드. `db/migration-dev` 는 non-prod 프로파일에서만 로드된다
  (`application-prod.yml` 이 `db/migration` 으로 제한).

---

# Acceptance Criteria

- [ ] **AC-0 (전제 재측정 — 착수 첫 작업)** — 위 § 실측 세 항목을 **다시 잰다**.
      특히 (3) 의 `INSERT INTO admin_operators` 건수와 `V9001` 의 `account_id` 값을
      파일에서 직접 확인한다. 🔴 **이 티켓의 숫자를 물려받지 말 것** — `MONO-510` 이
      대리지표로 판정했다가 2회차에 정확히 뒤집힌 축이다.
      전제가 이미 달라졌으면 phantom 으로 기록하고 범위를 다시 짠다.
- [ ] **AC-1 (credential)** — auth-service `db/migration-dev` 에 두 번째 신원 추가.
      🔴 **`V9001` 을 편집하지 말 것** — 버전드 마이그레이션이라 체크섬이 바뀌면 이미
      적용한 로컬/데모 DB 에서 Flyway `validate` 가 실패한다. **`V9002__…` 를 새로 만든다**
      (9000 대역은 account-service 가 쓰는 dev-seed 관례이고 `R__seed_demo_operator.sql`
      헤더가 그 이유를 적어 두었다). 새 `account_id` 는 기존과 **다른 UUID**.
- [ ] **AC-2 (operator)** — `R__seed_demo_operator.sql` 에 두 번째 운영자 3종 세트
      (`admin_operators` + `admin_operator_roles` + `operator_tenant_assignment → demo-corp`).
      `oidc_subject` = AC-1 의 새 `account_id` 와 **문자 그대로 동일**.
      🔴 불일치는 degrade 하지 않고 콘솔에서 **401 → `operator_exchange_unavailable`** 로
      뜨는데, 그 문구는 **5초 타임아웃과 글자까지 같다**(그 파일 L36-39 가 경고한다) ⇒
      "느린가?" 로 오진하기 쉽다. 판정은 문구가 아니라 토큰의 `sub` 로 낸다.
      🔵 이 파일은 `R__`(repeatable) 이라 체크섬 변경이 안전하다 — AC-1 과 대칭이 아닌 이유.
- [ ] **AC-3 (루프가 실제로 닫힘 — 이 티켓의 본론)** — 라이브 실측 4단:
      (a) 운영자 A 토큰의 `sub` ≠ 운영자 B 토큰의 `sub` (두 값을 **토큰에서 직접** 찍는다),
      (b) A 로 상신 → `2xx` 이고 `self_approval` 이 **아니다**,
      (c) B 의 `GET /api/erp/approval/inbox` 원소 수 **≥ 1**
      (경로는 `approval-api.md § GET /api/erp/approval/inbox` + `ApprovalInboxController`
      `@RequestMapping` 에서 확인함),
      (d) B 로 승인 → `2xx` 이고 상태가 실제로 전이한다.
      🔴 **(c) 는 BFF 원소 수로 잰다, 화면 HTML 로 재지 않는다** — 콘솔은 클라이언트
      렌더라 SSR grep 은 구조적으로 0건이다.
      🔴 **상태·본문·전후 읽기 차이를 따로 적는다** — `MONO-510` 6회차가 200-but-no-op
      (거짓 양성)과 봉투 불일치(거짓 음성)를 양방향으로 밟았다.
- [ ] **AC-4 (두 번째 결재함)** — erp `notification-service` 알림함도 같은 방식으로
      판정한다(운영자 B 의 `sub` 로 수신 원소 수). 🔴 **안 재고 넘어가면 `MONO-515` 가
      "결재함이 둘" 이라 발굴한 것을 되돌리는 것이다.**
- [ ] **AC-5 (멱등)** — 볼륨 삭제 후 신선 기동 → `demo-up.sh erp` → `seed-erp.sh` **2회**.
      1회차 `생성 N·기존 0·실패 0`, 2회차 `생성 0·기존 N·실패 0`, 양쪽 `rc=0`.
      🔵 "있으면 건너뜀" 으로 만들지 않는다 — 중간 상태는 **실패로 센다**
      (`MONO-510` 2회차에서 그 술어가 504 위양성을 잡았다).
- [ ] **AC-6 (워크스루 한계 표)** — 해당 행을 실측 결과로 갱신한다. 🔴 `TASK-MONO-518`
      이 이 표의 stale 행을 잡는 가드를 만들고 있다 — 닫으면서 행을 안 고치면
      **그 가드의 첫 희생자가 이 티켓이 된다**.
- [ ] **AC-7 (안 하는 것도 산출물)** — 두 번째 운영자에게 `ecommerce` 배정을
      **주지 않는 이유**를 시드 주석에 한 줄로 적는다(운영자 A 의 `ecommerce` 배정은
      `TASK-BE-576` 이 스토어프론트 가시성 때문에 넣은 것이고, B 는 그 증상을 갖지
      않는다). 안 적으면 다음 사람이 비대칭을 결함으로 읽고 조사를 반복한다.

# Related Specs

- `projects/erp-platform/specs/services/approval-service/architecture.md`
- `projects/erp-platform/specs/services/notification-service/architecture.md`
- `projects/iam-platform/specs/services/admin-service/architecture.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` (§ 결재함 / PageMeta)
- `projects/erp-platform/specs/contracts/http/notification-api.md`
- `platform/contracts/jwt-standard-claims.md` § `sub`
  (`ADR-MONO-060` / `TASK-MONO-515` 변경 이력 행)

# Related ADR

- `docs/adr/ADR-MONO-060-assumed-token-subject-identity.md` — assume 토큰 `sub` = 계정 UUID (ACCEPTED, A)

# Edge Cases

- **두 운영자가 같은 `oidc_subject` 를 갖는 경우** — 링크 키가 겹치면 `MONO-515` 가
  고친 결함이 데이터 층에서 **되살아난다**(둘이 다시 한 사람이 된다). AC-3(a) 가
  이것을 잡는 유일한 술어다.
- **`R__` 재실행** — `ON DUPLICATE KEY UPDATE` / `INSERT IGNORE` 를 기존 문장과 같은
  형태로 유지한다. 새 문장이 비멱등이면 파일 전체의 재실행 안전성이 깨진다.
- **다단계 결재 라우트** — 승인자가 2명 이상인 라우트를 시드하면 세 번째 신원이
  필요해질 수 있다. AC-3 은 **단일 단계**로 판정하고, 다단계가 필요하면 그 사실을
  적고 범위를 넓히지 않는다.

# Failure Scenarios

- **`V9001` 을 편집해 버림** → 이미 마이그레이션을 적용한 데모/로컬 DB 에서 Flyway
  체크섬 검증 실패. 증상은 설정 오류가 아니라 **앱 크래시 루프**로 나타난다.
  복구는 `flyway_schema_history` 손질 또는 볼륨 삭제 — 둘 다 비싸다. AC-1 이 이것을 막는다.
- **`oidc_subject` 오타** → 콘솔 401 `operator_exchange_unavailable`. 문구가
  콜드스타트/타임아웃과 동일해 **"백엔드가 느리다" 로 오진**된다.
- **`operator_tenant_assignment` 누락** → 토큰 교환은 되는데 assume 이
  `invalid_grant "operator is not assigned to the selected tenant"` 로 떨어진다
  (`MONO-512` 가 fan 에서 밟은 그 갈래).
- **AC-3 을 화면 스크린샷으로 판정** → 클라이언트 렌더라 아무것도 증명하지 못한다.
  BFF 원소 수가 유일한 술어.

# Definition of Done

- [ ] AC-0 ~ AC-7 전부 충족
- [ ] `TASK-MONO-515` AC-4 가 남긴 항목이 실측으로 닫혔음을 워크스루 표에 반영
- [ ] 제품 코드 변경 0건 (시드 + 데모 스크립트 + 문서만)
