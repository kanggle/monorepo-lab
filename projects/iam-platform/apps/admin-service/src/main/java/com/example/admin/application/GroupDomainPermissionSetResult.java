package com.example.admin.application;

import java.time.Instant;

/**
 * TASK-BE-631 slice B — one set granted to a group (an {@code operator_group_domain_set_grant} template) as the
 * group endpoints answer it: {@code {setId, name, systemKey, grantedAt, grantedBy}}.
 *
 * @param grantedBy the granting operator's external id ({@code null} when that operator no longer exists)
 */
public record GroupDomainPermissionSetResult(String setId, String name, String systemKey, Instant grantedAt,
                                             String grantedBy) {

    /** The 201 of a group set grant: the template and how many member rows the fan-out wrote. */
    public record Granted(GroupDomainPermissionSetResult item, int fannedOutRows) {}
}
