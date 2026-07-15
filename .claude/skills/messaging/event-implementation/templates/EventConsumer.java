// Event consumer pattern. Always guard null payload and use ${spring.application.name} for groupId.
// Topic naming: {service|domain}.{aggregate}.{version} is the canonical RULE (platform/event-driven-policy.md § Broker).
// Placeholder below omits the version segment for brevity — check specs/contracts/events/<producer>-events.md
// for the real topic name; do not copy this literal string into service code.

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedEventConsumer {

    private final PaymentProcessingService paymentProcessingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.order.placed", groupId = "${spring.application.name}")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        if (event.payload() == null) {
            log.warn("Null payload, skipping. eventId={}", event.eventId());
            return;
        }
        paymentProcessingService.processPayment(
            event.payload().orderId(),
            event.payload().userId(),
            event.payload().totalPrice()
        );
    }
}
