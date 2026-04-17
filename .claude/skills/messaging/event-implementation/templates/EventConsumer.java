// Event consumer pattern. Always guard null payload and use ${spring.application.name} for groupId.

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
