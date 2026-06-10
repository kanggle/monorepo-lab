package com.example.account.application.service;

import com.example.account.application.event.TenantDomainSubscriptionEventPublisher;
import com.example.account.application.exception.SubscriptionAlreadyExistsException;
import com.example.account.application.exception.SubscriptionNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.SubscriptionMutationResult;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.IllegalSubscriptionTransitionException;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-342 (ADR-MONO-023 § 3.3 step 2 — D1/D3/D4): unit coverage for the
 * subscription mutation use-case (subscribe / suspend / resume / cancel),
 * including the state-machine guard + the {@code tenant.subscription.changed}
 * event emission.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TenantDomainSubscriptionMutationUseCaseTest {

    @Mock
    private TenantDomainSubscriptionRepository subscriptionRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantDomainSubscriptionEventPublisher eventPublisher;

    private TenantDomainSubscriptionMutationUseCase useCase() {
        return new TenantDomainSubscriptionMutationUseCase(
                subscriptionRepository, tenantRepository, eventPublisher);
    }

    private static TenantDomainSubscription sub(String tenantId, String domainKey, SubscriptionStatus status) {
        Instant t = Instant.parse("2026-06-10T00:00:00Z");
        return TenantDomainSubscription.reconstitute(new TenantId(tenantId), domainKey, status, t, t);
    }

    @Nested
    @DisplayName("subscribe (create)")
    class Subscribe {

        @Test
        @DisplayName("new subscription → saved ACTIVE + event with previousStatus=null")
        void subscribe_createsActiveAndEmitsEvent() {
            when(tenantRepository.existsById(any())).thenReturn(true);
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "scm"))
                    .thenReturn(Optional.empty());

            SubscriptionMutationResult result = useCase()
                    .subscribe("acme", "scm", null, "operator", "op-1", "new contract");

            assertThat(result.previousStatus()).isNull();
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

            ArgumentCaptor<TenantDomainSubscription> saved =
                    ArgumentCaptor.forClass(TenantDomainSubscription.class);
            verify(subscriptionRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(saved.getValue().getDomainKey()).isEqualTo("scm");

            verify(eventPublisher).publishSubscriptionChanged(
                    eq("acme"), eq("scm"), isNull(), eq("ACTIVE"),
                    eq("new contract"), eq("operator"), eq("op-1"), any());
        }

        @Test
        @DisplayName("explicit PENDING status is honored")
        void subscribe_pending() {
            when(tenantRepository.existsById(any())).thenReturn(true);
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "scm"))
                    .thenReturn(Optional.empty());

            SubscriptionMutationResult result = useCase()
                    .subscribe("acme", "scm", SubscriptionStatus.PENDING, "operator", "op-1", null);

            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PENDING);
        }

        @Test
        @DisplayName("unknown tenant → TenantNotFoundException, no save/event")
        void subscribe_unknownTenant() {
            when(tenantRepository.existsById(any())).thenReturn(false);

            assertThatThrownBy(() -> useCase().subscribe("ghost", "scm", null, "operator", "op-1", null))
                    .isInstanceOf(TenantNotFoundException.class);

            verify(subscriptionRepository, never()).save(any());
            verify(eventPublisher, never()).publishSubscriptionChanged(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("existing subscription → SubscriptionAlreadyExistsException, no save/event")
        void subscribe_alreadyExists() {
            when(tenantRepository.existsById(any())).thenReturn(true);
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "wms"))
                    .thenReturn(Optional.of(sub("acme", "wms", SubscriptionStatus.ACTIVE)));

            assertThatThrownBy(() -> useCase().subscribe("acme", "wms", null, "operator", "op-1", null))
                    .isInstanceOf(SubscriptionAlreadyExistsException.class);

            verify(subscriptionRepository, never()).save(any());
            verify(eventPublisher, never()).publishSubscriptionChanged(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("non-creatable status (SUSPENDED) → IllegalArgumentException (→400)")
        void subscribe_nonCreatableStatus() {
            when(tenantRepository.existsById(any())).thenReturn(true);
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "scm"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase()
                    .subscribe("acme", "scm", SubscriptionStatus.SUSPENDED, "operator", "op-1", null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("changeStatus (suspend / resume / cancel)")
    class ChangeStatus {

        @Test
        @DisplayName("ACTIVE → SUSPENDED → saved + event previous=ACTIVE current=SUSPENDED")
        void suspend() {
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "wms"))
                    .thenReturn(Optional.of(sub("acme", "wms", SubscriptionStatus.ACTIVE)));

            SubscriptionMutationResult result = useCase()
                    .changeStatus("acme", "wms", SubscriptionStatus.SUSPENDED, "operator", "op-1", "past due");

            assertThat(result.previousStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);

            ArgumentCaptor<TenantDomainSubscription> saved =
                    ArgumentCaptor.forClass(TenantDomainSubscription.class);
            verify(subscriptionRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);

            verify(eventPublisher).publishSubscriptionChanged(
                    eq("acme"), eq("wms"), eq("ACTIVE"), eq("SUSPENDED"),
                    eq("past due"), eq("operator"), eq("op-1"), any());
        }

        @Test
        @DisplayName("SUSPENDED → ACTIVE (resume) is legal")
        void resume() {
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "wms"))
                    .thenReturn(Optional.of(sub("acme", "wms", SubscriptionStatus.SUSPENDED)));

            SubscriptionMutationResult result = useCase()
                    .changeStatus("acme", "wms", SubscriptionStatus.ACTIVE, "operator", "op-1", null);

            assertThat(result.previousStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            verify(subscriptionRepository).save(any());
        }

        @Test
        @DisplayName("ACTIVE → CANCELLED (cancel) is legal")
        void cancel() {
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "wms"))
                    .thenReturn(Optional.of(sub("acme", "wms", SubscriptionStatus.ACTIVE)));

            SubscriptionMutationResult result = useCase()
                    .changeStatus("acme", "wms", SubscriptionStatus.CANCELLED, "operator", "op-1", null);

            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        }

        @Test
        @DisplayName("missing subscription → SubscriptionNotFoundException, no save/event")
        void notFound() {
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "erp"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase()
                    .changeStatus("acme", "erp", SubscriptionStatus.SUSPENDED, "operator", "op-1", null))
                    .isInstanceOf(SubscriptionNotFoundException.class);

            verify(subscriptionRepository, never()).save(any());
            verify(eventPublisher, never()).publishSubscriptionChanged(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("illegal transition (CANCELLED → ACTIVE) → IllegalSubscriptionTransitionException, no save/event")
        void illegalTransition() {
            when(subscriptionRepository.findByTenantIdAndDomainKey("acme", "wms"))
                    .thenReturn(Optional.of(sub("acme", "wms", SubscriptionStatus.CANCELLED)));

            assertThatThrownBy(() -> useCase()
                    .changeStatus("acme", "wms", SubscriptionStatus.ACTIVE, "operator", "op-1", null))
                    .isInstanceOf(IllegalSubscriptionTransitionException.class);

            verify(subscriptionRepository, never()).save(any());
            verify(eventPublisher, never()).publishSubscriptionChanged(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
