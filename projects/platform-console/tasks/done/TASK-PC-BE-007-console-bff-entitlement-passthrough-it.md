# Task ID

TASK-PC-BE-007

# Title

ADR-MONO-019 § 3.3 step 3 (console-bff 항목) — console-bff `tenant_id` + `entitled_domains` pass-through 회귀-가드 IT. entitlement-trust 모델에서 console-bff 가 **고객-id `tenant_id`(`acme-corp`) + 서명 `entitled_domains` claim 을 변형 없이 5 도메인에 전달**함을 증명(ADR-017 D6 producer-side authority 보존; BFF 는 재작성/중앙 게이트/claim strip 안 함).

# Status

done

> **완료 (2026-05-31)**: impl PR #972 (squash `0a11e0f7`). ADR-MONO-019 § 3.3 step 3 console-bff deliverable("1 console-bff tenant_id pass-through IT, ADR-017 D6"). 신규 `EntitlementPassThroughIntegrationTest`(CrossTenantDenyIntegrationTest 하니스 미러) — operator JWT(`tenant_id=acme-corp` + `entitled_domains=[finance,wms]` 서명) + `X-Tenant-Id=acme-corp`(일치). **AC-A/C**: 비-GAP 5 leg 각 outbound Bearer = inbound 토큰 **byte-identical**(entitled_domains claim 보존 — strip/재서명 안 함) + `X-Tenant-Id` verbatim(ADR-017 D6.A no-rewrite). **AC-B**: finance/wms 200→ok card / scm/erp 403→forbidden·PERMISSION_DENIED per-card(200 envelope, 중앙 collapse·gate 없음). **production code 0** — BFF 가 이미 pass-through(OperatorCredentialContext raw bearer 추출 → CredentialSelectionAdapter 비-GAP→GapOidcAccessToken → RestClientHelper as-is; entitled_domains 미검사). regression gate. **3차원**(MERGED `0a11e0f7` / tip 일치 / pre-merge 0). **BE-299 re-stage** ✓. **CI 1-pass**: console-bff Integration GREEN 50s(MockWebServer+@SpringBootTest, stateless — Testcontainers 불요; agent 로컬 실행도 PASS) + Build GREEN. **scope-lock**: 신규 IT 1 파일만(src/main 0). **후속**: PC-BE-008(acme-corp operator 시드 + federation-hardening-e2e 런타임 cross-domain spec). **메타**: console-bff 는 credential 재설계 불필요 — pass-through 투명성을 IT 로 고정하는 것이 step 3 console-bff 몫. operator OIDC 토큰의 tenant_id 는 `auth_db.credentials.tenant_id` 에서 옴(CredentialAuthenticationProvider setDetails → customizer principal-details 우선; ClientSettings `gap` fallback 미도달) → acme-corp 계정 로그인 시 tenant_id=acme-corp + keystone entitled_domains=[finance,wms].

# Owner

backend

# Task Tags

- code
- security
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED + step 3 게이트 4/4(FIN/ERP/SCM-BE / BE-323) + keystone(BE-324 — `entitled_domains` producer) + step 2(BE-325 — acme-corp 구독). ADR-017 D4/D6(console-bff credential dispatch + tenant_id pass-through, HARD INVARIANT) + TASK-PC-BE-006(CrossTenantDenyIntegrationTest — 본 IT 의 하니스 템플릿).
- **paired (후속)**: TASK-PC-BE-008(acme-corp operator 시드 + federation-hardening-e2e 런타임 cross-domain spec — nightly).
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (기존 CrossTenantDenyIntegrationTest 패턴 미러 IT; production code 0).

---

# Goal

ADR-019 § 3.3 step 3 의 console-bff deliverable("1 console-bff `tenant_id` pass-through IT (ADR-017 D6)")를 entitlement-trust 모델에 맞춰 구현한다. ADR-017 D6.A(HARD INVARIANT): **BFF 는 operator 의 GAP OIDC access token 을 비-GAP 도메인에 그대로 전달 + `X-Tenant-Id` verbatim 포워딩 — 토큰을 새로 mint 하거나 tenant 를 재작성하거나 claim 을 strip 하지 않는다.** TASK-PC-BE-006(CrossTenantDenyIntegrationTest)은 *forged* cross-tenant(token `gap` vs `X-Tenant-Id` `scm`) verbatim 전달을 증명했으나, **entitlement-trust 모델의 고객-id 토큰**(`tenant_id=acme-corp` + `entitled_domains=[finance,wms]`)이 BFF 를 통과할 때 그대로 보존되는지는 미검증.

본 IT 는 이를 증명:
- operator JWT 가 `tenant_id=acme-corp` + `entitled_domains=[finance,wms]`(서명) claim 보유, `X-Tenant-Id=acme-corp`(일치).
- BFF 가 5 도메인 leg 각각에 **(a) Authorization Bearer 로 동일 토큰 전달**(outbound 토큰 디코드 시 `entitled_domains=[finance,wms]` claim 보존 — strip/재서명 안 함) **(b) `X-Tenant-Id=acme-corp` verbatim**.
- entitled 도메인(finance/wms) 200 / 미-entitled(scm/erp) 403 stub 시 → BFF 가 per-card `ok`(finance/wms) / `forbidden`(scm/erp)을 200 envelope 로 surface(중앙 collapse/mask 없음) — entitlement-trust 의 도메인별 결과를 BFF 가 충실히 반영.

이는 console-bff 가 **새 entitlement 모델에 코드 변경 없이 투명**함을 회귀-가드로 고정한다(ADR-019 가 console-bff 를 credential rewriter 로 만들지 않음을 입증).

# Scope

## In scope

1. 신규 IT `projects/platform-console/apps/console-bff/src/test/java/com/kanggle/platformconsole/bff/integration/EntitlementPassThroughIntegrationTest.java`(또는 CrossTenantDenyIntegrationTest 와 동일 패키지) — `AbstractConsoleBffIntegrationTest` 확장, CrossTenantDenyIntegrationTest 하니스(5 MockWebServer + RSA 서명 JWT + JWKS publish + `@DynamicPropertySource` outbound base-url) 미러.
   - operator JWT claims: `tenant_id=acme-corp`, `entitled_domains=["finance","wms"]`(`List<String>`), iss/sub/aud/exp 기존 패턴.
   - `X-Tenant-Id=acme-corp`(token 과 일치 — forged 아님), `Authorization: Bearer <jwt>`, `X-Operator-Token` 기존 패턴, finance leg 는 `X-Finance-Default-Account-Id` 필요 시 set(아니면 short-circuit 회피 위해 다른 leg 로 검증).
   - stub: finance/wms 200(+ 최소 JSON body), scm/erp 403, gap 200(federate). FINANCE short-circuit 주의(필요 헤더 set 또는 wms 로 entitled-accept 검증).
   - **단언**:
     - **AC-A (토큰 pass-through)**: 각 비-GAP leg 의 `RecordedRequest` Authorization 헤더에서 Bearer 토큰 추출 → 디코드 → payload 에 `entitled_domains` claim = `[finance,wms]` 보존(BFF strip/재서명 안 함). `X-Tenant-Id=acme-corp` verbatim.
     - **AC-B (per-card 충실 반영)**: 응답 200 envelope, finance/wms card `status=ok`(또는 데이터 present), scm/erp card `status=forbidden`/`reason=PERMISSION_DENIED`(중앙 collapse/mask 없음 — CrossTenantDeny 매핑 동일).
     - **AC-C (no rewrite)**: 어떤 leg 도 `tenant_id`/`entitled_domains` 가 재작성되지 않음(verbatim).

## Out of scope

- console-bff production code 변경(이미 pass-through — IT 로 증명만; 변경 시 D6 위반).
- acme-corp operator/credential/finance-data 시드 + federation-hardening-e2e 런타임 spec(별 PC-BE-008).
- 도메인 게이트/keystone/카탈로그 변경.
- multi-고객 세션 전환(D3-B, 미명세 — 별 ADR).

# Acceptance Criteria

- **AC-1**: 신규 IT 가 `tenant_id=acme-corp` + `entitled_domains=[finance,wms]` operator JWT 로 operator-overview 호출 시, 5 leg 모두에 동일 Bearer 토큰(entitled_domains claim 보존) + `X-Tenant-Id=acme-corp` verbatim 전달됨을 RecordedRequest 로 단언.
- **AC-2**: finance/wms 200 + scm/erp 403 stub → 200 envelope 에 finance/wms `ok` / scm/erp `forbidden`(PERMISSION_DENIED) per-card. 중앙 게이트/collapse 없음.
- **AC-3 (no rewrite)**: outbound 토큰/`X-Tenant-Id` 가 BFF 에서 변형되지 않음(ADR-017 D6.A).
- **AC-4**: console-bff production code 변경 0(IT 만). 컴파일 + IT GREEN — **CI Linux platform-console console-bff Integration(Testcontainers + WireMock JWKS)** 권위 게이트.
- **AC-5 (scope-lock)**: 변경 = 신규 IT 1 파일(+ 필요 시 spec 노트). production code/시드/도메인 게이트/e2e 0.

# Related Specs

- `docs/adr/ADR-MONO-017-...md` D4/D6(console-bff credential dispatch + tenant_id pass-through HARD INVARIANT).
- `docs/adr/ADR-MONO-019-...md` § 2 D5 + § 3.3 step 3(console-bff line "1 console-bff tenant_id pass-through IT (ADR-017 D6)").
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9(outbound dispatch table) / § 2.6.
- `projects/platform-console/tasks/done/TASK-PC-BE-006-...md`(하니스 템플릿).

# Related Contracts

- console-bff 는 GAP 서명 토큰(`tenant_id`+`entitled_domains`)의 pass-through. 도메인 게이트가 entitlement 권위(producer-side).

# Related Code

- `apps/console-bff/.../integration/CrossTenantDenyIntegrationTest.java`(템플릿) + `AbstractConsoleBffIntegrationTest` + `OperatorOverviewIntegrationTest`(stub/JWT 패턴).
- `apps/console-bff/.../adapter/outbound/http/RestClientHelper.java`(Authorization + X-Tenant-Id set) + `CredentialSelectionAdapter`(비-GAP→GapOidcAccessToken).

# Edge Cases

- **FINANCE short-circuit**: `X-Finance-Default-Account-Id` 없으면 MISSING_PREREQUISITE — finance entitled-accept 검증하려면 헤더 set, 아니면 wms 로 entitled 통과 검증 + finance 는 별 처리.
- **토큰 디코드**: outbound Bearer 는 inbound 와 동일(BFF mint 안 함) — RecordedRequest Authorization 에서 추출해 nimbus 로 payload 파싱, `entitled_domains` 보존 확인.
- **claim 형**: `entitled_domains` = JSON array. inbound JWT 빌드 시 `List.of("finance","wms")`.
- **per-card 매핑**: 403→forbidden/PERMISSION_DENIED 는 OperatorOverviewIntegrationTest/CrossTenantDeny 와 동일 매핑 재사용.

# Failure Scenarios

- BFF 가 토큰 재서명/strip → entitled_domains 소실 → 도메인 게이트가 entitlement 인식 불가 → AC-1/AC-3 가 잡음.
- BFF 가 X-Tenant-Id 재작성 → D6 위반 → AC-3.
- 중앙 collapse(한 leg 403 시 전체 실패) → AC-2.
- production code 손댐 → D6 HARD INVARIANT 위반 → AC-4/AC-5.

---

# Implementation Design Notes

- CrossTenantDenyIntegrationTest 를 템플릿으로: 차이 = (a) tenant_id=acme-corp + entitled_domains claim, (b) X-Tenant-Id=acme-corp(일치), (c) finance/wms 200 / scm/erp 403, (d) outbound Bearer 토큰 디코드해 entitled_domains 보존 단언.
- production code 무변경(이미 D6 pass-through). IT only.
- CI Linux console-bff Integration 권위. 구현 = Sonnet.

---

# Notes

- ADR-019 § 3.3 step 3 console-bff deliverable. 후속 PC-BE-008(런타임 federation-e2e). console-bff 는 credential 재설계 불필요 — pass-through 투명성을 IT 로 고정하는 것이 step 3 의 console-bff 몫.
