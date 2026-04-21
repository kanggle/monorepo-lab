#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$(dirname "$SCRIPT_DIR")"
EXIT_CODE=0

TOTAL_CHECKS=13
echo "=== K8s Security Hardening Validation ==="
echo ""

# 1. SecurityContext: runAsNonRoot and capabilities.drop ALL
echo "[1/$TOTAL_CHECKS] Checking SecurityContext (runAsNonRoot, capabilities.drop ALL)..."
SC_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")

  if ! grep -q "runAsNonRoot: true" "$deploy"; then
    echo "  FAIL: $svc — missing runAsNonRoot: true"
    EXIT_CODE=1
    SC_EXIT=1
  fi

  if ! grep -q '"ALL"' "$deploy"; then
    echo "  FAIL: $svc — missing capabilities.drop: [\"ALL\"]"
    EXIT_CODE=1
    SC_EXIT=1
  fi

  if ! grep -q "allowPrivilegeEscalation: false" "$deploy"; then
    echo "  FAIL: $svc — missing allowPrivilegeEscalation: false"
    EXIT_CODE=1
    SC_EXIT=1
  fi

  if ! grep -q "readOnlyRootFilesystem: true" "$deploy"; then
    echo "  FAIL: $svc — missing readOnlyRootFilesystem: true"
    EXIT_CODE=1
    SC_EXIT=1
  fi
done
if [ $SC_EXIT -eq 0 ]; then
  echo "  PASS: All deployments have SecurityContext configured"
fi
echo ""

# 2. NetworkPolicy existence
echo "[2/$TOTAL_CHECKS] Checking NetworkPolicy definitions..."
NETPOL_DIR="$K8S_DIR/network-policies"
NETPOL_EXIT=0
if [ ! -f "$NETPOL_DIR/default-deny.yaml" ]; then
  echo "  FAIL: Missing default-deny NetworkPolicy"
  EXIT_CODE=1
  NETPOL_EXIT=1
fi
for svc_dir in "$K8S_DIR"/services/*/; do
  svc=$(basename "$svc_dir")
  if [ ! -f "$NETPOL_DIR/$svc.yaml" ]; then
    echo "  FAIL: Missing NetworkPolicy for $svc"
    EXIT_CODE=1
    NETPOL_EXIT=1
  fi
done
if [ $NETPOL_EXIT -eq 0 ]; then
  echo "  PASS: All services have NetworkPolicy defined"
fi
echo ""

# 3. Secrets: no plaintext CHANGE_ME_IN_PRODUCTION
echo "[3/$TOTAL_CHECKS] Checking secrets for plaintext values..."
SECRETS_FILE="$K8S_DIR/base/secrets.yaml"
if grep -q "CHANGE_ME_IN_PRODUCTION" "$SECRETS_FILE"; then
  echo "  FAIL: secrets.yaml contains plaintext 'CHANGE_ME_IN_PRODUCTION'"
  EXIT_CODE=1
else
  echo "  PASS: No plaintext secrets found"
fi
if grep -q "kind: Secret" "$SECRETS_FILE" && ! grep -q "kind: SealedSecret" "$SECRETS_FILE"; then
  echo "  FAIL: secrets.yaml uses plain Secret instead of SealedSecret"
  EXIT_CODE=1
else
  echo "  PASS: SealedSecret pattern in use"
fi
echo ""

# 4. Image tag: no :latest
echo "[4/$TOTAL_CHECKS] Checking image tags (no :latest)..."
IMG_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  if grep -q "image:.*:latest" "$deploy"; then
    echo "  FAIL: $svc — uses :latest image tag"
    EXIT_CODE=1
    IMG_EXIT=1
  fi
done
if [ $IMG_EXIT -eq 0 ]; then
  echo "  PASS: No deployments use :latest tag"
fi
echo ""

# 5. imagePullPolicy
echo "[5/$TOTAL_CHECKS] Checking imagePullPolicy..."
PULL_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  if ! grep -q "imagePullPolicy:" "$deploy"; then
    echo "  FAIL: $svc — missing imagePullPolicy"
    EXIT_CODE=1
    PULL_EXIT=1
  fi
done
if [ $PULL_EXIT -eq 0 ]; then
  echo "  PASS: All deployments have imagePullPolicy set"
fi
echo ""

# 6. checksum/config annotation: no PLACEHOLDER
echo "[6/$TOTAL_CHECKS] Checking checksum/config annotations..."
CHECKSUM_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  if grep -q 'checksum/config:.*PLACEHOLDER' "$deploy"; then
    echo "  FAIL: $svc — checksum/config is still PLACEHOLDER"
    EXIT_CODE=1
    CHECKSUM_EXIT=1
  fi
  if ! grep -q "checksum/config:" "$deploy"; then
    echo "  FAIL: $svc — missing checksum/config annotation"
    EXIT_CODE=1
    CHECKSUM_EXIT=1
  fi
done
if [ $CHECKSUM_EXIT -eq 0 ]; then
  echo "  PASS: All checksum/config annotations are properly configured"
fi
echo ""

# 7. seccompProfile: RuntimeDefault
echo "[7/$TOTAL_CHECKS] Checking seccompProfile..."
SECCOMP_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  if ! grep -q "type: RuntimeDefault" "$deploy"; then
    echo "  FAIL: $svc — missing seccompProfile: RuntimeDefault"
    EXIT_CODE=1
    SECCOMP_EXIT=1
  fi
done
if [ $SECCOMP_EXIT -eq 0 ]; then
  echo "  PASS: All deployments have seccompProfile: RuntimeDefault"
fi
echo ""

# 8. automountServiceAccountToken: false
echo "[8/$TOTAL_CHECKS] Checking automountServiceAccountToken..."
AUTOMOUNT_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  if ! grep -q "automountServiceAccountToken: false" "$deploy"; then
    echo "  FAIL: $svc — missing automountServiceAccountToken: false"
    EXIT_CODE=1
    AUTOMOUNT_EXIT=1
  fi
done
if [ $AUTOMOUNT_EXIT -eq 0 ]; then
  echo "  PASS: All deployments have automountServiceAccountToken: false"
fi
echo ""

# 9. runAsUser and runAsGroup
echo "[9/$TOTAL_CHECKS] Checking runAsUser and runAsGroup..."
RUNAS_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  if ! grep -q "runAsUser:" "$deploy"; then
    echo "  FAIL: $svc — missing runAsUser"
    EXIT_CODE=1
    RUNAS_EXIT=1
  fi
  if ! grep -q "runAsGroup:" "$deploy"; then
    echo "  FAIL: $svc — missing runAsGroup"
    EXIT_CODE=1
    RUNAS_EXIT=1
  fi
done
if [ $RUNAS_EXIT -eq 0 ]; then
  echo "  PASS: All deployments have runAsUser and runAsGroup set"
fi
echo ""

# 10. PodDisruptionBudget for replicas >= 2
echo "[10/$TOTAL_CHECKS] Checking PodDisruptionBudget..."
PDB_EXIT=0
for deploy in "$K8S_DIR"/services/*/deployment.yaml; do
  svc=$(basename "$(dirname "$deploy")")
  svc_dir=$(dirname "$deploy")
  replicas=$(grep "replicas:" "$deploy" | head -1 | awk '{print $2}')
  if [ "$replicas" -ge 2 ] 2>/dev/null; then
    if [ ! -f "$svc_dir/pdb.yaml" ]; then
      echo "  FAIL: $svc — replicas=$replicas but missing pdb.yaml"
      EXIT_CODE=1
      PDB_EXIT=1
    fi
  fi
done
if [ $PDB_EXIT -eq 0 ]; then
  echo "  PASS: All services with replicas >= 2 have PodDisruptionBudget"
fi
echo ""

# 11. Ingress: no server-snippets annotation (CVE-2021-25742)
echo "[11/$TOTAL_CHECKS] Checking Ingress for deprecated server-snippets..."
INGRESS_FILE="$K8S_DIR/ingress/ingress.yaml"
SNIPPET_EXIT=0
if grep -q "server-snippets" "$INGRESS_FILE" 2>/dev/null; then
  echo "  FAIL: ingress.yaml uses deprecated server-snippets annotation (CVE-2021-25742)"
  EXIT_CODE=1
  SNIPPET_EXIT=1
fi
if [ $SNIPPET_EXIT -eq 0 ]; then
  echo "  PASS: No deprecated server-snippets annotation"
fi
echo ""

# 12. Security response headers ConfigMap exists
echo "[12/$TOTAL_CHECKS] Checking security response headers ConfigMap..."
HEADERS_CM="$K8S_DIR/ingress/security-response-headers.yaml"
HEADERS_EXIT=0
if [ ! -f "$HEADERS_CM" ]; then
  echo "  FAIL: Missing security-response-headers ConfigMap"
  EXIT_CODE=1
  HEADERS_EXIT=1
else
  for header in "X-Frame-Options" "X-Content-Type-Options" "Referrer-Policy"; do
    if ! grep -q "$header" "$HEADERS_CM"; then
      echo "  FAIL: security-response-headers missing $header"
      EXIT_CODE=1
      HEADERS_EXIT=1
    fi
  done
fi
if [ $HEADERS_EXIT -eq 0 ]; then
  echo "  PASS: Security response headers ConfigMap is complete"
fi
echo ""

# 13. SSR services have gateway-service egress in NetworkPolicy
echo "[13/$TOTAL_CHECKS] Checking SSR service NetworkPolicy egress to gateway-service..."
SSR_EXIT=0
SSR_SERVICES="web-store"
for svc in $SSR_SERVICES; do
  netpol="$K8S_DIR/network-policies/$svc.yaml"
  if [ -f "$netpol" ]; then
    if ! grep -q "app: gateway-service" "$netpol"; then
      echo "  FAIL: $svc — SSR service missing egress to gateway-service"
      EXIT_CODE=1
      SSR_EXIT=1
    fi
  fi
done
if [ $SSR_EXIT -eq 0 ]; then
  echo "  PASS: SSR services have gateway-service egress"
fi
echo ""

echo "=== Validation Complete ==="
if [ $EXIT_CODE -eq 0 ]; then
  echo "Result: ALL CHECKS PASSED"
else
  echo "Result: SOME CHECKS FAILED"
fi
exit $EXIT_CODE
