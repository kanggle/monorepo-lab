# web-store Observability

Defines observability concerns specific to web-store.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Server-side Observability

`web-store` runs as a Next.js standalone server (Node.js). Server-side logs are structured and emitted to stdout (collected by the container runtime).

Key log events:
- OIDC callback errors (`next-auth` session callbacks: `account_type_mismatch`, token exchange failures)
- API client 4xx / 5xx responses from `gateway-service` (logged by the axios interceptor in `@repo/api-client`)
- 401 re-auth redirects triggered by `onAuthError`

# Client-side / RUM

No dedicated RUM agent is wired in v1. Browser-side errors surface via the Next.js error boundary (`error.tsx` per segment) and are visible in browser DevTools / console only.

# Next.js Built-in Metrics

Next.js reports Web Vitals (LCP, FID, CLS) via the `reportWebVitals` hook in `app/layout.tsx`. In v1 these are logged to the browser console; a RUM sink can be wired at `reportWebVitals` without architectural change.

---

# Change Rule

New web-store observability concerns must be documented here before implementation.
