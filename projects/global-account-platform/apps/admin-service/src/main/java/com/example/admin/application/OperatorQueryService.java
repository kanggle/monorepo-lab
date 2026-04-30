package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperatorQueryService {

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminOperatorRoleJpaRepository operatorRoleRepository;
    private final AdminRoleJpaRepository roleRepository;
    private final AdminOperatorTotpJpaRepository totpRepository;

    @Transactional(readOnly = true)
    public OperatorSummary getCurrentOperator(String operatorUuid) {
        AdminOperatorJpaEntity entity = operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorUnauthorizedException(
                        "Operator not found for operatorId=" + operatorUuid));
        return toSummary(entity, loadRoleNames(entity.getId()));
    }

    @Transactional(readOnly = true)
    public OperatorPage listOperators(String statusFilter, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<AdminOperatorJpaEntity> rows;
        if (statusFilter == null || statusFilter.isBlank()) {
            rows = operatorRepository.findAll(pageable);
        } else {
            rows = operatorRepository.findByStatus(statusFilter, pageable);
        }

        List<AdminOperatorJpaEntity> content = rows.getContent();
        Map<Long, List<String>> rolesByOperator = bulkLoadRoles(content);
        Set<Long> enrolledIds = bulkLoadEnrolledTotpIds(content);

        List<OperatorSummary> summaries = new ArrayList<>(content.size());
        for (AdminOperatorJpaEntity entity : content) {
            List<String> roles = rolesByOperator.getOrDefault(entity.getId(), List.of());
            summaries.add(new OperatorSummary(
                    entity.getOperatorId(),
                    entity.getEmail(),
                    entity.getDisplayName(),
                    entity.getStatus(),
                    roles,
                    entity.getTotpEnrolledAt() != null || enrolledIds.contains(entity.getId()),
                    entity.getLastLoginAt(),
                    entity.getCreatedAt()));
        }
        return new OperatorPage(summaries, rows.getTotalElements(), rows.getNumber(),
                rows.getSize(), rows.getTotalPages());
    }

    private List<String> loadRoleNames(Long operatorPk) {
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorId(operatorPk);
        if (bindings.isEmpty()) return List.of();
        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        List<AdminRoleJpaEntity> roles = roleRepository.findAllById(roleIds);
        List<String> names = new ArrayList<>(roles.size());
        for (AdminRoleJpaEntity r : roles) names.add(r.getName());
        Collections.sort(names);
        return names;
    }

    private Map<Long, List<String>> bulkLoadRoles(List<AdminOperatorJpaEntity> operators) {
        if (operators.isEmpty()) return Map.of();
        List<Long> ids = new ArrayList<>(operators.size());
        for (AdminOperatorJpaEntity o : operators) ids.add(o.getId());
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorIdIn(ids);
        if (bindings.isEmpty()) return Map.of();

        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        Map<Long, String> roleNameById = new LinkedHashMap<>();
        for (AdminRoleJpaEntity r : roleRepository.findAllById(roleIds)) {
            roleNameById.put(r.getId(), r.getName());
        }

        Map<Long, List<String>> byOperator = new LinkedHashMap<>();
        for (AdminOperatorRoleJpaEntity b : bindings) {
            String roleName = roleNameById.get(b.getRoleId());
            if (roleName == null) continue;
            byOperator.computeIfAbsent(b.getOperatorId(), k -> new ArrayList<>()).add(roleName);
        }
        for (List<String> names : byOperator.values()) Collections.sort(names);
        return byOperator;
    }

    private Set<Long> bulkLoadEnrolledTotpIds(Collection<AdminOperatorJpaEntity> operators) {
        if (operators.isEmpty()) return Set.of();
        List<Long> operatorInternalIds = new ArrayList<>(operators.size());
        for (AdminOperatorJpaEntity op : operators) operatorInternalIds.add(op.getId());

        Set<Long> enrolled = new java.util.HashSet<>();
        for (AdminOperatorTotpJpaEntity row : totpRepository.findByOperatorIdIn(operatorInternalIds)) {
            if (row != null && row.getEnrolledAt() != null) enrolled.add(row.getOperatorId());
        }
        return enrolled;
    }

    private OperatorSummary toSummary(AdminOperatorJpaEntity entity, List<String> roles) {
        boolean totpEnrolled = entity.getTotpEnrolledAt() != null
                || totpRepository.findById(entity.getId())
                        .map(row -> row != null && row.getEnrolledAt() != null).orElse(false);
        return new OperatorSummary(
                entity.getOperatorId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getStatus(),
                roles,
                totpEnrolled,
                entity.getLastLoginAt(),
                entity.getCreatedAt());
    }

    public record OperatorSummary(
            String operatorId,
            String email,
            String displayName,
            String status,
            List<String> roles,
            boolean totpEnrolled,
            Instant lastLoginAt,
            Instant createdAt
    ) {}

    public record OperatorPage(
            List<OperatorSummary> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {}
}
