package com.wms.inbound.adapter.in.messaging.scm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.id.UuidV7;
import com.example.messaging.dedupe.EventDedupePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inbound.application.exception.InboundExpectationRejectedException;
import com.wms.inbound.application.port.in.CancelScmInboundExpectationUseCase;
import com.wms.inbound.application.port.out.AsnNoSequencePort;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.service.CreateScmInboundExpectationService;
import com.wms.inbound.config.KafkaConsumerConfig;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import com.wms.inbound.domain.model.masterref.LotSnapshot;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import com.wms.inbound.domain.model.masterref.ZoneSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

/**
 * TASK-MONO-683 AC-1 / AC-2 — what wms inbound-service does with the inbound-expected event
 * that the <strong>demo seed's data shape</strong> would produce, measured rather than inferred.
 *
 * <p>{@code infra/demo/seed/seed-scm.sh} writes the supplier master's <em>server-issued UUID</em>
 * into {@code sku_supplier_map.supplier_id}; demand-planning copies it onto the suggestion, the
 * from-suggestion PO stores it verbatim, and {@code OutboxProcurementEventPublisher} emits it as
 * the event's {@code supplierId}. wms resolves that field as a partner <em>code</em>
 * (ADR-MONO-050 D9).
 *
 * <p>Everything below the Kafka listener is real: the parser, the consumer, the
 * {@link CreateScmInboundExpectationService} and the {@link DefaultErrorHandler} built by
 * {@link KafkaConsumerConfig}. The master read model is a fake <strong>loaded from the wms dev
 * seed file itself</strong> ({@code db/seed/R__seed_dev_masterref.sql}), so the "which codes wms
 * knows" half is read from the repository, not typed here. Only the persistence/outbox ports and
 * the Kafka producer are mocks. No broker, no database — Docker was unavailable when this was
 * written; {@code ScmInboundExpectedConsumerIT} is the Testcontainers-level sibling.
 *
 * <p>🔴 Honest limit: the scm-side values ({@code SUP-DEMO-01}, {@code SKU-DEMO-A1}) are copied
 * from {@code seed-scm.sh} lines 67-68, and the supplier UUID is generated in the same shape
 * procurement issues ({@code UuidV7}). This test does not read {@code seed-scm.sh}; changing the
 * seed does not change this test's inputs.
 */
@ExtendWith(MockitoExtension.class)
class ScmInboundExpectedDemoSeedShapeDltTest {

    private static final String TOPIC = "scm.procurement.inbound-expected.v1";
    private static final String WMS_DEV_SEED = "db/seed/R__seed_dev_masterref.sql";

    /** {@code infra/demo/seed/seed-scm.sh:67} — the scm supplier master's natural key. */
    private static final String SCM_SEED_SUPPLIER_CODE = "SUP-DEMO-01";
    /** {@code infra/demo/seed/seed-scm.sh:68} — the SKU the seed maps to that supplier. */
    private static final String SCM_SEED_SKU = "SKU-DEMO-A1";

    @Mock AsnPersistencePort asnPersistence;
    @Mock AsnNoSequencePort asnNoSequence;
    @Mock InboundEventPort eventPort;
    @Mock EventDedupePort dedupe;
    @Mock CancelScmInboundExpectationUseCase cancelUseCase;
    @Mock KafkaOperations<Object, Object> kafkaOperations;
    @Mock Consumer<Object, Object> kafkaConsumer;
    @Mock MessageListenerContainer container;

    private WmsDevSeedReadModel wmsSeed;
    private ScmInboundExpectedConsumer consumer;

    @BeforeEach
    void setUp() throws IOException {
        wmsSeed = WmsDevSeedReadModel.load(WMS_DEV_SEED);
        CreateScmInboundExpectationService service = new CreateScmInboundExpectationService(
                asnPersistence, asnNoSequence, eventPort, wmsSeed,
                Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC));
        ScmInboundExpectedEventParser parser =
                new ScmInboundExpectedEventParser(new ObjectMapper().registerModule(new JavaTimeModule()));
        consumer = new ScmInboundExpectedConsumer(parser, dedupe, service, cancelUseCase);
    }

    @Test
    void wmsDevSeed_isLoadedNonVacuously() {
        // Guard against a silently empty fake: every assertion below reads "unknown" from it.
        assertThat(wmsSeed.warehouseCodes).containsExactly("WH01");
        assertThat(wmsSeed.partnerCodes).containsExactly("SUP-001");
        assertThat(wmsSeed.skuCodes).containsExactly("SKU-APPLE-001");
    }

    @Test
    void seedShapedSupplierUuid_isRejectedByWms_andGoesToDltWithoutRetry() {
        runDedupeWork();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        stubDltSend();
        String supplierUuid = UuidV7.randomUuid().toString();

        Throwable thrown = catchThrowable(() -> consumer.onInboundExpected(
                event(supplierUuid, "WH01", SCM_SEED_SKU)));

        assertThat(thrown)
                .isInstanceOf(InboundExpectationRejectedException.class)
                .hasMessageStartingWith("unknown supplierId=" + supplierUuid);
        verify(asnPersistence, never()).save(any());

        // Hand the failure to the service's real error handler, as the listener container does.
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 0, 42L, "po", "raw");
        DefaultErrorHandler handler = realErrorHandler();
        handler.handleRemaining(new ListenerExecutionFailedException("listener failed", thrown),
                List.of(record), kafkaConsumer, container);

        ArgumentCaptor<ProducerRecord<Object, Object>> sent = producerRecordCaptor();
        verify(kafkaOperations, times(1)).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");
        // Not re-polled: no seek back to the failed offset means no retry attempt.
        verify(kafkaConsumer, never()).seek(any(), any(Long.class));
    }

    @Test
    void control_retryableFailure_isSeekedBackForRetry_notSentToDlt() {
        // Without this control the test above cannot tell "rejected → DLT at once" from
        // "every failure → DLT at once".
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 0, 42L, "po", "raw");
        DefaultErrorHandler handler = realErrorHandler();

        Throwable retry = catchThrowable(() -> handler.handleRemaining(
                new ListenerExecutionFailedException("listener failed", new IllegalStateException("transient")),
                List.of(record), kafkaConsumer, container));

        // The handler signals "will be redelivered" by throwing after seeking back.
        assertThat(retry).isNotNull();
        assertThat(retry.getClass().getSimpleName()).isEqualTo("RecordInRetryException");
        verify(kafkaOperations, never()).send(any(ProducerRecord.class));
        verify(kafkaConsumer).seek(any(), any(Long.class));
    }

    @Test
    void scmSeedSupplierCode_isAlsoUnknownToWmsDevSeed() {
        // AC-2: replacing the UUID with the scm supplier CODE alone does not meet wms.
        runDedupeWork();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);

        Throwable thrown = catchThrowable(() -> consumer.onInboundExpected(
                event(SCM_SEED_SUPPLIER_CODE, "WH01", SCM_SEED_SKU)));

        assertThat(thrown)
                .isInstanceOf(InboundExpectationRejectedException.class)
                .hasMessageStartingWith("unknown supplierId=" + SCM_SEED_SUPPLIER_CODE);
    }

    @Test
    void wmsSeedSupplierCode_passesSupplierGate_butScmSeedSku_isUnknownToWms() {
        // AC-2: even a supplier code wms knows is followed by a SKU code wms does not know.
        runDedupeWork();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);

        Throwable thrown = catchThrowable(() -> consumer.onInboundExpected(
                event("SUP-001", "WH01", SCM_SEED_SKU)));

        assertThat(thrown)
                .isInstanceOf(InboundExpectationRejectedException.class)
                .hasMessageStartingWith("unknown skuCode=" + SCM_SEED_SKU);
        verify(asnPersistence, never()).save(any());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The exact handler the service registers. The {@code @Bean} method is package-private in
     * {@code com.wms.inbound.config}, so it is invoked reflectively rather than re-built here —
     * re-building it would test a copy, not the configuration.
     */
    private DefaultErrorHandler realErrorHandler() {
        try {
            Method bean = KafkaConsumerConfig.class.getDeclaredMethod(
                    "kafkaErrorHandler", KafkaOperations.class, String.class);
            bean.setAccessible(true);
            return (DefaultErrorHandler) bean.invoke(new KafkaConsumerConfig(), kafkaOperations, ".DLT");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("KafkaConsumerConfig#kafkaErrorHandler not invocable", e);
        }
    }

    private void runDedupeWork() {
        doAnswer(inv -> {
            Runnable work = inv.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    private void stubDltSend() {
        when(kafkaOperations.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<Object, Object> pr = inv.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(pr, null));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<ProducerRecord<Object, Object>> producerRecordCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
    }

    /** Byte-shape of {@code OutboxProcurementEventPublisher#publishInboundExpected}. */
    private static String event(String supplierId, String warehouseCode, String skuCode) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "scm.procurement.inbound-expected",
                  "source": "scm-platform-procurement-service",
                  "occurredAt": "2026-09-16T00:00:00.000Z",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "poId": "%s",
                    "poNumber": "PO-DEMO0001",
                    "supplierId": "%s",
                    "destinationWarehouseId": "%s",
                    "destinationNodeType": "WMS_WAREHOUSE",
                    "expectedArrivalDate": "2026-09-19",
                    "currency": "KRW",
                    "lines": [ { "skuCode": "%s", "expectedQty": "100", "uom": "EA" } ]
                  }
                }
                """.formatted(UUID.randomUUID(), "po-1", UUID.randomUUID(), supplierId,
                warehouseCode, skuCode);
    }

    /**
     * Master read model holding exactly the rows of the wms dev seed file. Every row in that file
     * is a single-row {@code INSERT INTO <table> (...) VALUES (...)}; the code column is the
     * second string literal of the VALUES tuple, and the partner type the third.
     */
    static final class WmsDevSeedReadModel implements MasterReadModelPort {

        private static final Pattern INSERT = Pattern.compile(
                "INSERT INTO (\\w+)\\s*\\([^)]*\\)\\s*VALUES\\s*\\((.*?)\\)\\s*ON CONFLICT",
                Pattern.DOTALL);
        private static final Pattern LITERAL = Pattern.compile("'([^']*)'");
        private static final Instant CACHED = Instant.parse("2026-04-18T00:00:00Z");

        final List<String> warehouseCodes = new ArrayList<>();
        final List<String> partnerCodes = new ArrayList<>();
        final List<String> skuCodes = new ArrayList<>();
        private final List<PartnerSnapshot> partners = new ArrayList<>();

        static WmsDevSeedReadModel load(String resource) throws IOException {
            String sql;
            try (InputStream in = WmsDevSeedReadModel.class.getClassLoader().getResourceAsStream(resource)) {
                assertThat(in).as("wms dev seed %s on the test classpath", resource).isNotNull();
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            WmsDevSeedReadModel model = new WmsDevSeedReadModel();
            Matcher m = INSERT.matcher(sql);
            while (m.find()) {
                List<String> literals = new ArrayList<>();
                Matcher lit = LITERAL.matcher(m.group(2));
                while (lit.find()) {
                    literals.add(lit.group(1));
                }
                switch (m.group(1)) {
                    case "warehouse_snapshot" -> model.warehouseCodes.add(literals.get(1));
                    case "sku_snapshot" -> model.skuCodes.add(literals.get(1));
                    case "partner_snapshot" -> {
                        model.partnerCodes.add(literals.get(1));
                        model.partners.add(new PartnerSnapshot(UUID.fromString(literals.get(0)),
                                literals.get(1), PartnerSnapshot.PartnerType.valueOf(literals.get(2)),
                                PartnerSnapshot.Status.valueOf(literals.get(3)), CACHED, 0L));
                    }
                    default -> { }
                }
            }
            return model;
        }

        @Override
        public Optional<WarehouseSnapshot> findWarehouseByCode(String warehouseCode) {
            return warehouseCodes.contains(warehouseCode)
                    ? Optional.of(new WarehouseSnapshot(UUID.randomUUID(), warehouseCode,
                            WarehouseSnapshot.Status.ACTIVE, CACHED, 0L))
                    : Optional.empty();
        }

        @Override
        public Optional<PartnerSnapshot> findPartnerByCode(String partnerCode) {
            return partners.stream().filter(p -> p.partnerCode().equals(partnerCode)).findFirst();
        }

        @Override
        public Optional<SkuSnapshot> findSkuByCode(String skuCode) {
            return skuCodes.contains(skuCode)
                    ? Optional.of(new SkuSnapshot(UUID.randomUUID(), skuCode, SkuSnapshot.TrackingType.LOT,
                            SkuSnapshot.Status.ACTIVE, CACHED, 0L))
                    : Optional.empty();
        }

        @Override public Optional<WarehouseSnapshot> findWarehouse(UUID id) { return Optional.empty(); }
        @Override public Optional<ZoneSnapshot> findZone(UUID id) { return Optional.empty(); }
        @Override public Optional<LocationSnapshot> findLocation(UUID id) { return Optional.empty(); }
        @Override public Optional<SkuSnapshot> findSku(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLot(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLotBySkuAndLotNo(UUID skuId, String lotNo) { return Optional.empty(); }
        @Override public Optional<PartnerSnapshot> findPartner(UUID id) { return Optional.empty(); }
    }
}
