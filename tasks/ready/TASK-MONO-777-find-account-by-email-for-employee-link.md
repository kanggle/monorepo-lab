# Task ID

TASK-MONO-777

# Title

erp 직원 ↔ 계정 연결 제안에서 **이메일로 계정 찾기** — 인사 담당이 UUID 를 직접 입력하지 않게 (iam 읽기 · 콘솔)

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- platform-console
- erp

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 누가 어느 테넌트의 계정을 이메일로 찾을 수 있게 하나(존재 노출 · 범위)가 판정의 전부.

---

# Dependency Markers

- 출처: `TASK-PC-FE-318` AC-0 판단 1 · 후속 후보(2026-10-08 UTC), 소유자 «추천대로 진행».
- 관련: 우산 `TASK-MONO-774`(review) · `TASK-ERP-BE-044`(done — 제안 시 계정 존재를 확인하지 않는다는 소유자 결정 2차).
- ⚠️ 접점(다른 세션 알림, 2026-10-08 UTC): `TASK-MONO-771`(iam 2FA)이 admin-service 게이트에 2FA 요구를 얹을 수 있다 — 이 티켓이 iam 읽기를 쓰거나 더하면 그 게이트와 만난다. 착수 때 771 의 상태를 확인한다.

# Goal

콘솔의 «계정 연결» 대화상자는 지금 **계정 id(UUID)를 직접 입력**받는다(`TASK-PC-FE-318`, `EmployeeAccountLinkDialog.tsx`). 사람이 자기 계정 UUID 를 알 리 없으므로 데모에서 이 기능은 사실상 쓰기 어렵다. 이메일로 계정을 찾아 고르게 한다.

🔴 **PC-FE-318 의 판단과 계약이 어긋난다 — AC-0 이 먼저 잰다.**

| 출처 | 말하는 것 |
|---|---|
| `TASK-PC-FE-318` AC-0 판단 1 | IAM 계정 조회는 별도 IAM 권한이 필요하고, 그 프로필은 403 을 강제 재로그인으로 다룬다 ⇒ 직접 입력으로 갔다 |
| `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/accounts` (`:87-115`) | **`email` 파라미터가 있으면 `account.read` 권한 불필요**(기존 동작 유지) — 단 운영자 토큰(`token_type=admin`) · 해석된 테넌트의 effective scope 게이트는 거친다. `email` 없는 목록만 `account.read` |

⇒ 계약대로라면 **새 iam API 없이** 기존 이메일 단건 검색으로 될 수 있다. 막는 것이 있다면 (a) 테넌트 범위(직원이 속한 erp 테넌트 vs 계정이 사는 풀 — `ADR-MONO-080` «consumer pool» 위의 workforce), (b) 콘솔 쪽 403 처리, (c) erp.write 보유자의 운영자 토큰 모양 중 무엇인지 재야 한다.

# Scope

## In Scope

- AC-0 실측 후 가장 작은 길: 기존 `GET /api/admin/accounts?email=` 재사용이 되면 콘솔 프록시 + 대화상자 «이메일로 찾기» 만. 안 되면 무엇이 막는지(위 a/b/c)를 적고 iam 쪽 최소 변경(계약 먼저)을 고른다 — 새 권한 키가 필요하면 **소유자 결정**으로 멈춘다(권한 행렬은 분류기 게이트 대상이기도 하다).
- 존재 비노출: 찾지 못함과 범위 밖을 같은 문구로.
- 직접 입력은 남긴다(대체 경로).

## Out of Scope

- 제안 시 IAM 존재 확인(소유자 결정 2차 «안 함» 그대로 — 이 티켓은 **고르는 화면**이지 서버 판정이 아니다).
- 승인자 선택기 검색(100 명 한계, 별도 후보).

# Acceptance Criteria

- [ ] **AC-0** — 위 표의 모순을 실측으로 푼다: erp.write 만 가진 콘솔 운영자 토큰으로 `GET /api/admin/accounts?email=` 가 어떻게 답하는지(코드 경로 file:line + 시험), 계정이 사는 테넌트와 erp 테넌트의 관계, 콘솔 403 처리 경로. 결론과 고른 길을 적는다.
- [ ] **AC-1** — 대화상자에서 이메일 입력 → 계정 하나를 골라 제안까지(렌더 DOM 시험).
- [ ] **AC-2** — 없는 이메일 · 범위 밖 이메일 → **같은 문구**(존재 비노출) — 한 시험에서 비교.
- [ ] **AC-3** — 조회가 403/401 이어도 **로그아웃되지 않는다**(PC-FE-318 이 지목한 위험) — 시험으로 고정.
- [ ] **AC-4** — bite: AC-2 의 같은 문구를 깨면 AC-2 칸만 빨강.
- [ ] **AC-5** — 콘솔 `tsc` · `lint` · `vitest` rc=0 · e2e grep · 머지 뒤 nightly 콘솔 확인.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` (D7 = E1)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8
- `projects/iam-platform/specs/services/admin-service/rbac.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/accounts
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` § Employee ↔ IAM account link

# Edge Cases

- 같은 이메일이 여러 테넌트에 — 어느 테넌트의 계정인지 화면이 보여야 한다(직원의 erp 테넌트와 맞는 것만 고를 수 있게).
- 이미 다른 직원에 연결된 계정 — 고를 수는 있되 서버 409 `account_already_linked` 문구로.

# Failure Scenarios

1. 찾지 못함과 범위 밖이 다른 문구 — 이메일로 남의 테넌트 계정 존재를 캐는 길이 된다.
2. 조회 403 이 콘솔 세션을 끊는다 — 인사 담당이 연결 화면에서 로그아웃된다.
3. 계약을 안 읽고 새 iam API 부터 만든다 — 이미 있는 이메일 검색과 겹치는 두 번째 길이 생긴다.
