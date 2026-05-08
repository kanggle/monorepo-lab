package com.wms.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * admin-service — User / Role / UserRoleAssignment / Setting management
 * (write-side, BE-045) plus future read-model projection consumers (BE-046).
 *
 * <p>Service Type: {@code rest-api} + {@code event-consumer} (dual). The
 * rest-api half is the only surface in BE-045; consumer wiring lands with
 * BE-046.
 *
 * <p>Architecture: <strong>Layered (deliberate exception)</strong> — see
 * {@code specs/services/admin-service/architecture.md § Architecture Style}
 * and {@code projects/wms-platform/PROJECT.md § Overrides}.
 *
 * <p>Authoritative spec: {@code specs/services/admin-service/architecture.md}.
 */
@SpringBootApplication
@EnableScheduling
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
