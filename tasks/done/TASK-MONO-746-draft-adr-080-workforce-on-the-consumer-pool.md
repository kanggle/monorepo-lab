# Task ID

TASK-MONO-746

# Title

⏳ `ADR-MONO-080` 후보 기안 — **직원도 풀 계정**: 이메일 인증 게이트 → IAM 2단계 인증 → 운영자 규칙 변경

# Status

done

# Owner

monorepo

# Task Tags

- adr
- identity
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (인증 모델 · 운영자 권한 평면)
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** 날짜 조건이 아니다. AC-0 은 verify-then-act 게이트다 — 재서 참이면 진행, 아니면 이 파일에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.

---

# Dependency Markers

- **선행**: `ADR-MONO-078` 단계 티켓 완료(`TASK-BE-614`~`617` · `744` — `745` 는 2026-10-02 구현 없이 닫혀 079 로 흡수 · `743` 은 2026-10-05 구현 없이 종결: 따로 된 계정의 모집단이 0 이고 더는 안 생긴다 — 남은 소셜 구멍은 617 의 순서 규칙으로) · `ADR-MONO-079`(소속사·셀러·팬 콘솔 관리 — 기안 `TASK-MONO-747`) 결정. 소유자가 정한 순서: **078 → 079 → 080** (2026-10-01).

# Goal

소유자 결정(2026-10-01 UTC, «080 후보 등록»)을 durable 하게 둔다. 고객사 직원 · 협력사 직원 · 셀프 온보딩 운영자도 **개인 풀 계정 하나에 회사 권한을 받는** 모델(GitHub · Slack 의 «개인 계정 + 조직 소속» 모양)을 ADR 로 기안한다. 플랫폼 관리자(`iam`)는 계속 분리한다.

대화에서 정리된 그 모델의 위험과 해법(2026-10-01, 저장소 실측 포함):

| 위험 | 해법 | 저장소 현황 |
|---|---|---|
| ① 초대가 엉뚱한 사람에게 — IAM 이 이메일 소유를 확인하지 않는다 | **초대 수락 시 인증된 이메일 필수** | 이메일 인증 기능은 있다(`VerifyEmailUseCase`) · 로그인·초대 조건으로는 안 쓰인다 |
| ② 회사가 보안 정책을 걸 수 없다 | IAM 로그인 2단계 인증 + **회사 테넌트 진입(assume-tenant) 시** 2단계 인증 여부 확인(또는 회사 SSO 연동) | TOTP 는 admin-service 비상 로그인(`AdminLoginService`)에만 있다 · IAM OIDC 로그인에는 없다 |
| ③ 권한 회수 누락 | 묶음 회수 | ✅ 이미 있다 — 운영자 그룹(`ADR-MONO-046`) · 테넌트 배정 해제 · 파트너십 해지 일괄 회수(`ADR-MONO-045` D6) |
| ④ 코드 규칙 | «운영자는 대상 테넌트 계정에만»(`TASK-MONO-334`) 변경 · 내부 프로비저닝(회사 테넌트에 계정 생성)을 «풀 계정 초대» 로 | 결정 필요 |

# Scope

## In Scope

- `ADR-MONO-080` PROPOSED 기안(선택지 · 실측 · 선행 단계 순서: 이메일 인증 게이트 → IAM 2단계 인증 → 운영자 규칙 변경)
- 🔵 **인증된 이메일끼리 자동 묶기** — 078 이전에 팬·스토어에 따로 계정이 있던 사람 중 **두 계정 모두 같은 이메일로 인증**한 경우는 자동으로 묶어도 안전하다(같은 메일함 주인). 080 이 이메일 인증을 들이므로 같이 다룬다(`ADR-MONO-078` D2 의 «이메일만으로는 묶지 않는다» 와 충돌하지 않는다 — 그 이유가 «인증이 없어서» 였다)
  - 🔵 **2026-10-05 메모** — 이 항목의 대상(078 이전 이중 계정)은 `TASK-MONO-743` 닫기 기록 기준 **0명**이고 새로 생기지 않는다(이메일 가입은 풀 · 소셜은 617 순서 규칙). 착수 시 그 술어를 다시 재서 0 이면 이 항목은 «대상 없음» 으로 적고 넘어간다.

## Out of Scope

- 구현 — ADR ACCEPT 뒤 단계 티켓으로

# Acceptance Criteria

- [x] **AC-0 (게이트)** — 선행 표의 티켓이 전부 `done/` 이고 `ADR-MONO-079` 가 ACCEPTED 다. 아니면 착수하지 않는다. → **참**(2026-10-07 UTC, § 착수 기록)
- [x] **AC-1** — `ADR-MONO-080` PROPOSED 가 위 표의 네 위험과 해법을 실측과 함께 적고, 정확형 수락 형식을 명시한다. → ADR § Context 네 표(file:line) · D3~D6 · § 수락 형식
- [x] **AC-2** — 플랫폼 관리자 분리 유지가 결정 본문에 있다(소유자 대화 2026-10-01: «플랫폼 관리자 빼고 전부 풀»). → ADR D1
- [x] **AC-3** — 078 이전 이중 계정 중 «인증 안 하고 묶지도 않은» 사람이 남는다는 사실과 그 처리(남김 / 묶기 안내 강화)를 선택지로 적는다. (🔵 2026-10-05: 743 닫기 기록의 술어로 다시 재서 0 이면 «대상 없음 — 측정값과 날짜» 로 닫는다.) → **대상 없음**(2026-10-07 UTC — 정적 0 · 라이브 ⚪, ADR D8 · § 착수 기록)

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D5 · `ADR-MONO-045` D6·D7 · `ADR-MONO-046`
- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

# Edge Cases

- 협력사 직원 — 파트너십 해지 시 일괄 회수가 개인 풀 계정에서도 그대로 끝나는가(B 회사 소속을 어디에 기록하나).
- 회사 도메인 메일 vs 개인 메일로 초대 — 퇴사 시 메일 접근 차단 여부가 다르다.

# Failure Scenarios

1. 이메일 인증 게이트 없이 운영자 규칙만 바꾼다 — 위험 ① 이 그대로 열린다.
2. 이 티켓이 «080 을 쓴다» 는 산문으로만 남고 078·079 가 끝난 뒤 아무도 착수하지 않는다 — 그래서 `ready/` 의 게이트 티켓으로 둔다.

---

# 착수 기록 (2026-10-07 UTC) — 소유자 지시 «MONO-746 기안을 시작»

## AC-0 — 게이트 재측정 (verify-then-act)

| 술어 | 결과 | 근거 |
|---|---|---|
| `TASK-BE-614`~`617` 이 `done/` | ✅ 4/4 — `projects/iam-platform/tasks/done/` 에 있고 Status `done`. ready·in-progress·review 에 같은 id 0 | `git ls-files` (base `origin/main` `504218022`) |
| `TASK-MONO-744` 가 `done/` | ✅ `tasks/done/` · Status `done` | 같은 |
| (참고) `743` · `745` | 둘 다 `done/`(구현 없이 종결 — 선행 표에서 이미 빠짐) | 같은 |
| `ADR-MONO-079` Status | ✅ `ACCEPTED`(2026-10-02, 정확형 `ADR-MONO-079 ACCEPTED — A`) | ADR-079 헤더 L3 · L8 |

⇒ 게이트 **참** → 착수.

## 산출물 — `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` (PROPOSED)

- 티켓 Goal 표 네 행을 **복사하지 않고 코드로 다시 쟀다**(ADR § Context, 전부 file:line). 네 행 다 **맞았다**. 빠진 것 셋을 더했다:
  1. 🔴 인증 메일을 **실제로 보내는 어댑터가 없다** — 구현체는 `LoggingEmailVerificationNotifier`(`!prod`, 토큰 미기록) 하나. 이메일 인증 게이트(단계 1)는 발송 어댑터가 **선행**이다.
  2. 🔴 위험 ① 은 이미 한 번 열렸다 — `ADR-MONO-079` D5 셀러 구성원 수락(`ConsumerSiteRoleWriteUseCase.java:78`)이 **인증 여부 없이** 이메일 일치로 `SELLER` 를 준다.
  3. 🔴 `require_2fa` 는 break-glass 로컬 로그인에서만 문다 — 주 경로(OIDC 토큰 교환, `TokenExchangeService.java:72-106`)는 `ACTIVE` 만 본다. 플랫폼 관리자(`SUPER_ADMIN`)도 지금 2FA 없이 콘솔에 들어온다 ⇒ D1(분리) ≠ 2FA 면제.
  - 덧붙여(코드 읽기, 라이브 아님): 풀 계정은 지금 셀프 온보딩도 못 한다(온보딩이 `platform-console-web` 토큰을 요구, 풀 계정은 그 토큰을 못 받음) — 080 이 여는 문은 생성(334)·로그인(615)·온보딩(044) 셋.
- 결정 구조: D1 플랫폼 관리자 분리(AC-2) · D2 단계 순서 · D3 이메일 게이트 · D4 IAM 2단계 + 진입 검사 · D5 회수 = 측면만 · D6 운영자 규칙 = 초대 → 인증된 본인 수락 · **D7 직원 ↔ 계정(선택지만, 정하지 않음)** · D8 AC-3 대상 없음. 갈래 A(세 단계)/B(단계 1·2)/C(기록만), 추천 A(구현자 선호), 라이더 R1~R4.
- 🔵 **오케스트레이터 지시로 추가한 입력 — 직원 마스터 ↔ IAM 계정**(ADR § 새 입력 · D7): 결재함 술어 `approver_id = JWT sub`(`ApprovalRequestJpaRepository.java:61-67`) vs 계약 «approverId = employee id»(`approval-api.md:253`) · 직원 마스터에 계정 칸 0(`V1__init.sql:38-55`) · 데모 시드가 승인자에 운영자 `sub` 를 넣는 편법(`seed-erp.sh:316-319`) · 콘솔 보정(`TASK-PC-FE-311`). 재측정 중 새로 나온 것 둘: ⓐ 계약 E3(«승인자는 살아 있는 직원», `approval-api.md:323`)를 **코드가 지키지 않는다**(`ApprovalApplicationService.submit` 은 subject 만 확인) — 편법이 가능한 이유 ⓑ 자기결재 금지가 상신자(계정 UUID) 와 승인자(계약상 직원 id)라는 **다른 id 공간**을 비교한다. 선택지 E1(직원이 `account_id` 를 든다) · E2(결재가 `sub` → 직원 매핑) · E3(지금대로) — 기본값 없음.

## AC-3 — 743 닫기 술어 재측정

술어(743 § AC-00): account_db 에서 같은 `email` 이 `fan-platform` 과 `ecommerce` 두 테넌트에 모두 있는 행 수, 데모 시드 제외.

- **정적 = 0** — account-service dev 시드의 소비자 계정은 전부 `consumer-pool`(`R__05` L143·150-171 · `R__06` L140-167). `INSERT … INTO accounts` 전수 grep 의 나머지는 `tests/` 픽스처 · `scripts/console-demo`(배포 시드 아님).
- **라이브 = ⚪** — DB 가 필요하고 이 세션은 데모 호스트를 켜지 않았다(이 티켓만을 위해 켜지 않는다). 측정하려면 위 술어 그대로 account_db 에 `SELECT email FROM accounts WHERE tenant_id IN ('fan-platform','ecommerce') GROUP BY email HAVING COUNT(DISTINCT tenant_id)=2` (데모 시드 이메일 제외).
- ⇒ «대상 없음». Scope 의 «인증된 이메일끼리 자동 묶기» 도 같은 모집단이라 **대상 없음**(ADR D8 — 세 처리 선택지는 되살리는 조건까지 보류).

## 이탈

- 없음 — 티켓의 산출물은 PROPOSED ADR 이고 구현 · ACCEPT 는 하지 않았다. D7(직원 ↔ 계정)은 티켓 원문에 없던 범위지만 착수 지시가 «ADR 이 다뤄야 할 입력» 으로 명시했다.

---

# 닫기 기록 (2026-10-07 UTC, 4차원 검증)

- (a) PR #4205 `state=MERGED`, squash `5a6eb6a81`.
- (b) `origin/main` 이 `5a6eb6a81` 를 포함.
- (c) 머지된 PR 의 `statusCheckRollup` 실패 0 · 대기 0.
- (d) AC 절을 열어 동사대로 읽음: AC-0~AC-3 전부 [x] — 산출물은 `ADR-MONO-080` PROPOSED 이고 ACCEPT 는 이 티켓의 동사가 아니다(소유자 정확형 수락 대기). AC-3 의 라이브 술어 ⚪ 는 «대상 없음(정적 0) + 라이브 SQL 기록» 으로 AC 가 요구한 닫힘 형태다.
