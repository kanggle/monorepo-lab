# Task ID

TASK-BE-614

# Status

in-progress

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
- **후속**: `TASK-BE-615`(로그인 + 플래그 켜기) · `TASK-BE-616` · `TASK-MONO-743` · `TASK-BE-618`(기존 한 사이트 계정의 같은 id 이동 — 이 티켓에서 분리)

# Goal

`TASK-MONO-742` 가 정한 저장 모양으로 소비자 계정 풀을 만들고, 소비자 client(팬·스토어)에서 새로 가입하는 사람이 **풀 계정**으로 태어나게 한다. 기존 사이트별 계정은 손대지 않는다 — 그 이동은 `TASK-BE-618` 이다.

🔴 **기능 플래그 뒤에, 기본값 꺼짐** (착수 시 정정, 2026-10-01): 이 티켓만 머지되면 새 가입자는 풀에 생기는데 풀 자격으로 로그인하는 경로는 `TASK-BE-615` 가 만든다 — 그 사이 main 에서 **가입한 사람이 로그인을 못 한다**. 그래서 풀 가입 경로는 `iam.consumer-pool.enabled`(기본 `false`) 뒤에 둔다. 꺼져 있으면 동작이 바이트 그대로 옛 규칙이고, 615 가 로그인과 함께 켠다. 실패는 «옛 동작» 쪽으로 떨어져야 한다.

# Scope

## In Scope

- account-service / auth-service 마이그레이션: 풀 계정 · 사이트 멤버십(사이트 테넌트 · 동의 시각) · 사이트별 역할의 저장
- 가입 경로(`SignupPageController` → 계정 생성): 소비자 client 에서 오면 풀로 — **플래그가 켜졌을 때만**
- 사이트 테넌트로 계정을 찾는 표면(`multi-tenancy.md` § 소비자 계정 풀 § 5 — `/internal/tenants/{t}/accounts` 목록·검색, 그것을 쓰는 admin-service 운영자 생성 확인)에 그 사이트 멤버 풀 계정 포함
- `TASK-MONO-742` AC-3 이 고른 이벤트 발행

## Out of Scope

- 로그인·토큰 발급(`TASK-BE-615`), 동의 화면(`TASK-BE-616`), 묶기(`TASK-MONO-743`)
- 기존 한 사이트 계정의 같은 id 이동(`TASK-BE-618`)

# Acceptance Criteria

- [ ] **AC-1** — 새 마이그레이션만 추가한다. 적용된 마이그레이션 파일은 고치지 않는다(Flyway 체크섬 — 기존 볼륨 기동 실패).
- [ ] **AC-2** — 🔴 마이그레이션이 **기존 볼륨**(현재 main 까지 적용된 DB)에 적용된다 — Testcontainers 로 이전 버전까지 올린 뒤 새 버전 적용. 불가하면 ⚪ «못 쟀다, 이유».
- [ ] **AC-3** — 소비자 client 로 가입한 계정은 풀에 생기고, 같은 이메일의 **기존 사이트별 계정이 있어도** 그것과 자동으로 묶이지 않는다(`ADR-MONO-078` D2) — 시험으로.
- [ ] **AC-4** — 콘솔(`iam`) 가입·운영자 계정 경로는 바뀌지 않는다(D1) — 기존 시험 그대로 초록.
- [ ] **AC-6 (구현자 기본값 — 소유자가 뒤집을 수 있다)** — 운영자 측면(셀러 · 셀프 온보딩 운영자)이 붙어 **아직 옮기지 않은** 사이트 계정의 이메일로 소비자 client 풀 가입이 오면 **거절**한다(`multi-tenancy.md` § 소비자 계정 풀 § 3). 대조 시험: 셀러 계정이 있는 이메일로 팬 풀 가입 → 거절, 셀러 계정은 그대로 로그인된다. 🔵 2026-10-01 소유자가 «셀러를 풀에 포함» 을 결정해 이 거절은 **임시**다 — 셀러는 `TASK-MONO-745`, 셀프 온보딩 운영자는 `ADR-MONO-080` 후보(`TASK-MONO-746`)에서 풀로 옮겨지며 그때 사라진다.
- [ ] ~~**AC-7**~~ — **`TASK-BE-618` 로 이관**(착수 시 정정 2026-10-01: Goal 은 «기존 계정 무변경» 인데 이 AC 는 기존 계정 이동을 시험했다 — 기안자의 모순. 이동은 두 DB 에 걸친 별도 작업이다).
- [ ] **AC-8** — 🔴 `iam.consumer-pool.enabled` 가 **꺼져 있으면**(기본) 가입·조회·이벤트가 이 티켓 이전과 같다 — 기존 가입·로그인 시험이 기대값 변경 없이 초록. 켜졌을 때의 동작은 AC-3·AC-6·AC-9·AC-10 이 잰다.
- [ ] **AC-9** — 사이트 테넌트로 계정을 찾는 표면(§ 5)이 그 사이트 멤버 풀 계정을 포함한다: `ecommerce` 목록·이메일 검색에 풀 가입 쇼핑객이 나온다. 🔴 대조군: 멤버십 **없는** 사이트로는 나오지 않는다(풀 가입 팬이 `ecommerce` 목록에 없다).
- [ ] **AC-10** — `account.created` 가 풀 가입 시 **가입한 사이트 테넌트**로 1회 나간다(`tenantId` ≠ `consumer-pool` 단언).
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
