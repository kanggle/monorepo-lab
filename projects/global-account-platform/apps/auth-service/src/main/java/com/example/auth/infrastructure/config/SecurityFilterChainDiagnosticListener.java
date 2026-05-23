package com.example.auth.infrastructure.config;

import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TASK-BE-311 — diagnostic instrumentation. Logs every {@link SecurityFilterChain}
 * bean's identity + matcher + ordered filter list at startup. Lets the next
 * dispatch's compose-log dump confirm which hypothesis from the spec applies
 * (chain not loaded / order regression / missing {@code DefaultLoginPageGeneratingFilter}).
 *
 * <p>Single-cycle diagnostic — slated for removal in a subsequent commit before
 * the impl PR merges (spec AC-8). Output goes to the default INFO logger so it
 * surfaces in the docker compose log dump captured by
 * {@code .github/workflows/nightly-e2e.yml} on Playwright failure, AND in the
 * existing JSON log shipping (logger name {@code TASK-BE-311}).
 */
@Component
public class SecurityFilterChainDiagnosticListener {

    private static final Logger log = LoggerFactory.getLogger("TASK-BE-311");

    private final List<SecurityFilterChain> chains;

    public SecurityFilterChainDiagnosticListener(List<SecurityFilterChain> chains) {
        this.chains = chains;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logChainsAtStartup() {
        List<SecurityFilterChain> sorted = chains.stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toList();
        log.info("BE-311 diagnostic — SecurityFilterChain count = {}", sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            SecurityFilterChain chain = sorted.get(i);
            String matcher = chain instanceof DefaultSecurityFilterChain dsfc
                    ? dsfc.getRequestMatcher().toString()
                    : chain.getClass().getName();
            List<String> filterNames = chain.getFilters().stream()
                    .map(Filter::getClass)
                    .map(Class::getSimpleName)
                    .toList();
            log.info(
                    "BE-311 diagnostic — chain[{}] type={} matcher={} filters={}",
                    i,
                    chain.getClass().getSimpleName(),
                    matcher,
                    filterNames);
        }
    }
}
