package com.example.admin.presentation.dto;

import java.util.List;

/** TASK-BE-520 (ADR-MONO-046) — {@code GET /api/admin/groups/{groupId}/grants} wire shape. */
public record GroupGrantListResponse(List<GroupGrantResponse> items) {}
