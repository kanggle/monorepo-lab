package com.example.shipping.application.service;

import com.example.web.exception.AccessDeniedException;
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

    private void validateAdminRole(String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
