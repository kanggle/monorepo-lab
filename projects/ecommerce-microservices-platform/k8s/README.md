# ecommerce — Kubernetes manifests (REFERENCE ONLY)

> **Status: reference / portfolio artifact — NOT an active deployment path.**
> These manifests are kept to demonstrate production-grade Kubernetes packaging. The actual
> runtime for this monorepo (local dev, demo, CI) is **docker-compose** (+ shared Traefik
> `*.local` routing). Nothing in this repository applies these manifests — there is no cluster,
> no GitOps controller, and no `kubectl`/`helm`/`kustomize` step in CI (`.github/workflows`).

This directory is the only Kubernetes footprint in the monorepo (the other six projects ship
none); it arrived with the externally-imported ecommerce prototype.

## Do not GitOps-sync this directory

These manifests will **not** apply cleanly to a fresh cluster as-is. Exclude the directory from
any ArgoCD `Application` / Kustomization root. The blockers below are intentional documentation,
not a TODO list.

## Why they are not turnkey

| Area | Gap |
|---|---|
| **Images** | Deployments reference `image: <svc>:${IMAGE_TAG}` with **no registry prefix** and no committed substitution step. Raw `kubectl apply` yields `InvalidImageName`/`ErrImagePull`. Requires a CI `envsubst`/kustomize pipeline + a registry (or pre-loaded node images). `checksum/config: ${...}` is likewise an unresolved placeholder. |
| **Secrets** | All secrets are Bitnami **`SealedSecret`** CRDs — require the sealed-secrets controller installed, and the ciphertext is sealed for one controller keypair, so it does **not** decrypt on a different cluster. |
| **Datastores** | Postgres/Kafka/Redis/Elasticsearch are treated as **external** (endpoints injected via the `infrastructure-endpoints` ConfigMap); only MinIO has an in-cluster `StatefulSet`. The manifests alone do not stand up a working backing layer. |
| **Ingress / TLS** | `ingress.yaml` needs an ingress controller and a `ecommerce-tls` secret that is **not** in the repo (expects cert-manager or manual provisioning). |
| **Autoscaling** | **No `HorizontalPodAutoscaler`** anywhere — replicas are static (`2`, batch-worker `1`). Adding HPA would also require metrics-server. |
| **Coverage** | Manifests exist for **8 of the 14** services — `batch-worker`, `gateway-service`, `order-service`, `payment-service`, `product-service`, `search-service`, `user-service`, `web-store` (+ the decommissioned `auth-service-deprecated`, marked DO-NOT-APPLY). **Missing:** `notification-service`, `promotion-service`, `review-service`, `shipping-service`, `settlement-service`. This set is a point-in-time snapshot from the prototype-import era and has not tracked the service roster since. |

## What they *do* demonstrate (the point of keeping them)

Well-structured, security-hardened packaging worth reading even though it is not deployed here:

- Pod security: `runAsNonRoot`, non-root uid/gid, `readOnlyRootFilesystem`, `drop: [ALL]`
  capabilities, `seccompProfile: RuntimeDefault`, `automountServiceAccountToken: false`.
- `NetworkPolicy` with a `default-deny` baseline + per-service allow rules.
- `PodDisruptionBudget`, three-tier probes (startup/liveness/readiness), resource requests/limits,
  `RollingUpdate maxUnavailable: 0` (zero-downtime).
- Service-to-service addressing via plain Kubernetes `Service` DNS — **no** Eureka/Consul-style
  discovery (consistent with the compose runtime, which uses Docker embedded DNS).

## To actually deploy (out of scope for this repo)

Would require, as a deliberate initiative (ideally with an ADR, applied consistently rather than
to one imported prototype): an image build + tag-substitution pipeline + registry, plain `Secret`
(or SOPS) instead of cluster-bound SealedSecrets, the 5 missing service manifests, HPA +
metrics-server, ingress controller + cert-manager, and managed/external datastores.

_Last reconciled: TASK-BE-395 (2026-06-17)._
