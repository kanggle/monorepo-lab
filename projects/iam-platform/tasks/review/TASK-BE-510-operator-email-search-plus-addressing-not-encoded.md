# Task ID

TASK-BE-510

# Title

운영자 계정 검색이 `+` 포함 이메일을 못 찾음 — admin→account 아웃바운드 URI 미인코딩

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# 🔴 발견 경위 (2026-07-15 라이브 풀스택 스윕)

`GET /api/admin/accounts?email=...` 로 방금 만든 계정을 검색했는데 **빈 결과(200, totalElements=0)**. 처음엔 "검색 전면 불능" 으로 의심했으나, **평문 이메일은 정상 검색**되고 **`+` 를 포함한 이메일만** 실패함을 격리 실험으로 확인했다(과대보고 직전 정정). 아래는 실측 — **착수 시 재현부터.**

---

# Goal

`+`(plus-addressing) 를 포함한 이메일 주소로도 운영자 계정 검색이 정상 동작하게 한다.

## Root Cause (실측, 재검증 대상)

`apps/admin-service/src/main/java/com/example/admin/infrastructure/client/AccountServiceClient.java` 의 `search(tenantId, email)` 가 RestClient `uriBuilder.queryParam("email", email)` 로 아웃바운드 URI 를 만든다. 이때 이메일의 `+` 가 **퍼센트 인코딩되지 않고 리터럴 `+`** 로 전송되어, 수신측 account-service 가 쿼리스트링 규칙대로 `+` 를 **공백**으로 디코드한다 → `foo+bar@x.com` 이 `foo bar@x.com` 으로 조회되어 매치 실패.

## 격리 실험 결과 (2026-07-15)

| 경로 | 이메일 | 결과 |
|---|---|---|
| account-service 직접 `/internal/accounts?email=...&tenantId=fan-platform` | `probe2+...@x.com` | **200, 1건** (직접 호출은 클라이언트가 URL 인코딩) |
| admin `/api/admin/accounts?email=...` | 평문 `plainuser...@x.com` | **200, 1건** ✅ |
| admin `/api/admin/accounts?email=...` | `plus+...@x.com` | **200, 0건** ✗ |

→ admin→account **아웃바운드** 인코딩만의 문제. account-service 검색 로직·테넌트 해소·권한은 모두 정상.

# Scope

## In Scope
- `AccountServiceClient.search` 의 `email` 쿼리 파라미터를 **명시적으로 인코딩**하여 `+` 등 예약문자가 보존되게 한다(예: `UriComponentsBuilder` 인코딩 모드 지정, 또는 이미 인코딩된 값 전달, 또는 값 인코딩 후 `build(true)`).
- 같은 클래스/다른 아웃바운드 호출에서 **동일 패턴이 있는지 전수 확인**(email 뿐 아니라 사용자 입력이 쿼리로 나가는 모든 경로 — `+`, 공백, `&`, `%`).

## Out of Scope
- account-service 검색 로직 자체(정상).
- 이메일 정규화 정책(소문자화 등 — signup.md 소관, 무관).

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: `+` 포함 이메일로 signup 후 `GET /api/admin/accounts?email=` 가 **현재 0건** 이고 평문은 1건임을 재현.
- [ ] `+` 포함 이메일 검색이 해당 계정을 **정확히 반환**.
- [ ] 평문 이메일 검색이 **여전히 정상**(회귀 0).
- [ ] `&`, 공백, 유니코드 등 다른 예약/특수문자 이메일도 라운드트립(대표 케이스 1~2개 확인).
- [ ] admin-service 단위/통합 테스트에 `+` 포함 이메일 검색 케이스 추가.
- [ ] `:check` GREEN.

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0.

- `specs/features/operator-management.md`
- `specs/features/signup.md` (이메일 형식 — plus-addressing 은 RFC 5322 유효)

# Related Contracts

- `specs/contracts/http/admin-api.md` (`GET /api/admin/accounts`)
- `specs/contracts/http/internal/admin-to-account.md` (`GET /internal/accounts?email=`)

# Target Service

- `admin-service`

# Edge Cases

- 이미 인코딩된 값을 다시 인코딩하는 이중 인코딩 방지(`build(true)` vs `build(false)` 선택 주의).
- account-service 수신측이 `+`→공백 디코드를 하는 것은 **표준 쿼리스트링 동작** — 수정은 송신측(admin)에서. 수신측을 바꾸면 다른 소비자와 어긋난다.

# Failure Scenarios

- `build(true)` 로 바꾸면서 tenantId 등 다른 파라미터가 이미 인코딩 안 된 상태면 그쪽이 깨질 수 있음 — 전체 파라미터 인코딩 일관성 확인.
- 이중 인코딩 시 `%2B` 가 그대로 검색되어 여전히 매치 실패.

# Definition of Done

- [ ] AC-0 재측정
- [ ] `+`/특수문자 이메일 검색 정상, 평문 회귀 0
- [ ] 테스트 추가, `:check` GREEN
- [ ] Ready for review
