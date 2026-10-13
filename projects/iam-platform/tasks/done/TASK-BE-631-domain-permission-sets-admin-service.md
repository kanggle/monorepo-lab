# Task ID

TASK-BE-631

# Title

`ADR-MONO-083` P2 — admin-service: 도메인 업무 권한 세트 · 테넌트별 직접/그룹 배정(출처별 행) · 실효 권한 · `domainRoles` · «이전 호환» 백필

# Status

done

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
- 권한 키 `domain_access.manage` 시드(`TENANT_ADMIN` · `SUPER_ADMIN`) · 행동 테넌트 grant scope 판정 · 구독 밖 · 조직 상한 밖 세트 배정 거절 · OD-4 자기 배정 허용 + 감사 표시. 🟢 **OD-3 소유자 결정(2026-10-14 UTC — ADR-083 § OD 소유자 결정)**: `'*'` 운영자 배정 행 = 구체 테넌트 또는 `'*'`(모든 테넌트) · 쓰기 = `'*'` grant scope 의 `domain_access.manage`(`SUPER_ADMIN`)만 · 소속 행 생성 0(082 D2) · 백필 `'*'` × `'*'` × `legacy-compat`. 상세 = `TASK-MONO-791` § 설계 4.
- `/internal/operator-assignments/check` 응답 `domainRoles`(그 테넌트의 실효 업무 역할 — 카탈로그 교집합은 auth 가 한다).
- 감사: 변경 = `AdminActionAuditWriter` · 거절 = `AdminActionDenyWriter`(fail-closed).
- **백필(OD-1)**: 실행 시점에 소속(배정 행)이 있는 비-`'*'` 운영자 × 그 테넌트에 «이전 호환» 시스템 프리셋을 `MIGRATION` 출처로(멱등). 🔵 AC-0 판단 대상: admin_db 마이그레이션은 account_db 의 구독을 못 읽는다 — 발급 시 `∩ entitled` 를 하므로 «모든 도메인의 이전 호환 세트» 를 주면 지금 파생과 **바이트 동일**(이후 구독 추가에도 지금과 같은 동작). 이 해석이 ADR D8 «그 테넌트의 entitled domains 에 해당하는» 과 결과가 같은지 대조해 기록한다.
- 데모 시드(`R__seed_*`)의 새 운영자 행 — 시드는 V 마이그레이션 뒤에 돈다(784 D-2 교훈): 시드 운영자에게 필요한 세트 행을 시드에서 직접 넣는다.

## Out of Scope

- auth-service 발급 전환(P3) · 콘솔(P4) · 도메인 세분 역할(P5~P8).
- 관리 역할 fan-out 의 단일 출처 결함(`TASK-BE-633`).

# Acceptance Criteria

- [x] **AC-0** — 재측정: 784 이후 `GroupFanOutService` · `GroupAdminUseCase` · `OperatorCompanyExitCascade` · 내부 check 응답 · Flyway 마지막 번호 · 시드 운영자 목록. 백필 해석(위 🔵) 결정 기록. — ✅ Flyway 마지막 = V0051 → V0052 · 시드 운영자 6(+ `'*'`) · 백필 해석 = «소속마다 `legacy-compat`(전 카탈로그) 하나» — 발급 시 `∩ entitled` 가 테넌트마다 거르므로 지금 파생과 같은 결과(이전 뒤 구독 추가도 지금과 같은 동작). `mes` 소비자 = **0**(§ 슬라이스 b 기록 4).
- [x] **AC-1** — 🔴 대조군(실패 쪽 먼저): 같은 운영자 · 테넌트 · 세트를 직접 + G1 + G2 가 줌 → G1 탈퇴 → 실효 권한에 그 세트 **유지**(G2 · 직접) → 직접 회수 → **유지**(G2) → G2 회수 → 사라짐. 같은 시험에서 단계마다 출처 목록을 단언. — ✅ 슬라이스 a: 개인 회수 절반(#4342 CI PASSED) · 슬라이스 b: 전체 단계 `GroupDomainPermissionSetIntegrationTest`(이 PR 의 CI 판정 — 닫기 전에 레인 로그 PASSED 확인).
- [x] **AC-2** — A · B 분리: 같은 운영자에게 A 재고 세트 · B 출고 세트 → A 변경이 B 실효 권한 · B `domainRoles` 를 바꾸지 않음. — ✅ `DomainPermissionSetIntegrationTest` AC-2(#4342 CI PASSED).
- [x] **AC-3** — 권한 상승 · 범위: `domain_access.manage` 없는 운영자 배정 403(DENIED 행) · 다른 테넌트 배정 403 · 구독 안 한 도메인 세트 배정 거절 · 그룹 세트 배정이 그룹 테넌트 밖에 행을 만들지 않음 · 관리 역할만 있고 세트 없음 → `domainRoles` 빈 배열. — ✅ 앞 셋 · 마지막 = #4342 CI PASSED · 그룹 테넌트 한정 + 🆕 세트 달린 그룹의 멤버 추가 게이트(`group.manage` 만 → 403 · 행 0 / 세트 없는 그룹 → 201 대조군) = 이 PR 의 IT.
- [x] **AC-4** — 내보내기: A 에서 내보내기 → A 세트 행(모든 출처) 0 · B 세트 행 바이트 불변. — ✅ `GroupDomainPermissionSetIntegrationTest` AC-4(DIRECT · MIGRATION · GROUP 셋 다 삭제 · B 불변 · 감사 `domainSetGrants=3`) — 이 PR 의 CI 판정.
- [x] **AC-5** — 백필: 마이그레이션 문장 재실행 0행(멱등) · 기존 소속 운영자 전원 세트 ≥ 1 · `'*'` 0 · 이전 시점 뒤 새 소속은 0 에서 시작(대조군). 완료 판정 SQL(«이전 시점 소속 중 세트 행 0» = 0)을 티켓에 남긴다 — P3 배포 게이트. — ✅ `V0052DomainSetBackfillIntegrationTest`(#4342 CI PASSED — 완료 판정 >0 → 0 · 재실행 0행 · 컷오버 뒤 소속 0). 🔴 «`'*'` 0» 은 **OD-3 소유자 결정(2026-10-14)으로 바뀌었다** — `'*'` 운영자는 `('*', legacy-compat)` 한 행을 받는다(그래야 P3 뒤에도 플랫폼 운영자 권한이 줄지 않는다). 완료 판정 SQL = `data-model.md` § Migration Strategy V0052 «완료 판정»(소속 쪽 + `'*'` 쪽 두 항의 합 = 0) — 🔴 **P3 직전에 라이브로 잰다**(관리자가 일부러 회수한 `legacy-compat` 도 세트 0 으로 센다).
- [x] **AC-6** — `domainRoles`: 판정 응답에 실효 역할 · 소속 없음/세트 없음 → 빈 배열 · 파트너십 경로 응답은 지금과 같음(호스트 세트를 읽지 않음). — ✅ `DomainPermissionSetIntegrationTest` AC-6 · `OperatorAssignmentCheckDomainRolesTest` · 시드 시험(demo-cs → 전 카탈로그) — #4342 CI PASSED.
- [x] **AC-7** — 배포 상태를 따로 적는다: `main` 머지 · AMI 재굽기 · 데모 창 라이브(백필 행 수 = 소속 수 · 완료 판정 0). 라이브가 없으면 ⚪ 와 이유. — ✅ 기록: `main` 머지 = 슬라이스 a #4342(`7d663829d`) · 슬라이스 b = 이 PR. ⚪ **AMI 재굽기 · 데모 창 라이브 = 아직 없음** — 이 세션에 데모 창이 없고, 백엔드 운영 반영은 `main` 머지가 아니라 AMI 굽기 · `terraform apply` 로만 일어난다. ⇒ 운영(데모) DB 에 V0052 는 **아직 적용되지 않았다**. 라이브 술어(백필 행 수 · 완료 판정 0)는 P3(`TASK-BE-632`) 배포 게이트로 넘긴다 — P3 은 그 값이 0 이 아니면 배포하지 않는다.

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

---

# 슬라이스 a 기록 (2026-10-14 UTC)

> 구현=Opus 5.5 (backend-engineer) · 검토 = 이 세션(계약 대조 · 재실행). worktree `wt-631` · 브랜치 `task/be-631-domain-permission-sets`. 범위: 스키마 · 카탈로그 · 프리셋 · 세트 CRUD · 직접 배정/회수 · 실효 권한 · `/me/domain-access` · 내부 판정 `domainRoles` · 백필 · 데모 시드. 🔵 슬라이스 b(그룹 세트 · fan-out · 멤버 추가 게이트 · 내보내기 연동)는 다음 PR — **그 전까지 내보내기는 세트 행을 남긴다**(백엔드 운영 반영은 AMI 굽기 뒤라 b 머지 전에 운영으로 가지 않는다).

## 1. 무엇을

- `V0052__create_domain_permission_sets_and_backfill.sql` — 표 넷(그룹 템플릿 표 포함 — b 에 DDL 없음) · 프리셋 15(고정 id `00000083-0000-7000-8000-0000000000NN` — `data-model.md` 에 기록) · `domain_access.manage` 시드(`SUPER_ADMIN` · `TENANT_ADMIN`) · 백필 두 문장(비-`'*'` 소속마다 `legacy-compat` `MIGRATION` · `'*'` 운영자마다 `('*', legacy-compat)`), 재실행 0행.
- 카탈로그 한 자리 `DomainRoleCatalog` · `DelegatableRoleCatalog` = 그 복사본(파트너십 허용 목록 바이트 동일 단언).
- 유스케이스 `DomainPermissionSetUseCase` · `OperatorDomainAccessUseCase` · `DomainSetEntitlementGuard` · 포트 3 · JPA 4 · 컨트롤러 2 · 오류 6 + 판정 읽기 실패(5xx) · 감사 코드 7.
- 내부 판정 `domainRoles`(소속 경로 · `'*'` 경로 합집합 · 파트너십 생략 · 읽기 실패 = 5xx, `[]` 아님).
- 데모 시드 `R__seed_zz_demo_domain_permission_sets.sql`(시드 운영자 6 의 `legacy-compat` — R__ 가 V 뒤에 돌아 백필이 못 보므로).

## 2. 계약 대조 (이 세션)

- 엔드포인트 경로 8 · 내부 응답 필드명 `domainRoles` = 계약 글자 그대로(grep).
- 구독 상한 판정 = account-service `GET /internal/tenants/{tenantId}/entitled-domains`(토큰과 같은 상한) · 404 = 빈 집합 → `422` · 그 밖 4xx · 5xx · CB-open → `DownstreamFailureException` → `503`(`AdminExceptionHandler.java:239-244`) — fail-closed 확인. account-service `/internal/**` = `internal.invoke` 범위만 요구(`SecurityConfig.java:128`) — admin 클라이언트가 이미 같은 경로군을 부른다 ⇒ 운영에서 막히지 않음.
- 계약 보완 2: `account-tenant-domain-subscriptions.md` § entitled-domains 에 두 번째 호출자(admin) · 실패 방향 · `data-model.md` 프리셋 고정 id.

## 3. 계약과 다르게 / 구현이 정한 것

| # | 무엇 | 판단 |
|---|---|---|
| I-1 | MySQL 은 FK 동작이 걸린 칸에 CHECK 를 못 건다(ER 3823) — `group_origin` CHECK 일부를 앱 · 형태 시험이 잡는다 | data-model 이 허용한 대체 |
| I-2 | 표 생성 가드 = `CREATE TABLE IF NOT EXISTS`(V0043/V0047 형태) | 멱등 동일 |
| I-3 | `Idempotency-Key` 재사용 = `409`(초대 생성 패턴, 저장 응답 재생 없음) · 누락 = `400` | 기존 패턴 |
| I-4 | `mes` 카탈로그 행 유지 — 소비자 유무(AC-0) 미측정 | 슬라이스 b AC-0 에서 |

## 4. 실행 (rc 는 각자 — 파이프 없음)

| 명령 | rc | 결과 |
|---|---|---|
| `:admin-service:compileTestJava` | **0** | IT 포함 컴파일 |
| `:admin-service:test` | **0** | 194 suites · 1270 tests · fail 0 · skip 58(Testcontainers — Docker 없음). 신규 · 수정 단위/슬라이스 10 클래스 통과(에이전트 보고) — 이 세션이 다시 돌려 rc 0 확인 |
| `:auth-service:test` | **0** | 시드 파일을 읽는 auth 시험 무영향 확인 |
| `integrationTest` | ⚪ CI | `DomainPermissionSetIntegrationTest`(AC-1 개인 회수 절반 · AC-2 · AC-3 · AC-6) · `V0052DomainSetBackfillIntegrationTest`(AC-5) · `DemoOperatorSeedIntegrationTest` · `TenantAdminRoleSeedIntegrationTest` 추가 단언 |

## 5. CI 결과 — #4342 (2026-10-14 UTC)

- 19 pass · 실패 0 · 스쿼시 `7d663829d`. 🔵 «레인 초록 = 새 IT 가 돌았다» 를 추론으로 두지 않고 `Integration (iam A)` 잡 로그를 열었다: `DomainPermissionSetIntegrationTest` 7 · `V0052DomainSetBackfillIntegrationTest` 2 · `TenantAdminRoleSeedIntegrationTest` · `DemoOperatorSeedIntegrationTest`(BE-631 단언 2 포함) — 전부 **PASSED**. (첫 grep 은 로그 형식과 안 맞아 «0» 을 냈다 — 판별자 탓이었다.)

---

# 슬라이스 b 기록 (2026-10-14 UTC)

> 구현=Opus 5.5 (backend-engineer) · 검토 = 이 세션. 브랜치 `task/be-631-b-group-sets`(origin/main `7d663829d` = 슬라이스 a 위). DDL 없음(그룹 템플릿 표는 V0052).

## 1. 무엇을

| 규칙 | 자리 |
|---|---|
| 그룹 세트 GET/POST/DELETE — `domain_access.manage` @ 그룹 테넌트(쓰기 밖 = 403 + DENIED · 읽기 밖 = 404 `GROUP_NOT_FOUND`) | `GroupDomainPermissionSetController` · `GroupDomainPermissionSetUseCase.java:89, 132` |
| 그룹이 가질 수 있는 세트 = 프리셋 · 그룹 테넌트 세트 · `legacy-compat` 400 · 구독 상한 · 중복 409 | `:96 · :106 · :107` |
| fan-out 은 skip 하지 않는다 — 현재 멤버마다 `GROUP:<id>` 행(그룹 테넌트에만) · 회수 = 그 그룹 · 그 세트 행 + 템플릿만 | `GroupDomainSetFanOut` · `:141` |
| 🔴 세트 달린 그룹의 멤버 추가 = `domain_access.manage` 도 — 없으면 DENIED 행(fail-closed) + 403, 쓰기 전 | `GroupAdminUseCase.java:209`(이후 fan-out `:225`) |
| 멤버 제거 · 그룹 삭제 = `source_key = 'GROUP:<id>'` 행만 | `GroupAdminUseCase.java:255 · :157` |
| 내보내기 = (운영자, 테넌트) 세트 행 **전 출처** · 감사 `domainSetGrants=<n>` | `OperatorCompanyExitCascade.java:120` |

## 2. 시험

- IT `GroupDomainPermissionSetIntegrationTest` 5: AC-1 전체(DIRECT + G1 + G2 → G1 탈퇴 → 개인 회수 → G2 세트 회수, 단계마다 출처 목록 · `domainRoles`) · 멤버 추가 게이트(`group.manage` 만 → 403 · DENIED · 행 0 / 세트 없는 그룹 → 201) · 그룹 테넌트 한정 · AC-4 내보내기 · 그룹 삭제.
- 단위/슬라이스: `GroupDomainPermissionSetUseCaseTest` 7 · `GroupDomainSetFanOutTest` 4 · `GroupDomainPermissionSetControllerSliceTest` 5 · `GroupAdminUseCaseTest` +4. 기존 내보내기 시험 · `OperatorMembershipWriteAxisIntegrationTest` 는 감사 detail 의 `,domainSetGrants=N` 를 기대하도록(약화 없음 — «거절 시 아무것도 안 지움» 칸 추가).

## 3. 실행 (rc 각자)

| 명령 | rc | 결과 |
|---|---|---|
| `:admin-service:compileTestJava` | **0** | 이 세션 재실행 |
| `:admin-service:test` | **0** | 197 suites · 1290 tests · fail 0 · skip 58 — 이 세션 재실행 rc 0 |
| `integrationTest` | ⚪ CI | 위 IT 5 — 이 PR 의 `Integration (iam A/B)` 로그에서 PASSED 를 확인한 뒤 닫는다 |

## 4. 판단 · 계약 보완

- **`mes` 소비자 = 0**(AC-0): `MES_OPERATOR` 는 IAM 자기 목록(카탈로그 · 위임 목록 · V0052 · auth 파생)과 콘솔 가이드 한 행에만 있고 `projects/*mes*` · 게이트웨이 · `ProductCatalog` 항목이 없다. **그대로 둔다** — 카탈로그 행과 `mes-full` 프리셋은 이미 `main` 의 V0052 에 있어 빼려면 데이터 마이그레이션이 따로 필요하고, 남겨도 발급 시 `∩ entitled` 로 걸러진다(구독하는 테넌트가 없다). MES 프로젝트가 생기면 그 단계에서 다시 본다.
- 계약 보완: 세트 생성 · 그룹 세트 POST 오류표 — `Idempotency-Key` 누락/길이 초과 = `400 VALIDATION_ERROR` · 재사용 = `409`(저장 응답 재생 없음 — 초대 생성 규약). 직접 배정 POST 는 키를 쓰지 않는다(계약도 그렇다).
- 그대로 둔 것: 멤버 추가 응답 `fannedOutGrants` · `GROUP_MEMBER_ADD` 감사 detail 은 역할 행만 센다(세트 행 미포함 — 계약이 정하지 않음) · 없는 세트의 그룹 회수 = `404 DOMAIN_PERMISSION_SET_NOT_FOUND`.

## 5. 🔴 CI 1회차 실패 — 원인은 세트가 아니라 **784 때부터 있던 감사 키 넘침** (2026-10-14 UTC)

- **증상**: #4343 `Integration (iam A)` — `GroupDomainPermissionSetIntegrationTest` AC-4 «이 회사에서 내보내기» 가 204 대신 **`500 AUDIT_FAILURE`**. 다른 4 IT 는 PASSED.
- **진단 과정**: 감사는 내보내기와 **같은 트랜잭션**(`AdminActionAuditWriter.recordWithPermission` = `@Transactional` REQUIRED)에서 fail-closed 로 원인을 감싸고, IT 프로파일은 앱 로그가 꺼져 있어 레인 로그 · JUnit XML 아티팩트 어디에도 원인이 없었다. 외래키 · TEXT 길이 · 잠금 대기(0.1초 만의 실패)는 코드로 배제했지만 **확정은 못 해서 추측으로 고치지 않았다** — 실패한 요청의 원인 체인을 출력하는 진단 한 줄을 시험에 넣고(동작 무변경, 그대로 둔다) CI 를 한 번 더 돌렸다.
- **원인**(CI 출력 그대로): `MysqlDataTruncation: Data too long for column 'idempotency_key'`. 키 = `"assignment:" + 동작이름 + ":" + 운영자 UUID + ":" + 테넌트 + ":" + millis` — 고정 부분만 **89자**, 칸은 `VARCHAR(100)`(`V0001:10`), 테넌트 id 는 최대 32자(`tenants.tenant_id`) ⇒ **테넌트 id 가 11자를 넘으면 배정 생성 · 삭제(내보내기)가 언제나 500**. 이 시험의 테넌트 `s631a-44r3ha`(12자)가 처음으로 그 경계를 넘었다 — 784 의 시험 테넌트는 짧았다. 🔴 세트 변경과 무관한 **기존 결함**이다.
- **수선**: `ManageOperatorAssignmentUseCase.auditIdempotencyKey` = `"asg:" + 운영자 + ":" + 테넌트 + ":" + millis`(최대 87자). 동작 이름은 행의 `action_code` 칸과 유일 색인 `(actor_id, action_code, key)` 에 이미 있어 키에서 빼도 유일성이 같다. 옛 형식에 의존하는 곳 0(grep).
- **시험**: `AssignmentAuditIdempotencyKeyTest` — 최장 입력(UUID 36 · 테넌트 32 · millis 13)이 100 이하 / 대조군 옛 형식은 같은 입력에서 넘침 / 키가 운영자 · 테넌트 · 시각을 가른다. `:admin-service:test` rc **0**.
- **형제**: 같은 손조립 키가 7 곳 더 있다. 🔴 `ManagePartnershipParticipantUseCase.java:162` 는 **계산상 항상 넘친다**(UUID 둘 → 127/130자) — 그 API 를 부르는 IT 가 0 이라 지금까지 안 보였다. 증명 없이 고치지 않고 **`TASK-BE-634`** 로 넘겼다(AC-0 = 빨강 먼저).
