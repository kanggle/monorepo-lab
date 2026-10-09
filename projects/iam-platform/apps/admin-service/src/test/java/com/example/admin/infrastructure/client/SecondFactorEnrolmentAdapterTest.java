package com.example.admin.infrastructure.client;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S5 — {@link SecondFactorEnrolmentAdapter} chunks to the provider's 500-id cap (a larger single
 * call is a 400 at auth-service, admin-to-auth.md) and de-duplicates before asking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SecondFactorEnrolmentAdapter (unit)")
class SecondFactorEnrolmentAdapterTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock AuthServiceClient authServiceClient;

    @Test
    @DisplayName("1001 distinct ids → 3 calls of ≤ 500 · answers unioned")
    @SuppressWarnings("unchecked")
    void chunksToTheProviderCap() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 1001; i++) ids.add("acc-" + i);
        when(authServiceClient.enrolledAmong(anyList()))
                .thenReturn(Set.of("acc-0"), Set.of("acc-500"), Set.of("acc-1000"));

        Set<String> out = new SecondFactorEnrolmentAdapter(operatorRepository, authServiceClient).enrolledAmong(ids);

        ArgumentCaptor<List<String>> calls = ArgumentCaptor.forClass(List.class);
        verify(authServiceClient, times(3)).enrolledAmong(calls.capture());
        assertThat(calls.getAllValues()).extracting(List::size).containsExactly(500, 500, 1);
        assertThat(out).containsExactlyInAnyOrder("acc-0", "acc-500", "acc-1000");
    }

    @Test
    @DisplayName("duplicates collapse before the call")
    @SuppressWarnings("unchecked")
    void dedupes() {
        when(authServiceClient.enrolledAmong(anyList())).thenReturn(Set.of());

        new SecondFactorEnrolmentAdapter(operatorRepository, authServiceClient).enrolledAmong(List.of("a", "a", "b"));

        ArgumentCaptor<List<String>> call = ArgumentCaptor.forClass(List.class);
        verify(authServiceClient).enrolledAmong(call.capture());
        assertThat(call.getValue()).containsExactly("a", "b");
    }
}
