# TASK-BE-463 — notification-service PUSH 채널이 sender 부재로 조용히 skip되는 갭 해소 (stub/log sender)

- **Status**: review
- **Project**: ecommerce-microservices-platform
- **Service**: notification-service
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (소규모 outbound 어댑터 추가)

## Goal

`NotificationChannel.PUSH` 는 도메인·템플릿·사용자 선호(`push_enabled`)에 1급 채널로 존재하지만, 이를 실제로 발송하는 `NotificationSender` 구현체가 없어서 발송 경로가 조용히 끊긴다. 현재 `adapter/out/external` 에는 `EmailNotificationSender`(EMAIL) 하나뿐이라, `NotificationSendService.sendViaChannel()` 가 PUSH 채널 처리 시 `senderMap.get(PUSH) == null` 분기로 `"No sender available for channel PUSH"` 로그만 남기고 return 한다 — PUSH 템플릿이 있고 사용자가 push를 켜도 아무 일도 일어나지 않는다.

본 task 는 **stub/log 수준의 `PushNotificationSender`** 를 추가해 PUSH 채널을 template 렌더 → 발송 경로에 end-to-end 로 연결한다. 실제 provider(FCM/APNs/Web Push) 연동과 그 전제인 디바이스-토큰/구독 레지스트리는 **의도적으로 out of scope**(후속 task) — 이는 EMAIL sender 가 dev용 `JavaMailSender` 인 것과 동일한 눈높이의 뼈대 구현이다.

## Scope

**In scope** (notification-service only):

1. `src/main/java/.../adapter/out/external/PushNotificationSender.java` (신규) — `NotificationSender` 구현. `@Component` 로 등록되면 `NotificationSendService` 생성자의 `List<NotificationSender>` 에 자동 합류하여 `senderMap[PUSH]` 슬롯을 채운다(발송 서비스 코드 변경 불필요). `send()` 는 실제 provider 호출 대신 렌더된 subject/body 를 INFO 로그로 기록. `supportedChannel()` = `NotificationChannel.PUSH`.
2. `src/test/java/.../adapter/out/external/PushNotificationSenderUnitTest.java` (신규) — `EmailNotificationSenderUnitTest` 미러. supportedChannel 반환값 + send() 가 예외 없이 INFO 로그를 남기는지(Logback `ListAppender` 캡처) 검증.

**Out of scope**:

- 실제 FCM/APNs/Web Push provider SDK 연동.
- `userId → device token / web-push subscription` 레지스트리(테이블·등록 API·조회 포트). PUSH 의 `recipient` 인자는 현재 계약(`send(userId, subject, body)`)상 userId 그대로 전달되며, 실 provider 연동 시 이 매핑 계층이 선행 필요.
- SMS 채널(`SmsNotificationSender`) — 별개 갭, 별도 task.
- 계약/스펙 변경 없음 — PUSH 는 이미 아키텍처 spec(`architecture.md` § Internal Structure Rule "push sender", NotificationChannel VO)과 overview 에 선언돼 있어, 본 구현은 선언된 확장 슬롯을 채우는 것이지 새 아키텍처 결정이 아니다.

## Acceptance Criteria

- **AC-1 — 채널 지원 선언.** `PushNotificationSender.supportedChannel()` 이 `NotificationChannel.PUSH` 를 반환한다.
- **AC-2 — 발송 경로 연결.** 스프링 컨텍스트에서 `PushNotificationSender` 가 `@Component` 로 등록되어 `NotificationSendService` 의 `senderMap` 에 PUSH 키로 잡힌다 → PUSH 템플릿이 존재하고 사용자 `push_enabled=true` 이면 `renderAndSend` 가 호출된다("No sender available for channel PUSH" 분기 미도달).
- **AC-3 — stub 발송 관측 가능.** `send(recipient, subject, body)` 가 예외 없이 완료되고, 수신자·제목을 포함한 INFO 로그 1건을 남긴다(운영 관측·후속 provider 교체 지점).
- **AC-4 — 게이트.** notification-service `:test` GREEN(신규 단위 테스트 포함). 풀 컨텍스트 wiring 검증은 Testcontainers IT 영역 → CI Linux 권위(로컬 Windows 는 Testcontainers 차단).

## Related Specs

- `specs/services/notification-service/architecture.md` § Internal Structure Rule — `adapter/out/external (email sender, SMS sender, push sender)` 및 Value Object `NotificationChannel (EMAIL, SMS, PUSH)`, outbound port `NotificationSender`.
- `specs/services/notification-service/overview.md` § Responsibilities — 다채널(email/SMS/push) 발송.
- TASK-BE-078 — notification-service 최초 구현(EMAIL sender 기준 패턴).

## Related Contracts

- 없음(계약 변경 없음). PUSH 는 발송 채널 열거값으로 이미 존재하며 HTTP/이벤트 계약에 새 필드가 추가되지 않는다.

## Edge Cases

- PUSH 템플릿 미존재 → `templateRepository.findByTypeAndChannel(type, PUSH, tenant)` 가 empty → `renderAndSend` 미호출(기존 동작 보존, sender 는 무관).
- 사용자 `push_enabled=false` → `preference.isChannelEnabled(PUSH)` false 로 채널 스킵(sender 도달 전, opt-out 규칙 보존).
- `recipient`(userId)/subject/body 가 빈 문자열 → provider 호출이 없으므로 stub 은 그대로 로그만 남기고 성공 처리(실 provider 연동 task 에서 유효성·토큰 부재 처리 추가).

## Failure Scenarios

- 만약 `supportedChannel()` 을 EMAIL 로 잘못 반환하면 `senderMap` 에서 EmailNotificationSender 와 키 충돌 → 생성자 merge 함수(`(existing, replacement) -> replacement`)로 둘 중 하나가 조용히 덮인다 → PUSH 미연결 + EMAIL 교체 위험. 단위 테스트 AC-1 로 반환값을 고정 검증하여 방지.
- stub `send()` 가 예외를 던지면 `renderAndSend` 의 catch 가 notification 을 FAILED 로 마킹 → 무해하나 관측 노이즈. stub 은 예외를 던지지 않도록 순수 로그 기록만 수행.
