package com.example.auth.application.service;

import com.example.auth.application.dto.SignupCommand;
import com.example.auth.application.dto.SignupResult;
import com.example.auth.application.exception.EmailAlreadyExistsException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.service.AuthMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import com.example.auth.domain.service.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuthEventPublisher eventPublisher;
    private final AuthMetricsRecorder authMetrics;

    @Transactional
    public SignupResult signup(SignupCommand command) {
        String normalizedEmail = command.email().toLowerCase().trim();
        try {
            if (userRepository.existsByEmail(normalizedEmail)) {
                log.warn("Signup rejected: email already registered, userId lookup skipped");
                throw new EmailAlreadyExistsException();
            }
        } catch (DataAccessException e) {
            log.error("Signup failed: DB error during email existence check", e);
            throw e;
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(normalizedEmail, encodedPassword, command.name());
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataAccessException e) {
            log.error("Signup failed: DB error during user save", e);
            throw e;
        }
        log.info("User registered: userId={}", saved.getId());
        authMetrics.incrementSignup();

        final UUID savedId = saved.getId();
        final String savedEmail = saved.getEmail().value();
        final String savedName = saved.getName();
        // 트랜잭션 커밋 후 감사 로그를 기록해야 실제 사용자 저장이 완료된 이후에 남는다.
        // 트랜잭션 컨텍스트가 없는 환경(단위 테스트 등)에서는 즉시 호출한다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAuditAndEvent(savedId, savedEmail, savedName, command.ipAddress(), command.userAgent());
                }
            });
        } else {
            publishAuditAndEvent(savedId, savedEmail, savedName, command.ipAddress(), command.userAgent());
        }
        return new SignupResult(saved.getId(), saved.getEmail().value(), saved.getName(), saved.getCreatedAt());
    }

    private void publishAuditAndEvent(UUID userId, String email, String name, String ipAddress, String userAgent) {
        auditLogService.recordSignup(userId, email, ipAddress, userAgent);
        eventPublisher.publish(AuthEvent.of(new UserSignedUp(userId, email, name)));
    }
}
