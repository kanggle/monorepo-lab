# Task ID

TASK-MONO-154

# Title

ADR-MONO-019 런타임 활성화 capstone — federation-hardening-e2e 에 실 고객 `acme-corp` operator 시드 + 런타임 cross-domain entitlement spec. acme-corp 토큰(`entitled_domains=[finance,wms]`)이 **실제로** finance/wms 도메인 게이트를 통과하고 scm/erp 는 거부됨을 full-stack(GAP + 4 도메인 + console-bff)에서 증명. (root `tests/` cross-product 하니스 = monorepo-level.)

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- multi-tenant
- e2e

---

# Dependency Markers

- **depends on**: ADR-MONO-019 step 3 게이트 4/4(FIN/ERP/SCM-BE / BE-323) + keystone(BE-324 — 발급 시점 `entitled_domains`) + step 2(BE-325 — `acme-corp`→[finance,wms] 구독, account-service V0020) + console-bff pass-through(PC-BE-007). federation-hardening-e2e 하니스(TASK-MONO-139/140 — GAP + finance/wms/scm/erp + console-bff + console-web full-stack).
- **검증된 게이팅 사실**: `auth_db.credentials.tenant_id=acme-corp` 사용자가 platform-console-web 로그인 시 OIDC 토큰 `tenant_id=acme-corp` + keystone `entitled_domains=[finance,wms]`(CredentialAuthenticationProvider→setDetails→customizer principal-details 우선).
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (federation full-stack 하니스 + OIDC PKCE 신규 operator + cross-domain 단언 un-defer; isolation 검증).

---

# Goal

ADR-019 의 논리 체인(seed→역조회 BE-325 / 발급→claim BE-324 / 게이트 accept-reject step3 / BFF pass-through PC-BE-007)을 **하나의 런타임 full-stack E2E** 로 capstone 한다. 실 고객 `acme-corp` operator 가 platform-console 에 로그인 → console-bff fan-out → **finance/wms 도메인 게이트가 entitlement-trust 로 통과**(acme-corp 는 그 두 도메인 구독) / **scm/erp 게이트가 거부**(미구독·미-entitlement). 이로써 entitlement-trust 가 데모-가능한 실 동작임을 증명.

기존 `operator-overview-composition.spec.ts` 는 card-status 단언을 "follow-up task 로 deferred"(MVP-relaxed, heading 만). 본 task 가 그 deferred 단언을 **entitlement-trust 판으로 un-defer** — entitled vs 미-entitled 도메인의 per-card forbidden 차이를 실 게이트로 검증.

federation-hardening-e2e 는 **nightly + workflow_dispatch**(PR 트리거 아님) — 검증은 머지 후 `workflow_dispatch` 실행으로(권위 게이트).

# Scope

## In scope

1. **`tests/federation-hardening-e2e/fixtures/seed.sql`**: SUPER_ADMIN 블록(무변경) 옆에 **acme-corp operator** 추가(idempotent, 기존 패턴 답습):
   - `auth_db.credentials`: `tenant_id='acme-corp'`, email `acme-operator@example.com`, **동일 argon2id 해시**(`devpassword123!` — SUPER_ADMIN 행과 동일 문자열 재사용), 신규 account_id UUID. INSERT IGNORE.
   - `admin_db.admin_operators`: `tenant_id='acme-corp'`, `oidc_subject='acme-operator@example.com'`, `finance_default_account_id=<acme finance account UUID>`, status ACTIVE. ON DUPLICATE KEY UPDATE.
   - `admin_db.admin_operator_roles`: SUPER_ADMIN(또는 적절 role) 바인딩(기존 SELECT-JOIN 패턴).
   - 비고: account_db 의 `acme-corp` 테넌트 + [finance,wms] 구독은 BE-322/BE-325 Flyway(V0019/V0020)로 이미 존재(시드 불요).
2. **finance 도메인 데이터**(`seed-domains.sql` 또는 `seed-finance` 해당 위치): `finance_db` 에 `tenant_id='acme-corp'` 계정 1개 + 잔액(operator 의 `finance_default_account_id` 와 매칭) — finance leg 가 MISSING_PREREQUISITE 아닌 실 200 ok 가 되도록. (기존 finance 시드 패턴 답습.) **(선택) wms**: acme-corp scoped 최소 데이터(없으면 wms entitled-accept 는 empty-200 ok 로도 충분 — 핵심은 403 아님).
3. **`tests/federation-hardening-e2e/fixtures/login.ts`**: `loginAsAcmeOperator(context)` 추가 — `driveOidcPkceLogin(context, 'acme-operator@example.com', 'devpassword123!')` + `console_active_tenant` 쿠키 `'acme-corp'` set(기존 SUPER_ADMIN 헬퍼 미러; DEFAULTS 에 acme 상수 추가).
4. **신규 spec `tests/federation-hardening-e2e/specs/entitlement-trust-crossdomain.spec.ts`**: acme-operator 로그인 → operator-overview(또는 도메인별 페이지) → 단언:
   - **finance card + wms card = NOT forbidden**(entitled — 게이트 통과; ok/데이터 present 또는 최소 not-`PERMISSION_DENIED`).
   - **scm card + erp card = forbidden / PERMISSION_DENIED**(미-entitlement — 실 게이트 거부).
   기존 `operator-overview-composition.spec.ts` + `finance-golden-path.spec.ts` 의 셀렉터/카드-status 표현을 읽어 충실히 단언(페이지 구조·card status DOM/text 확인). entitled vs 미-entitled 의 forbidden 차이가 핵심 discriminator.
5. **(선택) `operator-overview-composition.spec.ts` 주석 갱신**: card-status deferred 가 본 task 로 해소됨을 1줄 반영(또는 그대로 두고 신규 spec 으로 분리). 기존 spec 단언 무변경.

## Out of scope

- console-bff/도메인 게이트/keystone/카탈로그 production code 변경(전부 머지 완료 — E2E 가 실 동작 검증만).
- multi-고객 세션 전환(D3-B, 미명세 — 별 ADR).
- 추가 고객(globex 등).
- step 4 cleanup(legacy slug 제거).
- gap admin-service operator-scope 게이트(step 3 잔여 별건).
- PR-time CI 통합(federation-e2e 는 nightly/dispatch — 의도된 채널).

# Acceptance Criteria

- **AC-1**: federation-e2e seed 에 acme-corp operator(credential `tenant_id=acme-corp` + admin_operator + role + finance default account) 추가, idempotent, 기존 SUPER_ADMIN 시드 무변경.
- **AC-2**: `loginAsAcmeOperator` 가 OIDC PKCE 로 acme-operator 로그인(production-identical, 토큰 mint/cookie 주입 없음) + `console_active_tenant='acme-corp'`.
- **AC-3 (런타임 entitlement-trust 증명)**: acme-operator 세션에서 finance/wms 도메인 = NOT forbidden(게이트가 acme-corp 토큰의 `entitled_domains` 로 통과) / scm/erp = forbidden·PERMISSION_DENIED(미-entitlement 거부). 실 게이트(step3) + 실 토큰(keystone) + 실 BFF(pass-through)로.
- **AC-4**: 기존 federation-e2e spec(SUPER_ADMIN) + 기존 seed 무회귀.
- **AC-5 (검증)**: 머지 후 `gh workflow run federation-hardening-e2e.yml`(workflow_dispatch) 실행 → 신규 spec + 기존 spec GREEN. (PR-time 은 path-filter fast-lane — federation-e2e 는 nightly 채널.)
- **AC-6 (scope-lock)**: 변경 = federation-e2e seed/login/신규 spec(+선택 주석) 만. production code 0, 다른 도메인/ADR 0.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D5 + § 3.3(step 2/3 + 런타임). `docs/adr/ADR-MONO-018-...md`(federation hardening, D3 composition spec). `docs/adr/ADR-MONO-017-...md` D6(BFF pass-through).
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9(outbound dispatch).
- done: BE-322/324/325 + step3 게이트 4 + PC-BE-007 + MONO-139/140(하니스).

# Related Contracts

- acme-corp 토큰 `entitled_domains=[finance,wms]`(keystone producer) → 도메인 게이트(consumer) accept/reject. 런타임 full-stack 검증.

# Related Code

- `tests/federation-hardening-e2e/fixtures/seed.sql` + `seed-domains.sql` + `login.ts` + `specs/operator-overview-composition.spec.ts`(+ `finance-golden-path.spec.ts` 셀렉터 참조) + `docker/docker-compose.federation-e2e.yml`(서비스 구성 — 무변경, 이미 4 도메인 live) + `.github/workflows/federation-hardening-e2e.yml`(nightly/dispatch).

# Edge Cases

- **password 해시**: SUPER_ADMIN 의 argon2id(`devpassword123!`) 문자열 그대로 재사용(동일 password) — 신규 해시 생성 불요.
- **finance MISSING_PREREQUISITE**: operator `finance_default_account_id` + 매칭 finance 계정(tenant_id=acme-corp) 시드해야 finance leg 가 실 200. 없으면 finance 는 not-403 이나 데이터 없음 — AC-3 의 "NOT forbidden" 은 충족하나 ok 데이터까진 못 봄(권장: 시드).
- **wms 데이터 부재**: wms entitled-accept 는 empty-200 ok 로도 "NOT forbidden" 충족(데이터 시드 선택).
- **card status DOM**: 신규 spec 작성 전 실제 overview 페이지 + card status 표현(text/role/testid) 확인 — 기존 spec 이 MVP-relaxed 라 셀렉터 신중. forbidden 표현이 명확하지 않으면 도메인별 페이지(wms ok / scm forbidden) 네비게이션으로 대체 단언.
- **nightly-only**: PR 에서 spec 미실행 → 머지 후 workflow_dispatch 필수(AC-5).
- **idempotent**: 모든 시드 INSERT IGNORE / ON DUPLICATE KEY.

# Failure Scenarios

- 잘못된 셀렉터 → spec flaky/오탐 → 기존 spec + finance-golden-path 패턴 답습 + workflow_dispatch 실검증.
- finance default account 누락 → finance 카드 데이터 없음(단 not-403 은 유지).
- SUPER_ADMIN 시드 오염 → 기존 spec 회귀 → acme 블록은 추가만.
- production code 손댐 → scope 위반(E2E 검증 task) → seed/login/spec 만.

---

# Implementation Design Notes

- 기존 seed.sql SUPER_ADMIN 블록 미러로 acme-corp operator 추가. login.ts 파라미터화된 `driveOidcPkceLogin` 재사용.
- 신규 spec 은 entitled(finance/wms not-forbidden) vs 미-entitled(scm/erp forbidden) discriminator 에 집중. card-status DOM 불명확 시 도메인별 페이지 네비로 대체.
- 검증 = 머지 후 `gh workflow run federation-hardening-e2e.yml` + watch(full-stack, nightly 채널 권위). 로컬 docker-compose 는 Windows/Rancher 부담으로 비권장.
- 구현 = Opus.

---

# Notes

- ADR-019 런타임 활성화 capstone. 논리 체인(BE-322→324→325 + step3 게이트 + PC-BE-007) 을 full-stack 런타임으로 닫음. 잔여(별건): gap admin operator-scope 게이트 + step 4 cleanup + multi-고객 전환(ADR 선행). root `tests/` = monorepo-level, root tasks/.
