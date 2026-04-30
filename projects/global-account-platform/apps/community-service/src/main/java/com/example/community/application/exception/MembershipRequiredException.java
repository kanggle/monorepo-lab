package com.example.community.application.exception;

public class MembershipRequiredException extends RuntimeException {
    public MembershipRequiredException() {
        super("MEMBERSHIP_REQUIRED");
    }
}
