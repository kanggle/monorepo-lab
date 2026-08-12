# Task ID

TASK-MONO-519

# Title

데모 운영자가 **한 명뿐**이라 ERP 결재 루프는 여전히 닫히지 않는다 — `MONO-515` 가 토큰을 고치자 그 아래에서 드러난 데이터 공백

# Status

done

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

# 🛑 2026-08-12 실측 기록 — AC-0 이 범위를 **줄였다**

AC-0 이 요구한 재측정에서 **전제 3건 확인 · 드리프트 2건 · 신규 발견 2건**이 나왔고,
그 결과 **AC-4 를 이 티켓에서 분리**했다. 아래는 전부 실측이며 추론은 그렇다고 표시했다.

## ① 전제 3건 — 그대로 성립 (다만 ②의 문장은 정정한다)

| # | 티켓의 진술 | 실측 |
|---|---|---|
| 1 | 결재함 = `approverId` + `status IN (SUBMITTED, IN_REVIEW)` | ✅ `ApprovalRequestJpaRepository` L61-67 문자 그대로. 호출자 = `ApprovalApplicationService` L247 `actor.actorId()` |
| 2 | 자기결재 금지 = **생성 게이트** | ✅ `createDraft` L92 `ApprovalRoute.multiStage(actor.actorId(), …)` → `SelfApprovalGuard` |
| 3 | `admin_operators` INSERT **1건**, `oidc_subject='…ad03'` | ✅ 파일에서 확인, `V9001` 의 `iam` 행 `account_id` 와 문자 일치 |

🔴 **②의 서술은 정정한다.** 티켓은 *"애초에 넣을 행을 만들 수 없다"* 고 적었는데, 기존
시드는 승인자에 **사원 마스터 id** 를 써서 행 자체는 만들어졌다(`DRAFT 1 · SUBMITTED 2`).
정확한 진술은 **"결재함에 뜰 수 있는 행"** — 승인자가 로그인 가능한 신원인 행 — 을 만들 수
없다는 것이다. 결함의 실체는 같지만, 이 구별이 없으면 "상신은 되는데 왜 0인가" 에서 막힌다.

## ② 드리프트 2건 — 티켓의 지시가 낡았다

- 🔴 **AC-1 의 "`V9002__…` 를 새로 만든다" 는 이미 쓸 수 없다.** `V9002` 는
  `TASK-MONO-512`(팬 아티스트 자격증명)가 가져갔다.
- 🔴 **Out of Scope 의 "`application-prod.yml` 이 `db/migration` 으로 제한" 은
  admin-service 얘기다.** auth-service 에는 `application-prod.yml` 이 **없고**,
  `application.yml` 이 기본(=프로덕션) 프로파일을 `classpath:db/migration` 으로 핀한다.
  데모가 dev 시드를 얻는 경로는 `infra/demo/projects.sh` 가 iam 에
  `docker-compose.e2e.yml`(`SPRING_PROFILES_ACTIVE: e2e`)을 함께 주기 때문이다.
  전제(= 새 시드가 데모에 로드된다)는 성립하지만 **이유가 다르다.**

## ③ 신규 발견 2건 — 하나는 이 티켓의 설계를 바꿨고, 하나는 별도 티켓이 됐다

**(a) `approverId` 는 참조 검증을 받지 않는다.** `submit` 은 `masterDataPort.isSubjectActive`
로 **subject 만** 검사한다(L131). ⇒ 승인자에 **계정 UUID** 를 넣을 수 있고, 그것이 결재함을
채우는 유일한 길이다. 이 사실이 없었으면 "승인자는 사원이어야 한다" 는 잘못된 제약 아래에서
직접-DB 로 우회하는 설계가 나왔을 것이다.

**(b) 🔴 `TASK-MONO-524` — auth-service 가 기존 DB 에서 크래시 루프다.** 라이브 검증 중
`auth-service` 가 `Exited(1)` 로 죽었다. `auth_db.flyway_schema_history` 를 직접 읽은 결과:
최고 적용 = **9001**(2026-08-05), 그런데 어제 `#3270` 이 **`V0032`** 를 추가했다 ⇒
`0032 < 9001` 이라 Flyway out-of-order 거부. **이 티켓과 무관한 main 의 라이브 결함**이고
별도 티켓으로 냈다. 🔵 그리고 이 발견이 **이 티켓의 AC-1 을 뒤집었다** — AC-1 이 지시한
V9000 대역이 정확히 그 함정이므로, 새 시드는 **`R__`(repeatable)** 로 넣었다. 근거는 그
파일 헤더에 실측과 함께 있다. 복구는 볼륨 삭제가 아니라 `flyway_schema_history` 의 9001 행
제거로 했다(데모 데이터 보존).

---

# 📏 라이브 실측 (2026-08-12, iam + erp, `DEMO_DOMAIN=local`)

## AC-3 — 루프가 실제로 닫힌다 (4단 전부)

```
(a) 두 assume 토큰의 sub — 토큰에서 직접 디코드
    demo@demo.com       sub = 0199de70-0000-7000-8000-00000000ad03   (승인자)
    requester@demo.com  sub = 0199de70-0000-7000-8000-00000000ad04   (상신자)
    ⇒ 서로 다르다. 둘 다 tenant_id=demo-corp, aud=platform-console-web

(b) 상신자로 생성 → self_approval 아님 (DB 실물)
    approval_request.submitter_id = …ad04   approver_id = …ad03   ×2행

(c) 승인자 결재함 (BFF 원소 수, HTML 아님)
    GET /api/erp/approval/inbox  →  200  totalElements = 2

(d) 승인자로 승인
    상태 BEFORE : SUBMITTED
    POST …/approve →  HTTP 200   body.status = "APPROVED"
    상태 AFTER  : APPROVED
    결재함 AFTER: totalElements 2 → 1
```

🔵 상태·본문·전후 읽기 차이를 **따로** 적었다(AC-3 의 요구). 200-but-no-op 이면 상태가
안 바뀌고, 봉투 불일치면 본문이 안 맞는데, 셋 다 일치한다.

## AC-4 — 🔴 **충족하지 못했다. 구조적으로 도달 불가이며 이 티켓의 범위 밖이다**

```
승인자 sub 로 GET /api/erp/notifications  →  200  totalElements = 0
erp_notification_db.notification                →  0행
```

**0건은 계측 실패가 아니다** — 소비자 로그가 원인을 그대로 적는다:

```
Invalid envelope on topic=erp.approval.submitted.v1 offset=0; routing to DLT:
  Out-of-contract tenantId 'demo-corp' on topic erp.approval.submitted.v1
  (single-tenant invariant: erp)
```

세 토픽(`delegated` · `submitted`×2 · `approved`)이 **전부 DLT**. 🔴 봉투 자체는 이미
옳다 — 카프카에서 읽은 실물에 최상위 `tenantId`/`aggregateType`/`aggregateId` 가 전부 있다
(`TASK-ERP-BE-043` 의 AC-1/AC-2 는 이미 랜딩됐다). 막는 것은 `EnvelopeToCommandMapper`
L67 의 **테넌트 관문**(`requiredTenantId` 기본 `erp`, 데모 compose 가
`OIDC_REQUIRED_TENANT_ID: erp`)이고, 콘솔 `/erp/delegation` 의 read-model 뷰가 비는 것과
**같은 관문 하나**다.

⇒ **데모 데이터 공백이 아니라 아키텍처 결정**이다. 결정은 `ADR-ERP-001`(**Proposed**)이
쥐고 있고 실행은 `TASK-ERP-BE-043` 이 **HARDSTOP-09 로 정지** 중이다. 설정으로 우회하면
(`OIDC_REQUIRED_TENANT_ID=demo-corp`) **HTTP 인증이 erp 도메인 전체에서 깨진다** — 같은
프로퍼티를 HTTP 두 곳이 *도메인 키*로 읽기 때문이다.

🔴 그래서 알림함은 이 티켓에서 닫지 않는다. `TASK-ERP-BE-043` 이 닫히면 자동으로 따라오도록
워크스루 표에 **별도 행**으로 실측치와 함께 남겼다 — 조용히 빠뜨리지 않는다(`MONO-515` 가
"결재함은 둘" 이라고 발굴한 것을 되돌리지 않기 위해).

## AC-5 — 🟡 **부분 충족.** 재현한 것과 못 한 것을 나눠 적는다

```
연속 실행 (같은 스택, 코드 변경 없음)
  run 3 :  실패 1  ← masterdata 도달 실패 1건 (아래 잡음 표)
  run 4 :  생성 0 · 기존 20 · 실패 0 · rc=0 · 결재함 2건 = 대기 행 2건
```

✅ **2회차 형태(`생성 0 · 기존 N · 실패 0`, rc=0)는 실측했다.**
🔴 **"볼륨 삭제 후 신선 기동" 은 못 했다** — `docker compose down -v` 가 이 세션의 자동
승인 분류기에 차단됐고 우회하지 않았다. 대신 **MONO-515 이전의 도달 불가 행 3건만**
회수했다(그 행들의 `submitter_id` 는 `platform-console-web` — 이제 어떤 신원도 가질 수 없는
값이므로 아무도 승인할 수 없는 상태다). 그래서 1회차가 마스터 존재 상태에서 시작했고
`1회차 생성 N·기존 0` 은 재현되지 않았다. 완전한 AC-5 는 볼륨 초기화 뒤 재측정이 필요하다.

🔵 **레거시 데이터 가드는 그 과정에서 실물로 물었다** — 승인자가 다른 기존 행에 대해:
```
✗ 결재 운영본부 개편 — 기존 행의 승인자가 '019fd768-bc95-…' 입니다(기대 '0199de70-…ad03').
  TASK-MONO-519 이전에 심긴 데이터입니다 — … 볼륨을 초기화한 뒤 다시 심으십시오   (rc=1)
```
이 가드가 없었으면 그 실행은 **`생성 0 · 기존 20 · 실패 0` 이라는 완벽한 요약과 함께
결재함 0** 이었을 것이다.

## 🔴 실측 중 밟은 것 — 판정에 쓰지 않은 잡음 3종

닫는 사람이 회귀로 오해하지 않도록 적어 둔다. 셋 다 **코드 변경 없이 재시도로 사라졌다** ⇒
결함이 아니라 **호스트 포화**다(이미지 빌드 + JVM 11개 동시 기동 중이었다).

| 증상 | 어디 | 재시도 결과 |
|---|---|---|
| `504 Gateway Timeout` (create/submit/notifications) | 게이트웨이 | 사라짐. 🔴 **create 는 504 를 냈지만 서버에는 정상 생성됐다** — 상태코드만 보고 "실패" 로 세면 다음 실행이 중복을 만든다 |
| `cause=unreachable … I/O error on GET http://masterdata-service:8080/…` | approval→masterdata | 사라짐. DNS 는 정상이었다(`getent` 확인) ⇒ connect 실패 |
| `dependency failed to start: timed out dialing Hyper-V socket` | compose | 순차 재기동으로 해소 |

🔵 `subject_unresolved`(422)가 나왔지만 **`TASK-ERP-BE-041` 의 회귀가 아니다** — 로그의
`cause` 가 `unreachable` 이었다(권한 문제라면 다른 분류다). 두 토큰 **모두** 같은 마스터를
게이트웨이 경유로 200 으로 읽는다는 대조군도 함께 측정했다.

---

# Acceptance Criteria

- [x] **AC-0 (전제 재측정 — 착수 첫 작업)** — 위 § 실측 기록. **드리프트 2건 + 신규 발견 2건.**
      특히 (3) 의 `INSERT INTO admin_operators` 건수와 `V9001` 의 `account_id` 값을
      파일에서 직접 확인한다. 🔴 **이 티켓의 숫자를 물려받지 말 것** — `MONO-510` 이
      대리지표로 판정했다가 2회차에 정확히 뒤집힌 축이다.
      전제가 이미 달라졌으면 phantom 으로 기록하고 범위를 다시 짠다.
- [x] **AC-1 (credential)** — ✅ 충족하되 **지시한 형태와 다르다.** `V9002` 는 이미 쓰였고
      (`MONO-512`), 무엇보다 실측이 **V9000 대역 자체가 함정**임을 보였다(위 ③(b)) ⇒
      `R__seed_demo_second_operator_credential.sql`(repeatable)로 넣었다. 🔵 AC-1 의 *의도*
      (이미 적용한 DB 를 깨지 않는다)는 R__ 가 **더 강하게** 만족한다 — 버전 순서에
      참여하지 않으므로 미래의 프로덕션 마이그레이션도 out-of-order 로 만들지 않는다.
      🔵 원문의 경고 *"`V9001` 을 편집하지 말 것"* 은 그대로 지켰다(편집 0건). 새
      `account_id` 도 기존과 다른 UUID 다(`…ad04`). 틀린 것은 **대체 위치**뿐이다 —
      🔴 원문의 *"`V9002__…` 를 새로 만든다(9000 대역은 … `R__seed_demo_operator.sql`
      헤더가 그 이유를 적어 두었다)"* 에서 **인용한 출처를 열어 보니 정반대**였다. 그
      헤더는 admin-service 가 그 대역을 *피한* 이유를 설명하는 문단이고, 거기 적힌
      시나리오가 어제 auth-service 에서 실제로 일어났다(③(b)).
- [x] **AC-2 (operator)** — ✅ `demo-requester` 3종 세트. `oidc_subject` = `…ad04` 문자 일치
      (라이브 확인: `admin_operators` 에 `demo-corp` 운영자 **2행**, 토큰 `sub` 가 그대로 나옴).
      구성: `admin_operators` + `admin_operator_roles`(SUPER_ADMIN) +
      `operator_tenant_assignment → demo-corp`. 🔵 판정은 문구가 아니라 **토큰의 `sub`** 로
      냈다(원문의 요구) — 불일치는 degrade 하지 않고 콘솔 401
      `operator_exchange_unavailable` 로 뜨는데 그 문구가 5초 타임아웃과 글자까지 같아
      "느린가?" 로 오진되기 때문이다. 🔵 원문이 *"AC-1 과 대칭이 아닌 이유"* 라고 적은
      비대칭은 **해소됐다** — 이제 양쪽 다 `R__` 다.
- [x] **AC-3 (루프가 실제로 닫힘 — 이 티켓의 본론)** — ✅ **4단 전부.** 위 § 라이브 실측.
      🔵 A/B 는 라벨이고, 이 구현은 **B(시드 전용 신원)가 상신, A(`demo@demo.com`)가 승인**
      으로 배정했다 — 면접관이 로그인하는 계정이 결재함을 쥐어야 그가 여는 화면이 차기
      때문이다. 반대로 하면 숫자상 루프는 닫히는데 화면은 0 이다. 원문 요구 ↓
      라이브 실측 4단:
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
- [ ] **AC-4 (두 번째 결재함)** — 🔴 **미충족 · 이 티켓에서 분리.** 재긴 **쟀다**(0건,
      HTTP 200, DB 0행) — 안 재고 넘어가지 않았다. 그러나 0 의 원인은 데모 데이터가 아니라
      `EnvelopeToCommandMapper` 의 **테넌트 관문**이고, 그 해제는 `ADR-ERP-001`(Proposed)의
      결정 + `TASK-ERP-BE-043`(HARDSTOP-09 정지)의 실행에 달려 있다. 위 § AC-4 참조.
      워크스루 표에 **별도 행**으로 실측치와 함께 남겼으므로 `MONO-515` 의 발굴은 보존된다.
      원문 요구 ↓ erp `notification-service` 알림함도 같은 방식으로
      판정한다(운영자 B 의 `sub` 로 수신 원소 수). 🔴 **안 재고 넘어가면 `MONO-515` 가
      "결재함이 둘" 이라 발굴한 것을 되돌리는 것이다.**
- [ ] **AC-5 (멱등)** — 🟡 **부분 충족.** 2회차 형태(`생성 0 · 기존 20 · 실패 0`, rc=0)는
      실측했고, 레거시 데이터 가드가 실물로 물었다. **볼륨 삭제 후 신선 기동은 못 했다**
      (`down -v` 가 세션 분류기에 차단, 우회 안 함) ⇒ `1회차 생성 N·기존 0` 미재현.
      위 § AC-5 참조. 원문 요구 ↓ 볼륨 삭제 후 신선 기동 → `demo-up.sh erp` → `seed-erp.sh` **2회**.
      1회차 `생성 N·기존 0·실패 0`, 2회차 `생성 0·기존 N·실패 0`, 양쪽 `rc=0`.
      🔵 "있으면 건너뜀" 으로 만들지 않는다 — 중간 상태는 **실패로 센다**
      (`MONO-510` 2회차에서 그 술어가 504 위양성을 잡았다).
- [x] **AC-6 (워크스루 한계 표)** — ✅ 🟡 행을 ✅ 로 바꾸고 4단 실측치를 넣었다. 🔴 그리고
      **행을 하나 늘렸다** — 알림함(AC-4)이 여전히 0 이라는 사실을 결재함 행에 섞으면 "찬다"
      와 "안 찬다" 가 한 줄에서 서로를 가린다. 원문 ↓ 해당 행을 실측 결과로 갱신한다. 🔴 `TASK-MONO-518`
      이 이 표의 stale 행을 잡는 가드를 만들고 있다 — 닫으면서 행을 안 고치면
      **그 가드의 첫 희생자가 이 티켓이 된다**.
- [x] **AC-7 (안 하는 것도 산출물)** — ✅ `R__seed_demo_operator.sql` §5 에 한 문단으로 적었고,
      **테스트가 그것을 핀한다**(`secondOperatorAssignmentsAreTheDocumentedOnes` — `demo-corp`
      포함 · `ecommerce` 불포함, 대조군으로 첫 운영자의 2건을 함께 센다). 🔵 주석만으로는
      "대칭을 맞추자" 를 못 막는다 — 이제 그 변경은 RED 와 논쟁해야 한다. 원문 ↓
      두 번째 운영자에게 `ecommerce` 배정을
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

# 후속 (이 티켓이 낳은 것)

- `TASK-MONO-524` — auth/account-service 의 dev 시드 대역이 이후 프로덕션 마이그레이션을
  out-of-order 로 만든다. **기존 `auth_db` 는 어제부터 크래시 루프.** 이 티켓이 밟아서 발견했다.
- **AC-4(알림함)** — `ADR-ERP-001` ACCEPTED → `TASK-ERP-BE-043` 재개 시 자동으로 따라온다.
  별도 티켓을 새로 파지 않는다: BE-043 이 이미 그 관문의 소유자이고, 워크스루 표의 새 행이
  실측치와 함께 그것을 가리킨다.
- **AC-5 잔여** — 볼륨 초기화 후 `demo-up.sh erp` → `seed-erp.sh` 1회차가
  `생성 N · 기존 0 · 실패 0` 임을 확인. 차단 없이 실행할 수 있는 세션에서 한 번 재고 닫는다.

# Definition of Done

- [x] AC-0 ~ AC-3 · AC-6 · AC-7 충족 / AC-4 분리(사유 기록) · AC-5 부분(사유 기록)
- [x] `TASK-MONO-515` AC-4 가 남긴 항목이 실측으로 닫혔음을 워크스루 표에 반영
- [x] 제품 코드 변경 0건 (시드 + 데모 스크립트 + 가드 테스트 + 문서만)
