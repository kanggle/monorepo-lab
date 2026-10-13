# Task ID

TASK-BE-629

# Title

IdP 화면마다 이메일 가림 규칙이 다르다 — 초대 수락 `h*****@demo.com` · 이메일 인증 `h***@demo.com`

# Status

done

# Owner

iam-platform

# Task Tags

- iam
- auth-service
- ux

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku — 가림 함수 하나로 모으는 작은 정리(AC-0 이 함수 위치를 정한다).

---

# Dependency Markers

- 출처: 26차 데모 창(2026-10-11 UTC) — `TASK-MONO-772` 라이브 확인 중. 같은 계정 `hire1@demo.com` 이 초대 화면에선 `h*****@demo.com`, 이메일 인증 화면에선 `h***@demo.com` 으로 보였다.
- 관련: `TASK-MONO-772`(초대 미리보기 마스킹 — admin `OperatorInvitationAcceptanceUseCaseTest` 의 `p*****@` 단언) · `TASK-MONO-770`(인증 화면).

# Goal

같은 사람이 한 흐름(초대 → 인증 → 수락) 안에서 두 가지 가림을 본다. 보안상 결함은 아니지만(둘 다 로컬 파트 첫 글자만 드러냄), 같은 계정인지 사용자가 의심하게 만들고 «가림 규칙» 이 두 곳에 산다(한쪽만 고쳐지는 구조).

# Scope

## In Scope

- **AC-0** — 가림을 하는 곳 전수(auth-service 페이지 컨트롤러 · admin 초대 미리보기 · 메일 본문) file:line, 각 규칙(고정 별 개수 / 길이 비례). 어느 쪽을 정본으로 할지 적는다(🔵 고정 개수가 길이를 덜 드러낸다).
- 한 규칙으로 맞추고, 남는 곳은 그 함수를 쓰게 한다(서비스가 다르면 같은 규칙을 시험으로 고정).

## Out of Scope

- 무엇을 가릴지(도메인 노출 여부) 자체의 정책 변경.

# Acceptance Criteria

- ✅ **AC-0** — 위 전수와 정본 선택.
  - **초대 화면 (admin-service)**: `admin-service/src/main/java/com/example/admin/application/OperatorInvitationAcceptanceUseCase.java:371-379` — 5개 고정별 `p*****@example.com` (로컬 파트 길이 미노출)
  - **이메일 인증 화면 (auth-service)**: `auth-service/src/main/java/com/example/auth/presentation/EmailVerificationPageController.java:135-144` — 3개 고정별 `a***@example.com` (로컬 파트 길이 미노출)
  - **감사 로그 (admin-service outbox)**: `admin-service/src/main/java/com/example/admin/infrastructure/outbox/OutboxAdminEventPublisher.java:97` — PiiMaskingUtils 사용 → 3개 고정별
  - **로그 마스킹 (auth-service)**: `auth-service/src/main/java/com/example/auth/infrastructure/email/LoggingEmailSender.java:101-119` — 3개 고정별 (dev stub)
  - **MFA 화면 (auth-service)**: `auth-service/src/main/java/com/example/auth/presentation/MfaPageController.java:314` — EmailVerificationPageController.mask 사용 → 3개 고정별
  - **정본 선택**: 🔵 별 3개 고정(`j***@example.com`) = `libs/java-security/src/main/java/com/example/security/pii/PiiMaskingUtils.java:35-40`. 근거: 감사 로그 · 인증 화면 · MFA 화면이 이미 이 규칙이고 공용 함수가 있다. 별 5개도 고정 개수라 길이를 드러내지 않는다는 점은 같다 — 고른 이유는 «이미 다수 + 공용 함수», 노출량 차이가 아니다.
  - **짧은 로컬 파트**: 1자 `a@x.com` → `a***@x.com` — 그 한 글자는 보이지만, 별이 고정이라 «로컬 파트가 그 한 글자뿐» 이라는 사실은 드러나지 않는다. 2자 `ab@x.com` → `a***@x.com`.
  - 🔴 **공용 함수는 주소가 아닌 입력(`@` 없음 · 맨 앞)을 원문 그대로 돌려준다**(`PiiMaskingUtils.java:38`). 화면 쪽 두 함수는 그 경우를 공용 함수에 넘기지 않는다 — admin `***`(전엔 `*****`), auth `null`(전과 같음). 가림은 실패하면 더 가리는 쪽이어야 한다. 공용 함수 자체의 동작은 다른 호출처(감사 로그)가 있어 이 티켓에서 바꾸지 않았다.

- ✅ **AC-1** — 초대 화면 · 인증 화면 · (있으면) 메일 본문이 같은 입력에 같은 가림을 낸다(단위 시험 — 짧은 로컬 파트 1~2자 포함).
  - 두 화면 함수가 모두 `PiiMaskingUtils.maskEmail` 에 위임한다 — admin `OperatorInvitationAcceptanceUseCase.maskEmail` · auth `EmailVerificationPageController.mask`(MFA 화면도 이것을 쓴다).
  - 서비스가 달라 호출 지점을 하나로 못 모으므로, **같은 입력 · 같은 기대값**을 양쪽에 고정했다: admin `OperatorInvitationAcceptanceUseCaseTest.MaskEmailEdgeCases`(표준 · 1자 · 2자 · `@` 없음 · `@` 맨 앞 · null) / auth `EmailVerificationMaskTest`(표준 · `hire1@demo.com`→`h***@demo.com` · 1자 · 2자 · 주소 아님).
  - bite: `@` 없음 · 맨 앞 두 셀은 공용 함수에 그대로 위임하던 중간 판(`noemailaddress` → 원문)에서 빨강이다.
  - 메일 본문: 초대 · 인증 메일 본문에는 가린 주소가 없다(받는 사람 본인에게 가므로) — 해당 없음.

- ✅ **AC-2** — 772 의 마스킹 단언(`p*****@`)이 정본과 다르면 그 시험을 정본 기대값으로 고친다(걷지 않는다).
  - `OperatorInvitationAcceptanceUseCaseTest.java:523` — `p*****@example.com` → `p***@example.com` 로 업데이트 완료. 통합 테스트 · 컨트롤러 슬라이스 · auth-service 클라이언트 테스트도 함께 업데이트.

# Related Specs

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Operator Invitation(미리보기)

# Related Contracts

- 위와 같음(미리보기 응답의 마스킹 필드)

# Edge Cases

- 로컬 파트 1자(`a@x.com`) — 첫 글자를 보여 주면 전부 드러난다. 정본 규칙이 이것을 어떻게 다루는지 적는다.

# Failure Scenarios

1. 한 화면만 고친다 — 다음에 세 번째 화면이 생기면 또 갈라진다(규칙 한 곳 + 시험 고정이 이 티켓의 요점).

---

# 완료 기록 (2026-10-13 UTC)

- impl PR **#4329**, 스쿼시 **`7b115c60e`**(2026-10-13T15:08:22Z) — `origin/main` 에 있음 · 머지 시점 `statusCheckRollup` 실패 0 · 대기 0(`Build & Test` · `Integration (iam A/B)` · `E2E smoke (iam docker-compose)` 포함 초록).
- AC 절을 열어 읽음: AC-0 · AC-1 · AC-2 모두 ✅, 라이브 ⚪ 없음(화면 가림은 단위 시험으로 판정되는 AC 들).
- PR 안의 검토 수정(에이전트 초안 뒤): 주소 아닌 입력을 공용 함수에 넘기면 원문이 나오던 것 → 화면 쪽에서 막음 · auth 인증 화면을 공용 함수 위임 + 같은 기대값 시험(`EmailVerificationMaskTest`).
- 후속(AC 아님): 공용 `PiiMaskingUtils.maskEmail` 자체는 주소 아닌 입력을 원문 그대로 돌려준다(`PiiMaskingUtils.java:38`) — 감사 로그 호출처가 있어 이 티켓에서 바꾸지 않았다.

