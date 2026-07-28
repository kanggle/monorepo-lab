package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.exception.BillingKeyEnrollmentNotFoundException;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CancelBillingKeyEnrollmentUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final ActorContext ACTOR = new ActorContext("acc1", "fan-platform", Set.of("FAN"));

    @Mock BillingKeyEnrollmentRepository repository;

    private CancelBillingKeyEnrollmentUseCase useCase() {
        return new CancelBillingKeyEnrollmentUseCase(repository);
    }

    @Test
    @DisplayName("active enrollment → deactivated, view active=false")
    void deactivates() {
        BillingKeyEnrollment e = BillingKeyEnrollment.enroll(
                "e1", "fan-platform", "acc1", MembershipTier.PREMIUM, "bk", NOW);
        when(repository.findActiveByAccountAndTier("acc1", "fan-platform", MembershipTier.PREMIUM))
                .thenReturn(Optional.of(e));
        when(repository.save(any(BillingKeyEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        BillingKeyEnrollmentView view = useCase().execute(ACTOR, MembershipTier.PREMIUM);

        assertThat(view.active()).isFalse();
        assertThat(e.isActive()).isFalse();
    }

    @Test
    @DisplayName("no active enrollment → 404 BillingKeyEnrollmentNotFound, no save")
    void notFound() {
        when(repository.findActiveByAccountAndTier("acc1", "fan-platform", MembershipTier.PREMIUM))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(ACTOR, MembershipTier.PREMIUM))
                .isInstanceOf(BillingKeyEnrollmentNotFoundException.class);

        verify(repository, never()).save(any());
    }
}
