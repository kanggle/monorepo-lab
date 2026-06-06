# Task ID

TASK-BE-272

# Title

SAS public-client `AuthenticationConverter` for `refresh_token` + `revoke` grants — recover Cluster A 3 IT (선행=ADR-003)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[ADR-003](../../docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md) 채택 후 옵션 A (Custom converter, full implementation) 를 구현하여 `OAuth2RefreshTokenIntegrationTest` 의 `refreshTokenGrant_normalRotation` + `refreshTokenGrant_reuseDetected_returns400` 와 `OAuth2RevokeIntrospectIntegrationTest` 의 `authCode_revokeRefreshToken_introspectInactive` 3 deferred IT method 를 회복한다.

**전제**: ADR-003 status = ACCEPTED. PR #264 11-cycle 학습된 4 anti-pattern (A1 SAS Customizer timing / A2 DomainSync vs persistRotation race / A3 manual instantiation @Transactional 불가 / A4 test order pollution) 을 모두 회피하는 설계.

---

# Scope

## In Scope

### Phase 1 — Converter 구현 + unit test (≤ 2 cycle)

신규 파일 (`projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/`):

- `PublicClientRefreshTokenAuthenticationConverter.java` — `grant_type=refresh_token + client_id + no Authorization header + ClientAuthenticationMethod.NONE` 매칭 시 authenticated `OAuth2ClientAuthenticationToken` emit.
- `PublicClientRevokeAuthenticationConverter.java` — `POST /oauth2/revoke + client_id + no Authorization header + NONE` 동일 패턴.

`AuthorizationServerConfig.authorizationServerSecurityFilterChain` 에서 두 converter 를 SAS 의 `tokenEndpoint(...).accessTokenRequestConverter(...)` 와 `tokenRevocationEndpoint(...).revocationRequestConverter(...)` 에 wire.

신규 unit test:

- `PublicClientRefreshTokenAuthenticationConverterTest.java` — 5+ 케이스: matching grant_type / non-matching grant_type / missing client_id / present Authorization 헤더 / NONE method 미허용 client / null returned 시나리오.
- `PublicClientRevokeAuthenticationConverterTest.java` — 동일 패턴.

**회귀 가드 (Phase 1 종료 시 PASS 유지)**:

- `OAuth2AuthCodePkceIntegrationTest` 7/7 (authorization_code + PKCE)
- `OAuth2AuthorizationServerIntegrationTest` 모든 method (client_credentials)
- `OAuth2JpaPersistenceIntegrationTest`
- `OAuth2RevokeIntrospectIntegrationTest` 의 enabled method (3 method, public-client revoke 외 — `clientCredentials_issueAccessToken` 등)

### Phase 2 — IT enable + CI verify (≤ 2 cycle)

3 IT method `@Disabled` 제거:

- `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation`
- `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_reuseDetected_returns400`
- `OAuth2RevokeIntrospectIntegrationTest.authCode_revokeRefreshToken_introspectInactive`

이전 PR #264 의 disable 사유 강화 주석 (refresh_token grant 의 missing public-client converter) 도 같은 commit 에서 cleanup. `OAuth2RefreshTokenIntegrationTest.refreshTokenGrant_normalRotation` 위의 long-form 주석은 제거 + 짧은 comment 로 대체 ("ADR-003 옵션 A 채택 — PublicClientRefreshTokenAuthenticationConverter 가 인증 entry 제공").

CI run → 회귀 매트릭스 8 케이스 모두 PASS 검증 (ADR-003 § "회귀 매트릭스" 참조).

### Phase 3 — Cluster C 부수 효과 검증 (≤ 1 cycle)

`OAuthLoginIntegrationTest` 의 5 disabled method 는 본 task 의 직접 영역이 아니지만 (Cluster C RC 는 Linux-specific HTTP/network 영역 — ADR-004 영역), converter 추가가 Cluster C 의 503 빈도/패턴에 변화를 주는지 1 cycle observation. 영향 0 → ADR-004 분리 진행.

만약 converter 추가가 Cluster C 의 일부 method 도 PASS 시키면 (예상 외 부수 효과), 추가 PR 로 enable 가능. 단, 본 task scope 는 Cluster A 3 만.

## Out of Scope

- Cluster C 5 IT (`OAuthLoginIntegrationTest`) — ADR-004 / TASK-BE-273
- Cluster B userinfo (이미 PR #264 회복)
- `SasRefreshTokenAuthenticationProvider` 또는 `DomainSyncOAuth2AuthorizationService` 의 도메인 로직 변경 — 본 task 의 변경 범위는 인증 entry (converter) 만
- `demo-spa-client` 의 등록 정보 변경 — public client + PKCE + ["none"] auth method 유지
- 다른 grant type (client_credentials / authorization_code) 의 converter 변경 — stock 경로 그대로 활용

---

# Acceptance Criteria

- [ ] AC-01 — `PublicClientRefreshTokenAuthenticationConverter` + `PublicClientRevokeAuthenticationConverter` 신설, unit test 모두 PASS
- [ ] AC-02 — `AuthorizationServerConfig` 에서 두 converter 가 SAS configurer 의 token / revocation endpoint 에 wire
- [ ] AC-03 — Cluster A 3 IT method `@Disabled` 제거 + CI Integration (GAP) Job 에서 PASS
- [ ] AC-04 — 회귀 가드 8 케이스 (ADR-003 § "회귀 매트릭스") 모두 PASS — authorization_code/PKCE/client_credentials/test-internal-client revoke 모든 흐름 무회귀
- [ ] AC-05 — 4 anti-pattern (A1-A4) 위반 0 — converter 안에 IO/transaction/manual instantiation 불포함, `@DirtiesContext(AFTER_CLASS)` 패턴 유지
- [ ] AC-06 — 총 cycle ≤ 5 (Phase 1: 2 + Phase 2: 2 + Phase 3: 1). 초과 시 ADR-003 § "Alternative Path" 의 옵션 B (provider-side fallback) 로 전환 검토 후 별 task
- [ ] AC-07 — Cluster C 5 IT 변화 없음 (`@Disabled` 유지) — 본 task 가 Cluster C 에 영향 0 임을 Phase 3 cycle 에서 확인

---

# Related Specs

- [ADR-003 — SAS Public-Client AuthenticationConverter](../../docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md)
- `tasks/done/TASK-MONO-046-7-auth-service-sas-deferred-8.md` (root) — 11-cycle burn 결과
- `tasks/done/TASK-MONO-046-7a-auth-service-sas-7-deferred.md` (root) — 0/7 recovery, 사유 강화
- `projects/global-account-platform/specs/services/auth-service/architecture.md` — SAS 통합 디자인
- `projects/global-account-platform/specs/services/auth-service/idempotency.md` — RT 멱등성

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` — `/oauth2/token` `/oauth2/revoke` API
- 본 task 의 변경은 contract level 변화 0 (이미 등록된 public-client 의 행위 보장만 강화)

---

# Edge Cases

- **converter 가 fire 했는데 `clientRepository.findByClientId(...)` 가 null 반환** (등록 안된 client_id 로 시도): converter 가 null 반환 → SAS 가 다음 converter (stock) 시도 → stock 도 매칭 안되면 `INVALID_CLIENT` 401. 회귀 매트릭스 의 "registered but wrong method" 케이스로 unit test 추가.
- **client 가 ["none", "client_secret_basic"] 같이 복수 method 등록**: 본 converter 는 NONE 가 set 에 포함된 경우만 fire. confidential 흐름 우선순위 유지.
- **`Authorization` 헤더 + `client_id` 동시 전송**: converter 가 null 반환 → stock `ClientSecretBasicAuthenticationConverter` 가 처리. RFC 6749 § 2.3.1 의 "MUST NOT use more than one authentication method" 와 호환.
- **`refresh_token` grant 의 cross-tenant 시도**: converter 는 client identity 만 인증. `SasRefreshTokenAuthenticationProvider` 의 cross-tenant 검사 (`extractClientTenantId` 비교) 는 그대로 작동 — ADR-003 의 변경 영역 외.
- **`revoke` grant 의 token type hint**: SAS 의 stock `OAuth2TokenRevocationAuthenticationProvider` 가 revoke 책임. 본 task 의 converter 는 인증만. RFC 7009 의 모든 token-type-hint 시나리오 무영향.

# Failure Scenarios

- **Phase 1 단위 test 까지 PASS 됐지만 Phase 2 IT 에서 다른 회귀 발생** (예: `OAuth2AuthCodePkceIntegrationTest` 의 PKCE 경로가 깨짐): 즉시 revert + cycle 재시작. 회귀 root cause 진단 우선. ADR-003 § "Alternative Path" 의 옵션 B (provider-side fallback) 로 전환 검토.
- **Phase 2 에서 Cluster A 3 IT 중 1-2 만 PASS**: 부분 PASS 케이스의 패턴 분석. 만약 normal rotation 만 PASS / reuse-detection FAIL 이면 `SasRefreshTokenAuthenticationProvider` 의 reuse-detection 경로가 converter 변경에 영향받았을 가능성 — 도메인 코드 무영향 가설 재검토.
- **Phase 3 에서 Cluster C 일부 PASS** (예상 외): 본 task scope 외이지만 PR commit 에 명시 + ADR-004 의 진단 가설 수정. ADR-004 의 옵션 A (diagnostic harness) 가 아직 시행 전이라면 본 task PR 의 부수 effect 가 그 진단을 부분 대체.
- **6 cycle 초과해도 미해결**: ADR-003 의 fallback (옵션 B → C → D) 로 별 task 발행. 본 task 는 옵션 A 단독 실패로 close.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — SAS converter lifecycle + 4 anti-pattern 회피 = complex domain. Sonnet 으로 burn 시 anti-pattern 위반 위험.
- **연관 메모리**: `project_046_7_11_cycle_burn` (ADR-003 의 base context), `project_gap_idp_promotion` (SAS 도입 결정).
- **회귀 가드 우선**: PR #264 의 cycle 6 best state (60-2 fail) 가 본 task 의 baseline. converter 추가 후 60-2 가 60-0 으로 떨어지는지가 PASS criteria. 더 떨어지면 회귀.
