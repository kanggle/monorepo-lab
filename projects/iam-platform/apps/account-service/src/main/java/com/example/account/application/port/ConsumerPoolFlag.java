package com.example.account.application.port;

/**
 * TASK-BE-614 — the {@code iam.consumer-pool.enabled} feature flag. Default {@code true} since
 * TASK-BE-616 (was {@code false}).
 *
 * <p>On: a signup from a consumer site is born into the pool, and the site-scoped account lookups
 * include that site's pool members (multi-tenancy.md § 소비자 계정 풀 § 2 · § 5). It was introduced
 * off because pool <i>login</i> ({@code TASK-BE-615}) and the first-visit consent screen
 * ({@code TASK-BE-616}) had not landed — turning it on earlier would have let people sign up into an
 * account they could not log in to, or could not take to the other consumer site. TASK-BE-616 ships
 * the consent screen and turns it on, in the same change that renames the store's login page to
 * «IAM» (ADR-007 § 값 변경 2026-10-01 — the name says «one account» exactly when it is one).
 *
 * <p>Off ({@code IAM_CONSUMER_POOL_ENABLED=false}) remains a kill switch: signup, the site-scoped
 * lookups and {@code account.created} then behave exactly as before ADR-MONO-078. Pool accounts that
 * already exist keep logging in and keep consenting either way — the login, membership read and
 * consent write are not behind this flag.
 */
public interface ConsumerPoolFlag {

    boolean isEnabled();
}
