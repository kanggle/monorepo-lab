#!/bin/bash
# =============================================================================
# Kafka Topic Creation Script for Global Account Platform
# =============================================================================
# Idempotent: safe to run multiple times. Existing topics are skipped.
#
# Usage:
#   ./scripts/kafka-create-topics.sh                    # default: localhost:9092
#   KAFKA_BOOTSTRAP=kafka:9092 ./scripts/kafka-create-topics.sh  # custom broker
# =============================================================================

set -euo pipefail

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

# Topic configuration
MAIN_PARTITIONS=3
DLQ_PARTITIONS=1
REPLICATION_FACTOR=1
MAIN_RETENTION_MS=604800000    # 7 days
DLQ_RETENTION_MS=2592000000    # 30 days

# All 14 main topics
TOPICS=(
  "auth.login.attempted"
  "auth.login.failed"
  "auth.login.succeeded"
  "auth.token.refreshed"
  "auth.token.reuse.detected"
  "account.created"
  "account.status.changed"
  "account.locked"
  "account.unlocked"
  "account.deleted"
  "session.revoked"
  "security.suspicious.detected"
  "security.auto.lock.triggered"
  "admin.action.performed"
)

echo "=== Kafka Topic Creation ==="
echo "Bootstrap: ${KAFKA_BOOTSTRAP}"
echo ""

create_topic() {
  local topic_name="$1"
  local partitions="$2"
  local retention_ms="$3"

  echo -n "Creating topic: ${topic_name} (partitions=${partitions}, retention=${retention_ms}ms)... "

  ${KAFKA_BIN}/kafka-topics.sh \
    --bootstrap-server "${KAFKA_BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "${topic_name}" \
    --partitions "${partitions}" \
    --replication-factor "${REPLICATION_FACTOR}" \
    --config retention.ms="${retention_ms}" \
    --config cleanup.policy=delete \
    2>/dev/null && echo "OK" || echo "SKIPPED (already exists)"
}

# Create main topics
echo "--- Main Topics (${#TOPICS[@]}) ---"
for topic in "${TOPICS[@]}"; do
  create_topic "${topic}" "${MAIN_PARTITIONS}" "${MAIN_RETENTION_MS}"
done

echo ""

# Create DLQ topics
echo "--- DLQ Topics (${#TOPICS[@]}) ---"
for topic in "${TOPICS[@]}"; do
  create_topic "${topic}.dlq" "${DLQ_PARTITIONS}" "${DLQ_RETENTION_MS}"
done

echo ""
echo "=== Topic Creation Complete ==="

# List all topics for verification
echo ""
echo "--- Existing Topics ---"
${KAFKA_BIN}/kafka-topics.sh \
  --bootstrap-server "${KAFKA_BOOTSTRAP}" \
  --list 2>/dev/null | grep -E "^(auth\.|account\.|session\.|security\.|admin\.)" | sort || true

echo ""
echo "Total GAP topics: $(${KAFKA_BIN}/kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" --list 2>/dev/null | grep -cE "^(auth\.|account\.|session\.|security\.|admin\.)" || echo 0)"
