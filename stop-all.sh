#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"

stop_service() {
  local service_name="$1"
  local pid_file="$RUN_DIR/${service_name}.pid"

  if [[ ! -f "$pid_file" ]]; then
    echo "$service_name is not running (no pid file)."
    return
  fi

  local pid
  pid="$(cat "$pid_file")"

  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping $service_name (pid $pid)..."
    kill "$pid"
  else
    echo "$service_name process already stopped."
  fi

  rm -f "$pid_file"
}

stop_service "test-client"
stop_service "api-gateway-service"
stop_service "notification-service"
stop_service "loan-service"
stop_service "auth-service"

echo "Stopping infrastructure..."
docker compose down

echo "All services and infrastructure stopped."
