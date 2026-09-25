# Task ID

TASK-BE-602

# Status

ready

# Title

소셜 로그인은 계정의 **실제 테넌트**를 모른다 — 스토어 소셜 계정은 잠겨도 들어오고, 소셜 로그인 이벤트도 못 낸다

# Owner

iam-platform

# Task Tags

- auth-service
- social-login
- multi-tenant
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — «이 계정의 테넌트는 어디서 믿고 가져오나» 가 설계 결정이다.

---

# Goal

두 후속이 **같은 한 질문**에 막혀 있다: 소셜 로그인 경로에서 **계정의 실제 테넌트**를 어디서 얻는가.

1. **잠금 효력** (`TASK-BE-600` 후속) — BE-600 이 상태 조회에 `X-Tenant-Id` 를 붙여 폼 로그인은 모든 테넌트에서 잠금이 효력을 갖게 됐다.
   소셜 경로는 **여전히 테넌트를 안 보낸다** ⇒ account-service 가 fan-platform 으로 읽고, BE-507 이후 `ecommerce` 등에서 만든 소셜 계정은
   **404 → 상태 검사 생략(설계상 유지)** ⇒ **잠긴 스토어 소셜 계정이 소셜로 들어온다**.
2. **소셜 로그인 이벤트** (`TASK-BE-599` 후속) — 소셜 로그인은 `auth.login.*` 을 내지 않는다. BE-599 가 넣지 않은 이유:
   이벤트의 `tenantId` 를 시작 client 에서 가져오면 BE-507 이전 계정의 실제 테넌트와 다를 수 있고(틀린 테넌트 키로 VELOCITY 가 센다),
   소셜 실패의 대부분은 `emailHash` 가 없어 계약(`emailHash` 필수)과 맞지 않으며, `loginMethod` 값 매핑도 정해야 한다.

🔵 **소유자 결정 (2026-09-25 UTC)** — 둘을 **한 티켓**으로 기안(같은 테넌트 결정에 묶여 있다).

## 실측 (2026-09-25 UTC · 저장소만 · BE-599/600 보고 기준)

| 사실 | 근거 |
|---|---|
| 소셜 상태 조회는 테넌트 없이 부른다 | `OAuthLoginUseCase.java` 상태 조회 · BE-600 이 empty 를 «404 만» 으로 좁혔다(실패는 fail-closed) |
| 404 를 거부로 바꾸면 안 된다 | BE-507 이후 `ecommerce` 소셜 계정이 fan-platform 조회에서 404 — 거부로 바꾸면 **스토어 소셜 로그인 전부 차단**(BE-600 보고) |
| 폼 경로는 해결됐다 | BE-600: 폼 로그인은 자격 행의 테넌트로 `X-Tenant-Id` 를 보낸다 |
| ⚪ 미측정 | ① 소셜 신원(`social_identities` 등) 행이 계정의 테넌트를 들고 있는가 ② BE-507 이전 계정의 테넌트 값 실태(마이그레이션 여부) ③ 소셜 실패 유형별로 `accountId`·`emailHash` 를 알 수 있는 지점 |

# Scope

## 포함

- **AC-0 결정**: 소셜 경로의 «계정의 실제 테넌트» 출처(후보: 소셜 신원 행 · 자격 행 · account-service 역조회 · 시작 client). 🔴 소유자 결정 대상.
- 그 출처로 ① 상태 조회에 `X-Tenant-Id` ② 소셜 로그인 `auth.login.*` 발행(`loginMethod` 매핑 · `emailHash` 없는 실패의 계약 처리).

## 제외

- 폼 로그인(해결됨). 디바이스 세션(BE-599 에서 철회 — 기기 식별 쿠키 후속).

# Acceptance Criteria

- [ ] **AC-0** — ⚪ 셋 재측 → 테넌트 출처 후보 표(정확성 · BE-507 이전 계정 · 비용) → 🔴 **소유자 결정**.
- [ ] **AC-1** — 상태 조회에 실제 테넌트 전달 · 단위 테스트: 스토어 소셜 계정 LOCKED → 거부 · 신규 소셜 가입(404) → 통과 · 조회 실패 → fail-closed(BE-600 규칙 유지).
- [ ] **AC-2** — 소셜 로그인 이벤트 발행 — 🔴 계약에 없는 값이 필요하면 **계약부터**(`loginMethod`, `emailHash` 선택화 여부).
- [ ] **AC-3** — 🔴 결과로 판정: 스토어 테넌트 일회용 소셜 계정을 잠그고 소셜 로그인 → 거부 · 대조군(잠그기 전 성공). 소셜 공급자 없이 재현 가능한지부터(e2e 스텁).

# Related Specs

- `specs/features/oauth-social-login.md` · `specs/contracts/events/auth-events.md` · `specs/contracts/http/internal/auth-to-account.md`
- `TASK-BE-599` (소셜 이벤트 후속) · `TASK-BE-600` (소셜 테넌트 후속) · `TASK-BE-507` (테넌트별 소셜 계정)

# Related Contracts

- `GET /internal/accounts/{id}/status` — BE-600 이 추가한 선택 `X-Tenant-Id` 를 소셜 경로도 쓴다(계약 변경 없음).
- `auth.login.*` — 필요 시 계약 변경은 AC-2 에서 먼저.

# Edge Cases

| 상황 | 기대 |
|---|---|
| BE-507 이전 계정(테넌트 값이 fan-platform 으로 굳은) | AC-0 의 출처가 그 계정도 맞게 답하는가 — 표에 칸으로 |
| 한 소셜 신원이 여러 테넌트 계정에 연결 | 존재하는가부터(AC-0) — 있으면 어느 계정으로 로그인하는지가 먼저 정해져 있어야 한다 |

# Failure Scenarios

1. **시작 client 의 테넌트를 믿는다** → BE-507 이전 계정에 틀린 테넌트 → 잠금 누락 · VELOCITY 오집계.
2. **404 를 거부로 바꾼다** → 스토어 소셜 로그인 전면 차단.
3. **이벤트를 계약 밖 값으로 낸다** → 소비자가 DLQ 로 보낸다(테넌트·필드 규칙).
