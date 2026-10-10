# Task ID

TASK-MONO-784

# Title

`ADR-MONO-082` 갈래 **b** — 운영자 행 하나에 **회사별 소속 · 역할**: 관리 평면 권한을 행동 테넌트 축으로 · «이 회사에서 내보내기» · 두 409 를 «소속 추가» 로

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- security
- rbac

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한 평가 · 캐시 · 마이그레이션 · 권한 축소 대조군)
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** 날짜 조건이 아니다. AC-0 은 verify-then-act 게이트다 — 재서 참이면 진행, 아니면 이 파일에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.

---

# Dependency Markers

- **선행**: `TASK-MONO-772` `done/` — 바꿀 두 409(초대 수락 OD-1 · 셀프 온보딩 S1-11)와 그 IT 칸(772 AC-6)이 772 의 산출물이다.
- **후속**: `TASK-MONO-773` AC-3(«이미 운영자인 계정이 두 번째 회사를 생성») 이 이 티켓에 묶인다.

# Goal

`ADR-MONO-082` ACCEPTED — b (2026-10-10 UTC). 한 사람(계정 `sub` 하나 = 운영자 행 하나)이 여러 회사의 운영자가 되게 한다. 교환 · assume · 콘솔 스위처는 이미 «행 하나 · 테넌트 여럿»(ADR-020 배정)으로 돈다 — 빠진 것은 **관리 평면의 회사별 역할**이다. 지금 권한 평가는 테넌트를 보지 않아(`PermissionEvaluatorImpl.java:39-45`) 홈의 역할이 배정 테넌트 읽기에 «상속» 되고(`QueryTenantScopeGate.java:66-81`), 역할 행은 홈에만 둘 수 있다(WI-2, `V0026:20-23`).

라이더는 기본값 그대로(ADR-082 § 라이더 대조): R1 홈은 남기되 판정에서 읽지 않음 · R2 역할 행 없는 배정 테넌트의 관리 권한 0 · R3 행 전체 정지는 소속 하나일 때만 회사 관리자 · R4 `permission_set_id` 미사용.

# Scope

## In Scope

- 계약 먼저: `projects/iam-platform/specs/contracts/http/admin-api.md`(두 409 → 소속 추가 · «이 회사에서 내보내기» · 역할 변경의 회사 축 · 정지 R3) · `rbac.md` · `data-model.md`(WI-2 폐기 · 역할 PK) · `onboarding-api.md:93` · `multi-tenancy.md:635`(«773 착수 전 ADR» 문장 → «ADR-082 = b») · `projects/platform-console/specs/contracts/console-registry-api.md:282-283` 정정(구현 = 배정 ∪ 홈)
- admin-service: 역할 PK `(operator_id, role_id, tenant_id)` 마이그레이션 · 권한 평가 + 캐시 키에 행동 테넌트 · 읽기 게이트를 «그 권한의 역할 행 테넌트» 로 · 정지(R3) · 역할 변경을 회사 축으로(지금 전부 지우고 다시 씀 — `PatchOperatorRoleUseCase.java:54`) · «이 회사에서 내보내기»(그 회사 역할 행 + 배정) · 파트너십(`partner = 홈`) · 그룹 멤버(«홈 == 그룹 테넌트») 술어를 소속 집합으로 · 초대 수락 · 셀프 온보딩의 409 를 «기존 행에 소속 추가» 로(같은 테넌트 · `'*'` 는 409 유지, ADR-082 D2 · D4)
- 데모 시드: 한 행에 두 회사 역할 행 + 배정 하나
- `ADR-MONO-020` · `ADR-MONO-024` 에 «082 b 가 D5 · 역할 행 의미를 바꾼다» 덧붙임(본문 바이트 그대로)

## Out of Scope

- 토큰 클레임 모양 · 도메인 역할 파생(ADR-082 D1 · D5) · 플랫폼 관리자 모델(D2)
- 773 의 «테넌트 생성» 화면 · 셸 상태 · 신원당 상한 **값**(773 AC-0)

# Acceptance Criteria

- [ ] **AC-0 (게이트)** — 772 가 `done/` 이다. 아니면 착수하지 않는다. 참이면 재측정: ① 테넌트를 받지 않는 관리 API(자기 정보 · 목록)의 전수와 각각의 판정 기준(ADR-082 § 새로 생기는 위험 1 — 기본값 «홈» 은 R1 과, «모든 소속의 합» 은 상속 누수와 모순이므로 API 마다 정해 적는다) ② 감사기가 지금 `admin_actions.tenant_id` 를 무엇으로 채우는지 ③ R2 로 관리 화면이 줄어드는 데모 시드 행 수(`demo-operator` 의 `ecommerce` 배정 등) ④ ADR-082 § Outstanding 의 라이브 술어(홈 밖 배정 수) — 창이 없으면 ⚪ 와 이유.
- [ ] **AC-1** — 🔴 **대조군 — 권한 누수**: A 에서 `TENANT_ADMIN`, B 는 배정만(역할 없음) → B 의 운영자 목록 · 감사 조회가 **거절**되고, 같은 시험에서 A 의 같은 조회는 통한다. 실패 쪽을 먼저 단언한다(지금 코드에서 이 앞 절반은 빨갛다 — 그것이 «전» 상태).
- [ ] **AC-2** — 🔴 **대조군 — 한 회사만 떠나기**(ADR-082 D3): 같은 사람이 A · B 운영자 → A 에서 내보내기 → 같은 시험에서 A assume · A 관리 API 거절 · B assume · B 관리 API 성공 · 같은 계정의 스토어 · 팬 토큰 그대로.
- [ ] **AC-3** — 두 번째 회사 소속 추가(초대 수락 · 셀프 온보딩) → 기존 행에 새 테넌트 역할 행 + 배정 · 기존 회사 역할 행 **바이트 불변** · 같은 테넌트 재소속 409 · `'*'` 행에 회사 소속 쓰기 거절 · 풀 행에 `'*'` 거절(D2). 772 AC-6 IT 의 «두 번째 회사 409 · 셀프 온보딩 409» 칸은 **바뀐 기대값으로 고친다**(걷지 않는다).
- [ ] **AC-4** — 정지(R3): 소속 둘 이상인 운영자의 행 정지는 회사 관리자에게 거절 · `SUPER_ADMIN` 은 성공 · 소속 하나면 지금과 같다. 역할 변경이 다른 회사의 역할 행을 바꾸지 않는다.
- [ ] **AC-5** — 권한 캐시: 한 회사의 역할 변경 · 내보내기 뒤 그 회사 판정이 다음 요청에서 바뀌고 다른 회사의 캐시 판정은 그대로(캐시 키 = 운영자 × 테넌트).
- [ ] **AC-6** — 콘솔 e2e 디렉터리 grep(운영자 · 그룹 · 파트너 화면) 후 필요한 수정 · 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인 · 다음 데모 창에서 R2 영향 행 라이브 확인(⚪ 허용, 이유 기록).

# Related Specs

- `docs/adr/ADR-MONO-082-one-person-operator-in-many-companies.md` (ACCEPTED — b) D1~D6 · 라이더 R1~R4 · § Verification
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` D1 · D2 · D5 · `ADR-MONO-024` D2 · `ADR-MONO-044` D2 · D4 · `ADR-MONO-045` D6 · D7 · `ADR-MONO-046` · `ADR-MONO-080` D1 · D5
- `projects/iam-platform/specs/features/multi-tenancy.md` · `projects/iam-platform/specs/services/admin-service/` (rbac · data-model)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` · `onboarding-api.md` · `projects/platform-console/specs/contracts/console-registry-api.md`

# Edge Cases

- 홈 회사를 떠난 운영자 — 홈 값은 남지만(R1) 판정에서 읽지 않으므로 그 회사 권한은 없다. 표시 화면이 «홈» 을 «소속» 처럼 보여 주지 않는가.
- 소속이 하나 남은 운영자가 그 회사에서 내보내진다 — 측면 0 = 운영자 아님(772 의 콘솔 적격 거절로 떨어진다). 행을 지울지 남길지 AC-0 에서 정한다.
- 그룹(ADR-046) 멤버가 그 회사를 떠난다 — 그룹 멤버십도 함께 걷히는가.

# Failure Scenarios

1. 권한 평가에 테넌트를 더하고 캐시 키는 운영자만 둔다 — A 의 판정이 B 에서 재사용된다(누수가 캐시로 되살아난다).
2. 테넌트를 받지 않는 관리 API 가 기본값으로 «홈» 을 읽는다 — 홈을 떠난 운영자가 그 회사 권한을 계속 쓴다(R1 위반).
3. 역할 변경이 «전부 지우고 다시 씀» 을 그대로 둔다 — A 의 역할 변경이 B 의 역할 행을 지운다(D3 위반).
