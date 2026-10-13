# Task ID

TASK-MONO-791

# Title

`ADR-MONO-083` P1 — 도메인 업무 권한 세트의 **계약 · 명세 먼저**: admin 세트 · 배정 API · assume 판정 `domainRoles` · 콘솔 두 신호 · 회수 지연 문장

# Status

done

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

- [x] **AC-0** — 재측정: ADR-083 § Context ①~⑤ 의 file:line 이 `main` 에서 여전한지(784 이후 변동) · assume 토큰 클레임의 정본 문서 위치 · 🔴 fan 도메인의 운영자 인가(ADR § Outstanding ⚪ — `FAN_OPERATOR` 를 무엇이 검사하는지)를 재서 1차 카탈로그에 넣을지 정한다. — ✅ 2026-10-14 UTC: 인용 줄 drift 0 · 정본 위치 · fan = 1차 카탈로그(전체 업무 하나) · 🔴 OD-3 충돌 발견 → 소유자 결정. § AC-0 기록.
- [x] **AC-1** — 위 In Scope 문서 전부 갱신. 카탈로그 1차 = WMS 8역할(ADR D2) + SCM · ERP · Finance · E-commerce · fan 은 진입 역할 하나 «전체 업무(세분 미지원)»(D3). `*_ADMIN` · `MASTER_WRITE` · `INVENTORY_RESERVE` · `WMS_ADMIN` 없음. — ✅ `admin-api.md` § 도메인 업무 권한 세트(카탈로그 표 · 프리셋 · 12 엔드포인트) 외 § 계약 기록 1 의 파일 전부. 🔵 카탈로그에 `mes`(`MES_OPERATOR`)도 넣었다(지금 파생 표에 있음 — 소비자 유무는 P2 AC-0, 없으면 계약 개정으로 뺀다).
- [x] **AC-2** — 출처별 행 · 회수 규칙이 계약에 단언으로 있다: 직접 회수는 직접 행만 · 그룹 회수/탈퇴/삭제는 그 그룹 행만 · 여러 출처 공존. — ✅ `admin-api.md` § 출처(`source_key`)와 회수 규칙 표 · `data-model.md` § operator_domain_set_grant · `rbac.md` § 도메인 업무 권한 세트.
- [x] **AC-3** — `domainRoles` 의 실패 의미(D6)가 계약 오류표에 있다: 판정 실패 = 거절 · 비었음 = 역할 0 · 어떤 경우도 «구독 전체» 아님. — ✅ `auth-to-admin.md` 판정 규칙 7 · Caller Constraints 의 실패 의미 표(admin 세트 읽기 실패 = 5xx → 거절 · `[]` · 부재 = 역할 0).
- [x] **AC-4** — 새 오류 코드가 `platform/error-handling.md` 에 먼저 있고 공유 문서 문구가 프로젝트 비종속이다. — ✅ 6 코드 · `rules/domains/saas.md` 대응 · `jwt-standard-claims.md` `roles` 행 — 세 공유 파일 추가 줄에 서비스 · 도메인 이름 0(grep — ADR 번호만).

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

---

# AC-0 기록 (2026-10-14 UTC)

> 분석=Opus 5.5 · 코드 변경 0 · worktree `wt-791`(origin/main `782073f1a`). 784 는 `review/`(«`done/` 권장» 은 권장 — 계약 절이 머지돼 있어 착수).

## 1. ADR-083 § Context 인용 재확인 — drift 0

`OperatorRoleDerivation.java:53-58, 80-125` · `TenantClaimTokenCustomizer.java:1005-1007, 1029-1037, 1078-1093, 1118-1138` · `AssumeTenantAuthenticationProvider.java:250, 268-270` · `V0015:93, 95` · `V0023:21` · `GroupFanOutService.java:131-145` · `data-model.md:243` · `AdminAssignmentClient.java:121-139` · `DelegatableRoleCatalog.java:24-29, 47-61` · `OperatorAssignmentCheckUseCase.java:282-294` · `V0030:26-37` · `V0051:64` · `auth-to-admin.md:16-77` — 전부 그대로.

## 2. 정본 위치 (계약을 고칠 자리)

| 무엇 | 위치 |
|---|---|
| 클레임 등록부(공유) | `platform/contracts/jwt-standard-claims.md` § Standard Claims `:51-79` — `roles` `:60`(일반 문구) · `scope` 행 **없음** |
| assume 동작(프로젝트) | `projects/iam-platform/specs/contracts/http/auth-api.md` § Assume-Tenant Exchange `:150-200` — Assumed Token Claims 표 `:180-190` 에 **`roles` · `scope` 행이 없다** · TTL 은 예시 `:175` 뿐 · OAuth2 Clients 표 `:395-410` 에 `platform-console-web` 행 없음 |
| 서비스 명세 | `auth-service/architecture.md` `:267-280`(`:277` = 구독 파생의 유일한 명세 문장) · `internal/auth-internal.md:37` |
| assume 판정 | `internal/auth-to-admin.md` `:16-77`(필드표 `:50-55` · 규칙 `:57-66`) · fail-closed `:293-299` · 거울 `rbac.md:562` |
| admin API | `admin-api.md` 행동 테넌트 `:86`(표 `:118-145` · `'*'` `:162`) · 역할/권한 카탈로그 `:1284` · `:1336` · permission-sets 미구현 `:1385` · 배정 `:1389-1560`(내보내기 `:1531`, TTL 문장 `:1547`) · 그룹 `:3236-3561` · 오류 형식 `:3565` · 감사 = 엔드포인트별 `Side Effects` 줄(중앙 목록 없음) |
| rbac · data-model | `rbac.md` 권한 키 `:56-82` · 시드 행렬 `:115-145` · fan-out `:462` · 캐시 `:529` / `data-model.md` 역할 행 `:131-157` · 그룹 `:161-243` · `operator_tenant_assignment` **전용 절 없음** · Migration Strategy `:474-533`(다음 = `V0052`) |
| 콘솔 | `console-integration-contract.md` nav `:174` · 권한/권한 세트 `:199-223` · 도메인 403 `:599-601` · `:655-657` · 테넌트 전환 `:3786-3821` · 🔴 «구독 필요» 배지는 **명세에 없다**(코드뿐) |
| 오류 코드 | `platform/error-handling.md` `## Admin [domain: saas]` `:655-702` · `rules/domains/saas.md` `:72-103` — 🔵 기존 결함(`:653` 깨진 표 · `saas.md:74` 절 이름 불일치)은 따라 하지 않는다 |
| 감사 코드 | `ActionCode.java:3-135`(명세 중앙 목록 없음) |

## 3. fan — 1차 카탈로그에 넣는다

- `FAN_OPERATOR` 검사: artist-service 쓰기(`SecurityConfig.java:181, 242-256`) · community · membership · notification 은 `FAN_OPERATOR` 를 **거절**(`:73 · :78 · :68`). 세분 없음 ⇒ D3 모양 «fan 전체 업무(세분 미지원)» 하나.
- 🔴 `fan-platform` 은 **플랫폼 운영자(`'*'`)만** assume(`OperatorAssignmentCheckUseCase.java:172-174, 183-187` · 배정 금지 `ManageOperatorAssignmentUseCase.java:76-80` · `admin-api.md:1507`).

## 4. 🔴 OD-3 충돌 → 소유자 결정

- OD-3 기본값(«`'*'` 도 배정된 세트만») × `'*'` 는 회사 소속 불가(ADR-082 D2 · `admin-api.md:162`) ⇒ P3 뒤 플랫폼 운영자의 도메인 역할 = 0, 줄 방법도 없음(fan 디렉터리 관리 포함).
- **소유자 결정 (2026-10-14 UTC, 선택창)**: **«세트 배정 허용»** — `SUPER_ADMIN` 만 `'*'` 운영자에게 테넌트별 세트를 배정할 수 있다 · 회사 소속(배정 행)은 만들지 않는다(082 D2 유지) · 이전 호환 백필을 `'*'` 운영자에게도. 🔵 이 선택은 내 추천과 같았다 — ADR ACCEPT 가 아니라 티켓 범위(OD) 결정이라 선택창 응답으로 받는다(784 OD 선례). ADR-083 D6 의 «OD-3 이 `'*'` 는 전체를 고르지 않는 한» 문장과 모순 없음(배정된 세트만 — 다만 배정 길이 생긴다).

# 설계 — 계약에 싣는 것 (AC-1~AC-4)

1. **카탈로그**(서버 한 자리 · `GET /api/admin/domain-roles`): 항목 = `{domainKey, role, access: READ|WRITE|OPERATE, fineGrained, label}`. 1차: `wms` = `INVENTORY_READ` · `INVENTORY_WRITE` · `INBOUND_READ` · `INBOUND_WRITE` · `OUTBOUND_READ` · `OUTBOUND_WRITE` · `MASTER_READ` · `WMS_OPERATOR`(운영 조치) / `scm` `SCM_OPERATOR` · `erp` `ERP_OPERATOR` · `finance` `FINANCE_OPERATOR` · `ecommerce` `ECOMMERCE_OPERATOR` · `fan` `FAN_OPERATOR` · `mes` `MES_OPERATOR`(파생 표에 있는 것 — 소비자 유무는 P2 AC-0) = 각 «전체 업무(세분 미지원)»(`fineGrained=false`). ADMIN 등급 · `MASTER_WRITE` · `INVENTORY_RESERVE` 없음.
2. **세트**: `domain_permission_set`(테넌트 세트 · `tenant_id` NULL = 시스템 프리셋, `system_key` 유일) · `domain_permission_set_role(set_id, domain_key, role_name)`. 시스템 프리셋(읽기 전용): `wms-inventory-read` · `wms-inventory-operate` · `wms-inbound-operate` · `wms-outbound-operate` · `wms-master-read` · `wms-ops-actions` · `<domain>-full`(도메인마다) · `all-domains-full`(OD-5 생성자용) · `legacy-compat`(내용 = `all-domains-full` 과 같음, `MIGRATION` 출처 전용 — 완료 판정 구분용). 테넌트 세트 생성 · 수정 = 그 테넌트 entitled 도메인의 카탈로그 역할만(밖 → `422`). 삭제 = 배정이 남으면 `409`.
3. **배정**: `operator_domain_set_grant(operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)` PK `(operator_id, tenant_id, set_id, source_key)` · `source_key` ∈ `DIRECT` · `MIGRATION` · `GROUP:<id>`(NULL 없음). 그룹: `operator_group_domain_set_grant(group_id, set_id)` → 멤버마다 `GROUP:<id>` 행(그룹 테넌트에만). 회수 = 자기 출처만. 개인 회수(`DELETE …/operators/{id}/domain-permission-sets/{setId}`) = `DIRECT` + `MIGRATION` 행. 내보내기(784 D-9) = 그 테넌트의 모든 출처 행.
4. **`'*'` 운영자(OD-3 결정)**: 배정 행 `tenant_id` = 구체 테넌트 또는 `'*'`(= 모든 테넌트에 적용) — `'*'` 테넌트 행은 `'*'` 운영자에게만 · 쓰기는 `'*'` grant scope 의 `domain_access.manage` 만(= `SUPER_ADMIN`) · 소속 행은 만들지 않음. 백필 = `'*'` 운영자 × `'*'` × `legacy-compat`.
5. **실효**: 운영자 × 테넌트 T 의 역할 = (T 행 ∪ `'*'` 운영자면 `'*'` 행)의 세트 역할 합. admin 은 카탈로그 교집합 전 값을 `domainRoles` 로 낸다 · auth 가 `∩ derive(entitled ∩ 상한)` · 진입 역할 파생은 P5~P8 이 세분을 넣을 때 그 도메인에 정의.
6. **권한 키** `domain_access.manage` — `SUPER_ADMIN` · `TENANT_ADMIN`. 업무 권한을 주지 않는다. 자기 배정 허용(OD-4) · 감사 `detail` 에 `selfGrant=true`.
7. **API**(행동 테넌트 = `X-Tenant-Id`, 784 규칙): `GET /api/admin/domain-roles` · `GET|POST /api/admin/domain-permission-sets` · `GET|PATCH|DELETE /api/admin/domain-permission-sets/{setId}` · `GET /api/admin/operators/{operatorId}/domain-access`(실효 + 세트별 출처) · `POST /api/admin/operators/{operatorId}/domain-permission-sets` · `DELETE …/{setId}` · `GET|POST /api/admin/groups/{groupId}/domain-permission-sets` · `DELETE …/{setId}` · `GET /api/admin/me/domain-access`(자기 · 콘솔 nav).
8. **감사 코드**: `DOMAIN_PERMISSION_SET_CREATE|UPDATE|DELETE` · `OPERATOR_DOMAIN_SET_GRANT|REVOKE` · `GROUP_DOMAIN_SET_GRANT|REVOKE` · 거절 = DENIED 행(fail-closed).
9. **오류 코드**: `DOMAIN_PERMISSION_SET_NOT_FOUND`(404) · `DOMAIN_PERMISSION_SET_READ_ONLY`(409) · `DOMAIN_PERMISSION_SET_IN_USE`(409) · `DOMAIN_PERMISSION_SET_ALREADY_GRANTED`(409) · `DOMAIN_ROLE_NOT_ASSIGNABLE`(422 — 카탈로그 밖 · 관리 등급) · `DOMAIN_NOT_SUBSCRIBED`(422 — 세트 도메인이 그 테넌트 entitled 밖).
10. **assume 판정** `domainRoles: string[]` — 소속 · `'*'` 경로에서 **항상 있음**(없으면 `[]`) · 파트너십 경로는 생략(`delegatedScope` 그대로, 호스트 세트 무시) · auth: 판정 실패 = 거절(지금 그대로) · `[]`/생략 = 역할 0 · 어떤 경우도 구독 전체 아님.
11. **토큰**: `auth-api.md` Assumed Token Claims 표에 `roles` · `scope` 행 추가 — 운영자 `scope` 에서 도메인 쓰기 범위 제외 · assume 토큰 TTL **300초**(OD-2 기본) · 리프레시 없음. 회수 문장(D7): «세트 회수 · 그룹 탈퇴 · 정지 · 구독 해지 · 세트 구성 변경은 다음 교환부터 · 이미 발급된 토큰은 `exp`(최대 300초)까지». `jwt-standard-claims.md` `roles` 행은 프로젝트 비종속 문구로.
12. **콘솔**: 화면 «권한 세트» → «IAM 관리 역할» · 새 «업무 권한 세트» · nav 두 신호(구독 없음 = 레지스트리 · 업무 권한 없음 = `GET /api/admin/me/domain-access` ∩ 구독) · 실효 API 실패 = 메뉴 열어 둠 · 조회 전용 = 변경 버튼 숨김 + 서버 403 · 운영자/그룹 화면.

# 계약 기록 (2026-10-14 UTC)

> 작성=Opus 5.5 (api-designer) · 검토 = 이 세션이 diff 를 § 설계와 대조(CLAUDE.md «plan-exact» 규칙). 코드 0.

## 1. 바꾼 파일

| 파일 | 절 |
|---|---|
| `projects/iam-platform/specs/contracts/http/admin-api.md` | 행동 테넌트 표 24~28 행 · `permission-sets` 노트 → «IAM 관리 역할» · 배정 생성(새 소속 = 세트 0) · 내보내기(세트 행 전 출처 삭제 · 감사 `domainSetGrants` · D7 문장) · 그룹 노트 · 🆕 § 도메인 업무 권한 세트(카탈로그 · 프리셋 · 출처/회수 표 · 12 엔드포인트, 각 오류표 · Side Effects) · § POST …/members(아래 3) |
| `…/internal/auth-to-admin.md` | `domainRoles` — 예시 · 필드표 · 판정 규칙 7 · 실패 의미 표 |
| `…/http/auth-api.md` | assume 예시 `expires_in` 300 · `roles` · `scope` 행 · 회수 지연 — 전부 «P3 부터, 그 전엔 지금 동작» 명시 |
| `auth-service/architecture.md:277` · `internal/auth-internal.md:37` | 파생 문장 + P3 노트 |
| `admin-service/rbac.md` | `domain_access.manage` · 시드 행렬 · 🆕 § 도메인 업무 권한 세트 · assume 판정 거울 |
| `admin-service/data-model.md` | 표 넷 · `permission_set_id` 미사용 노트 · V0052 계획 + 완료 판정 SQL |
| `features/operator-management.md` | 멤버 추가 규칙(아래 3) |
| `platform-console/specs/contracts/console-integration-contract.md` | 2.4.3.2 «IAM 관리 역할» · 🆕 2.4.3.4 업무 권한 세트 화면 · nav 두 신호(«구독 필요» 배지 첫 명세) · 2.7 의 300초 재교환 의무 |
| `platform/error-handling.md` · `rules/domains/saas.md` · `platform/contracts/jwt-standard-claims.md` | 6 코드 · 대응 bullet · `roles` 행(프로젝트 비종속) |
| `docs/adr/ADR-MONO-083-…md` | § OD 소유자 결정(OD-3) 덧붙임 |
| `projects/iam-platform/tasks/ready/TASK-BE-631` · `632` | OD-3 결정 반영 |

## 2. 설계가 정하지 않아 계약이 정한 것

| # | 결정 | 이유 |
|---|---|---|
| C-1 | `all-domains-full` 을 API 로 배정하려면 모든 도메인이 구독돼야 한다(`422 DOMAIN_NOT_SUBSCRIBED`) — P9 생성자 경로는 서버 내부 배정 | 세트 ⊆ 테넌트 entitled 규칙의 일관 적용 |
| C-2 | `legacy-compat` 은 API 배정 불가(`400`) · `'*'` 테넌트 행은 프리셋만 | 완료 판정 표지 보존 · 테넌트 세트를 모든 테넌트에 걸지 않음 |
| C-3 | 세트 이름은 테넌트 안에서 유일하지 않아도 된다(식별 = `setId`) | 이름 충돌 오류 코드 없이 단순화 |
| C-4 | 소속 아닌 테넌트에 배정 → `404 OPERATOR_NOT_FOUND` | 784 D-7 과 같은 열거 안전 |
| C-5 | 배정이 남은 세트 삭제 → `409 DOMAIN_PERMISSION_SET_IN_USE`(걷지 않음) | 조용한 권한 회수 방지 |
| C-6 | 배정자가 그 세트 역할을 스스로 가질 필요 없음(관리 역할 ≤-own 미적용) — 상한 = 테넌트 entitled + 카탈로그 | D5 · OD-4 |

## 3. 🔴 검토에서 고친 것 — 권한 상승 경로

작성본은 «업무 세트가 올라간 그룹에 멤버 추가 = `group.manage` 만» 이었다. 그러면 `group.manage` 를 가진 `ORG_ADMIN` 이 `domain_access.manage` 없이 업무 권한을 **간접 부여**한다(D5 우회 — 관리 역할 fan-out 의 add-member ≤-own 재검사와 같은 자리). ⇒ **업무 세트가 하나 이상인 그룹의 멤버 추가 = `group.manage` + `domain_access.manage`**(없으면 `403 PERMISSION_DENIED` · DENIED 행 · 행 0) · 멤버 제거 · 그룹 삭제는 `group.manage` 만(권한을 줄이는 쪽). 네 자리를 함께 고쳤다(`admin-api.md` 세트 절 노트 · § POST …/members 본문 + 오류표 · `rbac.md` · `features/operator-management.md`) — 한 사실을 한 곳만 고치지 않게.

## 4. 단계 티켓에 넘기는 것

- P2(`TASK-BE-631`): 표 칸 이름 = `role_name`(티켓 본문의 `role` 은 이 계약이 이긴다) · `mes` 소비자 유무 · 완료 판정 SQL 은 **P3 직전**에 잰다(관리자가 일부러 회수한 `legacy-compat` 도 «세트 0» 으로 센다).
- P4(`TASK-PC-FE-338`): assume 토큰 300초 → 콘솔은 IAM 리프레시 때만 재교환하면 안 된다(2.7 의무) · 도메인 화면의 «role 로 미리 막지 않는다»(`:599` · `:656`)는 버튼 표시에 한해 2.4.3.4 가 덮는다.
- 🔵 관찰(이 티켓 범위 밖): 콘솔 계약 2.7 «Selectable tenants» 가 아직 «홈 ∪ 배정» 을 적는다 — 784 가 «배정만» 으로 바꿨다. 784 정리 몫.
