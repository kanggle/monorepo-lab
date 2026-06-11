package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.domain.notification.NotificationPage;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListNotificationsUseCaseTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final ListNotificationsUseCase useCase = new ListNotificationsUseCase(repository);
    private static final ActorContext ACTOR = new ActorContext("acc-1", "fan-platform", Set.of("FAN"));

    @Test
    void scopesQueryToCallerAndPassesStatusAndPaging() {
        NotificationPage page = new NotificationPage(List.of(), 0, 20, 0);
        when(repository.findInbox("fan-platform", "acc-1", NotificationStatus.UNREAD, 0, 20))
                .thenReturn(page);

        NotificationPage result = useCase.list(ACTOR, NotificationStatus.UNREAD, 0, 20);

        assertThat(result).isSameAs(page);
        verify(repository).findInbox("fan-platform", "acc-1", NotificationStatus.UNREAD, 0, 20);
    }

    @Test
    void nullStatusReturnsAllStates() {
        NotificationPage page = new NotificationPage(List.of(), 1, 5, 12);
        when(repository.findInbox("fan-platform", "acc-1", null, 1, 5)).thenReturn(page);

        NotificationPage result = useCase.list(ACTOR, null, 1, 5);

        assertThat(result.totalElements()).isEqualTo(12);
        verify(repository).findInbox("fan-platform", "acc-1", null, 1, 5);
    }
}
