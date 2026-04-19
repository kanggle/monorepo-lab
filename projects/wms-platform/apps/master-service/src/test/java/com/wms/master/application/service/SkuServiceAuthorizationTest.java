package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wms.master.application.command.CreateSkuCommand;
import com.wms.master.application.command.DeactivateSkuCommand;
import com.wms.master.application.port.in.SkuCrudUseCase;
import com.wms.master.application.port.in.SkuQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.result.SkuResult;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Verifies method-level {@code @PreAuthorize} enforcement on {@link SkuService}.
 * Mirrors {@link WarehouseServiceAuthorizationTest}; injection is by use-case
 * interface because {@code @PreAuthorize} generates a JDK dynamic proxy.
 */
@SpringJUnitConfig(SkuServiceAuthorizationTest.TestConfig.class)
class SkuServiceAuthorizationTest {

    @Autowired
    SkuCrudUseCase crudUseCase;

    @Autowired
    SkuQueryUseCase queryUseCase;

    @Autowired
    SkuPersistencePort skuPersistencePort;

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void create_withReadOnlyRole_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void create_withWriteRole_isAllowed() {
        when(skuPersistencePort.insert(any(Sku.class))).thenAnswer(inv -> inv.getArgument(0));

        SkuResult result = crudUseCase.create(sampleCreate());
        assertThat(result.skuCode()).isEqualTo("SKU-001");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void deactivate_withWriteRole_isDenied_adminRequired() {
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateSkuCommand(UUID.randomUUID(), "obsolete", 0L, "actor")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void deactivate_withAdminRole_isAllowed_adminPassesAuthorizationGate() {
        UUID id = UUID.randomUUID();
        Sku sku = Sku.create("SKU-001", "Name", null, null,
                BaseUom.EA, TrackingType.NONE, null, null, null, null, "actor");
        when(skuPersistencePort.findById(id)).thenReturn(Optional.of(sku));

        // Authorization gate passes — the command fails further in with
        // version mismatch (not AccessDeniedException), which is what we want
        // to assert: the @PreAuthorize allowed the call through.
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateSkuCommand(id, "obsolete", 5L, "actor")))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void findById_withReadRole_isAllowed() {
        UUID id = UUID.randomUUID();
        Sku sku = Sku.create("SKU-001", "Name", null, null,
                BaseUom.EA, TrackingType.NONE, null, null, null, null, "actor");
        when(skuPersistencePort.findById(id)).thenReturn(Optional.of(sku));

        SkuResult result = queryUseCase.findById(id);
        assertThat(result.skuCode()).isEqualTo("SKU-001");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void findByBarcode_withReadRole_isAllowed() {
        Sku sku = Sku.create("SKU-001", "Name", null, "8801234567890",
                BaseUom.EA, TrackingType.NONE, null, null, null, null, "actor");
        when(skuPersistencePort.findByBarcode("8801234567890")).thenReturn(Optional.of(sku));

        SkuResult result = queryUseCase.findByBarcode("8801234567890");
        assertThat(result.skuCode()).isEqualTo("SKU-001");
    }

    @Test
    void unauthenticated_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOfAny(AccessDeniedException.class,
                        AuthenticationCredentialsNotFoundException.class);
    }

    private static CreateSkuCommand sampleCreate() {
        return new CreateSkuCommand("SKU-001", "Name", null, null,
                BaseUom.EA, TrackingType.NONE, null, null, null, null, "actor");
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        SkuPersistencePort skuPersistencePort() {
            return mock(SkuPersistencePort.class);
        }

        @Bean
        DomainEventPort eventPort() {
            return mock(DomainEventPort.class);
        }

        @Bean
        SkuService skuService(SkuPersistencePort skuPersistencePort, DomainEventPort eventPort) {
            return new SkuService(skuPersistencePort, eventPort);
        }
    }
}
