# Service Type: ML Pipeline

Normative requirements for any service whose `Service Type` is `ml-pipeline`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

An `ml-pipeline` service trains, evaluates, hosts, or serves machine learning models. It may include data ingestion, feature engineering, training, evaluation, deployment, and online or batch inference.

This service type is **optional** and currently has no implementations in this monorepo. The first ML service activates this spec.

---

# Mandatory Separation: Train vs Serve

A single deployment unit MUST NOT do both training and online serving. Split into at least:
- **Training pipeline**: scheduled (`batch-job` style) — produces model artifacts
- **Inference service**: online (`rest-api` or `grpc-service` style) — consumes model artifacts

A single repository may contain both, but each gets its own deployment unit, its own resource sizing, and its own SLO.

---

# Model Artifact Versioning

## Storage
- Model artifacts MUST be stored in an immutable, content-addressed registry (MLflow, S3 with versioning, or equivalent)
- Artifact metadata MUST include: model name, version, training dataset checksum, training run ID, evaluation metrics, owning team

## Promotion
- Models progress through stages: `dev` → `staging` → `production`
- Promotion requires recorded approval (PR or signed metadata entry)
- Rollback is one operation: switch the production pointer to the previous version

## Reproducibility
- Training runs MUST be reproducible from a recorded random seed, dataset checksum, and code commit hash
- Training code, data, and hyperparameters MUST be tagged with the model version

---

# Feature Store

When features are shared across models or between training and serving:
- Features MUST live in a feature store (Feast, Tecton, or in-house)
- Online and offline features MUST be derived from the same feature definition (no skew)
- Feature documentation includes owner, freshness SLO, and known invariants

If a feature store is not yet in place, document features inline in the service spec and create a follow-up task to migrate.

---

# Inference Service Requirements

When the inference component is online:
- It inherits all `rest-api` or `grpc-service` requirements from the corresponding service-type spec
- Additionally:
  - p99 inference latency SLO MUST be declared in `specs/services/<service>/slo.md`
  - Model loading happens at startup; the service refuses traffic until loaded (readiness probe)
  - Hot model swap (without restart) is allowed via a guarded admin endpoint

---

# Training Pipeline Requirements

When the training component is scheduled:
- It inherits all `batch-job` requirements
- Additionally:
  - Each run records evaluation metrics to the model registry
  - Promotion to `staging`/`production` is blocked unless metrics meet declared thresholds
  - Training data lineage (source dataset version + transformation script hash) is recorded

---

# Observability

| Signal | Where | Threshold |
|---|---|---|
| Inference latency p50/p95/p99 | per inference service | per service SLO |
| Inference error rate | per inference service | < 1% |
| Prediction distribution drift | offline dashboard | alert on > 2 sigma shift |
| Feature freshness | feature store | < freshness SLO |
| Training success rate | training pipeline | 100% over rolling window |

Drift detection is mandatory before a model reaches production traffic.

---

# Privacy and Compliance

- PII MUST NOT be logged from inference requests
- Training data containing PII MUST be processed under documented purpose with retention limits
- Model artifacts MUST NOT memorize raw PII (verify via test set leakage check for sensitive features)

---

# Allowed Patterns

- Python-based training (Spring Boot is not required for training)
- Spring Boot or Python-based inference (whichever the team can operate)
- Online inference behind REST or gRPC
- Batch inference as a `batch-job`
- A/B testing via traffic split at the gateway or service mesh

---

# Forbidden Patterns

- Online serving from a notebook or ad-hoc script
- Loading a model from an unversioned filesystem path in production
- Skipping evaluation metric thresholds when promoting
- Reading prod features from a different definition than training features

---

# Testing Requirements

- Unit tests for feature transformations
- Snapshot tests for model input/output schema
- Evaluation tests against a held-out dataset, run on every training PR
- Drift detection test in production (synthetic baseline vs current distribution)

---

# Default Skill Set

`service-types/ml-pipeline-setup`, matched architecture skill (typically `layered` or `clean`), `cross-cutting/observability-setup`, `cross-cutting/security-hardening`, `backend/testing-backend` (if Java) or service's language test framework

---

# Acceptance for a New ML Pipeline Service

- [ ] Train and serve are separate deployment units
- [ ] Model artifacts versioned in a registry with metadata
- [ ] Promotion gated on evaluation metrics
- [ ] Feature definitions documented (or in feature store)
- [ ] Inference SLO declared
- [ ] Drift detection in place
- [ ] PII handling reviewed
