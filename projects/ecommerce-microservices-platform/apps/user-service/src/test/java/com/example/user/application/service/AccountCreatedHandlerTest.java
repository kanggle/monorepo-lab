package com.example.user.application.service;

import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountCreatedHandler 단위 테스트 (ADR-MONO-037 P1 minimal profile)")
class AccountCreatedHandlerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private AccountCreatedHandler handler;

    @Test
    @DisplayName("프로필이 없으면 email/name 없는 최소 프로필을 생성한다")
    void handle_noExistingProfile_createsMinimal() {
        UUID accountId = UUID.randomUUID();
        given(userProfileRepository.existsByUserId(accountId)).willReturn(false);

        handler.handle(accountId);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        UserProfile saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(accountId);
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getName()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ProfileStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 프로필이 존재하면 중복 생성하지 않는다 (멱등 — 재전달 안전)")
    void handle_existingProfile_skips() {
        UUID accountId = UUID.randomUUID();
        given(userProfileRepository.existsByUserId(accountId)).willReturn(true);

        handler.handle(accountId);

        then(userProfileRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
