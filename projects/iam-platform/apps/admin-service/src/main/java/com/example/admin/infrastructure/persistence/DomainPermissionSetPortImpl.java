package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.EffectiveDomainRolePort;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.DomainPermissionSetJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.DomainPermissionSetJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.DomainPermissionSetRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.DomainPermissionSetRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorDomainSetGrantJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorDomainSetGrantJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupDomainSetGrantJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-BE-631 (ADR-MONO-083 · V0052) — JPA-backed {@link DomainPermissionSetPort} and {@link EffectiveDomainRolePort}.
 * One adapter for the four tables so the effective-role read and the management surface read the same rows the same
 * way.
 */
@Component
@RequiredArgsConstructor
public class DomainPermissionSetPortImpl implements DomainPermissionSetPort, EffectiveDomainRolePort {

    private final DomainPermissionSetJpaRepository sets;
    private final DomainPermissionSetRoleJpaRepository setRoles;
    private final OperatorDomainSetGrantJpaRepository grants;
    private final OperatorGroupDomainSetGrantJpaRepository groupGrants;
    private final OperatorGroupJpaRepository groups;
    private final AdminOperatorJpaRepository operators;

    // ---------- Sets ----------

    @Override
    @Transactional(readOnly = true)
    public Optional<SetView> findBySetId(String setId) {
        if (setId == null || setId.isBlank()) {
            return Optional.empty();
        }
        return sets.findBySetId(setId).map(e -> toViews(List.of(e)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SetView> findPresets() {
        return toViews(sets.findByTenantIdIsNull());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SetView> findTenantSets(String tenantId) {
        return toViews(sets.findByTenantIdOrderByCreatedAtAscIdAsc(tenantId));
    }

    @Override
    @Transactional
    public SetView createTenantSet(String setId, String tenantId, String name, String description,
                                   List<RoleRef> roles, Long createdByInternalId, Instant now) {
        DomainPermissionSetJpaEntity saved = sets.saveAndFlush(DomainPermissionSetJpaEntity.createTenantSet(
                setId, tenantId, name, description, createdByInternalId, now));
        saveRoles(saved.getId(), roles);
        return toViews(List.of(saved)).get(0);
    }

    @Override
    @Transactional
    public SetView updateTenantSet(long setInternalId, String name, String description, List<RoleRef> roles,
                                   Instant now) {
        DomainPermissionSetJpaEntity entity = sets.findById(setInternalId)
                .orElseThrow(() -> new IllegalStateException("domain_permission_set " + setInternalId + " vanished"));
        entity.applyUpdate(name, description, now);
        // BE-335 lesson: flush the dirty UPDATE (and its version check) inside the request transaction.
        DomainPermissionSetJpaEntity saved = sets.saveAndFlush(entity);
        if (roles != null) {
            setRoles.deleteBySetId(setInternalId);
            saveRoles(setInternalId, roles);
        }
        return toViews(List.of(sets.findById(saved.getId()).orElse(saved))).get(0);
    }

    @Override
    @Transactional
    public void deleteSet(long setInternalId) {
        setRoles.deleteBySetId(setInternalId);
        sets.deleteById(setInternalId);
        sets.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public long countGrantRows(long setInternalId, String tenantId) {
        return grants.countBySetIdAndTenantId(setInternalId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countGroupGrants(long setInternalId, String tenantId) {
        return groupGrants.countBySetIdInTenant(setInternalId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAllGrantRows(long setInternalId) {
        return grants.countBySetId(setInternalId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAllGroupGrants(long setInternalId) {
        return groupGrants.countBySetId(setInternalId);
    }

    // ---------- Operator grants ----------

    @Override
    @Transactional(readOnly = true)
    public List<GrantRowView> findGrantRows(long operatorInternalId, Collection<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return List.of();
        }
        List<OperatorDomainSetGrantJpaEntity> rows = grants.findByOperatorIdAndTenantIdIn(operatorInternalId,
                new LinkedHashSet<>(tenantIds));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> setIds = new LinkedHashSet<>();
        Set<Long> groupIds = new HashSet<>();
        Set<Long> granterIds = new HashSet<>();
        for (OperatorDomainSetGrantJpaEntity r : rows) {
            setIds.add(r.getSetId());
            if (r.getGroupOrigin() != null) groupIds.add(r.getGroupOrigin());
            if (r.getGrantedBy() != null) granterIds.add(r.getGrantedBy());
        }
        Map<Long, SetView> setViews = new HashMap<>();
        for (SetView v : toViews(sets.findAllById(setIds))) {
            setViews.put(v.internalId(), v);
        }
        Map<Long, OperatorGroupJpaEntity> groupById = new HashMap<>();
        for (OperatorGroupJpaEntity g : groups.findAllById(groupIds)) {
            groupById.put(g.getId(), g);
        }
        Map<Long, String> granterById = new HashMap<>();
        for (AdminOperatorJpaEntity o : operators.findAllById(granterIds)) {
            granterById.put(o.getId(), o.getOperatorId());
        }
        List<GrantRowView> out = new ArrayList<>(rows.size());
        for (OperatorDomainSetGrantJpaEntity r : rows) {
            SetView set = setViews.get(r.getSetId());
            if (set == null) {
                // FK RESTRICT makes this unreachable; never invent a set.
                throw new IllegalStateException("grant row points at missing set " + r.getSetId());
            }
            OperatorGroupJpaEntity group = r.getGroupOrigin() == null ? null : groupById.get(r.getGroupOrigin());
            out.add(new GrantRowView(r.getOperatorId(), r.getTenantId(), set, r.getSourceKey(), r.getGroupOrigin(),
                    group == null ? null : group.getGroupId(), group == null ? null : group.getName(),
                    r.getGrantedBy() == null ? null : granterById.get(r.getGrantedBy()), r.getGrantedAt()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean grantExists(long operatorInternalId, String tenantId, long setInternalId, String sourceKey) {
        return grants.existsByOperatorIdAndTenantIdAndSetIdAndSourceKey(
                operatorInternalId, tenantId, setInternalId, sourceKey);
    }

    @Override
    @Transactional
    public void insertGrant(long operatorInternalId, String tenantId, long setInternalId, String sourceKey,
                            Long groupOrigin, Long grantedByInternalId, Instant grantedAt) {
        grants.saveAndFlush(OperatorDomainSetGrantJpaEntity.create(operatorInternalId, tenantId, setInternalId,
                sourceKey, groupOrigin, grantedByInternalId, grantedAt));
    }

    @Override
    @Transactional
    public List<String> deleteGrants(long operatorInternalId, String tenantId, long setInternalId,
                                     Collection<String> sourceKeys) {
        if (sourceKeys == null || sourceKeys.isEmpty()) {
            return List.of();
        }
        List<String> present = new ArrayList<>();
        for (OperatorDomainSetGrantJpaEntity r : grants.findByOperatorIdAndTenantIdAndSetIdAndSourceKeyIn(
                operatorInternalId, tenantId, setInternalId, sourceKeys)) {
            present.add(r.getSourceKey());
        }
        if (present.isEmpty()) {
            return List.of();
        }
        grants.deleteSources(operatorInternalId, tenantId, setInternalId, present);
        present.sort(null);
        return List.copyOf(present);
    }

    // ---------- Effective roles (auth-to-admin.md rule 7) ----------

    @Override
    @Transactional(readOnly = true)
    public List<String> effectiveRoleNames(long operatorInternalId, Collection<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return List.of();
        }
        Set<Long> setIds = new LinkedHashSet<>();
        for (OperatorDomainSetGrantJpaEntity r : grants.findByOperatorIdAndTenantIdIn(operatorInternalId,
                new LinkedHashSet<>(tenantIds))) {
            setIds.add(r.getSetId());
        }
        if (setIds.isEmpty()) {
            return List.of();
        }
        List<RoleRef> refs = new ArrayList<>();
        for (DomainPermissionSetRoleJpaEntity role : setRoles.findBySetIdIn(setIds)) {
            refs.add(new RoleRef(role.getDomainKey(), role.getRoleName()));
        }
        Set<String> names = new LinkedHashSet<>();
        for (RoleRef r : DomainRoleCatalog.sortAndDistinct(refs)) {
            names.add(r.role());
        }
        return List.copyOf(names);
    }

    // ---------- helpers ----------

    private void saveRoles(long setInternalId, List<RoleRef> roles) {
        List<DomainPermissionSetRoleJpaEntity> rows = new ArrayList<>();
        for (RoleRef r : DomainRoleCatalog.sortAndDistinct(roles)) {
            rows.add(DomainPermissionSetRoleJpaEntity.create(setInternalId, r.domainKey(), r.role()));
        }
        setRoles.saveAllAndFlush(rows);
    }

    private List<SetView> toViews(Collection<DomainPermissionSetJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (DomainPermissionSetJpaEntity e : entities) {
            ids.add(e.getId());
        }
        Map<Long, List<RoleRef>> rolesBySet = new HashMap<>();
        for (DomainPermissionSetRoleJpaEntity r : setRoles.findBySetIdIn(ids)) {
            rolesBySet.computeIfAbsent(r.getSetId(), k -> new ArrayList<>())
                    .add(new RoleRef(r.getDomainKey(), r.getRoleName()));
        }
        List<SetView> out = new ArrayList<>(entities.size());
        for (DomainPermissionSetJpaEntity e : entities) {
            out.add(new SetView(e.getId(), e.getSetId(), e.getTenantId(), e.getSystemKey(), e.getName(),
                    e.getDescription(), DomainRoleCatalog.sortAndDistinct(rolesBySet.getOrDefault(e.getId(), List.of())),
                    e.getCreatedAt(), e.getUpdatedAt()));
        }
        return out;
    }
}
