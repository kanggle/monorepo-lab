package com.example.auth.application.service;

import com.example.auth.domain.entity.AuditLog;
import com.example.auth.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 저장 전용 컴포넌트.
 * <p>
 * AuditLogService에서 직접 @Transactional(REQUIRES_NEW)을 사용하면,
 * JPA 예외 발생 시 Hibernate Session이 rollback-only로 표시되어 커밋 단계에서
 * UnexpectedRollbackException이 발생하고, AuditLogService의 catch 블록을 우회하여
 * 호출자에게 전파된다. 이 클래스를 별도 Spring 빈으로 분리하면 try-catch가
 * 커밋 예외까지 포함하여 처리할 수 있다.
 */
@Component
@RequiredArgsConstructor
class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }
}
