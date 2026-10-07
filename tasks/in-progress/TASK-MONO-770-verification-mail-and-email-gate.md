# Task ID

TASK-MONO-770

# Title

`ADR-MONO-080` 단계 1 (D3 · R1) — **인증 메일을 실제로 보낸다** + 회사 권한이 붙는 쓰기에 **인증된 이메일 필수** (셀러 구성원 수락 포함)

# Status

in-progress

# Owner

monorepo

# Task Tags

- iam
- ecommerce
- security
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한이 붙는 쓰기의 게이트 · 새 장애 지점 = 메일 발송)

---

# Dependency Markers

- **선행**: `ADR-MONO-080` ACCEPTED — A · 직원연결 E1 · 테넌트생성 T1 (2026-10-07 UTC).
- **후속**: `TASK-MONO-771`(단계 2) · `TASK-MONO-772`(단계 3)는 이 티켓이 `done/` 이어야 착수한다(ADR-080 D2).

# Goal

IAM 이 이메일 소유를 확인하게 한다. 두 부분이고 **순서가 있다**:

1. **메일이 실제로 나가야 한다.** 지금 구현체는 `LoggingEmailVerificationNotifier`(`!prod`, 토큰도 안 남김) 하나이고 SMTP·SES 어댑터가 0 이다(ADR-080 Context). prod 발송 어댑터 + 데모에서 사람이 그 메일을 **받아 볼 수 있는** 경로를 연다.
2. 그다음 **게이트**: 풀 계정에 회사 권한(운영자 측면 · 협력사 참여자 · 셀러 구성원 역할)이 **붙는 쓰기**는 `email_verified_at` 이 있어야 한다. 로그인 자체에는 걸지 않는다(소비자 이용 불변).

라이더 R1(기본값): `ADR-MONO-079` D5 셀러 구성원 수락에도 적용한다 — 지금 «초대 이메일 == 로그인 이메일» 만 보고 인증 여부를 안 본다(`ConsumerSiteRoleWriteUseCase.java:44-47, 78-83`).

# Scope

## In Scope

- auth/account-service: prod 프로필 메일 발송 어댑터(SMTP 또는 SES — 착수 시 결정, 계약 먼저) · 데모 수신 경로(예: 데모 전용 메일함 화면 또는 Mailpit 류 — 착수 시 결정) · 발송 실패의 분류(일시/영구)
- 게이트: 셀러 구성원 수락(지금 구현됨) · 이후 단계 3 의 운영자 초대 수락 · 협력사 참여자 붙이기 — 이 티켓은 **지금 존재하는 쓰기**(셀러 수락)에 걸고, 나머지는 공용 술어로 만들어 772 가 재사용
- 화면: 인증 안 된 계정이 수락하려 할 때 «이메일 인증 필요 + 재발송» 안내 · 발송 실패 표기(ADR-080 § 새로 생기는 위험)
- 계약 갱신: `account-api.md`(인증 메일 발송) · 셀러 수락 계약의 새 거절 코드

## Out of Scope

- 로그인에 인증 필수(ADR-080 Alternatives 에서 기각)
- 2단계 인증(771) · 운영자 규칙(772)

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: 발송 어댑터 0 · `email_verified_at` 을 읽는 곳(로그인·초대·운영자 생성 0) · 셀러 수락 술어 file:line. 발송 수단(SMTP/SES)과 데모 수신 경로를 정하고 이유를 적는다.
- [ ] **AC-1** — prod 프로필 기동 시 발송 어댑터가 빈으로 뜬다(지금은 fail-fast). 데모에서 가입 → 인증 메일 수신 → 링크 → `email_verified_at` 기록을 끝까지 한 번 돈다(라이브 ⚪ 가능).
- [ ] **AC-2** — 🔴 대조군: 남의 이메일로 가입한(인증 안 된) 풀 계정이 그 이메일로 온 셀러 초대를 **수락하지 못한다**(실패 쪽을 먼저 단언) · 같은 시험에서 인증 후 수락은 된다.
- [ ] **AC-3** — 소비자 로그인 · 쇼핑 · 팬 이용은 인증 여부와 무관하게 그대로(회귀 시험).
- [ ] **AC-4** — 발송 실패 시 화면이 «권한을 못 받음» 이 아니라 «메일을 보내지 못했다 · 재시도» 를 말한다.
- [ ] **AC-5** — 공용 술어(예: `requireVerifiedEmail(account)`)가 하나의 집에 있고, 772 가 운영자 초대 수락에 그대로 쓴다는 것을 이 티켓의 노트에 적는다.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D2 · D3 · R1 · § Verification
- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D5 · § 부분 개정 (2026-10-07)
- `projects/iam-platform/specs/features/signup.md` (이메일 검증 — 초기 선택사항)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/account-api.md` · 셀러 구성원 수락 계약(ecommerce product-service · IAM 사이트 역할 쓰기)

# Edge Cases

- 이미 셀러 구성원인데 인증 안 된 계정 — 소급해 회수하지 않는다(«인증은 붙일 때의 증거일 뿐 유지 조건이 아니다», ADR-080 D5).
- 재발송 남용 — 기존 재발송 거절(`SendVerificationEmailUseCase.java:81`)과 속도 제한을 확인한다.

# Failure Scenarios

1. 게이트만 켜고 발송 경로가 없다 — 아무도 셀러가 되지 못한다(ADR-080 D3 «게이트는 옳은데 길이 없는 상태»).
2. 로그인에 게이트를 건다 — 메일이 안 나가는 동안 모든 소비자가 막힌다.
