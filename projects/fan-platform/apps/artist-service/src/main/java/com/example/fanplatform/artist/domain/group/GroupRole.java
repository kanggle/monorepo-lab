package com.example.fanplatform.artist.domain.group;

/**
 * Role of an artist within a group.
 *
 * <ul>
 *   <li>{@link #LEADER}        — leader; one per group is the convention but
 *                                 not enforced as a hard invariant in v1.</li>
 *   <li>{@link #MEMBER}        — active member.</li>
 *   <li>{@link #FORMER_MEMBER} — left the group (left_at populated).</li>
 * </ul>
 */
public enum GroupRole {
    LEADER,
    MEMBER,
    FORMER_MEMBER
}
