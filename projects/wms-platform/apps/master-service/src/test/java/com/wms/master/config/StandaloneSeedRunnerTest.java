package com.wms.master.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.domain.model.Warehouse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StandaloneSeedRunnerTest {

    private final WarehousePersistencePort persistencePort = mock(WarehousePersistencePort.class);
    private final StandaloneSeedRunner runner = new StandaloneSeedRunner(persistencePort);

    @Test
    void insertsWh01_whenAbsent() throws Exception {
        when(persistencePort.findByCode("WH01")).thenReturn(Optional.empty());
        when(persistencePort.insert(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run();

        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(persistencePort, times(1)).insert(captor.capture());
        Warehouse seed = captor.getValue();
        assertThat(seed.getWarehouseCode()).isEqualTo("WH01");
        assertThat(seed.getName()).isEqualTo("Seoul Main Warehouse");
        assertThat(seed.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(seed.getCreatedBy()).isEqualTo("seed-standalone");
    }

    @Test
    void skipsInsert_whenWh01AlreadyExists() throws Exception {
        Warehouse existing = Warehouse.create(
                "WH01", "Seoul", null, "Asia/Seoul", "earlier-seed");
        when(persistencePort.findByCode("WH01")).thenReturn(Optional.of(existing));

        runner.run();

        verify(persistencePort, never()).insert(any());
    }
}
