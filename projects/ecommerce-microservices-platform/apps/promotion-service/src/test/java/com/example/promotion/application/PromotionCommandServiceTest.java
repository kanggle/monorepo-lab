package com.example.promotion.application;

import com.example.promotion.application.command.CreatePromotionCommand;
import com.example.promotion.application.command.UpdatePromotionCommand;
import com.example.web.exception.AccessDeniedException;
import com.example.promotion.application.result.CreatePromotionResult;
import com.example.promotion.application.result.UpdatePromotionResult;
import com.example.promotion.application.service.PromotionCommandService;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionHasIssuedCouponsException;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionCommandService 단위 테스트")
class PromotionCommandServiceTest {

    @InjectMocks
    private PromotionCommandService promotionCommandService;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private CouponRepository couponRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("프로모션 생성 시 ID가 반환된다")
    void createPromotion_validCommand_returnsId() {
        // InjectMocks에서 clock이 주입되지 않으므로 직접 생성자 호출
        PromotionCommandService service = new PromotionCommandService(
                promotionRepository, couponRepository, clock);

        given(promotionRepository.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        CreatePromotionCommand command = new CreatePromotionCommand(
                "테스트 프로모션", "설명", "FIXED", 5000, 10000, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                "ADMIN"
        );

        CreatePromotionResult result = service.createPromotion(command);

        assertThat(result.promotionId()).isNotBlank();
        verify(promotionRepository).save(any(Promotion.class));
    }

    @Test
    @DisplayName("존재하지 않는 프로모션 수정 시 예외 발생")
    void updatePromotion_notFound_throws() {
        PromotionCommandService service = new PromotionCommandService(
                promotionRepository, couponRepository, clock);

        given(promotionRepository.findById("non-existent")).willReturn(Optional.empty());

        UpdatePromotionCommand command = new UpdatePromotionCommand(
                "non-existent", "수정", "설명", "FIXED", 5000, 10000, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                "ADMIN"
        );

        assertThatThrownBy(() -> service.updatePromotion(command))
                .isInstanceOf(PromotionNotFoundException.class);
    }

    @Test
    @DisplayName("발급된 쿠폰이 있는 프로모션 삭제 시 예외 발생")
    void deletePromotion_hasIssuedCoupons_throws() {
        PromotionCommandService service = new PromotionCommandService(
                promotionRepository, couponRepository, clock);

        Promotion promotion = Promotion.create(
                "프로모션", "설명", com.example.promotion.domain.promotion.DiscountType.FIXED,
                1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );
        promotion.incrementIssuedCount(5);

        given(promotionRepository.findById(promotion.getPromotionId())).willReturn(Optional.of(promotion));

        assertThatThrownBy(() -> service.deletePromotion(promotion.getPromotionId(), "ADMIN"))
                .isInstanceOf(PromotionHasIssuedCouponsException.class);
    }

    @Test
    @DisplayName("존재하지 않는 프로모션 삭제 시 예외 발생")
    void deletePromotion_notFound_throws() {
        PromotionCommandService service = new PromotionCommandService(
                promotionRepository, couponRepository, clock);

        given(promotionRepository.findById("non-existent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePromotion("non-existent", "ADMIN"))
                .isInstanceOf(PromotionNotFoundException.class);
    }

    @Test
    @DisplayName("비관리자 역할로 프로모션 생성 시 AccessDeniedException")
    void createPromotion_nonAdminRole_throwsAccessDeniedException() {
        PromotionCommandService service = new PromotionCommandService(
                promotionRepository, couponRepository, clock);

        CreatePromotionCommand command = new CreatePromotionCommand(
                "프로모션", "설명", "FIXED", 5000, 10000, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                "USER"
        );

        assertThatThrownBy(() -> service.createPromotion(command))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("비관리자 역할로 프로모션 삭제 시 AccessDeniedException")
    void deletePromotion_nonAdminRole_throwsAccessDeniedException() {
        PromotionCommandService service = new PromotionCommandService(
                promotionRepository, couponRepository, clock);

        assertThatThrownBy(() -> service.deletePromotion("promo-1", "USER"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
