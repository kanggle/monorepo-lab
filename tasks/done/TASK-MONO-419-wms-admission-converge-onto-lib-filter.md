# Task ID

TASK-MONO-419

# Title

wms `AccountTypeValidationFilter` 를 lib `RoleAdmissionFilter` 로 수렴 — TASK-MONO-416 이 추출한 정경 필터의 마지막 미채택 게이트웨이

# Status

done

# Owner

monorepo

# Task Tags

- code
- refactor

---

# Goal

`TASK-MONO-416` 이 `libs/java-gateway` 에 `RoleAdmissionFilter`(+`RoleAdmissions`)를 추출하고 fan·erp·scm·finance 가 채택했다. **wms 는 out-of-scope 로 남아 자기 `AccountTypeValidationFilter` 를 유지**한다(416 은 "wms/ecommerce 재작성 금지"). 그 결과 지금:

- lib presence/credential 필터 1개 + **wms 사본 1개**가 공존 = `ADR-MONO-049`(보안 사본 → lib)의 잔여 1건.

wms 를 lib 필터로 수렴시키면 presence 계열 admission 사본이 0 이 된다. ecommerce 는 path-based 도메인 지식이라 `ADR-MONO-048 D4` 로 서비스 잔류가 정당하므로 **이 티켓의 대상 아님**(수렴은 wms 만).

⚠️ **행동 변경 위험** — wms `AccountTypeValidationFilter` 는 **role presence-only**(`roles ≥1 else 403`, `AccountTypeValidationFilter.java:47-50`)다. lib 의 `RoleAdmissions.roleOrScope()` 는 **role 또는 scope**다. wms 에 `client_credentials`(scope-only) 머신 트래픽이 있으면, 수렴이 그들을 **새로 통과**시킨다(현재 wms 는 403). 반대로 wms 를 lib 의 별도 `anyRole()`(있으면 추가) 술어로 수렴시키면 byte-호환. **어느 술어로 수렴할지는 wms 머신 트래픽 실측이 정한다.**

---

# 🔴 AC-0 (착수 = 재측정, 코드가 이긴다)

- [ ] wms `AccountTypeValidationFilter` 의 현재 술어를 재확인(presence-only role 인지). lib `RoleAdmissionFilter`/`RoleAdmissions` 의 현재 API 확인(`roleOrScope()` 외에 presence-only 팩토리가 있는지 — 없으면 추가 여부가 이 티켓의 판정).
- [ ] **wms 에 `client_credentials`/scope-only 머신 토큰이 게이트웨이에 도달하는지 실측** — wms iam-integration 스펙 + wms 게이트웨이 IT(`JwtTestHelper` 에 client_credentials 헬퍼가 있는지)로. 이게 `roleOrScope` vs `anyRole` 수렴을 가른다([[env_empty_detector_output_is_not_absence]]: 없음을 증명하려면 아는 답에 먼저 돌려라).

# Scope

## In Scope

- wms `AccountTypeValidationFilter` 를 **lib `RoleAdmissionFilter` opt-in `@Bean`** 으로 교체(fan/erp/scm/finance 와 동형 — `GatewayIdentityConfig`(또는 wms 대응 config)에 bean 선언). 술어는 AC-0 실측이 정한다(머신 트래픽 없으면 presence-only 유지가 byte-호환 → lib 에 `anyRole()` 팩토리 추가 검토 / 있으면 `roleOrScope()`).
- wms `AccountTypeValidationFilterTest` 를 lib 필터 기반으로 이관(정경 필터는 lib 에서 테스트되므로 wms 는 프로덕션 bean 배선 + 음성 케이스만).
- 사본 클래스 삭제.

## Out of Scope

- **행동 변경** — AC-0 이 "머신 트래픽 없음" 이면 presence-only 를 보존한다(순수 수렴). 있으면 그건 별도 판단(계약이 wms 머신 admission 을 어떻게 말하는지)이라 이 티켓에서 하지 말고 기록.
- ecommerce(`AccountTypeEnforcementFilter`) — path-based 도메인 지식, D4 로 서비스 잔류 정당. 무접촉.
- lib 필터 자체의 로직 변경(MONO-416 검증 완료).

---

# Acceptance Criteria

- [ ] **AC-0** 위 재측정 — wms 술어 + 머신 트래픽 유무 실측, 아는 답 자기검증.
- [ ] **AC-1 (수렴)** wms 가 lib `RoleAdmissionFilter` 를 opt-in bean 으로 채택, 사본 클래스 삭제. presence 계열 admission 사본 0.
- [ ] **AC-2 (행동 보존)** AC-0 이 머신 트래픽 없음이면 wms admission 동작 불변(presence-only). 술어 선택 근거를 기록.
- [ ] **AC-3 (음성 테스트 유지)** 역할 없는 토큰 → 403 을 단언하는 테스트가 수렴 후에도 존재(프로덕션 bean 기반). 회귀 방지(D5-8).
- [ ] **AC-4 (CI 3차원 GREEN)** wms 게이트웨이 + lib 잡 GREEN, 종료코드 금지 판정.

---

# Related Specs

- `docs/adr/ADR-MONO-049-*`(보안 사본 → lib) · `ADR-MONO-048 D4`(도메인 소유 vs 재사용)
- `tasks/done/TASK-MONO-416-*.md`(lib 필터 추출 — 참조 모양)
- `projects/wms-platform/specs/integration/iam-integration.md`(머신 트래픽 재측정)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

# Target Service

`projects/wms-platform/apps/gateway-service` (+ 술어 팩토리 추가 시 `libs/java-gateway`).

---

# Edge Cases

- **wms 는 operator-only + tenant-gated 플랫폼** — presence-only 가 그 표면에선 정당(계약의 "operator role" 을 만족). 수렴이 이 성질을 깨면 안 된다.
- **lib 에 `anyRole()` 팩토리가 없으면** — presence-only 보존을 위해 추가할지, 아니면 `roleOrScope` 로 통일할지가 AC-0 실측(머신 트래픽) 결과에 종속.
- **atomic PR** — lib 에 팩토리를 추가하면 lib + wms 가 한 PR(Cross-Project Changes).

# Failure Scenarios

- **머신 트래픽 확인 없이 `roleOrScope` 로 수렴** → wms 에 머신 토큰이 있으면 문제없지만 없다고 가정하고 presence 로 갔는데 실은 있으면… 반대로도. 완화 = AC-0 실측이 술어를 정한다.
- **행동을 바꾸면서 수렴이라 부른다** → 준수 게이트웨이에 회귀. 완화 = AC-2(행동 보존).
- **ecommerce 도 수렴시킨다** → D4 도메인 지식(path split)을 lib 로 끌어와 라이브러리가 도메인 분기를 갖는다(ADR-048 부정 결과). 완화 = out-of-scope 명시.

---

# Provenance

`TASK-MONO-416` 이 lib `RoleAdmissionFilter` 를 추출하며 명시적으로 남긴 후속(416 out-of-scope: "wms/ecommerce 재작성 금지, 수렴 여부는 판정"). 정리 성격이라 우선순위 낮음 — 우선 후속(MONO-417 결정) 처리 후.

분석=Opus 4.8 / 구현 권장=**Sonnet** (머신 트래픽 실측 후 기계적 수렴).

[[project_gateway_role_admission_gap_2026_07_15]] · [[project_gateway_three_lineages_convergence]] · [[feedback_repo_knows_what_it_does_not_say]]
