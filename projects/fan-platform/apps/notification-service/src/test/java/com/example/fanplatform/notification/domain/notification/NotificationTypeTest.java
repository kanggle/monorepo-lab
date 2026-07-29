package com.example.fanplatform.notification.domain.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTypeTest {

    @Test
    void mapsActivatedToWelcome() {
        assertThat(NotificationType.fromEventType("fan.membership.activated"))
                .isEqualTo(NotificationType.WELCOME);
    }

    @Test
    void mapsCanceledToCancellation() {
        assertThat(NotificationType.fromEventType("fan.membership.canceled"))
                .isEqualTo(NotificationType.CANCELLATION);
    }

    @Test
    void mapsExpiredToExpiryReminder() {
        // TASK-FAN-BE-014: the producer's expiry sweeper now emits expired.v1.
        assertThat(NotificationType.fromEventType("fan.membership.expired"))
                .isEqualTo(NotificationType.EXPIRY_REMINDER);
    }

    @Test
    void mapsReactionAddedToReactionBadge() {
        // TASK-FAN-BE-026: community.reaction.added has exactly one recipient role
        // (the post author), so the event type alone determines the notification type.
        assertThat(NotificationType.fromEventType("community.reaction.added"))
                .isEqualTo(NotificationType.REACTION_BADGE);
    }

    @Test
    void rejectsCommentAddedBecauseItIsRecipientRoleDependent() {
        // community.comment.added produces REPLY (to the post author) and/or MENTION
        // (per mentioned account) from ONE event, so it deliberately has no
        // eventType→type mapping; HandleCommunityEventUseCase picks per recipient.
        assertThatThrownBy(() -> NotificationType.fromEventType("community.comment.added"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REPLY or MENTION");
    }

    @Test
    void rejectsUnknownTypes() {
        assertThatThrownBy(() -> NotificationType.fromEventType("something.else"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
