---
name: secrets-management
description: Secrets storage, sealed secrets, ESO, rotation
category: infra
---

# Skill: Secrets Management

Patterns for storing, injecting, and rotating secrets across environments.

Prerequisite: read `platform/security-rules.md` and `cross-cutting/security-hardening/SKILL.md` before using this skill. CI/CD-side secret usage lives in `infra/ci-cd/SKILL.md`.

---

## Storage by Environment

| Environment | Storage | Tooling |
|---|---|---|
| Local dev | `.env.local` (gitignored) or `@Profile("standalone")` placeholders | none |
| CI | GitHub Actions encrypted secrets | `gh secret set` |
| Staging | Kubernetes Secret encrypted with KMS (SealedSecrets / SOPS) | `kubeseal` / `sops` |
| Production | HashiCorp Vault or cloud KMS-backed secret store | Vault Agent / External Secrets Operator |

**Never** commit plain secrets to git. Use `gitleaks` in pre-commit and CI.

---

## Sealed Secrets Pattern

```bash
# Generate sealed secret
kubectl create secret generic db-credentials \
  --from-literal=username=app \
  --from-literal=password=$(openssl rand -base64 32) \
  --dry-run=client -o yaml \
  | kubeseal --format=yaml > infra/k8s/<env>/sealed-db-credentials.yaml
```

```yaml
# infra/k8s/prod/sealed-db-credentials.yaml (committed to git)
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: db-credentials
  namespace: default
spec:
  encryptedData:
    username: AgB3...
    password: AgC9...
```

The `SealedSecret` is safe to commit — only the cluster's controller can decrypt it.

---

## External Secrets Operator (ESO)

For production, prefer pulling from a central store at runtime:

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: db-credentials
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: db-credentials
  data:
    - secretKey: username
      remoteRef: { key: secret/data/example-service/db, property: username }
    - secretKey: password
      remoteRef: { key: secret/data/example-service/db, property: password }
```

Refresh interval triggers automatic re-sync when the source rotates.

---

## Injecting into Spring Boot

```yaml
# Helm values
env:
  - name: DB_USERNAME
    valueFrom:
      secretKeyRef: { name: db-credentials, key: username }
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef: { name: db-credentials, key: password }
```

```yaml
# application.yml
spring:
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Never use `application-prod.yml` to hold real secrets.

---

## Rotation Policy

| Secret Type | Rotation Interval | Trigger |
|---|---|---|
| Database password | 90 days | Scheduled + on incident |
| API key (external service) | 180 days | Vendor recommendation |
| JWT signing key | 365 days | Scheduled + key compromise |
| Service account token | continuous (short TTL) | K8s automatic |
| OAuth client secret | 365 days | Provider rotation event |

Rotation must be tested in staging before production. Document the rollback procedure in the runbook.

---

## CI Secret Hygiene

- Use environment-scoped secrets, not repo-wide
- Mask secrets in logs (GitHub Actions does this for declared secrets — never `echo` them)
- Use OIDC federation to cloud providers when available (no static long-lived credentials)
- Audit `gh api repos/{owner}/{repo}/actions/secrets` periodically

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Secret in plaintext `application.yml` | Inject via env from K8s secret |
| Unencrypted secret committed to git | `gitleaks` scan + `git filter-repo` to remove |
| Sharing prod secrets via Slack | Use vault one-time URL or `op` CLI |
| Long-lived AWS access keys | Use IAM roles with OIDC federation |
| No rotation tracking | Document last-rotated date in vault metadata |
| Same secret reused across environments | One per environment |

---

## Verification Checklist

- [ ] No plain secret in git history (`gitleaks` clean)
- [ ] All K8s secrets created via SealedSecrets or ESO
- [ ] CI secrets scoped to environment
- [ ] Rotation interval documented per secret
- [ ] Runbook describes rollback for failed rotation
- [ ] OIDC federation used where supported
