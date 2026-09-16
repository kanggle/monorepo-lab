package com.example.promotion.application;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.result.ApplyCouponResult;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.domain.coupon.CouponPlacementReleasedException;
import com.example.promotion.domain.coupon.CouponRelease;
import com.example.promotion.domain.coupon.CouponReleaseRepository;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-INT-027 — the release and the {@code apply} it compensates are not ordered.
 *
 * <p>order-service registers its release BEFORE calling apply, and sends it when the placement
 * does not commit. The release is sent <em>because</em> the apply timed out on the caller, which
 * is exactly when that apply may still be executing here. AC-0 measured what happens then: the
 * release finds the coupon still {@code ISSUED}, does nothing, and the late apply binds the coupon
 * to an order that was never saved.
 *
 * <p><b>The fence store is a fake, not a mock, on purpose.</b> A mock has no memory: the release
 * would call {@code record} and the apply's {@code existsFor} would still answer {@code false}
 * unless the test fed it the answer — which would prove only that the stub returns what it was
 * told. {@link InMemoryCouponReleaseRepository} behaves like the table instead, <b>including its
 * {@code UNIQUE (coupon_id, order_id)}</b>, so removing the {@code existsFor} check from either
 * side of the production code makes a test here fail rather than quietly pass.
 *
 * <p>Still a unit lane: it pins the ordering logic. Whether Flyway V9 applied and the constraint
 * is real is {@code CouponReleaseFenceIntegrationTest}'s job, on a real Postgres.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponCommandService — 되돌릴 것이 없던 release 뒤에 도착한 apply (TASK-INT-027)")
class CouponApplyAfterReleaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionEventPublisher eventPublisher;

    @Mock
    private CouponIssueRequestRepository couponIssueRequestRepository;

    private final InMemoryCouponReleaseRepository couponReleaseRepository =
            new InMemoryCouponReleaseRepository();

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-28T12:00:00Z"), ZoneOffset.UTC);

    private CouponCommandService service() {
        return new CouponCommandService(couponRepository, promotionRepository, eventPublisher,
                couponIssueRequestRepository, couponReleaseRepository, clock);
    }

    private Promotion fixed5000() {
        return Promotion.create("프로모션", "설명", DiscountType.FIXED, 5000, 0, 100,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"), clock);
    }

    private Coupon issuedCoupon(String promotionId) {
        return Coupon.issue(promotionId, "user-1", Instant.parse("2026-04-01T00:00:00Z"), clock);
    }

    @Test
    @DisplayName("🔴 AC-0 재현 — 주문이 커밋되지 않아 release 가 먼저 갔는데, 늦게 커밋된 apply 가 없는 주문에 쿠폰을 묶는다")
    void applyArrivingAfterTheReleaseOfAnUncommittedPlacement_mustNotUseTheCoupon() {
        Promotion promotion = fixed5000();
        Coupon coupon = issuedCoupon(promotion.getPromotionId());
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));
        // lenient: once the fence is in place, apply refuses before it ever needs the promotion.
        lenient().when(promotionRepository.findById(promotion.getPromotionId()))
                .thenReturn(Optional.of(promotion));

        // ① The placement transaction did not commit, so its release arrives first. The coupon is
        //    still ISSUED — there is nothing to revert.
        service().releaseCoupon(coupon.getCouponId(), "order-1");
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);

        // ② The apply that timed out on the caller now commits here. "order-1" was never saved.
        assertThatThrownBy(() -> service().applyCoupon(
                new ApplyCouponCommand(coupon.getCouponId(), "user-1", "order-1", 30000)))
                .isInstanceOf(CouponPlacementReleasedException.class);

        // A dead order must not hold a coupon: nothing can free it later — the cancellation
        // restore path needs an order to fire, and there is none. It would sit USED until expiry.
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getOrderId()).isNull();
        verify(eventPublisher, never()).publishCouponUsed(any());
    }

    @Test
    @DisplayName("펜스는 쌍 단위다 — 실패한 주문이 사용자의 쿠폰을 빼앗지 않는다")
    void theFenceIsScopedToThePair_soAnotherOrderCanStillUseTheCoupon() {
        Promotion promotion = fixed5000();
        Coupon coupon = issuedCoupon(promotion.getPromotionId());
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));
        given(promotionRepository.findById(promotion.getPromotionId())).willReturn(Optional.of(promotion));

        // order-1 died and was fenced; the user simply orders again.
        service().releaseCoupon(coupon.getCouponId(), "order-1");
        ApplyCouponResult result = service().applyCoupon(
                new ApplyCouponCommand(coupon.getCouponId(), "user-1", "order-2", 30000));

        assertThat(result.discountAmount()).isEqualTo(5000);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(coupon.getOrderId()).isEqualTo("order-2");
    }

    @Test
    @DisplayName("되돌릴 것이 없는 release 가 재시도로 두 번 와도 기록은 하나다")
    void aRetriedReleaseRecordsTheFenceOnce() {
        Coupon coupon = issuedCoupon("promo-1");
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));

        service().releaseCoupon(coupon.getCouponId(), "order-1");
        // The fake enforces UNIQUE (coupon_id, order_id): a second insert would throw, so this
        // passing means the service asked before inserting.
        assertThatCode(() -> service().releaseCoupon(coupon.getCouponId(), "order-1"))
                .doesNotThrowAnyException();

        assertThat(couponReleaseRepository.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("apply 가 먼저 커밋됐으면 release 는 되돌리기만 하고 펜스를 남기지 않는다")
    void aReleaseThatFindsItsOwnOrderRevertsTheCouponAndLeavesNoFence() {
        Coupon coupon = issuedCoupon("promo-1");
        coupon.apply("order-1", "user-1", clock);
        given(couponRepository.findByIdForUpdate(coupon.getCouponId())).willReturn(Optional.of(coupon));
        given(couponRepository.save(any(Coupon.class))).willAnswer(inv -> inv.getArgument(0));

        service().releaseCoupon(coupon.getCouponId(), "order-1");

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getOrderId()).isNull();
        // Nothing to fence: the compensation did its job the ordinary way.
        assertThat(couponReleaseRepository.size()).isZero();
    }

    /** Behaves like the {@code coupon_release} table, constraint included. */
    private static final class InMemoryCouponReleaseRepository implements CouponReleaseRepository {

        private final List<CouponRelease> rows = new ArrayList<>();

        @Override
        public boolean existsFor(String couponId, String orderId) {
            return rows.stream().anyMatch(
                    r -> r.getCouponId().equals(couponId) && r.getOrderId().equals(orderId));
        }

        @Override
        public CouponRelease record(CouponRelease release) {
            if (existsFor(release.getCouponId(), release.getOrderId())) {
                throw new IllegalStateException(
                        "uq_coupon_release_pair violated: " + release.getCouponId()
                                + " / " + release.getOrderId());
            }
            rows.add(release);
            return release;
        }

        int size() {
            return rows.size();
        }
    }
}
