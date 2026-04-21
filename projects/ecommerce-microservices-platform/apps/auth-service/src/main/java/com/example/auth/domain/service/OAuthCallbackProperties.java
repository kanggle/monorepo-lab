package com.example.auth.domain.service;

import java.util.List;

public interface OAuthCallbackProperties {

    List<String> allowedCallbackUrls();

    String redirectUriFor(String provider);
}
