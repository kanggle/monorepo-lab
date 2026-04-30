---
name: ml-pipeline-setup
description: Set up an `ml-pipeline` service end-to-end
category: service-types
---

# Skill: ML Pipeline Service Setup

Implementation orchestration for an `ml-pipeline` service. Use when training, hosting, or serving ML models.

Prerequisite: read `platform/service-types/ml-pipeline.md` before using this skill. No ML services in this monorepo yet — this skill activates with the first one.

---

## Orchestration Order

1. **Split decision** — train and serve are separate deployment units; document each
2. **Model registry** — pick MLflow, S3 with versioning, or equivalent; document in `architecture.md`
3. **Feature definitions** — inline in spec, or in a feature store; document owner and freshness SLO
4. **Training pipeline** — inherits all `batch-job` requirements (`service-types/batch-job-setup/SKILL.md`)
5. **Inference service** — inherits all `rest-api` or `grpc-service` requirements
6. **Promotion gates** — evaluation metric thresholds enforced before staging/production
7. **Drift detection** — baseline distribution recorded, alert on shift
8. **Privacy review** — PII not logged from inference, retention policy on training data
9. **Observability** — inference latency, error rate, drift metrics
10. **Tests** — feature transformation tests, schema snapshot tests, evaluation tests on held-out set

---

## Repository Layout

```
services/
  recommendation/
    training/                 # batch-job, Python or Spring Boot
      Dockerfile
      pyproject.toml
      src/
      tests/
    inference/                # rest-api or grpc-service
      Dockerfile
      build.gradle
      src/main/java/...
      src/test/java/...
    feature-definitions/      # YAML or code, single source of truth
specs/services/recommendation/
  architecture.md             # Service Type: ml-pipeline
  slo.md                      # inference SLO
```

Each component has its own CI pipeline and deploy target.

---

## Model Artifact Metadata

Every artifact written to the registry includes:

```json
{
  "model_name": "product-recommender",
  "version": "2026.04.12-rc1",
  "training_run_id": "mlflow://abc123",
  "code_commit": "a1b2c3d4",
  "training_dataset": {
    "name": "user_clicks_v3",
    "checksum": "sha256:...",
    "row_count": 12345678
  },
  "hyperparameters": { "lr": 0.001, "epochs": 20 },
  "evaluation": {
    "auc": 0.83,
    "precision_at_10": 0.42
  },
  "owner_team": "growth-ml",
  "promoted_to": "dev"
}
```

Promotion to `production` writes a new entry, never mutates an existing one.

---

## Promotion Gate

```python
def promote(version: str, target: str) -> None:
    metadata = registry.get(version)
    threshold = THRESHOLDS[target]   # e.g., {"auc": 0.80, "precision_at_10": 0.40}
    for metric, min_value in threshold.items():
        actual = metadata["evaluation"][metric]
        if actual < min_value:
            raise PromotionRejected(f"{metric}={actual} < threshold {min_value}")
    registry.promote(version, target)
```

The promotion call requires recorded approval (PR merge or signed metadata entry).

---

## Inference Service (Spring Boot example)

```java
@Service
public class RecommendationModelHolder {

    private volatile RecommendationModel current;

    @PostConstruct
    public void loadInitial() {
        current = modelRegistry.loadProduction("product-recommender");
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void hotSwap(String version) {
        RecommendationModel next = modelRegistry.load("product-recommender", version);
        current = next;   // atomic reference swap
    }

    public List<ProductId> recommend(UserId userId, int k) {
        return current.predict(userId, k);
    }
}
```

The readiness probe must return 503 until `current` is loaded.

---

## Drift Detection

```python
# Run periodically (batch-job)
def detect_drift() -> None:
    baseline = registry.get_baseline_distribution("product-recommender")
    current = sample_recent_predictions(window="1d")
    psi = population_stability_index(baseline, current)
    if psi > 0.2:
        alert("PSI={psi:.3f} indicates drift", severity="warning")
    if psi > 0.5:
        alert("PSI={psi:.3f} indicates strong drift", severity="critical")
```

---

## Self-Review Checklist

Verify against `platform/service-types/ml-pipeline.md` Acceptance section. Specifically:

- [ ] Train and serve are separate deployment units
- [ ] Every artifact in the registry has full metadata
- [ ] Promotion gated on metric thresholds (tested with a failing case)
- [ ] Feature definitions documented or in feature store
- [ ] Inference SLO declared in `slo.md`
- [ ] Drift detection wired
- [ ] PII handling reviewed and approved
- [ ] Hot swap tested without restart
