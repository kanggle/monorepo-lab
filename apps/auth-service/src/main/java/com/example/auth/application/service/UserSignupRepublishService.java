package com.example.auth.application.service;

import com.example.auth.application.dto.RepublishSignupEventsResult;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 내부 운영용: auth-service {@code users} 테이블을 순회하여
 * {@code auth.user.signed-up} 이벤트를 재발행한다.
 *
 * <p>user-service의 {@code UserSignedUpConsumer}가 멱등이므로,
 * 이미 {@code user_profiles}가 있는 유저는 자동 스킵되고
 * 누락된 유저만 복구된다.
 *
 * <p>failedCount는 best-effort: {@link AuthEventPublisher#publish}가
 * 동기적으로 던지는 예외만 집계하며, 비동기 Kafka 실패는 메트릭으로만 노출된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSignupRepublishService {

    private final UserRepository userRepository;
    private final AuthEventPublisher eventPublisher;

    public RepublishSignupEventsResult republishAll() {
        List<User> users = userRepository.findAll();
        int total = users.size();
        int published = 0;
        int failed = 0;

        log.info("Signup event republish started: totalUsers={}", total);

        for (User user : users) {
            try {
                UserSignedUp payload = new UserSignedUp(
                    user.getId(),
                    user.getEmail().value(),
                    user.getName()
                );
                eventPublisher.publish(AuthEvent.of(payload));
                published++;
            } catch (Exception e) {
                failed++;
                // PII 보호: 예외 메시지에 이메일 등이 포함될 수 있으므로 클래스명만 기록한다.
                log.warn("Signup event republish failed for userId={}: errorType={}",
                    user.getId(), e.getClass().getSimpleName());
            }
        }

        log.info("Signup event republish finished: total={}, published={}, failed={}",
            total, published, failed);

        return new RepublishSignupEventsResult(total, published, failed);
    }
}
