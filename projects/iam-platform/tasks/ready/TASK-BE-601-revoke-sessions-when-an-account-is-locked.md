# Task ID

TASK-BE-601

# Status

ready

# Title

계정이 잠기면 그 계정의 세션을 폐기한다 — 잠금 전에 받은 세션이 토큰 갱신으로 계속 산다

# Owner

iam-platform

# Task Tags

- auth-service
- security
- event-consumer

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — 이벤트 소비 · 멱등 · 두 토큰 저장소(SAS 인가 · 자체 refresh) 정합이 걸린다.

---

# Goal

`TASK-BE-600`(#4020 `9fc286ba3`)이 **새 로그인**에서 잠긴 계정을 거부하게 했다. 그러나 **잠기기 전에 받은 세션**은 그대로 산다 —
`SasRefreshTokenAuthenticationProvider.java:173-219` 는 계정 상태를 보지 않고, auth-service 는 `account.locked` 를 구독하지 않는다
(BE-600 AC-0 ① 실측). ⇒ 자동 잠금(security-service)이든 관리자 잠금이든, 이미 로그인한 공격자는 refresh 로 **계속 머문다**.

🔵 **소유자 결정 (2026-09-25 UTC)** — 방식 = **잠금 이벤트로 세션 폐기**. 기각: «갱신 때마다 상태 조회(fail-closed)» — account-service
장애가 모든 활성 세션의 갱신 장애로 번진다. 받아들인 대가: 이벤트 전파 지연만큼의 틈.

## 실측 (2026-09-25 UTC · 저장소만)

| 사실 | 근거 |
|---|---|
| 폐기 유스케이스는 이미 있다 | `auth-service/…/application/ForceLogoutUseCase.java` — `refreshTokenRepository.revokeAllByAccountId` + Redis 무효화 마커(access token TTL 동안). 테넌트 한정 오버로드(BE-468) 포함 |
| 지금 부르는 곳은 관리자 «세션 폐기» 하나 | `InternalCredentialController.java:77` `POST /internal/auth/accounts/{id}/force-logout` ← admin-service `SessionAdminUseCase` → `AuthServiceClient.forceLogout`. 🔴 **잠금과는 별개 동작** — account-service 잠금 경로에 force-logout 호출 0(grep) |
| `account.locked` 는 발행되고 있다 | account-service `AccountStatusEvents` / outbox → 토픽 `account.locked`. 현재 소비자 = security-service `AccountLockedConsumer`(잠금 이력 기록)뿐 |
| ⚪ 미측정 | ① `revokeAllByAccountId` 가 **SAS 인가 저장소**(`oauth2_authorization`)의 refresh 까지 무효화하는가, 자체 `refresh_tokens` 만인가 — BE-599 보고에 «SAS refresh 미러 행» 언급. 한쪽만 지우면 다른 쪽으로 갱신된다 ② 이벤트 봉투의 테넌트 필드 — BE-468 한정 오버로드에 무엇을 넘길지 ③ 관리자 잠금이 이미 같은 효과를 내는 다른 경로가 있는가 |

# Scope

## 포함

- auth-service 에 `account.locked` 소비자 — `ForceLogoutUseCase` 로 그 계정의 세션을 폐기한다. 멱등(재전달·중복 안전) · 테넌트 봉투 검증(security-service 소비자의 DLQ 규칙과 같은 결).
- ⚪ ① 결과에 따라 SAS 인가 저장소의 refresh 도 무효화(필요하면 `ForceLogoutUseCase` 확장 — 관리자 폐기도 같은 구멍이면 같이 고쳐진다).
- 계약 문서: `account.locked` Consumers 에 auth-service 추가.

## 제외

- 갱신 때 상태 조회(기각된 방식).
- DORMANT · DELETED 이벤트 — 별도 판단(필요하면 AC-0 에서 이름으로 남긴다).

# Acceptance Criteria

- [ ] **AC-0** — ⚪ 셋을 재고 범위를 확정한다. 🔴 ① 이 «자체 저장소만» 이면, 이 티켓이 폐기해도 SAS 쪽 refresh 로 여전히 산다 — 그것을 고치지 않으면 AC-3 이 실패해야 정상이다.
- [ ] **AC-1** — 소비자 구현 + 단위 테스트: 잠금 이벤트 → 폐기 호출 · 중복 이벤트 → 두 번째는 no-op · 테넌트 없는 봉투 → DLQ · 폐기 실패 → 재시도/DLQ(조용한 삼킴 금지).
- [ ] **AC-2** — 계약·아키텍처 문서 갱신(소비자 추가 · 지연 틈 명시).
- [ ] **AC-3** — 🔴 **결과로 판정**(창 또는 iam e2e compose): 일회용 계정 로그인 → refresh 성공(대조군) → 잠금 → 같은 refresh 토큰으로 갱신 **거부**. 🔴 공유 데모 계정은 잠그지 마라.

# Related Specs

- `specs/contracts/events/account-events.md`(또는 `account.locked` 를 정의하는 파일) · `specs/services/auth-service/architecture.md`
- `TASK-BE-600` (새 로그인 거부 · AC-0 ① 이 이 티켓의 출처)
- `TASK-BE-468` (테넌트 한정 force-logout)

# Related Contracts

- `account.locked` — 스키마 불변, 소비자 추가.
- `POST /internal/auth/accounts/{id}/force-logout` — 불변(같은 유스케이스를 이벤트로도 부른다).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 잠금 직후 해제 | 폐기는 이미 일어났다 — 사용자는 다시 로그인한다(정상) |
| 이벤트가 늦게 온다 | 그 사이 갱신은 성공할 수 있다 — 수용한 틈(소유자 결정). 로그로 지연을 볼 수 있게 한다 |
| auth-service 가 내려가 있을 때 발행 | Kafka 가 보관 — 복귀 후 소비(오프셋) |

# Failure Scenarios

1. **자체 refresh 저장소만 지운다** → SAS 인가 저장소로 계속 갱신(AC-0 ①).
2. **소비자가 실패를 삼킨다** → 잠금은 됐는데 세션은 산다, 그리고 아무도 모른다.
3. **단위 테스트로 닫는다** → 실제 갱신 거부는 모른다(AC-3).
