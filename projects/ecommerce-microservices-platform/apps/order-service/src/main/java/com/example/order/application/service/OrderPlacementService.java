package com.example.order.application.service;

import com.example.order.application.dto.PlaceOrderCommand;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
        List<Order.OrderItemData> itemDataList = toItemDataList(command.items());
        ShippingAddress shippingAddress = toShippingAddress(command.shippingAddress());

        Order order = Order.create(command.userId(), itemDataList, shippingAddress, clock);
        orderRepository.save(order);

        log.info("Order placed: orderId={}, userId={}", order.getOrderId(), order.getUserId());
        orderMetrics.recordOrderPlaced();
        orderMetrics.recordOrderAmount(order.getTotalPrice());

        orderEventPublisher.publishOrderPlaced(
                buildOrderPlacedEvent(order, command.shippingAddress()));

        return new PlaceOrderResult(order.getOrderId());
    }

    private List<Order.OrderItemData> toItemDataList(List<PlaceOrderCommand.OrderItemCommand> items) {
        // Capture each line's seller (denormalized snapshot at placement, §C). Absent
        // → default seller is applied by OrderItem (degrade). A single order may span
        // multiple sellers — each line is attributed independently.
        return items.stream()
                .map(i -> new Order.OrderItemData(
                        i.productId(), i.variantId(),
                        i.productName(), i.optionName(),
                        i.quantity(), i.unitPrice(),
                        i.sellerId()
                ))
                .toList();
    }

    private ShippingAddress toShippingAddress(PlaceOrderCommand.ShippingAddressCommand addr) {
        return new ShippingAddress(
                addr.recipient(), addr.phone(), addr.zipCode(),
                addr.address1(), addr.address2()
        );
    }

    private OrderPlacedEvent buildOrderPlacedEvent(Order order, PlaceOrderCommand.ShippingAddressCommand addr) {
        List<OrderPlacedEvent.Item> eventItems = order.getItems().stream()
                .map(i -> new OrderPlacedEvent.Item(
                        i.getProductId(), i.getVariantId(), i.getQuantity(), i.getUnitPrice(), i.getSellerId()
                ))
                .toList();

        OrderPlacedEvent.ShippingAddress eventAddr = new OrderPlacedEvent.ShippingAddress(
                addr.recipient(), addr.phone(), addr.zipCode(), addr.address1(), addr.address2()
        );

        return OrderPlacedEvent.of(order.getOrderId(), order.getUserId(),
                order.getTotalPrice(), eventItems, eventAddr, clock);
    }
}
