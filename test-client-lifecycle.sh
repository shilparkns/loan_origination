#!/usr/bin/env bash
# Full loan lifecycle via test-client (8084) -> api-gateway (8080) -> services.
# Prerequisite: ./start-all.sh (includes test-client)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/lifecycle-common.sh
source "$ROOT_DIR/scripts/lifecycle-common.sh"

export LIFECYCLE_BASE_URL="${CLIENT_URL:-http://localhost:8084}"
export LIFECYCLE_AUTH_PATH="/test/auth"
export LIFECYCLE_LOANS_PATH="/test/loans"

lifecycle_require_tools

echo "=== 0. test-client health ==="
curl -sf "${LIFECYCLE_BASE_URL}/test/health" | jq '.'
echo ""

lifecycle_run_full "test-client (${LIFECYCLE_BASE_URL})"
