package com.example.admin.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * TASK-BE-339 — wire shape of one {@code operator_tenant_assignment} row on the
 * admin-facing org_scope management surface (the element of {@code GET
 * .../assignments} and the body of {@code PUT .../org-scope}).
 *
 * <p>{@code orgScope} is field-level {@code @JsonInclude(NON_NULL)}: a
 * {@code null} org_scope (unset column ⟺ {@code ["*"]} net-zero) is OMITTED from
 * the JSON entirely — NOT rendered as {@code "orgScope": null} (§14 / BE-338
 * absent-vs-null discipline). An explicit empty list {@code []} (zero-scope) IS
 * rendered (it is non-null). {@code permissionSetId} is likewise omitted when
 * {@code null} (inherit operator-level role grants).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorAssignmentResponse(
        String tenantId,
        List<String> orgScope,
        Long permissionSetId
) {}
