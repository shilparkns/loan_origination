#!/usr/bin/env bash
# Shared full loan lifecycle helpers. Source from run-lifecycle wrappers only.

lifecycle_require_tools() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required. Install with: brew install jq"
    exit 1
  fi
  if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required."
    exit 1
  fi
}

lifecycle_register_login() {
  local role="$1"
  local email="$2"
  local first="${3:-Test}"
  local last="${4:-User}"

  local body
  body=$(jq -n \
    --arg fn "$first" \
    --arg ln "$last" \
    --arg em "$email" \
    --arg pw "pass123" \
    --arg role "$role" \
    '{firstName: $fn, lastName: $ln, email: $em, password: $pw, role: $role}')

  local resp token
  resp=$(curl -s -X POST "${LIFECYCLE_BASE_URL}${LIFECYCLE_AUTH_PATH}/register" \
    -H "Content-Type: application/json" \
    -d "$body")
  token=$(echo "$resp" | jq -r '.token // empty')

  if [[ -z "$token" ]]; then
    resp=$(curl -s -X POST "${LIFECYCLE_BASE_URL}${LIFECYCLE_AUTH_PATH}/login" \
      -H "Content-Type: application/json" \
      -d "$(jq -n --arg em "$email" --arg pw "pass123" '{email: $em, password: $pw}')")
    token=$(echo "$resp" | jq -r '.token // empty')
  fi

  if [[ -z "$token" ]]; then
    echo "Failed to obtain token for role=$role email=$email"
    echo "$resp" | jq '.' 2>/dev/null || echo "$resp"
    exit 1
  fi

  echo "$token"
}

lifecycle_expect_status_field() {
  local json="$1"
  local expected="$2"
  local actual
  actual=$(echo "$json" | jq -r '.status // empty')
  if [[ "$actual" != "$expected" ]]; then
    echo "Expected status=$expected but got status=$actual"
    echo "$json" | jq '.'
    exit 1
  fi
}

lifecycle_run_full() {
  local label="$1"
  local ts
  ts="$(date +%s)"

  echo "=========================================="
  echo "Full lifecycle via $label"
  echo "Base URL: $LIFECYCLE_BASE_URL"
  echo "Run id: $ts"
  echo "=========================================="
  echo ""

  echo "=== 1. Borrower: register, login, create loan (APPLIED) ==="
  local borrower_email="borrower-${ts}@test.com"
  local borrower_token
  borrower_token=$(lifecycle_register_login "BORROWER" "$borrower_email" "John" "Borrower")

  local loan_resp loan_id
  loan_resp=$(curl -s -X POST "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}" \
    -H "Authorization: Bearer $borrower_token" \
    -H "Content-Type: application/json" \
    -d '{"loanAmount": 250000, "propertyAddress": "123 Main St"}')
  echo "$loan_resp" | jq '.'
  loan_id=$(echo "$loan_resp" | jq -r '.id // empty')
  if [[ -z "$loan_id" || "$loan_id" == "null" ]]; then
    echo "Create loan failed."
    exit 1
  fi
  lifecycle_expect_status_field "$loan_resp" "APPLIED"
  echo "Loan ID: $loan_id"
  echo ""

  echo "=== 2. Credit officer: APPLIED -> UNDER_REVIEW ==="
  local officer_token
  officer_token=$(lifecycle_register_login "CREDIT_OFFICER" "officer-${ts}@test.com")
  loan_resp=$(curl -s -X PATCH "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}/status" \
    -H "Authorization: Bearer $officer_token" \
    -H "Content-Type: application/json" \
    -d '{"toStatus": "UNDER_REVIEW"}')
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "UNDER_REVIEW"
  echo ""

  echo "=== 3. Appraiser: submit assessment (UNDER_REVIEW -> ASSESSED) ==="
  local appraiser_token
  appraiser_token=$(lifecycle_register_login "APPRAISER" "appraiser-${ts}@test.com")
  loan_resp=$(curl -s -X POST "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}/assessment" \
    -H "Authorization: Bearer $appraiser_token" \
    -H "Content-Type: application/json" \
    -d '{"assessedValue": 350000}')
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "ASSESSED"
  echo ""

  echo "=== 4. Underwriter: decision APPROVED (ASSESSED -> APPROVED) ==="
  local underwriter_token
  underwriter_token=$(lifecycle_register_login "UNDERWRITER" "underwriter-${ts}@test.com")
  loan_resp=$(curl -s -X POST "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}/decision" \
    -H "Authorization: Bearer $underwriter_token" \
    -H "Content-Type: application/json" \
    -d '{"decision": "APPROVED", "notes": "Strong profile"}')
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "APPROVED"
  echo ""

  echo "=== 5. Legal: APPROVED -> LEGAL_REVIEW ==="
  local legal_token
  legal_token=$(lifecycle_register_login "LEGAL" "legal-${ts}@test.com")
  loan_resp=$(curl -s -X PATCH "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}/status" \
    -H "Authorization: Bearer $legal_token" \
    -H "Content-Type: application/json" \
    -d '{"toStatus": "LEGAL_REVIEW"}')
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "LEGAL_REVIEW"
  echo ""

  echo "=== 6. Legal: upload document (status unchanged) ==="
  loan_resp=$(curl -s -X POST "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}/documents" \
    -H "Authorization: Bearer $legal_token" \
    -H "Content-Type: application/json" \
    -d '{"documentType": "deed", "filePath": "/docs/deed_'${loan_id}'.pdf"}')
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "LEGAL_REVIEW"
  echo ""

  echo "=== 7. Disbursement: LEGAL_REVIEW -> DISBURSED ==="
  local disbursement_token
  disbursement_token=$(lifecycle_register_login "DISBURSEMENT" "disbursement-${ts}@test.com")
  loan_resp=$(curl -s -X PATCH "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}/disburse" \
    -H "Authorization: Bearer $disbursement_token")
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "DISBURSED"
  echo ""

  echo "=== 8. Borrower: verify final loan details ==="
  loan_resp=$(curl -s -X GET "${LIFECYCLE_BASE_URL}${LIFECYCLE_LOANS_PATH}/${loan_id}" \
    -H "Authorization: Bearer $borrower_token")
  echo "$loan_resp" | jq '.'
  lifecycle_expect_status_field "$loan_resp" "DISBURSED"
  echo ""

  lifecycle_verify_side_effects "$loan_id" "$label"
}

lifecycle_verify_side_effects() {
  local loan_id="$1"
  local label="$2"

  echo "=== 9. EP7-T4 checks: audit log, Kafka consumers (notification DB) ==="
  echo "Waiting 3s for Kafka consumers..."
  sleep 3

  if docker ps --format '{{.Names}}' | grep -q '^loan-db$'; then
    echo "--- audit_logs (loan-db) for loan $loan_id ---"
    docker exec loan-db psql -U loanuser -d loandb -c \
      "SELECT id, from_status, to_status, notes FROM audit_logs WHERE loan_application_id = ${loan_id} ORDER BY id;"
    local audit_count
    audit_count=$(docker exec loan-db psql -U loanuser -d loandb -t -A -c \
      "SELECT COUNT(*) FROM audit_logs WHERE loan_application_id = ${loan_id};")
    if [[ "${audit_count:-0}" -lt 5 ]]; then
      echo "Warning: expected at least 5 audit rows, got ${audit_count}"
    fi
  else
    echo "Skip audit_logs check: loan-db container not running."
  fi

  if docker ps --format '{{.Names}}' | grep -q '^notification-db$'; then
    echo "--- loan_status_events (notification-db) for loan $loan_id ---"
    docker exec notification-db psql -U notifyuser -d notificationdb -c \
      "SELECT id, from_status, to_status, changed_by FROM loan_status_events WHERE loan_id = ${loan_id} ORDER BY id;"
    echo "--- notification_log for loan $loan_id ---"
    docker exec notification-db psql -U notifyuser -d notificationdb -c \
      "SELECT id, notified_role, message FROM notification_log WHERE loan_id = ${loan_id} ORDER BY id;"
    local event_count notify_count
    event_count=$(docker exec notification-db psql -U notifyuser -d notificationdb -t -A -c \
      "SELECT COUNT(*) FROM loan_status_events WHERE loan_id = ${loan_id};")
    notify_count=$(docker exec notification-db psql -U notifyuser -d notificationdb -t -A -c \
      "SELECT COUNT(*) FROM notification_log WHERE loan_id = ${loan_id};")
    if [[ "${event_count:-0}" -lt 4 ]]; then
      echo "Warning: expected multiple loan_status_events rows, got ${event_count}"
    fi
    if [[ "${notify_count:-0}" -lt 4 ]]; then
      echo "Warning: expected multiple notification_log rows, got ${notify_count}"
    fi
  else
    echo "Skip notification DB checks: notification-db container not running."
  fi

  if docker ps --format '{{.Names}}' | grep -q '^kafka$'; then
    echo "--- Kafka topic peek (loan-status-changes, last 20 lines) ---"
    docker exec kafka kafka-console-consumer \
      --bootstrap-server localhost:9092 \
      --topic loan-status-changes \
      --from-beginning \
      --timeout-ms 3000 2>/dev/null | tail -20 || true
  fi

  echo ""
  echo "=========================================="
  echo "Lifecycle complete ($label). Loan $loan_id -> DISBURSED"
  echo "=========================================="
}
