// Kafka publisher adapter — place in infrastructure/messaging/.
// Port (ProductEventPublisher) lives in the application layer. Topic naming RULE: {service|domain}.{aggregate}.{version} per platform/event-driven-policy.md § Broker — check specs/contracts/events/<producer>-events.md for this project's actual declared shape before copying the unversioned placeholders below.

// application layer — port
public interface ProductEventPublisher {
    void publish(ProductEvent event);
}

// infrastructure layer — Kafka adapter
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class KafkaProductEventPublisher implements ProductEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ProductMetrics productMetrics;

    @Override
    public void publish(ProductEvent event) {
        String topic = resolveTopic(event.eventType());
        String key = event.eventId().toString();
        try {
            kafkaTemplate.send(topic, key, event);
        } catch (KafkaException e) {
            // Narrow catch, not top-level (platform/coding-rules.md § Exceptions: "Do not catch
            // Exception unless at the top-level handler"). KafkaTemplate#send can throw
            // synchronously (e.g. serialization failure) before the async Future is even
            // returned; that synchronous path is what this catches. Broker-side failures
            // surface later on the returned Future/CompletableFuture and are not caught here.
            log.error("Event publishing failed: eventType={}, topic={}", event.eventType(), topic, e);
            productMetrics.incrementEventPublishFailure(event.eventType());
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "ProductCreated" -> "product.product.created";
            case "ProductUpdated" -> "product.product.updated";
            case "ProductDeleted" -> "product.product.deleted";
            case "StockChanged"   -> "product.product.stock-changed";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
