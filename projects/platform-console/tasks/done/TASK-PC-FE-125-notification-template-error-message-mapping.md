# TASK-PC-FE-125 — 알림 템플릿 등록/수정 실패 시 "저장하지 못했습니다" 만 떠 원인을 알 수 없는 버그 (producer 코드 미매핑)

- **Status**: done
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (프런트 에러 메시지 매핑 버그픽스)

## Goal

콘솔 E-Commerce > 알림 템플릿 **등록/수정 실패 시 항상 generic "저장하지 못했습니다."** 만 노출돼, 운영자가 실패 원인을 알 수 없는 버그를 고친다. 가장 흔한 케이스(같은 유형·채널 중복)에서 "이미 존재하는 템플릿" 임을 명확히 안내한다.

근본 원인: `TemplateForm` 의 제출 에러 처리(`handleError → messageForCode(code, '저장하지 못했습니다.')`)는 producer 에러 코드를 `MESSAGES`(`shared/api/errors.ts`) 맵에서 찾아 노출하는데, 알림 템플릿 producer 코드 **`TEMPLATE_ALREADY_EXISTS`(409)** 와 **`TEMPLATE_NOT_FOUND`(404)** 가 맵에 없다. 폼 기본값은 `ORDER_PLACED + EMAIL` 이라, 해당 (type, channel) 템플릿이 이미 있는 테넌트에서 첫 등록 시 백엔드가 409 `TEMPLATE_ALREADY_EXISTS` 를 반환 → 맵 미스 → fallback "저장하지 못했습니다." 가 그대로 노출된다. (목록 GET 은 정상 동작하므로 서비스 도달 가능 = infra 장애 아님 = 409 중복이 원인.) `TASK-PC-FE-089` 가 알림 facet 을 추가하면서 이 두 코드의 메시지 매핑을 누락했다.

## Scope

**In scope** (console-web only):

1. `src/shared/api/errors.ts` — `MESSAGES` 맵에 `TEMPLATE_ALREADY_EXISTS`, `TEMPLATE_NOT_FOUND` 항목 추가(actionable 한국어 메시지).
2. `tests/unit/error-messages-notification-template.test.ts` — 두 코드가 fallback 이 아닌 actionable 메시지로 매핑됨 + 미지정 코드는 fallback 유지 회귀 테스트.

**Out of scope**: 백엔드(notification-service 는 정상 — 409/404 를 올바른 code 로 반환), 409 시 자동 "기존 템플릿 수정으로 이동" 라우팅(메시지 안내로 충분), 다른 도메인 facet 의 에러 코드.

## Acceptance Criteria

- **AC-1 — 중복 안내.** 알림 템플릿 등록이 409 `TEMPLATE_ALREADY_EXISTS` 로 실패하면 폼에 "같은 유형·채널의 알림 템플릿이 이미 있습니다…" 가 노출된다(generic fallback 아님).
- **AC-2 — not-found 안내.** 수정 제출이 404 `TEMPLATE_NOT_FOUND` 로 실패하면 "대상 알림 템플릿을 찾을 수 없습니다…" 가 노출된다.
- **AC-3 — fallback 보존.** 정말로 매핑되지 않은 임의 코드는 호출부가 넘긴 fallback("저장하지 못했습니다.")을 그대로 반환한다.
- **AC-4 — 게이트.** console-web `pnpm lint` + `tsc --noEmit` + `vitest run` GREEN(신규 테스트 포함).

## Related Specs

- console-integration-contract § 2.4.10.4 — ecommerce 알림 템플릿 operator surface(producer 코드: 409 TEMPLATE_ALREADY_EXISTS, 404 TEMPLATE_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_ERROR).
- TASK-PC-FE-089 (ADR-031 Phase 5b) — 알림 facet 도입(이 버그의 출처).

## Related Contracts

- notification-service `GET/POST/PUT /api/notifications/templates` — FLAT 에러 봉투 `{ code, message, timestamp }`. console 은 `code` 를 `messageForCode` 로 매핑해 노출.

## Edge Cases

- 403 ACCESS_DENIED / 400 VALIDATION_ERROR 는 이미 `MESSAGES` 에 존재 → 영향 없음(회귀 없음).
- 503/timeout/network 는 `EcommerceUnavailableError → SERVICE_UNAVAILABLE` 로 여전히 fallback 노출(본 task 범위 아님; infra 신호는 별도 — 목록 GET 동작으로 배제됨).
- detail 페이지의 404 는 server-state(`getNotificationDetailSectionState`)에서 이미 notFound 분기로 처리됨; 본 수정은 **폼 mutation 제출** 경로의 메시지에 한정.

## Failure Scenarios

- 메시지 누락이 재발하지 않도록 회귀 테스트로 두 코드의 비-fallback 매핑을 고정. 새 producer 코드 추가 시 `MESSAGES` 갱신을 잊으면 동일 generic-fallback 함정 재현.
