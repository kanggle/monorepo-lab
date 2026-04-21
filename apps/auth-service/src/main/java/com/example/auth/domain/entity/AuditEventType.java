package com.example.auth.domain.entity;

public enum AuditEventType {
    SIGNUP,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    TOKEN_REFRESH,
    LOGOUT,
    ACCOUNT_DEACTIVATED
}
