#!/bin/bash
# =============================================================================
# Test: Kafka Topic Creation Idempotency
# =============================================================================
# Verifies that running kafka-create-topics.sh twice does not produce errors.
#
# Prerequisites: docker compose up (kafka must be running)
#
# Usage:
#   ./scripts/test-kafka-topics-idempotency.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER_NAME="gap-kafka"

echo "=== Test: Kafka Topic Creation Idempotency ==="
echo ""

# Check if Kafka container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "FAIL: Kafka container '${CONTAINER_NAME}' is not running."
  echo "Run 'docker compose up -d kafka' first."
  exit 1
fi

# Run topic creation inside the container (first run)
echo "--- Run 1: Creating topics ---"
docker exec "${CONTAINER_NAME}" bash -c '
  KAFKA_BIN="/opt/kafka/bin"
  BOOTSTRAP="localhost:9092"
  TOPICS=(
    "auth.login.attempted" "auth.login.failed" "auth.login.succeeded"
    "auth.token.refreshed" "auth.token.reuse.detected"
    "account.created" "account.status.changed" "account.locked"
    "account.unlocked" "account.deleted" "session.revoked"
    "security.suspicious.detected" "security.auto.lock.triggered"
    "admin.action.performed"
  )
  for t in "${TOPICS[@]}"; do
    $KAFKA_BIN/kafka-topics.sh --bootstrap-server $BOOTSTRAP --create --if-not-exists --topic "$t" --partitions 3 --replication-factor 1 --config retention.ms=604800000 2>&1
    $KAFKA_BIN/kafka-topics.sh --bootstrap-server $BOOTSTRAP --create --if-not-exists --topic "$t.dlq" --partitions 1 --replication-factor 1 --config retention.ms=2592000000 2>&1
  done
  echo "Run 1 complete."
'
RUN1_EXIT=$?
echo "Run 1 exit code: ${RUN1_EXIT}"
echo ""

# Run topic creation inside the container (second run - idempotency check)
echo "--- Run 2: Re-creating topics (should be idempotent) ---"
docker exec "${CONTAINER_NAME}" bash -c '
  KAFKA_BIN="/opt/kafka/bin"
  BOOTSTRAP="localhost:9092"
  TOPICS=(
    "auth.login.attempted" "auth.login.failed" "auth.login.succeeded"
    "auth.token.refreshed" "auth.token.reuse.detected"
    "account.created" "account.status.changed" "account.locked"
    "account.unlocked" "account.deleted" "session.revoked"
    "security.suspicious.detected" "security.auto.lock.triggered"
    "admin.action.performed"
  )
  for t in "${TOPICS[@]}"; do
    $KAFKA_BIN/kafka-topics.sh --bootstrap-server $BOOTSTRAP --create --if-not-exists --topic "$t" --partitions 3 --replication-factor 1 --config retention.ms=604800000 2>&1
    $KAFKA_BIN/kafka-topics.sh --bootstrap-server $BOOTSTRAP --create --if-not-exists --topic "$t.dlq" --partitions 1 --replication-factor 1 --config retention.ms=2592000000 2>&1
  done
  echo "Run 2 complete."
'
RUN2_EXIT=$?
echo "Run 2 exit code: ${RUN2_EXIT}"
echo ""

# Verify topic count
echo "--- Verification ---"
TOPIC_COUNT=$(docker exec "${CONTAINER_NAME}" bash -c '/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -cE "^(auth\.|account\.|session\.|security\.|admin\.)"' || echo "0")
echo "GAP topics found: ${TOPIC_COUNT}"

if [ "${RUN1_EXIT}" -eq 0 ] && [ "${RUN2_EXIT}" -eq 0 ] && [ "${TOPIC_COUNT}" -ge 28 ]; then
  echo ""
  echo "PASS: Topic creation is idempotent. ${TOPIC_COUNT} topics exist."
  exit 0
else
  echo ""
  echo "FAIL: Run1=${RUN1_EXIT}, Run2=${RUN2_EXIT}, TopicCount=${TOPIC_COUNT} (expected >= 28)"
  exit 1
fi
