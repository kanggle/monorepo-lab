# Task ID

TASK-BE-616

# Status

in-progress

# Title

전역 소비자 계정 4단계 — 사이트 **첫 방문 동의** 화면 + 사이트 멤버십·역할 · 로그인 화면 브랜드 문구 (`ADR-MONO-078` A · D3)

# Owner

iam-platform

# Task Tags

- auth-service
- ui
- oidc

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (화면 + 멤버십 기록 — 흐름은 `TASK-BE-615` 가 만들었다)

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615`

# Goal

풀 계정이 처음 가는 소비자 사이트에서 **이용 동의 한 화면**을 보고, 동의하면 그 사이트 멤버십과 사이트 역할(팬=FAN, 스토어=CUSTOMER)이 생긴 뒤 토큰이 발급되게 한다. 가입 폼도 비밀번호도 없다. 소유자가 고른 «가입 안내»(①)를 이 화면이 대체한다.

# Scope

## In Scope

- authorize 흐름 안의 동의 화면(풀 계정 + 그 사이트 멤버십 없음일 때)
- 동의 기록(사이트 테넌트 · 시각) + 사이트 역할 시드(`RoleSeedPolicy` 와 정합)
- 로그인·동의 화면의 브랜드 문구

## Out of Scope

- 약관 본문(저장소에 없다 — 데모 문구)

# 착수 시 결정 (2026-10-01 UTC)

- **AC-4 소유자 결정 — 계정 이름 하나로: «IAM 로그인»** (소유자 원문 «A. 계정 이름 하나로 통일 / IAM 로그인»). 🔵 착수 시 확인: 팬은 **이미** 2026-09-29 에 «GAP» → «IAM» 으로 바뀌어 있었다(`ADR-007` § 값 변경 2026-09-29) — 바꿀 것은 **스토어** 하나다(«Global Account» → «IAM»). 기록: `ADR-007` § 값 변경 (2026-10-01). 색·로고·설명 문구는 사이트별 유지, 이름만 하나.
- 🔴 **스토어 이름 변경과 플래그 켜기(AC-6)는 같은 PR** — «같은 이름인데 다른 계정» 기간을 만들지 않는다.
- **선행 확인 — security-service 자동 잠금과 `consumer-pool`**(`TASK-BE-615` 검토 절의 후속, 착수 전 확인): 자동 잠금은 `X-Tenant-Id` 를 **보내지 않는다**(`TASK-MONO-735`) → account-service 가 계정 id 로 찾으므로 풀 계정도 잠긴다 ✅. 탐지 규칙의 `(tenantId, accountId)` 는 풀 계정이 어느 사이트에서든 `consumer-pool` 로 찍혀 **사람 단위로** 일관되게 센다 ✅. ⚪ 공백: 콘솔에서 `ecommerce` 로 전환해 보안 이벤트를 조회하면 풀 계정 이벤트(`consumer-pool`)는 보이지 않는다 — 이 티켓 밖, 후속으로 기록.
# Acceptance Criteria

- [ ] **AC-1** — 풀 계정, 스토어 멤버십 없음 → 스토어 authorize 에서 동의 화면 → 동의 → `CUSTOMER` 역할 토큰. 거절하면 토큰 없이 스토어로 돌아간다(오류 문구).
- [ ] **AC-2** — 두 번째 방문부터는 동의 화면이 나오지 않는다.
- [ ] **AC-3** — 콘솔(`iam`)에는 동의 화면이 없다 — 운영자 계정은 자동으로 만들어지지 않는다(D1).
- [x] **AC-4 (소유자 결정 수령 — 위 «착수 시 결정»)** — 🔴 **소유자에게 묻는 AC**: iam `ADR-007` 은 팬=「GAP」 · 스토어=「Global Account」 로 로그인 화면 이름을 **일부러 달리** 했고, 근거가 «서로 다른 계정» 이었다(`projects/iam-platform/docs/adr/ADR-007-per-client-branding-of-the-shared-login-page.md`). `ADR-MONO-078` A 아래서는 같은 계정이다. 이름을 하나로 맞출지, 사이트 이름을 유지하되 «같은 계정» 을 알릴지 — 소유자 결정을 받아 기록한다(받기 전에는 007 그대로).
- [ ] **AC-5** — 동의 화면의 접근성: 키보드로 동의·거절, 400px 폭.
- [ ] **AC-7** — 스토어 로그인·가입 화면과 스토어 앱 진입 버튼이 «IAM 로그인» 이다(`ADR-007` § 값 변경 2026-10-01) — 색·로고·설명은 사이트별. 화면 문구를 단언하는 기존 시험(e2e 포함)을 같이 고친다.
- [ ] **AC-6** — 🔴 **이 티켓이 `iam.consumer-pool.enabled` 를 켠다**(`TASK-BE-615` 착수 시 정정 2026-10-01 — 동의 화면이 생겨야 풀 가입자가 다른 사이트에 처음 들어갈 길이 생긴다). 켜기 전 확인: 614(풀 가입) · 615(풀 로그인·토큰) · 616(동의)이 전부 main 에 있다. 켠 뒤 «스토어 풀 가입 → 팬 첫 방문 → 동의 → 팬 토큰» 을 한 시험으로 잇는다.

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` D3
- `projects/iam-platform/docs/adr/ADR-007-per-client-branding-of-the-shared-login-page.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md`(`TASK-MONO-742` AC-3 이 동의 시점 이벤트를 골랐다면 여기서 발행)

# Edge Cases

- 동의 화면에서 브라우저 뒤로가기 — authorize 요청이 남아 있어야 한다.
- 이커머스 user-service 프로필이 이벤트/pull-through 중 어느 경로로 생기는지가 `TASK-MONO-742` 결정과 맞는가.

# Failure Scenarios

1. 동의 없이 역할을 먼저 시드해 «동의 안 한 사이트» 토큰이 발급된다.
2. 브랜드 문구를 소유자 결정 없이 바꾼다(AC-4).
