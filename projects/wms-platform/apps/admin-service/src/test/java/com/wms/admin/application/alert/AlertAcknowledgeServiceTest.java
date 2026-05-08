package com.wms.admin.application.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wms.admin.domain.error.AlertNotFoundException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertAcknowledgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock AlertLogRepository alertRepo;

    private AlertAcknowledgeService service;

    @BeforeEach
    void setUp() {
        service = new AlertAcknowledgeService(alertRepo, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acknowledge_setsAcknowledgedAtAndBy() {
        UUID alertId = UUID.randomUUID();
        AlertLogEntity row = new AlertLogEntity(alertId, "LOW_STOCK", null, null, null, null,
                10, 5, NOW.minusSeconds(60), NOW.minusSeconds(60));
        when(alertRepo.findById(alertId)).thenReturn(Optional.of(row));

        AlertLogEntity result = service.acknowledge(alertId, "actor-1");

        assertThat(result.getAcknowledgedAt()).isEqualTo(NOW);
        assertThat(result.getAcknowledgedBy()).isEqualTo("actor-1");
    }

    @Test
    void acknowledge_alreadyAcknowledged_throwsStateTransitionInvalid() {
        UUID alertId = UUID.randomUUID();
        AlertLogEntity row = new AlertLogEntity(alertId, "LOW_STOCK", null, null, null, null,
                10, 5, NOW.minusSeconds(60), NOW.minusSeconds(60));
        row.acknowledge("first-actor", NOW.minusSeconds(30));
        when(alertRepo.findById(alertId)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.acknowledge(alertId, "second-actor"))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void acknowledge_alertNotFound_throws() {
        UUID alertId = UUID.randomUUID();
        when(alertRepo.findById(alertId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(alertId, "actor"))
                .isInstanceOf(AlertNotFoundException.class);
    }
}
