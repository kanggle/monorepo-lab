package com.example.platform.notification.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationViewTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void unreadItem_omitsReadAtAndDeepLink_perContractNonNull() throws Exception {
        NotificationView view = new NotificationView(
                "01928c4a-7e9f-7c00-9a40-d2b1f5e8c500",
                "erp",
                "APPROVAL_SUBMITTED",
                "결재 요청 도착",
                "구매 품의 결재가 요청되었습니다.",
                null,                       // deepLink absent
                false,                      // unread
                null,                       // readAt absent
                Instant.parse("2026-06-28T01:23:45Z"));

        String json = mapper.writeValueAsString(view);

        assertThat(json).doesNotContain("readAt");
        assertThat(json).doesNotContain("deepLink");
        assertThat(json).contains("\"read\":false");
        assertThat(json).contains("\"sourceDomain\":\"erp\"");
        assertThat(json).contains("\"createdAt\":\"2026-06-28T01:23:45Z\"");
    }

    @Test
    void readItem_includesReadAtAndDeepLinkWhenPresent() throws Exception {
        NotificationView view = new NotificationView(
                "id-2", "fan", "MEMBERSHIP_ACTIVATED", "title", "body",
                "/fan/memberships/1", true,
                Instant.parse("2026-06-28T02:00:00Z"),
                Instant.parse("2026-06-28T01:00:00Z"));

        String json = mapper.writeValueAsString(view);

        assertThat(json).contains("\"readAt\":\"2026-06-28T02:00:00Z\"");
        assertThat(json).contains("\"deepLink\":\"/fan/memberships/1\"");
        assertThat(json).contains("\"read\":true");
    }
}
