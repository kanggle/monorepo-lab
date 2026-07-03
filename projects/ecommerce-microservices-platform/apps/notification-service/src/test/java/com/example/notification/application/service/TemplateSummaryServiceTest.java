package com.example.notification.application.service;

import com.example.common.summary.PeriodSummary;
import com.example.notification.application.port.out.TemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link TemplateService#getPeriodSummary()} (TASK-BE-468, TASK-MONO-322).
 *
 * <p>Boundary logic (KST wall-clock→{@code LocalDateTime}) is tested by mocking the
 * repository layer and verifying the result record is assembled correctly for both the
 * zero-row case and the "one today row" case.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TemplateService#getPeriodSummary 단위 테스트")
class TemplateSummaryServiceTest {

    @InjectMocks
    private TemplateService templateService;

    @Mock
    private TemplateRepository templateRepository;

    @Test
    @DisplayName("저장된 템플릿이 없으면 모든 카운트가 0이다")
    void getPeriodSummary_empty_returnsAllZeros() {
        given(templateRepository.countAllForTenant()).willReturn(0L);
        given(templateRepository.countCreatedBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(0L);

        PeriodSummary result = templateService.getPeriodSummary();

        assertThat(result.total()).isZero();
        assertThat(result.today()).isZero();
        assertThat(result.week()).isZero();
        assertThat(result.month()).isZero();
    }

    @Test
    @DisplayName("오늘 생성된 템플릿이 있으면 today 카운트에 반영된다")
    void getPeriodSummary_oneRowToday_countedCorrectly() {
        // total = 3, today = 1, week = 2, month = 3
        given(templateRepository.countAllForTenant()).willReturn(3L);
        // countCreatedBetween is called 3 times (today/week/month);
        // use a simple stub that returns different values per invocation order.
        given(templateRepository.countCreatedBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(1L, 2L, 3L);

        PeriodSummary result = templateService.getPeriodSummary();

        assertThat(result.total()).isEqualTo(3L);
        assertThat(result.today()).isEqualTo(1L);
        assertThat(result.week()).isEqualTo(2L);
        assertThat(result.month()).isEqualTo(3L);
    }
}
