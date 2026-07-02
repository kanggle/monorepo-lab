# TASK-FE-085 — 알림 설정에 "푸시 수신 기기" 목록 표시 (목록 API + 기기명)

- **Status**: done
- **DONE (2026-07-02, 3-dim verified — PR #2119 `87ebff41a`)**: state=MERGED + origin/main tip=`87ebff41a` 일치 + pre-merge failing=0(Frontend lint&build+unit[신규 device-label/PushDeviceList]+E2E + notification-service Testcontainers Integration + ecommerce boot jars GREEN). 알림 설정 "푸시 수신 기기" 목록(GET 목록 API + user_agent V7 캡처 + 기기명 라벨 + 이 기기 배지 + 기기별 해지).
- **Project**: ecommerce-microservices-platform
- **Service**: notification-service (백엔드) + web-store (프런트)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (백엔드=backend-engineer 위임, 프런트=직접)
- **IMPLEMENTED (2026-07-02)**: 원자 1 PR. **백엔드**(notification-service): `V7__add_user_agent_to_push_subscriptions.sql`(nullable), 도메인/커맨드/엔티티/매퍼에 `userAgent`, 컨트롤러 register 가 `User-Agent` 헤더 캡처, 신규 `GET /me/push-subscriptions`(newest-first, 키 미노출) + `PushSubscriptionListResponse` + `listByUser` 유스케이스(`findByUserId` 재사용). 단위/슬라이스 테스트(GET 목록·키 미노출·UA 캡처·listByUser desc). **로컬 `:notification-service:compileJava/compileTestJava` + 타깃 단위/슬라이스 테스트 GREEN**(Testcontainers IT=CI 권위). **프런트**(web-store + 공유): `@repo/types`(PushSubscriptionDevice/ListPushSubscriptionsResponse), `@repo/api-client`(listPushSubscriptions), `lib/device-label`(UA→`OS · 브라우저`), `usePushDevices`(목록+현재 endpoint+해지 mutation), `PushDeviceList`(라벨·등록일·`이 기기` 배지·기기별 해지), NotificationSettings 통합. 테스트 2종(device-label 파서, PushDeviceList). ⚠️로컬 web-store vitest 불가→CI Node20 권위. 계약/architecture spec 선행 갱신.

## Goal

web-store 알림 설정 화면(`/my/notifications/settings`)에 현재 사용자가 **푸시를 받도록 등록한 기기(브라우저) 목록**을 보여준다. 지금은 "이 브라우저에서 푸시 받기" 토글(`PushOptIn`)만 있어 **다른 기기의 구독을 볼 수 없다**. 사용자는 여러 기기(회사 PC·집 PC·폰)에서 각각 구독할 수 있으므로(BE-464 스키마가 user당 다수 구독 허용), 등록된 기기를 나열하고 기기별로 해지할 수 있어야 한다.

기기 표시는 **기기명 수준**(선택됨): 등록 시 `User-Agent` 를 저장해 `Windows · Chrome` 형태 라벨로 표시하고, 현재 브라우저는 `이 기기` 배지로 구분한다.

## Scope

교차-레이어 기능(한 프로젝트 내 notification-service + web-store), **원자적 1 PR**.

**백엔드 (notification-service)**:

1. `V7__add_user_agent_to_push_subscriptions.sql` — `push_subscriptions` 에 `user_agent VARCHAR(512) NULL` 추가(기존 행은 NULL 허용, 후방호환).
2. 도메인 `PushSubscription` — `userAgent` 필드 추가(nullable). `register(...)` 시그니처에 `userAgent` 추가, `reconstitute(...)` 에 `userAgent` 추가, getter.
3. `RegisterPushSubscriptionCommand` — `userAgent` 필드 추가.
4. 컨트롤러 `register` — `@RequestHeader(value = "User-Agent", required = false) String userAgent` 를 커맨드로 전달.
5. `PushSubscriptionService.register` — 신규 생성 시 `userAgent` 저장(재등록=키 회전 경로는 UA 갱신 optional; 최소한 신규 insert 에 저장).
6. **목록 조회**: `ManagePushSubscriptionUseCase.list(String userId)` → `List<PushSubscription>`(또는 결과 레코드). `PushSubscriptionService.list` = `repository.findByUserId(userId)`(이미 존재하는 포트 재사용) createdAt desc 정렬.
7. 컨트롤러 `GET /api/notifications/me/push-subscriptions` — `@RequestHeader("X-User-Id") userId` → `200 { "subscriptions": [{ id, endpoint, userAgent, createdAt }] }`.
8. 응답 DTO `PushSubscriptionListResponse` (+ item). 엔티티/매퍼에 `userAgent` 반영.
9. 단위 테스트: service.list, controller-slice(GET 목록 + register UA 헤더 전달), 매퍼 userAgent 왕복.

**프런트 (web-store + 공유 패키지)**:

10. `@repo/types` — `PushSubscriptionDevice { id; endpoint; userAgent: string | null; createdAt }`, `ListPushSubscriptionsResponse { subscriptions: PushSubscriptionDevice[] }`. `index.ts` 배럴에 명시 export 추가.
11. `@repo/api-client` notification-api — `listPushSubscriptions()` → `GET /api/notifications/me/push-subscriptions`.
12. web-store — `usePushDevices`(목록 fetch), `lib/device-label.ts`(User-Agent → `OS · 브라우저` 파싱, 프레젠테이션), `PushDeviceList`(등록일 표시 + `이 기기` 배지[현재 브라우저 endpoint 매칭] + 기기별 `해지`[기존 deletePushSubscription(endpoint) 재사용]), `NotificationSettings` 에 통합.
13. 프런트 테스트: device-label 파서, PushDeviceList(목록 렌더 + 이 기기 배지 + 해지 호출).

**Out of scope**: 서버측 푸시 발송 로직, VAPID 설정, 알림 선호도 토글, `expirationTime` 영속화, 기기별 이름 커스텀 편집.

## Acceptance Criteria

- **AC-1 — 목록 API.** `GET /api/notifications/me/push-subscriptions` 가 인증 사용자의 구독을 `{ subscriptions: [...] }` 로 반환(각 항목: id, endpoint, userAgent, createdAt). 구독 없으면 빈 배열.
- **AC-2 — UA 저장.** 신규 구독 등록 시 요청의 `User-Agent` 가 `user_agent` 컬럼에 저장된다(헤더 없으면 NULL, 정상 처리).
- **AC-3 — 후방호환.** V7 는 nullable 컬럼 추가로 기존 행/발송 경로(`findByUserId`, `WebPushSender`)에 회귀 없음.
- **AC-4 — 리스트 UI.** 알림 설정에 기기 목록이 표시된다: 기기명(UA 파싱, 없으면 fallback 라벨) + 등록일 + `이 기기` 배지(현재 브라우저 구독 endpoint 일치 항목) + 기기별 `해지`.
- **AC-5 — 해지 반영.** 기기별 `해지` 는 기존 `DELETE /me/push-subscriptions`(endpoint 본문)로 처리되고, 목록에서 제거된다(재조회 또는 낙관적 갱신).
- **AC-6 — 미지원/빈 상태.** 구독이 없거나 푸시 미지원 브라우저에서 안전하게 빈/안내 상태로 degrade(에러 노출 아님).
- **AC-7 — 게이트.** 백엔드 `:notification-service` 컴파일 + 단위/Testcontainers IT GREEN(CI). 프런트 vitest GREEN(신규 device-label/PushDeviceList 테스트). ⚠️ 로컬 web-store vitest 불가(Node24↔vitest4)→CI Node20 권위.

## Related Specs

- `specs/contracts/http/notification-api.md` — **선행 갱신**: `GET /me/push-subscriptions`(목록) 추가 + register 의 `User-Agent` 캡처/`userAgent` 필드 문서화.
- `specs/services/notification-service/architecture.md` — PushSubscription 에 userAgent 속성 + list 유스케이스 반영.
- TASK-BE-464(백엔드 구독) / TASK-FE-083(브라우저 구독 UI) — 본 기능이 확장.

## Related Contracts

- `specs/contracts/http/notification-api.md` — 신규 GET 목록 엔드포인트 + `userAgent` 응답 필드. 구현 전 계약 갱신(원자 PR 내 계약→구현 순서).

## Edge Cases

- User-Agent 헤더 부재(비브라우저/프록시 변형): `userAgent = NULL`, 라벨은 fallback("알 수 없는 기기").
- 재등록(키 회전, 동일 endpoint): 행 1개 유지(중복 없음), UA 갱신은 optional(최소 insert 시 저장).
- 현재 브라우저가 미구독: 목록에 `이 기기` 배지 없음(정상).
- 목록 endpoint 노출: 사용자 본인 데이터라 허용(이 기기 매칭용). 키(p256dh/auth)는 **응답에 미포함**.
- 매우 긴 UA: 512 컬럼 초과분은 저장 시 truncate(또는 컬럼 길이 내 저장), 라벨 파싱은 앞부분으로 충분.

## Failure Scenarios

- V7 을 NOT NULL 로 추가하면 기존 행 마이그레이션 실패 → **nullable** 로 추가(AC-3).
- 목록 응답에 p256dh/auth 포함 시 불필요한 키 노출 → DTO 에서 endpoint/UA/createdAt 만 노출.
- 프런트가 UA 파싱 실패 시 throw → 파서는 항상 문자열 반환(실패=fallback 라벨)로 방어.
- `이 기기` 매칭을 endpoint 정확 비교가 아닌 부분 비교로 하면 오탐 → `getSubscription().endpoint` 완전 일치로 판정.
