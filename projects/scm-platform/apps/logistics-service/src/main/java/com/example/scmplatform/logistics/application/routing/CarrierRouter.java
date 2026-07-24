package com.example.scmplatform.logistics.application.routing;

import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.model.Carrier;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Selects <b>exactly one</b> dispatch vendor per shipment from the shipment's requested
 * {@code carrierCode} (ADR-053 §D3; subscriptions contract § CarrierRouter selection). This is the
 * multi-vendor seam: the moment two {@code !standalone} {@link ShipmentDispatchPort} beans exist
 * (EasyPost + 굿스플로), direct injection is ambiguous, so the use case injects this router instead.
 *
 * <p>Routing signal = <b>{@code carrierCode}</b> (the field the seam actually carries), <b>not</b>
 * a geographic {@code Region}: the seam carries no address/region (the documented "known input
 * gap"). The domestic/international split is modelled as this registry's internal structure — a
 * config-driven {@code carrierCode → vendor} map (domestic carriers → {@link Carrier#GOODSFLOW};
 * international → {@link Carrier#EASYPOST}).
 *
 * <ul>
 *   <li><b>Mapped {@code carrierCode}</b> → its owning vendor.</li>
 *   <li><b>{@code null} or unmapped {@code carrierCode}</b> → the configured <b>default vendor</b>
 *       + a {@code CARRIER_UNROUTABLE} degrade (log + metric). <b>Never</b> a thrown error that
 *       drops the shipment (architecture.md § Failure Modes; task Failure Scenario D).</li>
 * </ul>
 *
 * Framework-free (no Spring annotations): constructor-injected, fully unit-testable. Wired into a
 * bean by {@code CarrierRouterConfig}. Under the {@code standalone} profile it is built via
 * {@link #singleVendor} — every route resolves to the single stub with no degrade signal.
 */
public class CarrierRouter {

    private static final Logger log = LoggerFactory.getLogger(CarrierRouter.class);

    /** Degrade counter for a null/unmapped carrierCode routed to the default vendor. */
    static final String UNROUTABLE_METRIC = "logistics_dispatch_carrier_unroutable_total";

    private final Map<Carrier, ShipmentDispatchPort> portsByVendor;
    private final Map<String, Carrier> carrierToVendor;
    private final Carrier defaultVendor;
    private final MeterRegistry meterRegistry;
    /** Single-vendor passthrough (standalone): every route → the one stub, no degrade. */
    private final boolean passthrough;

    /**
     * Multi-vendor router.
     *
     * @param portsByVendor  the live dispatch ports keyed by vendor (must contain {@code defaultVendor})
     * @param carrierToVendor the config-driven {@code carrierCode → vendor} registry
     * @param defaultVendor  the vendor a null/unmapped carrierCode falls back to
     * @param meterRegistry  for the {@code CARRIER_UNROUTABLE} degrade metric
     */
    public CarrierRouter(Map<Carrier, ShipmentDispatchPort> portsByVendor,
                         Map<String, Carrier> carrierToVendor,
                         Carrier defaultVendor,
                         MeterRegistry meterRegistry) {
        this.portsByVendor = Map.copyOf(portsByVendor);
        this.carrierToVendor = normalize(carrierToVendor);
        this.defaultVendor = Objects.requireNonNull(defaultVendor, "defaultVendor");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.passthrough = false;
        if (!this.portsByVendor.containsKey(defaultVendor)) {
            throw new IllegalArgumentException(
                    "No dispatch port registered for the default vendor " + defaultVendor);
        }
    }

    private CarrierRouter(Carrier vendor, ShipmentDispatchPort port) {
        this.portsByVendor = Map.of(vendor, port);
        this.carrierToVendor = Map.of();
        this.defaultVendor = vendor;
        this.meterRegistry = null;
        this.passthrough = true;
    }

    /**
     * Single-vendor passthrough router for the {@code standalone} profile: every {@code select}
     * returns the one stub port, no vendor split, no {@code CARRIER_UNROUTABLE} degrade.
     */
    public static CarrierRouter singleVendor(Carrier vendor, ShipmentDispatchPort port) {
        return new CarrierRouter(Objects.requireNonNull(vendor, "vendor"),
                Objects.requireNonNull(port, "port"));
    }

    /**
     * Resolve the dispatch port for a requested carrier code. Never returns {@code null} and never
     * drops a shipment: a null/unmapped code degrades to the configured default vendor.
     */
    public ShipmentDispatchPort select(String carrierCode) {
        if (passthrough) {
            return portsByVendor.get(defaultVendor);
        }
        Carrier vendor = carrierCode == null ? null
                : carrierToVendor.get(carrierCode.trim().toUpperCase(Locale.ROOT));
        if (vendor == null) {
            // null OR unmapped → configured default + CARRIER_UNROUTABLE degrade (never a drop).
            recordUnroutable(carrierCode);
            vendor = defaultVendor;
        }
        ShipmentDispatchPort port = portsByVendor.get(vendor);
        if (port == null) {
            throw new IllegalStateException("No dispatch port registered for vendor " + vendor);
        }
        return port;
    }

    private void recordUnroutable(String carrierCode) {
        log.warn("CARRIER_UNROUTABLE: carrierCode='{}' is null/unmapped; routing to default vendor "
                + "{} (degrade, no drop)", carrierCode, defaultVendor);
        meterRegistry.counter(UNROUTABLE_METRIC, "fallback_vendor", defaultVendor.name()).increment();
    }

    private static Map<String, Carrier> normalize(Map<String, Carrier> in) {
        Map<String, Carrier> out = new HashMap<>();
        in.forEach((k, v) -> out.put(k.trim().toUpperCase(Locale.ROOT), v));
        return Map.copyOf(out);
    }
}
