#!/usr/bin/env bash
# =============================================================
# deploy.sh – Deploy (or roll back to) a specific image version with
# Docker Compose, verify health, and automatically recover on failure.
#
# Required environment:
#   APP_IMAGE     e.g. devopsproject/workout-plan-system
#   APP_VERSION   e.g. 1.0.42
#   ENV_FILE      path to a file holding DB_PASSWORD, DB_ROOT_PASSWORD,
#                 APP_ADMIN_PASSWORD, APP_TRAINER_PASSWORD (never committed)
# Optional:
#   APP_PORT (8080)        NO_AUTO_ROLLBACK=1 (do not restore previous version)
#   HEALTH_TIMEOUT (180)   COMPOSE_FILE_PATH (docker-compose.yml)
#
# Sequence: validate config -> ensure image present -> start/update database
#           -> record running version -> replace app -> verify health ->
#           on failure restore the previous version.
# NOTE: the app container is recreated in place, so there is a short outage
#       during the switch. This is NOT a zero-downtime deployment.
# =============================================================
set -euo pipefail

: "${APP_IMAGE:?APP_IMAGE is required}"
: "${APP_VERSION:?APP_VERSION is required}"
: "${ENV_FILE:?ENV_FILE is required (file with DB/admin secrets)}"
APP_PORT="${APP_PORT:-8080}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"
COMPOSE_FILE_PATH="${COMPOSE_FILE_PATH:-docker-compose.yml}"
STATE_DIR="${STATE_DIR:-.deploy}"
HEALTH_URL="http://localhost:${APP_PORT}/actuator/health"

export APP_IMAGE APP_PORT
dc() { docker compose -f "${COMPOSE_FILE_PATH}" --env-file "${ENV_FILE}" "$@"; }

wait_healthy() {  # $1 = seconds
  local waited=0
  until curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; do
    if [ "${waited}" -ge "$1" ]; then return 1; fi
    sleep 5; waited=$((waited + 5))
  done
}

# 1. Validate ------------------------------------------------------------
[ -f "${ENV_FILE}" ] || { echo "ERROR: ENV_FILE '${ENV_FILE}' not found" >&2; exit 2; }
APP_VERSION="${APP_VERSION}" dc config -q || { echo "ERROR: compose configuration is invalid" >&2; exit 2; }
if ! docker image inspect "${APP_IMAGE}:${APP_VERSION}" >/dev/null 2>&1; then
  echo "Image ${APP_IMAGE}:${APP_VERSION} not found locally, pulling..."
  docker pull "${APP_IMAGE}:${APP_VERSION}" || { echo "ERROR: image ${APP_IMAGE}:${APP_VERSION} unavailable" >&2; exit 2; }
fi

# 2. Dependencies (creates the network and volume on first deployment) -----
APP_VERSION="${APP_VERSION}" dc up -d --wait --wait-timeout 120 db

# 3. Remember what is running now ---------------------------------------
PREVIOUS_VERSION=""
if docker inspect workout-app >/dev/null 2>&1; then
  PREVIOUS_IMAGE="$(docker inspect --format '{{.Config.Image}}' workout-app)"
  PREVIOUS_VERSION="${PREVIOUS_IMAGE##*:}"
fi
mkdir -p "${STATE_DIR}"

# 4. Deploy new version ------------------------------------------------------
echo "Deploying ${APP_IMAGE}:${APP_VERSION} (previous: ${PREVIOUS_VERSION:-none})"
if APP_VERSION="${APP_VERSION}" dc up -d --no-deps --wait --wait-timeout "${HEALTH_TIMEOUT}" app \
   && wait_healthy 60; then
  echo "${APP_VERSION}" > "${STATE_DIR}/current_version"
  [ -n "${PREVIOUS_VERSION}" ] && [ "${PREVIOUS_VERSION}" != "${APP_VERSION}" ] \
    && echo "${PREVIOUS_VERSION}" > "${STATE_DIR}/previous_version"
  echo "DEPLOY OK: ${APP_IMAGE}:${APP_VERSION} is healthy"
  exit 0
fi

# 5. Failure handling -------------------------------------------------------
echo "ERROR: ${APP_VERSION} did not become healthy. Last container logs:" >&2
docker logs --tail 50 workout-app >&2 || true
if [ "${NO_AUTO_ROLLBACK:-0}" != "1" ] && [ -n "${PREVIOUS_VERSION}" ] && [ "${PREVIOUS_VERSION}" != "${APP_VERSION}" ]; then
  echo "Restoring previous version ${PREVIOUS_VERSION}..." >&2
  if APP_VERSION="${PREVIOUS_VERSION}" dc up -d --no-deps --wait --wait-timeout "${HEALTH_TIMEOUT}" app; then
    echo "RECOVERED: ${PREVIOUS_VERSION} is running again. The new release was NOT deployed." >&2
  else
    echo "CRITICAL: previous version ${PREVIOUS_VERSION} also failed to start." >&2
  fi
else
  echo "No previous version available (first deployment) or auto-rollback disabled." >&2
fi
exit 1
