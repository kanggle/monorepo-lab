package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EnrollBillingKeyUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final ActorContext ACTOR = new ActorContext("acc1", "fan-platform", Set.of("FAN"));

    @Mock BillingKeyEnrollmentRepository repository;
    @Captor ArgumentCaptor<BillingKeyEnrollment> captor;

    private EnrollBillingKeyUseCase useCase() {
        return new EnrollBillingKeyUseCase(repository, () -> NOW);
    }

    @Test
    @DisplayName("creates a new ACTIVE enrollment; view carries no key; the key is stored on the entity")
    void createsNew() {
        when(repository.save(any(BillingKeyEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        BillingKeyEnrollmentView view = useCase().execute(
                new EnrollBillingKeyCommand(ACTOR, MembershipTier.PREMIUM, "bk_secret"));

        assertThat(view.tier()).isEqualTo(MembershipTier.PREMIUM);
        assertThat(view.active()).isTrue();
        assertThat(view.createdAt()).isEqualTo(NOW);
        // Always deactivates first (idempotent even when there is nothing to deactivate).
        verify(repository).deactivateActive("acc1", "fan-platform", MembershipTier.PREMIUM);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBillingKey()).isEqualTo("bk_secret");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("replace path: deactivates the tier's active enrollment then inserts exactly one new row")
    void replacesExisting() {
        when(repository.save(any(BillingKeyEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase().execute(new EnrollBillingKeyCommand(ACTOR, MembershipTier.PREMIUM, "bk_new"));

        // The immediate bulk deactivation runs before the single insert (never stacked).
        verify(repository).deactivateActive("acc1", "fan-platform", MembershipTier.PREMIUM);
        verify(repository, times(1)).save(any(BillingKeyEnrollment.class));
    }
}
