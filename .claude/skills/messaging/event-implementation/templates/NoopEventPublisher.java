// Standalone no-op publisher — activated when Kafka is absent (local dev, tests).

@Slf4j
@Component
@Profile("standalone")
public class NoopProductEventPublisher implements ProductEventPublisher {
    @Override
    public void publish(ProductEvent event) {
        log.debug("Standalone mode — skipping event: {}", event.eventType());
    }
}
