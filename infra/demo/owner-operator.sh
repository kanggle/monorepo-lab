#!/usr/bin/env bash
# =============================================================================
# infra/demo/owner-operator.sh — the owner-only '*' SUPER_ADMIN operator (TASK-MONO-798)
# =============================================================================
# Owner decision 2026-10-17 UTC (verbatim in tasks/…/TASK-MONO-798-*.md): the demo seeds ONE
# owner-only, unconfined '*' SUPER_ADMIN with an IdP login, so that the four '*' checks of
# TASK-MONO-672 «Still open» (1–4) can be measured. In this exact form:
#   1. unconfined '*', IdP login, NOT in README / demo guide / public seed docs;
#   2. no fixed password in any seed or repo file — generated at boot, stored in SSM Parameter
#      Store as a SecureString;
#   3. seeded SUSPENDED; this owner-run wrapper turns it ACTIVE for a measurement window and
#      back to SUSPENDED afterwards, and reports the state. The password is never printed.
#
# The operator row: admin-service migration-dev R__seed_demo_star_owner_operator.sql
# (SUSPENDED, '*', SUPER_ADMIN, password_hash NULL). There is deliberately NO auth-service
# credential seed — `provision` below writes that row at boot.
#
# Usage (on the demo host, as root — run over SSM):
#   bash infra/demo/owner-operator.sh provision     # boot path (demo-up.sh calls it), once per boot
#   bash infra/demo/owner-operator.sh activate      # owner: open a measurement window
#   bash infra/demo/owner-operator.sh deactivate    # owner: close it (SUSPENDED + refresh tokens revoked)
#   bash infra/demo/owner-operator.sh status        # owner: report, read-only
#   bash infra/demo/owner-operator.sh --check-undisclosed  # CI: the operator is in no public doc
#   bash infra/demo/owner-operator.sh --self-test   # CI (demo-wrapper-smoke): stubs docker/aws/java
#
# -----------------------------------------------------------------------------
# provision — what happens at boot, and why in this order
# -----------------------------------------------------------------------------
#   0. once per boot (boot_id marker in /run, the TASK-MONO-753 shape): the control plane's
#      per-domain start (`demo-boot.sh fan`) re-runs demo-up.sh with iam in its set; without the
#      marker that run would rotate the password and suspend the operator in the MIDDLE of an
#      owner's window.
#   1. wait for auth_db.credentials and the admin_db row (Flyway may still be running).
#   2. force SUSPENDED + revoke its admin refresh tokens — every boot starts closed, whatever the
#      last window left behind (an instance stopped by the idle timer mid-window keeps its volume).
#   3. generate a 40-char password on the instance (/dev/urandom); hash it with the login path's
#      own hasher (OwnerOperatorHash.java + the jars of the BAKED auth-service boot jar).
#   4. put it into SSM (SecureString, --overwrite) — value read from a 0600 file, never argv.
#   5. ONLY if 4 succeeded: upsert the `iam` credential with the hash.
#      🔴 The order is the fail-closed direction: if the put fails, the DB keeps the previous
#      hash, which still matches the previous SSM value (or there is no credential at all) —
#      never «a credential whose password nobody holds» paired with a readable stale value.
#
# 🔴🔴 The password is never printed: no `set -x` here (and a caller's xtrace is turned off),
#    it goes to java on stdin and to aws through a file, error messages name steps, not values.
#    The self-test asserts it is absent from every captured stdout/stderr and every argv.
# =============================================================================
set -euo pipefail
case "$-" in *x*) set +x ;; esac

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${ROOT:-$(cd "$HERE/../.." && pwd)}"

# --- identity (the seed file carries the same three literals; --self-test compares them) -----
OWNER_OPERATOR_ID="demo-star-owner"
OWNER_EMAIL="star-owner@demo.com"
OWNER_ACCOUNT_ID="0199de70-0000-7000-8000-00000000ad09"
OWNER_CREDENTIAL_TENANT="iam"
OWNER_SSM_PARAM="${OWNER_OPERATOR_SSM_PARAM:-/portfolio-demo/owner-operator/password}"

ADMIN_SEED="$ROOT/projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_star_owner_operator.sql"
AUTH_DEV_SEEDS="$ROOT/projects/iam-platform/apps/auth-service/src/main/resources/db/migration-dev"

MYSQL_CONTAINER="${IAM_MYSQL_CONTAINER:-iam-mysql}"
AUTH_DB="${AUTH_DB_NAME:-auth_db}"
ADMIN_DB="${ADMIN_DB_NAME:-admin_db}"
AUTH_BOOT_JAR_DIR="${OWNER_OPERATOR_AUTH_JAR_DIR:-$ROOT/projects/iam-platform/apps/auth-service/build/libs}"
MARKER="${OWNER_OPERATOR_MARKER:-/run/demo-owner-operator.boot-id}"
BOOT_ID_SRC="${DEMO_BOOT_ID_SRC:-/proc/sys/kernel/random/boot_id}"
WAIT_SECS="${OWNER_OPERATOR_WAIT_SECS:-300}"
WAIT_STEP="${OWNER_OPERATOR_WAIT_STEP:-5}"
ADMIN_ACCESS_TTL_NOTE="${ADMIN_JWT_ACCESS_TTL:-3600}"

log()  { echo "[owner-operator] $*"; }
warn() { echo "[owner-operator] ⚠ $*" >&2; }

# _sql <db> — SQL on stdin, rows on stdout. The root password is read INSIDE the container
# (its own env), so it is on no host argv and in no host variable.
_sql() {
  docker exec -i "$MYSQL_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B "$0"' "$1"
}

_q() { # _q <db> <tag> <sql> — one statement, tagged so the self-test stub can tell them apart
  printf '/* owner-operator:%s */ %s\n' "$2" "$3" | _sql "$1"
}

_operator_status() {
  _q "$ADMIN_DB" status "SELECT status FROM admin_operators WHERE operator_id = '$OWNER_OPERATOR_ID';" 2>/dev/null || true
}

_set_status() { # _set_status ACTIVE|SUSPENDED
  _q "$ADMIN_DB" "set-$1" "UPDATE admin_operators SET status = '$1', updated_at = NOW(6) WHERE operator_id = '$OWNER_OPERATOR_ID';"
}

_revoke_refresh_tokens() {
  # What PatchOperatorStatusUseCase does on SUSPENDED (the API path) — refresh does not re-check status.
  _q "$ADMIN_DB" revoke-refresh "UPDATE admin_operator_refresh_tokens t JOIN admin_operators o ON o.id = t.operator_id SET t.revoked_at = NOW(6), t.revoke_reason = 'FORCE_LOGOUT' WHERE o.operator_id = '$OWNER_OPERATOR_ID' AND t.revoked_at IS NULL;"
}

_report() {
  local st cred active
  st="$(_operator_status)"
  cred="$(_q "$AUTH_DB" credential "SELECT CONCAT_WS(' ', 'present', DATE_FORMAT(updated_at, '%Y-%m-%dT%H:%i:%sZ')) FROM credentials WHERE account_id = '$OWNER_ACCOUNT_ID' AND tenant_id = '$OWNER_CREDENTIAL_TENANT';" 2>/dev/null || true)"
  active="$(_q "$ADMIN_DB" active-refresh "SELECT COUNT(*) FROM admin_operator_refresh_tokens t JOIN admin_operators o ON o.id = t.operator_id WHERE o.operator_id = '$OWNER_OPERATOR_ID' AND t.revoked_at IS NULL AND t.expires_at > NOW(6);" 2>/dev/null || true)"
  log "operator ${OWNER_OPERATOR_ID} (${OWNER_EMAIL}) status=${st:-<row not found>}"
  log "credential (tenant ${OWNER_CREDENTIAL_TENANT}): ${cred:-absent}"
  log "unrevoked admin refresh tokens: ${active:-?}"
  if [ -n "$(_boot_id)" ] && [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null)" = "$(_boot_id)" ]; then
    log "password provisioned this boot: yes (SSM ${OWNER_SSM_PARAM})"
  else
    log "password provisioned this boot: no — the next boot (or 'provision') generates one"
  fi
}

_boot_id() { cat "$BOOT_ID_SRC" 2>/dev/null || true; }

_region() {
  local r="${AWS_REGION:-${AWS_DEFAULT_REGION:-}}"
  if [ -z "$r" ]; then
    # shellcheck source=infra/demo/demo-selection.sh
    declare -F imds_region >/dev/null 2>&1 || source "$HERE/demo-selection.sh"
    r="$(imds_region)" || return 1
  fi
  printf '%s' "$r"
}

_wait_ready() {
  local waited=0 n_cred n_op
  while :; do
    n_cred="$(_q "$AUTH_DB" wait-credentials "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$AUTH_DB' AND table_name = 'credentials';" 2>/dev/null || echo 0)"
    n_op="$(_q "$ADMIN_DB" wait-operator "SELECT COUNT(*) FROM admin_operators WHERE operator_id = '$OWNER_OPERATOR_ID';" 2>/dev/null || echo 0)"
    [ "${n_cred:-0}" = "1" ] && [ "${n_op:-0}" = "1" ] && return 0
    if [ "$waited" -ge "$WAIT_SECS" ]; then
      warn "credentials table=${n_cred:-0} · operator row=${n_op:-0} after ${WAIT_SECS}s — not provisioning"
      return 1
    fi
    sleep "$WAIT_STEP"; waited=$(( waited + WAIT_STEP ))
  done
}

_generate_password() {
  # 40 alphanumerics from /dev/urandom (~238 bits). pipefail is off INSIDE this subshell only:
  # `head` closing the pipe is how this ends, and tr's SIGPIPE is not a failure.
  ( set +o pipefail; LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c 40 )
}

_hash() { # password on stdin → hash on stdout. Jars from the BAKED auth-service boot jar.
  local work="$1" jar
  jar="$(ls -1 "$AUTH_BOOT_JAR_DIR"/*.jar 2>/dev/null | grep -v -E 'plain|sources|javadoc' | head -n1 || true)"
  if [ -z "$jar" ]; then
    warn "no auth-service boot jar under $AUTH_BOOT_JAR_DIR — cannot hash"
    return 1
  fi
  mkdir -p "$work/cp"
  unzip -q -j -o "$jar" 'BOOT-INF/lib/password4j-*.jar' 'BOOT-INF/lib/slf4j-api-*.jar' \
        'BOOT-INF/lib/java-security*.jar' -d "$work/cp" >/dev/null 2>"$work/unzip.err" || {
    warn "could not take the hasher's jars out of $(basename "$jar")"
    return 1
  }
  java -cp "$work/cp/*" "$HERE/OwnerOperatorHash.java" 2>"$work/java.err"
}

provision() {
  local boot work pw hash region rc=0 err
  # Off the demo host there is nothing to provision (no SSM, no baked jar) — the same skip as
  # seed-demo-domain.sh. It also keeps verify-demo-wrapper.sh's demo-up harnesses (docker shims,
  # DEMO_DOMAIN=local) from waiting WAIT_SECS on a database that does not exist.
  if [ "${DEMO_DOMAIN:-local}" = "local" ]; then
    log "DEMO_DOMAIN=local — not the demo host, skipped"
    return 0
  fi
  boot="$(_boot_id)"
  if [ -n "$boot" ] && [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null)" = "$boot" ]; then
    log "already provisioned this boot (boot_id=$boot) — nothing to do (a window may be open)"
    return 0
  fi

  _wait_ready || return 1

  # 2. every boot starts closed.
  _set_status SUSPENDED >/dev/null || { warn "could not set SUSPENDED"; return 1; }
  _revoke_refresh_tokens >/dev/null || warn "could not revoke refresh tokens"

  if ! command -v aws >/dev/null 2>&1; then
    warn "aws CLI missing — no password generated; the operator stays SUSPENDED with its previous credential (if any)"
    return 1
  fi
  region="$(_region)" || { warn "region unknown (off EC2?) — no password generated"; return 1; }

  work="$(mktemp -d)"; chmod 700 "$work"
  # shellcheck disable=SC2064
  trap "rm -rf '$work'" RETURN

  pw="$(_generate_password)"
  if [ "${#pw}" -lt 40 ]; then
    warn "password generation produced ${#pw} characters — not provisioning"
    return 1
  fi
  hash="$(printf '%s' "$pw" | _hash "$work")" || rc=$?
  if [ "$rc" -ne 0 ] || [[ "$hash" != '$argon2id$'* ]]; then
    warn "hashing failed (rc=$rc) — not provisioning; java said: $(head -c 300 "$work/java.err" 2>/dev/null | tr '\n' ' ')"
    return 1
  fi

  # 4. SSM first (value from a 0600 file, never argv).
  ( umask 077; printf '%s' "$pw" > "$work/value" )
  pw=""
  err="$work/put.err"
  if ! aws ssm put-parameter --region "$region" --name "$OWNER_SSM_PARAM" --type SecureString \
         --overwrite --value "file://$work/value" >/dev/null 2>"$err"; then
    if grep -q 'AccessDenied' "$err" 2>/dev/null; then
      warn "SSM put refused (AccessDenied) — has the instance-role policy (terraform main.tf, TASK-MONO-798) been applied? Credential NOT changed."
    else
      warn "SSM put failed — credential NOT changed (fail-closed)"
    fi
    return 1
  fi

  # 5. only now the credential.
  if ! _q "$AUTH_DB" upsert-credential "INSERT INTO credentials (tenant_id, account_id, email, credential_hash, hash_algorithm, created_at, updated_at, version) VALUES ('$OWNER_CREDENTIAL_TENANT', '$OWNER_ACCOUNT_ID', '$OWNER_EMAIL', '$hash', 'argon2id', NOW(6), NOW(6), 0) ON DUPLICATE KEY UPDATE credential_hash = VALUES(credential_hash), hash_algorithm = 'argon2id', updated_at = NOW(6), version = version + 1;" >/dev/null; then
    warn "SSM holds a NEW password but the credential write failed — login will fail until the next provision"
    return 1
  fi

  if [ -n "$boot" ]; then
    printf '%s' "$boot" > "$MARKER" 2>/dev/null || warn "could not write $MARKER — a later per-domain start would rotate again"
  fi
  log "provisioned: new password in SSM ${OWNER_SSM_PARAM} (SecureString) · credential updated · operator SUSPENDED"
}

activate() {
  _set_status ACTIVE >/dev/null || { warn "could not set ACTIVE"; return 1; }
  log "ACTIVE — measurement window open. Close it with: owner-operator.sh deactivate"
  _report
}

deactivate() {
  _set_status SUSPENDED >/dev/null || { warn "could not set SUSPENDED"; return 1; }
  _revoke_refresh_tokens >/dev/null || warn "could not revoke refresh tokens"
  log "SUSPENDED — window closed. New exchanges are refused now; an operator access token already minted lives until its exp (ADMIN_JWT_ACCESS_TTL=${ADMIN_ACCESS_TTL_NOTE}s); refresh tokens revoked."
  _report
}

# =============================================================================
# --check-undisclosed — owner decision point 1: this operator is in NO public doc.
# =============================================================================
# Population: every TRACKED file (git grep — 🔴 stage new files first, an unstaged file is
# invisible to it). Allowlist, kept short on purpose — everything else counts as public,
# which is the fail-toward-more-work direction (a new README, a launcher page, a guide, a
# frontend demo-account hint are all covered without being named here):
#   · tasks/ (tickets narrate the decision; they are the record, not a guide)
#   · this script and the two seeds that must carry the identity
#   · the two tests that pin it
# Identifiers: email · operator id · account id (the OIDC sub). The SSM parameter NAME is not
# a secret and is not searched (terraform must name it).
UNDISCLOSED_ALLOW=(
  ':!tasks/*' ':!*/tasks/*'
  ':!infra/demo/owner-operator.sh'
  ':!projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_star_owner_operator.sql'
  ':!projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_zz_demo_domain_permission_sets.sql'
  ':!projects/iam-platform/apps/admin-service/src/test/java/com/example/admin/integration/DemoOperatorSeedIntegrationTest.java'
  ':!projects/iam-platform/apps/auth-service/src/test/java/com/example/auth/demoseed/DemoStarOwnerOperatorSeedTest.java'
)

check_undisclosed() {
  local root="${UNDISCLOSED_ROOT:-$ROOT}" hits rc=0
  hits="$(git -C "$root" grep -n -F -e "$OWNER_EMAIL" -e "$OWNER_OPERATOR_ID" -e "$OWNER_ACCOUNT_ID" \
            -- . "${UNDISCLOSED_ALLOW[@]}" 2>&1)" || rc=$?
  case "$rc" in
    1) log "undisclosed: ok — no tracked file outside the allowlist names this operator"; return 0 ;;
    0) echo "[owner-operator] ✖ the owner-only operator is named outside its allowlist (owner decision 2026-10-17, point 1):" >&2
       printf '%s\n' "$hits" | sed 's/^/    /' >&2
       echo "[owner-operator]   → remove it from those files. It must not appear in README / demo guides / public seed docs." >&2
       return 1 ;;
    *) echo "[owner-operator] ✖ git grep failed (rc=$rc) — not a verdict: $hits" >&2; return 2 ;;
  esac
}

# =============================================================================
# --self-test — stubs docker / aws / java / unzip on PATH; no Docker, no AWS.
# =============================================================================
self_test() {
  local t pass=0 failn=0 out rc
  t="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$t'" EXIT
  ok()   { pass=$((pass+1)); echo "  PASS $*"; }
  bad()  { failn=$((failn+1)); echo "  FAIL $*"; }

  # ---- (1) the seed and this script name the same operator; the seed carries no password --------
  echo "[self-test] (1) seed ↔ script identity · SUSPENDED · no fixed password"
  if [ ! -f "$ADMIN_SEED" ]; then bad "(1) seed file missing: $ADMIN_SEED"; else
    local seed; seed="$(sed 's/--.*$//' "$ADMIN_SEED")"
    grep -q "'$OWNER_OPERATOR_ID', '\*', '$OWNER_EMAIL'" <<<"$seed" && ok "(1a) operator id · '*' · email" || bad "(1a) seed row does not start ('$OWNER_OPERATOR_ID', '*', '$OWNER_EMAIL')"
    grep -q "'$OWNER_ACCOUNT_ID'" <<<"$seed" && ok "(1b) oidc_subject == OWNER_ACCOUNT_ID" || bad "(1b) seed oidc_subject != $OWNER_ACCOUNT_ID (login would fail-close to 401)"
    grep -q "'SUSPENDED'" <<<"$seed" && ! grep -q "'ACTIVE'" <<<"$seed" && ok "(1c) seeded SUSPENDED, never ACTIVE" || bad "(1c) seed must say SUSPENDED and never ACTIVE"
    ! grep -q 'argon2' <<<"$seed" && ok "(1d) no hash literal in the seed" || bad "(1d) the seed carries a hash literal — a fixed password"
    grep -qE "confined_tenant_id *= *NULL" <<<"$seed" && ok "(1e) unconfined on re-run" || bad "(1e) re-run must keep confined_tenant_id NULL"
  fi
  if grep -rlE "$OWNER_ACCOUNT_ID|$OWNER_EMAIL" "$AUTH_DEV_SEEDS" >/dev/null 2>&1; then
    bad "(1f) an auth-service dev seed names this account — a credential row with a fixed password"
  else ok "(1f) no auth-service credential seed for this account"; fi

  # ---- stubs ---------------------------------------------------------------------------------------
  mkdir -p "$t/bin" "$t/log" "$t/jars"
  : > "$t/jars/auth-service-0.0.1.jar"
  cat > "$t/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$STUB_LOG/argv"
sql="$(cat)"; printf '%s\n' "$sql" >> "$STUB_LOG/sql"
case "$sql" in
  *owner-operator:wait-credentials*) echo "${STUB_TABLES:-1}" ;;
  *owner-operator:wait-operator*)    echo "${STUB_OPERATOR:-1}" ;;
  *owner-operator:status*)           cat "$STUB_LOG/state" 2>/dev/null || echo SUSPENDED ;;
  *owner-operator:set-ACTIVE*)       echo ACTIVE > "$STUB_LOG/state" ;;
  *owner-operator:set-SUSPENDED*)    echo SUSPENDED > "$STUB_LOG/state" ;;
  *owner-operator:upsert-credential*) [ "${STUB_UPSERT_RC:-0}" = 0 ] || exit 1 ;;
  *owner-operator:credential*)       echo "present 2026-10-17T00:00:00Z" ;;
  *owner-operator:active-refresh*)   echo 0 ;;
esac
STUB
  cat > "$t/bin/aws" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$STUB_LOG/argv"
v=""; prev=""
for a in "$@"; do [ "$prev" = "--value" ] && v="$a"; prev="$a"; done
case "$v" in file://*) cat "${v#file://}" > "$STUB_LOG/ssm-value" ;; *) printf '%s' "$v" > "$STUB_LOG/ssm-value-argv" ;; esac
if [ "${STUB_PUT_RC:-0}" != 0 ]; then echo "An error occurred (AccessDeniedException) when calling the PutParameter operation" >&2; exit 254; fi
STUB
  cat > "$t/bin/unzip" <<'STUB'
#!/usr/bin/env bash
printf 'unzip %s\n' "$*" >> "$STUB_LOG/argv"
STUB
  cat > "$t/bin/java" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$STUB_LOG/argv"
cat > "$STUB_LOG/java-stdin"
printf '%s' '$argon2id$v=16$m=65536,t=3,p=1$STUBSALT$STUBHASH'
STUB
  chmod +x "$t/bin/"*

  _run() { # _run <case> <subcommand> [ENV=VAL...] → $t/<case>.out, returns rc
    local c="$1" sub="$2"; shift 2
    rm -rf "$t/log"; mkdir -p "$t/log"
    env PATH="$t/bin:$PATH" STUB_LOG="$t/log" AWS_REGION=ap-northeast-2 DEMO_DOMAIN=1-2-3-4.sslip.io \
        OWNER_OPERATOR_AUTH_JAR_DIR="$t/jars" OWNER_OPERATOR_MARKER="$t/marker-$c" \
        DEMO_BOOT_ID_SRC="$t/boot_id" OWNER_OPERATOR_WAIT_SECS=0 OWNER_OPERATOR_WAIT_STEP=1 \
        "$@" bash "$HERE/owner-operator.sh" "$sub" > "$t/$c.out" 2>&1
  }
  echo "boot-A" > "$t/boot_id"

  # ---- (2) provision, happy path ---------------------------------------------------------------
  echo "[self-test] (2) provision — SSM then credential, SUSPENDED, password never printed"
  rc=0; _run p1 provision || rc=$?
  [ "$rc" -eq 0 ] && ok "(2a) rc=0" || { bad "(2a) rc=$rc"; sed 's/^/      /' "$t/p1.out"; }
  local pwv=""; [ -f "$t/log/ssm-value" ] && pwv="$(cat "$t/log/ssm-value")"
  [ "${#pwv}" -ge 40 ] && ok "(2b) SSM got a ${#pwv}-char value via file://" || bad "(2b) SSM value missing or short (${#pwv})"
  [ ! -e "$t/log/ssm-value-argv" ] && ok "(2c) the value was not on argv" || bad "(2c) the value was passed on argv"
  grep -q -- '--type SecureString' "$t/log/argv" && grep -q -- '--overwrite' "$t/log/argv" && ok "(2d) SecureString · --overwrite" || bad "(2d) put-parameter not SecureString/--overwrite"
  [ -n "$pwv" ] && [ "$(cat "$t/log/java-stdin" 2>/dev/null)" = "$pwv" ] && ok "(2e) the hashed password IS the stored one" || bad "(2e) java hashed a different value than SSM stores"
  if [ -n "$pwv" ] && { grep -qF -- "$pwv" "$t/p1.out" || grep -qF -- "$pwv" "$t/log/argv" || grep -qF -- "$pwv" "$t/log/sql"; }; then
    bad "(2f) the password appears in output / argv / SQL"
  else ok "(2f) the password is in no output, argv or SQL"; fi
  grep -q 'owner-operator:upsert-credential' "$t/log/sql" && grep -qF 'STUBHASH' "$t/log/sql" && ok "(2g) credential upserted with the hash" || bad "(2g) no credential upsert with the hash"
  awk '/owner-operator:set-SUSPENDED/{s=NR} /owner-operator:upsert-credential/{u=NR} END{exit !(s && u && s<u)}' "$t/log/sql" \
    && ok "(2h) SUSPENDED forced before the credential is written" || bad "(2h) SUSPENDED not forced (or not before the credential)"
  grep -q "$OWNER_ACCOUNT_ID" "$t/log/sql" && grep -q "'$OWNER_CREDENTIAL_TENANT'" "$t/log/sql" && ok "(2i) credential keyed by OWNER_ACCOUNT_ID in tenant iam" || bad "(2i) credential not keyed by the link key"
  [ "$(cat "$t/marker-p1" 2>/dev/null)" = "boot-A" ] && ok "(2j) boot marker written" || bad "(2j) boot marker not written"

  # ---- (3) once per boot ----------------------------------------------------------------------
  echo "[self-test] (3) a second provision in the same boot changes nothing"
  echo "boot-A" > "$t/marker-p2"
  rc=0; _run p2 provision || rc=$?
  if [ "$rc" -eq 0 ] && [ ! -s "$t/log/sql" ] && [ ! -e "$t/log/ssm-value" ]; then ok "(3) same boot_id → no SQL, no SSM"
  else bad "(3) same boot rotated or suspended again (rc=$rc)"; fi

  # ---- (4) SSM put fails → credential untouched ---------------------------------------------------
  echo "[self-test] (4) SSM put refused → no credential write, rc≠0, still SUSPENDED"
  rc=0; _run p3 provision STUB_PUT_RC=1 || rc=$?
  [ "$rc" -ne 0 ] && ok "(4a) rc=$rc" || bad "(4a) rc=0 on a refused put"
  ! grep -q 'owner-operator:upsert-credential' "$t/log/sql" && ok "(4b) credential not written" || bad "(4b) credential written although SSM refused the new password"
  grep -q 'owner-operator:set-SUSPENDED' "$t/log/sql" && ok "(4c) still forced SUSPENDED" || bad "(4c) not forced SUSPENDED"
  grep -q 'AccessDenied' "$t/p3.out" && grep -q 'terraform' "$t/p3.out" && ok "(4d) the warning names the policy apply" || bad "(4d) the warning does not say what to apply"
  [ ! -e "$t/marker-p3" ] && ok "(4e) no marker — a later run may retry" || bad "(4e) marker written on failure"

  # ---- (5) not ready → nothing written ------------------------------------------------------------
  echo "[self-test] (5) operator row absent → nothing written"
  rc=0; _run p4 provision STUB_OPERATOR=0 || rc=$?
  [ "$rc" -ne 0 ] && ! grep -qE 'owner-operator:(set-|upsert)' "$t/log/sql" && [ ! -e "$t/log/ssm-value" ] \
    && ok "(5) rc=$rc, no write, no SSM" || bad "(5) wrote something without the row (rc=$rc)"

  # ---- (6) activate / deactivate / status --------------------------------------------------------
  echo "[self-test] (6) activate → ACTIVE · deactivate → SUSPENDED + refresh revoke · status read-only"
  local touched_ssm=""
  rc=0; _run a1 activate || rc=$?
  grep -q 'ssm' "$t/log/argv" 2>/dev/null && touched_ssm="$touched_ssm activate"
  [ "$rc" -eq 0 ] && grep -q 'owner-operator:set-ACTIVE' "$t/log/sql" && grep -q 'status=ACTIVE' "$t/a1.out" \
    && ok "(6a) activate sets and reports ACTIVE" || bad "(6a) activate (rc=$rc)"
  rc=0; _run d1 deactivate || rc=$?
  grep -q 'ssm' "$t/log/argv" 2>/dev/null && touched_ssm="$touched_ssm deactivate"
  [ "$rc" -eq 0 ] && grep -q 'owner-operator:set-SUSPENDED' "$t/log/sql" && grep -q 'owner-operator:revoke-refresh' "$t/log/sql" \
    && grep -q 'status=SUSPENDED' "$t/d1.out" && ok "(6b) deactivate sets SUSPENDED, revokes refresh, reports" || bad "(6b) deactivate (rc=$rc)"
  rc=0; _run s1 status || rc=$?
  grep -q 'ssm' "$t/log/argv" 2>/dev/null && touched_ssm="$touched_ssm status"
  if [ "$rc" -eq 0 ] && ! grep -qE 'owner-operator:(set-|upsert|revoke)' "$t/log/sql"; then
    ok "(6c) status writes nothing"
  else bad "(6c) status wrote something (rc=$rc)"; fi
  [ -z "$touched_ssm" ] && ok "(6d) activate · deactivate · status never touch SSM (the secret is read only by the owner, off-host)" \
    || bad "(6d) touched SSM:$touched_ssm"

  # ---- (6e) off the demo host provision does nothing (verify-demo-wrapper's shimmed harnesses) ----
  rc=0; _run l1 provision DEMO_DOMAIN=local || rc=$?
  [ "$rc" -eq 0 ] && [ ! -s "$t/log/sql" ] && [ ! -e "$t/log/ssm-value" ] \
    && ok "(6e) DEMO_DOMAIN=local → skipped, nothing touched" || bad "(6e) DEMO_DOMAIN=local still provisioned (rc=$rc)"

  # ---- (7) unknown subcommand ------------------------------------------------------------------
  rc=0; _run u1 bogus || rc=$?
  [ "$rc" -eq 2 ] && ok "(7) unknown subcommand → rc=2" || bad "(7) unknown subcommand rc=$rc"

  # ---- (8) --check-undisclosed bites a README, spares tasks/ and the allowlisted seed ----------
  echo "[self-test] (8) --check-undisclosed — synthetic tree"
  local g="$t/undisclosed"
  mkdir -p "$g/tasks/ready" "$g/docs" \
           "$g/projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev"
  printf '# demo accounts\n\ndemo@demo.com\n' > "$g/README.md"
  printf 'decision: %s\n' "$OWNER_EMAIL" > "$g/tasks/ready/TASK-X.md"
  printf "INSERT … '%s' …\n" "$OWNER_OPERATOR_ID" \
    > "$g/projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_star_owner_operator.sql"
  git -C "$g" init -q; git -C "$g" config user.email t@l; git -C "$g" config user.name t
  git -C "$g" add -A >/dev/null; git -C "$g" commit -qm base
  rc=0; UNDISCLOSED_ROOT="$g" check_undisclosed >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 0 ] && ok "(8a) tasks/ + allowlisted seed → not a hit (control)" || bad "(8a) rc=$rc on the allowlist alone"
  local ident
  for ident in "$OWNER_EMAIL" "$OWNER_OPERATOR_ID" "$OWNER_ACCOUNT_ID"; do
    printf '%s\n' "$ident" > "$g/docs/guide.md"
    git -C "$g" add -A >/dev/null; git -C "$g" commit -qm "leak" >/dev/null
    rc=0; UNDISCLOSED_ROOT="$g" check_undisclosed >/dev/null 2>&1 || rc=$?
    [ "$rc" -eq 1 ] && ok "(8b) a guide naming '$ident' → rc=1" || bad "(8b) a guide naming '$ident' → rc=$rc (not caught)"
  done
  printf 'nothing\n' > "$g/docs/guide.md"; printf '%s\n' "$OWNER_EMAIL" >> "$g/README.md"
  git -C "$g" add -A >/dev/null; git -C "$g" commit -qm "readme leak" >/dev/null
  rc=0; UNDISCLOSED_ROOT="$g" check_undisclosed >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 1 ] && ok "(8c) README naming the email → rc=1" || bad "(8c) README leak not caught (rc=$rc)"

  echo "[self-test] pass=$pass fail=$failn"
  [ "$failn" -eq 0 ]
}

case "${1:-}" in
  provision)   provision ;;
  activate)    activate ;;
  deactivate)  deactivate ;;
  status)      _report ;;
  --check-undisclosed) check_undisclosed ;;
  --self-test) self_test ;;
  *) echo "usage: $(basename "$0") provision|activate|deactivate|status|--check-undisclosed|--self-test" >&2; exit 2 ;;
esac
