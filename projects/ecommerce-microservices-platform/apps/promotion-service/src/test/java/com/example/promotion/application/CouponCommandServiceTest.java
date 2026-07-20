package com.example.promotion.application;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.web.exception.AccessDeniedException;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.result.IssueCouponsResult;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponAlreadyUsedException;
import com.example.promotion.domain.coupon.CouponIssueRequest;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.domain.coupon.CouponNotFoundException;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionNotFoundException;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponCommandService 단위 테스트")
class CouponCommandServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionEventPublisher eventPublisher;

    @Mock
    private CouponIssueRequestRepository couponIssueRequestRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    private CouponCommandService createService() {
        // Not every test reaches the replay lookup (e.g. role-guard / missing-key
        // throw earlier) — lenient() so those do not trip STRICT_STUBS.
        lenient().when(couponIssueRequestRepository.find(any(), any())).thenReturn(Optional.empty());
        return new CouponCommandService(
                couponRepository, promotionRepository, eventPublisher, couponIssueRequestRepository, clock);
    }

    @Test
    @DisplayName("쿠폰 발급 시 발급 수가 반환된다")
    void issueCoupons_validCommand_returnsCount() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(promotionRepository.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1", "user-2"), "ECOMMERCE_OPERATOR", "idem-key-1"
        );

        IssueCouponsResult result = service.issueCoupons(command);

        assertThat(result.issuedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("쿠폰 적용 시 할인 금액이 계산된다")
    void applyCoupon_validCoupon_returnsDiscount() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        Coupon coupon = Coupon.issue(promotion.getPromotionId(), "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);

        given(couponRepository.findByIdForUpdate(coupon.getCouponId()))
                .willReturn(Optional.of(coupon));
        given(promotionRepository.findById(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        ApplyCouponCommand command = new ApplyCouponCommand(
                coupon.getCouponId(), "user-1", "order-1", 30000
        );

        ApplyCouponResult result = service.applyCoupon(command);

        assertThat(result.discountAmount()).isEqualTo(5000);
        assertThat(result.finalAmount()).isEqualTo(25000);
        verify(eventPublisher).publishCouponUsed(any());
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 적용 시 예외 발생")
    void applyCoupon_notFound_throws() {
        CouponCommandService service = createService();

        given(couponRepository.findByIdForUpdate("non-existent")).willReturn(Optional.empty());

        ApplyCouponCommand command = new ApplyCouponCommand(
                "non-existent", "user-1", "order-1", 30000
        );

        assertThatThrownBy(() -> service.applyCoupon(command))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    @DisplayName("이미 사용된 쿠폰 적용 시 예외 발생")
    void applyCoupon_alreadyUsed_throws() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        Coupon coupon = Coupon.issue(promotion.getPromotionId(), "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        given(couponRepository.findByIdForUpdate(coupon.getCouponId()))
                .willReturn(Optional.of(coupon));

        ApplyCouponCommand command = new ApplyCouponCommand(
                coupon.getCouponId(), "user-1", "order-2", 30000
        );

        assertThatThrownBy(() -> service.applyCoupon(command))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 프로모션으로 쿠폰 발급 시 예외 발생")
    void issueCoupons_promotionNotFound_throws() {
        CouponCommandService service = createService();

        given(promotionRepository.findByIdForUpdate("non-existent")).willReturn(Optional.empty());

        IssueCouponsCommand command = new IssueCouponsCommand(
                "non-existent", List.of("user-1"), "ECOMMERCE_OPERATOR", "idem-key-2");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(PromotionNotFoundException.class);
    }

    @Test
    @DisplayName("주문 취소 시 USED 쿠폰이 ISSUED 상태로 복원된다")
    void restoreCouponsByOrderId_usedCoupons_restoresToIssued() {
        CouponCommandService service = createService();

        Coupon coupon = Coupon.issue("promo-1", "user-1",
                Instant.parse("2026-04-01T00:00:00Z"), clock);
        coupon.apply("order-1", "user-1", clock);

        given(couponRepository.findByOrderIdAndStatus("order-1", CouponStatus.USED))
                .willReturn(List.of(coupon));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        service.restoreCouponsByOrderId("order-1");

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        verify(couponRepository).save(coupon);
    }

    @Test
    @DisplayName("비관리자 역할로 쿠폰 발급 시 AccessDeniedException")
    void issueCoupons_nonAdminRole_throwsAccessDeniedException() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand("promo-1", List.of("user-1"), "USER");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("주문에 연결된 USED 쿠폰이 없으면 복원을 건너뛴다")
    void restoreCouponsByOrderId_noCouponsFound_skips() {
        CouponCommandService service = createService();

        given(couponRepository.findByOrderIdAndStatus("order-999", CouponStatus.USED))
                .willReturn(List.of());

        assertThatCode(() -> service.restoreCouponsByOrderId("order-999"))
                .doesNotThrowAnyException();

        verify(couponRepository, never()).save(any(Coupon.class));
    }

    // ─── Multi-value X-User-Role (BE-393) ─────────────────────────────────

    @Test
    @DisplayName("멀티롤 헤더에 ECOMMERCE_OPERATOR 포함 시 쿠폰 발급 허용 (multi-domain operator)")
    void issueCoupons_multiRoleHeaderContainingAdmin_admitted() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(promotionRepository.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1"),
                "ECOMMERCE_OPERATOR,ERP_OPERATOR,SCM_OPERATOR", "idem-key-3"
        );

        assertThatCode(() -> service.issueCoupons(command))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("단일 ECOMMERCE_OPERATOR 롤 헤더는 계속 허용 (회귀 방지)")
    void issueCoupons_singleAdminRole_admitted() {
        CouponCommandService service = createService();

        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );

        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(promotionRepository.save(any(Promotion.class))).willAnswer(inv -> inv.getArgument(0));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1"), "ECOMMERCE_OPERATOR", "idem-key-4"
        );

        assertThatCode(() -> service.issueCoupons(command))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ECOMMERCE_OPERATOR 없는 멀티롤 헤더는 거부")
    void issueCoupons_multiRoleWithoutAdmin_throwsAccessDeniedException() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand(
                "promo-1", List.of("user-1"), "SCM_OPERATOR,ERP_OPERATOR"
        );

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("null 헤더는 거부")
    void issueCoupons_nullRole_throwsAccessDeniedException() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand(
                "promo-1", List.of("user-1"), null
        );

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUPERADMIN 서브스트링만 있는 헤더는 거부 (부분일치 방지)")
    void issueCoupons_superadminSubstringOnly_throwsAccessDeniedException() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand(
                "promo-1", List.of("user-1"), "SUPERADMIN"
        );

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── TASK-BE-536: Idempotency-Key guard ─────────────────────────────────────

    @Test
    @DisplayName("AC-0/F4 Idempotency-Key 없으면 IdempotencyKeyRequiredException, 쿠폰 미발급")
    void issueCoupons_missingIdempotencyKey_isRefused_noCouponIssued() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand(
                "promo-1", List.of("user-1"), "ECOMMERCE_OPERATOR", null);

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(com.example.promotion.application.exception.IdempotencyKeyRequiredException.class);

        verify(couponRepository, never()).saveAll(any());
        verify(promotionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("AC-0/F4 blank Idempotency-Key 도 거부된다 (webhook-store 널 구멍을 복사하지 않는다)")
    void issueCoupons_blankIdempotencyKey_isRefused() {
        CouponCommandService service = createService();

        IssueCouponsCommand command = new IssueCouponsCommand(
                "promo-1", List.of("user-1"), "ECOMMERCE_OPERATOR", "   ");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(com.example.promotion.application.exception.IdempotencyKeyRequiredException.class);
    }

    @Test
    @DisplayName("AC-1 같은 키 + 같은 사용자 배치 재생 → 기존 발급수 반환, 재발급 없음")
    void issueCoupons_sameKeySameUserBatch_isReplay_noSecondIssuance() {
        CouponCommandService service = createService();
        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );
        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponIssueRequestRepository.find(promotion.getPromotionId(), "idem-A"))
                .willReturn(Optional.of(CouponIssueRequest.of(
                        promotion.getPromotionId(), "idem-A", List.of("user-1", "user-2"), 2, Instant.now())));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1", "user-2"), "ECOMMERCE_OPERATOR", "idem-A");

        IssueCouponsResult result = service.issueCoupons(command);

        assertThat(result.issuedCount()).isEqualTo(2);
        verify(couponRepository, never()).saveAll(any());
        verify(promotionRepository, never()).save(any());
        verify(couponIssueRequestRepository, never()).insert(any());
    }

    @Test
    @DisplayName("같은 키 + 다른 사용자 배치 재사용 → IdempotencyKeyConflictException, 쿠폰 미발급")
    void issueCoupons_sameKeyDifferentUserBatch_isConflict() {
        CouponCommandService service = createService();
        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );
        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponIssueRequestRepository.find(promotion.getPromotionId(), "idem-A"))
                .willReturn(Optional.of(CouponIssueRequest.of(
                        promotion.getPromotionId(), "idem-A", List.of("user-1"), 1, Instant.now())));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-2"), "ECOMMERCE_OPERATOR", "idem-A");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(com.example.promotion.application.exception.IdempotencyKeyConflictException.class);

        verify(couponRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AC-4 동시성 — claim insert 가 유니크 제약 위반 → IdempotencyKeyConflictException, 쿠폰 미발급")
    void issueCoupons_concurrentDuplicate_claimInsertLosesRace_isConflict() {
        CouponCommandService service = createService();
        Promotion promotion = Promotion.create(
                "프로모션", "설명", DiscountType.FIXED, 1000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );
        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponIssueRequestRepository.insert(any()))
                .willThrow(new DataIntegrityViolationException("uq_coupon_issue_request_key"));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1"), "ECOMMERCE_OPERATOR", "idem-B");

        assertThatThrownBy(() -> service.issueCoupons(command))
                .isInstanceOf(com.example.promotion.application.exception.IdempotencyKeyConflictException.class);

        verify(couponRepository, never()).saveAll(any());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Edge Case 쿠폰 발급 한도 상호작용 — 재생은 한도까지 발급이 아니라 아무것도 발급하지 않는다")
    void issueCoupons_replayComposesWithCap_issuesNothingNotUpToCap() {
        CouponCommandService service = createService();
        // maxIssuanceCount == 1, already fully issued.
        Promotion promotion = Promotion.create(
                "한도 프로모션", "설명", DiscountType.FIXED, 1000, 0, 1,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"), clock
        );
        promotion.incrementIssuedCount(1);
        given(promotionRepository.findByIdForUpdate(promotion.getPromotionId()))
                .willReturn(Optional.of(promotion));
        given(couponIssueRequestRepository.find(promotion.getPromotionId(), "idem-cap"))
                .willReturn(Optional.of(CouponIssueRequest.of(
                        promotion.getPromotionId(), "idem-cap", List.of("user-1"), 1, Instant.now())));

        IssueCouponsCommand command = new IssueCouponsCommand(
                promotion.getPromotionId(), List.of("user-1"), "ECOMMERCE_OPERATOR", "idem-cap");

        // The replay must succeed (no re-validation against the now-exhausted cap)
        // and must not mint a second coupon.
        IssueCouponsResult result = service.issueCoupons(command);

        assertThat(result.issuedCount()).isEqualTo(1);
        verify(couponRepository, never()).saveAll(any());
    }
}
