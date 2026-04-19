package com.wms.master;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.master.adapter.in.web.controller.WarehouseController;
import com.wms.master.adapter.in.web.controller.ZoneController;
import com.wms.master.adapter.in.web.filter.IdempotencyFilter;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.port.in.ZoneCrudUseCase;
import com.wms.master.application.port.in.ZoneQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.IdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context under the {@code standalone} profile to catch
 * bean wiring regressions that slice tests miss. Does not exercise any
 * endpoint — that is covered by the controller, security, and filter slice
 * tests.
 */
@SpringBootTest
@ActiveProfiles("standalone")
class ApplicationContextSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void webLayerBeansAreRegistered() {
        assertThat(context.getBean(WarehouseController.class)).isNotNull();
        assertThat(context.getBean(ZoneController.class)).isNotNull();
        FilterRegistrationBean<?> registration =
                context.getBean("idempotencyFilterRegistration", FilterRegistrationBean.class);
        assertThat(registration.getFilter()).isInstanceOf(IdempotencyFilter.class);
    }

    @Test
    void applicationPortsAreWired() {
        assertThat(context.getBean(WarehouseCrudUseCase.class)).isNotNull();
        assertThat(context.getBean(WarehouseQueryUseCase.class)).isNotNull();
        assertThat(context.getBean(ZoneCrudUseCase.class)).isNotNull();
        assertThat(context.getBean(ZoneQueryUseCase.class)).isNotNull();
        assertThat(context.getBean(DomainEventPort.class)).isNotNull();
        assertThat(context.getBean(IdempotencyStore.class)).isNotNull();
    }

    @Test
    void securityFilterChainIsPresent() {
        assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
    }
}
