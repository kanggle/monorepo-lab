package com.example.admin.infrastructure.access;

import com.example.admin.infrastructure.persistence.access.AdminResourceTagJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-355 — {@link AccountResourceTagResolver}: path applicability
 * ({@code POST /api/admin/accounts/{id}/lock|unlock} only; {@code bulk-lock} has no
 * single id → skipped) + the admin-local {@code admin_resource_tags} (type
 * {@code ACCOUNT}) tags → tag set.
 */
@DisplayName("AccountResourceTagResolver — ADR-MONO-029 account tag resolution")
class AccountResourceTagResolverTest {

    private final AdminResourceTagJpaRepository repo = mock(AdminResourceTagJpaRepository.class);
    private final AccountResourceTagResolver resolver = new AccountResourceTagResolver(repo);

    private static MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        return r;
    }

    @Test
    @DisplayName("account lock: ACCOUNT tags split into a set")
    void lockResolvesTags() {
        when(repo.findTags("ACCOUNT", "acc-1")).thenReturn(Optional.of("protected"));
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/accounts/acc-1/lock")))
                .contains(Set.of("protected"));
    }

    @Test
    @DisplayName("account unlock: applicable")
    void unlockApplicable() {
        when(repo.findTags("ACCOUNT", "acc-1")).thenReturn(Optional.of("protected"));
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/accounts/acc-1/unlock")))
                .contains(Set.of("protected"));
    }

    @Test
    @DisplayName("untagged / absent account → present-but-empty set")
    void untaggedOrAbsentResolvesEmptySet() {
        when(repo.findTags("ACCOUNT", "acc-2")).thenReturn(Optional.empty());
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/accounts/acc-2/lock"))).contains(Set.of());
    }

    @Test
    @DisplayName("bulk-lock (no single id) → not applicable (empty Optional)")
    void bulkLockNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/accounts/bulk-lock"))).isEmpty();
    }

    @Test
    @DisplayName("non-account path → not applicable")
    void nonAccountPathNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/tenants/globex"))).isEmpty();
    }
}
