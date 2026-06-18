# TASK-BE-395 — Label ecommerce k8s manifests as reference-only

**Status:** ready

**Type:** TASK-BE (infra/docs — no production code)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (single doc, no behaviour change)

---

## Goal

The `projects/ecommerce-microservices-platform/k8s/` manifests (imported with the prototype) read
like an active deployment path but are not: the runtime is docker-compose, no CI/GitOps applies
them, and they will not `kubectl apply` cleanly to a fresh cluster. Add a top-level `k8s/README.md`
that records this status + the concrete applyability gaps, so the directory is not mistaken for a
live deploy path (and not GitOps-synced).

## Scope

**In scope:**
1. New `projects/ecommerce-microservices-platform/k8s/README.md` — reference-only status; gap table
   (image `${IMAGE_TAG}`/no-registry, SealedSecret non-portability, external datastores, ingress/TLS
   prereqs, no HPA, 8-of-14 coverage with the 5 missing services named); the security/structure
   value the manifests still demonstrate; "exclude from GitOps sync"; pointer to the deprecated
   sub-README precedent.

**Out of scope:**
- Editing/fixing/deleting any manifest (no `image:`/secret/HPA changes — that would be the separate
  "actually make it deployable" initiative, which needs its own ADR).
- Touching the other six projects (none ship k8s manifests).

## Acceptance Criteria

- **AC-1** — `k8s/README.md` states reference-only + "not applied by any CI/GitOps; runtime is
  docker-compose"; instructs excluding the directory from GitOps sync.
- **AC-2** — Lists the applyability blockers verified by audit: image placeholders/no registry,
  SealedSecret cluster-binding, external datastores (only MinIO in-cluster), ingress controller +
  `ecommerce-tls` + cert-manager, no HPA, and the 8-of-14 coverage gap (5 missing services named).
- **AC-3** — Records what the manifests positively demonstrate (hardened pod security, NetworkPolicy
  default-deny, PDB/probes/limits, plain-Service DNS — no service-discovery registry).
- **AC-4** — Doc-only: `git diff --stat` touches only `k8s/README.md` + this task file. No manifest,
  no CI, no container behaviour change.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` (referenced by
  the deprecated auth-service k8s sub-README; informational).

## Related Contracts

- None (doc-only).

## Edge Cases

- **A future "make it deployable" initiative** — this task deliberately does NOT fix the gaps; it
  documents them. If k8s becomes a real target, that is a separate ADR-gated initiative (image
  pipeline, plain Secrets, missing services, HPA, ingress/cert-manager), ideally applied across
  projects rather than to one imported prototype.

## Failure Scenarios

- **F1 — someone GitOps-syncs the directory** expecting a working deploy → fails on `${IMAGE_TAG}` /
  SealedSecret / missing services. Mitigated by AC-1's explicit "exclude from GitOps sync".
- **F2 — README drifts from the manifests** as services are added → coverage line goes stale. Low
  blast radius (reference-only); the dated "last reconciled" line signals snapshot age.
