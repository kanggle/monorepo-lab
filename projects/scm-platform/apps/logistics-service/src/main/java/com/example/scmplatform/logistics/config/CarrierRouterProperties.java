package com.example.scmplatform.logistics.config;

import com.example.scmplatform.logistics.domain.model.Carrier;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code CarrierRouter} registry configuration (ADR-053 §D3). Bound from
 * {@code logistics.carrier-router.*}:
 *
 * <ul>
 *   <li>{@code carrier-vendors} — the {@code carrierCode → vendor} map (domestic carriers →
 *       {@code GOODSFLOW}; international → {@code EASYPOST}). Keys are matched case-insensitively.</li>
 *   <li>{@code default-vendor} — the vendor a {@code null} or unmapped carrierCode falls back to
 *       (with a {@code CARRIER_UNROUTABLE} degrade). Defaults to {@code EASYPOST}.</li>
 * </ul>
 *
 * The domestic/international split lives here as registry structure — <b>not</b> a separately
 * sourced geographic {@code Region} field (the seam carries {@code carrierCode}, not a region).
 */
@ConfigurationProperties(prefix = "logistics.carrier-router")
public class CarrierRouterProperties {

    /** {@code carrierCode → vendor}. A carrierCode absent here is "unmapped" → default + degrade. */
    private Map<String, Carrier> carrierVendors = new LinkedHashMap<>();

    /** Fallback vendor for a null/unmapped carrierCode (never a silent drop). */
    private Carrier defaultVendor = Carrier.EASYPOST;

    public Map<String, Carrier> getCarrierVendors() {
        return carrierVendors;
    }

    public void setCarrierVendors(Map<String, Carrier> carrierVendors) {
        this.carrierVendors = carrierVendors;
    }

    public Carrier getDefaultVendor() {
        return defaultVendor;
    }

    public void setDefaultVendor(Carrier defaultVendor) {
        this.defaultVendor = defaultVendor;
    }
}
