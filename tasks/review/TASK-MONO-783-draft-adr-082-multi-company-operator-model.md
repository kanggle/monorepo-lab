# Task ID

TASK-MONO-783

# Title

`ADR-MONO-082` 기안 — **한 사람 · 여러 회사의 운영자 모델** (`TASK-MONO-772` OD-1 · `TASK-MONO-773` AC-3 의 선행)

# Status

review

# Owner

monorepo

# Task Tags

- adr
- iam
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (토큰 교환 · RBAC 평가의 뼈대 · 아키텍처 결정)

---

# Dependency Markers

- **선행**: `TASK-MONO-772` OD-1 결정(2026-10-10 UTC, 소유자 «772는 한 사람=한 회사 (추천)») — 772 는 두 번째 회사 수락을 `409 OPERATOR_ALREADY_PROVISIONED` 로 막고, 다회사 모델은 **773 착수 전 ADR** 로 정한다고 적었다.
- **후속**: `TASK-MONO-773` AC-3(«이미 운영자인 계정이 두 번째 회사를 생성») 은 이 ADR 의 ACCEPT 에 묶인다.

# Goal

`ADR-MONO-080` D5 는 «회사 소속 = `admin_operators.tenant_id`» 라 쓰고 772 Edge Case 1 은 «테넌트마다 측면» 을 전제하는데, 코드는 IAM 계정(`sub`) 하나에 운영자 행 하나만 허용한다(772 F2: `V0027` `uk_admin_operators_oidc_subject` 플랫폼 전역 UNIQUE · `TokenExchangeService` 가 `sub` 로 한 행만 찾음 · 역할 바인딩 PK `(operator_id, role_id)`). 한 사람이 여러 회사의 운영자가 되는 모델을 선택지 · 실측 · 추천과 함께 ADR PROPOSED 로 기안하고, 정확형 수락 형식을 명시한다.

# Scope

## In Scope

- `docs/adr/ADR-MONO-082-*.md` PROPOSED — 772 OD-1 의 (b) 한 운영자 행 + 회사별 역할 / (c) 회사마다 행 / 그 밖에 실측이 드러낸 갈래를 비교
- 각 갈래가 바꾸는 것: 스키마(유니크 · PK) · 토큰 교환 · assume · RBAC 평가 · 콘솔 로그인 흐름 · 셀프 온보딩 409(772 S1-11) · 초대 수락 409 · 퇴사(측면 회수) · 감사 · 데모 시드
- `docs/adr/INDEX.md` 행

## Out of Scope

- 구현 — ACCEPT 뒤 단계 티켓(또는 773 의 슬라이스)으로
- 조직 전환 화면 개선(773 Out of Scope 와 같음)

# Acceptance Criteria

- [x] **AC-1** — 772 F2 의 file:line 을 착수 시 재측정해 Context 에 적는다(바뀐 것이 있으면 그대로 적는다). → ADR § Context ①(세 벽 그대로 — `V0027:74` · `TokenExchangeService.java:89-96` · `V0004:48`, 넷째 벽 WI-2 `V0026:20-23` 추가) · ②(두 409) · ③(이미 있는 배정 기전과 그것이 주는 것) · ④(홈을 읽는 판정) · ⑤(Edge Case 2)
- [x] **AC-2** — 갈래마다 바뀌는 표면(스키마 · 교환 · assume · RBAC · 콘솔 흐름 · 온보딩/수락 409 · 회수 · 감사 · 시드)을 표로 비교하고, 추천은 «구현자의 선호» 로 표시한다. → ADR § 갈래 비교(13행 · 데이터 이동 · 크기 포함) · § 두 Edge Case 의 답 · § 추천(«구현자의 선호 — 소유자 결정 아님», b)
- [x] **AC-3** — 정확형 수락 형식(`ADR-MONO-082 ACCEPTED — <갈래>`)을 명시한다. 추천과 같은 선택창 응답은 수락이 아니다. → ADR § 수락 형식(`<a|b|c>` · 선택창 응답 ≠ 수락 · 소유자가 정하는 것 넷)
- [x] **AC-4** — 773 AC-3 이 각 갈래에서 어떻게 닫히는지 한 줄씩 적는다. → ADR D6 표

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D5 · D6 · D9
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D2 · D5
- `docs/adr/ADR-MONO-060-assumed-token-subject-identity.md`
- `tasks/review/TASK-MONO-772-operator-invite-verified-acceptance.md` F2 · OD-1 · S1-11
- `projects/iam-platform/specs/features/multi-tenancy.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § Operator Invitation · `onboarding-api.md` · `platform/contracts/jwt-standard-claims.md`

# Edge Cases

- 같은 사람이 회사 A 에서 퇴사(측면 회수)하고 회사 B 에는 남는다 — B 권한이 그대로여야 한다.
- 플랫폼 관리자(`iam`)이면서 고객사 운영자 — `ADR-MONO-080` D1 «분리 유지» 와 충돌하지 않는가.

# Failure Scenarios

1. 갈래 비교가 스키마만 보고 토큰 교환 · assume 의 «어느 회사로» 질문을 빠뜨린다 — 773 구현 중에 다시 HARDSTOP-09.
2. 추천이 소유자 결정처럼 적힌다 — 귀속 오류(`ADR-MONO-080` § ACCEPT 게이트 기록).

---

# 리뷰 이동 기록 (2026-10-10 UTC)

- PROPOSED PR #4288(`3e0fd511b`) 머지. 소유자 정확형 `ADR-MONO-082 ACCEPTED — b` 도착 → ACCEPT PR 에서 ADR 상태 · INDEX · § ACCEPT 게이트 기록 · § 라이더 대조(R1~R4 기본값) · § ACCEPT 가 만든 새 의무를 적었다.
- 단계 티켓 `TASK-MONO-784`(갈래 b 구현, 선행 772 `done/`) 기안 · `TASK-MONO-773` 에 784 선행과 AC-3 의 b 행 기대값을 더했다.
- AC-1~4 는 PROPOSED PR 에서 닫혔다(위 체크 표시의 근거).
