# TASK-FE-083 — web-store Web Push 구독: Service Worker + opt-in UI + 배너 표시

- **Status**: backlog
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model (권장)**: Sonnet 또는 Opus (Service Worker + PushManager 브라우저 통합 + 구독 등록 연동)
- **Depends on**: TASK-BE-464 (구독 등록 API `POST/DELETE /api/notifications/me/push-subscriptions` + VAPID 공개키 노출). BE-464 merge 후 착수.

## Goal

브라우저 storefront(`web-store`)에서 사용자가 알림 수신을 opt-in 하면, TASK-BE-464 의 백엔드에 Web Push 구독을 등록하고, 백엔드가 발송한 push 를 **브라우저 데스크톱 알림 배너**로 표시한다. 이로써 PUSH 채널이 프런트~백엔드 end-to-end 로 실동작한다(데모 증명 가능).

## Scope

**In scope** (web-store frontend only):

1. **Service Worker** — push 수신용 SW 등록(`public/sw.js` 또는 next-pwa 등 방식 택1). `push` 이벤트 → `registration.showNotification(title, { body, icon, data })`. `notificationclick` → 관련 주문/페이지로 focus/open.
2. **구독 opt-in 플로우** — 사용자 액션(설정/알림 토글)에서 `Notification.requestPermission()` → 허용 시 `registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: <VAPID public key> })` → 결과 PushSubscription 을 `POST /api/notifications/me/push-subscriptions` 로 전송. opt-out 시 `subscription.unsubscribe()` + `DELETE`.
3. **VAPID 공개키 주입** — BE-464 계약이 **엔드포인트 방식**을 채택했으므로 `GET /api/notifications/vapid-public-key` 조회로 공개키를 획득(빌드타임 `NEXT_PUBLIC_VAPID_PUBLIC_KEY` 는 미채택). base64url → Uint8Array 변환 유틸(`applicationServerKey` 용).
4. **UI** — 알림 수신 토글(권한 상태 3-way: default/granted/denied 반영), denied 시 브라우저 설정 안내 카피. web-store 기존 사용자 설정/마이페이지 위치에 배치.
5. **테스트** — 구독 등록/해지 훅 단위(fetch mock), base64url 변환 유틸, 권한 상태별 토글 렌더(vitest).

**Out of scope**: 백엔드 구독 저장·발송(→ TASK-BE-464); admin-dashboard(운영자용 push 불필요); iOS Safari Web Push 세부 대응(홈스크린 PWA 요구 등 — 데스크톱 Chrome/Firefox/Edge 우선); 마케팅 대량 push.

## Acceptance Criteria

- **AC-1 — SW 등록.** web-store 로드 시(또는 opt-in 시) Service Worker 가 등록되고 push 이벤트 핸들러가 배너를 표시한다.
- **AC-2 — 구독 등록.** opt-in 시 권한 요청 → `pushManager.subscribe(VAPID)` → `POST /api/notifications/me/push-subscriptions` 로 endpoint+keys 전송. 성공 시 토글이 granted 상태로 반영.
- **AC-3 — 해지.** opt-out 시 `unsubscribe()` + `DELETE` 로 백엔드 구독 제거.
- **AC-4 — 권한 상태 UX.** default/granted/denied 3-way 를 UI 가 정확히 반영하고, denied 는 재요청 대신 브라우저 설정 안내를 노출(브라우저는 denied 후 프로그램적 재프롬프트 불가).
- **AC-5 — end-to-end(데모/라이브 증명).** granted 사용자에게 백엔드 PUSH 발송(BE-464) → 브라우저 배너 표시. 라이브 검증은 fed-e2e 스택 기동 하 수동/스모크(자동화는 브라우저 push 시뮬 한계로 best-effort).
- **AC-6 — 게이트.** web-store vitest GREEN(신규). ⚠️ 로컬 Windows Node24 ↔ vitest4 비호환 가능(메모리 `env_webstore_vitest4_node24_module_evaluator`) → tsc/lint 로컬 + vitest 는 CI Node20 권위.

## Related Specs

- `specs/services/web-store/architecture.md` § Rendering/Client Strategy — SW/PWA 진입 시 갱신 필요(본 task 에서 push SW 섹션 추가).
- TASK-BE-464 — 백엔드 구독 API + VAPID 공개키(전제).

## Related Contracts

- `specs/contracts/http/notification-api.md` — BE-464 가 추가하는 `POST/DELETE /api/notifications/me/push-subscriptions`(+공개키) 소비. 프런트는 계약 소비자(신규 계약 필드 추가 없음).

## Edge Cases

- 브라우저 미지원(구형/일부 iOS) → 기능 감지(`'PushManager' in window`) 후 토글 비활성 + 미지원 안내.
- 권한 denied 상태 → 재프롬프트 불가, 설정 안내만.
- SW 갱신/캐시 → SW 버전 변경 시 재등록·기존 구독 재사용(endpoint 유지 시 백엔드 upsert).
- 로그아웃 → 구독 유지 여부 정책(기기 공용 시 해지 권장) 결정 반영.

## Failure Scenarios

- HTTPS 전제: Web Push/SW 는 secure context 필요(localhost 예외). 배포 환경 http 이면 미동작 → 문서화·환경 전제 확인.
- VAPID 공개키 불일치(프런트 env vs 백엔드 서명키) → 구독은 생성되나 발송 서명 검증 실패/prune. BE-464 와 키 소스 단일화.
- `applicationServerKey` base64url 변환 오류 → subscribe 실패(InvalidAccessError). 변환 유틸 단위 테스트로 방지.

## Promotion to ready (backlog → ready 조건)

1. TASK-BE-464 merge 완료(등록 API + 공개키 노출 방식 확정).
2. VAPID 공개키 프런트 주입 경로(env vs 조회) BE-464 와 합의.
3. web-store 알림 설정 UI 배치 위치 확정.
