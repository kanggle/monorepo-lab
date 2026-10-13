# Task ID

TASK-MONO-790

# Title

테넌트별 **도메인 업무 권한 세트** — 운영자 · 그룹에 배정한 세트만 assume 토큰에 싣는다: 조사 · `ADR-MONO-083` 발행 · ACCEPT 뒤 단계 티켓 기안

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- iam
- security
- rbac

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한 모델 · 토큰 발급 · 마이그레이션 · 회수 지연 측정)
>
> 🔴 이 티켓은 **결정 티켓**이다. 코드는 바꾸지 않는다. 구현은 `ADR-MONO-083` ACCEPT 뒤 그 PR 안에서 기안하는 단계 티켓(P1~P9)이 한다 — 이 티켓을 닫았다고 «구현 완료» 로 보고하지 않는다.

---

# Dependency Markers

- **선행**: `TASK-MONO-784` `done/` — 단계 티켓 P2 이상(admin 스키마 · 그룹 fan-out · «이 회사에서 내보내기» 연동)은 784 의 회사별 소속 · 역할 위에 선다. 2026-10-14 UTC 기준 784 = `review/`(S1~S6 머지 · ⚪ nightly · 데모 창 라이브만 남음). 이 티켓(조사 · ADR)은 784 와 파일이 겹치지 않아 병행 가능.
- **후속**: `TASK-MONO-773` — 회사 생성자 기본 업무 세트(ADR-083 OD-5)가 773 AC-2 · AC-3 의 기대값을 바꾼다. 🔴 773 은 ADR-083 ACCEPT 전에 착수하지 않는 것을 권한다(착수하면 «구독 파생 = 업무 전체» 를 전제로 시험을 쓰게 된다).

# Goal

하나의 테넌트가 여러 도메인을 구독해도, 운영자 · 그룹마다 **담당 도메인과 세부 업무만** 쓰게 한다. 지금 assume 토큰의 `roles` 는 «테넌트의 활성 구독 도메인» 에서 하드코딩 매핑으로 파생되어(`OperatorRoleDerivation.java:53-58, 80-125`) 같은 테넌트의 운영자가 모두 같은 업무 권한을 받는다. 이것을 «그 테넌트에서 그 운영자에게 (직접 · 그룹으로) 배정된 업무 권한 세트의 합 ∩ 구독 · 조직 상한» 으로 바꾸는 결정을 내린다.

# Scope

## In Scope

- 현재 구조 실측(AC-0) — 발급 경로 전수 · 도메인별 서버 인가 · 그룹/배정/출처 · 구독 · 조직 상한 · 파트너십 · 회수 장치 · 콘솔 메뉴/화면 · 배포 방식. 결과 = `ADR-MONO-083` § Context ①~⑤.
- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` PROPOSED 발행 · `docs/adr/INDEX.md` 행.
- 소유자 ACCEPT 게이트(정확형) · OD-1~OD-6 답 기록.
- ACCEPT PR 안에서 단계 티켓 P1~P9 기안(루트 · 각 프로젝트 `tasks/ready/`), 773 선행 표기 갱신.

## Out of Scope

- 코드 · 마이그레이션 · 계약 변경 — 전부 단계 티켓.
- 개별 업무 권한 직접 배정(소유자 원칙 — 세트만).
- 관리 역할 그룹 fan-out 의 단일 출처 결함 수선(ADR-083 § Outstanding — 별도 티켓).
- E-commerce 판매자 범위(`X-Seller-Scope`) 주입 — 데이터 범위, ADR-030 Step 4.

# Acceptance Criteria

- [x] **AC-0** — 실측: ① 운영자 도메인 역할의 발급 경로 전수와 각각의 입력 · 실패 처리 ② 도메인별(WMS · SCM · ERP · Finance · E-commerce) 실제 서버 인가와 세분 유무 ③ 관리 역할 · 그룹 fan-out · 출처 표시가 중복 부여를 안전하게 표현하는지 ④ 기존 토큰 회수 지연 ⑤ 콘솔 메뉴 · 화면 · 버튼 판정 입력과 배포 방식. — ✅ 2026-10-14 UTC: `ADR-MONO-083` § Context ①~⑤ (file:line 근거). ⚪ fan 도메인 인가 · 라이브 모집단은 열지 않음(ADR § Outstanding).
- [ ] **AC-1** — `ADR-MONO-083` PROPOSED 를 `main` 에 머지(번호 확정 — ACCEPT 대기 중 번호가 넘어가지 않게).
- [ ] **AC-2** — 소유자 정확형 `ADR-MONO-083 ACCEPTED — <A|B|C>` 수신 → Status 전환 · History · ACCEPT 게이트 기록 · OD 답(또는 기본값 = 구현자 선택) 대조. 결정 본문 바이트 불변.
- [ ] **AC-3** — ACCEPT PR 안에서 단계 티켓 P1~P9 기안 · `TASK-MONO-773` 선행에 ADR-083(+P9) 추가 · ADR-035 · ADR-082 · ADR-020 덧붙임(본문 바이트 불변).

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` (이 티켓의 산출물)
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` O1 (A 채택 · B 미룸 · C 기각)
- `docs/adr/ADR-MONO-082-one-person-operator-in-many-companies.md` D1 · D5 · R4
- `docs/adr/ADR-MONO-020-…md` D5 · `ADR-MONO-024` D2 · D6 · `ADR-MONO-045` D3 · `ADR-MONO-046` D2 · `ADR-MONO-047` D2 · D3 · D6
- `projects/iam-platform/specs/services/admin-service/rbac.md` · `data-model.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` § operator-assignments/check (`domainRoles` 추가 예정 — P1)
- `projects/iam-platform/specs/contracts/http/admin-api.md` (세트 · 배정 API — P1)
- `projects/platform-console/specs/contracts/console-integration-contract.md` (메뉴 두 신호 — P1)

# Edge Cases

- 같은 사람이 A 에선 재고, B 에선 출고 — 배정이 테넌트별 행이라 서로 영향 없음(ADR D4).
- 직접 + 그룹, 그룹 G1 + G2 가 같은 세트를 줌 — 출처별 행이라 한쪽 회수가 다른 쪽을 지우지 않음(D4).
- 세트는 배정됐지만 그 도메인 구독이 없음 — 토큰에 아무것도 싣지 않음(D1 상한).
- 세분 인가가 없는 도메인(SCM · ERP · Finance · E-commerce)의 «조회만» 세트 — 서버가 구분하지 못하므로 만들 수 없게 막고 «전체 업무(세분 미지원)» 로만 표시(D3).
- 플랫폼 운영자(`'*'`) — OD-3.

# Failure Scenarios

1. 역할만 줄이고 `scope` 를 둔다 — 운영자 assume 토큰의 `erp.write`(V0023) 때문에 ERP 쓰기가 역할과 무관하게 계속 열린다(ADR ②).
2. 그룹 세트 배정을 관리 역할과 같은 단일 `group_origin` 으로 구현 — G1 회수가 G2 멤버의 권한을 지운다(ADR ④).
3. 이전 호환을 코드 분기(플래그)로 둔다 — 새 운영자에게도 전체 권한을 주는 영구 우회가 된다(D8).
4. 판정 조회 실패를 «구독 전체» 로 떨어뜨린다 — 장애가 권한 확대가 된다(D6).
5. `main` 머지를 백엔드 운영 반영으로 보고 — 백엔드는 AMI 재굽기 · 데모 창이 따로다(ADR ⑤).
