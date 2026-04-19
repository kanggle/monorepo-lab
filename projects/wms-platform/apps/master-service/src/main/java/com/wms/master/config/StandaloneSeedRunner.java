package com.wms.master.config;

import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.domain.model.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single ACTIVE warehouse ({@code WH01}) when running under the
 * {@code standalone} profile. Mirrors the Flyway-based seed used by the
 * {@code dev} profile so both local-infra-less and docker-backed runs have a
 * usable baseline for manual walk-throughs.
 */
@Component
@Profile("standalone")
class StandaloneSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StandaloneSeedRunner.class);

    private final WarehousePersistencePort persistencePort;

    StandaloneSeedRunner(WarehousePersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (persistencePort.findByCode("WH01").isPresent()) {
            return;
        }
        Warehouse seed = Warehouse.create(
                "WH01",
                "Seoul Main Warehouse",
                "Seoul, Korea",
                "Asia/Seoul",
                "seed-standalone");
        persistencePort.insert(seed);
        log.info("Standalone seed inserted: warehouse WH01");
    }
}
