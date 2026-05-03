package com.example.fanplatform.artist.application.port.out;

import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistId;
import com.example.fanplatform.artist.domain.artist.ArtistType;
import com.example.fanplatform.artist.domain.group.ArtistGroup;
import com.example.fanplatform.artist.domain.group.GroupRole;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port: appends the {@code artist.*} event family to the transactional
 * outbox. Implementation under {@code adapter.out.event}. Domain code never
 * touches Kafka directly — outbox is the only path.
 *
 * <p>Topic naming follows {@code platform/event-driven-policy.md}: every Kafka
 * topic name is the envelope's {@code eventType} field plus a {@code .v1}
 * suffix.
 */
public interface ArtistEventPublisher {

    void publishArtistRegistered(Artist artist, String registeredBy);

    void publishArtistPublished(Artist artist);

    void publishArtistUpdated(ArtistId artistId, String tenantId, List<String> changedFields,
                              String updatedBy, Instant occurredAt);

    void publishArtistArchived(Artist artist, String reason, String archivedBy);

    void publishArtistGroupCreated(ArtistGroup group);

    void publishArtistGroupMemberChanged(ArtistGroup group, ArtistId artistId,
                                         GroupRole role, MemberChangeAction action,
                                         Instant occurredAt);

    enum MemberChangeAction { ADDED, REMOVED }
}
