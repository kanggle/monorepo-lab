package com.example.notification.application.service;

import com.example.notification.application.command.UpdatePreferenceCommand;
import com.example.notification.application.port.out.PreferenceRepository;
import com.example.notification.application.result.GetPreferenceResult;
import com.example.notification.domain.model.UserNotificationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreferenceService 단위 테스트")
class PreferenceServiceTest {

    @InjectMocks
    private PreferenceService preferenceService;

    @Mock
    private PreferenceRepository preferenceRepository;

    @Test
    @DisplayName("사용자 설정이 없으면 기본 설정을 생성하여 GetPreferenceResult로 반환한다")
    void getPreference_notFound_createsDefault() {
        given(preferenceRepository.findByUserId("user-1")).willReturn(Optional.empty());
        UserNotificationPreference defaultPref = UserNotificationPreference.createDefault("user-1");
        given(preferenceRepository.save(any())).willReturn(defaultPref);

        GetPreferenceResult result = preferenceService.getPreference("user-1");

        assertThat(result.emailEnabled()).isTrue();
        assertThat(result.smsEnabled()).isFalse();
        assertThat(result.pushEnabled()).isTrue();
    }

    @Test
    @DisplayName("사용자 설정을 업데이트하고 GetPreferenceResult로 반환한다")
    void updatePreference_success() {
        UserNotificationPreference existing = UserNotificationPreference.createDefault("user-1");
        given(preferenceRepository.findByUserId("user-1")).willReturn(Optional.of(existing));
        given(preferenceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        UpdatePreferenceCommand command = new UpdatePreferenceCommand("user-1", false, true, false);

        GetPreferenceResult result = preferenceService.updatePreference(command);

        assertThat(result.emailEnabled()).isFalse();
        assertThat(result.smsEnabled()).isTrue();
        assertThat(result.pushEnabled()).isFalse();
    }
}
