package com.example.admin.application;

import com.example.admin.application.port.DomainPermissionSetPort.SetView;

/**
 * TASK-BE-631 — one set as the API shows it (admin-api.md «Domain permission set wire shape»): the set and its
 * roles, plus the two counts measured IN THE ACTING TENANT — operator grant rows of every source
 * ({@code grantCount}) and groups holding it ({@code groupCount}).
 */
public record DomainPermissionSetResult(SetView set, long grantCount, long groupCount) {}
