package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wms.master.application.command.CreateWarehouseCommand;
import com.wms.master.application.command.DeactivateWarehouseCommand;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.model.Warehouse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Verifies method-level {@code @PreAuthorize} enforcement on {@link WarehouseService}.
 */
@SpringJUnitConfig(WarehouseServiceAuthorizationTest.TestConfig.class)
class WarehouseServiceAuthorizationTest {

    @Autowired
    WarehouseCrudUseCase crudUseCase;

    @Autowired
    WarehouseQueryUseCase queryUseCase;

    @Autowired
    WarehousePersistencePort persistencePort;

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void create_withReadOnlyRole_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void create_withWriteRole_isAllowed() {
        when(persistencePort.insert(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        WarehouseResult result = crudUseCase.create(sampleCreate());

        assertThat(result.warehouseCode()).isEqualTo("WH01");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void deactivate_withWriteRole_isDenied_adminRequired() {
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateWarehouseCommand(UUID.randomUUID(), "closing", 0L, "actor")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void deactivate_withAdminRole_isAllowed() {
        UUID id = UUID.randomUUID();
        Warehouse wh = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "actor");
        when(persistencePort.findById(id)).thenReturn(Optional.of(wh));
        when(persistencePort.update(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        // We only care that the @PreAuthorize lets us through — the domain
        // logic layer beyond is exercised elsewhere.
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateWarehouseCommand(id, "closing", 5L, "actor")))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void list_withReadRole_isAllowed() {
        when(persistencePort.findPage(any(), any())).thenReturn(
                new com.example.common.page.PageResult<>(java.util.List.of(), 0, 20, 0L, 0));

        assertThat(queryUseCase.list(new com.wms.master.application.query.ListWarehousesQuery(
                com.wms.master.application.query.WarehouseListCriteria.any(),
                com.example.common.page.PageQuery.of(0, 20, "updatedAt", "desc"))))
                .isNotNull();
    }

    @Test
    void unauthenticated_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOfAny(AccessDeniedException.class,
                        org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    private static CreateWarehouseCommand sampleCreate() {
        return new CreateWarehouseCommand("WH01", "Seoul", null, "Asia/Seoul", "actor");
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        WarehousePersistencePort persistencePort() {
            return mock(WarehousePersistencePort.class);
        }

        @Bean
        DomainEventPort eventPort() {
            return mock(DomainEventPort.class);
        }

        @Bean
        WarehouseService warehouseService(WarehousePersistencePort persistencePort,
                                          DomainEventPort eventPort) {
            return new WarehouseService(persistencePort, eventPort);
        }
    }
}
