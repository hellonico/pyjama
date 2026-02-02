#!/bin/bash
# Test Gemini API with curl

# Get API key from secrets
API_KEY=$(clj -M -e "(require '[secrets.core :as s])(print (s/get-secret :google-api-key))")

echo "Testing Gemini API with curl..."
echo "API Key (first 20 chars): ${API_KEY:0:20}..."
echo ""

# Test URL
URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${API_KEY}"

echo "Endpoint: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
echo ""

# Make request
curl -s -X POST "$URL" \
  -H 'Content-Type: application/json' \
  -d '{
    "contents": [{
      "parts":[{
        "text": "Say hello"
      }]
    }]
  }' | jq '.' || echo "Request failed"
