# Task ID

TASK-MONO-741

# Title

⏳ 전역 소비자 계정 — `ADR-MONO-078` 갈래대로 단계 티켓을 기안한다

# Status

done (2026-10-01 UTC — AC-0~AC-6 닫힘 · PR #4084 squash `333b8eef5`)

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

- [x] **AC-0 (게이트)** — `docs/adr/ADR-MONO-078-*.md` Status 가 `ACCEPTED` 이고 갈래 letter 가 적혀 있다. 아니면 착수하지 않는다.
- [x] **AC-1** — 단계 티켓이 `ready/` 에 있고, 각 티켓이 Goal/Scope/AC/Related Specs/Related Contracts/Edge Cases/Failure Scenarios 를 갖는다.
- [x] **AC-2** — 🔴 첫 단계는 **계약·스펙 갱신**이다(구현 전). `multi-tenancy.md` 의 «테넌트마다 한 계정» · SSO 절이 소비자 둘에 대해 새 규칙을 말한다.
- [x] **AC-3** — 🔴 대조군이 **한 단계의 AC 로** 들어가 있다: 남의 이메일로 팬에 가입한 사람이 그 이메일의 기존 스토어 계정에 비밀번호 없이 들어가지 못한다(`ADR-MONO-078` D2 · § Verification).
- [x] **AC-4** — `ADR-MONO-078` 라이더(R1 본인 확인 = 다른 쪽 비밀번호 1회 · R2 묶기는 사용자 선택 · R3 데모 계정 미리 묶기)가 소유자 공급 여부와 함께 단계 티켓 AC 로 옮겨져 있다.
- [x] **AC-5** — 로그아웃 범위(한 사이트 / 전체)가 한 단계의 AC 로 명명돼 있다(`ADR-MONO-078` § 새로 생기는 위험).
- [x] **AC-6** — 가입 안내 ①(소유자 선택)을 D3 동의 화면이 대체한다는 사실과, 전역 계정이 늦어질 때 임시 조치로 할지의 소유자 결정 여부가 기록돼 있다.

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

---

# 구현 기록 (2026-10-01 UTC · 분석=Opus 5.5 · 구현=Opus 5.5)

`ADR-MONO-078` ACCEPTED — A(소유자 정확형, 2026-10-01). ADR § Outstanding follow-ups 가 «ACCEPT PR 안에서 그 자리 기안» 을 요구했으므로 **ACCEPT 와 같은 PR** 에서 단계 티켓을 기안했다.

## AC-0 — ✅ `ADR-MONO-078` Status = ACCEPTED, 갈래 A(개정판).

## AC-1 — ✅ 단계 티켓 7개, 전부 필수 절을 갖는다

| 단계 | 티켓 | 자리 | 왜 그 자리 |
|---|---|---|---|
| 1 계약·스펙 | `TASK-MONO-742` | 루트 | `platform/contracts/` 공유 경로를 고친다 |
| 2 풀 데이터 모델 | `TASK-BE-614` | iam | IAM 안 |
| 3 authorize·토큰·게이트·로그아웃 | `TASK-BE-615` | iam | IAM 안 |
| 4 첫 방문 동의 | `TASK-BE-616` | iam | IAM 안 |
| 5 기존 계정 묶기 + id 이전 | `TASK-MONO-743` | 루트 | IAM · 팬 · 이커머스 · 운영자 매핑 — 두 프로젝트를 넘는다 |
| 6 소셜 | `TASK-BE-617` | iam | IAM 안 |
| 7 데모·문서·라이브 | `TASK-MONO-744` | 루트 | 시드가 IAM·팬·이커머스에 걸치고 `docs/guides/` |

## AC-2 — ✅ 첫 단계가 계약(`TASK-MONO-742`). 후속 여섯이 모두 742 를 선행으로 적는다.

## AC-3 — ✅ 대조군(남의 이메일로 팬 가입 → 기존 스토어 계정에 비밀번호 없이 못 들어감)은 `TASK-MONO-743` AC-3. 같은 부류의 대조군을 `TASK-BE-615` AC-2(묶이지 않은 기존 계정은 재인증 유지) · `TASK-BE-617` AC-2(소셜 이메일 자동 연결 금지)에도 뒀다.

## AC-4 — ✅ 라이더는 소유자가 공급하지 않았다. R1·R2 → `TASK-MONO-743` AC-1·AC-2, R3 → `TASK-MONO-744` AC-1, 각각 «구현자 기본값 — 소유자가 한 줄로 뒤집을 수 있다» 로 표시. 라이더 표에 **없던** 미결 둘(살아남는 id · 풀 계정 이벤트의 `tenantId`)도 `TASK-MONO-743` AC-0 · `TASK-MONO-742` AC-3 으로 명명했다.

## AC-5 — ✅ 로그아웃 범위 = `TASK-BE-615` AC-4.

## AC-6 — ✅ 가입 안내(①)는 D3 동의 화면이 대체한다(`TASK-BE-616` Goal). 🔴 «전역 계정이 늦어지면 임시 조치로 따로 할지» 는 **소유자가 아직 정하지 않았다** — `ADR-MONO-078` § Outstanding follow-ups 에 그대로 남아 있다(이 티켓이 닫혀도 의무는 ADR 에 있다).

## 다른 결정에 생긴 의무

iam `ADR-007` 의 브랜드 이름 차이(팬 「GAP」 · 스토어 「Global Account」)는 근거가 «다른 계정» 이었고 A 아래서 사라진다 → `TASK-BE-616` AC-4 가 소유자에게 묻는다.
