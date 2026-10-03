package com.example.auth.presentation;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.infrastructure.security.LoginBranding;
import com.example.auth.infrastructure.security.PendingSiteConsentStore;
import com.example.auth.infrastructure.security.PendingSiteConsentStore.PendingSiteConsent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * TASK-BE-616 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 4) — the one-screen «이 사이트 이용
 * 동의» a pool account sees the first time it reaches a consumer site it has never joined.
 *
 * <p>The authorize gate ({@code AuthorizeSessionTenantGate}) parks the authorize request in
 * {@link PendingSiteConsentStore} and redirects here. No form, no password — one decision:
 * <ul>
 *   <li><b>accept</b> → account-service makes the account an ACTIVE member of the site (and publishes
 *       that site's {@code account.created}), then the parked authorize resumes; it now passes the gate
 *       as a member and the token carries the site and the site's seed role.</li>
 *   <li><b>decline</b> → the client gets {@code error=access_denied} (with its {@code state}) on its
 *       registered redirect URI — no code, no token, no membership, and no loop: the gate is not asked
 *       again for this request. The IAM session is untouched (other sites keep working).</li>
 * </ul>
 *
 * <p>The page wears the site's brand (colour, logo) from the parked client's registered settings — the
 * ADR-007 rule: never from the current request. Its subtitle is the consent's own (TASK-BE-619 — «{site}에서
 * 내 IAM 계정을 쓰도록 허용합니다»), not the site's login subtitle. The console never reaches this page
 * (the gate does not map a pool principal onto {@code iam}), and the page itself refuses any session
 * that is not a pool principal.
 *
 * <p>Public on the {@code @Order(0)} web chain like {@code /login} (CSRF on); the checks below are the
 * gate — a direct visit without a parked request or without a pool session gets the «expired» page.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SiteConsentPageController {

    static final String VIEW = "consent";

    private final PendingSiteConsentStore pendingSiteConsentStore;
    private final AccountServicePort accountServicePort;

    @GetMapping("/consent")
    public ModelAndView consentPage(Principal principal, HttpServletRequest request,
                                    HttpServletResponse response) {
        Optional<PendingSiteConsent> pending = pendingSiteConsentStore.find(request, response);
        if (pending.isEmpty() || poolAccountId(principal, pending.get()) == null) {
            return expired(pending);
        }
        return page(pending.get(), null, HttpStatus.OK, siteName(pending.get()));
    }

    /**
     * TASK-BE-619 (owner decision 2026-10-03 «동의 화면 전용 부제») — the site the person is consenting to,
     * by name, for the subtitle «{site}에서 내 IAM 계정을 쓰도록 허용합니다». The name is the parked client's
     * site tenant's {@code display_name} in account-service — the parked client, never the current request
     * (ADR-007 invariant 1). Fail-soft: any failure or a missing name → {@code null}, and the page says
     * «이 사이트» instead (the page must not fail over a label).
     */
    private String siteName(PendingSiteConsent pending) {
        try {
            return accountServicePort.getTenant(pending.siteTenantId())
                    .map(AccountServicePort.TenantLookupResult::displayName)
                    .filter(name -> !name.isBlank())
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("site consent: site name lookup failed for {} — the page says «이 사이트»",
                    pending.siteTenantId(), e);
            return null;
        }
    }

    @PostMapping("/consent")
    public ModelAndView answer(@RequestParam(name = "decision", required = false) String decision,
                               Principal principal, HttpServletRequest request, HttpServletResponse response) {
        Optional<PendingSiteConsent> found = pendingSiteConsentStore.find(request, response);
        String accountId = found.map(p -> poolAccountId(principal, p)).orElse(null);
        if (found.isEmpty() || accountId == null) {
            return expired(found);
        }
        PendingSiteConsent pending = found.get();

        if ("decline".equals(decision)) {
            pendingSiteConsentStore.clear(request, response);
            log.info("site consent declined: account {} on site {} (client {})",
                    accountId, pending.siteTenantId(), pending.client().getClientId());
            return pending.errorRedirect("access_denied", "the user declined to use this site")
                    .map(SiteConsentPageController::redirect)
                    .orElseGet(() -> page(pending, "declined", HttpStatus.OK));
        }
        if (!"accept".equals(decision)) {
            return page(pending, "invalid_decision", HttpStatus.BAD_REQUEST);
        }

        ConsumerSiteMembershipLookupResult result;
        try {
            result = accountServicePort.consentToConsumerSite(pending.siteTenantId(), accountId);
        } catch (AccountServiceUnavailableException e) {
            // The parked request stays: the person can press «accept» again.
            log.warn("site consent: account-service unavailable for account {} on site {}",
                    accountId, pending.siteTenantId(), e);
            return page(pending, "temporarily_unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        pendingSiteConsentStore.clear(request, response);
        if (!result.isActiveMember()) {
            // A suspended site, a membership the site's operator ended (TASK-BE-619 — one the person left
            // themself IS reopened by this consent), an account that is not in the pool:
            // nothing was written and no token will be issued. Tell the client, like a decline.
            log.info("site consent: account {} is not an active member of site {} after consent (status {})",
                    accountId, pending.siteTenantId(), result.membershipStatus());
            return pending.errorRedirect("access_denied", "this account cannot use this site")
                    .map(SiteConsentPageController::redirect)
                    .orElseGet(() -> page(pending, "not_available", HttpStatus.FORBIDDEN));
        }
        log.info("site consent accepted: account {} joined site {} — resuming the authorize request",
                accountId, pending.siteTenantId());
        return redirect(pending.authorizeUrl());
    }

    /**
     * The account id of a pool principal whose consent this page may record for {@code pending}'s site,
     * or {@code null}. A per-site session, an anonymous visitor and the console are all {@code null}.
     */
    private static String poolAccountId(Principal principal, PendingSiteConsent pending) {
        if (!(principal instanceof Authentication authentication)
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)) {
            return null;
        }
        if (!(details.get(PrincipalDetailKeys.TENANT_ID) instanceof String tenant)
                || !TenantContext.isConsumerPool(tenant)
                || !TenantContext.poolPrincipalMapsTo(pending.siteTenantId())) {
            return null;
        }
        return details.get(PrincipalDetailKeys.ACCOUNT_ID) instanceof String id && !id.isBlank() ? id : null;
    }

    private ModelAndView page(PendingSiteConsent pending, String error, HttpStatus status) {
        return page(pending, error, status, siteName(pending));
    }

    private static ModelAndView page(PendingSiteConsent pending, String error, HttpStatus status, String siteName) {
        ModelAndView mav = new ModelAndView(VIEW, status);
        LoginBranding branding = LoginBranding.from(pending.client().getClientSettings());
        mav.addObject("branding", branding);
        mav.addObject("siteName", siteName);
        mav.addObject("consentSubtitle", consentSubtitle(siteName, branding));
        mav.addObject("error", error);
        mav.addObject("expired", false);
        mav.addObject("answerable", !"declined".equals(error) && !"not_available".equals(error));
        return mav;
    }

    private static ModelAndView expired(Optional<PendingSiteConsent> pending) {
        ModelAndView mav = new ModelAndView(VIEW, HttpStatus.BAD_REQUEST);
        mav.addObject("branding", pending.map(p -> LoginBranding.from(p.client().getClientSettings()))
                .orElse(LoginBranding.DEFAULT));
        mav.addObject("siteName", null);
        mav.addObject("consentSubtitle", null);
        mav.addObject("error", null);
        mav.addObject("expired", true);
        mav.addObject("answerable", false);
        return mav;
    }

    /**
     * TASK-BE-619 (owner decision 2026-10-03 «동의 화면 전용 부제») — what is being consented to, in one
     * line, instead of the site's LOGIN subtitle (which told an already-signed-in person to «로그인하세요»).
     * «에서» reads right after any site name (no 받침-dependent particle to pick).
     */
    static String consentSubtitle(String siteName, LoginBranding branding) {
        String site = siteName == null || siteName.isBlank() ? "이 사이트" : siteName;
        return site + "에서 내 " + branding.serviceName() + " 계정을 쓰도록 허용합니다.";
    }

    /** A plain redirect: no model attributes appended, no {@code {…}} template expansion of the URL. */
    private static ModelAndView redirect(String url) {
        RedirectView view = new RedirectView(url);
        view.setExpandUriTemplateVariables(false);
        view.setExposeModelAttributes(false);
        return new ModelAndView(view);
    }
}
