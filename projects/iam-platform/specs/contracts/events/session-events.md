# Event Contract: session-events

세션 무효화 이벤트. **구현된 계약은 [auth-events.md](auth-events.md) 의 `auth.session.revoked`** 이다 (아래 § SUPERSEDED 참조).

---

## Event Envelope

[auth-events.md](auth-events.md)와 동일한 표준 envelope.

---

## session.revoked — ⚠️ SUPERSEDED (미구현 설계, 발행/소비 0) — 2026-07-16 TASK-BE-513

이 문서는 원래 세션 무효화를 위한 **bulk / cross-cutting** 이벤트 `session.revoked`(한 계정의 전체 무효화를 `revokedJtis[]` + `totalRevoked` 로 묶은 단일 이벤트)를 선언했다. **이 설계는 코드에 구현된 적이 없다.**

**실제 구현(권위)**: auth-service 는 대신 **per-device** 이벤트 [`auth.session.revoked`](auth-events.md#authsessionrevoked) 를 발행한다 — 전체 계정 revoke 시 evicted device 마다 한 건씩(bulk 단일 이벤트 아님). `TASK-BE-513` 재측정(2026-07-16)이 확인:

- **생산자**: `session.revoked` 를 발행하는 코드 **0** (auth-service outbox 는 `auth.session.revoked` 만 발행). 이 문서가 선언한 topic 명은 코드 어디에도 없었다.
- **소비자**: `session.revoked` **와** `auth.session.revoked` **둘 다 `@KafkaListener` 소비자 0**. 아래 "Consumers" 가 약속한 security-service login_history 소비도, 관측성 메트릭 소비도 **미구현**이었다.
- **Kafka init**: `TASK-BE-511` 이 이미 compose 의 topic 목록에서 미생산 `session.revoked` 를 제거하고 생산되는 `auth.session.revoked` 를 등록했다.

**⇒ 정합 방향 A (문서를 코드에 맞춤, doc-only, 런타임 무변경)**: 세션 무효화 이벤트를 참조하는 모든 spec/use-case 는 `auth.session.revoked`(per-device)를 가리킨다. 이 `session.revoked` 선언은 역사 기록으로만 남긴다.

### 원래 설계 의도 (미구현 — 향후 참고용)

bulk 형태(`revokeReason` enum · `totalRevoked` · 계정 단위 단일 이벤트)와 "원인 이벤트 / 결과 이벤트 분리"(교차 관심사 전담 토픽)라는 설계 취지는 참고 가치가 있으나, **현재 구현은 per-device 모델**이다. 만약 미래에 bulk 무효화 이벤트 또는 security-service 의 revocation→`login_history(outcome=SESSION_REVOKED)` 소비를 실제로 도입하려면 **별도 기능 티켓**으로 진행한다 — 이 문서를 근거로 "이미 있는 계약" 인 양 소비자를 붙이지 말 것(그 소비는 오늘 존재하지 않는다).

원래 payload 초안(참고, 비권위):
```json
{
  "accountId": "string",
  "revokedJtis": ["string (UUID)", "..."],
  "revokeReason": "USER_LOGOUT | ADMIN_FORCE_LOGOUT | TOKEN_REUSE_DETECTED | ACCOUNT_DELETED | ACCOUNT_LOCKED",
  "actorType": "user | operator | system",
  "actorId": "string | null",
  "revokedAt": "2026-04-12T10:00:00Z",
  "totalRevoked": 5
}
```
구현된 per-device payload 는 [auth-events.md `auth.session.revoked`](auth-events.md#authsessionrevoked) 를 정본으로 본다.
