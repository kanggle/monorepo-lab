package com.wms.admin.domain.error;

import java.util.UUID;

public final class AssignmentNotFoundException extends AdminDomainException {
    public AssignmentNotFoundException(UUID id) {
        super("ASSIGNMENT_NOT_FOUND", "assignment not found: " + id);
    }
}
