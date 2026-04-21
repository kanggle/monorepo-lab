package com.example.user.application.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserSignedUpHandlerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserSignedUpHandler userSignedUpHandler;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logbackLogger;

    @BeforeEach
    void setUpLogCapture() {
        logbackLogger = (Logger) LoggerFactory.getLogger(UserSignedUpHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logbackLogger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("새로운 사용자의 UserSignedUp 이벤트를 처리하여 프로필을 생성한다")
    void handle_newUser_createsProfile() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String name = "홍길동";
        given(userProfileRepository.existsByUserId(userId)).willReturn(false);
        given(userProfileRepository.save(org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        userSignedUpHandler.handle(userId, email, name);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        UserProfile saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail().value()).isEqualTo(email);
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getStatus()).isEqualTo(ProfileStatus.ACTIVE);
    }

    @Test
    @DisplayName("프로필 생성 로그에 이메일이 포함되지 않는다")
    void handle_newUser_logDoesNotContainEmail() {
        UUID userId = UUID.randomUUID();
        String email = "sensitive@example.com";
        given(userProfileRepository.existsByUserId(userId)).willReturn(false);
        given(userProfileRepository.save(org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        userSignedUpHandler.handle(userId, email, "홍길동");

        assertThat(listAppender.list)
            .filteredOn(e -> e.getFormattedMessage().contains("Created UserProfile"))
            .isNotEmpty()
            .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(email));
    }

    @Test
    @DisplayName("이미 존재하는 사용자의 이벤트는 무시한다 (멱등성)")
    void handle_existingUser_skipsCreation() {
        UUID userId = UUID.randomUUID();
        given(userProfileRepository.existsByUserId(userId)).willReturn(true);

        userSignedUpHandler.handle(userId, "test@example.com", "홍길동");

        then(userProfileRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
