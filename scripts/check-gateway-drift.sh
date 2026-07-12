#!/usr/bin/env bash
#
# check-gateway-drift.sh — TASK-MONO-360 (carries TASK-MONO-347 AC-3)
#
# WHY THIS EXISTS
#
# `platform/api-gateway-policy.md` declares, without exception:
#
#   L3   "Every project that exposes HTTP traffic to external clients has a
#         gateway service."
#   L13  "All external traffic MUST pass through the gateway."
#   L14  "No backend service may be directly exposed to external traffic."
#
# For months, finance and erp had no gateway module at all — while their own
# PROJECT.md files listed `gateway-service` as v1-IN, and Traefik routed
# `finance.local` straight at `account-service` and split `erp.local` across
# four backends by PathPrefix. The policy, the plan, and the code each said a
# different thing, the L13/L14 violation was live, and NOTHING FAILED
# (TASK-MONO-347; fixed by TASK-MONO-357).
#
# TASK-MONO-347 itself misdiagnosed this as "zero external surface — internal
# BFF calls only". That is the tell: two careful passes over the same drift
# both missed the exposure axis, because external surface is decided by Traefik
# labels, not by the code call path. This is not a surface humans can hold.
#
# WHAT MONO-345 ALREADY COVERS, AND WHAT IT DOES NOT
#
# scripts/check-service-map-drift.sh compares `settings.gradle` against
# `docs/project-overview.md`. It reads NEITHER `PROJECT.md` NOR the compose
# Traefik labels — so the exact shape of MONO-347 still passes CI today.
# This script covers the two axes that one does not.
#
# CHECKS
#
#   STRUCT  every projects/*/PROJECT.md has a `## Service Map` section.
#           TEMPLATE.md L206 prescribes it. ecommerce's PROJECT.md had NO such
#           section, which is why its gateway declaration could not drift — it
#           had no declaration surface at all. A doc that says nothing is not
#           "in sync"; it is unfalsifiable. This check keeps the surface from
#           silently vanishing, which is how a declaration check gets defeated.
#
#   I1      PROJECT.md declares `gateway-service`  <->  settings.gradle has
#           projects:<p>:apps:gateway-service.  BOTH directions.
#           The phantom direction (declared, no module) is the MONO-347 axis.
#
#   I2      In a project that OWNS a gateway module: any service in that
#           project's docker-compose.yml carrying a Traefik router rule must be
#           the gateway, or be on the explicit allowlist below. This is policy
#           L14, and it is the axis that was live-violated and unseen.
#
#   I3      Every gateway validates `iss` against an ALLOWLIST that includes the
#           SAS issuer (`OIDC_ISSUER_URL`) — not a single pinned value.
#           TASK-MONO-365: iam's gateway was the one edge of seven that pinned a
#           single `expected-issuer`, and it pinned the LEGACY one. SAS-issued
#           tokens — what the other six take as primary — were 401'd there, and
#           nothing failed, because console-bff bypasses that edge entirely
#           (TASK-MONO-347). Worse, TASK-BE-398 retires the legacy issuer: under
#           that config the edge would have ended up accepting NOTHING, on a
#           date, with nobody watching. This check exists so an edge cannot
#           quietly fall off the fleet's issuer axis again.
#
# WHAT THIS SCRIPT DOES *NOT* GUARD (do not claim otherwise)
#
#   * The prose of api-gateway-policy.md. "Every project that exposes HTTP
#     traffic..." is a universal statement, not a project list; there is
#     nothing to diff it against. It is upheld here only INDIRECTLY, via I1+I2.
#   * A NEW project that declares no gateway in PROJECT.md and exposes a
#     backend directly. I1 stays silent (nothing declared) and I2 stays silent
#     (no gateway module to scope it to). Closing that would require deciding,
#     mechanically, which projects "expose HTTP traffic to external clients" —
#     a judgement, not a lookup. Knowing this hole is not the same as not
#     having it, and it is written down here on purpose.
#
# DELIBERATE LIMITS (do not "fix" these without reading TASK-MONO-360)
#
#   * wms/iam docker-compose.yml are INFRA-ONLY — postgres/redis/kafka plus
#     observability, and ZERO app services (the file header says
#     "gateway-service is reached via http://wms.local/" while the service is
#     not in the file; the apps come up from the fed-e2e compose or from the
#     IDE). So I2 must NOT demand that a gateway own the project hostname —
#     it only forbids a NON-gateway app service from holding one. A guard that
#     is RED on day one gets switched off, and a switched-off guard is worse
#     than no guard, because a skip reports green.
#   * platform-console owns no gateway BY DESIGN (ADR-MONO-013 Model B). That much
#     is true, and I1 must not demand one here.
#
#     What was NOT true is the reason this file originally gave for exempting the
#     project from I2: that `console-bff` "deliberately holds `console-bff.local`"
#     and that this is "a structure the policy explicitly permits". The policy
#     permits no such thing — L14 forbids exactly it, and no ADR ever granted an
#     exception (ADR-MONO-013 decides Model B; ADR-MONO-017 lists the Traefik label
#     in a follow-up task's checklist, which is not a decision). So the guard was
#     blessing, in an executable artifact, the one thing the policy bans in prose —
#     while every reader of the policy still believed the absolute rule.
#
#     TASK-MONO-362 removed the router instead of writing the exception into policy:
#     console-web reaches the BFF on the docker network, as the fed-e2e stack always
#     had. platform-console is now IN I2's scope and passes, because its only
#     router-holder is `console-web`, which is allowlisted below on its merits.
#     The exception was deleted, not legalised — the guard got stricter.
#   * The I2 allowlist is a list of EXACT NAMES, never a pattern. A regex like
#     `*-web` would wave through the next backend that happens to end in -web.
#
# Usage: bash scripts/check-gateway-drift.sh
# Exit:  0 = in sync, 1 = drift (offending projects are printed), 2 = harness error

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="${SETTINGS_GRADLE:-$ROOT/settings.gradle}"
PROJECTS_DIR="${PROJECTS_DIR:-$ROOT/projects}"

[ -r "$SETTINGS" ]   || { echo "FATAL: cannot read $SETTINGS" >&2; exit 2; }
[ -d "$PROJECTS_DIR" ] || { echo "FATAL: cannot read $PROJECTS_DIR" >&2; exit 2; }

# Services that may legitimately hold a Traefik router inside a gateway-owning
# project. EXACT NAMES ONLY — see "DELIBERATE LIMITS" above.
#   web-store    ecommerce storefront (Next.js). A browser-facing SPA is not
#                "a backend service directly exposed" (policy L14); it is the
#                external client.
#   console-web  platform-console UI. Same reasoning.
#   kafka-ui     operator tooling, not an application surface.
#   grafana      operator tooling, not an application surface.
TRAEFIK_ALLOWLIST="web-store console-web kafka-ui grafana"

# I4 — gateways permitted to key their rate limit on client IP alone, by RECORDED
# deviation (api-gateway-policy.md § Rate Limiting > Current fleet). EXACT NAMES ONLY.
#
# EMPTY, and it should stay that way. wms was the sole entry (MONO-368) and TASK-MONO-370
# aligned it, so every gateway now keys authenticated traffic on the principal. An entry here
# is a promise that someone wrote down WHY in the policy's fleet table — not a place to park a
# guard you could not get green.
RATELIMIT_IP_ONLY_ALLOWLIST=""

fail=0

# --- gradle side: which projects own a gateway module ------------------------
# Strip line comments first — settings.gradle prose names module paths
# (TASK-MONO-357 left several explanatory comments that mention gateway-service).
gateway_modules="$(
  sed 's://.*::' "$SETTINGS" \
    | tr -d '\r' \
    | grep -oE "projects:[a-z0-9-]+:apps:gateway-service" \
    | sed 's/^projects://; s/:apps:gateway-service$//' \
    | sort -u
)"

[ -n "$gateway_modules" ] || { echo "FATAL: no gateway-service module found in $SETTINGS — the parser is broken, not the tree" >&2; exit 2; }

has_module() { printf '%s\n' "$gateway_modules" | grep -qx "$1"; }

# --- per-project checks ------------------------------------------------------
for project_md in "$PROJECTS_DIR"/*/PROJECT.md; do
  [ -r "$project_md" ] || continue
  project="$(basename "$(dirname "$project_md")")"

  # STRUCT — the declaration surface must exist (TEMPLATE.md L206).
  if ! grep -qE '^#{2,3} Service Map' "$project_md"; then
    echo "NO-SERVICE-MAP  $project — PROJECT.md has no '## Service Map' section (TEMPLATE.md L206)."
    echo "                A PROJECT.md that declares nothing cannot be checked against anything."
    fail=1
  fi

  # I1 — declaration <-> module, both directions.
  declared=0
  grep -qF '`gateway-service`' "$project_md" && declared=1
  module=0
  has_module "$project" && module=1

  if [ "$declared" = "1" ] && [ "$module" = "0" ]; then
    echo "PHANTOM-GATEWAY  $project — PROJECT.md declares \`gateway-service\`, but settings.gradle has no"
    echo "                 projects:$project:apps:gateway-service. This is the TASK-MONO-347 shape: the plan"
    echo "                 promises a gateway, the build never builds one, and every reader of the doc is misled."
    fail=1
  fi
  if [ "$declared" = "0" ] && [ "$module" = "1" ]; then
    echo "UNDECLARED-GATEWAY  $project — settings.gradle builds projects:$project:apps:gateway-service, but"
    echo "                    PROJECT.md never mentions \`gateway-service\`. The edge exists and the plan is silent."
    fail=1
  fi

  # I2 — policy L14: no backend service may hold a Traefik router. Only the
  # gateway, or an allowlisted non-backend surface (browser-facing frontend /
  # operator tooling), may sit on the edge.
  #
  # TASK-MONO-362 widened this to EVERY project. It used to run only on
  # gateway-owning ones, which exempted platform-console — the one project that
  # was actually violating L14 (`console-bff`, a backend, held
  # `Host(console-bff.local)`). Scoping a policy check to the projects that
  # already comply is how the single offender goes unchecked.
  compose="$PROJECTS_DIR/$project/docker-compose.yml"
  if [ -r "$compose" ]; then
    exposed="$(
      tr -d '\r' < "$compose" | awk '
        /^  [a-zA-Z0-9._-]+:[[:space:]]*$/ { svc = $1; sub(/:$/, "", svc) }
        svc != "" && /traefik\.http\.routers\.[a-zA-Z0-9._-]+\.rule/ { print svc }
      ' | sort -u
    )"
    for svc in $exposed; do
      [ "$svc" = "gateway-service" ] && continue
      allowed=0
      for ok in $TRAEFIK_ALLOWLIST; do [ "$svc" = "$ok" ] && allowed=1; done
      [ "$allowed" = "1" ] && continue
      echo "DIRECT-EXPOSURE  $project:$svc — holds a Traefik router in $project/docker-compose.yml."
      echo "                 api-gateway-policy.md L13/L14: all external traffic MUST pass through the"
      echo "                 gateway; no backend service may be directly exposed. On the edge without a"
      echo "                 gateway there is no rate limiting, no identity-header strip->enrich, and no"
      echo "                 uniform error envelope."
      if [ "$module" = "1" ]; then
        echo "                 This project HAS a gateway: route the service through gateway-service and"
        echo "                 give it 'expose:' only."
      else
        echo "                 This project has NO gateway, so nothing sits in front of it at all. Either"
        echo "                 drop the router and reach the service on the docker network (TASK-MONO-362"
        echo "                 did this for console-bff), or give the project a gateway (TASK-MONO-357 did"
        echo "                 this for finance/erp)."
      fi
      echo "                 (If it is genuinely not a backend — a browser-facing frontend or operator"
      echo "                 tooling — add it to TRAEFIK_ALLOWLIST by exact name, with a reason.)"
      fail=1
    done
  fi

  # I3 — the gateway's `iss` check must be an ALLOWLIST that includes the SAS issuer.
  gateway_yml="$PROJECTS_DIR/$project/apps/gateway-service/src/main/resources/application.yml"
  if [ "$module" = "1" ] && [ -r "$gateway_yml" ]; then
    issuer_line="$(tr -d '\r' < "$gateway_yml" \
      | grep -E '^[[:space:]]*(allowed-issuers|expected-issuer):' || true)"

    if [ -z "$issuer_line" ]; then
      echo "NO-ISSUER-CHECK  $project — the gateway's application.yml declares neither 'allowed-issuers'"
      echo "                 nor 'expected-issuer'. An edge that does not pin 'iss' will accept a token from"
      echo "                 any issuer whose signing key it can resolve."
      fail=1
    elif printf '%s\n' "$issuer_line" | grep -qE '^[[:space:]]*expected-issuer:'; then
      echo "SINGLE-ISSUER  $project — the gateway pins a single 'expected-issuer'. Every edge must take an"
      echo "               ALLOWLIST ('allowed-issuers'): IAM mints TWO issuers during the D2-b window (the"
      echo "               SAS/OIDC issuer and the legacy 'iam' string). This is the exact state TASK-MONO-365"
      echo "               found on the iam edge — it pinned the LEGACY value alone, so SAS-issued tokens were"
      echo "               401'd there while the other six gateways took them as primary. Nothing failed,"
      echo "               because console-bff bypasses that edge (TASK-MONO-347). And TASK-BE-398 retires the"
      echo "               legacy issuer: under that config the edge would have ended up accepting NOTHING."
      fail=1
    elif ! printf '%s\n' "$issuer_line" | grep -q 'OIDC_ISSUER_URL'; then
      echo "ISSUER-MISSING-SAS  $project — the gateway's 'allowed-issuers' default never references"
      echo "                    OIDC_ISSUER_URL, so it cannot accept SAS/OIDC-issued tokens — the issuer the"
      echo "                    rest of the fleet treats as primary. The legacy 'iam' entry is the one that"
      echo "                    goes away at the TASK-BE-398 sunset; the SAS entry is the one that must stay."
      fail=1
    fi
  fi

  # I4 — the authenticated rate-limit key must identify the CALLER, not just the tenant
  # (api-gateway-policy.md § Rate Limiting > Key shape).
  #
  # A rate limit is only as good as the thing it counts. TASK-MONO-368: ecommerce keyed
  # authenticated traffic on the `tenant_id` claim alone, citing multi-tenant M7 — but its own
  # gate makes that claim a CONSTANT for shopper traffic ('ecommerce', derived from the OAuth
  # client), so every authenticated shopper landed in ONE bucket and a single account could 429
  # the whole marketplace. Anonymous traffic, meanwhile, WAS split by IP. The callers we can
  # identify were the ones sharing a bucket.
  #
  # A claim your own config pins to a constant is not a bucket. So: if a gateway's rate-limit
  # keying reads the JWT at all, it must read the SUBJECT.
  if [ "$module" = "1" ]; then
    rl_src="$(find "$PROJECTS_DIR/$project/apps/gateway-service/src/main/java" \
      -name '*RateLimit*.java' -type f 2>/dev/null | sort || true)"

    if [ -n "$rl_src" ]; then
      ip_only=0
      for ok in $RATELIMIT_IP_ONLY_ALLOWLIST; do [ "$project" = "$ok" ] && ip_only=1; done

      # Does the keying read the authenticated principal?
      #
      # Both spellings count. Six gateways use Spring Security's Jwt#getSubject(); the iam
      # gateway hand-decodes the payload and reads the "sub" claim with Jackson (it rate-limits
      # BEFORE signature verification, so it cannot use the Spring Jwt type). A predicate that
      # only knew getSubject() flagged iam on this guard's first run — a false positive, and a
      # guard that is RED on day one gets switched off, which is worse than no guard at all.
      keys_on_subject=0
      grep -lqE 'getSubject\(\)|"sub"' $rl_src 2>/dev/null && keys_on_subject=1

      # Does it read the tenant claim?
      keys_on_tenant=0
      grep -lqE 'tenant_id|CLAIM_TENANT_ID' $rl_src 2>/dev/null && keys_on_tenant=1

      if [ "$keys_on_subject" = "0" ] && [ "$ip_only" = "0" ]; then
        if [ "$keys_on_tenant" = "1" ]; then
          echo "TENANT-ONLY-BUCKET  $project — the rate-limit key reads the tenant claim but never the JWT"
          echo "                    subject. If this gateway's tenant gate pins 'required-tenant-id', that claim"
          echo "                    is the SAME VALUE for every token it admits — so this is not per-tenant"
          echo "                    isolation, it is one global bucket per route wearing that costume, and any"
          echo "                    single authenticated caller can exhaust it for everyone. This is the exact"
          echo "                    shape TASK-MONO-368 found on the ecommerce edge. Add an account segment."
        else
          echo "IP-ONLY-BUCKET  $project — the gateway's routes are authenticated, but its rate-limit key never"
          echo "                reads the JWT subject. Everyone behind one NAT then shares a bucket, while an"
          echo "                abuser rotating IPs is never throttled per account (api-gateway-policy.md"
          echo "                § Rate Limiting > Key shape). Key authenticated traffic on 'sub'; keep IP for"
          echo "                pre-auth routes only."
        fi
        echo "                (If this is a deliberate, RECORDED deviation, add the project to"
        echo "                RATELIMIT_IP_ONLY_ALLOWLIST by exact name AND to the fleet table in"
        echo "                api-gateway-policy.md — a deviation nobody wrote down is drift, not a decision.)"
        fail=1
      fi
    fi
  fi
done

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "Gateway declaration/exposure drift. See TASK-MONO-360 (and TASK-MONO-347, which this guards against recurring)."
  exit 1
fi

echo "OK: every gateway declaration matches a module, and no backend service holds a Traefik router in any project."
