#!/bin/bash

echo "Wait for Lumina to start..."
while ! curl -s http://localhost:8080/api/v1/actuator/health | grep -q '"status":"UP"'; do
  sleep 1
done
echo "Lumina started!"

echo "Logging in..."
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

echo "Got token: $TOKEN"

echo "Generating API Key..."
API_KEY=$(curl -s -X POST http://localhost:8080/api/v1/api-keys/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"test_key"}' | jq -r '.data.apiKey')

echo "Got API Key: $API_KEY"

echo "Testing Rate Limit (Limit is 5 per minute)..."
for i in {1..7}; do
  echo -n "Request $i: "
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET http://localhost:8080/v1/models \
    -H "Authorization: Bearer $API_KEY")
  echo "HTTP $STATUS"
done

