package com.example.promotion.application;

import com.example.promotion.application.command.CreatePromotionCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.promotion.application.command.UpdatePromotionCommand;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.application.service.PromotionCommandService;
import com.example.promotion.application.service.PromotionQueryService;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.domain.coupon.CouponReleaseRepository;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.promotion.PromotionRepository;
import com.example.web.exception.AccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions 2026-10-16 UTC) — promotion-service re-checks the E-commerce
 * business role on its seven operator operations (3 reads · 4 writes on {@code /api/promotions/**}). The check lives
 * in the application services (where the old {@code ECOMMERCE_OPERATOR} guard was), so this exercises them directly —
 * the same answer for a call that came through the gateway and one that did not.
 *
 * <p>Decision (ecommerce iam-integration.md § 업무 역할): reads = {@code ECOMMERCE_PROMOTION_READ ∨
 * ECOMMERCE_PROMOTION_WRITE}; create · update · delete · coupon issue = {@code ECOMMERCE_PROMOTION_WRITE}. The entry
 * role alone, {@code CUSTOMER}, a blank or absent header → denied. «Allowed» = anything but
 * {@link AccessDeniedException} (the repositories are mocks).
 */
@DisplayName("TASK-MONO-795 promotion 업무 역할 게이트 (운영자 연산 7 전수)")
class PromotionBusinessRoleGateTest {

    private static final String ENTRY = "ECOMMERCE_OPERATOR";

    private record Op(String name, boolean write, Consumer<String> call) {}

    private static List<Op> ops() {
        PromotionRepository promotions = mock(PromotionRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        Clock clock = Clock.systemUTC();
        PromotionQueryService query = new PromotionQueryService(promotions, clock);
        PromotionCommandService command = new PromotionCommandService(promotions, coupons, clock);
        CouponCommandService couponCommand = new CouponCommandService(coupons, promotions,
                mock(PromotionEventPublisher.class), mock(CouponIssueRequestRepository.class),
                mock(CouponReleaseRepository.class), clock);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-31T00:00:00Z");
        return List.of(
                new Op("get", false, role -> query.getPromotion("p-1", role)),
                new Op("list", false, role -> query.getPromotions(0, 20, null, role)),
                new Op("summary", false, query::getPeriodSummary),
                new Op("create", true, role -> command.createPromotion(new CreatePromotionCommand(
                        "n", "d", "FIXED", 1000, 0, 10, start, end, role))),
                new Op("update", true, role -> command.updatePromotion(new UpdatePromotionCommand(
                        "p-1", "n", "d", "FIXED", 1000, 0, 10, start, end, role))),
                new Op("delete", true, role -> command.deletePromotion("p-1", role)),
                new Op("coupons.issue", true, role -> couponCommand.issueCoupons(
                        new IssueCouponsCommand("p-1", List.of("u-1"), role, "idem-1"))));
    }

    static Stream<Arguments> personas() {
        return Stream.of(
                Arguments.of("promotion read set", ENTRY + ",ECOMMERCE_PROMOTION_READ", true, false),
                Arguments.of("promotion operate set", ENTRY + ",ECOMMERCE_PROMOTION_READ,ECOMMERCE_PROMOTION_WRITE", true, true),
                Arguments.of("write role alone still reads", ENTRY + ",ECOMMERCE_PROMOTION_WRITE", true, true),
                Arguments.of("🔴 shipping operate set only (another area)", ENTRY + ",ECOMMERCE_SHIPPING_READ,ECOMMERCE_SHIPPING_WRITE", false, false),
                Arguments.of("🔴 entry role alone", ENTRY, false, false),
                Arguments.of("CUSTOMER", "CUSTOMER", false, false),
                Arguments.of("blank", "", false, false),
                Arguments.of("absent (null)", null, false, false),
                Arguments.of("lower-case role (comparison ignores case)", "ecommerce_promotion_write", true, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("personas")
    @DisplayName("each persona × the seven promotion operator operations")
    void gate(String persona, String roleHeader, boolean mayRead, boolean mayWrite) {
        List<String> wrong = new ArrayList<>();
        for (Op op : ops()) {
            boolean denied;
            try {
                op.call().accept(roleHeader);
                denied = false;
            } catch (AccessDeniedException e) {
                denied = true;
            } catch (RuntimeException other) {
                denied = false; // past the gate — the mock repositories answered something the code did not like
            }
            boolean expectAllowed = op.write() ? mayWrite : mayRead;
            if (expectAllowed == denied) {
                wrong.add(op.name() + " → " + (denied ? "denied" : "allowed"));
            }
        }
        assertThat(wrong).as(persona).isEmpty();
    }
}
