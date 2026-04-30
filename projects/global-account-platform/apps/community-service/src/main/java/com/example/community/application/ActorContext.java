package com.example.community.application;

import java.util.Set;

public record ActorContext(String accountId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
