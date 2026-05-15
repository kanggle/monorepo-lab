# console-web

`console-web` is the single frontend for the platform-console project (ADR-MONO-013, Model B). It provides an AWS/GCP-console-style unified operations surface over the portfolio enterprise suite: gap, wms, scm, and future erp and finance. Because wms, scm, erp, and finance are all backend-only services (no per-domain UI), the console renders every domain's operational screens itself by calling each domain's gateway/admin APIs through a backend-for-frontend (BFF) integration layer.

This repository contains the **Phase 1 skeleton** only. The real shell — GAP OIDC Authorization Code + PKCE login, data-driven service catalog from the GAP product/tenant registry, tenant switcher, and federated domain screens for gap/wms/scm — is delivered in `TASK-PC-FE-001`.
