// Kafka publisher adapter — place in infrastructure/messaging/.
// Port (ProductEventPublisher) lives in the application layer.

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
        } catch (Exception e) {
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
