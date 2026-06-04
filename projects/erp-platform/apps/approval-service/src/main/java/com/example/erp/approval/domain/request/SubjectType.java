package com.example.erp.approval.domain.request;

/**
 * The kind of master subject an approval request references (E1). An approval
 * request references exactly one master subject of one of these types; the
 * {@code MasterDataPort} resolves the corresponding master at submit time.
 *
 * <p>Pure Java — no framework imports.
 */
public enum SubjectType {
    DEPARTMENT,
    EMPLOYEE
}
