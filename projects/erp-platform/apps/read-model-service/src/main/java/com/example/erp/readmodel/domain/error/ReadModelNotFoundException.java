package com.example.erp.readmodel.domain.error;

/**
 * Raised when an org-view is requested for an employee id that has no
 * projection row (a projection miss is a 404 {@code MASTERDATA_NOT_FOUND},
 * never a fabricated row — E5). Pure domain exception (no framework type).
 */
public class ReadModelNotFoundException extends RuntimeException {

    public static final String CODE = "MASTERDATA_NOT_FOUND";

    public ReadModelNotFoundException(String employeeId) {
        super("No employee projection for id: " + employeeId);
    }
}
