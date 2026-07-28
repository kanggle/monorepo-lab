# TASK-FAN-BE-033-fix-02: `portone` 프로파일 기동 시 PaymentGatewayPort/RecurringBillingGateway 빈 모호성으로 컨텍스트 기동 실패

## Goal

ADR-001 라이브 결제 검증(실 PortOne 테스트 키로 membership-service를 `portone`
프로파일로 기동) 중 발견된 컨텍스트 기동 결함을 수정한다.

`PaymentGatewayConfig`의 두 `@Bean`(`portOnePaymentGateway`: `PaymentGatewayPort`,
`portOneRecurringBillingGateway`: `RecurringBillingGateway`)이 **같은
`PortOnePaymentAdapter` 인스턴스**를 두 포트로 노출한다. 이 어댑터가 두 인터페이스를
모두 구현하므로, Spring의 constructor-injection eager 타입매칭이 각 빈의 실제
인스턴스를 검사해 **양쪽 다 두 포트 모두에 대해 candidate로 판정** — 어느 한쪽
포트를 단독으로 요구하는 모든 미주입(unqualified) 지점이
`NoUniqueBeanDefinitionException`으로 `portone` 프로파일에서만 깨진다(`!portone`
mock 프로파일은 `MockPaymentGatewayAdapter`/`MockRecurringBillingGateway`가
단일 인터페이스 클래스라 문제없음). 어떤 테스트도 `portone` 프로파일로 풀
컨텍스트를 기동하지 않아 CI에서 잡히지 않았다 — 이번 라이브 검증으로 처음 발견.

## Scope

### Backend (membership-service)

두 포트 각각에 안정적인 `@Qualifier`(`"paymentGateway"` / `"recurringBillingGateway"`)를
프로파일-무관하게(mock/portone 양쪽 빈에) 동일하게 부여해 타입 모호성을 이름으로
명확히 해소한다:

1. `PaymentGatewayConfig` — 두 `@Bean` 메서드에 `@Qualifier` 추가, `portOneRecurringBillingGateway`가 주입받는 `PaymentGatewayPort` 파라미터에도 `@Qualifier("paymentGateway")` 추가.
2. `MockPaymentGatewayAdapter` — 클래스에 `@Qualifier("paymentGateway")`.
3. `MockRecurringBillingGateway` — 클래스에 `@Qualifier("recurringBillingGateway")`.
4. `SubscribeUseCase`, `RenewMembershipUseCase`, `WebhookReconcileUseCase` — `PaymentGatewayPort` 필드에 `@Qualifier("paymentGateway")`(Lombok `@RequiredArgsConstructor`가 필드 애노테이션을 생성자 파라미터로 복사).
5. `AutoRenewMembershipsUseCase` — 두 생성자 파라미터에 각각 `@Qualifier` 추가.

도메인/유스케이스 로직은 무변경 — 순수 DI 배선(애노테이션 추가)만.

## Acceptance Criteria

- [ ] `SPRING_PROFILES_ACTIVE=portone` + 실 `FAN_PAYMENT_PORTONE_API_SECRET`로
      membership-service가 `NoUniqueBeanDefinitionException` 없이 기동한다(라이브
      검증으로 직접 확인).
- [ ] `./gradlew :projects:fan-platform:apps:membership-service:test`(mock 프로파일
      Docker-free 유닛/슬라이스) 기존 동작 그대로 통과.
- [ ] mock 프로파일 동작 무변경 — `@Qualifier`는 순수 추가 애노테이션이며 기존
      비한정 주입 지점의 단일-후보 해석에 영향 없음.
