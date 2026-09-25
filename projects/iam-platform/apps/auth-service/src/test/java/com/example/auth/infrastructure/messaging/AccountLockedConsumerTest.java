package com.example.auth.infrastructure.messaging;

import com.example.auth.application.RevokeSessionsOnAccountLockedUseCase;
import com.example.auth.application.command.RevokeSessionsOnAccountLockedCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLockedConsumer 단위 테스트 — TASK-BE-601")
class AccountLockedConsumerTest {

    private static final String EVENT_ID = "0199bbbb-0000-7000-8000-000000000001";
    private static final String ACCOUNT_ID = "0199aaaa-0000-7000-8000-000000000001";

    @Mock private RevokeSessionsOnAccountLockedUseCase useCase;

    private SimpleMeterRegistry meterRegistry;
    private AccountLockedConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new AccountLockedConsumer(new ObjectMapper(), useCase, meterRegistry);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("account.locked", 0, 7L, ACCOUNT_ID, value);
    }

    /** The FLAT wire account-service's outbox publishes (AccountEventFactory#lockedEvent). */
    private static String flat(String eventId, String accountId, String tenantId) {
        StringBuilder json = new StringBuilder("{");
        if (eventId != null) json.append("\"eventId\":\"").append(eventId).append("\",");
        if (accountId != null) json.append("\"accountId\":\"").append(accountId).append("\",");
        if (tenantId != null) json.append("\"tenantId\":\"").append(tenantId).append("\",");
        json.append("\"reasonCode\":\"ADMIN_LOCK\",\"actorType\":\"operator\",")
                .append("\"lockedAt\":\"2026-09-25T01:00:00Z\"}");
        return json.toString();
    }

    private double count(String outcome) {
        return meterRegistry.get(AccountLockedConsumer.OUTCOME_METRIC).tag("outcome", outcome).counter().count();
    }

    @Test
    @DisplayName("flat 봉투 → 명령으로 변환해 유스케이스 호출 (계정·테넌트·eventId·사유·lockedAt)")
    void flatEnvelope_isHandedToTheUseCase() {
        given(useCase.execute(any())).willReturn(new RevokeSessionsOnAccountLockedUseCase.Result(
                RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED, 2, Duration.ofMillis(40)));

        consumer.onMessage(record(flat(EVENT_ID, ACCOUNT_ID, "fan-platform")));

        ArgumentCaptor<RevokeSessionsOnAccountLockedCommand> captor =
                ArgumentCaptor.forClass(RevokeSessionsOnAccountLockedCommand.class);
        verify(useCase).execute(captor.capture());
        RevokeSessionsOnAccountLockedCommand command = captor.getValue();
        assertThat(command.eventId()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(command.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.tenantId()).isEqualTo("fan-platform");
        assertThat(command.reasonCode()).isEqualTo("ADMIN_LOCK");
        assertThat(command.lockedAt()).isEqualTo(Instant.parse("2026-09-25T01:00:00Z"));
        assertThat(count("revoked")).isEqualTo(1.0);
        assertThat(count("duplicate")).isZero();
        assertThat(meterRegistry.get(AccountLockedConsumer.LAG_METRIC).timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("payload 로 감싼 봉투도 읽는다 (security-service 소비자와 같은 관용)")
    void nestedPayloadEnvelope_isAccepted() {
        given(useCase.execute(any())).willReturn(new RevokeSessionsOnAccountLockedUseCase.Result(
                RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED, 0, null));
        String nested = "{\"eventId\":\"" + EVENT_ID + "\",\"payload\":{\"accountId\":\"" + ACCOUNT_ID
                + "\",\"tenantId\":\"wms\",\"reasonCode\":\"AUTO_DETECT\"}}";

        consumer.onMessage(record(nested));

        ArgumentCaptor<RevokeSessionsOnAccountLockedCommand> captor =
                ArgumentCaptor.forClass(RevokeSessionsOnAccountLockedCommand.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("wms");
        assertThat(captor.getValue().lockedAt()).isNull();
    }

    @Test
    @DisplayName("중복 → duplicate 카운터만 오른다 (revoked 로 세지 않는다)")
    void duplicate_isCountedAsDuplicate() {
        given(useCase.execute(any())).willReturn(new RevokeSessionsOnAccountLockedUseCase.Result(
                RevokeSessionsOnAccountLockedUseCase.Outcome.DUPLICATE, 0, Duration.ofMillis(5)));

        consumer.onMessage(record(flat(EVENT_ID, ACCOUNT_ID, "fan-platform")));

        assertThat(count("duplicate")).isEqualTo(1.0);
        assertThat(count("revoked")).isZero();
        assertThat(meterRegistry.get(AccountLockedConsumer.LAG_METRIC).timer().count()).isZero();
    }

    @Test
    @DisplayName("tenantId 없는 봉투 → MissingTenantIdException (DLQ 직행), 폐기 호출 없음")
    void missingTenant_goesToDlq() {
        assertThatThrownBy(() -> consumer.onMessage(record(flat(EVENT_ID, ACCOUNT_ID, null))))
                .isInstanceOf(MissingTenantIdException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("eventId 없음 / UUID 아님 / accountId 없음 / JSON 아님 → InvalidEventPayloadException")
    void invalidEnvelopes_goToDlq() {
        assertThatThrownBy(() -> consumer.onMessage(record(flat(null, ACCOUNT_ID, "fan-platform"))))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.onMessage(record(flat("not-a-uuid", ACCOUNT_ID, "fan-platform"))))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.onMessage(record(flat(EVENT_ID, null, "fan-platform"))))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.onMessage(record("{not json")))
                .isInstanceOf(InvalidEventPayloadException.class);
        assertThatThrownBy(() -> consumer.onMessage(record("[1,2]")))
                .isInstanceOf(InvalidEventPayloadException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("eventVersion: 지원 버전(2)은 처리, 모르는 버전(3)은 DLQ — 추측하지 않는다")
    void eventVersion_isBranchedOn() {
        given(useCase.execute(any())).willReturn(new RevokeSessionsOnAccountLockedUseCase.Result(
                RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED, 0, null));
        String v2 = flat(EVENT_ID, ACCOUNT_ID, "fan-platform").replace("{", "{\"eventVersion\":2,");
        String v3 = flat(EVENT_ID, ACCOUNT_ID, "fan-platform").replace("{", "{\"eventVersion\":3,");

        consumer.onMessage(record(v2));
        assertThatThrownBy(() -> consumer.onMessage(record(v3)))
                .isInstanceOf(InvalidEventPayloadException.class)
                .hasMessageContaining("unsupported event version 3");
        verify(useCase).execute(any());
    }

    @Test
    @DisplayName("폐기 실패 → 예외가 리스너 밖으로 나간다 (에러 핸들러가 재시도 후 DLQ), 카운터 불변")
    void useCaseFailure_propagates() {
        given(useCase.execute(any())).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.onMessage(record(flat(EVENT_ID, ACCOUNT_ID, "fan-platform"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("revoked")).isZero();
        assertThat(count("duplicate")).isZero();
    }
}
