package com.example.settlement.application.service;

import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.repository.CommissionRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionRateResolverTest {

    @Mock
    private CommissionRateRepository rateRepository;
    private CommissionRateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CommissionRateResolver(rateRepository);
        ReflectionTestUtils.setField(resolver, "defaultRateBps", 500);
    }

    @Test
    void uses_seller_override_when_present() {
        when(rateRepository.findRateBps("tenantA", "seller-1")).thenReturn(Optional.of(1200));

        CommissionRate rate = resolver.resolve("tenantA", "seller-1");

        assertThat(rate.rateBps()).isEqualTo(1200);
        assertThat(rate.source()).isEqualTo(CommissionRate.Source.SELLER_OVERRIDE);
    }

    @Test
    void falls_back_to_platform_default_when_absent() {
        when(rateRepository.findRateBps("tenantA", "seller-2")).thenReturn(Optional.empty());

        CommissionRate rate = resolver.resolve("tenantA", "seller-2");

        assertThat(rate.rateBps()).isEqualTo(500);
        assertThat(rate.source()).isEqualTo(CommissionRate.Source.PLATFORM_DEFAULT);
    }

    @Test
    void default_zero_is_net_zero_degrade() {
        ReflectionTestUtils.setField(resolver, "defaultRateBps", 0);
        when(rateRepository.findRateBps("tenantA", "default")).thenReturn(Optional.empty());

        CommissionRate rate = resolver.resolve("tenantA", "default");

        assertThat(rate.rateBps()).isZero();
    }
}
