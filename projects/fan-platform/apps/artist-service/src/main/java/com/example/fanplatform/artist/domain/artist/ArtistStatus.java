package com.example.fanplatform.artist.domain.artist;

/**
 * Lifecycle states of an artist record.
 *
 * <ul>
 *   <li>{@link #DRAFT}     — newly registered. Visible only to admin actors.</li>
 *   <li>{@link #PUBLISHED} — visible to all authenticated callers in the same tenant.</li>
 *   <li>{@link #ARCHIVED}  — retired (former member, ended career, ...). Visible
 *       only to admin actors. Terminal.</li>
 * </ul>
 *
 * <p>Allowed transitions (validated in {@link Artist}):
 * <pre>
 *   DRAFT     → PUBLISHED | ARCHIVED
 *   PUBLISHED → ARCHIVED
 * </pre>
 */
public enum ArtistStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
