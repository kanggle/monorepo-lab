package com.wms.notification.application.service.fakes;

import com.wms.notification.application.port.outbound.DeliveryRepository;
import com.wms.notification.domain.delivery.DeliveryStatus;
import com.wms.notification.domain.delivery.NotificationDelivery;
import com.wms.notification.domain.error.IdempotencyKeyDuplicateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryDeliveryRepository implements DeliveryRepository {

    public final Map<UUID, NotificationDelivery> byId = new LinkedHashMap<>();
    public final Map<String, NotificationDelivery> byKey = new LinkedHashMap<>();

    @Override
    public void save(NotificationDelivery delivery) {
        if (byKey.containsKey(delivery.deliveryIdempotencyKey())
                && !byId.containsKey(delivery.id())) {
            throw new IdempotencyKeyDuplicateException(delivery.deliveryIdempotencyKey(), null);
        }
        byId.put(delivery.id(), delivery);
        byKey.put(delivery.deliveryIdempotencyKey(), delivery);
    }

    @Override
    public void update(NotificationDelivery delivery) {
        byId.put(delivery.id(), delivery);
        byKey.put(delivery.deliveryIdempotencyKey(), delivery);
    }

    @Override
    public Optional<NotificationDelivery> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<NotificationDelivery> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(byKey.get(idempotencyKey));
    }

    @Override
    public List<NotificationDelivery> findByEventId(UUID eventId) {
        return byId.values().stream()
                .filter(d -> d.eventId().equals(eventId))
                .toList();
    }

    @Override
    public List<NotificationDelivery> findAndLockPendingDueForRetry(Instant now, int batchSize) {
        List<NotificationDelivery> result = new ArrayList<>();
        for (NotificationDelivery d : byId.values()) {
            if (d.status() != DeliveryStatus.PENDING) continue;
            if (d.scheduledRetryAt().isPresent() && d.scheduledRetryAt().get().isAfter(now)) continue;
            result.add(d);
            if (result.size() >= batchSize) break;
        }
        return result;
    }
}
