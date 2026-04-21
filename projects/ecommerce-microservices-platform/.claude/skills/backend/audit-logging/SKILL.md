---
name: audit-logging
description: Audit log entity, REQUIRES_NEW transaction pattern
category: backend
---

# Skill: Audit Logging

Patterns for recording audit logs with separate transaction handling.

Prerequisite: read `specs/platform/security-rules.md` before using this skill.

---

## Audit Log Domain Entity

```java
@Entity
@Table(name = "auth_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    private UUID id;
    private UUID userId;
    private String email;

    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    private String ipAddress;
    private String userAgent;

    @Enumerated(EnumType.STRING)
    private AuditResult result;

    private String failureReason;
    private Instant createdAt;

    public static AuditLog create(UUID userId, String email, AuditEventType eventType,
                                   String ipAddress, String userAgent,
                                   AuditResult result, String failureReason) {
        AuditLog log = new AuditLog();
        log.id = UUID.randomUUID();
        log.userId = userId;
        log.email = email;
        log.eventType = eventType;
        log.ipAddress = ipAddress;
        log.userAgent = truncate(userAgent, 500);
        log.result = result;
        log.failureReason = failureReason;
        log.createdAt = Instant.now();
        return log;
    }
}
```

---

## Service + Writer Pattern

Two separate beans to isolate transaction behavior.

### AuditLogService (Facade)

Provides typed methods per event. Catches all exceptions to prevent audit failures from breaking the main flow.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogWriter auditLogWriter;

    public void recordLoginSuccess(UUID userId, String email, String ip, String ua) {
        save(AuditLog.create(userId, email, AuditEventType.LOGIN_SUCCESS, ip, ua, AuditResult.SUCCESS, null));
    }

    public void recordLoginFailure(String email, String ip, String ua, String reason) {
        save(AuditLog.create(null, email, AuditEventType.LOGIN_FAILURE, ip, ua, AuditResult.FAILURE, reason));
    }

    private void save(AuditLog auditLog) {
        try {
            auditLogWriter.save(auditLog);
        } catch (Exception e) {
            log.error("Audit log save failed: eventType={}", auditLog.getEventType(), e);
            // Never propagate — audit failure must not break the main operation
        }
    }
}
```

### AuditLogWriter (Separate Transaction)

```java
@Component
@RequiredArgsConstructor
class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }
}
```

**Why separate bean?** If `@Transactional(REQUIRES_NEW)` is on `AuditLogService.save()` directly, a JPA commit-phase exception (`UnexpectedRollbackException`) escapes the `try/catch` because the exception is thrown after the method returns. The separate bean ensures the commit happens inside the writer's proxy boundary, so the service's catch block can handle all failures.

---

## Event Types

```java
public enum AuditEventType {
    SIGNUP,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    TOKEN_REFRESH,
    LOGOUT,
    ACCOUNT_DEACTIVATED
}

public enum AuditResult {
    SUCCESS,
    FAILURE
}
```

---

## Captured Fields

| Field | Purpose |
|---|---|
| userId | Who performed the action (null for failed logins) |
| email | Account identifier |
| eventType | What happened |
| ipAddress | Source IP for forensics |
| userAgent | Client identification (truncated to 500 chars) |
| result | SUCCESS or FAILURE |
| failureReason | Why it failed (null on success) |

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Audit failure propagates to caller | Always catch exceptions in `AuditLogService.save()` |
| `REQUIRES_NEW` on same bean method | Spring self-invocation bypasses proxy — use separate bean |
| Long user agent strings | Truncate to column max length |
| Missing audit on error paths | Record failure events, not just successes |
