#!/usr/bin/env bash
# Quick smoke test via test-client (register, login, create/list/get loan).
# For full lifecycle (EP7-T4), use: ./test-client-lifecycle.sh
# Prerequisite: ./start-all.sh (or infra + all services including test-client running)

set -euo pipefail

CLIENT_URL="${CLIENT_URL:-http://localhost:8084}"
EMAIL="${TEST_EMAIL:-borrower-$(date +%s)@test.com}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required. Install with: brew install jq"
  exit 1
fi

echo "Using test-client at $CLIENT_URL"
echo "Register email: $EMAIL"
echo ""

echo "=== 0. test-client health ==="
curl -sf "$CLIENT_URL/test/health" | jq '.'
echo ""

echo "=== 1. Register borrower (via test-client -> gateway -> auth) ==="
REGISTER_RESPONSE=$(curl -s -X POST "$CLIENT_URL/test/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"firstName\": \"John\",
    \"lastName\": \"Doe\",
    \"email\": \"$EMAIL\",
    \"password\": \"pass123\",
    \"role\": \"BORROWER\"
  }")

echo "$REGISTER_RESPONSE" | jq '.'
TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token // empty')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Register failed (no token). Aborting."
  exit 1
fi
echo ""

echo "=== 2. Login (via test-client) ==="
LOGIN_RESPONSE=$(curl -s -X POST "$CLIENT_URL/test/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$EMAIL\",
    \"password\": \"pass123\"
  }")

echo "$LOGIN_RESPONSE" | jq '.'
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
echo "Token: ${TOKEN:0:40}..."
echo ""

echo "=== 3. Create loan (via test-client) ==="
LOAN_RESPONSE=$(curl -s -X POST "$CLIENT_URL/test/loans" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "loanAmount": 250000,
    "propertyAddress": "123 Main St"
  }')

echo "$LOAN_RESPONSE" | jq '.'
LOAN_ID=$(echo "$LOAN_RESPONSE" | jq -r '.id')
if [[ -z "$LOAN_ID" || "$LOAN_ID" == "null" ]]; then
  echo "Create loan failed. Aborting."
  exit 1
fi
echo "Loan ID: $LOAN_ID"
echo ""

echo "=== 4. List loans (via test-client) ==="
curl -s -X GET "$CLIENT_URL/test/loans" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

echo "=== 5. Get loan details (via test-client) ==="
curl -s -X GET "$CLIENT_URL/test/loans/$LOAN_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

echo "Done. All calls went through test-client ($CLIENT_URL/test/...)."
