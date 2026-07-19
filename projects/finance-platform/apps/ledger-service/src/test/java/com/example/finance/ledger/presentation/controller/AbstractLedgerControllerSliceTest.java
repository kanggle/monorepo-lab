package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Shared base for the ledger-service {@code @WebMvcTest} controller-slice tests.
 *
 * <p>Every slice bypasses the security filter chain ({@code addFilters = false}) and instead
 * places an authenticated {@link ActorContext} directly into the {@link SecurityContextHolder}
 * before each test — the identical four-line {@code @BeforeEach} that used to be copy-pasted into
 * each slice. Subclasses supply their own actor shape via {@link #actor()}; nothing else about the
 * SecurityContext handling differs between slices.
 *
 * <p>No teardown is declared: each subclass re-installs a fresh authentication in this
 * {@code @BeforeEach} before any test observes the context, so cross-test cleanup is not relied
 * upon (the single test that needs an empty context clears it itself).
 */
abstract class AbstractLedgerControllerSliceTest {

    /**
     * The authenticated actor pushed into the {@link SecurityContextHolder} before each test.
     * Each slice overrides this with its own actor shape.
     */
    protected abstract ActorContext actor();

    @BeforeEach
    void setUpSecurityContext() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(actor(), "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
