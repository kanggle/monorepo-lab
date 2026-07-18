package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** TASK-BE-520 (ADR-MONO-046) — {@code POST /api/admin/groups/{groupId}/members} body. */
public record AddGroupMemberRequest(
        @NotBlank String operatorId
) {}
