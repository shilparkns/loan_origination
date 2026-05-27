#!/usr/bin/env bash
# EP7-T4: full loan lifecycle directly through api-gateway (port 8080).
# Prerequisite: ./start-all.sh (infra + auth + loan + notification + gateway)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/lifecycle-common.sh
source "$ROOT_DIR/scripts/lifecycle-common.sh"

export LIFECYCLE_BASE_URL="${GATEWAY_URL:-http://localhost:8080}"
export LIFECYCLE_AUTH_PATH="/auth"
export LIFECYCLE_LOANS_PATH="/api/loans"

lifecycle_require_tools
lifecycle_run_full "api-gateway (${LIFECYCLE_BASE_URL})"
