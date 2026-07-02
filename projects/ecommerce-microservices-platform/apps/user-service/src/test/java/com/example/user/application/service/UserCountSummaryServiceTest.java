package com.example.user.application.service;

import com.example.user.application.result.UserCountSummaryResult;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService.getCountSummary 단위 테스트")
class UserCountSummaryServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private UserProfileService userProfileService;

    @Nested
    @DisplayName("getCountSummary")
    class GetCountSummary {

        @Test
        @DisplayName("사용자가 없으면 모든 카운트가 0이다")
        void getCountSummary_noUsers_returnsZeros() {
            given(userProfileRepository.countForTenant()).willReturn(0L);
            given(userProfileRepository.countForTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                    .willReturn(0L);

            UserCountSummaryResult result = userProfileService.getCountSummary();

            assertThat(result.total()).isZero();
            assertThat(result.today()).isZero();
            assertThat(result.week()).isZero();
            assertThat(result.month()).isZero();
        }

        @Test
        @DisplayName("total=5이고 오늘 가입한 사용자가 2명이면 today=2로 집계된다")
        void getCountSummary_todayUsers_countedCorrectly() {
            given(userProfileRepository.countForTenant()).willReturn(5L);
            // first call = today, second = week, third = month
            given(userProfileRepository.countForTenantCreatedBetween(any(Instant.class), any(Instant.class)))
                    .willReturn(2L, 3L, 4L);

            UserCountSummaryResult result = userProfileService.getCountSummary();

            assertThat(result.total()).isEqualTo(5L);
            assertThat(result.today()).isEqualTo(2L);
            assertThat(result.week()).isEqualTo(3L);
            assertThat(result.month()).isEqualTo(4L);
        }
    }
}
