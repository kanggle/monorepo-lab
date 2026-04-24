package com.example.auth.application.service;

import com.example.auth.domain.entity.AuditEventType;
import com.example.auth.domain.entity.AuditLog;
import com.example.auth.domain.entity.AuditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogWriter auditLogWriter;

    public void recordSignup(UUID userId, String email, String ipAddress, String userAgent) {
        save(AuditLog.create(userId, email, AuditEventType.SIGNUP, ipAddress, userAgent, AuditResult.SUCCESS, null));
    }

    public void recordLoginSuccess(UUID userId, String email, String ipAddress, String userAgent) {
        save(AuditLog.create(userId, email, AuditEventType.LOGIN_SUCCESS, ipAddress, userAgent, AuditResult.SUCCESS, null));
    }

    public void recordLoginFailure(String email, String ipAddress, String userAgent, String reason) {
        save(AuditLog.create(null, email, AuditEventType.LOGIN_FAILURE, ipAddress, userAgent, AuditResult.FAILURE, reason));
    }

    public void recordTokenRefresh(UUID userId, String email, String ipAddress, String userAgent) {
        save(AuditLog.create(userId, email, AuditEventType.TOKEN_REFRESH, ipAddress, userAgent, AuditResult.SUCCESS, null));
    }

    public void recordLogout(UUID userId, String email, String ipAddress, String userAgent) {
        save(AuditLog.create(userId, email, AuditEventType.LOGOUT, ipAddress, userAgent, AuditResult.SUCCESS, null));
    }

    public void recordAccountDeactivation(UUID userId, String email) {
        save(AuditLog.create(userId, email, AuditEventType.ACCOUNT_DEACTIVATED, null, null, AuditResult.SUCCESS, null));
    }

    private void save(AuditLog auditLog) {
        try {
            auditLogWriter.save(auditLog);
        } catch (Exception e) {
            log.error("Audit log save failed: eventType={}, userId={}", auditLog.getEventType(), auditLog.getUserId(), e);
        }
    }
}
