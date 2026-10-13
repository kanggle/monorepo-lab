# Task ID

TASK-MONO-791

# Title

`ADR-MONO-083` P1 — 도메인 업무 권한 세트의 **계약 · 명세 먼저**: admin 세트 · 배정 API · assume 판정 `domainRoles` · 콘솔 두 신호 · 회수 지연 문장

# Status

ready

# Owner

monorepo

# Task Tags

- api
- adr
- iam
- rbac

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계약 설계 — 여러 프로젝트 · 공유 오류 코드)
> 🔴 **코드 0** — 계약 · 명세 · 공유 오류 코드 문서만. P2(`TASK-BE-631`) · P3(`TASK-BE-632`) · P4(`TASK-PC-FE-338`)가 이 계약을 구현한다.

# Dependency Markers

- **선행**: `ADR-MONO-083` ACCEPTED — A (2026-10-14 UTC). `TASK-MONO-784` `done/` 권장 — 784 가 같은 계약 절(`admin-api.md` § 행동 테넌트 · 내보내기)을 마지막으로 썼다.
- **후속**: `TASK-BE-631` · `TASK-BE-632` · `TASK-PC-FE-338` · `TASK-MONO-792`~`795`.

# Goal

ADR-083 D1~D9 · 갈래 A · OD 기본값(§ OD 대조)을 계약으로 고정해, 구현 단계가 «무엇을 지킬지» 를 문서에서 읽게 한다(CLAUDE.md § Contracts — API 변경은 구현 전에 계약).

# Scope

## In Scope

- `projects/iam-platform/specs/contracts/http/admin-api.md` — 카탈로그 조회 · 세트 CRUD(테넌트 세트 + 읽기 전용 시스템 프리셋) · 운영자 직접 배정/회수(테넌트별) · 그룹 세트 배정/회수 · 실효 업무 권한 + 출처 조회 · 오류 코드 · 감사 action code · 행동 테넌트 규칙(784) 적용 · OD-4 자기 배정 표시
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` § operator-assignments/check — `domainRoles` 필드(실효 업무 역할, 카탈로그 교집합 전) · 비었음/없음 = 업무 역할 0 · 호출 실패 = assume 거절(지금 그대로)
- `projects/iam-platform/specs/services/admin-service/rbac.md` — `domain_access.manage`(가칭, 최종 이름은 여기서) · `TENANT_ADMIN` · `SUPER_ADMIN` · 세트 배정은 관리 평면, 업무 권한 아님(D1 · D5)
- `projects/iam-platform/specs/services/admin-service/data-model.md` — 세 표(갈래 A) · 출처별 행 PK(NULL 없는 출처 키) · fan-out 규칙 · 내보내기 연동 · 백필 마이그레이션 계획(OD-1)
- assume 토큰 클레임 문서(AC-0 에서 정본 위치 확정 — `jwt-standard-claims.md` 등): `roles` 의미 변경 · `scope` 에서 도메인 쓰기 범위 제외 · (OD-2) assume 토큰 TTL
- `projects/platform-console/specs/contracts/console-integration-contract.md` — «IAM 관리 역할» / «업무 권한 세트» 화면 분리 · 메뉴 두 신호(구독 없음 / 업무 권한 없음) · 실효 권한 API · 조회 전용 버튼 숨김 + 서버 403
- `platform/error-handling.md` · `rules/domains/saas.md` — 새 오류 코드(🔴 공유 경로 — 프로젝트 비종속 문구, HARDSTOP-03)
- 회수 지연 문장(D7): «다음 재교환부터 · 기존 토큰은 `exp` 까지» 를 계약에 그대로 — «즉시 회수» 금지

## Out of Scope

- 도메인 서비스 계약의 세분 역할(P5~P8 각 티켓)
- 코드 · 마이그레이션 파일

# Acceptance Criteria

- [ ] **AC-0** — 재측정: ADR-083 § Context ①~⑤ 의 file:line 이 `main` 에서 여전한지(784 이후 변동) · assume 토큰 클레임의 정본 문서 위치 · 🔴 fan 도메인의 운영자 인가(ADR § Outstanding ⚪ — `FAN_OPERATOR` 를 무엇이 검사하는지)를 재서 1차 카탈로그에 넣을지 정한다.
- [ ] **AC-1** — 위 In Scope 문서 전부 갱신. 카탈로그 1차 = WMS 8역할(ADR D2) + SCM · ERP · Finance · E-commerce · fan 은 진입 역할 하나 «전체 업무(세분 미지원)»(D3). `*_ADMIN` · `MASTER_WRITE` · `INVENTORY_RESERVE` · `WMS_ADMIN` 없음.
- [ ] **AC-2** — 출처별 행 · 회수 규칙이 계약에 단언으로 있다: 직접 회수는 직접 행만 · 그룹 회수/탈퇴/삭제는 그 그룹 행만 · 여러 출처 공존.
- [ ] **AC-3** — `domainRoles` 의 실패 의미(D6)가 계약 오류표에 있다: 판정 실패 = 거절 · 비었음 = 역할 0 · 어떤 경우도 «구독 전체» 아님.
- [ ] **AC-4** — 새 오류 코드가 `platform/error-handling.md` 에 먼저 있고 공유 문서 문구가 프로젝트 비종속이다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` (D1~D9 · § OD 대조)
- `docs/adr/ADR-MONO-035-…md` O1 · `ADR-MONO-082-…md` D1 · R4 · `ADR-MONO-046-…md` D2
- `projects/iam-platform/specs/services/admin-service/rbac.md` · `data-model.md`

# Related Contracts

- 위 In Scope 의 계약 파일 전부

# Edge Cases

- 시스템 프리셋 세트를 테넌트가 편집하려 함 — 읽기 전용(복제해서 테넌트 세트로).
- 세트 구성 변경(역할 추가/삭제)이 이미 배정된 운영자에게 미치는 시점 — 다음 재교환(D7).
- 구독 밖 도메인 역할을 담은 세트 — 생성은 허용하되 배정 판정에서 구독 밖이면 거절할지, 세트 생성 자체를 그 테넌트 구독 도메인으로 묶을지 계약에서 정한다(D5 «배정 거절» 이 최소선).

# Failure Scenarios

1. 계약이 «실효 권한 = 세트 합» 만 적고 «∩ 구독 · 상한» 을 빠뜨린다 — 구현이 상한 없이 싣는다.
2. 회수 지연을 «즉시» 로 적는다 — 오래된 토큰으로 30분(또는 OD-2 값) 접근이 계속되는데 문서는 막혔다고 말한다.
3. 공유 오류 문서에 프로젝트 이름(서비스 · 도메인)을 적는다 — HARDSTOP-03.
