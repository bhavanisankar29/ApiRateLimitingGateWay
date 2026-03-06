#!/bin/bash

echo "=== Rate Limiter Quick Test ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

TEST_CLIENT_ID='127.0.0.1'
STATUS_URL='http://localhost:8080/gateway/rate-limit/status'
TEST_URL='http://localhost:8080/api/test'

fetch_status() {
    curl -s -H "X-Forwarded-For: $TEST_CLIENT_ID" "$STATUS_URL" 2>/dev/null
}

extract_available_tokens() {
    if command -v jq >/dev/null 2>&1; then
        tokens=$(echo "$1" | jq -r '.availableTokens // empty' 2>/dev/null)
    else
        tokens=$(echo "$1" | sed -n 's/.*"availableTokens"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p')
    fi

    if [ -n "$tokens" ]; then
        echo "$tokens"
    else
        echo "unknown"
    fi
}

echo "1. Initial Rate Limit Status:"
initial_status=$(fetch_status)
echo "$initial_status" | jq . 2>/dev/null || echo "$initial_status"
initial_tokens=$(extract_available_tokens "$initial_status")
echo ""

# Make requests
echo "4. Making 20 requests (capacity is 10)..."
success_count=0
blocked_count=0

for i in {1..20}; do
    response=$(curl -s -H "X-Forwarded-For: $TEST_CLIENT_ID" -w "\n%{http_code}" "$TEST_URL" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    
    if [ "$http_code" = "200" ]; then
        echo -e "  Request $i: ${GREEN}✓ Allowed (200)${NC}"
        ((success_count++))
    elif [ "$http_code" = "429" ]; then
        echo -e "  Request $i: ${RED}✗ Blocked (429)${NC}"
        ((blocked_count++))
    else
        echo -e "  Request $i: ${YELLOW}? Status ($http_code)${NC}"
    fi
done
echo ""

# Final status
echo "5. Final Rate Limit Status:"
final_status=$(fetch_status)
echo "$final_status" | jq . 2>/dev/null || echo "$final_status"
final_tokens=$(extract_available_tokens "$final_status")
echo ""

# Summary
echo "=== Test Summary ==="
echo "Successful requests: $success_count"
echo "Blocked requests: $blocked_count"
echo "Initial tokens: $initial_tokens"
echo "Final tokens: $final_tokens"
echo ""

