package com.example.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserNotificationPreference 도메인 모델 단위 테스트")
class UserNotificationPreferenceTest {

    @Test
    @DisplayName("기본 설정 생성 시 email=true, sms=false, push=true")
    void createDefault_setsDefaultValues() {
        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1");

        assertThat(pref.getUserId()).isEqualTo("user-1");
        assertThat(pref.isEmailEnabled()).isTrue();
        assertThat(pref.isSmsEnabled()).isFalse();
        assertThat(pref.isPushEnabled()).isTrue();
    }

    @Test
    @DisplayName("isChannelEnabled가 채널별 설정을 올바르게 반환한다")
    void isChannelEnabled_returnsCorrectValue() {
        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1");

        assertThat(pref.isChannelEnabled(NotificationChannel.EMAIL)).isTrue();
        assertThat(pref.isChannelEnabled(NotificationChannel.SMS)).isFalse();
        assertThat(pref.isChannelEnabled(NotificationChannel.PUSH)).isTrue();
    }

    @Test
    @DisplayName("update가 설정을 올바르게 변경한다")
    void update_changesPreferences() {
        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1");

        pref.update(false, true, false);

        assertThat(pref.isEmailEnabled()).isFalse();
        assertThat(pref.isSmsEnabled()).isTrue();
        assertThat(pref.isPushEnabled()).isFalse();
    }
}
