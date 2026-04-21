package com.example.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileStatusTest {

    @Test
    @DisplayName("ProfileStatus 값이 ACTIVE, SUSPENDED, WITHDRAWN 3개 존재한다")
    void values_containsAllStatuses() {
        ProfileStatus[] values = ProfileStatus.values();

        assertThat(values).containsExactlyInAnyOrder(
                ProfileStatus.ACTIVE,
                ProfileStatus.SUSPENDED,
                ProfileStatus.WITHDRAWN
        );
    }

    @Test
    @DisplayName("문자열로부터 ProfileStatus를 변환할 수 있다")
    void valueOf_validString_returnsStatus() {
        assertThat(ProfileStatus.valueOf("ACTIVE")).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(ProfileStatus.valueOf("SUSPENDED")).isEqualTo(ProfileStatus.SUSPENDED);
        assertThat(ProfileStatus.valueOf("WITHDRAWN")).isEqualTo(ProfileStatus.WITHDRAWN);
    }
}
