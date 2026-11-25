package com.example.account.infrastructure.metrics;

import com.example.account.application.port.SignupMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * TASK-BE-668 — Micrometer implementation of {@link SignupMetricsPort}
 * (account-service observability.md § 필수 메트릭 :13).
 *
 * <p>All three {@code result} series are registered up front, so the «Signup Rate» panel and the
 * {@code AccountSignupFailureHigh} ratio read a real {@code 0} from start-up, not no-data.
 */
@Component
public class MicrometerSignupMetrics implements SignupMetricsPort {

    /** observability.md:13 — counter, tag {@code result} (success/duplicate/validation_error). */
    static final String SIGNUP_METRIC_NAME = "account_signup_total";

    private final Map<SignupOutcome, Counter> counters = new EnumMap<>(SignupOutcome.class);

    public MicrometerSignupMetrics(MeterRegistry meterRegistry) {
        for (SignupOutcome outcome : SignupOutcome.values()) {
            counters.put(outcome, Counter.builder(SIGNUP_METRIC_NAME)
                    .description("Public signup attempts, one per attempt, by result")
                    .tag("result", outcome.tagValue())
                    .register(meterRegistry));
        }
    }

    @Override
    public void signupAttempt(SignupOutcome outcome) {
        counters.get(outcome).increment();
    }
}
