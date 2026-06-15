package com.example.erp.readmodel.application;

import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Expands {@code org_scope} subtree-roots → the union of their descendant
 * department ids over {@code department_proj.parent_id} (mirrors
 * masterdata-service's write-side subtree containment, but using the read-model's
 * own projection). Shared by the org-view / approval-fact / delegation-fact query
 * use cases (TASK-ERP-BE-008 read filter; extracted dedup — net-zero).
 *
 * <p>{@code null} → {@code null} (no narrowing, net-zero; every BE-007 caller is
 * unaffected); a bounded scope with no/empty roots → an empty list (matches
 * nothing — zero-scope fail-closed). A root not (yet) projected contributes only
 * itself (best-effort; never fabricates ids it cannot see).
 */
@Component
@RequiredArgsConstructor
public class OrgScopeExpander {

    private final DepartmentProjectionRepository departmentRepository;

    @Value("${erpplatform.readmodel.department-path-max-depth:32}")
    private int departmentPathMaxDepth;

    /**
     * Expands {@code org_scope} subtree-roots → the union of their descendant
     * department ids. See the class javadoc for the {@code null}/zero-scope
     * semantics (preserved verbatim from the in-lined copies).
     */
    public List<String> expand(List<String> orgScopeRootIds) {
        if (orgScopeRootIds == null) {
            return null;
        }
        Set<String> union = new LinkedHashSet<>();
        for (String root : orgScopeRootIds) {
            if (root != null && !root.isBlank()) {
                union.addAll(departmentRepository.findSubtreeIds(root, departmentPathMaxDepth));
            }
        }
        return new ArrayList<>(union);
    }
}
