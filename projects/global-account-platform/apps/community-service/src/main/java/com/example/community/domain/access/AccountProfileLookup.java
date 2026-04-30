package com.example.community.domain.access;

/**
 * Port for looking up an account's public display name.
 * Returns {@code null} if unknown or unavailable.
 */
public interface AccountProfileLookup {

    String displayNameOf(String accountId);
}
