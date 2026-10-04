# Task ID

TASK-BE-622

# Status

ready

# Title

내부 프로비저닝 상태 변경(`PATCH /internal/tenants/{t}/accounts/{id}/status`)도 풀 멤버에게는 **그 사이트 멤버십만** 바꾼다

# Owner

iam-platform

# Task Tags

- backend
- consumer-pool
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 상태 전이 · 기계 경로의 권한 범위. `TASK-BE-621` 의 설계를 그대로 잇는다. 🔴 데모 반영은 AMI 재굽기 뒤.

---

# Dependency Markers

- **선행**: `TASK-BE-621`(done) — 멤버십 `LOCKED`(V0033) · `SiteMembershipLockUseCase` · 응답 `scope`. `TASK-BE-619`(done) — 운영자 삭제 = `LEFT OPERATOR`.
- **후속**: 없음.

# 소유자 결정 (2026-10-04 UTC, 원문 그대로)

`TASK-BE-621` § 소유자 결정 필요 2 에 대한 답: **«별도 티켓으로 적용 (Recommended)»** — 셀러 정지처럼 사이트 백엔드가 내부 경로(프로비저닝 PATCH)로 계정 상태를 바꾸는 경로에도 «사이트 단위» 규칙을 적용한다. 다음 재굽기에 넣을지는 크기를 보고 정한다.

같은 날 결정 1 의 답: **«계정 전체 유지 (Recommended)»** — security-service 자동 잠금(`AUTO_DETECT`)은 이 티켓의 범위가 아니다(계정 전체 그대로).

# Goal

사이트 백엔드가 자기 테넌트 토큰으로 `PATCH /internal/tenants/{t}/accounts/{id}/status` 를 불러 **소비자 풀 멤버**의 상태를 바꿀 때, 계정 전체가 아니라 **사이트 `t` 의 멤버십**만 바뀐다 — 콘솔 사이트 운영자 경로(621)와 같은 규칙. 풀 멤버가 아닌 사이트 자기 계정(예: 셀러 운영 계정)은 지금처럼 계정이 바뀐다.

# Scope

## In Scope

- account-service 의 프로비저닝 상태 변경 처리: 대상이 풀 계정이고 경로 테넌트 `t` 가 사이트일 때
  - `LOCKED` → 멤버십 `ACTIVE → LOCKED`(621 의 전이 재사용)
  - `ACTIVE` → 멤버십 `LOCKED → ACTIVE`(계정 전체 잠금은 풀지 못한다 — 621 D-3 와 같음)
  - `DELETED` → 멤버십 `LEFT OPERATOR`(619 와 같음; 계정 삭제 아님)
  - 그 밖의 상태 값 → 계약이 정하는 거절(지금 계약 확인 후 결정 — 아래 AC-0)
- 응답에 `scope`(`SITE_MEMBERSHIP` | `ACCOUNT`) — 621 의 `/lock` 응답과 같은 어휘.
- 계약 문서 `account-internal-provisioning.md` 먼저.

## Out of Scope

- 자동 잠금(`AUTO_DETECT`) — 소유자 결정으로 계정 전체 유지.
- 본인 복구 해제(`USER_RECOVERY`).
- 콘솔 화면(목록의 멤버십 상태 표시) · 사이트 앱의 거절 문구 — `TASK-BE-621` § 후속.
- product-service 의 호출 코드 — 셀러 운영 계정은 풀 계정이 아니므로 동작이 바뀌지 않아야 한다(AC-3 대조군).

# Acceptance Criteria

- [ ] **AC-0 (전수 먼저)** — 이 경로의 호출자 전부를 `main` 에서 다시 센다(621 의 표 #4 를 물려받지 않는다): 누가 부르나 · 어떤 상태 값을 보내나 · 대상이 풀 계정일 수 있나. 프로비저닝 계약 파일과 지금 허용 상태 값 목록을 적는다. 풀 계정을 겨누는 호출자가 이미 있으면 그 호출자의 의도(정말 사이트 범위인가)를 적고, 의도가 계정 전체라면 STOP — 소유자 결정.
- [ ] **AC-1 (계약 먼저)** — 계약 문서가 코드보다 먼저(같은 PR, 앞 커밋) 풀 멤버의 사이트 범위 처리 · 응답 `scope` 를 적는다.
- [ ] **AC-2 (풀 멤버)** — 사이트 `ecommerce` 백엔드가 스토어·팬 둘 다 멤버인 풀 계정에 `LOCKED` → 스토어 멤버십 `LOCKED` · 팬 `ACTIVE` · 계정 `ACTIVE` · `account.locked` 0. `ACTIVE` → 멤버십 해제. `DELETED` → 스토어 멤버십 `LEFT OPERATOR` · 계정·팬 그대로.
- [ ] **AC-3 (대조군 — 사이트 자기 계정)** — 셀러 운영 계정(풀 아님)에 같은 호출 → 지금처럼 **계정** 상태가 바뀐다(`scope = ACCOUNT`) — product-service 셀러 정지·폐점 흐름이 그대로.
- [ ] **AC-4 (넘지 못함)** — 경로 테넌트 `t` 의 멤버십이 없는 풀 계정에 대한 호출은 다른 사이트의 멤버십이나 계정을 바꾸지 않는다(계약이 정하는 오류).
- [ ] **AC-5 (시험 · bite)** — 단위 + 슬라이스 + 통합(Testcontainers: 풀 멤버 사이트 범위 · 셀러 운영 계정 대조군). 사이트 갈래를 끄면 정확히 그 칸이 빨개지는 bite 를 기록. 로컬에서 못 돈 통합은 ⚪ 로 적고 CI 로그에서 실제 실행을 확인해 닫는다.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 (621 이 고친 «사이트 잠금 vs 계정 잠금»)
- `projects/iam-platform/specs/services/account-service/architecture.md`
- `projects/iam-platform/specs/services/account-service/data-model.md` § consumer_site_memberships

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (상태 변경 PATCH — AC-0 에서 해당 절과 허용 상태 값을 확인)
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (621 의 `scope` 어휘)

# Edge Cases

1. 풀 멤버가 사이트 `t` 에서 이미 `LEFT` — `LOCKED`/`ACTIVE` 요청은 무변경(되살리지 않는다), `DELETED` 는 멱등.
2. 계정 자체가 이미 `LOCKED`(플랫폼 관리자·자동) — 사이트 범위 `ACTIVE` 는 계정 잠금을 풀지 않는다.
3. 같은 사람이 사이트 `t` 의 자기 계정(풀 아님)도 따로 가진 경우 — 대상 id 로 판별(풀 계정 id 인가).

# Failure Scenarios

1. 셀러 운영 계정까지 «풀 멤버» 로 오판해 셀러 정지가 계정을 안 잠근다 — AC-3 대조군.
2. 사이트 백엔드 한 곳의 호출이 풀 계정 전체를 잠가 그 사람의 다른 사이트까지 막는다(지금의 결함 — 이 티켓이 없애는 것).
3. 통합 시험이 로컬에서 skip 됐는데 «통과» 로 닫는다 — CI 로그의 실행 줄로만 닫는다.
