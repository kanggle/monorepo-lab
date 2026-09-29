# Task ID

TASK-MONO-741

# Title

⏳ 전역 소비자 계정 — `ADR-MONO-078` 갈래대로 단계 티켓을 기안한다

# Status

ready

# Owner

monorepo

# Task Tags

- security
- identity
- planning

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (인증 모델 변경 — 세션·자격·계정 연결)
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** AC-0 은 verify-then-act 게이트다.

---

# Dependency Markers

- **선행 (prerequisite)**: `ADR-MONO-078` ACCEPTED(정확형 `ADR-MONO-078 ACCEPTED — <A|B>`).
- **후속**: `TASK-MONO-739`(팬 굿즈 링크)는 이 티켓과 **독립**이다 — 소유자가 로그인 쪽을 먼저 하라고 했을 뿐, 739 의 코드가 이 결정에 기대지 않는다.

# Goal

`ADR-MONO-078` 이 고른 모양(D5)으로 전역 소비자 계정을 들이기 위한 **단계 티켓을 기안**한다. 이 티켓 자신은 코드를 쓰지 않는다 —
인증 모델 변경은 단계마다 따로 검증돼야 하고, 한 PR 에 담으면 대조군(§ AC-3)이 무엇을 재는지 흐려진다.

# Scope

## In Scope

- 갈래별 단계 분해와 각 단계 티켓 파일(`projects/iam-platform/tasks/ready/` 또는 루트 `tasks/ready/` — 반경이 두 프로젝트를 넘으면 루트)
- 각 단계의 계약 선행 순서(`platform/contracts/jwt-standard-claims.md` · `projects/iam-platform/specs/features/multi-tenancy.md` 가 먼저)

## Out of Scope

- 구현 코드
- 콘솔·운영자 로그인(`ADR-MONO-078` D1)

# Acceptance Criteria

- [ ] **AC-0 (게이트)** — `docs/adr/ADR-MONO-078-*.md` Status 가 `ACCEPTED` 이고 갈래 letter 가 적혀 있다. 아니면 착수하지 않는다.
- [ ] **AC-1** — 단계 티켓이 `ready/` 에 있고, 각 티켓이 Goal/Scope/AC/Related Specs/Related Contracts/Edge Cases/Failure Scenarios 를 갖는다.
- [ ] **AC-2** — 🔴 첫 단계는 **계약·스펙 갱신**이다(구현 전). `multi-tenancy.md` 의 «테넌트마다 한 계정» · SSO 절이 소비자 둘에 대해 새 규칙을 말한다.
- [ ] **AC-3** — 🔴 대조군이 **한 단계의 AC 로** 들어가 있다: 남의 이메일로 팬에 가입한 사람이 그 이메일의 기존 스토어 계정에 비밀번호 없이 들어가지 못한다(`ADR-MONO-078` D2 · § Verification).
- [ ] **AC-4** — `ADR-MONO-078` 라이더(R1 본인 확인 = 다른 쪽 비밀번호 1회 · R2 묶기는 사용자 선택 · R3 데모 계정 미리 묶기)가 소유자 공급 여부와 함께 단계 티켓 AC 로 옮겨져 있다.
- [ ] **AC-5** — 로그아웃 범위(한 사이트 / 전체)가 한 단계의 AC 로 명명돼 있다(`ADR-MONO-078` § 새로 생기는 위험).
- [ ] **AC-6** — 가입 안내 ①(소유자 선택)을 D3 동의 화면이 대체한다는 사실과, 전역 계정이 늦어질 때 임시 조치로 할지의 소유자 결정 여부가 기록돼 있다.

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`
- `projects/iam-platform/specs/features/multi-tenancy.md`
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` § 1.3

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Single Sign-On (SSO) Scope · `sub` · `tenant_id`

# Edge Cases

- 같은 이메일로 팬·스토어에 **서로 다른 사람**이 가입해 있다 — 묶기가 본인 확인 없이 일어나면 안 된다.
- 소셜 로그인으로만 가입한 사람(비밀번호 없음) — R1(비밀번호 1회)이 성립하지 않는다. 그 사람의 본인 확인 수단을 단계 티켓이 정한다.

# Failure Scenarios

1. 단계를 한 PR 로 합쳐 대조군이 «묶기 전» 과 «묶기 후» 중 무엇을 쟀는지 흐려진다.
2. 이메일 일치만으로 묶는 코드가 들어간다(`ADR-MONO-034` § 1.3 권한 상승 경로).
