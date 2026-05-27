#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"

mkdir -p "$LOG_DIR"
cd "$ROOT_DIR"

start_service() {
  local service_name="$1"
  local module_name="$2"
  local pid_file="$RUN_DIR/${service_name}.pid"
  local log_file="$LOG_DIR/${service_name}.log"

  if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    echo "$service_name is already running (pid $(cat "$pid_file"))."
    return
  fi

  echo "Starting $service_name..."
  nohup mvn -pl "$module_name" spring-boot:run >"$log_file" 2>&1 &
  echo $! > "$pid_file"
}

echo "Starting infrastructure with Docker Compose..."
docker compose up -d

start_service "auth-service" "auth-service"
start_service "loan-service" "loan-service"
start_service "notification-service" "notification-service"
start_service "api-gateway-service" "api-gateway-service"
start_service "test-client" "test-client"

echo ""
echo "All services started."
echo "Logs: $LOG_DIR"
echo "Test client: http://localhost:8084/test/health"
echo "Use ./stop-all.sh to stop services and infra."
