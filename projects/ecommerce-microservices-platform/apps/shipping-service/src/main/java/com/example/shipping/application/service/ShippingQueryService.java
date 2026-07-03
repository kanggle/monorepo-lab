package com.example.shipping.application.service;

import com.example.web.exception.AccessDeniedException;
import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
import com.example.shipping.application.result.ShippingResult;
import com.example.shipping.application.result.ShippingSummary;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingQueryService {

    private final ShippingRepository shippingRepository;

    public ShippingResult getShippingByOrderId(String orderId, String userId) {
        Shipping shipping = shippingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ShippingNotFoundException(orderId));

        if (!shipping.getUserId().equals(userId)) {
            throw new com.example.shipping.application.exception.UnauthorizedShippingAccessException(
                    "User does not have access to this shipping record");
        }

        return ShippingResult.from(shipping);
    }

    public PageResult<ShippingSummary> listShippings(String userRole, ShippingStatus status, PageQuery pageQuery) {
        validateAdminRole(userRole);
        PageResult<Shipping> pageResult;
        if (status != null) {
            pageResult = shippingRepository.findByStatus(status, pageQuery);
        } else {
            pageResult = shippingRepository.findAll(pageQuery);
        }

        return new PageResult<>(
                pageResult.content().stream().map(ShippingSummary::from).toList(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages()
        );
    }

    public PeriodSummary getPeriodSummary(String userRole) {
        validateAdminRole(userRole);

        KstPeriodBounds b = KstPeriodBounds.now();

        long total = shippingRepository.countAllForTenant();
        long today = shippingRepository.countCreatedBetween(b.todayStartInstant(), b.nowInstant());
        long week = shippingRepository.countCreatedBetween(b.weekStartInstant(), b.nowInstant());
        long month = shippingRepository.countCreatedBetween(b.monthStartInstant(), b.nowInstant());

        return new PeriodSummary(today, week, month, total);
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if ("ADMIN".equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
