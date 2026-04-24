package com.example.shipping.application.service;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.command.UpdateShippingStatusCommand;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingCommandService {

    private final ShippingRepository shippingRepository;
    private final ShippingEventPublisher shippingEventPublisher;
    private final Clock clock;

    @Transactional
    public void createShipping(CreateShippingCommand command) {
        if (shippingRepository.existsByOrderId(command.orderId())) {
            log.info("Shipping already exists for orderId={}, skipping (idempotent)", command.orderId());
            return;
        }

        Shipping shipping = Shipping.create(command.orderId(), command.userId(), clock);
        shippingRepository.save(shipping);
        log.info("Shipping created: shippingId={}, orderId={}", shipping.getShippingId(), command.orderId());
    }

    @Transactional
    public UpdateShippingStatusResult updateStatus(UpdateShippingStatusCommand command) {
        validateAdminRole(command.userRole());
        Shipping shipping = shippingRepository.findById(command.shippingId())
                .orElseThrow(() -> new ShippingNotFoundException(command.shippingId()));

        ShippingStatus previousStatus = shipping.transitionTo(
                command.status(), command.trackingNumber(), command.carrier(), clock);

        Shipping saved = shippingRepository.save(shipping);

        shippingEventPublisher.publishShippingStatusChanged(
                saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                previousStatus, saved.getStatus(),
                saved.getTrackingNumber(), saved.getCarrier());

        log.info("Shipping status updated: shippingId={}, {} -> {}",
                saved.getShippingId(), previousStatus, saved.getStatus());

        return new UpdateShippingStatusResult(
                saved.getShippingId(), saved.getStatus(), saved.getUpdatedAt());
    }

    private void validateAdminRole(String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
