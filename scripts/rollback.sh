#!/usr/bin/env bash
# =============================================================
# rollback.sh – Roll back the Workout Plan app to a previous
# Docker image version.
#
# Usage:
#   ./scripts/rollback.sh <previous-version>
#   ./scripts/rollback.sh 1.0.5
#
# The script:
#   1. Stops the current container
#   2. Starts a container using the specified version tag
#   3. Verifies the health endpoint
#   4. Prints rollback success or failure
# =============================================================

set -euo pipefail

CONTAINER_NAME="workout-app"
DOCKER_IMAGE="${DOCKER_IMAGE:-devopsproject/workout-plan-system}"
HEALTH_URL="http://localhost:8080/actuator/health"
NETWORK="workout-network"

# ---------- Validate argument ----------
if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <version>"
  echo "Example: $0 1.0.5"
  exit 1
fi

ROLLBACK_VERSION="$1"
ROLLBACK_IMAGE="${DOCKER_IMAGE}:${ROLLBACK_VERSION}"

echo "=============================================="
echo "🔄  ROLLBACK INITIATED"
echo "    Target image : ${ROLLBACK_IMAGE}"
echo "    Container    : ${CONTAINER_NAME}"
echo "=============================================="

# ---------- Check if rollback image exists locally or can be pulled ----------
if ! docker image inspect "${ROLLBACK_IMAGE}" > /dev/null 2>&1; then
  echo "📥 Image not found locally. Attempting to pull ${ROLLBACK_IMAGE}..."
  docker pull "${ROLLBACK_IMAGE}" || {
    echo "❌ Could not pull image ${ROLLBACK_IMAGE}. Rollback aborted."
    exit 1
  }
fi

# ---------- Stop current container ----------
echo "🛑 Stopping current container..."
docker stop "${CONTAINER_NAME}" 2>/dev/null && echo "   Container stopped." || echo "   Container was not running."
docker rm   "${CONTAINER_NAME}" 2>/dev/null && echo "   Container removed." || true

# ---------- Start rollback container ----------
echo "🚀 Starting rollback container with image ${ROLLBACK_IMAGE}..."
docker run -d \
  --name "${CONTAINER_NAME}" \
  --network "${NETWORK}" \
  -p 8080:8080 \
  -e DB_URL="jdbc:mysql://workout-db:3306/workout_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USERNAME="${DB_USERNAME:-workout_user}" \
  -e DB_PASSWORD="${DB_PASSWORD:-workout_pass}" \
  --restart unless-stopped \
  "${ROLLBACK_IMAGE}"

echo "⏳ Waiting for application to become healthy..."
MAX_RETRIES=12
COUNT=0
until curl -sf "${HEALTH_URL}" | grep -q '"status":"UP"' 2>/dev/null; do
  COUNT=$((COUNT + 1))
  if [ "${COUNT}" -ge "${MAX_RETRIES}" ]; then
    echo "❌ Health check FAILED after ${MAX_RETRIES} retries."
    echo "   Container logs:"
    docker logs "${CONTAINER_NAME}" --tail 30
    exit 1
  fi
  echo "   Attempt ${COUNT}/${MAX_RETRIES} – waiting 10s..."
  sleep 10
done

echo ""
echo "=============================================="
echo "✅  ROLLBACK SUCCESSFUL"
echo "    Running version : ${ROLLBACK_VERSION}"
echo "    Health endpoint : ${HEALTH_URL}"
echo "=============================================="

docker ps --filter "name=${CONTAINER_NAME}"
