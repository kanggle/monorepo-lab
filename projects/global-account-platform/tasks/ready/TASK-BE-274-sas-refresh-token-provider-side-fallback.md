# Task ID

TASK-BE-274

# Title

`SasRefreshTokenAuthenticationProvider` provider-side fallback for RT 2 IT — recover Cluster A 2/3 (선행=ADR-003 옵션 B)

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

[ADR-003](../../docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md) 의 옵션 A 가 [TASK-BE-272](../done/TASK-BE-272-public-client-refresh-token-revoke-converter.md) (PR #292) 에서 부분 성공 — Cluster A 3 IT 중 1 (revoke) 만 회복하고 RT 2 method (`refreshTokenGrant_normalRotation` + `refreshTokenGrant_reuseDetected_returns400`) 는 A2 anti-pattern (`idx_rt_jti` UNIQUE violation, `DomainSyncOAuth2AuthorizationService.save()` 와 `SasRefreshTokenAuthenticationProvider.persistRotation()` 의 dual-INSERT race) 재발로 재@Disabled. 본 task 는 ADR-003 § "Alternative Path" 의 **옵션 B (provider-side fallback)** 를 적용해 RT 2 IT 를 회복한다.

**전제**: ADR-003 status = ACCEPTED — partial. PR #292 머지 완료. 본 task 의 변경 영역 = `SasRefreshTokenAuthenticationProvider` (또는 `DomainSyncOAuth2AuthorizationService`) 의 dual-INSERT 경로 — BE-272 의 "도메인 코드 변경 0" 전제와 정반대.

---

# Scope

## In Scope

### Phase 1 — Race 진단 (≤ 1 cycle)

`SasRefreshTokenAuthenticationProvider.authenticate()` 의 호출 경로를 추적해 `idx_rt_jti` 에 대한 INSERT 가 어디서 두 번 일어나는지 확인:

- (a) SAS 의 stock `OAuth2AuthorizationService.save()` (즉 `DomainSyncOAuth2AuthorizationService.save()`) 가 새 RT 를 저장
- (b) `SasRefreshTokenAuthenticationProvider.persistRotation()` 이 같은 RT 의 도메인 row 를 별도 INSERT
- 같은 transaction 안에서 두 INSERT 모두 같은 JTI 를 사용 → idx_rt_jti UNIQUE violation

진단 산출물: ADR-003 의 `## Outcome` 아래 별 sub-section `### A2 진단 결과 (TASK-BE-274)` 추가, INSERT 호출 stack 두 위치 명시.

### Phase 2 — 옵션 B fallback 구현 (≤ 3 cycle)

ADR-003 § "옵션 B" 의 핵심:

> `SasRefreshTokenAuthenticationProvider.authenticate()` 에서 `client_id` 파라미터로 client lookup → `ClientAuthenticationMethod.NONE` 인 경우 inline-authenticate. converter 추가 없음.

본 task 의 옵션 B 구체 구현 안:

- (1) **Skip-path 도입**: `DomainSyncOAuth2AuthorizationService.save()` 의 RT INSERT 를 conditional 로 만들어, 같은 transaction 안에서 `SasRefreshTokenAuthenticationProvider.persistRotation()` 이 후속 INSERT 를 책임지면 SAS-side INSERT 를 skip. 표시 메커니즘 = `RequestContextHolder` 또는 `TransactionSynchronizationManager.bindResource(...)` 로 "rotation in progress" flag 전달.
- (2) **또는 UPSERT 통일**: `DomainSyncOAuth2AuthorizationService` 의 INSERT 를 `INSERT ... ON CONFLICT (jti) DO NOTHING` 로 변환 → race 자체를 무해화. 단 PR #264 cycle 7 (`05ab3203`) 의 UPSERT 시도가 cluster C bleed 일으킨 사례 있어 careful 검증 필요.
- (3) **또는 SAS save 를 provider 가 직접 트리거**: `persistRotation()` 안에서 SAS service 의 save 를 호출하여 "single source of truth" 로 통합.

세 안 중 Phase 1 진단 결과에 따라 1개 채택. Phase 2 cycle 1 에서 채택 안의 spike → cycle 2-3 에서 회귀 가드 + 안정화.

### Phase 3 — RT 2 IT enable + CI 검증 (≤ 2 cycle)

- 2 IT method `@Disabled` 제거 + 위쪽 주석 cleanup ("ADR-003 옵션 B 채택 — provider-side fallback 으로 dual-INSERT race 해소")
- CI Integration (GAP) Job → 회귀 매트릭스 8 케이스 (ADR-003 § "회귀 매트릭스") 8/8 PASS 확인 (현재 6/8 → 목표 8/8)
- BE-272 가 회복한 revoke 1/3 + AuthCode/PKCE 7/7 등 baseline 전부 회귀 0

## Out of Scope

- Cluster B userinfo (이미 PR #264 회복)
- Cluster C 5 IT (`OAuthLoginIntegrationTest`) — TASK-BE-273 / ADR-004
- BE-272 가 추가한 converter 2개 또는 `PublicClientNoPkceAuthenticationProvider` 변경 — 본 task 는 RT rotation/reuse 의 도메인 INSERT race 만 다룸
- demo-spa-client 등록 정보 변경 (public client + PKCE + ["none"] 유지)

---

# Acceptance Criteria

- [ ] AC-01 — Phase 1 진단으로 dual-INSERT 두 위치가 ADR-003 § "A2 진단 결과 (TASK-BE-274)" 에 명시
- [ ] AC-02 — 옵션 B 구현 (skip-path / UPSERT / save 통합 중 1) 으로 같은 transaction 안 dual-INSERT 가 single-INSERT 로 수렴
- [ ] AC-03 — RT 2 IT method (`refreshTokenGrant_normalRotation` + `refreshTokenGrant_reuseDetected_returns400`) `@Disabled` 제거 + CI Integration (GAP) Job PASS
- [ ] AC-04 — 회귀 매트릭스 8 케이스 모두 PASS — BE-272 가 회복한 revoke + 다른 enabled IT 회귀 0
- [ ] AC-05 — 4 anti-pattern (A1-A4) 위반 0 — 특히 A2 의 root cause 가 architecturally 해소 (단순 retry/swallow 가 아닌)
- [ ] AC-06 — 총 cycle ≤ 6 (Phase 1: 1 + Phase 2: 3 + Phase 3: 2). 초과 시 ADR-003 의 옵션 D (영구 demote) 로 전환 후 ADR 갱신
- [ ] AC-07 — Cluster C 5 IT 영향 0 (TASK-BE-273 영역 무간섭)
- [ ] AC-08 — 단위 test (`SasRefreshTokenAuthenticationProviderTest` + `DomainSyncOAuth2AuthorizationServiceTest`) baseline 회귀 0 — 도메인 로직 변경 시 unit test 도 함께 수정 (race 검증 신규 case 추가 권장)

---

# Related Specs

- [ADR-003 — SAS Public-Client AuthenticationConverter](../../docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md) — 옵션 B 적용 영역
- `tasks/done/TASK-BE-272-public-client-refresh-token-revoke-converter.md` — BE-272 결과 (revoke 1/3, RT 2 deferred)
- `tasks/done/TASK-MONO-046-7-auth-service-sas-deferred-8.md` — PR #264 cycle 6 (`b86302d1`) persistRotation swap + cycle 7 (`05ab3203`) UPSERT 시도 + cycle 8 (`9958c2c5`) `@Transactional` 시도 등 학습된 negative lessons
- `projects/global-account-platform/specs/services/auth-service/architecture.md` — SAS 통합 디자인
- `projects/global-account-platform/specs/services/auth-service/idempotency.md` — RT 멱등성

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/auth-api.md` — `/oauth2/token` API
- 본 task 의 변경은 contract level 변화 0 (도메인 INSERT 경로 내부 race 해소만)

---

# Edge Cases

- **옵션 B (1) skip-path 채택 시 transaction abort 가 flag 를 stale 하게 남김**: `TransactionSynchronizationManager.unbindResource(...)` 를 `@AfterCompletion` 에서 강제 cleanup. unit test 로 verify.
- **옵션 B (2) UPSERT 채택 시 PR #264 cycle 7 cluster C bleed 재현**: cycle 7 의 정확한 회귀 시나리오 분석 후 회피 검증. 만약 unsafe 면 옵션 B (1) 또는 (3) 으로 전환.
- **옵션 B (3) save 통합 채택 시 SAS service 의 다른 호출자 (authorization_code grant) 영향**: AuthCode grant 는 RT 가 없으므로 무영향 가설 — IT 로 검증 (`OAuth2AuthCodePkceIntegrationTest` 7/7 PASS 유지).
- **rotation race 가 서로 다른 transaction 사이 발생** (예: 두 동시 RT 요청): 본 task 는 단일 transaction 안의 dual-INSERT 만 cover. multi-transaction race 는 별 task 영역.
- **Reuse-detection 의 chain revoke 가 dual-UPDATE 도 race 야기**: dual-INSERT 와 별 issue. Phase 1 에서 같이 진단해서 fix 범위 결정.

# Failure Scenarios

- **Phase 1 진단으로 dual-INSERT 두 위치 식별 실패**: source code grep + breakpoint debug 로 retry 1 cycle 추가. cycle 예산 초과 위험 → 즉시 보고.
- **Phase 2 옵션 B 구현 후 RT 1/2 만 PASS** (예: normal rotation PASS / reuse PASS X): 부분 성공 case 의 AC-04 회귀 매트릭스 결과 reporting 후 사용자 결정 (옵션 D demote vs 추가 task 발행).
- **Phase 3 IT enable 후 cluster C 5 IT 영향 발생** (예상 외 부수 효과): Cluster C 는 TASK-BE-273 영역. 본 task 의 변경이 OAuth callback 503 패턴에 변화 주는지 commit 별 추적. 만약 변화 있으면 BE-273 작업자에게 시그널 + 별 commit 으로 격리.
- **6 cycle 초과해도 미해결**: ADR-003 의 옵션 D (영구 demote) 적용 + RT 2 IT `@Disabled("permanent — see ADR-003 Outcome + TASK-BE-274 architectural blocker")` 영구 표기 + ADR-003 status `ACCEPTED — option D applied (RT 2 demoted, revoke 1/3 only)` 갱신.

---

# Notes

- **모델 권장**: 분석=Opus 4.7 / 구현=Opus 4.7 — `idx_rt_jti` race 진단 + transaction 경계 design + PR #264 cycle 6/7/8 negative lessons 회피 = complex domain. Sonnet 으로 burn 시 race regression 위험.
- **연관 메모리**: `project_046_7_11_cycle_burn` (cycle 6/7/8 학습), `project_gap_idp_promotion` (SAS 도입), TASK-BE-272 결과 ADR-003 § "Outcome".
- **회귀 가드 우선**: BE-272 가 회복한 revoke 1/3 + AuthCode/PKCE 7/7 + Cluster B userinfo 가 본 task 의 baseline. 떨어지면 즉시 revert.
- **PR open 정책**: 메모리 `feedback_pr_on_request` — PR open 은 사용자 명시 요청 시만. branch push 까지만 자동.
