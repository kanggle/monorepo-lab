package com.example.admin.infrastructure.access;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.presentation.aspect.ResourceTagResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-MONO-029 § D2-A / TASK-BE-353 — the iam pilot's {@link ResourceTagResolver}:
 * resolves an admin <b>operator</b>'s tags for the {@code RESOURCE_TAG} access
 * condition (pilot = deny role/status/profile mutations on a {@code protected}
 * operator).
 *
 * <p>Applicability is decided by the request PATH (a role/status/profile mutation
 * under {@code /api/admin/operators/{operatorId}/…}); the tags come from the
 * trusted {@code admin_operators.tags} column (never the request — anti-spoof,
 * § D2-C). A non-operator mutation (or the self-service {@code /me/…} paths) →
 * {@link Optional#empty()} (the condition is skipped — net-zero). An operator that
 * is untagged or absent → {@code Optional.of(emptySet)} (allowed at the gate; an
 * absent operator 404s downstream regardless).
 */
@Component
@RequiredArgsConstructor
public class OperatorResourceTagResolver implements ResourceTagResolver {

    /** Operator role/status/profile mutation paths (the pilot's gated surface). */
    private static final Pattern OPERATOR_MUTATION =
            Pattern.compile("^/api/admin/operators/([^/]+)/(?:roles|status|profile)$");

    private final AdminOperatorJpaRepository operatorRepository;

    @Override
    public Optional<Set<String>> resolveResourceTags(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Matcher m = OPERATOR_MUTATION.matcher(request.getRequestURI());
        if (!m.matches()) {
            return Optional.empty(); // not an operator-targeting mutation → skip (net-zero)
        }
        String operatorId = m.group(1);
        if ("me".equals(operatorId)) {
            return Optional.empty(); // self-service path is not a target resource
        }
        // Present (applicable). tags column may be NULL (untagged) or the row may be
        // absent — both project to an empty tag set (allowed at the gate).
        String raw = operatorRepository.findTagsByOperatorId(operatorId).orElse(null);
        return Optional.of(ResourceTags.splitTags(raw));
    }
}
