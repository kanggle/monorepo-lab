# auth-service (DEPRECATED — DO NOT APPLY)

These manifests are kept for historical reference only. The ecommerce
auth-service component was retired by TASK-BE-132 (PR #150) after the
ecommerce platform cut over to GAP (iam-platform) as the
standard OIDC provider.

The `Deployment` here still references SealedSecrets (`jwt-secret`,
`auth-db-secret`) that no longer exist in `k8s/base/secrets.yaml` —
applying these manifests will fail with `CreateContainerConfigError`.

## Replacement

- Token validation: `gateway-service` reads RS256 JWTs from GAP via
  `OIDC_ISSUER_URL` + `OIDC_JWK_SET_URI` (TASK-MONO-027 / PR #145).
- User-facing sign-in: `web-store` uses NextAuth v5
  with the GAP OIDC provider (TASK-FE-067 / PR #148).
- Spec: `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md`.

## Removal policy

This directory MUST be excluded from any GitOps sync (ArgoCD application
spec / Kustomization root). The retention is purely for git history /
audit; a future cleanup task may remove the directory entirely.
