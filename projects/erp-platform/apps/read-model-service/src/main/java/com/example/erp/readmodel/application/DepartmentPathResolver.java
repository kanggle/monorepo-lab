package com.example.erp.readmodel.application;

import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the root→leaf department ancestry path by walking {@code parentId}.
 * Shared by the employee org-view and approval-fact subject resolution (extracted
 * dedup — net-zero). Returns neutral {@link Node} triples; each caller maps them
 * to its own view's {@code PathNode} record.
 *
 * <p>Depth-bounded (the producer guarantees no parent-cycle; this terminates
 * defensively at {@code departmentPathMaxDepth} and also guards a self/visited
 * cycle). A parent whose projection is absent simply ends the walk (the path is
 * best-effort; the leaf is always present because the leaf department resolved).
 */
@Component
@RequiredArgsConstructor
public class DepartmentPathResolver {

    /** A single ancestry node (root→leaf), neutral to the consuming view type. */
    public record Node(String id, String code, String name) {
    }

    private final DepartmentProjectionRepository departmentRepository;

    @Value("${erpplatform.readmodel.department-path-max-depth:32}")
    private int departmentPathMaxDepth;

    /**
     * Resolves the root→leaf department ancestry path for {@code leaf}. See the
     * class javadoc for the depth-bound / cycle-guard / best-effort-end semantics
     * (preserved verbatim from the in-lined copies).
     */
    public List<Node> resolvePath(DepartmentProjection leaf) {
        Deque<Node> reversed = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        DepartmentProjection current = leaf;
        int depth = 0;
        while (current != null && depth < departmentPathMaxDepth && visited.add(current.id())) {
            reversed.addFirst(new Node(current.id(), current.code(), current.name()));
            String parentId = current.parentId();
            if (parentId == null || parentId.isBlank()) {
                break;
            }
            current = departmentRepository.findById(parentId).orElse(null);
            depth++;
        }
        return new ArrayList<>(reversed);
    }
}
