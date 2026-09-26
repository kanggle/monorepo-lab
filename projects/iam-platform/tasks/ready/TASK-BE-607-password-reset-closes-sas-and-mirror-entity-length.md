# Task ID

TASK-BE-607

# Status

ready

# Title

비밀번호 재설정이 SAS 인가를 닫지 않는다 · 미러 엔티티 길이 선언이 스키마와 어긋난다 · 비밀번호 변경 실패의 실제 영향 확인

# Owner

iam-platform

# Task Tags

- auth-service
- oauth2

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 기존 포트 재사용과 선언 정정. AC-0 판독만 Opus.

---

# Goal

`TASK-BE-604` 가 남긴 셋(§ ⑨-2 · ⑩-4 · ⑩-5 · § CORRECTION 2):

1. **재설정이 SAS 인가를 안 닫는다** — `ConfirmPasswordResetUseCase` 는 미러 행을 계정 UUID 로만 폐기한다. BE-604 로 폐기된 미러 행은 refresh 를 막게 됐지만,
   BE-603 이전의 **이메일 키 미러 행**(배수 기간 최대 30일)을 가진 세션은 재설정 뒤에도 다음 회전 전까지 refresh 된다. 강제 로그아웃과 같은 모양
   (`OAuthAuthorizationRevocationPort` 로 SAS 인가 자체 무효화)이면 배수 기간과 무관하게 닫힌다.
2. **엔티티 길이** — `RefreshTokenJpaEntity` 의 `jti`/`rotated_from` 이 `length = 36`, Flyway `V0014` 는 255. Hibernate DDL 을 쓰는 H2 슬라이스에서 SAS 토큰 INSERT 가 깨진다
   (BE-604 § ⑤ 에서 발견 · 운영은 Flyway 라 무해).
3. **비밀번호 변경 실패의 실제 영향** — BE-604 CORRECTION 2: `Credential.changePassword` 의 `version + 1` 때문에 기존 계정의 `PATCH /api/auth/password` ·
   `POST /api/auth/password-reset/confirm` 이 항상 실패했다(#4033 에서 수정). 배포된 스택(데모)에서 그 실패가 실제로 났는지.

# Scope

## 포함

- 1 구현(포트 재사용) + IT · 2 선언 정정 · 3 측정(데모 창 또는 로그).

## 제외

- 재사용 탐지(`TASK-BE-606`) · 세션 생성 시점 테넌트(`TASK-BE-605`).

# Acceptance Criteria

- [ ] **AC-1** — 재설정 확인이 `OAuthAuthorizationRevocationPort` 로 그 계정의 SAS 인가를 무효화한다 · IT: 재설정 뒤 기존 세션 refresh 400(미러 행 모양과 무관 — 이메일 키 행 픽스처 포함).
- [ ] **AC-2** — `RefreshTokenJpaEntity` 길이 선언 = 스키마(255) · H2 슬라이스에서 SAS 토큰 길이 INSERT 성공 단언.
- [ ] **AC-3** — 🔴 **결과로 판정**: 데모 auth-service 로그/응답에서 비밀번호 변경·재설정 실패 흔적을 찾거나, 없으면 «호출 0 이라 흔적 없음» 인지 «호출됐고 실패» 인지를
      구별해 적는다(0건 ≠ 결함 없음). 재굽기 뒤 데모에서 일회용 계정으로 변경 1회 → 성공(대조군: 수정 전 이미지면 실패).

# Related Specs

- `specs/services/auth-service/data-model.md` · `specs/contracts/http/auth-api.md`
- `TASK-BE-604`(§ ⑨ · ⑩ · CORRECTION) · `TASK-BE-601`(폐기 포트)

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 소셜 전용 계정(자격 행 없음) | 재설정 대상 아님 — 무변경 |

# Failure Scenarios

1. **미러 행만 폐기한다** → 배수 기간 세션이 산다(지금 상태).
2. **AC-3 에서 로그 0건을 «결함 없었다» 로 적는다** → 호출이 없었던 것일 수 있다.
