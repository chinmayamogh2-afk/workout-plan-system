#!/usr/bin/env bash
# =============================================================
# rollback.sh – Roll the application back to an earlier image version.
#
# Usage:
#   ENV_FILE=/path/to/secrets.env ./scripts/rollback.sh [version]
#
# Without an argument it uses the version recorded in .deploy/previous_version
# by the last successful deploy.sh run. Delegates to deploy.sh so the same
# validation and health verification apply.
# =============================================================
set -euo pipefail

cd "$(dirname "$0")/.."
STATE_DIR="${STATE_DIR:-.deploy}"

VERSION="${1:-}"
if [ -z "${VERSION}" ] && [ -f "${STATE_DIR}/previous_version" ]; then
  VERSION="$(cat "${STATE_DIR}/previous_version")"
fi
if [ -z "${VERSION}" ]; then
  echo "Usage: ENV_FILE=... $0 <version>   (no previous version recorded)" >&2
  exit 1
fi

export APP_IMAGE="${APP_IMAGE:-devopsproject/workout-plan-system}"
export APP_VERSION="${VERSION}"
export NO_AUTO_ROLLBACK=1   # we are already rolling back; do not bounce between versions

echo "Rolling back to ${APP_IMAGE}:${APP_VERSION}"
exec ./scripts/deploy.sh
