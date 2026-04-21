package com.example.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationTemplate 도메인 모델 단위 테스트")
class NotificationTemplateTest {

    @Test
    @DisplayName("템플릿 생성 시 ID와 타임스탬프가 설정된다")
    void create_initializesFields() {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Order {{orderId}}", "Your order {{orderId}} has been placed.");

        assertThat(template.getTemplateId()).isNotNull();
        assertThat(template.getType()).isEqualTo(TemplateType.ORDER_PLACED);
        assertThat(template.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(template.getCreatedAt()).isNotNull();
        assertThat(template.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("renderSubject가 변수를 올바르게 치환한다")
    void renderSubject_replacesPlaceholders() {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Order {{orderId}} confirmed", "Body");

        String result = template.renderSubject(Map.of("orderId", "ORD-123"));

        assertThat(result).isEqualTo("Order ORD-123 confirmed");
    }

    @Test
    @DisplayName("renderBody가 변수를 올바르게 치환한다")
    void renderBody_replacesPlaceholders() {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.WELCOME, NotificationChannel.EMAIL,
                "Welcome", "Hello {{name}}, welcome to our service!");

        String result = template.renderBody(Map.of("name", "John"));

        assertThat(result).isEqualTo("Hello John, welcome to our service!");
    }

    @Test
    @DisplayName("변수가 없는 플레이스홀더는 빈 문자열로 치환된다")
    void renderBody_missingVariable_replacesWithEmpty() {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.WELCOME, NotificationChannel.EMAIL,
                "Welcome", "Hello {{name}}!");

        String result = template.renderBody(Map.of());

        assertThat(result).isEqualTo("Hello !");
    }

    @Test
    @DisplayName("update가 subject, body, updatedAt을 변경한다")
    void update_changesFields() {
        NotificationTemplate template = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Old subject", "Old body");

        template.update("New subject", "New body");

        assertThat(template.getSubject()).isEqualTo("New subject");
        assertThat(template.getBody()).isEqualTo("New body");
    }
}
