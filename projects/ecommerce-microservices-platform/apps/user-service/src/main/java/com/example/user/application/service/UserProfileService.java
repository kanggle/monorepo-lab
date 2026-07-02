package com.example.user.application.service;

import com.example.user.application.command.UpdateProfileCommand;
import com.example.user.application.event.UserProfileUpdatedSpringEvent;
import com.example.user.application.event.UserWithdrawnSpringEvent;
import com.example.user.application.result.UserCountSummaryResult;
import com.example.user.application.result.UserListPageResult;
import com.example.user.application.result.UserProfileResult;
import com.example.user.application.result.UserProfileSummaryResult;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public UserProfileResult getProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        return UserProfileResult.from(profile);
    }

    @Transactional
    public UserProfileResult updateProfile(UpdateProfileCommand command) {
        UserProfile profile = userProfileRepository.findByUserId(command.userId())
                .orElseThrow(() -> new UserProfileNotFoundException(command.userId()));

        boolean changed = hasChanges(profile, command);

        if (command.nickname() != null) {
            profile.updateNickname(command.nickname());
        }
        if (command.phone() != null) {
            profile.updatePhone(command.phone());
        }
        if (command.profileImageUrl() != null) {
            profile.updateProfileImageUrl(command.profileImageUrl());
        }

        UserProfile saved = userProfileRepository.save(profile);

        if (changed) {
            applicationEventPublisher.publishEvent(new UserProfileUpdatedSpringEvent(
                    saved.getUserId(),
                    saved.getNickname(),
                    saved.getPhone(),
                    saved.getProfileImageUrl(),
                    saved.getUpdatedAt(),
                    TenantContext.currentTenant()
            ));
        }

        return UserProfileResult.from(saved);
    }

    private boolean hasChanges(UserProfile profile, UpdateProfileCommand command) {
        if (command.nickname() != null && !Objects.equals(profile.getNickname(), command.nickname().trim())) {
            return true;
        }
        if (command.phone() != null && !Objects.equals(profile.getPhone(), command.phone().trim())) {
            return true;
        }
        if (command.profileImageUrl() != null && !Objects.equals(profile.getProfileImageUrl(), command.profileImageUrl().trim())) {
            return true;
        }
        return false;
    }

    public UserListPageResult listUsers(ProfileStatus status, String email, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(Math.min(size, 100), 1);
        PageQuery pageQuery = new PageQuery(safePage, safeSize, "createdAt", "DESC");

        PageResult<UserProfile> profiles;

        if (status != null && email != null && !email.isBlank()) {
            profiles = userProfileRepository.findByStatusAndEmailContaining(status, email, pageQuery);
        } else if (status != null) {
            profiles = userProfileRepository.findByStatus(status, pageQuery);
        } else if (email != null && !email.isBlank()) {
            profiles = userProfileRepository.findByEmailContaining(email, pageQuery);
        } else {
            profiles = userProfileRepository.findAll(pageQuery);
        }

        List<UserProfileSummaryResult> content = profiles.content().stream()
                .map(UserProfileSummaryResult::from)
                .toList();
        return new UserListPageResult(content, profiles.totalElements(), profiles.totalPages(), profiles.page(), profiles.size());
    }

    public UserCountSummaryResult getCountSummary() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        ZonedDateTime now = ZonedDateTime.now(kst);
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(kst);
        ZonedDateTime weekStart  = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(kst);
        ZonedDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(kst);

        long total = userProfileRepository.countForTenant();
        long today = userProfileRepository.countForTenantCreatedBetween(todayStart.toInstant(), now.toInstant());
        long week  = userProfileRepository.countForTenantCreatedBetween(weekStart.toInstant(), now.toInstant());
        long month = userProfileRepository.countForTenantCreatedBetween(monthStart.toInstant(), now.toInstant());

        return new UserCountSummaryResult(today, week, month, total);
    }

    /**
     * Withdraw a profile in reaction to an IAM {@code account.deleted} grace-entry
     * ({@code anonymized=false}, ADR-MONO-037 P2). Idempotent + fail-soft (P5): a
     * missing profile or an already-WITHDRAWN profile is a no-op (no throw, no
     * duplicate {@code UserWithdrawn}), so Kafka at-least-once re-delivery and the
     * two-phase {@code account.deleted} emission are safe without a dedup store.
     */
    @Transactional
    public void withdrawProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            log.info("withdrawProfile: no profile for userId={}, no-op (idempotent/fail-soft, ADR-MONO-037 P5)", userId);
            return;
        }
        if (profile.isWithdrawn()) {
            log.info("withdrawProfile: profile userId={} already WITHDRAWN, no-op (idempotent re-delivery)", userId);
            return;
        }

        profile.withdraw();
        userProfileRepository.save(profile);

        applicationEventPublisher.publishEvent(new UserWithdrawnSpringEvent(
                profile.getUserId(),
                profile.getUpdatedAt(),
                TenantContext.currentTenant()
        ));
    }

    /**
     * Anonymize a profile's PII in reaction to an IAM {@code account.deleted} with
     * {@code anonymized=true} (post-grace, ADR-MONO-037 P2/P3 — the standing
     * TASK-BE-258 GDPR consumer obligation). Clears all identity-bearing fields while
     * preserving {@code userId} for FK/audit integrity. Idempotent + fail-soft: a
     * missing profile is a no-op; re-applying clears already-null fields.
     *
     * <p>Scope boundary (ADR-MONO-037 P3): user-service profile PII only.
     * order-service-held PII (shipping addresses, recipient names) is a documented
     * deferred follow-up, not cascaded here.
     */
    @Transactional
    public void anonymizeProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            log.info("anonymizeProfile: no profile for userId={}, no-op (idempotent/fail-soft, ADR-MONO-037 P5)", userId);
            return;
        }

        profile.anonymize();
        userProfileRepository.save(profile);
        log.info("Anonymized UserProfile PII for userId={} (account.deleted anonymized=true, TASK-BE-258 obligation)", userId);
    }
}
