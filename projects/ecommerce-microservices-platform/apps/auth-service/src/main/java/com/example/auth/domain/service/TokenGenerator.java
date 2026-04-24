package com.example.auth.domain.service;

import com.example.auth.domain.entity.User;

public interface TokenGenerator {

    String generateAccessToken(User user);

    long accessTokenTtlSeconds();
}
