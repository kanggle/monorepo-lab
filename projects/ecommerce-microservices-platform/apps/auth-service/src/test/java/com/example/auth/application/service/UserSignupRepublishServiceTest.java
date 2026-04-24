package com.example.auth.application.service;

import com.example.auth.application.dto.RepublishSignupEventsResult;
import com.example.auth.domain.entity.Role;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSignupRepublishService 단위 테스트")
class UserSignupRepublishServiceTest {

    @InjectMocks
    private UserSignupRepublishService service;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthEventPublisher eventPublisher;

    @Test
    @DisplayName("모든 유저에 대해 UserSignedUp 이벤트가 발행된다")
    void republishAll_publishesForEveryUser() {
        List<User> users = List.of(
            sampleUser("a@example.com", "A"),
            sampleUser("b@example.com", "B"),
            sampleUser("c@example.com", "C")
        );
        given(userRepository.findAll()).willReturn(users);

        RepublishSignupEventsResult result = service.republishAll();

        assertThat(result.totalUsers()).isEqualTo(3);
        assertThat(result.publishedCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<AuthEvent> captor = ArgumentCaptor.forClass(AuthEvent.class);
        then(eventPublisher).should(times(3)).publish(captor.capture());
        List<AuthEvent> captured = captor.getAllValues();
        assertThat(captured).allSatisfy(ev -> {
            assertThat(ev.payload()).isInstanceOf(UserSignedUp.class);
            assertThat(ev.eventType()).isEqualTo("UserSignedUp");
            assertThat(ev.source()).isEqualTo("auth-service");
        });
    }

    @Test
    @DisplayName("유저 0명이면 totalUsers=0으로 성공 반환")
    void republishAll_emptyUsers() {
        given(userRepository.findAll()).willReturn(List.of());

        RepublishSignupEventsResult result = service.republishAll();

        assertThat(result.totalUsers()).isZero();
        assertThat(result.publishedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일부 publish 실패해도 나머지는 계속 발행되고 failedCount에 반영")
    void republishAll_partialFailure() {
        List<User> users = List.of(
            sampleUser("a@example.com", "A"),
            sampleUser("b@example.com", "B"),
            sampleUser("c@example.com", "C")
        );
        given(userRepository.findAll()).willReturn(users);
        willThrow(new RuntimeException("broker down"))
            .willDoNothing()
            .willThrow(new RuntimeException("broker down"))
            .given(eventPublisher).publish(any(AuthEvent.class));

        RepublishSignupEventsResult result = service.republishAll();

        assertThat(result.totalUsers()).isEqualTo(3);
        assertThat(result.publishedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(2);
        then(eventPublisher).should(times(3)).publish(any(AuthEvent.class));
    }

    @Test
    @DisplayName("100명 시드 재발행 시나리오")
    void republishAll_hundredUsers() {
        List<User> users = IntStream.range(0, 100)
            .mapToObj(i -> sampleUser("u" + i + "@example.com", "U" + i))
            .toList();
        given(userRepository.findAll()).willReturn(users);

        RepublishSignupEventsResult result = service.republishAll();

        assertThat(result.totalUsers()).isEqualTo(100);
        assertThat(result.publishedCount()).isEqualTo(100);
        assertThat(result.failedCount()).isZero();
        then(eventPublisher).should(times(100)).publish(any(AuthEvent.class));
    }

    private User sampleUser(String email, String name) {
        Instant now = Instant.now();
        return User.reconstitute(
            UUID.randomUUID(), email, "hash", name,
            Role.CUSTOMER, null, now, now, true
        );
    }
}
