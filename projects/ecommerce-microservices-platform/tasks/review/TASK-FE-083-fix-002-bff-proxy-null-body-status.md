# TASK-FE-083-fix-002 — web-store BFF 프록시가 204/205/304 응답에 본문을 실어 `TypeError` → 500 을 반환하는 버그

- **Status**: review
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)
- **IMPLEMENTED (2026-07-02)**: `src/app/api/bff/[...path]/route.ts` — 응답 재구성 시 status 가 null-body-status(`204`/`205`/`304`)면 본문을 `null` 로 전달(그 외엔 기존대로 arrayBuffer). `src/__tests__/bff-proxy.test.ts` — 백엔드 204→예외 없이 204 통과(본문 없음) / 304 통과 / 200 본문·status 회귀 3케이스 추가. 라이브 fed-e2e push 해지 500 재현으로 발견(dev 로그 `TypeError: Response constructor: Invalid response status code 204`, DB 행은 정상 삭제됨=백엔드 204). ⚠️로컬 vitest 불가(Node24↔vitest4)→CI Node20 권위.

## Goal

**TASK-FE-083** 라이브 검증(2026-07-02, fed-e2e 스택) 중 발견된 버그: 브라우저 푸시 **구독 해지**(`DELETE /api/notifications/me/push-subscriptions`)를 누르면 UI 에 "푸시 알림 구독 해지에 실패했습니다" 가 뜬다. 그러나 백엔드는 정상적으로 구독을 삭제하고 `204 No Content` 를 반환했다(DB `push_subscriptions` 행 삭제 확인, notification-service 로그에 에러 없음).

실패의 근본 원인은 **web-store 의 same-origin BFF 프록시**(`src/app/api/bff/[...path]/route.ts`)다. 프록시는 백엔드 응답을 다음과 같이 무조건 되돌린다:

```ts
const resBody = await backendRes.arrayBuffer();          // 204 여도 빈(그러나 non-null) ArrayBuffer
return new NextResponse(resBody, { status: backendRes.status, ... });
```

Fetch 표준의 **null body status**(`204`, `205`, `304`)에는 본문을 실을 수 없어, 이 생성자가 `TypeError: Response constructor: Invalid response status code 204` 를 던진다. Next.js 라우트 핸들러가 이 예외를 잡아 **500** 을 반환하고, 프런트 axios 는 이를 실패로 오인한다.

라이브 재현(dev 로그): `DELETE /api/bff/api/notifications/me/push-subscriptions 500` + 스택 `TypeError: Response constructor: Invalid response status code 204`. DB 행은 이미 삭제됨(백엔드 정상).

**영향 범위는 push 해지에 국한되지 않는다** — BFF 프록시를 통과하는 **모든 204/205/304 응답**이 500 으로 변질된다. 지금까지 클라이언트에서 프록시로 호출한 엔드포인트 중 null-body-status 를 반환하는 것이 이 DELETE 가 처음이라 이제서야 드러난 잠복 버그다. FE-083 유닛/컴포넌트 테스트는 실제 프록시 라우트(204)를 경유하지 않아 미적발.

## Scope

**In scope** (web-store only):

1. `src/app/api/bff/[...path]/route.ts` — 응답 재구성 시 status 가 null-body-status(`204`/`205`/`304`)면 본문을 `null` 로 전달하여 `NextResponse`/`Response` 생성자 예외를 회피. 그 외 status 는 기존대로 arrayBuffer 본문 전달.
2. `src/__tests__/bff-proxy.test.ts` (기존) — 백엔드 204(및 304) 응답을 프록시가 **본문 없이 동일 status 로** 되돌리고 예외를 던지지 않음을 단언하는 케이스 추가.

**Out of scope**: 푸시 구독/해지 도메인 로직(FE-083/BE-464 에서 완료), `use-push-subscription` 의 해지 후 상태 처리(백엔드가 204 를 정상 반환하고 프록시가 이를 통과시키면 기존 로직대로 `subscription.unsubscribe()` → `setSubscribed(false)` 가 동작), 다른 프록시 헤더 정책.

## Acceptance Criteria

- **AC-1 — 204 통과.** 백엔드가 `204 No Content` 를 반환하면 BFF 프록시는 예외 없이 `204`(본문 없음)를 클라이언트로 되돌린다. push 구독 해지가 UI 에서 성공으로 처리된다.
- **AC-2 — 205/304 통과.** `205 Reset Content`, `304 Not Modified` 도 동일하게 본문 없이 통과(회귀 방지 일반화).
- **AC-3 — 본문 응답 회귀 없음.** 200/201 등 본문이 있는 응답은 기존대로 본문·status·헤더가 그대로 전달된다.
- **AC-4 — 게이트.** web-store 프런트 유닛(vitest) GREEN(신규 프록시 204/304 테스트 포함). ⚠️ 로컬 vitest 불가(Node24↔vitest4) → CI Node20 권위.

## Related Specs

- TASK-FE-083 — 원 구현(브라우저 푸시 구독/해지 UI). 본 fix 가 해지 응답(204)을 프런트에서 정상 처리하게 만든다.
- `specs/services/web-store/architecture.md` § Phase 4.5 (BFF 토큰 프록시) — 프록시가 백엔드 응답을 그대로 중계한다는 서술과 정합(null-body-status 예외만 교정).

## Related Contracts

- 없음(프런트 프록시 응답 중계 교정, API 계약 무관 — `notification-api.md` 의 DELETE=204 계약은 변경 없이 그대로 준수됨).

## Edge Cases

- 빈 본문의 200(예: 일부 mutation 이 200 + 빈 바디): non-null-body-status 이므로 빈 arrayBuffer 를 실어도 생성자 예외 없음 → 기존 경로 유지(회귀 없음).
- `Content-Length`/`Transfer-Encoding` 은 이미 `STRIPPED_RESPONSE_HEADERS` 로 제거되므로 본문 제거와 헤더 정합성 문제 없음.
- 304 조건부 요청: 본문 없이 통과해야 브라우저 캐시 재검증이 정상 동작.

## Failure Scenarios

- null-body-status 판정 누락(예: 205/304 미포함)으로 해당 status 만 여전히 500 → status 집합을 `204/205/304` 로 명시하고 테스트로 205/304 도 단언하여 방지.
- 200 을 null-body 로 오분류하면 정상 본문이 유실됨 → 판정을 정확히 `204/205/304` 로 한정하고 AC-3(본문 회귀) 테스트로 방지.
