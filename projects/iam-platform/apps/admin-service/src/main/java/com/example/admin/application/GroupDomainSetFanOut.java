package com.example.admin.application;

import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.GroupSetGrantView;
import com.example.admin.domain.rbac.DomainGrantSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;

/**
 * TASK-BE-631 slice B (ADR-MONO-083 D4 · ADR-MONO-046 D2 kept) — the fan-out of a group's DOMAIN PERMISSION SETS
 * onto its members, as rows of {@code operator_domain_set_grant} with {@code source_key = GROUP:<group.id>} and
 * {@code group_origin = group.id} (data-model.md § operator_group_domain_set_grant fan-out note).
 *
 * <p>Differs from {@link GroupFanOutService} (the admin-role / tenant-assignment fan-out) on purpose:
 * <ul>
 *   <li><b>Never skip.</b> The source is part of the primary key, so a member who already holds the same set through
 *       DIRECT · MIGRATION · another group still gets this group's own row. There is no «first writer owns it» —
 *       removing this group later cannot take away what another source gives (ADR-083 failure scenario 1).</li>
 *   <li><b>Group tenant only.</b> Every row's {@code tenant_id} is the group's tenant — a group gives sets in its
 *       own company and nowhere else. The member is a member of that company (the group-member invariant, 784 D-11),
 *       so no «work role without membership» row can arise.</li>
 *   <li><b>Own source only on the way out.</b> Every delete is keyed on the exact {@code GROUP:<id>} — DIRECT,
 *       MIGRATION and other groups' rows are never touched.</li>
 * </ul>
 * Joins the caller's transaction (the membership / template change and its audit row are one unit). Nothing here
 * touches the management-plane permission cache — a work-role set grants no {@code /api/admin/**} authority.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class GroupDomainSetFanOut {

    private final DomainPermissionSetPort setPort;

    /** Whether the group carries at least one set (the member-add {@code domain_access.manage} gate). */
    @Transactional(readOnly = true)
    public boolean groupHasSets(Long groupInternalId) {
        return groupInternalId != null && setPort.groupHasGrants(groupInternalId);
    }

    /**
     * A set newly granted to the group: one row per CURRENT member, in the group's tenant.
     *
     * @return the number of member rows written
     */
    public int fanOutSetToMembers(long groupInternalId, String groupTenantId, long setInternalId,
                                  Collection<Long> memberInternalIds, Long grantedBy, Instant now) {
        String sourceKey = DomainGrantSource.group(groupInternalId);
        int written = 0;
        for (Long member : memberInternalIds) {
            setPort.insertGrant(member, groupTenantId, setInternalId, sourceKey, groupInternalId, grantedBy, now);
            written++;
        }
        return written;
    }

    /**
     * A member newly added to the group: one row per template of the group, in the group's tenant.
     *
     * @return the number of rows written
     */
    public int fanOutSetsToMember(Long groupInternalId, String groupTenantId, long memberInternalId,
                                  Long grantedBy, Instant now) {
        if (groupInternalId == null) {
            return 0;
        }
        String sourceKey = DomainGrantSource.group(groupInternalId);
        int written = 0;
        for (GroupSetGrantView template : setPort.findGroupGrants(groupInternalId)) {
            setPort.insertGrant(memberInternalId, groupTenantId, template.set().internalId(), sourceKey,
                    groupInternalId, grantedBy, now);
            written++;
        }
        return written;
    }

    /** A member removed from the group: that member's {@code GROUP:<id>} rows only. @return rows deleted */
    public int revokeMember(Long groupInternalId, long memberInternalId) {
        if (groupInternalId == null) {
            return 0;
        }
        return setPort.deleteGroupSourceRowsOfOperator(groupInternalId, memberInternalId);
    }

    /** The group deleted: every {@code GROUP:<id>} row and every template. @return member rows deleted */
    public int revokeGroup(Long groupInternalId) {
        if (groupInternalId == null) {
            return 0;
        }
        int rows = setPort.deleteGroupSourceRows(groupInternalId);
        setPort.deleteGroupGrants(groupInternalId);
        return rows;
    }
}
