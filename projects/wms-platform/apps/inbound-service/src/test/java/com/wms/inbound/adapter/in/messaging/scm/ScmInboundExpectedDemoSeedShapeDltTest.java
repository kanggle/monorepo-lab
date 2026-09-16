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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * TASK-MONO-683 — does the scm <strong>demo seed's</strong> SKU→supplier mapping resolve in wms, and
 * what does wms do when it does not.
 *
 * <p>The mapping's {@code supplierId} and SKU code flow verbatim: {@code sku_supplier_map} →
 * reorder suggestion → from-suggestion PO → {@code scm.procurement.inbound-expected.v1}, where wms
 * inbound resolves both <em>by code</em> (ADR-MONO-050 §7 D9). The seed used to store the supplier
 * master's server-issued UUID and demo-only SKU codes that no wms seed knows, so any confirmed
 * suggestion would have been rejected straight to the DLT.
 *
 * <p>Both sides are read from the repository, not typed here:
 * <ul>
 *   <li>scm: {@code infra/demo/seed/seed-scm.sh} — the variable the mapping {@code PUT} writes as
 *       {@code supplierId}, and the SKU variables its loop iterates ({@link ScmDemoSeed});</li>
 *   <li>wms: {@code db/seed/R__seed_dev_masterref.sql} on the test classpath — the inbound master
 *       read model the demo boots with ({@link WmsDevSeedReadModel}).</li>
 * </ul>
 * Everything below the listener is real: the parser, the consumer, the service and the
 * {@link DefaultErrorHandler} the service registers. Only persistence/outbox ports and the Kafka
 * producer are mocks. No broker, no database — {@code ScmInboundExpectedConsumerIT} is the
 * Testcontainers-level sibling.
 */
@ExtendWith(MockitoExtension.class)
class ScmInboundExpectedDemoSeedShapeDltTest {

    private static final String TOPIC = "scm.procurement.inbound-expected.v1";
    private static final String WMS_DEV_SEED = "db/seed/R__seed_dev_masterref.sql";
    private static final Path SCM_DEMO_SEED = Paths.get("infra", "demo", "seed", "seed-scm.sh");

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
        // Guard against a silently empty fake: the tests below read "unknown" from it.
        assertThat(wmsSeed.warehouseCodes).containsExactly("WH01");
        assertThat(wmsSeed.partnerCodes).containsExactly("SUP-001");
        assertThat(wmsSeed.skuCodes).containsExactly("SKU-APPLE-001");
    }

    /**
     * 🔴 The seed-coupled check. Reverting {@code seed-scm.sh} to the pre-683 values (supplier
     * {@code SUP-DEMO-01} / SKU {@code SKU-DEMO-A1}) or back to writing {@code $SUPPLIER_ID} into
     * the mapping turns this red.
     */
    @Test
    void demoSeedMapping_resolvesInWmsDevSeed() throws IOException {
        ScmDemoSeed seed = ScmDemoSeed.load();
        assertThat(seed.mappedSkus).as("SKUs the seed maps to a supplier").isNotEmpty();

        runDedupeWork();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(asnNoSequence.nextAsnNo()).thenReturn("ASN-SEED-0001");
        String warehouse = wmsSeed.warehouseCodes.get(0);

        for (String sku : seed.mappedSkus) {
            Throwable thrown = catchThrowable(() -> consumer.onInboundExpected(
                    event(seed.mappedSupplierId, warehouse, sku)));
            assertThat(thrown)
                    .as("seed-scm.sh maps %s → supplierId %s (from $%s); wms must resolve both by code",
                            sku, seed.mappedSupplierId, seed.mappedSupplierVariable)
                    .isNull();
        }
        verify(asnPersistence, times(seed.mappedSkus.size())).save(any());
    }

    @Test
    void serverIssuedSupplierUuid_isRejectedByWms_andGoesToDltWithoutRetry() {
        // The pre-683 seed shape: the supplier master's id instead of its code. SKU and warehouse
        // are ones wms knows, so the supplier is the only reason for the rejection.
        runDedupeWork();
        when(asnPersistence.existsOpenByPoNumber(any())).thenReturn(false);
        stubDltSend();
        String supplierUuid = UuidV7.randomUuid().toString();

        Throwable thrown = catchThrowable(() -> consumer.onInboundExpected(
                event(supplierUuid, wmsSeed.warehouseCodes.get(0), wmsSeed.skuCodes.get(0))));

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
                    "poNumber": "PO-%s",
                    "supplierId": "%s",
                    "destinationWarehouseId": "%s",
                    "destinationNodeType": "WMS_WAREHOUSE",
                    "expectedArrivalDate": "2026-09-19",
                    "currency": "KRW",
                    "lines": [ { "skuCode": "%s", "expectedQty": "100", "uom": "EA" } ]
                  }
                }
                """.formatted(UUID.randomUUID(), "po-1", UUID.randomUUID(), skuCode, supplierId,
                warehouseCode, skuCode);
    }

    /**
     * What {@code infra/demo/seed/seed-scm.sh} writes into {@code sku_supplier_map}, read from the
     * script text.
     *
     * <ul>
     *   <li>{@code supplierId} = the {@code $VARIABLE} inside the JSON body of the
     *       {@code sku-supplier-map} {@code PUT}. A variable with a literal {@code NAME="value"}
     *       assignment resolves to that literal; {@code SUPPLIER_ID} has none because the script
     *       extracts it from the registration response (a server-issued UUIDv7), so it resolves to a
     *       fresh UUIDv7 — the same shape the demo would store.</li>
     *   <li>SKUs = the {@code $VARIABLE}s of the {@code for sku in …; do} loop that issues that
     *       {@code PUT}, each resolved to its literal.</li>
     * </ul>
     * 🔴 Anything else fails loudly — a parse that silently finds nothing would pass vacuously.
     */
    static final class ScmDemoSeed {

        private static final Pattern ASSIGNMENT = Pattern.compile("(?m)^([A-Z][A-Z0-9_]*)=\"([^\"$]*)\"\\s*$");
        private static final Pattern MAP_LOOP = Pattern.compile(
                "for sku in ([^;\\n]*); do((?:(?!\\bdone\\b).)*?)sku-supplier-map/",
                Pattern.DOTALL);
        private static final Pattern MAP_SUPPLIER_VAR = Pattern.compile(
                "sku-supplier-map/\\$sku\"[^\\n]*\\n[^\\n]*\\\\\"supplierId\\\\\":\\\\\"\\$([A-Z_][A-Z0-9_]*)\\\\\"");
        private static final Pattern VAR_REF = Pattern.compile("\\$\\{?([A-Z_][A-Z0-9_]*)\\}?");

        final String mappedSupplierVariable;
        final String mappedSupplierId;
        final List<String> mappedSkus = new ArrayList<>();

        private ScmDemoSeed(String supplierVariable, String supplierId) {
            this.mappedSupplierVariable = supplierVariable;
            this.mappedSupplierId = supplierId;
        }

        static ScmDemoSeed load() throws IOException {
            String script = Files.readString(locate(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            Map<String, String> literals = new HashMap<>();
            Matcher a = ASSIGNMENT.matcher(script);
            while (a.find()) {
                literals.put(a.group(1), a.group(2));
            }

            Matcher sup = MAP_SUPPLIER_VAR.matcher(script);
            assertThat(sup.find())
                    .as("seed-scm.sh: the sku-supplier-map PUT body with a \\\"supplierId\\\":\\\"$VAR\\\"")
                    .isTrue();
            String supplierVar = sup.group(1);
            String supplierId;
            if (literals.containsKey(supplierVar) && !literals.get(supplierVar).isEmpty()) {
                supplierId = literals.get(supplierVar);
            } else if ("SUPPLIER_ID".equals(supplierVar)) {
                supplierId = UuidV7.randomUuid().toString();
            } else {
                throw new AssertionError("seed-scm.sh maps supplierId from $" + supplierVar
                        + ", which has no literal assignment and is not the server-issued SUPPLIER_ID");
            }

            ScmDemoSeed seed = new ScmDemoSeed(supplierVar, supplierId);
            Matcher loop = MAP_LOOP.matcher(script);
            assertThat(loop.find()).as("seed-scm.sh: the `for sku in …; do` loop issuing the mapping PUT").isTrue();
            Matcher ref = VAR_REF.matcher(loop.group(1));
            while (ref.find()) {
                String var = ref.group(1);
                assertThat(literals).as("seed-scm.sh: literal assignment of $%s", var).containsKey(var);
                seed.mappedSkus.add(literals.get(var));
            }
            return seed;
        }

        /** Walks up from the Gradle test working directory (the module dir) to the repo root. */
        private static Path locate() {
            Path dir = Paths.get("").toAbsolutePath();
            while (dir != null) {
                Path candidate = dir.resolve(SCM_DEMO_SEED);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
                dir = dir.getParent();
            }
            throw new AssertionError("infra/demo/seed/seed-scm.sh not found above "
                    + Paths.get("").toAbsolutePath() + " — this check must not pass without reading it");
        }
    }

    /**
     * Master read model holding exactly the rows of the wms dev seed file. Every row in that file
     * is a single-row {@code INSERT INTO <table> (...) VALUES (...)}; the id is the first string
     * literal of the VALUES tuple, the code the second, and the partner type the third.
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
        private final List<WarehouseSnapshot> warehouses = new ArrayList<>();
        private final List<PartnerSnapshot> partners = new ArrayList<>();
        private final List<SkuSnapshot> skus = new ArrayList<>();

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
                UUID id = UUID.fromString(literals.get(0));
                switch (m.group(1)) {
                    case "warehouse_snapshot" -> {
                        model.warehouseCodes.add(literals.get(1));
                        model.warehouses.add(new WarehouseSnapshot(id, literals.get(1),
                                WarehouseSnapshot.Status.valueOf(literals.get(2)), CACHED, 0L));
                    }
                    case "sku_snapshot" -> {
                        model.skuCodes.add(literals.get(1));
                        model.skus.add(new SkuSnapshot(id, literals.get(1),
                                SkuSnapshot.TrackingType.valueOf(literals.get(2)),
                                SkuSnapshot.Status.valueOf(literals.get(3)), CACHED, 0L));
                    }
                    case "partner_snapshot" -> {
                        model.partnerCodes.add(literals.get(1));
                        model.partners.add(new PartnerSnapshot(id, literals.get(1),
                                PartnerSnapshot.PartnerType.valueOf(literals.get(2)),
                                PartnerSnapshot.Status.valueOf(literals.get(3)), CACHED, 0L));
                    }
                    default -> { }
                }
            }
            return model;
        }

        @Override
        public Optional<WarehouseSnapshot> findWarehouseByCode(String warehouseCode) {
            return warehouses.stream().filter(w -> w.warehouseCode().equals(warehouseCode)).findFirst();
        }

        @Override
        public Optional<PartnerSnapshot> findPartnerByCode(String partnerCode) {
            return partners.stream().filter(p -> p.partnerCode().equals(partnerCode)).findFirst();
        }

        @Override
        public Optional<SkuSnapshot> findSkuByCode(String skuCode) {
            return skus.stream().filter(s -> s.skuCode().equals(skuCode)).findFirst();
        }

        @Override
        public Optional<SkuSnapshot> findSku(UUID id) {
            return skus.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override public Optional<WarehouseSnapshot> findWarehouse(UUID id) { return Optional.empty(); }
        @Override public Optional<ZoneSnapshot> findZone(UUID id) { return Optional.empty(); }
        @Override public Optional<LocationSnapshot> findLocation(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLot(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLotBySkuAndLotNo(UUID skuId, String lotNo) { return Optional.empty(); }
        @Override public Optional<PartnerSnapshot> findPartner(UUID id) { return Optional.empty(); }
    }
}
