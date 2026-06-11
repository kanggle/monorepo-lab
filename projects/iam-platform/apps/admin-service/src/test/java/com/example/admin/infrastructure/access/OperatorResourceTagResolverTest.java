package com.example.admin.infrastructure.access;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR-MONO-029 / TASK-BE-353 — the iam pilot {@link OperatorResourceTagResolver}:
 * path applicability (operator role/status/profile mutations only) + the
 * comma-separated {@code tags} column → tag set, fail-safe on untagged/absent.
 */
@DisplayName("OperatorResourceTagResolver — ADR-MONO-029 operator tag resolution")
class OperatorResourceTagResolverTest {

    private final AdminOperatorJpaRepository repo = mock(AdminOperatorJpaRepository.class);
    private final OperatorResourceTagResolver resolver = new OperatorResourceTagResolver(repo);

    private static MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRequestURI(uri);
        return r;
    }

    @Test
    @DisplayName("operator role mutation: tags column split into a set")
    void rolesMutationResolvesTags() {
        when(repo.findTagsByOperatorId("op-x")).thenReturn(Optional.of("protected, vip"));
        Optional<Set<String>> tags = resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/op-x/roles"));
        assertThat(tags).contains(Set.of("protected", "vip"));
    }

    @Test
    @DisplayName("operator status + profile mutations are also applicable")
    void statusAndProfileApplicable() {
        when(repo.findTagsByOperatorId("op-y")).thenReturn(Optional.of("protected"));
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/op-y/status")))
                .contains(Set.of("protected"));
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/op-y/profile")))
                .contains(Set.of("protected"));
    }

    @Test
    @DisplayName("untagged operator (NULL tags) or absent row ⟹ applicable with an empty set")
    void untaggedOrAbsentIsEmptySet() {
        when(repo.findTagsByOperatorId("op-untagged")).thenReturn(Optional.empty()); // NULL tags / absent
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/op-untagged/roles")))
                .contains(Set.of());
    }

    @Test
    @DisplayName("blank entries in the tags column are dropped")
    void blanksDropped() {
        when(repo.findTagsByOperatorId("op-z")).thenReturn(Optional.of(" protected ,, , vip "));
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/op-z/roles")))
                .contains(Set.of("protected", "vip"));
    }

    @Test
    @DisplayName("non-operator mutation (e.g. account lock) ⟹ not applicable (empty Optional)")
    void nonOperatorPathNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/accounts/acc-1/lock"))).isEmpty();
    }

    @Test
    @DisplayName("operator CREATE / LIST (no {operatorId} target) ⟹ not applicable")
    void operatorCollectionPathsNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("POST", "/api/admin/operators"))).isEmpty();
        assertThat(resolver.resolveResourceTags(req("GET", "/api/admin/operators"))).isEmpty();
    }

    @Test
    @DisplayName("self-service /me path is not a target resource ⟹ not applicable")
    void mePathNotApplicable() {
        assertThat(resolver.resolveResourceTags(req("PATCH", "/api/admin/operators/me/profile"))).isEmpty();
    }

    @Test
    @DisplayName("null request ⟹ not applicable")
    void nullRequest() {
        assertThat(resolver.resolveResourceTags(null)).isEmpty();
    }
}
