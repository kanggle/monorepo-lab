package com.example.fanplatform.notification.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MembershipEventParserTest {

    private final MembershipEventParser parser = new MembershipEventParser(new ObjectMapper());

    private static String activatedEnvelope() {
        return """
                {"eventId":"evt-1","eventType":"fan.membership.activated","source":"fan-platform-membership-service",
                 "occurredAt":"2026-06-11T00:00:00Z","schemaVersion":1,"partitionKey":"mem-1",
                 "payload":{"membershipId":"mem-1","tenantId":"fan-platform","accountId":"acc-1",
                   "tier":"PREMIUM","planMonths":1,"validFrom":"2026-06-11T00:00:00Z",
                   "validTo":"2026-07-11T00:00:00Z","occurredAt":"2026-06-11T00:00:00Z"}}
                """;
    }

    private static String canceledEnvelope() {
        return """
                {"eventId":"evt-2","eventType":"fan.membership.canceled","source":"fan-platform-membership-service",
                 "occurredAt":"2026-06-11T12:00:00Z","schemaVersion":1,"partitionKey":"mem-1",
                 "payload":{"membershipId":"mem-1","tenantId":"fan-platform","accountId":"acc-1",
                   "tier":"PREMIUM","reason":"user requested","canceledAt":"2026-06-11T12:00:00Z",
                   "occurredAt":"2026-06-11T12:00:00Z"}}
                """;
    }

    @Test
    @DisplayName("parses an activated envelope into a MembershipEvent")
    void parsesActivated() {
        MembershipEvent e = parser.parse(activatedEnvelope());
        assertThat(e.eventId()).isEqualTo("evt-1");
        assertThat(e.eventType()).isEqualTo("fan.membership.activated");
        assertThat(e.tenantId()).isEqualTo("fan-platform");
        assertThat(e.accountId()).isEqualTo("acc-1");
        assertThat(e.membershipId()).isEqualTo("mem-1");
        assertThat(e.tier()).isEqualTo("PREMIUM");
        assertThat(e.planMonths()).isEqualTo(1);
        assertThat(e.validFrom()).isEqualTo(Instant.parse("2026-06-11T00:00:00Z"));
        assertThat(e.validTo()).isEqualTo(Instant.parse("2026-07-11T00:00:00Z"));
    }

    @Test
    @DisplayName("parses a canceled envelope including the optional reason")
    void parsesCanceled() {
        MembershipEvent e = parser.parse(canceledEnvelope());
        assertThat(e.eventType()).isEqualTo("fan.membership.canceled");
        assertThat(e.reason()).isEqualTo("user requested");
        assertThat(e.canceledAt()).isEqualTo(Instant.parse("2026-06-11T12:00:00Z"));
    }

    @Test
    @DisplayName("tolerates unknown payload fields (forward compatibility)")
    void toleratesUnknownFields() {
        String env = activatedEnvelope().replace("\"tier\":\"PREMIUM\"",
                "\"tier\":\"PREMIUM\",\"futureField\":\"ignored\"");
        assertThat(parser.parse(env).tier()).isEqualTo("PREMIUM");
    }

    @Test
    @DisplayName("unsupported schemaVersion → UnsupportedSchemaVersionException")
    void unsupportedSchema() {
        String env = activatedEnvelope().replace("\"schemaVersion\":1", "\"schemaVersion\":99");
        assertThatThrownBy(() -> parser.parse(env))
                .isInstanceOf(UnsupportedSchemaVersionException.class);
    }

    @Test
    @DisplayName("unparseable JSON → MalformedEventException")
    void malformedJson() {
        assertThatThrownBy(() -> parser.parse("this is not json {{{"))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    @DisplayName("missing payload field → MalformedEventException")
    void missingRequiredField() {
        String env = activatedEnvelope().replace("\"accountId\":\"acc-1\",", "");
        assertThatThrownBy(() -> parser.parse(env))
                .isInstanceOf(MalformedEventException.class);
    }
}
