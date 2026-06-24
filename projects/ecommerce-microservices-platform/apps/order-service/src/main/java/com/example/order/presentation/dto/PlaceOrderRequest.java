package com.example.order.presentation.dto;

import com.example.order.application.dto.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record PlaceOrderRequest(
        @NotEmpty(message = "Order items must not be empty")
        @Valid
        List<OrderItemRequest> items,

        @NotNull(message = "Shipping address is required")
        @Valid
        ShippingAddressRequest shippingAddress
) {
    public PlaceOrderCommand toCommand(String userId) {
        return toCommand(userId, null);
    }

    public PlaceOrderCommand toCommand(String userId, String idempotencyKey) {
        List<PlaceOrderCommand.OrderItemCommand> itemCommands = items.stream()
                .map(i -> new PlaceOrderCommand.OrderItemCommand(
                        i.productId(), i.variantId(), i.productName(), i.optionName(),
                        i.quantity(), i.unitPrice(), i.sellerId()))
                .toList();
        PlaceOrderCommand.ShippingAddressCommand addrCommand = new PlaceOrderCommand.ShippingAddressCommand(
                shippingAddress.recipient(), shippingAddress.phone(), shippingAddress.zipCode(),
                shippingAddress.address1(), shippingAddress.address2());
        return new PlaceOrderCommand(userId, itemCommands, addrCommand, idempotencyKey);
    }

    public record OrderItemRequest(
            @NotNull(message = "productId is required")
            String productId,

            @NotNull(message = "variantId is required")
            String variantId,

            @NotNull(message = "productName is required")
            String productName,

            String optionName,

            @Positive(message = "quantity must be at least 1")
            int quantity,

            @Positive(message = "unitPrice must be at least 1")
            long unitPrice,

            // Optional owning seller of this line (ADR-MONO-030 §3.2). The client
            // supplies it exactly like productName/unitPrice; absent → default seller.
            String sellerId
    ) {}

    public record ShippingAddressRequest(
            @NotNull(message = "recipient is required")
            String recipient,

            @NotNull(message = "phone is required")
            String phone,

            @NotNull(message = "zipCode is required")
            String zipCode,

            @NotNull(message = "address1 is required")
            String address1,

            String address2
    ) {}
}
