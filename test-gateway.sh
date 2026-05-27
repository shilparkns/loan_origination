#!/usr/bin/env bash
# Quick smoke test via api-gateway (register, login, create/list/get loan).
# For full lifecycle (EP7-T4), use: ./test-gateway-lifecycle.sh

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required. Install with: brew install jq"
  exit 1
fi

echo "=== 1. Register Borrower ==="
REGISTER_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@test.com",
    "password": "pass123",
    "role": "BORROWER"
  }')

echo "$REGISTER_RESPONSE" | jq '.'
TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token')
echo "Token: $TOKEN"
echo ""

echo "=== 2. Login ==="
LOGIN_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@test.com",
    "password": "pass123"
  }')

echo "$LOGIN_RESPONSE" | jq '.'
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
echo "Token: $TOKEN"
echo ""

echo "=== 3. Create Loan ==="
LOAN_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/api/loans" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "loanAmount": 250000,
    "propertyAddress": "123 Main St"
  }')

echo "$LOAN_RESPONSE" | jq '.'
LOAN_ID=$(echo "$LOAN_RESPONSE" | jq -r '.id')
echo "Loan ID: $LOAN_ID"
echo ""

echo "=== 4. List Loans ==="
curl -s -X GET "$GATEWAY_URL/api/loans" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
echo ""

echo "=== 5. Get Loan Details ==="
curl -s -X GET "$GATEWAY_URL/api/loans/$LOAN_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
