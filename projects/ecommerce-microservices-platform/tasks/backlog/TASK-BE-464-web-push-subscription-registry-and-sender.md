# TASK-BE-464 — notification-service Web Push(VAPID) 실연동: 구독 레지스트리 + 등록 API + WebPushSender

- **Status**: backlog
- **Project**: ecommerce-microservices-platform
- **Service**: notification-service
- **Analysis model**: Opus 4.8 / **Implementation model (권장)**: Opus (신규 계약 + 마이그레이션 + 외부 provider 어댑터 + 만료 구독 정리)
- **Depends on**: TASK-BE-463 (stub `PushNotificationSender` — 본 task 가 그 로그 라인을 실 VAPID 발송으로 대체하며 stub 을 제거/승격). BE-463 merge 후 착수.
- **Blocks**: TASK-FE-083 (web-store Service Worker/opt-in — 본 task 의 등록 엔드포인트 + VAPID 공개키가 전제).

## Goal

TASK-BE-463 이 연결한 PUSH 채널의 stub 로그 발송을, **실제 브라우저 Web Push(VAPID) 발송**으로 승격한다. 이메일과 달리 push 수신자는 주소가 아니라 **브라우저별 구독(subscription)** 이므로, `userId → push subscription` 을 저장·조회하는 레지스트리와 그 등록/해지 API 가 선행 필요하다. 발송 시 `WebPushSender` 가 userId 로 활성 구독을 조회하여 VAPID 서명된 요청을 각 구독 endpoint 로 보낸다.

FCM/APNs(모바일)는 범위 밖 — monorepo 에 소비할 모바일 앱이 없다(2026-07-02 user 결정). 단일 provider(Web Push)로 설계한다.

## Scope

**In scope** (notification-service backend + 계약):

1. **계약 (impl 전 선행)** — `specs/contracts/http/notification-api.md` 에 push 구독 관리 엔드포인트 추가:
   - `POST /api/users/me/push-subscriptions` (JWT self) — body = W3C PushSubscription JSON(`endpoint`, `keys.p256dh`, `keys.auth`). 멱등(동일 endpoint 재등록 시 갱신). 201/200.
   - `DELETE /api/users/me/push-subscriptions` (JWT self) — body 또는 쿼리로 `endpoint` 지정 해지.
   - (선택) `GET /api/notifications/vapid-public-key` 또는 프런트 빌드타임 env 로 공개키 노출 — 방식 택1을 계약에 명시.
2. **마이그레이션** — Flyway `V6__create_push_subscriptions.sql`: `push_subscriptions(id, tenant_id NOT NULL, user_id NOT NULL, endpoint NOT NULL, p256dh NOT NULL, auth NOT NULL, created_at, updated_at)`; unique(tenant_id, endpoint); index(tenant_id, user_id). tenant_id 는 다른 테이블과 동일하게 M1 정합.
3. **도메인** — `PushSubscription` 모델(+`domain/model`). 유효성(빈 endpoint/keys 거부).
4. **포트/유스케이스** — outbound `PushSubscriptionRepository`(save/findActiveByUserId/deleteByEndpoint) + inbound `ManagePushSubscriptionUseCase`(register/unregister). HTTP 어댑터는 `TenantContext` 로 tenant 스코프.
5. **어댑터(out/external)** — `WebPushSender implements NotificationSender`(supportedChannel=PUSH). `send(userId, subject, body)` → `findActiveByUserId(userId, tenant)` 로 구독 조회 → 각 구독에 VAPID 서명 발송(web-push 라이브러리). **만료 정리**: provider 응답 404/410 → 해당 구독 prune. 구독 0건 → no-op(정상). BE-463 의 stub `PushNotificationSender` 는 제거(동일 PUSH 키 충돌 방지 — merge 함수로 조용히 덮이는 Failure Scenario).
6. **의존성** — `build.gradle` 에 Java Web Push 라이브러리 추가(예: `nl.martijndwars:web-push` 또는 동급; 착수 시 유지보수성·BouncyCastle 전이의존 확인 후 확정).
7. **설정** — VAPID keypair 를 `app.notification.push.vapid.public-key` / `.private-key` / `.subject`(mailto:) 로 주입(env, secret). standalone/dev 프로파일 기본값·미설정 시 발송 skip+WARN(부팅 실패 금지, 이메일 dev SMTP 눈높이).

**Out of scope**: FCM/APNs 모바일; 프런트 Service Worker·opt-in UI(→ TASK-FE-083); push 발송 재시도 정책 고도화(4xx terminal / 5xx retry 는 기존 invariant 3 재사용); 구독 만료 배치 sweeper(무효 endpoint 는 발송 시 lazy prune 로 충분).

## Acceptance Criteria

- **AC-1 — 구독 등록/해지.** `POST /api/users/me/push-subscriptions` 가 인증 사용자 구독을 tenant 스코프로 저장(동일 endpoint 재등록=갱신, 중복행 없음); `DELETE` 가 해지. 계약 문서와 일치.
- **AC-2 — 실 발송.** PUSH 템플릿 + `push_enabled=true` + 활성 구독 보유 사용자에게, `WebPushSender` 가 렌더된 subject/body 를 VAPID 서명하여 각 구독 endpoint 로 POST 한다(단위: web-push 클라이언트 mock 으로 서명/호출 인자 검증).
- **AC-3 — 만료 구독 정리.** provider 404/410 응답 시 해당 구독을 저장소에서 제거하고 나머지 구독 발송은 계속한다.
- **AC-4 — 무구독/미설정 graceful.** 구독 0건이면 조용히 no-op; VAPID 키 미설정(dev)이면 발송 skip+WARN, 컨텍스트 부팅은 성공(net-zero, fail-closed 금지).
- **AC-5 — 테넌트 격리.** 구독 조회/저장이 `tenant_id` 스코프를 지킨다(타 테넌트 구독 미노출·교차 발송 없음).
- **AC-6 — stub 승격.** BE-463 stub `PushNotificationSender` 제거 후에도 senderMap[PUSH]=WebPushSender 단일 등록(키 충돌/덮임 없음).
- **AC-7 — 게이트.** notification-service `:test` GREEN(신규 단위). 구독 저장 파생쿼리·풀 wiring 은 Testcontainers IT 영역 → CI Linux 권위.

## Related Specs

- `specs/services/notification-service/architecture.md` § Internal Structure Rule(push sender), § Multi-Tenancy(M1 tenant_id).
- `specs/services/notification-service/overview.md` § Public surface(`/api/users/me/preferences` 패턴 = self JWT).
- TASK-BE-463 — stub sender(본 task 가 대체).

## Related Contracts

- `specs/contracts/http/notification-api.md` — **본 task 가 신규 엔드포인트 2~3개를 추가**(구독 등록/해지/공개키). contract-before-impl 규칙상 계약 갱신을 impl 커밋보다 먼저 랜딩.

## Edge Cases

- 동일 브라우저 재구독(endpoint 동일, keys 회전) → upsert 로 keys 갱신.
- 사용자가 여러 기기/브라우저 구독 → userId 당 N 구독, 전부에 발송(각 독립 prune).
- `push_enabled=false` → sendViaChannel 단계에서 스킵(sender 도달 전, 기존 opt-out 보존).
- VAPID 키 회전 → 기존 구독은 서명 검증 실패 가능; provider 4xx → prune 후 프런트 재구독 유도(FE-083 협조).

## Failure Scenarios

- 계약 없이 impl 선행 시 HARDSTOP-08(계약 누락) 위반 → notification-api.md 먼저.
- web-push 라이브러리 BouncyCastle 전이의존이 다른 서비스와 버전 충돌 시 부팅/서명 실패 → 착수 시 의존성 트리 확인, 필요 시 명시 버전 핀.
- VAPID private key 를 소스/이미지에 하드코딩하면 유출 → env/secret 주입만, 저장소 커밋 금지(shared-library-policy 및 보안 규칙).
- 만료 구독을 prune 하지 않으면 매 발송마다 410 누적 → provider rate-limit/노이즈. AC-3 로 lazy prune 강제.

## Promotion to ready (backlog → ready 조건)

1. TASK-BE-463 merge 완료(stub 존재 → 승격 대상 확정).
2. `notification-api.md` 구독 계약 초안 확정(엔드포인트 shape + 공개키 노출 방식 택1).
3. web-push 라이브러리 선정(유지보수성·전이의존 확인).
4. VAPID keypair 발급·주입 경로(env/secret) 확정.
