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
 * TASK-BE-355 — {@link TenantResourceTagResolver}: path applicability
 * ({@code PATCH /api/admin/tenants/{id}} only) + the admin-local
 * {@code admin_resource_tags} (type {@code TENANT}) tags → tag set, fail-safe on
 * untagged/absent.
 */
@DisplayName("TenantResourceTagResolver — ADR-MONO-029 tenant tag resolution")
class TenantResourceTagResolverTest {

    private final AdminResourceTagJpaRepository repo = mock(AdminResourceTagJpaRepository.class);
    private final TenantResourceTagResolver resolver = new TenantResourceTagResolver(repo);

    private static MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        return r;
    }

    @Test
    @DisplayName("tenant update: TENANT tags split into a set")
    void tenantUpdateResolvesTags() {
        when(repo.findTags("TENANT", "globex")).thenReturn(Optional.of("protected, vip"));
        Optional<Set<String>> tags = resolver.resolveResourceTags(req("PATCH", "/api/admin/tenants/globex"));
        assertThat(tags).contains(Set.of("protected", "vip"));
    }

    @Test
    @DisplayName("untagged tenant (NULL column) → present-but-empty set")
    void untaggedTenantResolvesEmptySet() {
        when(repo.findTags("TENANT", "globex")).thenReturn(Optional.of(""));
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/tenants/globex"))).contains(Set.of());
    }

    @Test
    @DisplayName("absent tenant row → present-but-empty set (allowed at gate; 404s downstream)")
    void absentTenantResolvesEmptySet() {
        when(repo.findTags("TENANT", "ghost")).thenReturn(Optional.empty());
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/tenants/ghost"))).contains(Set.of());
    }

    @Test
    @DisplayName("collection create POST /tenants (no id) → not applicable (empty Optional)")
    void createCollectionNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/tenants"))).isEmpty();
    }

    @Test
    @DisplayName("non-tenant path → not applicable (empty Optional)")
    void nonTenantPathNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/op-x/roles"))).isEmpty();
    }

    @Test
    @DisplayName("null request → not applicable")
    void nullRequestNotApplicable() {
        assertThat(resolver.resolveResourceTags(null)).isEmpty();
    }
}
