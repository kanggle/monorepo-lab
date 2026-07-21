package com.example.account.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link ProcessedEventStoreImpl} — recovers the dedup-row field-mapping
 * assertion that moved here when the store was extracted from {@code UpdateLastLoginUseCase}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessedEventStoreImpl")
class ProcessedEventStoreImplTest {

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @InjectMocks
    private ProcessedEventStoreImpl store;

    @Test
    @DisplayName("existsByEventId delegates to the JPA repository")
    void existsByEventId_delegates() {
        given(processedEventRepository.existsByEventId("ev-1")).willReturn(true);

        assertThat(store.existsByEventId("ev-1")).isTrue();
    }

    @Test
    @DisplayName("markProcessed saveAndFlushes a dedup row carrying the given eventId + eventType")
    void markProcessed_savesAndFlushesEntity() {
        store.markProcessed("ev-2", "auth.login.succeeded");

        ArgumentCaptor<ProcessedEventJpaEntity> captor =
                ArgumentCaptor.forClass(ProcessedEventJpaEntity.class);
        verify(processedEventRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("ev-2");
        assertThat(captor.getValue().getEventType()).isEqualTo("auth.login.succeeded");
    }
}
