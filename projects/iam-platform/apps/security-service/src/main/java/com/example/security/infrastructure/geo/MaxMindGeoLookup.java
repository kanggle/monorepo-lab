package com.example.security.infrastructure.geo;

import com.example.security.domain.detection.GeoLookup;
import com.example.security.domain.detection.GeoPoint;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * MaxMind GeoLite2 adapter. Loads the DB from {@code security.detection.geoip.db-path}
 * (filesystem) or, if absent, the classpath resource {@code GeoLite2-City.mmdb}. If
 * neither is available the adapter stays disabled — {@link #isAvailable()} returns
 * false and {@link #resolve(String)} returns empty — and the {@code GeoAnomalyRule}
 * skips gracefully per spec.
 */
@Slf4j
@Component
public class MaxMindGeoLookup implements GeoLookup {

    private final String configuredPath;
    private DatabaseReader reader;

    public MaxMindGeoLookup(@Value("${security.detection.geoip.db-path:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    @PostConstruct
    void init() {
        // 1) explicit filesystem path wins
        if (configuredPath != null && !configuredPath.isBlank()) {
            File f = new File(configuredPath);
            if (f.isFile() && f.canRead()) {
                tryOpen(f);
                return;
            } else {
                log.warn("GeoIP DB not found at configured path: {}. GeoAnomalyRule will be disabled.", configuredPath);
            }
        }
        // 2) environment variable fallback
        String envPath = System.getenv("GEOIP_DB_PATH");
        if (envPath != null && !envPath.isBlank()) {
            File f = new File(envPath);
            if (f.isFile() && f.canRead()) {
                tryOpen(f);
                return;
            }
        }
        // 3) classpath fallback — useful for tests that ship a minimal DB
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("GeoLite2-City.mmdb")) {
            if (in != null) {
                Path tmp = Files.createTempFile("GeoLite2-City", ".mmdb");
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tryOpen(tmp.toFile());
                return;
            }
        } catch (Exception e) {
            log.debug("No classpath GeoLite2-City.mmdb available", e);
        }
        log.info("GeoAnomalyRule disabled — no MaxMind GeoLite2 DB found (configured path, GEOIP_DB_PATH env, classpath).");
    }

    private void tryOpen(File f) {
        try {
            this.reader = new DatabaseReader.Builder(f).build();
            log.info("MaxMind GeoLite2 DB loaded from {}", f.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to open MaxMind DB at {}; GeoAnomalyRule disabled", f.getAbsolutePath(), e);
            this.reader = null;
        }
    }

    @PreDestroy
    void close() {
        if (reader != null) {
            try { reader.close(); } catch (Exception ignored) { }
        }
    }

    @Override
    public boolean isAvailable() {
        return reader != null;
    }

    @Override
    public Optional<GeoPoint> resolve(String ip) {
        if (reader == null || ip == null || ip.isBlank() || ip.contains("*")) {
            return Optional.empty();
        }
        try {
            CityResponse resp = reader.city(InetAddress.getByName(ip));
            if (resp == null || resp.getLocation() == null
                    || resp.getLocation().getLatitude() == null
                    || resp.getLocation().getLongitude() == null) {
                return Optional.empty();
            }
            String country = resp.getCountry() == null ? "UNKNOWN" : resp.getCountry().getIsoCode();
            return Optional.of(new GeoPoint(
                    country == null ? "UNKNOWN" : country,
                    resp.getLocation().getLatitude(),
                    resp.getLocation().getLongitude()));
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for ip={}: {}", ip, e.toString());
            return Optional.empty();
        }
    }
}
