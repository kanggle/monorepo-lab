package com.example.admin.presentation.aspect;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.Set;

/**
 * ADR-MONO-029 § D2-A — resolves the <b>target resource's tags</b> for the
 * {@code RESOURCE_TAG} access condition, so the condition can be evaluated at the
 * single authorization decision site ({@code RequiresPermissionAspect}) even though
 * the aspect runs before the controller loads the resource.
 *
 * <p>Returns {@link Optional#empty()} when the request does <b>not</b> target a
 * resolvable resource (the condition is then skipped — net-zero for non-targeted
 * endpoints). Returns {@code Optional.of(tags)} (a possibly-empty set) when it does
 * — an empty set means "the resource is known to carry no tags" (allowed under
 * deny-if-present). The tags MUST come from trusted domain data, never the request
 * (a client-supplied tag would be spoofable — ADR-029 § D2-C).
 */
public interface ResourceTagResolver {

    /**
     * @param request the in-flight admin mutation request
     * @return the target resource's tags ({@code Optional.of}, possibly empty), or
     *         {@link Optional#empty()} when the request targets no resolvable resource.
     */
    Optional<Set<String>> resolveResourceTags(HttpServletRequest request);
}
