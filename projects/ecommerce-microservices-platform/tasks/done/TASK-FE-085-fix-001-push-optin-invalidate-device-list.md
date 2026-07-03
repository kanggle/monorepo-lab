# TASK-FE-085-fix-001 — 구독/해지 후 "푸시 수신 기기" 목록이 즉시 갱신되지 않는 버그

- **Status**: done
- **DONE (2026-07-03, 3-dim verified — PR #2133 `26422a408`)**: state=MERGED + origin/main tip=`26422a408` 일치 + pre-merge failing=0(Frontend unit tests[Node20, 수정된 use-push-subscription QueryClient wrapper] GREEN + lint&build + E2E). 구독/해지 성공 시 `pushDevices` 쿼리 invalidate → 기기 목록 즉시 갱신(새로고침 불필요).
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)
- **IMPLEMENTED (2026-07-03)**: `use-push-subscription.ts` — `useQueryClient` 추가, 구독 성공(`setSubscribed(true)`)·해지 성공(`setSubscribed(false)`) 직후 `invalidateQueries({ queryKey: notificationKeys.pushDevices() })` 호출(성공 분기에서만; useCallback deps 에 queryClient 추가). `use-push-subscription.test.ts` — `renderHook` 을 QueryClientProvider wrapper(`createElement`, .ts 라 JSX 불가)로 감싸고 invalidateQueries spy 로 구독/해지 시 pushDevices invalidate·권한거부 시 미호출 검증. 라이브 fed-e2e 재현으로 발견(백엔드 GET 목록은 기기 정상 반환, 프런트 캐시만 미갱신 → 새로고침 필요했음). ⚠️로컬 vitest 불가(Node24↔vitest4)→CI Node20 권위.

## Goal

**TASK-FE-085** 라이브 검증(2026-07-03, fed-e2e)에서 발견된 버그: 알림 설정에서 "이 브라우저에서 푸시 받기"로 구독하면 백엔드 등록(POST 201)은 정상이고 DB·GET 목록 API 에도 기기가 존재하는데, 같은 화면의 **"푸시 수신 기기" 목록이 즉시 갱신되지 않아** 비어 보인다(새로고침해야 나타남).

원인: `PushOptIn`(구독/해지, `usePushSubscription`)과 `PushDeviceList`(`usePushDevices`, 쿼리 키 `notificationKeys.pushDevices()`)가 **서로 다른 React Query 캐시**를 쓰는데, 구독/해지 성공 시 목록 쿼리를 **invalidate 하지 않는다**. 그래서 목록은 페이지 진입 시 가져온 옛 결과(빈 배열)를 계속 표시한다. (백엔드 GET 목록은 해당 기기를 정확히 반환함 — 순수 프런트 캐시 동기화 갭.)

## Scope

**In scope** (web-store only):

1. `src/features/notification/model/use-push-subscription.ts` — `useQueryClient` 를 사용해 **구독 성공(register 후 `setSubscribed(true)`)과 해지 성공(delete + unsubscribe 후 `setSubscribed(false)`) 직후** `queryClient.invalidateQueries({ queryKey: notificationKeys.pushDevices() })` 호출 → `PushDeviceList` 가 자동 재조회되어 방금 등록/해지된 기기가 즉시 반영된다.
2. `src/__tests__/use-push-subscription.test.ts` — 훅이 `useQueryClient` 를 요구하므로 `renderHook` 을 `QueryClientProvider` wrapper 로 감싸고, 구독/해지 성공 시 `pushDevices` 쿼리가 invalidate 되는지 검증하는 케이스 추가.

**Out of scope**: 백엔드(FE-085 에서 완료, 정상), `PushDeviceList` 의 기기별 해지(이미 자체적으로 invalidate 함), PushOptIn↔목록의 역방향 동기화(목록에서 현재 기기 해지 시 PushOptIn 상태 갱신)는 별도.

## Acceptance Criteria

- **AC-1 — 구독 즉시 반영.** "이 브라우저에서 푸시 받기"로 구독 성공 시 새로고침 없이 "푸시 수신 기기" 목록에 해당 기기가 나타난다.
- **AC-2 — 해지 즉시 반영.** "이 브라우저 구독 해지" 성공 시 목록에서 해당 기기가 사라진다.
- **AC-3 — 회귀 없음.** 권한 미허용/미지원/에러 경로는 기존과 동일(그 경우 invalidate 호출 없음 또는 무해).
- **AC-4 — 게이트.** web-store 프런트 유닛(vitest) GREEN(기존 use-push-subscription 테스트 QueryClient wrapper 적용 + invalidate 검증 추가). ⚠️ 로컬 web-store vitest 불가(Node24↔vitest4) → CI Node20 권위.

## Related Specs

- TASK-FE-085 — 원 구현(기기 목록). 본 fix 가 구독/해지 시 목록을 실시간 동기화.

## Related Contracts

- 없음(프런트 캐시 동기화, API 무관).

## Edge Cases

- 구독 실패(권한 거부/미지원): `setSubscribed(true)` 에 도달하지 않으므로 invalidate 도 호출 안 됨(불필요한 재조회 없음).
- 해지 시 백엔드 204 후 브라우저 unsubscribe 실패해도 목록 invalidate 는 최종 상태 반영에 무해.

## Failure Scenarios

- invalidate 를 try 블록 밖(에러 경로 포함)에서 호출하면 실패한 구독에도 재조회가 발생 → 성공 분기(`setSubscribed` 직후)에서만 호출.
- 훅에 `useQueryClient` 추가 후 기존 테스트가 QueryClientProvider 부재로 throw → 테스트 wrapper 로 방지(AC-4).
