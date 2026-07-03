# TASK-FE-088 — 알림 설정 푸시 영역: 버튼 실선 제거 + 기기 해지 ↔ 옵트인 버튼 동기화

- **Status**: review
- **Type**: frontend / bugfix (web-store)
- **Domain**: ecommerce
- **Depends on**: TASK-FE-085 (device list), TASK-FE-086 (push area grouping), TASK-FE-087 (button-only opt-in)

## Goal

알림 설정의 푸시 영역에서 두 가지 UX 문제를 해결한다.

1. "이 브라우저에서 푸시 받기" 버튼 **위·아래에 걸친 실선(divider)** 을 제거해 버튼이 푸시 영역 안에서 실선 없이 배치되도록 한다.
2. "푸시 수신 기기" 목록에서 현재 브라우저 기기의 **"해지"** 를 누르면 옵트인 버튼이 "이 브라우저 구독 해지" → "이 브라우저에서 푸시 받기" 로 **즉시 동기화** 되도록 한다.

## Scope

- `apps/web-store/src/features/notification/ui/SettingToggle.tsx` — 옵션 `divider` prop(기본 true) 추가.
- `apps/web-store/src/features/notification/ui/NotificationSettings.tsx` — 푸시 토글에 `divider={!pushEnabled}` (푸시 영역이 열리면 토글 하단 실선 제거).
- `apps/web-store/src/features/notification/ui/PushDeviceList.tsx` — section `borderTop`/`paddingTop` 제거(버튼 아래 실선 제거).
- `apps/web-store/src/features/notification/model/query-keys.ts` — `pushSubscription()` 키 추가.
- `apps/web-store/src/features/notification/model/use-push-subscription.ts` — `subscribed` 를 로컬 state 대신 공유 React Query 로 전환, subscribe/unsubscribe 는 `setQueryData` 로 낙관적 갱신.
- `apps/web-store/src/features/notification/model/use-push-devices.ts` — 현재 브라우저 기기 해지 시 브라우저 구독까지 해제하고 `pushSubscription` 쿼리를 무효화(옵트인 버튼 동기화).

## Acceptance Criteria

- [ ] 푸시 활성화 시 옵트인 버튼 바로 위(푸시 토글 하단)와 바로 아래(기기 목록 상단)에 실선이 보이지 않는다. 이메일/SMS 토글의 divider 는 그대로 유지된다.
- [ ] 기기 목록에서 **현재 브라우저** 기기의 "해지" 클릭 시: (a) 서버 레코드 삭제, (b) 브라우저 푸시 구독 해제, (c) 옵트인 버튼이 "이 브라우저에서 푸시 받기" 로 즉시 전환, (d) 기기 목록에서 사라짐.
- [ ] 다른 기기의 "해지" 는 현재 브라우저 구독/옵트인 버튼에 영향을 주지 않는다.
- [ ] 옵트인 버튼으로 구독/해지 시 기존 동작(백엔드 등록/삭제 + 기기 목록 갱신)이 회귀 없이 유지된다.
- [ ] `pnpm --filter web-store test` (notification 스위트) GREEN, `pnpm lint`/tsc GREEN.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/features/notification/` (푸시 알림 옵트인/기기 관리)

## Related Contracts

- 변경 없음 (기존 `GET /me/push-subscriptions`, `DELETE` 재사용).

## Edge Cases

- 현재 브라우저의 실 구독 endpoint 가 목록의 삭제 대상과 일치하는지 판별은 캐시된 `currentEndpoint` 가 아니라 삭제 시점의 실제 `pushManager.getSubscription()` 로 재확인(구독 직후 stale state 방지).
- 푸시 미지원/권한 차단 브라우저에서 옵트인 버튼은 여전히 렌더되지 않고 안내만 표시.
- 구독 없는 상태에서 다른 기기 해지 시 브라우저 unsubscribe 를 시도하지 않는다.

## Failure Scenarios

- 브라우저 `unsubscribe()` 실패 시에도 서버 삭제·목록 갱신은 진행하고 조용히 무시(로그인 세션 깨지 않음).
- `pushSubscription` 쿼리 재조회가 실패해도 옵트인 버튼은 마지막 알려진 상태를 유지.
