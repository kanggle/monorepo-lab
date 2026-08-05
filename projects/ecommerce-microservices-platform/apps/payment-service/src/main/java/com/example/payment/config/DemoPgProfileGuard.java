package com.example.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start when the demo mock PG and {@code prod} are active together (TASK-BE-572 AC-2).
 *
 * <p>{@code demo-pg} makes every payment succeed without money moving. The one thing that must
 * never happen is that profile reaching a real deployment, where it would turn the storefront into
 * a shop that gives goods away. There is no clever detection for that — the only durable signal is
 * the profile a deployment declares, and every real ecommerce deployment declares {@code prod}
 * (all twelve services set {@code SPRING_PROFILES_ACTIVE=prod} in
 * {@code projects/ecommerce-microservices-platform/docker-compose.yml}). So the rule is: those two
 * together is a misconfiguration, and a misconfiguration about money fails <em>loudly at boot</em>
 * rather than quietly at the first checkout.
 *
 * <h2>This costs the demo nothing — measured, not assumed</h2>
 *
 * A guard that forces the demo to drop {@code prod} would be a bad trade if {@code prod} carried
 * configuration. It carries none. Swept at TASK-BE-572 AC-0 across the whole project and the
 * shared libs: <strong>zero</strong> {@code @Profile("prod")} annotations, <strong>zero</strong>
 * {@code application-prod.yml} files, <strong>zero</strong> {@code spring.config.activate.on-profile}
 * blocks naming it. Every profile gate in ecommerce is written against {@code standalone}.
 * {@code prod} is a pure marker in the compose file — which is precisely what makes it a good
 * thing to key this guard on, and why dropping it in the demo loses nothing.
 *
 * <p>If {@code prod} ever gains real configuration, this guard becomes a genuine trade-off and the
 * decision has to be revisited — not worked around by deleting the guard.
 *
 * <h2>Why a failing bean rather than a validator</h2>
 *
 * A {@code @Profile("demo-pg & prod")} configuration that throws in its constructor is evaluated
 * by Spring itself, so it cannot be bypassed by a property, an ordering change, or a listener that
 * someone forgets to register. The profile expression is the assertion.
 */
@Configuration
@Profile("demo-pg & prod")
public class DemoPgProfileGuard {

    public static final String MESSAGE =
            "Profiles 'demo-pg' and 'prod' are both active. 'demo-pg' approves every payment "
                    + "WITHOUT taking money and must never run in a production deployment. "
                    + "Activate exactly one of them (the integrated demo runs 'demo-pg' alone — "
                    + "the 'prod' profile carries no configuration in this project).";

    public DemoPgProfileGuard() {
        throw new IllegalStateException(MESSAGE);
    }
}
