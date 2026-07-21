package com.example.gateway.security;

import org.springframework.stereotype.Service;

@Service
public class RouteService {

    public String resolveTargetService(String path) {
        if (path.startsWith("/api/users") || path.startsWith("/api/admin/users")) return "user-service";
        if (path.startsWith("/api/products") || path.startsWith("/api/admin/products")) return "product-service";
        if (path.startsWith("/api/search")) return "search-service";
        if (path.startsWith("/api/orders")) return "order-service";
        if (path.startsWith("/api/payments")) return "payment-service";
        if (path.startsWith("/api/shippings")) return "shipping-service";
        if (path.startsWith("/api/reviews")) return "review-service";
        if (path.startsWith("/api/promotions") || path.startsWith("/api/coupons")) return "promotion-service";
        if (path.startsWith("/api/notifications")) return "notification-service";
        if (path.startsWith("/api/wishlists")) return "user-service";
        return "unknown";
    }
}
