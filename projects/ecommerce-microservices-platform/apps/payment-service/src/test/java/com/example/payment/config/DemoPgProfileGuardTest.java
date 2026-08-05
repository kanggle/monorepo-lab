package com.example.payment.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code demo-pg} × {@code prod} mutual exclusion (TASK-BE-572 AC-2).
 *
 * <p>Exercises the REAL {@link DemoPgProfileGuard} and the real
 * {@link DemoPaymentGatewayConfig} / {@link PaymentGatewayConfig} profile expressions — Spring
 * evaluates them here exactly as it would at boot. A test that re-stated the expressions as
 * strings would only be testing its own copy of them.
 */
@DisplayName("demo-pg × prod 상호배타 가드 (TASK-BE-572)")
class DemoPgProfileGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DemoPgProfileGuard.class, DemoPaymentGatewayConfig.class);

    @Test
    @DisplayName("prod + demo-pg 동시 활성 → 컨텍스트 기동 실패")
    void bothProfiles_failsToStart() {
        runner.withPropertyValues("spring.profiles.active=prod,demo-pg")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessageContaining("must never run in a production deployment"));
    }

    @Test
    @DisplayName("선언 순서가 반대여도 동일하게 실패한다 (프로파일 집합은 순서가 없다)")
    void bothProfiles_reversedOrder_alsoFails() {
        runner.withPropertyValues("spring.profiles.active=demo-pg,prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("demo-pg 단독 → 기동 성공 + mock 게이트웨이 등록")
    void demoPgAlone_startsAndRegistersTheMock() {
        runner.withPropertyValues("spring.profiles.active=demo-pg")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(DemoPaymentGatewayConfig.DemoPaymentGateway.class));
    }

    /**
     * The half that stops the guard from being over-broad: {@code prod} on its own — what every
     * real deployment declares — must be unaffected, and must NOT get the mock.
     */
    @Test
    @DisplayName("prod 단독 → 기동 성공 + mock 게이트웨이 없음")
    void prodAlone_startsWithoutTheMock() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(DemoPaymentGatewayConfig.DemoPaymentGateway.class));
    }

    @Test
    @DisplayName("프로파일 미지정 → mock 없음 (기본값은 실 PG 다 — AC-3)")
    void noProfile_hasNoMock() {
        runner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(DemoPaymentGatewayConfig.DemoPaymentGateway.class));
    }
}
