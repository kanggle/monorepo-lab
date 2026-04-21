// Standard event envelope. Replace Product* with your aggregate name.

public record ProductEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    ProductEventPayload payload
) {
    public static ProductEvent of(String eventType, ProductEventPayload payload) {
        return new ProductEvent(UUID.randomUUID(), eventType, Instant.now(), payload);
    }
}
