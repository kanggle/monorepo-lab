package com.example.user.application.service;

import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSignedUpHandler {

    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void handle(UUID userId, String email, String name) {
        if (userProfileRepository.existsByUserId(userId)) {
            log.info("UserProfile already exists for userId={}, skipping duplicate event", userId);
            return;
        }

        UserProfile profile = UserProfile.create(userId, email, name);
        userProfileRepository.save(profile);
        log.info("Created UserProfile for userId={}", userId);
    }
}
