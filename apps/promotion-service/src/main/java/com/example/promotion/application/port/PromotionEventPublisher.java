package com.example.promotion.application.port;

import com.example.promotion.application.event.CouponUsedEvent;
import com.example.promotion.application.event.CouponExpiredEvent;

public interface PromotionEventPublisher {

    void publishCouponUsed(CouponUsedEvent event);

    void publishCouponExpired(CouponExpiredEvent event);
}
