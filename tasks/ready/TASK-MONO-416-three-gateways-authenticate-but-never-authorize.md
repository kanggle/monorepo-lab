# Task ID

TASK-MONO-416

# Title

게이트웨이 3개가 **인증만 하고 인가를 안 한다** — 계약 규칙 6(역할 admission, 403)이 fan·erp·scm 에 배선돼 있지 않다

# Status

ready

# Owner

monorepo

# Task Tags

- code
- security

---

# Goal

`platform/contracts/jwt-standard-claims.md:130`(규칙 6)은 명시한다:

> **Validate authorization (role-based admission):** Admit iff the token carries ≥ 1 role valid for the requested surface; otherwise respond `403 Forbidden`. Authorization is a positive check against a closed role set.

공유 `libs/java-gateway/.../SecurityConfig.java:52-54` 는 `.pathMatchers(PUBLIC_PATHS).permitAll().anyExchange().authenticated()` 까지만 한다 — **인증**(유효 서명·발급자·`aud`·tenant)이지 **인가**(역할)가 아니다. 규칙 6 은 **각 게이트웨이가 자기 표면에 붙여야 하는** GlobalFilter 다. 붙인 곳(wms `AccountTypeValidationFilter`, ecommerce `AccountTypeEnforcementFilter`)과 안 붙인 곳이 갈린다.

**안 붙은 곳 = fan · erp · scm.** 셋 다 admission 코드 0건(custom filter·`authorizeExchange` role 규칙 아무것도 없음) → 공유 `SecurityConfig` 의 `.authenticated()` 까지만 → **역할이 하나도 없거나 그 표면에 맞지 않는 역할만 가진 인증 토큰이 그대로 백엔드에 도달한다.** 계약은 이 셋 전부에 대해 admission 을 명시적으로 요구한다(`:131` fan = FAN-family role, `:135` erp·scm = operator role).

**과장하지 않는다 — 노출면은 크로스-플랫폼이 아니라 플랫폼 *내부* 역할 분리다.** 토큰은 `aud`(규칙 5)로 한 플랫폼에 묶이므로 fan 토큰은 erp 게이트웨이에 도달조차 못 한다. 위험은 "역할 없는/잘못된 표면의 토큰이 같은 플랫폼 안에서 인가 없이 통과" 다 — 이중방어(서비스단 RBAC)가 남아 있으므로 즉시 침해는 아니지만, **계약이 요구하는 방어 겹이 edge 에 통째로 빠져 있다.** `ADR-MONO-049 D5-6`(면제 경로가 하중을 받는데 무방비)·`D5-7`(거부를 단언하는 테스트가 0개)이 가르친 그 자리다.

이건 **프로덕션 게이트웨이 보안 코드 변경**이다 — `TASK-MONO-410`(스킬 문서)과 달리, 그 티켓의 AC-4 가 *조사만* 하고 남긴 별건이다.

---

# 🔴 AC-0 3차 재측정 결과 (이 티켓의 권위 baseline — 착수 시 다시 잰다)

이 티켓의 **표적 집합 자체가 재측정으로 두 번 뒤집혔다.** 착수 세션은 아래 표를 *가설*로 받고 넓은 술어로 다시 재야 한다([[feedback_recount_population_dont_inherit_scope]]).

| # | 게이트웨이 | 계약 표(`:131-135`) | 역할 admission(≥1 유효 역할 없으면 403) 배선? | 판정 |
|---|---|---|---|---|
| 1 | **wms** | operator(`:135`) | **있음** — `AccountTypeValidationFilter`(roles≥1 else 403, order −2) | ✅ 준수 |
| 2 | **ecommerce** | path(`:132-134`) | **있음** — `AccountTypeEnforcementFilter`(admin→ECOMMERCE_OPERATOR / else→CUSTOMER, order −2) | ✅ 준수 |
| 3 | **fan** | FAN-family(`:131`) | **없음** — `TenantClaimValidator`(tenant gate)뿐, custom filter 0 | 🔴 **갭** |
| 4 | **erp** | operator(`:135`) | **없음** — 동일 | 🔴 **갭** |
| 5 | **scm** | operator(`:135`) | **없음** — `ScmTokenType` 은 `X-Token-Type` **헤더 enrichment**(`GatewayIdentityConfig:59` `JwtHeaderMapping.always`), admission 아님 | 🔴 **갭** |
| 6 | **finance** | **표에 없음** | 없음 — `TenantClaimValidator`뿐 | ⚪ 결정 사안(계약 규칙 부재) |
| 7 | **iam** | 표에 없음(IdP 자신) | 역할 체크 없음 — `JwtAuthenticationFilter` 는 인증 + `/internal/tenants/{id}/**` **tenant-scope** 인가 | 범위 밖(계약이 의도적 생략, tenant-scoping 이 자기 인가) |

**자기검증**: 넓힌 술어(`hasRole|hasAnyRole|hasAuthority|authorizeExchange|GlobalFilter|HttpStatus.FORBIDDEN` + custom filter 전수)를 **아는 답 wms=있음**에 먼저 돌려 발화 확인 후 나머지를 신뢰했다([[env_empty_detector_output_is_not_absence]]).

**재측정이 인계를 뒤집은 이력** — 착수자는 이 셋을 그대로 믿지 말 것:
- **1차(MONO-410 AC-4 초안, origin/main `tasks/done` 및 INDEX 에 박혀 있음)**: *"7개 중 5개(wms·fan·finance·erp·iam)에 없다, ecommerce·scm 만 붙였다"* — **wms 를 놓쳤고(있는데 없다 함) scm 을 오분류(없는데 있다 함).**
- **2차(인계 메모리)**: *"3 of 7 = fan·finance·erp, scm 은 있다"* — **scm 오분류를 그대로 물려받았다.** `ScmTokenType`(헤더 enrichment)을 admission 으로 착각.
- **3차(이 표)**: **fan·erp·scm 이 갭, finance 는 결정 사안, iam 은 범위 밖.** scm 이 `ScmTokenType` 때문에 세 번째로 뒤집혔다 — *"admission 으로 보이는 헬퍼"* 가 아니라 *"403 을 내는 역할 게이트가 있는가"* 를 물어야 보인다([[feedback_guard_predicate_wrong_verify_the_artifact]]).

⇒ **origin/main 의 MONO-410 done-note 와 INDEX review-note 에 담긴 "5개(…iam), ecommerce·scm 붙였다" 는 정정된 기록이다.** 닫힌 티켓을 편집하지 않는다(정정은 이 티켓이 담는다 — [[feedback_recount_population_dont_inherit_scope]]).

---

# Scope

## In Scope

- **fan · erp · scm 게이트웨이에 역할 기반 admission(규칙 6, 403)을 배선.** 각 표면의 유효 역할 집합을 계약(`:131`, `:135`)이 못박은 대로 gate 한다.
- **사본세(稅)를 피한다 (AC-2)** — wms/ecommerce 필터를 3번 복붙하면 `ADR-MONO-049` 가 값을 치르고 지운 그 패턴이다. `libs/java-gateway` 에 파라미터화된 admission GlobalFilter 를 추출하고 각 프로젝트는 자기 **표면→유효역할 매핑**만 선언하는 것이 이 저장소의 확립된 설계다(`JwtHeaderEnrichmentFilter(List<JwtHeaderMapping>)` · `ScmTokenType::of` 매핑-리스트 방식, `ADR-MONO-048 D4`). 추출 vs 인라인은 AC-2 의 명시적 판정 지점.
- **finance 결정을 먼저 해소 (AC-3, 블로킹)** — 계약 표에 finance 행이 없다. finance 에 admission 을 붙이려면 그 전에 계약 결정이 있어야 한다.

## Out of Scope

- **finance·iam 에 admission 구현 — 계약 결정 없이는 금지.** iam 은 IdP 자신이고 tenant-scoping 으로 자기 인가를 하며 계약이 표에서 의도적으로 생략한다. finance 는 AC-3 의 결정 전까지 손대지 않는다. **없는 규칙을 구현으로 만들지 말 것**([[project_gateway_role_admission_gap_2026_07_15]]).
- **wms·ecommerce 필터 재작성** — 이미 준수. 단 AC-2 가 lib 추출을 택하면 이 둘의 **수렴 여부**는 판정하되(ecommerce 는 path-based 도메인 지식이라 `ADR-MONO-048 D4` 근거로 서비스에 남는 것이 정당할 수 있다 — 자기 `SecurityConfig` 가 그렇듯), 행동 변경은 하지 않는다.
- 계약 본문 재작성 — AC-3 의 finance 결정이 계약 편집을 낳으면 그건 이 티켓의 산물이지만, 규칙 6 자체·타 게이트웨이 행은 무변경.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정 — 프로덕션 보안이라 필수)** 위 표를 *가설*로 받고 넓은 술어 + custom filter + `authorizeExchange` 를 **전수 재측정**한다. 아는 답(wms=있음, ecommerce=있음)에 먼저 돌려 자기검증. 표적이 fan·erp·scm 이 맞는지, phantom 이 없는지 확인하고 결과를 PR 본문에 기록(뒤집히면 뒤집힌 대로).
- [ ] **AC-1 (배선)** fan·erp·scm 각 게이트웨이가 규칙 6 을 만족한다: 그 표면의 유효 역할 집합에 속하는 역할이 토큰에 **하나도 없으면 403**, 있으면 통과. **공개 경로(security context 없음)는 통과** — wms/ecommerce 필터가 `defaultIfEmpty(TRUE)` 로 처리하는 그 패턴. order 는 header enrichment(`JwtHeaderEnrichmentFilter`)와의 관계를 wms(−2)·ecommerce(−2) 기준으로 맞춘다.
- [ ] **AC-2 (사본을 만들지 마라 — 명시적 판정)** lib 추출 vs 프로젝트 인라인을 **판정하고 근거를 적는다.** 인라인 3벌이면 `ADR-MONO-049`·`MONO-360` 이 이름 붙인 사본세다 ⇒ 기본은 `libs/java-gateway` 추출(표면→유효역할 매핑을 파라미터로, 각 프로젝트는 매핑 bean 만 선언). ecommerce 처럼 도메인 지식(path split)을 가진 게이트웨이가 서비스에 남는 것이 정당한지도 이 판정에 포함. **추출을 택하면 lib 변경 + fan·erp·scm 채택이 한 atomic PR**(`CLAUDE.md` § Cross-Project Changes).
- [ ] **AC-3 (finance 결정 — 블로킹, self-decision 금지)** 계약에 finance admission 행이 없다. **다음 중 하나를 사람이 결정한 뒤** 진행한다: (a) 계약 `:131-135` 표에 finance 행 추가(operator role 요구 — erp 와 대칭. finance 는 `ProductCatalog.ENTRIES` 소속 entitlement-plane operator 플랫폼이라 erp 와 같은 부류로 보인다) → 그러면 finance 도 AC-1 범위에 편입 / (b) finance 는 규칙 6 면제로 명문화(왜 role 존재만으로 충분한지 계약에 한 줄). **어느 쪽도 에이전트가 혼자 정하지 않는다** — 계약 규칙의 신설/면제는 결정 사안. iam 도 같은 자리(IdP·tenant-scoping)지만 계약이 이미 표에서 생략하므로 별도 결정 불요 — **다만 그 생략이 의도인지 한 줄 확인**.
- [ ] **AC-4 (역할 이름을 지어내지 마라)** 각 표면의 유효 역할 집합을 **그 플랫폼의 역할 발급/카탈로그 소스에서 읽어** 도출한다(fan=FAN-family, erp·scm=각 operator role). 계약의 예시(`WMS_OPERATOR`)는 *예시*지 그 플랫폼의 실제 role 이름이 아니다 — MONO-410 이 없는 lib API 를 가르칠 뻔한 그 함정([[feedback_deletion_leaves_survivors_grep_the_consumers]] 계열: 산출물은 옳고 문장이 틀리면 CI 가 못 잡는다). **presence-only(wms 식 "≥1 any role") vs named-role-set(ecommerce 식 특정 role) 판정**도 여기서: 계약은 특정 role 을 명명하므로(`:131` FAN, `:135` operator) named-set 이 계약의 letter 에 가깝다. Edge Cases 참조.
- [ ] **AC-5 (테스트가 프로덕션 게이트를 *보는가*)** `ADR-MONO-049 D5-7/D5-8` 의 교훈: **거부를 단언하는 테스트가 있어야 한다.** 각 게이트웨이에 **유효 역할이 없는 토큰 → 403** 을 단언하는 통합 테스트(프로덕션 필터 체인 통과). 유효 토큰만 쓰는 슬라이스 테스트는 admission 이 통째로 빠져도 초록이므로 증명력이 없다 — 음성 케이스가 핵심. Testcontainers IT 가 권위, **CI Linux 가 권위**([[project_testcontainers_docker_desktop_blocker]]).
- [ ] **AC-6 (CI 3차원 GREEN)** 머지 전 fan·erp·scm 게이트웨이 잡 + 영향받는 lib 잡이 GREEN. `gh pr checks` 텍스트/`--json` 으로 판정(종료코드 금지). 3차원 머지 검증([`platform/git-workflow-policy.md` § Merge-Verification](../../platform/git-workflow-policy.md)).

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (**정경 — 규칙 6 role admission `:130`, 게이트웨이별 요구 `:131-135`**)
- `platform/api-gateway-policy.md` (게이트웨이 정책)
- `platform/service-types/api-gateway.md`(대상 Service Type)
- `docs/adr/ADR-MONO-032-*`(roles = sole authorization axis) · `ADR-MONO-048`(게이트웨이 lib 추출 + D4 도메인 소유) · `ADR-MONO-049`(보안 사본 → lib, D5-6/7/8 감시되지 않는 성질)
- `projects/{wms,ecommerce}-*/apps/gateway-service/.../filter/*Filter.java` (참조 모양 — 준수 게이트웨이)

# Related Skills

- `.claude/skills/backend/gateway-security/SKILL.md` (`TASK-MONO-410` 이 규칙 6 role admission(403)을 신설해 둠 — 구현이 실제로 따라 쓸 표면)

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

---

# Target Service

`projects/fan-platform/apps/gateway-service` · `projects/erp-platform/apps/gateway-service` · `projects/scm-platform/apps/gateway-service` (+ AC-2 가 추출을 택하면 `libs/java-gateway`). Service Type = `api-gateway`.

---

# Implementation Notes

- **이 결함은 컴파일러·유닛테스트가 못 잡는다** — 게이트웨이는 인증을 하므로 컴파일·기동은 정상이고, 유효 토큰만 쓰는 테스트는 초록이다. **통합 레인만 본다**(AC-5). `ADR-MONO-049 D5-8` 이 정확히 이 실패 모드: *프로덕션이 모든 토큰을 거부해도 50개 스위트 초록.* 그 거울상(모든 토큰을 통과시켜도 초록)이 지금 상태다.
- **소스를 읽고 배선을 판정하라, 이름으로 추측하지 말 것.** scm 이 세 번 오분류된 이유가 `ScmTokenType`(admission 처럼 읽히는 이름)이다. `AccountTypeValidationFilter`(wms)·`AccountTypeEnforcementFilter`(ecommerce)가 참조 모양 — `GlobalFilter implements Ordered`, `ReactiveJwtAccess.currentJwt()` 또는 `ReactiveSecurityContextHolder` 로 토큰을 얻고 `jwt.getClaimAsStringList("roles")`(=`JwtClaims.CLAIM_ROLES`) 로 **배열을 직접** 읽는다(콤마 결합 문자열 `JwtClaims.role(jwt)` 로 admit 하면 안 된다 — MONO-410 착수 기록의 함정).
- **finance 를 (a)로 결정하면** erp 와 동일 패턴이 4번째로 붙는다 ⇒ AC-2 의 lib 추출 근거가 더 강해진다.
- **iam 은 건드리지 않는다** — `JwtAuthenticationFilter` 의 tenant-scope 인가는 자기 계약(TASK-BE-460 등)이 지키는 별개 성질. 여기에 role admission 을 얹으면 계약에 없는 규칙을 발명하는 것.

---

# Edge Cases

- **공개 경로**(actuator health, 각 게이트웨이의 public route)는 security context 가 없다 → **통과해야 한다.** wms/ecommerce 는 `defaultIfEmpty(Boolean.TRUE)` 로 처리 — `switchIfEmpty(chain.filter())` 안티패턴(Mono<Void> 는 성공 시 empty 완료라 "auth 없음"과 구별 불가)을 피한 그 형태를 그대로.
- **presence-only vs named-role-set** — wms 는 "≥1 any role" 인데 이는 operator-only + tenant-gated 플랫폼의 degenerate 케이스라 정당하다. fan·erp·scm 에 대해 계약은 **특정 역할**을 명명한다(FAN / operator). 한 플랫폼이 여러 역할 계열을 발급하고 그중 일부만 그 표면에 유효하면 presence-only 는 잘못된 역할을 통과시킨다 ⇒ **named-set 이 계약 letter 에 맞고 이중방어 관점에서도 강하다.** 단, 그 플랫폼이 자기 표면용 역할만 발급한다면 두 해석은 수렴 — AC-4 에서 실제 발급 역할을 읽고 판정.
- **SUPER_ADMIN 와일드카드** — fan/erp/scm 의 `tenantGate()` 는 `allowSuperAdminWildcard()` 를 켠다(플랫폼 운영자 incident response). 그 토큰이 admission 도 통과해야 하는지(와일드카드 tenant 토큰이 어떤 role 을 carry 하는지) AC-4 에서 확인 — 못 통과시키면 incident response 경로를 끊는다.
- **레거시 역할 없는 토큰** — 계약은 roles-only end state(`:139`, account_type 폐기). 역할 0개 토큰은 규칙 6 상 거부가 맞다. 발급 측이 항상 role 을 싣는지 확인(안 실으면 정상 사용자도 403 — 발급 측 결함 별건).

---

# Failure Scenarios

- **wms 필터를 3번 복붙한다** → 사본 50번째, `ADR-MONO-049` 가 지운 패턴 재생산. 완화 = AC-2(lib 추출 판정).
- **역할 이름을 계약 예시(`WMS_OPERATOR`)에서 그대로 베낀다** → 그 플랫폼에 존재하지 않는 role 을 gate 로 삼아 **정상 토큰을 전부 403** 하거나(너무 좁음) 아무도 못 가진 role 이라 사실상 무방비(너무 넓음). 완화 = AC-4(발급 소스에서 읽는다).
- **유효 토큰만으로 테스트하고 초록을 믿는다** → admission 이 빠져도 초록(`D5-8`). 완화 = AC-5(음성 케이스 = 403 단언).
- **finance 에 admission 을 self-decision 으로 붙인다** → 계약에 없는 규칙을 구현으로 발명. 완화 = AC-3(블로킹, 사람 결정).
- **CI-RED 인데 머지한다** → main 회귀. 완화 = AC-6(3차원 검증, 종료코드 금지).

---

# Test Requirements

- 각 게이트웨이(fan·erp·scm): **유효 역할 없는 인증 토큰 → 403**(음성, 프로덕션 필터 체인) + **유효 역할 토큰 → 통과**(양성) + **공개 경로 → 통과**(context 없음). Testcontainers IT, **CI Linux 권위**.
- AC-2 가 lib 추출이면: lib 필터의 파라미터화(표면→역할집합)를 단위 테스트로 고정(각 프로젝트 매핑 bean 이 그 시드를 읽게 — `IdentityHeaderStripFilterTest`/`JwtHeaderEnrichmentFilterTest` 가 프로젝트 wiring 을 읽는 그 방식).

---

# Definition of Done

- [ ] fan·erp·scm 에 규칙 6 admission 배선(AC-1) + AC-2 추출 판정 기록 + AC-4 역할 집합 소스 근거.
- [ ] AC-3 finance 결정(사람) 해소 — (a)면 계약 편집 + finance 편입, (b)면 계약 면제 명문화.
- [ ] AC-5 음성 케이스 테스트 GREEN(CI Linux).
- [ ] 머지(3-dim verify) + INDEX done entry(close chore) + 메모리 `project_gateway_role_admission_gap_2026_07_15` 에 종결 포인터.

---

# Provenance

`TASK-MONO-410`(보안 스킬 정정, ✅ PR #2579 `eaccd4cf5`) 의 **AC-4 조사가 남긴 별건**. 410 은 문서(스킬)만 고쳤고 이건 프로덕션 게이트웨이 필터를 추가한다 ⇒ **자기 티켓·자기 검증**.

**표적 집합은 재측정으로 세 번 정해졌다**(위 AC-0 표): 410 초안 "5개(…iam), ecommerce·scm 붙였다" → 인계 "3 = fan·finance·erp" → **3차 실측 = fan·erp·scm 갭 / finance 결정 / iam 범위 밖.** scm 이 `ScmTokenType`(헤더 enrichment) 때문에 세 번 오분류됐다. **origin/main 의 410 done-note·INDEX review-note 는 이 정정 이전 기록이다**(닫힌 티켓 미편집, 정정은 여기).

분석=Opus 4.8 / 구현 권장=**Opus** — 계약 해석(named-set vs presence, SUPER_ADMIN 상호작용) + lib 추출 판정 + finance 결정 해소. 문장 교체가 아니라 *무엇을 gate 로 삼을 것인가* 결정이다.

[[project_gateway_role_admission_gap_2026_07_15]] · [[feedback_recount_population_dont_inherit_scope]] · [[feedback_guard_predicate_wrong_verify_the_artifact]] · [[project_guard_reachability_not_just_bite]] · [[project_gateway_three_lineages_convergence]]
