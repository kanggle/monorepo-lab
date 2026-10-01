# Task ID

TASK-MONO-744

# Title

전역 소비자 계정 7단계 — 데모 시드 · 안내 문서 · 라이브 검증 (`ADR-MONO-078` A)

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- docs
- verification

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (시드·문서·측정 — 결정은 앞 단계가 했다)

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615` · `TASK-BE-616` · `TASK-MONO-743` · `TASK-BE-617` · `TASK-MONO-745`(셀러 이동 — 데모 셀러도 풀 계정이 된다)

# Goal

데모에서 «한 번 로그인으로 팬 ↔ 스토어» 가 첫 화면부터 보이게 하고, 안내 문서가 새 동작을 말하게 하고, 라이브(재굽기 뒤)에서 그것을 잰다.

# Scope

## In Scope

- dev 시드: `demo@demo.com` 의 팬·스토어 계정을 풀 계정 하나로(R3) — `auth-service` `R__01_seed_demo_single_identity_credentials.sql` · `account-service` `R__05` · `infra/demo/seed/seed-fan.sh`(`DEMO_SUB`)
- `docs/guides/interview-demo-walkthrough.md` § 0 · 로그인 순서 안내 정리
- 데모 서버 재굽기 필요 기록 + 라이브 측정

## Out of Scope

- 인증 코드(앞 단계)

# Acceptance Criteria

- [ ] **AC-1 (R3 — 구현자 기본값)** — 데모 계정은 시드에서 **미리 묶여** 있다. 소유자가 「데모도 묶기 흐름을 타게」 로 뒤집을 수 있다.
- [ ] **AC-2** — 데모 계정의 팬 데이터(팔로우·멤버십)와 스토어 데이터(주문)가 같은 `sub` 로 보인다 — 시드 id 를 바꿨다면 `seed-fan.sh` · 이커머스 주문 시드가 같이 바뀌었다.
- [ ] **AC-3** — 안내 문서가 «사이트마다 따로 로그인» 을 더 말하지 않고, 콘솔은 그대로 운영자 로그인임을 말한다.
- [ ] **AC-4** — 라이브(재굽기 뒤): 팬 로그인 → 스토어 이동 시 비밀번호 입력 없음(첫 방문이면 동의 화면). 측정 전 **이미지 시각 vs 머지 시각**을 대조한다(옛 이미지를 재면 무효).
- [ ] **AC-5** — 라이브: 스토어 쇼핑객 주문이 콘솔 `ecommerce` 전환에서 여전히 보인다(`ADR-MONO-078` § Verification).

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`
- `docs/guides/interview-demo-walkthrough.md`

# Related Contracts

- 없음(앞 단계의 계약을 쓴다)

# Edge Cases

- 데모 계정의 `iam`(콘솔) 행은 묶지 않는다(D1).

# Failure Scenarios

1. 시드 id 를 한쪽만 바꿔 데모 계정의 팬 팔로우나 스토어 주문이 사라진다.
2. 재굽기 전 이미지에서 «안 된다» 를 재고 결함으로 보고한다.
