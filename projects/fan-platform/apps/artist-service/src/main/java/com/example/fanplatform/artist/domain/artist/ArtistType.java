package com.example.fanplatform.artist.domain.artist;

/**
 * Discriminator for the artist aggregate.
 *
 * <ul>
 *   <li>{@link #SOLO} — an individual artist with no associated group.</li>
 *   <li>{@link #GROUP_MEMBER} — an individual whose primary identity is part of
 *       an {@code ArtistGroup}. The group itself is a separate aggregate
 *       (see {@code ArtistGroup}); membership lives in {@code GroupMembership}.</li>
 * </ul>
 */
public enum ArtistType {
    SOLO,
    GROUP_MEMBER
}
