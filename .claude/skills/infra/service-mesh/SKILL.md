---
name: service-mesh
description: Service mesh (Linkerd/Istio) mTLS, traffic split, authz
category: infra
---

# Skill: Service Mesh

Patterns for enabling mTLS, traffic policy, and observability via a service mesh (Istio or Linkerd).

Prerequisite: read `specs/platform/architecture.md`, `specs/platform/security-rules.md`, and `cross-cutting/security-hardening/SKILL.md` before using this skill. Service-mesh adoption is **optional** — use this skill only when zero-trust mTLS or advanced traffic shaping is required.

---

## When to Adopt

Adopt a service mesh when **two or more** of the following apply:
- Zero-trust mandate (mTLS between every pair of services)
- Need for canary or blue-green at the network layer (independent of app code)
- Cross-cluster service discovery
- Per-route circuit breaking and retries managed centrally
- Mandatory uniform telemetry (request rate / latency / errors) without per-service instrumentation

Do **not** adopt for:
- Single-cluster monolith with < 5 services
- When app-level libraries already provide retries / circuit breakers consistently
- Teams without operational capacity for mesh control plane

---

## Mesh Choice

| Mesh | Strengths | Tradeoffs |
|---|---|---|
| Istio (Envoy) | Most features, large ecosystem | Heavy, complex, high resource cost |
| Linkerd | Lightweight, simple, Rust proxy | Fewer features, smaller ecosystem |
| Cilium Service Mesh | eBPF-based, no sidecar | Newer, requires Cilium CNI |

Default recommendation for this monorepo: **Linkerd** unless an Istio-only feature is required.

---

## mTLS Activation

### Linkerd (automatic mTLS)

```yaml
# All pods in namespace are auto-injected and mTLS-enabled
apiVersion: v1
kind: Namespace
metadata:
  name: default
  annotations:
    linkerd.io/inject: enabled
```

Verify:

```bash
linkerd viz stat deploy -n default
linkerd viz edges deploy -n default   # shows mTLS status per connection
```

### Istio (PeerAuthentication)

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: istio-system
spec:
  mtls:
    mode: STRICT
```

`STRICT` mode rejects all plaintext. Roll out in `PERMISSIVE` first, then switch.

---

## Traffic Policy (Canary Example)

### Linkerd ServiceProfile + TrafficSplit

```yaml
apiVersion: split.smi-spec.io/v1alpha1
kind: TrafficSplit
metadata:
  name: order-service-canary
spec:
  service: order-service
  backends:
    - service: order-service-stable
      weight: 90
    - service: order-service-canary
      weight: 10
```

### Istio VirtualService

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
spec:
  hosts: [order-service]
  http:
    - route:
        - destination: { host: order-service, subset: stable }
          weight: 90
        - destination: { host: order-service, subset: canary }
          weight: 10
```

---

## Authorization Policy

Default deny, explicit allow per service-to-service edge:

```yaml
# Istio
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: order-service-allow
spec:
  selector:
    matchLabels: { app.kubernetes.io/name: order-service }
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/api-gateway"]
      to:
        - operation: { methods: ["GET", "POST"] }
```

This is the **mesh-level** allowlist. Application-level RBAC (`backend/gateway-security/SKILL.md`) still applies.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Enabling STRICT mTLS without ROLLOUT | Start in PERMISSIVE, observe, then switch |
| Mesh sidecar OOM | Tune sidecar resource requests/limits |
| Mesh handles retries AND app handles retries | Pick one layer to avoid retry storms |
| Headless services not meshed | Linkerd needs ClusterIP; Istio needs ServiceEntry |
| Mesh telemetry duplicating app metrics | Use mesh telemetry as authoritative for L7 |
| No fallback if control plane is down | Plan disaster recovery for control plane |

---

## Verification Checklist

- [ ] Adoption rationale documented in `specs/services/<service>/architecture.md`
- [ ] mTLS verified between all in-mesh services
- [ ] Traffic split tested in staging before production rollout
- [ ] Authorization policies cover every allowed edge, default deny
- [ ] Sidecar resources tuned to avoid OOM
- [ ] Runbook covers mesh control-plane outage
