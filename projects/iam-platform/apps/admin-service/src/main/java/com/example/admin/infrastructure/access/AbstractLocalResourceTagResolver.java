package com.example.admin.infrastructure.access;

import com.example.admin.infrastructure.persistence.access.AdminResourceTagJpaRepository;
import com.example.admin.presentation.aspect.ResourceTagResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TASK-BE-355 (ADR-MONO-029 § D2-A/D2-C) — base for {@link ResourceTagResolver}s
 * whose target is a non-operator resource (tenant / account) whose governance tags
 * live in the admin-local {@code admin_resource_tags} table.
 *
 * <p>Applicability is decided by the request PATH (a {@link Pattern} whose group 1
 * is the resource id); the tags come from the trusted local table (never the
 * request — anti-spoof). A request that does not match the pattern →
 * {@link Optional#empty()} (the condition is skipped for this resolver — net-zero).
 * A matched resource that is untagged or absent → {@code Optional.of(emptySet)}
 * (allowed at the gate under deny-if-present; an absent resource 404s downstream).
 */
abstract class AbstractLocalResourceTagResolver implements ResourceTagResolver {

    private final AdminResourceTagJpaRepository repository;
    private final String resourceType;
    private final Pattern pattern;

    protected AbstractLocalResourceTagResolver(AdminResourceTagJpaRepository repository,
                                               String resourceType, Pattern pattern) {
        this.repository = repository;
        this.resourceType = resourceType;
        this.pattern = pattern;
    }

    @Override
    public Optional<Set<String>> resolveResourceTags(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(request.getRequestURI());
        if (!m.matches()) {
            return Optional.empty(); // not a resource of this type → skip (net-zero)
        }
        String resourceId = m.group(1);
        String raw = repository.findTags(resourceType, resourceId).orElse(null);
        return Optional.of(splitTags(raw));
    }

    /** Split a comma-separated tags string into a set, dropping blanks. */
    private static Set<String> splitTags(String raw) {
        Set<String> tags = new HashSet<>();
        if (raw == null) {
            return tags;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                tags.add(t);
            }
        }
        return tags;
    }
}
