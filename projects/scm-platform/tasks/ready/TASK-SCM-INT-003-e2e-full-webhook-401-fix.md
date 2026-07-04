# Task ID

TASK-SCM-INT-003

# Title

scm nightly e2e-full 웹훅 2×401 복구 — `AsnReceiveE2ETest` · `SupplierAckWebhookE2ETest`가 유효 HMAC 서명 미전송으로 401 (BE-033 후 @Tag("full") 미갱신 가설)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`projects/scm-platform/tests/e2e`의 nightly `@Tag("full")` 스위트(`:projects:scm-platform:tests:e2e:e2eFullTest`)가 **지속적으로 RED**다. nightly-e2e.yml `scm-platform-e2e-full` 잡이 최소 2026-07-04 이전부터 연속 failure(sha 404849f11/7e982f0d8/9bef64695/7626bf2ce 등 확인). 7 tests 중 **2개가 401**로 실패:

- `AsnReceiveE2ETest > DRAFT -> SUBMITTED -> ACK -> CONFIRMED -> RECEIVED via ASN; audit_log >=5 rows` — expected 200 but was **401**.
- `SupplierAckWebhookE2ETest > submitted PO + supplier ack webhook -> ACKNOWLEDGED + po.acknowledged.v1` — expected 200 but was **401**.

나머지 5개(`SupplierCircuitBreakerE2ETest` 포함)는 PASS. 스택은 정상 부팅(리팩토링 TASK-MONO-326의 mode=build 검증 시 5/7 통과 확인 — 인프라 문제 아님).

**유력 가설 (착수 전 검증 필수)**: 두 실패 테스트는 **웹훅 수신 엔드포인트**(ASN receive, supplier-ack webhook)를 호출한다. **TASK-SCM-BE-033**(2026-06-29, #2020)이 이 두 웹훅에 `WebhookSignatureFilter`(HMAC-SHA256 + timestamp + replay, 실패 시 401 `WEBHOOK_SIGNATURE_INVALID`/`WEBHOOK_TIMESTAMP_INVALID`/`WEBHOOK_REPLAY_DETECTED`)를 도입했다. BE-033의 3-dim은 **PR-time `@Tag("smoke")` e2e만** GREEN을 확인했고 `@Tag("full")` nightly는 확인 대상이 아니었다 → **full-tag 웹훅 e2e 테스트가 서명 없이(또는 구식 고정 시크릿으로) POST → 새 필터가 401 거부**하는 것으로 추정.

이 task는 (1) 401의 정확한 원인을 e2e 리포트 + 서비스 로그로 확정하고, (2) full-tag 웹훅 e2e 테스트가 BE-033 규격의 유효 HMAC 서명을 전송하도록 복구하여 **nightly scm e2e-full을 GREEN으로 회복**한다.

> Provenance: TASK-MONO-326(CI DRY 리팩토링) 검증 중 발견. 리팩토링은 이 실패를 **그대로 재현**(동작 보존)했을 뿐 원인이 아님 — pre-existing.

---

# Scope

## In Scope

- 원인 확정: `scm-platform-e2e-full` 잡의 `e2eFullTest` 리포트(`projects/scm-platform/tests/e2e/build/reports/tests/e2eFullTest/`) + procurement 서비스 로그에서 401의 정확한 에러 코드(`WEBHOOK_SIGNATURE_INVALID` 등) 확인.
- 가설이 맞으면: `AsnReceiveE2ETest` · `SupplierAckWebhookE2ETest`(및 공유 e2e 헬퍼/base)가 웹훅 POST 시 BE-033 규격의 서명 헤더를 생성·부착하도록 수정 — `timestamp + "." + rawBody`의 HMAC-SHA256, freshness window 내 timestamp, 유니크 서명(replay 회피).
- e2e 테스트가 사용하는 웹훅 공유시크릿을 테스트 환경(docker-compose.e2e / application-e2e.yml)의 실제 값과 일치시키기.
- `@Tag("smoke")` 대응 웹훅 테스트가 있으면 동일 서명 헬퍼를 공유하도록 정리(중복 서명 로직 방지).

## Out of Scope

- `WebhookSignatureFilter` 프로덕션 로직 변경(BE-033은 의도된 동작 — 서명 없는 웹훅 거부가 정상). 스펙상 서명이 필수면 **테스트를 고치는 것이지 필터를 완화하지 않는다**.
- TASK-MONO-326 CI 리팩토링(이미 done) — 이 task와 무관.
- scm 외 프로젝트 e2e.
- 가설이 **틀린 경우**(401이 서명이 아니라 JWKS/OIDC 토큰 문제)면 → 원인에 맞게 재범위화하되 여전히 "full-tag e2e 401 복구"가 목표.

---

# Acceptance Criteria

- [ ] 401의 정확한 원인이 리포트/로그로 문서화됨(에러 코드 명시). 가설(BE-033 서명 미전송) 확정 또는 반증.
- [ ] `:projects:scm-platform:tests:e2e:e2eFullTest`가 로컬/CI에서 **7/7 GREEN**(또는 현재 카운트 전부 PASS).
- [ ] 수정이 테스트 측에 한정됨 — `WebhookSignatureFilter` 등 프로덕션 서명 검증 로직 무변경(스펙이 서명 필수를 요구하므로).
- [ ] 웹훅 서명 생성 로직이 e2e 헬퍼로 단일화(smoke/full 중복 없음).
- [ ] 머지 후 **nightly `scm-platform-e2e-full` 잡 GREEN** 확인(push-to-main 또는 cron 1사이클).

---

# Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md` (v2, § 웹훅 서명 I6)
- `projects/scm-platform/tasks/done/TASK-SCM-BE-033-webhook-hmac-replay-protection.md` (서명 규격 SoT — `timestamp + "." + rawBody` HMAC-SHA256, 300s window, replay nonce, 401 에러 코드)
- `projects/scm-platform/tests/e2e/` 의 `AsnReceiveE2ETest` · `SupplierAckWebhookE2ETest` · e2e base/헬퍼

# Related Skills

- `.claude/skills/backend/` (해당 시)

---

# Related Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` — 웹훅 서명 헤더 + 401 에러 응답(BE-033에서 갱신됨).

---

# Target Service

- `procurement-service` (웹훅 수신) + `projects/scm-platform/tests/e2e` (테스트 모듈)

---

# Architecture

Follow: `projects/scm-platform/specs/services/procurement-service/architecture.md`. 프로덕션 서명 검증은 유지, 테스트가 규격을 따르도록 정렬.

---

# Implementation Notes

- 서명 입력 = `timestamp + "." + rawBody`(raw bytes) — e2e에서 직렬화한 body와 **바이트 동일**한 값으로 HMAC 계산해야 함(재직렬화 불일치 주의).
- replay nonce = 서명 자체(Redis SETNX). full 스위트가 같은 body를 재전송하면 replay 감지로 401 가능 → 테스트마다 유니크 timestamp/payload.
- e2e 시크릿: docker-compose.e2e.yml / application-e2e.yml 의 웹훅 시크릿 env를 테스트가 참조(하드코딩 drift 금지).
- 로컬 Windows에서 scm e2e-full은 Testcontainers 신뢰 권위 아님 — **CI Linux 레인이 권위**(memory `project_testcontainers_docker_desktop_blocker`).

---

# Edge Cases

- 401이 **일부만**(2/7) → 서명 필요한 웹훅 경로에 국한. 나머지(게이트웨이 경유 인증 API)는 OIDC 토큰으로 통과 중 → JWKS는 정상이라는 방증(가설 강화).
- ASN receive와 supplier-ack이 서로 다른 시크릿/헤더를 쓸 수 있음 — 각 엔드포인트 규격 개별 확인.
- freshness window(300s) — CI 러너 시계/컨테이너 지연으로 timestamp가 window 밖이면 `WEBHOOK_TIMESTAMP_INVALID`. 서명은 요청 직전 생성.

---

# Failure Scenarios

- 가설이 틀리고 401이 OIDC/JWKS(WireMock stand-in) 문제 → e2e-full의 토큰 발급 경로 점검으로 재범위화.
- 필터를 완화해 통과시키려는 유혹 → **금지**(스펙상 서명 필수, 보안 회귀). 테스트를 고친다.
- 서명은 맞췄으나 replay nonce 충돌로 간헐 실패 → 테스트별 유니크 payload/timestamp 보장.

---

# Test Requirements

- `:projects:scm-platform:tests:e2e:e2eFullTest` 전건 GREEN(CI Linux 레인 권위).
- 필요 시 `@Tag("smoke")` 웹훅 e2e도 동일 헬퍼로 회귀 확인.
- 머지 후 nightly `scm-platform-e2e-full` GREEN.

---

# Definition of Done

- [ ] 401 원인 확정 문서화.
- [ ] full-tag 웹훅 e2e가 유효 HMAC 서명 전송, `e2eFullTest` 전건 GREEN.
- [ ] 프로덕션 서명 로직 무변경.
- [ ] nightly `scm-platform-e2e-full` GREEN 확인.
- [ ] `projects/scm-platform/tasks/INDEX.md` done entry(close chore 시).

---

# Provenance

Surfaced 2026-07-04 — TASK-MONO-326(CI 워크플로 DRY 리팩토링) 검증 중, 머지가 트리거한 nightly에서 `scm-platform-e2e-full`의 2×401(pre-existing, 직전 nightly 3연속 RED로 확인) 발견. 사용자 결정(2026-07-04): 별도 백로그 fix task로 등록(리팩토링과 분리). 유력 원인 = TASK-SCM-BE-033 웹훅 HMAC 도입 후 @Tag("full") 웹훅 e2e 미갱신.

분석=Opus 4.8 / 구현 권장=Sonnet 또는 Opus (원인 확정=진단 우선; 수정은 테스트-한정 서명 헬퍼 → 중간 복잡도. 가설 확정 후 backend-engineer 위임 가능).
