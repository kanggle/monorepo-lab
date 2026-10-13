# Task ID

TASK-BE-631

# Title

`ADR-MONO-083` P2 — admin-service: 도메인 업무 권한 세트 · 테넌트별 직접/그룹 배정(출처별 행) · 실효 권한 · `domainRoles` · «이전 호환» 백필

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (스키마 · fan-out · 권한 상승 가드 · 마이그레이션)
> 🔴 이 단계는 **토큰을 바꾸지 않는다**. assume `roles` 는 P3(`TASK-BE-632`) 배포 전까지 지금처럼 구독 파생이다 — 이 단계 배포만으로 «세트로 업무 권한이 제한된다» 고 보고하지 않는다.

# Dependency Markers

- **선행**: `TASK-MONO-791`(P1 계약) 머지 · `TASK-MONO-784` `done/`(회사별 소속 · 내보내기 · 행동 테넌트 규칙 위에 선다).
- **후속**: `TASK-BE-632`(P3 — 이 단계가 **배포되고 백필 완료 판정 = 0** 이어야 착수) · `TASK-PC-FE-338`(P4).

# Goal

ADR-083 갈래 A 의 저장 모델과 관리 API 를 admin-service 에 만든다. 실효 업무 권한 = (직접 ∪ 그룹 세트의 역할 합)이고, assume 판정 내부 응답에 `domainRoles` 로 싣는다. 기존 운영자는 «이전 호환» 데이터로 지금과 같은 권한을 유지한다(OD-1 기본값).

# Scope

## In Scope

- Flyway(admin_db, 다음 번호 — 착수 시 `main` · 열린 PR 대조): `domain_permission_set`(테넌트 세트 + `tenant_id` 없는 시스템 프리셋) · `domain_permission_set_role(set_id, domain_key, role)` · `operator_domain_set_grant(operator_id, tenant_id, set_id, source_key, …)` — 🔴 PK 에 출처를 넣는다(`DIRECT` / 그룹 id — NULL 없는 키) · 그룹의 세트 배정 표.
- 카탈로그 = 코드 한 자리(`DomainRoleCatalog` 가칭)로 `DelegatableRoleCatalog` 와 합친다(D2) — 파트너십 위임 허용 목록도 같은 출처.
- API(P1 계약대로): 카탈로그 · 세트 CRUD · 직접 배정/회수 · 그룹 세트 배정/회수 · 실효 권한 + 출처 조회.
- 그룹 fan-out(ADR-046 D2 유지): 세트 배정 · 멤버 가입 → 그룹 출처 행 생성(그룹 테넌트에만) · 회수 · 탈퇴 · 그룹 삭제 → **그 그룹 출처 행만** 삭제.
- 784 «이 회사에서 내보내기»(`OperatorCompanyExitCascade`) — 그 회사의 세트 행(모든 출처) 삭제 추가.
- 권한 키 `domain_access.manage` 시드(`TENANT_ADMIN` · `SUPER_ADMIN`) · 행동 테넌트 grant scope 판정 · 구독 밖 · 조직 상한 밖 세트 배정 거절 · OD-4 자기 배정 허용 + 감사 표시.
- `/internal/operator-assignments/check` 응답 `domainRoles`(그 테넌트의 실효 업무 역할 — 카탈로그 교집합은 auth 가 한다).
- 감사: 변경 = `AdminActionAuditWriter` · 거절 = `AdminActionDenyWriter`(fail-closed).
- **백필(OD-1)**: 실행 시점에 소속(배정 행)이 있는 비-`'*'` 운영자 × 그 테넌트에 «이전 호환» 시스템 프리셋을 `MIGRATION` 출처로(멱등). 🔵 AC-0 판단 대상: admin_db 마이그레이션은 account_db 의 구독을 못 읽는다 — 발급 시 `∩ entitled` 를 하므로 «모든 도메인의 이전 호환 세트» 를 주면 지금 파생과 **바이트 동일**(이후 구독 추가에도 지금과 같은 동작). 이 해석이 ADR D8 «그 테넌트의 entitled domains 에 해당하는» 과 결과가 같은지 대조해 기록한다.
- 데모 시드(`R__seed_*`)의 새 운영자 행 — 시드는 V 마이그레이션 뒤에 돈다(784 D-2 교훈): 시드 운영자에게 필요한 세트 행을 시드에서 직접 넣는다.

## Out of Scope

- auth-service 발급 전환(P3) · 콘솔(P4) · 도메인 세분 역할(P5~P8).
- 관리 역할 fan-out 의 단일 출처 결함(`TASK-BE-633`).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 784 이후 `GroupFanOutService` · `GroupAdminUseCase` · `OperatorCompanyExitCascade` · 내부 check 응답 · Flyway 마지막 번호 · 시드 운영자 목록. 백필 해석(위 🔵) 결정 기록.
- [ ] **AC-1** — 🔴 대조군(실패 쪽 먼저): 같은 운영자 · 테넌트 · 세트를 직접 + G1 + G2 가 줌 → G1 탈퇴 → 실효 권한에 그 세트 **유지**(G2 · 직접) → 직접 회수 → **유지**(G2) → G2 회수 → 사라짐. 같은 시험에서 단계마다 출처 목록을 단언.
- [ ] **AC-2** — A · B 분리: 같은 운영자에게 A 재고 세트 · B 출고 세트 → A 변경이 B 실효 권한 · B `domainRoles` 를 바꾸지 않음.
- [ ] **AC-3** — 권한 상승 · 범위: `domain_access.manage` 없는 운영자 배정 403(DENIED 행) · 다른 테넌트 배정 403 · 구독 안 한 도메인 세트 배정 거절 · 그룹 세트 배정이 그룹 테넌트 밖에 행을 만들지 않음 · 관리 역할만 있고 세트 없음 → `domainRoles` 빈 배열.
- [ ] **AC-4** — 내보내기: A 에서 내보내기 → A 세트 행(모든 출처) 0 · B 세트 행 바이트 불변.
- [ ] **AC-5** — 백필: 마이그레이션 문장 재실행 0행(멱등) · 기존 소속 운영자 전원 세트 ≥ 1 · `'*'` 0 · 이전 시점 뒤 새 소속은 0 에서 시작(대조군). 완료 판정 SQL(«이전 시점 소속 중 세트 행 0» = 0)을 티켓에 남긴다 — P3 배포 게이트.
- [ ] **AC-6** — `domainRoles`: 판정 응답에 실효 역할 · 소속 없음/세트 없음 → 빈 배열 · 파트너십 경로 응답은 지금과 같음(호스트 세트를 읽지 않음).
- [ ] **AC-7** — 배포 상태를 따로 적는다: `main` 머지 · AMI 재굽기 · 데모 창 라이브(백필 행 수 = 소속 수 · 완료 판정 0). 라이브가 없으면 ⚪ 와 이유.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D1 · D2 · D4 · D5 · D6 · D8 · § OD 대조
- `projects/iam-platform/specs/services/admin-service/rbac.md` · `data-model.md` (P1 갱신본)
- `docs/adr/ADR-MONO-046-operator-group-model.md` D2 · D5

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` · `internal/auth-to-admin.md` (P1 갱신본)

# Edge Cases

- 그룹이 삭제됐는데 그 그룹 출처 행을 다른 트랜잭션이 막 만들고 있음 — 그룹 FK `ON DELETE CASCADE` 또는 같은 트랜잭션 삭제로 고아 행 0.
- 세트를 지우려는데 배정이 남아 있음 — 거절(RESTRICT)할지 배정째 걷을지 P1 계약대로.
- 시스템 프리셋 세트(«이전 호환» 포함) 편집 시도 — 거절.
- 마지막 소속을 떠난 운영자 — 세트 행 0(내보내기가 걷었으므로).

# Failure Scenarios

1. 출처를 PK 밖 칸 하나로 둔다(관리 역할의 `group_origin` 모양) — G1 회수가 G2 분을 지운다(ADR ④).
2. 백필을 «구독 조회 실패 시 전체» 같은 코드 분기로 대신한다 — 영구 우회(D8).
3. `domainRoles` 계산이 실패할 때 빈 값 대신 예외를 삼키고 «미지정» 을 반환 — auth 쪽이 과거 파생으로 떨어질 여지가 생긴다(P3 이 파생을 지우기 전 기간).
