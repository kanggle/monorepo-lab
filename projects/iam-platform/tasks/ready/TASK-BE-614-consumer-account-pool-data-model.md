# Task ID

TASK-BE-614

# Status

ready

# Title

전역 소비자 계정 2단계 — 소비자 계정 **풀** 데이터 모델 + 새 가입은 풀로 (`ADR-MONO-078` A)

# Owner

iam-platform

# Task Tags

- account-service
- auth-service
- migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (UNIQUE 제약·마이그레이션 — 기존 볼륨에서 깨지면 기동 실패)

---

# Dependency Markers

- **선행**: `TASK-MONO-742`(저장 모양 결정 AC-4 · 이벤트 결정 AC-3)
- **후속**: `TASK-BE-615` · `TASK-BE-616` · `TASK-MONO-743`

# Goal

`TASK-MONO-742` 가 정한 저장 모양으로 소비자 계정 풀을 만들고, 소비자 client(팬·스토어)에서 새로 가입하는 사람이 **풀 계정**으로 태어나게 한다. 기존 사이트별 계정은 손대지 않는다(묶기 전까지 옛 규칙).

# Scope

## In Scope

- account-service / auth-service 마이그레이션: 풀 계정 · 사이트 멤버십(사이트 테넌트 · 동의 시각) · 사이트별 역할의 저장
- 가입 경로(`SignupPageController` → 계정 생성): 소비자 client 에서 오면 풀로
- `TASK-MONO-742` AC-3 이 고른 이벤트 발행

## Out of Scope

- 로그인·토큰 발급(`TASK-BE-615`), 동의 화면(`TASK-BE-616`), 묶기(`TASK-MONO-743`)

# Acceptance Criteria

- [ ] **AC-1** — 새 마이그레이션만 추가한다. 적용된 마이그레이션 파일은 고치지 않는다(Flyway 체크섬 — 기존 볼륨 기동 실패).
- [ ] **AC-2** — 🔴 마이그레이션이 **기존 볼륨**(현재 main 까지 적용된 DB)에 적용된다 — Testcontainers 로 이전 버전까지 올린 뒤 새 버전 적용. 불가하면 ⚪ «못 쟀다, 이유».
- [ ] **AC-3** — 소비자 client 로 가입한 계정은 풀에 생기고, 같은 이메일의 **기존 사이트별 계정이 있어도** 그것과 자동으로 묶이지 않는다(`ADR-MONO-078` D2) — 시험으로.
- [ ] **AC-4** — 콘솔(`iam`) 가입·운영자 계정 경로는 바뀌지 않는다(D1) — 기존 시험 그대로 초록.
- [ ] **AC-6 (미결 — 구현 전에 답하고 기록)** — 운영자 측면(셀러 · 셀프 온보딩 운영자)이 붙어 **옮기지 않는** 사이트 계정의 이메일로 소비자 client 풀 가입이 오면 거절할지 받을지(`multi-tenancy.md` § 소비자 계정 풀 § 3 ⚪). 고르고 근거·대조 시험을 적는다.
- [ ] **AC-7** — 사이트별 계정을 같은 id 로 풀로 옮길 때 운영자 측면이 붙은 계정(셀러 · 셀프 온보딩 운영자)은 **제외**되고, 팬 `ARTIST` 역할은 `consumer_site_roles` 로 옮겨진다 — 시험으로.
- [ ] **AC-5** — 테넌트 누출 시험(`multi-tenancy.md:380-395` 규칙)이 풀 계정에 대해 무엇을 보장하는지 갱신하고 초록.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md`(`TASK-MONO-742` 갱신본)
- `projects/iam-platform/specs/services/auth-service/data-model.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md`(`TASK-MONO-742` 갱신본)

# Edge Cases

- 풀 계정과 같은 이메일의 **기존** 사이트 계정이 공존하는 동안 로그인 조회가 어느 쪽을 고르는가 — `TASK-BE-615` 에 넘기되 이 단계의 데이터가 그 구분을 표현할 수 있어야 한다.

# Failure Scenarios

1. UNIQUE `(tenant_id,email)` 를 풀어 기존 사이트 계정과 풀 계정이 **같은 행**으로 섞인다.
2. 신선 볼륨 CI 만 보고 기존 볼륨에서 실패하는 마이그레이션이 머지된다.
